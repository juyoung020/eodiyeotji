//! 기록 모두 지우기 (동의 철회 → 저장 데이터 삭제, Pleos 앱마켓 심사 가이드 "개인정보 보호 및 보안").
//!
//! DB 안의 **모든 표**를 비운다: 위치 점·주행·머문 곳·장소·사진 색인·검색 색인(FTS5)·R-tree·기억(에이전트)·
//! 편집·커뮤니티 캐시·같이 가기 상태·최근 검색어 등. 표 이름을 손으로 나열하지 않고 `sqlite_master`에서 읽으므로
//! 나중에 표가 늘어도 빠지지 않는다. 가상 표(FTS5·R-tree)는 가상 표 자체를 비우고, 그 그림자 표(`x_data` 등)는
//! SQLite가 같이 정리하게 둔다. `meta`는 DB 구조에 딸린 값(시간대 `tz_offset_minutes`·날짜 규칙 `day_rules`)만 남긴다.
//! 끝나면 `VACUUM`으로 지운 내용이 파일에 남지 않게 한다.

use rusqlite::Connection;

use super::{CoreError, EodiStore};

/// 지워도 다시 만들 필요가 없는, DB 구조에 딸린 meta 키.
const KEEP_META: &[&str] = &["tz_offset_minutes", "day_rules"];

#[uniffi::export]
impl EodiStore {
    /// 기기 안 기록을 모두 지운다 (되돌릴 수 없다). 반환: 지운 줄 수(가상 표 제외, 확인용).
    /// 지운 뒤에도 저장소는 그대로 쓸 수 있다 (새 기록이 바로 쌓인다).
    pub fn wipe_all(&self) -> Result<u64, CoreError> {
        let removed = {
            let mut conn = self.lock()?;
            let tx = conn.transaction()?;
            let n = wipe_tables(&tx)?;
            tx.commit()?;
            n
        };
        // VACUUM은 트랜잭션 밖에서. 실패해도 데이터는 이미 지워졌으므로 결과는 그대로 돌려준다.
        let _ = self.lock()?.execute_batch("VACUUM");
        Ok(removed)
    }
}

fn wipe_tables(conn: &Connection) -> Result<u64, CoreError> {
    let all: Vec<(String, String)> = conn
        .prepare("SELECT name, coalesce(sql, '') FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")?
        .query_map([], |r| Ok((r.get(0)?, r.get(1)?)))?
        .collect::<Result<_, _>>()?;
    let virtuals: Vec<&str> = all
        .iter()
        .filter(|(_, sql)| sql.trim_start().to_ascii_uppercase().starts_with("CREATE VIRTUAL"))
        .map(|(n, _)| n.as_str())
        .collect();
    let is_shadow = |name: &str| virtuals.iter().any(|v| name != *v && name.starts_with(&format!("{v}_")));
    let mut removed = 0u64;
    for (name, _) in &all {
        if is_shadow(name) {
            continue;
        }
        let quoted = format!("\"{}\"", name.replace('"', "\"\""));
        if name == "meta" {
            let keep = KEEP_META.iter().map(|k| format!("'{k}'")).collect::<Vec<_>>().join(",");
            removed += conn.execute(&format!("DELETE FROM meta WHERE key NOT IN ({keep})"), [])? as u64;
        } else if virtuals.contains(&name.as_str()) {
            conn.execute(&format!("DELETE FROM {quoted}"), [])?;
        } else {
            removed += conn.execute(&format!("DELETE FROM {quoted}"), [])? as u64;
        }
    }
    Ok(removed)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::store::{DriveEvent, GeoFix};

    fn temp_path(name: &str) -> String {
        let p = std::env::temp_dir().join(format!("eodi-wipe-{name}-{}.db", std::process::id()));
        for s in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{s}", p.display()));
        }
        p.to_string_lossy().into_owned()
    }

    fn cleanup(path: &str) {
        for s in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{path}{s}"));
        }
    }

    fn count(store: &EodiStore, sql: &str) -> i64 {
        store.snapshot().unwrap().query_row(sql, [], |r| r.get(0)).unwrap()
    }

    /// 2026-11-13 09:00 KST 근처에서 서울 시내를 10분 달린 기록.
    fn seed(store: &EodiStore) {
        let t0 = 1_794_528_000_000i64;
        store.mark_drive_event(DriveEvent::Start, t0).unwrap();
        let fixes: Vec<GeoFix> = (0..120)
            .map(|i| GeoFix { t_ms: t0 + i * 5_000, lat: 37.5 + i as f64 * 0.0003, lon: 127.0, accuracy_m: 5.0 })
            .collect();
        store.ingest(fixes).unwrap();
        store.mark_drive_event(DriveEvent::End, t0 + 600_000).unwrap();
        store.day_summaries(10).unwrap();
    }

    #[test]
    fn wipe_all_removes_every_record_and_store_keeps_working() {
        let path = temp_path("all");
        {
            let store = EodiStore::open(path.clone(), 540).unwrap();
            seed(&store);
            assert!(store.fix_count().unwrap() > 0);
            assert!(!store.day_summaries(10).unwrap().is_empty());

            let removed = store.wipe_all().unwrap();
            assert!(removed > 0);
            assert_eq!(store.fix_count().unwrap(), 0);
            assert!(store.day_summaries(10).unwrap().is_empty(), "캐시된 날짜 목록도 비어야 한다");
            for t in ["trip", "drive_event", "day_summary", "place"] {
                assert_eq!(count(&store, &format!("SELECT count(*) FROM {t}")), 0, "{t}");
            }
            // 검색 색인(FTS5)도 비었다.
            assert_eq!(count(&store, "SELECT count(*) FROM memory_fts"), 0);
            // 구조 값은 남는다.
            assert_eq!(count(&store, "SELECT count(*) FROM meta WHERE key = 'tz_offset_minutes'"), 1);

            // 지운 뒤에도 바로 다시 기록된다.
            seed(&store);
            assert!(!store.day_summaries(10).unwrap().is_empty());
        }
        // 다시 열어도 문제없다.
        let store = EodiStore::open(path.clone(), 540).unwrap();
        assert!(store.fix_count().unwrap() > 0);
        drop(store);
        cleanup(&path);
    }

    #[test]
    fn wipe_all_on_empty_store_is_harmless() {
        let path = temp_path("empty");
        {
            let store = EodiStore::open(path.clone(), 540).unwrap();
            store.wipe_all().unwrap();
            assert_eq!(store.fix_count().unwrap(), 0);
        }
        cleanup(&path);
    }
}

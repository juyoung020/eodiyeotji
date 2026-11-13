//! 마지막으로 저장된 위치 한 점 — 기억 에이전트의 "지금 위치" (근처 추천 코스·지금 위치에서 가장 짧은 순서).
//!
//! 기록기는 기어가 P가 아닐 때만 위치를 저장하므로, 정차 중이면 마지막 점이 곧 차가 서 있는 자리다.
//! 표의 기본 키(t_ms) 끝에서 한 줄만 읽어 1ms 안쪽이다.

use rusqlite::OptionalExtension;

use crate::store::{CoreError, EodiStore, GeoFix};

#[uniffi::export]
impl EodiStore {
    /// 가장 최근에 저장된 위치 점. 기록이 없으면 None.
    pub fn last_fix(&self) -> Result<Option<GeoFix>, CoreError> {
        let conn = self.snapshot()?;
        let fix = conn
            .query_row("SELECT t_ms, lat, lon, accuracy_m FROM fix ORDER BY t_ms DESC LIMIT 1", [], |r| {
                Ok(GeoFix { t_ms: r.get(0)?, lat: r.get(1)?, lon: r.get(2)?, accuracy_m: r.get::<_, f64>(3)? as f32 })
            })
            .optional()?;
        Ok(fix)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn last_fix_is_the_newest_stored_point() {
        let path = crate::test_tmp::dir().join(format!("eodi-lastfix-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let store = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        assert_eq!(store.last_fix().unwrap(), None);
        let f = |t: i64, lat: f64| GeoFix { lat, lon: 126.86, t_ms: t, accuracy_m: 5.0 };
        // 늦게 들어온 옛 점은 마지막 점이 아니다
        store.ingest(vec![f(2_000_000, 37.52), f(3_000_000, 37.53)]).unwrap();
        store.ingest(vec![f(1_000_000, 37.50)]).unwrap();
        let last = store.last_fix().unwrap().unwrap();
        assert_eq!((last.t_ms, last.lat), (3_000_000, 37.53));
        drop(store);
        let _ = std::fs::remove_file(&path);
    }
}

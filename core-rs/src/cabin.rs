//! 탑승 인원 — 차 좌석 센서(Pleos Vehicle `Seat.getCurrentSeatOccupancy`)로 주행마다 몇 명이 탔는지.
//!
//! - 차가 주행을 시작하고 조금 뒤(다들 앉은 뒤) 좌석마다 앉았는지 읽어 [`EodiStore::set_drive_people`]로 넘긴다.
//!   한 주행에 여러 번 와도 된다: 그 주행 중 가장 많았던 인원을 쓴다 (휴게소에서 한 명 내려도 "4명이 함께 간 날").
//! - 센서는 "몇 명 · 어느 자리"까지만 안다. 누구인지는 모른다 → 기억 검색에서 "엄마랑 · 가족이랑 · 친구들이랑"은
//!   "둘 이상 탄 날", "혼자"는 "운전자만 탄 날"로 거른다 ([`PeopleWant`]).
//! - 동승자 정보는 기록 안에서만 쓰고 공유 카드 · 커뮤니티에는 넣지 않는다 (`share.rs`).
//!
//! # 저장 (DB 구조 버전 13)
//! `drive_people(t_ms, people)`: 읽은 시각과 인원. 주행 · 날짜와 시각으로 잇는다 (주행이 다시 계산돼도 그대로).

use rusqlite::params;

use crate::store::{CoreError, EodiStore};

pub(crate) const SCHEMA_V13: &str = "
CREATE TABLE IF NOT EXISTS drive_people (t_ms INTEGER PRIMARY KEY, people INTEGER NOT NULL);
";

/// 옛 버전 DB를 흉내 내는 테스트용 (버전 13 표를 지운다).
#[cfg(test)]
pub(crate) const DROP_V13: &str = "DROP TABLE drive_people;";

/// 질문이 바라는 동행.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PeopleWant {
    /// "엄마랑 · 가족이랑 · 친구들이랑": 둘 이상 탄 때
    Together,
    /// "혼자": 운전자만 탄 때
    Alone,
}

impl PeopleWant {
    /// 그 시간에 탄 인원(모르면 None)이 바라는 동행과 맞는가. 모르면 맞지 않는다고 본다.
    pub fn matches(self, people: Option<u32>) -> bool {
        match (self, people) {
            (PeopleWant::Together, Some(n)) => n >= 2,
            (PeopleWant::Alone, Some(n)) => n == 1,
            _ => false,
        }
    }
}

#[uniffi::export]
impl EodiStore {
    /// 지금 탄 인원을 기록한다 (`t_ms` = 읽은 시각, 주행 중). 0명은 저장하지 않는다 (센서를 못 읽은 것).
    pub fn set_drive_people(&self, t_ms: i64, people: u32) -> Result<(), CoreError> {
        if t_ms <= 0 || people == 0 {
            return Ok(());
        }
        let conn = self.lock()?;
        conn.execute("INSERT OR REPLACE INTO drive_people (t_ms, people) VALUES (?1, ?2)", params![t_ms, people as i64])?;
        Ok(())
    }

    /// 그 하루("YYYY-MM-DD")에 가장 많이 탄 인원. 모르면 0.
    pub fn day_people(&self, day: String) -> Result<u32, CoreError> {
        let d = crate::store::parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let from = self.day_start_ms(d);
        Ok(self.people_between(from, self.day_start_ms(d + 1) - 1)?.unwrap_or(0))
    }
}

impl EodiStore {
    /// `[from_ms, to_ms]` 동안 가장 많이 탄 인원 (기록이 없으면 None).
    pub fn people_between(&self, from_ms: i64, to_ms: i64) -> Result<Option<u32>, CoreError> {
        let conn = self.snapshot()?;
        let n: Option<i64> =
            conn.query_row("SELECT max(people) FROM drive_people WHERE t_ms BETWEEN ?1 AND ?2", params![from_ms, to_ms], |r| r.get(0))?;
        Ok(n.map(|n| n as u32))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stores_max_people_per_day_and_matches_wants() {
        let path = std::env::temp_dir().join(format!("eodi-cabin-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        // 2026-11-07 10:00 KST
        let t = 1_794_013_200_000;
        assert_eq!(s.day_people("2026-11-07".into()).unwrap(), 0);
        s.set_drive_people(t, 4).unwrap();
        s.set_drive_people(t + 3_600_000, 3).unwrap(); // 휴게소에서 한 명 내림
        s.set_drive_people(t + 7_200_000, 0).unwrap(); // 못 읽음 → 무시
        assert_eq!(s.day_people("2026-11-07".into()).unwrap(), 4);
        assert_eq!(s.day_people("2026-11-08".into()).unwrap(), 0);
        assert_eq!(s.people_between(t - 1, t + 1).unwrap(), Some(4));
        assert!(PeopleWant::Together.matches(Some(4)));
        assert!(!PeopleWant::Alone.matches(Some(4)));
        assert!(PeopleWant::Alone.matches(Some(1)));
        assert!(!PeopleWant::Together.matches(None));
        drop(s);
        let _ = std::fs::remove_file(&path);
    }
}

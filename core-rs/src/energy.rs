//! 배터리 · 연료 기록 — 주행을 시작할 때와 끝낼 때 잔량(%)을 남겨, 하루에 얼마나 썼고 언제 채웠는지.
//!
//! - 차가 Pleos Vehicle SDK로 읽는다: 전기차는 `EvBattery.getEvBatteryStateOfCharge`, 내연기관은 `Fuel.getFuelPercent`.
//!   출발 · 도착 때 한 번씩 [`EodiStore::set_drive_energy`]로 넘긴다.
//! - 하루 계산([`EodiStore::day_energy`]): 그날 기록을 시각순으로 보며
//!   - 줄어든 만큼을 더해 "쓴 양"(충전해서 오른 구간은 빼고),
//!   - 2%p 넘게 오른 곳을 "충전 · 주유"(한 번)로 센다. 센서 흔들림(1%p)은 무시한다.
//! - 업무 모드(출장 운행기록부)와 "이 코스는 배터리 18% 들어요" 같은 코스 안내에 쓰는 바탕이다.
//!
//! # 저장 (DB 구조 버전 14)
//! `drive_energy(t_ms, pct, ev)`: 읽은 시각, 잔량(%), 전기차면 1.

use rusqlite::params;

use crate::store::{CoreError, EodiStore};

pub(crate) const SCHEMA_V14: &str = "
CREATE TABLE IF NOT EXISTS drive_energy (t_ms INTEGER PRIMARY KEY, pct REAL NOT NULL, ev INTEGER NOT NULL);
";

/// 옛 버전 DB를 흉내 내는 테스트용.
#[cfg(test)]
pub(crate) const DROP_V14: &str = "DROP TABLE drive_energy;";

/// 오르면 충전 · 주유로 보는 최소 폭 (%p). 이보다 작은 오르내림은 센서 흔들림.
const REFILL_MIN_PCT: f64 = 2.0;

/// 하루 배터리 · 연료.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DayEnergy {
    /// 기록이 있는가 (없으면 나머지는 0)
    pub known: bool,
    /// 전기차 (false = 연료)
    pub ev: bool,
    /// 그날 첫 · 마지막 잔량 (%)
    pub start_pct: f64,
    pub end_pct: f64,
    /// 그날 쓴 양 (%p, 충전으로 오른 것은 빼고 줄어든 것만 더함)
    pub used_pct: f64,
    /// 충전 · 주유 횟수
    pub refills: u32,
}

#[uniffi::export]
impl EodiStore {
    /// 잔량 기록 (`t_ms` = 읽은 시각, `pct` 0~100, 전기차면 `ev`). 범위를 벗어나면 저장하지 않는다.
    pub fn set_drive_energy(&self, t_ms: i64, pct: f64, ev: bool) -> Result<(), CoreError> {
        if t_ms <= 0 || !(0.0..=100.0).contains(&pct) {
            return Ok(());
        }
        let conn = self.lock()?;
        conn.execute("INSERT OR REPLACE INTO drive_energy (t_ms, pct, ev) VALUES (?1, ?2, ?3)", params![t_ms, pct, ev as i64])?;
        Ok(())
    }

    /// 그 하루("YYYY-MM-DD")의 배터리 · 연료.
    pub fn day_energy(&self, day: String) -> Result<DayEnergy, CoreError> {
        let d = crate::store::parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let (from, to) = (self.day_start_ms(d), self.day_start_ms(d + 1) - 1);
        let conn = self.snapshot()?;
        let mut st = conn.prepare_cached("SELECT pct, ev FROM drive_energy WHERE t_ms BETWEEN ?1 AND ?2 ORDER BY t_ms")?;
        let rows: Vec<(f64, bool)> = st.query_map(params![from, to], |r| Ok((r.get(0)?, r.get::<_, i64>(1)? != 0)))?.collect::<Result<_, _>>()?;
        Ok(summarize(&rows))
    }
}

/// 시각순 잔량 → 하루 요약.
pub fn summarize(rows: &[(f64, bool)]) -> DayEnergy {
    let (Some(&(first, ev)), Some(&(last, _))) = (rows.first(), rows.last()) else {
        return DayEnergy { known: false, ev: false, start_pct: 0.0, end_pct: 0.0, used_pct: 0.0, refills: 0 };
    };
    let (mut used, mut refills) = (0.0, 0u32);
    for w in rows.windows(2) {
        let diff = w[1].0 - w[0].0;
        if diff < 0.0 {
            used -= diff;
        } else if diff >= REFILL_MIN_PCT {
            refills += 1;
        }
    }
    DayEnergy { known: true, ev, start_pct: first, end_pct: last, used_pct: used, refills }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sums_drops_and_counts_refills() {
        // 82 → 70 (12 사용) → 90 충전 → 84 (6 사용) → 84.5 (흔들림)
        let e = summarize(&[(82.0, true), (70.0, true), (90.0, true), (84.0, true), (84.5, true)]);
        assert!(e.known && e.ev);
        assert_eq!((e.start_pct, e.end_pct), (82.0, 84.5));
        assert!((e.used_pct - 18.0).abs() < 1e-9);
        assert_eq!(e.refills, 1);
        assert!(!summarize(&[]).known);
    }

    #[test]
    fn stores_per_day() {
        let path = crate::test_tmp::dir().join(format!("eodi-energy-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        let t = 1_794_013_200_000; // 2026-11-07 10:00 KST
        s.set_drive_energy(t, 64.0, false).unwrap();
        s.set_drive_energy(t + 3_600_000, 58.0, false).unwrap();
        s.set_drive_energy(t + 7_200_000, 140.0, false).unwrap(); // 잘못된 값 → 무시
        let e = s.day_energy("2026-11-07".into()).unwrap();
        assert!(e.known && !e.ev);
        assert!((e.used_pct - 6.0).abs() < 1e-9);
        assert!(!s.day_energy("2026-11-08".into()).unwrap().known);
        drop(s);
        let _ = std::fs::remove_file(&path);
    }
}

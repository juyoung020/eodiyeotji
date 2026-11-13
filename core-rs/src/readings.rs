//! 차 계기 기록 — 주행거리계 · 와이퍼(비) · 남은 주행 가능 거리.
//!
//! - 주행거리계(km): 차가 출발 · 도착 때 Pleos Vehicle `Odometer.getCurrentDistanceDriven`으로 읽어 넘긴다.
//!   업무용 운행기록부의 "주행 전 · 후 계기판 거리"가 된다 (`logbook.rs`).
//! - 비: 주행 중 2분마다 와이퍼(`Wiper.getCurrentWiperState`)가 움직이는지 읽어 1(비) / 0으로 넘긴다.
//!   그날 와이퍼가 한 번이라도 돌았으면 "비 온 날" → 하루 상세에 "비"가 붙고, "비 오던 날 갔던 곳"을 바로 찾는다.
//! - 남은 주행 가능 거리(km): 코스 안내를 시작할 때 읽어 코스 길이와 견준다 ([`range_check`]). 기록하지 않아도 되지만
//!   같은 표에 남겨 두면 나중에 "그날 몇 km 남기고 도착했는지"를 볼 수 있다.
//!
//! # 저장 (DB 구조 버전 15)
//! `drive_reading(t_ms, kind, value)`: 읽은 시각, 종류([`ReadingKind`]), 값.

use rusqlite::params;

use crate::store::{CoreError, EodiStore};

pub(crate) const SCHEMA_V15: &str = "
CREATE TABLE IF NOT EXISTS drive_reading (
  t_ms INTEGER NOT NULL, kind INTEGER NOT NULL, value REAL NOT NULL, PRIMARY KEY (t_ms, kind)
);
";

/// 옛 버전 DB를 흉내 내는 테스트용.
#[cfg(test)]
pub(crate) const DROP_V15: &str = "DROP TABLE drive_reading;";

/// 계기 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ReadingKind {
    /// 주행거리계 (km)
    Odometer,
    /// 와이퍼가 움직이는가 (1 = 비, 0 = 멈춤)
    Rain,
    /// 남은 주행 가능 거리 (km)
    Range,
}

impl ReadingKind {
    pub(crate) fn code(self) -> i64 {
        match self {
            ReadingKind::Odometer => 1,
            ReadingKind::Rain => 2,
            ReadingKind::Range => 3,
        }
    }

    pub(crate) fn from_code(c: i64) -> Option<Self> {
        match c {
            1 => Some(ReadingKind::Odometer),
            2 => Some(ReadingKind::Rain),
            3 => Some(ReadingKind::Range),
            _ => None,
        }
    }

    fn valid(self, v: f64) -> bool {
        match self {
            ReadingKind::Odometer => (0.0..10_000_000.0).contains(&v),
            ReadingKind::Rain => v == 0.0 || v == 1.0,
            ReadingKind::Range => (0.0..5_000.0).contains(&v),
        }
    }
}

/// 코스 길이와 남은 주행 가능 거리를 견준 결과.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RangeCheck {
    /// 충전 · 주유 없이 갈 수 있다 (여유 15% 포함)
    pub enough: bool,
    /// 화면 · 음성 한 줄 (예: "남은 거리 230km · 이 코스 64km면 넉넉해요")
    pub text: String,
}

/// 길이 견줄 때 남겨 둘 여유 (코스 길이의 15%, 최소 10km): 길이 막히거나 에어컨을 켜면 더 쓴다.
const RANGE_MARGIN: f64 = 0.15;
const RANGE_MARGIN_MIN_KM: f64 = 10.0;

/// 남은 주행 가능 거리(`range_km`)로 코스(`route_km`)를 갈 수 있는가.
pub fn range_check(range_km: f64, route_km: f64, ev: bool) -> RangeCheck {
    let need = route_km + (route_km * RANGE_MARGIN).max(RANGE_MARGIN_MIN_KM);
    let (r, c) = (range_km.round() as i64, route_km.max(1.0).round() as i64);
    if range_km >= need {
        RangeCheck { enough: true, text: format!("남은 거리 {r}km · 이 코스 {c}km면 넉넉해요") }
    } else {
        let what = if ev { "충전" } else { "주유" };
        let short = if range_km < route_km { format!("{}km 모자라요", (route_km - range_km).ceil() as i64) } else { "빠듯해요".into() };
        RangeCheck { enough: false, text: format!("남은 거리 {r}km · 이 코스 {c}km라 {short}. 가는 길에 {what}하고 가요") }
    }
}

/// 직선거리를 실제 도로 길이로 늘리는 비율 (국내 도로 평균 우회율 정도).
const ROAD_FACTOR: f64 = 1.3;

/// 코스 길이 어림 (km): `from`(지금 위치, 모르면 첫 곳부터)에서 구간의 경유지 · 목적지를 차례로 지나는 직선거리 × 1.3.
pub fn course_km(from: Option<crate::ffi::GeoPoint>, legs: &[crate::memory::CourseLeg]) -> f64 {
    let mut prev = from.map(|p| crate::geo::Point { lat: p.lat, lon: p.lon });
    let mut m = 0.0;
    for s in legs.iter().flat_map(|l| l.waypoints.iter().chain(std::iter::once(&l.destination))) {
        let p = crate::geo::Point { lat: s.lat, lon: s.lon };
        if let Some(q) = prev {
            m += crate::geo::haversine_m(q, p);
        }
        prev = Some(p);
    }
    m / 1000.0 * ROAD_FACTOR
}

#[uniffi::export]
impl EodiStore {
    /// 계기 값 기록 (`t_ms` = 읽은 시각). 범위를 벗어난 값은 저장하지 않는다.
    pub fn set_drive_reading(&self, t_ms: i64, kind: ReadingKind, value: f64) -> Result<(), CoreError> {
        if t_ms <= 0 || !kind.valid(value) {
            return Ok(());
        }
        let conn = self.lock()?;
        conn.execute(
            "INSERT OR REPLACE INTO drive_reading (t_ms, kind, value) VALUES (?1, ?2, ?3)",
            params![t_ms, kind.code(), value],
        )?;
        Ok(())
    }

    /// 그 하루("YYYY-MM-DD")에 와이퍼가 돌았는가 (비 온 날).
    pub fn day_rain(&self, day: String) -> Result<bool, CoreError> {
        let d = crate::store::parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        Ok(self.rain_between(self.day_start_ms(d), self.day_start_ms(d + 1) - 1)? == Some(true))
    }
}

impl EodiStore {
    /// `[from_ms, to_ms]` 동안 와이퍼가 돌았는가 (읽은 적이 없으면 None).
    pub fn rain_between(&self, from_ms: i64, to_ms: i64) -> Result<Option<bool>, CoreError> {
        let conn = self.snapshot()?;
        let v: Option<f64> = conn.query_row(
            "SELECT max(value) FROM drive_reading WHERE kind = ?3 AND t_ms BETWEEN ?1 AND ?2",
            params![from_ms, to_ms, ReadingKind::Rain.code()],
            |r| r.get(0),
        )?;
        Ok(v.map(|v| v > 0.0))
    }

    /// `[from_ms, to_ms]`의 주행거리계 기록 (시각순).
    pub(crate) fn odometer_between(&self, conn: &rusqlite::Connection, from_ms: i64, to_ms: i64) -> Result<Vec<(i64, f64)>, CoreError> {
        let mut st = conn.prepare_cached("SELECT t_ms, value FROM drive_reading WHERE kind = ?3 AND t_ms BETWEEN ?1 AND ?2 ORDER BY t_ms")?;
        let rows = st
            .query_map(params![from_ms, to_ms, ReadingKind::Odometer.code()], |r| Ok((r.get(0)?, r.get(1)?)))?
            .collect::<Result<_, _>>()?;
        Ok(rows)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn range_check_keeps_a_margin() {
        assert!(range_check(230.0, 64.0, true).enough);
        assert_eq!(range_check(230.0, 64.0, true).text, "남은 거리 230km · 이 코스 64km면 넉넉해요");
        // 64km + 여유 10km = 74km 필요
        let tight = range_check(70.0, 64.0, true);
        assert!(!tight.enough && tight.text.contains("빠듯해요") && tight.text.contains("충전"));
        let short = range_check(50.0, 64.0, false);
        assert!(!short.enough && short.text.contains("14km 모자라요") && short.text.contains("주유"));
    }

    #[test]
    fn course_km_walks_the_stops_in_order() {
        let stop = |lat: f64| crate::memory::StopFact {
            name: String::new(), category: String::new(), address: String::new(),
            arrive_ms: 0, leave_ms: 0, lat, lon: 127.0, first_visit: false,
        };
        // 위도 0.1도 ≈ 11.1km, 세 번 → 33.4km × 1.3
        let legs = vec![crate::memory::CourseLeg { waypoints: vec![stop(37.1), stop(37.2)], destination: stop(37.3) }];
        let km = course_km(Some(crate::ffi::GeoPoint { lat: 37.0, lon: 127.0 }), &legs);
        assert!((km - 33.36 * 1.3).abs() < 0.3, "{km}");
        assert!((course_km(None, &legs) - 22.24 * 1.3).abs() < 0.3);
    }

    #[test]
    fn stores_rain_per_day_and_rejects_bad_values() {
        let path = crate::test_tmp::dir().join(format!("eodi-readings-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        let t = 1_794_013_200_000; // 2026-11-07 10:00 KST
        s.set_drive_reading(t, ReadingKind::Rain, 0.0).unwrap();
        assert!(!s.day_rain("2026-11-07".into()).unwrap());
        s.set_drive_reading(t + 120_000, ReadingKind::Rain, 1.0).unwrap();
        s.set_drive_reading(t + 240_000, ReadingKind::Rain, 0.5).unwrap(); // 잘못된 값 → 무시
        assert!(s.day_rain("2026-11-07".into()).unwrap());
        assert_eq!(s.rain_between(t - 86_400_000, t - 1).unwrap(), None);
        s.set_drive_reading(t, ReadingKind::Odometer, 18_240.5).unwrap();
        s.set_drive_reading(t + 1, ReadingKind::Odometer, -3.0).unwrap();
        let conn = s.snapshot().unwrap();
        assert_eq!(s.odometer_between(&conn, t - 1, t + 10).unwrap(), vec![(t, 18_240.5)]);
        drop(conn);
        drop(s);
        let _ = std::fs::remove_file(&path);
    }
}

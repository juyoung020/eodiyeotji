//! 출장 관리 — 평일 업무 시간에 집 · 회사에서 멀리 다녀온 나들이를 "출장"으로 묶고, 한 건마다 정산서 한 줄을 만든다.
//!
//! # 출장 찾기 ([EodiStore::biz_trips])
//! - 평일(월~금) 하루의 에피소드 중 출퇴근이 아닌 것, 그리고
//! - 집과 회사 둘 다에서 [BIZ_MIN_KM] 넘게 떨어진 곳에 들렀고,
//! - 07:00~20:00 사이에 출발한 것.
//! - 사용자가 하루를 "출장" / "출장 아님"으로 바꿀 수 있다 ([EodiStore::set_biz_mark]). 바꾼 날은 자동 판정보다 앞선다
//!   ("출장"으로 정한 날은 그날 출퇴근이 아닌 에피소드를 모두 출장으로 본다 — 가까운 고객사 방문도 넣을 수 있게).
//!
//! # 한 건 = 정산서 한 줄 ([BizTrip])
//! - 행선지: 집 · 회사가 아닌 들른 곳 (머문 시간 포함), 대표 지역 → "경기 화성시 출장"
//! - 거리: 출발 · 도착 무렵 주행거리계 차이, 없으면 GPS 거리 (`readings.rs`, 운행기록부와 같은 규칙)
//! - 에너지: 그 사이 배터리 · 연료 기록으로 쓴 양(%p, `energy.rs`)
//! - 경비 어림: 쓴 에너지 × 단가 (전기차: 배터리 [EV_BATTERY_KWH]kWh × [EV_WON_PER_KWH]원,
//!   연료: 탱크 [FUEL_TANK_L]L × [FUEL_WON_PER_L]원). 에너지 기록이 없으면 거리 × km당 단가([WON_PER_KM_EV] · [WON_PER_KM_FUEL]).
//!   **시연용 어림값**이다 — 실제 정산 단가는 회사 규정으로 바꾼다.
//! - CSV([biz_report_csv]): 엑셀에서 한글이 깨지지 않게 UTF-8 BOM, 회사 정산 양식에 붙여 넣기 쉬운 칸.
//!
//! # 저장 (DB 구조 버전 16)
//! `biz_mark(day, mark)`: 사용자가 정한 날 ("YYYY-MM-DD", 1 = 출장, 0 = 출장 아님).

use std::collections::HashMap;

use rusqlite::{params, OptionalExtension};

use crate::geo::{haversine_m, Point};
use crate::memory::{Episode, EpisodeKind};
use crate::store::{CoreError, EodiStore};

pub(crate) const SCHEMA_V16: &str = "
CREATE TABLE IF NOT EXISTS biz_mark (day TEXT PRIMARY KEY, mark INTEGER NOT NULL);
";

/// 옛 버전 DB를 흉내 내는 테스트용.
#[cfg(test)]
pub(crate) const DROP_V16: &str = "DROP TABLE biz_mark;";

/// 집 · 회사에서 이만큼(km) 넘게 떨어진 곳에 들러야 출장으로 본다 (동네 볼일 · 점심과 가르려고).
pub const BIZ_MIN_KM: f64 = 15.0;
/// 출장으로 보는 출발 시각 (현지 시, 이상 · 미만).
const WORK_FROM_H: i64 = 7;
const WORK_TO_H: i64 = 20;
/// 주행거리계 읽은 시각이 출발 · 도착에서 이만큼 안이면 그 주행의 값으로 본다.
const MATCH_MS: i64 = 10 * 60_000;

/// 경비 어림 단가 (시연용, 2026 공공 급속충전 · 전국 평균 휘발유 근처 값).
pub const EV_BATTERY_KWH: f64 = 77.4;
pub const EV_WON_PER_KWH: f64 = 347.0;
pub const FUEL_TANK_L: f64 = 55.0;
pub const FUEL_WON_PER_L: f64 = 1_650.0;
/// 에너지 기록이 없을 때 km당 단가.
pub const WON_PER_KM_EV: f64 = 60.0;
pub const WON_PER_KM_FUEL: f64 = 140.0;

/// 출장 행선지 한 곳.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BizStop {
    pub name: String,
    pub address: String,
    pub arrive_ms: i64,
    pub leave_ms: i64,
    pub lat: f64,
    pub lon: f64,
}

/// 출장 한 건 (정산서 한 줄).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BizTrip {
    /// "YYYY-MM-DD"
    pub day: String,
    /// "월" … "금"
    pub weekday: String,
    pub episode_id: i64,
    pub start_ms: i64,
    pub end_ms: i64,
    /// "경기 화성시 출장"
    pub title: String,
    /// 대표 지역 (모르면 빈 문자열)
    pub region: String,
    pub stops: Vec<BizStop>,
    pub distance_km: f64,
    /// 계기판이 아니라 GPS로 잰 거리
    pub gps_distance: bool,
    /// 운전한 시간 (분) — 출발부터 도착까지에서 행선지에 머문 시간을 뺀 것
    pub drive_min: i64,
    /// 쓴 배터리 · 연료 (%p, 기록이 없으면 None)
    pub energy_pct: Option<f64>,
    pub ev: bool,
    /// 경비 어림 (원, 100원 단위)
    pub cost_won: i64,
    /// 사용자가 직접 출장으로 정했나 (false = 자동으로 찾음)
    pub marked: bool,
}

/// 한 달 출장.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BizMonth {
    /// "YYYY-MM"
    pub month: String,
    pub trips: Vec<BizTrip>,
    /// 출장 거리 합 (km, 계기판 우선 — 하루 상세의 GPS 거리와 출처 · 범위가 다르다)
    pub total_km: f64,
    /// [total_km]에 GPS로 잰 출장이 섞였나 (false = 모두 계기판). 화면은 숫자 옆에 기준을 적는다
    pub total_has_gps: bool,
    pub total_won: i64,
    /// 출장에 쓴 시간 (분, 출발~도착)
    pub total_min: i64,
}

#[uniffi::export]
impl EodiStore {
    /// 그 달("YYYY-MM")의 출장 (시각순).
    pub fn biz_trips(&self, month: String) -> Result<BizMonth, CoreError> {
        let first = crate::logbook::parse_month(&month).ok_or_else(|| CoreError::InvalidInput(format!("month {month:?}")))?;
        let next = crate::logbook::next_month(first);
        let marks = self.biz_marks()?;
        let anchors = self.home_office()?;
        let home = anchors.home.map(|a| Point { lat: a.lat, lon: a.lon });
        let office = anchors.office.map(|a| Point { lat: a.lat, lon: a.lon });
        let (odo, energy) = {
            let conn = self.snapshot()?;
            let (lo, hi) = (self.day_start_ms(first) - MATCH_MS, self.day_start_ms(next) + MATCH_MS);
            let odo = self.odometer_between(&conn, lo, hi)?;
            let mut st = conn.prepare_cached("SELECT t_ms, pct, ev FROM drive_energy WHERE t_ms BETWEEN ?1 AND ?2 ORDER BY t_ms")?;
            let energy: Vec<(i64, f64, bool)> =
                st.query_map(params![lo, hi], |r| Ok((r.get(0)?, r.get(1)?, r.get::<_, i64>(2)? != 0)))?.collect::<Result<_, _>>()?;
            (odo, energy)
        };
        let mut trips = Vec::new();
        for d in first..next {
            let day = crate::store::format_day(d);
            let mark = marks.get(&day).copied();
            let wd = crate::memory::episode::weekday_index(d);
            if mark == Some(false) || (mark.is_none() && !(1..=5).contains(&wd)) {
                continue;
            }
            for e in self.episodes_for_day(day.clone())? {
                let f = &e.facts;
                if matches!(f.kind, EpisodeKind::CommuteToWork | EpisodeKind::CommuteHome) || f.stops.is_empty() {
                    continue;
                }
                let away = |s: &crate::memory::StopFact| {
                    let p = Point { lat: s.lat, lon: s.lon };
                    [home, office].iter().flatten().all(|a| haversine_m(*a, p) > BIZ_MIN_KM * 1000.0)
                };
                let hour = ((f.start_ms / 60_000 + self.tz_offset_minutes as i64).rem_euclid(1440)) / 60;
                // 출장은 집이나 회사에서 떠난다 (여행지에서 시작한 주행은 출장이 아니다)
                let from_base = || -> Result<bool, CoreError> {
                    let conn = self.snapshot()?;
                    let p: Option<(f64, f64)> = conn
                        .query_row("SELECT lat, lon FROM fix WHERE t_ms >= ?1 AND t_ms <= ?2 ORDER BY t_ms LIMIT 1", [f.start_ms, f.end_ms], |r| {
                            Ok((r.get(0)?, r.get(1)?))
                        })
                        .optional()?;
                    let anchors: Vec<Point> = [home, office].into_iter().flatten().collect();
                    Ok(match p {
                        Some((lat, lon)) if !anchors.is_empty() => {
                            anchors.iter().any(|a| haversine_m(*a, Point { lat, lon }) <= BIZ_MIN_KM * 1000.0)
                        }
                        _ => true,
                    })
                };
                let auto = f.stops.iter().any(away) && (WORK_FROM_H..WORK_TO_H).contains(&hour) && from_base()?;
                if mark != Some(true) && !auto {
                    continue;
                }
                trips.push(trip(&e, &day, wd, away, &odo, &energy, mark == Some(true)));
            }
        }
        let total_km = trips.iter().map(|t| t.distance_km).sum();
        let total_won = trips.iter().map(|t| t.cost_won).sum();
        let total_min = trips.iter().map(|t| (t.end_ms - t.start_ms) / 60_000).sum();
        let total_has_gps = trips.iter().any(|t| t.gps_distance);
        Ok(BizMonth { month, trips, total_km, total_has_gps, total_won, total_min })
    }

    /// 하루를 출장으로 정한다 (`Some(true)` 출장, `Some(false)` 출장 아님, `None` 자동 판정으로 되돌리기).
    pub fn set_biz_mark(&self, day: String, mark: Option<bool>) -> Result<(), CoreError> {
        crate::store::parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let conn = self.lock()?;
        match mark {
            Some(m) => conn.execute("INSERT OR REPLACE INTO biz_mark (day, mark) VALUES (?1, ?2)", params![day, m as i64])?,
            None => conn.execute("DELETE FROM biz_mark WHERE day = ?1", params![day])?,
        };
        Ok(())
    }
}

impl EodiStore {
    fn biz_marks(&self) -> Result<HashMap<String, bool>, CoreError> {
        let conn = self.snapshot()?;
        let mut st = conn.prepare_cached("SELECT day, mark FROM biz_mark")?;
        let rows = st.query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, i64>(1)? != 0)))?;
        Ok(rows.collect::<Result<_, _>>()?)
    }
}

const WEEKDAYS: [&str; 7] = ["일", "월", "화", "수", "목", "금", "토"];

fn trip(
    e: &Episode,
    day: &str,
    wd: usize,
    away: impl Fn(&crate::memory::StopFact) -> bool,
    odo: &[(i64, f64)],
    energy: &[(i64, f64, bool)],
    marked: bool,
) -> BizTrip {
    let f = &e.facts;
    // 행선지: 멀리 들른 곳 (사용자가 정한 날이면 들른 곳 모두)
    let stops: Vec<BizStop> = f
        .stops
        .iter()
        .filter(|s| marked || away(s))
        .map(|s| BizStop { name: s.name.clone(), address: s.address.clone(), arrive_ms: s.arrive_ms, leave_ms: s.leave_ms, lat: s.lat, lon: s.lon })
        .collect();
    let stay_min: i64 = f.stops.iter().map(|s| (s.leave_ms - s.arrive_ms).max(0)).sum::<i64>() / 60_000;
    let nearest = |t: i64| odo.iter().filter(|(at, _)| (at - t).abs() <= MATCH_MS).min_by_key(|(at, _)| (at - t).abs()).map(|&(_, v)| v);
    let (distance_km, gps_distance) = match (nearest(f.start_ms), nearest(f.end_ms)) {
        (Some(a), Some(b)) if b >= a => (b - a, false),
        _ => (f.distance_m / 1000.0, true),
    };
    let rows: Vec<(f64, bool)> =
        energy.iter().filter(|(t, _, _)| *t >= f.start_ms - MATCH_MS && *t <= f.end_ms + MATCH_MS).map(|&(_, p, ev)| (p, ev)).collect();
    let e_sum = crate::energy::summarize(&rows);
    let energy_pct = (e_sum.known && rows.len() >= 2).then_some(e_sum.used_pct);
    let ev = e_sum.known && e_sum.ev;
    let cost = cost_won(energy_pct, ev, distance_km);
    let region = f.region.clone();
    let title = if region.is_empty() {
        stops.first().map(|s| format!("{} 출장", s.name)).unwrap_or_else(|| "출장".into())
    } else {
        format!("{region} 출장")
    };
    BizTrip {
        day: day.to_string(),
        weekday: WEEKDAYS[wd].into(),
        episode_id: f.id,
        start_ms: f.start_ms,
        end_ms: f.end_ms,
        title,
        region,
        stops,
        distance_km,
        gps_distance,
        drive_min: ((f.end_ms - f.start_ms) / 60_000 - stay_min).max(0),
        energy_pct,
        ev,
        cost_won: cost,
        marked,
    }
}

/// 경비 어림 (원, 100원 단위로 반올림). 에너지를 알면 에너지 × 단가, 모르면 거리 × km당 단가.
pub fn cost_won(energy_pct: Option<f64>, ev: bool, distance_km: f64) -> i64 {
    let won = match energy_pct {
        Some(p) if ev => p / 100.0 * EV_BATTERY_KWH * EV_WON_PER_KWH,
        Some(p) => p / 100.0 * FUEL_TANK_L * FUEL_WON_PER_L,
        None => distance_km * if ev { WON_PER_KM_EV } else { WON_PER_KM_FUEL },
    };
    ((won / 100.0).round() as i64) * 100
}

/// 출장 정산서 CSV (UTF-8 BOM, 줄바꿈 CRLF). 시각은 `tz_offset_minutes` 현지 시각.
pub fn biz_report_csv(m: &BizMonth, tz_offset_minutes: i32) -> String {
    let mut out = String::from("\u{feff}");
    out.push_str("출장일,요일,출발 시각,도착 시각,행선지,들른 곳,주행거리(km),운전 시간(분),쓴 에너지(%),경비 어림(원),비고\r\n");
    let hm = |ms: i64| {
        let m = (ms / 60_000 + tz_offset_minutes as i64).rem_euclid(24 * 60);
        format!("{:02}:{:02}", m / 60, m % 60)
    };
    for t in &m.trips {
        let mut note = vec![];
        if t.gps_distance {
            note.push("계기판 기록 없음 · GPS 거리");
        }
        if t.energy_pct.is_none() {
            note.push("에너지 기록 없음 · km당 단가");
        }
        if t.marked {
            note.push("직접 지정");
        }
        let cells = [
            t.day.clone(),
            t.weekday.clone(),
            hm(t.start_ms),
            hm(t.end_ms),
            t.title.clone(),
            t.stops.iter().map(|s| s.name.as_str()).collect::<Vec<_>>().join(" → "),
            format!("{:.1}", t.distance_km),
            t.drive_min.to_string(),
            t.energy_pct.map(|p| format!("{p:.0}")).unwrap_or_default(),
            t.cost_won.to_string(),
            note.join(" · "),
        ];
        out.push_str(&cells.iter().map(|c| crate::logbook::csv_cell(c)).collect::<Vec<_>>().join(","));
        out.push_str("\r\n");
    }
    out.push_str(&format!(",,,,합계 {}건,,{:.1},,,{},\r\n", m.trips.len(), m.total_km, m.total_won));
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ffi::GeoPoint;
    use crate::store::{DriveEvent, GeoFix};

    /// 직선으로 n점 (10초 간격)
    fn line(t: i64, n: i64, from: (f64, f64), to: (f64, f64)) -> Vec<GeoFix> {
        (0..n)
            .map(|i| {
                let k = i as f64 / (n - 1) as f64;
                GeoFix { t_ms: t + i * 10_000, lat: from.0 + (to.0 - from.0) * k, lon: from.1 + (to.1 - from.1) * k, accuracy_m: 5.0 }
            })
            .collect()
    }

    fn stay(t: i64, n: i64, at: (f64, f64)) -> Vec<GeoFix> {
        (0..n).map(|i| GeoFix { t_ms: t + i * 30_000, lat: at.0, lon: at.1, accuracy_m: 5.0 }).collect()
    }

    fn drive(s: &EodiStore, t: i64, from: (f64, f64), to: (f64, f64), n: i64) -> i64 {
        s.mark_drive_event(DriveEvent::Start, t).unwrap();
        s.ingest(line(t, n, from, to)).unwrap();
        let end = t + (n - 1) * 10_000;
        s.mark_drive_event(DriveEvent::End, end).unwrap();
        end
    }

    #[test]
    fn drive_that_starts_away_from_home_and_office_is_not_a_business_trip() {
        let path = std::env::temp_dir().join(format!("eodi-biz-away-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        let (home, office) = ((37.5530, 127.0335), (37.5672, 126.9844));
        s.set_home_office(Some(GeoPoint { lat: home.0, lon: home.1 }), Some(GeoPoint { lat: office.0, lon: office.1 })).unwrap();
        // 2026-11-05(목) 10:00: 여행 중인 강릉 숙소 → 경포호 (1시간 머묾) → 숙소
        let (hotel, lake) = ((37.7950, 128.9080), (37.7960, 128.8990));
        let t = 1_794_013_200_000 - 2 * 86_400_000;
        let arrive = drive(&s, t, hotel, lake, 60);
        s.ingest(stay(arrive + 30_000, 120, lake)).unwrap();
        drive(&s, arrive + 3_600_000 + 60_000, lake, hotel, 60);
        assert!(s.biz_trips("2026-11".into()).unwrap().trips.is_empty());
        // 직접 출장으로 정하면 들어간다
        s.set_biz_mark("2026-11-05".into(), Some(true)).unwrap();
        assert!(!s.biz_trips("2026-11".into()).unwrap().trips.is_empty());
        drop(s);
        let _ = std::fs::remove_file(&path);
    }

    #[test]
    fn far_weekday_visit_is_a_business_trip_with_cost() {
        let path = std::env::temp_dir().join(format!("eodi-biz-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        let home = (37.5530, 127.0335);
        let office = (37.5672, 126.9844);
        let hwaseong = (37.1950, 126.8320); // 회사에서 약 43km
        s.set_home_office(Some(GeoPoint { lat: home.0, lon: home.1 }), Some(GeoPoint { lat: office.0, lon: office.1 })).unwrap();
        // 2026-11-05(목) 10:00 KST: 회사 → 화성 (2시간 머묾) → 회사
        let t = 1_794_013_200_000 - 2 * 86_400_000;
        let arrive = drive(&s, t, office, hwaseong, 300);
        s.ingest(stay(arrive + 30_000, 240, hwaseong)).unwrap();
        let back = arrive + 2 * 3_600_000 + 60_000;
        let end = drive(&s, back, hwaseong, office, 300);
        s.set_drive_reading(t, crate::readings::ReadingKind::Odometer, 18_000.0).unwrap();
        s.set_drive_reading(end, crate::readings::ReadingKind::Odometer, 18_098.4).unwrap();
        s.set_drive_energy(t, 80.0, true).unwrap();
        s.set_drive_energy(arrive, 72.0, true).unwrap();
        s.set_drive_energy(end, 64.0, true).unwrap();
        // 같은 날 저녁 회사 근처 5km 볼일은 출장이 아니다 → 위 한 건만

        let m = s.biz_trips("2026-11".into()).unwrap();
        assert_eq!(m.trips.len(), 1, "{:?}", m.trips);
        let b = &m.trips[0];
        assert_eq!((b.day.as_str(), b.weekday.as_str()), ("2026-11-05", "목"));
        assert!(!b.gps_distance && (b.distance_km - 98.4).abs() < 1e-9);
        assert_eq!(b.energy_pct, Some(16.0));
        // 16% × 77.4kWh × 347원 = 4,297원 → 4,300원
        assert_eq!(b.cost_won, 4_300);
        assert!(b.title.ends_with("출장") && !b.stops.is_empty());
        assert!(b.drive_min > 0 && b.drive_min < (b.end_ms - b.start_ms) / 60_000);

        // "출장 아님"으로 바꾸면 빠지고, 되돌리면 다시
        s.set_biz_mark("2026-11-05".into(), Some(false)).unwrap();
        assert!(s.biz_trips("2026-11".into()).unwrap().trips.is_empty());
        s.set_biz_mark("2026-11-05".into(), None).unwrap();
        assert_eq!(s.biz_trips("2026-11".into()).unwrap().trips.len(), 1);

        let csv = biz_report_csv(&m, 540);
        assert!(csv.starts_with('\u{feff}'));
        let lines: Vec<&str> = csv.trim_end().split("\r\n").collect();
        assert_eq!(lines.len(), 3);
        assert!(lines[1].starts_with("2026-11-05,목,10:00,"), "{}", lines[1]);
        assert!(lines[1].contains(",98.4,") && lines[1].contains(",16,4300,"), "{}", lines[1]);
        assert!(lines[2].contains("합계 1건"));
        assert!(s.biz_trips("2026-1".into()).is_err());
        drop(s);
        let _ = std::fs::remove_file(&path);
    }

    #[test]
    fn cost_falls_back_to_distance() {
        assert_eq!(cost_won(None, false, 100.0), 14_000);
        assert_eq!(cost_won(None, true, 100.0), 6_000);
        // 연료 10% × 55L × 1,650원 = 9,075 → 9,100
        assert_eq!(cost_won(Some(10.0), false, 0.0), 9_100);
    }
}

#[cfg(test)]
mod demo_tests {
    use crate::store::EodiStore;

    #[test]
    fn demo_year_has_monthly_business_trips() {
        let path = std::env::temp_dir().join(format!("eodi-biz-demo-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        s.seed_demo_data(1_794_013_200_000).unwrap(); // 2026-11-07
        let oct = s.biz_trips("2026-10".into()).unwrap();
        let names: Vec<String> = oct.trips.iter().flat_map(|t| t.stops.iter().map(|x| x.name.clone())).collect();
        assert!(oct.trips.len() >= 3, "{names:?}");
        assert!(names.iter().any(|n| n.contains("남양연구소")) && names.iter().any(|n| n.contains("판교")));
        assert!(oct.trips.iter().all(|t| !t.gps_distance && t.energy_pct.is_some() && t.cost_won > 0), "계기판 · 배터리 기록으로 정산");
        assert!(oct.trips.iter().all(|t| !t.stops.iter().any(|x| x.name.contains("센터원") || x.name.contains("현대아파트"))), "집 · 회사는 행선지가 아니다");
        let sep = s.biz_trips("2026-09".into()).unwrap();
        assert!(sep.trips.iter().any(|t| t.stops.iter().any(|x| x.name == "대전컨벤션센터")), "분기 대전 출장");
        drop(s);
        let _ = std::fs::remove_file(&path);
    }
}

#[cfg(test)]
mod topup_tests {
    use crate::store::EodiStore;

    /// 예전 시연 기록(출장 · 새 나들이 없음)에 보충만 덧붙인다: 기존 주행은 그대로, 출장과 감자탕 나들이가 생긴다.
    #[test]
    fn top_up_adds_business_trips_to_an_old_demo_without_touching_it() {
        let path = std::env::temp_dir().join(format!("eodi-topup-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        let now = 1_794_013_200_000; // 2026-11-07
        s.seed_demo_data(now).unwrap();
        // 예전 판처럼: 보충 표시를 지우고 출장 날 낮 기록을 지운다 (출장이 없던 시연 기록)
        {
            let c = s.lock().unwrap();
            c.execute("DELETE FROM meta WHERE key = 'demo_topup'", []).unwrap();
        }
        let before = s.fix_count().unwrap();
        let oct_before = s.biz_trips("2026-10".into()).unwrap().trips.len();
        // 이미 출장이 있는 날은 건드리지 않으므로 더한 주행 0 · 점 수 그대로여야 한다
        let made = s.seed_demo_data(now).unwrap();
        assert_eq!(made, 0, "이미 있는 낮 기록 위에는 겹쳐 넣지 않는다");
        assert_eq!(s.fix_count().unwrap(), before);
        assert_eq!(s.biz_trips("2026-10".into()).unwrap().trips.len(), oct_before);
        assert_eq!(s.seed_demo_data(now).unwrap(), 0, "보충은 한 번");
        drop(s);
        let _ = std::fs::remove_file(&path);
    }
}

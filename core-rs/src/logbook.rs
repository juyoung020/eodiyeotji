//! 업무용 운행기록부 — 한 달 주행을 국세청 "업무용승용차 운행기록부" 칸에 맞춰 한 줄씩.
//!
//! - 주행마다: 사용일자 · 출발 · 도착 시각과 장소 · 주행 전 / 후 계기판 거리 · 주행거리 · 출퇴근 여부.
//! - 계기판 거리 = 차가 출발 · 도착 때 읽은 주행거리계(`readings.rs`, 주행 시작 · 끝 10분 안에서 가장 가까운 값).
//!   둘 다 있으면 주행거리도 계기판 차이로, 없으면 GPS로 잰 거리를 쓰고 비고에 "GPS 거리"라고 적는다.
//! - 출퇴근: 기억 모듈의 에피소드 종류(출근길 · 퇴근길)로 정한다. 그 밖의 주행이 일반 업무용인지 개인 용도인지는
//!   앱이 알 수 없으므로 칸을 비워 두고 사용자가 고른다.
//! - CSV는 엑셀에서 한글이 깨지지 않게 UTF-8 BOM을 붙인다 ([`logbook_csv`]).

use crate::memory::EpisodeKind;
use crate::store::{CoreError, EodiStore};

/// 주행 시작 · 끝과 주행거리계 읽은 시각이 이만큼 안이면 같은 주행으로 본다.
const ODO_MATCH_MS: i64 = 10 * 60_000;

/// 운행기록부 한 줄 = 주행 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct LogbookRow {
    /// "YYYY-MM-DD"
    pub day: String,
    /// "월" … "일"
    pub weekday: String,
    pub start_ms: i64,
    pub end_ms: i64,
    pub from_name: String,
    pub to_name: String,
    /// 주행 전 · 후 계기판 거리 (km, 모르면 None)
    pub odo_before_km: Option<f64>,
    pub odo_after_km: Option<f64>,
    /// 주행거리 (km): 계기판 차이, 모르면 GPS 거리
    pub distance_km: f64,
    /// 계기판이 아니라 GPS로 잰 거리
    pub gps_distance: bool,
    /// 출근길 · 퇴근길
    pub commute: bool,
}

/// 한 달 운행기록부.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Logbook {
    /// "YYYY-MM"
    pub month: String,
    pub rows: Vec<LogbookRow>,
    pub total_km: f64,
    pub commute_km: f64,
}

const WEEKDAYS: [&str; 7] = ["월", "화", "수", "목", "금", "토", "일"];

#[uniffi::export]
impl EodiStore {
    /// 그 달("YYYY-MM")의 운행기록부 (주행 시각순).
    pub fn logbook(&self, month: String) -> Result<Logbook, CoreError> {
        let first = parse_month(&month).ok_or_else(|| CoreError::InvalidInput(format!("month {month:?}")))?;
        let next = next_month(first);
        let mut rows = Vec::new();
        let odo = {
            let conn = self.snapshot()?;
            self.odometer_between(&conn, self.day_start_ms(first) - ODO_MATCH_MS, self.day_start_ms(next) + ODO_MATCH_MS)?
        };
        for d in first..next {
            let day = crate::store::format_day(d);
            let detail = self.day_detail(day.clone())?;
            if detail.trips.is_empty() {
                continue;
            }
            let episodes = self.episodes_for_day(day.clone())?;
            for t in &detail.trips {
                let commute = episodes.iter().any(|e| {
                    matches!(e.facts.kind, EpisodeKind::CommuteToWork | EpisodeKind::CommuteHome)
                        && e.facts.start_ms <= t.start_ms
                        && t.start_ms <= e.facts.end_ms
                });
                let before = nearest(&odo, t.start_ms);
                let after = nearest(&odo, t.end_ms);
                let (distance_km, gps_distance) = match (before, after) {
                    (Some(a), Some(b)) if b >= a => (b - a, false),
                    _ => (t.distance_m / 1000.0, true),
                };
                rows.push(LogbookRow {
                    day: day.clone(),
                    weekday: WEEKDAYS[(d + 3).rem_euclid(7) as usize].into(),
                    start_ms: t.start_ms,
                    end_ms: t.end_ms,
                    from_name: t.start_place.as_ref().map(|p| p.name.clone()).unwrap_or_default(),
                    to_name: t.end_place.as_ref().map(|p| p.name.clone()).unwrap_or_default(),
                    odo_before_km: before,
                    odo_after_km: after,
                    distance_km,
                    gps_distance,
                    commute,
                });
            }
        }
        let total_km = rows.iter().map(|r| r.distance_km).sum();
        let commute_km = rows.iter().filter(|r| r.commute).map(|r| r.distance_km).sum();
        Ok(Logbook { month, rows, total_km, commute_km })
    }
}

/// 운행기록부를 CSV 글로 (UTF-8 BOM, 줄바꿈 CRLF). 시각은 `tz_offset_minutes` 현지 시각.
pub fn logbook_csv(book: &Logbook, tz_offset_minutes: i32) -> String {
    let mut out = String::from("\u{feff}");
    out.push_str("사용일자,요일,출발 시각,도착 시각,출발지,도착지,주행 전 계기판(km),주행 후 계기판(km),주행거리(km),출·퇴근용(km),일반 업무용(km),비고\r\n");
    let hm = |ms: i64| {
        let m = (ms / 60_000 + tz_offset_minutes as i64).rem_euclid(24 * 60);
        format!("{:02}:{:02}", m / 60, m % 60)
    };
    let km = |v: Option<f64>| v.map(|v| format!("{v:.1}")).unwrap_or_default();
    for r in &book.rows {
        let d = format!("{:.1}", r.distance_km);
        let cells = [
            r.day.clone(),
            r.weekday.clone(),
            hm(r.start_ms),
            hm(r.end_ms),
            r.from_name.clone(),
            r.to_name.clone(),
            km(r.odo_before_km),
            km(r.odo_after_km),
            d.clone(),
            if r.commute { d } else { String::new() },
            String::new(),
            if r.gps_distance { "계기판 기록 없음 · GPS 거리".into() } else { String::new() },
        ];
        out.push_str(&cells.iter().map(|c| csv_cell(c)).collect::<Vec<_>>().join(","));
        out.push_str("\r\n");
    }
    out.push_str(&format!(",,,,,합계,,,{:.1},{:.1},,\r\n", book.total_km, book.commute_km));
    out
}

pub(crate) fn csv_cell(s: &str) -> String {
    if s.contains([',', '"', '\n', '\r']) {
        format!("\"{}\"", s.replace('"', "\"\""))
    } else {
        s.to_owned()
    }
}

fn nearest(odo: &[(i64, f64)], t: i64) -> Option<f64> {
    odo.iter().filter(|(at, _)| (at - t).abs() <= ODO_MATCH_MS).min_by_key(|(at, _)| (at - t).abs()).map(|&(_, v)| v)
}

/// "YYYY-MM" → 그 달 1일 (일 번호).
pub(crate) fn parse_month(s: &str) -> Option<i64> {
    if s.len() != 7 {
        return None;
    }
    crate::store::parse_day(&format!("{s}-01"))
}

pub(crate) fn next_month(first: i64) -> i64 {
    let s = crate::store::format_day(first);
    let (y, m): (i32, u32) = (s[0..4].parse().unwrap_or(1970), s[5..7].parse().unwrap_or(1));
    let (y, m) = if m == 12 { (y + 1, 1) } else { (y, m + 1) };
    crate::store::parse_day(&format!("{y:04}-{m:02}-01")).unwrap_or(first + 31)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::readings::ReadingKind;
    use crate::store::{DriveEvent, GeoFix};

    fn drive(start_ms: i64, n: i64, lat: f64, lon: f64, dlat: f64) -> Vec<GeoFix> {
        (0..n)
            .map(|i| GeoFix { t_ms: start_ms + i * 10_000, lat: lat + dlat * i as f64, lon, accuracy_m: 5.0 })
            .collect()
    }

    #[test]
    fn month_rows_use_odometer_and_fall_back_to_gps() {
        let path = crate::test_tmp::dir().join(format!("eodi-logbook-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        // 2026-11-07(토) 10:00 KST, 20분 주행 (위도 0.0002도 × 120 ≈ 2.7km)
        let t = 1_794_013_200_000;
        s.mark_drive_event(DriveEvent::Start, t).unwrap();
        s.ingest(drive(t, 120, 37.5, 127.0, 2e-4)).unwrap();
        let end = t + 119 * 10_000;
        s.mark_drive_event(DriveEvent::End, end).unwrap();
        s.set_drive_reading(t - 5_000, ReadingKind::Odometer, 18_240.0).unwrap();
        s.set_drive_reading(end + 5_000, ReadingKind::Odometer, 18_243.1).unwrap();
        // 다음 날 주행은 계기판 기록 없음
        let t2 = t + 86_400_000;
        s.mark_drive_event(DriveEvent::Start, t2).unwrap();
        s.ingest(drive(t2, 120, 37.6, 127.0, 2e-4)).unwrap();
        s.mark_drive_event(DriveEvent::End, t2 + 119 * 10_000).unwrap();

        let book = s.logbook("2026-11".into()).unwrap();
        assert_eq!(book.rows.len(), 2);
        let a = &book.rows[0];
        assert_eq!((a.day.as_str(), a.weekday.as_str()), ("2026-11-07", "토"));
        assert_eq!((a.odo_before_km, a.odo_after_km), (Some(18_240.0), Some(18_243.1)));
        assert!(!a.gps_distance && (a.distance_km - 3.1).abs() < 1e-9);
        let b = &book.rows[1];
        assert!(b.gps_distance && b.odo_before_km.is_none() && b.distance_km > 2.0);
        assert!(s.logbook("2026-10".into()).unwrap().rows.is_empty());
        assert!(s.logbook("2026-1".into()).is_err());

        let csv = logbook_csv(&book, 540);
        assert!(csv.starts_with('\u{feff}'));
        let lines: Vec<&str> = csv.trim_end().split("\r\n").collect();
        assert_eq!(lines.len(), 4); // 머리 + 2줄 + 합계
        assert!(lines[1].starts_with("2026-11-07,토,10:00,10:19,"));
        assert!(lines[1].contains(",18240.0,18243.1,3.1,"));
        assert!(lines[2].ends_with("계기판 기록 없음 · GPS 거리"));
        drop(s);
        let _ = std::fs::remove_file(&path);
    }

    #[test]
    fn csv_quotes_commas_and_month_rolls_over() {
        assert_eq!(csv_cell("a,b"), "\"a,b\"");
        assert_eq!(csv_cell("say \"hi\""), "\"say \"\"hi\"\"\"");
        let dec = parse_month("2026-12").unwrap();
        assert_eq!(crate::store::format_day(next_month(dec)), "2027-01-01");
    }
}

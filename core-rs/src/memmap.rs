//! 기억 지도: 기간 안의 모든 주행을 한 장의 밤하늘 지도로 (영상 첫 장면용).
//!
//! 계산은 모두 여기서 하고, Kotlin은 받은 GeoJSON을 MapLibre 소스에 그대로 넣고 달(`m`)로 필터만 바꾼다.
//!
//! # 경로 (`routes_geojson`, FeatureCollection)
//! - 주행마다 `LineString`, properties `{"m":달 번호}`. 달 번호 = 기간의 첫 기록 달에서 센 달 (0부터, `months`의 인덱스).
//! - 출발 시각순이라 필터를 `m <= k`로 넓혀 가면 한 달씩 차례로 그려진다.
//! - 점 수: 저장된 경로(5m 단순화)를 다시 [BASE_TOLERANCE_M]로 줄이고, 합이 [MAX_POINTS]를 넘으면 허용 오차를 두 배씩
//!   키워 다시 줄인다 → 1년치도 지도에 한 번에 넣을 수 있는 크기. 좌표는 소수 5자리(약 1m).
//! - 같은 길(출퇴근)을 여러 번 달리면 선이 겹쳐 그려져 저절로 더 환해진다.
//!
//! # 자주 간 곳 (`visits_geojson`)
//! - 주행의 도착점을 약 300m 격자([CELL_DEG])로 묶어, 그곳에 도착한 날 수를 센다 (같은 날 여러 번은 한 번).
//! - `Point`(묶음의 평균 위치), properties `{"n":날 수,"w":0~1 밝기,"m":처음 간 달}`. 밝기 = ln(1+n) / ln(1+최대 n).
//! - 많이 간 곳부터 최대 [MAX_VISITS]곳.
//!
//! # 빛기둥 (`pillars_geojson`, 3D 기억 지도)
//! - 많이 간 곳 위쪽 [MAX_PILLARS]곳을 팔각형 `Polygon`으로 (fill-extrusion으로 세운다).
//! - properties `{"w":밝기,"m":처음 간 달,"h":높이 m}`. 반지름 = [PILLAR_MIN_R_M] + [PILLAR_GROW_R_M]×w,
//!   높이 = [PILLAR_MIN_H_M] + [PILLAR_GROW_H_M]×w (나라 전체를 보는 축척에서도 보이게 크다).
//!
//! # 합계
//! - 거리 · 달린 날: 날짜별 요약(`day_summary`)의 합 (올해 결산과 같은 숫자). 주행 수: 주행 표.
//! - 기간 길이: 첫 기록 날 ~ 마지막 기록 날. 360일 이상이면 `span_years`(반올림), 아니면 `span_months`만 쓴다.

use std::collections::{HashMap, HashSet};
use std::fmt::Write;

use rusqlite::params;

use crate::agent::dates::civil_from_days;
use crate::geo::{self, Point};
use crate::mapdata::MapBounds;
use crate::store::{decode_points, parse_day, CoreError, EodiStore};

/// 다시 줄일 때 처음 허용 오차 (m). 나라 전체를 보는 축척이라 크게 줄여도 모양이 같다.
pub const BASE_TOLERANCE_M: f64 = 20.0;
/// 경로 점 수 상한 (모든 주행 합).
pub const MAX_POINTS: usize = 40_000;
/// 자주 간 곳 격자 크기 (도, 약 300m).
pub const CELL_DEG: f64 = 0.003;
/// 자주 간 곳 최대 개수.
pub const MAX_VISITS: usize = 400;
/// 빛기둥 최대 개수.
pub const MAX_PILLARS: usize = 40;
pub const PILLAR_MIN_R_M: f64 = 250.0;
pub const PILLAR_GROW_R_M: f64 = 650.0;
pub const PILLAR_MIN_H_M: f64 = 1_500.0;
pub const PILLAR_GROW_H_M: f64 = 16_000.0;

/// 기억 지도 데이터.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MemoryMap {
    /// 주행 경로 GeoJSON (모듈 설명)
    pub routes_geojson: String,
    /// 자주 간 곳 GeoJSON (모듈 설명)
    pub visits_geojson: String,
    /// 자주 간 곳 빛기둥 GeoJSON (모듈 설명)
    pub pillars_geojson: String,
    /// 그릴 것이 없으면 None
    pub bounds: Option<MapBounds>,
    /// 달 이름 ("2025년 11월", 화면에 그대로 쓴다), 인덱스 = 경로의 `m`. 기록이 없는 달도 이어서 넣는다.
    pub months: Vec<String>,
    pub distance_m: f64,
    /// 거리 (km, 반올림)
    pub distance_km: u32,
    pub drive_count: u32,
    pub drive_days: u32,
    /// 첫 기록 날 ~ 마지막 기록 날 (날 수, 양 끝 포함)
    pub span_days: u32,
    /// 360일 이상이면 해 수 (반올림), 아니면 0
    pub span_years: u32,
    /// 달 수 (올림, 최소 1). 기록이 없으면 0
    pub span_months: u32,
    /// 경로 GeoJSON의 점 수 합
    pub point_count: u32,
    /// 경로를 줄인 허용 오차 (m)
    pub tolerance_m: f64,
}

#[uniffi::export]
impl EodiStore {
    /// 기억 지도 (`from_day` ~ `to_day`, "YYYY-MM-DD", 양 끝 포함). 날짜가 틀리거나 순서가 거꾸로면 `InvalidInput`.
    pub fn memory_map(&self, from_day: String, to_day: String) -> Result<MemoryMap, CoreError> {
        let (Some(from), Some(to)) = (parse_day(&from_day), parse_day(&to_day)) else {
            return Err(CoreError::InvalidInput("day".into()));
        };
        if from > to {
            return Err(CoreError::InvalidInput("from > to".into()));
        }
        let conn = self.read()?;
        let lo = self.day_start_ms(from);
        let hi = self.day_start_ms(to + 1);

        // 주행: (출발 날, 도착점, 경로)
        let mut trips: Vec<(i64, Point, Vec<Point>)> = Vec::new();
        {
            let mut st = conn.prepare(
                "SELECT start_ms, end_lat, end_lon, path FROM trip WHERE start_ms >= ?1 AND start_ms < ?2 ORDER BY start_ms",
            )?;
            let mut rows = st.query(params![lo, hi])?;
            while let Some(r) = rows.next()? {
                let start: i64 = r.get(0)?;
                let end = Point { lat: r.get(1)?, lon: r.get(2)? };
                let blob: Vec<u8> = r.get(3)?;
                trips.push((self.local_day(start), end, decode_points(&blob)));
            }
        }
        let (distance_m, drive_days): (f64, i64) = conn.query_row(
            "SELECT coalesce(sum(distance_m), 0), count(*) FROM day_summary WHERE day BETWEEN ?1 AND ?2 AND trip_count > 0",
            params![from, to],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )?;
        drop(conn);
        Ok(build(&trips, distance_m, drive_days as u32))
    }
}

/// 날짜 번호 → 달 번호 (해 × 12 + 달 − 1).
fn month_key(day: i64) -> i64 {
    let (y, m, _) = civil_from_days(day);
    y * 12 + m as i64 - 1
}

/// 주행 목록(출발 날순)으로 기억 지도를 만든다.
pub(crate) fn build(trips: &[(i64, Point, Vec<Point>)], distance_m: f64, drive_days: u32) -> MemoryMap {
    let (Some(first), Some(last)) = (trips.first(), trips.last()) else {
        return MemoryMap {
            routes_geojson: r#"{"type":"FeatureCollection","features":[]}"#.into(),
            visits_geojson: r#"{"type":"FeatureCollection","features":[]}"#.into(),
            pillars_geojson: r#"{"type":"FeatureCollection","features":[]}"#.into(),
            bounds: None,
            months: Vec::new(),
            distance_m,
            distance_km: (distance_m / 1000.0).round() as u32,
            drive_count: 0,
            drive_days,
            span_days: 0,
            span_years: 0,
            span_months: 0,
            point_count: 0,
            tolerance_m: BASE_TOLERANCE_M,
        };
    };
    let m0 = month_key(first.0);
    let months: Vec<String> = (m0..=month_key(last.0))
        .map(|k| format!("{}년 {}월", k.div_euclid(12), k.rem_euclid(12) + 1))
        .collect();

    // 줄이기: 상한을 넘으면 허용 오차를 두 배씩
    let mut tol = BASE_TOLERANCE_M;
    let kept: Vec<Vec<Point>> = loop {
        let k: Vec<Vec<Point>> = trips
            .iter()
            .map(|(_, _, p)| geo::simplify(p, tol).into_iter().map(|i| p[i]).collect::<Vec<_>>())
            .collect();
        let n: usize = k.iter().filter(|p| p.len() >= 2).map(Vec::len).sum();
        if n <= MAX_POINTS || tol > 50_000.0 {
            break k;
        }
        tol *= 2.0;
    };

    let mut bounds: Option<MapBounds> = None;
    let mut grow = |p: Point| {
        let b = bounds.get_or_insert(MapBounds { min_lat: p.lat, min_lon: p.lon, max_lat: p.lat, max_lon: p.lon });
        b.min_lat = b.min_lat.min(p.lat);
        b.min_lon = b.min_lon.min(p.lon);
        b.max_lat = b.max_lat.max(p.lat);
        b.max_lon = b.max_lon.max(p.lon);
    };
    let mut routes = String::from(r#"{"type":"FeatureCollection","features":["#);
    let mut point_count = 0usize;
    let mut firstf = true;
    for ((day, _, _), path) in trips.iter().zip(&kept) {
        if path.len() < 2 {
            continue;
        }
        if !firstf {
            routes.push(',');
        }
        firstf = false;
        let m = month_key(*day) - m0;
        let _ = write!(routes, r#"{{"type":"Feature","properties":{{"m":{m}}},"geometry":{{"type":"LineString","coordinates":["#);
        for (i, p) in path.iter().enumerate() {
            if i > 0 {
                routes.push(',');
            }
            let _ = write!(routes, "[{:.5},{:.5}]", p.lon, p.lat);
            grow(*p);
        }
        routes.push_str("]}}");
        point_count += path.len();
    }
    routes.push_str("]}");

    // 자주 간 곳: 도착점 격자별 (날 집합, 위치 합, 처음 달)
    struct Cell {
        days: HashSet<i64>,
        lat: f64,
        lon: f64,
        n: f64,
        first_m: i64,
    }
    let mut cells: HashMap<(i64, i64), Cell> = HashMap::new();
    for (day, end, _) in trips {
        let key = ((end.lat / CELL_DEG).floor() as i64, (end.lon / CELL_DEG).floor() as i64);
        let c = cells.entry(key).or_insert(Cell { days: HashSet::new(), lat: 0.0, lon: 0.0, n: 0.0, first_m: month_key(*day) - m0 });
        c.days.insert(*day);
        c.lat += end.lat;
        c.lon += end.lon;
        c.n += 1.0;
    }
    let mut list: Vec<(usize, f64, f64, i64)> =
        cells.into_values().map(|c| (c.days.len(), c.lat / c.n, c.lon / c.n, c.first_m)).collect();
    list.sort_by(|a, b| b.0.cmp(&a.0).then(a.1.total_cmp(&b.1)).then(a.2.total_cmp(&b.2)));
    list.truncate(MAX_VISITS);
    let max_n = list.first().map_or(1, |v| v.0).max(1) as f64;
    let mut visits = String::from(r#"{"type":"FeatureCollection","features":["#);
    for (i, (n, lat, lon, m)) in list.iter().enumerate() {
        if i > 0 {
            visits.push(',');
        }
        let w = (1.0 + *n as f64).ln() / (1.0 + max_n).ln();
        let _ = write!(
            visits,
            r#"{{"type":"Feature","properties":{{"n":{n},"w":{w:.3},"m":{m}}},"geometry":{{"type":"Point","coordinates":[{lon:.5},{lat:.5}]}}}}"#
        );
    }
    visits.push_str("]}");
    let mut pillars = String::from(r#"{"type":"FeatureCollection","features":["#);
    for (i, (n, lat, lon, m)) in list.iter().take(MAX_PILLARS).enumerate() {
        if i > 0 {
            pillars.push(',');
        }
        let w = (1.0 + *n as f64).ln() / (1.0 + max_n).ln();
        let r = PILLAR_MIN_R_M + PILLAR_GROW_R_M * w;
        let h = PILLAR_MIN_H_M + PILLAR_GROW_H_M * w;
        let _ = write!(pillars, r#"{{"type":"Feature","properties":{{"w":{w:.3},"m":{m},"h":{h:.0}}},"geometry":{{"type":"Polygon","coordinates":[["#);
        let dlat = r / 111_320.0;
        let dlon = r / (111_320.0 * lat.to_radians().cos().max(0.01));
        for k in 0..=8 {
            let a = (k % 8) as f64 * std::f64::consts::FRAC_PI_4;
            if k > 0 {
                pillars.push(',');
            }
            let _ = write!(pillars, "[{:.5},{:.5}]", lon + dlon * a.cos(), lat + dlat * a.sin());
        }
        pillars.push_str("]]}}");
    }
    pillars.push_str("]}");

    let span_days = (last.0 - first.0 + 1) as u32;
    MemoryMap {
        routes_geojson: routes,
        visits_geojson: visits,
        pillars_geojson: pillars,
        bounds,
        months,
        distance_m,
        distance_km: (distance_m / 1000.0).round() as u32,
        drive_count: trips.len() as u32,
        drive_days,
        span_days,
        span_years: if span_days >= 360 { ((span_days as f64 / 365.0).round() as u32).max(1) } else { 0 },
        span_months: ((span_days as f64 / 30.44).ceil() as u32).max(1),
        point_count: point_count as u32,
        tolerance_m: tol,
    }
}

#[cfg(test)]
mod tests {
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU32, Ordering};

    use super::*;
    use crate::agent::dates::days_from_civil;
    use crate::store::DAY_MS;

    const KST: i32 = 540;

    struct TempDb(PathBuf);
    impl Drop for TempDb {
        fn drop(&mut self) {
            for s in ["", "-wal", "-shm"] {
                let _ = std::fs::remove_file(format!("{}{s}", self.0.display()));
            }
        }
    }

    fn temp(name: &str) -> TempDb {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        TempDb(std::env::temp_dir().join(format!("eodi-memmap-{name}-{}-{n}.db", std::process::id())))
    }

    fn today() -> i64 {
        days_from_civil(2026, 11, 6) * DAY_MS + 12 * 3_600_000 - KST as i64 * 60_000
    }

    fn line(n: usize, lat0: f64) -> Vec<Point> {
        (0..n).map(|i| Point { lat: lat0 + i as f64 * 1e-4, lon: 127.0 + (i as f64 * 0.05).sin() * 0.01 }).collect()
    }

    #[test]
    fn empty_is_empty() {
        let m = build(&[], 0.0, 0);
        assert!(m.bounds.is_none() && m.months.is_empty() && m.span_months == 0);
        assert!(m.routes_geojson.contains("\"features\":[]"));
    }

    #[test]
    fn months_count_from_first_drive_and_visits_rank_by_days() {
        let d = |y, m, dd| days_from_civil(y, m, dd);
        let home = Point { lat: 37.55, lon: 127.03 };
        let sea = Point { lat: 37.79, lon: 128.91 };
        let trips = vec![
            (d(2025, 11, 30), home, line(300, 37.5)),
            (d(2025, 11, 30), home, line(300, 37.5)), // 같은 날 두 번 = 한 날
            (d(2026, 1, 2), home, line(300, 37.5)),
            (d(2026, 2, 3), sea, line(300, 37.6)),
        ];
        let m = build(&trips, 123_456.0, 3);
        assert_eq!(m.months, ["2025년 11월", "2025년 12월", "2026년 1월", "2026년 2월"]);
        assert_eq!(m.distance_km, 123);
        assert_eq!(m.drive_count, 4);
        assert_eq!(m.span_years, 0);
        assert_eq!(m.span_months, 3);
        assert!(m.routes_geojson.contains(r#""m":0"#) && m.routes_geojson.contains(r#""m":3"#));
        assert!(!m.routes_geojson.contains(r#""m":1"#), "12월은 주행 없음");
        // 집 = 2날(밝기 1), 바다 = 1날
        let first_visit = m.visits_geojson.find(r#""n":2,"w":1.000,"m":0"#).expect("집이 먼저");
        let sea_visit = m.visits_geojson.find(r#""n":1,"#).expect("바다");
        assert!(first_visit < sea_visit);
        // 빛기둥: 곳마다 닫힌 팔각형 하나, 많이 간 집이 더 높다
        assert_eq!(m.pillars_geojson.matches(r#""type":"Polygon""#).count(), 2);
        let home_h = PILLAR_MIN_H_M + PILLAR_GROW_H_M;
        assert!(m.pillars_geojson.contains(&format!(r#""w":1.000,"m":0,"h":{home_h:.0}"#)));
        assert!(serde_json::from_str::<serde_json::Value>(&m.pillars_geojson).is_ok());
        let b = m.bounds.unwrap();
        assert!(b.min_lat >= 37.49 && b.max_lat <= 37.64);
        assert!(m.point_count as usize <= 4 * 300);
    }

    #[test]
    fn point_budget_raises_tolerance() {
        // 지그재그 긴 경로 40개 = 단순화 전 12만 점
        let zig: Vec<Point> =
            (0..3_000).map(|i| Point { lat: 36.0 + i as f64 * 2e-4, lon: 127.0 + if i % 2 == 0 { 0.0 } else { 0.02 } }).collect();
        let trips: Vec<(i64, Point, Vec<Point>)> = (0..40).map(|i| (20_000 + i * 5, Point { lat: 36.0, lon: 127.0 }, zig.clone())).collect();
        let m = build(&trips, 0.0, 40);
        assert!(m.point_count as usize <= MAX_POINTS, "{}", m.point_count);
        assert!(m.tolerance_m > BASE_TOLERANCE_M);
        assert_eq!(m.span_months, 7);
    }

    #[test]
    fn seeded_year_fits_budget_and_matches_year_totals() {
        let db = temp("seed");
        let store = EodiStore::open(db.0.to_string_lossy().into_owned(), KST).unwrap();
        store.seed_demo_data(today()).unwrap();
        let t = std::time::Instant::now();
        let m = store.memory_map("2025-11-07".into(), "2026-11-06".into()).unwrap();
        let ms = t.elapsed().as_millis();
        assert!(m.drive_count > 300, "{}", m.drive_count);
        assert!(m.point_count as usize <= MAX_POINTS && m.point_count > 1_000, "{}", m.point_count);
        assert!(m.distance_km > 1_000, "{}", m.distance_km);
        assert_eq!(m.span_years, 1, "span_days {}", m.span_days);
        assert!(m.months.len() >= 12 && m.months.len() <= 13, "{:?}", m.months);
        assert!(m.visits_geojson.contains(r#""w":1.000"#));
        assert!(m.bounds.is_some());
        eprintln!("memory_map: {} drives, {} pts, tol {}m, {} km, {} ms", m.drive_count, m.point_count, m.tolerance_m, m.distance_km, ms);
        // 날짜 검사
        assert!(store.memory_map("2026-13-01".into(), "2026-11-06".into()).is_err());
        assert!(store.memory_map("2026-11-06".into(), "2026-01-01".into()).is_err());
        assert_eq!(store.memory_map("2010-01-01".into(), "2010-12-31".into()).unwrap().drive_count, 0);
    }
}

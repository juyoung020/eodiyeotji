//! 지도에 넘길 하루치 데이터 (GeoJSON 문자열 + 화면 맞춤 범위).
//!
//! Kotlin은 이 문자열을 MapLibre GeoJSON 소스에 그대로 넣기만 한다 (점 목록을 Kotlin에서 가공하지 않는다).
//! 경로 점은 이미 `geo::simplify`(5m)로 줄인 것이라 하루치도 수백~수천 점이다.
//!
//! # GeoJSON 형식 (FeatureCollection)
//! - 주행: `LineString`, properties `{"kind":"trip","key":"t<start_ms>","start_ms":..,"end_ms":..,"distance_m":..}`
//! - 머문 곳: `Point`, properties `{"kind":"stay","key":"s<arrive_ms>","arrive_ms":..,"leave_ms":..,"open":true|false}`
//! - `key`는 앱의 타임라인 항목 키와 같다 → 항목을 누르면 지도에서 `key`로 골라 강조한다.
//! - 좌표는 GeoJSON 규칙대로 `[경도, 위도]`, 소수 6자리(약 0.1m).

use std::fmt::Write;

use crate::store::{DayDetail, StaySummary, TripSummary};

/// 지도 화면 맞춤 범위 (모든 경로 점과 머문 곳을 포함).
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct MapBounds {
    pub min_lat: f64,
    pub min_lon: f64,
    pub max_lat: f64,
    pub max_lon: f64,
}

/// 항목 하나(주행·머문 곳)의 범위. 타임라인에서 항목을 누르면 지도를 이 범위로 옮긴다.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct EntryBounds {
    /// 타임라인 항목 키 (GeoJSON properties.key와 같다)
    pub key: String,
    pub bounds: MapBounds,
}

/// 하루치 지도 데이터.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DayMap {
    pub day: String,
    /// GeoJSON FeatureCollection 문자열 (형식은 모듈 설명).
    pub geojson: String,
    /// 그릴 것이 없으면 None.
    pub bounds: Option<MapBounds>,
    /// 항목별 범위 (주행 → 머문 곳 순서).
    pub entries: Vec<EntryBounds>,
}

/// 하루 상세를 지도 데이터로 바꾼다.
pub fn day_map(d: &DayDetail) -> DayMap {
    let mut out = String::with_capacity(64 + d.trips.iter().map(|t| t.path.len() * 26 + 160).sum::<usize>());
    let mut b = Bounds::default();
    let mut entries = Vec::with_capacity(d.trips.len() + d.stays.len());
    out.push_str(r#"{"type":"FeatureCollection","features":["#);
    let mut first = true;
    for t in &d.trips {
        sep(&mut out, &mut first);
        let mut eb = Bounds::default();
        trip_feature(&mut out, t, &mut eb);
        if let Some(r) = eb.get() {
            b.merge(r);
            entries.push(EntryBounds { key: format!("t{}", t.start_ms), bounds: r });
        }
    }
    for s in &d.stays {
        sep(&mut out, &mut first);
        let mut eb = Bounds::default();
        stay_feature(&mut out, s, &mut eb);
        if let Some(r) = eb.get() {
            b.merge(r);
            entries.push(EntryBounds { key: format!("s{}", s.arrive_ms), bounds: r });
        }
    }
    out.push_str("]}");
    DayMap { day: d.day.clone(), geojson: out, bounds: b.get(), entries }
}

fn sep(out: &mut String, first: &mut bool) {
    if !*first {
        out.push(',');
    }
    *first = false;
}

fn trip_feature(out: &mut String, t: &TripSummary, b: &mut Bounds) {
    // 경로 점이 1개뿐이면 LineString이 안 되므로 출발·도착을 쓴다.
    let pts: Vec<(f64, f64)> = if t.path.len() >= 2 {
        t.path.iter().map(|p| (p.lat, p.lon)).collect()
    } else {
        vec![(t.start.lat, t.start.lon), (t.end.lat, t.end.lon)]
    };
    out.push_str(r#"{"type":"Feature","geometry":{"type":"LineString","coordinates":["#);
    for (i, &(lat, lon)) in pts.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        coord(out, lat, lon);
        b.add(lat, lon);
    }
    let _ = write!(
        out,
        r#"]}},"properties":{{"kind":"trip","key":"t{}","start_ms":{},"end_ms":{},"distance_m":{:.1}}}}}"#,
        t.start_ms, t.start_ms, t.end_ms, t.distance_m
    );
}

fn stay_feature(out: &mut String, s: &StaySummary, b: &mut Bounds) {
    out.push_str(r#"{"type":"Feature","geometry":{"type":"Point","coordinates":"#);
    coord(out, s.center.lat, s.center.lon);
    b.add(s.center.lat, s.center.lon);
    let _ = write!(
        out,
        r#"}},"properties":{{"kind":"stay","key":"s{}","arrive_ms":{},"leave_ms":{},"open":{}}}}}"#,
        s.arrive_ms,
        s.arrive_ms,
        s.leave_ms,
        s.leave_ms == s.arrive_ms
    );
}

fn coord(out: &mut String, lat: f64, lon: f64) {
    let _ = write!(out, "[{lon:.6},{lat:.6}]");
}

#[derive(Default)]
struct Bounds(Option<MapBounds>);

impl Bounds {
    fn add(&mut self, lat: f64, lon: f64) {
        let b = self.0.get_or_insert(MapBounds { min_lat: lat, min_lon: lon, max_lat: lat, max_lon: lon });
        b.min_lat = b.min_lat.min(lat);
        b.min_lon = b.min_lon.min(lon);
        b.max_lat = b.max_lat.max(lat);
        b.max_lon = b.max_lon.max(lon);
    }

    fn merge(&mut self, r: MapBounds) {
        self.add(r.min_lat, r.min_lon);
        self.add(r.max_lat, r.max_lon);
    }

    fn get(self) -> Option<MapBounds> {
        self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ffi::GeoPoint;
    use crate::store::DaySummary;

    fn p(lat: f64, lon: f64) -> GeoPoint {
        GeoPoint { lat, lon }
    }

    fn detail(trips: Vec<TripSummary>, stays: Vec<StaySummary>) -> DayDetail {
        DayDetail {
            day: "2026-11-05".into(),
            trips,
            stays,
            summary: DaySummary {
                day: "2026-11-05".into(),
                trip_count: 0,
                stay_count: 0,
                distance_m: 0.0,
                drive_millis: 0,
                first_ms: 0,
                last_ms: 0,
            },
            entries: Vec::new(),
        }
    }

    #[test]
    fn empty_day_has_empty_collection_and_no_bounds() {
        let m = day_map(&detail(vec![], vec![]));
        assert_eq!(m.geojson, r#"{"type":"FeatureCollection","features":[]}"#);
        assert_eq!(m.bounds, None);
    }

    #[test]
    fn trips_become_lines_and_stays_become_points_with_timeline_keys() {
        let trip = TripSummary {
            start_ms: 1000,
            end_ms: 5000,
            distance_m: 1234.56,
            start: p(37.5, 127.0),
            end: p(37.6, 127.1),
            path: vec![p(37.5, 127.0), p(37.55, 127.02), p(37.6, 127.1)],
        };
        let stay = StaySummary { center: p(37.6, 127.1), arrive_ms: 5000, leave_ms: 5000 };
        let m = day_map(&detail(vec![trip], vec![stay]));
        assert!(m.geojson.contains(r#""coordinates":[[127.000000,37.500000],[127.020000,37.550000],[127.100000,37.600000]]"#));
        assert!(m.geojson.contains(r#""kind":"trip","key":"t1000","start_ms":1000,"end_ms":5000,"distance_m":1234.6"#));
        assert!(m.geojson.contains(r#""type":"Point","coordinates":[127.100000,37.600000]"#));
        assert!(m.geojson.contains(r#""kind":"stay","key":"s5000","arrive_ms":5000,"leave_ms":5000,"open":true"#));
        assert_eq!(m.bounds, Some(MapBounds { min_lat: 37.5, min_lon: 127.0, max_lat: 37.6, max_lon: 127.1 }));
        assert_eq!(m.entries.len(), 2);
        assert_eq!(m.entries[0].key, "t1000");
        assert_eq!(m.entries[0].bounds, MapBounds { min_lat: 37.5, min_lon: 127.0, max_lat: 37.6, max_lon: 127.1 });
        assert_eq!(m.entries[1].key, "s5000");
        assert_eq!(m.entries[1].bounds, MapBounds { min_lat: 37.6, min_lon: 127.1, max_lat: 37.6, max_lon: 127.1 });
        // 괄호 짝이 맞는지 (간단한 형식 검사)
        assert_eq!(m.geojson.matches('{').count(), m.geojson.matches('}').count());
        assert_eq!(m.geojson.matches('[').count(), m.geojson.matches(']').count());
    }

    #[test]
    fn single_point_path_still_draws_a_line() {
        let trip = TripSummary {
            start_ms: 1,
            end_ms: 2,
            distance_m: 0.0,
            start: p(37.0, 127.0),
            end: p(37.0, 127.0),
            path: vec![p(37.0, 127.0)],
        };
        let m = day_map(&detail(vec![trip], vec![]));
        assert!(m.geojson.contains(r#""coordinates":[[127.000000,37.000000],[127.000000,37.000000]]"#));
    }

    #[test]
    fn a_day_of_ten_thousand_simplified_points_is_fast() {
        let path: Vec<GeoPoint> = (0..10_000).map(|i| p(37.0 + i as f64 * 1e-5, 127.0 + (i % 7) as f64 * 1e-5)).collect();
        let trip = TripSummary { start_ms: 0, end_ms: 1, distance_m: 0.0, start: path[0], end: path[9_999], path };
        let t = std::time::Instant::now();
        let m = day_map(&detail(vec![trip], vec![]));
        let ms = t.elapsed().as_secs_f64() * 1000.0;
        assert!(m.geojson.len() > 200_000);
        assert!(ms < 50.0, "10k points took {ms:.2} ms (debug build)");
    }
}

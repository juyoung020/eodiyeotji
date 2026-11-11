//! 지도에 넘길 하루치 데이터 (GeoJSON 문자열 + 화면 맞춤 범위).
//!
//! Kotlin은 이 문자열을 MapLibre GeoJSON 소스에 그대로 넣기만 한다 (점 목록을 Kotlin에서 가공하지 않는다).
//! 경로 점은 이미 `geo::simplify`(5m)로 줄인 것이라 하루치도 수백~수천 점이다.
//!
//! # GeoJSON 형식 (FeatureCollection)
//! - 주행: `LineString`, properties `{"kind":"trip","key":"t<start_ms>","start_ms":..,"end_ms":..,"distance_m":..}`
//! - 머문 곳: `Point`, properties `{"kind":"stay","dwell":..,("tag":..,)"key":"s<arrive_ms>","arrive_ms":..,"leave_ms":..,"open":true|false}`
//! - 장소 이름(`places` 모듈): 머문 곳 properties에 `"order"`(그날 들른 곳 순서, 도착 시각순 1부터 — 들른 곳
//!   [`crate::store::visit_indices`]에만 있고, 그날 출발 · 도착 머문 곳(집)에는 없다)와
//!   `"name"`(장소 이름, 모르면 키 자체가 없음), 주행 properties에 `"from"`·`"to"`(출발·도착 장소 이름, 모르면 없음).
//!   지도가 핀 옆에 이름·순서를 띄운다.
//!   이름은 지도 글꼴로 그릴 수 있는 글자만 남긴 것이다 ([`map_safe_name`]; 거르고 남는 게 없으면 키가 없다).
//!   화면에 쓸 전체 이름은 `day_detail`의 `PlaceLabel`에 그대로 있다.
//! - 화면 표시용 글(지도는 이 글을 그대로 그린다, 앱에서 계산하지 않는다):
//!   - 머문 곳 properties 앞쪽(`kind` 바로 뒤)에 `"dwell"`(정차 시간 "18분 정차" / "1시간 5분 정차" / "잠깐 정차",
//!     아직 머무는 곳은 "지금 여기", 그날의 출발·도착 머문 곳은 밤새 머문 시간이 섞이므로 빈 글)과, 그날 경로의 출발·도착 지점이면 `"tag":"출발"|"도착"|"출발·도착"` (`RouteEnds`).
//!   - 주행 가운데 지점(거리 기준)에 `Point`, properties `{"kind":"trip_label","key":"t<start_ms>","label":"12분 · 4.9km"}`
//!     (200m 미만 주행은 없음). `key`가 주행과 같아 눌러서 그 주행을 고를 수 있다.
//!   - 출발·도착 지점에 머문 곳이 없으면 `Point`, properties
//!     `{"kind":"endpoint","role":"start"|"end","key":"t<start_ms>","label":"출발"|"도착"}` (그 주행 키).
//!   - 순서: 주행 → 주행 가운데 글 → 출발·도착 → 머문 곳.
//! - `key`는 앱의 타임라인 항목 키와 같다 → 항목을 누르면 지도에서 `key`로 골라 강조한다.
//! - 좌표는 GeoJSON 규칙대로 `[경도, 위도]`, 소수 6자리(약 0.1m).

use std::fmt::Write;

use crate::geo::{haversine_m, Point};
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
    for t in &d.trips {
        if let Some((lat, lon)) = trip_midpoint(t) {
            sep(&mut out, &mut first);
            out.push_str(r#"{"type":"Feature","geometry":{"type":"Point","coordinates":"#);
            coord(&mut out, lat, lon);
            let _ = write!(out, r#"}},"properties":{{"kind":"trip_label","key":"t{}","label":"{}"}}}}"#, t.start_ms, trip_label(t));
        }
    }
    let ends = route_ends(d);
    for (role, label, t, at_stay) in [("start", "출발", d.trips.first(), ends.start_stay), ("end", "도착", d.trips.last(), ends.end_stay)] {
        let (Some(t), None) = (t, at_stay) else { continue };
        let pts = trip_points(t);
        let (lat, lon) = if role == "start" { pts[0] } else { pts[pts.len() - 1] };
        sep(&mut out, &mut first);
        out.push_str(r#"{"type":"Feature","geometry":{"type":"Point","coordinates":"#);
        coord(&mut out, lat, lon);
        let _ = write!(out, r#"}},"properties":{{"kind":"endpoint","role":"{role}","key":"t{}","label":"{label}"}}}}"#, t.start_ms);
    }
    // 핀 번호는 들른 곳에만 1부터 (하루 상세 "들른 곳 4곳" · 그날로 돌아가기 "세 번째 들른 곳"과 같은 기준, `store::visit_indices`).
    // 그날 출발 · 도착 머문 곳(대개 집)까지 세면 들른 곳이 4곳인 날 집 핀에 "5"가 붙었다.
    let visits = crate::store::visit_indices(&d.trips, &d.stays);
    for (i, s) in d.stays.iter().enumerate() {
        sep(&mut out, &mut first);
        let mut eb = Bounds::default();
        let order = visits.iter().position(|&v| v == i).map(|k| k as u32 + 1);
        stay_feature(&mut out, s, end_tag(&ends, i), order, &mut eb);
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

/// 지도에 그릴 주행 점 (위도, 경도). 경로 점이 1개뿐이면 LineString이 안 되므로 출발·도착을 쓴다.
pub(crate) fn trip_points(t: &TripSummary) -> Vec<(f64, f64)> {
    if t.path.len() >= 2 {
        t.path.iter().map(|p| (p.lat, p.lon)).collect()
    } else {
        vec![(t.start.lat, t.start.lon), (t.end.lat, t.end.lon)]
    }
}

/// 그날 경로의 출발·도착을 어느 머문 곳에 글("출발"/"도착")로 붙일지 (머문 곳 순번 index).
/// - 첫 주행 전에 도착한 머문 곳이 출발 지점, 마지막 주행이 시작된 뒤 도착한 머문 곳이 도착 지점이다.
/// - 그런 머문 곳이 없어도 출발·도착 지점에서 `END_NEAR_M` 안에 머문 곳이 있으면 거기에 붙인다
///   (집에서 나가 집으로 돌아온 날: 마지막 머문 곳이 "출발·도착"). 깃발이 그 머문 곳 카드와 겹치지 않는다.
/// - 붙일 곳이 없을 때만 깃발(`endpoint`)을 따로 그린다. 주행이 없으면 경로가 없으므로 둘 다 없다.
#[derive(Debug, Default, PartialEq)]
pub(crate) struct RouteEnds {
    pub(crate) start_stay: Option<usize>,
    pub(crate) end_stay: Option<usize>,
}

/// 출발·도착 지점과 이만큼(m) 가까운 머문 곳은 같은 곳으로 본다.
const END_NEAR_M: f64 = 300.0;

pub(crate) fn route_ends(d: &DayDetail) -> RouteEnds {
    route_ends_of(&d.trips, &d.stays)
}

/// [route_ends]를 주행·머문 곳 목록으로 (하루 요약의 들른 곳 수도 같은 규칙, `store::visit_count`).
pub(crate) fn route_ends_of(trips: &[TripSummary], stays: &[StaySummary]) -> RouteEnds {
    let (Some(first), Some(last)) = (trips.first(), trips.last()) else { return RouteEnds::default() };
    let nearest = |at: (f64, f64)| {
        stays
            .iter()
            .enumerate()
            .map(|(i, s)| (i, haversine_m(pt(at), pt((s.center.lat, s.center.lon)))))
            .filter(|&(_, m)| m <= END_NEAR_M)
            .min_by(|a, b| a.1.total_cmp(&b.1))
            .map(|(i, _)| i)
    };
    let (first_pts, last_pts) = (trip_points(first), trip_points(last));
    let start_stay = stays.iter().position(|s| s.arrive_ms < first.start_ms).or_else(|| nearest(first_pts[0]));
    let end_stay = stays.iter().rposition(|s| s.arrive_ms >= last.start_ms).or_else(|| nearest(last_pts[last_pts.len() - 1]));
    RouteEnds { start_stay, end_stay }
}

/// 머문 곳에 붙일 출발·도착 글.
pub(crate) fn end_tag(ends: &RouteEnds, i: usize) -> Option<&'static str> {
    match (ends.start_stay == Some(i), ends.end_stay == Some(i)) {
        (true, true) => Some("출발·도착"),
        (true, false) => Some("출발"),
        (false, true) => Some("도착"),
        (false, false) => None,
    }
}

/// 주행 경로를 거리로 반 가른 지점 (위도, 경도). 200m 미만 주행은 글을 달지 않는다(None).
fn trip_midpoint(t: &TripSummary) -> Option<(f64, f64)> {
    if t.distance_m < TRIP_LABEL_MIN_M {
        return None;
    }
    let pts = trip_points(t);
    let seg: Vec<f64> = pts.windows(2).map(|w| haversine_m(pt(w[0]), pt(w[1]))).collect();
    let total: f64 = seg.iter().sum();
    if total <= 0.0 {
        return Some(pts[0]);
    }
    let mut left = total / 2.0;
    for (w, len) in pts.windows(2).zip(&seg) {
        if left <= *len {
            let f = if *len > 0.0 { left / len } else { 0.0 };
            return Some((w[0].0 + (w[1].0 - w[0].0) * f, w[0].1 + (w[1].1 - w[0].1) * f));
        }
        left -= len;
    }
    pts.last().copied()
}

fn pt((lat, lon): (f64, f64)) -> Point {
    Point { lat, lon }
}

/// 이 거리(m)보다 짧은 주행에는 가운데 글을 달지 않는다 (핀끼리 붙어 있어 글이 핀을 가린다).
const TRIP_LABEL_MIN_M: f64 = 200.0;

/// 걸린 시간 글: "18분", "1시간", "1시간 5분" (반올림한 분). 1분 미만은 None.
pub(crate) fn duration_text(ms: i64) -> Option<String> {
    let min = (ms.max(0) + 30_000) / 60_000;
    match (min / 60, min % 60) {
        (0, 0) => None,
        (0, m) => Some(format!("{m}분")),
        (h, 0) => Some(format!("{h}시간")),
        (h, m) => Some(format!("{h}시간 {m}분")),
    }
}

/// 거리 글: "850m", "4.9km", "123km" (100km부터는 소수점 없이).
pub(crate) fn distance_text(m: f64) -> String {
    if m < 1000.0 {
        format!("{}m", ((m / 10.0).round() * 10.0) as i64)
    } else if m < 100_000.0 {
        format!("{:.1}km", m / 1000.0)
    } else {
        format!("{:.0}km", m / 1000.0)
    }
}

/// 주행 가운데 글: "12분 · 4.9km" (1분 미만이면 거리만).
fn trip_label(t: &TripSummary) -> String {
    let dist = distance_text(t.distance_m);
    match duration_text(t.end_ms - t.start_ms) {
        Some(d) => format!("{d} · {dist}"),
        None => dist,
    }
}

/// 머문 곳 정차 글: 아직 머무는 곳은 "지금 여기", 1분 미만은 "잠깐 정차", 그 밖에는 "18분 정차".
fn dwell_text(s: &StaySummary) -> String {
    if s.leave_ms == s.arrive_ms {
        return "지금 여기".into();
    }
    match duration_text(s.leave_ms - s.arrive_ms) {
        Some(d) => format!("{d} 정차"),
        None => "잠깐 정차".into(),
    }
}

fn trip_feature(out: &mut String, t: &TripSummary, b: &mut Bounds) {
    let pts = trip_points(t);
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
        r#"]}},"properties":{{"kind":"trip","key":"t{}","start_ms":{},"end_ms":{},"distance_m":{:.1}"#,
        t.start_ms, t.start_ms, t.end_ms, t.distance_m
    );
    if let Some(p) = &t.start_place {
        str_prop(out, "from", &p.name);
    }
    if let Some(p) = &t.end_place {
        str_prop(out, "to", &p.name);
    }
    out.push_str("}}");
}

fn stay_feature(out: &mut String, s: &StaySummary, tag: Option<&str>, order: Option<u32>, b: &mut Bounds) {
    out.push_str(r#"{"type":"Feature","geometry":{"type":"Point","coordinates":"#);
    coord(out, s.center.lat, s.center.lon);
    b.add(s.center.lat, s.center.lon);
    // 그날의 출발·도착 머문 곳(대개 집)은 밤새 머문 시간까지 세져 "63시간 정차"처럼 보이므로 정차 글을 비운다 (지금 여기는 그대로).
    let dwell = if tag.is_some() && s.leave_ms != s.arrive_ms { String::new() } else { dwell_text(s) };
    let _ = write!(out, r#"}},"properties":{{"kind":"stay","dwell":"{dwell}","#);
    if let Some(tag) = tag {
        let _ = write!(out, r#""tag":"{tag}","#);
    }
    let _ = write!(
        out,
        r#""key":"s{}","arrive_ms":{},"leave_ms":{},"open":{}"#,
        s.arrive_ms,
        s.arrive_ms,
        s.leave_ms,
        s.leave_ms == s.arrive_ms,
    );
    if let Some(n) = order {
        let _ = write!(out, r#","order":{n}"#);
    }
    if let Some(p) = &s.place {
        str_prop(out, "name", &p.name);
    }
    out.push_str("}}");
}

/// 지도 글꼴로 그릴 수 있는 글자인지. 그릴 수 없는 글자가 섞이면 MapLibre가 그 타일 영역 글자를 통째로 안 그린다.
/// 한글(음절·자모·호환 자모), 한자(CJK 통합·확장 A·호환), ASCII 출력 문자, 라틴-1 글자, 가운뎃점(·).
fn map_drawable(c: char) -> bool {
    matches!(c as u32,
        0x20..=0x7E
        | 0xB7
        | 0xC0..=0xD6 | 0xD8..=0xF6 | 0xF8..=0xFF
        | 0x1100..=0x11FF | 0x3130..=0x318F | 0xAC00..=0xD7A3
        | 0x3400..=0x4DBF | 0x4E00..=0x9FFF | 0xF900..=0xFAFF)
}

/// 지도에 띄울 이름: 그릴 수 없는 글자(★ ♡ ① Ⅱ ℃ 이모지 키릴 문자 등)는 공백으로 바꾸고, 공백을 하나로 줄이고
/// 앞뒤 공백을 지운다. 남는 게 없으면 None (그 속성을 빼다). 화면용 전체 이름은 `day_detail`에 그대로 있다.
pub fn map_safe_name(name: &str) -> Option<String> {
    let mut out = String::with_capacity(name.len());
    for c in name.chars().map(|c| if map_drawable(c) { c } else { ' ' }) {
        if c == ' ' && (out.is_empty() || out.ends_with(' ')) {
            continue;
        }
        out.push(c);
    }
    let len = out.trim_end().len();
    out.truncate(len);
    (!out.is_empty()).then_some(out)
}

/// `,"key":"값"` — 값은 지도용으로 거른 이름(`map_safe_name`)을 JSON 문자열로 이스케이프한 것. 거르고 남는 게 없으면 안 쓴다.
pub(crate) fn str_prop(out: &mut String, key: &str, name: &str) {
    let Some(v) = map_safe_name(name) else { return };
    let _ = write!(out, r#","{key}":""#);
    // 제어 문자는 map_safe_name이 이미 걸렀으므로 따옴표·역슬래시만 이스케이프하면 된다.
    for c in v.chars() {
        if c == '"' || c == '\\' {
            out.push('\\');
        }
        out.push(c);
    }
    out.push('"');
}

pub(crate) fn coord(out: &mut String, lat: f64, lon: f64) {
    let _ = write!(out, "[{lon:.6},{lat:.6}]");
}

#[derive(Default)]
pub(crate) struct Bounds(Option<MapBounds>);

impl Bounds {
    pub(crate) fn add(&mut self, lat: f64, lon: f64) {
        let b = self.0.get_or_insert(MapBounds { min_lat: lat, min_lon: lon, max_lat: lat, max_lon: lon });
        b.min_lat = b.min_lat.min(lat);
        b.min_lon = b.min_lon.min(lon);
        b.max_lat = b.max_lat.max(lat);
        b.max_lon = b.max_lon.max(lon);
    }

    pub(crate) fn merge(&mut self, r: MapBounds) {
        self.add(r.min_lat, r.min_lon);
        self.add(r.max_lat, r.max_lon);
    }

    pub(crate) fn get(self) -> Option<MapBounds> {
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
                replayable: false,
                day: "2026-11-05".into(),
                trip_count: 0,
                stay_count: 0,
                distance_m: 0.0,
                drive_millis: 0,
                first_ms: 0,
                last_ms: 0,
                stop_names: Vec::new(),
                title: None,
                tags: Vec::new(),
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
            start_place: None,
            end_place: None,
        };
        let stay = StaySummary { center: p(37.6, 127.1), arrive_ms: 5000, leave_ms: 5000, place: None, order: 1 };
        let m = day_map(&detail(vec![trip], vec![stay]));
        assert!(m.geojson.contains(r#""coordinates":[[127.000000,37.500000],[127.020000,37.550000],[127.100000,37.600000]]"#));
        assert!(m.geojson.contains(r#""kind":"trip","key":"t1000","start_ms":1000,"end_ms":5000,"distance_m":1234.6"#));
        assert!(m.geojson.contains(r#""type":"Point","coordinates":[127.100000,37.600000]"#));
        assert!(m.geojson.contains(r#""kind":"stay","dwell":"지금 여기","tag":"도착","key":"s5000","arrive_ms":5000,"leave_ms":5000,"open":true"#));
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
            start_place: None,
            end_place: None,
        };
        let m = day_map(&detail(vec![trip], vec![]));
        assert!(m.geojson.contains(r#""coordinates":[[127.000000,37.000000],[127.000000,37.000000]]"#));
    }

    fn trip(start_ms: i64, end_ms: i64, distance_m: f64, path: Vec<GeoPoint>) -> TripSummary {
        TripSummary {
            start_ms,
            end_ms,
            distance_m,
            start: path[0],
            end: path[path.len() - 1],
            path,
            start_place: None,
            end_place: None,
        }
    }

    fn stay(lat: f64, lon: f64, arrive_ms: i64, leave_ms: i64, order: u32) -> StaySummary {
        StaySummary { center: p(lat, lon), arrive_ms, leave_ms, place: None, order }
    }

    const MIN: i64 = 60_000;

    #[test]
    fn display_texts_for_durations_and_distances() {
        assert_eq!(duration_text(29_999), None);
        assert_eq!(duration_text(30_000).as_deref(), Some("1분"));
        assert_eq!(duration_text(18 * MIN).as_deref(), Some("18분"));
        assert_eq!(duration_text(60 * MIN).as_deref(), Some("1시간"));
        assert_eq!(duration_text(65 * MIN + 20_000).as_deref(), Some("1시간 5분"));
        assert_eq!(duration_text(-5), None);
        assert_eq!(distance_text(847.0), "850m");
        assert_eq!(distance_text(4_920.0), "4.9km");
        assert_eq!(distance_text(123_456.0), "123km");
        assert_eq!(dwell_text(&stay(0.0, 0.0, 0, 18 * MIN, 1)), "18분 정차");
        assert_eq!(dwell_text(&stay(0.0, 0.0, 0, 20_000, 1)), "잠깐 정차");
        assert_eq!(dwell_text(&stay(0.0, 0.0, 5, 5, 1)), "지금 여기");
        assert_eq!(trip_label(&trip(0, 12 * MIN, 4_900.0, vec![p(37.0, 127.0), p(37.1, 127.0)])), "12분 · 4.9km");
        assert_eq!(trip_label(&trip(0, 10_000, 250.0, vec![p(37.0, 127.0), p(37.1, 127.0)])), "250m");
    }

    #[test]
    fn trip_label_sits_halfway_along_the_path_by_distance() {
        // 북쪽으로 3km 갔다가 동쪽으로 1km: 가운데(2km 지점)는 첫 구간 위
        let t = trip(0, 10 * MIN, 4_000.0, vec![p(37.0, 127.0), p(37.027, 127.0), p(37.027, 127.0113)]);
        let (lat, lon) = trip_midpoint(&t).unwrap();
        let total = haversine_m(pt((37.0, 127.0)), pt((37.027, 127.0))) + haversine_m(pt((37.027, 127.0)), pt((37.027, 127.0113)));
        let walked = haversine_m(pt((37.0, 127.0)), pt((lat, lon)));
        assert!((walked - total / 2.0).abs() < 1.0, "walked {walked} of {total}");
        assert_eq!(lon, 127.0);
        // 짧은 주행은 글이 없다
        assert_eq!(trip_midpoint(&trip(0, MIN, 150.0, vec![p(37.0, 127.0), p(37.001, 127.0)])), None);
        // 점이 다 같으면 그 점
        assert_eq!(trip_midpoint(&trip(0, MIN, 500.0, vec![p(37.0, 127.0), p(37.0, 127.0)])), Some((37.0, 127.0)));
    }

    #[test]
    fn route_start_and_end_are_tagged_on_stays_or_drawn_as_flags() {
        // 집(머문 곳) → 주행 → 카페(머문 곳) → 주행 → 끝 (도착 지점에 머문 곳 없음)
        let t1 = trip(10 * MIN, 30 * MIN, 5_000.0, vec![p(37.0, 127.0), p(37.04, 127.0)]);
        let t2 = trip(60 * MIN, 80 * MIN, 6_000.0, vec![p(37.04, 127.0), p(37.04, 127.07)]);
        let home = stay(37.0, 127.0, 0, 10 * MIN, 1);
        let cafe = stay(37.04, 127.0, 30 * MIN, 60 * MIN, 2);
        let d = detail(vec![t1, t2], vec![home, cafe]);
        assert_eq!(route_ends(&d), RouteEnds { start_stay: Some(0), end_stay: None });
        let m = day_map(&d);
        assert!(m.geojson.contains(r#""kind":"stay","dwell":"","tag":"출발","key":"s0""#), "{}", m.geojson);
        assert!(m.geojson.contains(r#""kind":"stay","dwell":"30분 정차","key":"s1800000""#));
        assert!(!m.geojson.contains(r#""role":"start""#), "출발은 머문 곳 글로 붙었다");
        assert!(m.geojson.contains(
            r#"{"type":"Point","coordinates":[127.070000,37.040000]},"properties":{"kind":"endpoint","role":"end","key":"t3600000","label":"도착"}"#
        ));
        assert!(m.geojson.contains(r#""kind":"trip_label","key":"t600000","label":"20분 · 5.0km""#));
        assert_eq!(m.geojson.matches(r#""kind":"trip_label""#).count(), 2);
        // 기존 키·범위는 그대로 (주행 2 + 머문 곳 2)
        assert_eq!(m.entries.len(), 4);
        assert_eq!(m.geojson.matches('{').count(), m.geojson.matches('}').count());

        // 주행만 있는 날: 출발·도착 깃발 둘 다, 주행이 없는 날: 깃발·글 없음
        let only = day_map(&detail(vec![trip(0, MIN, 900.0, vec![p(37.0, 127.0), p(37.008, 127.0)])], vec![]));
        assert!(only.geojson.contains(r#""role":"start","key":"t0","label":"출발""#));
        assert!(only.geojson.contains(r#""role":"end","key":"t0","label":"도착""#));
        let stays_only = day_map(&detail(vec![], vec![stay(37.0, 127.0, 0, MIN, 1)]));
        assert!(!stays_only.geojson.contains("tag") && !stays_only.geojson.contains("endpoint"));
    }

    #[test]
    fn a_round_trip_from_home_tags_the_home_stay_instead_of_a_flag() {
        // 아침에 집 근처(약 100m)에서 출발 → 바다(머묾) → 집으로 돌아와 머묾: 집이 "출발·도착", 깃발 없음
        let out = trip(0, 60 * MIN, 50_000.0, vec![p(37.0009, 127.0), p(37.4, 127.3)]);
        let back = trip(120 * MIN, 180 * MIN, 50_000.0, vec![p(37.4, 127.3), p(37.0, 127.0)]);
        let sea = stay(37.4, 127.3, 60 * MIN, 120 * MIN, 1);
        let home = stay(37.0, 127.0, 180 * MIN, 900 * MIN, 2);
        let d = detail(vec![out, back], vec![sea, home]);
        assert_eq!(route_ends(&d), RouteEnds { start_stay: Some(1), end_stay: Some(1) });
        let m = day_map(&d);
        assert!(m.geojson.contains(r#""dwell":"","tag":"출발·도착","key":"s10800000""#), "{}", m.geojson);
        assert!(m.geojson.contains(r#""kind":"stay","dwell":"1시간 정차","key":"s3600000""#));
        assert!(!m.geojson.contains("endpoint"));

        // 출발 지점이 300m보다 멀면 깃발
        let far = trip(0, 60 * MIN, 50_000.0, vec![p(37.004, 127.0), p(37.4, 127.3)]);
        let d = detail(vec![far], vec![stay(37.0, 127.0, 180 * MIN, 900 * MIN, 1)]);
        assert_eq!(route_ends(&d), RouteEnds { start_stay: None, end_stay: Some(0) });
    }

    #[test]
    fn only_visited_places_get_pin_numbers() {
        // 집에서 출발 → 바다 → 식당 → 집 (2025-11-07 강릉 날처럼): 들른 곳 2곳만 1 · 2번, 집(출발·도착)엔 번호가 없다
        let t1 = trip(0, 60 * MIN, 50_000.0, vec![p(37.0, 127.0), p(37.4, 127.3)]);
        let t2 = trip(120 * MIN, 130 * MIN, 2_000.0, vec![p(37.4, 127.3), p(37.41, 127.31)]);
        let t3 = trip(190 * MIN, 250 * MIN, 50_000.0, vec![p(37.41, 127.31), p(37.0, 127.0)]);
        let sea = stay(37.4, 127.3, 60 * MIN, 120 * MIN, 1);
        let food = stay(37.41, 127.31, 130 * MIN, 190 * MIN, 2);
        let home = stay(37.0, 127.0, 250 * MIN, 900 * MIN, 3);
        let d = detail(vec![t1, t2, t3], vec![sea, food, home]);
        assert_eq!(crate::store::visit_count(&d.trips, &d.stays), 2);
        let m = day_map(&d);
        assert!(m.geojson.contains(r#""key":"s3600000","arrive_ms":3600000,"leave_ms":7200000,"open":false,"order":1"#), "{}", m.geojson);
        assert!(m.geojson.contains(r#""key":"s7800000","arrive_ms":7800000,"leave_ms":11400000,"open":false,"order":2"#), "{}", m.geojson);
        assert!(m.geojson.contains(r#""tag":"출발·도착","key":"s15000000","arrive_ms":15000000,"leave_ms":54000000,"open":false}"#), "{}", m.geojson);
        assert_eq!(m.geojson.matches(r#""order":"#).count(), 2);
    }

    #[test]
    fn a_day_of_ten_thousand_simplified_points_is_fast() {
        let path: Vec<GeoPoint> = (0..10_000).map(|i| p(37.0 + i as f64 * 1e-5, 127.0 + (i % 7) as f64 * 1e-5)).collect();
        let trip = TripSummary {
            start_ms: 0,
            end_ms: 1,
            distance_m: 0.0,
            start: path[0],
            end: path[9_999],
            path,
            start_place: None,
            end_place: None,
        };
        let t = std::time::Instant::now();
        let m = day_map(&detail(vec![trip], vec![]));
        let ms = t.elapsed().as_secs_f64() * 1000.0;
        assert!(m.geojson.len() > 200_000);
        assert!(ms < 50.0, "10k points took {ms:.2} ms (debug build)");
    }
}

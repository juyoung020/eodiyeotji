//! 에이전트 후보 카드 지도의 핀 번호를 카드 사진 줄 번호와 맞춘다.
//!
//! 하루 지도(`mapdata::day_map`)의 머문 곳 `order`는 그날 전체에서 도착한 순서(집·다른 나들이 포함)라,
//! 카드 아래 들른 곳 사진 줄(코스 순서 또는 그 나들이에서 들른 순서)과 번호가 어긋난다
//! (을왕리: 지도 1 = 을왕리해수욕장, 사진 1 = 조개구이).
//! [number_stops]는 지도 GeoJSON의 머문 곳 번호를 사진 줄 순서(1부터)로 다시 매긴다.
//! - 사진 줄에 없는 머문 곳(집, 같은 날 다른 나들이)은 번호를 뺀다 (지도는 번호 없는 핀으로 그린다).
//! - 사진 줄에는 있는데 지도에 머문 곳이 없는 곳(오래 머물지 않은 마지막 도착지)은 번호 핀을 더한다 → 번호가 빠지지 않는다.
//!
//! 한 문장 코스("순두부집만 빼고")로 [skipped]가 있으면:
//! - 뺀 곳의 머문 곳에 `"skipped":true`, 정차 글 대신 "빼고 가요" (지도는 회색 핀 · 흐린 카드로 그린다).
//! - 새 코스 순서대로 들른 곳을 잇는 선 `{"kind":"plan"}`을 더하고, 그날 실제로 달린 주행 선에는 `"faded":true`를 붙인다.

use serde_json::{json, Map, Value};

use crate::geo::{haversine_m, Point};
use crate::mapdata::{map_safe_name, MapBounds};
use crate::memory::StopFact;

/// 한 문장 코스 지도를 처음 비출 범위: 뺀 곳마다 그곳과 그날 바로 앞 · 뒤에 들른 곳 (남은 코스 [stops] 중 도착 시각으로).
/// 하루 전체(서울 ↔ 강릉)를 비추면 뺀 곳이 점처럼 작아 보이지 않는다. 뺀 곳이 없으면 None.
pub fn skip_focus(stops: &[StopFact], skipped: &[StopFact]) -> Option<MapBounds> {
    let mut pts: Vec<(f64, f64)> = Vec::new();
    for s in skipped {
        pts.push((s.lat, s.lon));
        if let Some(p) = stops.iter().filter(|x| x.arrive_ms < s.arrive_ms).max_by_key(|x| x.arrive_ms) {
            pts.push((p.lat, p.lon));
        }
        if let Some(n) = stops.iter().filter(|x| x.arrive_ms > s.arrive_ms).min_by_key(|x| x.arrive_ms) {
            pts.push((n.lat, n.lon));
        }
    }
    let first = pts.first()?;
    let mut b = MapBounds { min_lat: first.0, min_lon: first.1, max_lat: first.0, max_lon: first.1 };
    for &(lat, lon) in &pts {
        b.min_lat = b.min_lat.min(lat);
        b.max_lat = b.max_lat.max(lat);
        b.min_lon = b.min_lon.min(lon);
        b.max_lon = b.max_lon.max(lon);
    }
    Some(b)
}

/// 뺀 곳 카드의 정차 글 자리에 쓰는 말
pub const SKIPPED_LABEL: &str = "빼고 가요";

/// 도착 시각이 다르더라도 이만큼(m) 안의 머문 곳은 같은 곳으로 본다 (코스로 다시 짠 들른 곳 등).
const SAME_PLACE_M: f64 = 150.0;

/// 하루 지도 GeoJSON의 머문 곳 번호(`order`)를 [stops] 순서(1부터)로 바꾼 GeoJSON. 읽을 수 없는 글이면 그대로 돌려준다.
/// [skipped]가 있으면 뺀 곳 표시와 새 코스 선을 더한다 (모듈 설명).
pub fn number_stops(geojson: &str, stops: &[StopFact], skipped: &[StopFact]) -> String {
    let Ok(mut root) = serde_json::from_str::<Value>(geojson) else { return geojson.to_string() };
    let Some(features) = root.get_mut("features").and_then(Value::as_array_mut) else { return geojson.to_string() };

    // 머문 곳 (자리, 도착 시각, 좌표)
    let stays: Vec<(usize, i64, Point)> = features
        .iter()
        .enumerate()
        .filter(|(_, f)| f.pointer("/properties/kind").and_then(Value::as_str) == Some("stay"))
        .filter_map(|(i, f)| {
            let arrive = f.pointer("/properties/arrive_ms")?.as_i64()?;
            let c = f.pointer("/geometry/coordinates")?.as_array()?;
            Some((i, arrive, Point { lat: c.get(1)?.as_f64()?, lon: c.first()?.as_f64()? }))
        })
        .collect();

    // 들른 곳마다 지도의 머문 곳 하나: 먼저 도착 시각이 같은 곳, 없으면 가장 가까운 곳(SAME_PLACE_M 안)
    let mut taken = vec![false; stays.len()];
    let mut matched: Vec<Option<usize>> = stops
        .iter()
        .map(|s| {
            let j = stays.iter().position(|&(_, a, _)| a == s.arrive_ms)?;
            taken[j] = true;
            Some(j)
        })
        .collect();
    for (k, s) in stops.iter().enumerate() {
        if matched[k].is_some() {
            continue;
        }
        let at = Point { lat: s.lat, lon: s.lon };
        let near = stays
            .iter()
            .enumerate()
            .filter(|(j, _)| !taken[*j])
            .map(|(j, &(_, _, p))| (j, haversine_m(at, p)))
            .filter(|&(_, m)| m <= SAME_PLACE_M)
            .min_by(|a, b| a.1.total_cmp(&b.1));
        if let Some((j, _)) = near {
            taken[j] = true;
            matched[k] = Some(j);
        }
    }

    // 뺀 곳: 남은 머문 곳 중 같은 곳 (도착 시각 → 거리)
    let gone: Vec<usize> = skipped
        .iter()
        .filter_map(|s| {
            let at = Point { lat: s.lat, lon: s.lon };
            let j = stays.iter().position(|&(_, a, _)| a == s.arrive_ms).filter(|&j| !taken[j]).or_else(|| {
                stays
                    .iter()
                    .enumerate()
                    .filter(|(j, _)| !taken[*j])
                    .map(|(j, &(_, _, p))| (j, haversine_m(at, p)))
                    .filter(|&(_, m)| m <= SAME_PLACE_M)
                    .min_by(|a, b| a.1.total_cmp(&b.1))
                    .map(|(j, _)| j)
            })?;
            taken[j] = true;
            Some(j)
        })
        .collect();

    for &(i, _, _) in &stays {
        if let Some(p) = features[i].get_mut("properties").and_then(Value::as_object_mut) {
            p.remove("order");
        }
    }
    for &j in &gone {
        if let Some(p) = features[stays[j].0].get_mut("properties").and_then(Value::as_object_mut) {
            p.insert("skipped".into(), json!(true));
            p.insert("dwell".into(), json!(SKIPPED_LABEL));
            p.remove("tag");
        }
    }
    if !skipped.is_empty() && stops.len() >= 2 {
        for f in features.iter_mut() {
            if f.pointer("/properties/kind").and_then(Value::as_str) == Some("trip") {
                if let Some(p) = f.get_mut("properties").and_then(Value::as_object_mut) {
                    p.insert("faded".into(), json!(true));
                }
            }
        }
        let line: Vec<Value> = stops.iter().map(|s| json!([(s.lon * 1e6).round() / 1e6, (s.lat * 1e6).round() / 1e6])).collect();
        features.push(json!({"type": "Feature", "geometry": {"type": "LineString", "coordinates": line}, "properties": {"kind": "plan", "key": "plan"}}));
    }
    for (k, m) in matched.iter().enumerate() {
        let order = k as u64 + 1;
        match m {
            Some(j) => {
                if let Some(p) = features[stays[*j].0].get_mut("properties").and_then(Value::as_object_mut) {
                    p.insert("order".into(), json!(order));
                }
            }
            None => features.push(extra_stop(&stops[k], order)),
        }
    }
    root.to_string()
}

/// 지도에 머문 곳이 없는 들른 곳의 번호 핀 (`mapdata` 머문 곳과 같은 속성).
fn extra_stop(s: &StopFact, order: u64) -> Value {
    let mut p = Map::new();
    p.insert("kind".into(), json!("stay"));
    p.insert("dwell".into(), json!(""));
    p.insert("key".into(), json!(format!("s{}", s.arrive_ms)));
    p.insert("arrive_ms".into(), json!(s.arrive_ms));
    p.insert("leave_ms".into(), json!(s.leave_ms));
    p.insert("open".into(), json!(false));
    p.insert("order".into(), json!(order));
    if let Some(n) = map_safe_name(&s.name) {
        p.insert("name".into(), json!(n));
    }
    let lon = (s.lon * 1e6).round() / 1e6;
    let lat = (s.lat * 1e6).round() / 1e6;
    json!({"type": "Feature", "geometry": {"type": "Point", "coordinates": [lon, lat]}, "properties": p})
}

#[cfg(test)]
mod tests {
    use super::*;

    fn stop(name: &str, arrive_ms: i64, lat: f64, lon: f64) -> StopFact {
        StopFact {
            name: name.into(),
            category: String::new(),
            address: String::new(),
            arrive_ms,
            leave_ms: arrive_ms + 60_000,
            lat,
            lon,
            first_visit: false,
        }
    }

    fn stay(arrive_ms: i64, lat: f64, lon: f64, order: u32, name: &str) -> String {
        format!(
            r#"{{"type":"Feature","geometry":{{"type":"Point","coordinates":[{lon:.6},{lat:.6}]}},"properties":{{"kind":"stay","dwell":"","key":"s{arrive_ms}","arrive_ms":{arrive_ms},"leave_ms":{arrive_ms},"open":false,"order":{order},"name":"{name}"}}}}"#
        )
    }

    fn day(features: &[String]) -> String {
        let trip = r#"{"type":"Feature","geometry":{"type":"LineString","coordinates":[[126.0,37.0],[126.1,37.1]]},"properties":{"kind":"trip","key":"t1"}}"#;
        format!(r#"{{"type":"FeatureCollection","features":[{trip},{}]}}"#, features.join(","))
    }

    /// 이름 → 번호 (번호 없는 머문 곳은 None)
    fn orders(geojson: &str) -> Vec<(String, Option<u64>)> {
        let v: Value = serde_json::from_str(geojson).unwrap();
        v["features"]
            .as_array()
            .unwrap()
            .iter()
            .filter(|f| f["properties"]["kind"] == "stay")
            .map(|f| (f["properties"]["name"].as_str().unwrap_or("").to_string(), f["properties"]["order"].as_u64()))
            .collect()
    }

    #[test]
    fn card_order_wins_over_day_order() {
        // 그날 순서: 집(1) → 을왕리해수욕장(2) → 조개구이(3). 카드 사진 줄은 조개구이 → 해수욕장 (코스 순서).
        let g = day(&[
            stay(1_000, 37.50, 126.90, 1, "집"),
            stay(5_000, 37.45, 126.37, 2, "을왕리해수욕장"),
            stay(9_000, 37.44, 126.38, 3, "조개구이"),
        ]);
        let stops = [stop("조개구이", 9_000, 37.44, 126.38), stop("을왕리해수욕장", 5_000, 37.45, 126.37)];
        let out = number_stops(&g, &stops, &[]);
        assert_eq!(
            orders(&out),
            vec![("집".into(), None), ("을왕리해수욕장".into(), Some(2)), ("조개구이".into(), Some(1))]
        );
        // 경로 선은 그대로
        assert!(out.contains(r#""kind":"trip""#));
    }

    #[test]
    fn matches_by_place_when_time_differs_and_adds_missing_stop() {
        let g = day(&[stay(5_000, 37.45, 126.37, 1, "해변")]);
        // 해변: 도착 시각이 달라도 50m 안 → 같은 곳. 카페: 지도에 머문 곳이 없음 → 번호 핀을 더한다.
        let stops = [stop("해변", 7_000, 37.4504, 126.37), stop("카페", 9_000, 37.40, 126.30)];
        let out = number_stops(&g, &stops, &[]);
        assert_eq!(orders(&out), vec![("해변".into(), Some(1)), ("카페".into(), Some(2))]);
        let v: Value = serde_json::from_str(&out).unwrap();
        let cafe = v["features"].as_array().unwrap().last().unwrap();
        assert_eq!(cafe["properties"]["key"], "s9000");
        assert_eq!(cafe["geometry"]["coordinates"][0].as_f64(), Some(126.3));
    }

    #[test]
    fn skipped_stop_is_marked_and_new_course_line_is_added() {
        let g = day(&[
            stay(1_000, 37.80, 128.90, 1, "해변"),
            stay(5_000, 37.79, 128.91, 2, "순두부"),
            stay(9_000, 37.77, 128.94, 3, "카페"),
        ]);
        let stops = [stop("해변", 1_000, 37.80, 128.90), stop("카페", 9_000, 37.77, 128.94)];
        let out = number_stops(&g, &stops, &[stop("순두부", 5_000, 37.79, 128.91)]);
        assert_eq!(orders(&out), vec![("해변".into(), Some(1)), ("순두부".into(), None), ("카페".into(), Some(2))]);
        let v: Value = serde_json::from_str(&out).unwrap();
        let fs = v["features"].as_array().unwrap();
        let tofu = fs.iter().find(|f| f["properties"]["name"] == "순두부").unwrap();
        assert_eq!(tofu["properties"]["skipped"], true);
        assert_eq!(tofu["properties"]["dwell"], SKIPPED_LABEL);
        let plan = fs.iter().find(|f| f["properties"]["kind"] == "plan").expect("새 코스 선");
        assert_eq!(plan["geometry"]["coordinates"].as_array().unwrap().len(), 2);
        assert!(fs.iter().filter(|f| f["properties"]["kind"] == "trip").all(|f| f["properties"]["faded"] == true));
        // 뺀 곳이 없으면 선도 흐림도 없다
        let plain = number_stops(&g, &stops, &[]);
        assert!(!plain.contains(r#""kind":"plan""#) && !plain.contains("faded"));
    }

    #[test]
    fn skip_focus_frames_the_skipped_stop_and_its_neighbours() {
        let stops = [stop("휴게소", 1_000, 37.47, 128.03), stop("해변", 2_000, 37.80, 128.90), stop("카페", 4_000, 37.77, 128.94)];
        let b = skip_focus(&stops, &[stop("순두부", 3_000, 37.79, 128.91)]).unwrap();
        // 휴게소(멀리)는 빼고 해변 · 순두부 · 카페만
        assert_eq!((b.min_lat, b.max_lat, b.min_lon, b.max_lon), (37.77, 37.80, 128.90, 128.94));
        assert!(skip_focus(&stops, &[]).is_none());
    }

    #[test]
    fn far_stay_is_not_taken_and_bad_json_is_returned_as_is() {
        let g = day(&[stay(5_000, 37.45, 126.37, 1, "먼곳")]);
        let out = number_stops(&g, &[stop("딴곳", 9_000, 37.0, 127.0)], &[]);
        assert_eq!(orders(&out), vec![("먼곳".into(), None), ("딴곳".into(), Some(1))]);
        assert_eq!(number_stops("not json", &[], &[]), "not json");
    }
}

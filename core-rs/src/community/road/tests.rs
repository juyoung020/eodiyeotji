//! 코스 도로 경로: 요청 나누기 · 응답 읽기 · 줄여 저장 · 다음엔 바로 그리기 · 못 받으면 직선 · DB 올리기.

use super::*;

const KST: i32 = 540;
/// 2026-11-06 12:00 KST
const NOW: i64 = 1_793_934_000_000;

struct TempDb(std::path::PathBuf);

impl TempDb {
    fn new(tag: &str) -> Self {
        let db = TempDb(std::env::temp_dir().join(format!("eodi-road-{tag}-{}.db", std::process::id())));
        db.remove();
        db
    }
    fn open(&self) -> std::sync::Arc<EodiStore> {
        EodiStore::open(self.0.to_string_lossy().into_owned(), KST).unwrap()
    }
    fn remove(&self) {
        for ext in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{ext}", self.0.display()));
        }
    }
}

impl Drop for TempDb {
    fn drop(&mut self) {
        self.remove();
    }
}

fn seeded(tag: &str) -> (TempDb, std::sync::Arc<EodiStore>) {
    let db = TempDb::new(tag);
    let store = db.open();
    assert!(store.seed_community_demo(NOW).unwrap() > 0);
    (db, store)
}

fn stop(lat: f64, lon: f64) -> StopFact {
    StopFact {
        name: "곳".into(),
        category: String::new(),
        address: String::new(),
        arrive_ms: 0,
        leave_ms: 0,
        lat,
        lon,
        first_visit: false,
    }
}

/// 구간마다 가운데가 북쪽으로 휘는 도로 점 21개로 잇는 가짜 카카오 응답.
fn kakao(stops: &[StopFact]) -> String {
    let sections: Vec<Value> = stops
        .windows(2)
        .map(|w| {
            let (a, z) = (&w[0], &w[1]);
            let mut v = Vec::new();
            for k in 0..=20 {
                let t = k as f64 / 20.0;
                let bulge = 0.01 * (t * std::f64::consts::PI).sin();
                v.push(serde_json::json!(a.lon + (z.lon - a.lon) * t));
                v.push(serde_json::json!(a.lat + (z.lat - a.lat) * t + bulge));
            }
            serde_json::json!({"roads":[{"vertexes":v}],"guides":[]})
        })
        .collect();
    serde_json::json!({"routes":[{"result_code":0,"summary":{"distance":1,"duration":1},"sections":sections}]}).to_string()
}

fn trip_lines(m: &DayMap) -> Vec<Vec<Value>> {
    let v: Value = serde_json::from_str(&m.geojson).unwrap();
    v["features"]
        .as_array()
        .unwrap()
        .iter()
        .filter(|f| f["properties"]["kind"] == "trip")
        .map(|f| f["geometry"]["coordinates"].as_array().unwrap().clone())
        .collect()
}

#[test]
fn request_ranges_share_endpoints_and_cap_waypoints() {
    assert!(request_ranges(0).is_empty());
    assert!(request_ranges(1).is_empty());
    assert_eq!(request_ranges(2), vec![(0, 1)]);
    assert_eq!(request_ranges(7), vec![(0, 6)]);
    assert_eq!(request_ranges(8), vec![(0, 6), (6, 7)]);
    assert_eq!(request_ranges(14), vec![(0, 6), (6, 12), (12, 13)]);
}

#[test]
fn reads_sections_and_rejects_bad_json() {
    let s = kakao_sections(&kakao(&[stop(37.0, 127.0), stop(37.1, 127.1)])).unwrap().unwrap();
    assert_eq!(s.len(), 1);
    assert_eq!(s[0].len(), 21);
    let failed = r#"{"routes":[{"result_code":104,"result_msg":"출발지와 도착지가 5 m 이내"}]}"#;
    assert!(kakao_sections(failed).unwrap().is_none());
    assert!(kakao_sections("<html>").is_err());
}

#[test]
fn saves_simplified_road_and_draws_it_next_time() {
    let (db, store) = seeded("save");
    let id = "d13".to_string();
    let stops = store.course_by_id(&id).unwrap().stops;
    let urls = store.course_road_requests(id.clone()).unwrap();
    assert_eq!(urls.len(), 1, "5곳 = 요청 하나");
    assert!(urls[0].contains("apis-navi.kakaomobility.com") && urls[0].contains("waypoints="));
    // 처음엔 직선
    assert!(trip_lines(&store.course_map(id.clone()).unwrap()).iter().all(|l| l.len() == 2));

    let m = store.save_course_road(id.clone(), vec![kakao(&stops)]).unwrap();
    let lines = trip_lines(&m);
    assert_eq!(lines.len(), stops.len() - 1);
    for l in &lines {
        // 휜 도로를 따라 점이 늘었지만, 도로 점 21개 + 양 끝 핀보다는 줄었다
        assert!(l.len() > 2 && l.len() < 23, "{}", l.len());
    }
    // 범위가 휜 길 전체를 담는다 (카메라가 길을 자르지 않게)
    let b = m.bounds.unwrap();
    for p in lines.iter().flatten() {
        let (lon, lat) = (p[0].as_f64().unwrap(), p[1].as_f64().unwrap());
        assert!((b.min_lat..=b.max_lat).contains(&lat) && (b.min_lon..=b.max_lon).contains(&lon), "{lat},{lon}");
    }

    // 다시 열면(앱을 다시 켜도) 네트워크 없이 바로 도로로, 요청할 것도 없다
    drop(store);
    let store = db.open();
    assert!(store.course_road_requests(id.clone()).unwrap().is_empty());
    assert_eq!(trip_lines(&store.course_map(id).unwrap()), lines);
}

#[test]
fn missing_response_keeps_straight_and_kakao_failure_is_straight_leg() {
    let (_db, store) = seeded("fail");
    let id = "d13".to_string();
    // 못 받음(빈 응답): 저장하지 않고 직선, 다음에 다시 요청
    let m = store.save_course_road(id.clone(), vec![String::new()]).unwrap();
    assert!(trip_lines(&m).iter().all(|l| l.len() == 2));
    assert_eq!(store.course_road_requests(id.clone()).unwrap().len(), 1);
    // 응답 수가 안 맞으면 오류
    assert!(store.save_course_road(id.clone(), vec![]).is_err());
    // 카카오가 길을 못 찾음: 그 구간은 직선으로 저장 (같은 답이라 다시 묻지 않는다)
    let failed = r#"{"routes":[{"result_code":104,"result_msg":"x"}]}"#.to_string();
    let m = store.save_course_road(id.clone(), vec![failed]).unwrap();
    assert!(trip_lines(&m).iter().all(|l| l.len() == 2));
    assert!(store.course_road_requests(id).unwrap().is_empty());
}

#[test]
fn changed_stops_change_key_and_legs_round_trip() {
    let a = [stop(37.0, 127.0), stop(37.1, 127.1)];
    let mut b = a.clone();
    b[1].lat = 37.2;
    assert_ne!(stops_key(&a), stops_key(&b));
    let legs = vec![vec![Point { lat: 37.0, lon: 127.0 }, Point { lat: 37.05, lon: 127.02 }, Point { lat: 37.1, lon: 127.1 }]];
    assert_eq!(decode_legs(&encode_legs(&legs)), legs);
}

#[test]
fn old_db_gets_road_table() {
    let db = TempDb::new("v18");
    {
        let store = db.open();
        let c = store.lock().unwrap();
        c.execute_batch(DROP_V19).unwrap();
        c.pragma_update(None, "user_version", 18).unwrap();
    }
    let store = db.open();
    store.seed_community_demo(NOW).unwrap();
    assert_eq!(store.course_road_requests("d13".into()).unwrap().len(), 1);
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, crate::store::SCHEMA_VERSION);
}

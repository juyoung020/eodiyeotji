//! 구글 타임라인 가져오기 테스트. 지어낸 작은 파일로 확인한다 (실제 사람의 기록은 저장소에 두지 않는다).
//! DB · 파일은 임시 폴더에 만들고 끝나면 지운다.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

use super::*;

const KST: i32 = 540;

struct Temp(PathBuf);

impl Temp {
    fn new(name: &str, ext: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let t = Temp(std::env::temp_dir().join(format!("eodi-timeline-{name}-{}-{n}.{ext}", std::process::id())));
        t.remove();
        t
    }

    fn remove(&self) {
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", self.0.display()));
        }
    }

    fn path(&self) -> String {
        self.0.to_string_lossy().into_owned()
    }
}

impl Drop for Temp {
    fn drop(&mut self) {
        self.remove();
    }
}

/// 서울시청 → 남산서울타워(차, 20분) → 걷기 → 남산서울타워 → 서울숲(차, 30분). 그리고 다음 날 아주 짧은 차 이동.
const SAMPLE: &str = r#"{
  "semanticSegments": [
    {"startTime": "2026-05-02T10:00:00.000+09:00", "endTime": "2026-05-02T12:00:00.000+09:00",
     "timelinePath": [
       {"point": "37.5600°, 126.9800°", "time": "2026-05-02T10:08:00.000+09:00"},
       {"point": "37.5550°, 126.9850°", "time": "2026-05-02T10:15:00.000+09:00"},
       {"point": "37.5480°, 127.0100°", "time": "2026-05-02T11:45:00.000+09:00"}
     ]},
    {"startTime": "2026-05-02T10:00:00.000+09:00", "endTime": "2026-05-02T10:20:00.000+09:00",
     "activity": {"start": {"latLng": "37.5663°, 126.9779°"}, "end": {"latLng": "37.5512°, 126.9882°"},
                  "distanceMeters": 2100.0, "topCandidate": {"type": "IN_PASSENGER_VEHICLE", "probability": 0.9}}},
    {"startTime": "2026-05-02T10:20:00.000+09:00", "endTime": "2026-05-02T11:20:00.000+09:00",
     "visit": {"hierarchyLevel": 0, "topCandidate": {"placeId": "x", "placeLocation": {"latLng": "37.5512°, 126.9882°"}}}},
    {"startTime": "2026-05-02T11:20:00.000+09:00", "endTime": "2026-05-02T11:30:00.000+09:00",
     "activity": {"start": {"latLng": "37.5512°, 126.9882°"}, "end": {"latLng": "37.5520°, 126.9890°"},
                  "topCandidate": {"type": "WALKING"}}},
    {"startTime": "2026-05-02T11:30:00.000+09:00", "endTime": "2026-05-02T12:00:00.000+09:00",
     "activity": {"start": {"latLng": "37.5512°, 126.9882°"}, "end": {"latLng": "37.5444°, 127.0374°"},
                  "topCandidate": {"type": "IN_PASSENGER_VEHICLE"}}},
    {"startTime": "2026-05-03T09:00:00.000+09:00", "endTime": "2026-05-03T09:00:30.000+09:00",
     "activity": {"start": {"latLng": "37.5444°, 127.0374°"}, "end": {"latLng": "37.5450°, 127.0380°"},
                  "topCandidate": {"type": "IN_PASSENGER_VEHICLE"}}}
  ],
  "rawSignals": [{"position": {"LatLng": "37.5°, 127.0°", "timestamp": "2026-05-02T10:00:00.000+09:00"}}],
  "userLocationProfile": {"frequentPlaces": []}
}"#;

fn sample_file(name: &str) -> Temp {
    let f = Temp::new(name, "json");
    std::fs::write(&f.0, SAMPLE).unwrap();
    f
}

fn open(name: &str) -> (Temp, Arc<EodiStore>) {
    let db = Temp::new(name, "db");
    let store = EodiStore::open(db.path(), KST).expect("open");
    (db, store)
}

fn count(store: &EodiStore, sql: &str) -> i64 {
    store.snapshot().unwrap().query_row(sql, [], |r| r.get(0)).unwrap()
}

#[test]
fn times_and_points_are_read() {
    let t = parse_time("2026-11-12T16:09:20.000+09:00").unwrap();
    assert_eq!(t, days_from_civil(2026, 11, 12) * DAY_MS + (7 * 3600 + 9 * 60 + 20) * 1000, "한국 16:09:20 = UTC 07:09:20");
    assert_eq!(parse_time("2026-11-12T07:09:20Z"), Some(t));
    assert_eq!(parse_time("2026-11-12T02:09:20.500-05:00"), Some(t + 500));
    assert_eq!(parse_time("2026-11-12"), None);
    let p = parse_point("37.6012345°, 126.8612345°").unwrap();
    assert!((p.lat - 37.6012345).abs() < 1e-9 && (p.lon - 126.8612345).abs() < 1e-9);
    assert!(parse_point("91.0°, 10.0°").is_none() && parse_point("서울").is_none());
}

#[test]
fn points_between_are_filled_every_five_seconds() {
    let a = Point { lat: 37.0, lon: 127.0 };
    let b = Point { lat: 37.1, lon: 127.2 };
    let fixes = densify(&[(0, a), (20_000, b)]);
    assert_eq!(fixes.iter().map(|f| f.0).collect::<Vec<_>>(), [0, 5_000, 10_000, 15_000, 20_000]);
    assert!((fixes[2].1 - 37.05).abs() < 1e-9 && (fixes[2].2 - 127.1).abs() < 1e-9, "가운데는 두 점의 중간");
    assert_eq!(fixes[4], (20_000, 37.1, 127.2));
}

#[test]
fn car_moves_become_drives_and_walks_are_left_out() {
    let file = sample_file("basic");
    let (_db, store) = open("basic");
    let r = store.import_google_timeline(file.path(), None).unwrap();
    assert_eq!((r.found, r.drives, r.skipped_existing), (2, 2, 0), "차 이동 2번 (걷기 · 30초짜리는 뺀다)");
    assert_eq!((r.first_day.as_str(), r.last_day.as_str()), ("2026-05-02", "2026-05-02"));
    // 20분 + 30분을 5초 간격으로: 241 + 361점
    assert_eq!(r.fixes, 241 + 361);
    assert_eq!(count(&store, "SELECT count(*) FROM fix"), 602);
    assert_eq!(count(&store, "SELECT count(*) FROM drive_event"), 4, "주행마다 시작 · 끝");
    // 첫 주행은 지나간 점(10:08, 10:15)을 거친다
    let t = parse_time("2026-05-02T10:08:00.000+09:00").unwrap();
    assert_eq!(count(&store, &format!("SELECT count(*) FROM fix WHERE t_ms = {t} AND abs(lat - 37.56) < 1e-6")), 1);
    // 하루 상세에 주행 2번이 잡힌다
    let day = store.day_detail("2026-05-02".into()).unwrap();
    assert_eq!(day.trips.len(), 2, "{:?}", day.trips.len());

    // 다시 가져오면 그날은 이미 기록이 있어서 건너뛴다
    let again = store.import_google_timeline(file.path(), None).unwrap();
    assert_eq!((again.drives, again.fixes, again.skipped_existing), (0, 0, 2));
    assert_eq!(again.first_day, "");
}

#[test]
fn from_day_and_bad_files() {
    let file = sample_file("from");
    let (_db, store) = open("from");
    let r = store.import_google_timeline(file.path(), Some("2026-05-03".into())).unwrap();
    assert_eq!((r.found, r.drives), (0, 0), "5월 3일부터면 넣을 것이 없다");
    assert!(store.import_google_timeline(file.path(), Some("어제".into())).is_err());
    assert!(store.import_google_timeline("없는-파일.json".into(), None).is_err());
    let bad = Temp::new("bad", "json");
    std::fs::write(&bad.0, "[1, 2, 3]").unwrap();
    assert!(store.import_google_timeline(bad.path(), None).is_err());
}

/// 내 PC의 실제 파일로 확인할 때만: `EODI_TIMELINE=<파일> cargo test real_timeline -- --ignored --nocapture`.
/// 숫자만 찍는다 (위치는 찍지 않는다).
#[test]
#[ignore]
fn real_timeline_file() {
    let Ok(path) = std::env::var("EODI_TIMELINE") else { return };
    let (_db, store) = open("real");
    let t = std::time::Instant::now();
    let r = store.import_google_timeline(path, std::env::var("EODI_TIMELINE_FROM").ok()).unwrap();
    println!("{r:?} ({:?})", t.elapsed());
    println!("주행 있는 날: {}", count(&store, "SELECT count(*) FROM day_summary"));
}

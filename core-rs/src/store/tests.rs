//! EodiStore 테스트. DB는 임시 폴더의 파일을 쓰고 끝나면 지운다.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::time::Instant;

use super::*;
use crate::geo::haversine_m;

const SEC: i64 = 1000;
const MIN: i64 = 60 * SEC;
const HOUR: i64 = 60 * MIN;
const KST: i32 = 540;

/// 임시 DB 파일. 테스트가 끝나면(Drop) -wal, -shm까지 지운다. 저장소보다 먼저 선언해야 나중에 지워진다.
struct TempDb(PathBuf);

impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let p = std::env::temp_dir().join(format!("eodi-{name}-{}-{n}.db", std::process::id()));
        let db = TempDb(p);
        db.remove();
        db
    }

    fn path(&self) -> String {
        self.0.to_string_lossy().into_owned()
    }

    fn open(&self, tz: i32) -> Arc<EodiStore> {
        EodiStore::open(self.path(), tz).expect("open")
    }

    fn remove(&self) {
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", self.0.display()));
        }
    }
}

impl Drop for TempDb {
    fn drop(&mut self) {
        self.remove();
    }
}

/// 한국 시각(연, 월, 일, 시, 분) → UTC epoch ms.
fn kst(y: i64, m: i64, d: i64, h: i64, mi: i64) -> i64 {
    days_from_civil(y, m, d) * DAY_MS + (h * 60 + mi) * MIN - KST as i64 * MIN
}

fn gf(lat: f64, lon: f64, t_ms: i64) -> GeoFix {
    GeoFix { lat, lon, t_ms, accuracy_m: 5.0 }
}

/// t0부터 1초 간격 n개, (lat0, lon0)에서 북쪽으로 초속 약 11m.
fn drive(t0: i64, n: i64, lat0: f64, lon0: f64) -> Vec<GeoFix> {
    (0..n).map(|i| gf(lat0 + i as f64 * 1e-4, lon0, t0 + i * SEC)).collect()
}

fn last_point(fixes: &[GeoFix]) -> (f64, f64) {
    let f = fixes.last().unwrap();
    (f.lat, f.lon)
}

fn day_of(store: &EodiStore, t: i64) -> String {
    format_day(store.local_day(t))
}

#[test]
fn open_creates_schema_and_rejects_bad_input() {
    let db = TempDb::new("open");
    assert!(matches!(EodiStore::open(String::new(), KST), Err(CoreError::InvalidInput(_))));
    assert!(matches!(EodiStore::open(db.path(), 10_000), Err(CoreError::InvalidInput(_))));
    let store = db.open(KST);
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, SCHEMA_VERSION);
    assert_eq!(store.fix_count().unwrap(), 0);
    assert!(store.day_summaries(10).unwrap().is_empty());
    assert!(matches!(store.mark_drive_event(DriveEvent::Start, 0), Err(CoreError::InvalidInput(_))));
}

#[test]
fn newer_db_version_is_refused() {
    let db = TempDb::new("newer");
    {
        let c = Connection::open(db.path()).unwrap();
        c.pragma_update(None, "user_version", SCHEMA_VERSION + 1).unwrap();
    }
    assert!(matches!(EodiStore::open(db.path(), KST), Err(CoreError::Db(_))));
}

#[test]
fn bundled_sqlite_has_fts5_and_rtree() {
    let c = Connection::open_in_memory().unwrap();
    c.execute_batch("CREATE VIRTUAL TABLE t USING fts5(body); CREATE VIRTUAL TABLE r USING rtree(id, x0, x1, y0, y1);")
        .unwrap();
}

#[test]
fn ingest_accepts_out_of_order_and_ignores_duplicate_times() {
    let db = TempDb::new("order");
    let store = db.open(KST);
    let t0 = kst(2026, 11, 5, 9, 0);
    let fixes = drive(t0, 600, 37.5, 127.0);
    let mut back_half: Vec<GeoFix> = fixes[300..].to_vec();
    back_half.reverse();
    assert_eq!(store.ingest(back_half).unwrap(), 300);
    // 앞 절반(역순) + 이미 들어간 뒤 절반 다시 + 같은 묶음 안 중복
    let mut again: Vec<GeoFix> = fixes.iter().rev().copied().collect();
    again.push(fixes[0]);
    assert_eq!(store.ingest(again).unwrap(), 300);
    assert_eq!(store.fix_count().unwrap(), 600);
    // 같은 시각에 다른 좌표가 오면 먼저 들어온 점이 남는다
    assert_eq!(store.ingest(vec![gf(38.0, 128.0, t0)]).unwrap(), 0);

    let d = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d.trips.len(), 1);
    let t = &d.trips[0];
    assert_eq!((t.start_ms, t.end_ms), (t0, t0 + 599 * SEC));
    assert_eq!(t.start, GeoPoint { lat: 37.5, lon: 127.0 });
    let want = haversine_m(Point { lat: 37.5, lon: 127.0 }, Point { lat: 37.5 + 599e-4, lon: 127.0 });
    assert!((t.distance_m - want).abs() < 1.0, "{} vs {want}", t.distance_m);
    assert_eq!(t.path.len(), 2, "직선은 양 끝만 남는다");
}

#[test]
fn noise_is_not_stored() {
    let db = TempDb::new("noise");
    let store = db.open(KST);
    let t = kst(2026, 11, 5, 9, 0);
    let mut bad = vec![
        GeoFix { accuracy_m: 150.0, ..gf(37.5, 127.0, t) },
        GeoFix { accuracy_m: f32::NAN, ..gf(37.5, 127.0, t + 1) },
        gf(f64::NAN, 127.0, t + 2),
        gf(37.5, f64::INFINITY, t + 3),
        gf(91.0, 127.0, t + 4),
        gf(37.5, -181.0, t + 5),
        gf(0.0, 0.0, t + 6),
        gf(37.5, 127.0, 0),
        gf(37.5, 127.0, -5),
    ];
    let good = vec![
        GeoFix { accuracy_m: 0.0, ..gf(37.5, 127.0, t + 10) }, // 정확도 모름
        GeoFix { accuracy_m: 100.0, ..gf(37.5, 127.0, t + 11) }, // 경계값은 받는다
        gf(-33.9, 151.2, t + 12),
    ];
    bad.extend(good);
    assert_eq!(store.ingest(bad).unwrap(), 3);
    assert_eq!(store.fix_count().unwrap(), 3);
}

#[test]
fn gps_jump_is_left_out_of_the_trip() {
    let db = TempDb::new("jump");
    let clean_db = TempDb::new("jump-clean");
    let (store, clean) = (db.open(KST), clean_db.open(KST));
    let t0 = kst(2026, 11, 5, 9, 0);
    let fixes = drive(t0, 300, 37.5, 127.0);
    clean.ingest(fixes.clone()).unwrap();
    let mut noisy = fixes.clone();
    noisy[150].lon += 0.3; // 약 26km 옆으로 튐
    assert_eq!(store.ingest(noisy).unwrap(), 300, "저장은 한다");
    let day = day_of(&store, t0);
    let (a, b) = (store.day_detail(day.clone()).unwrap(), clean.day_detail(day).unwrap());
    assert_eq!(a.trips.len(), 1);
    assert!((a.trips[0].distance_m - b.trips[0].distance_m).abs() < 2.0, "{} vs {}", a.trips[0].distance_m, b.trips[0].distance_m);
    assert!(a.trips[0].path.iter().all(|p| p.lon < 127.01));
}

#[test]
fn location_gap_splits_trips() {
    let db = TempDb::new("gap");
    let store = db.open(KST);
    let t0 = kst(2026, 11, 5, 9, 0);
    let a = drive(t0, 600, 37.5, 127.0);
    let (lat, lon) = last_point(&a);
    let b = drive(t0 + 599 * SEC + 7 * MIN, 600, lat, lon); // 7분 공백 (5분 이상 → 끊김)
    let c_start = b.last().unwrap().t_ms + 3 * MIN; // 3분 공백 (터널 등 → 이어짐)
    let (lat, lon) = last_point(&b);
    let c = drive(c_start, 100, lat, lon);
    store.ingest(a).unwrap();
    store.ingest(b).unwrap();
    store.ingest(c).unwrap();
    let d = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d.trips.len(), 2);
    assert!(d.stays.is_empty(), "공백 7분은 머문 곳(10분 이상)이 아니다: {:?}", d.stays);
    assert_eq!(d.trips[1].end_ms, c_start + 99 * SEC);
}

#[test]
fn drive_events_split_trips_even_if_they_arrive_late() {
    let db = TempDb::new("events");
    let store = db.open(KST);
    let t0 = kst(2026, 11, 5, 9, 0);
    let a = drive(t0, 600, 37.5, 127.0);
    let (lat, lon) = last_point(&a);
    let end_t = a.last().unwrap().t_ms;
    let start_t = end_t + 2 * MIN; // 2분 동안 P → 공백만으로는 안 끊긴다
    let b = drive(start_t, 600, lat, lon);
    store.ingest(a).unwrap();
    store.ingest(b).unwrap();
    assert_eq!(store.day_summaries(10).unwrap()[0].trip_count, 1);

    store.mark_drive_event(DriveEvent::End, end_t).unwrap();
    store.mark_drive_event(DriveEvent::Start, start_t).unwrap();
    store.mark_drive_event(DriveEvent::Start, start_t).unwrap(); // 중복 신호는 한 번만
    let d = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d.trips.iter().map(|t| (t.start_ms, t.end_ms)).collect::<Vec<_>>(), vec![
        (t0, end_t),
        (start_t, start_t + 599 * SEC)
    ]);
    assert_eq!(d.summary.trip_count, 2);
}

#[test]
fn trips_belong_to_the_local_day_they_start() {
    let db = TempDb::new("midnight");
    let store = db.open(KST);
    // 11/4 23:50 KST 출발, 20분 달려 11/5 00:10 도착 후 주차(끝 신호)
    let t0 = kst(2026, 11, 4, 23, 50);
    let night = drive(t0, 1201, 37.5, 127.0);
    let arrive = night.last().unwrap().t_ms;
    store.ingest(night).unwrap();
    store.mark_drive_event(DriveEvent::End, arrive).unwrap();
    // 11/5 08:30 KST = 11/4 23:30 UTC 출발 → 한국 날짜 11/5
    let morning = kst(2026, 11, 5, 8, 30);
    store.ingest(drive(morning, 60, 36.0, 128.0)).unwrap();

    let days = store.day_summaries(10).unwrap();
    assert_eq!(days.iter().map(|d| d.day.as_str()).collect::<Vec<_>>(), vec!["2026-11-05", "2026-11-04"]);
    assert_eq!((days[1].trip_count, days[1].stay_count), (1, 0));
    assert_eq!((days[1].first_ms, days[1].last_ms), (t0, arrive), "자정 넘긴 주행은 출발한 날에");
    assert!((days[1].drive_millis - 20 * MIN).abs() == 0);
    assert_eq!(days[0].trip_count, 1);

    let d5 = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d5.trips[0].start_ms, morning);
    // 00:10 도착 → 아침 출발지는 멀다(다른 곳) → 머문 곳 없음. (같은 자리 출발이면 아래 테스트)
    assert!(d5.stays.is_empty());
    assert_eq!(d5.summary, days[0]);

    // 시간대 0(UTC)으로 다시 열면 날짜가 다시 나뉜다: 11/4 14:50 UTC, 11/4 23:30 UTC → 둘 다 11/4
    drop(store);
    let utc = db.open(0);
    let days = utc.day_summaries(10).unwrap();
    assert_eq!(days.len(), 1);
    assert_eq!((days[0].day.as_str(), days[0].trip_count), ("2026-11-04", 2));
}

#[test]
fn stay_after_a_trip_that_crosses_midnight_counts_on_the_arrival_day() {
    let db = TempDb::new("midnight-stay");
    let store = db.open(KST);
    let t0 = kst(2026, 11, 4, 23, 50);
    let night = drive(t0, 1201, 37.5, 127.0);
    let (lat, lon) = last_point(&night);
    let arrive = night.last().unwrap().t_ms;
    store.ingest(night).unwrap();
    store.mark_drive_event(DriveEvent::End, arrive).unwrap();
    let days = store.day_summaries(10).unwrap();
    // 11/5: 주행 0, 머무는 중 1
    assert_eq!(days[0].day, "2026-11-05");
    assert_eq!((days[0].trip_count, days[0].stay_count, days[0].first_ms, days[0].last_ms), (0, 1, arrive, arrive));
    let d5 = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d5.stays, vec![StaySummary { center: GeoPoint { lat, lon }, arrive_ms: arrive, leave_ms: arrive }]);

    // 아침에 같은 자리에서 출발 → 머문 곳이 00:10~08:30으로 닫힌다
    let leave = kst(2026, 11, 5, 8, 30);
    store.ingest(drive(leave, 60, lat + 1e-4, lon)).unwrap();
    let d5 = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d5.stays.len(), 1);
    assert_eq!((d5.stays[0].arrive_ms, d5.stays[0].leave_ms), (arrive, leave));
    assert_eq!(d5.trips.len(), 1);
}

#[test]
fn parking_between_trips_is_a_stay() {
    let db = TempDb::new("park");
    let store = db.open(KST);
    let a = drive(kst(2026, 11, 5, 9, 0), 1800, 37.5, 127.0);
    let (lat, lon) = last_point(&a);
    let a_end = a.last().unwrap().t_ms;
    // 11시에 50m 떨어진 곳에서 출발 (같은 주차장)
    let b_start = kst(2026, 11, 5, 11, 0);
    let b = drive(b_start, 1800, lat + 0.00045, lon);
    let (blat, blon) = last_point(&b);
    // 13시에 2km 떨어진 곳에서 출발 → 어디 있었는지 모름
    let c = drive(kst(2026, 11, 5, 13, 0), 600, blat + 0.02, blon);
    // 순서를 섞어서 넣는다
    store.ingest(c).unwrap();
    store.ingest(a).unwrap();
    store.ingest(b).unwrap();
    let d = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d.trips.len(), 3);
    assert_eq!(d.stays, vec![StaySummary { center: GeoPoint { lat, lon }, arrive_ms: a_end, leave_ms: b_start }]);
    assert_eq!((d.summary.trip_count, d.summary.stay_count), (3, 1));
}

#[test]
fn long_stop_inside_a_trip_is_a_stay() {
    let db = TempDb::new("intrip");
    let store = db.open(KST);
    let t0 = kst(2026, 11, 5, 12, 0);
    let mut fixes = drive(t0, 600, 37.5, 127.0);
    let (lat, lon) = last_point(&fixes);
    // 드라이브스루: 기어 D로 15분, 30m 안에서 꿈틀
    let wait0 = t0 + 600 * SEC;
    fixes.extend((0..900).map(|i| gf(lat + (i % 7) as f64 * 4e-5, lon, wait0 + i * SEC)));
    fixes.extend(drive(wait0 + 900 * SEC, 600, lat + 3e-4, lon));
    for chunk in fixes.chunks(30) {
        store.ingest(chunk.to_vec()).unwrap();
    }
    let d = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d.trips.len(), 1);
    assert_eq!(d.stays.len(), 1, "{:?}", d.stays);
    let s = &d.stays[0];
    assert!(s.leave_ms - s.arrive_ms >= 14 * MIN);
    assert!(s.arrive_ms <= wait0 && s.leave_ms >= wait0 + 899 * SEC - MIN);
}

#[test]
fn last_arrival_is_an_open_stay_only_after_an_end_signal() {
    let db = TempDb::new("open-stay");
    let store = db.open(KST);
    let a = drive(kst(2026, 11, 5, 18, 0), 600, 37.5, 127.0);
    let (lat, lon) = last_point(&a);
    let end = a.last().unwrap().t_ms;
    store.ingest(a).unwrap();
    assert!(store.day_detail("2026-11-05".into()).unwrap().stays.is_empty(), "아직 달리는 중일 수 있다");

    store.mark_drive_event(DriveEvent::End, end + 2 * SEC).unwrap();
    let d = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d.stays, vec![StaySummary { center: GeoPoint { lat, lon }, arrive_ms: end, leave_ms: end }]);

    // 다음 날 아침 같은 자리에서 출발: 밤새 주차는 도착한 날(11/5)의 머문 곳, 11/6에는 주행만
    let leave = kst(2026, 11, 6, 8, 0);
    store.mark_drive_event(DriveEvent::Start, leave).unwrap();
    store.ingest(drive(leave, 300, lat, lon)).unwrap();
    let d5 = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!((d5.stays[0].arrive_ms, d5.stays[0].leave_ms), (end, leave));
    let d6 = store.day_detail("2026-11-06".into()).unwrap();
    assert_eq!((d6.trips.len(), d6.stays.len()), (1, 0));
    let days = store.day_summaries(10).unwrap();
    assert_eq!(days.iter().map(|d| (d.day.as_str(), d.stay_count)).collect::<Vec<_>>(), vec![
        ("2026-11-06", 0),
        ("2026-11-05", 1)
    ]);
}

#[test]
fn entries_merge_trips_and_stays_in_time_order() {
    let db = TempDb::new("entries");
    let store = db.open(KST);
    // 주행1(드라이브스루 포함) → 주차 → 주행2 → 끝 신호(머무는 중)
    let t0 = kst(2026, 11, 5, 9, 0);
    let mut one = drive(t0, 300, 37.5, 127.0);
    let (lat, lon) = last_point(&one);
    one.extend((0..900).map(|i| gf(lat + (i % 5) as f64 * 3e-5, lon, t0 + (300 + i) * SEC)));
    one.extend(drive(t0 + 1200 * SEC, 300, lat + 2e-4, lon));
    let (lat, lon) = last_point(&one);
    let two_start = kst(2026, 11, 5, 13, 0);
    let two = drive(two_start, 300, lat, lon);
    let two_end = two.last().unwrap().t_ms;
    store.ingest(two).unwrap();
    store.ingest(one).unwrap();
    store.mark_drive_event(DriveEvent::End, two_end).unwrap();

    let d = store.day_detail("2026-11-05".into()).unwrap();
    let kinds: Vec<(char, i64)> = d
        .entries
        .iter()
        .map(|e| match e {
            DayEntry::Trip { trip } => ('T', trip.start_ms),
            DayEntry::Stay { stay } => ('S', stay.arrive_ms),
        })
        .collect();
    assert_eq!(kinds.iter().map(|k| k.0).collect::<String>(), "TSSTS", "{kinds:?}");
    assert!(kinds.windows(2).all(|w| w[0].1 <= w[1].1));
    assert_eq!(d.entries.len(), d.trips.len() + d.stays.len());
    assert_eq!(d.summary, store.day_summaries(1).unwrap()[0]);

    let empty = store.day_detail("2026-01-01".into()).unwrap();
    assert!(empty.trips.is_empty() && empty.stays.is_empty() && empty.entries.is_empty());
    assert_eq!(
        empty.summary,
        DaySummary {
            day: "2026-01-01".into(),
            trip_count: 0,
            stay_count: 0,
            distance_m: 0.0,
            drive_millis: 0,
            first_ms: 0,
            last_ms: 0
        }
    );
}

#[test]
fn records_survive_reopen_including_unflushed_changes() {
    let db = TempDb::new("reopen");
    let t0 = kst(2026, 11, 5, 9, 0);
    let before = {
        let store = db.open(KST);
        store.ingest(drive(t0, 600, 37.5, 127.0)).unwrap();
        let s = store.day_summaries(10).unwrap();
        // 조회하지 않은(아직 계산 안 된) 변경도 남아야 한다
        store.ingest(drive(t0 + 2 * HOUR, 600, 37.5, 127.0)).unwrap();
        s
    };
    assert_eq!(before[0].trip_count, 1);
    let store = db.open(KST);
    assert_eq!(store.fix_count().unwrap(), 1200);
    let after = store.day_summaries(10).unwrap();
    assert_eq!(after.len(), 1);
    assert_eq!(after[0].trip_count, 2);
}

#[test]
fn day_detail_validates_the_day_string() {
    let db = TempDb::new("dayfmt");
    let store = db.open(KST);
    for bad in ["", "2026-1-05", "2026/11/05", "2026-02-30", "2026-13-01", "abcd-ef-gh"] {
        assert!(matches!(store.day_detail(bad.into()), Err(CoreError::InvalidInput(_))), "{bad}");
    }
    assert_eq!(store.day_detail("2024-02-29".into()).unwrap().day, "2024-02-29");
}

#[test]
fn day_numbers_round_trip() {
    assert_eq!(format_day(0), "1970-01-01");
    assert_eq!(parse_day("1970-01-01"), Some(0));
    assert_eq!(format_day(-1), "1969-12-31");
    for d in (-1000..30_000).step_by(37) {
        assert_eq!(parse_day(&format_day(d)), Some(d));
    }
    assert_eq!(format_day(days_from_civil(2026, 11, 5)), "2026-11-05");
}

#[test]
fn concurrent_calls_are_safe() {
    let db = TempDb::new("threads");
    let store = db.open(KST);
    let t0 = kst(2026, 11, 5, 9, 0);
    let handles: Vec<_> = (0..4)
        .map(|k| {
            let s = Arc::clone(&store);
            std::thread::spawn(move || {
                let fixes = drive(t0 + k * HOUR, 600, 37.5, 127.0);
                for c in fixes.chunks(30) {
                    s.ingest(c.to_vec()).unwrap();
                    s.day_summaries(5).unwrap();
                }
            })
        })
        .collect();
    for h in handles {
        h.join().unwrap();
    }
    assert_eq!(store.fix_count().unwrap(), 2400);
    assert_eq!(store.day_summaries(5).unwrap()[0].trip_count, 4);
}

// ---- 조금씩·순서 없이 넣은 결과가 한 번에 넣은 결과와 같은지 ----

/// 결정적인 의사 난수 (테스트 재현용).
struct Lcg(u64);

impl Lcg {
    fn next(&mut self) -> u64 {
        self.0 = self.0.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1_442_695_040_888_963_407);
        self.0 >> 33
    }

    fn below(&mut self, n: u64) -> u64 {
        self.next() % n
    }
}

/// 며칠치 가짜 기록: 주차·먼 곳 출발·드라이브스루·자정 넘김·신호 누락이 섞여 있다.
fn synthetic_days(rng: &mut Lcg, days: i64) -> (Vec<GeoFix>, Vec<(DriveEvent, i64)>) {
    let mut fixes = Vec::new();
    let mut events = Vec::new();
    let mut t = kst(2026, 10, 1, 7, 0);
    let (mut lat, mut lon) = (37.5, 127.0);
    let end_t = t + days * DAY_MS;
    while t < end_t {
        if rng.below(4) == 0 {
            lat += 0.05; // 먼 곳에서 출발 (기록 빠짐)
        }
        let n = 120 + rng.below(1500) as i64;
        let start = t;
        let heading = rng.below(4);
        for i in 0..n {
            if rng.below(400) == 0 {
                continue; // 가끔 빠진 점
            }
            let (dlat, dlon) = match heading {
                0 => (1e-4, 0.0),
                1 => (0.0, 1.2e-4),
                2 => (-1e-4, 0.0),
                _ => (0.0, -1.2e-4),
            };
            let wait = i > n / 2 && i < n / 2 + 700 && n > 1200; // 긴 주행 가운데 드라이브스루
            if !wait {
                lat += dlat;
                lon += dlon;
            }
            let mut f = gf(lat, lon, t + i * SEC);
            if rng.below(300) == 0 {
                f.lon += 0.2; // GPS 점프
            }
            if rng.below(500) == 0 {
                f.accuracy_m = 500.0; // 정확도 나쁜 점
            }
            fixes.push(f);
        }
        let end = t + (n - 1) * SEC;
        if rng.below(5) != 0 {
            events.push((DriveEvent::Start, start));
        }
        if rng.below(5) != 0 {
            events.push((DriveEvent::End, end));
        }
        // 다음 출발까지: 신호대기 수준 ~ 반나절
        t = end + [2 * MIN, 7 * MIN, 30 * MIN, 3 * HOUR, 9 * HOUR][rng.below(5) as usize] + rng.below(60) as i64 * SEC;
    }
    (fixes, events)
}

fn snapshot(store: &EodiStore) -> (Vec<DaySummary>, Vec<DayDetail>) {
    let days = store.day_summaries(10_000).unwrap();
    let details = days.iter().map(|d| store.day_detail(d.day.clone()).unwrap()).collect();
    (days, details)
}

#[test]
fn incremental_ingest_matches_one_shot() {
    for seed in [1u64, 7, 42] {
        let mut rng = Lcg(seed);
        let (fixes, events) = synthetic_days(&mut rng, 4);

        let one_db = TempDb::new("oneshot");
        let one = one_db.open(KST);
        one.ingest(fixes.clone()).unwrap();
        for (e, t) in &events {
            one.mark_drive_event(*e, *t).unwrap();
        }
        let want = snapshot(&one);
        assert!(want.0.len() >= 3, "테스트 데이터가 너무 적다: {:?}", want.0);
        assert!(want.0.iter().any(|d| d.stay_count > 0));

        // 크기가 제각각인 묶음을, 순서를 섞어서, 신호는 중간중간, 조회도 중간중간
        let inc_db = TempDb::new("incremental");
        let inc = inc_db.open(KST);
        let mut chunks: Vec<Vec<GeoFix>> = Vec::new();
        let mut rest = &fixes[..];
        while !rest.is_empty() {
            let k = (1 + rng.below(400) as usize).min(rest.len());
            chunks.push(rest[..k].to_vec());
            rest = &rest[k..];
        }
        for _ in 0..chunks.len() / 3 {
            let (i, j) = (rng.below(chunks.len() as u64) as usize, rng.below(chunks.len() as u64) as usize);
            chunks.swap(i, j);
        }
        let mut pending_events = events.clone();
        for c in chunks {
            inc.ingest(c).unwrap();
            if rng.below(3) == 0 && !pending_events.is_empty() {
                let (e, t) = pending_events.swap_remove(rng.below(pending_events.len() as u64) as usize);
                inc.mark_drive_event(e, t).unwrap();
            }
            if rng.below(4) == 0 {
                inc.day_summaries(3).unwrap();
            }
        }
        for (e, t) in pending_events {
            inc.mark_drive_event(e, t).unwrap();
            if rng.below(2) == 0 {
                inc.day_summaries(1).unwrap();
            }
        }
        let got = snapshot(&inc);
        assert_eq!(got.0, want.0, "seed {seed}");
        assert_eq!(got.1, want.1, "seed {seed}");
    }
}

// ---- 성능: cargo test --release -- --ignored --nocapture perf_ ----

/// 1초 간격으로 n개, 방향을 천천히 바꾸며 초속 약 15m로 달린다.
fn wiggly_drive(t0: i64, n: i64, lat0: f64, lon0: f64, seed: f64) -> Vec<GeoFix> {
    let (mut lat, mut lon) = (lat0, lon0);
    (0..n)
        .map(|i| {
            let heading = seed + (i as f64 / 90.0).sin() * 1.5 + i as f64 / 600.0;
            lat += heading.cos() * 15.0 / 111_000.0;
            lon += heading.sin() * 15.0 / 88_000.0;
            let jitter = ((i * 7919) % 11) as f64 / 11.0 - 0.5;
            gf(lat + jitter * 2e-5, lon + jitter * 2e-5, t0 + i * SEC)
        })
        .collect()
}

#[test]
#[ignore = "느림: --release -- --ignored 로 실행"]
fn perf_year_of_driving() {
    let db = TempDb::new("perf");
    let store = db.open(KST);
    let first_day = days_from_civil(2025, 11, 6);
    let mut batch_times = Vec::new();
    let mut flush_times = Vec::new();
    let mut total = 0usize;
    let started = Instant::now();
    for k in 0..365 {
        let day = first_day + k;
        let base = day * DAY_MS - KST as i64 * MIN;
        // 보통 날: 1시간 주행 2번(점 7,200개). 마지막 날: 5,000개씩 2번(점 10,000개).
        let n = if k == 364 { 5000 } else { 3600 };
        let morning = wiggly_drive(base + 8 * HOUR, n, 37.5, 127.0, k as f64);
        let (lat, lon) = last_point(&morning);
        let evening = wiggly_drive(base + 18 * HOUR, n, lat, lon, k as f64 + 1.0);
        for trip in [morning, evening] {
            store.mark_drive_event(DriveEvent::Start, trip[0].t_ms).unwrap();
            for (i, c) in trip.chunks(30).enumerate() {
                let s = Instant::now();
                store.ingest(c.to_vec()).unwrap();
                batch_times.push(s.elapsed().as_secs_f64() * 1e3);
                // 주행 중에 타임라인을 보고 있다고 치고 가끔 조회 (지금 주행 하나 다시 계산)
                if i % 40 == 39 {
                    let s = Instant::now();
                    store.day_summaries(1).unwrap();
                    flush_times.push(s.elapsed().as_secs_f64() * 1e3);
                }
            }
            total += trip.len();
            store.mark_drive_event(DriveEvent::End, trip.last().unwrap().t_ms).unwrap();
        }
        store.day_summaries(1).unwrap();
    }
    let build = started.elapsed();

    let s = Instant::now();
    let days = store.day_summaries(400).unwrap();
    let summaries_ms = s.elapsed().as_secs_f64() * 1e3;
    let last_day = format_day(first_day + 364);
    let s = Instant::now();
    let detail = store.day_detail(last_day.clone()).unwrap();
    let detail_ms = s.elapsed().as_secs_f64() * 1e3;
    let path_pts: usize = detail.trips.iter().map(|t| t.path.len()).sum();

    batch_times.sort_by(f64::total_cmp);
    flush_times.sort_by(f64::total_cmp);
    let pct = |v: &[f64], p: f64| v[((v.len() - 1) as f64 * p) as usize];
    let avg = |v: &[f64]| v.iter().sum::<f64>() / v.len() as f64;
    println!("fixes {total}, days {}, build {:.1}s", days.len(), build.as_secs_f64());
    println!(
        "ingest(30) avg {:.3}ms p50 {:.3}ms p99 {:.3}ms max {:.3}ms",
        avg(&batch_times),
        pct(&batch_times, 0.5),
        pct(&batch_times, 0.99),
        pct(&batch_times, 1.0)
    );
    println!(
        "주행 중 조회(지금 주행 재계산) avg {:.3}ms p99 {:.3}ms max {:.3}ms",
        avg(&flush_times),
        pct(&flush_times, 0.99),
        pct(&flush_times, 1.0)
    );
    println!("day_summaries(400) {summaries_ms:.3}ms");
    println!("day_detail({last_day}, 점 10,000개) {detail_ms:.3}ms, 경로 점 {path_pts}개");

    // 시간대가 바뀌어 날짜 요약 전체를 다시 만들 때
    drop(store);
    let s = Instant::now();
    let store = db.open(0);
    println!("시간대 변경 후 열기(요약 365일 재생성) {:.1}ms", s.elapsed().as_secs_f64() * 1e3);
    // 아침 8시(한국) 주행은 UTC로 전날 23시 → 첫날 앞에 하루가 더 생긴다
    assert_eq!(store.day_summaries(400).unwrap().len(), 366);
    drop(store);
    let store = db.open(KST);

    // 원본에서 전부 다시 계산 (최악: 한 번도 조회 안 하고 1년치가 밀린 경우)
    {
        let mut c = store.lock().unwrap();
        mark_dirty(&c, i64::MIN, i64::MAX).unwrap();
        let s = Instant::now();
        store.flush(&mut c).unwrap();
        println!("1년치 전체 재계산 {:.1}s", s.elapsed().as_secs_f64());
    }

    assert_eq!(days.len(), 365);
    assert_eq!(detail.trips.len(), 2);
    assert!(summaries_ms < 100.0, "day_summaries {summaries_ms}ms");
    assert!(detail_ms < 50.0, "day_detail {detail_ms}ms");
}

/// 기록기는 기어 P를 몇 초 확인하는 동안 위치를 더 받는다. 끝 신호 뒤에 들어온 그 점들은 주행이 아니다.
#[test]
fn fixes_after_end_signal_are_parked_not_a_trip() {
    let db = TempDb::new("parked-tail");
    let store = db.open(KST);
    let t0 = kst(2026, 11, 5, 9, 0);
    store.mark_drive_event(DriveEvent::Start, t0).unwrap();
    let moving = drive(t0, 300, 37.5, 127.0);
    let (lat, lon) = last_point(&moving);
    let t_end = t0 + 299 * SEC;
    // 주차 확인 대기 5초 동안 같은 자리 점 5개 → 끝 신호보다 늦게 저장된다 (실제 기록기 순서)
    let parked: Vec<GeoFix> = (1..=5).map(|i| gf(lat, lon, t_end + i * SEC)).collect();
    store.ingest(moving).unwrap();
    store.ingest(parked).unwrap();
    store.mark_drive_event(DriveEvent::End, t_end).unwrap();

    let d = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d.trips.len(), 1, "{:?}", d.trips);
    assert_eq!(d.trips[0].end_ms, t_end);
    assert_eq!(d.summary.trip_count, 1);

    // 다음 주행은 다시 시작 신호부터 정상으로 잡힌다
    let t1 = t_end + 30 * MIN;
    store.mark_drive_event(DriveEvent::Start, t1).unwrap();
    store.ingest(drive(t1, 120, lat, lon)).unwrap();
    let d = store.day_detail("2026-11-05".into()).unwrap();
    assert_eq!(d.trips.len(), 2);
    assert_eq!(d.stays.len(), 1, "주차 30분 = 머문 곳 하나");
}

//! 그날 다시 보기 테스트: 손으로 만든 하루 + 시연 1년치(오늘 = 2026-11-06).

use std::sync::atomic::{AtomicU32, Ordering};

use super::*;
use crate::ffi::GeoPoint;
use crate::places::{PlaceLabel, PlaceSource};
use crate::store::{days_from_civil, DaySummary, StaySummary, TripSummary};

const MIN: i64 = 60_000;
const KST: i32 = 540;

fn kst(h: i64, mi: i64) -> i64 {
    days_from_civil(2025, 11, 6) * DAY_MS + (h * 60 + mi) * MIN - KST as i64 * MIN
}

fn p(lat: f64, lon: f64) -> GeoPoint {
    GeoPoint { lat, lon }
}

fn trip(s: i64, e: i64, path: Vec<GeoPoint>) -> TripSummary {
    TripSummary { start_ms: s, end_ms: e, distance_m: 1000.0, start: path[0], end: *path.last().unwrap(), path, start_place: None, end_place: None }
}

fn stay(at: GeoPoint, a: i64, l: i64, order: u32, name: &str) -> StaySummary {
    let place = (!name.is_empty())
        .then(|| PlaceLabel { name: name.into(), category: String::new(), address: String::new(), source: PlaceSource::User });
    StaySummary { center: at, arrive_ms: a, leave_ms: l, place, order }
}

fn day(trips: Vec<TripSummary>, stays: Vec<StaySummary>) -> DayDetail {
    DayDetail {
        day: "2025-11-06".into(),
        trips,
        stays,
        summary: DaySummary {
            replayable: false,
            day: "2025-11-06".into(),
            trip_count: 3,
            stay_count: 0,
            distance_m: 384_200.0,
            drive_millis: (4 * 60 + 12) * MIN,
            first_ms: 0,
            last_ms: 0,
            stop_names: Vec::new(),
            title: Some("강릉 바다 하루".into()),
            tags: Vec::new(),
        },
        entries: Vec::new(),
    }
}

/// 집 → 해변(1시간 52분) → 순두부 → 집.
fn sample() -> DayDetail {
    let home = p(37.50, 127.00);
    let beach = p(37.80, 128.90);
    let tofu = p(37.79, 128.91);
    day(
        vec![
            trip(kst(7, 0), kst(9, 51), vec![home, p(37.6, 127.8), beach]),
            trip(kst(11, 43), kst(11, 55), vec![beach, tofu]),
            trip(kst(13, 0), kst(16, 0), vec![tofu, home]),
        ],
        vec![
            stay(home, kst(0, 0) - 10 * 60 * MIN, kst(7, 0), 1, "우리 집"),
            stay(beach, kst(9, 51), kst(11, 43), 2, "경포해변"),
            stay(tofu, kst(11, 55), kst(12, 55), 3, "초당할머니순두부"),
            stay(home, kst(16, 0), kst(16, 0), 4, "우리 집"),
        ],
    )
}

#[test]
fn scenes_skip_home_and_carry_time_and_stay_text() {
    let today = days_from_civil(2026, 11, 6);
    let r = build_replay(&sample(), KST, today, Some(EpisodeInfo { id: 7, label: "강릉 여행".into(), region: "강원 강릉시".into() }));
    let names: Vec<&str> = r.scenes.iter().map(|s| s.stop.name.as_str()).collect();
    assert_eq!(names, ["경포해변", "초당할머니순두부"]);
    assert_eq!(r.scenes[0].time_text, "09:51");
    assert_eq!(r.scenes[0].stay_text, "1시간 52분 머묾");
    assert_eq!(r.scenes[1].order, 2);
    assert_eq!(r.stamp, "1년 전 오늘");
    assert_eq!(r.years_ago, 1);
    assert_eq!(r.date_text, "2025년 11월 6일 목요일");
    assert_eq!(r.title, "강릉 바다 하루");
    assert_eq!(r.subtitle, "가을 · 강원 강릉시");
    assert_eq!(r.summary_line, "그날, 우리는 384km를 달렸어요");
    assert_eq!(r.stats_line, "들른 곳 2곳 · 운전 4시간 12분");
    assert_eq!(r.episode_id, Some(7));
    assert!(r.bounds.is_some());
}

#[test]
fn each_scene_draws_its_leg_in_frames_ending_at_the_stop() {
    let r = build_replay(&sample(), KST, days_from_civil(2026, 11, 6), None);
    let s0 = &r.scenes[0];
    assert_eq!(s0.leg_frames.len(), LEG_FRAMES);
    // 첫 단계는 짧고, 마지막 단계는 해변(128.9)에 차가 있다
    assert!(s0.leg_frames[0].len() < s0.leg_frames[LEG_FRAMES - 1].len());
    assert!(s0.leg_frames[LEG_FRAMES - 1].contains(r#"[128.900000,37.800000]},"properties":{"kind":"car"}"#));
    // 첫 장면 전에는 지나온 길이 없고, 둘째 장면에는 첫 구간이 지나온 길로 남는다
    assert!(!s0.done_geojson.contains(r#""kind":"route""#));
    assert!(r.scenes[1].done_geojson.contains(r#""kind":"route""#));
    assert!(r.scenes[1].done_geojson.contains(r#""order":2,"now":true"#));
    // 집으로 오는 길은 끝 지도에만
    assert_eq!(r.final_geojson.matches(r#""kind":"route""#).count(), 3);
    assert_eq!(r.subtitle, "가을");
}

#[test]
fn leg_prefix_cuts_by_distance() {
    let leg = [(0.0, 0.0), (0.0, 1.0), (0.0, 2.0)];
    let half = leg_prefix(&leg, 0.5);
    assert_eq!(half.len(), 2);
    assert!((half[1].1 - 1.0).abs() < 1e-9);
    let q = leg_prefix(&leg, 0.25);
    assert!((q.last().unwrap().1 - 0.5).abs() < 1e-9);
    assert_eq!(leg_prefix(&leg, 1.0), leg.to_vec());
}

#[test]
fn a_day_without_named_places_has_no_scenes_but_a_summary() {
    let d = day(vec![trip(kst(9, 0), kst(10, 0), vec![p(37.5, 127.0), p(37.6, 127.1)])], vec![stay(p(37.6, 127.1), kst(10, 0), kst(11, 0), 1, "")]);
    let r = build_replay(&d, KST, days_from_civil(2025, 12, 1), None);
    assert!(r.scenes.is_empty());
    assert_eq!(r.stamp, "");
    assert!(r.final_geojson.contains(r#""kind":"route""#));
}

#[test]
fn seeded_on_this_day_replays_with_scenes() {
    static N: AtomicU32 = AtomicU32::new(0);
    let path = std::env::temp_dir().join(format!("eodi-replay-{}-{}.db", std::process::id(), N.fetch_add(1, Ordering::Relaxed)));
    let now = days_from_civil(2026, 11, 6) * DAY_MS + 3 * 3_600_000;
    let store = EodiStore::open(path.to_string_lossy().into_owned(), KST).unwrap();
    store.seed_demo_data(now).unwrap();
    let otd = store.on_this_day(now).unwrap().expect("시연 데이터에 1년 전 오늘이 있다");
    let r = store.day_replay(otd.day.clone(), now).unwrap();
    assert!(!r.scenes.is_empty());
    assert!(r.stamp.ends_with("오늘"));
    assert!(r.episode_id.is_some());
    assert!(r.distance_m > 0.0);
    drop(store);
    for s in ["", "-wal", "-shm"] {
        let _ = std::fs::remove_file(format!("{}{s}", path.display()));
    }
}

#[test]
fn leg_flight_follows_the_leg_and_flies_higher_on_long_legs() {
    let r = build_replay(&sample(), KST, days_from_civil(2026, 11, 6), None);
    let f = &r.scenes[0].leg_flight;
    assert_eq!(f.len(), LEG_FLIGHT_FRAMES);
    // 끝 장면은 해변(128.9, 37.8) 근처
    let last = f.last().unwrap();
    assert!((last.lon - 128.9).abs() < 0.01 && (last.lat - 37.8).abs() < 0.01);
    assert!(f.iter().all(|x| (10.5..=15.0).contains(&x.zoom)));
    assert!(leg_flight_zoom(1_000.0) > leg_flight_zoom(200_000.0));
    assert_eq!(leg_flight_zoom(200_000.0), 10.5);
    assert!(leg_flight(&[(37.0, 127.0)]).is_empty());
}

#[test]
fn a_short_day_ends_on_the_stops_not_the_distance() {
    let home = p(37.50, 127.00);
    let market = p(37.52, 127.01);
    let cafe = p(37.53, 127.02);
    let mut d = day(
        vec![
            trip(kst(10, 0), kst(10, 2), vec![home, market]),
            trip(kst(11, 0), kst(11, 3), vec![market, cafe]),
            trip(kst(12, 0), kst(12, 4), vec![cafe, home]),
        ],
        vec![
            stay(market, kst(10, 2), kst(10, 50), 1, "광장시장"),
            stay(cafe, kst(11, 3), kst(12, 0), 2, "북촌 카페"),
            stay(home, kst(12, 4), kst(18, 0), 3, "우리집"),
        ],
    );
    d.summary.distance_m = 4_500.0;
    d.summary.drive_millis = 5 * MIN;
    let r = build_replay(&d, KST, days_from_civil(2026, 11, 6), None);
    assert_eq!(r.summary_line, "그날, 광장시장부터 2곳에 들렀어요");
    assert_eq!(r.stats_line, "들른 곳 2곳 · 4.5km");

    d.stays.truncate(1);
    let r = build_replay(&d, KST, days_from_civil(2026, 11, 6), None);
    assert_eq!(r.summary_line, "그날, 광장시장에 들렀어요");
}

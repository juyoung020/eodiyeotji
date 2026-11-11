//! 회상 · 요약 테스트. 시연 1년치(오늘 = 2026-11-06)로 확인한다. DB는 임시 파일, 끝나면 지운다.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;
use std::time::Instant;

use super::*;
use crate::geo::haversine_m;
use crate::store::{days_from_civil, parse_day, DriveEvent, GeoFix};

const MIN: i64 = 60_000;
const KST: i32 = 540;

struct TempDb(PathBuf);

impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let db = TempDb(std::env::temp_dir().join(format!("eodi-recap-{name}-{}-{n}.db", std::process::id())));
        db.remove();
        db
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

/// 한국 시각 → UTC ms.
fn kst(y: i64, m: i64, d: i64, h: i64, mi: i64) -> i64 {
    days_from_civil(y, m, d) * DAY_MS + (h * 60 + mi) * MIN - KST as i64 * MIN
}

fn today() -> i64 {
    kst(2026, 11, 6, 12, 0)
}

fn seeded(name: &str) -> (TempDb, Arc<EodiStore>) {
    let db = TempDb::new(name);
    let store = EodiStore::open(db.0.to_string_lossy().into_owned(), KST).expect("open");
    assert!(store.seed_demo_data(today()).unwrap() > 600);
    (db, store)
}

fn at(lat: f64, lon: f64) -> Vec<GeoPoint> {
    vec![GeoPoint { lat, lon }]
}

const CHODANG: (f64, f64) = (37.7915, 128.9142);
const GYEONGPO: (f64, f64) = (37.8055, 128.9083);
const ANMOK: (f64, f64) = (37.7722, 128.9474);
const HAEUNDAE: (f64, f64) = (35.1587, 129.1604);
const SEORAK: (f64, f64) = (38.1727, 128.4890);
const HOME: (f64, f64) = (37.5530, 127.0335);

// ---------------------------------------------------------------- 말 만들기

#[test]
fn lines_use_the_right_particles_and_time_words() {
    let d = |y, m, dd| days_from_civil(y, m, dd);
    let today = d(2026, 11, 6);
    assert_eq!(when_text(d(2025, 11, 6), today), "1년 전 가을");
    assert_eq!(when_text(d(2024, 7, 1), today), "2년 전 여름");
    assert_eq!(when_text(d(2025, 11, 7), today), "작년 11월", "1년이 하루 모자라면 아직 작년");
    assert_eq!(when_text(d(2026, 8, 7), today), "8월");
    assert_eq!(when_text(d(2024, 1, 10), today), "2년 전 겨울");
    assert_eq!(recall_line("2년 전 가을", "초당할머니순두부"), "2년 전 가을에 들렀던 초당할머니순두부가 근처에 있어요");
    assert_eq!(recall_line("8월", "경포해변"), "8월에 들렀던 경포해변이 근처에 있어요");
    assert_eq!(on_this_day_line(1, "강릉 여행"), "1년 전 오늘은 강릉 여행을 다녀왔어요");
    assert_eq!(on_this_day_line(3, "성수 나들이"), "3년 전 오늘은 성수 나들이를 다녀왔어요");
}

#[test]
fn trip_days_join_into_runs() {
    let days: BTreeSet<i64> = [7, 8, 9, 20, 22, 23].into_iter().collect();
    assert_eq!(runs(&days), vec![vec![7, 8, 9], vec![20], vec![22, 23]]);
    assert!(runs(&BTreeSet::new()).is_empty());
}

// ---------------------------------------------------------------- N년 전 오늘

#[test]
fn one_year_ago_today_is_the_gangneung_trip() {
    let (_db, store) = seeded("otd");
    let otd = store.on_this_day(today()).unwrap().expect("1년 전 강릉 여행");
    assert_eq!((otd.years_ago, otd.day.as_str()), (1, "2025-11-06"));
    assert_eq!(otd.kind, EpisodeKind::Trip);
    assert_eq!(otd.label, "강릉 여행");
    assert_eq!(otd.title, "강릉 여행", "LLM 라벨 전이면 규칙 제목");
    assert_eq!(otd.region, "강원 강릉시");
    assert_eq!(otd.line, "1년 전 오늘은 강릉 여행을 다녀왔어요");
    let photos: Vec<&str> = otd.photos.iter().map(|s| s.name.as_str()).collect();
    assert_eq!(photos, ["경포해변", "초당할머니순두부", "안목해변 카페거리"], "휴게소는 사진 뒤로");
    assert_eq!(otd.stops.len(), 4);
    assert!(otd.distance_m > 300_000.0, "서울 ↔ 강릉 왕복: {}", otd.distance_m);
    assert!(!otd.dismissed && !otd.spoken);
    assert!(otd.replayable);

    // 오늘 닫음 · 읽음은 오늘만
    store.dismiss_on_this_day(today()).unwrap();
    store.mark_on_this_day_spoken(today()).unwrap();
    let again = store.on_this_day(today() + 3 * 3_600_000).unwrap().unwrap();
    assert!(again.dismissed && again.spoken);
    if let Some(next) = store.on_this_day(today() + DAY_MS).unwrap() {
        assert!(!next.dismissed && !next.spoken, "다음 날은 다시");
        assert_ne!(next.day, "2025-11-06");
    }
    // 1년 전 같은 날에 나들이 · 여행이 없으면 없음 (기록 시작 전 날)
    assert_eq!(store.on_this_day(kst(2026, 11, 5, 12, 0)).unwrap(), None);
}

// ---------------------------------------------------------------- 지나갈 때 회상

#[test]
fn passing_a_remembered_place_recalls_it_once() {
    let (_db, store) = seeded("recall");
    let t = today();
    let r = store.recall_near(at(CHODANG.0 + 0.001, CHODANG.1), t).unwrap().expect("초당할머니순두부 근처");
    assert_eq!(r.place.name, "초당할머니순두부");
    assert_eq!(r.line, "1년 전 가을에 들렀던 초당할머니순두부가 근처에 있어요");
    assert_eq!((r.years_ago, r.day.as_str(), r.when_text.as_str()), (1, "2025-11-06", "1년 전 가을"));
    assert_eq!(r.day_title, "강릉 여행");
    assert!(r.distance_m > 80.0 && r.distance_m < 150.0, "{}", r.distance_m);

    // 묶음의 점 하나만 가까워도 된다 (지나가는 중)
    let pass = vec![GeoPoint { lat: 37.70, lon: 128.95 }, GeoPoint { lat: CHODANG.0, lon: CHODANG.1 + 0.002 }];
    assert_eq!(store.recall_near(pass, t).unwrap().map(|r| r.place.name).as_deref(), Some("초당할머니순두부"));

    // 400m 밖이면 없음
    assert_eq!(store.recall_near(at(CHODANG.0 + 0.0045, CHODANG.1 + 0.003), t).unwrap(), None);

    // 보여 주면: 15분 안에는 다른 곳도 쉬고, 같은 곳은 1주일 동안 다시 안 나온다
    store.mark_recall_shown(r.key.clone(), t).unwrap();
    assert_eq!(store.recall_near(at(GYEONGPO.0, GYEONGPO.1), t + 5 * MIN).unwrap(), None, "최소 간격");
    assert_eq!(store.recall_near(at(CHODANG.0, CHODANG.1), t + 20 * MIN).unwrap(), None, "같은 곳 1주일");
    let g = store.recall_near(at(GYEONGPO.0, GYEONGPO.1), t + 20 * MIN).unwrap().expect("경포해변");
    assert_eq!(g.line, "1년 전 가을에 들렀던 경포해변이 근처에 있어요");
    store.mark_recall_shown(g.key, t + 20 * MIN).unwrap();
    let a = store.recall_near(at(ANMOK.0, ANMOK.1), t + 40 * MIN).unwrap().expect("안목해변");
    store.mark_recall_shown(a.key, t + 40 * MIN).unwrap();
    // 하루 3번까지
    assert_eq!(store.recall_near(at(HAEUNDAE.0, HAEUNDAE.1), t + 90 * MIN).unwrap(), None, "하루 3번");
    // 다음 날: 다른 곳은 되고, 어제 회상한 곳은 아직 안 된다
    let h = store.recall_near(at(HAEUNDAE.0, HAEUNDAE.1), t + DAY_MS).unwrap().expect("해운대");
    assert_eq!(h.line, "8월에 들렀던 해운대해수욕장이 근처에 있어요");
    assert_eq!(store.recall_near(at(CHODANG.0, CHODANG.1), t + DAY_MS).unwrap(), None);
    // 1주일 지나면 다시
    assert!(store.recall_near(at(CHODANG.0, CHODANG.1), t + 8 * DAY_MS).unwrap().is_some());
}

#[test]
fn recent_places_home_and_errands_are_not_recalled() {
    let (_db, store) = seeded("recall-skip");
    let t = today();
    assert_eq!(store.recall_near(at(SEORAK.0, SEORAK.1), t).unwrap(), None, "13일 전에 다녀온 곳");
    assert_eq!(store.recall_near(at(HOME.0, HOME.1), t).unwrap(), None, "집");
    assert_eq!(store.recall_near(vec![], t).unwrap(), None);
    assert!(store.recall_near(at(f64::NAN, 0.0), t).is_err());
    assert!(store.mark_recall_shown(String::new(), t).is_err());
    // 한 달 뒤에는 설악산도 회상할 수 있다
    let later = kst(2026, 12, 1, 12, 0);
    let s = store.recall_near(at(SEORAK.0, SEORAK.1), later).unwrap().expect("설악산");
    assert_eq!(s.when_text, "10월");
}

// ---------------------------------------------------------------- 요약

#[test]
fn month_and_year_recaps_add_up() {
    let (_db, store) = seeded("summary");
    let t0 = Instant::now();
    let oct = store.month_recap(2026, 10, today()).unwrap();
    let month_ms = t0.elapsed().as_secs_f64() * 1000.0;
    assert!(!oct.in_progress && oct.has_prev && oct.has_next);
    assert_eq!(oct.totals.trips, 1, "설악산 1박 2일 = 여행 1번");
    assert!(oct.totals.distance_m > 500_000.0 && oct.totals.drive_millis > 10 * 3_600_000);
    assert!(oct.totals.drive_days >= 18 && oct.record_days >= oct.totals.drive_days);
    let best = oct.best_day.as_ref().expect("최고의 날");
    assert_eq!((best.day.as_str(), best.kind), ("2026-10-24", EpisodeKind::Trip));
    assert!(best.photo.is_some());
    assert!(!oct.top_places.is_empty() && oct.top_places.len() <= 5);
    assert!(oct.top_places.windows(2).all(|w| w[0].days >= w[1].days));
    assert!(oct.top_places.iter().all(|p| p.place.name != "응봉동 현대아파트" && p.place.name != "센터원빌딩"), "집 · 회사 빼기");
    assert!(oct.regions.iter().any(|r| r.region.starts_with("강원")), "{:?}", oct.regions);
    assert!(oct.has_prev_records);

    // 이번 달(진행 중): 지난달 같은 날짜까지와 비교
    let nov = store.month_recap(2026, 11, today()).unwrap();
    assert!(nov.in_progress && !nov.has_next && nov.record_days <= 5);
    let days = store.day_summaries(1000).unwrap();
    let oct_1_6: f64 = days.iter().filter(|d| ("2026-10-01".."2026-10-07").contains(&d.day.as_str())).map(|d| d.distance_m).sum();
    assert!((nov.vs_prev_distance_m - (nov.totals.distance_m - oct_1_6)).abs() < 1.0, "지난달 1~6일과 비교");

    // 기록 시작 달: 앞 달 없음, 첫 방문이 많다
    let first = store.month_recap(2025, 11, today()).unwrap();
    assert!(!first.has_prev && first.totals.new_places >= 5 && first.totals.trips == 1);
    assert!(store.month_recap(2026, 13, today()).is_err());

    let t1 = Instant::now();
    let y = store.year_recap(2026, today()).unwrap();
    let year_ms = t1.elapsed().as_secs_f64() * 1000.0;
    println!("month {month_ms:.1}ms, year {year_ms:.1}ms, 2026 {:?}", y.totals);
    assert!(y.in_progress && y.has_prev && !y.has_next);
    assert_eq!(y.totals.trips, 2, "부산 · 설악");
    let longest = y.longest_trip.as_ref().expect("가장 긴 여행");
    assert_eq!((longest.day.as_str(), longest.span_days, longest.region.as_str()), ("2026-08-07", 3, "부산 해운대구"));
    assert_eq!(longest.title, "여름휴가 첫날, 해운대 바다", "AI 제목");
    assert_eq!(y.seasons.iter().map(|s| s.name.as_str()).collect::<Vec<_>>(), ["봄", "여름", "가을", "겨울"]);
    assert!(y.seasons.iter().all(|s| s.distance_m > 0.0));
    // 계절 칸 "나들이 · 여행 N번"은 머리 칸(나들이 N번 · 여행 N번)과 같은 셈 (계절을 넘는 여행이 없으면 합이 같다)
    let season_sum: u32 = y.seasons.iter().map(|s| s.outings).sum();
    assert_eq!(season_sum, y.totals.outings + y.totals.trips, "{:?}", y.seasons.iter().map(|s| s.outings).collect::<Vec<_>>());
    assert_eq!(y.month_distance_m.len(), 12);
    assert_eq!(y.month_distance_m[11], 0.0, "12월은 아직");
    let sum: f64 = y.month_distance_m.iter().sum();
    assert!((sum - y.totals.distance_m).abs() < 1.0);
    assert_eq!(y.top_places.len(), 5);
    assert!(y.totals.new_places > 10 && y.totals.outings > 10, "{:?}", y.totals);
    assert!(year_ms < 500.0, "1년치 결산 {year_ms:.1}ms");

    let y25 = store.year_recap(2025, today()).unwrap();
    assert!(!y25.in_progress && !y25.has_prev && y25.has_next);
    assert_eq!(y25.longest_trip.map(|d| d.day).as_deref(), Some("2025-11-06"));
    assert_eq!(parse_day("2025-11-06").map(|d| d >= 0), Some(true));
}

/// t0에 a에서 b까지 1초 간격 초속 15m로 곧게 달린다 (시작 신호, [end]면 끝 신호도). 반환: 도착 시각.
fn drive(store: &EodiStore, t0: i64, a: (f64, f64), b: (f64, f64), end: bool) -> i64 {
    let (p, q) = (Point { lat: a.0, lon: a.1 }, Point { lat: b.0, lon: b.1 });
    let n = (haversine_m(p, q) / 15.0).ceil().max(2.0) as i64;
    let fixes: Vec<GeoFix> = (0..=n)
        .map(|i| {
            let f = i as f64 / n as f64;
            GeoFix { lat: p.lat + (q.lat - p.lat) * f, lon: p.lon + (q.lon - p.lon) * f, t_ms: t0 + i * 1000, accuracy_m: 5.0 }
        })
        .collect();
    store.mark_drive_event(DriveEvent::Start, t0).unwrap();
    store.ingest(fixes).unwrap();
    if end {
        store.mark_drive_event(DriveEvent::End, t0 + n * 1000).unwrap();
    }
    t0 + n * 1000
}

#[test]
fn the_drive_in_progress_is_not_a_recent_visit_but_earlier_today_is() {
    let (_db, store) = seeded("recall-today");
    // 오늘 아침 경포해변에 들러 머물렀다가 (09:00 ~ 도착, 끝 신호)
    drive(&store, kst(2026, 11, 6, 9, 0), (37.7700, 128.9000), GYEONGPO, true);
    // 지금 초당할머니순두부 쪽으로 달리는 중 (11:58 경포에서 출발 ~ 지금, 아직 끝 신호 없음)
    let now = drive(&store, kst(2026, 11, 6, 11, 58), GYEONGPO, (CHODANG.0 - 0.001, CHODANG.1), false);
    store.day_summaries(5).unwrap(); // 화면이 조회한 것처럼 주행 · 에피소드를 다시 만든다
    let r = store.recall_near(at(CHODANG.0 - 0.001, CHODANG.1), now + 1000).unwrap();
    assert_eq!(r.map(|r| r.place.name).as_deref(), Some("초당할머니순두부"), "달리는 중인 주행의 끝은 들른 곳이 아니다");
    assert_eq!(store.recall_near(at(GYEONGPO.0, GYEONGPO.1), now + 1000).unwrap(), None, "오늘 아침 들른 곳");
}

// ---------------------------------------------------------------- 기억 지도 소개 카드

#[test]
fn memory_map_intro_shows_after_a_month_until_dismissed() {
    let (_db, store) = seeded("memmap-intro");
    assert!(store.memory_map_intro(today()).unwrap(), "1년치 기록");
    store.dismiss_memory_map_intro().unwrap();
    assert!(!store.memory_map_intro(today()).unwrap(), "닫으면 다시 안 나온다");
}

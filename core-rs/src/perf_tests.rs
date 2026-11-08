//! 핫 API 시간 측정 (1년치·3년치 시연 기록). `cargo test --release perf_hot -- --ignored --nocapture --test-threads=1`
//! 기능 검증이 아니라 숫자를 보는 용도라 기본 실행에서는 뺀다.

use crate::community::drive::DriveRouteSort;
use crate::community::CommunitySort;
use crate::search_ui::{SearchFilter, SearchScope};
use crate::store::{EodiStore, GeoFix};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Instant;

const KST: i32 = 540;
const DAY: i64 = 86_400_000;
// 2026-11-06 12:00 KST
const TODAY: i64 = 1_793_934_000_000;

struct TempDb(std::path::PathBuf);
impl TempDb {
    fn new(tag: &str) -> Self {
        let db = TempDb(std::env::temp_dir().join(format!("eodi-perf-{tag}-{}.db", std::process::id())));
        db.remove();
        db
    }
    fn open(&self) -> Arc<EodiStore> {
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

fn seed(store: &EodiStore, years: i64) {
    for y in 0..years {
        if y > 0 {
            store.lock().unwrap().execute("DELETE FROM meta WHERE key = 'demo_seeded'", []).unwrap();
        }
        store.seed_demo_data(TODAY - y * 365 * DAY).unwrap();
    }
    store.seed_community_demo(TODAY).unwrap();
}

/// 첫 호출(차가움)과 같은 호출 반복의 중앙값(따뜻함)을 ms로.
fn time<T>(name: &str, mut f: impl FnMut() -> T) {
    let s = Instant::now();
    std::hint::black_box(f());
    let cold = s.elapsed().as_secs_f64() * 1e3;
    let mut v: Vec<f64> = (0..9)
        .map(|_| {
            let s = Instant::now();
            std::hint::black_box(f());
            s.elapsed().as_secs_f64() * 1e3
        })
        .collect();
    v.sort_by(f64::total_cmp);
    println!("| {name:<28} | {cold:>9.2} | {:>9.2} |", v[4]);
}

fn run(years: i64) {
    let db = TempDb::new(&format!("y{years}"));
    let store = db.open();
    let s = Instant::now();
    seed(&store, years);
    println!("\n### {years}년치: 점 {}개, seed {:.1}s", store.fix_count().unwrap(), s.elapsed().as_secs_f64());
    // 처음 여는 앱처럼 다시 연다 (seed가 만든 메모리 상태를 버린다)
    drop(store);
    let s = Instant::now();
    let store = db.open();
    println!("open {:.2}ms", s.elapsed().as_secs_f64() * 1e3);
    let days = store.day_summaries(2000).unwrap();
    let busy = days.iter().max_by_key(|d| d.trip_count).unwrap().day.clone();
    let place = store.review_candidates(1).unwrap().into_iter().next();
    println!("| API | 첫 호출 ms | 반복 중앙값 ms |\n|---|---:|---:|");
    time("day_summaries(400)", || store.day_summaries(400).unwrap());
    time("day_detail(바쁜 날)", || store.day_detail(busy.clone()).unwrap());
    time("day_map(바쁜 날)", || store.day_map(busy.clone()).unwrap());
    time("day_replay(바쁜 날)", || store.day_replay(busy.clone(), TODAY).unwrap());
    time("episodes_for_day", || store.episodes_for_day(busy.clone()).unwrap());
    time("search_typed(카페)", || {
        store.search_typed("카페".into(), SearchScope::Records, SearchFilter::none(), TODAY).unwrap()
    });
    time("search_typed(리뷰)", || {
        store.search_typed("카페".into(), SearchScope::Reviews, SearchFilter::none(), TODAY).unwrap()
    });
    time("search_suggestions", || store.search_suggestions().unwrap());
    if let Some(p) = &place {
        time("place_detail", || store.place_detail(p.clone()).unwrap());
    }
    time("community_courses", || store.community_courses(CommunitySort::Popular, None, 50).unwrap());
    time("place_reviews", || store.place_reviews(CommunitySort::Recent, None, 50).unwrap());
    time("popular_places", || store.popular_places(None, 30).unwrap());
    time("board_posts", || store.board_posts(CommunitySort::Recent, 50).unwrap());
    time("drive_routes", || store.drive_routes(DriveRouteSort::Popular, None, 50).unwrap());
    time("review_candidates(20)", || store.review_candidates(20).unwrap());
    time("share_candidates(20)", || store.share_candidates(20).unwrap());
    time("month_recap", || store.month_recap(2026, 10, TODAY).unwrap());
    time("year_recap", || store.year_recap(2026, TODAY).unwrap());
    time("on_this_day", || store.on_this_day(TODAY).unwrap());
    time("frequent_places(10)", || store.frequent_places(10, TODAY).unwrap());
    time("saved_places", || store.saved_places().unwrap());
    let from = days.last().unwrap().day.clone();
    let to = days.first().unwrap().day.clone();
    time("memory_map(전체)", || store.memory_map(from.clone(), to.clone()).unwrap());
    // 상세 · 보조 화면과 백그라운드 경로
    let route_id = store.drive_routes(DriveRouteSort::Popular, None, 1).unwrap()[0].route.id.clone();
    let course_id = store.community_courses(CommunitySort::Popular, None, 1).unwrap()[0].id.clone();
    let board_id = store.board_posts(CommunitySort::Recent, 1).unwrap()[0].id.clone();
    time("drive_route(상세)", || store.drive_route(route_id.clone()).unwrap());
    time("drive_route_map", || store.drive_route_map(route_id.clone()).unwrap());
    time("drive_route_flight(600)", || store.drive_route_flight(route_id.clone(), 600).unwrap());
    time("course_map", || store.course_map(course_id.clone()).unwrap());
    time("course_stop_reviews", || store.course_stop_reviews(course_id.clone()).unwrap());
    time("board_post+comments", || (store.board_post(board_id.clone()).unwrap(), store.board_comments(board_id.clone()).unwrap()));
    time("share_card_day", || store.share_card_day(busy.clone(), 1.0).unwrap());
    time("share_card_course", || store.share_card_course(course_id.clone(), 1.0).unwrap());
    time("drive_share_candidates(30)", || store.drive_share_candidates(30).unwrap());
    time("hidden_records", || store.hidden_records().unwrap());
    time("home_office", || store.home_office().unwrap());
    time("claimed_coupons", || store.claimed_coupons().unwrap());
    time("last_known_position", || store.last_known_position().unwrap());
    time("pending_places(20)", || store.pending_places(20).unwrap());
    time("pending_day_enrichments(5)", || store.pending_day_enrichments(5).unwrap());
    let pts: Vec<crate::ffi::GeoPoint> = (0..10).map(|i| crate::ffi::GeoPoint { lat: 37.57 + i as f64 * 1e-4, lon: 126.98 }).collect();
    time("recall_near(10점)", || store.recall_near(pts.clone(), TODAY).unwrap());
    {
        let mut t = TODAY + 7_200_000;
        time("ingest(10점 묶음, 주행 중)", || {
            let fixes = (0..10)
                .map(|i| GeoFix { t_ms: t + i * 1000, lat: 37.5 + (t - TODAY) as f64 * 1e-9, lon: 127.0 + (t - TODAY) as f64 * 1e-9, accuracy_m: 5.0 })
                .collect();
            t += 10_000;
            store.ingest(fixes).unwrap()
        });
    }

    // 기록 중(1초마다 위치 묶음 저장)에 화면 읽기가 얼마나 기다리는지
    let stop = Arc::new(AtomicBool::new(false));
    let w = {
        let (store, stop) = (store.clone(), stop.clone());
        std::thread::spawn(move || {
            let mut t = TODAY + 3_600_000;
            while !stop.load(Ordering::Relaxed) {
                let fixes = (0..10)
                    .map(|i| GeoFix { t_ms: t + i * 100, lat: 37.5 + (t % 1000) as f64 * 1e-6, lon: 127.0, accuracy_m: 5.0 })
                    .collect();
                store.ingest(fixes).unwrap();
                t += 1000;
                std::thread::sleep(std::time::Duration::from_millis(5));
            }
        })
    };
    let readers: Vec<_> = (0..3)
        .map(|k| {
            let store = store.clone();
            let busy = busy.clone();
            std::thread::spawn(move || {
                let s = Instant::now();
                for _ in 0..10 {
                    match k {
                        0 => drop(store.day_summaries(400).unwrap()),
                        1 => drop(store.day_detail(busy.clone()).unwrap()),
                        _ => drop(store.place_reviews(CommunitySort::Recent, None, 50).unwrap()),
                    }
                }
                s.elapsed().as_secs_f64() * 1e3 / 10.0
            })
        })
        .collect();
    let r: Vec<f64> = readers.into_iter().map(|h| h.join().unwrap()).collect();
    stop.store(true, Ordering::Relaxed);
    w.join().unwrap();
    println!("| 기록 중 동시 읽기(요약/상세/리뷰) | {:.2} / {:.2} / {:.2} | |", r[0], r[1], r[2]);

    // 무거운 쓰기(집·회사 변경 → 에피소드 전부 다시 계산)가 도는 동안 화면 조회가 기다리는 최대 시간
    let heavy = {
        let store = store.clone();
        std::thread::spawn(move || {
            let s = Instant::now();
            for i in 0..3 {
                store.set_home_office(None, Some(crate::ffi::GeoPoint { lat: 37.5 + i as f64 * 0.01, lon: 127.0 })).unwrap();
            }
            s.elapsed().as_secs_f64() * 1e3 / 3.0
        })
    };
    std::thread::sleep(std::time::Duration::from_millis(5));
    let mut worst = 0f64;
    while !heavy.is_finished() {
        let s = Instant::now();
        store.place_reviews(CommunitySort::Recent, None, 50).unwrap();
        store.drive_routes(DriveRouteSort::Popular, None, 50).unwrap();
        worst = worst.max(s.elapsed().as_secs_f64() * 1e3);
    }
    let w_ms = heavy.join().unwrap();
    println!("| 무거운 쓰기({w_ms:.0}ms) 중 피드 읽기 최대 대기 | {worst:.2} | |");
}

#[test]
#[ignore = "느림: --release -- --ignored 로 실행"]
fn perf_hot_apis() {
    run(1);
    run(3);
}

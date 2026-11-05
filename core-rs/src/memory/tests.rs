//! 기억 테스트. DB는 임시 폴더의 파일을 쓰고 끝나면 지운다.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;
use std::time::Instant;

use super::*;
use crate::geo::haversine_m;
use crate::places::{PlaceLabel, PlaceName, PlaceSource};
use crate::store::{days_from_civil, format_day, DriveEvent, GeoFix, DAY_MS};

const MIN: i64 = 60_000;
const HOUR: i64 = 60 * MIN;
const KST: i32 = 540;

struct TempDb(PathBuf);

impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let db = TempDb(std::env::temp_dir().join(format!("eodi-mem-{name}-{}-{n}.db", std::process::id())));
        db.remove();
        db
    }

    fn path(&self) -> String {
        self.0.to_string_lossy().into_owned()
    }

    fn open(&self) -> Arc<EodiStore> {
        EodiStore::open(self.path(), KST).expect("open")
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

type Spot = (&'static str, &'static str, &'static str, f64, f64);

const HOME: Spot = ("응봉동 현대아파트", "아파트", "서울 성동구 응봉동 265", 37.5530, 127.0335);
const OFFICE: Spot = ("센터원빌딩", "빌딩", "서울 중구 수하동 67", 37.5672, 126.9844);
const EMART: Spot = ("이마트 성수점", "대형마트", "서울 성동구 성수동2가 333-16", 37.5395, 127.0531);
const CAFE: Spot = ("대림창고", "카페", "서울 성동구 성수동2가 322-32", 37.5419, 127.0566);
const BEACH: Spot = ("경포해변", "해수욕장", "강원 강릉시 안현동 산1", 37.8055, 128.9083);
const TOFU: Spot = ("초당할머니순두부", "두부전문점", "강원 강릉시 초당동 309-1", 37.7915, 128.9142);
const SPOTS: [Spot; 6] = [HOME, OFFICE, EMART, CAFE, BEACH, TOFU];

fn pt(s: Spot) -> GeoPoint {
    GeoPoint { lat: s.3, lon: s.4 }
}

/// t0에 a에서 b까지 1초 간격, 초속 v m로 곧게 달린다 (시작·끝 신호 포함). 반환: 도착 시각.
fn drive(store: &EodiStore, t0: i64, a: Spot, b: Spot, v: f64) -> i64 {
    let (p, q) = (Point { lat: a.3, lon: a.4 }, Point { lat: b.3, lon: b.4 });
    let n = (haversine_m(p, q) / v).ceil().max(2.0) as i64;
    let fixes: Vec<GeoFix> = (0..=n)
        .map(|i| {
            let f = i as f64 / n as f64;
            GeoFix { lat: p.lat + (q.lat - p.lat) * f, lon: p.lon + (q.lon - p.lon) * f, t_ms: t0 + i * 1000, accuracy_m: 5.0 }
        })
        .collect();
    let end = t0 + n * 1000;
    store.mark_drive_event(DriveEvent::Start, t0).unwrap();
    store.ingest(fixes).unwrap();
    store.mark_drive_event(DriveEvent::End, end).unwrap();
    end
}

/// 이름 없는 장소에 SPOTS 이름을 붙인다 (카카오 흉내).
fn name_places(store: &EodiStore) {
    for q in store.pending_places(1000).unwrap() {
        let at = Point { lat: q.lat, lon: q.lon };
        if let Some(s) = SPOTS.iter().find(|s| haversine_m(at, Point { lat: s.3, lon: s.4 }) < 200.0) {
            let label = PlaceLabel { name: s.0.into(), category: s.1.into(), address: s.2.into(), source: PlaceSource::Kakao };
            store.set_place_name(PlaceName { id: q.id, label }).unwrap();
        }
    }
}

/// 2026-10-05(월)~10-11(일): 평일 출퇴근(월요일 저녁 이마트, 목요일 저녁 이마트), 토요일 강릉 당일, 일요일 성수 카페.
fn small_week(store: &EodiStore) {
    for k in 0..5 {
        let d = 5 + k;
        let e = drive(store, kst(2026, 10, d, 8, 0), HOME, OFFICE, 10.0);
        assert!(e < kst(2026, 10, d, 9, 0));
        if k == 0 || k == 3 {
            let e = drive(store, kst(2026, 10, d, 18, 30), OFFICE, EMART, 10.0);
            drive(store, e + 40 * MIN, EMART, HOME, 10.0);
        } else {
            drive(store, kst(2026, 10, d, 18, 30), OFFICE, HOME, 10.0);
        }
    }
    let e = drive(store, kst(2026, 10, 10, 7, 0), HOME, BEACH, 30.0);
    let e = drive(store, e + 2 * HOUR, BEACH, TOFU, 10.0);
    drive(store, e + HOUR, TOFU, HOME, 30.0);
    let e = drive(store, kst(2026, 10, 11, 13, 0), HOME, CAFE, 10.0);
    drive(store, e + 90 * MIN, CAFE, HOME, 10.0);
    name_places(store);
}

fn kinds(eps: &[Episode]) -> Vec<EpisodeKind> {
    eps.iter().map(|e| e.facts.kind).collect()
}

#[test]
fn episodes_get_kinds_labels_regions_and_first_visits() {
    let db = TempDb::new("kinds");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);

    let mon = store.episodes_for_day("2026-10-05".into()).unwrap();
    assert_eq!(kinds(&mon), [EpisodeKind::CommuteToWork, EpisodeKind::CommuteHome]);
    assert_eq!(mon[0].facts.rule_label, "출근길");
    assert_eq!(mon[1].facts.rule_label, "퇴근길 · 이마트 성수점 들름");
    assert_eq!((mon[1].facts.from_name.as_str(), mon[1].facts.to_name.as_str()), ("회사", "집"));
    assert_eq!(mon[1].facts.stops.len(), 1);
    assert!(mon[1].facts.stops[0].first_visit, "이마트 첫 방문");
    assert_eq!(mon[1].facts.region, "서울 성동구");
    assert_eq!(mon[0].facts.id, kst(2026, 10, 5, 8, 0), "id = 첫 주행 출발 시각");
    let thu = store.episodes_for_day("2026-10-08".into()).unwrap();
    assert!(!thu[1].facts.stops[0].first_visit, "두 번째 방문");

    let sat = store.episodes_for_day("2026-10-10".into()).unwrap();
    assert_eq!(kinds(&sat), [EpisodeKind::Trip]);
    let trip = &sat[0].facts;
    assert_eq!(trip.rule_label, "강릉 여행");
    assert_eq!(trip.region, "강원 강릉시");
    let names: Vec<&str> = trip.stops.iter().map(|s| s.name.as_str()).collect();
    assert_eq!(names, ["경포해변", "초당할머니순두부"]);
    assert!(trip.distance_m > 300_000.0);

    let sun = store.episodes_for_day("2026-10-11".into()).unwrap();
    assert_eq!(kinds(&sun), [EpisodeKind::Outing]);
    assert_eq!(sun[0].facts.rule_label, "성수 나들이");

    // 하루 제목: 출퇴근뿐 = "평소 출퇴근", 출퇴근 + 들른 곳 = 규칙 제목, 여행 날 = 여행 라벨
    let days = store.day_summaries(30).unwrap();
    let title = |d: &str| days.iter().find(|s| s.day == d).and_then(|s| s.title.clone());
    assert_eq!(title("2026-10-06").as_deref(), Some("평소 출퇴근"));
    assert_eq!(title("2026-10-08").as_deref(), Some("출퇴근 · 이마트 성수점 들름"));
    assert_eq!(title("2026-10-10").as_deref(), Some("강릉 여행"));
    let detail = store.day_detail("2026-10-10".into()).unwrap();
    assert_eq!(detail.summary.title.as_deref(), Some("강릉 여행"), "day_detail도 같은 제목");
}

#[test]
fn home_and_office_are_inferred_without_navi_bookmarks() {
    let db = TempDb::new("infer");
    let store = db.open();
    small_week(&store);
    store.day_summaries(1).unwrap(); // 기억 계산을 반영
    let anchors = episode::current_anchors(&store.lock().unwrap()).unwrap();
    let near = |p: Option<Point>, s: Spot| p.is_some_and(|p| haversine_m(p, Point { lat: s.3, lon: s.4 }) < 100.0);
    assert!(near(anchors.home, HOME), "{anchors:?}");
    assert!(near(anchors.office, OFFICE), "{anchors:?}");
    let tue = store.episodes_for_day("2026-10-06".into()).unwrap();
    assert_eq!(kinds(&tue), [EpisodeKind::CommuteToWork, EpisodeKind::CommuteHome]);

    // 내비 즐겨찾기가 오면 그것이 우선 (회사를 이마트로 바꾸면 출퇴근이 아니게 된다)
    store.set_home_office(Some(pt(HOME)), Some(pt(EMART))).unwrap();
    let tue = store.episodes_for_day("2026-10-06".into()).unwrap();
    assert!(!tue.iter().any(|e| e.facts.kind == EpisodeKind::CommuteToWork), "{:?}", kinds(&tue));
    // 다시 비우면 추정으로 돌아간다
    store.set_home_office(None, None).unwrap();
    assert_eq!(kinds(&store.episodes_for_day("2026-10-06".into()).unwrap())[0], EpisodeKind::CommuteToWork);
    assert!(store.set_home_office(Some(GeoPoint { lat: f64::NAN, lon: 0.0 }), None).is_err());
}

#[test]
fn enrichment_queue_skips_routine_days_and_requeues_on_material_change() {
    let db = TempDb::new("queue");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);

    let pending: Vec<String> = store.pending_day_enrichments(10).unwrap().into_iter().map(|d| d.day).collect();
    // 최신순. 월(이마트 첫 방문)·토(여행)·일(나들이). 화·수·목·금(평소 출퇴근·두 번째 이마트)은 없다.
    assert_eq!(pending, ["2026-10-11", "2026-10-10", "2026-10-05"]);
    let sat = store.pending_day_enrichments(10).unwrap().into_iter().find(|d| d.day == "2026-10-10").unwrap();
    assert_eq!(sat.weekday, "토");
    assert!(sat.total_distance_m > 300_000.0);

    // 아직 기록 중인 날(마지막 점이 10분 안, 그날이 안 끝남)은 빠진다
    {
        let conn = store.lock().unwrap();
        let last = kst(2026, 10, 11, 15, 0);
        let days = |now: i64| -> Vec<String> {
            episode::pending_days(&store, &conn, 10, now).unwrap().into_iter().map(|d| d.day).collect()
        };
        let last_fix: i64 = conn.query_row("SELECT max(t_ms) FROM fix", [], |r| r.get(0)).unwrap();
        assert!(last_fix < last);
        assert!(!days(last_fix + 5 * MIN).contains(&"2026-10-11".to_string()));
        assert!(days(last_fix + 11 * MIN).contains(&"2026-10-11".to_string()));
    }

    // 라벨 저장 → 빠지고, 제목·태그가 날짜 목록에 나온다
    let ep_id = store.episodes_for_day("2026-10-10".into()).unwrap()[0].facts.id;
    store
        .set_day_enrichment(DayEnrichment {
            day: "2026-10-10".into(),
            title: "비 오는 날 강릉 순두부 원정".into(),
            summary: "경포해변을 걷고 순두부를 먹었다.".into(),
            tags: vec!["바다".into(), " 해변 ".into(), "바다".into(), "".into()],
            episodes: vec![
                EpisodeLabel { episode_id: ep_id, title: "강릉 바다 당일치기".into(), tags: vec!["가족".into()] },
                EpisodeLabel { episode_id: 12345, title: "없는 에피소드".into(), tags: vec![] },
            ],
        })
        .unwrap();
    let pending: Vec<String> = store.pending_day_enrichments(10).unwrap().into_iter().map(|d| d.day).collect();
    assert_eq!(pending, ["2026-10-11", "2026-10-05"]);
    let sum = store.day_summaries(30).unwrap().into_iter().find(|s| s.day == "2026-10-10").unwrap();
    assert_eq!(sum.title.as_deref(), Some("비 오는 날 강릉 순두부 원정"));
    assert_eq!(sum.tags, ["바다", "해변"]);
    let ep = &store.episodes_for_day("2026-10-10".into()).unwrap()[0];
    assert_eq!((ep.title.as_deref(), ep.tags.clone()), (Some("강릉 바다 당일치기"), vec!["가족".to_string()]));

    // 실패 → 대기, 0이면 바로 다시
    store.mark_day_enrichment_failed("2026-10-11".into(), HOUR).unwrap();
    let pending: Vec<String> = store.pending_day_enrichments(10).unwrap().into_iter().map(|d| d.day).collect();
    assert_eq!(pending, ["2026-10-05"]);
    store.mark_day_enrichment_failed("2026-10-11".into(), 0).unwrap();
    assert_eq!(store.pending_day_enrichments(1).unwrap()[0].day, "2026-10-11");

    // 같은 날 저녁에 새 나들이(성수 카페)를 더하면 크게 바뀐 것 → 다시 대기열. 라벨은 그대로 보인다.
    let e = drive(&store, kst(2026, 10, 10, 20, 0), HOME, CAFE, 10.0);
    drive(&store, e + 30 * MIN, CAFE, HOME, 10.0);
    let pending: Vec<String> = store.pending_day_enrichments(10).unwrap().into_iter().map(|d| d.day).collect();
    assert!(pending.contains(&"2026-10-10".to_string()), "{pending:?}");
    let eps = store.episodes_for_day("2026-10-10".into()).unwrap();
    assert_eq!(kinds(&eps), [EpisodeKind::Trip, EpisodeKind::Outing]);
    assert_eq!(eps[0].title.as_deref(), Some("강릉 바다 당일치기"), "id가 같은 에피소드는 라벨 유지");

    // 잘못된 입력
    assert!(store.set_day_enrichment(DayEnrichment {
        day: "2026-10-12".into(),
        title: "기록 없는 날".into(),
        summary: String::new(),
        tags: vec![],
        episodes: vec![]
    })
    .is_err());
    assert!(store.mark_day_enrichment_failed("2026-13-01".into(), 0).is_err());
    assert!(store.episodes_for_day("어제".into()).is_err());
    assert!(store.episodes_for_day("2026-10-12".into()).unwrap().is_empty());
}

#[test]
fn requeue_labels_where_puts_only_bad_titles_back_and_keeps_them_visible() {
    let db = TempDb::new("requeue");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let label = |day: &str, title: &str| {
        store
            .set_day_enrichment(DayEnrichment { day: day.into(), title: title.into(), summary: "s".into(), tags: vec!["t".into()], episodes: vec![] })
            .unwrap()
    };
    label("2026-10-10", "강릉 횡성휴게소 출발 바다 순두부");
    label("2026-10-11", "성수 카페 한 잔");
    assert_eq!(store.pending_day_enrichments(10).unwrap().into_iter().map(|d| d.day).collect::<Vec<_>>(), ["2026-10-05"]);

    let n = store.requeue_day_labels_where(&|t: &str| t.contains("휴게소")).unwrap();
    assert_eq!(n, 1);
    let pending: Vec<String> = store.pending_day_enrichments(10).unwrap().into_iter().map(|d| d.day).collect();
    assert_eq!(pending, ["2026-10-10", "2026-10-05"]);
    // 다시 붙을 때까지 예전 제목이 보인다
    let sum = store.day_summaries(30).unwrap().into_iter().find(|s| s.day == "2026-10-10").unwrap();
    assert_eq!(sum.title.as_deref(), Some("강릉 횡성휴게소 출발 바다 순두부"));
    // 라벨이 없는 날·평범한 날은 건드리지 않는다
    assert_eq!(store.requeue_day_labels_where(&|_| true).unwrap(), 1, "2026-10-11만 남아 있다");
}

fn q(terms: &[&str]) -> MemoryQuery {
    MemoryQuery { terms: terms.iter().map(|t| t.to_string()).collect(), from_ms: None, to_ms: None, kinds: vec![], limit: 10 }
}

#[test]
fn korean_substrings_short_words_and_synonyms_match() {
    let db = TempDb::new("korean");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let top = |terms: &[&str]| store.search_memories(q(terms)).unwrap();

    // 가게 이름 가운데 글자 ("초당할머니순두부" ⊃ "순두부", "두부")
    for t in ["순두부", "두부", "할머니순두부"] {
        let h = top(&[t]);
        assert_eq!(h[0].episode.facts.day, "2026-10-10", "{t}");
        assert_eq!(h[0].best_stop.as_ref().unwrap().name, "초당할머니순두부", "{t}");
    }
    // 2글자 말: 라벨 전에도 동의어(바다 → 해수욕장·해변)로 분류·이름에서 찾는다
    let h = top(&["바다"]);
    assert_eq!(h[0].episode.facts.day, "2026-10-10");
    assert_eq!(h[0].best_stop.as_ref().unwrap().name, "경포해변");
    assert_eq!(top(&["카페"])[0].episode.facts.day, "2026-10-11");
    // 띄어 쓴 말은 둘 다 (경포 AND 해변)
    assert_eq!(top(&["경포 해변"])[0].episode.facts.day, "2026-10-10");
    // 잘린 한 글자("바")는 버린다 → 아무것도 안 나옴. 뜻 있는 말과 섞이면 그 말로만.
    assert!(top(&["바"]).is_empty());
    assert_eq!(top(&["바", "순두부"])[0].episode.facts.day, "2026-10-10");
    // 없는 말
    assert!(top(&["제주도"]).is_empty());
    // 기간·종류 거르기
    let mut only_commute = q(&["이마트"]);
    only_commute.kinds = vec![EpisodeKind::CommuteHome];
    let h = store.search_memories(only_commute).unwrap();
    let mut got: Vec<&str> = h.iter().map(|h| h.episode.facts.day.as_str()).collect();
    got.sort();
    assert_eq!(got, ["2026-10-05", "2026-10-08"]);
    assert!(h[0].episode.facts.stops[0].first_visit, "처음 간 날이 조금 앞선다");
    let mut ranged = q(&["이마트"]);
    ranged.from_ms = Some(kst(2026, 10, 7, 0, 0));
    assert_eq!(store.search_memories(ranged).unwrap().len(), 1);
    // 낱말 없이 기간·종류만 → 최신순
    let mut all_trips = q(&[]);
    all_trips.kinds = vec![EpisodeKind::Trip, EpisodeKind::Outing];
    let h = store.search_memories(all_trips).unwrap();
    assert_eq!(h.iter().map(|h| h.episode.facts.day.as_str()).collect::<Vec<_>>(), ["2026-10-11", "2026-10-10"]);
    // LLM 태그는 바로 검색된다
    store
        .set_day_enrichment(DayEnrichment {
            day: "2026-10-11".into(),
            title: "주말 오후 성수 카페 산책".into(),
            summary: String::new(),
            tags: vec!["데이트".into()],
            episodes: vec![],
        })
        .unwrap();
    assert_eq!(top(&["데이트"])[0].episode.facts.day, "2026-10-11");
    assert_eq!(top(&["데이트"])[0].day_title.as_deref(), Some("주말 오후 성수 카페 산책"));
}

#[test]
fn course_route_keeps_or_optimizes_visit_order_in_navi_legs() {
    let db = TempDb::new("course");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let id = store.episodes_for_day("2026-10-10".into()).unwrap()[0].facts.id;
    let r = store.course_route(id, None, false).unwrap();
    assert_eq!(r.stops.iter().map(|s| s.name.as_str()).collect::<Vec<_>>(), ["경포해변", "초당할머니순두부"]);
    assert_eq!(r.legs.len(), 1);
    assert_eq!(r.legs[0].waypoints.len(), 1);
    assert_eq!(r.legs[0].destination.name, "초당할머니순두부");
    // 순두부집 바로 옆에서 출발하면 순두부집부터
    let r = store.course_route(id, Some(pt(TOFU)), true).unwrap();
    assert_eq!(r.stops[0].name, "초당할머니순두부");
    assert!(r.distance_m < 3_000.0, "{}", r.distance_m);
    // 들른 곳 없는 출근길은 도착지(회사) 하나
    let commute = store.episodes_for_day("2026-10-06".into()).unwrap()[0].facts.id;
    let r = store.course_route(commute, None, true).unwrap();
    assert_eq!(r.stops.len(), 1);
    assert_eq!(r.stops[0].name, "회사");
    assert!(store.course_route(1, None, false).is_err());
}

/// 버전 2 DB(기억 표 없음)를 열면 기록은 그대로이고 에피소드가 만들어진다.
#[test]
fn v2_database_is_migrated_to_v3() {
    let db = TempDb::new("v2");
    {
        let store = db.open();
        small_week(&store);
        let c = store.lock().unwrap();
        c.execute_batch(crate::community::DROP_V5).unwrap();
        c.execute_batch(crate::photos::DROP_V4).unwrap();
        c.execute_batch(crate::memory::DROP_V3).unwrap();
        c.pragma_update(None, "user_version", 2).unwrap();
    }
    let before = {
        let c = rusqlite::Connection::open(db.path()).unwrap();
        let n: i64 = c.query_row("SELECT count(*) FROM trip", [], |r| r.get(0)).unwrap();
        let named: i64 = c.query_row("SELECT count(*) FROM place WHERE name IS NOT NULL", [], |r| r.get(0)).unwrap();
        assert!(c.query_row("SELECT 1 FROM sqlite_master WHERE name = 'episode'", [], |_| Ok(())).is_err());
        (n, named)
    };
    let store = db.open();
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, crate::store::SCHEMA_VERSION);
    let trips: u32 = store.day_summaries(30).unwrap().iter().map(|d| d.trip_count).sum();
    assert_eq!(trips as i64, before.0);
    let named: i64 = store.lock().unwrap().query_row("SELECT count(*) FROM place WHERE name IS NOT NULL", [], |r| r.get(0)).unwrap();
    assert_eq!(named, before.1, "장소 이름 유지");
    // 집·회사 추정으로 에피소드가 생기고 검색된다
    assert_eq!(kinds(&store.episodes_for_day("2026-10-10".into()).unwrap()), [EpisodeKind::Trip]);
    assert_eq!(store.search_memories(q(&["순두부"])).unwrap()[0].episode.facts.day, "2026-10-10");
}

// ---- 시연 1년치 ----

/// 2026-11-06 12:00 (한국) = 시연 "오늘".
fn demo_today() -> i64 {
    kst(2026, 11, 6, 12, 0)
}

fn seeded(name: &str) -> (TempDb, Arc<EodiStore>, u32, f64) {
    let db = TempDb::new(name);
    let store = db.open();
    let s = Instant::now();
    let n = store.seed_demo_data(demo_today()).unwrap();
    (db, store, n, s.elapsed().as_secs_f64())
}

fn day_num(day: &str) -> i64 {
    crate::store::parse_day(day).unwrap()
}

#[test]
fn demo_year_has_commutes_outings_and_the_gangneung_trip() {
    let (_db, store, n, secs) = seeded("demo");
    println!("seed {n} trips in {secs:.2}s");
    let today = day_num("2026-11-06");
    let p = demo::plan(today);
    assert_eq!(format_day(p.gangneung), "2025-11-06", "정확히 1년 전");
    assert_eq!(p.busan.map(format_day).as_deref(), Some("2026-08-07"));
    assert_eq!(p.seorak.map(format_day).as_deref(), Some("2026-10-24"));
    assert_eq!(store.seed_demo_data(demo_today()).unwrap(), 0, "두 번째는 아무것도 안 함");

    let fixes = store.fix_count().unwrap();
    let days = store.day_summaries(1000).unwrap();
    let trips: u32 = days.iter().map(|d| d.trip_count).sum();
    println!("fixes {fixes}, days {}, trips {trips}", days.len());
    assert_eq!(trips, n, "만든 주행이 그대로 주행이 된다");
    assert!(fixes < 1_000_000 && n > 600, "{fixes} {n}");
    assert_eq!(days[0].day, "2026-11-05", "어제까지");
    assert!(days.last().unwrap().day.as_str() >= "2025-11-06");

    // 강릉 가족 여행
    let g = store.episodes_for_day("2025-11-06".into()).unwrap();
    assert_eq!(kinds(&g), [EpisodeKind::Trip]);
    let f = &g[0].facts;
    assert_eq!(f.rule_label, "강릉 여행");
    assert_eq!(f.region, "강원 강릉시");
    let names: Vec<&str> = f.stops.iter().map(|s| s.name.as_str()).collect();
    assert_eq!(names, ["횡성휴게소", "경포해변", "초당할머니순두부", "안목해변 카페거리"]);
    assert!(f.stops.iter().all(|s| s.first_visit));
    assert_eq!((f.from_name.as_str(), f.to_name.as_str()), ("집", "집"));
    let gd = days.iter().find(|d| d.day == "2025-11-06").unwrap();
    assert_eq!(gd.title.as_deref(), Some("강릉 여행"), "강릉 날은 LLM 라벨 전 (규칙 제목)");
    assert_eq!(gd.stop_names, ["횡성휴게소", "경포해변", "초당할머니순두부"]);

    // 부산 2박 3일, 설악산 1박 2일 = 매일 여행
    for d in ["2026-08-07", "2026-08-08", "2026-08-09", "2026-10-24", "2026-10-25"] {
        let eps = store.episodes_for_day(d.into()).unwrap();
        assert!(!eps.is_empty() && eps.iter().all(|e| e.facts.kind == EpisodeKind::Trip), "{d}: {:?}", kinds(&eps));
    }
    assert_eq!(store.episodes_for_day("2026-08-07".into()).unwrap()[0].facts.rule_label, "부산 여행");
    assert_eq!(store.episodes_for_day("2026-10-24".into()).unwrap()[0].facts.rule_label, "속초 여행");

    // 출퇴근·볼일·나들이 고루
    let mut count = std::collections::HashMap::new();
    let mut labels = std::collections::BTreeSet::new();
    for d in &days {
        for e in store.episodes_for_day(d.day.clone()).unwrap() {
            *count.entry(format!("{:?}", e.facts.kind)).or_insert(0) += 1;
            if e.facts.kind == EpisodeKind::Outing {
                labels.insert(e.facts.rule_label.clone());
            }
        }
    }
    println!("episodes {count:?}");
    println!("outing labels {labels:?}");
    for k in ["CommuteToWork", "CommuteHome", "Errand", "Outing", "Trip"] {
        assert!(count.get(k).copied().unwrap_or(0) > 0, "{k}");
    }
    assert!(labels.contains("성수 나들이") && labels.contains("남이섬 나들이"), "{labels:?}");
    let routine = days.iter().filter(|d| d.title.as_deref() == Some("평소 출퇴근")).count();
    assert!(routine > 100, "{routine}");

    // 대기열: 최근 30일 나들이·여행 + 강릉 날만 (오래된 날은 시연 라벨이 미리 들어 있다)
    let pending = store.pending_day_enrichments(100).unwrap();
    let pdays: Vec<&str> = pending.iter().map(|d| d.day.as_str()).collect();
    println!("pending {pdays:?}");
    assert!(pdays.contains(&"2025-11-06"));
    assert!(pdays.contains(&"2026-10-24"));
    assert!(pdays.iter().all(|d| *d == "2025-11-06" || *d >= "2026-10-07"));
    assert!(pdays.windows(2).all(|w| w[0] > w[1]), "최신순");
    assert!(pending.iter().all(|d| d.episodes.iter().any(|e| matches!(e.kind, EpisodeKind::Outing | EpisodeKind::Trip)
        || e.stops.iter().any(|s| s.first_visit))));
    let busan = days.iter().find(|d| d.day == "2026-08-08").unwrap();
    assert_eq!(busan.title.as_deref(), Some("감천문화마을부터 광안리 야경까지"));
    assert!(busan.tags.contains(&"야경".to_string()));

    // 코스: 강릉 여행을 그대로 = 경유지 2 + 목적지, 그 다음 구간 목적지 1
    let r = store.course_route(f.id, Some(pt(HOME)), false).unwrap();
    assert_eq!(r.legs.len(), 2);
    assert_eq!(r.legs[0].waypoints.len(), 2);
    assert_eq!(r.legs[1].destination.name, "안목해변 카페거리");
    let opt = store.course_route(f.id, Some(pt(HOME)), true).unwrap();
    assert!(opt.distance_m <= r.distance_m + 1.0);
    // 안목해변에서 다시 돌면 안목부터 가까운 순
    let from_anmok = GeoPoint { lat: 37.7722, lon: 128.9474 };
    let opt = store.course_route(f.id, Some(from_anmok), true).unwrap();
    assert_eq!(opt.stops[0].name, "안목해변 카페거리");
    assert_eq!(opt.stops.last().unwrap().name, "횡성휴게소");
}

/// 질문(이미 LLM이 낱말·기간으로 바꾼 것) → 정답 에피소드인지 판단.
struct EvalCase {
    question: &'static str,
    terms: &'static [&'static str],
    /// (from, to) 현지 날짜
    range: Option<(&'static str, &'static str)>,
    kinds: &'static [EpisodeKind],
    /// 정답: 이 이름을 포함하는 들른 곳이 있는 에피소드
    expect_stop: &'static str,
}

const EVAL: &[EvalCase] = &[
    EvalCase { question: "1년 전 바다 갔던 데", terms: &["바다", "해변", "바닷가"], range: Some(("2025-10-20", "2025-11-20")), kinds: &[], expect_stop: "경포해변" },
    EvalCase { question: "순두부 먹은 곳", terms: &["순두부", "두부"], range: None, kinds: &[], expect_stop: "초당할머니순두부" },
    EvalCase { question: "작년에 강릉 갔을 때 경포", terms: &["경포", "경포해변", "강릉"], range: None, kinds: &[], expect_stop: "경포해변" },
    EvalCase { question: "올여름 바다 여행", terms: &["바다", "해변", "해수욕장"], range: Some(("2026-06-01", "2026-08-31")), kinds: &[EpisodeKind::Trip], expect_stop: "해수욕장" },
    EvalCase { question: "해운대", terms: &["해운대", "해운대해수욕장"], range: None, kinds: &[], expect_stop: "해운대" },
    EvalCase { question: "감천문화마을 갔던 날", terms: &["감천문화마을", "감천"], range: None, kinds: &[], expect_stop: "감천문화마을" },
    EvalCase { question: "단풍 보러 간 산", terms: &["단풍", "산", "등산", "국립공원"], range: Some(("2026-10-01", "2026-11-05")), kinds: &[], expect_stop: "설악산" },
    EvalCase { question: "속초 시장", terms: &["속초", "시장", "전통시장"], range: None, kinds: &[], expect_stop: "속초관광수산시장" },
    EvalCase { question: "퇴근길에 들른 카페", terms: &["카페", "커피"], range: None, kinds: &[EpisodeKind::CommuteHome], expect_stop: "스타벅스" },
    EvalCase { question: "병원 다녀온 날", terms: &["병원", "의원"], range: None, kinds: &[], expect_stop: "한양대학교병원" },
    EvalCase { question: "남이섬", terms: &["남이섬", "섬"], range: None, kinds: &[], expect_stop: "남이섬" },
    EvalCase { question: "광장시장 빈대떡", terms: &["광장시장", "빈대떡", "시장"], range: None, kinds: &[], expect_stop: "광장시장" },
    EvalCase { question: "조개구이 먹은 바닷가", terms: &["조개구이", "바닷가", "바다"], range: None, kinds: &[], expect_stop: "을왕리" },
    EvalCase { question: "이케아 갔던 날", terms: &["이케아", "가구"], range: None, kinds: &[], expect_stop: "이케아" },
    EvalCase { question: "한강 피크닉", terms: &["한강", "피크닉", "한강공원"], range: None, kinds: &[], expect_stop: "여의도한강공원" },
    EvalCase { question: "성수 카페 나들이", terms: &["성수", "카페", "커피"], range: None, kinds: &[EpisodeKind::Outing], expect_stop: "성수" },
    EvalCase { question: "휴게소 들렀던 강릉 길 (잘린 낱말 섞임)", terms: &["휴게소", "바", "횡성"], range: Some(("2025-11-01", "2025-11-30")), kinds: &[], expect_stop: "횡성휴게소" },
    EvalCase { question: "롯데월드몰 쇼핑", terms: &["롯데월드몰", "쇼핑", "쇼핑몰"], range: None, kinds: &[], expect_stop: "롯데월드몰" },
];

fn eval_query(c: &EvalCase) -> MemoryQuery {
    let range = c.range.map(|(a, b)| (kst_day(a), kst_day(b) + DAY_MS - 1));
    MemoryQuery {
        terms: c.terms.iter().map(|t| t.to_string()).collect(),
        from_ms: range.map(|r| r.0),
        to_ms: range.map(|r| r.1),
        kinds: c.kinds.to_vec(),
        limit: 5,
    }
}

fn kst_day(day: &str) -> i64 {
    day_num(day) * DAY_MS - KST as i64 * MIN
}

fn is_hit(h: &MemoryHit, c: &EvalCase) -> bool {
    h.episode.facts.stops.iter().any(|s| s.name.contains(c.expect_stop) || s.category.contains(c.expect_stop))
}

/// 시연 1년치에 대한 검색 평가 (hit@1·hit@3). 질문은 LLM이 이미 낱말·기간으로 바꾼 형태.
#[test]
fn retrieval_eval_over_the_demo_year() {
    let (_db, store, _, _) = seeded("eval");
    let (mut h1, mut h3) = (0, 0);
    for c in EVAL {
        let hits = store.search_memories(eval_query(c)).unwrap();
        let rank = hits.iter().position(|h| is_hit(h, c));
        let top: Vec<String> = hits
            .iter()
            .take(3)
            .map(|h| format!("{} {} [{:.2}]", h.episode.facts.day, h.episode.facts.rule_label, h.score))
            .collect();
        println!("{:<32} rank {:?}  {}", c.question, rank.map(|r| r + 1), top.join(" | "));
        h1 += usize::from(rank == Some(0));
        h3 += usize::from(rank.is_some_and(|r| r < 3));
        // 추천 경유지는 정답 들른 곳
        if rank == Some(0) {
            let best = hits[0].best_stop.as_ref().unwrap();
            assert!(best.name.contains(c.expect_stop) || best.category.contains(c.expect_stop), "{}: {best:?}", c.question);
        }
    }
    let n = EVAL.len() as f64;
    println!("hit@1 {:.2} ({h1}/{}), hit@3 {:.2} ({h3}/{})", h1 as f64 / n, EVAL.len(), h3 as f64 / n, EVAL.len());
    assert!(h1 as f64 / n >= 0.85, "hit@1 {h1}");
    assert_eq!(h3, EVAL.len(), "hit@3");
    // 잘린 한 글자만 들어오면 아무것도 덮지 않는다
    assert!(store.search_memories(q(&["바"])).unwrap().is_empty());
}

// ---- 성능: cargo test --release -- --ignored --nocapture perf_memory ----

#[test]
#[ignore = "느림: --release -- --ignored 로 실행"]
fn perf_memory_demo_year() {
    let (_db, store, n, secs) = seeded("perf");
    println!("seed_demo_data: 주행 {n}개, 점 {}개, {secs:.2}s", store.fix_count().unwrap());
    let pct = |v: &mut Vec<f64>, p: f64| {
        v.sort_by(f64::total_cmp);
        v[((v.len() - 1) as f64 * p) as usize]
    };
    let mut times = Vec::new();
    for _ in 0..20 {
        for c in EVAL {
            let s = Instant::now();
            store.search_memories(eval_query(c)).unwrap();
            times.push(s.elapsed().as_secs_f64() * 1e3);
        }
    }
    println!("search_memories p50 {:.3}ms p99 {:.3}ms max {:.3}ms", pct(&mut times, 0.5), pct(&mut times, 0.99), pct(&mut times, 1.0));
    let mut broad = Vec::new();
    for _ in 0..50 {
        let s = Instant::now();
        store.search_memories(MemoryQuery { limit: 200, ..q(&["카페", "커피", "산", "바다"]) }).unwrap();
        broad.push(s.elapsed().as_secs_f64() * 1e3);
    }
    println!("search_memories(넓은 질의, 200개) p50 {:.3}ms p99 {:.3}ms", pct(&mut broad, 0.5), pct(&mut broad, 0.99));
    let s = Instant::now();
    let p = store.pending_day_enrichments(20).unwrap();
    println!("pending_day_enrichments(20) {}개 {:.3}ms", p.len(), s.elapsed().as_secs_f64() * 1e3);
    let mut ep = Vec::new();
    for d in ["2025-11-06", "2026-08-08", "2026-11-05", "2026-03-03"] {
        let s = Instant::now();
        store.episodes_for_day(d.into()).unwrap();
        ep.push(s.elapsed().as_secs_f64() * 1e3);
    }
    println!("episodes_for_day max {:.3}ms", pct(&mut ep, 1.0));
    let s = Instant::now();
    store.day_summaries(400).unwrap();
    println!("day_summaries(400) {:.3}ms", s.elapsed().as_secs_f64() * 1e3);
    // 집·회사가 바뀌어 1년치 에피소드를 모두 다시 만들 때
    let s = Instant::now();
    store.set_home_office(None, Some(GeoPoint { lat: 37.5, lon: 127.0 })).unwrap();
    println!("set_home_office → 1년치 에피소드 재계산 {:.1}ms", s.elapsed().as_secs_f64() * 1e3);
    assert!(pct(&mut times, 0.99) < 20.0);
}

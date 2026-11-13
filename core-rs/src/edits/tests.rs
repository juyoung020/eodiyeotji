//! 기록 고치기 테스트 (숨기기 · 지우기 · 되살리기 · 이름 고치기 · 집 · 회사). DB는 임시 파일.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

use super::*;
use crate::geo::haversine_m;
use crate::memory::{DayEnrichment, MemoryQuery};
use crate::places::{NaviHintKind, NaviPlaceHint, PlaceName};
use crate::store::{days_from_civil, DayEntry, DriveEvent, GeoFix, DAY_MS};

pub(crate) const MIN: i64 = 60_000;
pub(crate) const HOUR: i64 = 60 * MIN;
const KST: i32 = 540;
/// 테스트의 "지금" (2026-11-01, 기록보다 뒤)
pub(crate) const NOW: i64 = 1_793_000_000_000;

pub(crate) struct TempDb(PathBuf);

impl TempDb {
    pub(crate) fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let db = TempDb(crate::test_tmp::dir().join(format!("eodi-edit-{name}-{}-{n}.db", std::process::id())));
        db.remove();
        db
    }

    pub(crate) fn path(&self) -> String {
        self.0.to_string_lossy().into_owned()
    }

    pub(crate) fn open(&self) -> Arc<EodiStore> {
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

pub(crate) fn kst(y: i64, m: i64, d: i64, h: i64, mi: i64) -> i64 {
    days_from_civil(y, m, d) * DAY_MS + (h * 60 + mi) * MIN - KST as i64 * MIN
}

pub(crate) type Spot = (&'static str, &'static str, &'static str, f64, f64);

pub(crate) const HOME: Spot = ("응봉동 현대아파트", "아파트", "서울 성동구 응봉동 265", 37.5530, 127.0335);
pub(crate) const OFFICE: Spot = ("센터원빌딩", "빌딩", "서울 중구 수하동 67", 37.5672, 126.9844);
pub(crate) const EMART: Spot = ("이마트 성수점", "대형마트", "서울 성동구 성수동2가 333-16", 37.5395, 127.0531);
pub(crate) const CAFE: Spot = ("대림창고", "카페", "서울 성동구 성수동2가 322-32", 37.5419, 127.0566);
pub(crate) const BEACH: Spot = ("경포해변", "해수욕장", "강원 강릉시 안현동 산1", 37.8055, 128.9083);
pub(crate) const TOFU: Spot = ("초당할머니순두부", "두부전문점", "강원 강릉시 초당동 309-1", 37.7915, 128.9142);
const SPOTS: [Spot; 6] = [HOME, OFFICE, EMART, CAFE, BEACH, TOFU];

pub(crate) fn pt(s: Spot) -> GeoPoint {
    GeoPoint { lat: s.3, lon: s.4 }
}

/// t0에 a에서 b까지 1초 간격, 초속 v m로 곧게 달린다 (시작 · 끝 신호 포함). 반환: 도착 시각.
pub(crate) fn drive(store: &EodiStore, t0: i64, a: Spot, b: Spot, v: f64) -> i64 {
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
pub(crate) fn name_places(store: &EodiStore) {
    for q in store.pending_places(1000).unwrap() {
        let at = Point { lat: q.lat, lon: q.lon };
        if let Some(s) = SPOTS.iter().find(|s| haversine_m(at, Point { lat: s.3, lon: s.4 }) < 200.0) {
            let label = PlaceLabel { name: s.0.into(), category: s.1.into(), address: s.2.into(), source: PlaceSource::Kakao };
            store.set_place_name(PlaceName { id: q.id, label }).unwrap();
        }
    }
}

/// 2026-10-05(월)~10-11(일): 평일 출퇴근(월 · 목 저녁 이마트), 토요일 강릉, 일요일 성수 카페.
pub(crate) fn small_week(store: &EodiStore) {
    for k in 0..5 {
        let d = 5 + k;
        drive(store, kst(2026, 10, d, 8, 0), HOME, OFFICE, 10.0);
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

fn stays_named(store: &EodiStore, day: &str) -> Vec<String> {
    store
        .day_detail(day.into())
        .unwrap()
        .stays
        .iter()
        .map(|s| s.place.as_ref().map(|p| p.name.clone()).unwrap_or_default())
        .collect()
}

fn search(store: &EodiStore, word: &str) -> Vec<String> {
    let q = MemoryQuery { terms: vec![word.into()], from_ms: None, to_ms: None, kinds: vec![], limit: 50 };
    let mut days: Vec<String> = store.search_memories(q).unwrap().into_iter().map(|h| h.episode.facts.day).collect();
    days.sort();
    days.dedup();
    days
}

fn summary_of<'a>(days: &'a [crate::store::DaySummary], day: &str) -> Option<&'a crate::store::DaySummary> {
    days.iter().find(|d| d.day == day)
}

// ---- 숨기기 · 되살리기 ----

#[test]
fn hiding_a_trip_removes_it_everywhere_and_restore_brings_it_back() {
    let db = TempDb::new("hide-trip");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let before = store.day_detail("2026-10-11".into()).unwrap();
    assert_eq!(before.trips.len(), 2);
    let cafe_trip = before.trips[0].clone();
    assert_eq!(search(&store, "대림창고"), ["2026-10-11"]);

    store.hide_trip(cafe_trip.start_ms, NOW).unwrap();
    let after = store.day_detail("2026-10-11".into()).unwrap();
    assert_eq!(after.trips.len(), 1, "숨긴 주행은 상세에서 빠진다");
    assert!(after.trips.iter().all(|t| t.start_ms != cafe_trip.start_ms));
    assert_eq!(after.stays, before.stays, "머문 곳은 그대로");
    let sun = store.day_summaries(30).unwrap();
    assert_eq!(summary_of(&sun, "2026-10-11").unwrap().trip_count, 1, "날짜 목록 요약도 다시 만든다");
    let map = store.day_map("2026-10-11".into()).unwrap();
    assert_eq!(map.geojson.matches("\"kind\":\"trip\"").count(), 1, "지도에서도 빠진다");

    let hidden = store.hidden_records().unwrap();
    assert_eq!(hidden.len(), 1);
    assert_eq!(hidden[0].kind, HiddenKind::Trip);
    assert_eq!(hidden[0].day, "2026-10-11");
    assert_eq!(hidden[0].label, "응봉동 현대아파트 → 대림창고");
    assert_eq!(hidden[0].hidden_ms, NOW);

    store.restore_hidden(hidden[0].id).unwrap();
    assert_eq!(store.day_detail("2026-10-11".into()).unwrap().trips, before.trips);
    assert!(store.hidden_records().unwrap().is_empty());
    assert!(store.restore_hidden(hidden[0].id).is_err(), "두 번 되살릴 수 없다");
    assert!(store.hide_trip(12345, NOW).is_err(), "없는 주행");
}

#[test]
fn hiding_a_stay_removes_it_from_details_summaries_search_and_share_candidates() {
    let db = TempDb::new("hide-stay");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    assert_eq!(stays_named(&store, "2026-10-11"), ["대림창고", "응봉동 현대아파트"]);
    assert_eq!(search(&store, "대림창고"), ["2026-10-11"]);
    assert!(!store.share_candidates_on("2026-10-11".into()).unwrap().is_empty());

    let cafe = store.day_detail("2026-10-11".into()).unwrap().stays[0].clone();
    store.hide_stay(cafe.arrive_ms, NOW).unwrap();
    assert_eq!(stays_named(&store, "2026-10-11"), ["응봉동 현대아파트"]);
    let d = store.day_detail("2026-10-11".into()).unwrap();
    assert_eq!(d.stays[0].order, 1, "순서 번호를 다시 매긴다");
    assert_eq!(d.summary.stop_names, ["응봉동 현대아파트"]);
    assert!(!d.entries.iter().any(|e| matches!(e, DayEntry::Stay { stay } if stay.arrive_ms == cafe.arrive_ms)));
    assert!(search(&store, "대림창고").is_empty(), "검색에서 빠진다");
    assert!(store.share_candidates_on("2026-10-11".into()).unwrap().is_empty(), "공유 후보에서 빠진다");
    let rec = &store.hidden_records().unwrap()[0];
    assert_eq!((rec.kind, rec.label.as_str()), (HiddenKind::Stay, "대림창고"));

    store.restore_hidden(rec.id).unwrap();
    assert_eq!(search(&store, "대림창고"), ["2026-10-11"]);
}

#[test]
fn hiding_a_day_removes_it_from_the_list_and_memory() {
    let db = TempDb::new("hide-day");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    assert_eq!(search(&store, "경포해변"), ["2026-10-10"]);

    store.hide_day("2026-10-10".into(), NOW).unwrap();
    let days = store.day_summaries(30).unwrap();
    assert!(summary_of(&days, "2026-10-10").is_none(), "날짜 목록에서 빠진다");
    assert_eq!(days.len(), 6);
    let d = store.day_detail("2026-10-10".into()).unwrap();
    assert!(d.trips.is_empty() && d.stays.is_empty());
    assert!(store.episodes_for_day("2026-10-10".into()).unwrap().is_empty());
    assert!(search(&store, "경포해변").is_empty());
    let rec = &store.hidden_records().unwrap()[0];
    assert_eq!(rec.kind, HiddenKind::Day);
    assert_eq!(rec.label, "경포해변 · 초당할머니순두부 · 응봉동 현대아파트");

    store.restore_hidden(rec.id).unwrap();
    assert!(summary_of(&store.day_summaries(30).unwrap(), "2026-10-10").is_some());
    assert_eq!(search(&store, "경포해변"), ["2026-10-10"]);
    assert!(store.hide_day("2026-10-20".into(), NOW).is_err(), "기록 없는 날");
    assert!(store.hide_day("2026-13-01".into(), NOW).is_err(), "틀린 날짜");
}

#[test]
fn hiding_forgets_the_ai_labels_of_that_day() {
    let db = TempDb::new("hide-label");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let facts = store.pending_day_enrichments(10).unwrap();
    let sun = facts.iter().find(|f| f.day == "2026-10-11").expect("일요일 라벨링 대기");
    store
        .set_day_enrichment(DayEnrichment {
            day: sun.day.clone(),
            title: "대림창고에서 커피".into(),
            summary: "성수 카페".into(),
            tags: vec!["카페".into()],
            episodes: vec![],
        })
        .unwrap();
    let title = |s: &EodiStore| summary_of(&s.day_summaries(30).unwrap(), "2026-10-11").unwrap().title.clone();
    assert_eq!(title(&store).as_deref(), Some("대림창고에서 커피"));
    let cafe = store.day_detail("2026-10-11".into()).unwrap().stays[0].arrive_ms;
    store.hide_stay(cafe, NOW).unwrap();
    assert_ne!(title(&store).as_deref(), Some("대림창고에서 커피"), "숨긴 곳 이름이 든 AI 제목이 남지 않는다");
    assert!(search(&store, "커피").is_empty());
}

// ---- 지우기 ----

#[test]
fn deleting_a_trip_removes_its_fixes_for_good() {
    let db = TempDb::new("del-trip");
    let store = db.open();
    small_week(&store);
    let fixes = store.fix_count().unwrap();
    let mon = store.day_detail("2026-10-05".into()).unwrap();
    assert_eq!(mon.trips.len(), 3);
    let t = mon.trips[1].clone(); // 회사 → 이마트
    let n_points = (t.end_ms - t.start_ms) / 1000 + 1;

    store.delete_trip(t.start_ms).unwrap();
    assert_eq!(store.fix_count().unwrap(), fixes - n_points as u64, "그 주행의 점만 지운다");
    let mon2 = store.day_detail("2026-10-05".into()).unwrap();
    assert_eq!(mon2.trips.len(), 2);
    assert_eq!(mon2.trips[0], mon.trips[0]);
    assert_eq!(mon2.trips[1].start_ms, mon.trips[2].start_ms);
    assert!(store.hidden_records().unwrap().is_empty(), "지운 것은 숨긴 기록 목록에 없다");
    assert!(store.delete_trip(t.start_ms).is_err(), "이미 지웠다");
    // 다른 날은 그대로
    assert_eq!(store.day_detail("2026-10-08".into()).unwrap().trips.len(), 3);
}

#[test]
fn deleting_a_stay_hides_it_for_good() {
    let db = TempDb::new("del-stay");
    let store = db.open();
    small_week(&store);
    let emart = store.day_detail("2026-10-05".into()).unwrap().stays[1].clone();
    assert_eq!(emart.place.as_ref().unwrap().name, "이마트 성수점");
    store.delete_stay(emart.arrive_ms, NOW).unwrap();
    let names = stays_named(&store, "2026-10-05");
    assert!(!names.contains(&"이마트 성수점".to_owned()), "{names:?}");
    assert!(store.hidden_records().unwrap().is_empty(), "되살릴 수 없다");
    assert_eq!(store.day_detail("2026-10-05".into()).unwrap().trips.len(), 3, "앞뒤 주행은 그대로");
}

#[test]
fn deleting_a_day_removes_its_fixes_and_keeps_other_days() {
    let db = TempDb::new("del-day");
    let store = db.open();
    small_week(&store);
    let before = store.fix_count().unwrap();
    let sat_fixes: u64 = store
        .day_detail("2026-10-10".into())
        .unwrap()
        .trips
        .iter()
        .map(|t| ((t.end_ms - t.start_ms) / 1000 + 1) as u64)
        .sum();
    let fri = store.day_detail("2026-10-09".into()).unwrap();
    let sun = store.day_detail("2026-10-11".into()).unwrap();

    store.delete_day("2026-10-10".into(), NOW).unwrap();
    assert_eq!(store.fix_count().unwrap(), before - sat_fixes);
    assert!(summary_of(&store.day_summaries(30).unwrap(), "2026-10-10").is_none());
    assert!(store.hidden_records().unwrap().is_empty());
    assert_eq!(store.day_detail("2026-10-09".into()).unwrap().trips, fri.trips);
    assert_eq!(store.day_detail("2026-10-11".into()).unwrap().trips, sun.trips);
    assert!(store.delete_day("2026-10-10".into(), NOW).is_err(), "이미 지운 날");
}

// ---- 이름 고치기 ----

#[test]
fn a_user_name_beats_every_source_and_applies_to_all_visits() {
    let db = TempDb::new("rename");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let emart = store.day_detail("2026-10-05".into()).unwrap().stays[1].clone();
    let card = store
        .place_card(crate::saved::PlaceSpot {
            kind: crate::saved::SpotKind::Stay,
            t_ms: emart.arrive_ms,
            lat: emart.center.lat,
            lon: emart.center.lon,
        })
        .unwrap()
        .unwrap();
    assert_eq!(card.label.as_ref().unwrap().name, "이마트 성수점");

    store.rename_place(card.id, "홈플러스 성수점".into(), String::new(), String::new()).unwrap();
    for day in ["2026-10-05", "2026-10-08"] {
        let d = store.day_detail(day.into()).unwrap();
        let p = d.stays[1].place.clone().unwrap();
        assert_eq!((p.name.as_str(), p.source), ("홈플러스 성수점", PlaceSource::User), "{day}");
        assert_eq!(p.address, "서울 성동구 성수동2가 333-16", "주소는 그대로");
        assert!(d.summary.stop_names.contains(&"홈플러스 성수점".to_owned()));
    }
    let ep = store.episodes_for_day("2026-10-05".into()).unwrap();
    assert_eq!(ep[1].facts.rule_label, "퇴근길 · 홈플러스 성수점 들름", "에피소드도 다시 만든다");
    assert_eq!(search(&store, "홈플러스"), ["2026-10-05", "2026-10-08"]);
    assert!(search(&store, "이마트").is_empty());

    // 카카오 · 내비는 사용자 이름을 바꾸지 못한다
    let kakao = PlaceLabel { name: "이마트".into(), category: String::new(), address: String::new(), source: PlaceSource::Kakao };
    store.set_place_name(PlaceName { id: card.id, label: kakao }).unwrap();
    store
        .add_navi_hint(NaviPlaceHint {
            name: "이마트 성수".into(),
            lat: EMART.3,
            lon: EMART.4,
            t_ms: emart.arrive_ms,
            kind: NaviHintKind::Destination,
            address: String::new(),
        })
        .unwrap();
    assert_eq!(stays_named(&store, "2026-10-05")[1], "홈플러스 성수점");
    // 사용자는 다시 고칠 수 있다
    store.rename_place(card.id, "단골 마트".into(), "마트".into(), String::new()).unwrap();
    let p = store.day_detail("2026-10-08".into()).unwrap().stays[1].place.clone().unwrap();
    assert_eq!((p.name.as_str(), p.category.as_str()), ("단골 마트", "마트"));
    assert!(store.rename_place(card.id, "  ".into(), String::new(), String::new()).is_err());
    assert!(store.rename_place(999_999, "x".into(), String::new(), String::new()).is_err());
}

#[test]
fn renaming_forgets_ai_titles_that_mention_the_old_name() {
    let db = TempDb::new("rename-label");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let sat = store.pending_day_enrichments(10).unwrap().into_iter().find(|f| f.day == "2026-10-10").unwrap();
    store
        .set_day_enrichment(DayEnrichment {
            day: sat.day.clone(),
            title: "경포해변 바다 나들이".into(),
            summary: String::new(),
            tags: vec![],
            episodes: vec![],
        })
        .unwrap();
    let beach = store.day_detail("2026-10-10".into()).unwrap().stays[0].clone();
    let id = store
        .place_card(crate::saved::PlaceSpot { kind: crate::saved::SpotKind::Stay, t_ms: beach.arrive_ms, lat: 0.0, lon: 0.0 })
        .unwrap()
        .unwrap()
        .id;
    store.rename_place(id, "경포대 해수욕장".into(), String::new(), String::new()).unwrap();
    let t = summary_of(&store.day_summaries(30).unwrap(), "2026-10-10").unwrap().title.clone();
    assert_ne!(t.as_deref(), Some("경포해변 바다 나들이"));
    assert!(store.pending_day_enrichments(10).unwrap().iter().any(|f| f.day == "2026-10-10"), "다시 라벨링");
}

// ---- 집 · 회사 ----

#[test]
fn the_users_home_and_office_beat_navi_and_guesses() {
    let db = TempDb::new("anchor");
    let store = db.open();
    small_week(&store);
    // 추정: 밤에 머문 곳 = 집
    let ho = store.home_office().unwrap();
    let home = ho.home.unwrap();
    assert_eq!((home.source, home.name.as_str()), (AnchorSource::Guessed, "응봉동 현대아파트"));

    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    assert_eq!(store.home_office().unwrap().office.unwrap().source, AnchorSource::Navi);

    // 사용자가 카페를 회사로 → 내비보다 우선, 에피소드 종류도 바뀐다
    store.set_my_anchor(AnchorRole::Office, Some(pt(CAFE))).unwrap();
    let office = store.home_office().unwrap().office.unwrap();
    assert_eq!((office.source, office.name.as_str()), (AnchorSource::User, "대림창고"));
    let sun = store.episodes_for_day("2026-10-11".into()).unwrap();
    assert_eq!(sun[0].facts.kind, crate::memory::EpisodeKind::CommuteToWork);
    // 내비가 다시 와도 사용자 것이 남는다
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    assert_eq!(store.home_office().unwrap().office.unwrap().source, AnchorSource::User);
    // 지우면 내비로 돌아간다
    store.set_my_anchor(AnchorRole::Office, None).unwrap();
    let office = store.home_office().unwrap().office.unwrap();
    assert_eq!((office.source, office.name.as_str()), (AnchorSource::Navi, "센터원빌딩"));
    assert!(store.set_my_anchor(AnchorRole::Home, Some(GeoPoint { lat: f64::NAN, lon: 0.0 })).is_err());
}

// ---- DB 올리기 ----

#[test]
fn v6_database_gets_edit_tables_and_keeps_records() {
    let db = TempDb::new("v6");
    let before = {
        let store = db.open();
        small_week(&store);
        let days = store.day_summaries(30).unwrap();
        let c = store.lock().unwrap();
        c.execute_batch(DROP_V7).unwrap();
        c.pragma_update(None, "user_version", 6).unwrap();
        days
    };
    let store = db.open();
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, crate::store::SCHEMA_VERSION);
    assert_eq!(store.day_summaries(30).unwrap(), before);
    assert!(store.hidden_records().unwrap().is_empty());
    store.hide_day("2026-10-10".into(), NOW).unwrap();
    assert_eq!(store.hidden_records().unwrap().len(), 1);
}

// ---- 하루 제목 고치기 · 짧은 이동 제목 ----

#[test]
fn a_user_day_title_beats_ai_and_rule_titles_and_survives_relabeling() {
    let db = TempDb::new("day-title");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let title = |s: &EodiStore| summary_of(&s.day_summaries(30).unwrap(), "2026-10-11").unwrap().title.clone();
    let rule = title(&store);

    store.set_day_title("2026-10-11".into(), Some("  엄마랑   성수 커피  ".into())).unwrap();
    assert_eq!(title(&store).as_deref(), Some("엄마랑 성수 커피"), "공백을 정리해 저장");
    assert_eq!(search(&store, "엄마랑"), ["2026-10-11"], "검색 색인도 새 제목");
    store
        .set_day_enrichment(DayEnrichment {
            day: "2026-10-11".into(),
            title: "대림창고에서 커피".into(),
            summary: "성수 카페".into(),
            tags: vec!["카페".into()],
            episodes: vec![],
        })
        .unwrap();
    assert_eq!(title(&store).as_deref(), Some("엄마랑 성수 커피"), "AI 제목이 와도 사용자 제목이 이긴다");

    store.set_day_title("2026-10-11".into(), Some("   ".into())).unwrap();
    assert_eq!(title(&store).as_deref(), Some("대림창고에서 커피"), "비우면 AI 제목으로 돌아간다");
    store.set_day_title("2026-10-11".into(), None).unwrap();
    assert_ne!(title(&store), None);
    assert_ne!(rule, None);

    assert!(store.set_day_title("2026-13-01".into(), Some("x".into())).is_err(), "틀린 날짜");
    let long = "가".repeat(DAY_TITLE_MAX + 1);
    assert!(store.set_day_title("2026-10-11".into(), Some(long)).is_err(), "너무 긴 제목");

    store.set_day_title("2026-10-10".into(), Some("강릉 바다".into())).unwrap();
    store.delete_day("2026-10-10".into(), NOW).unwrap();
    let left: i64 = store.lock().unwrap().query_row("SELECT count(*) FROM day_user_title", [], |r| r.get(0)).unwrap();
    assert_eq!(left, 0, "그날을 지우면 사용자 제목도 지운다");
}

#[test]
fn a_short_hop_far_from_home_is_not_called_a_trip() {
    let db = TempDb::new("short-hop");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    // 강릉에서 경포해변 → 초당할머니순두부 잠깐 (약 1.6km · 3분). 집에서 멀어 규칙상 '여행' 묶음이다.
    drive(&store, kst(2026, 10, 14, 9, 18), BEACH, TOFU, 10.0);
    name_places(&store);
    let eps = store.episodes_for_day("2026-10-14".into()).unwrap();
    assert_eq!(eps.len(), 1);
    let label = &eps[0].facts.rule_label;
    assert!(!label.contains("여행") && !label.contains("나들이"), "{label}");
    assert_eq!(label, "초당할머니순두부 들름");
    let day = summary_of(&store.day_summaries(30).unwrap(), "2026-10-14").unwrap().title.clone();
    assert_eq!(day.as_deref(), Some("초당할머니순두부 들름"));
    // 긴 강릉 여행은 그대로 '여행'
    small_week(&store);
    let sat = summary_of(&store.day_summaries(30).unwrap(), "2026-10-10").unwrap().title.clone().unwrap();
    assert!(sat.contains("여행"), "{sat}");
}

#[test]
fn short_move_labels_are_factual() {
    use crate::memory::is_short_move;
    assert!(is_short_move(747.0, MIN));
    assert!(!is_short_move(747.0, HOUR), "오래 머물렀으면 짧은 이동이 아니다");
    assert!(!is_short_move(30_000.0, 10 * MIN));
}

/// 에뮬레이터 위치 재생으로 쌓인 하루 (UX 리뷰 4번, 2026-11-07 실제 기록 모양): 호연재 → 경포해변 747m를 17초에
/// 세 번, 그 사이 400m · 15초 이동 한 번. 지우지 않고 보여 주는 규칙으로 정리한다.
#[test]
fn replayed_short_hops_are_one_row_an_errand_and_never_the_day_of_the_month() {
    const HOYEON: Spot = ("호연재", "한옥", "강원 강릉시 운정동 1", 37.8010, 128.9020);
    const NEAR: Spot = ("", "", "", 37.8030, 128.9050);
    let db = TempDb::new("replayed-hops");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let t = kst(2026, 10, 20, 9, 0);
    drive(&store, t, HOYEON, BEACH, 45.0);
    drive(&store, t + 3 * MIN, HOYEON, BEACH, 45.0);
    drive(&store, t + 6 * MIN, NEAR, BEACH, 27.0);
    drive(&store, t + 9 * MIN, HOYEON, BEACH, 45.0);
    name_places(&store);
    let day = "2026-10-20";

    // 타임라인: 400m · 15초 이동은 숨고, 같은 길 세 번은 한 줄 (지도용 주행 목록 · 거리 합은 그대로)
    let d = store.day_detail(day.into()).unwrap();
    assert_eq!(d.trips.len(), 4);
    let rows: Vec<u32> = d.entries.iter().filter_map(|e| match e { DayEntry::Trip { repeat, .. } => Some(*repeat), _ => None }).collect();
    assert_eq!(rows, [3]);
    assert_eq!(d.summary.trip_count, 1, "주행 수 = 타임라인 주행 줄 수");
    assert!((d.summary.distance_m - d.trips.iter().map(|t| t.distance_m).sum::<f64>()).abs() < 1e-6);
    let listed = summary_of(&store.day_summaries(30).unwrap(), day).unwrap().clone();
    assert_eq!((listed.trip_count, listed.stay_count), (d.summary.trip_count, d.summary.stay_count), "목록과 상세가 같은 값");

    // 집에서 멀어도 운전 1분 남짓이면 여행 · 나들이가 아니다 → 사실 그대로의 이름, AI 라벨링도 필요 없다
    let eps = store.episodes_for_day(day.into()).unwrap();
    assert_eq!(eps.len(), 1);
    assert_eq!(eps[0].facts.kind, crate::memory::EpisodeKind::Errand);
    assert_eq!(eps[0].facts.rule_label, "경포해변 들름");
    assert!(store.pending_day_enrichments(50).unwrap().iter().all(|f| f.day != day));
    // 예전 규칙에서 붙은 AI 제목이 남아 있어도 화면에는 규칙 제목 (지우지는 않는다)
    store
        .set_day_enrichment(DayEnrichment {
            day: day.into(),
            title: "가을 경포해변 단풍 구경".into(),
            summary: String::new(),
            tags: vec!["단풍".into()],
            episodes: vec![],
        })
        .unwrap();
    let title = summary_of(&store.day_summaries(30).unwrap(), day).unwrap().title.clone();
    assert_eq!(title.as_deref(), Some("경포해변 들름"));
    assert_eq!(store.day_detail(day.into()).unwrap().summary.title.as_deref(), Some("경포해변 들름"));
    let kept: Option<String> =
        store.lock().unwrap().query_row("SELECT title FROM day_memory WHERE day = ?1", [days_from_civil(2026, 10, 20)], |r| r.get(0)).unwrap();
    assert_eq!(kept.as_deref(), Some("가을 경포해변 단풍 구경"), "AI 제목은 지우지 않고 숨긴다");

    // 이달의 하루 · 나들이 · 여행 횟수에서 빠진다 (토요일 강릉 여행이 이달의 하루)
    let m = store.month_recap(2026, 10, kst(2026, 10, 31, 12, 0)).unwrap();
    assert_eq!(m.best_day.as_ref().map(|b| b.day.as_str()), Some("2026-10-10"));
    assert_eq!(m.outing_count, m.totals.outings + m.totals.trips, "비교 문장의 횟수 = 위 칸의 나들이 + 여행");
}

/// 하루 상세 · 날짜 목록 · 그날로 돌아가기의 들른 곳 수가 같다 (UX 리뷰 3번: 5곳 vs 4곳).
#[test]
fn day_detail_list_and_replay_count_the_same_visits() {
    let db = TempDb::new("same-visits");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    for day in ["2026-10-05", "2026-10-10", "2026-10-11"] {
        let d = store.day_detail(day.into()).unwrap();
        let listed = summary_of(&store.day_summaries(30).unwrap(), day).unwrap().stay_count;
        assert_eq!(d.summary.stay_count, listed, "{day}");
        let r = store.day_replay(day.into(), NOW).unwrap();
        let want = if listed > 0 { format!("들른 곳 {listed}곳") } else { String::new() };
        assert!(r.stats_line.starts_with(&want), "{day}: {} vs {want}", r.stats_line);
    }
    // 강릉: 집 → 경포해변 → 초당할머니순두부 → 집. 돌아와 머문 집은 세지 않는다
    assert_eq!(store.day_detail("2026-10-10".into()).unwrap().summary.stay_count, 2);
}

// ---- "이 요약이 이상해요" (AI 하루 요약 숨기기) ----

#[test]
fn hiding_a_day_story_falls_back_to_rule_title_and_story_and_survives_reopen() {
    let db = TempDb::new("hide-story");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let day = |s: &EodiStore| summary_of(&s.day_summaries(30).unwrap(), "2026-10-11").unwrap().clone();
    let rule = day(&store);
    let rule_story = store.day_detail("2026-10-11".into()).unwrap().summary.story;
    assert!(rule_story.is_some());
    store
        .set_day_enrichment(DayEnrichment {
            day: "2026-10-11".into(),
            title: "대림창고에서 커피".into(),
            summary: "엉뚱한 AI 요약".into(),
            tags: vec!["카페".into()],
            episodes: vec![],
        })
        .unwrap();
    let ai = day(&store);
    assert_eq!(ai.title.as_deref(), Some("대림창고에서 커피"));
    assert_eq!(ai.story.as_deref(), Some("엉뚱한 AI 요약"));
    assert!(ai.ai_story && store.day_detail("2026-10-11".into()).unwrap().summary.ai_story);
    assert_eq!(search(&store, "엉뚱한"), ["2026-10-11"]);

    store.hide_day_story("2026-10-11".into(), NOW).unwrap();
    store.hide_day_story("2026-10-11".into(), NOW).unwrap(); // 다시 눌러도 그대로
    let hidden = day(&store);
    assert_eq!(hidden.title, rule.title, "규칙 제목으로 돌아간다");
    assert_eq!(hidden.story, rule.story, "규칙 요약으로 돌아간다");
    assert!(hidden.tags.is_empty());
    assert!(!hidden.ai_story && !store.day_detail("2026-10-11".into()).unwrap().summary.ai_story);
    assert_eq!(store.day_detail("2026-10-11".into()).unwrap().summary.story, rule_story, "하루 화면은 규칙 요약 문장");
    assert!(search(&store, "엉뚱한").is_empty(), "검색 색인에서도 빠진다");
    drop(store);
    let store = db.open();
    assert_eq!(day(&store).title, rule.title, "다시 열어도 숨긴 채");
    assert!(store.hide_day_story("2026-13-01".into(), NOW).is_err(), "틀린 날짜");
}

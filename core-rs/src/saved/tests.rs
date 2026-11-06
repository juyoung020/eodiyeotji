//! 저장한 곳 · 자주 가는 곳 테스트.

use super::*;
use crate::edits::tests::{drive, kst, name_places, pt, small_week, TempDb, CAFE, EMART, HOME, MIN, NOW, OFFICE, TOFU};
use crate::places::PlaceSource;

fn stay_spot(store: &EodiStore, day: &str, i: usize) -> PlaceSpot {
    let s = store.day_detail(day.into()).unwrap().stays[i].clone();
    PlaceSpot { kind: SpotKind::Stay, t_ms: s.arrive_ms, lat: s.center.lat, lon: s.center.lon }
}

fn names(cards: &[PlaceCard]) -> Vec<String> {
    cards.iter().map(|c| c.label.as_ref().map(|l| l.name.clone()).unwrap_or_default()).collect()
}

#[test]
fn saving_from_a_stay_row_and_listing_saved_places() {
    let db = TempDb::new("saved");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    let emart = store.place_card(stay_spot(&store, "2026-10-05", 1)).unwrap().unwrap();
    assert!(!emart.saved);
    assert_eq!(emart.visit_days, 2, "월 · 목");
    assert_eq!(emart.last_visit_day.as_deref(), Some("2026-10-08"));
    assert_eq!(emart.role, None);
    let tofu = store.place_card(stay_spot(&store, "2026-10-10", 1)).unwrap().unwrap();
    assert_eq!(tofu.label.as_ref().unwrap().name, "초당할머니순두부");

    store.set_place_saved(emart.id, true, 100).unwrap();
    store.set_place_saved(tofu.id, true, 200).unwrap();
    store.set_place_saved(tofu.id, true, 300).unwrap(); // 두 번 눌러도 처음 저장 시각
    assert_eq!(names(&store.saved_places().unwrap()), ["초당할머니순두부", "이마트 성수점"], "최근 저장 먼저");
    assert!(store.saved_places().unwrap().iter().all(|c| c.saved));

    // 검색 결과 좌표로 저장 여부 (100m 안 장소)
    let flags = store.places_saved_at(vec![pt(EMART), pt(CAFE), GeoPoint { lat: 10.0, lon: 10.0 }]).unwrap();
    assert_eq!(flags, [true, false, false]);
    // 좌표만으로도 카드를 찾는다
    let by_point = store.place_card(PlaceSpot { kind: SpotKind::Point, t_ms: 0, lat: TOFU.3, lon: TOFU.4 }).unwrap().unwrap();
    assert_eq!(by_point.id, tofu.id);
    assert!(by_point.saved);

    store.set_place_saved(emart.id, false, 400).unwrap();
    assert_eq!(names(&store.saved_places().unwrap()), ["초당할머니순두부"]);
    assert!(store.set_place_saved(999_999, true, 1).is_err());
    assert_eq!(store.place_card(PlaceSpot { kind: SpotKind::Point, t_ms: 0, lat: 10.0, lon: 10.0 }).unwrap(), None);

    // 집은 카드에 표시된다
    let home = store.place_card(PlaceSpot { kind: SpotKind::Point, t_ms: 0, lat: HOME.3, lon: HOME.4 }).unwrap().unwrap();
    assert_eq!(home.role, Some(AnchorRole::Home));
}

#[test]
fn trip_start_and_end_spots_use_the_linked_place() {
    let db = TempDb::new("spots");
    let store = db.open();
    small_week(&store);
    let t = store.day_detail("2026-10-11".into()).unwrap().trips[0].clone();
    let start = store.place_card(PlaceSpot { kind: SpotKind::TripStart, t_ms: t.start_ms, lat: 0.0, lon: 0.0 }).unwrap().unwrap();
    let end = store.place_card(PlaceSpot { kind: SpotKind::TripEnd, t_ms: t.end_ms, lat: 0.0, lon: 0.0 }).unwrap().unwrap();
    assert_eq!(start.label.unwrap().name, "응봉동 현대아파트");
    assert_eq!(end.label.unwrap().name, "대림창고");
}

#[test]
fn frequent_places_rank_by_visits_and_recency_without_home_office_or_hidden() {
    let db = TempDb::new("frequent");
    let store = db.open();
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    small_week(&store);
    // 카페에 두 번 더 (10-18, 10-25) → 카페 3일, 이마트 2일
    for d in [18, 25] {
        let e = drive(&store, kst(2026, 10, d, 13, 0), HOME, CAFE, 10.0);
        drive(&store, e + 90 * MIN, CAFE, HOME, 10.0);
    }
    name_places(&store);
    let now = kst(2026, 11, 1, 12, 0);
    let f = store.frequent_places(10, now).unwrap();
    assert_eq!(names(&f), ["대림창고", "이마트 성수점"], "집 · 회사 · 한 번 간 곳은 빠진다");
    assert_eq!(f[0].visit_days, 3);

    // 카페 방문을 숨기면 순위가 바뀐다 (2일 → 이마트와 같은 수, 남은 마지막 방문이 더 최근이라 카페가 위)
    let sun = store.day_detail("2026-10-25".into()).unwrap();
    store.hide_stay(sun.stays[0].arrive_ms, NOW).unwrap();
    let f = store.frequent_places(10, now).unwrap();
    assert_eq!(f[0].visit_days, 2);
    store.hide_day("2026-10-18".into(), NOW).unwrap();
    assert_eq!(names(&store.frequent_places(10, now).unwrap()), ["이마트 성수점"], "숨긴 방문은 세지 않는다");
    assert_eq!(store.frequent_places(0, now).unwrap(), vec![]);
}

#[test]
fn frequent_score_prefers_more_recent_and_longer_visits() {
    assert!(frequent_score(5, 0, 0) > frequent_score(4, 0, 0));
    assert!(frequent_score(3, 0, 0) > frequent_score(3, 120, 0), "최근이 위");
    assert!(frequent_score(3, 0, 5 * 3_600_000) > frequent_score(3, 0, 0), "오래 머문 곳이 위");
    assert!(frequent_score(3, 10_000, 0) >= 1.5, "아무리 오래돼도 절반은 남는다");
    assert_eq!(frequent_score(2, 0, 100 * 3_600_000), 2.0 + 1.0, "머문 시간 점수는 10시간까지");
}

#[test]
fn candidates_are_ranked_by_distance_without_duplicates() {
    let l = |n: &str| PlaceLabel { name: n.into(), category: String::new(), address: String::new(), source: PlaceSource::Kakao };
    let c = |n: &str, d: f64| PlaceCandidate { label: l(n), distance_m: d };
    let out = rank_place_candidates(vec![c("카페 B", 40.0), c("마트", 10.0), c("카페 B", 5.0), c(" ", 1.0), c("약국", 70.0)], 2);
    assert_eq!(out.iter().map(|x| x.name.as_str()).collect::<Vec<_>>(), ["카페 B", "마트"]);
}

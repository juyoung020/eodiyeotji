//! 장소 상세 창 테스트: 같은 곳 고르기, 리뷰 정렬 · 별점 · 하트, 내 방문 · 저장, 쿠폰, 잘못된 좌표.

use super::*;
use crate::community::encode_stops;
use crate::edits::tests::{small_week, TempDb, CAFE, EMART, TOFU};

fn stop(name: &str, lat: f64, lon: f64) -> StopFact {
    StopFact { name: name.into(), category: String::new(), address: String::new(), arrive_ms: 0, leave_ms: 0, lat, lon, first_visit: false }
}

/// 다른 사람이 쓴 리뷰 하나를 바로 넣는다.
fn put_review(store: &EodiStore, id: &str, name: &str, (lat, lon): (f64, f64), rating: i64, hearts: i64, created_ms: i64) {
    let conn = store.lock().unwrap();
    conn.execute(
        "INSERT INTO place_review (id, name, category, address, visit_ms, lat, lon, author, mine, rating, text, created_ms,
                                   visited_day, weather, others_hearts, my_heart)
         VALUES (?1, ?2, '두부전문점', '강원 강릉시 초당동 309-1', ?3, ?4, ?5, '바다좋아', 0, ?6, '맛있어요', ?3, '2026-10-10', '맑음 · 14°C', ?7, 0)",
        params![id, name, created_ms, lat, lon, rating, hearts],
    )
    .unwrap();
}

fn put_course(store: &EodiStore, id: &str, stops: &[StopFact], hearts: i64) {
    let conn = store.lock().unwrap();
    conn.execute(
        "INSERT INTO community_course (id, title, body, author, mine, region, tags, stops, distance_m, duration_ms, created_ms,
                                       visited_day, weather, others_hearts, my_heart)
         VALUES (?1, '강릉 한 바퀴', '', '바다좋아', 0, '강원 강릉시', '', ?2, 1000.0, 3600000, 1, '2026-10-10', NULL, ?3, 0)",
        params![id, encode_stops(stops), hearts],
    )
    .unwrap();
}

fn ids(d: &PlaceDetail) -> Vec<&str> {
    d.reviews.iter().map(|r| r.id.as_str()).collect()
}

#[test]
fn reviews_of_the_same_spot_sorted_by_hearts_with_rating_summary() {
    let db = TempDb::new("detail-reviews");
    let store = db.open();
    let (lat, lon) = (TOFU.3, TOFU.4);
    put_review(&store, "a", "초당할머니순두부", (lat, lon), 5, 3, 100);
    put_review(&store, "b", "초당 할머니 순두부", (lat + 0.001, lon), 4, 9, 200); // 띄어쓰기만 다름 · 110m
    put_review(&store, "c", "초당할머니순두부", (lat, lon), 4, 3, 300); // 하트가 같으면 최신이 먼저
    put_review(&store, "far", "초당할머니순두부", (lat + 0.02, lon), 1, 50, 400); // 같은 이름이라도 2km 밖
    put_review(&store, "other", "경포해변", (lat, lon), 1, 50, 500); // 같은 자리 다른 이름
    put_course(&store, "c1", &[stop("초당할머니순두부", lat, lon), stop("경포해변", 37.8055, 128.9083)], 7);
    put_course(&store, "c2", &[stop("경포해변", 37.8055, 128.9083)], 100);

    // 카카오 이름이 조금 달라도(거의 같은 이름, 300m 안) 같은 곳
    let d = store.place_detail(stop("초당할머니순두부 강릉", lat, lon)).unwrap();
    assert_eq!(ids(&d), ["b", "c", "a"]);
    assert_eq!(d.review_count, 3);
    assert!((d.avg_rating - 13.0 / 3.0).abs() < 1e-4);
    assert_eq!(d.rating_halves, 9, "4.33 → 별 4개 반");
    assert_eq!(d.hearts, 9 + 3 + 3 + 7, "리뷰 하트 + 이곳이 들어간 코스 하트");
    // 분류 · 주소는 리뷰에서 채운다
    assert_eq!(d.place.category, "두부전문점");
    assert_eq!(d.place.address, "강원 강릉시 초당동 309-1");
    // 시연 쿠폰 (offers 규칙)
    assert!(d.offers.iter().any(|o| o.id == "o-chodang-halmeoni"));
    // 내 기록에 없는 곳: 방문 · 저장 없음
    assert_eq!((d.visit_days, d.last_visit_day.as_deref(), d.place_id, d.saved), (0, None, None, false));
    assert!(d.last_visit.is_none());
}

#[test]
fn empty_place_and_bad_points() {
    let db = TempDb::new("detail-empty");
    let store = db.open();
    let d = store.place_detail(stop("아무 데도 아닌 곳", 36.0, 127.5)).unwrap();
    assert_eq!((d.review_count, d.hearts, d.rating_halves, d.avg_rating), (0, 0, 0, 0.0));
    assert!(d.reviews.is_empty() && d.offers.is_empty());
    assert!(store.place_detail(stop("x", f64::NAN, 127.0)).is_err());
    assert!(store.place_detail(stop("x", 0.0, 0.0)).is_err());
    assert!(store.place_detail(stop("x", 91.0, 127.0)).is_err());
}

#[test]
fn my_visits_saved_place_and_last_visit_for_review() {
    let db = TempDb::new("detail-mine");
    let store = db.open();
    small_week(&store);

    // 이마트: 월 · 목 저녁 두 번
    let d = store.place_detail(stop("이마트 성수점", EMART.3, EMART.4)).unwrap();
    assert_eq!(d.visit_days, 2);
    assert_eq!(d.last_visit_day.as_deref(), Some("2026-10-08"));
    let id = d.place_id.expect("내 기록의 장소");
    assert!(!d.saved);
    assert_eq!(d.place.category, "대형마트", "분류는 내 기록 이름에서 채운다");

    store.set_place_saved(id, true, 1).unwrap();
    let d = store.place_detail(stop("이마트 성수점", EMART.3, EMART.4)).unwrap();
    assert!(d.saved);
    assert_eq!(d.place_id, Some(id));

    // 리뷰 쓰기용 마지막 방문: 실제로 머문 곳 (들른 시각이 있다)
    let d = store.place_detail(stop("초당할머니순두부", TOFU.3, TOFU.4)).unwrap();
    assert_eq!(d.visit_days, 1);
    let v = d.last_visit.expect("강릉에서 들른 곳");
    assert_eq!(v.name, "초당할머니순두부");
    assert!(v.arrive_ms > 0);

    // 이름이 다른 가까운 곳(대림창고 ↔ 이마트 400m)은 섞지 않는다
    let d = store.place_detail(stop("대림창고", CAFE.3, CAFE.4)).unwrap();
    assert_eq!(d.visit_days, 1);
    assert!(!d.saved);
}

#[test]
fn same_spot_rules() {
    let q = Point { lat: 37.5, lon: 127.0 };
    let n = norm("이마트 성수점");
    let near = Point { lat: 37.5005, lon: 127.0 }; // 약 56m
    let mid = Point { lat: 37.504, lon: 127.0 }; // 약 445m
    assert!(same_spot(&n, q, "이마트성수점", mid), "같은 이름은 500m 안");
    assert!(!same_spot(&n, q, "이마트성수점", Point { lat: 37.51, lon: 127.0 }));
    assert!(same_spot(&n, q, "이마트 성수 1층", near), "거의 같은 이름은 300m 안");
    assert!(!same_spot(&n, q, "이마트 성수 1층", mid));
    assert!(!same_spot(&n, q, "이마트 왕십리점", near), "다른 지점(몇 글자만 겹침)은 가까워도 다른 곳");
    assert!(!same_spot(&n, q, "대림창고", near), "겹치는 글자가 없으면 다른 곳");
    assert!(same_spot(&n, q, "", near), "이름 모르는 곳은 150m 안");
    assert_eq!([rating_halves(0.0), rating_halves(4.74), rating_halves(4.76), rating_halves(5.0), rating_halves(f64::NAN)], [0, 9, 10, 10, 0]);
}

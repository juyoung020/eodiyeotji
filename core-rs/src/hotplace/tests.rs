//! 핫플 · 가고 싶은 곳 테스트.

use super::*;
use crate::edits::tests::{pt, small_week, TempDb, BEACH, NOW, TOFU};

fn spot(name: &str) -> StopFact {
    poi(name).unwrap()
}

fn hot<'a>(list: &'a [HotPlace], name: &str) -> &'a HotPlace {
    list.iter().find(|h| h.place.name == name).unwrap()
}

#[test]
fn hot_list_uses_only_known_places_in_order() {
    for (name, tag) in HOT {
        assert!(poi(name).is_some(), "{name}이 시연 장소 표에 없다");
        assert!(!tag.is_empty());
    }
    let db = TempDb::new("hot-order");
    let store = db.open();
    let all = store.hot_places(None, 0).unwrap();
    assert_eq!(all.len(), HOT.len());
    assert_eq!(all.iter().map(|h| h.rank).collect::<Vec<_>>(), (1..=HOT.len() as u32).collect::<Vec<_>>());
    assert_eq!(all[0].place.name, HOT[0].0);
    assert_eq!(store.hot_places(None, 3).unwrap().len(), 3);
    // 커뮤니티도 내 기록도 없으면 다녀온 사람 · 별점 · 내 방문이 모두 0
    assert!(all.iter().all(|h| h.visitors == 0 && h.review_count == 0 && h.avg_rating == 0.0 && h.my_visit_days == 0));
}

#[test]
fn visitors_count_distinct_review_and_course_authors() {
    let db = TempDb::new("hot-visitors");
    let store = db.open();
    store.seed_community_demo(NOW).unwrap();
    let all = store.hot_places(None, 0).unwrap();
    let conn = store.snapshot().unwrap();
    let (reviews, courses) = (load_reviews(&conn, None).unwrap(), load_courses(&conn, None).unwrap());
    for h in &all {
        let mut who: Vec<&str> = reviews.iter().filter(|r| r.place.name == h.place.name).map(|r| r.author.as_str()).collect();
        who.extend(courses.iter().filter(|c| c.stops.iter().any(|s| s.name == h.place.name)).map(|c| c.author.as_str()));
        who.sort_unstable();
        who.dedup();
        assert_eq!(h.visitors as usize, who.len(), "{}", h.place.name);
        assert_eq!(h.review_count as usize, reviews.iter().filter(|r| r.place.name == h.place.name).count());
    }
    let beach = hot(&all, "경포해변");
    assert!(beach.visitors >= 2, "경포해변은 리뷰 · 코스에 여러 사람이 올렸다");
    assert!((1.0..=5.0).contains(&beach.avg_rating));
    assert!(all.iter().any(|h| h.visitors > 0) && all.iter().any(|h| h.review_count > 0));
}

#[test]
fn my_visits_come_from_my_records() {
    let db = TempDb::new("hot-mine");
    let store = db.open();
    small_week(&store); // 10-10(토) 경포해변 · 초당할머니순두부
    let all = store.hot_places(None, 0).unwrap();
    let beach = hot(&all, "경포해변");
    assert_eq!(beach.my_visit_days, 1);
    assert_eq!(beach.my_last_day.as_deref(), Some("2026-10-10"));
    assert_eq!(hot(&all, "초당할머니순두부").my_visit_days, 1);
    let surf = hot(&all, "서피비치");
    assert_eq!((surf.my_visit_days, surf.my_last_day.clone()), (0, None));
}

#[test]
fn near_filter_keeps_places_in_radius() {
    let db = TempDb::new("hot-near");
    let store = db.open();
    let near = store.hot_places(Some(NearFilter { center: pt(BEACH), radius_km: 6.0 }), 0).unwrap();
    let names: Vec<&str> = near.iter().map(|h| h.place.name.as_str()).collect();
    assert_eq!(names, ["안목해변 카페거리", "경포해변", "초당할머니순두부"], "목록 순서 그대로");
    assert!(store.hot_places(Some(NearFilter { center: pt(BEACH), radius_km: 0.0 }), 0).is_err());
}

#[test]
fn wish_places_add_dedupe_remove() {
    let db = TempDb::new("wish");
    let store = db.open();
    let a = store.add_wish_place(spot("서피비치"), WishSource::HotList, String::new(), 100).unwrap();
    let b = store.add_wish_place(spot("어니언 성수"), WishSource::Instagram, "https://www.instagram.com/p/abc/".into(), 200).unwrap();
    assert_eq!(store.wish_places().unwrap().iter().map(|w| w.id).collect::<Vec<_>>(), [b.id, a.id], "최근에 넣은 것부터");
    assert_eq!(b.source, WishSource::Instagram);
    assert_eq!(b.url, "https://www.instagram.com/p/abc/");

    // 같은 곳을 다시 넣으면 처음 것. 주소가 비어 있었으면 채운다
    let again = store.add_wish_place(spot("서피비치"), WishSource::Instagram, "https://instagram.com/reel/x/".into(), 300).unwrap();
    assert_eq!(again.id, a.id);
    assert_eq!((again.source, again.url.as_str(), again.saved_ms), (WishSource::Instagram, "https://instagram.com/reel/x/", 100));
    assert_eq!(store.wish_places().unwrap().len(), 2);

    // 핫플 목록에 ♥ 표시
    let hots = store.hot_places(None, 0).unwrap();
    assert_eq!(hot(&hots, "서피비치").wish_id, Some(a.id));
    assert_eq!(hot(&hots, "경포해변").wish_id, None);

    assert!(store.remove_wish_place(a.id).unwrap());
    assert!(!store.remove_wish_place(a.id).unwrap());
    assert_eq!(store.wish_places().unwrap().len(), 1);

    let mut bad = spot("서피비치");
    bad.name = "  ".into();
    assert!(store.add_wish_place(bad, WishSource::Manual, String::new(), 1).is_err());
    let mut bad = spot("서피비치");
    bad.lat = 0.0;
    bad.lon = 0.0;
    assert!(store.add_wish_place(bad, WishSource::Manual, String::new(), 1).is_err());
}

#[test]
fn wish_near_skips_places_i_already_visited() {
    let db = TempDb::new("wish-near");
    let store = db.open();
    small_week(&store);
    store.add_wish_place(spot("경포해변"), WishSource::HotList, String::new(), 1).unwrap(); // 이미 다녀옴
    store.add_wish_place(spot("안목해변 카페거리"), WishSource::Instagram, String::new(), 2).unwrap();
    store.add_wish_place(spot("서피비치"), WishSource::HotList, String::new(), 3).unwrap(); // 멀다
    let listed = store.wish_places().unwrap();
    assert_eq!(listed.iter().find(|w| w.place.name == "경포해변").unwrap().my_visit_days, 1);
    let near = store.wish_places_near(pt(TOFU), 6_000.0).unwrap();
    assert_eq!(near.iter().map(|w| w.place.name.as_str()).collect::<Vec<_>>(), ["안목해변 카페거리"]);
    assert!(store.wish_places_near(pt(TOFU), -1.0).is_err());
}

#[test]
fn shared_text_splits_url_source_and_hint() {
    let s = parse_shared_text("강릉 오션뷰 카페 https://www.instagram.com/reel/Cxyz123/?igsh=MWabc== 꼭 가자".into());
    assert_eq!(s.source, WishSource::Instagram);
    assert_eq!(s.url, "https://www.instagram.com/reel/Cxyz123/", "공유한 사람을 가리키는 값은 버린다");
    assert_eq!(s.hint, "강릉 오션뷰 카페 꼭 가자");

    let naver = SharedLink { url: "https://naver.me/abc".into(), source: WishSource::NaverMap, hint: String::new() };
    assert_eq!(parse_shared_text("https://naver.me/abc".into()), naver);
    let kakao = parse_shared_text("[카카오맵] 서피비치 https://kko.to/xyz".into());
    assert_eq!((kakao.source, kakao.hint.as_str()), (WishSource::KakaoMap, "[카카오맵] 서피비치"));
    let web = parse_shared_text("http://example.com/a?b=1".into());
    assert_eq!((web.source, web.url.as_str()), (WishSource::Web, "http://example.com/a?b=1"));
    // 비슷한 이름의 다른 주소는 인스타그램이 아니다
    assert_eq!(parse_shared_text("https://notinstagram.com/p/1".into()).source, WishSource::Web);
    let plain = parse_shared_text("  서피비치   양양  ".into());
    assert_eq!((plain.source, plain.url.as_str(), plain.hint.as_str()), (WishSource::Manual, "", "서피비치 양양"));
    assert_eq!(parse_shared_text("가".repeat(200)).hint.chars().count(), 60);
}

#[test]
fn old_db_gets_wish_table() {
    let db = TempDb::new("wish-v16");
    {
        let store = db.open();
        let c = store.lock().unwrap();
        c.execute_batch(DROP_V17).unwrap();
        c.pragma_update(None, "user_version", 16).unwrap();
    }
    let store = db.open();
    assert!(store.wish_places().unwrap().is_empty());
    store.add_wish_place(spot("낙산사"), WishSource::HotList, String::new(), 1).unwrap();
    assert_eq!(store.wish_places().unwrap().len(), 1);
}

#[test]
fn pin_kind_from_category_text() {
    for (cat, want) in [
        ("카페", PinKind::Cafe),
        ("카페거리", PinKind::Cafe),
        ("두부전문점", PinKind::Food),
        ("전통시장", PinKind::Food),
        ("해수욕장", PinKind::Travel),
        ("사찰", PinKind::Travel),
        ("", PinKind::Travel),
    ] {
        assert_eq!(pin_kind(cat.into()), want, "{cat}");
    }
    let db = TempDb::new("hot-kind");
    let store = db.open();
    let all = store.hot_places(None, 0).unwrap();
    assert_eq!(hot(&all, "어니언 성수").kind, PinKind::Cafe);
    assert_eq!(hot(&all, "초당할머니순두부").kind, PinKind::Food);
    assert_eq!(hot(&all, "경포해변").kind, PinKind::Travel);
    let w = store.add_wish_place(spot("주문진수산시장"), WishSource::HotList, String::new(), 1).unwrap();
    assert_eq!(w.kind, PinKind::Food);
}

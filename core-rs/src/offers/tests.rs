//! 제휴 쿠폰 테스트: 장소 맞추기(이름 · 좌표), 코스 쿠폰, 쿠폰 받기 · 쿠폰함, DB 올리기.

use super::*;
use crate::store::SCHEMA_VERSION;

const KST: i32 = 9 * 60;

struct TempDb(std::path::PathBuf);

impl TempDb {
    fn new(tag: &str) -> Self {
        let db = TempDb(std::env::temp_dir().join(format!("eodi-offers-{tag}-{}.db", std::process::id())));
        db.remove();
        db
    }
    fn open(&self) -> std::sync::Arc<EodiStore> {
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

fn p(lat: f64, lon: f64) -> Option<Point> {
    Some(Point { lat, lon })
}

fn ids(v: &[PartnerOffer]) -> Vec<&str> {
    v.iter().map(|o| o.id.as_str()).collect()
}

fn stop(name: &str, lat: f64, lon: f64) -> StopFact {
    StopFact { name: name.into(), category: String::new(), address: String::new(), arrive_ms: 0, leave_ms: 0, lat, lon, first_visit: false }
}

#[test]
fn demo_offers_are_well_formed() {
    let all = all_offers();
    assert!(all.len() >= 12);
    for (i, o) in all.iter().enumerate() {
        assert!(!all[..i].iter().any(|x| x.id == o.id), "번호가 겹친다: {}", o.id);
        assert!(!o.badge.is_empty() && o.badge.chars().count() <= 12, "배지는 짧게: {}", o.badge);
        assert!(!o.title.is_empty() && !o.conditions.is_empty() && !o.partner_name.is_empty());
        assert!(o.valid_until.as_str() >= "2026-12-31", "공모전 시연(12월) 동안 유효: {}", o.id);
        assert!(o.valid_text.ends_with("일까지"));
        // 표의 좌표에서 자기 이름으로 찾으면 자기 자신이 나온다
        assert!(ids(&offers_at(&o.place_name, p(o.lat, o.lon))).contains(&o.id.as_str()), "{}", o.id);
    }
    assert_eq!(find_offer("o-sk-eungbong").unwrap().badge, "리터당 100원 할인");
    assert_eq!(find_offer("o-sk-eungbong").unwrap().category, OfferCategory::Fuel);
    assert_eq!(find_offer("o-chodang-halmeoni").unwrap().valid_text, "12월 31일까지");
    assert_eq!(find_offer("없는 쿠폰"), None);
}

#[test]
fn matching_by_name_and_by_nearby_coordinates() {
    // 이름만 (좌표 모름), 띄어쓰기 달라도
    assert_eq!(ids(&offers_at("초당할머니순두부", None)), ["o-chodang-halmeoni"]);
    assert_eq!(ids(&offers_at("테라로사 커피공장  강릉본점", None)), ["o-terarosa"]);
    assert_eq!(ids(&offers_at("테라로사커피공장강릉본점", None)), ["o-terarosa"]);
    // 한쪽이 다른 쪽을 품으면 (4글자 이상)
    assert_eq!(ids(&offers_at("이마트 성수점 1층", None)), ["o-emart-seongsu"]);
    assert!(offers_at("이마트", None).is_empty(), "짧은 이름(체인 이름만)은 아무 이마트에나 붙지 않는다");
    // 같은 이름이라도 멀면 다른 지점
    assert!(offers_at("스타벅스 왕십리역점", p(35.1587, 129.1604)).is_empty());
    // 이름이 조금 달라도 가깝고 거의 같으면 (카카오 이름 ↔ 쿠폰 이름)
    assert_eq!(ids(&offers_at("테라로사커피 강릉", p(37.7072, 128.8692))), ["o-terarosa"]);
    // 이웃 가게처럼 이름이 몇 글자만 겹치면 가까워도 다른 가게 (남의 가게 쿠폰이 섞이지 않는다)
    assert_eq!(ids(&offers_at("초당할머니순두부", p(37.7913, 128.9147))), ["o-chodang-halmeoni"]);
    // 가까워도 이름이 전혀 다르면 다른 가게
    assert!(offers_at("경포 편의점", p(37.7915, 128.9143)).is_empty());
    // 이름을 아직 모르면 아주 가까울 때만
    assert_eq!(ids(&offers_at("", p(37.5521, 127.0406))), ["o-sk-eungbong"]);
    assert!(offers_at("", p(37.5545, 127.0405)).is_empty(), "250m 떨어진 이름 모를 곳");
    // 좌표가 이상하면 이름으로만
    assert_eq!(ids(&offers_at("남이섬", p(f64::NAN, 0.0))), ["o-nami"]);
    // 쿠폰 없는 곳
    assert!(offers_at("경포해변", p(37.8055, 128.9083)).is_empty());
    assert!(offers_at("", None).is_empty());

    // 묶음: 넘긴 순서대로
    let got = offers_for(&[
        OfferPlace { name: "횡성휴게소".into(), point: Some(GeoPoint { lat: 37.47, lon: 128.03 }) },
        OfferPlace { name: "경복궁".into(), point: None },
        OfferPlace { name: "대관령 양떼목장".into(), point: None },
    ]);
    assert_eq!(got.iter().map(|v| ids(v).join(",")).collect::<Vec<_>>(), ["o-hoengseong", "", "o-sheep"]);
}

#[test]
fn course_offers_follow_stop_order_and_count_places() {
    // 시연 커뮤니티 코스 d03 "대관령 넘어 강릉 가는 길"
    let d03 = [
        stop("횡성휴게소", 37.47, 128.03),
        stop("대관령 양떼목장", 37.6877, 128.7537),
        stop("오죽헌", 37.7792, 128.8784),
        stop("안목해변 카페거리", 37.7722, 128.9474),
    ];
    let c = course_offers_of(&d03);
    assert_eq!(c.place_count, 3);
    assert_eq!(ids(&c.offers), ["o-hoengseong", "o-sheep", "o-anmok-cafe"]);
    assert_eq!(c.offers[2].partner_name, "안목 바다정원 카페", "카페거리는 그 안의 가게 이름으로 보인다");

    // 같은 곳을 두 번 들러도 쿠폰은 한 번, 이웃 가게(원조초당순두부) 쿠폰은 붙지 않는다
    let tofu = [stop("초당할머니순두부", 37.7913, 128.9147), stop("경포해변", 37.8055, 128.9083), stop("초당 할머니 순두부", 37.7915, 128.9142)];
    let c = course_offers_of(&tofu);
    assert_eq!((c.place_count, ids(&c.offers)), (1, vec!["o-chodang-halmeoni"]));

    assert_eq!(course_offers_of(&[]), CourseOffers { place_count: 0, offers: vec![] });
    assert_eq!(course_offers_of(&[stop("경포해변", 37.8055, 128.9083)]).place_count, 0);
}

#[test]
fn demo_courses_and_seed_places_have_offers() {
    // 커뮤니티 시연 코스 중 쿠폰이 붙는 코스가 여럿 있어야 화면에서 보인다
    let with = crate::community::demo::COURSES
        .iter()
        .filter(|c| {
            let stops: Vec<StopFact> = c
                .6
                .iter()
                .map(|n| {
                    let poi = crate::community::demo::PLACES.iter().find(|p| p.0 == *n).unwrap();
                    stop(poi.0, poi.3, poi.4)
                })
                .collect();
            course_offers_of(&stops).place_count > 0
        })
        .count();
    assert!(with >= 5, "{with}");
    // 시연 기록의 동네 볼일 (주유 · 장보기 · 커피)
    for (name, lat, lon) in [("SK에너지 응봉주유소", 37.5520, 127.0405), ("이마트 성수점", 37.5395, 127.0531), ("스타벅스 왕십리역점", 37.5614, 127.0369)] {
        assert_eq!(offers_at(name, p(lat, lon)).len(), 1, "{name}");
    }
}

#[test]
fn coupon_code_is_short_stable_and_readable() {
    let a = coupon_code("o-nami", 1_793_000_000_000);
    assert_eq!(a, coupon_code("o-nami", 1_793_000_000_000));
    assert_ne!(a, coupon_code("o-nami", 1_793_000_000_001));
    assert_ne!(a, coupon_code("o-sheep", 1_793_000_000_000));
    assert!(a.starts_with("EODI-") && a.len() == 9, "{a}");
    assert!(a[5..].chars().all(|c| !"0O1IB8S5".contains(c)), "{a}");
}

#[test]
fn claiming_offers_and_listing_my_coupons() {
    let db = TempDb::new("claim");
    let store = db.open();
    assert!(store.claimed_coupons().unwrap().is_empty());
    assert_eq!(store.claimed_coupon("o-nami".into()).unwrap(), None);

    let nami = store.claim_offer("o-nami".into(), 1_000).unwrap();
    assert_eq!((nami.offer.id.as_str(), nami.claimed_ms), ("o-nami", 1_000));
    assert!(nami.code.starts_with("EODI-"));
    // 다시 받아도 처음 받은 그대로
    assert_eq!(store.claim_offer("o-nami".into(), 9_000).unwrap(), nami);
    store.claim_offer("o-sk-eungbong".into(), 2_000).unwrap();
    assert!(matches!(store.claim_offer("없는 쿠폰".into(), 3_000), Err(CoreError::InvalidInput(_))));

    let mine = store.claimed_coupons().unwrap();
    assert_eq!(mine.iter().map(|c| c.offer.id.as_str()).collect::<Vec<_>>(), ["o-sk-eungbong", "o-nami"], "최근에 받은 것부터");
    assert_eq!(store.claimed_coupon("o-nami".into()).unwrap(), Some(nami));

    // 목록에서 없어진 쿠폰은 쿠폰함에서 빠진다
    store.lock().unwrap().execute("INSERT INTO claimed_coupon VALUES ('o-gone', 'EODI-AAAA', 5000)", []).unwrap();
    assert_eq!(store.claimed_coupons().unwrap().len(), 2);
    assert_eq!(store.claimed_coupon("o-gone".into()).unwrap(), None);

    // 다시 열어도 남아 있다
    drop(store);
    assert_eq!(db.open().claimed_coupons().unwrap().len(), 2);
}

#[test]
fn v7_database_gets_coupon_table() {
    let db = TempDb::new("v7");
    {
        let store = db.open();
        let c = store.lock().unwrap();
        c.execute_batch(DROP_V8).unwrap();
        c.pragma_update(None, "user_version", 7).unwrap();
    }
    let store = db.open();
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, SCHEMA_VERSION);
    assert_eq!(SCHEMA_VERSION, 10);
    store.claim_offer("o-sheep".into(), 1).unwrap();
    assert_eq!(store.claimed_coupons().unwrap().len(), 1);
}

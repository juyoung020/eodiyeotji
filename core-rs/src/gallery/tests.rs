//! 내 사진 맞추기: 시각 여유 · 거리 · 겹칠 때 고르기 · 순서 · 집/회사 거르기 · 올린 글 사진 저장 · DB 올리기.

use super::*;
use crate::community::CommunitySort;
use crate::edits::tests::{kst, pt, small_week, TempDb, BEACH, HOME, HOUR, MIN, NOW, OFFICE, TOFU};

fn win(arrive: i64, end: i64, lat: f64, lon: f64) -> StayWindow {
    StayWindow { arrive_ms: arrive, end_ms: end, center: Point { lat, lon }, private: false }
}

fn photo(id: i64, t: i64, at: Option<(f64, f64)>) -> GalleryPhoto {
    GalleryPhoto { id, uri: format!("content://media/external/images/media/{id}"), taken_ms: t, location: at.map(|(lat, lon)| GeoPoint { lat, lon }) }
}

fn ids(s: &StayPhotos) -> Vec<i64> {
    s.photos.iter().map(|p| p.photo.id).collect()
}

const T0: i64 = 1_762_480_000_000;

#[test]
fn time_margin_and_distance_decide_the_stay() {
    let beach = win(T0, T0 + 2 * HOUR, BEACH.3, BEACH.4);
    let photos = [
        photo(1, T0 + 30 * MIN, Some((BEACH.3 + 0.001, BEACH.4))), // 안, 약 110 m
        photo(2, T0 - 9 * MIN, None),                               // 도착 9분 전, 위치 없음 → 시각만으로
        photo(3, T0 - 11 * MIN, None),                              // 여유 밖
        photo(4, T0 + HOUR, Some((TOFU.3, TOFU.4))),                // 시각은 맞지만 1.5 km 떨어짐
        photo(5, T0 + 2 * HOUR + 10 * MIN, None),                   // 떠난 뒤 정확히 10분 → 포함
        photo(6, T0 + HOUR, Some((0.0, 0.0))),                      // 깨진 GPS → 위치 없음으로
    ];
    let out = match_stays(&[beach], &photos, &[]);
    assert_eq!(out.len(), 1);
    assert_eq!(out[0].arrive_ms, T0);
    assert_eq!(out[0].total, 4);
    // 위치까지 맞은 사진이 대표, 그다음 최신순
    assert_eq!(ids(&out[0]), vec![1, 5, 6, 2]);
    assert!(out[0].photos.iter().all(|p| p.shareable));
}

#[test]
fn overlapping_margins_go_to_one_stay_only() {
    // 경포 10:00~11:00, 초당 11:12~12:00 → 11:05 사진은 두 곳 여유 안 → 시간 차 작은 경포(5분) 쪽
    let a = win(T0, T0 + HOUR, BEACH.3, BEACH.4);
    let b = win(T0 + HOUR + 12 * MIN, T0 + 2 * HOUR, TOFU.3, TOFU.4);
    let photos = [photo(1, T0 + HOUR + 5 * MIN, None), photo(2, T0 + HOUR + 8 * MIN, None), photo(3, T0 + HOUR + 6 * MIN, Some((TOFU.3, TOFU.4)))];
    let out = match_stays(&[a, b], &photos, &[]);
    assert_eq!(out.len(), 2);
    assert_eq!(ids(&out[0]), vec![1], "경포: 5분 차");
    // 2번은 초당까지 4분 → 초당. 3번은 위치가 초당이라 경포 후보에서 빠진다
    assert_eq!(ids(&out[1]), vec![3, 2]);
    let n: usize = out.iter().map(|s| s.photos.len()).sum();
    assert_eq!(n, 3, "한 사진은 한 곳에만");
}

#[test]
fn caps_per_stay_but_counts_all_and_open_stay_looks_ahead() {
    let open = StayWindow::of(
        &StaySummary { center: GeoPoint { lat: BEACH.3, lon: BEACH.4 }, arrive_ms: T0, leave_ms: T0, place: None, order: 1 },
        &[],
    );
    assert_eq!(open.end_ms, T0 + OPEN_STAY_MS);
    let photos: Vec<GalleryPhoto> = (0..20).map(|i| photo(i, T0 + i * 5 * MIN, None)).collect();
    let out = match_stays(&[open], &photos, &[]);
    assert_eq!(out[0].total, 20);
    assert_eq!(out[0].photos.len(), MAX_PER_STAY);
    assert_eq!(out[0].photos[0].photo.id, 19, "최신이 먼저");
    assert!(match_stays(&[], &photos, &[]).is_empty());
    assert!(match_stays(&[open], &[], &[]).is_empty());
}

#[test]
fn photos_near_home_or_office_are_not_shareable() {
    let home = Point { lat: HOME.3, lon: HOME.4 };
    let cafe_near_home = StayWindow::of(
        &StaySummary { center: GeoPoint { lat: HOME.3 + 0.004, lon: HOME.4 }, arrive_ms: T0, leave_ms: T0 + HOUR, place: None, order: 1 },
        &[home],
    );
    assert!(cafe_near_home.private, "집에서 약 450 m");
    let beach = win(T0 + 3 * HOUR, T0 + 4 * HOUR, BEACH.3, BEACH.4);
    let photos = [
        photo(1, T0 + 10 * MIN, None),
        photo(2, T0 + 3 * HOUR + 5 * MIN, None),
        // 경포 시각인데 위치가 집 근처 (해변 반경 밖이라 맞지 않는다)
        photo(3, T0 + 3 * HOUR + 6 * MIN, Some((HOME.3, HOME.4))),
    ];
    let out = match_stays(&[cafe_near_home, beach], &photos, &[home]);
    assert_eq!(out.len(), 2);
    assert!(!out[0].photos[0].shareable, "집 근처 곳의 사진");
    assert_eq!(ids(&out[1]), vec![2]);
    assert!(out[1].photos[0].shareable);
    // 주행 중 사진: 집 근처 GPS만 뺀다
    let w = match_window(T0, T0 + HOUR, &[photo(7, T0 + 5 * MIN, Some((HOME.3, HOME.4))), photo(8, T0 + 20 * MIN, None), photo(9, T0 + 2 * HOUR, None)], &[home]);
    assert_eq!(w.iter().map(|m| (m.photo.id, m.shareable)).collect::<Vec<_>>(), vec![(8, true), (7, false)]);
}

#[test]
fn day_window_spans_all_stays_with_margin() {
    let w = day_window(&[win(T0 + HOUR, T0 + 2 * HOUR, 0.0, 0.0), win(T0, T0 + 30 * MIN, 0.0, 0.0)]);
    assert_eq!(w, GalleryWindow { from_ms: T0 - MARGIN_MS, to_ms: T0 + 2 * HOUR + MARGIN_MS });
    let empty = day_window(&[]);
    assert!(empty.from_ms > empty.to_ms);
}

#[test]
fn store_matches_a_recorded_day_and_hides_home_photos() {
    let db = TempDb::new("gallery-day");
    let store = db.open();
    small_week(&store);
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    let day = "2026-10-10".to_string();
    let detail = store.day_detail(day.clone()).unwrap();
    let beach = detail.stays.iter().find(|s| s.place.as_ref().is_some_and(|p| p.name == BEACH.0)).expect("경포").clone();
    let tofu = detail.stays.iter().find(|s| s.place.as_ref().is_some_and(|p| p.name == TOFU.0)).expect("초당").clone();
    let w = store.gallery_window(day.clone()).unwrap();
    assert!(w.from_ms <= beach.arrive_ms && w.to_ms >= tofu.leave_ms);
    let photos = vec![
        photo(1, beach.arrive_ms + 20 * MIN, Some((BEACH.3, BEACH.4))),
        photo(2, tofu.arrive_ms + 10 * MIN, None),
        photo(3, kst(2026, 10, 10, 4, 0), None), // 출발 전 새벽 (아무 곳도 아님)
    ];
    let got = store.gallery_for_day(day.clone(), photos).unwrap();
    let of = |t: i64| got.iter().find(|s| s.arrive_ms == t).map(ids);
    assert_eq!(of(beach.arrive_ms), Some(vec![1]));
    assert_eq!(of(tofu.arrive_ms), Some(vec![2]));
    assert!(got.iter().flat_map(|s| &s.photos).all(|p| p.photo.id != 3));
    // 집에 돌아와 머무는 곳의 사진은 올리기 후보가 아니다
    let home_stay = detail.stays.last().unwrap();
    let at_home = store.gallery_for_day(day, vec![photo(4, home_stay.arrive_ms + 5 * MIN, None)]).unwrap();
    assert_eq!(at_home.len(), 1);
    assert!(!at_home[0].photos[0].shareable);
    assert!(store.gallery_for_day("2026-13-01".into(), vec![]).is_err());
}

#[test]
fn course_stop_photos_follow_stops_and_can_be_cleared() {
    let db = TempDb::new("gallery-course");
    let store = db.open();
    small_week(&store);
    store.set_home_office(Some(pt(HOME)), Some(pt(OFFICE))).unwrap();
    let cand = store.share_candidates_on("2026-10-10".into()).unwrap().into_iter().next().expect("강릉 나들이");
    let post = store.share_course(cand.episode_id, "강릉".into(), "".into(), NOW).unwrap();
    assert!(post.stop_photos.iter().all(String::is_empty) && post.stop_photos.len() == post.stops.len());
    let first = post.stops[0].arrive_ms;
    let updated = store
        .set_course_stop_photos(
            post.id.clone(),
            vec![
                StopPhotoRef { arrive_ms: first, path: "/data/p/1.jpg".into() },
                StopPhotoRef { arrive_ms: 42, path: "/data/p/x.jpg".into() }, // 글에 없는 곳 → 버림
                StopPhotoRef { arrive_ms: first + 1, path: " ".into() },
            ],
        )
        .unwrap();
    assert_eq!(updated.stop_photos[0], "/data/p/1.jpg");
    assert!(updated.stop_photos[1..].iter().all(String::is_empty));
    // 목록으로 다시 읽어도 같다
    let listed = store.community_courses(CommunitySort::Recent, None, 0).unwrap().into_iter().find(|c| c.id == post.id).unwrap();
    assert_eq!(listed.stop_photos, updated.stop_photos);
    // 다시 올려도 사진은 남고, 빈 목록으로 떼어 낸다
    let again = store.share_course(cand.episode_id, "강릉 2".into(), "".into(), NOW + 1).unwrap();
    assert_eq!(again.stop_photos[0], "/data/p/1.jpg");
    assert!(store.set_course_stop_photos(post.id.clone(), vec![]).unwrap().stop_photos.iter().all(String::is_empty));
    // 남의 글은 못 바꾼다
    assert!(store.seed_community_demo(NOW).unwrap() > 0);
    let other = store.community_courses(CommunitySort::Recent, None, 0).unwrap().into_iter().find(|c| !c.mine).unwrap();
    assert!(store.set_course_stop_photos(other.id, vec![]).is_err());
}

#[test]
fn drive_route_photos_only_on_my_routes() {
    let db = TempDb::new("gallery-route");
    let store = db.open();
    assert!(store.seed_community_demo(NOW).unwrap() > 0);
    let other = store.drive_routes(crate::community::drive::DriveRouteSort::Recent, None, 1).unwrap()[0].route.id.clone();
    assert!(store.set_drive_route_photos(other.clone(), vec!["/a.jpg".into()]).is_err());
    // 내 글로 바꿔 두고 붙이기: 빈 경로는 버리고 4장까지
    store.lock_community().unwrap().execute("UPDATE drive_route SET mine = 1 WHERE id = ?1", [&other]).unwrap();
    let paths: Vec<String> = ["/1.jpg", "", "/2.jpg", "/3.jpg", "/4.jpg", "/5.jpg"].iter().map(|s| s.to_string()).collect();
    store.set_drive_route_photos(other.clone(), paths).unwrap();
    assert_eq!(store.drive_route(other).unwrap().photos, vec!["/1.jpg", "/2.jpg", "/3.jpg", "/4.jpg"]);
}

#[test]
fn v16_database_gets_photo_columns() {
    let db = TempDb::new("gallery-v16");
    {
        let store = db.open();
        assert!(store.seed_community_demo(NOW).unwrap() > 0);
        let c = store.lock().unwrap();
        c.execute_batch("ALTER TABLE community_course DROP COLUMN stop_photos; ALTER TABLE drive_route DROP COLUMN photos;").unwrap();
        c.pragma_update(None, "user_version", 16).unwrap();
    }
    let store = db.open();
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, crate::store::SCHEMA_VERSION);
    let courses = store.community_courses(CommunitySort::Recent, None, 0).unwrap();
    assert!(!courses.is_empty());
    assert!(courses.iter().all(|c| c.stop_photos.len() == c.stops.len() && c.stop_photos.iter().all(String::is_empty)));
    let routes = store.drive_routes(crate::community::drive::DriveRouteSort::Recent, None, 0).unwrap();
    assert!(!routes.is_empty() && routes.iter().all(|r| r.route.photos.is_empty()));
}

#[test]
fn map_spots_pin_photos_where_taken_or_at_the_stay() {
    let stays = [win(10 * HOUR, 11 * HOUR, 37.0, 127.0), win(14 * HOUR, 15 * HOUR, 37.5, 127.5)];
    let photos = [
        photo(1, 10 * HOUR + 5 * MIN, None),                      // 위치 없음 → 첫 곳 가운데
        photo(2, 12 * HOUR, Some((37.2, 127.2))),                 // 길 위에서 찍음 → 찍은 곳
        photo(3, 12 * HOUR + MIN, None),                          // 위치도 맞는 곳도 없음 → 빠짐
        photo(4, 14 * HOUR + 10 * MIN, Some((37.5005, 127.5))),   // 둘째 곳 근처 → 찍은 곳 그대로
        photo(5, 3 * HOUR, Some((37.0, 127.0))),                  // 하루 범위 밖 → 빠짐
    ];
    let v = map_spots(&stays, &photos);
    assert_eq!(v.iter().map(|s| s.id).collect::<Vec<_>>(), vec![1, 2, 4]);
    assert_eq!((v[0].at.lat, v[0].stay_arrive_ms), (37.0, Some(10 * HOUR)));
    assert_eq!((v[1].at.lat, v[1].stay_arrive_ms), (37.2, None));
    assert_eq!((v[2].at.lat, v[2].stay_arrive_ms), (37.5005, Some(14 * HOUR)));
}

//! 커뮤니티: 피드 정렬·근처·하트·인기 장소·추천 점수·지도·구간·내 기록 올리기·게시판·계절·날씨·시연 데이터·DB 올리기.

use super::demo::{self, BOARD, COURSES, PLACES, REVIEWS};
use super::weather::{clean_weather, open_meteo_url, parse_open_meteo_weather, season_label, wmo_text};
use super::*;
use crate::store::SCHEMA_VERSION;

/// 2026-11-06 12:00 KST
const NOW: i64 = 1_793_934_000_000;
const KST: i32 = 540;

struct TempDb(std::path::PathBuf);

impl TempDb {
    fn new(tag: &str) -> Self {
        let p = std::env::temp_dir().join(format!("eodi-community-{tag}-{}.db", std::process::id()));
        let db = TempDb(p);
        db.remove();
        db
    }
    fn path(&self) -> String {
        self.0.to_string_lossy().into_owned()
    }
    fn open(&self) -> std::sync::Arc<EodiStore> {
        EodiStore::open(self.path(), KST).unwrap()
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

fn seeded(tag: &str) -> (TempDb, std::sync::Arc<EodiStore>) {
    let db = TempDb::new(tag);
    let store = db.open();
    assert!(store.seed_community_demo(NOW).unwrap() > 0);
    (db, store)
}

fn gp(lat: f64, lon: f64) -> GeoPoint {
    GeoPoint { lat, lon }
}

fn place(name: &str, lat: f64, lon: f64) -> StopFact {
    StopFact { name: name.into(), category: "카페".into(), address: "".into(), arrive_ms: 0, leave_ms: 0, lat, lon, first_visit: false }
}

// ---- 시연 데이터 ----

#[test]
fn demo_places_are_real_korea_coordinates_and_every_name_resolves() {
    for p in PLACES {
        assert!((33.0..38.7).contains(&p.3) && (124.5..131.0).contains(&p.4), "{p:?}");
        assert!(!p.2.is_empty(), "{p:?} 주소");
    }
    let mut names: Vec<&str> = PLACES.iter().map(|p| p.0).collect();
    names.sort();
    names.dedup();
    assert_eq!(names.len(), PLACES.len(), "장소 이름 중복");
    for c in COURSES {
        assert!((2..=5).contains(&c.6.len()), "{} 들른 곳 수", c.0);
        for n in c.6 {
            assert!(demo::poi(n).is_some(), "{}: {n} 없음", c.0);
        }
    }
    for r in REVIEWS {
        assert!(demo::poi(r.0).is_some(), "리뷰 장소 {} 없음", r.0);
        assert!((1..=5).contains(&r.2));
    }
    assert!((20..=40).contains(&COURSES.len()));
    assert!((60..=100).contains(&REVIEWS.len()));
    assert!((15..=25).contains(&BOARD.len()));
    assert!(BOARD.iter().all(|b| (2..=6).contains(&b.8.len())));
}

#[test]
fn demo_route_stops_are_close_enough_to_drive_between() {
    // 같은 코스의 이웃한 곳이 150km를 넘으면 좌표·이름이 잘못 들어간 것
    for c in COURSES {
        for w in c.6.windows(2) {
            let (a, b) = (demo::poi(w[0]).unwrap(), demo::poi(w[1]).unwrap());
            let d = haversine_m(Point { lat: a.3, lon: a.4 }, Point { lat: b.3, lon: b.4 });
            assert!(d < 150_000.0, "{}: {} → {} {d:.0}m", c.0, w[0], w[1]);
        }
    }
}

#[test]
fn seeding_is_idempotent_and_fills_every_table() {
    let (_db, store) = seeded("seed");
    assert_eq!(store.seed_community_demo(NOW + 1).unwrap(), 0, "두 번째는 아무것도 안 넣는다");
    let courses = store.community_courses(CommunitySort::Recent, None, 0).unwrap();
    assert_eq!(courses.len(), COURSES.len());
    assert!(courses.iter().all(|c| !c.mine && c.stops.len() >= 2 && c.distance_m > 0.0));
    assert_eq!(courses.iter().filter(|c| c.hearted_by_me).count(), 3);
    assert!(courses.iter().all(|c| is_day(&c.visited_day) && !c.season.is_empty() && c.weather.is_some()));
    // 다녀온 날은 올린 날보다 앞
    assert!(courses.iter().all(|c| c.stops[0].arrive_ms < c.created_ms));
    assert_eq!(store.place_reviews(CommunitySort::Recent, None, 500).unwrap().len(), REVIEWS.len());
    let posts = store.board_posts(CommunitySort::Recent, 0).unwrap();
    assert_eq!(posts.len(), BOARD.len());
    let comments: u32 = posts.iter().map(|p| p.comment_count).sum();
    assert_eq!(comments as usize, BOARD.iter().map(|b| b.8.len()).sum::<usize>());
    assert!(store.seed_community_demo(0).is_err());
}

#[test]
fn demo_weather_looks_like_a_real_reading() {
    let w = demo::demo_weather("2026-10-20", 0);
    assert_eq!(w, "맑음 · 18°C");
    assert!(demo::demo_weather("2026-01-05", 3).starts_with("흐림 · "));
}

// ---- 정렬 · 근처 ----

#[test]
fn popular_sort_is_by_hearts_and_recent_by_time() {
    let (_db, store) = seeded("sort");
    let pop = store.community_courses(CommunitySort::Popular, None, 5).unwrap();
    assert_eq!(pop.len(), 5);
    assert!(pop.windows(2).all(|w| w[0].hearts >= w[1].hearts));
    assert_eq!(pop[0].title, "7번 국도 동해안 드라이브", "가장 하트 많은 코스 (강릉)");
    let recent = store.community_courses(CommunitySort::Recent, None, 0).unwrap();
    assert!(recent.windows(2).all(|w| w[0].created_ms >= w[1].created_ms));
    let reviews = store.place_reviews(CommunitySort::Popular, None, 0).unwrap();
    assert!(reviews.windows(2).all(|w| w[0].hearts >= w[1].hearts));
    assert_eq!(reviews[0].place.name, "초당할머니순두부");
}

#[test]
fn near_filter_keeps_courses_with_any_stop_in_radius() {
    let (_db, store) = seeded("near");
    // 양평 두물머리 20km 안: 북한강·남한강·수종사 코스
    let near = NearFilter { center: gp(37.5344, 127.3189), radius_km: 20.0 };
    let ids: Vec<String> = store.community_courses(CommunitySort::Popular, Some(near), 0).unwrap().into_iter().map(|c| c.id).collect();
    assert_eq!(ids, ["d04", "d05", "d06"]);
    let reviews = store.place_reviews(CommunitySort::Recent, Some(near), 0).unwrap();
    assert!(!reviews.is_empty());
    assert!(reviews.iter().all(|r| haversine_m(Point { lat: 37.5344, lon: 127.3189 }, stop_point(&r.place)) <= 20_000.0));
    let bad = NearFilter { center: gp(f64::NAN, 0.0), radius_km: 1.0 };
    assert!(store.community_courses(CommunitySort::Popular, Some(bad), 0).is_err());
    let zero = NearFilter { center: gp(37.5, 127.0), radius_km: 0.0 };
    assert!(store.place_reviews(CommunitySort::Popular, Some(zero), 0).is_err());
}

// ---- 하트 ----

#[test]
fn hearts_toggle_and_survive_reopen() {
    let db = TempDb::new("heart");
    {
        let store = db.open();
        store.seed_community_demo(NOW).unwrap();
        // d02: 다른 사람 438, 내 하트 없음
        assert_eq!(store.toggle_course_heart("d02".into()).unwrap(), 439);
        assert_eq!(store.toggle_course_heart("d02".into()).unwrap(), 438);
        assert_eq!(store.toggle_course_heart("d02".into()).unwrap(), 439);
        // d01은 이미 내가 누른 상태 (512 + 1)
        assert_eq!(store.toggle_course_heart("d01".into()).unwrap(), 512);
        assert_eq!(store.toggle_review_heart("r001".into()).unwrap(), 133);
        assert_eq!(store.toggle_board_post_heart("b01".into()).unwrap(), 35);
        assert_eq!(store.toggle_board_comment_heart("b01-c1".into()).unwrap(), 22);
        assert!(matches!(store.toggle_course_heart("없음".into()), Err(CoreError::InvalidInput(_))));
        assert!(store.toggle_review_heart("없음".into()).is_err());
        assert!(store.toggle_board_post_heart("없음".into()).is_err());
        assert!(store.toggle_board_comment_heart("없음".into()).is_err());
    }
    let store = db.open();
    let all = store.community_courses(CommunitySort::Popular, None, 0).unwrap();
    let d02 = all.iter().find(|c| c.id == "d02").unwrap();
    assert!(d02.hearted_by_me && d02.hearts == 439);
    assert!(!all.iter().find(|c| c.id == "d01").unwrap().hearted_by_me);
}

// ---- 인기 장소 ----

#[test]
fn popular_places_sum_review_and_course_hearts_per_place() {
    let (_db, store) = seeded("popular");
    let top = store.popular_places(None, 500).unwrap();
    assert!(top.windows(2).all(|w| w[0].hearts >= w[1].hearts));
    let dumul = top.iter().find(|p| p.place.name == "두물머리").unwrap();
    // 리뷰 3개(141+64+23) + 코스 d04(455+1 내 하트)·d05(377)·d06(212)
    assert_eq!(dumul.review_count, 3);
    assert_eq!(dumul.hearts, 141 + 64 + 23 + 456 + 377 + 212);
    assert!((dumul.avg_rating - 14.0 / 3.0).abs() < 1e-4);
    assert_eq!(dumul.place.arrive_ms, 0, "장소 묶음은 들른 시각이 없다");
    // 같은 이름 = 한 줄
    assert_eq!(top.iter().filter(|p| p.place.name == "경포해변").count(), 1);
    // 리뷰 없는 코스 장소도 나온다 (별점 0)
    let bridge = top.iter().find(|p| p.place.name == "남해대교").unwrap();
    assert_eq!((bridge.review_count, bridge.avg_rating), (0, 0.0));
    // 근처만
    let near = NearFilter { center: gp(33.4626, 126.3108), radius_km: 30.0 };
    assert!(store.popular_places(Some(near), 0).unwrap().iter().all(|p| p.place.address.starts_with("제주")));
}

#[test]
fn same_place_needs_same_name_and_500m() {
    let a = place("경포해변", 37.8055, 128.9083);
    assert!(same_place(&a, &place("경포 해변", 37.8080, 128.9083)), "띄어쓰기 무시, 280m");
    assert!(!same_place(&a, &place("경포해변", 37.8155, 128.9083)), "1.1km");
    assert!(!same_place(&a, &place("강문해변", 37.8055, 128.9083)));
}

#[test]
fn reviews_of_place_finds_only_that_place() {
    let (_db, store) = seeded("ofplace");
    let r = store.reviews_of_place(place("초당할머니순두부", 37.7916, 128.9141), CommunitySort::Popular, 0).unwrap();
    assert_eq!(r.len(), 3);
    assert!(r.iter().all(|r| r.place.name == "초당할머니순두부"));
    assert_eq!(r[0].hearts, 156);
}

// ---- 추천 ----

#[test]
fn score_trades_popularity_against_distance_and_quality() {
    // 가까울수록, 하트가 많을수록, 들른 곳이 3~5곳일수록, 별점이 높을수록 높다
    assert!(course_score(100, 1.0, 30.0, 4, None) > course_score(100, 20.0, 30.0, 4, None));
    assert!(course_score(400, 5.0, 30.0, 4, None) > course_score(40, 5.0, 30.0, 4, None));
    assert!(course_score(100, 5.0, 30.0, 4, None) > course_score(100, 5.0, 30.0, 1, None));
    assert!(course_score(100, 5.0, 30.0, 4, Some(5.0)) > course_score(100, 5.0, 30.0, 4, Some(2.0)));
    // 반경의 1/3마다 거리 감쇠가 반으로
    let near = course_score(100, 0.0, 30.0, 4, None);
    assert!((course_score(100, 10.0, 30.0, 4, None) / near - 0.5).abs() < 1e-9);
    // 하트 10배 = 점수 10배가 아니다: 하트 10배·거리 1/3 지점이면 가까운 쪽이 이긴다
    assert!(course_score(50, 2.0, 30.0, 4, None) > course_score(500, 12.0, 30.0, 4, None));
    // 반경이 작아도 감쇠 기준은 3km 밑으로 안 내려간다
    assert!((course_score(1, 3.0, 3.0, 4, None) / course_score(1, 0.0, 3.0, 4, None) - 0.5).abs() < 1e-9);
}

#[test]
fn recommend_prefers_nearby_popular_courses_and_skips_far_ones() {
    let (_db, store) = seeded("rec");
    // 강릉 경포에서 40km: 강릉 코스들, 경포에서 출발하는 d01이 1등
    let recs = store.recommend_drive_courses(gp(37.8055, 128.9083), 40.0, 3).unwrap();
    assert_eq!(recs.len(), 3);
    assert_eq!(recs[0].post.id, "d01");
    assert!(recs[0].distance_km < 0.5);
    assert!(recs.windows(2).all(|w| w[0].score >= w[1].score));
    assert!(recs.iter().all(|r| r.distance_km <= 40.0));
    // 양평 두물머리: 북한강·남한강·수종사
    let ids: Vec<String> = store.recommend_drive_courses(gp(37.5344, 127.3189), 30.0, 5).unwrap().into_iter().map(|r| r.post.id).collect();
    assert_eq!(&ids[..2], ["d04", "d05"]);
    // 아무것도 없는 바다 한가운데
    assert!(store.recommend_drive_courses(gp(36.0, 131.5), 30.0, 3).unwrap().is_empty());
    // 기본값: k 0 → 3개, 반경 NaN → 30km
    assert!(store.recommend_drive_courses(gp(37.5665, 126.9780), f64::NAN, 0).unwrap().len() <= 3);
    assert!(store.recommend_drive_courses(gp(f64::NAN, 1.0), 30.0, 3).is_err());
}

// ---- 지도 · 구간 ----

#[test]
fn course_map_is_stay_points_in_order() {
    let (_db, store) = seeded("map");
    let m = store.course_map("d13".into()).unwrap();
    let v: Value = serde_json::from_str(&m.geojson).unwrap();
    let f = v["features"].as_array().unwrap();
    assert_eq!(f.len(), 5);
    assert_eq!(f[0]["properties"]["kind"], "stay");
    assert_eq!(f[0]["properties"]["key"], "s1");
    assert_eq!(f[4]["properties"]["order"], 5);
    assert_eq!(f[0]["properties"]["name"], "영종대교휴게소");
    assert_eq!(f[0]["geometry"]["coordinates"][0].as_f64().unwrap(), 126.5856);
    assert_eq!(m.entries.len(), 5);
    let b = m.bounds.unwrap();
    assert!(b.min_lat <= 37.4152 && b.max_lat >= 37.5513);
    assert_eq!(m.day, "d13");
    assert!(store.course_map("없음".into()).is_err());
    // 지도 글꼴로 못 그리는 글자는 걸러진다
    let odd = stops_map("x", &[place("카페 ★ 바다", 37.0, 127.0)]);
    assert!(odd.geojson.contains(r#""name":"카페 바다""#), "{}", odd.geojson);
    assert!(stops_map("x", &[]).bounds.is_none());
}

#[test]
fn course_legs_keep_order_with_two_waypoints_each() {
    let (_db, store) = seeded("legs");
    let legs = store.course_legs("d13".into()).unwrap();
    assert_eq!(legs.len(), 2);
    assert_eq!(legs[0].waypoints.len(), 2);
    assert_eq!(legs[0].destination.name, "선녀바위해수욕장");
    assert_eq!(legs[1].waypoints.iter().map(|s| s.name.as_str()).collect::<Vec<_>>(), ["을왕리해수욕장"]);
    assert_eq!(legs[1].destination.name, "왕산해수욕장");
    let short = store.course_legs("d12".into()).unwrap();
    assert_eq!(short.len(), 1);
    assert_eq!(short[0].waypoints.len(), 1);
    assert!(store.course_legs("없음".into()).is_err());
}

// ---- 리뷰 쓰기 ----

#[test]
fn add_review_validates_and_appears_first_in_recent_feed() {
    let (_db, store) = seeded("review");
    let mut p = place("초당할머니순두부", 37.7915, 128.9142);
    p.arrive_ms = NOW - 3 * 86_400_000;
    let r = store.add_place_review_with(p.clone(), 5, "  아침 7시 반에 가니 바로 앉았어요  ".into(), Some(" 맑음 · 15°C ".into()), NOW).unwrap();
    assert!(r.mine);
    assert_eq!(r.author, ME);
    assert_eq!(r.text, "아침 7시 반에 가니 바로 앉았어요");
    assert_eq!(r.visited_day, "2026-11-03");
    assert_eq!(r.season, "가을 · 단풍철");
    assert_eq!(r.weather.as_deref(), Some("맑음 · 15°C"));
    let feed = store.place_reviews(CommunitySort::Recent, None, 1).unwrap();
    assert_eq!(feed[0].id, r.id);
    // 같은 밀리초에 또 써도 id가 다르다
    let r2 = store.add_place_review(p.clone(), 4, "또".into(), NOW).unwrap();
    assert_ne!(r2.id, r.id);
    assert_eq!(r2.weather, None);
    // 인기 장소에 반영
    let top = store.popular_places(None, 500).unwrap();
    assert_eq!(top.iter().find(|x| x.place.name == "초당할머니순두부").unwrap().review_count, 5);
    assert!(store.add_place_review(p.clone(), 0, "".into(), NOW).is_err());
    assert!(store.add_place_review(p.clone(), 6, "".into(), NOW).is_err());
    assert!(store.add_place_review(place(" ", 37.0, 127.0), 3, "".into(), NOW).is_err());
    assert!(store.add_place_review(place("x", 0.0, 0.0), 3, "".into(), NOW).is_err());
    let long = store.add_place_review(p, 3, "가".repeat(900), NOW).unwrap();
    assert_eq!(long.text.chars().count(), MAX_REVIEW_CHARS);
}

// ---- 게시판 ----

#[test]
fn board_posts_comments_and_writing() {
    let (_db, store) = seeded("board");
    let pop = store.board_posts(CommunitySort::Popular, 0).unwrap();
    assert!(pop.windows(2).all(|w| w[0].hearts >= w[1].hearts));
    assert_eq!(pop[0].id, "b17");
    let b03 = store.board_post("b03".into()).unwrap().unwrap();
    assert_eq!(b03.comment_count, 4);
    assert_eq!(b03.region, None);
    let comments = store.board_comments("b03".into()).unwrap();
    assert_eq!(comments.len(), 4);
    assert!(comments.windows(2).all(|w| w[0].created_ms <= w[1].created_ms));
    assert!(comments.iter().all(|c| c.created_ms > b03.created_ms && c.created_ms < NOW));
    assert!(store.board_post("없음".into()).unwrap().is_none());
    assert!(store.board_comments("없음".into()).unwrap().is_empty());

    let mine = store.add_board_post(" 강릉 주차 질문 ".into(), "경포 근처 주차 어디가 좋아요?".into(), vec!["#강릉".into(), " ".into(), "주차".into()], NOW).unwrap();
    assert!(mine.mine && mine.title == "강릉 주차 질문" && mine.tags == ["강릉", "주차"] && mine.comment_count == 0);
    assert_eq!(store.board_posts(CommunitySort::Recent, 1).unwrap()[0].id, mine.id);
    let c = store.add_board_comment(mine.id.clone(), " 경포 공영주차장이요 ".into(), NOW + 1).unwrap();
    assert_eq!((c.body.as_str(), c.mine, c.author.as_str()), ("경포 공영주차장이요", true, ME));
    assert_eq!(store.board_post(mine.id.clone()).unwrap().unwrap().comment_count, 1);
    assert_eq!(store.toggle_board_comment_heart(c.id).unwrap(), 1);
    assert!(store.add_board_post(" ".into(), "".into(), vec![], NOW).is_err());
    assert!(store.add_board_comment(mine.id, " ".into(), NOW).is_err());
    assert!(store.add_board_comment("없음".into(), "안녕".into(), NOW).is_err());
}

// ---- 계절 · 날씨 ----

#[test]
fn seasons_follow_the_calendar_with_named_peaks() {
    assert_eq!(season_label("2026-04-05".into()), "봄 · 벚꽃철");
    assert_eq!(season_label("2026-05-20".into()), "봄");
    assert_eq!(season_label("2026-03-01".into()), "봄");
    assert_eq!(season_label("2026-06-30".into()), "여름");
    assert_eq!(season_label("2026-08-01".into()), "여름 · 휴가철");
    assert_eq!(season_label("2026-09-15".into()), "가을");
    assert_eq!(season_label("2026-10-20".into()), "가을 · 단풍철");
    assert_eq!(season_label("2026-11-10".into()), "가을 · 단풍철");
    assert_eq!(season_label("2026-11-11".into()), "가을");
    assert_eq!(season_label("2026-12-01".into()), "겨울");
    assert_eq!(season_label("2026-12-25".into()), "겨울 · 눈꽃철");
    assert_eq!(season_label("2027-01-15".into()), "겨울 · 눈꽃철");
    assert_eq!(season_label("2027-02-20".into()), "겨울");
    assert_eq!(season_label("어제".into()), "");
}

#[test]
fn open_meteo_url_picks_forecast_for_recent_days_and_archive_for_old() {
    let recent = open_meteo_url(37.8055, 128.9083, "2026-11-03".into(), "2026-11-06".into());
    assert!(recent.starts_with("https://api.open-meteo.com/v1/forecast?"), "{recent}");
    assert!(recent.contains("past_days=4") && recent.contains("latitude=37.8055") && recent.contains("weather_code"));
    let old = open_meteo_url(37.8055, 128.9083, "2025-11-06".into(), "2026-11-06".into());
    assert!(old.starts_with("https://archive-api.open-meteo.com/v1/archive?"), "{old}");
    assert!(old.contains("start_date=2025-11-06&end_date=2025-11-06") && old.contains("timezone=Asia%2FSeoul"));
    assert_eq!(open_meteo_url(37.0, 127.0, "x".into(), "2026-11-06".into()), "");
}

#[test]
fn visit_weather_request_uses_local_day_of_visit() {
    let db = TempDb::new("wreq");
    let store = db.open();
    // 2025-11-06 23:30 KST = 2025-11-06 14:30 UTC → 현지 날짜 11월 6일, 1년 전이라 archive
    let t = NOW - 365 * 86_400_000 + 11 * 3_600_000 + 30 * 60_000;
    let r = store.visit_weather_request(37.8, 128.9, t);
    assert_eq!(r.day, "2025-11-06");
    assert!(r.url.contains("archive-api") && r.url.contains("start_date=2025-11-06"), "{}", r.url);
}

#[test]
fn open_meteo_daily_becomes_short_korean_weather() {
    let json = r#"{"daily":{"time":["2026-11-02","2026-11-03"],"weather_code":[61,0],
        "temperature_2m_max":[9.6,14.4],"temperature_2m_min":[3.1,4.0],"precipitation_sum":[5.2,0.0]}}"#;
    assert_eq!(parse_open_meteo_weather(json.into(), "2026-11-03".into()).as_deref(), Some("맑음 · 14°C"));
    assert_eq!(parse_open_meteo_weather(json.into(), "2026-11-02".into()).as_deref(), Some("비 · 10°C"));
    assert_eq!(parse_open_meteo_weather(json.into(), "2026-11-04".into()), None);
    assert_eq!(parse_open_meteo_weather("<html>".into(), "2026-11-03".into()), None);
    assert_eq!(wmo_text(2), "구름 조금");
    assert_eq!(wmo_text(3), "흐림");
    assert_eq!(wmo_text(73), "눈");
    assert_eq!(wmo_text(81), "소나기");
    assert_eq!(clean_weather(Some("   ".into())), None);
    assert_eq!(clean_weather(Some("가".repeat(60))).unwrap().chars().count(), 40);
}

// ---- 내 기록에서 올리기 ----

/// 시연 1년치(집 성수동·회사 을지로) 기록 + 커뮤니티.
fn with_memory(tag: &str) -> (TempDb, std::sync::Arc<EodiStore>) {
    let (db, store) = seeded(tag);
    assert!(store.seed_demo_data(NOW).unwrap() > 0);
    (db, store)
}

#[test]
fn share_candidates_on_a_day_are_that_days_outings() {
    let (_db, store) = with_memory("share-day");
    let all = store.share_candidates(0).unwrap();
    let trip = all.iter().find(|c| c.stops.iter().any(|s| s.name == "초당할머니순두부")).expect("강릉 여행");
    let on = store.share_candidates_on(trip.day.clone()).unwrap();
    assert!(on.iter().any(|c| c.episode_id == trip.episode_id), "그날 기록에서 바로 올릴 수 있다");
    assert!(on.iter().all(|c| c.day == trip.day && !c.stops.is_empty()));
    assert!(store.share_candidates_on("2001-01-01".into()).unwrap().is_empty(), "기록 없는 날");
    assert!(matches!(store.share_candidates_on("어제".into()), Err(CoreError::InvalidInput(_))));
}

#[test]
fn share_from_my_record_hides_home_and_office_and_keeps_hearts_on_reshare() {
    let (_db, store) = with_memory("share");
    let cands = store.share_candidates(0).unwrap();
    assert!(!cands.is_empty());
    assert!(cands.windows(2).all(|w| w[0].day >= w[1].day), "최신순");
    assert!(cands.iter().all(|c| matches!(c.kind, EpisodeKind::Outing | EpisodeKind::Trip) && !c.stops.is_empty()));
    let home = Point { lat: 37.5530, lon: 127.0335 };
    let office = Point { lat: 37.5672, lon: 126.9844 };
    for c in &cands {
        for s in &c.stops {
            assert!(!s.name.is_empty());
            assert!(haversine_m(home, stop_point(s)) > PRIVACY_RADIUS_M && haversine_m(office, stop_point(s)) > PRIVACY_RADIUS_M);
        }
    }
    // 1년 전 강릉 가족 여행 (휴게소 → 경포 → 초당 → 안목)
    let trip = cands.iter().find(|c| c.stops.iter().any(|s| s.name == "초당할머니순두부")).expect("강릉 여행");
    assert_eq!(trip.kind, EpisodeKind::Trip);
    assert!(!trip.title.is_empty());
    assert_eq!(trip.season, season_label(trip.day.clone()));
    assert!(trip.weather_url.starts_with("https://archive-api.open-meteo.com/"), "{}", trip.weather_url);
    let p = store.share_preview(trip.episode_id).unwrap();
    assert_eq!(&p, trip);

    // 휴게소를 빼고 올린다
    let rest = trip.stops.iter().find(|s| s.name == "횡성휴게소").unwrap().arrive_ms;
    let post = store
        .share_course_with(trip.episode_id, " 강릉 가족 여행 ".into(), "아이들이 좋아한 코스".into(), vec![rest], Some("맑음 · 25°C".into()), NOW)
        .unwrap();
    assert!(post.mine && post.author == ME && post.id == format!("me-{}", trip.episode_id));
    assert_eq!(post.title, "강릉 가족 여행");
    assert_eq!(post.stops.len(), trip.stops.len() - 1);
    assert!(post.stops.iter().all(|s| s.name != "횡성휴게소"));
    assert_eq!(post.visited_day, trip.day);
    assert_eq!(post.weather.as_deref(), Some("맑음 · 25°C"));
    assert!(post.distance_m > 0.0 && post.distance_m < 50_000.0, "집에서 온 거리는 안 들어간다: {}", post.distance_m);
    assert!(store.share_preview(trip.episode_id).unwrap().shared);

    // 하트 받은 뒤 다시 올리면 하트·올린 시각·날씨(새 값이 없으면)는 그대로
    store.toggle_course_heart(post.id.clone()).unwrap();
    let again = store.share_course(trip.episode_id, "강릉 순두부 여행".into(), "".into(), NOW + 999).unwrap();
    assert_eq!(again.title, "강릉 순두부 여행");
    assert_eq!((again.hearts, again.created_ms), (1, NOW));
    assert_eq!(again.stops.len(), trip.stops.len(), "이번엔 휴게소 포함");
    assert_eq!(again.weather.as_deref(), Some("맑음 · 25°C"));
    assert_eq!(store.community_courses(CommunitySort::Recent, None, 0).unwrap().iter().filter(|c| c.mine).count(), 1);

    // 막는 경우
    assert!(store.share_course(trip.episode_id, "  ".into(), "".into(), NOW).is_err(), "빈 제목");
    assert!(store.share_course(1, "x".into(), "".into(), NOW).is_err(), "없는 에피소드");
    let all: Vec<i64> = trip.stops.iter().map(|s| s.arrive_ms).collect();
    assert!(store.share_course_with(trip.episode_id, "x".into(), "".into(), all, None, NOW).is_err(), "다 뺌");
    let commute = store
        .search_memories(MemoryQuery { terms: vec![], from_ms: None, to_ms: None, kinds: vec![EpisodeKind::CommuteToWork], limit: 1 })
        .unwrap()[0]
        .episode
        .facts
        .id;
    assert!(store.share_course(commute, "출근".into(), "".into(), NOW).is_err(), "출근길은 못 올린다");
    // 출근길 미리보기: 들른 곳(회사)은 가려진다
    let pv = store.share_preview(commute).unwrap();
    assert!(pv.stops.is_empty());
    assert!(store.share_preview(1).is_err());
}

#[test]
fn shareable_stops_counts_hidden_and_unnamed() {
    let home = Point { lat: 37.5530, lon: 127.0335 };
    let stops = vec![place("동네 카페", 37.5560, 127.0340), place("", 37.9, 128.0), place("경포해변", 37.8055, 128.9083)];
    let kept = shareable_stops(&stops, &[home]);
    assert_eq!(kept.iter().map(|s| s.name.as_str()).collect::<Vec<_>>(), ["경포해변"]);
    assert_eq!(shareable_stops(&stops, &[]).len(), 2, "집·회사를 모르면 이름 있는 곳은 다 남는다");
    assert!((course_distance_m(&[place("a", 37.0, 127.0), place("b", 37.01, 127.0)]) - 1_111.95 * ROAD_FACTOR).abs() < 2.0);
}

#[test]
fn review_candidates_are_recent_named_stops_without_duplicates() {
    let (_db, store) = with_memory("cands");
    let c = store.review_candidates(20).unwrap();
    assert_eq!(c.len(), 20);
    assert!(c.windows(2).all(|w| w[0].arrive_ms >= w[1].arrive_ms), "최신순");
    let mut names: Vec<&str> = c.iter().map(|s| s.name.as_str()).collect();
    names.sort();
    names.dedup();
    assert_eq!(names.len(), 20, "이름 중복 없음");
    assert!(c.iter().all(|s| !s.name.is_empty()));
}

#[test]
fn last_known_position_is_latest_fix() {
    let db = TempDb::new("pos");
    let store = db.open();
    assert_eq!(store.last_known_position().unwrap(), None);
    store
        .ingest(vec![
            crate::store::GeoFix { lat: 37.1, lon: 127.1, t_ms: NOW, accuracy_m: 5.0 },
            crate::store::GeoFix { lat: 37.2, lon: 127.2, t_ms: NOW - 1000, accuracy_m: 5.0 },
        ])
        .unwrap();
    assert_eq!(store.last_known_position().unwrap(), Some(gp(37.1, 127.1)));
}

// ---- DB 올리기 ----

#[test]
fn v4_database_gets_community_tables() {
    let db = TempDb::new("v4");
    {
        let store = db.open();
        let c = store.lock().unwrap();
        c.execute_batch(DROP_V5).unwrap();
        c.pragma_update(None, "user_version", 4).unwrap();
    }
    let store = db.open();
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, SCHEMA_VERSION);
    assert!(store.seed_community_demo(NOW).unwrap() > 0);
    assert!(!store.board_posts(CommunitySort::Popular, 3).unwrap().is_empty());
}

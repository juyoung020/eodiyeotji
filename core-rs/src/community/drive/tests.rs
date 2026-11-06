//! 드라이브 코스(경치 길): 시연 데이터, 목록 순위(인기·최신·근처 = 길 위 가장 가까운 점), 근처 추천, 하트, 지도 데이터,
//! 카메라·비행, 안내 구간(경유지 없는 두 구간), 내 기록에서 올리기(집·회사 근처 지우기), DB 올리기.

use super::super::drive_demo::{shapes, ROUTES};
use super::*;
use crate::store::SCHEMA_VERSION;

/// 2026-11-06 12:00 KST
const NOW: i64 = 1_793_934_000_000;
const KST: i32 = 540;

struct TempDb(std::path::PathBuf);

impl TempDb {
    fn new(tag: &str) -> Self {
        let p = std::env::temp_dir().join(format!("eodi-drive-{tag}-{}.db", std::process::id()));
        let db = TempDb(p);
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

fn seeded(tag: &str) -> (TempDb, std::sync::Arc<EodiStore>) {
    let db = TempDb::new(tag);
    let store = db.open();
    assert!(store.seed_community_demo(NOW).unwrap() > 0);
    (db, store)
}

fn gp(lat: f64, lon: f64) -> GeoPoint {
    GeoPoint { lat, lon }
}

fn ids(v: &[DriveRouteRecommendation]) -> Vec<&str> {
    v.iter().map(|r| r.route.id.as_str()).collect()
}

/// 서울 목동
const MOKDONG: GeoPoint = GeoPoint { lat: 37.5265, lon: 126.8643 };

// ---- 시연 데이터 ----

#[test]
fn demo_routes_are_real_roads_with_sights_beside_them() {
    let shapes = shapes();
    assert_eq!(shapes.len(), 10);
    assert_eq!(ROUTES.len(), shapes.len());
    for s in &shapes {
        let meta = ROUTES.iter().find(|m| m.0 == s.id).unwrap_or_else(|| panic!("{} 글이 없다", s.id));
        assert!(s.line.len() >= 10, "{}", s.id);
        assert!(s.line.iter().all(|p| (33.0..38.7).contains(&p.lat) && (124.5..131.0).contains(&p.lon)), "{}", s.id);
        // OSRM 거리와 선 길이가 크게 다르지 않다 (선은 이미 줄인 것이라 조금 짧다)
        let len = line_length_m(&s.line);
        assert!(len > s.distance_m * 0.85 && len < s.distance_m * 1.05, "{}: {len:.0} vs {}", s.id, s.distance_m);
        // 풍경은 길에서 3km 안 (차창으로 보이는 것)
        assert!(!meta.4.is_empty(), "{}", s.id);
        for &(name, lat, lon) in meta.4 {
            let d = nearest_distance_m(Point { lat, lon }, &s.line);
            assert!(d < 3_000.0, "{} {name}: {d:.0}m", s.id);
        }
        // 소개는 2~4문장
        let sentences = meta.2.matches("요.").count();
        assert!((2..=4).contains(&sentences), "{}: {sentences}문장", s.id);
    }
}

#[test]
fn seeding_puts_ten_routes_once_with_visit_details() {
    let (_db, store) = seeded("seed");
    let all = store.drive_routes(DriveRouteSort::Recent, None, 0).unwrap();
    assert_eq!(all.len(), 10);
    assert!(all.iter().all(|r| !r.route.mine && r.distance_km == -1.0 && r.route.distance_m > 5_000.0 && r.route.duration_ms > 0));
    assert!(all.iter().all(|r| crate::store::parse_day(&r.route.visited_day).is_some() && !r.route.season.is_empty() && r.route.weather.is_some()));
    assert!(all.iter().all(|r| r.route.created_ms < NOW));
    assert_eq!(all.iter().filter(|r| r.route.hearted_by_me).count(), 2);
    let gb = all.iter().find(|r| r.route.id == "d-gangbyeon-night").unwrap();
    assert_eq!((gb.route.best_time, gb.route.theme), (BestTime::Night, RouteTheme::Night));
    assert_eq!((gb.route.start_name.as_str(), gb.route.end_name.as_str()), ("월드컵대교 북단", "광진교 북단"));
    assert_eq!(gb.route.highlights.len(), 4);
    let bloom = all.iter().find(|r| r.route.id == "d-bukhangang-391").unwrap();
    assert!(bloom.route.season.starts_with("봄"), "벚꽃 길은 봄에 다녀왔다: {}", bloom.route.season);
    // 두 번째는 아무것도 안 넣는다
    assert_eq!(store.seed_community_demo(NOW + 1).unwrap(), 0);
    assert_eq!(store.drive_routes(DriveRouteSort::Recent, None, 0).unwrap().len(), 10);
}

// ---- 목록 · 추천 ----

#[test]
fn list_sorts_by_hearts_recent_or_nearest_point_on_the_road() {
    let (_db, store) = seeded("sort");
    let popular = store.drive_routes(DriveRouteSort::Popular, None, 0).unwrap();
    assert_eq!(popular[0].route.id, "d-route7", "하트 533");
    assert!(popular.windows(2).all(|w| w[0].route.hearts >= w[1].route.hearts));
    let recent = store.drive_routes(DriveRouteSort::Recent, None, 0).unwrap();
    assert!(recent.windows(2).all(|w| w[0].route.created_ms >= w[1].route.created_ms));
    // 근처: 목동에서 가까운 길 순 (거리 = 길 위 가장 가까운 점)
    let near = store.drive_routes(DriveRouteSort::Nearest, Some(MOKDONG), 0).unwrap();
    assert!(near.windows(2).all(|w| w[0].distance_km <= w[1].distance_km));
    assert!(near[0].distance_km < 3.5, "{:?}", near.iter().map(|r| (&r.route.id, r.distance_km)).collect::<Vec<_>>());
    assert!(ids(&near[..3]).iter().all(|id| ["d-olympic-sunset", "d-gangbyeon-night", "d-jayuro-imjin"].contains(id)), "{:?}", ids(&near));
    // 반포는 올림픽대로 시작·끝(잠실·가양)에서 멀지만 길 한가운데라 가깝다
    let banpo = store.drive_routes(DriveRouteSort::Nearest, Some(gp(37.5100, 126.9960)), 2).unwrap();
    let olympic = banpo.iter().find(|r| r.route.id == "d-olympic-sunset").expect("반포 근처");
    assert!(olympic.distance_km < 1.0, "{}", olympic.distance_km);
    // 위치를 모르면 근처순은 인기순
    assert_eq!(ids(&store.drive_routes(DriveRouteSort::Nearest, None, 3).unwrap()), ids(&popular[..3]));
    assert!(store.drive_routes(DriveRouteSort::Popular, Some(gp(f64::NAN, 1.0)), 3).is_err());
    assert_eq!(store.drive_routes(DriveRouteSort::Popular, None, 4).unwrap().len(), 4);
}

#[test]
fn recommend_prefers_nearby_popular_roads_and_drops_far_ones() {
    let (_db, store) = seeded("reco");
    // 강릉 경포 근처: 7번 국도·헌화로·대관령 옛길만, 서울 길은 빠진다
    let recs = store.recommend_drive_routes(Some(gp(37.7900, 128.9000)), 30.0, 5).unwrap();
    let got = ids(&recs);
    assert!(got.contains(&"d-route7") && got.contains(&"d-heonhwa") && got.contains(&"d-daegwallyeong"), "{got:?}");
    assert!(recs.iter().all(|r| r.distance_km <= 30.0 && r.score > 0.0));
    assert!(recs.windows(2).all(|w| w[0].score >= w[1].score));
    assert_eq!(recs[0].route.id, "d-route7", "경포에서 바로 시작하는 인기 길");
    // 바다 한가운데·NaN
    assert!(store.recommend_drive_routes(Some(gp(36.0, 131.5)), 30.0, 3).unwrap().is_empty());
    assert!(store.recommend_drive_routes(Some(gp(f64::NAN, 1.0)), 30.0, 3).is_err());
    // 위치 없이 = 전국 하트순, 거리 모름. k 0 = 3개, 반경 NaN = 30km
    let all = store.recommend_drive_routes(None, 0.0, 0).unwrap();
    assert_eq!(ids(&all), vec!["d-route7", "d-gangbyeon-night", "d-heonhwa"]);
    assert!(all.iter().all(|r| r.distance_km == -1.0));
    assert!(store.recommend_drive_routes(Some(MOKDONG), f64::NAN, 0).unwrap().len() <= 3);
}

#[test]
fn score_trades_popularity_against_distance() {
    assert!(route_score(400, 2.0, 30.0) > route_score(400, 20.0, 30.0));
    assert!(route_score(400, 5.0, 30.0) > route_score(40, 5.0, 30.0));
    // 하트 10배여도 10배 점수는 아니다 (거리가 묻히지 않게)
    assert!(route_score(400, 5.0, 30.0) < 2.0 * route_score(40, 5.0, 30.0));
    // 반경의 1/3마다 반
    assert!((route_score(10, 10.0, 30.0) / route_score(10, 0.0, 30.0) - 0.5).abs() < 1e-9);
}

#[test]
fn hearts_toggle_and_unknown_ids_fail() {
    let (_db, store) = seeded("heart");
    let before = store.drive_route("d-sihwa".into()).unwrap();
    assert_eq!(store.toggle_drive_route_heart("d-sihwa".into()).unwrap(), before.hearts + 1);
    assert!(store.drive_route("d-sihwa".into()).unwrap().hearted_by_me);
    assert_eq!(store.toggle_drive_route_heart("d-sihwa".into()).unwrap(), before.hearts);
    assert!(store.toggle_drive_route_heart("없음".into()).is_err());
    assert!(store.drive_route("없음".into()).is_err());
    assert!(store.drive_route_map("없음".into()).is_err());
    assert!(store.drive_route_legs("없음".into()).is_err());
}

// ---- 지도 · 카메라 · 비행 · 안내 ----

#[test]
fn map_is_line_start_end_and_sight_labels() {
    let (_db, store) = seeded("map");
    let m = store.drive_route_map("d-gangbyeon-night".into()).unwrap();
    assert_eq!(m.route_id, "d-gangbyeon-night");
    let v: Value = serde_json::from_str(&m.geojson).unwrap();
    let f = v["features"].as_array().unwrap();
    assert_eq!(f[0]["geometry"]["type"], "LineString");
    assert_eq!(f[0]["properties"]["kind"], "route");
    let coords = f[0]["geometry"]["coordinates"].as_array().unwrap();
    assert!(coords.len() >= 10);
    // GeoJSON은 [경도, 위도]
    assert!((coords[0][0].as_f64().unwrap() - 126.89343).abs() < 1e-4 && (coords[0][1].as_f64().unwrap() - 37.55584).abs() < 1e-4);
    let kinds: Vec<&str> = f.iter().map(|x| x["properties"]["kind"].as_str().unwrap()).collect();
    assert_eq!(kinds, vec!["route", "start", "end", "highlight", "highlight", "highlight", "highlight"]);
    assert_eq!(f[1]["properties"]["name"], "월드컵대교 북단");
    assert_eq!(f[5]["properties"]["name"], "반포대교 달빛무지개분수");
    let b = m.bounds;
    for c in coords {
        let (lon, lat) = (c[0].as_f64().unwrap(), c[1].as_f64().unwrap());
        assert!(lat >= b.min_lat && lat <= b.max_lat && lon >= b.min_lon && lon <= b.max_lon);
    }
    assert!(b.max_lat >= 37.5512, "남산서울타워 글자까지 범위에");
}

#[test]
fn camera_looks_along_the_road_and_fits_it() {
    let (_db, store) = seeded("camera");
    let r = store.drive_route("d-gangbyeon-night".into()).unwrap();
    let cam = store.drive_route_camera(r.id.clone(), 900.0, 500.0, 40.0, PREVIEW_PITCH).unwrap();
    let want = bearing_deg(Point::from(r.start), Point::from(r.end));
    assert!((cam.bearing - want).abs() < 1e-6, "{} vs {want}", cam.bearing);
    assert!((80.0..110.0).contains(&cam.bearing), "강변북로는 서→동: {}", cam.bearing);
    assert_eq!(cam.pitch, PREVIEW_PITCH);
    assert!((10.0..13.5).contains(&cam.zoom), "22km 길이 900px에: {}", cam.zoom);
    // 가운데는 길 범위 안
    let m = store.drive_route_map(r.id.clone()).unwrap();
    assert!(cam.lat > m.bounds.min_lat - 0.01 && cam.lat < m.bounds.max_lat + 0.01 && cam.lon > m.bounds.min_lon && cam.lon < m.bounds.max_lon);
    // 화면이 크면 더 가깝게, 짧은 길은 더 가깝게
    let big = store.drive_route_camera(r.id.clone(), 1800.0, 1000.0, 80.0, PREVIEW_PITCH).unwrap();
    assert!((big.zoom - cam.zoom - 1.0).abs() < 0.05, "{} {}", big.zoom, cam.zoom);
    let short = store.drive_route_camera("d-heonhwa".into(), 900.0, 500.0, 40.0, PREVIEW_PITCH).unwrap();
    assert!(short.zoom > cam.zoom + 1.0, "{} {}", short.zoom, cam.zoom);
    // 기울기가 이상하면 기본값, 범위 밖은 자른다
    assert_eq!(store.drive_route_camera(r.id.clone(), 900.0, 500.0, 40.0, f64::NAN).unwrap().pitch, PREVIEW_PITCH);
    assert_eq!(store.drive_route_camera(r.id, 900.0, 500.0, 40.0, 85.0).unwrap().pitch, 60.0);
}

#[test]
fn camera_fit_keeps_every_demo_road_inside_the_frame_and_uses_it() {
    // 목록 카드 스냅숏 크기 (640×305, 여백 36)
    let (w, h, pad) = (640.0, 305.0, 36.0);
    for s in shapes() {
        let cam = fit_camera(&s.line, w, h, pad, PREVIEW_PITCH);
        let o = Point { lat: cam.lat, lon: cam.lon };
        let scale = WORLD_PX * 2f64.powf(cam.zoom) / (EARTH_CIRCUMFERENCE_M * cam.lat.to_radians().cos());
        let d0 = 0.5 / (FOV / 2.0).tan() * h;
        let (sb, cb) = cam.bearing.to_radians().sin_cos();
        let sc = cam.pitch.to_radians().sin_cos();
        let (mut ax, mut lo, mut hi) = (0.0f64, f64::MAX, f64::MIN);
        for &p in &s.line {
            let (x, y) = local_m(o, p);
            let (sx, sy) = screen_xy(x * cb - y * sb, x * sb + y * cb, scale, 0.0, d0, sc).expect("지평선 안");
            (ax, lo, hi) = (ax.max(sx.abs()), lo.min(sy), hi.max(sy));
        }
        let (wh, hh) = (w / 2.0 - pad, h / 2.0 - pad);
        assert!(ax <= wh + 1.0 && hi <= hh + 1.0 && -lo <= hh + 1.0, "{}: 밖으로 나감 {ax:.0} {lo:.0}..{hi:.0}", s.id);
        assert!(ax > wh * 0.95 || hi > hh * 0.95 || -lo > hh * 0.95, "{}: 화면을 다 쓰지 않음 {ax:.0} {lo:.0}..{hi:.0}", s.id);
        assert!((hi + lo).abs() < 2.0 || ax > wh * 0.95, "{}: 위아래가 한쪽으로 쏠림 {lo:.0}..{hi:.0}", s.id);
    }
}

#[test]
fn flight_runs_from_start_to_end_with_smooth_heading() {
    let (_db, store) = seeded("flight");
    let r = store.drive_route("d-jayuro-imjin".into()).unwrap();
    let f = store.drive_route_flight(r.id.clone(), 300).unwrap();
    assert_eq!(f.len(), 300);
    let at = |x: &FlightFrame| Point { lat: x.lat, lon: x.lon };
    assert!(haversine_m(at(&f[0]), Point::from(r.start)) < 1.0);
    assert!(haversine_m(at(&f[299]), Point::from(r.end)) < 1.0);
    // 길을 따라 같은 간격 (모퉁이에서는 직선으로 재면 조금 짧다)
    let steps: Vec<f64> = f.windows(2).map(|w| haversine_m(at(&w[0]), at(&w[1]))).collect();
    let max = steps.iter().copied().fold(0.0, f64::max);
    let even = steps.iter().filter(|&&s| s > max * 0.95).count();
    assert!(even * 10 >= steps.len() * 9, "{even}/{} max {max}", steps.len());
    // 방향은 끊김 없이 (풀어 둔 값이라 한 장면에 크게 돌지 않는다)
    assert!(f.windows(2).all(|w| (w[1].bearing - w[0].bearing).abs() < 15.0));
    // 자유로는 대체로 북(서)쪽으로 (중간 나들목에서 잠깐 돌아도 평균은 북쪽)
    let (s, c) = f.iter().fold((0.0, 0.0), |(s, c), x| (s + x.bearing.to_radians().sin(), c + x.bearing.to_radians().cos()));
    let mean = s.atan2(c).to_degrees().rem_euclid(360.0);
    assert!(!(90.0..270.0).contains(&mean), "{mean}");
    assert!(f.iter().all(|x| (14.0..=15.6).contains(&x.zoom)));
    assert_eq!(store.drive_route_flight(r.id.clone(), 0).unwrap().len(), FLIGHT_FRAMES as usize);
    assert_eq!(store.drive_route_flight(r.id, 1).unwrap().len(), 2);
}

#[test]
fn flight_heading_unwraps_across_north() {
    // 북동 → 북서로 꺾이는 길: 0°를 지나며 359 → 1처럼 튀지 않는다
    let mut line: Vec<Point> = (0..50).map(|i| Point { lat: 37.0 + i as f64 * 0.001, lon: 127.0 + i as f64 * 0.0005 }).collect();
    let last = *line.last().unwrap();
    line.extend((1..50).map(|i| Point { lat: last.lat + i as f64 * 0.001, lon: last.lon - i as f64 * 0.0005 }));
    let f = flight(&line, 200);
    assert!(f.windows(2).all(|w| (w[1].bearing - w[0].bearing).abs() < 10.0));
    assert!(f[0].bearing.rem_euclid(360.0) < 40.0 && f[199].bearing.rem_euclid(360.0) > 320.0);
}

#[test]
fn legs_go_to_start_then_end_without_waypoints() {
    let (_db, store) = seeded("legs");
    let legs = store.drive_route_legs("d-incheon-bridge".into()).unwrap();
    assert_eq!(legs.len(), 2);
    assert!(legs.iter().all(|l| l.waypoints.is_empty()));
    assert_eq!((legs[0].destination.name.as_str(), legs[1].destination.name.as_str()), ("송도", "영종도"));
    let r = store.drive_route("d-incheon-bridge".into()).unwrap();
    assert_eq!((legs[0].destination.lat, legs[1].destination.lon), (r.start.lat, r.end.lon));
}

// ---- 내 기록에서 올리기 ----

#[test]
fn share_a_trip_trims_home_and_office_and_keeps_hearts_on_reshare() {
    let (_db, store) = seeded("share");
    assert!(store.seed_demo_data(NOW).unwrap() > 0);
    let home = Point { lat: 37.5530, lon: 127.0335 };
    let office = Point { lat: 37.5672, lon: 126.9844 };
    let cands = store.drive_share_candidates(0).unwrap();
    assert!(!cands.is_empty());
    assert!(cands.windows(2).all(|w| w[0].trip_start_ms >= w[1].trip_start_ms), "최신순");
    assert!(cands.iter().all(|c| c.distance_m >= MIN_SHARE_M && !c.shared && c.weather_url.starts_with("https://")));
    // 출퇴근처럼 집·회사에서 시작하는 주행은 앞·뒤가 지워진다
    let trimmed = cands.iter().find(|c| c.trimmed).expect("집·회사 근처를 지운 주행");
    let c = trimmed.clone();
    let post = store.share_drive_route(c.trip_start_ms, " 퇴근길 강변 ".into(), "불빛이 예뻐요".into(), BestTime::Night, Some("맑음 · 9°C".into()), NOW).unwrap();
    assert!(post.mine && post.author == ME && post.id == format!("me-d{}", c.trip_start_ms));
    assert_eq!((post.title.as_str(), post.best_time, post.theme), ("퇴근길 강변", BestTime::Night, RouteTheme::Night));
    assert_eq!(post.visited_day, c.day);
    assert!((post.distance_m - c.distance_m).abs() < 1.0);
    let line = route_by_id(&store.lock().unwrap(), &post.id).unwrap().line;
    assert!(line.iter().all(|&p| haversine_m(p, home) > ROUTE_TRIM_M - 50.0 && haversine_m(p, office) > ROUTE_TRIM_M - 50.0), "집·회사 근처 점이 없다");
    if c.from_name != "출발 지점" {
        assert!(haversine_m(Point::from(post.start), home) > ROUTE_TRIM_M);
    }
    assert!(store.drive_share_candidates(0).unwrap().iter().any(|x| x.trip_start_ms == c.trip_start_ms && x.shared));
    // 하트 받은 뒤 다시 올리면 하트·올린 시각·날씨는 그대로
    store.toggle_drive_route_heart(post.id.clone()).unwrap();
    let again = store.share_drive_route(c.trip_start_ms, "강변 야경".into(), "".into(), BestTime::Sunset, None, NOW + 999).unwrap();
    assert_eq!((again.title.as_str(), again.hearts, again.created_ms, again.best_time), ("강변 야경", 1, NOW, BestTime::Sunset));
    assert_eq!(again.weather.as_deref(), Some("맑음 · 9°C"));
    // 내가 올린 길은 목록엔 있고 추천에는 없다
    assert!(store.drive_routes(DriveRouteSort::Recent, None, 0).unwrap()[0].route.mine);
    assert!(store.recommend_drive_routes(None, 0.0, 20).unwrap().iter().all(|r| !r.route.mine));
    // 막는 경우
    assert!(store.share_drive_route(c.trip_start_ms, "  ".into(), "".into(), BestTime::Day, None, NOW).is_err(), "빈 제목");
    assert!(store.share_drive_route(12345, "x".into(), "".into(), BestTime::Day, None, NOW).is_err(), "없는 주행");
}

#[test]
fn shareable_piece_keeps_the_longest_part_outside_home() {
    let home = Point { lat: 37.0, lon: 127.0 };
    // 집에서 북쪽으로 10km
    let path: Vec<GeoPoint> = (0..=100).map(|i| GeoPoint { lat: 37.0 + i as f64 * 0.0009, lon: 127.0 }).collect();
    let t = TripSummary {
        start_ms: 0,
        end_ms: 600_000,
        distance_m: 10_000.0,
        start: path[0],
        end: path[100],
        path,
        start_place: None,
        end_place: None,
    };
    let p = shareable_piece(&t, &[home]).unwrap();
    assert!(p.cut_start && !p.cut_end);
    assert!(p.line.iter().all(|&q| haversine_m(q, home) > ROUTE_TRIM_M));
    assert!(p.length_m > 8_000.0 && p.length_m < 9_000.0, "{}", p.length_m);
    assert!(p.duration_ms > 0 && p.duration_ms < 600_000);
    assert_eq!(end_names(&t, &p), ("출발 지점".to_string(), "도착 지점".to_string()));
    assert_eq!(default_title("출발 지점", "도착 지점", "2026-11-06"), "11월 6일 드라이브");
    assert_eq!(default_title("출발 지점", "경포해변", "2026-11-06"), "경포해변 가는 길");
    assert_eq!(default_title("양수리", "청평", "2026-04-08"), "양수리 → 청평 드라이브");
    // 집 근처만 맴돈 주행은 못 올린다
    let short = TripSummary { path: t.path[..10].to_vec(), ..t.clone() };
    assert!(shareable_piece(&short, &[home]).is_none());
}

#[test]
fn line_and_highlights_round_trip() {
    let line = vec![Point { lat: 37.123456, lon: 127.654321 }, Point { lat: 37.2, lon: 127.7 }];
    let back = decode_line(&encode_line(&line));
    assert!((back[0].lat - 37.12346).abs() < 1e-9 && back.len() == 2);
    assert!(decode_line("깨짐").is_empty());
    let h = vec![RouteHighlight { name: "남산 \"타워\"".into(), lat: 37.5, lon: 127.0 }];
    assert_eq!(decode_highlights(&encode_highlights(&h)), h);
}

// ---- DB 올리기 ----

#[test]
fn v9_database_gets_drive_routes_and_old_demo_device_gets_them_on_next_seed() {
    let db = TempDb::new("v9");
    {
        let store = db.open();
        assert!(store.seed_community_demo(NOW).unwrap() > 0);
        let c = store.lock().unwrap();
        // 버전 9 시절처럼: 표 없음, 커뮤니티 시연은 넣었고 드라이브 길 표시는 없음
        c.execute_batch(DROP_V10).unwrap();
        c.execute("DELETE FROM meta WHERE key = 'drive_routes_seeded'", []).unwrap();
        c.pragma_update(None, "user_version", 9).unwrap();
    }
    let store = db.open();
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, SCHEMA_VERSION);
    assert!(store.drive_routes(DriveRouteSort::Popular, None, 0).unwrap().is_empty());
    // 시연을 다시 부르면 드라이브 길만 넣는다 (여행 코스 후기·리뷰는 그대로)
    let courses = store.community_courses(crate::community::CommunitySort::Recent, None, 0).unwrap().len();
    assert_eq!(store.seed_community_demo(NOW + 1).unwrap(), 10);
    assert_eq!(store.drive_routes(DriveRouteSort::Popular, None, 0).unwrap().len(), 10);
    assert_eq!(store.community_courses(crate::community::CommunitySort::Recent, None, 0).unwrap().len(), courses);
    assert_eq!(store.seed_community_demo(NOW + 2).unwrap(), 0);
}

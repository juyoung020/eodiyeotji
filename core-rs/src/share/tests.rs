//! 공유 카드: 길 그림 맞추기 · 집·회사 근처 거르기 · 보낼 글 · 날짜 글 · 시연 데이터로 하루/에피소드/코스 카드.

use super::*;
use crate::store::days_from_civil;

const M: f64 = 1.0 / 111_195.0; // 위도 1m

fn p(lat: f64, lon: f64) -> Point {
    Point { lat, lon }
}

fn stop(name: &str, lat: f64, lon: f64, arrive_ms: i64) -> StopFact {
    StopFact {
        name: name.into(),
        category: "카페".into(),
        address: "".into(),
        arrive_ms,
        leave_ms: arrive_ms + 1,
        lat,
        lon,
        first_visit: false,
    }
}

fn share_stop(name: &str) -> ShareStop {
    ShareStop { order: 1, name: name.into(), category: "".into(), address: "".into(), lat: 0.0, lon: 0.0, at: SketchPoint { x: 0.5, y: 0.5 } }
}

/// (lat0, lon0)에서 북쪽으로 100m 간격 n점.
fn north(lat0: f64, lon0: f64, n: usize) -> Vec<Point> {
    (0..n).map(|i| p(lat0 + i as f64 * 100.0 * M, lon0)).collect()
}

// ---- 길 그림 ----

#[test]
fn frame_keeps_every_point_inside_the_margins_and_puts_north_up() {
    let pts = [p(37.0, 127.0), p(37.1, 127.0), p(37.05, 127.2)];
    for aspect in [0.5, 1.0, 2.5] {
        let f = Frame::fit(pts.iter().copied(), aspect);
        let margin = (SKETCH_MARGIN * aspect.min(1.0)) as f32;
        for q in pts {
            let s = f.place(q);
            assert!(s.x >= margin / aspect as f32 - 1e-4 && s.x <= 1.0 - margin / aspect as f32 + 1e-4, "{aspect} {s:?}");
            assert!(s.y >= margin - 1e-4 && s.y <= 1.0 - margin + 1e-4, "{aspect} {s:?}");
        }
        // 북쪽 점이 위(y 작음), 동쪽 점이 오른쪽
        assert!(f.place(pts[1]).y < f.place(pts[0]).y);
        assert!(f.place(pts[2]).x > f.place(pts[0]).x);
    }
}

#[test]
fn frame_does_not_stretch_the_shape() {
    // 동서 2km × 남북 1km 사각형을 가로 3 : 세로 1 칸에 넣어도 가로/세로 길이 비는 2:1 그대로
    let k = 37.0_f64.to_radians().cos();
    let pts = [p(37.0, 127.0), p(37.0, 127.0 + 2000.0 * M / k), p(37.0 + 1000.0 * M, 127.0)];
    let aspect = 3.0;
    let f = Frame::fit(pts.iter().copied(), aspect);
    let (a, b, c) = (f.place(pts[0]), f.place(pts[1]), f.place(pts[2]));
    let w_px = (b.x - a.x) as f64 * aspect; // 칸 높이 단위
    let h_px = (a.y - c.y) as f64;
    assert!((w_px / h_px - 2.0).abs() < 0.01, "{w_px} / {h_px}");
}

#[test]
fn frame_of_one_point_or_nothing_is_the_center() {
    let f = Frame::fit([p(37.5, 127.0)].into_iter(), 1.6);
    assert_eq!(f.place(p(37.5, 127.0)), SketchPoint { x: 0.5, y: 0.5 });
    let f = Frame::fit(std::iter::empty(), 1.6);
    let s = f.place(p(0.0, 0.0));
    assert!(s.x.is_finite() && s.y.is_finite());
}

// ---- 집·회사 근처 ----

#[test]
fn trim_cuts_the_route_where_it_passes_home_and_drops_tiny_pieces() {
    // 2km 북쪽으로 가는 길의 1km 지점이 집 → 집 앞뒤 500m 점이 사라지고 두 조각이 된다
    let path = north(37.0, 127.0, 21);
    let home = path[10];
    let parts = trim_near(&path, &[home], ROUTE_TRIM_M);
    assert_eq!(parts.len(), 2);
    for part in &parts {
        assert!(part.iter().all(|&q| haversine_m(q, home) > ROUTE_TRIM_M));
    }
    let outside = path.iter().filter(|&&q| haversine_m(q, home) > ROUTE_TRIM_M).count();
    assert_eq!(parts[0].len() + parts[1].len(), outside, "집 근처 점만 빠진다");
    assert!(outside >= 10);
    // 집에서 출발해 1점만 밖에 남으면 버린다
    assert!(trim_near(&north(37.0, 127.0, 6), &[p(37.0, 127.0)], ROUTE_TRIM_M).is_empty());
    // 기준점이 없으면 그대로
    assert_eq!(trim_near(&path, &[], ROUTE_TRIM_M), vec![path.clone()]);
}

fn raw(stops: Vec<StopFact>, paths: Vec<Vec<Point>>) -> RawCard {
    RawCard {
        kind: ShareKind::Day,
        title: "강릉 바다와 순두부 나들이".into(),
        day: "2025-11-06".into(),
        weather: Some("맑음 · 14°C".into()),
        region: "강원 강릉시".into(),
        stops,
        paths,
        straight: false,
        fixed_distance_m: None,
        duration_ms: 3_600_000,
    }
}

#[test]
fn card_hides_stops_near_home_renumbers_and_trims_the_route() {
    let home = p(37.0, 127.0);
    let path = north(37.0, 127.0, 51); // 집에서 북쪽으로 5km
    let stops = vec![
        stop("동네 편의점", 37.0 + 200.0 * M, 127.0, 1), // 집 200m → 뺀다
        stop("", 37.0 + 2000.0 * M, 127.0, 2),           // 이름 모름 → 카드에 없다 (가린 수에 안 셈)
        stop("바다 카페", 37.0 + 3000.0 * M, 127.0, 3),
        stop("순두부집", 37.0 + 5000.0 * M, 127.0, 4),
    ];
    let card = raw(stops, vec![path]).build(&[home], 1.6);
    assert_eq!(card.hidden_count, 1);
    assert_eq!(card.stops.iter().map(|s| (s.order, s.name.as_str())).collect::<Vec<_>>(), [(1, "바다 카페"), (2, "순두부집")]);
    // 남은 경로는 집 500m 밖부터: 4.5km 남짓
    assert!((card.distance_m - 4_500.0).abs() < 120.0, "{}", card.distance_m);
    assert_eq!(card.lines.len(), 1);
    // 그림의 선 시작점은 집이 아니라 집에서 500m 넘게 떨어진 곳 (그림 안에서 집 쪽 끝이 비어 있다)
    let first = card.lines[0].points[0];
    let near_home_y = card.lines[0].points.iter().map(|q| q.y).fold(f32::MIN, f32::max);
    assert_eq!(first.y, near_home_y, "남쪽(집 쪽) 끝이 선의 시작");
    // 곧은 길이라 두 점으로 줄어든다
    assert_eq!(card.lines[0].points.len(), 2);
    assert_eq!(card.weather.as_deref(), Some("맑음 · 14°C"));
    assert_eq!(card.date_text, "2025년 11월 6일 목요일");
    assert_eq!(card.season, "가을 · 단풍철");
    assert_eq!(card.share_text, "11월 6일 가을 강릉 바다와 순두부 나들이 · 바다 카페 → 순두부집 #어디였지");
}

#[test]
fn course_card_joins_stops_in_order_with_a_straight_line() {
    let mut r = raw(vec![stop("A", 37.0, 127.0, 1), stop("B", 37.01, 127.01, 2), stop("C", 37.02, 127.0, 3)], vec![]);
    r.kind = ShareKind::Course;
    r.straight = true;
    r.fixed_distance_m = Some(4321.0);
    let card = r.build(&[], 1.0);
    assert!(card.straight);
    assert_eq!(card.lines.len(), 1);
    assert_eq!(card.lines[0].points.len(), 3);
    assert_eq!(card.lines[0].points[1], card.stops[1].at);
    assert_eq!(card.distance_m, 4321.0);
    assert_eq!(card.hidden_count, 0);
}

// ---- 글 ----

#[test]
fn share_text_follows_the_example_and_skips_a_repeated_season() {
    let stops: Vec<ShareStop> = ["경포해변", "초당할머니순두부", "안목해변 카페거리", "주문진항"].iter().map(|n| share_stop(n)).collect();
    assert_eq!(
        share_text("2025-11-06", "가을 · 단풍철", "강릉 바다와 순두부 나들이", &stops, 0.0),
        "11월 6일 가을 강릉 바다와 순두부 나들이 · 경포해변 → 초당할머니순두부 → 안목해변 카페거리 → … #어디였지"
    );
    assert_eq!(share_text("2026-08-07", "여름 · 휴가철", "여름 부산 바다", &stops[..1], 0.0), "8월 7일 여름 부산 바다 · 경포해변 #어디였지");
    assert_eq!(share_text("2026-03-02", "봄", "출근길", &[], 12_400.0), "3월 2일 봄 출근길 · 12km 드라이브 #어디였지");
    assert_eq!(share_text("bad", "", "제목", &[], 0.0), "제목 #어디였지");
}

#[test]
fn date_text_has_the_korean_weekday() {
    assert_eq!(date_text("2026-11-06"), "2026년 11월 6일 금요일");
    assert_eq!(date_text("2026-11-08"), "2026년 11월 8일 일요일");
    assert_eq!(date_text("2024-02-29"), "2024년 2월 29일 목요일");
    assert_eq!(date_text("nope"), "nope");
    assert_eq!(days_from_civil(1970, 1, 1), 0);
}

// ---- 시연 데이터로 끝까지 ----

struct TempDb(std::path::PathBuf);

impl TempDb {
    fn new(tag: &str) -> Self {
        let db = TempDb(std::env::temp_dir().join(format!("eodi-share-{tag}-{}.db", std::process::id())));
        db.remove();
        db
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

#[test]
fn demo_gangneung_day_episode_and_course_cards() {
    let db = TempDb::new("demo");
    let store = EodiStore::open(db.0.to_string_lossy().into_owned(), 540).unwrap();
    // 2026-11-06 12:00 KST
    let now = days_from_civil(2026, 11, 6) * 86_400_000 + 3 * 3_600_000;
    store.seed_demo_data(now).unwrap();
    store.seed_community_demo(now).unwrap();
    let anchors = anchor_points(&store.lock().unwrap()).unwrap();
    assert!(!anchors.is_empty(), "시연 데이터에는 집·회사가 있다");

    let day = store.share_card_day("2025-11-06".into(), 1.6).unwrap();
    let names: Vec<&str> = day.stops.iter().map(|s| s.name.as_str()).collect();
    assert!(names.contains(&"경포해변") && names.contains(&"초당할머니순두부"), "{names:?}");
    assert!(day.stops.iter().all(|s| anchors.iter().all(|&a| haversine_m(a, p(s.lat, s.lon)) > STOP_PRIVACY_M)));
    assert!(!day.lines.is_empty() && day.distance_m > 100_000.0, "서울~강릉 왕복 {}", day.distance_m);
    for l in &day.lines {
        assert!(l.points.iter().all(|q| (0.0..=1.0).contains(&q.x) && (0.0..=1.0).contains(&q.y)));
    }
    let total: usize = day.lines.iter().map(|l| l.points.len()).sum();
    assert!(total < 800, "줄인 점 수 {total}");
    assert!(day.share_text.starts_with("11월 6일 ") && day.share_text.ends_with(" #어디였지"), "{}", day.share_text);
    assert!(day.share_text.contains("경포해변"), "{}", day.share_text);

    println!("{} / {} / {:?} / 숨김 {} / 선 {} / {}", day.title, day.date_text, day.weather, day.hidden_count, day.lines.len(), day.share_text);
    let ep = store.episodes_for_day("2025-11-06".into()).unwrap().into_iter().find(|e| e.facts.kind == EpisodeKind::Trip).unwrap();
    let card = store.share_card_episode(ep.facts.id, 1.6).unwrap();
    assert_eq!(card.kind, ShareKind::Episode);
    assert_eq!(card.day, "2025-11-06");
    assert!(card.stops.iter().any(|s| s.name == "경포해변"));
    assert!(card.distance_m > 50_000.0 && card.duration_ms > 0);

    let post = store.community_courses(crate::community::CommunitySort::Popular, None, 1).unwrap().remove(0);
    let course = store.share_card_course(post.id.clone(), 1.6).unwrap();
    assert!(course.straight);
    assert_eq!(course.stops.len(), post.stops.len());
    assert_eq!(course.title, post.title);
    assert!(store.share_card_course("없는 코스".into(), 1.6).is_err());
    assert!(store.share_card_day("2025-13-01".into(), 1.6).is_err());
}

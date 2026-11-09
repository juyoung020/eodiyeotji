//! 앱 안 길 안내 (폰 판): 카카오모빌리티 길찾기 결과를 읽고, 위치가 올 때마다 경로 위 진행·다음 안내·음성 시점을 계산한다.
//!
//! - 경로 받기 주소: [nav_directions_url] (출발 = 지금 위치, 경유지 최대 5곳, 도착). 요청 · 응답은 Kotlin이 HTTP로 한다.
//! - [NavSession::new]가 응답 JSON에서 경로 점 · 안내 지점(회전 · 경유지 · 도착)을 꺼낸다.
//! - [NavSession::update]: 위치 하나 → 경로에 붙인 위치 · 남은 거리/시간 · 다음 안내까지 거리 · 읽을 말 · 다시 찾기 필요 여부.
//!   읽을 말은 안내 지점마다 멀리(500m 전) · 가까이(150m 전) · 지금(30m 전) 한 번씩만 나온다.
//!   경로에서 [OFF_ROUTE_M] 넘게 벗어난 위치가 [OFF_ROUTE_FIXES]번 이어지면 `reroute`.

use std::sync::Mutex;

use serde_json::Value;

use crate::ffi::GeoPoint;
use crate::geo::{bearing_deg, haversine_m, Point};
use crate::store::CoreError;

/// 경로에서 이만큼(m) 넘게 떨어지면 벗어난 것으로 본다.
pub const OFF_ROUTE_M: f64 = 50.0;
/// 벗어난 위치가 이만큼 이어지면 경로를 다시 찾는다 (GPS가 한 번 튄 것은 넘긴다).
pub const OFF_ROUTE_FIXES: u32 = 3;
/// 먼저 찾아보는 범위: 지난 위치에서 경로를 따라 앞으로 이만큼(m). 1초 간격 위치면 시속 500km도 넉넉하다.
pub const MAX_JUMP_M: f64 = 150.0;
/// 도착으로 보는 거리(m).
pub const ARRIVE_M: f64 = 30.0;
const FAR_M: f64 = 500.0;
const NEAR_M: f64 = 150.0;
const NOW_M: f64 = 30.0;

/// 안내 지점 종류 (화면 화살표).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum NavTurn {
    Straight,
    Left,
    Right,
    SlightLeft,
    SlightRight,
    UTurn,
    Waypoint,
    Destination,
    Other,
}

/// 안내 지점 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NavStep {
    pub turn: NavTurn,
    /// 화면 글 ("좌회전", "경포 방면으로 왼쪽 10시 방향")
    pub text: String,
    /// 길 이름 (모르면 빈 문자열)
    pub road: String,
    pub at: GeoPoint,
    /// 경로 처음부터 이 지점까지 거리(m)
    pub along_m: f64,
}

/// 경로 전체 (그리기용).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NavRoute {
    pub points: Vec<GeoPoint>,
    pub steps: Vec<NavStep>,
    pub distance_m: f64,
    pub duration_s: f64,
}

/// 위치 하나에 대한 안내 상태.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NavProgress {
    /// 경로에 붙인 위치와 진행 방향(도, 북쪽 0)
    pub snapped: GeoPoint,
    pub bearing_deg: f64,
    /// 경로에서 떨어진 거리(m)
    pub off_m: f64,
    /// 다음 안내 지점 ([NavRoute::steps] 번호, 다 지났으면 None)
    pub next_step: Option<u32>,
    pub to_next_m: f64,
    pub remaining_m: f64,
    pub remaining_s: f64,
    /// 지금 읽을 말 (없으면 None)
    pub say: Option<String>,
    /// 경로를 다시 찾아야 한다
    pub reroute: bool,
    /// 목적지에 도착했다
    pub arrived: bool,
    /// 방금 지난 경유지 번호 (0부터, 이번 위치에서 처음 지났을 때만)
    pub passed_waypoint: Option<u32>,
}

/// 경로 받기 주소 (카카오모빌리티 `v1/directions`). [stops] = 경유지들 + 도착(마지막). 경유지는 앞에서 5곳까지.
#[uniffi::export]
pub fn nav_directions_url(origin: GeoPoint, stops: Vec<GeoPoint>) -> String {
    let xy = |p: &GeoPoint| format!("{:.6},{:.6}", p.lon, p.lat);
    let Some((dest, via)) = stops.split_last() else { return String::new() };
    let mut u = format!(
        "https://apis-navi.kakaomobility.com/v1/directions?origin={}&destination={}&priority=RECOMMEND&summary=false",
        xy(&origin),
        xy(dest)
    );
    if !via.is_empty() {
        let w: Vec<String> = via.iter().take(5).map(xy).collect();
        u.push_str("&waypoints=");
        u.push_str(&w.join("%7C"));
    }
    u
}

struct State {
    cursor: usize,
    spoken: Vec<u8>,
    off_count: u32,
    waypoints_passed: u32,
    arrived: bool,
}

/// 안내 한 번 (경로 하나). 위치마다 [update]를 부른다.
#[derive(uniffi::Object)]
pub struct NavSession {
    route: NavRoute,
    pts: Vec<Point>,
    /// 점마다 경로 처음부터의 거리(m)
    cum: Vec<f64>,
    state: Mutex<State>,
}

#[uniffi::export]
impl NavSession {
    /// 길찾기 응답 JSON으로 만든다. 경로가 없으면 `InvalidInput` (메시지에 카카오가 준 사유).
    #[uniffi::constructor]
    pub fn new(json: String) -> Result<std::sync::Arc<Self>, CoreError> {
        let route = parse_kakao(&json)?;
        let pts: Vec<Point> = route.points.iter().map(|p| Point { lat: p.lat, lon: p.lon }).collect();
        let mut cum = vec![0.0; pts.len()];
        for i in 1..pts.len() {
            cum[i] = cum[i - 1] + haversine_m(pts[i - 1], pts[i]);
        }
        let n = route.steps.len();
        Ok(std::sync::Arc::new(Self {
            route,
            pts,
            cum,
            state: Mutex::new(State { cursor: 0, spoken: vec![0; n], off_count: 0, waypoints_passed: 0, arrived: false }),
        }))
    }

    pub fn route(&self) -> NavRoute {
        self.route.clone()
    }

    /// 위치 하나 → 안내 상태.
    pub fn update(&self, lat: f64, lon: f64) -> NavProgress {
        let me = Point { lat, lon };
        let mut st = self.state.lock().unwrap_or_else(|e| e.into_inner());
        // 먼저 지난번 자리에서 앞으로 [MAX_JUMP_M]까지만 본다: 같은 길을 되돌아오는 경로(갔다 오는 길)에서
        // 지금 위치가 한참 뒤 구간에 잘못 붙어 경유지를 건너뛰지 않게. 거기서 못 찾으면(터널 뒤 등) 앞쪽 전체에서 찾는다.
        let lo = st.cursor.saturating_sub(5);
        let reach = self.cum.get(st.cursor).copied().unwrap_or(0.0) + MAX_JUMP_M;
        let mut hi = st.cursor;
        while hi + 1 < self.pts.len() && self.cum[hi] <= reach {
            hi += 1;
        }
        let nearest = |a: usize, b: usize| {
            let (mut best, mut best_d, mut best_t) = (a, f64::MAX, 0.0);
            for i in a..b {
                let (d, t) = project(me, self.pts[i], self.pts[i + 1]);
                if d < best_d {
                    (best, best_d, best_t) = (i, d, t);
                }
            }
            (best, best_d, best_t)
        };
        let (mut best, mut best_d, mut best_t) = nearest(lo, hi);
        if best_d > OFF_ROUTE_M {
            let far = nearest(st.cursor, self.pts.len().saturating_sub(1));
            if far.1 < best_d {
                (best, best_d, best_t) = far;
            }
        }
        if self.pts.len() < 2 {
            best_d = self.pts.first().map(|p| haversine_m(me, *p)).unwrap_or(0.0);
        }
        let (a, b) = (self.pts.get(best).copied().unwrap_or(me), self.pts.get(best + 1).copied().unwrap_or(me));
        let snapped = Point { lat: a.lat + (b.lat - a.lat) * best_t, lon: a.lon + (b.lon - a.lon) * best_t };
        let along = self.cum.get(best).copied().unwrap_or(0.0) + haversine_m(a, snapped);
        let total = self.cum.last().copied().unwrap_or(0.0);
        let off_route = best_d > OFF_ROUTE_M;
        if off_route {
            st.off_count += 1;
        } else {
            st.off_count = 0;
            st.cursor = best;
        }
        let remaining_m = (total - along).max(0.0);
        let remaining_s = if total > 0.0 { self.route.duration_s * remaining_m / total } else { 0.0 };

        // 지난 경유지
        let mut passed_waypoint = None;
        let wps: Vec<&NavStep> = self.route.steps.iter().filter(|s| s.turn == NavTurn::Waypoint).collect();
        if let Some(w) = wps.get(st.waypoints_passed as usize) {
            if !off_route && along + ARRIVE_M >= w.along_m {
                passed_waypoint = Some(st.waypoints_passed);
                st.waypoints_passed += 1;
            }
        }

        let next = self.route.steps.iter().position(|s| s.along_m > along + 5.0);
        let to_next = next.map(|i| self.route.steps[i].along_m - along).unwrap_or(remaining_m);
        let arrived = !st.arrived && remaining_m <= ARRIVE_M && !off_route;
        if arrived {
            st.arrived = true;
        }
        let mut say = None;
        if arrived {
            say = Some("목적지에 도착했습니다. 안내를 마칩니다.".to_string());
        } else if let (Some(i), false) = (next, off_route) {
            let s = &self.route.steps[i];
            let level = if to_next <= NOW_M { 3 } else if to_next <= NEAR_M { 2 } else if to_next <= FAR_M { 1 } else { 0 };
            if level > st.spoken[i] {
                st.spoken[i] = level;
                say = Some(speech(s, to_next, level));
            }
        }
        let reroute = st.off_count >= OFF_ROUTE_FIXES;
        if reroute {
            st.off_count = 0;
        }
        NavProgress {
            snapped: GeoPoint { lat: snapped.lat, lon: snapped.lon },
            bearing_deg: bearing_deg(a, b),
            off_m: best_d,
            next_step: next.map(|i| i as u32),
            to_next_m: to_next,
            remaining_m,
            remaining_s,
            say,
            reroute,
            arrived,
            passed_waypoint,
        }
    }
}

/// 점 p를 선분 ab에 내린 거리(m)와 위치(0~1). 짧은 구간이라 평면으로 계산한다.
fn project(p: Point, a: Point, b: Point) -> (f64, f64) {
    let k = (a.lat.to_radians()).cos();
    let (ax, ay, bx, by, px, py) = (a.lon * k, a.lat, b.lon * k, b.lat, p.lon * k, p.lat);
    let (dx, dy) = (bx - ax, by - ay);
    let len2 = dx * dx + dy * dy;
    let t = if len2 > 0.0 { (((px - ax) * dx + (py - ay) * dy) / len2).clamp(0.0, 1.0) } else { 0.0 };
    let q = Point { lat: ay + dy * t, lon: (ax + dx * t) / k };
    (haversine_m(p, q), t)
}

fn speech(s: &NavStep, dist: f64, level: u8) -> String {
    let what = match s.turn {
        NavTurn::Destination => "목적지입니다".to_string(),
        NavTurn::Waypoint => "경유지입니다".to_string(),
        _ if !s.text.trim().is_empty() => s.text.trim().to_string(),
        _ => "직진입니다".to_string(),
    };
    match level {
        1 => format!("{} 앞에서 {}", meters(dist), what),
        2 => format!("잠시 후 {}", what),
        _ => what,
    }
}

fn meters(d: f64) -> String {
    if d >= 1000.0 {
        format!("{:.1}킬로미터", d / 1000.0)
    } else {
        format!("{}미터", ((d / 50.0).round() * 50.0).max(50.0) as i64)
    }
}

/// 카카오 안내 종류 코드 → 화살표. (공식 표: 1 좌회전 · 2 우회전 · 3 유턴 · 5 왼쪽 · 6 오른쪽 · 11~ 방면/고속도로 진출입 · 100 출발 · 101 도착 · 1000 경유지)
fn turn_of(code: i64, text: &str) -> NavTurn {
    match code {
        1 => NavTurn::Left,
        2 => NavTurn::Right,
        3 => NavTurn::UTurn,
        5 | 7 => NavTurn::SlightLeft,
        6 | 8 => NavTurn::SlightRight,
        101 | 201 => NavTurn::Destination,
        1000 | 300 | 301 => NavTurn::Waypoint,
        0 | 4 => NavTurn::Straight,
        _ if text.contains("왼쪽") || text.contains("좌") => NavTurn::SlightLeft,
        _ if text.contains("오른쪽") || text.contains("우") => NavTurn::SlightRight,
        _ => NavTurn::Other,
    }
}

fn parse_kakao(json: &str) -> Result<NavRoute, CoreError> {
    let v: Value = serde_json::from_str(json).map_err(|_| CoreError::InvalidInput("길찾기 응답 형식".into()))?;
    let r = v["routes"].get(0).ok_or_else(|| CoreError::InvalidInput("경로 없음".into()))?;
    if r["result_code"].as_i64().unwrap_or(-1) != 0 {
        return Err(CoreError::InvalidInput(r["result_msg"].as_str().unwrap_or("경로를 찾지 못함").to_string()));
    }
    let mut points: Vec<GeoPoint> = Vec::new();
    // (종류, 글, 길 이름, 위치, 구간 [처음, 끝] 점 번호, 구간 끝 안내인지)
    let mut raw_steps: Vec<(i64, String, String, GeoPoint, usize, usize, bool)> = Vec::new();
    for sec in r["sections"].as_array().into_iter().flatten() {
        let sec_start = points.len().saturating_sub(1);
        for road in sec["roads"].as_array().into_iter().flatten() {
            let xs: Vec<f64> = road["vertexes"].as_array().into_iter().flatten().filter_map(|x| x.as_f64()).collect();
            for [x, y] in xs.as_chunks::<2>().0 {
                let p = GeoPoint { lat: *y, lon: *x };
                if points.last() != Some(&p) {
                    points.push(p);
                }
            }
        }
        let sec_end = points.len().saturating_sub(1);
        for g in sec["guides"].as_array().into_iter().flatten() {
            let code = g["type"].as_i64().unwrap_or(0);
            if code == 100 {
                continue; // 출발
            }
            let text = g["guidance"].as_str().unwrap_or("").to_string();
            let road = g["name"].as_str().unwrap_or("").to_string();
            let at = GeoPoint { lat: g["y"].as_f64().unwrap_or(0.0), lon: g["x"].as_f64().unwrap_or(0.0) };
            // 경유지 안내는 앞 구간 끝과 다음 구간 처음에 같은 것이 두 번 온다 → 한 번만
            if raw_steps.last().is_some_and(|s| s.0 == code && s.3 == at) {
                continue;
            }
            let at_end = g["road_index"].as_i64() == Some(-1);
            raw_steps.push((code, text, road, at, sec_start, sec_end, at_end));
        }
    }
    if points.len() < 2 {
        return Err(CoreError::InvalidInput("경로 점이 없음".into()));
    }
    let pts: Vec<Point> = points.iter().map(|p| Point { lat: p.lat, lon: p.lon }).collect();
    let mut cum = vec![0.0; pts.len()];
    for i in 1..pts.len() {
        cum[i] = cum[i - 1] + haversine_m(pts[i - 1], pts[i]);
    }
    // 안내 지점의 경로 위 거리: 구간 끝 안내(경유지 · 목적지)는 그 구간의 마지막 점,
    // 나머지는 그 구간 안에서 앞 안내 뒤로 가장 가까운 점 (갔다 오는 길에서 엉뚱한 쪽에 붙지 않게)
    let mut from = 0usize;
    let steps = raw_steps
        .into_iter()
        .map(|(code, text, road, at, s0, s1, at_end)| {
            let i = if at_end {
                s1
            } else {
                let q = Point { lat: at.lat, lon: at.lon };
                let lo = from.max(s0).min(s1);
                (lo..=s1).min_by(|&a, &b| haversine_m(pts[a], q).total_cmp(&haversine_m(pts[b], q))).unwrap_or(lo)
            };
            from = i;
            let text = if text.trim().is_empty() { default_text(turn_of(code, "")) } else { text };
            NavStep { turn: turn_of(code, &text), text, road, at, along_m: cum[i] }
        })
        .collect();
    Ok(NavRoute {
        points,
        steps,
        distance_m: r["summary"]["distance"].as_f64().unwrap_or(*cum.last().unwrap_or(&0.0)),
        duration_s: r["summary"]["duration"].as_f64().unwrap_or(0.0),
    })
}

fn default_text(t: NavTurn) -> String {
    match t {
        NavTurn::Left => "좌회전",
        NavTurn::Right => "우회전",
        NavTurn::UTurn => "유턴",
        NavTurn::SlightLeft => "왼쪽 방향",
        NavTurn::SlightRight => "오른쪽 방향",
        NavTurn::Destination => "목적지",
        NavTurn::Waypoint => "경유지",
        _ => "직진",
    }
    .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 북쪽으로 1km 간 뒤 동쪽으로 꺾어 500m (좌표는 대략), 꺾는 곳에 우회전 안내, 끝에 도착.
    fn sample() -> String {
        let (lat0, lon0) = (37.5, 127.0);
        let mut v = Vec::new();
        for i in 0..=10 {
            v.push(lon0);
            v.push(lat0 + i as f64 * 0.0009);
        }
        let lat1 = lat0 + 0.009;
        for i in 1..=5 {
            v.push(lon0 + i as f64 * 0.00113);
            v.push(lat1);
        }
        serde_json::json!({"routes":[{"result_code":0,"result_msg":"길찾기 성공","summary":{"distance":1500,"duration":180},
            "sections":[{"roads":[{"vertexes":v}],"guides":[
                {"type":100,"guidance":"출발지","name":"","x":lon0,"y":lat0},
                {"type":2,"guidance":"우회전","name":"테스트로","x":lon0,"y":lat1},
                {"type":1000,"guidance":"경유지","name":"","x":lon0+0.00226,"y":lat1},
                {"type":1000,"guidance":"경유지","name":"","x":lon0+0.00226,"y":lat1},
                {"type":101,"guidance":"목적지","name":"","x":lon0+0.00565,"y":lat1,"road_index":-1}]}]}]})
        .to_string()
    }

    #[test]
    fn parses_route_and_steps() {
        let s = NavSession::new(sample()).unwrap();
        let r = s.route();
        assert_eq!(r.points.len(), 16);
        assert_eq!(r.steps.len(), 3, "겹친 경유지는 하나로");
        assert_eq!(r.steps[0].turn, NavTurn::Right);
        assert_eq!(r.steps[1].turn, NavTurn::Waypoint);
        assert!((r.steps[0].along_m - 1000.0).abs() < 20.0, "{}", r.steps[0].along_m);
        assert_eq!(r.steps[2].turn, NavTurn::Destination);
    }

    #[test]
    fn speaks_each_level_once_and_arrives() {
        let s = NavSession::new(sample()).unwrap();
        // 600m 지점: 다음 안내(우회전)까지 약 400m → 멀리 안내
        let p = s.update(37.5 + 0.0054, 127.00002);
        assert_eq!(p.next_step, Some(0));
        assert!(p.say.as_deref().unwrap_or("").contains("우회전"), "{:?}", p.say);
        assert!(s.update(37.5 + 0.00545, 127.0).say.is_none(), "같은 단계는 한 번만");
        let p = s.update(37.5 + 0.0081, 127.0); // 약 100m 전
        assert_eq!(p.say.as_deref(), Some("잠시 후 우회전"));
        // 꺾은 뒤 경유지를 지나면 한 번 알린다
        assert_eq!(s.update(37.509, 127.0024).passed_waypoint, Some(0));
        assert_eq!(s.update(37.509, 127.0026).passed_waypoint, None);
        // 끝까지
        let end = s.update(37.509, 127.00565);
        assert!(end.arrived && end.say.unwrap().contains("도착"));
        assert!(!s.update(37.509, 127.00565).arrived, "도착은 한 번만");
    }

    #[test]
    fn reroutes_after_repeated_off_route() {
        let s = NavSession::new(sample()).unwrap();
        s.update(37.501, 127.0);
        let far = (37.501, 127.003); // 약 260m 동쪽
        assert!(!s.update(far.0, far.1).reroute);
        assert!(!s.update(far.0, far.1).reroute);
        assert!(s.update(far.0, far.1).reroute);
    }

    /// 북쪽으로 500m 갔다가 같은 길로 돌아오는 경로, 끝(출발점 근처)에 경유지. 출발 직후 위치가 돌아오는 구간에 붙으면 안 된다.
    #[test]
    fn out_and_back_does_not_jump_ahead() {
        let mut v = Vec::new();
        for i in (0..=5).chain((0..5).rev()) {
            v.push(127.0 + if i == 5 { 0.0 } else { 0.00001 });
            v.push(37.5 + i as f64 * 0.0009);
        }
        let json = serde_json::json!({"routes":[{"result_code":0,"summary":{"distance":1000,"duration":120},
            "sections":[{"roads":[{"vertexes":v}],"guides":[{"type":1000,"guidance":"경유지","x":127.00001,"y":37.5,"road_index":-1}]}]}]})
        .to_string();
        let s = NavSession::new(json).unwrap();
        let p = s.update(37.5001, 127.0);
        assert!(p.along_ok(), "{p:?}");
        assert_eq!(p.passed_waypoint, None, "출발하자마자 끝 경유지를 지난 것으로 보지 않는다");
    }

    impl NavProgress {
        fn along_ok(&self) -> bool {
            self.remaining_m > 800.0
        }
    }

    #[test]
    fn rejects_failed_route_and_builds_url() {
        let bad = r#"{"routes":[{"result_code":104,"result_msg":"출발지와 도착지가 5 m 이내"}]}"#;
        assert!(NavSession::new(bad.into()).is_err());
        let u = nav_directions_url(
            GeoPoint { lat: 37.0, lon: 127.0 },
            vec![GeoPoint { lat: 37.1, lon: 127.1 }, GeoPoint { lat: 37.2, lon: 127.2 }],
        );
        assert!(u.contains("origin=127.000000,37.000000") && u.contains("destination=127.200000,37.200000") && u.contains("waypoints=127.100000,37.100000"));
    }
}

#[cfg(test)]
mod real {
    /// 실제 응답으로 확인: `NAV_JSON=파일 cargo test nav::real -- --ignored --nocapture`
    #[test]
    #[ignore]
    fn real_route() {
        let Ok(p) = std::env::var("NAV_JSON") else { return };
        let s = super::NavSession::new(std::fs::read_to_string(p).unwrap()).unwrap();
        for st in s.route().steps {
            println!("{:?} {:.0} {}", st.turn, st.along_m, st.text);
        }
        let first = s.route().points[0];
        println!("{:?}", s.update(first.lat, first.lon));
    }
}

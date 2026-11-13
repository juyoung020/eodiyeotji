//! 길 위의 필요 ("나 똥마려" · "졸려" · "배고파" · "커피") — 경로 앞쪽에서 들를 곳을 찾는 도구 에이전트의 계산 부분.
//!
//! 흐름 (docs/소통/2026-11-13_길위_도구_에이전트.md):
//! - Kotlin `agent/roadside/RoadsideAgent`가 LLM 도구 호출 루프를 돌린다 (LLM은 해석 · 계획만, 행동은 제안 → 운전자 "응").
//! - 여기(Rust)는 무거운 일과 규칙을 맡는다:
//!   1. [need]: 흔한 말은 규칙으로 바로 알아듣는다 (LLM이 없거나 느릴 때의 빠른 길, 같은 도구를 그대로 쓴다).
//!   2. [rank]: 경로(꺾은선) + 내 위치 + 후보 → 경로를 따라 앞쪽 거리 · 옆으로 벗어나는 거리 · 도착 분. 지나온 곳 · 반대 방향 휴게소는 뺀다.
//!   3. [bundled]: 기기에 넣어 둔 고속도로 휴게소 · 졸음쉼터 (끊겨도 동작).
//!   4. [parse_kakao]: 카카오 로컬 검색 응답 → 후보.
//!   5. [tool_specs] · [parse_call] · [say]: 도구 정의(공급자 중립 JSON 스키마), 도구 인자 검증, 규칙 답 문장.
//!
//! 오른쪽 통행: 방향이 정해진 시설(이름에 "○○방향")은 내 진행 방향 오른쪽에 있을 때만 들를 수 있다고 본다.

use serde_json::{json, Value};

use crate::ffi::GeoPoint;
use crate::geo::{bearing_deg, haversine_m, Point};

/// 찾을 곳 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, uniffi::Enum)]
pub enum RoadsideCategory {
    RestArea,
    DrowsyShelter,
    Toilet,
    Gas,
    EvCharger,
    Cafe,
    Food,
    Convenience,
}

impl RoadsideCategory {
    pub const ALL: [RoadsideCategory; 8] = [
        Self::RestArea,
        Self::DrowsyShelter,
        Self::Toilet,
        Self::Gas,
        Self::EvCharger,
        Self::Cafe,
        Self::Food,
        Self::Convenience,
    ];

    /// 도구 인자 이름 ("rest_area").
    pub fn key(self) -> &'static str {
        match self {
            Self::RestArea => "rest_area",
            Self::DrowsyShelter => "drowsy_shelter",
            Self::Toilet => "toilet",
            Self::Gas => "gas",
            Self::EvCharger => "ev_charger",
            Self::Cafe => "cafe",
            Self::Food => "food",
            Self::Convenience => "convenience",
        }
    }

    pub fn from_key(s: &str) -> Option<Self> {
        let s = s.trim().to_ascii_lowercase();
        Self::ALL.into_iter().find(|c| c.key() == s)
    }

    /// 말로 읽는 이름 ("졸음쉼터").
    pub fn word(self) -> &'static str {
        match self {
            Self::RestArea => "휴게소",
            Self::DrowsyShelter => "졸음쉼터",
            Self::Toilet => "화장실",
            Self::Gas => "주유소",
            Self::EvCharger => "충전소",
            Self::Cafe => "카페",
            Self::Food => "식당",
            Self::Convenience => "편의점",
        }
    }
}

/// 카카오 로컬 검색으로 찾는 법: 분류 코드(있으면 category.json) 또는 검색어(keyword.json).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RoadsideKakaoQuery {
    pub group_code: String,
    pub keyword: String,
    /// 찾을 반경(m)
    pub radius_m: u32,
}

pub fn kakao_query(c: RoadsideCategory) -> RoadsideKakaoQuery {
    let (code, kw, r) = match c {
        RoadsideCategory::RestArea => ("", "휴게소", 5_000),
        RoadsideCategory::DrowsyShelter => ("", "졸음쉼터", 5_000),
        RoadsideCategory::Toilet => ("", "공중화장실", 2_000),
        RoadsideCategory::Gas => ("OL7", "", 3_000),
        RoadsideCategory::EvCharger => ("", "전기차충전소", 3_000),
        RoadsideCategory::Cafe => ("CE7", "", 2_000),
        RoadsideCategory::Food => ("FD6", "", 2_000),
        RoadsideCategory::Convenience => ("CS2", "", 2_000),
    };
    RoadsideKakaoQuery { group_code: code.into(), keyword: kw.into(), radius_m: r }
}

/// 들를 후보 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RoadsidePlace {
    pub name: String,
    pub category: RoadsideCategory,
    pub lat: f64,
    pub lon: f64,
    pub address: String,
    /// 한쪽 방향 차로에서만 들어가는 시설 (고속도로 휴게소 "부산방향") → 진행 방향 오른쪽일 때만
    pub one_way: bool,
    /// "기기 목록" · "카카오"
    pub source: String,
}

/// 경로 앞쪽에서 찾은 곳.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RoadsideHit {
    pub place: RoadsidePlace,
    /// 경로를 따라 지금 위치에서 그곳 옆까지 (m)
    pub along_m: f64,
    /// 경로에서 벗어났다 돌아오는 거리 (m, 왕복)
    pub detour_m: f64,
    /// 도착까지 걸릴 분 (지금 속도 기준, 올림)
    pub eta_min: u32,
}

/// 규칙으로 알아들은 필요.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum RoadsideNeedKind {
    Toilet,
    Sleepy,
    Hungry,
    Coffee,
    Fuel,
    Charge,
    Rest,
    Snack,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RoadsideNeed {
    pub kind: RoadsideNeedKind,
    /// 찾을 순서 (앞이 먼저 권할 곳)
    pub categories: Vec<RoadsideCategory>,
}

// ---------------------------------------------------------------------------------------------
// 1. 빠른 길: 흔한 말
// ---------------------------------------------------------------------------------------------

/// 지난 기억을 묻는 말 ("작년에 들렀던 휴게소 어디였지?")은 기억 에이전트 몫이다.
const MEMORY_MARKERS: [&str; 10] = ["었지", "였지", "갔던", "들렀던", "들른", "지난", "작년", "그때", "저번", "기억"];

/// 말에서 필요를 알아듣는다. 모르면 None (LLM 또는 기억 에이전트로).
pub fn need(text: &str) -> Option<RoadsideNeed> {
    let t: String = text.split_whitespace().collect::<Vec<_>>().join(" ");
    if t.is_empty() || MEMORY_MARKERS.iter().any(|m| t.contains(m)) {
        return None;
    }
    let has = |ws: &[&str]| ws.iter().any(|w| t.contains(w));
    use RoadsideCategory as C;
    use RoadsideNeedKind as K;
    let (kind, cats) = if has(&["화장실", "똥", "오줌", "소변", "대변", "마려", "볼일", "급똥", "쉬하"]) {
        (K::Toilet, vec![C::RestArea, C::DrowsyShelter])
    } else if has(&["졸려", "졸리", "졸음", "피곤", "잠와", "잠 와", "잠이 와", "하품", "눈이 감", "눈 감겨"]) {
        (K::Sleepy, vec![C::DrowsyShelter, C::RestArea])
    } else if has(&["주유", "기름", "연료"]) {
        (K::Fuel, vec![C::Gas, C::RestArea])
    } else if has(&["충전"]) {
        (K::Charge, vec![C::EvCharger, C::RestArea])
    } else if has(&["커피", "카페", "아아", "라떼"]) {
        (K::Coffee, vec![C::RestArea, C::Cafe])
    } else if has(&["배고", "배가 고", "출출", "허기", "밥 먹", "밥먹", "식사", "점심", "저녁 먹", "요기"]) {
        (K::Hungry, vec![C::RestArea, C::Food])
    } else if has(&["편의점", "간식", "물 사", "음료"]) {
        (K::Snack, vec![C::Convenience, C::RestArea])
    } else if has(&["휴게소", "쉼터", "쉬어", "쉬고", "쉴 데", "쉴데", "쉬었다", "스트레칭", "바람 쐬"]) {
        (K::Rest, vec![C::RestArea, C::DrowsyShelter])
    } else {
        return None;
    };
    Some(RoadsideNeed { kind, categories: cats })
}

// ---------------------------------------------------------------------------------------------
// 2. 경로 앞쪽 계산
// ---------------------------------------------------------------------------------------------

/// 경로가 없을 때 진행 방향으로 그어 보는 길이 (m).
const STRAIGHT_AHEAD_M: f64 = 60_000.0;
/// 이보다 뒤(지나온 곳)는 뺀다 (m).
const BEHIND_SLACK_M: f64 = 80.0;
/// 앞쪽으로 이보다 먼 곳은 보지 않는다 (m).
pub const MAX_AHEAD_M: f64 = 150_000.0;
/// 방향 시설이 왼쪽이어도 이 안쪽이면 들어갈 수 있다고 본다 (같은 차로 바로 옆, m).
const ONE_WAY_LEFT_SLACK_M: f64 = 25.0;
/// 같은 곳으로 볼 거리 (m)
const SAME_PLACE_M: f64 = 150.0;

#[derive(Clone, Copy)]
struct Proj {
    along: f64,
    lateral: f64,
    /// 오른쪽이면 +, 왼쪽이면 -
    side: f64,
    /// 그 자리 진행 방위 (도)
    bearing: f64,
}

/// 위도 · 경도를 [o] 둘레 평면(m)으로.
fn local(o: Point, p: Point) -> (f64, f64) {
    let k = 111_320.0;
    ((p.lon - o.lon) * k * o.lat.to_radians().cos(), (p.lat - o.lat) * 110_540.0)
}

/// 선분 a→b에 p를 내린 결과: (선분 안 비율 t, 옆 거리 m, 부호: 오른쪽 +).
fn project(p: Point, a: Point, b: Point) -> (f64, f64, f64) {
    let (bx, by) = local(a, b);
    let (px, py) = local(a, p);
    let len2 = bx * bx + by * by;
    let t = if len2 <= f64::EPSILON { 0.0 } else { ((px * bx + py * by) / len2).clamp(0.0, 1.0) };
    let (dx, dy) = (px - t * bx, py - t * by);
    // 진행 방향 (bx, by)에 대해 cross > 0 이면 왼쪽
    let cross = bx * py - by * px;
    ((t), (dx * dx + dy * dy).sqrt(), if cross > 0.0 { -1.0 } else { 1.0 })
}

/// "○○방향"의 도시 (휴게소 · 졸음쉼터 이름에 나오는 것).
const DIRECTION_CITIES: [(&str, f64, f64); 22] = [
    ("서울", 37.5665, 126.978),
    ("부산", 35.1796, 129.0756),
    ("강릉", 37.7519, 128.8761),
    ("인천", 37.4563, 126.7052),
    ("목포", 34.8118, 126.3922),
    ("대전", 36.3504, 127.3845),
    ("대구", 35.8714, 128.6014),
    ("광주", 35.1595, 126.8526),
    ("춘천", 37.8813, 127.7298),
    ("양양", 38.0754, 128.6189),
    ("통영", 34.8544, 128.4331),
    ("순천", 34.9507, 127.4872),
    ("마산", 35.2141, 128.5812),
    ("창원", 35.2281, 128.6811),
    ("당진", 36.8898, 126.6459),
    ("평택", 36.9921, 127.1129),
    ("포항", 36.019, 129.3435),
    ("울산", 35.5384, 129.3114),
    ("진주", 35.18, 128.1076),
    ("여주", 37.2983, 127.637),
    ("원주", 37.3422, 127.9202),
    ("천안", 36.8151, 127.1139),
];

/// 방향이 정해진 시설에 지금 방향으로 들어갈 수 있나.
/// 이름의 "○○방향" 도시를 알면: 그 자리 진행 방위와 시설 → 도시 방위가 90도 안쪽이면 같은 방향.
/// 모르면 오른쪽 통행 규칙: 진행 방향 오른쪽(또는 바로 옆)이어야 한다.
fn same_direction(name: &str, at: Point, p: &Proj) -> bool {
    let city = name.split_whitespace().find_map(|w| {
        let w = w.trim_matches(|c: char| c == '(' || c == ')');
        let head = w.strip_suffix("방향")?;
        DIRECTION_CITIES.iter().find(|(n, _, _)| head.ends_with(n))
    });
    match city {
        Some(&(_, lat, lon)) => {
            let to_city = bearing_deg(at, Point { lat, lon });
            let diff = (to_city - p.bearing).rem_euclid(360.0);
            diff.min(360.0 - diff) < 90.0
        }
        None => p.side > 0.0 || p.lateral <= ONE_WAY_LEFT_SLACK_M,
    }
}

/// 꺾은선 위 누적 거리.
fn cumulative(line: &[Point]) -> Vec<f64> {
    let mut cum = vec![0.0; line.len()];
    for i in 1..line.len() {
        cum[i] = cum[i - 1] + haversine_m(line[i - 1], line[i]);
    }
    cum
}

/// [p]를 꺾은선에 내린다. [from_along] 이후 구간에서 가장 가까운 곳. 선분이 없으면 None.
fn project_line(p: Point, line: &[Point], cum: &[f64], from_along: f64) -> Option<Proj> {
    let mut best: Option<Proj> = None;
    for i in 0..line.len().saturating_sub(1) {
        if cum[i + 1] < from_along {
            continue;
        }
        let (t, d, side) = project(p, line[i], line[i + 1]);
        let along = cum[i] + t * (cum[i + 1] - cum[i]);
        if best.is_none_or(|b| d < b.lateral) {
            best = Some(Proj { along, lateral: d, side, bearing: bearing_deg(line[i], line[i + 1]) });
        }
    }
    best
}

/// [here]에서 [heading_deg] 쪽으로 [len_m] 곧게 그은 선.
fn straight_line(here: Point, heading_deg: f64, len_m: f64) -> Vec<Point> {
    let h = heading_deg.to_radians();
    let steps = 30;
    (0..=steps)
        .map(|i| {
            let d = len_m * i as f64 / steps as f64;
            Point {
                lat: here.lat + d * h.cos() / 110_540.0,
                lon: here.lon + d * h.sin() / (111_320.0 * here.lat.to_radians().cos()),
            }
        })
        .collect()
}

/// 계산에 쓸 경로: 받은 경로(2점 이상)가 있으면 그것, 없으면 진행 방향 직선, 그것도 없으면 None (거리순만).
fn working_line(route: &[GeoPoint], here: Point, heading: Option<f64>) -> Option<Vec<Point>> {
    if route.len() >= 2 {
        return Some(route.iter().map(|p| Point { lat: p.lat, lon: p.lon }).collect());
    }
    heading.filter(|h| h.is_finite()).map(|h| straight_line(here, h, STRAIGHT_AHEAD_M))
}

/// 이 속도로 간다고 본다 (km/h): 너무 느리거나 모르면 시내·고속 사이 값.
fn cruise_kmh(speed_kmh: Option<f64>) -> f64 {
    match speed_kmh {
        Some(s) if s.is_finite() && s >= 30.0 => s.min(110.0),
        _ => 70.0,
    }
}

pub fn eta_min(along_m: f64, detour_m: f64, speed_kmh: Option<f64>) -> u32 {
    let km = (along_m + detour_m / 2.0) / 1000.0;
    ((km / cruise_kmh(speed_kmh)) * 60.0).ceil().max(1.0) as u32
}

/// 경로 앞쪽 후보를 가까운 순으로. [max_detour_m]: 왕복으로 벗어나도 되는 거리. [limit]: 최대 개수.
pub fn rank(
    route: &[GeoPoint],
    here: GeoPoint,
    heading_deg: Option<f64>,
    speed_kmh: Option<f64>,
    candidates: Vec<RoadsidePlace>,
    max_detour_m: f64,
    limit: usize,
) -> Vec<RoadsideHit> {
    let me = Point { lat: here.lat, lon: here.lon };
    let max_detour = if max_detour_m.is_finite() && max_detour_m > 0.0 { max_detour_m } else { 2_000.0 };
    let mut hits: Vec<RoadsideHit> = match working_line(route, me, heading_deg) {
        Some(line) => {
            let cum = cumulative(&line);
            // 내 자리: 경로 처음 쪽에서 가장 가까운 곳 (경로가 나를 지나지 않으면 맨 앞)
            let mine = project_line(me, &line, &cum, 0.0).map(|p| p.along).unwrap_or(0.0);
            candidates
                .into_iter()
                .filter_map(|c| {
                    let p = project_line(Point { lat: c.lat, lon: c.lon }, &line, &cum, mine - BEHIND_SLACK_M)?;
                    let along = p.along - mine;
                    if along < -BEHIND_SLACK_M || along > MAX_AHEAD_M {
                        return None;
                    }
                    if c.one_way && !same_direction(&c.name, Point { lat: c.lat, lon: c.lon }, &p) {
                        return None; // 반대 방향 휴게소
                    }
                    // 고속도로 시설은 길 바로 옆이어도 들어가는 길이 있다 → 옆 거리만큼 왕복
                    let detour = 2.0 * p.lateral;
                    if detour > max_detour {
                        return None;
                    }
                    Some(RoadsideHit { eta_min: eta_min(along.max(0.0), detour, speed_kmh), along_m: along.max(0.0), detour_m: detour, place: c })
                })
                .collect()
        }
        // 경로도 방향도 모름: 곧은 거리순 (앞뒤를 가릴 수 없다)
        None => candidates
            .into_iter()
            .filter_map(|c| {
                let d = haversine_m(me, Point { lat: c.lat, lon: c.lon });
                (d <= max_detour.max(5_000.0)).then(|| RoadsideHit { eta_min: eta_min(d, 0.0, speed_kmh), along_m: d, detour_m: 0.0, place: c })
            })
            .collect(),
    };
    hits.sort_by(|a, b| a.along_m.total_cmp(&b.along_m).then(a.detour_m.total_cmp(&b.detour_m)));
    // 같은 곳 (기기 목록과 카카오가 둘 다 준 것 등) 하나만
    let mut out: Vec<RoadsideHit> = Vec::new();
    for h in hits {
        let dup = out.iter().any(|o| {
            o.place.category == h.place.category
                && haversine_m(Point { lat: o.place.lat, lon: o.place.lon }, Point { lat: h.place.lat, lon: h.place.lon }) < SAME_PLACE_M
        });
        if !dup {
            out.push(h);
        }
        if out.len() >= limit.max(1) {
            break;
        }
    }
    out
}

/// 카카오에 물어볼 지점: 경로 앞쪽으로 [step_m]마다 [count]개 (경로가 없으면 진행 방향, 그것도 없으면 지금 위치 하나).
pub fn probe_points(route: &[GeoPoint], here: GeoPoint, heading_deg: Option<f64>, step_m: f64, count: u32) -> Vec<GeoPoint> {
    let me = Point { lat: here.lat, lon: here.lon };
    let Some(line) = working_line(route, me, heading_deg) else { return vec![here] };
    let cum = cumulative(&line);
    let mine = project_line(me, &line, &cum, 0.0).map(|p| p.along).unwrap_or(0.0);
    let step = if step_m.is_finite() && step_m > 100.0 { step_m } else { 3_000.0 };
    let total = *cum.last().unwrap_or(&0.0);
    let mut out = Vec::new();
    for k in 0..count.max(1) {
        let target = mine + step * (k as f64 + 0.5);
        if target > total {
            break;
        }
        let i = cum.partition_point(|&c| c < target).clamp(1, line.len() - 1);
        let seg = cum[i] - cum[i - 1];
        let t = if seg > 0.0 { (target - cum[i - 1]) / seg } else { 0.0 };
        let (a, b) = (line[i - 1], line[i]);
        out.push(GeoPoint { lat: a.lat + t * (b.lat - a.lat), lon: a.lon + t * (b.lon - a.lon) });
    }
    if out.is_empty() {
        out.push(here);
    }
    out
}

// ---------------------------------------------------------------------------------------------
// 3. 기기 목록 · 카카오 응답
// ---------------------------------------------------------------------------------------------

const BUNDLED_JSON: &str = include_str!("roadside_places.json");

/// 기기에 넣어 둔 휴게소 · 졸음쉼터 (출처는 json "source"). 휴게소 · 졸음쉼터에는 화장실이 있다 → 화장실을 찾으면 둘 다 낸다.
pub fn bundled(categories: &[RoadsideCategory]) -> Vec<RoadsidePlace> {
    let Ok(root) = serde_json::from_str::<Value>(BUNDLED_JSON) else { return Vec::new() };
    let want_rest = categories.iter().any(|c| matches!(c, RoadsideCategory::RestArea));
    let want_shelter = categories.iter().any(|c| matches!(c, RoadsideCategory::DrowsyShelter));
    root["places"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|p| {
            let cat = match p["kind"].as_str()? {
                "rest_area" if want_rest => RoadsideCategory::RestArea,
                "drowsy_shelter" if want_shelter => RoadsideCategory::DrowsyShelter,
                _ => return None,
            };
            let dir = p["dir"].as_str().unwrap_or("");
            Some(RoadsidePlace {
                name: p["name"].as_str()?.to_string(),
                category: cat,
                lat: p["lat"].as_f64()?,
                lon: p["lon"].as_f64()?,
                address: p["road"].as_str().unwrap_or("").to_string(),
                one_way: !dir.is_empty(),
                source: "기기 목록".into(),
            })
        })
        .collect()
}

/// 카카오 로컬 검색(keyword.json · category.json) 응답 → 후보. 휴게소를 찾을 땐 "고속도로휴게소" 분류만 (휴게소 안 가게는 뺀다).
pub fn parse_kakao(json: &str, category: RoadsideCategory) -> Vec<RoadsidePlace> {
    let Ok(root) = serde_json::from_str::<Value>(json) else { return Vec::new() };
    root["documents"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|d| {
            let name = d["place_name"].as_str()?.trim().to_string();
            let cat_name = d["category_name"].as_str().unwrap_or("");
            match category {
                // 고속도로휴게소 분류 또는 이름에 "휴게소" (운수종사자 쉼터 · 휴게소 안 가게는 뺀다)
                RoadsideCategory::RestArea if !(cat_name.ends_with("고속도로휴게소") || cat_name.contains("휴게소") && name.contains("휴게소")) => return None,
                RoadsideCategory::DrowsyShelter if !name.contains("졸음쉼터") || name.contains("화장실") => return None,
                RoadsideCategory::Toilet if !cat_name.contains("화장실") => return None,
                _ => {}
            }
            let lat: f64 = d["y"].as_str()?.parse().ok()?;
            let lon: f64 = d["x"].as_str()?.parse().ok()?;
            let address = d["road_address_name"].as_str().filter(|s| !s.is_empty()).or(d["address_name"].as_str()).unwrap_or("").to_string();
            Some(RoadsidePlace {
                one_way: matches!(category, RoadsideCategory::RestArea | RoadsideCategory::DrowsyShelter) && name.contains("방향"),
                name,
                category,
                lat,
                lon,
                address,
                source: "카카오".into(),
            })
        })
        .collect()
}

// ---------------------------------------------------------------------------------------------
// 4. 지금 움직임 (기록된 위치에서)
// ---------------------------------------------------------------------------------------------

/// 기록에서 읽은 지금 움직임.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RoadsideMotion {
    pub here: Option<GeoPoint>,
    pub heading_deg: Option<f64>,
    pub speed_kmh: Option<f64>,
    /// 마지막으로 오래 멈춘 뒤 이어서 달린 분
    pub driving_min: Option<u32>,
}

/// 위치 기록(시간순 (t_ms, 점))에서 방향 · 속도 · 쉬지 않고 달린 시간.
pub fn motion(fixes: &[(i64, Point)], now_ms: i64) -> RoadsideMotion {
    let Some(&(last_t, last)) = fixes.last() else { return RoadsideMotion { here: None, heading_deg: None, speed_kmh: None, driving_min: None } };
    let here = Some(GeoPoint { lat: last.lat, lon: last.lon });
    if now_ms - last_t > 10 * 60_000 {
        return RoadsideMotion { here, heading_deg: None, speed_kmh: None, driving_min: None };
    }
    // 방향 · 속도: 최근 점 중 150 m 이상 떨어진 가장 가까운 과거 점과 비교 (2분 안)
    let mut heading = None;
    let mut speed = None;
    for &(t, p) in fixes.iter().rev().skip(1) {
        if last_t - t > 120_000 {
            break;
        }
        let d = haversine_m(p, last);
        if d >= 150.0 {
            heading = Some(bearing_deg(p, last));
            let dt = (last_t - t).max(1) as f64 / 1000.0;
            speed = Some(d / dt * 3.6);
            break;
        }
    }
    // 쉬지 않고 달린 시간: 뒤에서부터, 5분 넘게 기록이 끊기거나 5분 동안 100 m 안에 머문 곳까지
    let mut start = last_t;
    let mut i = fixes.len() - 1;
    while i > 0 {
        let (t, p) = fixes[i - 1];
        if fixes[i].0 - t > 5 * 60_000 {
            break;
        }
        // 이 점에서 5분 앞까지 거의 안 움직였으면 멈춘 곳
        let mut j = i - 1;
        while j > 0 && t - fixes[j - 1].0 < 5 * 60_000 {
            j -= 1;
        }
        if t - fixes[j].0 >= 4 * 60_000 && haversine_m(fixes[j].1, p) < 100.0 {
            break;
        }
        start = t;
        i -= 1;
    }
    let driving_min = Some(((last_t - start) / 60_000) as u32);
    RoadsideMotion { here, heading_deg: heading, speed_kmh: speed, driving_min }
}

// ---------------------------------------------------------------------------------------------
// 5. 도구 정의 · 인자 검증 · 규칙 답
// ---------------------------------------------------------------------------------------------

pub const TOOL_CONTEXT: &str = "get_drive_context";
pub const TOOL_SEARCH: &str = "search_along_route";
pub const TOOL_MEMORY: &str = "search_memory";
pub const TOOL_WAYPOINT: &str = "propose_waypoint";
pub const TOOL_NAVIGATE: &str = "navigate_to";

/// 도구 정의 (공급자 중립: `[{name, description, parameters(JSON Schema)}]`). OpenAI `tools` · Gemini `functionDeclarations`로 그대로 옮긴다.
pub fn tool_specs() -> String {
    let cats: Vec<&str> = RoadsideCategory::ALL.iter().map(|c| c.key()).collect();
    json!([
        {
            "name": TOOL_CONTEXT,
            "description": "지금 주행 상황: 주행 중인지, 속도, 위치, 진행 방향, 내비 목적지와 남은 거리, 쉬지 않고 달린 시간, 연료/배터리. 상황이 [상황]에 이미 있으면 부르지 않는다.",
            "parameters": {"type": "object", "properties": {}}
        },
        {
            "name": TOOL_SEARCH,
            "description": "내 경로 앞쪽(지나온 곳·반대 방향 제외)에서 들를 곳을 가까운 순으로 찾는다. 결과: id, 이름, 앞쪽 거리 km, 도착 분, 우회 km. 화장실이 급하면 rest_area·drowsy_shelter(둘 다 화장실 있음), 졸리면 drowsy_shelter·rest_area, 배고프면 rest_area·food.",
            "parameters": {
                "type": "object",
                "properties": {
                    "category": {"type": "string", "enum": cats, "description": "찾을 곳 종류"},
                    "max_detour_km": {"type": "number", "description": "경로에서 벗어나도 되는 왕복 거리 km (기본 2)"},
                    "limit": {"type": "integer", "description": "최대 개수 1~5 (기본 3)"}
                },
                "required": ["category"]
            }
        },
        {
            "name": TOOL_MEMORY,
            "description": "내가 전에 가 본 곳 기록에서 찾는다 (예: '늘 가던 휴게소').",
            "parameters": {"type": "object", "properties": {"query": {"type": "string"}}, "required": ["query"]}
        },
        {
            "name": TOOL_WAYPOINT,
            "description": "찾은 곳(id)을 지금 안내 경로의 경유지로 넣자고 운전자에게 묻는다. 운전자가 '응'해야 실행된다. 내비 목적지가 있을 때만.",
            "parameters": {"type": "object", "properties": {"place_id": {"type": "string"}}, "required": ["place_id"]}
        },
        {
            "name": TOOL_NAVIGATE,
            "description": "찾은 곳(id)으로 바로 안내하자고 운전자에게 묻는다 (목적지가 없을 때). 운전자가 '응'해야 실행된다.",
            "parameters": {"type": "object", "properties": {"place_id": {"type": "string"}}, "required": ["place_id"]}
        }
    ])
    .to_string()
}

/// 검증한 도구 호출.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum RoadsideCall {
    DriveContext,
    Search { category: RoadsideCategory, max_detour_km: f64, limit: u32 },
    Memory { query: String },
    Waypoint { place_id: String },
    Navigate { place_id: String },
    /// 모델에 돌려줄 오류 한 줄
    Invalid { error: String },
}

/// 도구 이름 + 인자(JSON 문자열) → 검증한 호출. 범위를 벗어난 값은 고쳐 쓴다.
pub fn parse_call(name: &str, arguments: &str) -> RoadsideCall {
    let a: Value = if arguments.trim().is_empty() {
        json!({})
    } else {
        match serde_json::from_str(arguments) {
            Ok(v @ Value::Object(_)) => v,
            _ => return RoadsideCall::Invalid { error: format!("{name}: 인자는 JSON 객체여야 합니다") },
        }
    };
    let id = |a: &Value| a["place_id"].as_str().or(a["id"].as_str()).map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
    match name.trim() {
        TOOL_CONTEXT => RoadsideCall::DriveContext,
        TOOL_SEARCH => {
            let Some(category) = a["category"].as_str().and_then(RoadsideCategory::from_key) else {
                let keys: Vec<&str> = RoadsideCategory::ALL.iter().map(|c| c.key()).collect();
                return RoadsideCall::Invalid { error: format!("category는 {} 중 하나", keys.join("|")) };
            };
            let max_detour_km = a["max_detour_km"].as_f64().filter(|v| v.is_finite()).unwrap_or(2.0).clamp(0.2, 10.0);
            let limit = a["limit"].as_u64().unwrap_or(3).clamp(1, 5) as u32;
            RoadsideCall::Search { category, max_detour_km, limit }
        }
        TOOL_MEMORY => match a["query"].as_str().map(str::trim).filter(|s| !s.is_empty()) {
            Some(q) => RoadsideCall::Memory { query: q.chars().take(60).collect() },
            None => RoadsideCall::Invalid { error: "query가 필요합니다".into() },
        },
        TOOL_WAYPOINT => id(&a).map_or(RoadsideCall::Invalid { error: "place_id가 필요합니다".into() }, |place_id| RoadsideCall::Waypoint { place_id }),
        TOOL_NAVIGATE => id(&a).map_or(RoadsideCall::Invalid { error: "place_id가 필요합니다".into() }, |place_id| RoadsideCall::Navigate { place_id }),
        other => RoadsideCall::Invalid { error: format!("없는 도구: {other}") },
    }
}

/// 도구 호출을 글로 흘린 모델(네이티브 tool_calls 없이)을 위한 예비 길: `{"tool": "...", "args": {...}}` 한 덩어리를 찾는다.
/// 찾으면 (이름, 인자 JSON). 문장 안 JSON이어도 처음 나오는 균형 잡힌 `{...}` 중 "tool"이 있는 것.
pub fn parse_json_call(text: &str) -> Option<(String, String)> {
    let bytes: Vec<char> = text.chars().collect();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == '{' {
            let (mut depth, mut in_str, mut esc) = (0i32, false, false);
            for j in i..bytes.len() {
                let c = bytes[j];
                if in_str {
                    match (esc, c) {
                        (true, _) => esc = false,
                        (false, '\\') => esc = true,
                        (false, '"') => in_str = false,
                        _ => {}
                    }
                    continue;
                }
                match c {
                    '"' => in_str = true,
                    '{' => depth += 1,
                    '}' => {
                        depth -= 1;
                        if depth == 0 {
                            let s: String = bytes[i..=j].iter().collect();
                            if let Ok(v) = serde_json::from_str::<Value>(&s) {
                                let name = v["tool"].as_str().or(v["name"].as_str()).map(str::to_string);
                                if let Some(name) = name.filter(|n| !n.trim().is_empty()) {
                                    let args = v.get("args").or(v.get("arguments")).cloned().unwrap_or(json!({}));
                                    let args = if let Value::String(s) = args { s } else { args.to_string() };
                                    return Some((name.trim().to_string(), args));
                                }
                            }
                            break;
                        }
                    }
                    _ => {}
                }
            }
        }
        i += 1;
    }
    None
}

/// 도구 결과(찾은 곳)를 모델에 돌려줄 JSON. id는 Kotlin이 붙인 것 ([ids]와 같은 순서).
pub fn hits_json(hits: &[RoadsideHit], ids: &[String]) -> String {
    let list: Vec<Value> = hits
        .iter()
        .zip(ids)
        .map(|(h, id)| {
            json!({
                "id": id,
                "name": h.place.name,
                "kind": h.place.category.key(),
                "ahead_km": round1(h.along_m / 1000.0),
                "eta_min": h.eta_min,
                "detour_km": round1(h.detour_m / 1000.0),
            })
        })
        .collect();
    json!({"ok": true, "places": list, "note": if hits.is_empty() { "경로 앞쪽에서 찾지 못함" } else { "가까운 순" }}).to_string()
}

fn round1(v: f64) -> f64 {
    (v * 10.0).round() / 10.0
}

/// 거리 말: 950 → "950m", 4200 → "4.2km", 12400 → "12km".
pub fn distance_words(m: f64) -> String {
    if m < 1_000.0 {
        format!("{}m", ((m / 100.0).round() * 100.0).max(100.0) as i64)
    } else if m < 10_000.0 {
        let k = round1(m / 1000.0);
        if (k - k.round()).abs() < 1e-9 { format!("{}km", k as i64) } else { format!("{k}km") }
    } else {
        format!("{}km", (m / 1000.0).round() as i64)
    }
}

/// 받침이 있으면 true (한글이 아니면 false).
fn has_batchim(word: &str) -> bool {
    word.chars().rev().find(|c| !c.is_whitespace() && *c != ')').map(|c| {
        let u = c as u32;
        if (0xAC00..=0xD7A3).contains(&u) { !(u - 0xAC00).is_multiple_of(28) } else { matches!(c, '0' | '1' | '3' | '6' | '7' | '8') }
    }) == Some(true)
}

fn eun(word: &str) -> String {
    format!("{word}{}", if has_batchim(word) { "은" } else { "는" })
}

/// 카드 · 음성에 쓸 고른 결과: 1순위 + (있으면) 다른 선택 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RoadsidePick {
    pub primary: Option<RoadsideHit>,
    pub second: Option<RoadsideHit>,
    /// 읽을 한두 문장 (묻는 말 포함)
    pub line: String,
}

/// 규칙 답: 찾은 곳들(종류별로 모은 것, [order] 순서가 먼저 권할 종류)에서 1순위와 다른 선택을 고르고 문장을 만든다.
/// - 1순위: [order]에서 결과가 있는 첫 종류의 가장 가까운 곳.
/// - 다른 선택: 다른 종류 중 1순위보다 가까운 곳이 있으면 그것, 아니면 같은 종류 다음 곳.
///
/// [can_add_waypoint]: 내비 목적지가 있어 경유지로 넣을 수 있다 (없으면 "안내할까요?").
pub fn pick(order: &[RoadsideCategory], hits: &[RoadsideHit], can_add_waypoint: bool) -> RoadsidePick {
    let primary = order.iter().find_map(|c| hits.iter().filter(|h| h.place.category == *c).min_by(|a, b| a.along_m.total_cmp(&b.along_m))).cloned();
    let Some(p) = primary else {
        let what = order.first().map(|c| c.word()).unwrap_or("들를 곳");
        return RoadsidePick { primary: None, second: None, line: format!("가는 길 앞쪽에서 {} 찾지 못했어요.", eul(what)) };
    };
    // 다른 종류(화장실: 휴게소 다음 졸음쉼터)가 있으면 그 가장 가까운 곳이 다른 선택. 문장에는 1순위보다 가까울 때만 말한다.
    let closer_other = hits
        .iter()
        .filter(|h| h.place.category != p.place.category && order.contains(&h.place.category))
        .min_by(|a, b| a.along_m.total_cmp(&b.along_m));
    let next_same = hits.iter().filter(|h| h.place.category == p.place.category && h.place.name != p.place.name).min_by(|a, b| a.along_m.total_cmp(&b.along_m));
    let second = closer_other.or(next_same).cloned();
    let mut line = format!("다음 {}까지 {}, 약 {}분이에요.", p.place.name, distance_words(p.along_m), p.eta_min);
    if let Some(s) = &second {
        if s.place.category != p.place.category && s.along_m < p.along_m {
            line.push_str(&format!(" {} {} 앞이에요.", eun(s.place.category.word()), distance_words(s.along_m)));
        }
    }
    line.push_str(if can_add_waypoint { " 경유지로 넣을까요?" } else { " 안내할까요?" });
    RoadsidePick { primary: Some(p), second, line }
}

fn eul(word: &str) -> String {
    format!("{word}{}", if has_batchim(word) { "을" } else { "를" })
}

/// LLM 시스템 프롬프트. [context]: 지금 상황 한 덩어리(JSON). [json_protocol]: 네이티브 도구 호출이 없는 서버용 예비 규칙을 붙인다.
pub fn system_prompt(context: &str, driving: bool, json_protocol: bool) -> String {
    let mut s = String::from(
        "너는 차 안의 비서 '어디였지'다. 운전자의 필요(화장실, 졸음, 배고픔, 커피, 주유, 충전, 쉬기)를 알아채고 도구로 경로 앞쪽에서 들를 곳을 찾아 짧게 알려 준다.\n\
         규칙:\n\
         - 숫자(거리·분)는 도구 결과 그대로 쓴다. 지어내지 않는다.\n\
         - 들를 곳을 찾으면 가장 알맞은 곳 하나를 propose_waypoint(목적지가 있을 때) 또는 navigate_to(없을 때)로 제안한다. 직접 실행하지 않는다.\n\
         - 화장실이 급하면 휴게소와 졸음쉼터를 둘 다 본다. 졸리면 가까운 졸음쉼터를 먼저 권한다.\n\
         - 도구는 3번까지만 부른다. 찾지 못하면 솔직히 말한다.\n",
    );
    if driving {
        s.push_str("- 운전 중이다: 답은 소리로 읽는 1~2문장(60자 안팎). 예: \"다음 화성휴게소까지 12km, 약 8분이에요. 졸음쉼터는 4km 앞이에요. 경유지로 넣을까요?\"\n");
    } else {
        s.push_str("- 정차 중이다: 답은 2~3문장.\n");
    }
    if json_protocol {
        s.push_str("- 도구를 부를 땐 다른 말 없이 JSON 한 줄만: {\"tool\": \"이름\", \"args\": {...}}\n");
    }
    s.push_str("[상황]\n");
    s.push_str(context);
    s
}

// ---------------------------------------------------------------------------------------------
// 6. 기록 · LLM 연결
// ---------------------------------------------------------------------------------------------

impl crate::store::EodiStore {
    /// 최근 3시간 위치 기록으로 [motion].
    pub fn roadside_motion_at(&self, now_ms: i64) -> Result<RoadsideMotion, crate::store::CoreError> {
        let conn = self.snapshot()?;
        let mut st = conn.prepare("SELECT t_ms, lat, lon FROM fix WHERE t_ms >= ?1 ORDER BY t_ms")?;
        let fixes = st
            .query_map([now_ms - 3 * 3_600_000], |r| Ok((r.get::<_, i64>(0)?, Point { lat: r.get(1)?, lon: r.get(2)? })))?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(motion(&fixes, now_ms))
    }
}

/// 공급자 중립 도구 정의 → OpenAI 호환 `tools` (Gemini의 OpenAI 호환 주소도 같은 모양을 받는다).
pub fn openai_tools(specs: &Value) -> Value {
    Value::Array(
        specs
            .as_array()
            .into_iter()
            .flatten()
            .map(|t| json!({"type": "function", "function": {"name": t["name"], "description": t["description"], "parameters": t["parameters"]}}))
            .collect(),
    )
}

static CLIENT: std::sync::Mutex<Option<(crate::agent::LlmConfig, std::sync::Arc<crate::agent::llm::LlmClient>)>> = std::sync::Mutex::new(None);

/// 채팅 한 번 (도구 포함, 스트리밍 없음). 요청: `{"messages": [...], "tools": [중립 정의], "max_tokens": n}`.
/// 응답: `{"content": "...", "tool_calls": [{"id","name","arguments"}], "ms": n}`. 같은 설정이면 연결을 다시 쓴다.
pub fn llm_chat(cfg: crate::agent::LlmConfig, request: &str) -> Result<String, crate::store::CoreError> {
    use crate::agent::llm::{LlmClient, Params};
    use crate::store::CoreError;
    if !cfg.is_configured() {
        return Err(CoreError::InvalidInput("LLM 설정 없음".into()));
    }
    let req: Value = serde_json::from_str(request).map_err(|e| CoreError::InvalidInput(format!("요청 JSON: {e}")))?;
    let client = {
        let mut g = CLIENT.lock().unwrap_or_else(|e| e.into_inner());
        match g.as_ref() {
            Some((c, cl)) if *c == cfg => cl.clone(),
            _ => {
                let cl = std::sync::Arc::new(LlmClient::new(cfg.clone()));
                *g = Some((cfg, cl.clone()));
                cl
            }
        }
    };
    let messages: Vec<Value> = req["messages"].as_array().cloned().unwrap_or_default();
    let p = Params { temperature: 0.3, top_p: 0.9, max_tokens: req["max_tokens"].as_u64().unwrap_or(300).clamp(32, 1024) as u32 };
    let mut body = client.body(&messages, &p);
    let tools = openai_tools(&req["tools"]);
    if tools.as_array().is_some_and(|a| !a.is_empty()) {
        body["tools"] = tools;
        body["tool_choice"] = json!("auto");
    }
    let r = client.complete(body).map_err(|e| CoreError::InvalidInput(e.to_string()))?;
    let calls: Vec<Value> = r.tool_calls.iter().map(|c| json!({"id": c.id, "name": c.name, "arguments": c.arguments})).collect();
    Ok(json!({"content": r.content, "tool_calls": calls, "ms": r.total_ms}).to_string())
}

#[cfg(test)]
#[path = "roadside_tests.rs"]
mod tests;

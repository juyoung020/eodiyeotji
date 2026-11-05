//! 근처 드라이브 추천 코스 — "여기 근처 드라이브 추천코스 알려줘" → 인기 코스 상위 k개 → "2번으로 가자" (LLM 없이, 수 ms).
//!
//! - 추천 목록은 커뮤니티에 공유된 코스를 Rust `recommend_drive_courses`(인기 + 거리)로 받고, 여기서는 테마 거르기·말 만들기만 한다.
//! - 목록은 세션에 남는다 → 번호("2번", "두 번째 거"), "어디 들러?", "더 가까운 데", "그거 말고 바다 쪽", "다른 거" 같은 후속 말을
//!   규칙으로 바로 푼다. 규칙에 안 맞는 말은 LLM이 [상황]의 목록을 보고 `courses`·`nav mode=pick` 도구로 이어 간다.
//! - 안내는 언제나 제안만 한다 (운전자 "응"/확인 버튼 뒤에만 `NavAction::SharedCourse`가 나간다).
//! - 코스 순서는 작성자가 짠 순서를 지키고, 지금 위치에서 가까운 쪽 끝부터 달리게 필요하면 뒤집는다. 내비 경유지는 구간마다 2개까지.

use serde_json::{json, Value};

use super::runner::Turn;
use super::tools::{self, km};
use super::words;
use super::{AgentContext, MemoryAgent, MemoryBackend, NavAction, NavProposal, Session};
use crate::community::CourseRecommendation;
use crate::course;
use crate::ffi::GeoPoint;
use crate::geo::{haversine_m, Point};
use crate::memory::{CourseLeg, CourseRoute, StopFact};

/// 근처 반경 기본값 (km)
pub const DEFAULT_RADIUS_KM: f64 = 50.0;
/// 한 번에 말하고 보여 주는 코스 수
pub const SHOW_K: usize = 3;
/// 거르기·"더 가까운 데"용으로 넉넉히 받아 오는 수
const FETCH_K: u32 = 20;

/// 세션에 남는 추천 코스 상태.
#[derive(Debug, Clone, Default)]
pub struct CourseState {
    /// 지금 보여 준 목록 (순서 = 순위)
    pub list: Vec<CourseRecommendation>,
    /// 지금 이야기하는(안내를 제안한) 코스 자리
    pub focus: usize,
    /// 찾은 조건: 테마·지역 낱말, 반경
    pub q: Vec<String>,
    pub radius_km: f64,
}

/// 추천 코스에 대한 말의 뜻.
#[derive(Debug, Clone, PartialEq)]
pub enum CourseIntent {
    /// 새로 추천 ([q]: 테마·지역 낱말, 비면 근처 인기)
    Recommend { q: Vec<String> },
    /// 목록의 [index]번(0부터, None = 지금 고른 코스). [go]면 안내를 원함, 아니면 설명.
    Pick { index: Option<usize>, go: bool },
    /// 지금 고른 코스 설명 ("어디 들러?")
    Describe,
    /// 더 가까운 코스
    Closer,
    /// 테마·지역 바꾸기 ("그거 말고 바다 쪽")
    Theme { q: Vec<String> },
    /// 지금 것 말고 다른 것
    Other,
}

// ---------------------------------------------------------------- 말 알아듣기 (규칙)

/// 코스·갈 곳을 찾는 말 (띄어쓰기 없앤 꼴로 본다)
const COURSE_WORDS: &[&str] = &["드라이브", "코스", "갈만한", "가볼만한", "갈데", "갈곳", "가볼데", "가볼곳", "놀러갈", "바람쐬", "나들이갈"];
/// 추천을 바라는 말
const RECO_WORDS: &[&str] = &[
    "추천", "인기", "알려", "근처", "주변", "여기", "가까운", "좋은", "좋을까", "어디가", "어디갈", "있어", "있을까", "없어", "찾아", "보여", "뭐있",
    "골라", "갈만한", "가볼만한", "놀러갈", "바람쐬", "괜찮은", "유명한", "요즘",
];
/// 지난 기억을 묻는 말 (이러면 추천이 아니라 기억 찾기다)
const PAST_WORDS: &[&str] = &["갔던", "갔었", "다녀온", "다녀왔", "였지", "였더라", "들렀던", "갔을때", "갔다온", "했던", "달렸던", "었지", "었더라", "갔더라"];
/// 안내를 바라는 말
const GO_WORDS: &[&str] = &["가자", "가줘", "안내", "갈래", "출발", "로가", "으로가", "그걸로", "데려다", "가고싶", "가볼래", "갈게", "고를게", "할게", "로할래", "으로할래"];
/// 설명을 바라는 말
const DESCRIBE_WORDS: &[&str] = &[
    "들러", "들르", "들려", "어디어디", "뭐있", "뭐가있", "어때", "자세히", "설명", "어떤곳", "얼마나걸", "몇시간", "몇키로", "멀어", "거리", "코스는",
];
const CLOSER_WORDS: &[&str] = &["가까운", "가까이", "덜먼", "안먼"];
const OTHER_WORDS: &[&str] = &["다른거", "다른코스", "다른데", "딴거", "딴데", "말고", "다음거", "다른곳", "또없", "더없", "다른건"];

/// 테마 동의어 묶음 (장소 사전이 아니라 일반 낱말). 첫 낱말이 대표.
const THEMES: &[&[&str]] = &[
    &["바다", "해변", "해수욕장", "바닷가", "해안", "항구", "섬", "노을"],
    &["산", "등산", "산성", "고개", "계곡", "숲", "단풍"],
    &["카페", "커피"],
    &["맛집", "식당", "밥", "먹방"],
    &["강", "호수", "강변", "물"],
    &["야경", "밤", "전망"],
    &["꽃", "벚꽃", "봄", "꽃길"],
];

/// 추천 말에서 지역·테마가 아닌 군말
const FILLER: &[&str] = &[
    "드라이브", "코스", "추천", "인기", "여기", "근처", "주변", "근방", "만한", "만한데", "갈", "가볼", "놀러", "바람", "쐬러", "좋은", "좋을까", "안내", "데",
    "곳", "거", "있어", "있을까", "알려", "보여", "찾아", "골라", "요즘", "주말", "오늘", "지금", "가까운", "괜찮은", "할", "하기", "쪽", "가고", "싶어",
    "싶은데", "어디", "제일", "가장", "많은", "있는", "가기", "가면", "갈래", "유명한", "곳은", "데는", "건", "것", "코스는", "코스로", "인기있는", "인기많은",
    "더", "번", "번째", "말고", "없어", "없을까", "없니", "없지", "나갈", "만한곳", "곳이", "다른", "그거", "그럼", "보러", "같은", "어때", "어떤", "가요", "갈까", "가볼까", "있나", "있니", "없나", "뭐", "좀",
];

fn compact(u: &str) -> String {
    u.chars().filter(|c| !c.is_whitespace() && !"?!.,~".contains(*c)).collect::<String>().to_lowercase()
}

fn any(c: &str, set: &[&str]) -> bool {
    set.iter().any(|w| c.contains(w))
}

/// 지난 기억을 묻는 말인가 ("갔던", "어디였지").
pub fn is_past(u: &str) -> bool {
    any(&compact(u), PAST_WORDS)
}

/// 말의 뜻. [in_topic]: 바로 앞이 추천 코스 이야기였고 목록이 [list_len]개 있음. 추천 코스 말이 아니면 None.
pub fn detect(u: &str, in_topic: bool, list_len: usize) -> Option<CourseIntent> {
    let c = compact(u);
    if any(&c, PAST_WORDS) {
        return None;
    }
    let has_list = in_topic && list_len > 0;
    // 번호는 목록이 있을 때만 (기억 이야기 중의 "2번"은 코스 번호가 아니다)
    if has_list {
        if let Some(index) = ordinal(&c, list_len) {
            return Some(CourseIntent::Pick { index: Some(index), go: any(&c, GO_WORDS) });
        }
    }
    if any(&c, COURSE_WORDS) && any(&c, RECO_WORDS) {
        return Some(CourseIntent::Recommend { q: place_terms(u) });
    }
    if !has_list {
        return None;
    }
    if any(&c, CLOSER_WORDS) {
        return Some(CourseIntent::Closer);
    }
    let q = place_terms(u);
    let other = any(&c, OTHER_WORDS);
    let themed = q.iter().any(|w| theme_of(w).is_some());
    if !q.is_empty() && (other || themed || c.contains("쪽")) {
        return Some(CourseIntent::Theme { q });
    }
    if other {
        return Some(CourseIntent::Other);
    }
    if any(&c, DESCRIBE_WORDS) {
        return Some(CourseIntent::Describe);
    }
    let refers = ["그거", "그코스", "거기", "이거", "이코스", "그걸"].iter().any(|w| c.starts_with(w));
    (refers && any(&c, GO_WORDS)).then_some(CourseIntent::Pick { index: None, go: true })
}

/// "2번", "1위", "두 번째", "둘째", "마지막" → 자리 (0부터). 목록 밖 번호도 돌려준다(없다고 답하게).
pub fn ordinal(c: &str, len: usize) -> Option<usize> {
    let chars: Vec<char> = c.chars().collect();
    for (i, ch) in chars.iter().enumerate() {
        if let Some(d) = ch.to_digit(10) {
            let prev_digit = i > 0 && chars[i - 1].is_ascii_digit();
            let next = chars.get(i + 1).copied();
            if !prev_digit && (1..=9).contains(&d) && matches!(next, Some('번') | Some('위')) && chars.get(i + 2) != Some(&'지') {
                return Some(d as usize - 1);
            }
        }
    }
    const WORDS: &[(&[&str], usize)] = &[
        (&["첫번째", "첫째", "첫코스", "처음거", "처음꺼", "처음코스", "맨앞", "일번"], 0),
        (&["두번째", "둘째", "이번째", "두번코스"], 1),
        (&["세번째", "셋째", "삼번"], 2),
        (&["네번째", "넷째", "사번"], 3),
        (&["다섯번째", "다섯째", "오번"], 4),
    ];
    for (ws, n) in WORDS {
        if ws.iter().any(|w| c.contains(w)) {
            return Some(*n);
        }
    }
    (c.contains("마지막") && len > 0).then(|| len - 1)
}

/// 추천 말에 들어 있는 지역·테마 낱말 (예: "강릉 쪽 드라이브 코스" → [강릉], "바다 보러 가기 좋은 코스" → [바다]).
pub fn place_terms(u: &str) -> Vec<String> {
    let mut out: Vec<String> = vec![];
    for w in words::parse(u).terms {
        let w = w.strip_suffix("코스").unwrap_or(&w).to_string();
        let w = w.strip_suffix("쪽").unwrap_or(&w).to_string();
        if w.is_empty()
            || FILLER.contains(&w.as_str())
            || ["드라이브", "추천", "인기", "만한", "가볼", "놀러", "번째"].iter().any(|p| w.contains(p))
            || w.chars().all(|c| c.is_ascii_digit() || c == '번')
            || (w.chars().count() < 2 && theme_of(&w).is_none())
            || (theme_of(&w).is_none() && ["한데", "는데", "은데", "고", "며", "지만"].iter().any(|e| w.ends_with(e)))
        {
            continue;
        }
        if !out.contains(&w) {
            out.push(w);
        }
    }
    out.truncate(3);
    out
}

/// 낱말이 속한 테마 묶음.
fn theme_of(w: &str) -> Option<&'static [&'static str]> {
    THEMES.iter().copied().find(|g| g.contains(&w))
}

/// 코스가 낱말 중 하나에 맞나 (동의어 포함). 한 글자 낱말(산·강·밤)은 태그·분류·제목 낱말과 똑같을 때만.
pub fn matches(p: &crate::community::CoursePost, q: &[String]) -> bool {
    if q.is_empty() {
        return true;
    }
    let long_fields: Vec<&str> = [p.title.as_str(), p.body.as_str(), p.region.as_str()]
        .into_iter()
        .chain(p.tags.iter().map(String::as_str))
        .chain(p.stops.iter().flat_map(|s| [s.name.as_str(), s.category.as_str()]))
        .collect();
    let exact: Vec<&str> = p.tags.iter().map(String::as_str)
        .chain(p.stops.iter().map(|s| s.category.as_str()))
        .chain(p.title.split_whitespace())
        .collect();
    q.iter().any(|w| {
        let group: Vec<&str> = theme_of(w).map(|g| g.to_vec()).unwrap_or_else(|| vec![w.as_str()]);
        group.iter().any(|g| if g.chars().count() < 2 { exact.contains(g) } else { long_fields.iter().any(|f| f.contains(g)) })
    })
}

// ---------------------------------------------------------------- 찾기

/// 찾은 결과와 어떻게 찾았는지.
#[derive(Debug, Clone, Default)]
pub struct Fetched {
    pub list: Vec<CourseRecommendation>,
    /// 지금 위치를 몰라 전국에서 골랐다
    pub no_location: bool,
    /// 근처에 없어 전국으로 넓혔다
    pub widened: bool,
    /// 낱말에 맞는 코스가 없어 낱말 없이 골랐다
    pub theme_missed: bool,
}

/// 추천 코스 찾기: 근처(인기+거리)에서 낱말 거르기 → 없으면 전국에서 → 그래도 없으면 낱말 없이. [exclude]의 코스는 뺀다. 최대 [k]개.
pub fn fetch(b: &dyn MemoryBackend, loc: Option<GeoPoint>, q: &[String], radius_km: f64, k: usize, exclude: &[String]) -> Fetched {
    let keep = |v: &[CourseRecommendation], q: &[String]| -> Vec<CourseRecommendation> {
        v.iter().filter(|r| !exclude.contains(&r.post.id) && matches(&r.post, q)).cloned().collect()
    };
    let near = loc.map(|p| b.recommend_drive_courses(Some(p), radius_km, FETCH_K).unwrap_or_default()).unwrap_or_default();
    // 전국 인기순은 필요할 때만 받는다. 거리는 여기서 잰다 (지금 위치~코스 첫 곳).
    let nationwide = || {
        let mut all = b.recommend_drive_courses(None, 0.0, FETCH_K * 3).unwrap_or_default();
        if let Some(p) = loc {
            for r in &mut all {
                r.distance_km = distance_km(p, &r.post.stops);
            }
        }
        all
    };
    let mut f = Fetched { no_location: loc.is_none(), ..Default::default() };
    let mut list = keep(&near, q);
    let mut all: Option<Vec<CourseRecommendation>> = None;
    if list.is_empty() {
        let a = nationwide();
        list = keep(&a, q);
        f.widened = loc.is_some() && !list.is_empty();
        all = Some(a);
    }
    if list.is_empty() && !q.is_empty() {
        f.theme_missed = true;
        list = keep(&near, &[]);
        if list.is_empty() {
            list = keep(&all.unwrap_or_else(nationwide), &[]);
            f.widened = loc.is_some() && !list.is_empty();
        }
    }
    list.truncate(k);
    f.list = list;
    f
}

fn distance_km(from: GeoPoint, stops: &[StopFact]) -> f64 {
    stops.first().map(|s| haversine_m(Point::from(from), Point { lat: s.lat, lon: s.lon }) / 1000.0).unwrap_or(-1.0)
}

// ---------------------------------------------------------------- 내비 경로

/// 공유 코스의 내비 경로: 작성자 순서를 지키되, 지금 위치에서 가까운 쪽 끝부터 (뒤집은 쪽이 더 짧으면 뒤집는다).
/// 경유지는 구간마다 2개까지 (`course::split_legs`).
pub fn shared_route(stops: &[StopFact], from: Option<GeoPoint>) -> CourseRoute {
    let pts: Vec<Point> = stops.iter().map(|s| Point { lat: s.lat, lon: s.lon }).collect();
    let origin = from.map(Point::from);
    let fwd: Vec<usize> = (0..pts.len()).collect();
    let rev: Vec<usize> = fwd.iter().rev().copied().collect();
    let order = if origin.is_some() && course::path_length_m(origin, &pts, &rev) + 1.0 < course::path_length_m(origin, &pts, &fwd) { rev } else { fwd };
    let distance_m = course::path_length_m(origin, &pts, &order);
    let ordered: Vec<StopFact> = order.iter().map(|&i| stops[i].clone()).collect();
    let ordered_pts: Vec<Point> = order.iter().map(|&i| pts[i]).collect();
    let mut it = ordered.iter().cloned();
    let legs = course::split_legs(&ordered_pts)
        .into_iter()
        .map(|l| {
            let waypoints: Vec<StopFact> = it.by_ref().take(l.waypoints.len()).collect();
            CourseLeg { waypoints, destination: it.next().expect("split_legs keeps every stop") }
        })
        .collect();
    CourseRoute { stops: ordered, legs, distance_m }
}

/// 공유 코스 안내 제안. 들른 곳이 없으면 None.
pub fn shared_proposal(seq: &mut u64, r: &CourseRecommendation, from: Option<GeoPoint>) -> Option<NavProposal> {
    if r.post.stops.is_empty() {
        return None;
    }
    let route = shared_route(&r.post.stops, from);
    let names = route.stops.iter().map(|s| s.name.as_str()).collect::<Vec<_>>().join(" → ");
    let split = if route.legs.len() > 1 { format!(" ({}구간으로 나눠 안내)", route.legs.len()) } else { String::new() };
    Some(NavProposal {
        id: tools::next_id(seq),
        summary: format!("'{}' 코스 안내: {names}{split}", r.post.title),
        action: NavAction::SharedCourse { post_id: r.post.id.clone(), title: r.post.title.clone(), legs: route.legs },
    })
}

// ---------------------------------------------------------------- 말 만들기

/// 받침이 있나 (한글 마지막 글자, 숫자는 읽는 소리로)
fn batchim(w: &str) -> Option<u32> {
    let c = w.trim_end_matches(['\'', '"', ')', ' ']).chars().last()?;
    if ('가'..='힣').contains(&c) {
        return Some((c as u32 - '가' as u32) % 28);
    }
    c.to_digit(10).map(|d| [1, 8, 0, 1, 0, 0, 1, 8, 1, 0][d as usize]) // 영·일·이·삼·사·오·육·칠·팔·구
}

/// 이에요/예요
fn eyo(w: &str) -> &'static str {
    if batchim(w).is_some_and(|b| b != 0) { "이에요" } else { "예요" }
}

/// 으로/로 (ㄹ 받침은 로)
fn ro(w: &str) -> &'static str {
    if batchim(w).is_some_and(|b| b != 0 && b != 8) { "으로" } else { "로" }
}

/// 은/는
fn eun(w: &str) -> &'static str {
    if batchim(w).is_some_and(|b| b != 0) { "은" } else { "는" }
}

/// 18km / 3.2km
pub fn km_text(d: f64) -> String {
    if d < 10.0 { format!("{:.1}km", d.max(0.1)) } else { format!("{}km", d.round() as i64) }
}

fn duration_text(ms: i64) -> String {
    let min = (ms / 60_000).max(1);
    match (min / 60, min % 60) {
        (0, m) => format!("{m}분"),
        (h, 0) => format!("{h}시간"),
        (h, m) => format!("{h}시간 {m}분"),
    }
}

fn quoted(r: &CourseRecommendation) -> String {
    format!("'{}'", r.post.title)
}

/// "1번 '양평 두물머리 한 바퀴'(♥ 312, 여기서 18km)"
fn list_item(i: usize, r: &CourseRecommendation) -> String {
    let d = if r.distance_km >= 0.0 { format!(", {}{}", if i == 0 { "여기서 " } else { "" }, km_text(r.distance_km)) } else { String::new() };
    format!("{}번 {}(♥ {}{d})", i + 1, quoted(r), r.post.hearts)
}

fn stop_names(r: &CourseRecommendation, n: usize) -> String {
    r.post.stops.iter().take(n).map(|s| s.name.as_str()).filter(|s| !s.is_empty()).collect::<Vec<_>>().join(", ")
}

/// 앞말 범위: "근처 " / "전국 "
fn scope(f: &Fetched) -> &'static str {
    if f.no_location || f.widened { "전국 " } else { "근처 " }
}

/// 목록 답. [lead]: 앞말 (예: "근처 인기 코스", "더 가까운 코스").
fn list_answer(f: &Fetched, lead: &str, q: &[String], radius_km: f64, driving: bool) -> String {
    let pre = if f.no_location {
        "지금 위치를 몰라서 ".to_string()
    } else if f.theme_missed {
        format!("'{}' 코스는 아직 없어서 대신 ", q.join("·"))
    } else if f.widened {
        format!("근처 {}km 안에는 없어서 ", radius_km.round() as i64)
    } else {
        String::new()
    };
    let top = &f.list[0];
    let can_go = !top.post.stops.is_empty();
    if driving {
        let d = if top.distance_km >= 0.0 { format!(" 여기서 {}, ♥ {}.", km_text(top.distance_km), top.post.hearts) } else { format!(" ♥ {}.", top.post.hearts) };
        let ask = if can_go { " 안내할까요?" } else { "" };
        return format!("{pre}{lead} 1위는 {}{}.{d}{ask}", quoted(top), eyo(&top.post.title));
    }
    let items = f.list.iter().enumerate().map(|(i, r)| list_item(i, r)).collect::<Vec<_>>().join(", ");
    let ask = if can_go { " 1번으로 안내할까요?" } else { "" };
    format!("{pre}{lead} {}개예요. {items}.{ask}", f.list.len())
}

/// 한 코스 이야기 (번호로 골랐을 때).
fn one_answer(i: usize, r: &CourseRecommendation, go: bool, driving: bool) -> String {
    let t = quoted(r);
    let can_go = !r.post.stops.is_empty();
    if !can_go {
        return format!("{}번 {t}{} 들른 곳 정보가 없어 안내할 수 없어요.", i + 1, eun(&r.post.title));
    }
    if go {
        return format!("{}번 {t}{} 안내할까요?", i + 1, ro(&r.post.title));
    }
    if driving {
        return format!("{}번은 {}에 들러요. 안내할까요?", i + 1, stop_names(r, 2));
    }
    let d = if r.distance_km >= 0.0 { format!(" 여기서 {}, ", km_text(r.distance_km)) } else { " ".into() };
    let when = when_phrase(&r.post).map(|w| format!(" {w}")).unwrap_or_default();
    format!(
        "{}번 {t}{} {}에 들러요.{when}{d}전체 {}, {}쯤 걸려요. 안내할까요?",
        i + 1, eun(&r.post.title), stop_names(r, 4), km_text(r.post.distance_m / 1000.0), duration_text(r.post.duration_ms)
    )
}

/// 언제 다녀온 코스인지 한마디 (예: "작년 가을 단풍철에 다녀온 코스예요."). 값이 없으면 None.
fn when_phrase(p: &crate::community::CoursePost) -> Option<String> {
    if p.season.trim().is_empty() {
        return None;
    }
    let season = p.season.replace(" · ", " ");
    Some(match p.weather.as_deref().filter(|w| !w.trim().is_empty()) {
        Some(w) => format!("{season}, {}에 다녀온 코스예요.", w.split(" · ").next().unwrap_or(w)),
        None => format!("{season}에 다녀온 코스예요."),
    })
}

impl MemoryAgent {
    /// 추천 코스 말 처리 (LLM 없이). 처리했으면 true. 답마다 목록을 화면에 다시 보낸다 (새 질문 카드가 목록을 잃지 않게).
    pub(crate) fn course_fast_path(&self, s: &mut Session, t: &mut Turn, intent: CourseIntent, ctx: &AgentContext) -> bool {
        self.course_reply(s, t, intent, ctx);
        if !t.course_shown && !s.courses.list.is_empty() {
            t.courses(&s.courses);
        }
        true
    }

    fn course_reply(&self, s: &mut Session, t: &mut Turn, intent: CourseIntent, ctx: &AgentContext) {
        let loc = ctx.current_location;
        let driving = ctx.is_driving;
        match intent {
            CourseIntent::Recommend { q } => {
                let f = fetch(self.backend.as_ref(), loc, &q, DEFAULT_RADIUS_KM, SHOW_K, &[]);
                if f.list.is_empty() {
                    s.courses = CourseState::default();
                    t.say(if q.is_empty() { "아직 공유된 드라이브 코스가 없어요." } else { "그쪽으로 공유된 드라이브 코스가 아직 없어요." });
                    return;
                }
                let what = if q.is_empty() || f.theme_missed { "인기 코스".to_string() } else { format!("'{}' 인기 코스", q.join("·")) };
                let lead = format!("{}{what}", scope(&f));
                let text = list_answer(&f, &lead, &q, DEFAULT_RADIUS_KM, driving);
                self.show_courses(s, t, CourseState { list: f.list, focus: 0, q, radius_km: DEFAULT_RADIUS_KM });
                t.say(&text);
                self.propose_focus(s, t, loc);
            }
            CourseIntent::Pick { index, go } => {
                let index = index.unwrap_or(s.courses.focus);
                let Some(r) = s.courses.list.get(index).cloned() else {
                    t.say(&format!("목록에는 {}번까지 있어요. 몇 번으로 할까요?", s.courses.list.len()));
                    return;
                };
                s.courses.focus = index;
                let st = s.courses.clone();
                self.show_courses(s, t, st);
                t.say(&one_answer(index, &r, go, driving));
                self.propose_focus(s, t, loc);
            }
            CourseIntent::Describe => {
                let i = s.courses.focus;
                let r = s.courses.list[i.min(s.courses.list.len() - 1)].clone();
                t.say(&one_answer(i, &r, false, driving));
                self.propose_focus(s, t, loc);
            }
            CourseIntent::Closer => {
                let cur = s.courses.list[s.courses.focus.min(s.courses.list.len() - 1)].clone();
                if loc.is_none() || cur.distance_km < 0.0 {
                    t.say("지금 위치를 몰라서 거리를 비교할 수 없어요.");
                    return;
                }
                let mut f = fetch(self.backend.as_ref(), loc, &s.courses.q, s.courses.radius_km, FETCH_K as usize, &[]);
                f.list.retain(|r| r.distance_km >= 0.0 && r.distance_km + 0.5 < cur.distance_km);
                f.list.sort_by(|a, b| a.distance_km.total_cmp(&b.distance_km));
                f.list.truncate(SHOW_K);
                if f.list.is_empty() {
                    t.say(&format!("근처에서는 {}{} 가장 가까운 추천 코스예요. 안내할까요?", quoted(&cur), if batchim(&cur.post.title).is_some_and(|b| b != 0) { "이" } else { "가" }));
                    self.propose_focus(s, t, loc);
                    return;
                }
                let f = Fetched { widened: false, theme_missed: false, ..f };
                let text = list_answer(&f, "더 가까운 코스", &[], s.courses.radius_km, driving);
                let q = s.courses.q.clone();
                let radius = s.courses.radius_km;
                self.show_courses(s, t, CourseState { list: f.list, focus: 0, q, radius_km: radius });
                t.say(&text);
                self.propose_focus(s, t, loc);
            }
            CourseIntent::Theme { q } => {
                let exclude: Vec<String> = s.courses.list.get(s.courses.focus).map(|r| vec![r.post.id.clone()]).unwrap_or_default();
                let f = fetch(self.backend.as_ref(), loc, &q, s.courses.radius_km.max(DEFAULT_RADIUS_KM), SHOW_K, &exclude);
                if f.list.is_empty() || f.theme_missed {
                    t.say(&format!("'{}' 쪽 추천 코스는 아직 없어요. 지금 목록에서 골라 주세요.", q.join("·")));
                    return;
                }
                let lead = format!("{}'{}' 코스", scope(&f), q.join("·"));
                let text = list_answer(&f, &lead, &q, DEFAULT_RADIUS_KM, driving);
                self.show_courses(s, t, CourseState { list: f.list, focus: 0, q, radius_km: DEFAULT_RADIUS_KM });
                t.say(&text);
                self.propose_focus(s, t, loc);
            }
            CourseIntent::Other => {
                let next = s.courses.focus + 1;
                if let Some(r) = s.courses.list.get(next).cloned() {
                    s.courses.focus = next;
                    let st = s.courses.clone();
                    self.show_courses(s, t, st);
                    let d = if r.distance_km >= 0.0 { format!(", 여기서 {}", km_text(r.distance_km)) } else { String::new() };
                    t.say(&format!("그럼 {}번 {}{} 어떠세요? ♥ {}{d}예요. 안내할까요?", next + 1, quoted(&r), eun(&r.post.title), r.post.hearts));
                    self.propose_focus(s, t, loc);
                    return;
                }
                let shown: Vec<String> = s.courses.list.iter().map(|r| r.post.id.clone()).collect();
                let f = fetch(self.backend.as_ref(), loc, &s.courses.q, s.courses.radius_km, SHOW_K, &shown);
                if f.list.is_empty() || f.theme_missed {
                    t.say("추천할 코스는 이게 다예요. 바다·산·카페처럼 테마를 말씀해 주시면 다시 찾아 볼게요.");
                    return;
                }
                let text = list_answer(&f, "다른 인기 코스", &[], s.courses.radius_km, driving);
                let q = s.courses.q.clone();
                let radius = s.courses.radius_km;
                self.show_courses(s, t, CourseState { list: f.list, focus: 0, q, radius_km: radius });
                t.say(&text);
                self.propose_focus(s, t, loc);
            }
        }
    }

    /// 목록을 세션에 넣고 화면에 보낸다.
    fn show_courses(&self, s: &mut Session, t: &mut Turn, st: CourseState) {
        s.courses = st;
        t.courses(&s.courses);
    }

    /// 추천 코스 이야기 중 LLM이 답한 뒤의 안전망 (LLM이 스스로 nav를 불렀으면 아무것도 하지 않는다):
    /// - 목록의 코스 하나를 말하며 "안내…"를 물었는데 nav를 부르지 않았으면 그 코스로 제안을 만든다 (작은 모델에서 자주 나온다).
    /// - 그 밖에는 대기 중인 코스 제안을 다시 보낸다 (새 질문 화면에도 확인 버튼이 있게).
    ///
    /// 어느 쪽이든 제안일 뿐이고 실행은 여전히 운전자 확인 뒤다.
    pub(crate) fn course_proposal_from_answer(&self, s: &mut Session, t: &mut Turn, ctx: &AgentContext, pending_before: Option<String>) {
        if s.courses.list.is_empty() {
            return;
        }
        if !t.course_shown {
            t.courses(&s.courses); // 이야기가 이어지는 동안 화면이 목록을 잃지 않게
        }
        let pending_now = self.pending.lock().unwrap_or_else(|e| e.into_inner()).clone();
        if pending_now.as_ref().map(|p| p.id.clone()) != pending_before {
            return;
        }
        let pending_post = match pending_now.as_ref().map(|p| &p.action) {
            Some(NavAction::SharedCourse { post_id, .. }) => Some(post_id.clone()),
            _ => None,
        };
        let named: Vec<usize> = s.courses.list.iter().enumerate().filter(|(_, r)| t.answer_text().contains(r.post.title.as_str())).map(|(i, _)| i).collect();
        if let ([i], true) = (named.as_slice(), t.answer_text().contains("안내")) {
            s.courses.focus = *i;
            t.courses(&s.courses);
            if pending_post.as_deref() != Some(s.courses.list[*i].post.id.as_str()) {
                return self.propose_focus(s, t, ctx.current_location);
            }
        }
        if let (Some(p), Some(_)) = (pending_now, pending_post) {
            t.l.on_action_proposal(p);
        }
    }

    /// 지금 고른 코스로 안내 제안 (대기 제안을 바꾼다).
    fn propose_focus(&self, s: &mut Session, t: &mut Turn, loc: Option<GeoPoint>) {
        let Some(r) = s.courses.list.get(s.courses.focus) else { return };
        if let Some(p) = shared_proposal(&mut s.proposal_seq, r, loc) {
            *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
            t.l.on_action_proposal(p);
        }
    }
}

// ---------------------------------------------------------------- LLM 도구 결과 (작게)

pub fn course_json(i: usize, r: &CourseRecommendation) -> Value {
    json!({
        "n": i + 1, "title": r.post.title, "by": r.post.author, "hearts": r.post.hearts,
        "km_from_here": (r.distance_km >= 0.0).then(|| (r.distance_km * 10.0).round() / 10.0),
        "region": r.post.region,
        "stops": r.post.stops.iter().take(5).map(|s| s.name.clone()).collect::<Vec<_>>(),
        "course_km": km(r.post.distance_m),
    })
}

pub fn list_json(st: &CourseState) -> Value {
    Value::Array(st.list.iter().enumerate().map(|(i, r)| course_json(i, r)).collect())
}

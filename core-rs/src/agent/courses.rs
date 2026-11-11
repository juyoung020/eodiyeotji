//! 근처 드라이브 추천 — "여기 근처 드라이브 추천코스 알려줘" → 인기 드라이브 길 상위 k개 → "2번으로 가자" (LLM 없이, 수 ms).
//!
//! - 드라이브 코스는 들를 곳 없는 경치 길 하나다 (강변북로 한강 야경, 인천대교 …, `community::drive`).
//!   추천 목록은 Rust `recommend_drive_routes`(인기 + 지금 위치에서 길 위 가장 가까운 점까지 거리)로 받고,
//!   여기서는 테마 거르기·말 만들기만 한다.
//! - 목록은 세션에 남는다 → 번호("2번", "두 번째 거"), "어디 들러?", "더 가까운 데", "그거 말고 바다 쪽", "다른 거" 같은 후속 말을
//!   규칙으로 바로 푼다. 규칙에 안 맞는 말은 LLM이 [상황]의 목록을 보고 `courses`·`nav mode=pick` 도구로 이어 간다.
//! - 안내는 언제나 제안만 한다 (운전자 "응"/확인 버튼 뒤에만 `NavAction::SharedCourse`가 나간다).
//! - 안내는 경유지 없는 두 구간: 길 시작점까지 → 도착하면 끝점까지 (`community::drive::route_legs`).

use serde_json::{json, Value};

use super::runner::Turn;
use super::tools::{self, km};
use super::words;
use super::{AgentContext, MemoryAgent, MemoryBackend, NavAction, NavProposal, Session};
use crate::community::drive::{route_legs, BestTime, DriveRoutePost, DriveRouteRecommendation};
use crate::ffi::GeoPoint;

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
    pub list: Vec<DriveRouteRecommendation>,
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
    "들러", "들르", "들려", "어디어디", "뭐있", "뭐가있", "어때", "자세히", "설명", "어떤곳", "어떤길", "얼마나걸", "몇시간", "몇키로", "멀어", "거리", "코스는",
    "뭐보여", "뭐가보여", "보여줘",
];
const CLOSER_WORDS: &[&str] = &["가까운", "가까이", "덜먼", "안먼"];
pub(crate) const OTHER_WORDS: &[&str] = &["다른거", "다른코스", "다른데", "딴거", "딴데", "말고", "다음거", "다른곳", "또없", "더없", "다른건"];

/// 테마 동의어 묶음 (장소 사전이 아니라 일반 낱말). 첫 낱말이 대표.
const THEMES: &[&[&str]] = &[
    &["바다", "해변", "해수욕장", "바닷가", "바닷길", "해안", "항구", "섬", "방조제"],
    &["노을", "석양", "일몰", "해질녘"],
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

pub(crate) fn compact(u: &str) -> String {
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
    // "2번", "12번" (후보가 많으면 두 자리도), "1위". "2번지"는 주소라 뺀다.
    let chars: Vec<char> = c.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        if chars[i].is_ascii_digit() {
            let start = i;
            while i < chars.len() && chars[i].is_ascii_digit() {
                i += 1;
            }
            let n: usize = chars[start..i].iter().collect::<String>().parse().unwrap_or(0);
            if (1..=99).contains(&n) && matches!(chars.get(i), Some('번') | Some('위')) && chars.get(i + 1) != Some(&'지') {
                return Some(n - 1);
            }
        } else {
            i += 1;
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

/// 길이 낱말 중 하나에 맞나 (동의어 포함). 한 글자 낱말(산·강·밤)은 언제·제목 낱말과 똑같을 때만.
pub fn matches(p: &DriveRoutePost, q: &[String]) -> bool {
    if q.is_empty() {
        return true;
    }
    let long_fields: Vec<&str> = [p.title.as_str(), p.body.as_str(), p.region.as_str(), p.start_name.as_str(), p.end_name.as_str(), p.best_time.label()]
        .into_iter()
        .chain(p.highlights.iter().map(|h| h.name.as_str()))
        .collect();
    let exact: Vec<&str> = std::iter::once(p.best_time.label()).chain(p.title.split_whitespace()).collect();
    q.iter().any(|w| {
        let group: Vec<&str> = theme_of(w).map(|g| g.to_vec()).unwrap_or_else(|| vec![w.as_str()]);
        group.iter().any(|g| if g.chars().count() < 2 { exact.contains(g) } else { long_fields.iter().any(|f| f.contains(g)) })
    })
}

// ---------------------------------------------------------------- 찾기

/// 찾은 결과와 어떻게 찾았는지.
#[derive(Debug, Clone, Default)]
pub struct Fetched {
    pub list: Vec<DriveRouteRecommendation>,
    /// 지금 위치를 몰라 전국에서 골랐다
    pub no_location: bool,
    /// 근처에 없어 전국으로 넓혔다
    pub widened: bool,
    /// 낱말에 맞는 길이 없어 낱말 없이 골랐다
    pub theme_missed: bool,
}

/// 추천 길 찾기: 근처(인기+거리)에서 낱말 거르기 → 없으면 전국 인기순에서 → 그래도 없으면 낱말 없이. [exclude]의 길은 뺀다. 최대 [k]개.
pub fn fetch(b: &dyn MemoryBackend, loc: Option<GeoPoint>, q: &[String], radius_km: f64, k: usize, exclude: &[String]) -> Fetched {
    let keep = |v: &[DriveRouteRecommendation], q: &[String]| -> Vec<DriveRouteRecommendation> {
        v.iter().filter(|r| !exclude.contains(&r.route.id) && matches(&r.route, q)).cloned().collect()
    };
    let near = loc.map(|p| b.recommend_drive_routes(p, radius_km, FETCH_K).unwrap_or_default()).unwrap_or_default();
    // 전국 인기순은 필요할 때만 받는다 (위치를 알면 거리도 같이 온다: 길 위 가장 가까운 점까지)
    let nationwide = || b.popular_drive_routes(loc, FETCH_K * 3).unwrap_or_default();
    let mut f = Fetched { no_location: loc.is_none(), ..Default::default() };
    let mut list = keep(&near, q);
    let mut all: Option<Vec<DriveRouteRecommendation>> = None;
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

// ---------------------------------------------------------------- 안내 제안

/// 드라이브 길 안내 제안: 시작점까지 → 도착하면 끝점까지 (경유지 없는 두 구간).
pub fn shared_proposal(seq: &mut u64, r: &DriveRouteRecommendation) -> NavProposal {
    let p = &r.route;
    NavProposal {
        id: tools::next_id(seq),
        summary: format!("‘{}’ 안내: {} → {} (시작점에 닿으면 끝까지 이어서 안내)", p.title, p.start_name, p.end_name),
        action: NavAction::SharedCourse { post_id: p.id.clone(), title: p.title.clone(), legs: route_legs(p) },
    }
}

// ---------------------------------------------------------------- 말 만들기

/// 받침이 있나 (한글 마지막 글자, 숫자는 읽는 소리로)
fn batchim(w: &str) -> Option<u32> {
    let c = w.trim_end_matches(['\'', '’', '"', ')', ' ']).chars().last()?;
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

/// 이/가
fn iga(w: &str) -> &'static str {
    if batchim(w).is_some_and(|b| b != 0) { "이" } else { "가" }
}

/// 18km / 3.2km
pub fn km_text(d: f64) -> String {
    if d < 10.0 { format!("{:.1}km", d.max(0.1)) } else { format!("{}km", d.round() as i64) }
}

/// "여기서 364m" / "여기서 2.3km": 1km 안은 m로 — 코스 카드("여기서 364m")와 같은 값이 말에도 나오게 ("0.4km"로 말하면 어긋나 보였다).
pub(super) fn here_text(d: f64) -> String {
    if d < 1.0 { format!("{}m", (d.max(0.0) * 1000.0).round() as i64) } else { km_text(d) }
}

fn duration_text(ms: i64) -> String {
    let min = ((ms + 30_000) / 60_000).max(1);
    match (min / 60, min % 60) {
        (0, m) => format!("{m}분"),
        (h, 0) => format!("{h}시간"),
        (h, m) => format!("{h}시간 {m}분"),
    }
}

fn quoted(r: &DriveRouteRecommendation) -> String {
    format!("‘{}’", r.route.title)
}

/// "22km·25분"
fn stats(r: &DriveRouteRecommendation) -> String {
    format!("{}·{}", km_text(r.route.distance_m / 1000.0), duration_text(r.route.duration_ms))
}

/// 언제 가면 좋은지 한마디 ("밤에", "해 질 녘에").
fn when_word(b: BestTime) -> &'static str {
    match b {
        BestTime::Night => "밤에",
        BestTime::Sunset => "해 질 녘에",
        BestTime::Morning => "아침에",
        BestTime::Day => "낮에",
        BestTime::Foliage => "단풍철에",
        BestTime::Blossom => "벚꽃철에",
    }
}

fn sights(r: &DriveRouteRecommendation, n: usize) -> String {
    r.route.highlights.iter().take(n).map(|h| h.name.as_str()).filter(|s| !s.is_empty()).collect::<Vec<_>>().join(", ")
}

/// 앞말 범위: "근처 " / "전국 "
fn scope(f: &Fetched) -> &'static str {
    if f.no_location || f.widened { "전국 " } else { "근처 " }
}

/// 목록 답. [lead]: 앞말 (예: "근처 인기 드라이브 길", "더 가까운 드라이브 길").
fn list_answer(f: &Fetched, lead: &str, q: &[String], radius_km: f64, driving: bool) -> String {
    let pre = if f.no_location {
        "지금 위치를 몰라서 ".to_string()
    } else if f.theme_missed {
        format!("‘{}’ 길은 아직 없어서 대신 ", q.join("·"))
    } else if f.widened {
        format!("근처 {}km 안에는 없어서 ", radius_km.round() as i64)
    } else {
        String::new()
    };
    let top = &f.list[0];
    if driving {
        let d = if top.distance_km >= 0.0 { format!(" 여기서 {}, 하트 {}개예요.", here_text(top.distance_km), top.route.hearts) } else { format!(" 하트 {}개예요.", top.route.hearts) };
        return format!("{pre}{lead} 1위는 {}{}.{d} 안내할까요?", quoted(top), eyo(&top.route.title));
    }
    // 목록은 화면 카드가 보여 준다 → 말은 가장 인기 있는 길 하나만 짧게 (번호·괄호를 늘어놓지 않는다).
    let d = if top.distance_km >= 0.0 { format!(" 여기서 {}, {} 코스예요.", here_text(top.distance_km), stats(top)) } else { format!(" {} 코스예요.", stats(top)) };
    if f.list.len() == 1 {
        return format!("{pre}{lead}{} {} 하나예요.{d} 안내할까요?", eun(lead), quoted(top));
    }
    format!("{pre}{lead} {}개를 찾았어요. 하트가 가장 많은 길은 {}{}.{d} 안내할까요?", f.list.len(), quoted(top), eyo(&top.route.title))
}

/// 한 길 이야기 (번호로 골랐을 때).
fn one_answer(i: usize, r: &DriveRouteRecommendation, go: bool, driving: bool) -> String {
    let t = quoted(r);
    let p = &r.route;
    if go {
        return format!("{}번 {t}{} 안내할까요?", i + 1, ro(&p.title));
    }
    if driving {
        return format!("{}번은 {}에서 {}까지 {} 길이에요. 안내할까요?", i + 1, p.start_name, p.end_name, km_text(p.distance_m / 1000.0));
    }
    let seen = sights(r, 3);
    let seen = if seen.is_empty() { String::new() } else { format!(" 가는 길에 {seen}{} 보여요.", iga(&seen)) };
    let here = if r.distance_km >= 0.0 { format!("고, 여기서 {}예요.", here_text(r.distance_km)) } else { "아요.".to_string() };
    format!(
        "{}번 {t}{} {}에서 {}까지 {}, {}쯤 걸리는 길이에요.{seen} {} 가장 좋{here} 안내할까요?",
        i + 1,
        eun(&p.title),
        p.start_name,
        p.end_name,
        km_text(p.distance_m / 1000.0),
        duration_text(p.duration_ms),
        when_word(p.best_time)
    )
}

impl MemoryAgent {
    /// 추천 길 말 처리 (LLM 없이). 처리했으면 true. 답마다 목록을 화면에 다시 보낸다 (새 질문 카드가 목록을 잃지 않게).
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
                    t.say(if q.is_empty() { "아직 공유된 드라이브 길이 없어요." } else { "그쪽으로 공유된 드라이브 길이 아직 없어요." });
                    return;
                }
                let what = if q.is_empty() || f.theme_missed { "인기 드라이브 길".to_string() } else { format!("‘{}’ 인기 드라이브 길", q.join("·")) };
                let lead = format!("{}{what}", scope(&f));
                let text = list_answer(&f, &lead, &q, DEFAULT_RADIUS_KM, driving);
                self.show_courses(s, t, CourseState { list: f.list, focus: 0, q, radius_km: DEFAULT_RADIUS_KM });
                t.say(&text);
                self.propose_focus(s, t);
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
                self.propose_focus(s, t);
            }
            CourseIntent::Describe => {
                let i = s.courses.focus.min(s.courses.list.len() - 1);
                let r = s.courses.list[i].clone();
                t.say(&one_answer(i, &r, false, driving));
                self.propose_focus(s, t);
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
                    t.say(&format!("근처에서는 {}{} 가장 가까운 드라이브 길이에요. 안내할까요?", quoted(&cur), iga(&cur.route.title)));
                    self.propose_focus(s, t);
                    return;
                }
                let f = Fetched { widened: false, theme_missed: false, ..f };
                let text = list_answer(&f, "더 가까운 드라이브 길", &[], s.courses.radius_km, driving);
                let q = s.courses.q.clone();
                let radius = s.courses.radius_km;
                self.show_courses(s, t, CourseState { list: f.list, focus: 0, q, radius_km: radius });
                t.say(&text);
                self.propose_focus(s, t);
            }
            CourseIntent::Theme { q } => {
                let exclude: Vec<String> = s.courses.list.get(s.courses.focus).map(|r| vec![r.route.id.clone()]).unwrap_or_default();
                let f = fetch(self.backend.as_ref(), loc, &q, s.courses.radius_km.max(DEFAULT_RADIUS_KM), SHOW_K, &exclude);
                if f.list.is_empty() || f.theme_missed {
                    t.say(&format!("‘{}’ 쪽 드라이브 길은 아직 없어요. 지금 목록에서 골라 주세요.", q.join("·")));
                    return;
                }
                let lead = format!("{}‘{}’ 드라이브 길", scope(&f), q.join("·"));
                let text = list_answer(&f, &lead, &q, DEFAULT_RADIUS_KM, driving);
                self.show_courses(s, t, CourseState { list: f.list, focus: 0, q, radius_km: DEFAULT_RADIUS_KM });
                t.say(&text);
                self.propose_focus(s, t);
            }
            CourseIntent::Other => {
                let next = s.courses.focus + 1;
                if let Some(r) = s.courses.list.get(next).cloned() {
                    s.courses.focus = next;
                    let st = s.courses.clone();
                    self.show_courses(s, t, st);
                    let d = if r.distance_km >= 0.0 { format!(", 여기서 {}", here_text(r.distance_km)) } else { String::new() };
                    t.say(&format!("그럼 {}번 {}{} 어떠세요? 하트 {}개{d}예요. 안내할까요?", next + 1, quoted(&r), eun(&r.route.title), r.route.hearts));
                    self.propose_focus(s, t);
                    return;
                }
                let shown: Vec<String> = s.courses.list.iter().map(|r| r.route.id.clone()).collect();
                let f = fetch(self.backend.as_ref(), loc, &s.courses.q, s.courses.radius_km, SHOW_K, &shown);
                if f.list.is_empty() || f.theme_missed {
                    t.say("추천할 길은 이게 다예요. 바다·강·야경처럼 테마를 말씀해 주시면 다시 찾아 볼게요.");
                    return;
                }
                let text = list_answer(&f, "다른 인기 드라이브 길", &[], s.courses.radius_km, driving);
                let q = s.courses.q.clone();
                let radius = s.courses.radius_km;
                self.show_courses(s, t, CourseState { list: f.list, focus: 0, q, radius_km: radius });
                t.say(&text);
                self.propose_focus(s, t);
            }
        }
    }

    /// 목록을 세션에 넣고 화면에 보낸다.
    fn show_courses(&self, s: &mut Session, t: &mut Turn, st: CourseState) {
        s.courses = st;
        t.courses(&s.courses);
    }

    /// 추천 길 이야기 중 LLM이 답한 뒤의 안전망 (LLM이 스스로 nav를 불렀으면 아무것도 하지 않는다):
    /// - 목록의 길 하나를 말하며 "안내…"를 물었는데 nav를 부르지 않았으면 그 길로 제안을 만든다 (작은 모델에서 자주 나온다).
    /// - 그 밖에는 대기 중인 길 제안을 다시 보낸다 (새 질문 화면에도 확인 버튼이 있게).
    ///
    /// 어느 쪽이든 제안일 뿐이고 실행은 여전히 운전자 확인 뒤다.
    pub(crate) fn course_proposal_from_answer(&self, s: &mut Session, t: &mut Turn, pending_before: Option<String>) {
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
        let named: Vec<usize> =
            s.courses.list.iter().enumerate().filter(|(_, r)| t.answer_text().contains(r.route.title.as_str())).map(|(i, _)| i).collect();
        if let ([i], true) = (named.as_slice(), t.answer_text().contains("안내")) {
            s.courses.focus = *i;
            t.courses(&s.courses);
            if pending_post.as_deref() != Some(s.courses.list[*i].route.id.as_str()) {
                return self.propose_focus(s, t);
            }
        }
        if let (Some(p), Some(_)) = (pending_now, pending_post) {
            t.l.on_action_proposal(p);
        }
    }

    /// 지금 고른 길로 안내 제안 (대기 제안을 바꾼다).
    fn propose_focus(&self, s: &mut Session, t: &mut Turn) {
        let Some(r) = s.courses.list.get(s.courses.focus) else { return };
        let p = shared_proposal(&mut s.proposal_seq, r);
        *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
        t.l.on_action_proposal(p);
    }
}

// ---------------------------------------------------------------- LLM 도구 결과 (작게)

pub fn course_json(i: usize, r: &DriveRouteRecommendation) -> Value {
    let p = &r.route;
    json!({
        "n": i + 1, "title": p.title, "by": p.author, "hearts": p.hearts,
        "km_from_here": (r.distance_km >= 0.0).then(|| (r.distance_km * 10.0).round() / 10.0),
        "region": p.region,
        "from": p.start_name, "to": p.end_name,
        "route_km": km(p.distance_m),
        "minutes": (p.duration_ms + 30_000) / 60_000,
        "best": p.best_time.label(),
        "sights": p.highlights.iter().take(4).map(|h| h.name.clone()).collect::<Vec<_>>(),
    })
}

pub fn list_json(st: &CourseState) -> Value {
    Value::Array(st.list.iter().enumerate().map(|(i, r)| course_json(i, r)).collect())
}

//! 검색 화면 (손으로 치는 검색): 내 기록과 커뮤니티를 글자를 칠 때마다 바로 찾는다.
//!
//! 기억 에이전트(말로 묻기)와 같은 규칙과 색인을 쓰고, 계산(말 풀기 · 묶기 · 순위 · 날짜 글)은 모두 여기서 한다.
//! Kotlin은 받은 묶음 · 줄을 순서대로 그리기만 한다.
//!
//! # 말 풀기 ([parse_query])
//! - 낱말: `agent::words::parse` (조사 · 어미 · 질문 말 · 날짜 말을 뗀다) + 한 글자 뜻말(산 · 강 · 섬 …).
//!   "이맘때"는 날짜 말이라 낱말에서 뺀다.
//! - 칠 때 한 글자만 들어온 경우("순")는 장소 이름 안에 그 글자가 있는 곳만 찾는다 (색인은 2글자 조각이라).
//! - 날짜 말: "작년 이맘때"(1년 전 같은 날 ±2주, "재작년 이맘때"는 2년 전) → 그 밖은 `agent::dates::parse_korean_range`
//!   ("지난주", "작년 여름", "10월", "어린이날" …). 기간 칩(이번 주 · 이번 달 · 올해 · 직접)과 겹치면 둘 다 맞는 기간만.
//! - 질문 같은 말: 물음표, 질문 말(어디 · 언제 · 무슨 · 였지 · 더라 · 알려 · 추천 …), 사람 말(엄마랑 · 친구와),
//!   앞 대화 말(거기 · 그때), 낱말 4개 이상 → [AskAi::primary] = true (화면이 맨 위에 "AI에게 물어보기"를 크게 둔다).
//!   결과는 질문이어도 같이 찾는다.
//!
//! # 내 기록 찾기 ([SearchScope::Records])
//! - **장소**: 기억 색인(FTS5)으로 후보 에피소드를 고르고, 그 들른 곳 중 이름(3) · 분류(2) · 주소(1)에 낱말이 든 곳을
//!   이름 · 주소가 같은 곳끼리 묶는다. 뜻 묶음(동의어 묶음은 하나)을 가장 많이 덮는 곳만 남기고
//!   (예: "성수 카페" → 성수에 있는 카페), 맞춤 점수 → 다녀온 날 수 → 최근 순. 날짜 말 · 칩만 있으면 그 기간에 들른 곳 전부.
//! - **날짜 · 기록**: 기억 검색(`memory::search`, 동의어 · 순위 · 하루 하나)을 그대로 쓴다.
//! - **코스 · 리뷰**: 커뮤니티 코스와 리뷰 장소 중 낱말이 맞는 것 상위 몇 개.
//!
//! # 커뮤니티 찾기 (코스 · 리뷰 · 게시판)
//! 글이 적어(수백 개) 전부 읽어 낱말이 든 칸의 무게(제목 · 태그 3, 지역 · 들른 곳 2, 본문 1)로 순위를 매기고,
//! 같으면 하트 많은 순. 날짜 말이 있으면 다녀온 날(코스 · 리뷰)·올린 날(게시판)로 거른다.
//!
//! # 최근 검색어 (DB 구조 버전 6, `search_recent`)
//! 검색을 확정할 때(키보드 검색 · 결과 열기 · AI에게 넘기기) [EodiStore::remember_search]로 넣는다. 같은 말은 시각만
//! 새로 하고, 20개를 넘으면 오래된 것부터 지운다.

use std::collections::{BTreeSet, HashMap};
use std::time::Instant;

use rusqlite::{params, Connection};

use crate::agent::dates::{self, civil_from_days, days_from_civil, weekday, DayRange, WEEKDAYS};
use crate::agent::words;
use crate::community::{norm_name, same_place, BoardPost, CommunitySort, CoursePost, PopularPlace};
use crate::geo::{haversine_m, Point};
use crate::memory::{
    concepts, episode_from_row, expand_terms, kind_code, match_expr, search_hits, squash, Episode, EpisodeKind, MemoryQuery,
    StopFact, EP_COLS, SINGLE_OK,
};
use crate::store::{parse_day, CoreError, EodiStore};

/// DB 구조 버전 6에서 더하는 표 (예전 DB를 흉내 내는 테스트가 표를 남겨 둔 채 버전만 내려도 되게 IF NOT EXISTS).
pub(crate) const SCHEMA_V6: &str =
    "CREATE TABLE IF NOT EXISTS search_recent (text TEXT PRIMARY KEY, used_ms INTEGER NOT NULL) WITHOUT ROWID;";

/// 예전 버전 DB를 흉내 내는 테스트용 (v6 표 지우기).
#[cfg(test)]
pub(crate) const DROP_V6: &str = "DROP TABLE search_recent;";

/// 최근 검색어 최대 개수 (저장) · 보여 줄 개수.
const RECENT_KEEP: i64 = 20;
const RECENT_SHOW: i64 = 8;
/// 최근 검색어 최대 길이(글자).
const RECENT_MAX_CHARS: usize = 60;
/// "이맘때" = 그날 앞뒤 며칠.
const AROUND_DAYS: i64 = 14;
/// 집 · 회사에서 이 거리(m) 안의 곳은 자주 가는 곳 칩에서 뺀다.
const ANCHOR_M: f64 = 250.0;

/// 어디를 찾나. 내 기록 화면은 [Records], 커뮤니티는 지금 보고 있는 하위 탭.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SearchScope {
    Records,
    Courses,
    Reviews,
    Board,
}

/// 기간 칩.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SearchPeriod {
    Any,
    /// 이번 주 월요일 ~ 오늘
    ThisWeek,
    /// 이번 달 1일 ~ 오늘
    ThisMonth,
    /// 올해 1월 1일 ~ 오늘
    ThisYear,
    /// [SearchFilter::from_day] ~ [SearchFilter::to_day]
    Custom,
}

/// 칩으로 고른 조건 (내 기록 찾기에만 쓴다).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchFilter {
    pub period: SearchPeriod,
    /// [SearchPeriod::Custom]일 때 기간 "YYYY-MM-DD" (양 끝 포함, None이면 그쪽 끝은 열림)
    pub from_day: Option<String>,
    pub to_day: Option<String>,
    /// 에피소드 종류 (비면 전체)
    pub kinds: Vec<EpisodeKind>,
    /// 처음 간 곳만
    pub first_visit_only: bool,
}

impl SearchFilter {
    pub fn none() -> Self {
        Self { period: SearchPeriod::Any, from_day: None, to_day: None, kinds: vec![], first_visit_only: false }
    }

    fn is_set(&self) -> bool {
        self.period != SearchPeriod::Any || !self.kinds.is_empty() || self.first_visit_only
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SearchGroupKind {
    /// 장소 (내 기록)
    Places,
    /// 날짜 · 기록 (내 기록)
    Days,
    /// 코스 · 리뷰 · 게시글 (커뮤니티)
    Community,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SearchRowKind {
    /// 내가 들른 곳 → 열기 = 마지막으로 간 날, 지도 = 그날 지도에서 그곳, 안내 = 그곳
    Place,
    /// 하루 기록 → 열기 = 그날, 지도 = 그날 지도에서 가장 맞는 곳, 안내 = 가장 맞는 곳
    Day,
    /// 드라이브 코스 → 열기 = 코스 상세
    Course,
    /// 리뷰가 있는 장소 → 열기 = 그 장소 리뷰
    Review,
    /// 게시글 → 열기 = 글
    Post,
}

/// 결과 한 줄. 글은 화면에 그대로 쓴다.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchRow {
    /// 목록 키 (묶음 안에서 겹치지 않는다)
    pub key: String,
    pub kind: SearchRowKind,
    pub title: String,
    /// 예: "카페 · 서울 성동구 성수동2가 322-32", "경포해변 · 초당할머니순두부 · 안목해변 카페거리"
    pub subtitle: String,
    /// 예: "작년 11월 6일 (목) · 여행", "5번 · 마지막 3일 전 (월)"
    pub date_line: String,
    /// 열 날 (장소 · 하루)
    pub day: Option<String>,
    /// 사진 · 지도 · 안내할 곳
    pub place: Option<StopFact>,
    /// 처음 간 곳 표시
    pub first_visit: bool,
    pub episode_id: Option<i64>,
    pub course: Option<CoursePost>,
    pub review_place: Option<PopularPlace>,
    pub post: Option<BoardPost>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchGroup {
    pub kind: SearchGroupKind,
    /// 묶음 이름 ("장소", "날짜 · 기록", "코스 · 리뷰", "드라이브 코스" …)
    pub title: String,
    pub rows: Vec<SearchRow>,
    /// 보이지 않은 결과 수 (줄 수 제한으로 잘린 것)
    pub more: u32,
}

/// "AI에게 물어보기" 줄. [primary]면 맨 위에 크게, 아니면 결과 아래에 작게.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AskAi {
    pub text: String,
    pub primary: bool,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchAnswer {
    /// 찾은 말 (그대로)
    pub text: String,
    /// 실제로 찾은 낱말 (조사 · 날짜 말을 뗀 것)
    pub terms: Vec<String>,
    /// 말 속 날짜 말이 가리키는 기간 (예: "10월 26일 ~ 11월 1일"), 없으면 None
    pub date_label: Option<String>,
    /// 말이 비었으면 None
    pub ask_ai: Option<AskAi>,
    /// 결과가 있는 묶음만 (빈 묶음은 없다)
    pub groups: Vec<SearchGroup>,
    /// 결과 수 (잘린 것 포함)
    pub total: u32,
    /// Rust 안에서 걸린 시간
    pub elapsed_micros: u64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SearchChipKind {
    Keyword,
    /// 날짜 말 ("지난주", "작년 이맘때")
    Date,
    /// 자주 가는 곳
    Place,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchChip {
    pub label: String,
    /// 누르면 검색 칸에 넣을 말
    pub text: String,
    pub kind: SearchChipKind,
}

/// 아무것도 치지 않았을 때 보이는 것.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchSuggestions {
    /// 최근 검색어 (최신순)
    pub recent: Vec<String>,
    pub chips: Vec<SearchChip>,
}

/// 기본 추천 칩 (낱말 · 날짜 말). 자주 가는 곳 칩은 기록에서 붙인다.
const BASE_CHIPS: &[(&str, SearchChipKind)] = &[
    ("바다", SearchChipKind::Keyword),
    ("카페", SearchChipKind::Keyword),
    ("지난주", SearchChipKind::Date),
    ("휴게소", SearchChipKind::Keyword),
    ("작년 이맘때", SearchChipKind::Date),
];

/// 질문으로 보는 말 (띄어쓰기를 뺀 글에 들어 있으면).
const QUESTION_WORDS: &[&str] = &[
    "어디", "언제", "뭐였", "뭐더라", "무슨", "어느", "누구", "몇번", "였지", "었지", "았지", "했지", "더라", "알려", "찾아줘", "추천",
    "어땠", "갔던", "갔었", "다녀온", "먹었던", "했던", "인가요", "일까", "나요", "까요",
];

/// 날짜 말이지만 `words::parse`가 낱말로 남기는 것.
const DATE_TERMS: &[&str] = &["이맘때", "이맘때쯤", "이맘쯤"];

/// 검색어를 푼 결과.
#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Query {
    /// 찾을 낱말 (동의어 넓히기 전)
    pub terms: Vec<String>,
    /// 한 글자만 친 경우 그 글자 (장소 이름 안 글자로만 찾는다)
    pub single: Option<String>,
    /// 말 속 날짜 기간 (현지 날짜)
    pub range: Option<DayRange>,
    pub question: bool,
}

impl Query {
    fn has_words(&self) -> bool {
        !self.terms.is_empty() || self.single.is_some()
    }
}

/// 검색어 풀기 (모듈 설명 "말 풀기"). [today] = 현지 날짜 번호.
pub(crate) fn parse_query(text: &str, today: i64) -> Query {
    let t = text.trim();
    let parsed = words::parse(t);
    let toks = words::tokens(t);
    let mut terms: Vec<String> = parsed.terms.into_iter().filter(|w| !DATE_TERMS.contains(&w.as_str())).collect();
    for w in &toks {
        if SINGLE_OK.contains(&w.as_str()) && !terms.contains(w) {
            terms.push(w.clone());
        }
    }
    let single = (terms.is_empty() && toks.len() == 1 && toks[0].chars().count() == 1 && !dates::is_date_word(&toks[0]))
        .then(|| toks[0].clone())
        .filter(|w| w.chars().all(char::is_alphanumeric));
    let range = around_last_year(t, today).or_else(|| dates::parse_korean_range(t, today));
    let nospace: String = t.chars().filter(|c| !c.is_whitespace()).collect();
    let question = t.ends_with('?')
        || t.ends_with('？')
        || parsed.has_people
        || parsed.has_reference
        || QUESTION_WORDS.iter().any(|w| nospace.contains(w))
        || toks.len() >= 4;
    Query { terms, single, range, question }
}

/// "작년 이맘때" · "이맘때" (= 1년 전) · "재작년 이맘때" → 그날 앞뒤 [AROUND_DAYS]일 (오늘을 넘지 않게).
fn around_last_year(t: &str, today: i64) -> Option<DayRange> {
    let nospace: String = t.chars().filter(|c| !c.is_whitespace()).collect();
    if !DATE_TERMS.iter().any(|w| nospace.contains(w)) {
        return None;
    }
    let years = if nospace.contains("재작년") { 2 } else { 1 };
    let (y, m, d) = civil_from_days(today);
    // 2월 29일이면 그해 2월 28일
    let center = (0..3).map(|back| d.saturating_sub(back)).find_map(|dd| {
        let c = days_from_civil(y - years, m, dd.max(1));
        (civil_from_days(c).1 == m).then_some(c)
    })?;
    Some(DayRange { from: center - AROUND_DAYS, to: (center + AROUND_DAYS).min(today) })
}

/// 기간 칩 → 기간 (None = 제한 없음). 직접 고른 날이 틀리면 그쪽 끝은 열어 둔다.
fn period_range(f: &SearchFilter, today: i64) -> Option<(Option<i64>, Option<i64>)> {
    let (y, m, _) = civil_from_days(today);
    match f.period {
        SearchPeriod::Any => None,
        SearchPeriod::ThisWeek => Some((Some(today - weekday(today) as i64), Some(today))),
        SearchPeriod::ThisMonth => Some((Some(days_from_civil(y, m, 1)), Some(today))),
        SearchPeriod::ThisYear => Some((Some(days_from_civil(y, 1, 1)), Some(today))),
        SearchPeriod::Custom => {
            Some((f.from_day.as_deref().and_then(parse_day), f.to_day.as_deref().and_then(parse_day)))
        }
    }
}

/// 말의 기간과 칩 기간을 겹친다. 겹치는 날이 없으면 Err(()).
fn combine_ranges(text: Option<DayRange>, chip: Option<(Option<i64>, Option<i64>)>) -> Result<(Option<i64>, Option<i64>), ()> {
    let (mut lo, mut hi) = chip.unwrap_or((None, None));
    if let Some(r) = text {
        lo = Some(lo.map_or(r.from, |l| l.max(r.from)));
        hi = Some(hi.map_or(r.to, |h| h.min(r.to)));
    }
    match (lo, hi) {
        (Some(a), Some(b)) if a > b => Err(()),
        _ => Ok((lo, hi)),
    }
}

/// 날짜 한 줄: "오늘", "어제", "3일 전", "11월 2일", "작년 8월 14일", "2024년 5월 5일" + " (요일)".
pub(crate) fn when_text(day: i64, today: i64) -> String {
    let (y, m, d) = civil_from_days(day);
    let (ty, _, _) = civil_from_days(today);
    let head = match today - day {
        0 => "오늘".to_string(),
        1 => "어제".to_string(),
        n @ 2..=6 => format!("{n}일 전"),
        _ if y == ty => format!("{m}월 {d}일"),
        _ if y == ty - 1 => format!("작년 {m}월 {d}일"),
        _ => format!("{y}년 {m}월 {d}일"),
    };
    format!("{head} ({})", WEEKDAYS[weekday(day)])
}

/// 기간 글: "11월 5일 (수)" 또는 "10월 26일 ~ 11월 1일" (올해가 아니면 앞에 연도).
pub(crate) fn range_label(r: DayRange, today: i64) -> String {
    let (ty, _, _) = civil_from_days(today);
    let (fy, fm, fd) = civil_from_days(r.from);
    let (ey, em, ed) = civil_from_days(r.to);
    let from = if fy == ty { format!("{fm}월 {fd}일") } else { format!("{fy}년 {fm}월 {fd}일") };
    if r.from == r.to {
        return format!("{from} ({})", WEEKDAYS[weekday(r.from)]);
    }
    let to = if ey == fy { format!("{em}월 {ed}일") } else { format!("{ey}년 {em}월 {ed}일") };
    format!("{from} ~ {to}")
}

fn kind_label(k: EpisodeKind) -> &'static str {
    match k {
        EpisodeKind::CommuteToWork => "출근길",
        EpisodeKind::CommuteHome => "퇴근길",
        EpisodeKind::Errand => "볼일",
        EpisodeKind::Outing => "나들이",
        EpisodeKind::Trip => "여행",
    }
}

/// 칸들(글, 무게)에 낱말 뜻 묶음이 얼마나 맞나: (점수 = 묶음마다 가장 무거운 맞는 칸의 무게 합, 맞은 묶음 수).
/// [single]이면 그 글자가 든 칸 무게 하나.
fn text_match(fields: &[(String, u32)], groups: &[Vec<String>], single: Option<&str>) -> (u32, usize) {
    if let Some(ch) = single {
        let w = fields.iter().filter(|(f, _)| f.contains(ch)).map(|(_, w)| *w).max().unwrap_or(0);
        return (w, usize::from(w > 0));
    }
    let mut score = 0;
    let mut matched = 0;
    for g in groups {
        let best = fields
            .iter()
            .filter(|(f, _)| g.iter().any(|t| !t.is_empty() && f.contains(t.as_str())))
            .map(|(_, w)| *w)
            .max()
            .unwrap_or(0);
        if best > 0 {
            score += best;
            matched += 1;
        }
    }
    (score, matched)
}

/// 낱말 → 뜻 묶음 (동의어로 넓힌 뒤, 비교용으로 띄어쓰기 · 대소문자를 없앤 꼴).
fn term_groups(terms: &[String]) -> Vec<Vec<String>> {
    concepts(&expand_terms(terms)).into_iter().map(|g| g.iter().map(|t| squash(t)).collect()).collect()
}

/// 가장 많은 뜻 묶음을 덮는 것만 남긴다 ("성수 카페" → 성수 · 카페 둘 다 맞는 것; 없으면 하나라도 맞는 것).
fn keep_best_coverage<T>(items: &mut Vec<(T, u32, usize)>) {
    let best = items.iter().map(|x| x.2).max().unwrap_or(0);
    items.retain(|x| x.2 == best);
}

fn short_body(s: &str, max: usize) -> String {
    let line = s.lines().map(str::trim).find(|l| !l.is_empty()).unwrap_or("");
    if line.chars().count() <= max {
        line.to_string()
    } else {
        format!("{}…", line.chars().take(max).collect::<String>())
    }
}

fn join_dot(parts: &[&str]) -> String {
    parts.iter().map(|s| s.trim()).filter(|s| !s.is_empty()).collect::<Vec<_>>().join(" · ")
}

/// 들른 곳 묶음 (장소 줄 하나).
struct PlaceAgg {
    /// 가장 최근 들름
    last: StopFact,
    last_day: String,
    score: u32,
    matched: usize,
    days: BTreeSet<String>,
    first_visit: bool,
}

#[uniffi::export]
impl EodiStore {
    /// 검색 화면: 친 말([text])로 [scope]를 바로 찾는다 (모듈 설명). 1년치도 수 ms.
    /// [filter]는 내 기록 찾기에만 쓴다. 말이 비고 칩도 없으면 빈 결과 (화면은 [EodiStore::search_suggestions]를 보인다).
    /// 같은 말·범위·칩·날짜를 다시 찾으면(탭 오가기·지우고 다시 치기) 데이터가 바뀌기 전까지 저장해 둔 결과를 준다.
    pub fn search_typed(&self, text: String, scope: SearchScope, filter: SearchFilter, now_ms: i64) -> Result<SearchAnswer, CoreError> {
        let started = Instant::now();
        let key = format!("search_typed {text:?} {scope:?} {filter:?} {}", self.local_day(now_ms));
        let mut ans = self.cached(crate::store::Domain::Both, key, || self.search_typed_uncached(text, scope, filter, now_ms))?;
        ans.elapsed_micros = started.elapsed().as_micros() as u64;
        Ok(ans)
    }
}

impl EodiStore {
    fn search_typed_uncached(&self, text: String, scope: SearchScope, filter: SearchFilter, now_ms: i64) -> Result<SearchAnswer, CoreError> {
        let started = Instant::now();
        let today = self.local_day(now_ms);
        let q = parse_query(&text, today);
        let trimmed = text.trim().to_string();
        let date_label = q.range.map(|r| range_label(r, today));
        let ask_ai = (!trimmed.is_empty()).then(|| AskAi { text: trimmed.clone(), primary: q.question });
        let chip_range = if scope == SearchScope::Records { period_range(&filter, today) } else { None };
        let filter = if scope == SearchScope::Records { filter } else { SearchFilter::none() };
        let mut groups = Vec::new();
        let searchable = q.has_words() || q.range.is_some() || filter.is_set();
        if searchable {
            if let Ok((lo, hi)) = combine_ranges(q.range, chip_range) {
                groups = match scope {
                    SearchScope::Records => self.search_records(&q, lo, hi, &filter, today)?,
                    SearchScope::Courses => self.course_group(&q, lo, hi, today, 50)?.into_iter().collect(),
                    SearchScope::Reviews => self.review_group(&q, lo, hi, today, 50)?.into_iter().collect(),
                    SearchScope::Board => self.board_group(&q, lo, hi, today, 50)?.into_iter().collect(),
                };
            }
        }
        let total = groups.iter().map(|g| g.rows.len() as u32 + g.more).sum();
        Ok(SearchAnswer {
            text: trimmed,
            terms: q.terms.clone(),
            date_label,
            ask_ai,
            groups,
            total,
            elapsed_micros: started.elapsed().as_micros() as u64,
        })
    }
}

#[uniffi::export]
impl EodiStore {
    /// 아무것도 치지 않았을 때: 최근 검색어(최신순 8개) + 추천 칩(바다 · 카페 · 지난주 · 휴게소 · 작년 이맘때 +
    /// 자주 가는 곳 3곳, 집 · 회사 근처는 뺀다).
    pub fn search_suggestions(&self) -> Result<SearchSuggestions, CoreError> {
        let conn = self.read()?;
        let recent: Vec<String> = {
            let mut st = conn.prepare_cached("SELECT text FROM search_recent ORDER BY used_ms DESC LIMIT ?1")?;
            let rows = st.query_map([RECENT_SHOW], |r| r.get(0))?;
            rows.collect::<Result<_, _>>()?
        };
        let mut chips: Vec<SearchChip> =
            BASE_CHIPS.iter().map(|(t, k)| SearchChip { label: t.to_string(), text: t.to_string(), kind: *k }).collect();
        for name in frequent_places(&conn, 3)? {
            if !chips.iter().any(|c| c.text == name) {
                chips.push(SearchChip { label: name.clone(), text: name, kind: SearchChipKind::Place });
            }
        }
        Ok(SearchSuggestions { recent, chips })
    }

    /// 최근 검색어에 넣는다 (같은 말이면 시각만 새로, 20개까지). 빈 말은 무시.
    pub fn remember_search(&self, text: String, now_ms: i64) -> Result<(), CoreError> {
        let t: String = text.split_whitespace().collect::<Vec<_>>().join(" ").chars().take(RECENT_MAX_CHARS).collect();
        if t.is_empty() {
            return Ok(());
        }
        let conn = self.lock_side()?;
        conn.execute("INSERT OR REPLACE INTO search_recent (text, used_ms) VALUES (?1, ?2)", params![t, now_ms])?;
        conn.execute(
            "DELETE FROM search_recent WHERE text NOT IN (SELECT text FROM search_recent ORDER BY used_ms DESC LIMIT ?1)",
            [RECENT_KEEP],
        )?;
        Ok(())
    }

    /// 최근 검색어 하나 지우기.
    pub fn forget_search(&self, text: String) -> Result<(), CoreError> {
        let conn = self.lock_side()?;
        conn.execute("DELETE FROM search_recent WHERE text = ?1", [text.trim()])?;
        Ok(())
    }

    /// 최근 검색어 모두 지우기.
    pub fn clear_recent_searches(&self) -> Result<(), CoreError> {
        let conn = self.lock_side()?;
        conn.execute("DELETE FROM search_recent", [])?;
        Ok(())
    }
}

impl EodiStore {
    /// 내 기록 찾기: 장소 · 날짜 · 기록 · (낱말이 있으면) 코스 · 리뷰.
    fn search_records(
        &self,
        q: &Query,
        lo: Option<i64>,
        hi: Option<i64>,
        filter: &SearchFilter,
        today: i64,
    ) -> Result<Vec<SearchGroup>, CoreError> {
        let tz = self.tz_minutes();
        let from_ms = lo.map(|d| dates::day_start_ms(d, tz));
        let to_ms = hi.map(|d| dates::day_start_ms(d + 1, tz) - 1);
        let (places, days) = {
            let conn = self.read()?;
            let places = place_group(&conn, q, from_ms, to_ms, filter, today, 8)?;
            let days = day_group(&conn, q, from_ms, to_ms, filter, today, 20)?;
            (places, days)
        };
        let mut groups: Vec<SearchGroup> = Vec::new();
        // 날짜 말 · 칩만 있으면 날짜가 주인공, 낱말이 있으면 장소가 먼저
        if q.has_words() {
            groups.extend(places);
            groups.extend(days);
        } else {
            groups.extend(days);
            groups.extend(places);
        }
        if q.has_words() && !filter.is_set() {
            let mut rows: Vec<(SearchRow, u32)> = Vec::new();
            for g in [self.course_group(q, lo, hi, today, 3)?, self.review_group(q, lo, hi, today, 3)?].into_iter().flatten() {
                rows.extend(g.rows.into_iter().enumerate().map(|(i, r)| (r, i as u32)));
            }
            // 코스 1위 · 리뷰 1위 · 코스 2위 … 순서로 섞어 3개
            rows.sort_by_key(|(_, rank)| *rank);
            let more = rows.len().saturating_sub(3) as u32;
            let rows: Vec<SearchRow> = rows.into_iter().take(3).map(|(r, _)| r).collect();
            if !rows.is_empty() {
                groups.push(SearchGroup { kind: SearchGroupKind::Community, title: "코스 · 리뷰".into(), rows, more });
            }
        }
        Ok(groups)
    }

    fn course_group(&self, q: &Query, lo: Option<i64>, hi: Option<i64>, today: i64, limit: usize) -> Result<Option<SearchGroup>, CoreError> {
        let groups = term_groups(&q.terms);
        let posts = self.community_courses(CommunitySort::Popular, None, 500)?;
        let mut hits: Vec<(CoursePost, u32, usize)> = posts
            .into_iter()
            .filter(|p| in_range(parse_day(&p.visited_day), lo, hi))
            .filter_map(|p| {
                let names: Vec<&str> = p.stops.iter().map(|s| s.name.as_str()).collect();
                let cats: Vec<&str> = p.stops.iter().map(|s| s.category.as_str()).collect();
                let fields = [
                    (squash(&p.title), 3),
                    (squash(&p.tags.join(" ")), 3),
                    (squash(&p.region), 2),
                    (squash(&names.join(" ")), 2),
                    (squash(&cats.join(" ")), 1),
                    (squash(&p.body), 1),
                ];
                let (score, matched) = text_match(&fields, &groups, q.single.as_deref());
                (matched > 0 || !q.has_words()).then_some((p, score, matched))
            })
            .collect();
        keep_best_coverage(&mut hits);
        hits.sort_by(|a, b| b.1.cmp(&a.1).then(b.0.hearts.cmp(&a.0.hearts)).then_with(|| a.0.id.cmp(&b.0.id)));
        let more = hits.len().saturating_sub(limit) as u32;
        let rows = hits
            .into_iter()
            .take(limit)
            .map(|(p, _, _)| {
                let visited = parse_day(&p.visited_day).map(|d| when_text(d, today)).unwrap_or_default();
                let km = format!("{:.0}km", p.distance_m / 1000.0);
                let count = format!("{}곳", p.stops.len());
                SearchRow {
                    key: format!("c:{}", p.id),
                    kind: SearchRowKind::Course,
                    title: p.title.clone(),
                    subtitle: join_dot(&[&p.region, &count, &km]),
                    date_line: join_dot(&[&visited, &format!("♥ {}", p.hearts)]),
                    day: None,
                    place: p.stops.first().cloned(),
                    first_visit: false,
                    episode_id: None,
                    course: Some(p),
                    review_place: None,
                    post: None,
                }
            })
            .collect();
        Ok(group_of(SearchGroupKind::Community, "드라이브 코스", rows, more))
    }

    fn review_group(&self, q: &Query, lo: Option<i64>, hi: Option<i64>, today: i64, limit: usize) -> Result<Option<SearchGroup>, CoreError> {
        let groups = term_groups(&q.terms);
        let places = self.popular_places(None, 500)?;
        let reviews = self.place_reviews(CommunitySort::Recent, None, 500)?;
        // 같은 장소는 이름이 같아야 하니 이름별로 미리 나눠 두고 그 안에서만 비교한다 (장소 × 리뷰 전부 비교 안 함)
        let mut by_name: HashMap<String, Vec<&crate::community::PlaceReview>> = HashMap::new();
        for r in &reviews {
            by_name.entry(norm_name(&r.place.name)).or_default().push(r);
        }
        let mut hits: Vec<(PopularPlace, u32, usize)> = places
            .into_iter()
            .filter_map(|pp| {
                let mine: Vec<&crate::community::PlaceReview> = by_name
                    .get(&norm_name(&pp.place.name))
                    .map(|v| v.iter().copied().filter(|r| same_place(&r.place, &pp.place)).collect())
                    .unwrap_or_default();
                if (lo.is_some() || hi.is_some()) && !mine.iter().any(|r| in_range(parse_day(&r.visited_day), lo, hi)) {
                    return None;
                }
                let texts: Vec<&str> = mine.iter().map(|r| r.text.as_str()).collect();
                let fields = [
                    (squash(&pp.place.name), 3),
                    (squash(&pp.place.category), 2),
                    (squash(&pp.place.address), 1),
                    (squash(&texts.join(" ")), 1),
                ];
                let (score, matched) = text_match(&fields, &groups, q.single.as_deref());
                (matched > 0 || !q.has_words()).then_some((pp, score, matched))
            })
            .collect();
        keep_best_coverage(&mut hits);
        hits.sort_by(|a, b| b.1.cmp(&a.1).then(b.0.hearts.cmp(&a.0.hearts)).then_with(|| a.0.place.name.cmp(&b.0.place.name)));
        let more = hits.len().saturating_sub(limit) as u32;
        let latest: HashMap<String, i64> = reviews.iter().fold(HashMap::new(), |mut m, r| {
            if let Some(d) = parse_day(&r.visited_day) {
                let e = m.entry(squash(&r.place.name)).or_insert(d);
                *e = (*e).max(d);
            }
            m
        });
        let rows = hits
            .into_iter()
            .take(limit)
            .map(|(pp, _, _)| {
                let rating = if pp.review_count > 0 { format!("★ {:.1}", pp.avg_rating) } else { String::new() };
                let reviews = format!("리뷰 {}", pp.review_count);
                let last = latest.get(&squash(&pp.place.name)).map(|&d| when_text(d, today)).unwrap_or_default();
                SearchRow {
                    key: format!("r:{}|{}", pp.place.name, pp.place.address),
                    kind: SearchRowKind::Review,
                    title: pp.place.name.clone(),
                    subtitle: join_dot(&[&pp.place.category, &pp.place.address]),
                    date_line: join_dot(&[&rating, &reviews, &format!("♥ {}", pp.hearts), &last]),
                    day: None,
                    place: Some(pp.place.clone()),
                    first_visit: false,
                    episode_id: None,
                    course: None,
                    review_place: Some(pp),
                    post: None,
                }
            })
            .collect();
        Ok(group_of(SearchGroupKind::Community, "여행 리뷰", rows, more))
    }

    fn board_group(&self, q: &Query, lo: Option<i64>, hi: Option<i64>, today: i64, limit: usize) -> Result<Option<SearchGroup>, CoreError> {
        let groups = term_groups(&q.terms);
        let posts = self.board_posts(CommunitySort::Popular, 500)?;
        let mut hits: Vec<(BoardPost, u32, usize)> = posts
            .into_iter()
            .filter(|p| in_range(Some(self.local_day(p.created_ms)), lo, hi))
            .filter_map(|p| {
                let fields = [
                    (squash(&p.title), 3),
                    (squash(&p.tags.join(" ")), 3),
                    (squash(p.region.as_deref().unwrap_or("")), 2),
                    (squash(&p.body), 1),
                ];
                let (score, matched) = text_match(&fields, &groups, q.single.as_deref());
                (matched > 0 || !q.has_words()).then_some((p, score, matched))
            })
            .collect();
        keep_best_coverage(&mut hits);
        hits.sort_by(|a, b| b.1.cmp(&a.1).then(b.0.hearts.cmp(&a.0.hearts)).then(b.0.created_ms.cmp(&a.0.created_ms)));
        let more = hits.len().saturating_sub(limit) as u32;
        let rows = hits
            .into_iter()
            .take(limit)
            .map(|(p, _, _)| {
                let when = when_text(self.local_day(p.created_ms), today);
                SearchRow {
                    key: format!("b:{}", p.id),
                    kind: SearchRowKind::Post,
                    title: p.title.clone(),
                    subtitle: short_body(&p.body, 60),
                    date_line: join_dot(&[&when, &format!("댓글 {}", p.comment_count), &format!("♥ {}", p.hearts)]),
                    day: None,
                    place: None,
                    first_visit: false,
                    episode_id: None,
                    course: None,
                    review_place: None,
                    post: Some(p),
                }
            })
            .collect();
        Ok(group_of(SearchGroupKind::Community, "게시글", rows, more))
    }
}

fn group_of(kind: SearchGroupKind, title: &str, rows: Vec<SearchRow>, more: u32) -> Option<SearchGroup> {
    (!rows.is_empty()).then(|| SearchGroup { kind, title: title.into(), rows, more })
}

fn in_range(day: Option<i64>, lo: Option<i64>, hi: Option<i64>) -> bool {
    match day {
        Some(d) => lo.is_none_or(|l| d >= l) && hi.is_none_or(|h| d <= h),
        None => lo.is_none() && hi.is_none(),
    }
}

fn kinds_sql(kinds: &[EpisodeKind]) -> String {
    if kinds.is_empty() {
        String::new()
    } else {
        let codes: Vec<String> = kinds.iter().map(|&k| kind_code(k).to_string()).collect();
        format!(" AND e.kind IN ({})", codes.join(","))
    }
}

/// 장소 묶음 (모듈 설명 "내 기록 찾기").
fn place_group(
    conn: &Connection,
    q: &Query,
    from_ms: Option<i64>,
    to_ms: Option<i64>,
    filter: &SearchFilter,
    today: i64,
    limit: usize,
) -> Result<Option<SearchGroup>, CoreError> {
    let terms = expand_terms(&q.terms);
    let groups = term_groups(&q.terms);
    let (from, to) = (from_ms.unwrap_or(i64::MIN), to_ms.unwrap_or(i64::MAX));
    let kinds = kinds_sql(&filter.kinds);
    let read = |sql: String, p: &[&dyn rusqlite::ToSql]| -> Result<Vec<Episode>, CoreError> {
        let mut st = conn.prepare_cached(&sql)?;
        let rows = st.query_map(p, |r| episode_from_row(r, 0))?;
        Ok(rows.collect::<Result<_, _>>()?)
    };
    let eps = match match_expr(&terms) {
        Some(m) => read(
            format!(
                "SELECT {EP_COLS} FROM memory_fts JOIN episode e ON e.id = memory_fts.rowid
                 WHERE memory_fts MATCH ?1 AND e.end_ms >= ?2 AND e.start_ms <= ?3{kinds} LIMIT 600"
            ),
            &[&m, &from, &to],
        )?,
        // 한 글자만 쳤거나 날짜 · 칩만 있으면 그 기간 에피소드를 다 본다
        None if q.terms.is_empty() => read(
            format!("SELECT {EP_COLS} FROM episode e WHERE e.end_ms >= ?1 AND e.start_ms <= ?2{kinds} ORDER BY e.start_ms DESC LIMIT 3000"),
            &[&from, &to],
        )?,
        None => Vec::new(),
    };
    let mut aggs: HashMap<(String, String), PlaceAgg> = HashMap::new();
    for ep in &eps {
        for s in &ep.facts.stops {
            if s.name.trim().is_empty() || (filter.first_visit_only && !s.first_visit) {
                continue;
            }
            let fields = [(squash(&s.name), 3), (squash(&s.category), 2), (squash(&s.address), 1)];
            let (score, matched) = if q.has_words() {
                // 한 글자는 이름 안에서만
                let (sc, m) = text_match(&fields[..if q.single.is_some() { 1 } else { 3 }], &groups, q.single.as_deref());
                if m == 0 {
                    continue;
                }
                (sc, m)
            } else {
                (0, 0)
            };
            let a = aggs.entry((squash(&s.name), squash(&s.address))).or_insert_with(|| PlaceAgg {
                last: s.clone(),
                last_day: ep.facts.day.clone(),
                score,
                matched,
                days: BTreeSet::new(),
                first_visit: false,
            });
            a.days.insert(ep.facts.day.clone());
            a.first_visit |= s.first_visit;
            if s.arrive_ms > a.last.arrive_ms {
                a.last = s.clone();
                a.last_day = ep.facts.day.clone();
            }
        }
    }
    let mut items: Vec<(PlaceAgg, u32, usize)> = aggs.into_values().map(|a| (a.score, a.matched, a)).map(|(s, m, a)| (a, s, m)).collect();
    keep_best_coverage(&mut items);
    items.sort_by(|a, b| {
        b.1.cmp(&a.1)
            .then(b.0.days.len().cmp(&a.0.days.len()))
            .then(b.0.last.arrive_ms.cmp(&a.0.last.arrive_ms))
            .then_with(|| a.0.last.name.cmp(&b.0.last.name))
    });
    let more = items.len().saturating_sub(limit) as u32;
    let rows = items
        .into_iter()
        .take(limit)
        .map(|(a, _, _)| {
            let n = a.days.len();
            let when = parse_day(&a.last_day).map(|d| when_text(d, today)).unwrap_or_default();
            let date_line = if n > 1 { format!("{n}번 · 마지막 {when}") } else { when };
            SearchRow {
                key: format!("p:{}|{}", a.last.name, a.last.address),
                kind: SearchRowKind::Place,
                title: a.last.name.clone(),
                subtitle: join_dot(&[&a.last.category, &a.last.address]),
                date_line,
                day: Some(a.last_day.clone()),
                first_visit: n == 1 && a.first_visit,
                place: Some(a.last),
                episode_id: None,
                course: None,
                review_place: None,
                post: None,
            }
        })
        .collect();
    Ok(group_of(SearchGroupKind::Places, "장소", rows, more))
}

/// 날짜 · 기록 묶음: 기억 검색 결과 그대로 (하루 하나, 순위순; 낱말이 없으면 최신순).
fn day_group(
    conn: &Connection,
    q: &Query,
    from_ms: Option<i64>,
    to_ms: Option<i64>,
    filter: &SearchFilter,
    today: i64,
    limit: usize,
) -> Result<Option<SearchGroup>, CoreError> {
    if q.single.is_some() {
        return Ok(None);
    }
    let query = MemoryQuery {
        terms: q.terms.clone(),
        from_ms,
        to_ms,
        kinds: filter.kinds.clone(),
        limit: if filter.first_visit_only { 200 } else { (limit + 40) as u32 },
    };
    let mut hits = search_hits(conn, &query)?;
    if filter.first_visit_only {
        hits.retain(|h| h.episode.facts.stops.iter().any(|s| s.first_visit));
    }
    let more = hits.len().saturating_sub(limit) as u32;
    let rows = hits
        .into_iter()
        .take(limit)
        .map(|h| {
            let f = &h.episode.facts;
            let title = [h.day_title.clone(), h.episode.title.clone()]
                .into_iter()
                .flatten()
                .map(|t| t.trim().to_string())
                .find(|t| !t.is_empty())
                .unwrap_or_else(|| f.rule_label.clone());
            let mut names: Vec<&str> = Vec::new();
            for s in &f.stops {
                let n = s.name.trim();
                if !n.is_empty() && !names.contains(&n) {
                    names.push(n);
                }
            }
            let subtitle = if names.is_empty() {
                join_dot(&[&format!("{} → {}", f.from_name, f.to_name)])
            } else {
                let shown: Vec<&str> = names.iter().take(4).copied().collect();
                let extra = if names.len() > 4 { format!(" 외 {}곳", names.len() - 4) } else { String::new() };
                format!("{}{extra}", shown.join(" · "))
            };
            let when = parse_day(&f.day).map(|d| when_text(d, today)).unwrap_or_default();
            SearchRow {
                key: format!("d:{}", f.day),
                kind: SearchRowKind::Day,
                title,
                subtitle,
                date_line: join_dot(&[&when, kind_label(f.kind)]),
                day: Some(f.day.clone()),
                first_visit: f.stops.iter().any(|s| s.first_visit),
                place: h.best_stop.clone(),
                episode_id: Some(f.id),
                course: None,
                review_place: None,
                post: None,
            }
        })
        .collect();
    Ok(group_of(SearchGroupKind::Days, "날짜 · 기록", rows, more))
}

/// 자주 가는 곳 이름 (머문 날 수 순, 집 · 회사 근처 빼고) [n]개.
fn frequent_places(conn: &Connection, n: usize) -> Result<Vec<String>, CoreError> {
    let anchors: Vec<Point> = {
        let mut st = conn.prepare_cached("SELECT lat, lon FROM memory_anchor WHERE kind IN (2, 3)")?;
        let rows = st.query_map([], |r| Ok(Point { lat: r.get(0)?, lon: r.get(1)? }))?;
        rows.collect::<Result<_, _>>()?
    };
    let mut st = conn.prepare_cached(
        "SELECT p.name, p.lat, p.lon FROM place_stat s JOIN place p ON p.id = s.place_id
         WHERE p.name IS NOT NULL AND p.name != '' ORDER BY s.days DESC, s.first_day LIMIT 40",
    )?;
    let rows = st.query_map([], |r| Ok((r.get::<_, String>(0)?, Point { lat: r.get(1)?, lon: r.get(2)? })))?;
    let mut out: Vec<String> = Vec::new();
    for row in rows {
        let (name, p) = row?;
        if anchors.iter().any(|a| haversine_m(*a, p) <= ANCHOR_M) || out.contains(&name) {
            continue;
        }
        out.push(name);
        if out.len() >= n {
            break;
        }
    }
    Ok(out)
}

#[cfg(test)]
mod tests;

//! 먼저 말 걸기 — 차 상태(출발 · 잔량 · 도착)를 보고 에이전트가 먼저 한 번 제안한다. 고르기 · 문장 · 횟수 제한은 모두 여기서 하고,
//! Kotlin(`agent/ProactiveCenter.kt`)은 신호를 모아 넘기고, 카드 한 장 + 짧은 음성 + "응/아니"를 나르기만 한다.
//!
//! # 계기 세 가지
//! 1. **출발** (시동 · 기어 P→D): 오늘 요일 · 지금 시각에 늘 가던 곳이 있으면 "수요일이네요. 늘 가던 ○○로 안내할까요?"
//!    - 최근 [LOOKBACK_DAYS]일 동안 같은 요일, 지금부터 [DEPART_WINDOW_MIN]분 안에 도착한 적이 [DEPART_MIN_DAYS]번(날) 이상인 곳.
//!      `routine.rs`의 "늘 가던 날"이 오늘이면 그곳을 먼저 쓴다. 오늘 이미 들렀거나 내비 목적지가 이미 있으면 말하지 않는다.
//! 2. **잔량 부족**: 남은 주행 가능 거리(없으면 잔량 %로 어림)가 목적지까지(직선 × 1.3, `readings::range_check` 여유 포함)에 모자라면
//!    예전에 들렀던 충전소(전기차) · 주유소 중 돌아가는 거리가 가장 짧은 곳을 경유지로 제안한다.
//!    내비 경유지는 최대 2개 → 이미 2개면 말하지 않는다 (넣을 자리가 없다).
//! 3. **도착 · 주차**: 여기(200m 안)에 온 적이 있으면 회상 한 줄("1년 전 오늘도 여기 왔었어요" / "지난번엔 3월 2일에 왔었어요"),
//!    없으면 오늘 주행이 출장 규칙(`biztrip.rs`)에 맞고 아직 정하지 않았을 때 "출장으로 기록할까요?"
//!
//! # 말 거는 횟수 ([decide]가 지킨다)
//! - 내비 안내 음성이 나오는 중이면 아무것도 하지 않는다 (겹치지 않게).
//! - 하루 [MAX_PER_DAY]번, 아무 제안 사이 [MIN_GAP_MS] 간격, 계기마다 쉬는 시간([cooldown_ms]).
//! - 출발 · 잔량 제안을 "아니"라고 하면 그날은 그 계기로 다시 묻지 않는다.
//! - 횟수는 실제로 보여 준 뒤 [EodiStore::proactive_mark_shown]으로 센다 (meta 표, 키 `proactive:*`).

use std::collections::BTreeMap;

use rusqlite::Connection;

use crate::ffi::GeoPoint;
use crate::geo::{haversine_m, Point};
use crate::memory::StopFact;
use crate::recap::{anchors, episodes_between, get_meta, notable, place_key, set_meta};
use crate::store::{CoreError, EodiStore, DAY_MS};

/// 이 기간(일)의 기록을 본다 (1년 전 오늘을 찾으려고 1년보다 조금 길게).
pub const LOOKBACK_DAYS: i64 = 400;
/// 출발: 지금부터 이 시간(분) 안에 도착했던 곳만.
pub const DEPART_WINDOW_MIN: i64 = 240;
/// 출발: 같은 요일에 이만큼(날) 이상 간 곳.
pub const DEPART_MIN_DAYS: usize = 2;
/// 출발: 같은 요일 기록은 최근 이 기간(일)만 (오래된 버릇은 안 본다).
pub const DEPART_LOOKBACK_DAYS: i64 = 120;
/// 하루 최대 제안 수.
pub const MAX_PER_DAY: u32 = 4;
/// 아무 제안 사이 최소 간격.
pub const MIN_GAP_MS: i64 = 2 * 60_000;
/// 도착: 여기서 이 거리(m) 안에 들른 곳이면 같은 곳.
pub const SAME_PLACE_M: f64 = 200.0;
/// 잔량: 경유지로 돌아가는 거리가 이보다 길면(km) 제안하지 않는다.
pub const MAX_DETOUR_KM: f64 = 30.0;
/// 잔량 %만 알 때 가득 찬 차의 주행 가능 거리 어림 (km).
pub const FULL_RANGE_EV_KM: f64 = 400.0;
pub const FULL_RANGE_FUEL_KM: f64 = 600.0;
const ROAD_FACTOR: f64 = 1.3;
const MIN_MS: i64 = 60_000;
const WEEKDAYS: [&str; 7] = ["일", "월", "화", "수", "목", "금", "토"];

/// 무엇이 일어났나.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ProactiveEvent {
    /// 시동 · 기어 P→D
    Departure,
    /// 잔량을 다시 읽었다 (주행 중 몇 분마다)
    LowEnergy,
    /// 내비 목적지 도착 또는 주행 뒤 기어 P
    Arrival,
}

impl ProactiveEvent {
    fn key(self) -> &'static str {
        match self {
            ProactiveEvent::Departure => "departure",
            ProactiveEvent::LowEnergy => "energy",
            ProactiveEvent::Arrival => "arrival",
        }
    }
}

/// 계기마다 같은 제안을 다시 하기까지 쉬는 시간.
pub fn cooldown_ms(e: ProactiveEvent) -> i64 {
    match e {
        ProactiveEvent::Departure => 6 * 60 * MIN_MS,
        ProactiveEvent::LowEnergy => 30 * MIN_MS,
        ProactiveEvent::Arrival => 10 * MIN_MS,
    }
}

/// 내비가 안내 중인 목적지.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NavTarget {
    pub name: String,
    pub lat: f64,
    pub lon: f64,
}

/// 지금 차 상태 (Kotlin이 읽은 그대로, 모르면 None).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct VehicleSnapshot {
    pub here: Option<GeoPoint>,
    /// 내비 목적지 (안내 중이 아니면 None)
    pub destination: Option<NavTarget>,
    /// 내비에 이미 들어 있는 경유지 수
    pub waypoint_count: u32,
    /// 남은 주행 가능 거리 (km)
    pub range_km: Option<f64>,
    /// 배터리 · 연료 잔량 (%)
    pub energy_pct: Option<f64>,
    pub ev: bool,
    /// 내비 안내 음성이 나오는 중
    pub nav_voice: bool,
}

/// 제안이 "응"을 받으면 할 일 (실행은 Kotlin이 Pleos 내비 · 저장소로).
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum ProactiveAction {
    /// 알려 주기만 (회상)
    Inform,
    /// 그곳으로 안내
    Navigate { stop: StopFact },
    /// 지금 목적지는 그대로, [stop]을 경유지로 넣어 다시 안내
    AddWaypoint { stop: StopFact, destination: NavTarget },
    /// 그날을 출장으로 (`set_biz_mark(day, Some(true))`)
    MarkBiz { day: String },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ProactiveKind {
    Routine,
    Charge,
    Recall,
    Biz,
}

/// 카드 한 장 + 음성 한 줄.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ProactiveSuggestion {
    pub event: ProactiveEvent,
    pub kind: ProactiveKind,
    /// 카드 제목 (짧게)
    pub title: String,
    /// 카드 본문 = 읽는 말
    pub line: String,
    /// 카드 아래 흐린 한 줄
    pub detail: String,
    /// "응/아니"를 물을지 (회상은 알려 주기만)
    pub ask: bool,
    pub action: ProactiveAction,
}

/// 오늘 말 건 기록.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct CapsState {
    pub today_count: u32,
    pub last_any_ms: Option<i64>,
    pub last_event_ms: Option<i64>,
    /// 오늘 이 계기 제안을 "아니"라고 했다
    pub declined_today: bool,
}

/// 오늘 출장으로 볼 만한 주행 (아직 정하지 않은 것).
#[derive(Debug, Clone, PartialEq)]
pub struct BizCandidate {
    pub day: String,
    pub place: String,
}

/// 고르기에 쓰는 것 전부 (DB에서 꺼낸 값 + 지금 상태). [decide]는 이것만 보고 정한다.
#[derive(Debug, Clone)]
pub struct ProactiveInput {
    pub event: ProactiveEvent,
    pub now_ms: i64,
    pub tz_minutes: i32,
    pub vehicle: VehicleSnapshot,
    /// 지난 기록의 들른 곳 (이름 있고 집 · 회사가 아닌 곳, 오늘 것 포함)
    pub visits: Vec<StopFact>,
    /// "늘 가던 날"이 오늘인 곳과 그 규칙 ("매주 수요일")
    pub routine_today: Option<(StopFact, String)>,
    pub biz: Option<BizCandidate>,
    pub caps: CapsState,
}

fn local_day(ms: i64, tz: i32) -> i64 {
    (ms + tz as i64 * MIN_MS).div_euclid(DAY_MS)
}

fn local_min(ms: i64, tz: i32) -> i64 {
    (ms + tz as i64 * MIN_MS).rem_euclid(DAY_MS) / MIN_MS
}

fn weekday(day: i64) -> usize {
    (day + 4).rem_euclid(7) as usize
}

fn pt(s: &StopFact) -> Point {
    Point { lat: s.lat, lon: s.lon }
}

/// 받침 (한글 마지막 글자, 숫자는 읽는 소리로).
fn batchim(w: &str) -> Option<u32> {
    let c = w.trim_end_matches(['\'', '"', ')', ' ']).chars().last()?;
    if ('가'..='힣').contains(&c) {
        return Some((c as u32 - '가' as u32) % 28);
    }
    c.to_digit(10).map(|d| [1, 8, 0, 1, 0, 0, 1, 8, 1, 0][d as usize])
}

/// 로/으로 (ㄹ 받침은 "로").
pub(crate) fn ro(w: &str) -> &'static str {
    match batchim(w) {
        Some(b) if b != 0 && b != 8 => "으로",
        _ => "로",
    }
}

fn eul(w: &str) -> &'static str {
    if batchim(w).is_some_and(|b| b != 0) { "을" } else { "를" }
}

/// 말 걸어도 되는가 (횟수 · 간격 · 내비 음성).
fn allowed(i: &ProactiveInput) -> bool {
    if i.vehicle.nav_voice || i.caps.today_count >= MAX_PER_DAY {
        return false;
    }
    if i.caps.last_any_ms.is_some_and(|t| i.now_ms - t < MIN_GAP_MS) {
        return false;
    }
    if i.caps.last_event_ms.is_some_and(|t| i.now_ms - t < cooldown_ms(i.event)) {
        return false;
    }
    !(i.caps.declined_today && i.event != ProactiveEvent::Arrival)
}

/// 제안 하나 고르기 (없으면 None). 모듈 설명의 규칙 그대로.
pub fn decide(i: &ProactiveInput) -> Option<ProactiveSuggestion> {
    if !allowed(i) {
        return None;
    }
    match i.event {
        ProactiveEvent::Departure => departure(i),
        ProactiveEvent::LowEnergy => low_energy(i),
        ProactiveEvent::Arrival => arrival(i),
    }
}

fn departure(i: &ProactiveInput) -> Option<ProactiveSuggestion> {
    if i.vehicle.destination.is_some() {
        return None;
    }
    let today = local_day(i.now_ms, i.tz_minutes);
    let now_min = local_min(i.now_ms, i.tz_minutes);
    let wd = weekday(today);
    let visited_today = |key: &str| i.visits.iter().any(|v| local_day(v.arrive_ms, i.tz_minutes) == today && place_key(v) == key);
    let w = WEEKDAYS[wd];
    let make = |stop: &StopFact, detail: String| ProactiveSuggestion {
        event: ProactiveEvent::Departure,
        kind: ProactiveKind::Routine,
        title: format!("늘 가던 {}", stop.name),
        line: format!("{w}요일이네요. 늘 가던 {}{} 안내할까요?", stop.name, ro(&stop.name)),
        detail,
        ask: true,
        action: ProactiveAction::Navigate { stop: stop.clone() },
    };
    if let Some((stop, pattern)) = &i.routine_today {
        if !visited_today(&place_key(stop)) {
            return Some(make(stop, format!("{pattern}마다 가던 곳")));
        }
    }
    // 곳마다: 같은 요일 · 이 시간쯤 도착한 날들, 가장 최근 기록
    let mut places: BTreeMap<String, (Vec<i64>, StopFact)> = BTreeMap::new();
    for v in &i.visits {
        let d = local_day(v.arrive_ms, i.tz_minutes);
        if d >= today || today - d > DEPART_LOOKBACK_DAYS || weekday(d) != wd {
            continue;
        }
        let m = local_min(v.arrive_ms, i.tz_minutes);
        if !(now_min..=now_min + DEPART_WINDOW_MIN).contains(&m) {
            continue;
        }
        let e = places.entry(place_key(v)).or_insert_with(|| (Vec::new(), v.clone()));
        if !e.0.contains(&d) {
            e.0.push(d);
        }
        if v.arrive_ms > e.1.arrive_ms {
            e.1 = v.clone();
        }
    }
    let (_, (days, stop)) = places
        .into_iter()
        .filter(|(k, (days, _))| days.len() >= DEPART_MIN_DAYS && !visited_today(k))
        .max_by_key(|(_, (days, s))| (days.len(), s.arrive_ms))?;
    Some(make(&stop, format!("최근 {w}요일 {}번 · 이 시간쯤 출발", days.len())))
}

fn low_energy(i: &ProactiveInput) -> Option<ProactiveSuggestion> {
    let v = &i.vehicle;
    let (here, dest) = (v.here?, v.destination.clone()?);
    if v.waypoint_count >= 2 {
        return None;
    }
    let range = v.range_km.or_else(|| v.energy_pct.map(|p| p / 100.0 * if v.ev { FULL_RANGE_EV_KM } else { FULL_RANGE_FUEL_KM }))?;
    let (h, d) = (Point::from(here), Point { lat: dest.lat, lon: dest.lon });
    let need = haversine_m(h, d) / 1000.0 * ROAD_FACTOR;
    if crate::readings::range_check(range, need, v.ev).enough {
        return None;
    }
    let word = if v.ev { "충전" } else { "주유" };
    let mut seen = std::collections::BTreeSet::new();
    let best = i
        .visits
        .iter()
        .filter(|s| s.category.contains(word) || s.name.contains(word))
        .filter(|s| seen.insert(place_key(s)))
        .filter_map(|s| {
            let to_c = haversine_m(h, pt(s)) / 1000.0 * ROAD_FACTOR;
            let detour = (haversine_m(h, pt(s)) + haversine_m(pt(s), d) - haversine_m(h, d)) / 1000.0 * ROAD_FACTOR;
            (to_c <= range && detour <= MAX_DETOUR_KM).then_some((detour, s))
        })
        .min_by(|a, b| a.0.total_cmp(&b.0))?;
    let (detour, stop) = best;
    let what = if v.ev { "배터리가" } else { "연료가" };
    Some(ProactiveSuggestion {
        event: ProactiveEvent::LowEnergy,
        kind: ProactiveKind::Charge,
        title: format!("{} 경유", stop.name),
        line: format!("{}까지 {what} 부족해요. 예전에 들렀던 {}{} 경유지로 넣을까요?", dest.name, stop.name, eul(&stop.name)),
        detail: format!("남은 거리 {}km · 목적지까지 약 {}km · 돌아가는 길 {}km", range.round() as i64, need.round() as i64, detour.round().max(0.0) as i64),
        ask: true,
        action: ProactiveAction::AddWaypoint { stop: stop.clone(), destination: dest },
    })
}

fn arrival(i: &ProactiveInput) -> Option<ProactiveSuggestion> {
    let today = local_day(i.now_ms, i.tz_minutes);
    if let Some(here) = i.vehicle.here {
        let h = Point::from(here);
        let before: Vec<&StopFact> = i
            .visits
            .iter()
            .filter(|s| local_day(s.arrive_ms, i.tz_minutes) < today && haversine_m(h, pt(s)) <= SAME_PLACE_M)
            .collect();
        let (_, tm, td) = crate::agent::dates::civil_from_days(today);
        let (ty, _, _) = crate::agent::dates::civil_from_days(today);
        // 같은 날짜(몇 년 전 오늘)가 있으면 그것, 없으면 가장 최근
        let same_day = before.iter().filter(|s| {
            let (_, m, d) = crate::agent::dates::civil_from_days(local_day(s.arrive_ms, i.tz_minutes));
            (m, d) == (tm, td)
        });
        if let Some(s) = same_day.max_by_key(|s| s.arrive_ms) {
            let (y, _, _) = crate::agent::dates::civil_from_days(local_day(s.arrive_ms, i.tz_minutes));
            let n = ty - y;
            return Some(recall(s, format!("{n}년 전 오늘도 여기 왔었어요."), format!("{y}년 {tm}월 {td}일 · {}", s.name)));
        }
        if let Some(s) = before.iter().max_by_key(|s| s.arrive_ms) {
            let d = local_day(s.arrive_ms, i.tz_minutes);
            let (y, m, dd) = crate::agent::dates::civil_from_days(d);
            let ago = today - d;
            let line = if y == ty {
                format!("지난번엔 {m}월 {dd}일에 왔었어요. {ago}일 만이에요.")
            } else if ago >= 365 {
                format!("{}년 만이에요. 지난번엔 {y}년 {m}월 {dd}일에 왔었어요.", ago / 365)
            } else {
                format!("지난번엔 작년 {m}월 {dd}일에 왔었어요. {ago}일 만이에요.")
            };
            let count = before.len();
            return Some(recall(s, line, format!("{} · 지금까지 {count}번 들름", s.name)));
        }
    }
    let b = i.biz.as_ref()?;
    let place = if b.place.is_empty() { "오늘".to_owned() } else { format!("{}에 다녀온 길,", b.place) };
    Some(ProactiveSuggestion {
        event: ProactiveEvent::Arrival,
        kind: ProactiveKind::Biz,
        title: "출장 기록".into(),
        line: format!("{place} 출장으로 기록할까요?"),
        detail: "평일 업무 시간 · 집 · 회사에서 먼 곳 (출장 관리에 들어가요)".into(),
        ask: true,
        action: ProactiveAction::MarkBiz { day: b.day.clone() },
    })
}

fn recall(s: &StopFact, line: String, detail: String) -> ProactiveSuggestion {
    ProactiveSuggestion {
        event: ProactiveEvent::Arrival,
        kind: ProactiveKind::Recall,
        title: s.name.clone(),
        line,
        detail,
        ask: false,
        action: ProactiveAction::Inform,
    }
}

/// "응/아니" 알아듣기.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ReplyIntent {
    Yes,
    No,
    Unknown,
}

const NO_WORDS: &[&str] = &["아니", "아냐", "됐어", "됐다", "괜찮아", "괜찮습니다", "싫어", "나중에", "그만", "취소", "안 해", "안해", "필요 없", "필요없", "노"];
const YES_WORDS: &[&str] = &["응", "어", "네", "예", "그래", "좋아", "좋지", "넣어", "안내해", "가자", "가줘", "해줘", "해 줘", "기록해", "오케이", "ok", "콜", "부탁", "그렇게"];

/// 짧은 대답 하나를 응/아니로 (부정을 먼저 본다: "아니 안 해").
pub fn reply_intent(text: &str) -> ReplyIntent {
    let t: String = text.trim().to_lowercase().chars().filter(|c| !matches!(c, '.' | ',' | '!' | '?' | '~')).collect();
    if t.is_empty() {
        return ReplyIntent::Unknown;
    }
    if NO_WORDS.iter().any(|w| if *w == "노" { t == "노" } else { t.contains(w) }) {
        return ReplyIntent::No;
    }
    let short = |w: &str| w.chars().count() == 1;
    if YES_WORDS.iter().any(|w| if short(w) { t == *w || t.starts_with(&format!("{w} ")) || t.starts_with(&format!("{w}요")) } else { t.contains(w) }) {
        return ReplyIntent::Yes;
    }
    ReplyIntent::Unknown
}

/// 에이전트 대화의 다음 말 (빠른 길, LLM 없이). [Pass]면 기억 에이전트에 그대로 묻는다.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum FollowUpIntent {
    /// "주말에 바다 가자" → 기억 에이전트가 알아듣는 코스 추천 질문으로 ([query])
    Plan { query: String, theme: String },
    /// "같이 갈 사람 초대해줘"
    Invite,
    /// "출발하자" · "안내해줘" (앞에서 짠 코스로 안내 시작)
    Start,
    Pass,
}

const THEMES: &[(&str, &[&str])] = &[
    ("바다", &["바다", "해변", "바닷가", "해수욕장"]),
    ("산", &["산에", "산으로", "등산", "계곡"]),
    ("호수", &["호수", "강변"]),
    ("야경", &["야경"]),
];
const PLAN_WORDS: &[&str] = &["가자", "갈까", "가고 싶", "가고싶", "놀러", "떠나", "다녀오"];

/// 대화 다음 말 알아듣기. [has_plan]: 앞에서 코스를 이미 짰다 (그때만 "출발하자"를 안내 시작으로 본다).
pub fn follow_up_intent(text: &str, has_plan: bool) -> FollowUpIntent {
    let t = text.trim();
    if ["초대", "같이 갈 사람", "같이갈 사람", "같이 가기", "모임 만들", "친구 불러", "친구도 불러", "친구들 불러"].iter().any(|w| t.contains(w)) {
        return FollowUpIntent::Invite;
    }
    let recall_q = ["어디였", "어디 갔", "언제", "뭐 먹"].iter().any(|w| t.contains(w));
    if !recall_q && PLAN_WORDS.iter().any(|w| t.contains(w)) {
        if let Some((theme, _)) = THEMES.iter().find(|(_, ws)| ws.iter().any(|w| t.contains(w))) {
            return FollowUpIntent::Plan { query: format!("{theme} 보러 가기 좋은 드라이브 코스 추천해줘"), theme: (*theme).to_owned() };
        }
    }
    if has_plan {
        let bare = t.trim_end_matches(['.', '!', '~', ' ']);
        if ["출발", "안내해", "안내 시작", "안내시작", "길 안내", "이제 가자"].iter().any(|w| bare.contains(w)) || ["가자", "가요", "고고", "ㄱㄱ"].contains(&bare) {
            return FollowUpIntent::Start;
        }
    }
    FollowUpIntent::Pass
}

// ---- 저장소 쪽 (meta 표로 횟수 기록, DB에서 고르기 재료 꺼내기) ----

fn caps_state(store: &EodiStore, conn: &Connection, event: ProactiveEvent, now_ms: i64) -> rusqlite::Result<CapsState> {
    let today = store.local_day(now_ms);
    let same_day = get_meta(conn, "proactive:day")? == Some(today);
    Ok(CapsState {
        today_count: if same_day { get_meta(conn, "proactive:count")?.unwrap_or(0) as u32 } else { 0 },
        last_any_ms: get_meta(conn, "proactive:last")?,
        last_event_ms: get_meta(conn, &format!("proactive:last:{}", event.key()))?,
        declined_today: get_meta(conn, &format!("proactive:declined:{}", event.key()))? == Some(today),
    })
}

/// 지금 상태로 제안 하나 (모듈 설명). 횟수는 세지 않는다 → 보여 줬으면 [mark_shown].
pub fn suggestion(store: &EodiStore, event: ProactiveEvent, now_ms: i64, vehicle: VehicleSnapshot) -> Result<Option<ProactiveSuggestion>, CoreError> {
    let today = store.local_day(now_ms);
    // 출장 판정은 따로 잠금을 잡으므로 먼저 (도착일 때만)
    let biz = if event == ProactiveEvent::Arrival {
        let (y, m, _) = crate::agent::dates::civil_from_days(today);
        let day = crate::store::format_day(today);
        store.biz_trips(format!("{y:04}-{m:02}"))
            .ok()
            .and_then(|mo| mo.trips.into_iter().rev().find(|t| t.day == day && !t.marked))
            .map(|t| BizCandidate { day, place: t.stops.first().map(|s| s.name.clone()).unwrap_or_default() })
    } else {
        None
    };
    let routine_today = if event == ProactiveEvent::Departure {
        store.routine_nudge(now_ms)?.filter(|n| n.days_until == 0).map(|n| (n.place, n.pattern))
    } else {
        None
    };
    let conn = store.read()?;
    let anchors = anchors(&conn)?;
    let visits: Vec<StopFact> = episodes_between(&conn, today - LOOKBACK_DAYS, today)?
        .into_iter()
        .flat_map(|e| e.facts.stops)
        .filter(|s| notable(s, &anchors))
        .collect();
    let caps = caps_state(store, &conn, event, now_ms)?;
    let input = ProactiveInput { event, now_ms, tz_minutes: store.tz_minutes(), vehicle, visits, routine_today, biz, caps };
    Ok(decide(&input))
}

/// 제안을 실제로 보여 줬다 (하루 횟수 · 간격 · 계기 쉬는 시간을 센다).
pub fn mark_shown(store: &EodiStore, event: ProactiveEvent, now_ms: i64) -> Result<(), CoreError> {
    let today = store.local_day(now_ms);
    let conn = store.lock_side()?;
    let count = if get_meta(&conn, "proactive:day")? == Some(today) { get_meta(&conn, "proactive:count")?.unwrap_or(0) } else { 0 };
    set_meta(&conn, "proactive:day", today)?;
    set_meta(&conn, "proactive:count", count + 1)?;
    set_meta(&conn, "proactive:last", now_ms)?;
    set_meta(&conn, &format!("proactive:last:{}", event.key()), now_ms)?;
    Ok(())
}

/// "아니"라고 했다 → 오늘은 그 계기로 다시 묻지 않는다.
pub fn mark_declined(store: &EodiStore, event: ProactiveEvent, now_ms: i64) -> Result<(), CoreError> {
    let today = store.local_day(now_ms);
    let conn = store.lock_side()?;
    set_meta(&conn, &format!("proactive:declined:{}", event.key()), today)?;
    Ok(())
}

/// 횟수 기록을 지운다 (시연 · 디버그).
pub fn reset(store: &EodiStore) -> Result<(), CoreError> {
    let conn = store.lock_side()?;
    conn.execute("DELETE FROM meta WHERE key LIKE 'proactive:%'", [])?;
    Ok(())
}

#[cfg(test)]
mod tests;

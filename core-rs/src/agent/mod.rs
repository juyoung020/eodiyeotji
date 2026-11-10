//! 기억 에이전트 — "작년 여름 바다 어디였지?" → 짧은 답 + 추천 카드 → "거기로 안내해줘" (docs/70_기억_검색_설계.md §2·§4).
//!
//! 속도 우선 구조:
//! 1. **빠른 길 (LLM 없음, 수 ms):** 규칙으로 날짜 말("작년 여름", "지난주", "8월 14일")과 검색어를 뽑아 바로 기억 검색 →
//!    추천 카드·틀 문장 답·안내 제안. 확실하면 여기서 끝난다.
//! 2. **LLM (필요할 때만):** 결과가 없거나, 사람·"거기/그때" 같은 앞 대화 말이 있거나, 후속 질문이면 도구 루프를 돈다.
//!    빠른 길 결과는 [상황]에 데이터로 넣어 LLM이 검색 도구를 다시 부르지 않아도 되게 한다.
//! 3. **안내는 제안만:** `confirm(proposal_id)`(또는 다음 말 "응/안내해줘")이 있어야 실제 내비 명령(`NavAction`)을 돌려준다.
//!
//! 근처 추천 코스(`courses` 모듈)도 빠른 길로 푼다: "여기 근처 드라이브 추천코스" → 커뮤니티 인기 코스 상위 k개 목록 + 1번 안내 제안.
//! 목록은 세션에 남아 "2번으로 가자"·"더 가까운 데"·"바다 쪽은?"을 규칙으로 바로 푼다 (LLM 없이).
//!
//! Kotlin은 `ask`를 백그라운드 스레드에서 부르고, `AgentListener` 콜백을 화면·TTS로 옮기고, `NavAction`을 Pleos 내비로 실행만 한다.
//! LLM: OpenAI 호환 서버 (개발: 개발 PC의 llama.cpp `llama-server` + Qwen3.5-9B). 서버가 없으면 빠른 길만으로 동작한다.

/// 에이전트 로그. Android에서는 표준 오류(stderr)가 버려지므로 logcat(태그 `EodiRust`)으로, 그 밖(테스트)에서는 stderr로.
macro_rules! alog {
    ($($arg:tt)*) => { $crate::agent::log_line(&format!($($arg)*)) };
}
pub(crate) use alog;

#[cfg(target_os = "android")]
pub(crate) fn log_line(msg: &str) {
    use std::ffi::{c_char, c_int, CString};
    #[link(name = "log")]
    extern "C" {
        fn __android_log_write(prio: c_int, tag: *const c_char, text: *const c_char) -> c_int;
    }
    const INFO: c_int = 4;
    let text = CString::new(msg.replace('\0', " ")).unwrap_or_default();
    // SAFETY: 두 문자열 모두 NUL로 끝나는 C 문자열이고 호출 동안 살아 있다.
    unsafe {
        __android_log_write(INFO, c"EodiRust".as_ptr(), text.as_ptr());
    }
}

#[cfg(not(target_os = "android"))]
pub(crate) fn log_line(msg: &str) {
    eprintln!("{msg}");
}

pub mod courses;
pub mod dates;
pub mod label;
pub mod llm;
pub mod prompts;
mod runner;
pub mod tools;
pub mod words;

#[cfg(test)]
mod course_tests;
#[cfg(test)]
mod vague_tests;
#[cfg(test)]
mod eval;
#[cfg(test)]
mod tests;

use std::collections::{HashMap, VecDeque};
use std::sync::atomic::{AtomicBool, AtomicI64, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use serde_json::Value;

use crate::community::drive::{DriveRouteRecommendation, DriveRouteSort};
use crate::ffi::GeoPoint;
use crate::memory::{CourseLeg, CourseRoute, DayEnrichment, DayFacts, Episode, MemoryHit, MemoryQuery, StopFact};
use crate::store::{CoreError, EodiStore};

pub use llm::LlmConfig;

/// 질문 한 번의 상황.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AgentContext {
    /// 지금 (UTC epoch ms). "작년 여름"을 이 날짜 기준으로 푼다.
    pub now_ms: i64,
    /// 현지 시간대 (분, 한국 = 540)
    pub tz_offset_minutes: i32,
    /// 지금 차 위치. 있으면 코스를 지금 위치에서 가장 짧은 순서로 짠다.
    pub current_location: Option<GeoPoint>,
    /// 주행 중이면 답을 TTS용 한 문장으로 더 짧게.
    pub is_driving: bool,
}

/// 실행할 내비 명령. `confirm` 뒤에만 Kotlin에 넘어간다 → Kotlin이 PleosNavi로 실행한다.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum NavAction {
    /// 코스: 구간마다 경유지 ≤ 2 + 목적지. 첫 구간을 요청하고, 도착(onDestinationArrived)하면 다음 구간.
    Course { episode_id: i64, title: String, legs: Vec<CourseLeg> },
    /// 커뮤니티 드라이브 코스(경치 길 [post_id]): 시작점까지 → 끝점까지, 경유지 없는 두 구간. 구간 규칙은 [NavAction::Course]와 같다.
    SharedCourse { post_id: String, title: String, legs: Vec<CourseLeg> },
    /// 들른 곳 한 곳으로 안내
    Place { stop: StopFact },
    /// 차량 내비 장소 검색 화면을 이 낱말로 연다
    Search { keyword: String },
}

/// 운전자 확인을 기다리는 안내 제안. [summary]: 무엇을 실행하는지 한 줄 (확인 카드용).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NavProposal {
    pub id: String,
    pub summary: String,
    pub action: NavAction,
}

/// 답을 누가 만들었나.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AnswerSource {
    /// 규칙 + 기기 안 검색만 (LLM 없음)
    FastPath,
    /// LLM 도구 루프
    Llm,
    /// LLM이 없거나 꺼져 있어 기기 안 검색으로 대신
    Fallback,
    /// 대기 중인 제안에 대한 "응/아니"
    Confirm,
}

/// 지연 측정 (ms, 질문 시작부터).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AgentTiming {
    /// 첫 추천 카드까지
    pub to_recommendation_ms: Option<u64>,
    /// 첫 답 글자까지
    pub first_text_ms: Option<u64>,
    pub total_ms: u64,
    pub llm_calls: u32,
    pub llm_ms: u64,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AgentDone {
    /// 답 전체 (운전 중이면 TTS로 읽는다)
    pub answer: String,
    pub source: AnswerSource,
    pub timing: AgentTiming,
}

/// 하루 라벨링 한 차례 결과.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct LabelReport {
    pub labeled: u32,
    /// 모델 출력이 두 번 다 규칙에 안 맞아 미룬 날 수
    pub bad_output: u32,
    /// 서버가 꺼져 있어 멈췄으면 true (Kotlin은 나중에 다시 부른다)
    pub server_down: bool,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum AgentError {
    #[error("이미 처리했거나 지난 제안이에요")]
    NoSuchProposal,
}

/// 에이전트 사건을 받는 쪽 (Kotlin이 구현한다). `ask`를 부른 스레드에서 차례대로 불린다.
#[uniffi::export(with_foreign)]
pub trait AgentListener: Send + Sync {
    /// 답 글 조각 (스트리밍). 이어 붙이면 답 전체.
    fn on_answer_delta(&self, text: String);
    /// 추천 카드 (같은 질문에서 여러 번 오면 마지막이 최신). 코스를 짰으면 [course] 포함.
    fn on_recommendation(&self, hit: MemoryHit, course: Option<CourseRoute>);
    /// 근처 추천 드라이브 길 목록 (순서 = 순위, 1번이 맨 앞). [focus]: 지금 이야기하는(안내를 제안한) 길의 자리 (0부터).
    /// 후속 말("2번으로 가자", "바다 쪽은?")로 목록이 바뀌거나 고른 길이 바뀌면 다시 온다 (마지막이 최신).
    fn on_course_recommendations(&self, courses: Vec<DriveRouteRecommendation>, focus: u32);
    /// 안내 제안 ("그 코스로 안내할까요?"). 운전자가 "예"하면 `confirm(proposal.id)`.
    fn on_action_proposal(&self, proposal: NavProposal);
    /// 운전자가 말로 "응/안내해줘"라고 확인함 → Kotlin이 [action]을 바로 실행한다.
    fn on_action_confirmed(&self, proposal_id: String, action: NavAction);
    fn on_done(&self, done: AgentDone);
    /// 실패 (화면·TTS에 그대로 보여 줄 수 있는 한국어)
    fn on_error(&self, message: String);
}

/// 에이전트가 쓰는 기기 안 기억. 실제는 [EodiStore], 테스트·평가는 가짜.
pub trait MemoryBackend: Send + Sync {
    fn search_memories(&self, q: MemoryQuery) -> Result<Vec<MemoryHit>, CoreError>;
    fn episodes_for_day(&self, day: String) -> Result<Vec<Episode>, CoreError>;
    fn course_route(&self, episode_id: i64, from: Option<GeoPoint>, optimize: bool) -> Result<CourseRoute, CoreError>;
    fn pending_day_enrichments(&self, limit: u32) -> Result<Vec<DayFacts>, CoreError>;
    fn set_day_enrichment(&self, e: DayEnrichment) -> Result<(), CoreError>;
    fn mark_day_enrichment_failed(&self, day: String, retry_after_ms: i64) -> Result<(), CoreError>;
    /// 이미 붙은 라벨 중 제목이 [bad]인 날을 다시 대기열에 (반환: 다시 넣은 날 수).
    fn requeue_day_labels_where(&self, bad: &dyn Fn(&str) -> bool) -> Result<u32, CoreError>;
    /// 근처 드라이브 길 상위 [k]개: [from]에서 [radius_km] 안 (인기 + 길 위 가장 가까운 점까지 거리).
    fn recommend_drive_routes(&self, from: GeoPoint, radius_km: f64, k: u32) -> Result<Vec<DriveRouteRecommendation>, CoreError>;
    /// 전국 인기순 드라이브 길 [k]개. [from]이 있으면 거리도 (없으면 `distance_km` -1 = 모름).
    fn popular_drive_routes(&self, from: Option<GeoPoint>, k: u32) -> Result<Vec<DriveRouteRecommendation>, CoreError>;
    /// `[from_ms, to_ms]` 동안 가장 많이 탄 인원 (좌석 센서, 모르면 None).
    fn people_between(&self, _from_ms: i64, _to_ms: i64) -> Option<u32> {
        None
    }
    /// `[from_ms, to_ms]` 동안 와이퍼가 돌았는가 (비, 모르면 None).
    fn rain_between(&self, _from_ms: i64, _to_ms: i64) -> Option<bool> {
        None
    }
}

impl MemoryBackend for EodiStore {
    fn search_memories(&self, q: MemoryQuery) -> Result<Vec<MemoryHit>, CoreError> {
        EodiStore::search_memories(self, q)
    }
    fn episodes_for_day(&self, day: String) -> Result<Vec<Episode>, CoreError> {
        EodiStore::episodes_for_day(self, day)
    }
    fn course_route(&self, episode_id: i64, from: Option<GeoPoint>, optimize: bool) -> Result<CourseRoute, CoreError> {
        EodiStore::course_route(self, episode_id, from, optimize)
    }
    fn pending_day_enrichments(&self, limit: u32) -> Result<Vec<DayFacts>, CoreError> {
        EodiStore::pending_day_enrichments(self, limit)
    }
    fn set_day_enrichment(&self, e: DayEnrichment) -> Result<(), CoreError> {
        EodiStore::set_day_enrichment(self, e)
    }
    fn mark_day_enrichment_failed(&self, day: String, retry_after_ms: i64) -> Result<(), CoreError> {
        EodiStore::mark_day_enrichment_failed(self, day, retry_after_ms)
    }
    fn requeue_day_labels_where(&self, bad: &dyn Fn(&str) -> bool) -> Result<u32, CoreError> {
        EodiStore::requeue_day_labels_where(self, bad)
    }
    fn recommend_drive_routes(&self, from: GeoPoint, radius_km: f64, k: u32) -> Result<Vec<DriveRouteRecommendation>, CoreError> {
        EodiStore::recommend_drive_routes(self, Some(from), radius_km, k)
    }
    fn popular_drive_routes(&self, from: Option<GeoPoint>, k: u32) -> Result<Vec<DriveRouteRecommendation>, CoreError> {
        Ok(EodiStore::drive_routes(self, DriveRouteSort::Popular, from, k)?.into_iter().filter(|r| !r.route.mine).collect())
    }
    fn people_between(&self, from_ms: i64, to_ms: i64) -> Option<u32> {
        EodiStore::people_between(self, from_ms, to_ms).ok().flatten()
    }
    fn rain_between(&self, from_ms: i64, to_ms: i64) -> Option<bool> {
        EodiStore::rain_between(self, from_ms, to_ms).ok().flatten()
    }
}

/// 동작 설정.
#[derive(Debug, Clone)]
pub struct AgentOptions {
    /// 빠른 길(규칙 + 기기 검색)을 먼저 쓴다
    pub fast_path: bool,
    /// 빠른 길이 확실해도 LLM으로 답을 다시 만든다 (느리지만 말이 더 자연스럽다)
    pub llm_when_confident: bool,
    /// 대화 호출 temperature (도구 고르기는 0이 가장 고르다)
    pub temperature: f64,
}

impl Default for AgentOptions {
    fn default() -> Self {
        Self { fast_path: true, llm_when_confident: false, temperature: 0.0 }
    }
}

/// 세션 (질문 사이에 남는 것).
#[derive(Default)]
pub(crate) struct Session {
    /// 지난 질문들 (한 질문 = 사용자 말 + 도구 호출·결과 + 답). 도구 호출과 결과가 갈라지지 않게 질문 단위로 자른다.
    pub turns: VecDeque<Vec<Value>>,
    pub last_activity_ms: i64,
    /// 지금 이야기하는 기억과 다른 후보 (후속 질문 "거기로 가자", "거기 말고"용)
    pub focus: Option<MemoryHit>,
    pub candidates: Vec<MemoryHit>,
    pub tools: tools::ToolCache,
    pub proposal_seq: u64,
    /// 지금 이야기하는 추천 코스 목록·고른 자리·찾은 조건 (후속 "2번으로 가자", "더 가까운 데", "바다 쪽은?"용)
    pub courses: courses::CourseState,
    /// 바로 앞 질문이 추천 코스 이야기였나 (번호 말을 코스 번호로 읽을지)
    pub course_topic: bool,
}

#[derive(uniffi::Object)]
pub struct MemoryAgent {
    backend: Arc<dyn MemoryBackend>,
    llm: Option<llm::LlmClient>,
    opts: AgentOptions,
    session: Mutex<Session>,
    /// 대기 중인 제안 (최신 하나만 유효). `ask`가 도는 중에도 `confirm`할 수 있게 세션과 따로 잠근다.
    pending: Mutex<Option<NavProposal>>,
    cancel_gen: AtomicU64,
    /// 서버가 꺼져 있다고 본 시각까지는 연결하지 않는다 (매 질문마다 연결 대기를 쓰지 않게)
    server_down_until_ms: AtomicI64,
    label_failures: Mutex<HashMap<String, u32>>,
    /// 이 실행에서 예전 규칙으로 붙은 어색한 제목을 이미 다시 대기열에 넣었나 (`label::label_pending`이 한 번만)
    label_audit_done: AtomicBool,
}

#[uniffi::export]
impl MemoryAgent {
    /// [config].base_url이 비어 있으면 LLM 없이 빠른 길만 쓴다.
    #[uniffi::constructor]
    pub fn new(store: Arc<EodiStore>, config: LlmConfig) -> Arc<Self> {
        Self::with_backend(store, config, AgentOptions::default())
    }

    /// 질문 하나. **막는 호출** — Kotlin 백그라운드 스레드에서 부른다. 사건은 [listener]로 차례대로 온다
    /// (on_recommendation·on_answer_delta·on_action_proposal … → on_done 또는 on_error 한 번).
    pub fn ask(&self, utterance: String, context: AgentContext, listener: Arc<dyn AgentListener>) {
        self.run_turn(&utterance, &context, listener.as_ref());
    }

    /// 돌고 있는 `ask`를 멈춘다 (LLM 스트림을 다음 조각에서 끊는다).
    pub fn cancel(&self) {
        self.cancel_gen.fetch_add(1, Ordering::SeqCst);
    }

    /// 제안 실행 허락 (운전자가 "예"를 눌렀을 때). 대기 중인 제안과 id가 같아야 내비 명령을 돌려준다.
    pub fn confirm(&self, proposal_id: String) -> Result<NavAction, AgentError> {
        let mut p = self.pending.lock().unwrap_or_else(|e| e.into_inner());
        match p.as_ref() {
            Some(x) if x.id == proposal_id => Ok(p.take().map(|x| x.action).expect("checked")),
            _ => Err(AgentError::NoSuchProposal),
        }
    }

    /// 제안 거절 ("아니오").
    pub fn decline(&self, proposal_id: String) {
        let mut p = self.pending.lock().unwrap_or_else(|e| e.into_inner());
        if p.as_ref().is_some_and(|x| x.id == proposal_id) {
            *p = None;
        }
    }

    /// 지금 대기 중인 제안.
    pub fn pending_proposal(&self) -> Option<NavProposal> {
        self.pending.lock().unwrap_or_else(|e| e.into_inner()).clone()
    }

    /// 대화 기억을 지운다.
    pub fn new_session(&self) {
        *self.session.lock().unwrap_or_else(|e| e.into_inner()) = Session::default();
        *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = None;
    }

    /// LLM 서버에 고정 앞부분(시스템 프롬프트 + 도구)을 미리 계산시켜 첫 질문을 빠르게 한다. 앱 시작 때 한 번 (막는 호출).
    pub fn warm_up(&self) -> bool {
        self.warm_up_inner()
    }

    /// 라벨이 필요한 날을 [limit]일까지 라벨링한다 (막는 호출, 하루 1~2초). 서버가 꺼져 있으면 멈추고 server_down.
    pub fn label_pending_days(&self, limit: u32, tz_offset_minutes: i32) -> LabelReport {
        self.label_pending(limit, tz_offset_minutes)
    }
}

impl MemoryAgent {
    pub fn with_backend(backend: Arc<dyn MemoryBackend>, config: LlmConfig, opts: AgentOptions) -> Arc<Self> {
        let llm = config.is_configured().then(|| llm::LlmClient::new(config));
        Arc::new(Self {
            backend,
            llm,
            opts,
            session: Mutex::new(Session::default()),
            pending: Mutex::new(None),
            cancel_gen: AtomicU64::new(0),
            server_down_until_ms: AtomicI64::new(0),
            label_failures: Mutex::new(HashMap::new()),
            label_audit_done: AtomicBool::new(false),
        })
    }

    fn server_down(&self) -> bool {
        wall_ms() < self.server_down_until_ms.load(Ordering::SeqCst)
    }

    fn mark_server_down(&self) {
        self.server_down_until_ms.store(wall_ms() + SERVER_DOWN_COOLDOWN_MS, Ordering::SeqCst);
    }
}

/// 질문 중 LLM 서버에 닿지 않으면 이 동안은 연결을 시도하지 않고 기기 안 검색으로 답한다 (매 질문마다 연결 대기를 쓰지 않게).
const SERVER_DOWN_COOLDOWN_MS: i64 = 15_000;

fn wall_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

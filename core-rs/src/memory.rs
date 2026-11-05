//! 기억: 에피소드(출퇴근·볼일·나들이·여행)와 AI 라벨, 기억 검색, 코스 다시 달리기.
//!
//! 역할 분담:
//! - Rust(여기): 주행·머문 곳을 에피소드로 묶고(규칙), 집·회사를 알고, 하루치 사실을 LLM에 넘길 형태로 만들고,
//!   LLM이 돌려준 제목·태그·이야기를 저장하고, 기억 검색(FTS5 + 조건 + 순위)을 하고, 코스를 내비 구간으로 나눈다.
//! - Kotlin: LLM 호출만 한다 (하루 라벨링, 질문 → `MemoryQuery` 변환, 답 문장 만들기).
//!
//! 설계: `docs/70_기억_검색_설계.md`.
//!
//! [계약 단계 껍데기] 지금은 빈 결과를 돌려준다. 실제 구현으로 교체될 예정.

use crate::ffi::GeoPoint;
use crate::store::{CoreError, EodiStore};

/// 에피소드 종류. 규칙으로 정한다 (LLM 아님).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum EpisodeKind {
    /// 집 → 회사 (출근길)
    CommuteToWork,
    /// 회사 → 집 (퇴근길)
    CommuteHome,
    /// 생활권 안 볼일 (마트·병원·학원 등 자주 가는 곳)
    Errand,
    /// 생활권 밖 나들이 (당일, 처음이거나 드문 곳)
    Outing,
    /// 여행 (멀리·오래, 또는 하룻밤 이상)
    Trip,
}

/// 들른 곳 하나 (LLM·검색 결과·코스에 공통).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StopFact {
    /// 장소 이름 (모르면 빈 문자열)
    pub name: String,
    /// 분류 (예: "두부전문점")
    pub category: String,
    pub address: String,
    pub arrive_ms: i64,
    pub leave_ms: i64,
    pub lat: f64,
    pub lon: f64,
    /// 이 장소에 처음 온 날이면 true
    pub first_visit: bool,
}

/// 에피소드 사실 (LLM 입력·검색 결과의 바탕).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct EpisodeFacts {
    pub id: i64,
    /// 현지 날짜 "YYYY-MM-DD" (시작 기준)
    pub day: String,
    pub kind: EpisodeKind,
    /// 규칙으로 만든 기본 라벨 (예: "출근길", "퇴근길 · 이마트 들름", "강릉 나들이"). LLM 제목이 없을 때 쓴다.
    pub rule_label: String,
    pub start_ms: i64,
    pub end_ms: i64,
    pub distance_m: f64,
    pub from_name: String,
    pub to_name: String,
    /// 머문 곳 (도착 순서)
    pub stops: Vec<StopFact>,
    /// 대표 지역 (예: "강원 강릉시"). 모르면 빈 문자열.
    pub region: String,
}

/// 하루치 사실 (LLM 라벨링 한 번의 입력).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DayFacts {
    pub day: String,
    /// 요일 ("월"~"일")
    pub weekday: String,
    pub episodes: Vec<EpisodeFacts>,
    pub total_distance_m: f64,
}

/// LLM이 붙인 에피소드 라벨.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct EpisodeLabel {
    pub episode_id: i64,
    /// 센스 있는 짧은 제목 (예: "퇴근길에 성수 카페 한 잔")
    pub title: String,
    pub tags: Vec<String>,
}

/// LLM이 돌려준 하루 라벨링 결과.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DayEnrichment {
    pub day: String,
    /// 하루 제목 (예: "비 오는 날 강릉 순두부 원정")
    pub title: String,
    /// 한두 문장 이야기
    pub summary: String,
    /// 검색용 태그 (예: "바다", "여행", "가족", "맛집"). 동의어도 넣는다 ("해변", "해수욕장").
    pub tags: Vec<String>,
    pub episodes: Vec<EpisodeLabel>,
}

/// 에피소드 (사실 + 라벨).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Episode {
    pub facts: EpisodeFacts,
    /// LLM 제목 (없으면 None → 화면은 rule_label)
    pub title: Option<String>,
    pub tags: Vec<String>,
}

/// 기억 검색 조건. Kotlin이 LLM(또는 규칙)으로 질문을 이 형태로 바꿔 넘긴다.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MemoryQuery {
    /// 의미어·키워드 (동의어 확장 포함, 예: ["바다", "해변", "해수욕장"])
    pub terms: Vec<String>,
    /// 기간 (UTC epoch ms, 없으면 전체)
    pub from_ms: Option<i64>,
    pub to_ms: Option<i64>,
    /// 종류 제한 (비면 전체, 보통 출퇴근은 빼고 찾는다)
    pub kinds: Vec<EpisodeKind>,
    pub limit: u32,
}

/// 검색 결과 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MemoryHit {
    pub episode: Episode,
    pub day_title: Option<String>,
    pub score: f64,
    /// 질문과 가장 맞는 들른 곳 (추천 경유지)
    pub best_stop: Option<StopFact>,
}

/// 코스 한 구간 (내비 경유지 2개 + 목적지).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CourseLeg {
    pub waypoints: Vec<StopFact>,
    pub destination: StopFact,
}

/// 코스 다시 달리기 경로.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CourseRoute {
    /// 방문 순서 (optimize면 현재 위치에서 가장 짧은 순서)
    pub stops: Vec<StopFact>,
    pub legs: Vec<CourseLeg>,
    /// 예상 직선거리 합 (m)
    pub distance_m: f64,
}

#[uniffi::export]
impl EodiStore {
    /// 집·회사 위치 (내비 즐겨찾기 HOME/OFFICE). 모르면 None → Rust가 기록에서 추정한다.
    pub fn set_home_office(&self, home: Option<GeoPoint>, office: Option<GeoPoint>) -> Result<(), CoreError> {
        let _ = (home, office);
        Ok(())
    }

    /// LLM 라벨링이 필요한 날 (최신순). 출퇴근만 있는 평범한 날은 규칙 라벨로 끝나므로 나오지 않는다.
    pub fn pending_day_enrichments(&self, limit: u32) -> Result<Vec<DayFacts>, CoreError> {
        let _ = limit;
        Ok(Vec::new())
    }

    /// LLM 라벨링 결과 저장 (검색 색인도 갱신).
    pub fn set_day_enrichment(&self, enrichment: DayEnrichment) -> Result<(), CoreError> {
        let _ = enrichment;
        Ok(())
    }

    /// 라벨링 실패 (네트워크 등). `retry_after_ms` 뒤에 다시 나온다.
    pub fn mark_day_enrichment_failed(&self, day: String, retry_after_ms: i64) -> Result<(), CoreError> {
        let _ = (day, retry_after_ms);
        Ok(())
    }

    /// 하루의 에피소드 (시간순).
    pub fn episodes_for_day(&self, day: String) -> Result<Vec<Episode>, CoreError> {
        let _ = day;
        Ok(Vec::new())
    }

    /// 기억 검색 (기기 안, 수 ms). 점수 높은 순.
    pub fn search_memories(&self, query: MemoryQuery) -> Result<Vec<MemoryHit>, CoreError> {
        let _ = query;
        Ok(Vec::new())
    }

    /// 에피소드 코스를 다시 달리는 경로. `optimize`면 `from`(현재 위치)에서 가장 짧은 방문 순서로 다시 짠다.
    /// 내비 경유지 2개 제한에 맞춰 구간으로 나눈다 (`course::split_legs`).
    pub fn course_route(&self, episode_id: i64, from: Option<GeoPoint>, optimize: bool) -> Result<CourseRoute, CoreError> {
        let _ = (episode_id, from, optimize);
        Err(CoreError::InvalidInput("not implemented".into()))
    }

    /// 시연용 1년치 기록을 만든다 (출퇴근 + 주말 나들이 + 여행, 장소 이름 포함). 반환: 만든 주행 수.
    /// 앱 디버그 빌드에서만 부른다.
    pub fn seed_demo_data(&self, today_ms: i64) -> Result<u32, CoreError> {
        let _ = today_ms;
        Ok(0)
    }
}

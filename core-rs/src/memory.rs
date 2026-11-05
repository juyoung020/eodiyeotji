//! 기억: 에피소드(출퇴근·볼일·나들이·여행)와 AI 라벨, 기억 검색, 코스 다시 달리기.
//!
//! 역할 분담:
//! - Rust(여기): 주행·머문 곳을 에피소드로 묶고(규칙), 집·회사를 알고, 하루치 사실을 LLM에 넘길 형태로 만들고,
//!   LLM이 돌려준 제목·태그·이야기를 저장하고, 기억 검색(FTS5 + 조건 + 순위)을 하고, 코스를 내비 구간으로 나눈다.
//! - Kotlin: LLM 호출만 한다 (하루 라벨링, 질문 → `MemoryQuery` 변환, 답 문장 만들기).
//!
//! 설계: `docs/70_기억_검색_설계.md`.
//!
//! # 언제 계산하나
//! 주행·머문 곳이 바뀐 날(`store`의 `refresh_days`), 장소 이름이 바뀐 날, 집·회사가 바뀌면 모든 날이
//! `memory_dirty_day`에 표시되고, 다음 기억 조회(`day_summaries`·`day_detail`·이 모듈의 함수) 때 그날들만
//! 에피소드를 다시 만든다 (`episode::flush_memory`). 주행 중 조회는 오늘 하루만 다시 만든다 (수 ms).
//!
//! # 집 · 회사
//! - `set_home_office`(내비 즐겨찾기 HOME/OFFICE)가 1순위. None이면 기록에서 추정한다.
//! - 추정: 날짜별·장소별 머문 시간(`day_place_dwell`)을 모아 둔다. 집 = 밤(현지 22~06시)에 머문 시간 합이 가장 긴 장소
//!   (4시간 이상), 회사 = 평일 낮(09~18시)에 머문 시간 합이 가장 긴 장소 중 집에서 500m 넘게 떨어진 곳 (8시간 이상).
//!   합계만 다시 읽으므로 1년치도 1ms 안팎이다. 집·회사가 바뀌면 모든 날의 에피소드를 다시 만든다.
//! - 지점이 집·회사에서 250m 안이면 집·회사다.
//!
//! # 에피소드 묶기 (하루 단위, 주행 출발 날짜 기준)
//! - 그날 주행을 시간순으로 보며, 집·회사에서 출발하면 새 에피소드를 열고, 집·회사에 도착하면 닫는다.
//!   집·회사를 모르면 그날 이어진 주행 전체가 한 에피소드다.
//! - 들른 곳(`stops`) = 에피소드 시간 안에 도착한 머문 곳(집·회사 제외, 주행 안 머문 곳 포함) +
//!   마지막 도착지가 집·회사가 아니고 머문 곳도 아니면 그 도착지(`arrive_ms == leave_ms`).
//! - 종류(위에서부터 먼저 맞는 것):
//!   1. 출근길: 집에서 출발해 회사에 도착 (중간 들른 곳 포함). 2. 퇴근길: 회사 → 집.
//!   3. 여행: 들른 곳·출발·도착 중 집에서 80km 넘는 곳이 있거나, 집에서 20km 이상 떨어진 집·회사 아닌 곳에서
//!      자정을 넘겨 머묾(외박).
//!   4. 나들이: 드문 곳(그 장소에 머문 날이 전체 기록에서 3일 미만, 첫 방문 포함)에 들렀거나,
//!      생활권(집·회사에서 10km) 밖에 들름.
//!   5. 볼일: 나머지 (생활권 안 자주 가는 곳).
//! - `first_visit`: 그 장소에 처음 머문 날의 첫 들름이면 true.
//! - `region`: 대표 들른 곳(여행 = 집에서 가장 먼 곳, 나들이 = 드문·생활권 밖 곳 중 가장 오래 머문 곳,
//!   그 밖 = 가장 오래 머문 곳)의 주소 앞 두 마디 ("강원 강릉시", "서울 성동구"). 주소가 없으면 빈 문자열.
//! - `rule_label`: "출근길", "퇴근길 · 이마트 성수점 들름", "동네 볼일 · 이마트 성수점", "성수 나들이", "강릉 여행".
//!   나들이 지명은 관광지·공원·해수욕장 같은 곳이면 장소 이름, 아니면 주소의 동 이름("성수동2가" → "성수"),
//!   없으면 시·군·구 이름이다. 여행 지명은 시·군 이름("강릉"), 광역시·제주는 시·도 이름("부산").
//! - id: 에피소드 첫 주행의 출발 시각(ms). 다시 계산해도 첫 주행이 같으면 같다.
//!   다시 계산해서 id가 바뀌면(첫 주행의 첫 점이 늦게 들어온 경우 등) 같은 날·같은 순서·같은 종류였던 에피소드의
//!   LLM 라벨을 새 id로 옮긴다.
//!
//! # 하루 라벨링 대기열
//! - LLM이 필요한 날 = 나들이·여행 에피소드가 있거나, 처음 들른 곳이 있는 날. 그 밖(출퇴근·동네 볼일뿐)은
//!   규칙 제목("평소 출퇴근", "출퇴근 · 이마트 성수점 들름", "동네 볼일 · …")으로 끝나고 대기열에 나오지 않는다.
//! - 대기열(`pending_day_enrichments`): LLM이 필요하고, 아직 라벨이 없거나 라벨을 붙인 뒤 그날이 크게 바뀌었고
//!   (나들이·여행·처음 들른 곳이 있는 에피소드의 종류·들른 장소 구성이 달라짐), 다시 시도 대기 중이 아니고,
//!   기록이 끝난 날. 기록이 끝났다 = 그날이 지났거나, 마지막 위치 점이 10분 이상 전(운전을 멈춤). 최신 날부터.
//! - `DaySummary.title` = LLM 제목, 없으면 규칙 제목 (LLM이 필요한 날은 가장 큰 나들이·여행의 `rule_label`),
//!   에피소드가 없는 날(머문 곳만 있는 날)은 None. `tags`는 LLM 태그.
//!
//! # 검색
//! `search` 모듈 설명 (FTS5 + 한국어 2글자 조각 + 동의어 + 순위).
//!
//! # 저장 (DB 구조 버전 3)
//! `episode`(에피소드·LLM 라벨·`embedding` 칸은 나중 벡터 재순위용으로 비워 둠), `day_memory`(하루 규칙 제목·LLM 라벨·
//! 대기열 상태), `memory_fts`(검색 색인), `day_place_dwell`·`place_stat`(집·회사 추정, 자주 가는 곳),
//! `memory_anchor`(집·회사), `memory_dirty_day`(다시 만들 날). 버전 2 DB를 열면 표를 더하고 기존 날을 모두
//! 다시 만들 날로 표시한다 (원본·주행·장소는 그대로).

mod demo;
mod episode;
mod search;

pub(crate) use episode::{day_title, flush_memory, SCHEMA_V3};

/// 테스트용: 버전 3에서 더한 표를 지운다 (버전 2 DB 흉내).
#[cfg(test)]
pub(crate) const DROP_V3: &str = "DROP TABLE memory_dirty_day; DROP TABLE memory_anchor; DROP TABLE day_place_dwell;
    DROP TABLE place_stat; DROP TABLE episode; DROP TABLE day_memory; DROP TABLE memory_fts;";

use crate::course;
use crate::ffi::GeoPoint;
use crate::geo::Point;
use crate::store::{parse_day, CoreError, EodiStore};

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

fn bad_day(day: &str) -> CoreError {
    CoreError::InvalidInput(format!("day {day:?}"))
}

#[uniffi::export]
impl EodiStore {
    /// 집·회사 위치 (내비 즐겨찾기 HOME/OFFICE). 모르면 None → Rust가 기록에서 추정한다 (모듈 설명).
    /// 바뀌면 모든 날의 에피소드 종류를 다시 정한다. 좌표가 범위 밖·NaN이면 `InvalidInput`.
    pub fn set_home_office(&self, home: Option<GeoPoint>, office: Option<GeoPoint>) -> Result<(), CoreError> {
        for p in [home, office].into_iter().flatten() {
            if !(p.lat.is_finite() && p.lon.is_finite() && p.lat.abs() <= 90.0 && p.lon.abs() <= 180.0) {
                return Err(CoreError::InvalidInput(format!("point {p:?}")));
            }
        }
        let mut conn = self.lock()?;
        self.flush(&mut conn)?;
        episode::set_navi_anchors(&conn, home.map(Point::from), office.map(Point::from))?;
        flush_memory(self, &mut conn, true)
    }

    /// LLM 라벨링이 필요한 날 (최신순, 최대 `limit`개). 출퇴근·동네 볼일뿐인 평범한 날은 규칙 제목으로 끝나므로
    /// 나오지 않는다. 다시 시도 대기 중인 날, 아직 기록 중인 날(오늘이고 마지막 위치가 10분 안)도 빠진다 (모듈 설명).
    pub fn pending_day_enrichments(&self, limit: u32) -> Result<Vec<DayFacts>, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        episode::pending_days(self, &conn, limit, crate::places::now_ms())
    }

    /// LLM 라벨링 결과 저장 (검색 색인도 갱신). 저장한 뒤 그날은 대기열에서 빠지고, 나중에 그날 나들이·여행이
    /// 크게 바뀌면 다시 나온다. `episodes`의 없는 id(그날 에피소드가 아님)는 무시한다.
    /// 날짜 형식이 틀리거나, 그날 에피소드가 없거나, 제목이 비었으면 `InvalidInput`.
    pub fn set_day_enrichment(&self, enrichment: DayEnrichment) -> Result<(), CoreError> {
        let d = parse_day(&enrichment.day).ok_or_else(|| bad_day(&enrichment.day))?;
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        episode::save_enrichment(&mut conn, d, &enrichment)
    }

    /// 라벨링 실패 (네트워크 등). 지금부터 `retry_after_ms` 뒤에 다시 나온다 (0 이하면 바로).
    /// 날짜 형식이 틀리거나 그날 에피소드가 없으면 `InvalidInput`.
    pub fn mark_day_enrichment_failed(&self, day: String, retry_after_ms: i64) -> Result<(), CoreError> {
        let d = parse_day(&day).ok_or_else(|| bad_day(&day))?;
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let at = crate::places::now_ms().saturating_add(retry_after_ms.max(0));
        if conn.execute("UPDATE day_memory SET retry_at_ms = ?2 WHERE day = ?1", [d, at])? == 0 {
            return Err(CoreError::InvalidInput(format!("day {day}: no episodes")));
        }
        Ok(())
    }

    /// 하루의 에피소드 (시간순). 기록이 없는 날은 빈 목록. 날짜 형식이 틀리면 `InvalidInput`.
    pub fn episodes_for_day(&self, day: String) -> Result<Vec<Episode>, CoreError> {
        let d = parse_day(&day).ok_or_else(|| bad_day(&day))?;
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        episode::episodes_of_day(&conn, d)
    }

    /// 기억 검색 (기기 안, 1년치 수 ms). 점수 높은 순으로 최대 `limit`개 (0이면 10개, 많아도 200개).
    /// `terms`가 모두 비면 기간·종류만으로 최신순.
    pub fn search_memories(&self, query: MemoryQuery) -> Result<Vec<MemoryHit>, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        search::search(&conn, &query)
    }

    /// 에피소드 코스를 다시 달리는 경로. `optimize`면 `from`(현재 위치)에서 가장 짧은 방문 순서로 다시 짠다
    /// (들른 곳 12개까지는 정확한 최단, 넘으면 근사; `from`이 없으면 첫 곳은 그대로 두고 나머지 순서만).
    /// 내비 경유지 2개 제한에 맞춰 구간으로 나눈다 (`course::split_legs`).
    /// 들른 곳이 없는 에피소드(출퇴근 등)는 도착지 하나짜리 코스. 없는 id면 `InvalidInput`.
    /// `distance_m` = `from`(있으면)부터 방문 순서대로 직선(대원) 거리 합.
    pub fn course_route(&self, episode_id: i64, from: Option<GeoPoint>, optimize: bool) -> Result<CourseRoute, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let stops = episode::course_stops(&conn, episode_id)?
            .ok_or_else(|| CoreError::InvalidInput(format!("episode {episode_id} not found")))?;
        drop(conn);
        let pts: Vec<Point> = stops.iter().map(|s| Point { lat: s.lat, lon: s.lon }).collect();
        let origin = from.map(Point::from);
        let order: Vec<usize> = if optimize { course::best_order(origin, &pts) } else { (0..pts.len()).collect() };
        let distance_m = course::path_length_m(origin, &pts, &order);
        let ordered: Vec<StopFact> = order.iter().map(|&i| stops[i].clone()).collect();
        let ordered_pts: Vec<Point> = order.iter().map(|&i| pts[i]).collect();
        // split_legs는 순서를 지키며 앞에서부터 (경유지 ≤2 + 목적지)로 자른다 → 같은 순서로 StopFact를 붙인다.
        let mut it = ordered.iter().cloned();
        let legs = course::split_legs(&ordered_pts)
            .into_iter()
            .map(|l| {
                let waypoints: Vec<StopFact> = it.by_ref().take(l.waypoints.len()).collect();
                let destination = it.next().expect("split_legs keeps every stop");
                CourseLeg { waypoints, destination }
            })
            .collect();
        Ok(CourseRoute { stops: ordered, legs, distance_m })
    }

    /// 시연용 1년치 기록을 만든다 (출퇴근 + 주말 나들이 + 여행, 장소 이름 포함). 반환: 만든 주행 수.
    /// 앱 디버그 빌드에서만 부른다. 내용·규칙은 `demo` 모듈 설명 (이미 만들었으면 아무것도 하지 않고 0).
    pub fn seed_demo_data(&self, today_ms: i64) -> Result<u32, CoreError> {
        if today_ms <= 0 {
            return Err(CoreError::InvalidInput(format!("today_ms {today_ms}")));
        }
        demo::seed(self, today_ms)
    }
}

#[cfg(test)]
mod tests;

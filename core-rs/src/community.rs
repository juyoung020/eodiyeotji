//! 커뮤니티 · 여행 리뷰 · 추천 코스 (계약).
//!
//! - 커뮤니티 탭: 사람들이 공유한 드라이브 코스(들른 곳 순서) 피드. 하트, 인기순·최신순, 내 기록에서 코스 공유.
//! - 여행 리뷰 탭: 들른 곳(경유지) 리뷰. 하트, 인기순·최신순, 인기 장소 순위.
//! - 추천 코스: "여기 근처 드라이브 추천코스" → 지금 위치 근처에서 하트가 많은 코스 상위 k개 (에이전트 도구).
//!
//! 지금은 기기 안 저장소 + 시연용 커뮤니티 데이터([seed_community_demo])로 동작한다. 2차 단계에서 같은 모양으로
//! 서버(`server/`)와 맞춘다 — 그래서 id는 문자열, 작성자는 이름 문자열이다.
//!
//! 순위 계산(인기도·거리 감쇠)·하트 집계는 모두 여기(Rust)에서 하고, Kotlin은 받은 순서대로 그리기만 한다.

use crate::ffi::GeoPoint;
use crate::memory::StopFact;
use crate::store::{CoreError, EodiStore};

/// 정렬 기준.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum CommunitySort {
    /// 하트 많은 순 (같으면 최신)
    Popular,
    /// 최신순
    Recent,
}

/// 위치 조건: [center]에서 [radius_km] 안. None이면 전국.
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct NearFilter {
    pub center: GeoPoint,
    pub radius_km: f64,
}

/// 공유된 드라이브 코스 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CoursePost {
    pub id: String,
    /// 예: "강릉 바다 순두부 당일치기"
    pub title: String,
    /// 한두 문장 소개
    pub body: String,
    /// 작성자 표시 이름 (예: "바다좋아", 내 글이면 "나")
    pub author: String,
    pub mine: bool,
    /// 지역 (예: "강원 강릉시")
    pub region: String,
    pub tags: Vec<String>,
    /// 들른 곳 순서 (사진은 이름·좌표로 구글 대표 사진을 받는다)
    pub stops: Vec<StopFact>,
    /// 코스 전체 거리(m)·걸린 시간(ms, 출발~도착)
    pub distance_m: f64,
    pub duration_ms: i64,
    pub created_ms: i64,
    pub hearts: u32,
    pub hearted_by_me: bool,
}

/// 들른 곳(경유지) 리뷰 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PlaceReview {
    pub id: String,
    pub place: StopFact,
    pub author: String,
    pub mine: bool,
    /// 1~5
    pub rating: u8,
    pub text: String,
    pub created_ms: i64,
    pub hearts: u32,
    pub hearted_by_me: bool,
}

/// 인기 장소 (리뷰·하트를 장소별로 모은 것).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PopularPlace {
    pub place: StopFact,
    /// 그 장소 리뷰들의 하트 합 + 그 장소가 들어간 코스들의 하트 합
    pub hearts: u32,
    pub review_count: u32,
    /// 리뷰 평균 별점 (리뷰 없으면 0)
    pub avg_rating: f32,
}

/// 추천 코스 (에이전트 도구 결과). [score]가 클수록 위, [distance_km]는 지금 위치~코스 첫 곳.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CourseRecommendation {
    pub post: CoursePost,
    pub distance_km: f64,
    pub score: f64,
}

#[uniffi::export]
impl EodiStore {
    /// 커뮤니티 코스 피드.
    pub fn community_courses(&self, sort: CommunitySort, near: Option<NearFilter>, limit: u32) -> Result<Vec<CoursePost>, CoreError> {
        let _ = (sort, near, limit);
        Ok(vec![])
    }

    /// 여행 리뷰 피드.
    pub fn place_reviews(&self, sort: CommunitySort, near: Option<NearFilter>, limit: u32) -> Result<Vec<PlaceReview>, CoreError> {
        let _ = (sort, near, limit);
        Ok(vec![])
    }

    /// 인기 장소 순위 (하트 합 → 리뷰 수 → 별점 순).
    pub fn popular_places(&self, near: Option<NearFilter>, limit: u32) -> Result<Vec<PopularPlace>, CoreError> {
        let _ = (near, limit);
        Ok(vec![])
    }

    /// 코스 하트 누르기/취소. 반환: 바뀐 하트 수.
    pub fn toggle_course_heart(&self, id: String) -> Result<u32, CoreError> {
        Err(CoreError::InvalidInput(format!("코스 없음: {id}")))
    }

    /// 리뷰 하트 누르기/취소. 반환: 바뀐 하트 수.
    pub fn toggle_review_heart(&self, id: String) -> Result<u32, CoreError> {
        Err(CoreError::InvalidInput(format!("리뷰 없음: {id}")))
    }

    /// 내 기록의 에피소드(나들이·여행)를 코스로 공유한다. 집·회사 근처 곳은 빼고 올린다.
    pub fn share_course(&self, episode_id: i64, title: String, body: String, now_ms: i64) -> Result<CoursePost, CoreError> {
        let _ = (title, body, now_ms);
        Err(CoreError::InvalidInput(format!("에피소드 없음: {episode_id}")))
    }

    /// 들른 곳 리뷰 쓰기.
    pub fn add_place_review(&self, place: StopFact, rating: u8, text: String, now_ms: i64) -> Result<PlaceReview, CoreError> {
        let _ = (place, rating, text, now_ms);
        Err(CoreError::InvalidInput("아직 구현 전".into()))
    }

    /// 근처 드라이브 추천 코스 상위 [k]개: 인기(하트)와 지금 위치에서의 거리를 함께 본다 (에이전트 `courses` 도구).
    pub fn recommend_drive_courses(&self, from: GeoPoint, radius_km: f64, k: u32) -> Result<Vec<CourseRecommendation>, CoreError> {
        let _ = (from, radius_km, k);
        Ok(vec![])
    }

    /// 시연용 커뮤니티(가상의 사용자·코스·리뷰·하트)를 넣는다 (디버그 빌드에서만 부른다). 이미 있으면 0.
    pub fn seed_community_demo(&self, now_ms: i64) -> Result<u32, CoreError> {
        let _ = now_ms;
        Ok(0)
    }
}

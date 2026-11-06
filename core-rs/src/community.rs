//! 커뮤니티: 게시판 · 드라이브 코스 · 여행 리뷰.
//!
//! - 게시판: 질문·이야기 글과 댓글. 하트, 인기순·최신순.
//! - 드라이브 코스: 차에서 즐기는 경치 길 하나 (들를 곳 없음, 강변북로 한강 야경·인천대교·헌화로 등). [drive] 모듈.
//!   에이전트 "여기 근처 드라이브 추천코스"도 이 길들을 추천한다.
//! - 여행 리뷰: 식당·카페·관광지 리뷰와 인기 장소 순위(하트·별점), 그리고 **여행 코스 후기**(들른 곳을 차례로 이은 코스:
//!   쉼터·전망대·식당 순서, 하트, 내 기록(나들이·여행 에피소드)에서 올리기, 이 코스로 안내). 내가 들른 곳에서 리뷰 쓰기.
//!
//! 지금은 기기 안 저장소 + 시연용 커뮤니티 데이터([EodiStore::seed_community_demo])로 동작한다. 2차 단계에서 같은 모양으로
//! 서버(`server/`)와 맞춘다 — 그래서 id는 문자열, 작성자는 이름 문자열이다.
//!
//! 순위 계산(인기도·거리 감쇠)·하트 집계·같은 장소 묶기·공유할 곳 거르기는 모두 여기(Rust)에서 하고,
//! Kotlin은 받은 순서대로 그리기만 한다.
//!
//! # 저장 (DB 구조 버전 5)
//! - `community_course`: 여행 코스 후기 한 줄. 들른 곳은 JSON 배열. 하트 = `others_hearts`(다른 사람들, 서버가 셀 값) +
//!   `my_heart`(0/1, 내가 누름). 내가 올린 코스는 `id = "me-<에피소드 id>"`, `mine = 1`, `episode_id`가 있다.
//! - `place_review`: 리뷰 한 줄 (장소 이름·분류·주소·좌표를 그대로 담는다). 하트는 코스와 같은 방식.
//!   버전 9부터 키워드([REVIEW_KEYWORDS] 중 5개까지)·누구와·다녀온 때·머문 분·몇 번째 방문도 담는다 (내가 쓸 때는 기록에서 채움,
//!   [EodiStore::write_place_review]). 장소별 키워드 요약(많은 순)도 여기서 센다 ([keyword_summary]).
//! - `board_post`·`board_comment`: 게시판 글·댓글.
//! - 코스·리뷰는 다녀온 날(`visited_day`)과 그날 날씨(`weather`, 올리는 순간 받은 것)를 같이 둔다. 계절은 날짜로
//!   그때그때 계산한다 (`weather` 모듈).
//!
//! # 같은 장소 묶기
//! 리뷰와 코스의 들른 곳은 장소 id가 없다(서로 다른 사람이 올린 이름·좌표). 이름(띄어쓰기 무시)이 같고
//! [SAME_PLACE_M] 안이면 같은 장소로 본다 → 인기 장소 순위, 코스 품질(그 곳 리뷰 별점), 장소별 리뷰에 쓴다.
//!
//! # 개인정보 (내 기록에서 올리기)
//! 집·회사([PRIVACY_RADIUS_M] 안)와 이름 모르는 곳은 자동으로 빼고 그 수를 미리보기에 알려 준다.
//! 거리도 집에서 출발한 거리가 아니라 올리는 곳들 사이 거리로 다시 계산한다 (집 위치를 거꾸로 짐작할 수 없게).

use std::collections::HashSet;
use std::fmt::Write;

use rusqlite::{params, Connection, OptionalExtension, Row};
use serde_json::{json, Value};

use crate::course;
use crate::ffi::GeoPoint;
use crate::geo::{haversine_m, Point};
use crate::mapdata::{map_safe_name, DayMap, EntryBounds, MapBounds};
use crate::memory::{CourseLeg, Episode, EpisodeKind, MemoryQuery, StopFact};
use crate::store::{format_day, join_names, parse_day, split_names, CoreError, EodiStore};

pub(crate) mod demo;
pub mod drive;
mod drive_demo;
pub mod weather;

pub use weather::{open_meteo_url, parse_open_meteo_weather, season_label};

/// DB 구조 버전 5에서 더하는 표.
pub(crate) const SCHEMA_V5: &str = "
-- 드라이브 코스. stops = 들른 곳 JSON 배열, tags = U+001F로 이은 태그, visited_day = 다녀온 날(YYYY-MM-DD),
-- weather = 그날 날씨 글(\"맑음 · 14°C\", 모르면 NULL). 하트 = others_hearts(다른 사람들) + my_heart(0/1).
-- 내가 올린 코스는 id = 'me-<episode_id>'.
CREATE TABLE community_course (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    author TEXT NOT NULL,
    mine INTEGER NOT NULL,
    region TEXT NOT NULL,
    tags TEXT NOT NULL,
    stops TEXT NOT NULL,
    distance_m REAL NOT NULL,
    duration_ms INTEGER NOT NULL,
    created_ms INTEGER NOT NULL,
    visited_day TEXT NOT NULL,
    weather TEXT,
    others_hearts INTEGER NOT NULL,
    my_heart INTEGER NOT NULL DEFAULT 0,
    episode_id INTEGER
);
-- 들른 곳 리뷰. 장소는 이름·분류·주소·좌표를 그대로 담는다 (visit_ms = 다녀온 시각).
CREATE TABLE place_review (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    address TEXT NOT NULL,
    visit_ms INTEGER NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    author TEXT NOT NULL,
    mine INTEGER NOT NULL,
    rating INTEGER NOT NULL,
    text TEXT NOT NULL,
    created_ms INTEGER NOT NULL,
    visited_day TEXT NOT NULL,
    weather TEXT,
    others_hearts INTEGER NOT NULL,
    my_heart INTEGER NOT NULL DEFAULT 0
);
-- 게시판 글·댓글. 하트는 코스와 같은 방식.
CREATE TABLE board_post (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    author TEXT NOT NULL,
    mine INTEGER NOT NULL,
    region TEXT,
    tags TEXT NOT NULL,
    created_ms INTEGER NOT NULL,
    others_hearts INTEGER NOT NULL,
    my_heart INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE board_comment (
    id TEXT PRIMARY KEY,
    post_id TEXT NOT NULL,
    author TEXT NOT NULL,
    mine INTEGER NOT NULL,
    body TEXT NOT NULL,
    created_ms INTEGER NOT NULL,
    others_hearts INTEGER NOT NULL,
    my_heart INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX board_comment_post ON board_comment (post_id, created_ms);
";

/// DB 구조 버전 9에서 더하는 칸 (표, 칸, 정의). 기존 줄은 기본값(빈 키워드·모름·0)으로 그대로 읽힌다.
/// 리뷰: 키워드(U+001F로 이음), 누구와(`WithWhom` 키), 때(`TimeOfDay` 키), 머문 분, 몇 번째 방문.
const V9_COLUMNS: &[(&str, &str, &str)] = &[
    ("place_review", "keywords", "TEXT NOT NULL DEFAULT ''"),
    ("place_review", "with_whom", "TEXT"),
    ("place_review", "time_of_day", "TEXT"),
    ("place_review", "stay_min", "INTEGER NOT NULL DEFAULT 0"),
    ("place_review", "visit_nth", "INTEGER NOT NULL DEFAULT 0"),
];

/// 버전 9 칸 더하기. 이미 있는 칸은 건너뛴다 (테스트가 버전 숫자만 되돌려도 다시 올릴 수 있게).
pub(crate) fn migrate_v9(conn: &Connection) -> rusqlite::Result<()> {
    for (table, col, def) in V9_COLUMNS {
        let has: i64 =
            conn.query_row(&format!("SELECT count(*) FROM pragma_table_info('{table}') WHERE name = ?1"), [col], |r| r.get(0))?;
        if has == 0 {
            conn.execute_batch(&format!("ALTER TABLE {table} ADD COLUMN {col} {def};"))?;
        }
    }
    Ok(())
}

/// 예전 버전 DB를 흉내 내는 테스트용 (v9 칸 지우기).
#[cfg(test)]
pub(crate) fn drop_v9(conn: &Connection) {
    for (table, col, _) in V9_COLUMNS {
        conn.execute_batch(&format!("ALTER TABLE {table} DROP COLUMN {col};")).unwrap();
    }
}

/// 예전 버전 DB를 흉내 내는 테스트용 (v5 표 지우기).
#[cfg(test)]
pub(crate) const DROP_V5: &str =
    "DROP TABLE community_course; DROP TABLE place_review; DROP TABLE board_post; DROP TABLE board_comment;";

/// 이름이 같고 이 거리(m) 안이면 같은 장소.
pub const SAME_PLACE_M: f64 = 500.0;
/// 내 기록을 올릴 때 집·회사에서 이 거리(m) 안의 곳은 뺀다.
pub const PRIVACY_RADIUS_M: f64 = 1_000.0;
/// 직선거리 → 도로 거리 어림 (코스 거리 표시용).
pub const ROAD_FACTOR: f64 = 1.3;
/// 내가 쓴 글의 작성자 표시 이름.
pub const ME: &str = "나";
/// limit = 0일 때 돌려주는 개수, 최대 개수.
const DEFAULT_LIMIT: usize = 50;
const MAX_LIMIT: usize = 500;
/// 리뷰 글 최대 길이(글자).
pub const MAX_REVIEW_CHARS: usize = 500;
/// 리뷰 키워드 (네이버 지도 "이런 점이 좋았어요"처럼 정해진 것 중에서 고른다). 요약에서 수가 같으면 이 순서.
pub const REVIEW_KEYWORDS: &[&str] = &[
    "뷰가 좋아요",
    "음식이 맛있어요",
    "커피가 맛있어요",
    "사진 찍기 좋아요",
    "산책하기 좋아요",
    "드라이브하기 좋아요",
    "야경이 멋져요",
    "아이와 가기 좋아요",
    "반려동물과 가기 좋아요",
    "주차하기 편해요",
    "충전하기 편해요",
    "화장실이 깨끗해요",
    "조용해요",
    "친절해요",
    "가성비 좋아요",
];
/// 리뷰 하나에 고를 수 있는 키워드 수.
pub const MAX_REVIEW_KEYWORDS: usize = 5;
/// 장소 창 "이런 점이 좋았어요"에 보여 줄 키워드 수.
pub const KEYWORD_SUMMARY_TOP: usize = 5;

/// 누구와 갔는지 (리뷰 쓸 때 고른다).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum WithWhom {
    Alone,
    /// 연인 · 배우자
    Partner,
    Family,
    /// 아이와
    Kids,
    Friends,
    Pet,
}

impl WithWhom {
    const ALL: [WithWhom; 6] = [WithWhom::Alone, WithWhom::Partner, WithWhom::Family, WithWhom::Kids, WithWhom::Friends, WithWhom::Pet];

    pub(crate) fn key(self) -> &'static str {
        match self {
            WithWhom::Alone => "alone",
            WithWhom::Partner => "partner",
            WithWhom::Family => "family",
            WithWhom::Kids => "kids",
            WithWhom::Friends => "friends",
            WithWhom::Pet => "pet",
        }
    }

    fn from_key(k: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|w| w.key() == k)
    }
}

/// 다녀온 때 (도착 시각의 현지 시간으로 정한다: 아침 5~11시, 낮 11~17시, 저녁 17~21시, 밤 21~5시).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum TimeOfDay {
    Morning,
    Day,
    Evening,
    Night,
}

impl TimeOfDay {
    const ALL: [TimeOfDay; 4] = [TimeOfDay::Morning, TimeOfDay::Day, TimeOfDay::Evening, TimeOfDay::Night];

    pub(crate) fn from_hour(h: i64) -> Self {
        match h.rem_euclid(24) {
            5..=10 => TimeOfDay::Morning,
            11..=16 => TimeOfDay::Day,
            17..=20 => TimeOfDay::Evening,
            _ => TimeOfDay::Night,
        }
    }

    pub(crate) fn key(self) -> &'static str {
        match self {
            TimeOfDay::Morning => "morning",
            TimeOfDay::Day => "day",
            TimeOfDay::Evening => "evening",
            TimeOfDay::Night => "night",
        }
    }

    fn from_key(k: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|t| t.key() == k)
    }
}

/// 키워드 하나의 수 ("이런 점이 좋았어요" 막대 한 줄). [ratio] = 수 / 맨 위 키워드 수 (막대 길이, 0~1).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct KeywordCount {
    pub keyword: String,
    pub count: u32,
    pub ratio: f32,
}

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

/// 여행 코스 후기 하나 (들른 곳을 차례로 이은 코스).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CoursePost {
    pub id: String,
    /// 예: "강릉에서 양양까지, 바다 따라 하루 여행"
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
    /// 실제로 다녀온 날 "YYYY-MM-DD"
    pub visited_day: String,
    /// 그날 계절 (예: "가을 · 단풍철", `season_label`)
    pub season: String,
    /// 그날 날씨 (예: "맑음 · 14°C"), 모르면 None
    pub weather: Option<String>,
}

/// 들른 곳(식당·카페·관광지) 리뷰 하나.
/// 여행 리뷰 피드 한 칸: 한 장소 리뷰, 또는 여러 곳을 차례로 다녀온 여행 코스 후기.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum TravelFeedItem {
    Review { review: PlaceReview },
    Course { course: CoursePost },
}

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
    /// 들른 날 "YYYY-MM-DD"
    pub visited_day: String,
    pub season: String,
    pub weather: Option<String>,
    /// 고른 키워드 ([REVIEW_KEYWORDS] 중, 최대 5개)
    pub keywords: Vec<String>,
    /// 누구와 (모르면 None)
    pub with_whom: Option<WithWhom>,
    /// 다녀온 때 (모르면 None)
    pub time_of_day: Option<TimeOfDay>,
    /// 머문 분 (모르면 0)
    pub stay_min: u32,
    /// 글쓴이의 몇 번째 방문인지 (모르면 0)
    pub visit_nth: u32,
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
    /// 리뷰에서 가장 많이 고른 키워드 (없으면 None)
    pub top_keyword: Option<String>,
}

/// 추천 코스 (에이전트 도구 결과). [score]가 클수록 위, [distance_km]는 지금 위치~코스 첫 곳.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CourseRecommendation {
    pub post: CoursePost,
    pub distance_km: f64,
    pub score: f64,
}

/// 내 에피소드(나들이·여행)를 코스로 올리기 전 미리보기. 계산(가릴 곳·거리·계절)은 Rust가 한다.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SharePreview {
    pub episode_id: i64,
    /// 다녀온 날 "YYYY-MM-DD"
    pub day: String,
    pub season: String,
    /// 제목 기본값: 에피소드 LLM 제목 → 하루 제목 → 규칙 라벨 ("강릉 여행")
    pub title: String,
    pub region: String,
    pub kind: EpisodeKind,
    /// 올라갈 곳 (도착 순서). 집·회사 근처와 이름 모르는 곳은 이미 빠져 있다.
    /// 화면에서 체크를 풀어 더 뺄 곳은 `arrive_ms`로 [EodiStore::share_course_with]에 넘긴다.
    pub stops: Vec<StopFact>,
    /// 집·회사 근처라 자동으로 가린 곳 수 ("집·회사 근처 2곳은 자동으로 가렸어요")
    pub hidden_count: u32,
    /// 이름을 몰라 뺀 곳 수
    pub unnamed_count: u32,
    /// 올라갈 곳 사이 거리(m, 직선 × 1.3)
    pub distance_m: f64,
    /// 이미 올렸으면 true (다시 올리면 제목·소개·들른 곳만 바뀌고 하트는 남는다)
    pub shared: bool,
    /// 그날 날씨를 받을 Open-Meteo 주소 (첫 곳 좌표). Kotlin이 받아 [parse_open_meteo_weather]로 바꿔 넘긴다.
    pub weather_url: String,
}

/// 날씨 받을 주소와 그 날짜 ("YYYY-MM-DD").
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct WeatherRequest {
    pub url: String,
    pub day: String,
}

/// 게시판 글 (질문·이야기).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BoardPost {
    pub id: String,
    pub title: String,
    pub body: String,
    pub author: String,
    pub mine: bool,
    /// 관련 지역 (예: "강원 강릉시"), 없으면 None
    pub region: Option<String>,
    pub tags: Vec<String>,
    pub created_ms: i64,
    pub hearts: u32,
    pub hearted_by_me: bool,
    pub comment_count: u32,
}

/// 게시판 댓글.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BoardComment {
    pub id: String,
    pub post_id: String,
    pub author: String,
    pub mine: bool,
    pub body: String,
    pub created_ms: i64,
    pub hearts: u32,
    pub hearted_by_me: bool,
}

#[uniffi::export]
impl EodiStore {
    // ---- 여행 코스 후기 ----

    /// 여행 코스 후기 피드. [near]가 있으면 들른 곳 중 하나라도 반경 안인 코스만. `limit` 0이면 50개 (최대 500).
    pub fn community_courses(&self, sort: CommunitySort, near: Option<NearFilter>, limit: u32) -> Result<Vec<CoursePost>, CoreError> {
        let near = check_near(near)?;
        let conn = self.lock()?;
        let mut posts: Vec<CoursePost> = load_courses(&conn, None)?
            .into_iter()
            .filter(|p| near.is_none_or(|(c, r)| p.stops.iter().any(|s| haversine_m(c, stop_point(s)) <= r)))
            .collect();
        sort_by_hearts(&mut posts, sort, |p| (p.hearts, p.created_ms, p.id.as_str()));
        posts.truncate(clamp_limit(limit));
        Ok(posts)
    }

    /// 코스 하트 누르기/취소. 반환: 바뀐 하트 수. 없는 id면 `InvalidInput`.
    pub fn toggle_course_heart(&self, id: String) -> Result<u32, CoreError> {
        let conn = self.lock()?;
        toggle_heart(&conn, "community_course", &id)?.ok_or_else(|| CoreError::InvalidInput(format!("코스 없음: {id}")))
    }

    /// 근처 여행 코스 후기 상위 [k]개: 인기(하트)와 지금 위치에서의 거리, 코스 품질을 함께 본다.
    /// (에이전트 "근처 드라이브 추천코스"는 경치 길 [EodiStore::recommend_drive_routes]를 쓴다.)
    ///
    /// 점수 = 인기 × 거리 감쇠 × 품질 (클수록 위, [course_score]). 내가 올린 코스는 뺀다.
    /// - 인기 = 1 + ln(1 + 하트) — 하트 400개가 40개보다 낫지만 10배는 아니다 (로그로 눌러 거리가 묻히지 않게).
    /// - 거리 감쇠 = 0.5^(d / h): d = [from]~코스 첫 곳 직선 km, h = max([radius_km] / 3, 3km).
    ///   반경의 1/3마다 점수가 반으로 준다. d > [radius_km]인 코스는 아예 빠진다.
    /// - 품질 = 0.6 + 0.2 × 들른 곳 수 적합도 + 0.2 × 별점 적합도 (0.6~1.0)
    ///   - 들른 곳 수: 3~5곳 = 1, 2곳·6곳 이상 = 0.5, 1곳 = 0 (드라이브 코스는 쉼터 3~5곳이 알맞다)
    ///   - 별점: 코스의 들른 곳들에 달린 리뷰 평균 별점 (★-1)/4, 리뷰가 없으면 0.5 (보통)
    ///
    /// 같은 점수면 하트 많은 순, 그다음 id. [k] 0이면 3개 (최대 20). [radius_km]가 0 이하·NaN이면 30km.
    /// [from]이 틀린 좌표면 `InvalidInput`.
    pub fn recommend_drive_courses(&self, from: GeoPoint, radius_km: f64, k: u32) -> Result<Vec<CourseRecommendation>, CoreError> {
        if !valid_point(from.lat, from.lon) {
            return Err(CoreError::InvalidInput(format!("from {from:?}")));
        }
        let radius_km = if radius_km.is_finite() && radius_km > 0.0 { radius_km } else { 30.0 };
        let k = if k == 0 { 3 } else { k.min(20) } as usize;
        let conn = self.lock()?;
        let courses = load_courses(&conn, None)?;
        let groups = PlaceGroups::build(&courses, &load_reviews(&conn, None)?);
        drop(conn);
        let origin = Point::from(from);
        let mut recs: Vec<CourseRecommendation> = courses
            .into_iter()
            .filter(|p| !p.mine && !p.stops.is_empty())
            .filter_map(|p| {
                let d_km = haversine_m(origin, stop_point(&p.stops[0])) / 1000.0;
                if d_km > radius_km {
                    return None;
                }
                let ratings: Vec<f64> = p.stops.iter().filter_map(|s| groups.find(s).and_then(PlaceAgg::avg_rating)).collect();
                let avg = (!ratings.is_empty()).then(|| ratings.iter().sum::<f64>() / ratings.len() as f64);
                let score = course_score(p.hearts, d_km, radius_km, p.stops.len(), avg);
                Some(CourseRecommendation { post: p, distance_km: d_km, score })
            })
            .collect();
        recs.sort_by(|a, b| {
            b.score.total_cmp(&a.score).then(b.post.hearts.cmp(&a.post.hearts)).then_with(|| a.post.id.cmp(&b.post.id))
        });
        recs.truncate(k);
        Ok(recs)
    }

    /// 코스 지도 데이터: 들른 곳 핀만 있는 GeoJSON (`mapdata` 머문 곳과 같은 모양 — kind "stay", key "s<순서>",
    /// order = 순서(1부터), name = 지도용 이름). `day` 칸에는 코스 id가 들어간다. 없는 id면 `InvalidInput`.
    pub fn course_map(&self, post_id: String) -> Result<DayMap, CoreError> {
        let post = self.course_by_id(&post_id)?;
        Ok(stops_map(&post.id, &post.stops))
    }

    /// 코스를 내비 구간으로 나눈다 (경유지 ≤ 2 + 목적지, `course::split_legs`). 첫 구간부터 차례로 요청한다.
    /// 없는 id면 `InvalidInput`.
    pub fn course_legs(&self, post_id: String) -> Result<Vec<CourseLeg>, CoreError> {
        let post = self.course_by_id(&post_id)?;
        let pts: Vec<Point> = post.stops.iter().map(stop_point).collect();
        let mut it = post.stops.into_iter();
        Ok(course::split_legs(&pts)
            .into_iter()
            .map(|l| {
                let waypoints: Vec<StopFact> = it.by_ref().take(l.waypoints.len()).collect();
                let destination = it.next().expect("split_legs keeps every stop");
                CourseLeg { waypoints, destination }
            })
            .collect())
    }

    /// 차가 마지막으로 있던 곳 (기록의 마지막 위치 점, "내 근처" 기준). 기록이 없으면 None.
    pub fn last_known_position(&self) -> Result<Option<GeoPoint>, CoreError> {
        let conn = self.lock()?;
        Ok(conn
            .query_row("SELECT lat, lon FROM fix ORDER BY t_ms DESC LIMIT 1", [], |r| Ok(GeoPoint { lat: r.get(0)?, lon: r.get(1)? }))
            .optional()?)
    }

    // ---- 내 기록에서 코스 올리기 ----

    /// 코스로 올릴 수 있는 내 나들이·여행 미리보기 (최신순, 하루에 하나, 올릴 곳이 남는 것만). `limit` 0이면 30개.
    pub fn share_candidates(&self, limit: u32) -> Result<Vec<SharePreview>, CoreError> {
        let limit = if limit == 0 { 30 } else { limit.min(200) };
        let hits = self.search_memories(MemoryQuery {
            terms: vec![],
            from_ms: None,
            to_ms: None,
            kinds: vec![EpisodeKind::Outing, EpisodeKind::Trip],
            limit,
        })?;
        let conn = self.lock()?;
        let ctx = ShareCtx::load(self, &conn)?;
        Ok(hits.into_iter().map(|h| ctx.preview(h.episode, h.day_title)).filter(|p| !p.stops.is_empty()).collect())
    }

    /// 그날(`day` = "YYYY-MM-DD")에 코스로 올릴 수 있는 나들이·여행 미리보기 (먼저 시작한 순, 올릴 곳이 남는 것만).
    /// 타임라인 하루 상세에서 바로 "커뮤니티에 올리기"를 띄울 때 쓴다. 날짜 형식이 틀리면 `InvalidInput`.
    pub fn share_candidates_on(&self, day: String) -> Result<Vec<SharePreview>, CoreError> {
        let d = parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let hits = self.search_memories(MemoryQuery {
            terms: vec![],
            from_ms: Some(self.day_start_ms(d)),
            to_ms: Some(self.day_start_ms(d + 1)),
            kinds: vec![EpisodeKind::Outing, EpisodeKind::Trip],
            limit: 20,
        })?;
        let conn = self.lock()?;
        let ctx = ShareCtx::load(self, &conn)?;
        let mut out: Vec<SharePreview> = hits
            .into_iter()
            .filter(|h| h.episode.facts.day == day)
            .map(|h| ctx.preview(h.episode, h.day_title))
            .filter(|p| !p.stops.is_empty())
            .collect();
        out.sort_by_key(|p| p.stops.first().map_or(i64::MAX, |s| s.arrive_ms));
        Ok(out)
    }

    /// 에피소드 하나의 공유 미리보기 (제목 기본값, 올라갈 곳, 자동으로 가린 곳 수, 날씨 주소). 나들이·여행이 아니어도
    /// 만든다 (올리기는 [EodiStore::share_course_with]가 막는다). 없는 id면 `InvalidInput`.
    pub fn share_preview(&self, episode_id: i64) -> Result<SharePreview, CoreError> {
        let (ep, day_title) = self.episode_by_id(episode_id)?;
        let conn = self.lock()?;
        Ok(ShareCtx::load(self, &conn)?.preview(ep, day_title))
    }

    /// 내 기록의 에피소드(나들이·여행)를 코스로 올린다 ([EodiStore::share_course_with]에서 더 뺄 곳·날씨 없이).
    pub fn share_course(&self, episode_id: i64, title: String, body: String, now_ms: i64) -> Result<CoursePost, CoreError> {
        self.share_course_with(episode_id, title, body, vec![], None, now_ms)
    }

    /// 내 기록의 에피소드(나들이·여행)를 코스로 올린다. 집·회사 근처 곳과 이름 모르는 곳은 자동으로 빠지고
    /// (모듈 설명), [excluded_arrive_ms]에 도착 시각이 있는 곳도 뺀다 (미리보기에서 체크를 푼 곳).
    /// [weather]는 Kotlin이 미리보기의 `weather_url`로 받아 바꾼 글 (못 받았으면 None).
    /// 같은 에피소드를 다시 올리면 제목·소개·들른 곳·날씨만 바뀌고 하트·올린 시각은 그대로다.
    /// 없는 id, 나들이·여행이 아닌 에피소드, 빈 제목, 올릴 곳이 하나도 안 남으면 `InvalidInput`.
    pub fn share_course_with(
        &self,
        episode_id: i64,
        title: String,
        body: String,
        excluded_arrive_ms: Vec<i64>,
        weather: Option<String>,
        now_ms: i64,
    ) -> Result<CoursePost, CoreError> {
        let title = title.trim().to_string();
        if title.is_empty() {
            return Err(CoreError::InvalidInput("제목이 비었다".into()));
        }
        let (ep, _) = self.episode_by_id(episode_id)?;
        if !matches!(ep.facts.kind, EpisodeKind::Outing | EpisodeKind::Trip) {
            return Err(CoreError::InvalidInput(format!("나들이·여행만 올린다: {:?}", ep.facts.kind)));
        }
        let conn = self.lock()?;
        let excluded: HashSet<i64> = excluded_arrive_ms.into_iter().collect();
        let stops: Vec<StopFact> = shareable_stops(&ep.facts.stops, &privacy_anchors(&conn)?)
            .into_iter()
            .filter(|s| !excluded.contains(&s.arrive_ms))
            .collect();
        if stops.is_empty() {
            return Err(CoreError::InvalidInput("올릴 수 있는 들른 곳이 없다 (집·회사 근처·이름 모르는 곳·뺀 곳 제외)".into()));
        }
        let mut tags: Vec<String> = ep.tags.iter().map(|t| t.trim().to_string()).filter(|t| !t.is_empty()).take(5).collect();
        if tags.is_empty() {
            tags.push(if ep.facts.kind == EpisodeKind::Trip { "여행" } else { "나들이" }.to_string());
        }
        let id = format!("me-{episode_id}");
        conn.execute(
            "INSERT INTO community_course
                 (id, title, body, author, mine, region, tags, stops, distance_m, duration_ms, created_ms, visited_day, weather,
                  others_hearts, my_heart, episode_id)
             VALUES (?1, ?2, ?3, ?4, 1, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, 0, 0, ?13)
             ON CONFLICT (id) DO UPDATE SET title = excluded.title, body = excluded.body, region = excluded.region,
                 tags = excluded.tags, stops = excluded.stops, distance_m = excluded.distance_m, duration_ms = excluded.duration_ms,
                 visited_day = excluded.visited_day, weather = coalesce(excluded.weather, community_course.weather)",
            params![
                id,
                title,
                body.trim(),
                ME,
                ep.facts.region,
                join_names(&tags),
                encode_stops(&stops),
                course_distance_m(&stops),
                course_duration_ms(&stops),
                now_ms,
                ep.facts.day,
                weather::clean_weather(weather),
                episode_id
            ],
        )?;
        load_courses(&conn, Some(&id))?.pop().ok_or_else(|| CoreError::Db("올린 코스를 다시 못 읽음".into()))
    }

    // ---- 여행 리뷰 ----

    /// 여행 리뷰 피드. [near]가 있으면 그 장소가 반경 안인 리뷰만. `limit` 0이면 50개.
    pub fn place_reviews(&self, sort: CommunitySort, near: Option<NearFilter>, limit: u32) -> Result<Vec<PlaceReview>, CoreError> {
        let near = check_near(near)?;
        let conn = self.lock()?;
        let mut reviews: Vec<PlaceReview> = load_reviews(&conn, None)?
            .into_iter()
            .filter(|r| near.is_none_or(|(c, rad)| haversine_m(c, stop_point(&r.place)) <= rad))
            .collect();
        sort_by_hearts(&mut reviews, sort, |r| (r.hearts, r.created_ms, r.id.as_str()));
        reviews.truncate(clamp_limit(limit));
        Ok(reviews)
    }

    /// 여행 리뷰 한 피드: 장소 리뷰와 여행 코스 후기를 한 목록에 섞는다 (같은 정렬 기준, [sort_by_hearts]).
    /// [near]가 있으면 리뷰는 그 장소가, 코스는 들른 곳 중 하나라도 반경 안인 것만. `limit` 0이면 50개 (최대 500).
    pub fn travel_feed(&self, sort: CommunitySort, near: Option<NearFilter>, limit: u32) -> Result<Vec<TravelFeedItem>, CoreError> {
        let near = check_near(near)?;
        let conn = self.lock()?;
        let reviews = load_reviews(&conn, None)?
            .into_iter()
            .filter(|r| near.is_none_or(|(c, rad)| haversine_m(c, stop_point(&r.place)) <= rad))
            .map(|review| TravelFeedItem::Review { review });
        let courses = load_courses(&conn, None)?
            .into_iter()
            .filter(|p| near.is_none_or(|(c, r)| p.stops.iter().any(|s| haversine_m(c, stop_point(s)) <= r)))
            .map(|course| TravelFeedItem::Course { course });
        let mut items: Vec<TravelFeedItem> = reviews.chain(courses).collect();
        sort_by_hearts(&mut items, sort, |i| match i {
            TravelFeedItem::Review { review: r } => (r.hearts, r.created_ms, r.id.as_str()),
            TravelFeedItem::Course { course: c } => (c.hearts, c.created_ms, c.id.as_str()),
        });
        items.truncate(clamp_limit(limit));
        Ok(items)
    }

    /// 한 장소의 리뷰 (이름이 같고 500m 안인 곳, 모듈 설명 "같은 장소 묶기"). `limit` 0이면 50개.
    pub fn reviews_of_place(&self, place: StopFact, sort: CommunitySort, limit: u32) -> Result<Vec<PlaceReview>, CoreError> {
        let conn = self.lock()?;
        let mut reviews: Vec<PlaceReview> =
            load_reviews(&conn, None)?.into_iter().filter(|r| same_place(&r.place, &place)).collect();
        sort_by_hearts(&mut reviews, sort, |r| (r.hearts, r.created_ms, r.id.as_str()));
        reviews.truncate(clamp_limit(limit));
        Ok(reviews)
    }

    /// 인기 장소 순위 (하트 합 → 리뷰 수 → 별점 순). 장소는 리뷰와 코스의 들른 곳을 같은 장소끼리 묶은 것.
    /// [near]가 있으면 반경 안 장소만. `limit` 0이면 50개.
    pub fn popular_places(&self, near: Option<NearFilter>, limit: u32) -> Result<Vec<PopularPlace>, CoreError> {
        let near = check_near(near)?;
        let conn = self.lock()?;
        let groups = PlaceGroups::build(&load_courses(&conn, None)?, &load_reviews(&conn, None)?);
        let mut out: Vec<PopularPlace> = groups
            .groups
            .into_iter()
            .filter(|g| near.is_none_or(|(c, r)| haversine_m(c, stop_point(&g.place)) <= r))
            .map(|g| PopularPlace {
                hearts: g.hearts.min(u32::MAX as u64) as u32,
                review_count: g.reviews,
                avg_rating: g.avg_rating().unwrap_or(0.0) as f32,
                top_keyword: rank_keywords(&g.keywords, 1).pop().map(|k| k.keyword),
                place: g.place,
            })
            .collect();
        out.sort_by(|a, b| {
            b.hearts
                .cmp(&a.hearts)
                .then(b.review_count.cmp(&a.review_count))
                .then(b.avg_rating.total_cmp(&a.avg_rating))
                .then_with(|| a.place.name.cmp(&b.place.name))
        });
        out.truncate(clamp_limit(limit));
        Ok(out)
    }

    /// 리뷰 하트 누르기/취소. 반환: 바뀐 하트 수. 없는 id면 `InvalidInput`.
    pub fn toggle_review_heart(&self, id: String) -> Result<u32, CoreError> {
        let conn = self.lock()?;
        toggle_heart(&conn, "place_review", &id)?.ok_or_else(|| CoreError::InvalidInput(format!("리뷰 없음: {id}")))
    }

    /// 리뷰를 쓸 수 있는 곳: 최근 나들이·여행·볼일에서 실제로 머문 곳 (이름 있는 곳, 이름이 같으면 가장 최근 한 번,
    /// 최신순). `arrive_ms`가 그 들른 시각이다. `limit` 0이면 30개.
    pub fn review_candidates(&self, limit: u32) -> Result<Vec<StopFact>, CoreError> {
        let limit = if limit == 0 { 30 } else { limit.min(200) } as usize;
        let hits = self.search_memories(MemoryQuery {
            terms: vec![],
            from_ms: None,
            to_ms: None,
            kinds: vec![EpisodeKind::Outing, EpisodeKind::Trip, EpisodeKind::Errand],
            limit: 120,
        })?;
        let mut seen = HashSet::new();
        let mut out = Vec::new();
        // 에피소드는 최신순이고 한 에피소드 안 들른 곳은 도착순 → 뒤에서부터 보면 최신순
        for s in hits.into_iter().flat_map(|h| h.episode.facts.stops.into_iter().rev()) {
            if s.name.trim().is_empty() || !seen.insert(norm_name(&s.name)) {
                continue;
            }
            out.push(s);
            if out.len() >= limit {
                break;
            }
        }
        Ok(out)
    }

    /// 들른 곳 날씨를 받을 Open-Meteo 주소와 그 날짜 (들른 시각 [t_ms]의 현지 날짜, 오늘 기준으로 archive/forecast를 고른다).
    /// Kotlin은 `url`을 받아 `day`와 함께 [parse_open_meteo_weather]에 넘긴다.
    pub fn visit_weather_request(&self, lat: f64, lon: f64, t_ms: i64) -> WeatherRequest {
        let day = format_day(self.local_day(t_ms));
        let url = open_meteo_url(lat, lon, day.clone(), format_day(self.local_day(crate::places::now_ms())));
        WeatherRequest { url, day }
    }

    /// 들른 곳 리뷰 쓰기 ([EodiStore::add_place_review_with]에서 날씨 없이).
    pub fn add_place_review(&self, place: StopFact, rating: u8, text: String, now_ms: i64) -> Result<PlaceReview, CoreError> {
        self.add_place_review_with(place, rating, text, None, now_ms)
    }

    /// 들른 곳 리뷰 쓰기 ([EodiStore::write_place_review]에서 키워드 · 누구와 없이).
    pub fn add_place_review_with(
        &self,
        place: StopFact,
        rating: u8,
        text: String,
        weather: Option<String>,
        now_ms: i64,
    ) -> Result<PlaceReview, CoreError> {
        self.write_place_review(place, rating, text, vec![], None, weather, now_ms)
    }

    /// 들른 곳 리뷰 쓰기. 별점 1~5, 글은 앞뒤 공백을 지우고 500자까지. [keywords]는 [REVIEW_KEYWORDS]에 있는 것만
    /// 겹치지 않게 앞에서부터 5개까지 남긴다. [weather]는 그날 날씨 글 (Kotlin이 [EodiStore::visit_weather_request]로 받아 바꾼 것).
    ///
    /// 내 기록에서 채우는 것 (`place`가 리뷰 쓸 곳 목록에서 온 실제 방문일 때):
    /// - 들른 날 = `place.arrive_ms`의 현지 날짜 (0 이하면 [now_ms]의 날짜)
    /// - 때 = 도착 시각의 현지 시간 ([TimeOfDay::from_hour]). 도착 시각을 모르면 None
    /// - 머문 분 = 떠난 시각 − 도착 시각 (모르면 0)
    /// - 몇 번째 방문 = 내 기록에서 이곳에 머문 날 중 들른 날보다 앞선 날 수 + 1 (장소 창 "내 방문"과 같은 곳 고르기).
    ///   도착 시각을 모르면 0
    ///
    /// 이름이 비었거나 좌표 · 별점이 틀리면 `InvalidInput`.
    #[allow(clippy::too_many_arguments)]
    pub fn write_place_review(
        &self,
        place: StopFact,
        rating: u8,
        text: String,
        keywords: Vec<String>,
        with_whom: Option<WithWhom>,
        weather: Option<String>,
        now_ms: i64,
    ) -> Result<PlaceReview, CoreError> {
        if !(1..=5).contains(&rating) {
            return Err(CoreError::InvalidInput(format!("별점 {rating}")));
        }
        if place.name.trim().is_empty() || !valid_point(place.lat, place.lon) {
            return Err(CoreError::InvalidInput(format!("장소 {:?} ({}, {})", place.name, place.lat, place.lon)));
        }
        let text: String = text.trim().chars().take(MAX_REVIEW_CHARS).collect();
        let keywords = clean_keywords(&keywords);
        let known = place.arrive_ms > 0;
        let visit = if known { place.arrive_ms } else { now_ms };
        let day = self.local_day(visit);
        let tz_ms = i64::from(self.tz_minutes()) * 60_000;
        let time_of_day = known.then(|| TimeOfDay::from_hour((visit + tz_ms).rem_euclid(crate::store::DAY_MS) / 3_600_000));
        let stay_min =
            if known && place.leave_ms > place.arrive_ms { ((place.leave_ms - place.arrive_ms + 30_000) / 60_000) as u32 } else { 0 };
        let mut conn = self.lock()?;
        let visit_nth = if known {
            self.flush_all(&mut conn)?;
            let (_, days) = crate::place_detail::my_visits(&conn, &crate::offers::norm(&place.name), stop_point(&place))?;
            days.iter().filter(|&&d| d < day).count() as u32 + 1
        } else {
            0
        };
        let id = free_id(&conn, "place_review", &format!("me-r{now_ms}"))?;
        conn.execute(
            "INSERT INTO place_review (id, name, category, address, visit_ms, lat, lon, author, mine, rating, text, created_ms,
                                       visited_day, weather, others_hearts, my_heart, keywords, with_whom, time_of_day, stay_min,
                                       visit_nth)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 1, ?9, ?10, ?11, ?12, ?13, 0, 0, ?14, ?15, ?16, ?17, ?18)",
            params![
                id,
                place.name.trim(),
                place.category,
                place.address,
                visit,
                place.lat,
                place.lon,
                ME,
                rating,
                text,
                now_ms,
                format_day(day),
                weather::clean_weather(weather),
                join_names(&keywords),
                with_whom.map(WithWhom::key),
                time_of_day.map(TimeOfDay::key),
                stay_min,
                visit_nth
            ],
        )?;
        load_reviews(&conn, Some(&id))?.pop().ok_or_else(|| CoreError::Db("쓴 리뷰를 다시 못 읽음".into()))
    }

    // ---- 게시판 ----

    /// 게시판 글 목록. 인기순 = 하트 → 댓글 수 → 최신. `limit` 0이면 50개.
    pub fn board_posts(&self, sort: CommunitySort, limit: u32) -> Result<Vec<BoardPost>, CoreError> {
        let conn = self.lock()?;
        let mut posts = load_board_posts(&conn, None)?;
        match sort {
            CommunitySort::Popular => posts.sort_by(|a, b| {
                b.hearts
                    .cmp(&a.hearts)
                    .then(b.comment_count.cmp(&a.comment_count))
                    .then(b.created_ms.cmp(&a.created_ms))
                    .then_with(|| a.id.cmp(&b.id))
            }),
            CommunitySort::Recent => posts.sort_by(|a, b| b.created_ms.cmp(&a.created_ms).then_with(|| a.id.cmp(&b.id))),
        }
        posts.truncate(clamp_limit(limit));
        Ok(posts)
    }

    /// 게시판 글 하나 (없으면 None).
    pub fn board_post(&self, id: String) -> Result<Option<BoardPost>, CoreError> {
        let conn = self.lock()?;
        Ok(load_board_posts(&conn, Some(&id))?.pop())
    }

    /// 글의 댓글 (쓴 순서대로). 없는 글이면 빈 목록.
    pub fn board_comments(&self, post_id: String) -> Result<Vec<BoardComment>, CoreError> {
        let conn = self.lock()?;
        let mut st = conn.prepare_cached(
            "SELECT id, post_id, author, mine, body, created_ms, others_hearts + my_heart, my_heart
             FROM board_comment WHERE post_id = ?1 ORDER BY created_ms, id",
        )?;
        let rows = st.query_map([post_id], comment_from_row)?;
        Ok(rows.collect::<Result<_, _>>()?)
    }

    /// 게시판 글 쓰기 (작성자 "나"). 제목이 비었으면 `InvalidInput`. 제목 100자, 글 2000자, 태그 5개까지("#"은 뗀다).
    pub fn add_board_post(&self, title: String, body: String, tags: Vec<String>, now_ms: i64) -> Result<BoardPost, CoreError> {
        let title: String = title.trim().chars().take(100).collect();
        if title.is_empty() {
            return Err(CoreError::InvalidInput("제목이 비었다".into()));
        }
        let body: String = body.trim().chars().take(2000).collect();
        let tags: Vec<String> =
            tags.iter().map(|t| t.trim().trim_start_matches('#').to_string()).filter(|t| !t.is_empty()).take(5).collect();
        let conn = self.lock()?;
        let id = free_id(&conn, "board_post", &format!("me-p{now_ms}"))?;
        conn.execute(
            "INSERT INTO board_post (id, title, body, author, mine, region, tags, created_ms, others_hearts, my_heart)
             VALUES (?1, ?2, ?3, ?4, 1, NULL, ?5, ?6, 0, 0)",
            params![id, title, body, ME, join_names(&tags), now_ms],
        )?;
        load_board_posts(&conn, Some(&id))?.pop().ok_or_else(|| CoreError::Db("쓴 글을 다시 못 읽음".into()))
    }

    /// 댓글 쓰기 (작성자 "나"). 없는 글이거나 댓글이 비었으면 `InvalidInput`. 1000자까지.
    pub fn add_board_comment(&self, post_id: String, body: String, now_ms: i64) -> Result<BoardComment, CoreError> {
        let body: String = body.trim().chars().take(1000).collect();
        if body.is_empty() {
            return Err(CoreError::InvalidInput("댓글이 비었다".into()));
        }
        let conn = self.lock()?;
        if conn.query_row("SELECT 1 FROM board_post WHERE id = ?1", [&post_id], |_| Ok(())).optional()?.is_none() {
            return Err(CoreError::InvalidInput(format!("글 없음: {post_id}")));
        }
        let id = free_id(&conn, "board_comment", &format!("me-m{now_ms}"))?;
        conn.execute(
            "INSERT INTO board_comment (id, post_id, author, mine, body, created_ms, others_hearts, my_heart)
             VALUES (?1, ?2, ?3, 1, ?4, ?5, 0, 0)",
            params![id, post_id, ME, body, now_ms],
        )?;
        Ok(BoardComment { id, post_id, author: ME.into(), mine: true, body, created_ms: now_ms, hearts: 0, hearted_by_me: false })
    }

    /// 게시판 글 하트 누르기/취소. 반환: 바뀐 하트 수. 없는 id면 `InvalidInput`.
    pub fn toggle_board_post_heart(&self, id: String) -> Result<u32, CoreError> {
        let conn = self.lock()?;
        toggle_heart(&conn, "board_post", &id)?.ok_or_else(|| CoreError::InvalidInput(format!("글 없음: {id}")))
    }

    /// 댓글 하트 누르기/취소. 반환: 바뀐 하트 수. 없는 id면 `InvalidInput`.
    pub fn toggle_board_comment_heart(&self, id: String) -> Result<u32, CoreError> {
        let conn = self.lock()?;
        toggle_heart(&conn, "board_comment", &id)?.ok_or_else(|| CoreError::InvalidInput(format!("댓글 없음: {id}")))
    }

    // ---- 시연 ----

    /// 시연용 커뮤니티(가상의 사용자·여행 코스 후기·드라이브 코스·리뷰·게시판·하트)를 넣는다 (디버그 빌드에서만 부른다).
    /// 반환: 넣은 코스 + 드라이브 코스 + 리뷰 + 글 + 댓글 수. 이미 넣었으면 0 (버전 10 전에 넣었으면 드라이브 코스만 더 넣고 그 수).
    /// 내용은 `demo` 모듈 설명.
    pub fn seed_community_demo(&self, now_ms: i64) -> Result<u32, CoreError> {
        if now_ms <= 0 {
            return Err(CoreError::InvalidInput(format!("now_ms {now_ms}")));
        }
        demo::seed(self, now_ms)
    }
}

// ---- 점수 ----

/// 추천 점수 ([EodiStore::recommend_drive_courses] 설명).
pub(crate) fn course_score(hearts: u32, d_km: f64, radius_km: f64, stops: usize, avg_rating: Option<f64>) -> f64 {
    let popularity = 1.0 + (1.0 + hearts as f64).ln();
    let half_km = (radius_km / 3.0).max(3.0);
    let decay = 0.5f64.powf(d_km.max(0.0) / half_km);
    let stop_fit = match stops {
        3..=5 => 1.0,
        0 | 1 => 0.0,
        _ => 0.5,
    };
    let rating_fit = avg_rating.map_or(0.5, |a| ((a - 1.0) / 4.0).clamp(0.0, 1.0));
    let quality = 0.6 + 0.2 * stop_fit + 0.2 * rating_fit;
    popularity * decay * quality
}

// ---- 장소 묶기 ----

pub(crate) struct PlaceAgg {
    pub place: StopFact,
    pub hearts: u64,
    pub reviews: u32,
    rating_sum: u32,
    /// [REVIEW_KEYWORDS] 순서대로 고른 수
    keywords: Vec<u32>,
}

impl PlaceAgg {
    fn avg_rating(&self) -> Option<f64> {
        (self.reviews > 0).then(|| self.rating_sum as f64 / self.reviews as f64)
    }
}

/// 키워드 이름 → [REVIEW_KEYWORDS] 번호 (정해진 키워드가 아니면 None).
fn keyword_index(k: &str) -> Option<usize> {
    let k = k.trim();
    REVIEW_KEYWORDS.iter().position(|x| *x == k)
}

/// 고른 키워드 정리: 정해진 키워드만, 겹치면 한 번, 앞에서부터 [MAX_REVIEW_KEYWORDS]개.
pub(crate) fn clean_keywords(keywords: &[String]) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for i in keywords.iter().filter_map(|k| keyword_index(k)) {
        let k = REVIEW_KEYWORDS[i].to_string();
        if !out.contains(&k) {
            out.push(k);
        }
        if out.len() == MAX_REVIEW_KEYWORDS {
            break;
        }
    }
    out
}

/// 키워드별 수 (리뷰 하나에서 같은 키워드는 한 번).
fn keyword_tally<'a>(reviews: impl IntoIterator<Item = &'a PlaceReview>) -> (Vec<u32>, u32) {
    let mut counts = vec![0u32; REVIEW_KEYWORDS.len()];
    let mut voters = 0;
    for r in reviews {
        let mut seen = vec![false; REVIEW_KEYWORDS.len()];
        for i in r.keywords.iter().filter_map(|k| keyword_index(k)) {
            if !seen[i] {
                seen[i] = true;
                counts[i] += 1;
            }
        }
        if seen.iter().any(|&b| b) {
            voters += 1;
        }
    }
    (counts, voters)
}

/// 수 → 많은 순 상위 [top]개 (같으면 [REVIEW_KEYWORDS] 순서, 0개는 뺀다). ratio = 수 / 맨 위 수.
fn rank_keywords(counts: &[u32], top: usize) -> Vec<KeywordCount> {
    let mut idx: Vec<usize> = (0..counts.len()).filter(|&i| counts[i] > 0).collect();
    idx.sort_by(|&a, &b| counts[b].cmp(&counts[a]).then(a.cmp(&b)));
    idx.truncate(top);
    let max = idx.first().map_or(1, |&i| counts[i]).max(1) as f32;
    idx.into_iter().map(|i| KeywordCount { keyword: REVIEW_KEYWORDS[i].to_string(), count: counts[i], ratio: counts[i] as f32 / max }).collect()
}

/// 장소 리뷰들의 "이런 점이 좋았어요": 많이 고른 키워드 상위 [KEYWORD_SUMMARY_TOP]개와 키워드를 고른 리뷰 수.
pub(crate) fn keyword_summary<'a>(reviews: impl IntoIterator<Item = &'a PlaceReview>) -> (Vec<KeywordCount>, u32) {
    let (counts, voters) = keyword_tally(reviews);
    (rank_keywords(&counts, KEYWORD_SUMMARY_TOP), voters)
}

/// 리뷰·코스 들른 곳을 같은 장소끼리 묶은 것 (모듈 설명 "같은 장소 묶기").
pub(crate) struct PlaceGroups {
    pub groups: Vec<PlaceAgg>,
}

impl PlaceGroups {
    fn build(courses: &[CoursePost], reviews: &[PlaceReview]) -> Self {
        let mut g = PlaceGroups { groups: Vec::new() };
        // 리뷰를 먼저 넣어 대표 장소 정보(분류·주소)가 리뷰 쪽 값이 되게 한다
        for r in reviews {
            let i = g.find_or_add(&r.place);
            let a = &mut g.groups[i];
            a.hearts += r.hearts as u64;
            a.reviews += 1;
            a.rating_sum += r.rating as u32;
            for (k, n) in a.keywords.iter_mut().zip(keyword_tally([r]).0) {
                *k += n;
            }
        }
        for c in courses {
            // 한 코스가 같은 곳을 두 번 들러도 하트는 한 번만
            let mut counted = HashSet::new();
            for s in &c.stops {
                let i = g.find_or_add(s);
                if counted.insert(i) {
                    g.groups[i].hearts += c.hearts as u64;
                }
            }
        }
        g
    }

    fn find(&self, s: &StopFact) -> Option<&PlaceAgg> {
        self.groups.iter().find(|g| same_place(&g.place, s))
    }

    fn find_or_add(&mut self, s: &StopFact) -> usize {
        if let Some(i) = self.groups.iter().position(|g| same_place(&g.place, s)) {
            return i;
        }
        let place = StopFact { arrive_ms: 0, leave_ms: 0, first_visit: false, ..s.clone() };
        self.groups.push(PlaceAgg { place, hearts: 0, reviews: 0, rating_sum: 0, keywords: vec![0; REVIEW_KEYWORDS.len()] });
        self.groups.len() - 1
    }
}

fn norm_name(s: &str) -> String {
    s.chars().filter(|c| !c.is_whitespace()).collect()
}

/// 이름(띄어쓰기 무시)이 같고 [SAME_PLACE_M] 안이면 같은 장소.
pub(crate) fn same_place(a: &StopFact, b: &StopFact) -> bool {
    norm_name(&a.name) == norm_name(&b.name) && haversine_m(stop_point(a), stop_point(b)) <= SAME_PLACE_M
}

// ---- 내 기록 올리기 ----

/// 집·회사 좌표 (memory_anchor의 모든 줄: 내비 즐겨찾기·추정·지금 쓰는 값 — 하나라도 가까우면 뺀다).
fn privacy_anchors(conn: &Connection) -> Result<Vec<Point>, CoreError> {
    let mut st = conn.prepare_cached("SELECT lat, lon FROM memory_anchor")?;
    let rows = st.query_map([], |r| Ok(Point { lat: r.get(0)?, lon: r.get(1)? }))?;
    Ok(rows.collect::<Result<_, _>>()?)
}

/// 올릴 들른 곳: 이름 있는 곳 중 집·회사에서 [PRIVACY_RADIUS_M] 넘게 떨어진 곳.
pub(crate) fn shareable_stops(stops: &[StopFact], anchors: &[Point]) -> Vec<StopFact> {
    stops
        .iter()
        .filter(|s| !s.name.trim().is_empty())
        .filter(|s| anchors.iter().all(|&a| haversine_m(a, stop_point(s)) > PRIVACY_RADIUS_M))
        .cloned()
        .collect()
}

/// 코스 거리(m): 들른 곳 사이 직선거리 합 × [ROAD_FACTOR].
pub(crate) fn course_distance_m(stops: &[StopFact]) -> f64 {
    stops.windows(2).map(|w| haversine_m(stop_point(&w[0]), stop_point(&w[1]))).sum::<f64>() * ROAD_FACTOR
}

/// 코스 시간(ms): 첫 곳 도착 ~ 마지막 곳 떠남(아직 머무는 중이면 도착).
fn course_duration_ms(stops: &[StopFact]) -> i64 {
    match (stops.first(), stops.last()) {
        (Some(a), Some(b)) => (b.leave_ms.max(b.arrive_ms) - a.arrive_ms).max(0),
        _ => 0,
    }
}

/// 공유 미리보기에 필요한 기기 상태 (집·회사, 이미 올린 에피소드, 오늘).
struct ShareCtx {
    anchors: Vec<Point>,
    shared: HashSet<i64>,
    today: String,
}

impl ShareCtx {
    fn load(store: &EodiStore, conn: &Connection) -> Result<Self, CoreError> {
        let mut st = conn.prepare_cached("SELECT episode_id FROM community_course WHERE episode_id IS NOT NULL")?;
        let rows = st.query_map([], |r| r.get(0))?;
        Ok(ShareCtx {
            anchors: privacy_anchors(conn)?,
            shared: rows.collect::<Result<_, _>>()?,
            today: format_day(store.local_day(crate::places::now_ms())),
        })
    }

    fn preview(&self, ep: Episode, day_title: Option<String>) -> SharePreview {
        let f = ep.facts;
        let stops = shareable_stops(&f.stops, &self.anchors);
        let unnamed = f.stops.iter().filter(|s| s.name.trim().is_empty()).count();
        let hidden = f.stops.len() - unnamed - stops.len();
        let title =
            [ep.title, day_title].into_iter().flatten().map(|t| t.trim().to_string()).find(|t| !t.is_empty()).unwrap_or(f.rule_label);
        let weather_url = stops.first().map(|s| open_meteo_url(s.lat, s.lon, f.day.clone(), self.today.clone())).unwrap_or_default();
        SharePreview {
            episode_id: f.id,
            season: season_label(f.day.clone()),
            day: f.day,
            title,
            region: f.region,
            kind: f.kind,
            distance_m: course_distance_m(&stops),
            stops,
            hidden_count: hidden as u32,
            unnamed_count: unnamed as u32,
            shared: self.shared.contains(&f.id),
            weather_url,
        }
    }
}

impl EodiStore {
    /// 에피소드 하나와 그날 제목. 없는 id면 `InvalidInput`.
    pub(crate) fn episode_by_id(&self, id: i64) -> Result<(Episode, Option<String>), CoreError> {
        let not_found = || CoreError::InvalidInput(format!("에피소드 없음: {id}"));
        let (day, title) = {
            let mut conn = self.lock()?;
            self.flush_all(&mut conn)?;
            let day: i64 =
                conn.query_row("SELECT day FROM episode WHERE id = ?1", [id], |r| r.get(0)).optional()?.ok_or_else(not_found)?;
            (day, crate::memory::day_title(&conn, day)?.0)
        };
        let ep = self.episodes_for_day(format_day(day))?.into_iter().find(|e| e.facts.id == id).ok_or_else(not_found)?;
        Ok((ep, title))
    }

    pub(crate) fn course_by_id(&self, id: &str) -> Result<CoursePost, CoreError> {
        let conn = self.lock()?;
        load_courses(&conn, Some(id))?.pop().ok_or_else(|| CoreError::InvalidInput(format!("코스 없음: {id}")))
    }
}

// ---- 읽기 · 쓰기 ----

pub(crate) fn stop_point(s: &StopFact) -> Point {
    Point { lat: s.lat, lon: s.lon }
}

pub(crate) fn valid_point(lat: f64, lon: f64) -> bool {
    lat.is_finite() && lon.is_finite() && lat.abs() <= 90.0 && lon.abs() <= 180.0 && !(lat == 0.0 && lon == 0.0)
}

/// 반경 조건을 (중심, 반경 m)로. 좌표·반경이 틀리면 `InvalidInput`.
fn check_near(near: Option<NearFilter>) -> Result<Option<(Point, f64)>, CoreError> {
    match near {
        None => Ok(None),
        Some(n) if valid_point(n.center.lat, n.center.lon) && n.radius_km.is_finite() && n.radius_km > 0.0 => {
            Ok(Some((Point::from(n.center), n.radius_km * 1000.0)))
        }
        Some(n) => Err(CoreError::InvalidInput(format!("near {n:?}"))),
    }
}

fn clamp_limit(limit: u32) -> usize {
    if limit == 0 {
        DEFAULT_LIMIT
    } else {
        (limit as usize).min(MAX_LIMIT)
    }
}

/// 인기순 = 하트 많은 순 → 최신 → id, 최신순 = 올린 시각 → id. [key] = (하트, 올린 시각, id).
pub(crate) fn sort_by_hearts<T>(items: &mut [T], sort: CommunitySort, key: impl Fn(&T) -> (u32, i64, &str)) {
    items.sort_by(|a, b| {
        let (ha, ca, ia) = key(a);
        let (hb, cb, ib) = key(b);
        match sort {
            CommunitySort::Popular => hb.cmp(&ha).then(cb.cmp(&ca)).then_with(|| ia.cmp(ib)),
            CommunitySort::Recent => cb.cmp(&ca).then_with(|| ia.cmp(ib)),
        }
    });
}

/// 하트 뒤집기. 없는 id면 Ok(None).
fn toggle_heart(conn: &Connection, table: &str, id: &str) -> Result<Option<u32>, CoreError> {
    if conn.execute(&format!("UPDATE {table} SET my_heart = 1 - my_heart WHERE id = ?1"), [id])? == 0 {
        return Ok(None);
    }
    let n: i64 = conn.query_row(&format!("SELECT others_hearts + my_heart FROM {table} WHERE id = ?1"), [id], |r| r.get(0))?;
    Ok(Some(n.clamp(0, u32::MAX as i64) as u32))
}

/// 내가 쓰는 글의 id: [base]가 이미 있으면 "-1", "-2"…를 붙인다 (같은 밀리초에 두 번 써도 겹치지 않게).
fn free_id(conn: &Connection, table: &str, base: &str) -> Result<String, CoreError> {
    let mut n = 0;
    loop {
        let id = if n == 0 { base.to_string() } else { format!("{base}-{n}") };
        let taken = conn.query_row(&format!("SELECT 1 FROM {table} WHERE id = ?1"), [&id], |_| Ok(())).optional()?.is_some();
        if !taken {
            return Ok(id);
        }
        n += 1;
    }
}

fn hearts_of(n: i64) -> u32 {
    n.clamp(0, u32::MAX as i64) as u32
}

const COURSE_COLS: &str = "id, title, body, author, mine, region, tags, stops, distance_m, duration_ms, created_ms,
     others_hearts + my_heart, my_heart, visited_day, weather";

fn course_from_row(r: &Row) -> rusqlite::Result<CoursePost> {
    let visited_day: String = r.get(13)?;
    Ok(CoursePost {
        id: r.get(0)?,
        title: r.get(1)?,
        body: r.get(2)?,
        author: r.get(3)?,
        mine: r.get(4)?,
        region: r.get(5)?,
        tags: split_names(&r.get::<_, String>(6)?),
        stops: decode_stops(&r.get::<_, String>(7)?),
        distance_m: r.get(8)?,
        duration_ms: r.get(9)?,
        created_ms: r.get(10)?,
        hearts: hearts_of(r.get(11)?),
        hearted_by_me: r.get(12)?,
        season: season_label(visited_day.clone()),
        visited_day,
        weather: r.get(14)?,
    })
}

/// 코스 전부(또는 [id] 하나). 기기 안 코스는 수십~수백 개라 다 읽어 Rust에서 거르고 정렬한다.
pub(crate) fn load_courses(conn: &Connection, id: Option<&str>) -> Result<Vec<CoursePost>, CoreError> {
    load_rows(conn, "community_course", COURSE_COLS, id, course_from_row)
}

const REVIEW_COLS: &str = "id, name, category, address, visit_ms, lat, lon, author, mine, rating, text, created_ms,
     others_hearts + my_heart, my_heart, visited_day, weather, keywords, with_whom, time_of_day, stay_min, visit_nth";

fn review_from_row(r: &Row) -> rusqlite::Result<PlaceReview> {
    let visit: i64 = r.get(4)?;
    let visited_day: String = r.get(14)?;
    Ok(PlaceReview {
        id: r.get(0)?,
        place: StopFact {
            name: r.get(1)?,
            category: r.get(2)?,
            address: r.get(3)?,
            arrive_ms: visit,
            leave_ms: visit,
            lat: r.get(5)?,
            lon: r.get(6)?,
            first_visit: false,
        },
        author: r.get(7)?,
        mine: r.get(8)?,
        rating: r.get::<_, i64>(9)?.clamp(1, 5) as u8,
        text: r.get(10)?,
        created_ms: r.get(11)?,
        hearts: hearts_of(r.get(12)?),
        hearted_by_me: r.get(13)?,
        season: season_label(visited_day.clone()),
        visited_day,
        weather: r.get(15)?,
        keywords: split_names(&r.get::<_, String>(16)?),
        with_whom: r.get::<_, Option<String>>(17)?.as_deref().and_then(WithWhom::from_key),
        time_of_day: r.get::<_, Option<String>>(18)?.as_deref().and_then(TimeOfDay::from_key),
        stay_min: r.get::<_, i64>(19)?.clamp(0, u32::MAX as i64) as u32,
        visit_nth: r.get::<_, i64>(20)?.clamp(0, u32::MAX as i64) as u32,
    })
}

pub(crate) fn load_reviews(conn: &Connection, id: Option<&str>) -> Result<Vec<PlaceReview>, CoreError> {
    load_rows(conn, "place_review", REVIEW_COLS, id, review_from_row)
}

const BOARD_COLS: &str = "id, title, body, author, mine, region, tags, created_ms, others_hearts + my_heart, my_heart,
     (SELECT count(*) FROM board_comment c WHERE c.post_id = board_post.id)";

fn board_from_row(r: &Row) -> rusqlite::Result<BoardPost> {
    Ok(BoardPost {
        id: r.get(0)?,
        title: r.get(1)?,
        body: r.get(2)?,
        author: r.get(3)?,
        mine: r.get(4)?,
        region: r.get::<_, Option<String>>(5)?.filter(|s| !s.is_empty()),
        tags: split_names(&r.get::<_, String>(6)?),
        created_ms: r.get(7)?,
        hearts: hearts_of(r.get(8)?),
        hearted_by_me: r.get(9)?,
        comment_count: r.get::<_, i64>(10)?.max(0) as u32,
    })
}

fn load_board_posts(conn: &Connection, id: Option<&str>) -> Result<Vec<BoardPost>, CoreError> {
    load_rows(conn, "board_post", BOARD_COLS, id, board_from_row)
}

fn comment_from_row(r: &Row) -> rusqlite::Result<BoardComment> {
    Ok(BoardComment {
        id: r.get(0)?,
        post_id: r.get(1)?,
        author: r.get(2)?,
        mine: r.get(3)?,
        body: r.get(4)?,
        created_ms: r.get(5)?,
        hearts: hearts_of(r.get(6)?),
        hearted_by_me: r.get(7)?,
    })
}

/// 표 [table]의 줄 전부 또는 id 하나.
fn load_rows<T>(
    conn: &Connection,
    table: &str,
    cols: &str,
    id: Option<&str>,
    f: fn(&Row) -> rusqlite::Result<T>,
) -> Result<Vec<T>, CoreError> {
    let rows = match id {
        Some(id) => {
            let mut st = conn.prepare_cached(&format!("SELECT {cols} FROM {table} WHERE id = ?1"))?;
            let rows = st.query_map([id], f)?;
            rows.collect::<Result<Vec<_>, _>>()?
        }
        None => {
            let mut st = conn.prepare_cached(&format!("SELECT {cols} FROM {table}"))?;
            let rows = st.query_map([], f)?;
            rows.collect::<Result<Vec<_>, _>>()?
        }
    };
    Ok(rows)
}

pub(crate) fn encode_stops(stops: &[StopFact]) -> String {
    Value::Array(
        stops
            .iter()
            .map(|s| {
                json!({ "n": s.name, "c": s.category, "a": s.address, "t0": s.arrive_ms, "t1": s.leave_ms,
                        "la": s.lat, "lo": s.lon, "f": s.first_visit })
            })
            .collect(),
    )
    .to_string()
}

fn decode_stops(s: &str) -> Vec<StopFact> {
    serde_json::from_str::<Value>(s)
        .ok()
        .and_then(|v| v.as_array().cloned())
        .unwrap_or_default()
        .iter()
        .filter_map(|v| {
            Some(StopFact {
                name: v["n"].as_str()?.to_string(),
                category: v["c"].as_str().unwrap_or("").to_string(),
                address: v["a"].as_str().unwrap_or("").to_string(),
                arrive_ms: v["t0"].as_i64().unwrap_or(0),
                leave_ms: v["t1"].as_i64().unwrap_or(0),
                lat: v["la"].as_f64()?,
                lon: v["lo"].as_f64()?,
                first_visit: v["f"].as_bool().unwrap_or(false),
            })
        })
        .collect()
}

/// 날짜 문자열이 맞는지 (테스트·시연 데이터 점검용).
#[cfg(test)]
pub(crate) fn is_day(s: &str) -> bool {
    crate::store::parse_day(s).is_some()
}

// ---- 지도 ----

/// 들른 곳 핀만 있는 지도 데이터 (`mapdata`의 머문 곳과 같은 GeoJSON 모양).
fn stops_map(id: &str, stops: &[StopFact]) -> DayMap {
    let mut out = String::with_capacity(64 + stops.len() * 200);
    out.push_str(r#"{"type":"FeatureCollection","features":["#);
    let (mut lo_lat, mut lo_lon, mut hi_lat, mut hi_lon) = (f64::MAX, f64::MAX, f64::MIN, f64::MIN);
    let mut entries = Vec::with_capacity(stops.len());
    for (i, s) in stops.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        let order = i + 1;
        let _ = write!(
            out,
            r#"{{"type":"Feature","geometry":{{"type":"Point","coordinates":[{:.6},{:.6}]}},"properties":{{"kind":"stay","key":"s{order}","arrive_ms":{},"leave_ms":{},"open":false,"order":{order}"#,
            s.lon, s.lat, s.arrive_ms, s.leave_ms
        );
        if let Some(name) = map_safe_name(&s.name) {
            out.push_str(r#","name":"#);
            out.push_str(&Value::String(name).to_string());
        }
        out.push_str("}}");
        (lo_lat, lo_lon, hi_lat, hi_lon) = (lo_lat.min(s.lat), lo_lon.min(s.lon), hi_lat.max(s.lat), hi_lon.max(s.lon));
        entries.push(EntryBounds {
            key: format!("s{order}"),
            bounds: MapBounds { min_lat: s.lat, min_lon: s.lon, max_lat: s.lat, max_lon: s.lon },
        });
    }
    out.push_str("]}");
    let bounds = (!stops.is_empty()).then_some(MapBounds { min_lat: lo_lat, min_lon: lo_lon, max_lat: hi_lat, max_lon: hi_lon });
    DayMap { day: id.to_string(), geojson: out, bounds, entries }
}

#[cfg(test)]
mod tests;

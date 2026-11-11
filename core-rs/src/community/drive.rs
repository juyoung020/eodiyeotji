//! 드라이브 코스 = 차에서 즐기는 경치 길 하나 (들를 곳·경유지 없음).
//!
//! 강변북로 한강 야경, 인천대교, 헌화로처럼 "달리는 것 자체가 구경"인 길이다. 글 하나는 길 모양(실제 도로 선) +
//! 시작·끝 이름 + 가는 길에 보이는 풍경([RouteHighlight], 지도 글자로만 쓰고 들르지 않는다) + 언제 가면 좋은지([BestTime]).
//! 식당·관광지를 차례로 들르는 코스 글은 여행 리뷰 쪽 "여행 코스 후기"(`community_course`)에 그대로 있다.
//!
//! - 순위: 인기(하트) · 최신 · 내 근처(지금 위치에서 **길 위 가장 가까운 점**까지 거리) — [EodiStore::drive_routes].
//! - 근처 추천(에이전트 "여기 근처 드라이브 추천코스"): 인기 × 거리 감쇠 — [EodiStore::recommend_drive_routes].
//! - 안내: 길 시작점으로 간 뒤, 도착하면 끝점으로 — 경유지 없는 구간 두 개 ([route_legs]).
//! - 3D 미리보기에 쓸 지도 데이터(선·시작·끝·풍경 글자 GeoJSON), 길 방향을 따라 보는 카메라, 미리보기 비행 경로도 여기서 만든다.
//!   Kotlin은 받은 값을 MapLibre에 그대로 넘긴다.
//! - 내 기록에서 올리기: 하루의 주행 하나를 골라 그 길(집·회사 1.2km 안은 지운 선)을 올린다 ([EodiStore::share_drive_route]).
//!
//! # 저장 (DB 구조 버전 10, `drive_route`)
//! 선은 `[[위도, 경도], …]` JSON (소수 다섯째 자리, 약 1m). 풍경은 `[{"n":이름,"la":위도,"lo":경도}, …]`.
//! 하트는 다른 커뮤니티 글과 같다 (`others_hearts` + `my_heart`). 내가 올린 길은 `id = "me-d<주행 시작 시각>"`.

use std::fmt::Write;

use rusqlite::{params, Connection, Row};
use serde_json::{json, Value};

use super::{clamp_limit, hearts_of, season_label, toggle_heart, valid_point, weather, ME};
use crate::ffi::GeoPoint;
use crate::geo::{bearing_deg, haversine_m, line_length_m, nearest_distance_m, simplify, Point};
use crate::mapdata::{map_safe_name, MapBounds};
use crate::memory::{CourseLeg, StopFact};
use crate::share::{trim_near, ROUTE_TRIM_M};
use crate::store::{format_day, CoreError, EodiStore, TripSummary};

/// DB 구조 버전 10에서 더하는 표. 이미 있으면 건너뛴다 (테스트가 버전 숫자만 되돌려도 다시 올릴 수 있게).
pub(crate) const SCHEMA_V10: &str = "
CREATE TABLE IF NOT EXISTS drive_route (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    author TEXT NOT NULL,
    mine INTEGER NOT NULL,
    region TEXT NOT NULL,
    start_name TEXT NOT NULL,
    end_name TEXT NOT NULL,
    line TEXT NOT NULL,
    highlights TEXT NOT NULL DEFAULT '[]',
    best_time TEXT NOT NULL DEFAULT 'day',
    distance_m REAL NOT NULL,
    duration_ms INTEGER NOT NULL,
    created_ms INTEGER NOT NULL,
    visited_day TEXT NOT NULL,
    weather TEXT,
    others_hearts INTEGER NOT NULL DEFAULT 0,
    my_heart INTEGER NOT NULL DEFAULT 0,
    trip_start_ms INTEGER
);
";

/// 예전 버전 DB를 흉내 내는 테스트용.
#[cfg(test)]
pub(crate) const DROP_V10: &str = "DROP TABLE drive_route;";

/// 내 기록에서 올릴 때 이보다 짧게 남는 길은 올리지 않는다 (집·회사 근처를 지우고 남은 길이, m).
/// 집 ↔ 회사 출퇴근처럼 앞뒤를 지우면 동네 몇 km만 남는 주행은 드라이브 코스로 보이지 않게.
pub const MIN_SHARE_M: f64 = 5_000.0;
/// 지운 끝의 이름 대신 쓰는 말 (집·회사 이름이 드러나지 않게).
const FROM_UNKNOWN: &str = "출발 지점";
const TO_UNKNOWN: &str = "도착 지점";
/// 저장·지도에 넘기기 전 선 단순화 허용 오차(m). 3D 미리보기 확대(시점 높이 수백 m)에서도 길에서 벗어나 보이지 않는 정도.
const LINE_TOLERANCE_M: f64 = 8.0;
/// 근처 추천 반경 기본값(km)·개수.
const DEFAULT_RADIUS_KM: f64 = 30.0;
/// 3D 미리보기 기울기 기본값(도). MapLibre Native 최대가 60도다.
pub const PREVIEW_PITCH: f64 = 60.0;
/// 미리보기 비행 프레임 수 기본값과 최대.
const FLIGHT_FRAMES: u32 = 480;
const MAX_FLIGHT_FRAMES: u32 = 4_000;
/// MapLibre 줌 0에서 세계 둘레의 논리 픽셀 수 (512px 타일).
const WORLD_PX: f64 = 512.0;
const EARTH_CIRCUMFERENCE_M: f64 = 40_075_016.686;

/// 언제 가면 가장 좋은지 (글쓴이가 고른다). 3D 미리보기 색([RouteTheme])이 여기서 정해진다.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum BestTime {
    /// 야경
    Night,
    /// 노을
    Sunset,
    /// 아침 (해돋이·물안개)
    Morning,
    /// 낮
    Day,
    /// 단풍
    Foliage,
    /// 벚꽃
    Blossom,
}

impl BestTime {
    pub const ALL: [BestTime; 6] = [BestTime::Night, BestTime::Sunset, BestTime::Morning, BestTime::Day, BestTime::Foliage, BestTime::Blossom];

    pub(crate) fn key(self) -> &'static str {
        match self {
            BestTime::Night => "night",
            BestTime::Sunset => "sunset",
            BestTime::Morning => "morning",
            BestTime::Day => "day",
            BestTime::Foliage => "foliage",
            BestTime::Blossom => "blossom",
        }
    }

    pub(crate) fn from_key(k: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|b| b.key() == k)
    }

    /// 짧은 이름 ("야경"). 에이전트 답·찾기 낱말에 쓴다 (화면 글은 Kotlin 문자열 자원).
    pub fn label(self) -> &'static str {
        match self {
            BestTime::Night => "야경",
            BestTime::Sunset => "노을",
            BestTime::Morning => "아침",
            BestTime::Day => "낮",
            BestTime::Foliage => "단풍",
            BestTime::Blossom => "벚꽃",
        }
    }

    pub fn theme(self) -> RouteTheme {
        match self {
            BestTime::Night => RouteTheme::Night,
            BestTime::Sunset => RouteTheme::Sunset,
            _ => RouteTheme::Day,
        }
    }
}

/// 3D 미리보기 색 (야경 → 밤, 노을 → 해 질 녘, 나머지 → 낮).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum RouteTheme {
    Day,
    Sunset,
    Night,
}

/// 가는 길에 보이는 풍경 (지도 글자, 들르는 곳이 아니다).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RouteHighlight {
    pub name: String,
    pub lat: f64,
    pub lon: f64,
}

/// 드라이브 코스(경치 길) 글 하나. 선 자체는 무거워 [EodiStore::drive_route_map]으로 따로 받는다.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DriveRoutePost {
    pub id: String,
    /// 예: "강변북로 한강 야경 드라이브"
    pub title: String,
    /// 2~4문장 소개 (무엇이 보이는지, 언제 좋은지, 쉬어 갈 곳 팁)
    pub body: String,
    pub author: String,
    pub mine: bool,
    /// 예: "서울"
    pub region: String,
    /// 시작·끝 이름 (예: "월드컵대교 북단" → "광진교 북단")
    pub start_name: String,
    pub end_name: String,
    pub start: GeoPoint,
    pub end: GeoPoint,
    pub best_time: BestTime,
    pub theme: RouteTheme,
    pub highlights: Vec<RouteHighlight>,
    /// 길 거리(m)·걸리는 시간(ms)
    pub distance_m: f64,
    pub duration_ms: i64,
    pub created_ms: i64,
    pub hearts: u32,
    pub hearted_by_me: bool,
    /// 다녀온 날 "YYYY-MM-DD"·그날 계절·날씨
    pub visited_day: String,
    pub season: String,
    pub weather: Option<String>,
    /// 글쓴이가 붙인 내 사진 (앱 안 사본 경로, 없으면 빈 목록). `gallery` 모듈.
    pub photos: Vec<String>,
}

/// 목록·추천 한 줄. [distance_km] = 지금 위치에서 길 위 가장 가까운 점까지(모르면 -1). [score]는 추천 점수(목록에선 0).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DriveRouteRecommendation {
    pub route: DriveRoutePost,
    pub distance_km: f64,
    pub score: f64,
}

/// 드라이브 코스 목록 정렬.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DriveRouteSort {
    /// 하트 많은 순
    Popular,
    /// 최신순
    Recent,
    /// 지금 위치에서 가까운 길 순 (위치를 모르면 인기순)
    Nearest,
}

/// 3D 미리보기 지도 데이터. [geojson] 모양:
/// - LineString `{"kind":"route"}` — 단순화한 길
/// - Point `{"kind":"start"|"end","name":…}` — 시작·끝
/// - Point `{"kind":"highlight","name":…}` — 풍경 글자
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DriveRouteMap {
    pub route_id: String,
    pub geojson: String,
    pub bounds: MapBounds,
}

/// 지도 카메라 (MapLibre에 그대로 넣는다). [bearing]은 화면 위쪽이 가리키는 방위(도).
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct RouteCamera {
    pub lat: f64,
    pub lon: f64,
    pub zoom: f64,
    pub bearing: f64,
    pub pitch: f64,
}

/// 미리보기 비행 한 장면. 같은 거리 간격으로 늘어놓았다 (시간 맞추기·부드럽게는 Kotlin 애니메이터가 한다).
/// [bearing]은 이어지게 풀어 둔 값이라 360을 넘거나 0 아래일 수 있다 (프레임 사이 보간이 한 바퀴 돌지 않게).
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct FlightFrame {
    pub lat: f64,
    pub lon: f64,
    pub bearing: f64,
    pub zoom: f64,
}

/// 내 기록에서 드라이브 코스로 올릴 수 있는 주행 하나 (집·회사 근처를 지운 뒤 길이).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DriveTripCandidate {
    /// 이 주행의 시작 시각 (올릴 때 그대로 넘긴다)
    pub trip_start_ms: i64,
    pub day: String,
    pub start_ms: i64,
    pub end_ms: i64,
    /// 올라갈 길의 시작·끝 이름 (집·회사 근처라 지웠거나 모르면 "출발 지점"·"도착 지점")
    pub from_name: String,
    pub to_name: String,
    /// 제목 기본값: "A → B 드라이브" / "B 가는 길" / "A에서 달린 길" / "11월 6일 드라이브"
    pub default_title: String,
    /// 올라갈 길 길이(m)·시간(ms)
    pub distance_m: f64,
    pub duration_ms: i64,
    /// 집·회사 근처를 지웠으면 true ("집·회사 근처는 자동으로 지웠어요")
    pub trimmed: bool,
    /// 이미 올렸으면 true (다시 올리면 글만 바뀌고 하트는 남는다)
    pub shared: bool,
    /// 그날 날씨를 받을 Open-Meteo 주소
    pub weather_url: String,
}

#[uniffi::export]
impl EodiStore {
    /// 드라이브 코스(경치 길) 목록. [from] = 지금 위치 (있으면 거리를 같이 준다; [DriveRouteSort::Nearest]는 가까운 순).
    /// `limit` 0이면 50개. [from]이 틀린 좌표면 `InvalidInput`.
    pub fn drive_routes(&self, sort: DriveRouteSort, from: Option<GeoPoint>, limit: u32) -> Result<Vec<DriveRouteRecommendation>, CoreError> {
        if let Some(p) = from {
            if !valid_point(p.lat, p.lon) {
                return Err(CoreError::InvalidInput(format!("from {p:?}")));
            }
        }
        let rows = load_routes(&*self.snapshot()?, None)?;
        let mut out: Vec<DriveRouteRecommendation> = rows
            .into_iter()
            .map(|r| {
                let distance_km = from.map_or(-1.0, |p| r.distance_km_from(p));
                DriveRouteRecommendation { route: r.post, distance_km, score: 0.0 }
            })
            .collect();
        let by_hearts = |a: &DriveRouteRecommendation, b: &DriveRouteRecommendation| {
            b.route.hearts.cmp(&a.route.hearts).then(b.route.created_ms.cmp(&a.route.created_ms)).then_with(|| a.route.id.cmp(&b.route.id))
        };
        match (sort, from) {
            (DriveRouteSort::Nearest, Some(_)) => {
                out.sort_by(|a, b| a.distance_km.total_cmp(&b.distance_km).then_with(|| by_hearts(a, b)))
            }
            (DriveRouteSort::Recent, _) => {
                out.sort_by(|a, b| b.route.created_ms.cmp(&a.route.created_ms).then_with(|| a.route.id.cmp(&b.route.id)))
            }
            _ => out.sort_by(by_hearts),
        }
        out.truncate(clamp_limit(limit));
        Ok(out)
    }

    /// 드라이브 코스 하나 (없으면 `InvalidInput`).
    pub fn drive_route(&self, id: String) -> Result<DriveRoutePost, CoreError> {
        Ok(route_by_id(&*self.snapshot()?, &id)?.post)
    }

    /// 드라이브 코스 하트 누르기/취소. 반환: 바뀐 하트 수. 없는 id면 `InvalidInput`.
    pub fn toggle_drive_route_heart(&self, id: String) -> Result<u32, CoreError> {
        let conn = self.lock_community()?;
        toggle_heart(&conn, "drive_route", &id)?.ok_or_else(|| CoreError::InvalidInput(format!("드라이브 코스 없음: {id}")))
    }

    /// 근처 드라이브 코스 추천 상위 [k]개 (에이전트 "여기 근처 드라이브 추천코스"). 내가 올린 길은 뺀다.
    ///
    /// 점수 = 인기 × 거리 감쇠 ([route_score]): 인기 = 1 + ln(1 + 하트), 거리 감쇠 = 0.5^(d / h),
    /// d = [from]에서 길 위 가장 가까운 점까지 km, h = max([radius_km] / 3, 3km). d > [radius_km]인 길은 빠진다.
    /// [from]이 None이면 전국 하트순 (거리 -1). 같은 점수면 하트 → id. [k] 0이면 3개(최대 20), 반경이 0 이하·NaN이면 30km.
    pub fn recommend_drive_routes(&self, from: Option<GeoPoint>, radius_km: f64, k: u32) -> Result<Vec<DriveRouteRecommendation>, CoreError> {
        let k = if k == 0 { 3 } else { k.min(20) } as usize;
        let radius_km = if radius_km.is_finite() && radius_km > 0.0 { radius_km } else { DEFAULT_RADIUS_KM };
        if let Some(p) = from {
            if !valid_point(p.lat, p.lon) {
                return Err(CoreError::InvalidInput(format!("from {p:?}")));
            }
        }
        let rows = load_routes(&*self.snapshot()?, None)?;
        let mut out: Vec<DriveRouteRecommendation> = rows
            .into_iter()
            .filter(|r| !r.post.mine)
            .filter_map(|r| match from {
                Some(p) => {
                    let d = r.distance_km_from(p);
                    (d <= radius_km).then(|| {
                        let score = route_score(r.post.hearts, d, radius_km);
                        DriveRouteRecommendation { route: r.post, distance_km: d, score }
                    })
                }
                None => Some(DriveRouteRecommendation { score: route_score(r.post.hearts, 0.0, radius_km), route: r.post, distance_km: -1.0 }),
            })
            .collect();
        out.sort_by(|a, b| b.score.total_cmp(&a.score).then(b.route.hearts.cmp(&a.route.hearts)).then_with(|| a.route.id.cmp(&b.route.id)));
        out.truncate(k);
        Ok(out)
    }

    /// 3D 미리보기 지도 데이터 (모양은 [DriveRouteMap]). 없는 id면 `InvalidInput`.
    pub fn drive_route_map(&self, id: String) -> Result<DriveRouteMap, CoreError> {
        let r = route_by_id(&*self.snapshot()?, &id)?;
        Ok(route_map(&r))
    }

    /// 길 방향을 따라 보는 카메라: 시작 → 끝 방향이 화면 위쪽, [pitch]도 기울여 길 전체가 [width]×[height](논리 픽셀,
    /// 안쪽 여백 [padding]) 안에 들어오게. 목록 카드 스냅숏과 상세 첫 화면이 같은 값을 쓴다. 없는 id면 `InvalidInput`.
    pub fn drive_route_camera(&self, id: String, width: f64, height: f64, padding: f64, pitch: f64) -> Result<RouteCamera, CoreError> {
        let r = route_by_id(&*self.snapshot()?, &id)?;
        Ok(fit_camera(&r.line, width, height, padding, pitch))
    }

    /// 미리보기 비행 경로: 길을 같은 간격 [frames]개(0이면 480, 최대 4000)로 나눈 카메라 자리. 방향은 조금 앞을 본다. 없는 id면 `InvalidInput`.
    pub fn drive_route_flight(&self, id: String, frames: u32) -> Result<Vec<FlightFrame>, CoreError> {
        let r = route_by_id(&*self.snapshot()?, &id)?;
        let n = if frames == 0 { FLIGHT_FRAMES } else { frames.clamp(2, MAX_FLIGHT_FRAMES) };
        Ok(flight(&r.line, n as usize))
    }

    /// 안내 구간: ① 길 시작점까지 ② 도착하면 끝점까지 — 둘 다 경유지 없음 ([route_legs]). 없는 id면 `InvalidInput`.
    pub fn drive_route_legs(&self, id: String) -> Result<Vec<CourseLeg>, CoreError> {
        Ok(route_legs(&route_by_id(&*self.snapshot()?, &id)?.post))
    }

    // ---- 내 기록에서 올리기 ----

    /// 드라이브 코스로 올릴 수 있는 내 주행 (기록이 있는 최근 [days]일, 0이면 60일; 최신순). 집·회사([ROUTE_TRIM_M]) 근처를
    /// 지우고 [MIN_SHARE_M] 넘게 남는 주행만.
    pub fn drive_share_candidates(&self, days: u32) -> Result<Vec<DriveTripCandidate>, CoreError> {
        let days = if days == 0 { 60 } else { days.min(365) };
        let summaries = self.day_summaries(days)?;
        let (anchors, shared, today) = {
            let conn = self.snapshot()?;
            (anchor_points(&conn)?, shared_trips(&conn)?, format_day(self.local_day(crate::places::now_ms())))
        };
        let mut out = Vec::new();
        for s in summaries.into_iter().filter(|s| s.trip_count > 0) {
            let detail = self.day_detail(s.day.clone())?;
            for t in detail.trips.iter().rev() {
                if let Some(c) = candidate(t, &s.day, &anchors, &today) {
                    out.push(DriveTripCandidate { shared: shared.contains(&t.start_ms), ..c });
                }
            }
        }
        Ok(out)
    }

    /// 내 주행 하나([trip_start_ms])를 드라이브 코스로 올린다. 집·회사 근처를 지운 가장 긴 조각이 길이 된다.
    /// 같은 주행을 다시 올리면 제목·소개·언제·날씨만 바뀌고 하트·올린 시각은 그대로다.
    /// 빈 제목, 없는 주행, 남는 길이 [MIN_SHARE_M]보다 짧으면 `InvalidInput`.
    pub fn share_drive_route(
        &self,
        trip_start_ms: i64,
        title: String,
        body: String,
        best_time: BestTime,
        weather: Option<String>,
        now_ms: i64,
    ) -> Result<DriveRoutePost, CoreError> {
        let title: String = title.trim().chars().take(60).collect();
        if title.is_empty() {
            return Err(CoreError::InvalidInput("제목이 비었다".into()));
        }
        let day = format_day(self.local_day(trip_start_ms));
        let trip = self
            .day_detail(day.clone())?
            .trips
            .into_iter()
            .find(|t| t.start_ms == trip_start_ms)
            .ok_or_else(|| CoreError::InvalidInput(format!("주행 없음: {trip_start_ms}")))?;
        let anchors = anchor_points(&*self.snapshot()?)?;
        let piece = shareable_piece(&trip, &anchors).ok_or_else(|| CoreError::InvalidInput("올릴 수 있는 길이 너무 짧다".into()))?;
        let (from_name, to_name) = end_names(&trip, &piece);
        let line = simplified(&piece.line);
        let region = region_of(&trip);
        let body: String = body.trim().chars().take(1000).collect();
        let conn = self.lock()?;
        let id = format!("me-d{trip_start_ms}");
        conn.execute(
            "INSERT INTO drive_route (id, title, body, author, mine, region, start_name, end_name, line, highlights, best_time,
                                      distance_m, duration_ms, created_ms, visited_day, weather, others_hearts, my_heart, trip_start_ms)
             VALUES (?1, ?2, ?3, ?4, 1, ?5, ?6, ?7, ?8, '[]', ?9, ?10, ?11, ?12, ?13, ?14, 0, 0, ?15)
             ON CONFLICT (id) DO UPDATE SET title = excluded.title, body = excluded.body, best_time = excluded.best_time,
                 region = excluded.region, start_name = excluded.start_name, end_name = excluded.end_name, line = excluded.line,
                 distance_m = excluded.distance_m, duration_ms = excluded.duration_ms, visited_day = excluded.visited_day,
                 weather = coalesce(excluded.weather, drive_route.weather)",
            params![
                id,
                title,
                body,
                ME,
                region,
                from_name,
                to_name,
                encode_line(&line),
                best_time.key(),
                piece.length_m,
                piece.duration_ms,
                now_ms,
                day,
                weather::clean_weather(weather),
                trip_start_ms
            ],
        )?;
        Ok(route_by_id(&conn, &id)?.post)
    }
}

/// 드라이브 코스 고를 수 있는 "언제" 목록 (화면 칩 순서).
#[uniffi::export]
pub fn drive_best_time_options() -> Vec<BestTime> {
    BestTime::ALL.to_vec()
}

// ---- 점수 · 안내 ----

/// 추천 점수 ([EodiStore::recommend_drive_routes] 설명).
pub(crate) fn route_score(hearts: u32, d_km: f64, radius_km: f64) -> f64 {
    let popularity = 1.0 + (1.0 + hearts as f64).ln();
    let half_km = (radius_km / 3.0).max(3.0);
    popularity * 0.5f64.powf(d_km.max(0.0) / half_km)
}

/// 안내 구간: 시작점까지(경유지 없음) → 끝점까지(경유지 없음).
pub fn route_legs(r: &DriveRoutePost) -> Vec<CourseLeg> {
    let stop = |name: &str, kind: &str, p: GeoPoint| StopFact {
        name: name.to_string(),
        category: kind.to_string(),
        address: r.region.clone(),
        arrive_ms: 0,
        leave_ms: 0,
        lat: p.lat,
        lon: p.lon,
        first_visit: false,
    };
    vec![
        CourseLeg { waypoints: vec![], destination: stop(&r.start_name, "드라이브 시작", r.start) },
        CourseLeg { waypoints: vec![], destination: stop(&r.end_name, "드라이브 끝", r.end) },
    ]
}

// ---- 지도 · 카메라 · 비행 ----

fn simplified(line: &[Point]) -> Vec<Point> {
    simplify(line, LINE_TOLERANCE_M).into_iter().map(|i| line[i]).collect()
}

fn bounds_of(line: &[Point]) -> MapBounds {
    let mut b = MapBounds { min_lat: f64::MAX, min_lon: f64::MAX, max_lat: f64::MIN, max_lon: f64::MIN };
    for p in line {
        b = MapBounds { min_lat: b.min_lat.min(p.lat), min_lon: b.min_lon.min(p.lon), max_lat: b.max_lat.max(p.lat), max_lon: b.max_lon.max(p.lon) };
    }
    b
}

fn point_feature(out: &mut String, p: GeoPoint, kind: &str, name: &str) {
    let _ = write!(out, r#",{{"type":"Feature","geometry":{{"type":"Point","coordinates":[{:.6},{:.6}]}},"properties":{{"kind":"{kind}""#, p.lon, p.lat);
    if let Some(n) = map_safe_name(name) {
        out.push_str(r#","name":"#);
        out.push_str(&Value::String(n).to_string());
    }
    out.push_str("}}");
}

pub(crate) fn route_map(r: &RouteRow) -> DriveRouteMap {
    let line = simplified(&r.line);
    let mut out = String::with_capacity(128 + line.len() * 24 + r.post.highlights.len() * 120);
    out.push_str(r#"{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":["#);
    for (i, p) in line.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        let _ = write!(out, "[{:.6},{:.6}]", p.lon, p.lat);
    }
    out.push_str(r#"]},"properties":{"kind":"route"}}"#);
    point_feature(&mut out, r.post.start, "start", &r.post.start_name);
    point_feature(&mut out, r.post.end, "end", &r.post.end_name);
    for h in &r.post.highlights {
        point_feature(&mut out, GeoPoint { lat: h.lat, lon: h.lon }, "highlight", &h.name);
    }
    out.push_str("]}");
    let all: Vec<Point> = line.iter().copied().chain(r.post.highlights.iter().map(|h| Point { lat: h.lat, lon: h.lon })).collect();
    DriveRouteMap { route_id: r.post.id.clone(), geojson: out, bounds: bounds_of(&all) }
}

/// 기준점 둘레를 평면(m)으로: x 동쪽, y 북쪽.
fn local_m(o: Point, p: Point) -> (f64, f64) {
    let k = o.lat.to_radians().cos();
    ((p.lon - o.lon).to_radians() * 6_371_008.8 * k, (p.lat - o.lat).to_radians() * 6_371_008.8)
}

fn from_local_m(o: Point, x: f64, y: f64) -> Point {
    let k = o.lat.to_radians().cos().max(1e-6);
    Point { lat: o.lat + (y / 6_371_008.8).to_degrees(), lon: o.lon + (x / (6_371_008.8 * k)).to_degrees() }
}

/// 길이 보이는 방향: 시작 → 끝. 시작·끝이 거의 같은(되돌아오는) 길이면 시작 → 가장 먼 점.
fn view_bearing(line: &[Point]) -> f64 {
    let (Some(&a), Some(&b)) = (line.first(), line.last()) else { return 0.0 };
    if haversine_m(a, b) > 200.0 {
        return bearing_deg(a, b);
    }
    let far = line.iter().copied().max_by(|p, q| haversine_m(a, *p).total_cmp(&haversine_m(a, *q))).unwrap_or(b);
    bearing_deg(a, far)
}

/// MapLibre Native 기본 세로 시야각(라디안, `util::DEFAULT_FOV`).
const FOV: f64 = 0.643_501_108_793_284_4;

/// 기울인 카메라로 본 화면 위치 (가운데 0, 위쪽 +): 땅 위 점(화면 축으로 돌린 m 좌표 [x], [y])을 m당 [scale]px,
/// 앞쪽 [c] m 자리를 화면 가운데에 둘 때. 카메라에서 화면 가운데까지 거리 [d0](px). 지평선 너머면 None.
fn screen_xy(x: f64, y: f64, scale: f64, c: f64, d0: f64, (sin_p, cos_p): (f64, f64)) -> Option<(f64, f64)> {
    let d = (y - c) * scale;
    let z = d0 + d * sin_p;
    (z > d0 * 0.2).then(|| (x * scale * d0 / z, d * cos_p * d0 / z))
}

/// 길이 화면에 꼭 들어오는 카메라 ([EodiStore::drive_route_camera]).
///
/// 시작 → 끝 방향이 화면 위쪽(멀리)이 되게 돌리고, 기울기 원근(가까운 쪽은 크게, 먼 쪽은 작게)을 MapLibre와 같은 식으로
/// 계산해 ① 위아래가 화면 가운데에 고르게 오도록 가운데 점을 길 방향으로 옮기고 ② 여백 안에 다 들어오는 가장 큰 줌을 찾는다.
pub(crate) fn fit_camera(line: &[Point], width: f64, height: f64, padding: f64, pitch: f64) -> RouteCamera {
    let pitch = if pitch.is_finite() { pitch.clamp(0.0, 60.0) } else { PREVIEW_PITCH };
    let bearing = view_bearing(line);
    let Some(&o) = line.first() else { return RouteCamera { lat: 37.5665, lon: 126.978, zoom: 10.0, bearing, pitch } };
    let (sb, cb) = bearing.to_radians().sin_cos();
    // 화면 오른쪽 = (cos b, -sin b), 위쪽 = (sin b, cos b) (동·북 평면에서)
    let pts: Vec<(f64, f64)> = line
        .iter()
        .map(|&p| {
            let (x, y) = local_m(o, p);
            (x * cb - y * sb, x * sb + y * cb)
        })
        .collect();
    let (x0, x1) = pts.iter().fold((f64::MAX, f64::MIN), |(a, b), p| (a.min(p.0), b.max(p.0)));
    let (y0, y1) = pts.iter().fold((f64::MAX, f64::MIN), |(a, b), p| (a.min(p.1), b.max(p.1)));
    let cx = (x0 + x1) / 2.0;
    let pts: Vec<(f64, f64)> = pts.into_iter().map(|(x, y)| (x - cx, y)).collect();
    let (w_half, h_half) = (((width - 2.0 * padding) / 2.0).max(32.0), ((height - 2.0 * padding) / 2.0).max(32.0));
    let d0 = 0.5 / (FOV / 2.0).tan() * height.max(64.0);
    let sc = pitch.to_radians().sin_cos();
    // 줌 [scale]에서 위아래가 고르게 오는 가운데 자리 c와 그때의 화면 범위 (지평선 너머로 넘어가면 None)
    let place = |scale: f64| -> Option<(f64, f64, f64, f64)> {
        let span = |c: f64| -> Option<(f64, f64, f64)> {
            let (mut ax, mut lo, mut hi) = (0.0f64, f64::MAX, f64::MIN);
            for &(x, y) in &pts {
                let (sx, sy) = screen_xy(x, y, scale, c, d0, sc)?;
                (ax, lo, hi) = (ax.max(sx.abs()), lo.min(sy), hi.max(sy));
            }
            Some((ax, lo, hi))
        };
        // 가운데를 앞으로 옮길수록 화면이 아래로 내려간다 → 위·아래 끝이 같은 거리에 오는 c를 반으로 나눠 찾는다
        let (mut a, mut b) = (y0, y1);
        for _ in 0..48 {
            let m = (a + b) / 2.0;
            match span(m) {
                Some((_, lo, hi)) if hi + lo > 0.0 => a = m,
                _ => b = m,
            }
        }
        let c = (a + b) / 2.0;
        span(c).map(|(ax, lo, hi)| (c, ax, lo, hi))
    };
    let fits = |scale: f64| place(scale).is_some_and(|(_, ax, lo, hi)| ax <= w_half && hi <= h_half && -lo <= h_half);
    // m당 px: 아주 작게(다 들어옴) ~ 아주 크게 사이에서 들어오는 가장 큰 값
    let (mut lo_s, mut hi_s) = (1e-6f64, 50.0f64);
    for _ in 0..60 {
        let m = (lo_s * hi_s).sqrt();
        if fits(m) {
            lo_s = m;
        } else {
            hi_s = m;
        }
    }
    let c = place(lo_s).map_or((y0 + y1) / 2.0, |p| p.0);
    let center = from_local_m(o, cx * cb + c * sb, -cx * sb + c * cb);
    let circ = EARTH_CIRCUMFERENCE_M * center.lat.to_radians().cos();
    let zoom = (lo_s * circ / WORLD_PX).log2().clamp(3.0, 16.5);
    RouteCamera { lat: center.lat, lon: center.lon, zoom, bearing, pitch }
}

/// 같은 거리 간격으로 [n]개 점을 다시 뽑는다 (처음·끝 포함).
fn resample(line: &[Point], n: usize) -> Vec<Point> {
    if line.len() < 2 || n < 2 {
        return line.to_vec();
    }
    let total = line_length_m(line);
    let mut out = Vec::with_capacity(n);
    let (mut seg, mut seg_start) = (0usize, 0.0f64);
    for i in 0..n {
        let target = total * i as f64 / (n - 1) as f64;
        while seg + 2 < line.len() && seg_start + haversine_m(line[seg], line[seg + 1]) < target {
            seg_start += haversine_m(line[seg], line[seg + 1]);
            seg += 1;
        }
        let len = haversine_m(line[seg], line[seg + 1]);
        let t = if len > 0.0 { ((target - seg_start) / len).clamp(0.0, 1.0) } else { 0.0 };
        let (a, b) = (line[seg], line[seg + 1]);
        out.push(Point { lat: a.lat + (b.lat - a.lat) * t, lon: a.lon + (b.lon - a.lon) * t });
    }
    out
}

/// 비행 높이(줌): 건물이 입체로 보이는 높이(14 이상)에서, 짧은 길은 더 낮게, 긴 길은 조금 높게(너무 빨리 지나가지 않게).
fn flight_zoom(length_m: f64) -> f64 {
    (15.6 - 0.5 * (length_m.max(1.0) / 5_000.0).log2()).clamp(14.0, 15.6)
}

/// 미리보기 비행 ([EodiStore::drive_route_flight]): 같은 간격 장면 + 앞(길 길이의 6%, 600m 이상)을 보는 방향을
/// 앞뒤로 고르게 다듬고(나들목·굽잇길에서 흔들리지 않게) 이어지게 푼 것.
pub(crate) fn flight(line: &[Point], n: usize) -> Vec<FlightFrame> {
    let line = simplified(line);
    if line.len() < 2 {
        return line.iter().map(|p| FlightFrame { lat: p.lat, lon: p.lon, bearing: 0.0, zoom: 14.0 }).collect();
    }
    let total = line_length_m(&line);
    let pts = resample(&line, n);
    let step = total / (n - 1) as f64;
    let ahead = ((total * 0.06).max(600.0) / step.max(1e-6)).ceil() as usize;
    let raw: Vec<f64> = (0..n)
        .map(|i| {
            let j = (i + ahead).min(n - 1);
            if j > i { bearing_deg(pts[i], pts[j]) } else { bearing_deg(pts[i.saturating_sub(ahead)], pts[i]) }
        })
        .collect();
    // 각도 평균은 단위 벡터로 (359°와 1° 평균이 180°가 되지 않게)
    let win = (n / 25).max(2);
    let smooth: Vec<f64> = (0..n)
        .map(|i| {
            let (lo, hi) = (i.saturating_sub(win), (i + win).min(n - 1));
            let (s, c) = raw[lo..=hi].iter().fold((0.0, 0.0), |(s, c), b| (s + b.to_radians().sin(), c + b.to_radians().cos()));
            s.atan2(c).to_degrees().rem_euclid(360.0)
        })
        .collect();
    let zoom = flight_zoom(total);
    let mut prev = smooth[0];
    smooth
        .iter()
        .zip(&pts)
        .map(|(&b, p)| {
            let mut d = (b - prev.rem_euclid(360.0)).rem_euclid(360.0);
            if d > 180.0 {
                d -= 360.0;
            }
            prev += d;
            FlightFrame { lat: p.lat, lon: p.lon, bearing: prev, zoom }
        })
        .collect()
}

// ---- 내 기록 올리기 ----

/// 주행에서 올릴 조각: 집·회사 [ROUTE_TRIM_M] 안을 지우고 남은 가장 긴 조각.
pub(crate) struct Piece {
    pub line: Vec<Point>,
    pub length_m: f64,
    pub duration_ms: i64,
    /// 앞·뒤가 지워졌나
    pub cut_start: bool,
    pub cut_end: bool,
}

pub(crate) fn shareable_piece(t: &TripSummary, anchors: &[Point]) -> Option<Piece> {
    let path: Vec<Point> = t.path.iter().map(|p| Point::from(*p)).collect();
    if path.len() < 2 {
        return None;
    }
    let pieces = trim_near(&path, anchors, ROUTE_TRIM_M);
    let best = pieces.into_iter().max_by(|a, b| line_length_m(a).total_cmp(&line_length_m(b)))?;
    let length_m = line_length_m(&best);
    if length_m < MIN_SHARE_M {
        return None;
    }
    let full = line_length_m(&path).max(1.0);
    let duration_ms = (((t.end_ms - t.start_ms).max(0) as f64) * (length_m / full).min(1.0)) as i64;
    Some(Piece { cut_start: best.first() != path.first(), cut_end: best.last() != path.last(), line: best, length_m, duration_ms })
}

/// 올릴 길의 시작·끝 이름: 지우지 않은 끝이고 장소 이름을 알면 그 이름, 아니면 "출발 지점"·"도착 지점".
fn end_names(t: &TripSummary, piece: &Piece) -> (String, String) {
    let name = |cut: bool, label: &Option<crate::places::PlaceLabel>, fallback: &str| {
        label.as_ref().filter(|l| !cut && !l.name.trim().is_empty()).map_or_else(|| fallback.to_string(), |l| l.name.trim().to_string())
    };
    (name(piece.cut_start, &t.start_place, FROM_UNKNOWN), name(piece.cut_end, &t.end_place, TO_UNKNOWN))
}

/// 제목 기본값 (모르는 끝은 빼고 지은 이름).
fn default_title(from: &str, to: &str, day: &str) -> String {
    match (from != FROM_UNKNOWN, to != TO_UNKNOWN) {
        (true, true) => format!("{from} → {to} 드라이브"),
        (false, true) => format!("{to} 가는 길"),
        (true, false) => format!("{from}에서 달린 길"),
        (false, false) => {
            let mut it = day.split('-').skip(1).map(|x| x.trim_start_matches('0'));
            format!("{}월 {}일 드라이브", it.next().unwrap_or(""), it.next().unwrap_or(""))
        }
    }
}

/// 지역: 도착(없으면 출발) 장소 주소의 앞 두 낱말 ("강원 강릉시").
fn region_of(t: &TripSummary) -> String {
    [&t.end_place, &t.start_place]
        .into_iter()
        .flatten()
        .map(|l| l.address.split_whitespace().take(2).collect::<Vec<_>>().join(" "))
        .find(|r| !r.is_empty())
        .unwrap_or_default()
}

fn candidate(t: &TripSummary, day: &str, anchors: &[Point], today: &str) -> Option<DriveTripCandidate> {
    let piece = shareable_piece(t, anchors)?;
    let (from_name, to_name) = end_names(t, &piece);
    let first = piece.line[0];
    Some(DriveTripCandidate {
        default_title: default_title(&from_name, &to_name, day),
        trip_start_ms: t.start_ms,
        day: day.to_string(),
        start_ms: t.start_ms,
        end_ms: t.end_ms,
        from_name,
        to_name,
        distance_m: piece.length_m,
        duration_ms: piece.duration_ms,
        trimmed: piece.cut_start || piece.cut_end,
        shared: false,
        weather_url: super::open_meteo_url(first.lat, first.lon, day.to_string(), today.to_string()),
    })
}

fn anchor_points(conn: &Connection) -> Result<Vec<Point>, CoreError> {
    let mut st = conn.prepare_cached("SELECT lat, lon FROM memory_anchor")?;
    let rows = st.query_map([], |r| Ok(Point { lat: r.get(0)?, lon: r.get(1)? }))?;
    Ok(rows.collect::<Result<_, _>>()?)
}

fn shared_trips(conn: &Connection) -> Result<std::collections::HashSet<i64>, CoreError> {
    let mut st = conn.prepare_cached("SELECT trip_start_ms FROM drive_route WHERE trip_start_ms IS NOT NULL")?;
    let rows = st.query_map([], |r| r.get(0))?;
    Ok(rows.collect::<Result<_, _>>()?)
}

// ---- 읽기 · 쓰기 ----

/// 저장된 한 줄: 글 + 선.
pub(crate) struct RouteRow {
    pub post: DriveRoutePost,
    pub line: Vec<Point>,
}

impl RouteRow {
    fn distance_km_from(&self, p: GeoPoint) -> f64 {
        nearest_distance_m(Point::from(p), &self.line) / 1000.0
    }
}

const ROUTE_COLS: &str = "id, title, body, author, mine, region, start_name, end_name, line, highlights, best_time, distance_m,
     duration_ms, created_ms, others_hearts + my_heart, my_heart, visited_day, weather, photos";

fn route_from_row(r: &Row) -> rusqlite::Result<RouteRow> {
    let line = decode_line(&r.get::<_, String>(8)?);
    let visited_day: String = r.get(16)?;
    let best_time = BestTime::from_key(&r.get::<_, String>(10)?).unwrap_or(BestTime::Day);
    let gp = |p: Option<&Point>| p.map_or(GeoPoint { lat: 0.0, lon: 0.0 }, |p| GeoPoint { lat: p.lat, lon: p.lon });
    Ok(RouteRow {
        post: DriveRoutePost {
            id: r.get(0)?,
            title: r.get(1)?,
            body: r.get(2)?,
            author: r.get(3)?,
            mine: r.get(4)?,
            region: r.get(5)?,
            start_name: r.get(6)?,
            end_name: r.get(7)?,
            start: gp(line.first()),
            end: gp(line.last()),
            best_time,
            theme: best_time.theme(),
            highlights: decode_highlights(&r.get::<_, String>(9)?),
            distance_m: r.get(11)?,
            duration_ms: r.get(12)?,
            created_ms: r.get(13)?,
            hearts: hearts_of(r.get(14)?),
            hearted_by_me: r.get(15)?,
            season: season_label(visited_day.clone()),
            visited_day,
            weather: r.get(17)?,
            photos: crate::gallery::decode_paths(&r.get::<_, String>(18)?),
        },
        line,
    })
}

pub(crate) fn load_routes(conn: &Connection, id: Option<&str>) -> Result<Vec<RouteRow>, CoreError> {
    let rows = match id {
        Some(id) => {
            let mut st = conn.prepare_cached(&format!("SELECT {ROUTE_COLS} FROM drive_route WHERE id = ?1"))?;
            let rows = st.query_map([id], route_from_row)?;
            rows.collect::<Result<Vec<_>, _>>()?
        }
        None => {
            let mut st = conn.prepare_cached(&format!("SELECT {ROUTE_COLS} FROM drive_route"))?;
            let rows = st.query_map([], route_from_row)?;
            rows.collect::<Result<Vec<_>, _>>()?
        }
    };
    // 선이 깨진 줄은 지도·안내를 못 하므로 뺀다
    Ok(rows.into_iter().filter(|r| r.line.len() >= 2).collect())
}

pub(crate) fn route_by_id(conn: &Connection, id: &str) -> Result<RouteRow, CoreError> {
    load_routes(conn, Some(id))?.pop().ok_or_else(|| CoreError::InvalidInput(format!("드라이브 코스 없음: {id}")))
}

pub(crate) fn encode_line(line: &[Point]) -> String {
    let mut s = String::with_capacity(2 + line.len() * 22);
    s.push('[');
    for (i, p) in line.iter().enumerate() {
        if i > 0 {
            s.push(',');
        }
        let _ = write!(s, "[{:.5},{:.5}]", p.lat, p.lon);
    }
    s.push(']');
    s
}

pub(crate) fn decode_line(s: &str) -> Vec<Point> {
    serde_json::from_str::<Vec<[f64; 2]>>(s)
        .unwrap_or_default()
        .into_iter()
        .filter(|[la, lo]| valid_point(*la, *lo))
        .map(|[lat, lon]| Point { lat, lon })
        .collect()
}

pub(crate) fn encode_highlights(h: &[RouteHighlight]) -> String {
    Value::Array(h.iter().map(|h| json!({"n": h.name, "la": h.lat, "lo": h.lon})).collect()).to_string()
}

fn decode_highlights(s: &str) -> Vec<RouteHighlight> {
    serde_json::from_str::<Value>(s)
        .ok()
        .and_then(|v| v.as_array().cloned())
        .unwrap_or_default()
        .iter()
        .filter_map(|v| Some(RouteHighlight { name: v["n"].as_str()?.to_string(), lat: v["la"].as_f64()?, lon: v["lo"].as_f64()? }))
        .collect()
}

#[cfg(test)]
mod tests;

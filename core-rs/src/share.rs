//! 공유 이미지 카드 (카톡 등으로 보내는 한 장): 하루 · 에피소드(나들이·여행) · 커뮤니티 코스.
//!
//! 카드에 들어갈 것을 모두 여기서 계산한다. Kotlin은 받은 값으로 그림만 그린다 (`ui/share`).
//! - 제목 · 날짜 글 · 계절 · 날씨(커뮤니티에 올릴 때 받아 둔 값이 있으면)
//! - 들른 곳 (순서 번호, 이름, 사진을 찾을 좌표) + 작은 길 그림 위 위치
//! - 작은 길 그림: 주행 경로를 그림 칸 비율에 맞춰 0~1로 바꾸고 점 수를 줄인 선들 (지도 없이 모양만)
//! - 거리 · 시간, 보낼 글 ("11월 6일 가을 강릉 바다와 순두부 나들이 · 경포해변 → 초당할머니순두부 → … #어디였지")
//!
//! # 개인정보
//! - 집·회사(`memory_anchor`의 모든 줄, 커뮤니티 올리기와 같은 기준점)에서 [STOP_PRIVACY_M] 안의 들른 곳은 뺀다
//!   (빼서 남은 곳으로 순서 번호를 다시 매긴다). 뺀 수는 [ShareCard::hidden_count]로 알려 준다.
//! - 경로는 집·회사에서 [ROUTE_TRIM_M] 안의 점을 지우고 그 자리에서 선을 끊는다 → 집에서 나가는 길 · 들어오는 길이
//!   그림에 남지 않는다. 지운 점보다 바깥 점만 남기므로 선 끝이 집 바로 앞에 놓이지 않는다.
//! - 거리는 남은 선의 길이다 (집에서 출발한 거리로 집을 거꾸로 짐작할 수 없게).
//! - 그림에는 좌표·지도·축척이 없다. 동승자 정보는 처음부터 카드에 없다.
//! - 다른 사람이 올린 커뮤니티 코스는 이미 공개된 곳이라 거르지 않는다 (내가 올린 코스는 거른다).

use rusqlite::{params, Connection, OptionalExtension};

use crate::community::season_label;
use crate::geo::{haversine_m, simplify, Point};
use crate::memory::{EpisodeKind, StopFact};
use crate::store::{format_day, parse_day, CoreError, EodiStore, StaySummary, TripSummary};

/// 집·회사에서 이 거리(m) 안의 들른 곳은 카드에서 뺀다 (커뮤니티 올리기와 같은 기준).
pub const STOP_PRIVACY_M: f64 = crate::community::PRIVACY_RADIUS_M;
/// 집·회사에서 이 거리(m) 안의 경로 점은 지우고 선을 끊는다 (들른 곳 기준보다 넓게: 선 끝이 집을 가리키지 않게).
pub const ROUTE_TRIM_M: f64 = 1_200.0;
/// 길 그림 가장자리 여백 (그림 칸 짧은 변에 대한 비율). 순서 동그라미가 잘리지 않게.
pub const SKETCH_MARGIN: f64 = 0.12;
/// 길 그림의 최소 범위(m). 몇십 m 안에서 맴돈 날이 화면 가득 확대되지 않게.
const MIN_EXTENT_M: f64 = 600.0;
/// 단순화 허용 오차 = 그림 범위 / 이 값 (그림 폭 약 300px에서 1px 아래).
const SIMPLIFY_DIVISOR: f64 = 400.0;
/// 보낼 글에 넣는 들른 곳 이름 수 (넘치면 "…").
const TEXT_STOPS: usize = 3;
/// 앱 이름 태그.
const BRAND_TAG: &str = "#어디였지";

/// 무엇을 공유하나.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum, serde::Serialize, serde::Deserialize)]
pub enum ShareKind {
    /// 하루 전체
    Day,
    /// 나들이·여행 한 번 (기억 에이전트 카드)
    Episode,
    /// 커뮤니티 드라이브 코스 (들른 곳만 있고 실제 경로는 없다 → 들른 곳을 순서대로 이은 선)
    Course,
}

/// 길 그림 위 한 점. x는 왼쪽 0 → 오른쪽 1, y는 위 0 → 아래 1 (북쪽이 위). 그림 칸 비율에 맞춰 이미 늘려 둔 값이다.
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record, serde::Serialize, serde::Deserialize)]
pub struct SketchPoint {
    pub x: f32,
    pub y: f32,
}

/// 끊기지 않은 선 하나 (주행 하나, 또는 집·회사 근처에서 끊긴 조각).
#[derive(Debug, Clone, PartialEq, uniffi::Record, serde::Serialize, serde::Deserialize)]
pub struct SketchLine {
    pub points: Vec<SketchPoint>,
}

/// 카드에 올라가는 들른 곳 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record, serde::Serialize, serde::Deserialize)]
pub struct ShareStop {
    /// 카드 안 순서 (1부터, 뺀 곳을 건너뛰고 다시 매긴 번호)
    pub order: u32,
    pub name: String,
    pub category: String,
    pub address: String,
    /// 사진을 찾을 좌표 (카드에 글로 쓰지 않는다)
    pub lat: f64,
    pub lon: f64,
    /// 길 그림 위 위치
    pub at: SketchPoint,
}

/// 공유 이미지 카드 한 장의 내용.
#[derive(Debug, Clone, PartialEq, uniffi::Record, serde::Serialize, serde::Deserialize)]
pub struct ShareCard {
    pub kind: ShareKind,
    /// 큰 제목 (AI 하루 제목 → 에피소드 제목 → 규칙 라벨)
    pub title: String,
    /// 다녀온 날 "YYYY-MM-DD"
    pub day: String,
    /// "2025년 11월 6일 목요일"
    pub date_text: String,
    /// "가을 · 단풍철"
    pub season: String,
    /// "맑음 · 14°C" (모르면 None)
    pub weather: Option<String>,
    /// 지역 (예: "강원 강릉시", 모르면 빈 문자열)
    pub region: String,
    pub stops: Vec<ShareStop>,
    /// 길 그림 선들 (실제 경로, 코스는 들른 곳을 이은 선)
    pub lines: Vec<SketchLine>,
    /// true면 [lines]가 실제 경로가 아니라 들른 곳을 이은 선이다 (점선으로 그린다)
    pub straight: bool,
    /// 거리(m): 하루·에피소드는 그림에 남은 경로 길이, 코스는 코스 거리
    pub distance_m: f64,
    /// 시간(ms): 하루·에피소드는 운전 시간 합, 코스는 첫 곳 도착 ~ 마지막 곳 떠남
    pub duration_ms: i64,
    /// 집·회사 근처라 뺀 들른 곳 수
    pub hidden_count: u32,
    /// 카드와 같이 보낼 짧은 글 (끝 줄에 [route_url])
    pub share_text: String,
    /// 받는 사람이 누르면 카카오맵에서 들른 곳을 순서대로 잇는 운전 경로가 열리는 링크 (들른 곳이 없으면 빈 문자열)
    #[serde(default)]
    pub route_url: String,
}

#[uniffi::export]
impl EodiStore {
    /// 하루(`day` = "YYYY-MM-DD") 공유 카드. [sketch_aspect] = 카드의 길 그림 칸 가로/세로 비율.
    /// 날짜 형식이 틀리면 `InvalidInput`. 기록이 없는 날도 (빈 그림으로) 만든다.
    pub fn share_card_day(&self, day: String, sketch_aspect: f64) -> Result<ShareCard, CoreError> {
        let d = parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let detail = self.day_detail(day.clone())?;
        let episodes = self.episodes_for_day(day.clone())?;
        let (anchors, weather) = {
            let conn = self.snapshot()?;
            (anchor_points(&conn)?, saved_weather(&conn, &day, None)?)
        };
        let title = detail
            .summary
            .title
            .clone()
            .filter(|t| !t.trim().is_empty())
            .or_else(|| episodes.iter().find(|e| is_leisure(e.facts.kind)).map(|e| e.title.clone().unwrap_or(e.facts.rule_label.clone())))
            .unwrap_or_else(|| "드라이브 기록".to_string());
        let region = episodes.iter().map(|e| e.facts.region.trim()).find(|r| !r.is_empty()).unwrap_or("").to_string();
        let stops: Vec<StopFact> = detail.stays.iter().filter_map(stay_fact).collect();
        let paths: Vec<Vec<Point>> = detail.trips.iter().map(trip_points).collect();
        let raw = RawCard {
            kind: ShareKind::Day,
            title,
            day: format_day(d),
            weather,
            region,
            stops,
            paths,
            straight: false,
            fixed_distance_m: None,
            duration_ms: detail.summary.drive_millis,
        };
        Ok(raw.build(&anchors, sketch_aspect))
    }

    /// 에피소드(나들이·여행 한 번) 공유 카드 — 그 시간 안의 주행 경로와 들른 곳만. 없는 id면 `InvalidInput`.
    pub fn share_card_episode(&self, episode_id: i64, sketch_aspect: f64) -> Result<ShareCard, CoreError> {
        let (ep, day_title) = self.episode_by_id(episode_id)?;
        let f = &ep.facts;
        // 하룻밤 넘는 여행도 있어 시작~끝 날짜를 모두 본다 (2주까지)
        let (d0, d1) = (self.local_day(f.start_ms), self.local_day(f.end_ms).min(self.local_day(f.start_ms) + 14));
        let mut trips: Vec<TripSummary> = Vec::new();
        for d in d0..=d1 {
            trips.extend(self.day_detail(format_day(d))?.trips.into_iter().filter(|t| in_span(t, f.start_ms, f.end_ms)));
        }
        let (anchors, weather) = {
            let conn = self.snapshot()?;
            (anchor_points(&conn)?, saved_weather(&conn, &f.day, Some(episode_id))?)
        };
        let title = [ep.title.clone(), day_title]
            .into_iter()
            .flatten()
            .map(|t| t.trim().to_string())
            .find(|t| !t.is_empty())
            .unwrap_or_else(|| f.rule_label.clone());
        let raw = RawCard {
            kind: ShareKind::Episode,
            title,
            day: f.day.clone(),
            weather,
            region: f.region.clone(),
            stops: f.stops.clone(),
            paths: trips.iter().map(trip_points).collect(),
            straight: false,
            fixed_distance_m: None,
            duration_ms: trips.iter().map(|t| (t.end_ms - t.start_ms).max(0)).sum(),
        };
        Ok(raw.build(&anchors, sketch_aspect))
    }

    /// 커뮤니티 코스 공유 카드 — 들른 곳을 순서대로 이은 선. 내가 올린 코스만 집·회사 근처를 거른다. 없는 id면 `InvalidInput`.
    pub fn share_card_course(&self, post_id: String, sketch_aspect: f64) -> Result<ShareCard, CoreError> {
        let post = self.course_by_id(&post_id)?;
        let anchors = if post.mine { anchor_points(&*self.snapshot()?)? } else { Vec::new() };
        let raw = RawCard {
            kind: ShareKind::Course,
            title: post.title.clone(),
            day: post.visited_day.clone(),
            weather: post.weather.clone(),
            region: post.region.clone(),
            stops: post.stops.clone(),
            paths: Vec::new(),
            straight: true,
            fixed_distance_m: Some(post.distance_m),
            duration_ms: post.duration_ms,
        };
        Ok(raw.build(&anchors, sketch_aspect))
    }
}

/// 카드 재료 (개인정보 거르기·그림 맞추기 전).
pub(crate) struct RawCard {
    pub kind: ShareKind,
    pub title: String,
    pub day: String,
    pub weather: Option<String>,
    pub region: String,
    /// 들른 곳 (도착 순서, 이름 없는 곳 포함)
    pub stops: Vec<StopFact>,
    /// 주행 경로들 (시간순). 비었고 [straight]면 들른 곳을 잇는다.
    pub paths: Vec<Vec<Point>>,
    pub straight: bool,
    /// 코스처럼 거리가 정해져 있으면 그 값, 아니면 남은 경로 길이
    pub fixed_distance_m: Option<f64>,
    pub duration_ms: i64,
}

impl RawCard {
    /// 집·회사 근처를 거르고([anchors]), 그림 칸 비율([sketch_aspect], 가로/세로)에 맞춰 카드를 만든다.
    pub(crate) fn build(self, anchors: &[Point], sketch_aspect: f64) -> ShareCard {
        let named: Vec<&StopFact> = self.stops.iter().filter(|s| !s.name.trim().is_empty()).collect();
        let kept: Vec<&StopFact> = named.iter().copied().filter(|s| !near_any(stop_point(s), anchors, STOP_PRIVACY_M)).collect();
        let hidden = (named.len() - kept.len()) as u32;

        let lines_m: Vec<Vec<Point>> = if self.straight {
            let pts: Vec<Point> = kept.iter().map(|s| stop_point(s)).collect();
            if pts.len() >= 2 {
                vec![pts]
            } else {
                Vec::new()
            }
        } else {
            self.paths.iter().flat_map(|p| trim_near(p, anchors, ROUTE_TRIM_M)).collect()
        };
        let distance_m = self.fixed_distance_m.unwrap_or_else(|| lines_m.iter().map(|l| line_length_m(l)).sum());

        let stop_pts: Vec<Point> = kept.iter().map(|s| stop_point(s)).collect();
        let frame = Frame::fit(lines_m.iter().flatten().chain(stop_pts.iter()).copied(), sketch_aspect);
        let lines = lines_m
            .iter()
            .map(|l| {
                let idx = simplify(l, frame.tolerance_m());
                SketchLine { points: idx.into_iter().map(|i| frame.place(l[i])).collect() }
            })
            .filter(|l| l.points.len() >= 2)
            .collect();
        let stops: Vec<ShareStop> = kept
            .iter()
            .enumerate()
            .map(|(i, s)| ShareStop {
                order: i as u32 + 1,
                name: s.name.trim().to_string(),
                category: s.category.clone(),
                address: s.address.clone(),
                lat: s.lat,
                lon: s.lon,
                at: frame.place(stop_point(s)),
            })
            .collect();
        let season = season_label(self.day.clone());
        let route_url = crate::handoff::kakao_route_url(&stops);
        let mut share_text = share_text(&self.day, &season, &self.title, &stops, distance_m);
        if !route_url.is_empty() {
            share_text.push_str("\n경로 보기 ▶ ");
            share_text.push_str(&route_url);
        }
        ShareCard {
            kind: self.kind,
            date_text: date_text(&self.day),
            title: self.title.trim().to_string(),
            day: self.day,
            season,
            weather: self.weather.filter(|w| !w.trim().is_empty()),
            region: self.region.trim().to_string(),
            stops,
            lines,
            straight: self.straight,
            distance_m,
            duration_ms: self.duration_ms.max(0),
            hidden_count: hidden,
            share_text,
            route_url,
        }
    }
}

/// 위경도 → 길 그림 칸(0~1) 변환. 모든 점이 들어가게, 가로세로 비율은 지키고(늘리지 않는다) 가운데에 놓는다.
pub(crate) struct Frame {
    /// 기준점(범위 가운데)과 경도 1도의 m 비율
    center: Point,
    k_lon: f64,
    /// 1 m → 그림 칸 가로·세로 비율
    sx: f64,
    sy: f64,
    extent_m: f64,
}

const M_PER_DEG: f64 = 111_195.0;

impl Frame {
    pub(crate) fn fit(points: impl Iterator<Item = Point>, aspect: f64) -> Frame {
        let aspect = if aspect.is_finite() && aspect > 0.0 { aspect.clamp(0.2, 5.0) } else { 1.0 };
        let (mut lat0, mut lat1, mut lon0, mut lon1) = (f64::MAX, f64::MIN, f64::MAX, f64::MIN);
        for p in points {
            lat0 = lat0.min(p.lat);
            lat1 = lat1.max(p.lat);
            lon0 = lon0.min(p.lon);
            lon1 = lon1.max(p.lon);
        }
        if lat0 > lat1 {
            // 점이 하나도 없다 → 아무 데나 (그릴 것이 없다)
            (lat0, lat1, lon0, lon1) = (0.0, 0.0, 0.0, 0.0);
        }
        let center = Point { lat: (lat0 + lat1) / 2.0, lon: (lon0 + lon1) / 2.0 };
        let k_lon = center.lat.to_radians().cos();
        let w_m = ((lon1 - lon0) * k_lon * M_PER_DEG).max(0.0);
        let h_m = ((lat1 - lat0) * M_PER_DEG).max(0.0);
        // 칸 높이를 1, 가로를 aspect로 보고 여백을 뺀 안쪽에 범위가 꼭 들어가는 m당 크기를 고른다
        let margin = SKETCH_MARGIN * aspect.min(1.0);
        let (inner_w, inner_h) = (aspect - 2.0 * margin, 1.0 - 2.0 * margin);
        let per_m = (inner_w / w_m.max(MIN_EXTENT_M)).min(inner_h / h_m.max(MIN_EXTENT_M));
        let extent_m = w_m.max(h_m).max(MIN_EXTENT_M);
        Frame { center, k_lon, sx: per_m / aspect, sy: per_m, extent_m }
    }

    /// 점 → 그림 칸 좌표 (가운데 0.5, 북쪽이 위).
    pub(crate) fn place(&self, p: Point) -> SketchPoint {
        let dx = (p.lon - self.center.lon) * self.k_lon * M_PER_DEG;
        let dy = (p.lat - self.center.lat) * M_PER_DEG;
        SketchPoint { x: (0.5 + dx * self.sx) as f32, y: (0.5 - dy * self.sy) as f32 }
    }

    /// 단순화 허용 오차(m): 그림에서 1px 아래로 보이는 정도.
    fn tolerance_m(&self) -> f64 {
        self.extent_m / SIMPLIFY_DIVISOR
    }
}

/// 경로에서 기준점들 [radius_m] 안의 점을 지우고, 지운 자리에서 선을 끊는다. 점이 2개 안 되는 조각은 버린다.
pub(crate) fn trim_near(path: &[Point], anchors: &[Point], radius_m: f64) -> Vec<Vec<Point>> {
    let mut out: Vec<Vec<Point>> = Vec::new();
    let mut cur: Vec<Point> = Vec::new();
    for &p in path {
        if near_any(p, anchors, radius_m) {
            if cur.len() >= 2 {
                out.push(std::mem::take(&mut cur));
            } else {
                cur.clear();
            }
        } else {
            cur.push(p);
        }
    }
    if cur.len() >= 2 {
        out.push(cur);
    }
    out
}

fn near_any(p: Point, anchors: &[Point], radius_m: f64) -> bool {
    anchors.iter().any(|&a| haversine_m(a, p) <= radius_m)
}

fn line_length_m(l: &[Point]) -> f64 {
    l.windows(2).map(|w| haversine_m(w[0], w[1])).sum()
}

/// "2025년 11월 6일 목요일". 형식이 틀리면 받은 글 그대로.
pub(crate) fn date_text(day: &str) -> String {
    let Some(d) = parse_day(day) else { return day.to_string() };
    let (y, m, dd) = ymd(day);
    // 1970-01-01은 목요일
    const WEEK: [&str; 7] = ["목", "금", "토", "일", "월", "화", "수"];
    format!("{y}년 {m}월 {dd}일 {}요일", WEEK[d.rem_euclid(7) as usize])
}

fn ymd(day: &str) -> (i64, i64, i64) {
    let mut it = day.split('-').map(|s| s.parse::<i64>().unwrap_or(0));
    (it.next().unwrap_or(0), it.next().unwrap_or(0), it.next().unwrap_or(0))
}

/// 보낼 글: "11월 6일 가을 강릉 바다와 순두부 나들이 · 경포해변 → 초당할머니순두부 → … #어디였지".
/// 제목에 계절 말이 이미 있으면 계절을 다시 붙이지 않는다. 들른 곳이 없으면 거리만 붙인다.
pub(crate) fn share_text(day: &str, season: &str, title: &str, stops: &[ShareStop], distance_m: f64) -> String {
    let mut s = String::new();
    if parse_day(day).is_some() {
        let (_, m, d) = ymd(day);
        s.push_str(&format!("{m}월 {d}일 "));
    }
    let season_word = season.split(" · ").next().unwrap_or("").trim();
    let title = title.trim();
    if !season_word.is_empty() && !title.contains(season_word) {
        s.push_str(season_word);
        s.push(' ');
    }
    s.push_str(title);
    if !stops.is_empty() {
        let mut names: Vec<&str> = stops.iter().take(TEXT_STOPS).map(|st| st.name.as_str()).collect();
        if stops.len() > TEXT_STOPS {
            names.push("…");
        }
        s.push_str(" · ");
        s.push_str(&names.join(" → "));
    } else if distance_m >= 1_000.0 {
        s.push_str(&format!(" · {}km 드라이브", (distance_m / 1000.0).round() as i64));
    }
    s.push(' ');
    s.push_str(BRAND_TAG);
    s
}

/// 집·회사 좌표 (커뮤니티 올리기와 같은 기준: memory_anchor의 모든 줄).
fn anchor_points(conn: &Connection) -> Result<Vec<Point>, CoreError> {
    let mut st = conn.prepare_cached("SELECT lat, lon FROM memory_anchor")?;
    let rows = st.query_map([], |r| Ok(Point { lat: r.get(0)?, lon: r.get(1)? }))?;
    Ok(rows.collect::<Result<_, _>>()?)
}

/// 그날(또는 그 에피소드를) 커뮤니티에 올릴 때 받아 둔 날씨. 없으면 None (공유하려고 따로 받지 않는다).
fn saved_weather(conn: &Connection, day: &str, episode_id: Option<i64>) -> Result<Option<String>, CoreError> {
    if let Some(id) = episode_id {
        let w: Option<Option<String>> = conn
            .query_row("SELECT weather FROM community_course WHERE id = ?1", [format!("me-{id}")], |r| r.get(0))
            .optional()?;
        if let Some(Some(w)) = w {
            return Ok(Some(w));
        }
    }
    Ok(conn
        .query_row(
            "SELECT weather FROM community_course WHERE mine = 1 AND visited_day = ?1 AND weather IS NOT NULL
             ORDER BY created_ms LIMIT 1",
            params![day],
            |r| r.get(0),
        )
        .optional()?)
}

fn is_leisure(k: EpisodeKind) -> bool {
    matches!(k, EpisodeKind::Outing | EpisodeKind::Trip)
}

/// 에피소드 시간 안의 주행 (경계는 1분 여유).
fn in_span(t: &TripSummary, start_ms: i64, end_ms: i64) -> bool {
    const SLACK: i64 = 60_000;
    t.start_ms >= start_ms - SLACK && t.end_ms <= end_ms + SLACK
}

fn trip_points(t: &TripSummary) -> Vec<Point> {
    t.path.iter().map(|p| Point { lat: p.lat, lon: p.lon }).collect()
}

/// 머문 곳 → 들른 곳 (이름이 없으면 None).
fn stay_fact(s: &StaySummary) -> Option<StopFact> {
    let p = s.place.as_ref().filter(|p| !p.name.trim().is_empty())?;
    Some(StopFact {
        name: p.name.clone(),
        category: p.category.clone(),
        address: p.address.clone(),
        arrive_ms: s.arrive_ms,
        leave_ms: s.leave_ms,
        lat: s.center.lat,
        lon: s.center.lon,
        first_visit: false,
    })
}

fn stop_point(s: &StopFact) -> Point {
    Point { lat: s.lat, lon: s.lon }
}

#[cfg(test)]
mod tests;

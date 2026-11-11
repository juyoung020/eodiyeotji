//! "그날로 돌아가기": 지난 하루를 한 장면씩 다시 보는 화면의 재료.
//!
//! 장면 = 그날 들른 곳 하나 (사진 · 도착 시각 · 머문 시간). 장면 사이에는 그곳까지 달린 길(구간)이 있고,
//! 작은 지도는 그 구간을 조금씩 그려 가며 차 표시를 옮긴다. 그리기 단계(GeoJSON)는 모두 여기서 만들고
//! Kotlin은 시간에 맞춰 MapLibre 소스에 넣기만 한다.
//!
//! # 장면 고르기
//! - 이름이 있는 머문 곳만, 도착 순서대로.
//! - 하루 상세의 들른 곳과 같은 목록(`store::visit_indices`)에서 고른다: 그날 경로의 출발 · 도착 머문 곳(대개 집)은
//!   뺀다 — 밤새 머문 시간이 섞여 "14시간 머묾"이 된다. 끝 화면 "들른 곳 N곳"도 하루 상세와 같은 수다.
//!   그렇게 빼서 남는 곳이 없으면 이름 있는 곳을 그대로 쓴다.
//! - 장면 사진(`show_photo`): 집 · 회사(기억 모듈이 정한 곳, 250m 안)이거나 분류가 회사 · 건물 계열이면 사진 없이 지도만 —
//!   회사 건물에 가까운 카페 음료 사진이 뜨는 식으로 맥락이 어긋나기 쉽다.
//! - 끝 화면 "그 코스로 안내"(`can_guide`)는 대표 에피소드가 있고 출퇴근이 아닐 때만.
//!
//! # 구간 (장면 i)
//! - 앞 장면 도착 뒤(첫 장면은 그날 처음부터) ~ 이 장면 도착 전에 출발한 주행들의 경로를 이어 붙인 것.
//! - [LEG_FRAMES]단계로 나눠, 단계 k는 거리 기준 앞쪽 k/[LEG_FRAMES]만큼의 선 + 그 끝의 차 표시.
//! - 마지막 장면 뒤의 주행(집으로 오는 길)은 끝 화면 지도(`final_geojson`)에만 그린다.
//!
//! # GeoJSON (FeatureCollection)
//! - 지나온 길: `LineString`, `{"kind":"route"}` (`done_geojson`, `final_geojson`)
//! - 지금 그리는 구간: `LineString`, `{"kind":"leg"}` + 차 `Point`, `{"kind":"car"}` (`leg_frames`)
//! - 들른 곳: `Point`, `{"kind":"stop","order":n,"now":true|false}` — `now`는 지금 장면의 곳.

use std::fmt::Write;

use rusqlite::Connection;

use crate::agent::dates::civil_from_days;
use crate::community::drive::{flight, FlightFrame};
use crate::geo::{haversine_m, Point};
use crate::mapdata::{coord, distance_text, duration_text, trip_points, Bounds, MapBounds};
use crate::memory::{current_anchors, EpisodeKind, StopFact, ANCHOR_RADIUS_M};
use crate::recap::{ep_rank, episodes_between, season_of, years_between};
use crate::store::{parse_day, visit_indices, CoreError, DayDetail, EodiStore, DAY_MS};

/// 이보다 짧게 달린 날의 끝 문장은 들른 곳 중심이다 (m).
pub const SHORT_DAY_M: f64 = 10_000.0;

/// 구간 하나를 몇 단계로 나눠 그리는지.
pub const LEG_FRAMES: usize = 12;

/// 구간 비행 장면 수 (2~3초 비행을 Kotlin이 장면 사이를 섞어 그린다).
pub const LEG_FLIGHT_FRAMES: usize = 90;

/// 장면 하나 (들른 곳 하나).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ReplayScene {
    pub stop: StopFact,
    /// 순서 (1부터)
    pub order: u32,
    /// 도착 시각 "09:51"
    pub time_text: String,
    /// "1시간 52분 머묾" (아직 머무는 곳 · 1분 미만은 빈 글)
    pub stay_text: String,
    /// 이 장면으로 오는 구간 전까지 지나온 길 + 들른 곳 (지금 곳 포함). 구간은 [leg_frames]가 그린다
    pub done_geojson: String,
    /// 이 장면으로 오는 구간을 [LEG_FRAMES]단계로 (구간이 없으면 빈 목록)
    pub leg_frames: Vec<String>,
    /// 이 구간을 따라가는 3D 카메라 비행 ([LEG_FLIGHT_FRAMES]장면, 앞을 보는 방향 · 구간 길이에 맞춘 높이). 구간이 없으면 빈 목록
    pub leg_flight: Vec<FlightFrame>,
    /// 도착 시각의 하늘 (지도 색 · 하늘 그라데이션을 고른다)
    pub sky: SkyPhase,
    /// 이 곳의 사진을 띄울지 (집 · 회사 · 회사 건물 분류면 false → 지도만)
    pub show_photo: bool,
}

/// 하늘 색 때 (현지 시각 기준). 새벽 05:00~, 낮 07:00~, 노을 16:30~, 푸른 시간 18:30~, 밤 19:30~05:00.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SkyPhase {
    Dawn,
    Day,
    Golden,
    Blue,
    Night,
}

/// 현지 시각 → 하늘 때.
pub(crate) fn sky_phase(t_ms: i64, tz_minutes: i32) -> SkyPhase {
    let min = (t_ms + tz_minutes as i64 * 60_000).rem_euclid(DAY_MS) / 60_000;
    match min {
        300..=419 => SkyPhase::Dawn,
        420..=989 => SkyPhase::Day,
        990..=1109 => SkyPhase::Golden,
        1110..=1169 => SkyPhase::Blue,
        _ => SkyPhase::Night,
    }
}

/// 하루 다시 보기.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DayReplay {
    pub day: String,
    /// "2025년 11월 8일 토요일"
    pub date_text: String,
    /// 몇 년 전 (1년이 안 됐으면 0)
    pub years_ago: u32,
    /// 도장 글: "1년 전 오늘" (같은 월 · 일) · "1년 전" · 1년이 안 됐으면 빈 글
    pub stamp: String,
    /// 하루 제목 (AI, 없으면 규칙 이름, 그것도 없으면 날짜)
    pub title: String,
    /// "가을 · 강원 강릉시" (지역을 모르면 계절만)
    pub subtitle: String,
    pub scenes: Vec<ReplayScene>,
    /// 끝 화면 지도: 그날 경로 전체 + 들른 곳
    pub final_geojson: String,
    /// 작은 지도 맞춤 범위 (그릴 것이 없으면 None)
    pub bounds: Option<MapBounds>,
    /// "그날, 우리는 384km를 달렸어요"
    pub summary_line: String,
    /// "들른 곳 5곳 · 운전 4시간 12분"
    pub stats_line: String,
    pub distance_m: f64,
    /// 코스 안내에 쓸 그날 대표 에피소드 (없으면 None)
    pub episode_id: Option<i64>,
    /// 끝 화면에 "그 코스로 안내"를 보일지: 대표 에피소드가 있고 출퇴근길이 아니다
    pub can_guide: bool,
    /// 하루 전체 장면(시작 · 끝 화면)의 하늘 = 마지막 들른 곳의 때 (들른 곳이 없으면 노을)
    pub sky: SkyPhase,
}

#[uniffi::export]
impl EodiStore {
    /// 그날 다시 보기 (모듈 설명). `now_ms` = 지금 (UTC ms, "N년 전" 계산용).
    pub fn day_replay(&self, day: String, now_ms: i64) -> Result<DayReplay, CoreError> {
        let detail = self.day_detail(day)?;
        let d = parse_day(&detail.day).unwrap_or(0);
        let conn = self.snapshot()?;
        let ep = best_episode(&conn, d)?;
        let a = current_anchors(&conn)?;
        let anchors: Vec<Point> = [a.home, a.office].into_iter().flatten().collect();
        let today = self.local_day(now_ms);
        Ok(build_replay(&detail, self.tz_minutes(), today, ep, &anchors))
    }
}

/// 그날 대표 에피소드: (id, 규칙 이름, 지역, 출퇴근길인가).
pub(crate) struct EpisodeInfo {
    pub id: i64,
    pub label: String,
    pub region: String,
    pub commute: bool,
}

fn best_episode(conn: &Connection, d: i64) -> Result<Option<EpisodeInfo>, CoreError> {
    let eps = episodes_between(conn, d, d)?;
    Ok(eps
        .iter()
        .max_by_key(|e| ep_rank(e))
        .map(|e| EpisodeInfo {
            id: e.facts.id,
            label: e.facts.rule_label.clone(),
            region: e.facts.region.clone(),
            commute: matches!(e.facts.kind, EpisodeKind::CommuteToWork | EpisodeKind::CommuteHome),
        }))
}

/// 사진이 맥락과 어긋나기 쉬운 분류 낱말 (회사 · 건물 계열). 이름이 정확히 "집" · "회사"인 곳(내비 목적지 이름)도 같다.
const NO_PHOTO_CATEGORY: [&str; 7] = ["회사", "기업", "빌딩", "오피스", "사무", "연구소", "공장"];

/// 장면에 사진을 띄울지: 집 · 회사(250m 안)도, 회사 · 건물 분류도 아니다.
pub(crate) fn photo_fits(name: &str, category: &str, at: Point, anchors: &[Point]) -> bool {
    let name = name.trim();
    if name == "집" || name == "회사" {
        return false;
    }
    if NO_PHOTO_CATEGORY.iter().any(|w| category.contains(w)) {
        return false;
    }
    anchors.iter().all(|&a| haversine_m(a, at) > ANCHOR_RADIUS_M)
}

pub(crate) fn build_replay(d: &DayDetail, tz_minutes: i32, today: i64, ep: Option<EpisodeInfo>, anchors: &[Point]) -> DayReplay {
    let day = parse_day(&d.day).unwrap_or(0);
    let (y, m, dd) = civil_from_days(day);
    let (_, tm, td) = civil_from_days(today);
    let years_ago = years_between(day, today);
    let stamp = match years_ago {
        0 => String::new(),
        n if (tm, td) == (m, dd) => format!("{n}년 전 오늘"),
        n => format!("{n}년 전"),
    };
    let date_text = format!("{y}년 {m}월 {dd}일 {}요일", WEEKDAYS[(day + 3).rem_euclid(7) as usize]);
    let title = d
        .summary
        .title
        .clone()
        .filter(|t| !t.trim().is_empty())
        .or_else(|| ep.as_ref().map(|e| e.label.clone()).filter(|l| !l.is_empty()))
        .unwrap_or_else(|| date_text.clone());
    let season = season_of(m);
    let subtitle = match ep.as_ref().map(|e| e.region.as_str()).filter(|r| !r.is_empty()) {
        Some(r) => format!("{season} · {r}"),
        None => season.to_owned(),
    };

    // 장면이 될 머문 곳
    let named: Vec<usize> = (0..d.stays.len()).filter(|&i| d.stays[i].place.as_ref().is_some_and(|p| !p.name.trim().is_empty())).collect();
    // 하루 상세의 들른 곳과 같은 목록 (`store::visit_indices`)
    let visits = visit_indices(&d.trips, &d.stays);
    let middle: Vec<usize> = named.iter().copied().filter(|i| visits.contains(i)).collect();
    let picked = if middle.is_empty() { named } else { middle };

    let mut bounds = Bounds::default();
    for t in &d.trips {
        for (lat, lon) in trip_points(t) {
            bounds.add(lat, lon);
        }
    }
    for &i in &picked {
        bounds.add(d.stays[i].center.lat, d.stays[i].center.lon);
    }

    let stops: Vec<(f64, f64)> = picked.iter().map(|&i| (d.stays[i].center.lat, d.stays[i].center.lon)).collect();
    let mut done: Vec<Vec<(f64, f64)>> = Vec::new();
    let mut prev_cut = i64::MIN;
    let mut scenes = Vec::with_capacity(picked.len());
    for (k, &i) in picked.iter().enumerate() {
        let s = &d.stays[i];
        let leg: Vec<(f64, f64)> =
            d.trips.iter().filter(|t| t.start_ms >= prev_cut && t.start_ms < s.arrive_ms).flat_map(trip_points).collect();
        prev_cut = s.arrive_ms;
        let leg_frames = if leg.len() >= 2 {
            (1..=LEG_FRAMES).map(|f| leg_frame(&leg, f as f64 / LEG_FRAMES as f64)).collect()
        } else {
            Vec::new()
        };
        let leg_flight = leg_flight(&leg);
        let done_geojson = collection(&done, &stops[..=k], Some(k));
        if leg.len() >= 2 {
            done.push(leg);
        }
        let p = s.place.as_ref().expect("이름 있는 곳만 골랐다");
        let stay_text = if s.leave_ms > s.arrive_ms { duration_text(s.leave_ms - s.arrive_ms).map(|t| format!("{t} 머묾")) } else { None };
        scenes.push(ReplayScene {
            stop: StopFact {
                name: p.name.clone(),
                category: p.category.clone(),
                address: p.address.clone(),
                arrive_ms: s.arrive_ms,
                leave_ms: s.leave_ms,
                lat: s.center.lat,
                lon: s.center.lon,
                first_visit: false,
            },
            order: (k + 1) as u32,
            time_text: clock_text(s.arrive_ms, tz_minutes),
            stay_text: stay_text.unwrap_or_default(),
            done_geojson,
            leg_frames,
            leg_flight,
            sky: sky_phase(s.arrive_ms, tz_minutes),
            show_photo: photo_fits(&p.name, &p.category, Point { lat: s.center.lat, lon: s.center.lon }, anchors),
        });
    }
    let all: Vec<Vec<(f64, f64)>> = d.trips.iter().map(trip_points).filter(|p| p.len() >= 2).collect();
    let final_geojson = collection(&all, &stops, None);

    let distance_m = d.summary.distance_m;
    // 짧게 달린 날은 거리 대신 들른 곳이 주인공이다 ("4.5km를 달렸어요 · 운전 2분"은 자랑할 거리가 아니다)
    let short = distance_m < SHORT_DAY_M && !scenes.is_empty();
    // 들른 곳 수는 하루 상세와 같은 값 (`store::visit_indices`: 하루 출발 · 도착 머문 곳 뺀 곳, 이름 없는 곳 포함)
    let visits = visits.len();
    let mut stats = Vec::new();
    if visits > 0 {
        stats.push(format!("들른 곳 {visits}곳"));
    }
    if short {
        stats.push(distance_text(distance_m));
    } else if let Some(t) = duration_text(d.summary.drive_millis) {
        stats.push(format!("운전 {t}"));
    }
    let summary_line = match scenes.as_slice() {
        [one] if short => format!("그날, {}에 들렀어요", one.stop.name),
        [first, ..] if short => format!("그날, {}부터 {}곳에 들렀어요", first.stop.name, visits.max(scenes.len())),
        _ => format!("그날, 우리는 {}를 달렸어요", distance_text(distance_m)),
    };
    let scenes_sky = scenes.last().map(|s| s.sky).unwrap_or(SkyPhase::Golden);
    DayReplay {
        day: d.day.clone(),
        date_text,
        years_ago,
        stamp,
        title,
        subtitle,
        scenes,
        final_geojson,
        bounds: bounds.get(),
        summary_line,
        stats_line: stats.join(" · "),
        distance_m,
        can_guide: ep.as_ref().is_some_and(|e| !e.commute),
        episode_id: ep.map(|e| e.id),
        sky: scenes_sky,
    }
}

const WEEKDAYS: [&str; 7] = ["월", "화", "수", "목", "금", "토", "일"];

/// 현지 시각 "09:51".
fn clock_text(t_ms: i64, tz_minutes: i32) -> String {
    let min = (t_ms + tz_minutes as i64 * 60_000).rem_euclid(DAY_MS) / 60_000;
    format!("{:02}:{:02}", min / 60, min % 60)
}

/// 구간 앞쪽 `frac`(0~1, 거리 기준)만큼의 점. 끝점은 선분 위에서 잘라 넣는다.
pub(crate) fn leg_prefix(leg: &[(f64, f64)], frac: f64) -> Vec<(f64, f64)> {
    let seg: Vec<f64> = leg.windows(2).map(|w| haversine_m(Point { lat: w[0].0, lon: w[0].1 }, Point { lat: w[1].0, lon: w[1].1 })).collect();
    let total: f64 = seg.iter().sum();
    if frac >= 1.0 || total <= 0.0 {
        return leg.to_vec();
    }
    let mut left = total * frac.max(0.0);
    let mut out = vec![leg[0]];
    for (w, len) in leg.windows(2).zip(&seg) {
        if left <= *len {
            let f = if *len > 0.0 { left / len } else { 0.0 };
            out.push((w[0].0 + (w[1].0 - w[0].0) * f, w[0].1 + (w[1].1 - w[0].1) * f));
            return out;
        }
        left -= len;
        out.push(w[1]);
    }
    out
}

/// 구간 비행: 길을 따라 앞을 보는 카메라. 2~3초 안에 지나가야 하므로 높이는 구간 길이로 정한다
/// (3km면 건물이 보이는 15, 길이가 두 배마다 한 단계 높이, 10.5~15).
pub(crate) fn leg_flight(leg: &[(f64, f64)]) -> Vec<FlightFrame> {
    if leg.len() < 2 {
        return Vec::new();
    }
    let pts: Vec<Point> = leg.iter().map(|&(lat, lon)| Point { lat, lon }).collect();
    let total: f64 = pts.windows(2).map(|w| haversine_m(w[0], w[1])).sum();
    let zoom = leg_flight_zoom(total);
    flight(&pts, LEG_FLIGHT_FRAMES).into_iter().map(|f| FlightFrame { zoom, ..f }).collect()
}

pub(crate) fn leg_flight_zoom(length_m: f64) -> f64 {
    (15.0 - (length_m.max(1.0) / 3_000.0).log2()).clamp(10.5, 15.0)
}

/// 구간 한 단계: 앞쪽 선 + 끝의 차 (지나온 길 · 들른 곳은 `done_geojson`이 따로 그린다).
fn leg_frame(leg: &[(f64, f64)], frac: f64) -> String {
    let part = leg_prefix(leg, frac);
    let mut out = String::from(r#"{"type":"FeatureCollection","features":["#);
    line(&mut out, &part, "leg");
    let car = *part.last().expect("구간은 두 점 이상");
    out.push_str(r#",{"type":"Feature","geometry":{"type":"Point","coordinates":"#);
    coord(&mut out, car.0, car.1);
    out.push_str(r#"},"properties":{"kind":"car"}}]}"#);
    out
}

fn collection(lines: &[Vec<(f64, f64)>], stops: &[(f64, f64)], now: Option<usize>) -> String {
    let mut out = String::from(r#"{"type":"FeatureCollection","features":["#);
    let mut first = true;
    for l in lines {
        if !first {
            out.push(',');
        }
        first = false;
        line(&mut out, l, "route");
    }
    for (i, &(lat, lon)) in stops.iter().enumerate() {
        if !first {
            out.push(',');
        }
        first = false;
        out.push_str(r#"{"type":"Feature","geometry":{"type":"Point","coordinates":"#);
        coord(&mut out, lat, lon);
        let _ = write!(out, r#"}},"properties":{{"kind":"stop","order":{},"now":{}}}}}"#, i + 1, now == Some(i));
    }
    out.push_str("]}");
    out
}

fn line(out: &mut String, pts: &[(f64, f64)], kind: &str) {
    out.push_str(r#"{"type":"Feature","geometry":{"type":"LineString","coordinates":["#);
    for (i, &(lat, lon)) in pts.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        coord(out, lat, lon);
    }
    let _ = write!(out, r#"]}},"properties":{{"kind":"{kind}"}}}}"#);
}

#[cfg(test)]
mod tests;

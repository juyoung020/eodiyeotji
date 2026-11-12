//! 내 사진(기기 갤러리)을 하루의 들른 곳에 맞추기, 그리고 올린 글에 붙인 사진 기록.
//!
//! 사진을 읽는 일(MediaStore · EXIF)은 Kotlin(`gallery/GalleryReader.kt`)이 하고, 여기서는
//! ① 그날 사진을 찾을 시간 범위를 정하고([day_window]) ② 받은 사진 묶음(한 번에)을 머문 곳에 나눠 주고([match_stays])
//! ③ 집·회사 근처 사진을 올리기 후보에서 빼는 표시를 붙인다(`shareable`).
//!
//! # 맞추는 기준
//! - 찍은 시각이 머문 시간 앞뒤 [MARGIN_MS] 안이어야 한다. 아직 머무는 곳(떠난 시각 = 도착 시각)은 도착 뒤 [OPEN_STAY_MS]까지.
//! - 사진에 위치(EXIF GPS)가 있으면 그 곳에서 [MATCH_RADIUS_M] 안이어야 한다. 위치 없는 사진은 시각만으로 맞춘다.
//! - 두 곳에 다 걸치면(앞뒤 여유가 겹칠 때) 머문 시간 안에 든 곳 → 시간 차가 작은 곳 → 가까운 곳 순으로 한 곳에만 준다.
//! - 곳마다 순서: 위치까지 맞은 사진이 먼저(대표), 그다음 최신순. [MAX_PER_STAY]장까지 담고 전체 수는 `total`.
//!
//! # 저장하지 않는 것
//! 매칭 결과는 DB에 두지 않는다. 하루 사진은 수십~수백 장이라 맞추기가 1 ms도 안 걸리고, 갤러리가 진짜 원본이라
//! (지우면 바로 사라져야 한다) 사진 목록 사본을 앱 DB에 남기지 않는 쪽이 개인정보에도 맞다.
//!
//! # 올린 글의 사진 (DB 구조 버전 18)
//! 사용자가 올리기 창에서 직접 고른 사진만 Kotlin이 앱 안 폴더에 줄여 복사(EXIF 없이)하고, 그 경로를 여기 적는다.
//! - 여행 코스 후기: `community_course.stop_photos` = `[{"t": 도착 시각, "p": 경로}, …]` → [CoursePost::stop_photos]
//! - 드라이브 코스: `drive_route.photos` = `[경로, …]` → `DriveRoutePost::photos`

use std::collections::HashMap;

use rusqlite::{params, Connection};
use serde_json::{json, Value};

use crate::community::{privacy_anchors, valid_point, CoursePost, PRIVACY_RADIUS_M};
use crate::ffi::GeoPoint;
use crate::geo::{haversine_m, Point};
use crate::store::{CoreError, EodiStore, StaySummary};

/// 머문 시간 앞뒤로 더 봐 주는 여유 (도착 직전 · 떠나기 직후에 찍은 사진).
pub const MARGIN_MS: i64 = 10 * 60_000;
/// 사진 위치가 머문 곳에서 이 안이어야 그곳 사진이다 (m).
pub const MATCH_RADIUS_M: f64 = 300.0;
/// 아직 머무는 곳(하루 마지막 도착지)은 도착 뒤 이만큼까지 본다.
pub const OPEN_STAY_MS: i64 = 3 * 3_600_000;
/// 한 곳에 담는 사진 수 (카드 2~4장 + "+N", 올리기 창에서 고를 후보).
pub const MAX_PER_STAY: usize = 12;
/// 드라이브 코스 하나에 붙일 수 있는 사진 수.
pub const MAX_ROUTE_PHOTOS: usize = 4;

/// 갤러리 사진 한 장 (Kotlin이 MediaStore에서 읽은 것). [uri]는 `content://…`, [location]은 EXIF GPS (없으면 None).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GalleryPhoto {
    pub id: i64,
    pub uri: String,
    pub taken_ms: i64,
    pub location: Option<GeoPoint>,
}

/// 맞춘 사진. [shareable]이 false면 집·회사 근처라 올리기 후보에서 뺀다 (내 타임라인에는 보인다).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MatchedPhoto {
    pub photo: GalleryPhoto,
    pub shareable: bool,
}

/// 머문 곳 하나의 내 사진. [arrive_ms]로 머문 곳을 가리킨다 (타임라인 항목 키 `s<도착 ms>`와 같다).
/// [photos]는 대표 순 [MAX_PER_STAY]장까지, [total]은 맞은 전체 수 ("+N" 표시용).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StayPhotos {
    pub arrive_ms: i64,
    pub photos: Vec<MatchedPhoto>,
    pub total: u32,
}

/// 지도에 꽂을 내 사진 한 장: 찍은 곳(EXIF 위치, 없으면 맞춘 머문 곳 가운데). [stay_arrive_ms]는 맞춘 머문 곳(없으면 None, 길 위에서 찍은 사진).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MapPhotoSpot {
    pub id: i64,
    pub uri: String,
    pub taken_ms: i64,
    pub at: GeoPoint,
    pub stay_arrive_ms: Option<i64>,
}

/// 하루 지도에 꽂는 내 사진 최대 수 (넘으면 최신 사진을 뺀다 — 앞 시각 순으로 담는다).
pub const MAX_MAP_SPOTS: usize = 120;

/// 갤러리에서 읽을 시간 범위 (찍은 시각, 이 안의 사진만 Rust로 넘긴다). 머문 곳이 없으면 비어 있다(`from_ms > to_ms`).
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct GalleryWindow {
    pub from_ms: i64,
    pub to_ms: i64,
}

/// 올린 여행 코스 후기의 들른 곳 사진 하나 (도착 시각으로 곳을 가리킨다, [path]는 앱 안 사본 경로).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StopPhotoRef {
    pub arrive_ms: i64,
    pub path: String,
}

/// 머문 곳 하나의 맞추기 범위.
#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) struct StayWindow {
    pub arrive_ms: i64,
    /// 떠난 시각 (아직 머무는 곳은 도착 + [OPEN_STAY_MS]로 바꿔 넣는다)
    pub end_ms: i64,
    pub center: Point,
    /// 집·회사 근처 곳 (그곳 사진은 올리기 후보에서 뺀다)
    pub private: bool,
}

impl StayWindow {
    pub(crate) fn of(s: &StaySummary, anchors: &[Point]) -> Self {
        let center = Point::from(s.center);
        let end_ms = if s.leave_ms > s.arrive_ms { s.leave_ms } else { s.arrive_ms + OPEN_STAY_MS };
        StayWindow { arrive_ms: s.arrive_ms, end_ms, center, private: near_any(center, anchors) }
    }

    /// 머문 시간 밖으로 벗어난 정도 (안이면 0, 여유 밖이면 None).
    fn outside_ms(&self, t: i64) -> Option<i64> {
        let off = if t < self.arrive_ms {
            self.arrive_ms - t
        } else if t > self.end_ms {
            t - self.end_ms
        } else {
            0
        };
        (off <= MARGIN_MS).then_some(off)
    }
}

/// 그날 사진을 읽을 범위: 첫 도착 − 여유 ~ 마지막 떠남(또는 머무는 중) + 여유.
pub(crate) fn day_window(stays: &[StayWindow]) -> GalleryWindow {
    let from = stays.iter().map(|s| s.arrive_ms).min();
    let to = stays.iter().map(|s| s.end_ms).max();
    match (from, to) {
        (Some(a), Some(b)) => GalleryWindow { from_ms: a - MARGIN_MS, to_ms: b + MARGIN_MS },
        _ => GalleryWindow { from_ms: 1, to_ms: 0 },
    }
}

/// 사진을 머문 곳에 나눠 준다 (모듈 설명의 기준). 사진이 하나도 없는 곳은 결과에 없다. 결과는 도착 시각순.
pub(crate) fn match_stays(stays: &[StayWindow], photos: &[GalleryPhoto], anchors: &[Point]) -> Vec<StayPhotos> {
    // 머문 곳 번호 → (위치까지 맞았나, 사진)
    let mut got: Vec<Vec<(bool, &GalleryPhoto)>> = vec![Vec::new(); stays.len()];
    for p in photos {
        let at = photo_point(p);
        if let Some(i) = best_stay(stays, p, at) {
            got[i].push((at.is_some(), p));
        }
    }
    collect_stays(stays, got, anchors)
}

/// 사진 한 장이 들어갈 머문 곳 번호 (모듈 설명의 기준, 없으면 None).
fn best_stay(stays: &[StayWindow], p: &GalleryPhoto, at: Option<Point>) -> Option<usize> {
    stays
        .iter()
        .enumerate()
        .filter_map(|(i, s)| {
            let off = s.outside_ms(p.taken_ms)?;
            let dist = match at {
                Some(a) => {
                    let d = haversine_m(a, s.center);
                    if d > MATCH_RADIUS_M {
                        return None;
                    }
                    d
                }
                None => 0.0,
            };
            Some((i, off, dist))
        })
        .min_by(|a, b| a.1.cmp(&b.1).then(a.2.total_cmp(&b.2)))
        .map(|(i, _, _)| i)
}

fn collect_stays(stays: &[StayWindow], got: Vec<Vec<(bool, &GalleryPhoto)>>, anchors: &[Point]) -> Vec<StayPhotos> {
    let mut out: Vec<StayPhotos> = stays
        .iter()
        .zip(got)
        .filter(|(_, g)| !g.is_empty())
        .map(|(s, mut g)| {
            // 위치까지 맞은 사진이 대표 → 그다음 최신순 (같은 시각이면 id로 늘 같은 순서)
            g.sort_by(|a, b| b.0.cmp(&a.0).then(b.1.taken_ms.cmp(&a.1.taken_ms)).then(a.1.id.cmp(&b.1.id)));
            let total = g.len().min(u32::MAX as usize) as u32;
            let photos = g
                .into_iter()
                .take(MAX_PER_STAY)
                .map(|(_, p)| MatchedPhoto { photo: p.clone(), shareable: !s.private && !photo_near(p, anchors) })
                .collect();
            StayPhotos { arrive_ms: s.arrive_ms, photos, total }
        })
        .collect();
    out.sort_by_key(|s| s.arrive_ms);
    out
}

/// 그날 사진을 지도에 꽂을 자리로: 위치가 있는 사진은 찍은 곳에(길 위에서 찍었어도), 위치가 없으면 맞춘 머문 곳 가운데에.
/// 둘 다 없는 사진(위치 없음 · 머문 곳과 시각도 안 맞음)은 빠진다. 찍은 시각순, [MAX_MAP_SPOTS]장까지.
/// 하루 범위([day_window]) 밖 사진도 빠진다 (Kotlin이 넓게 읽어 넘겨도 그날 것만).
pub(crate) fn map_spots(stays: &[StayWindow], photos: &[GalleryPhoto]) -> Vec<MapPhotoSpot> {
    let w = day_window(stays);
    let mut v: Vec<&GalleryPhoto> = photos.iter().filter(|p| p.taken_ms >= w.from_ms && p.taken_ms <= w.to_ms).collect();
    v.sort_by(|a, b| a.taken_ms.cmp(&b.taken_ms).then(a.id.cmp(&b.id)));
    v.into_iter()
        .filter_map(|p| {
            let at = photo_point(p);
            let stay = best_stay(stays, p, at);
            let point = at.or_else(|| stay.map(|i| stays[i].center))?;
            Some(MapPhotoSpot {
                id: p.id,
                uri: p.uri.clone(),
                taken_ms: p.taken_ms,
                at: GeoPoint { lat: point.lat, lon: point.lon },
                stay_arrive_ms: stay.map(|i| stays[i].arrive_ms),
            })
        })
        .take(MAX_MAP_SPOTS)
        .collect()
}

/// 주행 하나([from_ms]~[to_ms] + 여유) 동안 찍은 사진 (드라이브 코스에 붙일 후보). 최신순, [MAX_PER_STAY]장까지.
pub(crate) fn match_window(from_ms: i64, to_ms: i64, photos: &[GalleryPhoto], anchors: &[Point]) -> Vec<MatchedPhoto> {
    let mut v: Vec<&GalleryPhoto> =
        photos.iter().filter(|p| p.taken_ms >= from_ms - MARGIN_MS && p.taken_ms <= to_ms + MARGIN_MS).collect();
    v.sort_by(|a, b| b.taken_ms.cmp(&a.taken_ms).then(a.id.cmp(&b.id)));
    v.into_iter()
        .take(MAX_PER_STAY)
        .map(|p| MatchedPhoto { photo: p.clone(), shareable: !photo_near(p, anchors) })
        .collect()
}

fn photo_point(p: &GalleryPhoto) -> Option<Point> {
    p.location.filter(|g| valid_point(g.lat, g.lon)).map(Point::from)
}

fn photo_near(p: &GalleryPhoto, anchors: &[Point]) -> bool {
    photo_point(p).is_some_and(|a| near_any(a, anchors))
}

fn near_any(p: Point, anchors: &[Point]) -> bool {
    anchors.iter().any(|&a| haversine_m(a, p) <= PRIVACY_RADIUS_M)
}

// ---- 저장소에서 부르는 것 (Kotlin 공개는 ffi.rs) ----

/// 그날 머문 곳의 맞추기 범위 (숨긴 기록은 day_detail에서 이미 빠진다).
fn stay_windows(store: &EodiStore, day: String) -> Result<(Vec<StayWindow>, Vec<Point>), CoreError> {
    let detail = store.day_detail(day)?;
    let anchors = privacy_anchors(&*store.read()?)?;
    Ok((detail.stays.iter().map(|s| StayWindow::of(s, &anchors)).collect(), anchors))
}

pub(crate) fn window_for_day(store: &EodiStore, day: String) -> Result<GalleryWindow, CoreError> {
    Ok(day_window(&stay_windows(store, day)?.0))
}

pub(crate) fn photos_for_day(store: &EodiStore, day: String, photos: &[GalleryPhoto]) -> Result<Vec<StayPhotos>, CoreError> {
    let (stays, anchors) = stay_windows(store, day)?;
    Ok(match_stays(&stays, photos, &anchors))
}

pub(crate) fn map_spots_for_day(store: &EodiStore, day: String, photos: &[GalleryPhoto]) -> Result<Vec<MapPhotoSpot>, CoreError> {
    Ok(map_spots(&stay_windows(store, day)?.0, photos))
}

pub(crate) fn photos_for_window(store: &EodiStore, from_ms: i64, to_ms: i64, photos: &[GalleryPhoto]) -> Result<Vec<MatchedPhoto>, CoreError> {
    let anchors = privacy_anchors(&*store.read()?)?;
    Ok(match_window(from_ms, to_ms, photos, &anchors))
}

/// DB 구조 버전 18에서 더하는 칸. 이미 있는 칸은 건너뛴다 (테스트가 버전 숫자만 되돌려도 다시 올릴 수 있게).
const V18_COLUMNS: &[(&str, &str)] = &[("community_course", "stop_photos"), ("drive_route", "photos")];

pub(crate) fn migrate_v18(conn: &Connection) -> rusqlite::Result<()> {
    for (table, col) in V18_COLUMNS {
        let has: i64 =
            conn.query_row(&format!("SELECT count(*) FROM pragma_table_info('{table}') WHERE name = ?1"), [col], |r| r.get(0))?;
        if has == 0 {
            conn.execute_batch(&format!("ALTER TABLE {table} ADD COLUMN {col} TEXT NOT NULL DEFAULT '[]';"))?;
        }
    }
    Ok(())
}

/// 내가 올린 여행 코스 후기의 들른 곳 사진을 통째로 바꾼다 (빈 목록 = 모두 뗌). 글에 없는 곳·빈 경로는 버린다.
pub(crate) fn set_course_stop_photos(store: &EodiStore, post_id: &str, photos: Vec<StopPhotoRef>) -> Result<CoursePost, CoreError> {
    let conn = store.lock_community()?;
    let post = crate::community::load_courses(&conn, Some(post_id))?
        .pop()
        .filter(|p| p.mine)
        .ok_or_else(|| CoreError::InvalidInput(format!("내 코스 아님: {post_id}")))?;
    let kept: Vec<Value> = photos
        .into_iter()
        .filter(|p| !p.path.trim().is_empty() && post.stops.iter().any(|s| s.arrive_ms == p.arrive_ms))
        .map(|p| json!({ "t": p.arrive_ms, "p": p.path }))
        .collect();
    conn.execute("UPDATE community_course SET stop_photos = ?2 WHERE id = ?1", params![post_id, Value::Array(kept).to_string()])?;
    crate::community::load_courses(&conn, Some(post_id))?.pop().ok_or_else(|| CoreError::InvalidInput(post_id.to_string()))
}

/// 내가 올린 드라이브 코스의 사진을 통째로 바꾼다 ([MAX_ROUTE_PHOTOS]장까지, 빈 경로는 버린다).
pub(crate) fn set_route_photos(store: &EodiStore, id: &str, paths: Vec<String>) -> Result<(), CoreError> {
    let conn = store.lock_community()?;
    let kept: Vec<Value> = paths.into_iter().filter(|p| !p.trim().is_empty()).take(MAX_ROUTE_PHOTOS).map(Value::String).collect();
    let n = conn.execute("UPDATE drive_route SET photos = ?2 WHERE id = ?1 AND mine = 1", params![id, Value::Array(kept).to_string()])?;
    if n == 0 {
        return Err(CoreError::InvalidInput(format!("내 드라이브 코스 아님: {id}")));
    }
    Ok(())
}

/// `community_course.stop_photos`를 들른 곳 순서에 맞춘 경로 목록으로 (사진 없는 곳은 빈 문자열).
pub(crate) fn decode_stop_photos(s: &str, stops: &[crate::memory::StopFact]) -> Vec<String> {
    let map: HashMap<i64, String> = serde_json::from_str::<Value>(s)
        .ok()
        .and_then(|v| v.as_array().cloned())
        .unwrap_or_default()
        .iter()
        .filter_map(|v| Some((v["t"].as_i64()?, v["p"].as_str()?.to_string())))
        .collect();
    if map.is_empty() {
        return vec![String::new(); stops.len()];
    }
    stops.iter().map(|s| map.get(&s.arrive_ms).cloned().unwrap_or_default()).collect()
}

/// `drive_route.photos` → 경로 목록.
pub(crate) fn decode_paths(s: &str) -> Vec<String> {
    serde_json::from_str::<Vec<String>>(s).unwrap_or_default()
}

#[cfg(test)]
mod tests;

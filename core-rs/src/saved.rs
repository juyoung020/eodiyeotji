//! 장소 저장(♥) · 자주 가는 곳.
//!
//! 역할 분담: Rust(여기)가 장소 찾기 · 저장 · 순위를 맡고, Kotlin은 카드 · 하트 · "여기로 안내"만 그린다.
//!
//! # 장소 고르기 ([PlaceSpot])
//! 화면의 한 줄에서 장소를 고를 때: 주행 출발점(`start_ms`) · 주행 도착점(`end_ms`) · 머문 곳(`arrive_ms`)은
//! 그 지점이 이어진 장소(`places` 모듈의 잇기)를 그대로 쓴다. 이어진 장소가 없거나 좌표만 있으면(검색 결과 · 지도)
//! 그 좌표에서 100m(`places::PLACE_RADIUS_M`) 안의 가장 가까운 장소다. 그런 장소가 없으면 None.
//!
//! # 장소 카드 ([PlaceCard])
//! 이름(모르면 None), 저장 여부, 다녀온 날 수(그 장소에 머문 날, 숨긴 기록 제외), 마지막으로 간 날,
//! 집 · 회사인지(지금 쓰는 집 · 회사에서 250m 안).
//!
//! # 저장한 곳 ([EodiStore::saved_places])
//! 저장한 순서의 역순(최근 저장이 먼저). 기억 에이전트가 나중에 같은 함수를 쓴다.
//!
//! # 자주 가는 곳 ([EodiStore::frequent_places])
//! 이름 있는 장소 중 2일 이상 머문 곳, 집 · 회사(250m 안)는 뺀다. 점수가 높은 순:
//! `점수 = 머문 날 수 × (0.5 + 0.5 × 0.5^(마지막으로 간 날부터 지난 날 / 60)) + min(머문 시간 합(시간), 10) × 0.1`
//! → 자주 갈수록, 최근에 갔을수록(60일마다 가중치가 반씩 줄어 최소 0.5배), 오래 머물수록 위.
//! 점수가 같으면 마지막으로 간 날이 늦은 곳, 그다음 장소 번호 순.
//!
//! # 이름 후보 ([rank_place_candidates])
//! "이 장소 이름 고치기"에서 보여 줄 카카오 근처 후보를 거리순으로 고른다 (같은 이름은 가까운 하나만).

use rusqlite::{params, Connection, OptionalExtension};

use crate::edits::AnchorRole;
use crate::ffi::GeoPoint;
use crate::geo::{haversine_m, Point};
use crate::memory::{current_anchors, Anchors, ANCHOR_RADIUS_M};
use crate::places::{self, source_from, PlaceLabel, PLACE_RADIUS_M, REF_END, REF_START, REF_STAY};
use crate::store::{format_day, CoreError, EodiStore, DAY_MS};

/// 자주 가는 곳이 되려면 머문 날이 이 수 이상.
pub const FREQUENT_MIN_DAYS: i64 = 2;
/// 최근 가중치가 반으로 줄어드는 날 수.
const RECENCY_HALF_DAYS: f64 = 60.0;
/// 머문 시간 점수는 이 시간까지만 센다.
const DWELL_CAP_HOURS: f64 = 10.0;

/// 장소를 고른 자리의 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum SpotKind {
    /// 주행 출발점 (`t_ms` = `TripSummary.start_ms`)
    TripStart,
    /// 주행 도착점 (`t_ms` = `TripSummary.end_ms`)
    TripEnd,
    /// 머문 곳 (`t_ms` = `StaySummary.arrive_ms`)
    Stay,
    /// 좌표만 (검색 결과 · 지도). `t_ms`는 쓰지 않는다.
    Point,
}

/// 화면에서 고른 장소 자리 (모듈 설명).
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct PlaceSpot {
    pub kind: SpotKind,
    pub t_ms: i64,
    pub lat: f64,
    pub lon: f64,
}

/// 장소 카드 (저장한 곳 · 자주 가는 곳 · 메뉴).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PlaceCard {
    /// 장소 번호 (이름 고치기 · 저장에 넘긴다)
    pub id: i64,
    /// 이름 (아직 모르면 None)
    pub label: Option<PlaceLabel>,
    pub lat: f64,
    pub lon: f64,
    pub saved: bool,
    /// 이 장소에 머문 날 수 (숨긴 기록 제외)
    pub visit_days: u32,
    /// 마지막으로 머문 날 "YYYY-MM-DD" (없으면 None)
    pub last_visit_day: Option<String>,
    /// 지금 쓰는 집 · 회사면 그것
    pub role: Option<AnchorRole>,
}

/// 이름 후보 하나 (카카오 근처 검색 결과).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PlaceCandidate {
    pub label: PlaceLabel,
    /// 고른 자리에서의 거리(m)
    pub distance_m: f64,
}

/// 이름 후보를 거리순으로 최대 `limit`개 (같은 이름은 가까운 하나, 빈 이름은 뺀다).
#[uniffi::export]
pub fn rank_place_candidates(candidates: Vec<PlaceCandidate>, limit: u32) -> Vec<PlaceLabel> {
    let mut c: Vec<PlaceCandidate> = candidates.into_iter().filter(|c| !c.label.name.trim().is_empty()).collect();
    c.sort_by(|a, b| a.distance_m.total_cmp(&b.distance_m));
    let mut out: Vec<PlaceLabel> = Vec::new();
    for x in c {
        if out.len() >= limit as usize {
            break;
        }
        if !out.iter().any(|l| l.name == x.label.name) {
            out.push(x.label);
        }
    }
    out
}

#[uniffi::export]
impl EodiStore {
    /// 고른 자리의 장소 카드 (모듈 설명). 장소가 없으면 None.
    pub fn place_card(&self, spot: PlaceSpot) -> Result<Option<PlaceCard>, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let Some(id) = resolve_spot(&conn, spot)? else { return Ok(None) };
        let anchors = current_anchors(&conn)?;
        Ok(card(&conn, id, &anchors)?)
    }

    /// 장소를 저장(♥)하거나 저장을 푼다. 없는 장소면 `InvalidInput`.
    pub fn set_place_saved(&self, place_id: i64, saved: bool, now_ms: i64) -> Result<(), CoreError> {
        let conn = self.lock()?;
        if !places::place_exists(&conn, place_id)? {
            return Err(CoreError::InvalidInput(format!("place {place_id} not found")));
        }
        if saved {
            conn.execute(
                "INSERT INTO saved_place (place_id, saved_ms) VALUES (?1, ?2) ON CONFLICT (place_id) DO NOTHING",
                params![place_id, now_ms],
            )?;
        } else {
            conn.execute("DELETE FROM saved_place WHERE place_id = ?1", [place_id])?;
        }
        Ok(())
    }

    /// 저장한 곳 (최근에 저장한 것부터). 기억 에이전트도 쓴다.
    pub fn saved_places(&self) -> Result<Vec<PlaceCard>, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let ids: Vec<i64> = {
            let mut st = conn.prepare_cached("SELECT place_id FROM saved_place ORDER BY saved_ms DESC, place_id DESC")?;
            let rows = st.query_map([], |r| r.get(0))?;
            rows.collect::<Result<_, _>>()?
        };
        let anchors = current_anchors(&conn)?;
        let mut out = Vec::with_capacity(ids.len());
        for id in ids {
            out.extend(card(&conn, id, &anchors)?);
        }
        Ok(out)
    }

    /// 자주 가는 곳 (점수 높은 순, 규칙은 모듈 설명) 최대 `limit`개. `now_ms` = 지금 (최근 가중치 기준).
    pub fn frequent_places(&self, limit: u32, now_ms: i64) -> Result<Vec<PlaceCard>, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let anchors = current_anchors(&conn)?;
        let today = self.local_day(now_ms);
        let mut scored: Vec<(f64, i64, i64)> = Vec::new();
        {
            let mut st = conn.prepare_cached(
                "SELECT p.id, p.lat, p.lon, s.days, max(d.day), sum(d.total_ms)
                 FROM place_stat s JOIN place p ON p.id = s.place_id JOIN day_place_dwell d ON d.place_id = s.place_id
                 WHERE s.days >= ?1 AND p.name IS NOT NULL AND p.name != ''
                 GROUP BY p.id",
            )?;
            let mut q = st.query([FREQUENT_MIN_DAYS])?;
            while let Some(r) = q.next()? {
                let p = Point { lat: r.get(1)?, lon: r.get(2)? };
                if role_of(&anchors, p).is_some() {
                    continue;
                }
                let (id, days, last, dwell_ms): (i64, i64, i64, i64) = (r.get(0)?, r.get(3)?, r.get(4)?, r.get(5)?);
                scored.push((frequent_score(days, (today - last).max(0), dwell_ms), last, id));
            }
        }
        scored.sort_by(|a, b| b.0.total_cmp(&a.0).then(b.1.cmp(&a.1)).then(a.2.cmp(&b.2)));
        let mut out = Vec::new();
        for (_, _, id) in scored.into_iter().take(limit as usize) {
            out.extend(card(&conn, id, &anchors)?);
        }
        Ok(out)
    }

    /// 좌표마다 그 자리(100m 안 가장 가까운 장소)가 저장된 곳인지 (검색 결과의 ♥). 장소가 없으면 false.
    pub fn places_saved_at(&self, points: Vec<GeoPoint>) -> Result<Vec<bool>, CoreError> {
        let conn = self.lock()?;
        let mut out = Vec::with_capacity(points.len());
        for p in points {
            let saved = match places::nearest_place(&conn, Point::from(p), PLACE_RADIUS_M)? {
                Some(id) => is_saved(&conn, id)?,
                None => false,
            };
            out.push(saved);
        }
        Ok(out)
    }
}

/// 자주 가는 곳 점수 (모듈 설명).
pub(crate) fn frequent_score(days: i64, age_days: i64, dwell_ms: i64) -> f64 {
    let recency = 0.5 + 0.5 * 0.5f64.powf(age_days as f64 / RECENCY_HALF_DAYS);
    let dwell_h = (dwell_ms as f64 / 3_600_000.0).min(DWELL_CAP_HOURS);
    days as f64 * recency + dwell_h * 0.1
}

fn role_of(a: &Anchors, p: Point) -> Option<AnchorRole> {
    let near = |x: Option<Point>| x.is_some_and(|x| haversine_m(x, p) <= ANCHOR_RADIUS_M);
    if near(a.home) {
        Some(AnchorRole::Home)
    } else if near(a.office) {
        Some(AnchorRole::Office)
    } else {
        None
    }
}

pub(crate) fn is_saved(conn: &Connection, id: i64) -> rusqlite::Result<bool> {
    Ok(conn
        .prepare_cached("SELECT 1 FROM saved_place WHERE place_id = ?1")?
        .query_row([id], |_| Ok(()))
        .optional()?
        .is_some())
}

/// 고른 자리 → 장소 번호 (모듈 설명).
fn resolve_spot(conn: &Connection, spot: PlaceSpot) -> rusqlite::Result<Option<i64>> {
    let kinds: &[i64] = match spot.kind {
        SpotKind::TripStart => &[REF_START],
        SpotKind::TripEnd => &[REF_END],
        // 주행 사이 머문 곳은 앞 주행 도착점(REF_END)과, 주행 안 머문 곳은 REF_STAY와 같은 시각이다
        SpotKind::Stay => &[REF_STAY, REF_END],
        SpotKind::Point => &[],
    };
    for &k in kinds {
        // 주행은 3일보다 길지 않다 → 그 시각 3일 전 이후에 시작한 주행만 본다 (seg_start가 키 앞자리)
        let id: Option<i64> = conn
            .prepare_cached(
                "SELECT place_id FROM trip_place WHERE seg_start BETWEEN ?1 AND ?2 AND kind = ?3 AND t_ms = ?2 LIMIT 1",
            )?
            .query_row(params![spot.t_ms - 3 * DAY_MS, spot.t_ms, k], |r| r.get(0))
            .optional()?;
        if id.is_some() {
            return Ok(id);
        }
    }
    let ok = spot.lat.is_finite() && spot.lon.is_finite() && spot.lat.abs() <= 90.0 && spot.lon.abs() <= 180.0;
    if !ok {
        return Ok(None);
    }
    places::nearest_place(conn, Point { lat: spot.lat, lon: spot.lon }, PLACE_RADIUS_M)
}

/// 장소 카드 하나. 없는 장소면 None.
fn card(conn: &Connection, id: i64, anchors: &Anchors) -> rusqlite::Result<Option<PlaceCard>> {
    type Row = (f64, f64, Option<String>, String, String, Option<i64>);
    let row: Option<Row> = conn
        .prepare_cached("SELECT lat, lon, name, category, address, source FROM place WHERE id = ?1")?
        .query_row([id], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?)))
        .optional()?;
    let Some((lat, lon, name, category, address, source)) = row else { return Ok(None) };
    let label = match (name, source) {
        (Some(name), Some(src)) if !name.is_empty() => Some(PlaceLabel { name, category, address, source: source_from(src) }),
        _ => None,
    };
    let (days, last): (i64, Option<i64>) = conn
        .prepare_cached("SELECT count(*), max(day) FROM day_place_dwell WHERE place_id = ?1")?
        .query_row([id], |r| Ok((r.get(0)?, r.get(1)?)))?;
    Ok(Some(PlaceCard {
        id,
        label,
        lat,
        lon,
        saved: is_saved(conn, id)?,
        visit_days: days as u32,
        last_visit_day: last.map(format_day),
        role: role_of(anchors, Point { lat, lon }),
    }))
}

#[cfg(test)]
mod tests;

//! 장소 이름 (경유지·머문 곳·출발/도착을 좌표 대신 실제 장소 이름으로).
//!
//! 역할 분담:
//! - Rust(여기): 어떤 지점에 이름이 필요한지 고르고(가까운 지점은 한 곳으로 묶음), 받은 이름을 저장하고,
//!   하루 상세·지도 데이터에 붙인다. 이름 출처 우선순위도 여기서 정한다: 사용자(User) > 내비(Navi) > 카카오(Kakao) > OSM.
//! - Kotlin: `pending_places`로 받은 지점을 내비 정보·온라인 지도 API로 찾아 `set_place_name`으로 돌려준다.
//!   네트워크·API 호출만 하고, 고르기·묶기·우선순위 판단은 하지 않는다.
//!
//! # 장소(place)와 지점
//! 장소는 이름 하나를 갖는 자리다 (`PlaceQuery.id`). 주행·머문 곳 계산(`store`의 `flush`)이 끝날 때마다
//! 아래 지점을 장소에 잇는다 (`trip_place` 표). 주행이 다시 계산되면 잇기도 다시 하지만 장소와 이름은 남는다.
//! - 주행 출발점: 항상.
//! - 주행 도착점: 주행이 **끝난 것이 확실할 때만** — 뒤에 다음 주행이 있거나, 끝 신호(`DriveEvent::End`)가
//!   주행 끝 이후에 있을 때. 달리는 중인 주행의 끝은 계속 움직이므로 장소를 만들지 않는다.
//! - 주행 안 머문 곳(드라이브스루 등): 그 자리를 떠났거나(머문 곳이 주행 끝에서 끝나지 않음) 주행이 끝났을 때.
//! - 주행 사이 머문 곳·지금 머무는 중인 곳: 중심이 앞 주행 도착점과 같으므로 그 도착점의 장소를 같이 쓴다.
//!
//! # 묶기 (같은 주차장·가게는 한 번만 찾고, 날이 달라도 같은 이름)
//! - 새 지점은 [`PLACE_RADIUS_M`](100m) 안에서 가장 가까운 기존 장소에 붙는다. 없으면 그 지점을 중심으로 새 장소.
//!   장소 중심은 처음 만든 지점에서 움직이지 않는다.
//! - 주행 사이 머문 곳이 있으면(앞 주행 도착점에서 300m 안에서 10분 이상 뒤 출발, `trip::between_stay`)
//!   다음 주행 출발점은 거리와 상관없이 그 머문 곳(= 앞 주행 도착점)의 장소다.
//! - `PlaceQuery.is_stay`는 그 장소가 한 번이라도 머문 곳의 중심이었으면 true (가게·건물 이름을 우선 찾을 곳),
//!   출발·도착점으로만 쓰였으면 false.
//! - 지금 어느 주행에도 이어지지 않은 장소(주행이 다시 나뉘며 버려진 것)는 `pending_places`에 나오지 않는다.
//!
//! # 이름
//! - 출처 우선순위 User > Navi > Kakao > Osm. 더 낮은 출처의 이름은 높은 출처의 이름을 바꾸지 못하고, 같은 출처면 바꾼다.
//!   User는 사용자가 "이 장소 이름 고치기"로 직접 붙인 이름이다 (`edits` 모듈의 `rename_place`). 내비 힌트·카카오도 바꾸지 못한다.
//! - 이름은 장소에 붙으므로 그 장소에 이어진 모든 주행·머문 곳(다른 날 포함)에 같이 보인다.
//! - 붙는 곳: `day_detail`의 `TripSummary.start_place`·`end_place`, `StaySummary.place`, `day_map` GeoJSON의
//!   `name`·`from`·`to`(지도 글꼴로 그릴 수 있는 글자만, `mapdata::map_safe_name`), `DaySummary.stop_names`.
//! - 이름이 실제로 바뀌면(이름 저장·내비 힌트) 그 장소가 나오는 첫 날~마지막 날의 `day_summary` 줄을 다시 만든다
//!   (`stop_names`). 같은 이름을 다시 받으면 아무것도 다시 만들지 않는다.
//!
//! # 내비 힌트 ([`NaviPlaceHint`]) 짝짓기 규칙
//! 힌트 시각 t, 위치 h에 대해 "도착"(주행 도착점 또는 주행 안 머문 곳) 후보를 고른다.
//! - 주행 도착점: 그 주행이 t 이전에 출발했고(`start_ms <= t`), t가 도착 후 30분 이내(`t <= end_ms + 30분`),
//!   h에서 [`HINT_RADIUS_M`](300m) 안. 즉 주행 중에 받은 목적지는 그 주행의 끝에, 도착(주차) 뒤 30분 안에 받은 힌트는
//!   방금 끝난 주행의 끝에 붙는다.
//! - 주행 안 머문 곳: `Waypoint` 힌트만. 그 주행이 t 이전에 출발했고, t가 머문 곳 도착 후 30분 이내, h에서 300m 안.
//! - 후보가 여럿이면 도착 시각이 t와 가장 가까운 것, 같으면 h와 가장 가까운 것 하나.
//! - 고른 도착점의 장소 이름이 힌트 이름이 된다 (출처 Navi, 분류는 빈 문자열, 주소는 힌트 주소).
//! - 힌트는 저장해 두고 주행이 다시 계산될 때마다 다시 짝짓는다. 그래서 힌트가 주행·머문 곳 계산보다 먼저 와도,
//!   나중에 와도 같은 결과다. 고른 도착점이 아직 확정 전(달리는 중)이면 확정될 때 붙는다.
//!
//! # 저장 (DB 구조 버전 2)
//! `place`(장소·이름·다시 시도 시각), `trip_place`(주행 지점 → 장소, 주행과 함께 지워지고 다시 만들어짐),
//! `navi_hint`(받은 힌트 원본), `day_summary.stop_names` 칸. 버전 1 DB를 열면 표·칸을 더하고, 다음 조회 때
//! 모든 주행을 다시 계산해 지점을 잇는다 (원본 위치·신호는 그대로, 1년치 약 0.7초).

use std::collections::HashMap;
use std::time::{SystemTime, UNIX_EPOCH};

use rusqlite::{params, Connection, OptionalExtension};

use crate::geo::{haversine_m, Point};
use crate::store::{decode_stays, CoreError, EodiStore};

/// 이 거리(m) 안의 지점은 같은 장소로 묶는다.
pub const PLACE_RADIUS_M: f64 = 100.0;
/// 내비 힌트 위치와 도착 지점이 이 거리(m) 안이어야 짝이 된다.
pub const HINT_RADIUS_M: f64 = 300.0;
/// 내비 힌트는 도착 뒤 이 시간(ms) 안에 받은 것까지 그 도착에 붙는다.
pub const HINT_AFTER_ARRIVAL_MS: i64 = 30 * 60_000;

/// 이름 출처. 우선순위: User > Navi > Kakao > Osm (더 높은 출처의 이름이 오면 바꾼다).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum PlaceSource {
    /// 사용자가 직접 고친 이름 ("이 장소 이름 고치기") — 무엇보다 우선
    User,
    /// 차량 내비(NaviHelper) 목적지·경유지 이름 — 사용자가 직접 고른 곳이라 가장 정확
    Navi,
    /// 카카오 로컬 API (가게·건물 이름)
    Kakao,
    /// OpenStreetMap(Nominatim) 역지오코딩
    Osm,
}

/// 화면·지도에 붙는 장소 이름.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PlaceLabel {
    /// 보여 줄 이름 (예: "광장시장", "경포해변"). 가게 이름이 없으면 동·도로명.
    pub name: String,
    /// 분류 (예: "음식점", "관광명소"). 모르면 빈 문자열.
    pub category: String,
    /// 주소 한 줄. 모르면 빈 문자열.
    pub address: String,
    pub source: PlaceSource,
}

/// 이름이 필요한 지점 하나. `id`는 Rust가 정한 지점 번호(답할 때 그대로 돌려준다).
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct PlaceQuery {
    pub id: i64,
    pub lat: f64,
    pub lon: f64,
    /// 머문 곳이면 true (가게·건물 이름을 우선 찾는다), 주행 출발·도착 지점이면 false.
    pub is_stay: bool,
}

/// `PlaceQuery`에 대한 답.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PlaceName {
    pub id: i64,
    pub label: PlaceLabel,
}

/// 내비 힌트 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum NaviHintKind {
    Destination,
    Waypoint,
}

/// 차량 내비에서 받은 목적지·경유지 (주행이 끝날 때, 또는 도착 이벤트 때 Kotlin이 넘긴다).
/// Rust가 시각·거리로 가까운 머문 곳·도착 지점에 이름을 붙인다 (규칙은 모듈 설명).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NaviPlaceHint {
    pub name: String,
    pub lat: f64,
    pub lon: f64,
    /// 힌트를 받은 시각 (UTC epoch ms)
    pub t_ms: i64,
    pub kind: NaviHintKind,
    /// 주소 (모르면 빈 문자열)
    pub address: String,
}

#[uniffi::export]
impl EodiStore {
    /// 이름이 아직 없는 장소를 최신순(그 장소에 이어진 지점의 가장 늦은 시각)으로 최대 `limit`개.
    /// 가까운 지점(같은 장소)은 하나로 묶어서 준다 (모듈 설명). 밀린 주행 계산을 먼저 반영한다.
    /// `mark_place_unresolved`된 장소는 다시 시도할 시각이 될 때까지 빠진다.
    pub fn pending_places(&self, limit: u32) -> Result<Vec<PlaceQuery>, CoreError> {
        let mut conn = self.lock()?;
        self.flush(&mut conn)?;
        let mut st = conn.prepare_cached(
            "SELECT p.id, p.lat, p.lon, p.is_stay, max(r.t_ms) AS last
             FROM trip_place r JOIN place p ON p.id = r.place_id
             WHERE p.source IS NULL AND p.retry_at_ms <= ?1
             GROUP BY p.id ORDER BY last DESC, p.id DESC LIMIT ?2",
        )?;
        let rows = st.query_map(params![now_ms(), limit], |r| {
            Ok(PlaceQuery { id: r.get(0)?, lat: r.get(1)?, lon: r.get(2)?, is_stay: r.get(3)? })
        })?;
        Ok(rows.collect::<Result<_, _>>()?)
    }

    /// 찾은 이름을 저장한다. 같은 장소에 이미 더 높은 출처의 이름이 있으면 무시하고, 같은 출처면 바꾼다.
    /// 없는 `id`이거나 이름이 비어 있으면(공백뿐 포함) `InvalidInput` — 못 찾았으면 `mark_place_unresolved`를 부른다.
    pub fn set_place_name(&self, name: PlaceName) -> Result<(), CoreError> {
        if name.label.name.trim().is_empty() {
            return Err(CoreError::InvalidInput(format!("place {}: empty name", name.id)));
        }
        let mut conn = self.lock()?;
        let tx = conn.transaction()?;
        if !place_exists(&tx, name.id)? {
            return Err(CoreError::InvalidInput(format!("place {} not found", name.id)));
        }
        if set_label(&tx, name.id, &name.label)? {
            self.refresh_place_days(&tx, &[name.id])?;
        }
        tx.commit()?;
        Ok(())
    }

    /// 이름을 못 찾았다 (네트워크 없음, 결과 없음). 지금부터 `retry_after_ms` 뒤에 다시 `pending_places`에 나온다
    /// (0 이하면 바로). 없는 `id`면 `InvalidInput`.
    pub fn mark_place_unresolved(&self, id: i64, retry_after_ms: i64) -> Result<(), CoreError> {
        let conn = self.lock()?;
        let at = now_ms().saturating_add(retry_after_ms.max(0));
        if conn.execute("UPDATE place SET retry_at_ms = ?2 WHERE id = ?1", params![id, at])? == 0 {
            return Err(CoreError::InvalidInput(format!("place {id} not found")));
        }
        Ok(())
    }

    /// 내비 목적지·경유지 힌트를 저장하고, 짝이 되는 도착 지점(모듈 설명의 규칙)의 장소 이름으로 쓴다 (출처 Navi).
    /// 짝이 될 주행이 아직 없으면 저장만 해 두었다가 주행이 계산될 때 붙인다.
    /// 이름이 비었거나 좌표가 범위 밖·NaN이거나 `t_ms <= 0`이면 `InvalidInput`. 같은 힌트를 두 번 넣으면 한 번만 저장한다.
    pub fn add_navi_hint(&self, hint: NaviPlaceHint) -> Result<(), CoreError> {
        let coord_ok = hint.lat.is_finite()
            && hint.lon.is_finite()
            && (-90.0..=90.0).contains(&hint.lat)
            && (-180.0..=180.0).contains(&hint.lon);
        if hint.name.trim().is_empty() || !coord_ok || hint.t_ms <= 0 {
            return Err(CoreError::InvalidInput(format!("navi hint {hint:?}")));
        }
        let mut conn = self.lock()?;
        self.flush(&mut conn)?;
        let tx = conn.transaction()?;
        let kind = match hint.kind {
            NaviHintKind::Destination => HINT_DESTINATION,
            NaviHintKind::Waypoint => HINT_WAYPOINT,
        };
        tx.execute(
            "INSERT OR IGNORE INTO navi_hint (t_ms, kind, lat, lon, name, address) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![hint.t_ms, kind, hint.lat, hint.lon, hint.name, hint.address],
        )?;
        if let Some(id) = apply_hint(&tx, &hint)? {
            self.refresh_place_days(&tx, &[id])?;
        }
        tx.commit()?;
        Ok(())
    }
}

// ---- 내부 구현 (store의 flush·day_detail이 부른다) ----

/// `trip_place.kind`: 주행 출발점 / 주행 도착점 / 주행 안 머문 곳.
pub(crate) const REF_START: i64 = 0;
pub(crate) const REF_END: i64 = 1;
pub(crate) const REF_STAY: i64 = 2;
/// trip_place 한 줄의 키: (seg_start, kind, t_ms).
pub(crate) type RefKey = (i64, i64, i64);

const HINT_DESTINATION: i64 = 0;
const HINT_WAYPOINT: i64 = 1;

/// DB 구조 버전 2에서 더하는 표.
pub(crate) const SCHEMA_V2: &str = "
-- 이름이 붙는 장소. source: NULL = 이름 없음, -1 = User(버전 7부터), 0 = Navi, 1 = Kakao, 2 = Osm (작을수록 우선).
CREATE TABLE place (
    id INTEGER PRIMARY KEY,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    is_stay INTEGER NOT NULL DEFAULT 0,
    name TEXT,
    category TEXT NOT NULL DEFAULT '',
    address TEXT NOT NULL DEFAULT '',
    source INTEGER,
    retry_at_ms INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX place_lat ON place (lat);
-- 주행 지점 → 장소. kind 0 = 출발점(t = start_ms), 1 = 도착점(t = end_ms), 2 = 주행 안 머문 곳(t = arrive_ms).
-- trip 줄과 같이 지우고 다시 만든다.
CREATE TABLE trip_place (
    seg_start INTEGER NOT NULL,
    kind INTEGER NOT NULL,
    t_ms INTEGER NOT NULL,
    place_id INTEGER NOT NULL,
    PRIMARY KEY (seg_start, kind, t_ms)
) WITHOUT ROWID;
CREATE INDEX trip_place_place ON trip_place (place_id);
-- 받은 내비 힌트 원본. kind 0 = 목적지, 1 = 경유지.
CREATE TABLE navi_hint (
    t_ms INTEGER NOT NULL,
    kind INTEGER NOT NULL,
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    name TEXT NOT NULL,
    address TEXT NOT NULL,
    PRIMARY KEY (t_ms, kind, lat, lon, name)
) WITHOUT ROWID;
";

pub(crate) fn now_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

pub(crate) fn source_code(s: PlaceSource) -> i64 {
    match s {
        PlaceSource::User => -1,
        PlaceSource::Navi => 0,
        PlaceSource::Kakao => 1,
        PlaceSource::Osm => 2,
    }
}

pub(crate) fn source_from(code: i64) -> PlaceSource {
    match code {
        -1 => PlaceSource::User,
        0 => PlaceSource::Navi,
        1 => PlaceSource::Kakao,
        _ => PlaceSource::Osm,
    }
}

pub(crate) fn place_exists(conn: &Connection, id: i64) -> rusqlite::Result<bool> {
    Ok(conn.query_row("SELECT 1 FROM place WHERE id = ?1", [id], |_| Ok(())).optional()?.is_some())
}

/// 이름을 저장한다 (우선순위가 같거나 높을 때만). 이름이 생기면 다시 시도 대기는 지운다.
/// 반환: 실제로 바뀌었는지 (같은 이름을 다시 받으면 false).
pub(crate) fn set_label(conn: &Connection, id: i64, l: &PlaceLabel) -> rusqlite::Result<bool> {
    let n = conn.prepare_cached(
        "UPDATE place SET name = ?2, category = ?3, address = ?4, source = ?5, retry_at_ms = 0
         WHERE id = ?1 AND (source IS NULL OR source >= ?5)
           AND NOT (name IS ?2 AND category IS ?3 AND address IS ?4 AND source IS ?5)",
    )?
    .execute(params![id, l.name, l.category, l.address, source_code(l.source)])?;
    Ok(n > 0)
}

/// 지점 p에서 `radius_m` 안의 가장 가까운 장소 (거리가 같으면 번호가 작은 것). 없으면 None.
pub(crate) fn nearest_place(conn: &Connection, p: Point, radius_m: f64) -> rusqlite::Result<Option<i64>> {
    let dlat = radius_m / 111_000.0;
    let dlon = radius_m / (111_000.0 * p.lat.to_radians().cos().max(0.01));
    let mut st =
        conn.prepare_cached("SELECT id, lat, lon FROM place WHERE lat BETWEEN ?1 AND ?2 AND lon BETWEEN ?3 AND ?4")?;
    let rows = st.query_map(params![p.lat - dlat, p.lat + dlat, p.lon - dlon, p.lon + dlon], |r| {
        Ok((r.get::<_, i64>(0)?, Point { lat: r.get(1)?, lon: r.get(2)? }))
    })?;
    let mut best: Option<(f64, i64)> = None;
    for row in rows {
        let (id, q) = row?;
        let d = haversine_m(p, q);
        if d <= radius_m && best.is_none_or(|(bd, bid)| (d, id) < (bd, bid)) {
            best = Some((d, id));
        }
    }
    Ok(best.map(|(_, id)| id))
}

/// 지점 p를 장소에 잇는다: 100m 안 가장 가까운 장소, 없으면 새 장소. `is_stay`면 장소에 머문 곳 표시를 한다.
pub(crate) fn assign_place(conn: &Connection, p: Point, is_stay: bool) -> rusqlite::Result<i64> {
    match nearest_place(conn, p, PLACE_RADIUS_M)? {
        Some(id) => {
            if is_stay {
                mark_stay(conn, id)?;
            }
            Ok(id)
        }
        None => {
            conn.prepare_cached("INSERT INTO place (lat, lon, is_stay) VALUES (?1, ?2, ?3)")?
                .execute(params![p.lat, p.lon, is_stay])?;
            Ok(conn.last_insert_rowid())
        }
    }
}

pub(crate) fn mark_stay(conn: &Connection, id: i64) -> rusqlite::Result<()> {
    conn.prepare_cached("UPDATE place SET is_stay = 1 WHERE id = ?1 AND is_stay = 0")?.execute([id])?;
    Ok(())
}

pub(crate) fn link(conn: &Connection, seg_start: i64, kind: i64, t_ms: i64, place_id: i64) -> rusqlite::Result<()> {
    conn.prepare_cached("INSERT OR REPLACE INTO trip_place (seg_start, kind, t_ms, place_id) VALUES (?1, ?2, ?3, ?4)")?
        .execute(params![seg_start, kind, t_ms, place_id])?;
    Ok(())
}

pub(crate) fn linked(conn: &Connection, seg_start: i64, kind: i64, t_ms: i64) -> rusqlite::Result<Option<i64>> {
    conn.prepare_cached("SELECT place_id FROM trip_place WHERE seg_start = ?1 AND kind = ?2 AND t_ms = ?3")?
        .query_row(params![seg_start, kind, t_ms], |r| r.get(0))
        .optional()
}

/// 이름이 있는 지점: (seg_start, kind, t_ms) → 이름. seg_start가 [from, to)인 주행만, 쿼리 한 번.
pub(crate) fn labels_between(
    conn: &Connection,
    from: i64,
    to: i64,
) -> rusqlite::Result<HashMap<RefKey, PlaceLabel>> {
    let mut st = conn.prepare_cached(
        "SELECT r.seg_start, r.kind, r.t_ms, p.name, p.category, p.address, p.source
         FROM trip_place r JOIN place p ON p.id = r.place_id
         WHERE r.seg_start >= ?1 AND r.seg_start < ?2 AND p.source IS NOT NULL",
    )?;
    let rows = st.query_map([from, to], |r| {
        Ok((
            (r.get(0)?, r.get(1)?, r.get(2)?),
            PlaceLabel { name: r.get(3)?, category: r.get(4)?, address: r.get(5)?, source: source_from(r.get(6)?) },
        ))
    })?;
    rows.collect()
}

/// 시각이 [t0, t1]인 저장된 힌트를 다시 짝짓는다 (시각순, 늦은 힌트가 같은 출처의 앞 이름을 바꾼다).
/// 반환: 이름이 실제로 바뀐 장소들.
pub(crate) fn apply_hints_between(conn: &Connection, t0: i64, t1: i64) -> rusqlite::Result<Vec<i64>> {
    let hints: Vec<NaviPlaceHint> = {
        let mut st = conn.prepare_cached(
            "SELECT t_ms, kind, lat, lon, name, address FROM navi_hint WHERE t_ms BETWEEN ?1 AND ?2 ORDER BY t_ms",
        )?;
        let rows = st.query_map([t0, t1], |r| {
            Ok(NaviPlaceHint {
                t_ms: r.get(0)?,
                kind: if r.get::<_, i64>(1)? == HINT_WAYPOINT { NaviHintKind::Waypoint } else { NaviHintKind::Destination },
                lat: r.get(2)?,
                lon: r.get(3)?,
                name: r.get(4)?,
                address: r.get(5)?,
            })
        })?;
        rows.collect::<Result<_, _>>()?
    };
    let mut changed = Vec::new();
    for h in &hints {
        if let Some(id) = apply_hint(conn, h)? {
            if !changed.contains(&id) {
                changed.push(id);
            }
        }
    }
    Ok(changed)
}

/// 힌트 하나를 규칙(모듈 설명)대로 도착 지점 하나에 짝짓고, 그 지점이 장소에 이어져 있으면 이름을 붙인다.
/// 반환: 이름이 실제로 바뀐 장소.
fn apply_hint(conn: &Connection, h: &NaviPlaceHint) -> rusqlite::Result<Option<i64>> {
    let at = Point { lat: h.lat, lon: h.lon };
    let t = h.t_ms;
    // 후보 주행: t 이전에 출발했고 t - 30분 이후에 끝난 주행 (seg_start <= start_ms, seg_end >= end_ms라 넉넉히 읽고 거른다).
    let mut best: Option<((i64, f64), RefKey)> = None;
    let mut consider = |arrive: i64, p: Point, key: RefKey| {
        let d = haversine_m(at, p);
        if t <= arrive + HINT_AFTER_ARRIVAL_MS && d <= HINT_RADIUS_M {
            let score = ((t - arrive).abs(), d);
            if best.as_ref().is_none_or(|(b, _)| score.0 < b.0 || (score.0 == b.0 && score.1 < b.1)) {
                best = Some((score, key));
            }
        }
    };
    {
        let mut st = conn.prepare_cached(
            "SELECT seg_start, start_ms, end_ms, end_lat, end_lon, stays FROM trip
             WHERE seg_end >= ?1 AND seg_start <= ?2",
        )?;
        let mut q = st.query([t - HINT_AFTER_ARRIVAL_MS, t])?;
        while let Some(r) = q.next()? {
            let (seg, start_ms, end_ms): (i64, i64, i64) = (r.get(0)?, r.get(1)?, r.get(2)?);
            if start_ms > t {
                continue;
            }
            consider(end_ms, Point { lat: r.get(3)?, lon: r.get(4)? }, (seg, REF_END, end_ms));
            if h.kind == NaviHintKind::Waypoint {
                for s in decode_stays(&r.get::<_, Vec<u8>>(5)?) {
                    consider(s.arrive_ms, s.center, (seg, REF_STAY, s.arrive_ms));
                }
            }
        }
    }
    let Some((_, (seg, kind, t_ms))) = best else { return Ok(None) };
    let Some(id) = linked(conn, seg, kind, t_ms)? else { return Ok(None) };
    let label =
        PlaceLabel { name: h.name.clone(), category: String::new(), address: h.address.clone(), source: PlaceSource::Navi };
    Ok(set_label(conn, id, &label)?.then_some(id))
}

#[cfg(test)]
mod tests;

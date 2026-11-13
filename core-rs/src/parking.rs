//! 주차 위치 기억과 걸어서 찾아가기 — "내 차 어디였지?"
//!
//! - 차가 P에 들어가면(주행 끝 신호) 그 직전 마지막 위치가 곧 주차한 자리다 ([`EodiStore::parked_car`]).
//!   이름은 그 자리 100m 안에서 이미 이름이 붙은 장소(같은 주차장·가게를 예전에 다녀왔으면)를 쓰고,
//!   이번 주행에 내비 목적지가 있었으면 그곳을 "걸어서 갈 곳"으로 같이 준다 (주차장 → 가게 입구).
//! - 차는 이것을 폰으로 보내고(`ffi::handoff_seal_parked`), 폰은 받아 둔 것과 폰이 직접 기록한 것 중 늦은 것을 쓴다.
//! - 걸어서 안내([`walk_guide`])는 도보 경로 API 없이 방향과 거리만 준다: 주차장 · 가게 앞 같은 짧은 거리라
//!   "오른쪽 앞으로 120m"가 길 안내보다 빠르고, 지하 · 건물 안에서도 그대로 쓸 수 있다.

use rusqlite::OptionalExtension;
use serde::{Deserialize, Serialize};

use crate::geo::{bearing_deg, haversine_m, Point};
use crate::places::PLACE_RADIUS_M;
use crate::store::{CoreError, EodiStore, KIND_END, KIND_START};

/// 내비 목적지는 주차 지점에서 이 거리(m) 안일 때만 "걸어서 갈 곳"으로 본다.
pub const WALK_DEST_MAX_M: f64 = 2_000.0;
/// 내비 목적지 힌트는 주차 시각 앞뒤로 이 시간(ms) 안에 받은 것만 이번 주행 것으로 본다.
const DEST_HINT_WINDOW_MS: i64 = 30 * 60_000;
/// 이 거리(m) 안이면 다 왔다고 본다.
pub const WALK_ARRIVED_M: f64 = 15.0;

/// 주차한 자리 (+ 이번에 가려던 곳).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, uniffi::Record)]
pub struct ParkedCar {
    pub lat: f64,
    pub lon: f64,
    /// 주차한 시각 (UTC epoch ms)
    pub t_ms: i64,
    /// 주차한 곳 이름 (예: "경포해변 공영주차장"). 모르면 빈 문자열.
    pub name: String,
    /// 이번 주행의 내비 목적지 이름 — 차에서 내려 걸어갈 곳. 없으면 빈 문자열.
    pub dest_name: String,
    pub dest_lat: f64,
    pub dest_lon: f64,
}

impl ParkedCar {
    pub fn has_dest(&self) -> bool {
        !self.dest_name.is_empty()
    }
}

#[uniffi::export]
impl EodiStore {
    /// 지금 주차 중이면 주차한 자리. 달리는 중이거나 기록이 없으면 None.
    pub fn parked_car(&self) -> Result<Option<ParkedCar>, CoreError> {
        let conn = self.snapshot()?;
        let last: Option<(i64, i64)> = conn
            .query_row("SELECT t_ms, kind FROM drive_event ORDER BY t_ms DESC, kind DESC LIMIT 1", [], |r| {
                Ok((r.get(0)?, r.get(1)?))
            })
            .optional()?;
        let end_ms = match last {
            Some((t, k)) if k == KIND_END => t,
            Some((_, k)) if k == KIND_START => return Ok(None),
            _ => return Ok(None),
        };
        let Some((t_ms, lat, lon)) = conn
            .query_row("SELECT t_ms, lat, lon FROM fix WHERE t_ms <= ?1 ORDER BY t_ms DESC LIMIT 1", [end_ms], |r| {
                Ok((r.get::<_, i64>(0)?, r.get::<_, f64>(1)?, r.get::<_, f64>(2)?))
            })
            .optional()?
        else {
            return Ok(None);
        };
        let at = Point { lat, lon };
        let name = nearest_named(&conn, at)?.unwrap_or_default();
        let mut car = ParkedCar { lat, lon, t_ms: end_ms.max(t_ms), name, dest_name: String::new(), dest_lat: 0.0, dest_lon: 0.0 };
        // 이번 주행의 내비 목적지 (주차 30분 전 ~ 30분 뒤에 받은 것 중 가장 늦은 것)
        let dest: Option<(String, f64, f64)> = conn
            .query_row(
                "SELECT name, lat, lon FROM navi_hint WHERE kind = 0 AND t_ms BETWEEN ?1 AND ?2 ORDER BY t_ms DESC LIMIT 1",
                [end_ms - DEST_HINT_WINDOW_MS, end_ms + DEST_HINT_WINDOW_MS],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
            )
            .optional()?;
        if let Some((n, dlat, dlon)) = dest {
            let d = haversine_m(at, Point { lat: dlat, lon: dlon });
            if !n.is_empty() && d <= WALK_DEST_MAX_M {
                // 목적지 바로 앞에 세웠으면 주차한 곳 이름이 비어 있을 때 목적지 이름을 빌린다
                if car.name.is_empty() && d <= PLACE_RADIUS_M {
                    car.name = n.clone();
                }
                if d > WALK_ARRIVED_M {
                    (car.dest_name, car.dest_lat, car.dest_lon) = (n, dlat, dlon);
                }
            }
        }
        Ok(Some(car))
    }
}

fn nearest_named(conn: &rusqlite::Connection, at: Point) -> Result<Option<String>, CoreError> {
    // 위도 0.001° ≈ 111m → 넉넉히 거른 뒤 정확한 거리로 고른다
    let d = 0.0015;
    let mut st = conn.prepare_cached(
        "SELECT lat, lon, name FROM place WHERE name IS NOT NULL AND name <> '' AND lat BETWEEN ?1 AND ?2 AND lon BETWEEN ?3 AND ?4",
    )?;
    let rows = st.query_map([at.lat - d, at.lat + d, at.lon - d * 1.3, at.lon + d * 1.3], |r| {
        Ok((r.get::<_, f64>(0)?, r.get::<_, f64>(1)?, r.get::<_, String>(2)?))
    })?;
    let mut best: Option<(f64, String)> = None;
    for row in rows {
        let (lat, lon, name) = row?;
        let dist = haversine_m(at, Point { lat, lon });
        if dist <= PLACE_RADIUS_M && best.as_ref().is_none_or(|(b, _)| dist < *b) {
            best = Some((dist, name));
        }
    }
    Ok(best.map(|(_, n)| n))
}

/// 걸어서 찾아가기 한 순간의 안내.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct WalkGuide {
    pub distance_m: f64,
    /// 목표가 있는 방향 (북 0°, 시계 방향)
    pub bearing_deg: f64,
    /// 내가 보는 방향 기준 목표 방향 (-180~180, 오른쪽 +). 보는 방향을 모르면 bearing_deg와 같다.
    pub relative_deg: f64,
    /// 큰 글자 한 줄 (예: "오른쪽 앞 · 120m", "북동쪽 · 1.2km", "다 왔어요")
    pub text: String,
    /// 걷는 시간 어림 (분, 분속 70m, 최소 1)
    pub minutes: u32,
    pub arrived: bool,
}

/// 지금 위치·보는 방향(없으면 음수)에서 목표까지 방향과 거리.
pub fn walk_guide(from_lat: f64, from_lon: f64, heading_deg: f64, to_lat: f64, to_lon: f64) -> WalkGuide {
    let (a, b) = (Point { lat: from_lat, lon: from_lon }, Point { lat: to_lat, lon: to_lon });
    let dist = haversine_m(a, b);
    let bearing = bearing_deg(a, b).rem_euclid(360.0);
    let has_heading = heading_deg >= 0.0 && heading_deg.is_finite();
    let rel = if has_heading { wrap180(bearing - heading_deg) } else { wrap180(bearing) };
    let arrived = dist <= WALK_ARRIVED_M;
    let text = if arrived {
        "다 왔어요".to_string()
    } else {
        let way = if has_heading { relative_word(rel) } else { compass_word(bearing) };
        format!("{way} · {}", walk_meters(dist))
    };
    WalkGuide {
        distance_m: dist,
        bearing_deg: bearing,
        relative_deg: rel,
        text,
        minutes: ((dist / 70.0).ceil() as u32).max(1),
        arrived,
    }
}

/// 짧은 거리 표기 (걷는 거리라 10m 단위, 1km부터 소수 한 자리).
pub fn walk_meters(d: f64) -> String {
    if d >= 1000.0 {
        format!("{:.1}km", d / 1000.0)
    } else {
        format!("{}m", ((d / 10.0).round() * 10.0).max(10.0) as i64)
    }
}

fn wrap180(d: f64) -> f64 {
    let x = (d + 180.0).rem_euclid(360.0) - 180.0;
    if x == -180.0 { 180.0 } else { x }
}

fn relative_word(rel: f64) -> &'static str {
    match rel {
        r if r.abs() <= 20.0 => "앞쪽",
        r if (20.0..=70.0).contains(&r) => "오른쪽 앞",
        r if (-70.0..=-20.0).contains(&r) => "왼쪽 앞",
        r if (70.0..=110.0).contains(&r) => "오른쪽",
        r if (-110.0..=-70.0).contains(&r) => "왼쪽",
        r if (110.0..=160.0).contains(&r) => "오른쪽 뒤",
        r if (-160.0..=-110.0).contains(&r) => "왼쪽 뒤",
        _ => "뒤쪽",
    }
}

fn compass_word(b: f64) -> &'static str {
    const W: [&str; 8] = ["북쪽", "북동쪽", "동쪽", "남동쪽", "남쪽", "남서쪽", "서쪽", "북서쪽"];
    W[(((b + 22.5).rem_euclid(360.0)) / 45.0) as usize % 8]
}

/// 차 → 폰: 주차 위치 잠그기 (글 앞 "park:"로 공유 카드 · 기록 묶음과 구별).
pub fn seal_parked(key: String, car: ParkedCar) -> Result<String, CoreError> {
    let json = serde_json::to_string(&car).map_err(|_| CoreError::InvalidInput("주차 위치 묶기 실패".into()))?;
    crate::handoff::seal(&key, &format!("{PARK_PREFIX}{json}"))
}

/// 폰: 받은 글이 주차 위치면 연다 (아니면 None).
pub fn open_parked(key: String, msg: String) -> Option<ParkedCar> {
    let text = crate::handoff::open(&key, &msg).ok()?;
    serde_json::from_str(text.strip_prefix(PARK_PREFIX)?).ok()
}

/// 폰에 저장해 둘 때 쓰는 글 ↔ 값.
pub fn to_json(car: ParkedCar) -> String {
    serde_json::to_string(&car).unwrap_or_default()
}

pub fn from_json(json: String) -> Option<ParkedCar> {
    serde_json::from_str(&json).ok()
}

/// 둘 중 늦게 주차한 쪽 (차에서 받은 것 · 폰이 직접 기록한 것).
pub fn latest(a: Option<ParkedCar>, b: Option<ParkedCar>) -> Option<ParkedCar> {
    match (a, b) {
        (Some(x), Some(y)) => Some(if y.t_ms > x.t_ms { y } else { x }),
        (x, y) => x.or(y),
    }
}

const PARK_PREFIX: &str = "park:";

#[cfg(test)]
mod tests {
    use super::*;
    use crate::places::{NaviHintKind, NaviPlaceHint};
    use crate::store::{DriveEvent, GeoFix};

    fn store(tag: &str) -> (std::sync::Arc<EodiStore>, std::path::PathBuf) {
        let path = crate::test_tmp::dir().join(format!("eodi-park-{tag}-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        (EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap(), path)
    }

    #[test]
    fn parked_only_after_drive_end_with_walk_destination() {
        let (s, path) = store("a");
        assert_eq!(s.parked_car().unwrap(), None);
        let t0 = 1_700_000_000_000;
        s.mark_drive_event(DriveEvent::Start, t0).unwrap();
        let fixes: Vec<GeoFix> =
            (0..30).map(|i| GeoFix { t_ms: t0 + i * 10_000, lat: 37.79 + i as f64 * 0.0005, lon: 128.90, accuracy_m: 5.0 }).collect();
        s.ingest(fixes).unwrap();
        assert_eq!(s.parked_car().unwrap(), None, "달리는 중에는 주차 위치 없음");
        let end = t0 + 30 * 10_000;
        // 주차장에서 약 330m 떨어진 가게가 내비 목적지
        s.add_navi_hint(NaviPlaceHint {
            name: "초당할머니순두부".into(),
            lat: 37.8045 + 0.003,
            lon: 128.90,
            t_ms: end,
            kind: NaviHintKind::Destination,
            address: String::new(),
        })
        .unwrap();
        s.mark_drive_event(DriveEvent::End, end).unwrap();
        let car = s.parked_car().unwrap().unwrap();
        assert!((car.lat - (37.79 + 29.0 * 0.0005)).abs() < 1e-9);
        assert_eq!(car.t_ms, end);
        assert_eq!(car.dest_name, "초당할머니순두부");
        // 다시 출발하면 사라진다
        s.mark_drive_event(DriveEvent::Start, end + 600_000).unwrap();
        assert_eq!(s.parked_car().unwrap(), None);
        drop(s);
        let _ = std::fs::remove_file(&path);
    }

    #[test]
    fn walk_guide_words() {
        // 정북 약 111m, 북쪽을 보고 있으면 앞쪽
        let g = walk_guide(37.0, 127.0, 0.0, 37.001, 127.0);
        assert_eq!(g.text, "앞쪽 · 110m");
        assert_eq!(g.minutes, 2);
        // 동쪽을 보고 있으면 왼쪽
        assert!(walk_guide(37.0, 127.0, 90.0, 37.001, 127.0).text.starts_with("왼쪽 ·"));
        // 보는 방향을 모르면 방위
        assert!(walk_guide(37.0, 127.0, -1.0, 37.001, 127.001).text.starts_with("북동쪽"));
        assert!(walk_guide(37.0, 127.0, 0.0, 37.00005, 127.0).arrived);
    }

    #[test]
    fn handoff_round_trip_and_latest() {
        let key = crate::handoff::new_pairing().unwrap().key;
        let car = ParkedCar { lat: 1.0, lon: 2.0, t_ms: 5, name: "주차장".into(), dest_name: String::new(), dest_lat: 0.0, dest_lon: 0.0 };
        let msg = seal_parked(key.clone(), car.clone()).unwrap();
        assert_eq!(open_parked(key.clone(), msg), Some(car.clone()));
        assert_eq!(open_parked(key.clone(), crate::handoff::seal(&key, "hello:x").unwrap()), None);
        let newer = ParkedCar { t_ms: 9, ..car.clone() };
        assert_eq!(latest(Some(car.clone()), Some(newer.clone())), Some(newer));
        assert_eq!(from_json(to_json(car.clone())), Some(car));
    }
}

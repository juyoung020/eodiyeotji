//! 같이 가기 (여러 차가 같은 목적지로 함께 갈 때): 중계 서버에서 받은 차들의 마지막 위치로
//! 목적지까지 남은 거리, 나와의 간격(앞 · 뒤), 도착 시각 차이, 끊김을 계산하고 화면 · 음성에 쓸 짧은 한국어를 만든다.
//!
//! - 간격: 목적지가 있으면 "목적지까지 남은 거리" 차이 (같은 길을 가므로 앞 · 뒤가 분명하다).
//!   목적지가 없으면 직선거리 + 내 진행 방향 기준 앞 · 뒤.
//! - 끊김: 마지막 위치가 [STALE_MS](60초)보다 오래됐으면.
//! - 도착: 목적지 [ARRIVE_M](150m) 안.
//! - 음성 알림: 누가 도착함 · 누가 [BEHIND_ALERT_M](3km) 넘게 뒤처짐 (다시 [BEHIND_CLEAR_M] 안으로 오면 다시 알릴 수 있다).
//!   알림 사이 [SPEAK_GAP_MS](60초), 한 번 같이 가기에 [MAX_SPEAKS]번까지. 같은 일은 한 번만.
//! - 잔량 부족(다른 차): 남은 % < [LOW_PCT](15) 또는 남은 주행 가능 거리 < 목적지까지 거리 × [ROAD_FACTOR] + [RANGE_MARGIN_KM].
//!   알림 "민수 차 배터리 부족 · 32km 남음" (같은 차는 한 번, 다시 넉넉해지면 다시 알릴 수 있다). 내 차 잔량은 먼저 말 걸기(`proactive`)가 맡는다.
//!   [energy_stop]: 예전에 들렀던 충전소 · 주유소 중 지금 위치 → 목적지 길에서 덜 돌아가는 곳 (모두 함께 들를 경유지).
//! - 대열 이격 → 휴게소 대기 제안 ([Tracker]가 [wait_candidates] · [pick_wait_stop]으로): 다른 차가 [BEHIND_ALERT_M] 넘게 뒤처지거나
//!   목적지와 반대쪽으로 달리면, 내 길 앞 [WAIT_MIN_AHEAD_M]~[WAIT_MAX_AHEAD_M] 안에서 덜 돌아가는 휴게소 · 졸음쉼터(기기에 넣어 둔 공공데이터,
//!   `roadside::bundled` · `roadside::rank`)를 골라 "민수 차와 4.2km 멀어졌어요. 2km 앞 기흥휴게소에서 잠깐 기다릴까요?"를 만든다.
//!   떨어진 한 번에 한 번만 (다시 [BEHIND_CLEAR_M] 안으로 오면 다음에 또). 목적지가 [WAIT_DEST_NEAR_M] 안이면 목적지에서 만나면 되므로 하지 않는다.
//! - 화면 지도: 차 · 나 · 목적지 점과 차마다 최근 지나온 길(단순화)을 GeoJSON 하나로 (Kotlin은 MapLibre에 그대로 넘긴다).

use std::collections::{HashMap, HashSet};

use serde_json::json;

use crate::geo::{bearing_deg, haversine_m, simplify, Point};
use crate::ffi::GeoPoint;
use crate::roadside::{RoadsideCategory, RoadsideHit, RoadsidePlace};
use crate::mapdata::MapBounds;
use crate::memory::StopFact;
use crate::recap::{anchors, episodes_between, notable};
use crate::store::{CoreError, EodiStore};

pub const STALE_MS: i64 = 60_000;
pub const ARRIVE_M: f64 = 150.0;
pub const BEHIND_ALERT_M: f64 = 3_000.0;
pub const BEHIND_CLEAR_M: f64 = 2_000.0;
pub const NEAR_M: f64 = 200.0;
pub const SPEAK_GAP_MS: i64 = 60_000;
pub const MAX_SPEAKS: u32 = 12;
/// ETA를 모를 때 쓰는 평균 속도 (시내 · 국도 섞어 시속 40km)
pub const FALLBACK_SPEED_MPS: f64 = 11.0;
pub const LOW_PCT: f64 = 15.0;
pub const RANGE_MARGIN_KM: f64 = 10.0;
pub const ROAD_FACTOR: f64 = 1.3;
/// 함께 들를 곳: 돌아가는 길이 이보다 길면 고르지 않는다
pub const STOP_MAX_DETOUR_KM: f64 = 15.0;
/// 휴게소 대기 제안: 내 길 앞 이 거리 안의 휴게소 · 졸음쉼터만 (너무 가까우면 못 빠지고, 너무 멀면 기다리는 의미가 없다)
pub const WAIT_MIN_AHEAD_M: f64 = 1_500.0;
pub const WAIT_MAX_AHEAD_M: f64 = 20_000.0;
/// 길에서 벗어났다 돌아오는 거리(왕복)가 이보다 길면 고르지 않는다
pub const WAIT_MAX_DETOUR_M: f64 = 3_000.0;
/// 목적지가 이보다 가까우면 제안하지 않는다 (목적지에서 만나면 된다)
pub const WAIT_DEST_NEAR_M: f64 = 8_000.0;
/// 다른 차의 진행 방향이 목적지 쪽과 이만큼 넘게 어긋나면 "다른 길로 감"
pub const WAIT_OFF_DIR_DEG: f64 = 120.0;
const WAIT_OFF_MIN_SPEED_MPS: f64 = 5.0;
const WAIT_OFF_MIN_DIST_M: f64 = 1_000.0;
/// 졸음쉼터는 휴게소보다 이만큼 먼 것으로 친다 (화장실 · 먹을 곳이 있는 휴게소가 기다리기 낫다)
const WAIT_SHELTER_PENALTY_M: f64 = 3_000.0;
/// 고른 곳이 이보다 가까워지면(거의 다 옴) 제안을 거둔다
const WAIT_PASSED_M: f64 = 200.0;
const TRAIL_MAX: usize = 400;
const TRAIL_MIN_STEP_M: f64 = 15.0;
const TRAIL_SIMPLIFY_M: f64 = 8.0;

/// 차 한 대의 마지막 보고 (중계 서버 그대로).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ConvoyMemberIn {
    pub id: String,
    pub name: String,
    pub lat: f64,
    pub lon: f64,
    pub heading: f64,
    pub speed_mps: f64,
    /// 목적지까지 남은 시간(초), 모르면 음수
    pub eta_s: i64,
    pub ts_ms: i64,
    /// 전기차면 true (배터리), 아니면 연료
    pub ev: bool,
    /// 남은 % (모르면 음수)
    pub energy_pct: f64,
    /// 남은 주행 가능 거리 km (모르면 음수)
    pub range_km: f64,
}

/// 나 (내 차 위치). 위치를 아직 모르면 Kotlin이 None을 넘긴다.
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct ConvoyMe {
    pub lat: f64,
    pub lon: f64,
    pub heading: f64,
    pub speed_mps: f64,
    pub eta_s: i64,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ConvoyDest {
    pub name: String,
    pub lat: f64,
    pub lon: f64,
}

/// 화면에 그릴 차 한 대.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ConvoyMemberView {
    pub id: String,
    pub name: String,
    /// 지도 점 안에 쓸 한 글자
    pub initial: String,
    /// 색 번호 (0부터, 들어온 순서로 고정). Kotlin이 색 목록에서 고른다.
    pub color: u32,
    pub lat: f64,
    pub lon: f64,
    /// 목적지까지 남은 거리 (목적지가 없으면 음수)
    pub to_dest_m: f64,
    /// 나와의 간격: + 이면 나보다 앞, - 이면 뒤 (내 위치를 모르면 0)
    pub gap_m: f64,
    /// 도착 시각 차이 (초): + 이면 나보다 늦게 도착
    pub eta_gap_s: i64,
    pub stale: bool,
    pub arrived: bool,
    /// 배터리 · 연료 부족
    pub low: bool,
    /// 잔량 한 마디 ("배터리 32km" · "연료 12%", 모르면 빈 문자열). 정차 중 목록에만 쓴다.
    pub energy: String,
    /// 지도 이름표 ("민수 · 1.2km 뒤")
    pub label: String,
    /// 큰 카드 한 줄 ("민수 차 · 2.1km 뒤 · 도착 5분 차이")
    pub card: String,
    /// 나와의 위치 한 마디 ("5.2km 앞" · "바로 근처" · "도착", 모르면 빈 문자열)
    pub rel: String,
    /// 마지막 소식이 몇 분 전인지 (끊긴 차만 의미 있음)
    pub stale_min: i64,
    /// 잔량 막대 (화면 게이지)
    pub gauge: ConvoyEnergy,
}

/// 잔량 막대 하나: 채울 비율 · 짧은 글.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ConvoyEnergy {
    /// 전기차면 true (배터리), 아니면 연료
    pub ev: bool,
    /// 막대 채움 0.0~1.0 (모르면 음수)
    pub frac: f64,
    /// "30km" · "12%" (모르면 빈 문자열)
    pub text: String,
}

/// 잔량 막대: %가 있으면 %로, 없으면 남은 거리를 만충 거리(전기차 400km · 연료 600km)에 견준다.
pub fn energy_gauge(ev: bool, pct: f64, range_km: f64) -> ConvoyEnergy {
    let full = if ev { 400.0 } else { 600.0 };
    let frac = if pct >= 0.0 {
        (pct / 100.0).clamp(0.0, 1.0)
    } else if range_km >= 0.0 {
        (range_km / full).clamp(0.0, 1.0)
    } else {
        -1.0
    };
    let text = if range_km >= 0.0 {
        format!("{}km", range_km.round() as i64)
    } else if pct >= 0.0 {
        format!("{}%", pct.round() as i64)
    } else {
        String::new()
    };
    ConvoyEnergy { ev, frac, text }
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ConvoyView {
    /// 나를 뺀 차들 (색 번호 순)
    pub members: Vec<ConvoyMemberView>,
    /// 주행 중 큰 카드에 보일 차 (잔량 부족 차가 먼저, 아니면 가장 뒤처진 · 끊기지 않은 차), 없으면 None
    pub focus: Option<ConvoyMemberView>,
    /// 잔량이 부족한 첫 차 ("같이 충전소 들르기"를 보일지), 없으면 None
    pub low: Option<ConvoyMemberView>,
    /// 지도 GeoJSON (FeatureCollection)
    pub geojson: String,
    pub bounds: Option<MapBounds>,
    /// 지금 읽어 줄 말 (없으면 None)
    pub speak: Option<String>,
    /// 나도 목적지에 도착함 → 같이 가기를 끝낸다
    pub me_arrived: bool,
    /// 휴게소에서 기다리자는 제안 (없으면 None). 주행 카드 큰 버튼 · 정차 중 창에 보인다
    pub wait: Option<ConvoyWait>,
}

/// 대열이 떨어졌을 때 "앞 휴게소에서 기다리기" 제안.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ConvoyWait {
    pub member_id: String,
    pub member_name: String,
    /// 기다릴 곳 (공공데이터 그대로, 이름에 "부산방향"이 있으면 그대로 — 다시 잴 때 방향을 맞춘다)
    pub place: RoadsidePlace,
    /// 말 · 버튼 · 내비에 쓸 짧은 이름 ("기흥휴게소")
    pub place_name: String,
    /// 내 길을 따라 그곳까지 (m)
    pub along_m: f64,
    /// 길에서 벗어났다 돌아오는 거리 (m, 왕복)
    pub detour_m: f64,
    /// 그 차와 떨어진 거리 (m)
    pub apart_m: f64,
    /// 음성 "민수 차와 4.2km 멀어졌어요. 2km 앞 기흥휴게소에서 잠깐 기다릴까요?"
    pub say: String,
    /// 카드 한 줄 "민수 차와 4.2km 멀어졌어요 · 2km 앞 기흥휴게소"
    pub card: String,
    /// 큰 버튼 "기흥휴게소에서 기다리기"
    pub action: String,
}

/// "350m" · "1.2km" · "12km"
pub fn short_distance(m: f64) -> String {
    let m = m.abs();
    if m < 1_000.0 {
        format!("{}m", ((m / 10.0).round() * 10.0) as i64)
    } else if m < 10_000.0 {
        format!("{:.1}km", m / 1_000.0)
    } else {
        format!("{}km", (m / 1_000.0).round() as i64)
    }
}

/// 이름 첫 글자 (빈 이름이면 "?")
pub fn initial_of(name: &str) -> String {
    name.trim().chars().next().map(|c| c.to_uppercase().collect()).unwrap_or_else(|| "?".into())
}

fn eta_of(eta_s: i64, to_dest_m: f64, speed_mps: f64) -> Option<f64> {
    if eta_s >= 0 {
        return Some(eta_s as f64);
    }
    if to_dest_m < 0.0 {
        return None;
    }
    Some(to_dest_m / speed_mps.max(FALLBACK_SPEED_MPS))
}

/// 차 한 대의 상태 (순수 계산).
pub fn member_view(m: &ConvoyMemberIn, color: u32, me: Option<&ConvoyMe>, dest: Option<&ConvoyDest>, now_ms: i64) -> ConvoyMemberView {
    let p = Point { lat: m.lat, lon: m.lon };
    let to_dest_m = dest.map_or(-1.0, |d| haversine_m(p, Point { lat: d.lat, lon: d.lon }));
    let stale = now_ms - m.ts_ms > STALE_MS;
    let arrived = (0.0..=ARRIVE_M).contains(&to_dest_m);
    let (gap_m, eta_gap_s) = match me {
        None => (0.0, 0),
        Some(me) => {
            let mp = Point { lat: me.lat, lon: me.lon };
            let gap = match dest {
                Some(d) => haversine_m(mp, Point { lat: d.lat, lon: d.lon }) - to_dest_m,
                None => {
                    let dist = haversine_m(mp, p);
                    let rel = (bearing_deg(mp, p) - me.heading).to_radians().cos();
                    if rel >= 0.0 { dist } else { -dist }
                }
            };
            let my_to = dest.map_or(-1.0, |d| haversine_m(mp, Point { lat: d.lat, lon: d.lon }));
            let eta_gap = match (eta_of(m.eta_s, to_dest_m, m.speed_mps), eta_of(me.eta_s, my_to, me.speed_mps)) {
                (Some(a), Some(b)) => (a - b).round() as i64,
                _ => 0,
            };
            (gap, eta_gap)
        }
    };
    let name = m.name.trim();
    let name = if name.is_empty() { "같이 가는 차" } else { name };
    let what = if m.ev { "배터리" } else { "연료" };
    let range = known_range(m);
    // 끊긴 차는 잔량이 지금 값이 아니므로 "부족"으로 치지 않는다 (지도 테두리 · 목록 빨강 · 충전소 제안 · 알림이 한 규칙을 따른다)
    let low = !arrived && !stale && is_low(m.energy_pct, range, to_dest_m);
    let energy = if m.range_km >= 0.0 {
        format!("{what} {}km", m.range_km.round() as i64)
    } else if m.energy_pct >= 0.0 {
        format!("{what} {}%", m.energy_pct.round() as i64)
    } else {
        String::new()
    };
    let rel = if me.is_none() {
        String::new()
    } else if gap_m.abs() < NEAR_M {
        "바로 근처".into()
    } else if gap_m > 0.0 {
        format!("{} 앞", short_distance(gap_m))
    } else {
        format!("{} 뒤", short_distance(gap_m))
    };
    let (label, card) = if low && !stale {
        let left = if m.range_km >= 0.0 { format!("{}km", m.range_km.round() as i64) } else { format!("{}%", m.energy_pct.round() as i64) };
        (format!("{name} · {what} 부족"), format!("{name} 차 {what} 부족 · {left} 남음"))
    } else if arrived {
        (format!("{name} · 도착"), format!("{name} 차 · 먼저 도착했어요"))
    } else if stale {
        let min = ((now_ms - m.ts_ms) / 60_000).max(1);
        (format!("{name} · 위치 끊김"), format!("{name} 차 · 위치 끊김 ({min}분 전)"))
    } else {
        let mut card = format!("{name} 차");
        if !rel.is_empty() {
            card.push_str(" · ");
            card.push_str(&rel);
        }
        let mins = (eta_gap_s.abs() as f64 / 60.0).round() as i64;
        if dest.is_some() && me.is_some() && mins >= 1 {
            card.push_str(&format!(" · 도착 {mins}분 차이"));
        }
        let label = if rel.is_empty() { name.to_string() } else { format!("{name} · {rel}") };
        (label, card)
    };
    ConvoyMemberView {
        id: m.id.clone(),
        name: name.to_string(),
        initial: initial_of(name),
        color,
        lat: m.lat,
        lon: m.lon,
        to_dest_m,
        gap_m,
        eta_gap_s,
        stale,
        arrived,
        low,
        energy,
        label,
        card,
        rel: if arrived { "도착".into() } else { rel },
        stale_min: ((now_ms - m.ts_ms) / 60_000).max(1),
        gauge: energy_gauge(m.ev, m.energy_pct, m.range_km),
    }
}

/// 남은 주행 가능 거리 (km): 차가 준 값, 없으면 %로 어림 (전기차 400km · 연료 600km 기준). 모르면 None.
pub fn known_range(m: &ConvoyMemberIn) -> Option<f64> {
    if m.range_km >= 0.0 {
        Some(m.range_km)
    } else if m.energy_pct >= 0.0 {
        Some(m.energy_pct / 100.0 * if m.ev { 400.0 } else { 600.0 })
    } else {
        None
    }
}

/// 부족: % < 15, 또는 (목적지가 있으면) 남은 거리 < 목적지까지 길 거리 + 10km
pub fn is_low(pct: f64, range_km: Option<f64>, to_dest_m: f64) -> bool {
    if (0.0..LOW_PCT).contains(&pct) {
        return true;
    }
    match range_km {
        Some(r) if to_dest_m >= 0.0 => r < to_dest_m / 1000.0 * ROAD_FACTOR + RANGE_MARGIN_KM,
        _ => false,
    }
}

/// 함께 들를 충전소(ev) · 주유소: [visits] 중 이름 · 분류에 "충전"/"주유"가 있고, [from]→[dest] 길에서 돌아가는 거리가 가장 짧은 곳.
pub fn pick_energy_stop(visits: &[StopFact], from: Point, dest: Point, ev: bool) -> Option<StopFact> {
    let word = if ev { "충전" } else { "주유" };
    let direct = haversine_m(from, dest);
    visits
        .iter()
        .filter(|s| s.category.contains(word) || s.name.contains(word))
        .map(|s| {
            let p = Point { lat: s.lat, lon: s.lon };
            ((haversine_m(from, p) + haversine_m(p, dest) - direct) / 1000.0 * ROAD_FACTOR, s)
        })
        .filter(|(d, _)| *d <= STOP_MAX_DETOUR_KM)
        .min_by(|a, b| a.0.total_cmp(&b.0))
        .map(|(_, s)| s.clone())
}

/// 기록에서 함께 들를 충전소 · 주유소 찾기 (최근 400일 머문 곳, 집 · 회사 근처 제외).
pub fn energy_stop(store: &EodiStore, from: Point, dest: Point, ev: bool, now_ms: i64) -> Result<Option<StopFact>, CoreError> {
    let today = store.local_day(now_ms);
    let conn = store.read()?;
    let anchors = anchors(&conn)?;
    let visits: Vec<StopFact> = episodes_between(&conn, today - 400, today)?
        .into_iter()
        .flat_map(|e| e.facts.stops)
        .filter(|s| notable(s, &anchors))
        .collect();
    Ok(pick_energy_stop(&visits, from, dest, ev))
}

/// 같이 가기에서 내 위치를 서버로 보내도 되는가: 집 · 회사([crate::community::PRIVACY_RADIUS_M], 공유와 같은 기준) 안이면 보내지 않는다.
/// (CLAUDE.md "공유할 때 집 · 회사 근처 구간은 자동으로 가린다") 그동안 다른 차에는 마지막으로 보낸 위치가 남는다.
pub fn position_shareable(p: Point, anchors: &[Point]) -> bool {
    anchors.iter().all(|&a| haversine_m(a, p) > crate::community::PRIVACY_RADIUS_M)
}

/// [position_shareable]을 기기 DB의 집 · 회사(`memory_anchor`)로.
pub fn position_shareable_now(store: &EodiStore, p: Point) -> Result<bool, CoreError> {
    let conn = store.read()?;
    Ok(position_shareable(p, &crate::community::privacy_anchors(&conn)?))
}

/// 시연(공모전 판)용 함께 가는 차가 한 번 움직인 결과. Kotlin은 이 값을 그대로 중계 서버에 보낸다.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ConvoyDemoCar {
    pub lat: f64,
    pub lon: f64,
    pub heading: f64,
    pub speed_mps: f64,
    /// 목적지까지 초 (목적지가 없으면 -1)
    pub eta_s: i64,
    pub range_km: f64,
    pub energy_pct: f64,
    pub arrived: bool,
}

/// 시연 차 출발점: [base](내 위치 등)에서 목적지 반대쪽으로 [behind_km]만큼 (음수면 목적지 쪽 = 내 앞). 목적지가 없거나 같은 자리면 북쪽.
pub fn demo_start(base: Point, dest: Option<Point>, behind_km: f64) -> Point {
    let km_lon = 111.0 * base.lat.to_radians().cos();
    let (dlat, dlon) = match dest {
        Some(d) if haversine_m(base, d) > 1.0 => (base.lat - d.lat, base.lon - d.lon),
        _ => (1.0, 0.0),
    };
    let norm = ((dlat * 111.0).powi(2) + (dlon * km_lon).powi(2)).sqrt().max(1e-9);
    Point { lat: base.lat + dlat / norm * behind_km, lon: base.lon + dlon / norm * behind_km }
}

/// 시연 차 한 걸음: [at]에서 목적지로 곧게 [speed_mps] × [dt_s] 만큼 (목적지가 없으면 남쪽으로). 달린 만큼 남은 거리가 준다.
/// 길 모양은 없다 (시연용). 꽉 찬 주행 가능 거리는 전기차 400km · 연료차 600km로 % 를 만든다.
pub fn demo_step(at: Point, dest: Option<Point>, speed_mps: f64, dt_s: f64, range_km: f64, ev: bool) -> ConvoyDemoCar {
    let step = (speed_mps * dt_s).max(0.0);
    let (next, moved, eta_s, heading, arrived) = match dest {
        Some(d) => {
            let dist = haversine_m(at, d);
            if dist <= step.max(1.0) {
                (d, dist, 0, if dist > 1.0 { bearing_deg(at, d) } else { 0.0 }, true)
            } else {
                let f = step / dist;
                let p = Point { lat: at.lat + (d.lat - at.lat) * f, lon: at.lon + (d.lon - at.lon) * f };
                let left = dist - step;
                let eta = if speed_mps > 0.0 { (left / speed_mps).round() as i64 } else { -1 };
                (p, step, eta, bearing_deg(at, d), false)
            }
        }
        None => (Point { lat: at.lat - step / 111_000.0, lon: at.lon }, step, -1, 180.0, false),
    };
    let range = (range_km - moved / 1000.0).max(0.0);
    let full = if ev { 400.0 } else { 600.0 };
    ConvoyDemoCar {
        lat: next.lat,
        lon: next.lon,
        heading,
        speed_mps: if arrived { 0.0 } else { speed_mps },
        eta_s,
        range_km: range,
        energy_pct: (range / full * 1000.0).round() / 10.0,
        arrived,
    }
}

/// 시연 차가 대열에서 떨어진다: [base](내 위치) 기준 목적지 반대쪽 [from_km] → [to_km]로 [ramp_s]초 동안 차츰 (그 뒤는 [to_km]).
/// 내 위치에 맞춰 두므로 에뮬레이터에서 내가 서 있어도 "멀어짐"이 보인다. 나머지 값(방향 · 도착 시간 · 잔량)은 [demo_step]과 같다.
#[allow(clippy::too_many_arguments)]
pub fn demo_drift(base: Point, dest: Option<Point>, from_km: f64, to_km: f64, elapsed_s: f64, ramp_s: f64, speed_mps: f64, range_km: f64) -> ConvoyDemoCar {
    let k = if ramp_s > 0.0 { (elapsed_s / ramp_s).clamp(0.0, 1.0) } else { 1.0 };
    let at = demo_start(base, dest, from_km + (to_km - from_km) * k);
    demo_step(at, dest, speed_mps, 0.0, range_km, true)
}

/// 기다릴 곳 후보: 기기에 넣어 둔 휴게소 · 졸음쉼터 (공공데이터, 한 번만 읽는다).
pub fn wait_candidates() -> &'static [RoadsidePlace] {
    static PLACES: std::sync::OnceLock<Vec<RoadsidePlace>> = std::sync::OnceLock::new();
    PLACES.get_or_init(|| crate::roadside::bundled(&[RoadsideCategory::RestArea, RoadsideCategory::DrowsyShelter]))
}

/// 이 차가 대열에서 떨어졌나: [BEHIND_ALERT_M] 넘게 뒤처짐, 또는 (목적지가 있으면) 나와 1km 넘게 떨어져 목적지 반대쪽으로 달림.
/// 끊긴 차 · 도착한 차는 아니다.
pub fn separated(m: &ConvoyMemberIn, v: &ConvoyMemberView, me: &ConvoyMe, dest: Option<&ConvoyDest>) -> bool {
    if v.stale || v.arrived {
        return false;
    }
    if v.gap_m < -BEHIND_ALERT_M {
        return true;
    }
    let Some(d) = dest else { return false };
    if m.heading < 0.0 || m.speed_mps < WAIT_OFF_MIN_SPEED_MPS {
        return false;
    }
    let p = Point { lat: m.lat, lon: m.lon };
    if haversine_m(Point { lat: me.lat, lon: me.lon }, p) < WAIT_OFF_MIN_DIST_M {
        return false;
    }
    let diff = (bearing_deg(p, Point { lat: d.lat, lon: d.lon }) - m.heading).rem_euclid(360.0);
    diff.min(360.0 - diff) > WAIT_OFF_DIR_DEG
}

/// 내 길 앞쪽 후보 (가까운 순, `roadside::rank`). 길: 받은 경로 → 내 진행 방향 → 나에서 목적지로 곧게. 셋 다 없으면 빈 목록.
/// 목적지가 있으면 목적지보다 가까워지는 곳만 (목적지를 지나친 곳 · 옆길로 새는 곳은 뺀다).
fn wait_hits(route: &[GeoPoint], here: Point, heading: Option<f64>, dest: Option<Point>, candidates: Vec<RoadsidePlace>) -> Vec<RoadsideHit> {
    let heading = heading.filter(|h| h.is_finite() && *h >= 0.0);
    let line: Vec<GeoPoint> = if route.len() >= 2 || heading.is_some() {
        route.to_vec()
    } else if let Some(d) = dest {
        vec![GeoPoint { lat: here.lat, lon: here.lon }, GeoPoint { lat: d.lat, lon: d.lon }]
    } else {
        return Vec::new();
    };
    let mine = dest.map(|d| haversine_m(here, d));
    crate::roadside::rank(&line, GeoPoint { lat: here.lat, lon: here.lon }, heading, None, candidates, WAIT_MAX_DETOUR_M, 200)
        .into_iter()
        .filter(|h| match (dest, mine) {
            (Some(d), Some(mine)) => h.along_m < mine && haversine_m(Point { lat: h.place.lat, lon: h.place.lon }, d) < mine,
            _ => true,
        })
        .collect()
}

/// 기다릴 휴게소 · 졸음쉼터 하나 고르기 (순수 계산). 앞 [WAIT_MIN_AHEAD_M]~[WAIT_MAX_AHEAD_M], 돌아가는 거리 [WAIT_MAX_DETOUR_M] 안에서
/// 가까우면서 덜 돌아가는 곳 (졸음쉼터는 [WAIT_SHELTER_PENALTY_M]만큼 불리). 목적지가 [WAIT_DEST_NEAR_M] 안이면 None.
pub fn pick_wait_stop(route: &[GeoPoint], here: Point, heading: Option<f64>, dest: Option<Point>, candidates: Vec<RoadsidePlace>) -> Option<RoadsideHit> {
    if dest.is_some_and(|d| haversine_m(here, d) <= WAIT_DEST_NEAR_M) {
        return None;
    }
    let score = |h: &RoadsideHit| {
        h.along_m + 2.0 * h.detour_m + if h.place.category == RoadsideCategory::DrowsyShelter { WAIT_SHELTER_PENALTY_M } else { 0.0 }
    };
    wait_hits(route, here, heading, dest, candidates)
        .into_iter()
        .filter(|h| (WAIT_MIN_AHEAD_M..=WAIT_MAX_AHEAD_M).contains(&h.along_m))
        .min_by(|a, b| score(a).total_cmp(&score(b)))
}

/// "기흥휴게소 부산방향" → "기흥휴게소" (말 · 버튼에는 방향을 뺀다. 방향은 고를 때 이미 맞췄다)
pub fn short_place_name(name: &str) -> String {
    let words: Vec<&str> = name.split_whitespace().filter(|w| !w.trim_matches(|c| c == '(' || c == ')').ends_with("방향")).collect();
    if words.is_empty() {
        name.trim().to_string()
    } else {
        words.join(" ")
    }
}

/// 제안 문구 채우기.
fn wait_texts(w: &mut ConvoyWait) {
    let apart = short_distance(w.apart_m);
    let along = crate::roadside::distance_words(w.along_m);
    let place = &w.place_name;
    w.say = format!("{} 차와 {apart} 멀어졌어요. {along} 앞 {place}에서 잠깐 기다릴까요?", w.member_name);
    w.card = format!("{} 차와 {apart} 멀어졌어요 · {along} 앞 {place}", w.member_name);
    w.action = format!("{place}에서 기다리기");
}

/// 받은 함께 들를 곳이 휴게소 · 졸음쉼터면 "선두가 기흥휴게소에서 기다려요" (다른 차가 "기다리기"를 눌러 올린 곳). 충전소 · 주유소면 None.
pub fn waypoint_wait_note(name: &str) -> Option<String> {
    let n = short_place_name(name);
    let rest = n.ends_with("휴게소") || n.ends_with("쉼터");
    (rest && !n.contains("충전") && !n.contains("주유")).then(|| format!("선두가 {n}에서 기다려요"))
}

/// 같이 가기 한 번의 상태 (색 번호 · 지나온 길 · 이미 한 알림). Kotlin은 [ConvoyTracker::update]만 부른다.
#[derive(Debug, Default)]
pub struct Tracker {
    colors: HashMap<String, u32>,
    trails: HashMap<String, Vec<Point>>,
    announced_arrive: HashSet<String>,
    behind_alerted: HashSet<String>,
    low_alerted: HashSet<String>,
    last_speak_ms: Option<i64>,
    speaks: u32,
    /// 지금 보이는 휴게소 대기 제안
    wait: Option<ConvoyWait>,
    /// [wait]를 아직 말하지 못함 (알림 간격에 막혀도 다음 차례에 한 번 말한다)
    wait_unsaid: bool,
    /// 이번에 떨어진 동안 이미 제안한 차 (다시 가까워지면 지운다)
    wait_offered: HashSet<String>,
}

const ME_KEY: &str = "\u{0}me";

impl Tracker {
    fn push_trail(&mut self, key: &str, p: Point) {
        let t = self.trails.entry(key.to_string()).or_default();
        if t.last().is_none_or(|l| haversine_m(*l, p) >= TRAIL_MIN_STEP_M) {
            t.push(p);
            if t.len() > TRAIL_MAX {
                t.drain(..t.len() - TRAIL_MAX);
            }
        }
    }

    pub fn update(&mut self, now_ms: i64, me: Option<ConvoyMe>, dest: Option<ConvoyDest>, others: &[ConvoyMemberIn]) -> ConvoyView {
        let mut others: Vec<&ConvoyMemberIn> = others.iter().collect();
        for m in &others {
            if !self.colors.contains_key(&m.id) {
                let next = self.colors.len() as u32;
                self.colors.insert(m.id.clone(), next);
            }
        }
        others.sort_by_key(|m| self.colors[&m.id]);
        let mut me = me;
        // 진행 방향을 정말 아는가 (받은 값이 있거나 지나온 두 점이 있을 때). 모르면 휴게소 대기 제안은 목적지 쪽 직선으로 본다
        let mut heading_known: Option<f64> = None;
        if let Some(m) = me.as_mut() {
            self.push_trail(ME_KEY, Point { lat: m.lat, lon: m.lon });
            // 진행 방향을 모르면(음수) 내가 지나온 마지막 두 점으로
            if m.heading < 0.0 {
                m.heading = match self.trails.get(ME_KEY).map(|t| t.as_slice()) {
                    Some([.., a, b]) => {
                        let h = bearing_deg(*a, *b);
                        heading_known = Some(h);
                        h
                    }
                    _ => 0.0,
                };
            } else {
                heading_known = Some(m.heading);
            }
        }
        for m in &others {
            self.push_trail(&m.id, Point { lat: m.lat, lon: m.lon });
        }
        let members: Vec<ConvoyMemberView> =
            others.iter().map(|m| member_view(m, self.colors[&m.id], me.as_ref(), dest.as_ref(), now_ms)).collect();

        let wait_new = self.update_wait(me.as_ref(), heading_known, dest.as_ref(), &others, &members);

        // 알림 후보 (도착이 먼저)
        let mut events: Vec<String> = Vec::new();
        for v in &members {
            if v.arrived && self.announced_arrive.insert(v.id.clone()) {
                events.push(format!("{} 차가 목적지에 도착했어요.", v.name));
            }
        }
        for v in &members {
            if v.low && !v.stale {
                if self.low_alerted.insert(v.id.clone()) {
                    events.push(format!("{}.", v.card));
                }
            } else if !v.low {
                self.low_alerted.remove(&v.id);
            }
        }
        for v in &members {
            if v.stale || v.arrived || me.is_none() {
                continue;
            }
            if v.gap_m < -BEHIND_ALERT_M && self.behind_alerted.insert(v.id.clone()) {
                // 같은 차로 휴게소 대기를 제안하면 그 말에 이미 거리가 들어 있다
                if wait_new.as_deref() != Some(v.id.as_str()) {
                    events.push(format!("{} 차가 {} 뒤처졌어요.", v.name, short_distance(v.gap_m)));
                }
            } else if v.gap_m > -BEHIND_CLEAR_M {
                self.behind_alerted.remove(&v.id);
            }
        }
        let can_speak = self.speaks < MAX_SPEAKS && self.last_speak_ms.is_none_or(|t| now_ms - t >= SPEAK_GAP_MS);
        // 휴게소 대기 제안은 묻는 말이라 막혀도 버리지 않고 다음 차례에 한 번 말한다
        if self.wait_unsaid && can_speak {
            if let Some(w) = &self.wait {
                events.push(w.say.clone());
            }
            self.wait_unsaid = false;
        }
        // 막혀 있으면 이번 알림은 버린다 (같은 일은 다시 알리지 않는다 — 운전자를 귀찮게 하지 않게)
        let speak = if can_speak && !events.is_empty() {
            self.last_speak_ms = Some(now_ms);
            self.speaks += 1;
            Some(events.join(" "))
        } else {
            None
        };

        let low = members.iter().find(|v| v.low && !v.stale).cloned();
        let focus = low.clone().or_else(|| {
            members
                .iter()
                .filter(|v| !v.stale && !v.arrived)
                .min_by(|a, b| a.gap_m.total_cmp(&b.gap_m))
                .or_else(|| members.first())
                .cloned()
        });
        let me_arrived = match (&me, &dest) {
            (Some(m), Some(d)) => haversine_m(Point { lat: m.lat, lon: m.lon }, Point { lat: d.lat, lon: d.lon }) <= ARRIVE_M,
            _ => false,
        };
        let (geojson, bounds) = self.geojson(me.as_ref(), dest.as_ref(), &members);
        ConvoyView { members, focus, low, geojson, bounds, speak, me_arrived, wait: self.wait.clone() }
    }

    /// 휴게소 대기 제안을 거둔다 ("기다리기"를 눌렀거나 "괜찮아요"). 같은 차가 다시 가까워졌다 떨어지기 전에는 다시 묻지 않는다.
    pub fn dismiss_wait(&mut self) {
        self.wait = None;
        self.wait_unsaid = false;
    }

    /// 휴게소 대기 제안 갱신. 새로 제안한 차의 id를 돌려준다.
    fn update_wait(&mut self, me: Option<&ConvoyMe>, heading: Option<f64>, dest: Option<&ConvoyDest>, others: &[&ConvoyMemberIn], views: &[ConvoyMemberView]) -> Option<String> {
        let Some(me) = me else {
            self.dismiss_wait();
            return None;
        };
        let here = Point { lat: me.lat, lon: me.lon };
        let dest_p = dest.map(|d| Point { lat: d.lat, lon: d.lon });
        let mut apart: Vec<(&ConvoyMemberIn, &ConvoyMemberView)> =
            others.iter().zip(views).filter(|(m, v)| separated(m, v, me, dest)).map(|(m, v)| (*m, v)).collect();
        // 다시 가까워진 차는 다음에 또 제안할 수 있다
        self.wait_offered.retain(|id| apart.iter().any(|(m, _)| &m.id == id));
        // 지금 제안: 그 차가 따라왔거나, 그곳을 거의 지났으면 거둔다. 아니면 거리 · 문구만 새로
        if let Some(w) = self.wait.as_mut() {
            let still = apart.iter().find(|(m, _)| m.id == w.member_id);
            let hit = wait_hits(&[], here, heading, dest_p, vec![w.place.clone()]).into_iter().next();
            match (still, hit) {
                (Some((m, _)), Some(h)) if h.along_m > WAIT_PASSED_M => {
                    w.along_m = h.along_m;
                    w.detour_m = h.detour_m;
                    w.apart_m = haversine_m(here, Point { lat: m.lat, lon: m.lon });
                    wait_texts(w);
                }
                _ => self.dismiss_wait(),
            }
            return None;
        }
        // 새 제안: 가장 멀리 떨어진 차부터, 이번에 아직 묻지 않은 차
        apart.sort_by(|a, b| a.1.gap_m.total_cmp(&b.1.gap_m));
        let (m, v) = apart.into_iter().find(|(m, _)| !self.wait_offered.contains(&m.id))?;
        let hit = pick_wait_stop(&[], here, heading, dest_p, wait_candidates().to_vec())?;
        self.wait_offered.insert(m.id.clone());
        let mut w = ConvoyWait {
            member_id: m.id.clone(),
            member_name: v.name.clone(),
            place_name: short_place_name(&hit.place.name),
            place: hit.place,
            along_m: hit.along_m,
            detour_m: hit.detour_m,
            apart_m: haversine_m(here, Point { lat: m.lat, lon: m.lon }),
            say: String::new(),
            card: String::new(),
            action: String::new(),
        };
        wait_texts(&mut w);
        self.wait = Some(w);
        self.wait_unsaid = true;
        Some(m.id.clone())
    }

    fn geojson(&self, me: Option<&ConvoyMe>, dest: Option<&ConvoyDest>, members: &[ConvoyMemberView]) -> (String, Option<MapBounds>) {
        let mut feats = Vec::new();
        let mut b: Option<MapBounds> = None;
        let mut grow = |lat: f64, lon: f64| {
            let x = b.get_or_insert(MapBounds { min_lat: lat, min_lon: lon, max_lat: lat, max_lon: lon });
            x.min_lat = x.min_lat.min(lat);
            x.max_lat = x.max_lat.max(lat);
            x.min_lon = x.min_lon.min(lon);
            x.max_lon = x.max_lon.max(lon);
        };
        let line = |key: &str, color: i64, feats: &mut Vec<serde_json::Value>| {
            if let Some(t) = self.trails.get(key) {
                if t.len() >= 2 {
                    let coords: Vec<[f64; 2]> = simplify(t, TRAIL_SIMPLIFY_M).into_iter().map(|i| [t[i].lon, t[i].lat]).collect();
                    feats.push(json!({"type":"Feature","geometry":{"type":"LineString","coordinates":coords},
                        "properties":{"kind":"trail","color":color}}));
                }
            }
        };
        line(ME_KEY, -1, &mut feats);
        for v in members {
            line(&v.id, v.color as i64, &mut feats);
        }
        if let Some(d) = dest {
            grow(d.lat, d.lon);
            feats.push(json!({"type":"Feature","geometry":{"type":"Point","coordinates":[d.lon,d.lat]},
                "properties":{"kind":"dest","label":d.name}}));
        }
        if let Some(m) = me {
            grow(m.lat, m.lon);
            feats.push(json!({"type":"Feature","geometry":{"type":"Point","coordinates":[m.lon,m.lat]},
                "properties":{"kind":"me","heading":m.heading}}));
        }
        for v in members {
            grow(v.lat, v.lon);
            feats.push(json!({"type":"Feature","geometry":{"type":"Point","coordinates":[v.lon,v.lat]},
                "properties":{"kind":"car","id":v.id,"color":v.color,"initial":v.initial,"label":v.label,"stale":v.stale,"low":v.low}}));
        }
        (json!({"type":"FeatureCollection","features":feats}).to_string(), b)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const DEST: (f64, f64) = (37.5000, 127.0000);

    fn dest() -> ConvoyDest {
        ConvoyDest { name: "카페".into(), lat: DEST.0, lon: DEST.1 }
    }
    /// 목적지에서 정북쪽으로 km만큼 떨어진 곳
    fn north(km: f64) -> (f64, f64) {
        (DEST.0 + km / 111.195, DEST.1)
    }
    fn member(id: &str, name: &str, km: f64, ts: i64) -> ConvoyMemberIn {
        let (lat, lon) = north(km);
        ConvoyMemberIn { id: id.into(), name: name.into(), lat, lon, heading: 180.0, speed_mps: 15.0, eta_s: -1, ts_ms: ts, ev: true, energy_pct: -1.0, range_km: -1.0 }
    }
    fn me(km: f64) -> ConvoyMe {
        let (lat, lon) = north(km);
        ConvoyMe { lat, lon, heading: 180.0, speed_mps: 15.0, eta_s: -1 }
    }

    #[test]
    fn position_near_home_or_work_is_not_shared() {
        let home = Point { lat: 37.5, lon: 127.0 };
        let work = Point { lat: 37.6, lon: 127.1 };
        let anchors = [home, work];
        // 집에서 500m · 회사에서 900m → 보내지 않는다
        assert!(!position_shareable(Point { lat: 37.5 + 0.5 / 111.195, lon: 127.0 }, &anchors));
        assert!(!position_shareable(Point { lat: 37.6 - 0.9 / 111.195, lon: 127.1 }, &anchors));
        // 집에서 2km → 보낸다. 집 · 회사를 모르면 늘 보낸다
        assert!(position_shareable(Point { lat: 37.5 + 2.0 / 111.195, lon: 127.0 }, &anchors));
        assert!(position_shareable(home, &[]));
    }

    #[test]
    fn distances_are_short_korean() {
        assert_eq!(short_distance(347.0), "350m");
        assert_eq!(short_distance(-2_140.0), "2.1km");
        assert_eq!(short_distance(12_600.0), "13km");
    }

    #[test]
    fn behind_and_ahead_along_destination() {
        let now = 1_000_000;
        let v = member_view(&member("a", "민수", 7.0, now), 0, Some(&me(5.0)), Some(&dest()), now);
        assert!(v.gap_m < -1_900.0 && v.gap_m > -2_100.0, "{}", v.gap_m);
        assert!(v.card.starts_with("민수 차 · 2.0km 뒤"), "{}", v.card);
        assert!(v.card.contains("도착 2분 차이"), "{}", v.card);
        assert_eq!(v.label, "민수 · 2.0km 뒤");
        let a = member_view(&member("b", "지현", 1.0, now), 1, Some(&me(5.0)), Some(&dest()), now);
        assert!(a.gap_m > 0.0 && a.label.ends_with("앞"));
        assert!(a.eta_gap_s < 0);
    }

    #[test]
    fn no_destination_uses_heading() {
        let now = 0;
        // 나는 남쪽으로 간다(180도). 북쪽에 있는 차는 뒤.
        let v = member_view(&member("a", "민수", 7.0, now), 0, Some(&me(5.0)), None, now);
        assert!(v.gap_m < 0.0);
        assert!(!v.card.contains("도착"));
    }

    #[test]
    fn stale_and_arrived() {
        let now = 10 * 60_000;
        let s = member_view(&member("a", "민수", 3.0, now - 3 * 60_000), 0, Some(&me(5.0)), Some(&dest()), now);
        assert!(s.stale);
        assert_eq!(s.card, "민수 차 · 위치 끊김 (3분 전)");
        assert_eq!(s.label, "민수 · 위치 끊김");
        let a = member_view(&member("b", "지현", 0.05, now), 0, Some(&me(5.0)), Some(&dest()), now);
        assert!(a.arrived && a.label.ends_with("도착"));
    }

    #[test]
    fn tracker_announces_once_and_throttles() {
        let mut t = Tracker::default();
        let d = Some(dest());
        let v = t.update(0, Some(me(5.0)), d.clone(), &[member("a", "민수", 9.0, 0)]);
        assert_eq!(v.speak.as_deref(), Some("민수 차가 4.0km 뒤처졌어요."));
        assert_eq!(v.focus.as_ref().map(|f| f.id.as_str()), Some("a"));
        // 60초 안의 새 일(도착)은 버린다
        let quick = t.update(20_000, Some(me(5.0)), d.clone(), &[member("a", "민수", 9.0, 20_000), member("b", "지현", 0.0, 20_000)]);
        assert!(quick.speak.is_none());
        // 같은 일은 다시 알리지 않는다
        assert!(t.update(70_000, Some(me(5.0)), d.clone(), &[member("a", "민수", 9.0, 70_000)]).speak.is_none());
        // 따라잡았다가 다시 뒤처지면 다시 알린다
        t.update(200_000, Some(me(5.0)), d.clone(), &[member("a", "민수", 5.5, 200_000)]);
        let again = t.update(300_000, Some(me(5.0)), d.clone(), &[member("a", "민수", 9.5, 300_000)]);
        assert!(again.speak.unwrap().contains("민수"));
    }

    #[test]
    fn colors_are_stable_and_geojson_has_all_parts() {
        let mut t = Tracker::default();
        t.update(0, None, None, &[member("b", "지현", 3.0, 0)]);
        let v = t.update(5_000, Some(me(5.0)), Some(dest()), &[member("a", "민수", 4.0, 5_000), member("b", "지현", 2.9, 5_000)]);
        assert_eq!(v.members[0].id, "b");
        assert_eq!(v.members[0].color, 0);
        assert_eq!(v.members[1].color, 1);
        let g: serde_json::Value = serde_json::from_str(&v.geojson).unwrap();
        let kinds: Vec<&str> = g["features"].as_array().unwrap().iter().map(|f| f["properties"]["kind"].as_str().unwrap()).collect();
        assert!(kinds.contains(&"dest") && kinds.contains(&"me") && kinds.contains(&"car") && kinds.contains(&"trail"));
        assert!(v.bounds.is_some());
        assert!(!v.me_arrived);
        assert!(t.update(6_000, Some(me(0.05)), Some(dest()), &[]).me_arrived);
    }

    #[test]
    fn my_heading_from_trail() {
        let mut t = Tracker::default();
        let mut m = me(6.0);
        m.heading = -1.0;
        t.update(0, Some(m), None, &[]);
        let mut m2 = me(5.0);
        m2.heading = -1.0;
        // 남쪽으로 왔다 → 북쪽(7km)의 차는 뒤
        let v = t.update(1_000, Some(m2), None, &[member("a", "민수", 7.0, 1_000)]);
        assert!(v.members[0].gap_m < 0.0);
    }

    #[test]
    fn energy_gauge_fills_by_percent_then_range() {
        let g = energy_gauge(true, 30.0, 120.0);
        assert!((g.frac - 0.3).abs() < 1e-9 && g.text == "120km" && g.ev);
        let r = energy_gauge(false, -1.0, 300.0);
        assert!((r.frac - 0.5).abs() < 1e-9 && r.text == "300km");
        let u = energy_gauge(true, -1.0, -1.0);
        assert!(u.frac < 0.0 && u.text.is_empty());
        assert_eq!(energy_gauge(true, 150.0, -1.0).frac, 1.0);
    }

    #[test]
    fn low_energy_by_range_or_percent() {
        let now = 0;
        // 목적지까지 20km(길 26km) + 여유 10km > 남은 32km → 부족
        let mut m = member("a", "민수", 20.0, now);
        m.range_km = 32.0;
        let v = member_view(&m, 0, Some(&me(5.0)), Some(&dest()), now);
        assert!(v.low);
        assert_eq!(v.card, "민수 차 배터리 부족 · 32km 남음");
        assert_eq!(v.label, "민수 · 배터리 부족");
        m.range_km = 80.0;
        let ok = member_view(&m, 0, Some(&me(5.0)), Some(&dest()), now);
        assert!(!ok.low);
        assert_eq!(ok.energy, "배터리 80km");
        // 목적지가 없어도 15% 밑이면 부족 (연료)
        let mut f = member("b", "지현", 3.0, now);
        f.ev = false;
        f.energy_pct = 9.0;
        let fv = member_view(&f, 1, None, None, now);
        assert!(fv.low && fv.card.starts_with("지현 차 연료 부족"));
        assert!(!is_low(-1.0, None, 5_000.0));
    }

    #[test]
    fn stale_low_car_has_no_red_ring_on_the_map() {
        // 끊긴 차는 잔량이 지금 값이 아니므로 지도에서도 빨간 테두리를 빼고 흐리게만
        let mut t = Tracker::default();
        let mut m = member("a", "민수", 20.0, 0);
        m.range_km = 30.0;
        let v = t.update(10 * 60_000, Some(me(5.0)), Some(dest()), &[m]);
        let g: serde_json::Value = serde_json::from_str(&v.geojson).unwrap();
        let car = g["features"].as_array().unwrap().iter().find(|f| f["properties"]["kind"] == "car").unwrap();
        assert_eq!(car["properties"]["stale"], true);
        assert_eq!(car["properties"]["low"], false);
        assert!(!v.members[0].low && v.low.is_none(), "끊긴 차는 부족으로 치지 않는다 (충전소 제안도 없음)");
    }

    #[test]
    fn tracker_alerts_low_once_and_focuses_it() {
        let mut t = Tracker::default();
        let mut m = member("a", "민수", 20.0, 0);
        m.range_km = 30.0;
        let v = t.update(0, Some(me(5.0)), Some(dest()), &[member("b", "지현", 9.5, 0), m.clone()]);
        assert_eq!(v.low.as_ref().map(|l| l.id.as_str()), Some("a"));
        assert_eq!(v.focus.as_ref().map(|l| l.id.as_str()), Some("a"));
        assert!(v.speak.unwrap().contains("민수 차 배터리 부족 · 30km 남음"));
        m.ts_ms = 120_000;
        assert!(t.update(120_000, Some(me(5.0)), Some(dest()), &[m]).speak.is_none());
    }

    #[test]
    fn picks_stop_with_least_detour() {
        let stop = |name: &str, cat: &str, km_east: f64| StopFact {
            name: name.into(),
            category: cat.into(),
            address: String::new(),
            arrive_ms: 0,
            leave_ms: 0,
            lat: DEST.0 + 5.0 / 111.195,
            lon: DEST.1 + km_east / 88.0,
            first_visit: false,
        };
        let visits = vec![stop("먼 충전소", "", 9.0), stop("가까운", "전기차 충전소", 0.5), stop("주유소", "주유소", 0.0), stop("카페", "카페", 0.0)];
        let from = Point { lat: north(10.0).0, lon: DEST.1 };
        let to = Point { lat: DEST.0, lon: DEST.1 };
        assert_eq!(pick_energy_stop(&visits, from, to, true).unwrap().name, "가까운");
        assert_eq!(pick_energy_stop(&visits, from, to, false).unwrap().name, "주유소");
        assert!(pick_energy_stop(&visits[3..], from, to, true).is_none());
    }

    #[test]
    fn initials() {
        assert_eq!(initial_of(" 민수"), "민");
        assert_eq!(initial_of("kim"), "K");
        assert_eq!(initial_of(""), "?");
    }

    #[test]
    fn demo_start_goes_behind_me_away_from_dest() {
        let d = Point { lat: DEST.0, lon: DEST.1 };
        let (la, lo) = north(5.0);
        let me = Point { lat: la, lon: lo };
        let back = demo_start(me, Some(d), 2.5);
        let front = demo_start(me, Some(d), -0.8);
        assert!((haversine_m(back, d) - 7_500.0).abs() < 50.0);
        assert!((haversine_m(front, d) - 4_200.0).abs() < 50.0);
        // 목적지가 없으면 북쪽
        let n = demo_start(me, None, 1.0);
        assert!(n.lat > me.lat && (haversine_m(n, me) - 1_000.0).abs() < 20.0);
    }

    #[test]
    fn demo_step_moves_toward_dest_and_drains_range() {
        let d = Point { lat: DEST.0, lon: DEST.1 };
        let (la, lo) = north(3.0);
        let c = demo_step(Point { lat: la, lon: lo }, Some(d), 12.5, 4.0, 30.0, true);
        let p = Point { lat: c.lat, lon: c.lon };
        assert!((haversine_m(p, d) - 2_950.0).abs() < 5.0);
        assert!((c.heading - 180.0).abs() < 1.0);
        assert!((c.range_km - 29.95).abs() < 1e-6);
        assert_eq!(c.energy_pct, 7.5);
        assert!((c.eta_s - 236).abs() <= 1);
        assert!(!c.arrived);
        // 한 걸음보다 가까우면 목적지에 멈춘다
        let (la, lo) = north(0.03);
        let c = demo_step(Point { lat: la, lon: lo }, Some(d), 12.5, 4.0, 10.0, false);
        assert!(c.arrived && c.lat == d.lat && c.lon == d.lon && c.speed_mps == 0.0 && c.eta_s == 0);
        // 목적지가 없으면 남쪽으로
        let c = demo_step(d, None, 10.0, 3.0, 100.0, true);
        assert!(c.lat < d.lat && c.eta_s == -1 && c.heading == 180.0);
    }

    // ---- 대열 이격 → 휴게소 대기 제안 ----

    fn rest(name: &str, cat: RoadsideCategory, at: (f64, f64)) -> RoadsidePlace {
        RoadsidePlace { name: name.into(), category: cat, lat: at.0, lon: at.1, address: String::new(), one_way: false, source: "공공데이터".into(), food: String::new() }
    }
    fn pt(at: (f64, f64)) -> Point {
        Point { lat: at.0, lon: at.1 }
    }
    /// 정북 km 자리에서 동쪽으로 east_km
    fn north_east(km: f64, east_km: f64) -> (f64, f64) {
        let (la, lo) = north(km);
        (la, lo + east_km / (111.32 * la.to_radians().cos()))
    }
    fn dest_pt() -> Option<Point> {
        Some(Point { lat: DEST.0, lon: DEST.1 })
    }

    #[test]
    fn wait_stop_is_ahead_not_behind() {
        // 나는 목적지 북쪽 30km에서 남쪽(목적지)으로. 뒤 5km · 앞 5km 휴게소 → 앞
        let cands = vec![rest("뒤휴게소", RoadsideCategory::RestArea, north(35.0)), rest("앞휴게소", RoadsideCategory::RestArea, north(25.0))];
        let h = pick_wait_stop(&[], pt(north(30.0)), None, dest_pt(), cands.clone()).unwrap();
        assert_eq!(h.place.name, "앞휴게소");
        assert!((h.along_m - 5_000.0).abs() < 100.0, "{}", h.along_m);
        // 진행 방향을 알면 그 방향으로 (북쪽으로 가는 중이면 북쪽 것이 앞)
        let h = pick_wait_stop(&[], pt(north(30.0)), Some(0.0), None, cands).unwrap();
        assert_eq!(h.place.name, "뒤휴게소");
    }

    #[test]
    fn wait_stop_respects_detour_and_range() {
        let here = pt(north(30.0));
        // 길에서 동쪽으로 5km 떨어진 곳(왕복 10km)은 안 된다, 0.5km는 된다
        assert!(pick_wait_stop(&[], here, None, dest_pt(), vec![rest("먼 옆", RoadsideCategory::RestArea, north_east(25.0, 5.0))]).is_none());
        let h = pick_wait_stop(&[], here, None, dest_pt(), vec![rest("가까운 옆", RoadsideCategory::RestArea, north_east(25.0, 0.5))]).unwrap();
        assert!((h.detour_m - 1_000.0).abs() < 50.0, "{}", h.detour_m);
        // 너무 가까움(500m) · 너무 멂(25km) · 없음 → None
        assert!(pick_wait_stop(&[], here, None, dest_pt(), vec![rest("코앞", RoadsideCategory::RestArea, north(29.5))]).is_none());
        assert!(pick_wait_stop(&[], here, None, dest_pt(), vec![rest("멀리", RoadsideCategory::RestArea, north(5.0))]).is_none());
        assert!(pick_wait_stop(&[], here, None, dest_pt(), vec![]).is_none());
        // 목적지도 방향도 모르면 앞뒤를 가릴 수 없어 고르지 않는다
        assert!(pick_wait_stop(&[], here, None, None, vec![rest("앞휴게소", RoadsideCategory::RestArea, north(25.0))]).is_none());
    }

    #[test]
    fn wait_stop_prefers_rest_area_and_skips_when_destination_is_near() {
        let cands = vec![rest("앞쉼터", RoadsideCategory::DrowsyShelter, north(27.0)), rest("앞휴게소", RoadsideCategory::RestArea, north(25.0))];
        assert_eq!(pick_wait_stop(&[], pt(north(30.0)), None, dest_pt(), cands).unwrap().place.name, "앞휴게소");
        // 목적지가 6km 앞이면 목적지에서 만나면 된다
        assert!(pick_wait_stop(&[], pt(north(6.0)), None, dest_pt(), vec![rest("앞휴게소", RoadsideCategory::RestArea, north(3.0))]).is_none());
    }

    #[test]
    fn off_route_direction_counts_as_separated() {
        let now = 0;
        let d = dest();
        let mut m = member("a", "민수", 3.0, now); // 나보다 2km 앞, 남쪽(목적지)으로
        let v = member_view(&m, 0, Some(&me(5.0)), Some(&d), now);
        assert!(!separated(&m, &v, &me(5.0), Some(&d)));
        m.heading = 0.0; // 목적지 반대로 달림
        assert!(separated(&m, &v, &me(5.0), Some(&d)));
        m.speed_mps = 1.0; // 거의 서 있으면 방향을 믿지 않는다
        assert!(!separated(&m, &v, &me(5.0), Some(&d)));
    }

    #[test]
    fn tracker_suggests_wait_once_per_separation() {
        // 경부고속도로 수원 근처에서 천안으로, 민수는 4km 넘게 뒤
        let d = ConvoyDest { name: "천안".into(), lat: 36.8151, lon: 127.1139 };
        let my = ConvoyMe { lat: 37.30, lon: 127.10, heading: -1.0, speed_mps: 25.0, eta_s: -1 };
        let behind = |lat: f64, ts: i64| ConvoyMemberIn {
            id: "a".into(), name: "민수".into(), lat, lon: 127.104, heading: 180.0, speed_mps: 20.0, eta_s: -1, ts_ms: ts, ev: true, energy_pct: 80.0, range_km: 300.0,
        };
        let mut t = Tracker::default();
        let now = 1_000_000;
        let v = t.update(now, Some(my), Some(d.clone()), &[behind(37.34, now)]);
        let w = v.wait.clone().expect("앞 휴게소 제안");
        assert!(w.place.lat < my.lat, "앞(남쪽)이어야: {w:?}");
        assert!((WAIT_MIN_AHEAD_M..=WAIT_MAX_AHEAD_M).contains(&w.along_m), "{w:?}");
        assert!(!w.place_name.contains("방향") && w.place.name.starts_with(&w.place_name), "{w:?}");
        assert!(w.say.starts_with("민수 차와 4.") && w.say.ends_with("에서 잠깐 기다릴까요?"), "{}", w.say);
        assert_eq!(w.action, format!("{}에서 기다리기", w.place_name));
        let said = v.speak.unwrap();
        assert!(said.contains("기다릴까요") && !said.contains("뒤처졌어요"), "{said}");
        // 다음 차례: 제안은 남고, 다시 말하지 않는다
        let v = t.update(now + 5_000, Some(my), Some(d.clone()), &[behind(37.34, now + 5_000)]);
        assert!(v.wait.is_some() && v.speak.is_none(), "{:?} {:?}", v.wait, v.speak);
        // 거두면 같은 이격 동안은 다시 묻지 않는다
        t.dismiss_wait();
        assert!(t.update(now + 10_000, Some(my), Some(d.clone()), &[behind(37.345, now + 10_000)]).wait.is_none());
        // 따라왔다가(1km 뒤) 다시 떨어지면 또 묻는다
        assert!(t.update(now + 20_000, Some(my), Some(d.clone()), &[behind(37.309, now + 20_000)]).wait.is_none());
        let v = t.update(now + 200_000, Some(my), Some(d.clone()), &[behind(37.35, now + 200_000)]);
        assert!(v.wait.is_some() && v.speak.is_some_and(|s| s.contains("기다릴까요")));
        // 따라오면 제안이 저절로 사라진다
        assert!(t.update(now + 210_000, Some(my), Some(d.clone()), &[behind(37.305, now + 210_000)]).wait.is_none());
        // 목적지가 가까우면(5km) 묻지 않는다
        let mut t = Tracker::default();
        let near = ConvoyMe { lat: 36.86, lon: 127.11, heading: -1.0, speed_mps: 25.0, eta_s: -1 };
        let v = t.update(now, Some(near), Some(d), &[behind(36.92, now)]);
        assert!(v.wait.is_none() && v.speak.is_some_and(|s| s.contains("뒤처졌어요")));
    }

    #[test]
    fn wait_texts_and_waypoint_note() {
        assert_eq!(short_place_name("기흥휴게소 부산방향"), "기흥휴게소");
        assert_eq!(short_place_name("(서울방향) 졸음쉼터"), "졸음쉼터");
        assert_eq!(short_place_name("방향"), "방향");
        assert_eq!(waypoint_wait_note("기흥휴게소 부산방향").as_deref(), Some("선두가 기흥휴게소에서 기다려요"));
        assert_eq!(waypoint_wait_note("망향졸음쉼터").as_deref(), Some("선두가 망향졸음쉼터에서 기다려요"));
        assert_eq!(waypoint_wait_note("덕평휴게소 전기차충전소"), None);
        assert_eq!(waypoint_wait_note("스타필드 충전소"), None);
    }

    #[test]
    fn demo_drift_falls_behind_me_over_time() {
        let d = Point { lat: DEST.0, lon: DEST.1 };
        let (la, lo) = north(10.0);
        let me = Point { lat: la, lon: lo };
        let start = demo_drift(me, Some(d), -0.8, 4.6, 0.0, 40.0, 12.5, 180.0);
        let mid = demo_drift(me, Some(d), -0.8, 4.6, 20.0, 40.0, 12.5, 180.0);
        let end = demo_drift(me, Some(d), -0.8, 4.6, 99.0, 40.0, 12.5, 180.0);
        let to = |c: &ConvoyDemoCar| haversine_m(Point { lat: c.lat, lon: c.lon }, d);
        assert!((to(&start) - 9_200.0).abs() < 50.0);
        assert!((to(&mid) - 11_900.0).abs() < 50.0);
        assert!((to(&end) - 14_600.0).abs() < 50.0);
        assert!(!end.arrived && end.speed_mps == 12.5 && (end.heading - 180.0).abs() < 1.0);
    }
}

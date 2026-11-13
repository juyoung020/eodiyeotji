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
//! - 화면 지도: 차 · 나 · 목적지 점과 차마다 최근 지나온 길(단순화)을 GeoJSON 하나로 (Kotlin은 MapLibre에 그대로 넘긴다).

use std::collections::{HashMap, HashSet};

use serde_json::json;

use crate::geo::{bearing_deg, haversine_m, simplify, Point};
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
    let low = !arrived && is_low(m.energy_pct, range, to_dest_m);
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
        (format!("{name} · 신호 끊김"), format!("{name} 차 · 위치 끊김 ({min}분 전)"))
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
        if let Some(m) = me.as_mut() {
            self.push_trail(ME_KEY, Point { lat: m.lat, lon: m.lon });
            // 진행 방향을 모르면(음수) 내가 지나온 마지막 두 점으로
            if m.heading < 0.0 {
                m.heading = match self.trails.get(ME_KEY).map(|t| t.as_slice()) {
                    Some([.., a, b]) => bearing_deg(*a, *b),
                    _ => 0.0,
                };
            }
        }
        for m in &others {
            self.push_trail(&m.id, Point { lat: m.lat, lon: m.lon });
        }
        let members: Vec<ConvoyMemberView> =
            others.iter().map(|m| member_view(m, self.colors[&m.id], me.as_ref(), dest.as_ref(), now_ms)).collect();

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
                events.push(format!("{} 차가 {} 뒤처졌어요.", v.name, short_distance(v.gap_m)));
            } else if v.gap_m > -BEHIND_CLEAR_M {
                self.behind_alerted.remove(&v.id);
            }
        }
        let can_speak = self.speaks < MAX_SPEAKS && self.last_speak_ms.is_none_or(|t| now_ms - t >= SPEAK_GAP_MS);
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
        ConvoyView { members, focus, low, geojson, bounds, speak, me_arrived }
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
}

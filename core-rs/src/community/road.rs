//! 여행 코스 후기 지도의 도로 경로: 들른 곳과 곳 사이를 직선 대신 실제 도로로 잇는다.
//!
//! 1. [EodiStore::course_road_requests]: 아직 저장된 길이 없으면 카카오모빌리티 길찾기 주소들을 만든다
//!    (요청 하나 = 출발 + 경유지 5곳 + 도착 = 들른 곳 [STOPS_PER_REQUEST]곳, 다음 요청은 앞 요청의 도착에서 출발).
//! 2. Kotlin이 그 주소들을 동시에 받아 응답을 순서대로 [EodiStore::save_course_road]에 넘긴다.
//!    Rust가 구간(곳과 곳 사이)마다 도로 점을 꺼내 [ROAD_SIMPLIFY_M]로 줄이고, 코스 id 기준으로 기기 DB(`course_road`)에 둔다.
//! 3. 다음부터 [EodiStore::course_map]이 저장된 길을 바로 그린다 (네트워크 없이).
//!
//! 들른 곳이 바뀌면(`stops_key`가 다르면) 저장된 길은 버리고 다시 받는다. 카카오가 그 구간 길을 못 찾았다고
//! 답하면 그 구간만 직선으로 저장한다 (다시 물어도 같은 답이라). 응답을 못 받은 것(빈 문자열)은 저장하지 않는다.

use std::fmt::Write;

use rusqlite::{params, Connection, OptionalExtension};
use serde_json::Value;

use super::{stop_point, stops_map};
use crate::ffi::GeoPoint;
use crate::geo::{simplify, Point};
use crate::mapdata::DayMap;
use crate::memory::StopFact;
use crate::nav::{kakao_sections, nav_directions_url};
use crate::store::{CoreError, EodiStore};

/// DB 구조 버전 19에서 더하는 표: 코스 id → 들른 곳 열쇠 + 구간마다 줄인 도로 점 (JSON `[[[lon,lat],…],…]`).
pub(crate) const SCHEMA_V19: &str =
    "CREATE TABLE IF NOT EXISTS course_road (course_id TEXT PRIMARY KEY, stops_key TEXT NOT NULL, legs TEXT NOT NULL);";

/// 예전 버전 DB를 흉내 내는 테스트용.
#[cfg(test)]
pub(crate) const DROP_V19: &str = "DROP TABLE course_road;";

/// 도로 점 줄이기 허용 오차(m). 코스 지도는 시·군 단위로 보므로 10m면 굽은 길이 그대로 보인다.
pub const ROAD_SIMPLIFY_M: f64 = 10.0;

/// 길찾기 요청 하나에 넣는 들른 곳 수 (출발 1 + 경유지 5 + 도착 1, 카카오 경유지 한도).
pub const STOPS_PER_REQUEST: usize = 7;

/// 들른 곳 좌표 열쇠 (바뀌면 저장된 길을 다시 받는다).
pub(crate) fn stops_key(stops: &[StopFact]) -> String {
    let mut k = String::with_capacity(stops.len() * 24);
    for s in stops {
        let _ = write!(k, "{:.5},{:.5};", s.lat, s.lon);
    }
    k
}

/// 요청마다 맡는 들른 곳 범위 [처음, 끝] (끝 포함). 이웃 요청은 끝 곳을 같이 쓴다. 곳이 2곳 미만이면 빈 목록.
pub(crate) fn request_ranges(n: usize) -> Vec<(usize, usize)> {
    let mut out = Vec::new();
    let mut a = 0;
    while a + 1 < n {
        let b = (a + STOPS_PER_REQUEST - 1).min(n - 1);
        out.push((a, b));
        a = b;
    }
    out
}

/// 저장된 길 (구간마다 점들). 없거나, 들른 곳이 바뀌었거나, 구간 수가 안 맞으면 None.
pub(crate) fn load_road(conn: &Connection, id: &str, stops: &[StopFact]) -> Result<Option<Vec<Vec<Point>>>, CoreError> {
    let row: Option<(String, String)> = conn
        .query_row("SELECT stops_key, legs FROM course_road WHERE course_id = ?1", [id], |r| Ok((r.get(0)?, r.get(1)?)))
        .optional()?;
    let Some((key, legs)) = row else { return Ok(None) };
    if key != stops_key(stops) {
        return Ok(None);
    }
    let legs = decode_legs(&legs);
    Ok((legs.len() + 1 == stops.len()).then_some(legs))
}

fn encode_legs(legs: &[Vec<Point>]) -> String {
    let mut out = String::from("[");
    for (i, leg) in legs.iter().enumerate() {
        out.push_str(if i > 0 { ",[" } else { "[" });
        for (j, p) in leg.iter().enumerate() {
            let _ = write!(out, "{}[{:.6},{:.6}]", if j > 0 { "," } else { "" }, p.lon, p.lat);
        }
        out.push(']');
    }
    out.push(']');
    out
}

fn decode_legs(s: &str) -> Vec<Vec<Point>> {
    let v: Value = serde_json::from_str(s).unwrap_or(Value::Null);
    v.as_array()
        .into_iter()
        .flatten()
        .map(|leg| {
            leg.as_array()
                .into_iter()
                .flatten()
                .filter_map(|p| Some(Point { lon: p.get(0)?.as_f64()?, lat: p.get(1)?.as_f64()? }))
                .collect()
        })
        .collect()
}

/// 곳 [a] → 곳 [z] 구간 선: 도로 점을 줄이고 양 끝을 핀에 붙인다 (도로가 핀에서 조금 떨어져 시작해도 이어 보이게).
/// 도로 점이 2개 미만이면 직선.
fn leg_line(a: Point, z: Point, road: &[Point]) -> Vec<Point> {
    if road.len() < 2 {
        return vec![a, z];
    }
    let mut out = Vec::with_capacity(road.len() + 2);
    out.push(a);
    out.extend(simplify(road, ROAD_SIMPLIFY_M).into_iter().map(|i| road[i]));
    out.push(z);
    out.dedup();
    if out.len() < 2 {
        return vec![a, z];
    }
    out
}

/// 응답들([request_ranges] 순서)에서 구간마다 선을 만든다. 빈 응답(못 받음)이 있으면 None.
fn legs_from_responses(stops: &[StopFact], responses: &[String]) -> Result<Option<Vec<Vec<Point>>>, CoreError> {
    let ranges = request_ranges(stops.len());
    if responses.len() != ranges.len() {
        return Err(CoreError::InvalidInput(format!("길찾기 응답 수 {} (요청 {})", responses.len(), ranges.len())));
    }
    let mut legs = Vec::with_capacity(stops.len().saturating_sub(1));
    for ((a, b), body) in ranges.into_iter().zip(responses) {
        if body.trim().is_empty() {
            return Ok(None);
        }
        // 카카오가 길을 못 찾았거나 구간 수가 안 맞으면 이 요청의 구간은 모두 직선
        let sections = kakao_sections(body)?.filter(|s| s.len() == b - a);
        for i in a..b {
            let road = sections.as_ref().and_then(|s| s.get(i - a)).map(Vec::as_slice).unwrap_or(&[]);
            legs.push(leg_line(stop_point(&stops[i]), stop_point(&stops[i + 1]), road));
        }
    }
    Ok(Some(legs))
}

#[uniffi::export]
impl EodiStore {
    /// 코스 도로 경로를 받을 길찾기 주소들 (순서대로 받아 [EodiStore::save_course_road]에 넘긴다).
    /// 이미 저장돼 있거나 들른 곳이 2곳 미만이면 빈 목록. 없는 id면 `InvalidInput`.
    pub fn course_road_requests(&self, post_id: String) -> Result<Vec<String>, CoreError> {
        let post = self.course_by_id(&post_id)?;
        if load_road(&*self.snapshot()?, &post.id, &post.stops)?.is_some() {
            return Ok(Vec::new());
        }
        let gp = |s: &StopFact| GeoPoint { lat: s.lat, lon: s.lon };
        Ok(request_ranges(post.stops.len())
            .into_iter()
            .map(|(a, b)| nav_directions_url(gp(&post.stops[a]), post.stops[a + 1..=b].iter().map(gp).collect()))
            .collect())
    }

    /// 받은 길찾기 응답들(주소 순서대로, 못 받은 것은 빈 문자열)로 도로 경로를 줄여 저장하고, 새 코스 지도를 돌려준다.
    /// 못 받은 응답이 하나라도 있으면 저장하지 않고 직선 지도를 그대로 돌려준다. 응답을 못 읽으면 `InvalidInput`.
    pub fn save_course_road(&self, post_id: String, responses: Vec<String>) -> Result<DayMap, CoreError> {
        let post = self.course_by_id(&post_id)?;
        let Some(legs) = legs_from_responses(&post.stops, &responses)? else {
            return Ok(stops_map(&post.id, &post.stops, None));
        };
        {
            let conn = self.lock_community()?;
            conn.execute(
                "INSERT OR REPLACE INTO course_road (course_id, stops_key, legs) VALUES (?1, ?2, ?3)",
                params![post.id, stops_key(&post.stops), encode_legs(&legs)],
            )?;
        }
        Ok(stops_map(&post.id, &post.stops, Some(&legs)))
    }
}

#[cfg(test)]
mod tests;

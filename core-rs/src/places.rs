//! 장소 이름 (경유지·머문 곳·출발/도착을 좌표 대신 실제 장소 이름으로).
//!
//! 역할 분담:
//! - Rust(여기): 어떤 지점에 이름이 필요한지 고르고(가까운 지점은 한 곳으로 묶음), 받은 이름을 저장하고,
//!   하루 상세·지도 데이터에 붙인다. 이름 출처 우선순위도 여기서 정한다: 내비(Navi) > 카카오(Kakao) > OSM.
//! - Kotlin: `pending_places`로 받은 지점을 내비 정보·온라인 지도 API로 찾아 `set_place_name`으로 돌려준다.
//!   네트워크·API 호출만 하고, 고르기·묶기·우선순위 판단은 하지 않는다.
//!
//! [계약 단계 껍데기] 지금은 이름이 필요한 지점이 없다고 답하고, 받은 이름은 버린다. 실제 구현으로 교체될 예정.

use crate::store::{CoreError, EodiStore};

/// 이름 출처. 우선순위: Navi > Kakao > Osm (더 높은 출처의 이름이 오면 바꾼다).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum PlaceSource {
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
/// Rust가 시각·거리로 가까운 머문 곳·도착 지점에 이름을 붙인다.
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
    /// 이름이 아직 없는 지점을 최신순으로 최대 `limit`개. 가까운 지점(같은 장소)은 하나로 묶어서 준다.
    /// 최근에 `mark_place_unresolved`된 지점은 다시 시도할 때가 될 때까지 빠진다.
    pub fn pending_places(&self, limit: u32) -> Result<Vec<PlaceQuery>, CoreError> {
        let _ = limit;
        Ok(Vec::new())
    }

    /// 찾은 이름을 저장한다. 같은 지점에 이미 더 높은 출처의 이름이 있으면 무시한다.
    pub fn set_place_name(&self, name: PlaceName) -> Result<(), CoreError> {
        let _ = name;
        Ok(())
    }

    /// 이름을 못 찾았다 (네트워크 없음, 결과 없음). `retry_after_ms` 뒤에 다시 `pending_places`에 나온다.
    pub fn mark_place_unresolved(&self, id: i64, retry_after_ms: i64) -> Result<(), CoreError> {
        let _ = (id, retry_after_ms);
        Ok(())
    }

    /// 내비 목적지·경유지 힌트를 저장한다. 가까운(시각·거리) 머문 곳·도착 지점의 이름이 된다 (출처 Navi).
    pub fn add_navi_hint(&self, hint: NaviPlaceHint) -> Result<(), CoreError> {
        let _ = hint;
        Ok(())
    }
}

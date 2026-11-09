//! Kotlin에 공개하는 함수 모음 (UniFFI가 Kotlin 코드를 자동 생성한다).
//!
//! 규칙: 여기에는 계산을 쓰지 않는다. 다른 모듈의 함수를 묶어서 넘기기만 한다.
//! 위치처럼 많은 데이터는 한 번에 묶음(Vec)으로 주고받는다.

use std::sync::{Arc, Mutex, MutexGuard};
use std::time::Instant;

use crate::drivedetect::{Detector, DriveDetectOut, DriveEdgeOut, MotionFix};
use crate::geo::{self, Point};
use crate::memory::StopFact;
use crate::offers::{self, CourseOffers, OfferPlace, PartnerOffer};

/// Kotlin과 주고받는 좌표.
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct GeoPoint {
    pub lat: f64,
    pub lon: f64,
}

impl From<GeoPoint> for Point {
    fn from(p: GeoPoint) -> Self {
        Point { lat: p.lat, lon: p.lon }
    }
}

/// 코어 연결 확인 결과. 앱 첫 화면에 그대로 보여준다.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CoreCheck {
    pub version: String,
    pub points_in: u32,
    pub points_out: u32,
    pub elapsed_micros: u64,
}

/// 코어 버전 (Cargo.toml의 version).
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

/// 리뷰 쓰기 화면에서 고를 수 있는 키워드 (정해진 순서, `community::REVIEW_KEYWORDS`).
#[uniffi::export]
pub fn review_keyword_options() -> Vec<String> {
    crate::community::REVIEW_KEYWORDS.iter().map(|k| k.to_string()).collect()
}

/// 리뷰 하나에 고를 수 있는 키워드 수.
#[uniffi::export]
pub fn review_keyword_limit() -> u32 {
    crate::community::MAX_REVIEW_KEYWORDS as u32
}

/// 경로 단순화. 남길 점의 인덱스를 오름차순으로 돌려준다.
#[uniffi::export]
pub fn simplify_route(points: Vec<GeoPoint>, tolerance_m: f64) -> Vec<u32> {
    let pts: Vec<Point> = points.into_iter().map(Point::from).collect();
    geo::simplify(&pts, tolerance_m).into_iter().map(|i| i as u32).collect()
}

/// 코어가 앱 안에서 제대로 도는지 확인한다.
///
/// 하루 주행 규모(점 1만 개)의 가짜 경로를 만들어 단순화하고, 걸린 시간을 잰다.
#[uniffi::export]
pub fn core_self_check() -> CoreCheck {
    const N: usize = 10_000;
    let route = synthetic_route(N);
    let start = Instant::now();
    let kept = geo::simplify(&route, 5.0);
    let elapsed = start.elapsed();
    CoreCheck {
        version: core_version(),
        points_in: N as u32,
        points_out: kept.len() as u32,
        elapsed_micros: elapsed.as_micros() as u64,
    }
}

/// 폰 주행 감지기 (폰 판만 쓴다, 규칙은 `drivedetect`). 위치는 묶음으로 넘긴다.
#[derive(uniffi::Object)]
pub struct DriveDetector {
    inner: Mutex<Detector>,
}

#[uniffi::export]
impl DriveDetector {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self { inner: Mutex::new(Detector::default()) })
    }

    /// 주행 기록 중에 앱이 다시 뜬 경우: 주행 중 상태로 이어 간다 ([last_fix_ms] = 마지막으로 저장한 점 시각).
    pub fn resume_driving(&self, last_fix_ms: i64) {
        self.lock().resume_driving(last_fix_ms);
    }

    /// 위치 묶음을 넣는다 (시각 순서대로).
    pub fn push(&self, fixes: Vec<MotionFix>) -> DriveDetectOut {
        let mut d = self.lock();
        let edges: Vec<DriveEdgeOut> = fixes.iter().filter_map(|f| d.push(f)).collect();
        let now = fixes.last().map_or(0, |f| f.t_ms);
        DriveDetectOut { driving: d.is_driving(), high_rate: d.high_rate(now), edges }
    }

    /// 시간만 흐름 (위치가 안 들어와도 주행 끝을 알게, 주기적으로 부른다).
    pub fn tick(&self, now_ms: i64) -> DriveDetectOut {
        let mut d = self.lock();
        let edges: Vec<DriveEdgeOut> = d.tick(now_ms).into_iter().collect();
        DriveDetectOut { driving: d.is_driving(), high_rate: d.high_rate(now_ms), edges }
    }
}

impl DriveDetector {
    fn lock(&self) -> MutexGuard<'_, Detector> {
        self.inner.lock().unwrap_or_else(|e| e.into_inner())
    }
}

/// 장소마다 붙는 제휴 쿠폰 (넘긴 순서대로, 없으면 빈 목록). 화면 한 장의 장소를 한 번에 넘긴다 (`offers` 모듈).
#[uniffi::export]
pub fn offers_for_places(places: Vec<OfferPlace>) -> Vec<Vec<PartnerOffer>> {
    offers::offers_for(&places)
}

/// 제휴 쿠폰 하나 (없는 번호면 None).
#[uniffi::export]
pub fn offer_by_id(id: String) -> Option<PartnerOffer> {
    offers::find_offer(&id)
}

/// 코스마다 들른 곳의 제휴 쿠폰 (넘긴 순서대로). 커뮤니티 코스 목록 · 추천 코스 카드가 한 번에 넘긴다.
#[uniffi::export]
pub fn course_offers(courses: Vec<Vec<StopFact>>) -> Vec<CourseOffers> {
    courses.iter().map(|s| offers::course_offers_of(s)).collect()
}

/// 서울에서 동쪽으로 가다 북쪽으로 꺾는 경로에 GPS 흔들림(약 ±1m)을 섞은 점 n개.
fn synthetic_route(n: usize) -> Vec<Point> {
    let half = n / 2;
    (0..n)
        .map(|i| {
            let jitter = ((i * 7919) % 17) as f64 / 17.0 - 0.5; // 결정적인 흔들림
            let (dlat, dlon) = if i < half {
                (0.0, i as f64 * 1e-5)
            } else {
                ((i - half) as f64 * 1e-5, half as f64 * 1e-5)
            };
            Point { lat: 37.5665 + dlat + jitter * 1e-5, lon: 126.9780 + dlon + jitter * 1e-5 }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn self_check_reduces_a_day_route_to_a_handful_of_points() {
        let c = core_self_check();
        assert_eq!(c.points_in, 10_000);
        assert!(c.points_out >= 3, "모서리는 남아야 한다: {c:?}");
        assert!(c.points_out < 100, "흔들림은 지워져야 한다: {c:?}");
    }

    #[test]
    fn simplify_route_matches_geo_simplify() {
        let pts: Vec<GeoPoint> = (0..100).map(|i| GeoPoint { lat: 37.0 + i as f64 * 1e-5, lon: 127.0 }).collect();
        assert_eq!(simplify_route(pts, 5.0), vec![0, 99]);
    }

    #[test]
    fn drive_detector_reports_edges_and_rate() {
        let t0 = 1_700_000_000_000_i64;
        let det = DriveDetector::new();
        let fixes: Vec<MotionFix> = (0..120)
            .map(|i| MotionFix { lat: 37.5 + i as f64 * 12.0 / 111_195.0, lon: 127.0, t_ms: t0 + i * 1000, accuracy_m: 5.0, speed_mps: None })
            .collect();
        let out = det.push(fixes);
        assert!(out.driving && out.high_rate);
        assert_eq!(out.edges.len(), 1);
        let out = det.tick(t0 + 119_000 + 11 * 60_000);
        assert!(!out.driving && !out.high_rate);
        assert_eq!(out.edges.len(), 1);
        assert!(!out.edges[0].active);
    }
}

// ---------- 차 → 폰 넘기기 (handoff.rs) ----------

/// 새 페어링 (차).
#[uniffi::export]
pub fn handoff_new_pairing() -> Result<crate::handoff::HandoffPairing, crate::store::CoreError> {
    crate::handoff::new_pairing()
}

/// 코드로 페어링 글을 주고받는 주제 이름.
#[uniffi::export]
pub fn handoff_pair_topic(code: String) -> String {
    crate::handoff::pair_topic(&code)
}

#[uniffi::export]
pub fn handoff_seal_pairing(pairing: crate::handoff::HandoffPairing) -> Result<String, crate::store::CoreError> {
    crate::handoff::seal_pairing(&pairing)
}

/// 폰: 입력한 코드로 페어링 글을 연다 (틀리면 None).
#[uniffi::export]
pub fn handoff_open_pairing(code: String, msg: String) -> Option<crate::handoff::HandoffPairing> {
    crate::handoff::open_pairing(&code, &msg)
}

#[uniffi::export]
pub fn handoff_seal(key: String, text: String) -> Result<String, crate::store::CoreError> {
    crate::handoff::seal(&key, &text)
}

#[uniffi::export]
pub fn handoff_open(key: String, msg: String) -> Result<String, crate::store::CoreError> {
    crate::handoff::open(&key, &msg)
}

#[uniffi::export]
pub fn handoff_seal_card(key: String, card: crate::share::ShareCard) -> Result<String, crate::store::CoreError> {
    crate::handoff::seal_card(&key, &card)
}

#[uniffi::export]
pub fn handoff_open_card(key: String, msg: String) -> Result<crate::share::ShareCard, crate::store::CoreError> {
    crate::handoff::open_card(&key, &msg)
}

/// 글 → QR 칸 (페어링 화면).
#[uniffi::export]
pub fn qr_matrix(text: String) -> Result<crate::handoff::QrMatrix, crate::store::CoreError> {
    crate::handoff::qr_matrix(&text)
}

#[uniffi::export]
pub fn handoff_seal_bytes(key: String, data: Vec<u8>) -> Result<String, crate::store::CoreError> {
    crate::handoff::seal_bytes(&key, &data)
}

#[uniffi::export]
pub fn handoff_open_bytes(key: String, msg: String) -> Result<Vec<u8>, crate::store::CoreError> {
    crate::handoff::open_bytes(&key, &msg)
}

// ---------- 주차 위치 · 걸어서 찾아가기 (parking.rs) ----------

/// 지금 위치 · 보는 방향(모르면 음수)에서 목표까지 걷는 방향과 거리.
#[uniffi::export]
pub fn walk_guide(from_lat: f64, from_lon: f64, heading_deg: f64, to_lat: f64, to_lon: f64) -> crate::parking::WalkGuide {
    crate::parking::walk_guide(from_lat, from_lon, heading_deg, to_lat, to_lon)
}

/// 차 → 폰: 주차 위치 잠그기.
#[uniffi::export]
pub fn handoff_seal_parked(key: String, car: crate::parking::ParkedCar) -> Result<String, crate::store::CoreError> {
    crate::parking::seal_parked(key, car)
}

/// 폰: 받은 글이 주차 위치면 연다 (아니면 None).
#[uniffi::export]
pub fn handoff_open_parked(key: String, msg: String) -> Option<crate::parking::ParkedCar> {
    crate::parking::open_parked(key, msg)
}

#[uniffi::export]
pub fn parked_to_json(car: crate::parking::ParkedCar) -> String {
    crate::parking::to_json(car)
}

#[uniffi::export]
pub fn parked_from_json(json: String) -> Option<crate::parking::ParkedCar> {
    crate::parking::from_json(json)
}

/// 둘 중 늦게 주차한 쪽 (차에서 받은 것 · 폰이 직접 기록한 것).
#[uniffi::export]
pub fn parked_latest(a: Option<crate::parking::ParkedCar>, b: Option<crate::parking::ParkedCar>) -> Option<crate::parking::ParkedCar> {
    crate::parking::latest(a, b)
}

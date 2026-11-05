//! Kotlin에 공개하는 함수 모음 (UniFFI가 Kotlin 코드를 자동 생성한다).
//!
//! 규칙: 여기에는 계산을 쓰지 않는다. 다른 모듈의 함수를 묶어서 넘기기만 한다.
//! 위치처럼 많은 데이터는 한 번에 묶음(Vec)으로 주고받는다.

use std::time::Instant;

use crate::geo::{self, Point};

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
}

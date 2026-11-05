//! 코스(들른 곳 목록)를 내비 경로 요청 단위로 나눈다.
//!
//! NaviHelper `requestRouteWithWaypoints`는 경유지를 최대 2개(FIRST, SECOND)까지만
//! 받는다. 그래서 들른 곳이 많으면 "경유지 2개 + 목적지 1개" 구간으로 나누고,
//! 앱은 한 구간의 목적지에 도착하면(onDestinationArrived) 다음 구간을 요청한다.

use crate::geo::Point;

pub const MAX_WAYPOINTS: usize = 2;

#[derive(Debug, Clone, PartialEq)]
pub struct Leg {
    pub waypoints: Vec<Point>,
    pub destination: Point,
}

/// 출발지를 뺀 들른 곳 목록(방문 순서)을 구간으로 나눈다.
pub fn split_legs(stops: &[Point]) -> Vec<Leg> {
    stops
        .chunks(MAX_WAYPOINTS + 1)
        .map(|chunk| {
            let (dest, wps) = chunk.split_last().expect("chunks are never empty");
            Leg { waypoints: wps.to_vec(), destination: *dest }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn stops(n: usize) -> Vec<Point> {
        (0..n).map(|i| Point { lat: i as f64, lon: 0.0 }).collect()
    }

    #[test]
    fn no_stops_no_legs() {
        assert!(split_legs(&[]).is_empty());
    }

    #[test]
    fn single_stop_is_a_plain_route() {
        let legs = split_legs(&stops(1));
        assert_eq!(legs.len(), 1);
        assert!(legs[0].waypoints.is_empty());
    }

    #[test]
    fn three_stops_fit_in_one_leg() {
        let s = stops(3);
        assert_eq!(split_legs(&s), vec![Leg { waypoints: vec![s[0], s[1]], destination: s[2] }]);
    }

    #[test]
    fn seven_stops_become_three_legs_in_order() {
        let s = stops(7);
        let legs = split_legs(&s);
        assert_eq!(legs.len(), 3);
        assert!(legs.iter().all(|l| l.waypoints.len() <= MAX_WAYPOINTS));
        let flattened: Vec<Point> = legs
            .iter()
            .flat_map(|l| l.waypoints.iter().copied().chain(std::iter::once(l.destination)))
            .collect();
        assert_eq!(flattened, s, "모든 들른 곳이 순서대로 한 번씩 포함돼야 한다");
    }
}

//! 코스(들른 곳 목록)를 내비 경로 요청 단위로 나눈다.
//!
//! NaviHelper `requestRouteWithWaypoints`는 경유지를 최대 2개(FIRST, SECOND)까지만
//! 받는다. 그래서 들른 곳이 많으면 "경유지 2개 + 목적지 1개" 구간으로 나누고,
//! 앱은 한 구간의 목적지에 도착하면(onDestinationArrived) 다음 구간을 요청한다.

use crate::geo::{haversine_m, Point};

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

/// 이 개수까지는 정확한 최단 순서(Held-Karp 동적 계획, 2^n·n² 계산)를 구한다. 넘으면 근사(가까운 곳 먼저 + 2-opt).
pub const EXACT_MAX_STOPS: usize = 12;

/// 열린 경로 길이(m): `from`(있으면) → order 순서대로 들른 곳. 직선(대원) 거리 합.
pub fn path_length_m(from: Option<Point>, stops: &[Point], order: &[usize]) -> f64 {
    let mut prev = from;
    let mut sum = 0.0;
    for &i in order {
        if let Some(p) = prev {
            sum += haversine_m(p, stops[i]);
        }
        prev = Some(stops[i]);
    }
    sum
}

/// 모든 들른 곳을 한 번씩 지나는 가장 짧은 방문 순서 (돌아오지 않는 열린 경로). 반환: `stops`의 인덱스 순서.
///
/// - `from`(지금 위치)이 있으면 거기서 출발한다. 없으면 원래 첫 곳(`stops[0]`)에서 출발하고 나머지 순서만 바꾼다.
/// - [`EXACT_MAX_STOPS`]개 이하면 정확한 최단 순서, 넘으면 가까운 곳 먼저 고른 뒤 2-opt로 다듬은 근사 순서.
/// - 길이가 같은 순서가 여럿이면 원래 순서에 가까운(사전순으로 앞선) 것.
pub fn best_order(from: Option<Point>, stops: &[Point]) -> Vec<usize> {
    let n = stops.len();
    if n <= 1 {
        return (0..n).collect();
    }
    let Some(origin) = from else {
        let rest: Vec<Point> = stops[1..].to_vec();
        let mut order = vec![0];
        order.extend(best_order(Some(stops[0]), &rest).into_iter().map(|i| i + 1));
        return order;
    };
    if n <= EXACT_MAX_STOPS {
        held_karp(origin, stops)
    } else {
        two_opt(origin, stops, nearest_first(origin, stops))
    }
}

/// 정확한 열린 경로 최단 순서 (Held-Karp). dp[집합][마지막] = origin에서 집합을 모두 지나 마지막에 선 최소 거리.
fn held_karp(origin: Point, stops: &[Point]) -> Vec<usize> {
    let n = stops.len();
    let full = (1usize << n) - 1;
    let d = |a: usize, b: usize| haversine_m(stops[a], stops[b]);
    let mut dp = vec![f64::INFINITY; (full + 1) * n];
    let mut parent = vec![usize::MAX; (full + 1) * n];
    for j in 0..n {
        dp[(1 << j) * n + j] = haversine_m(origin, stops[j]);
    }
    for mask in 1..=full {
        for j in 0..n {
            let cur = dp[mask * n + j];
            if mask & (1 << j) == 0 || !cur.is_finite() {
                continue;
            }
            for k in 0..n {
                if mask & (1 << k) != 0 {
                    continue;
                }
                let next = mask | (1 << k);
                let c = cur + d(j, k);
                // 1mm 미만 차이는 같은 길이로 보고 먼저 찾은(원래 순서에 가까운) 것을 남긴다.
                if c < dp[next * n + k] - 1e-3 {
                    dp[next * n + k] = c;
                    parent[next * n + k] = j;
                }
            }
        }
    }
    let mut last = (0..n).min_by(|&a, &b| dp[full * n + a].total_cmp(&dp[full * n + b])).unwrap_or(0);
    let mut mask = full;
    let mut rev = Vec::with_capacity(n);
    while last != usize::MAX {
        rev.push(last);
        let p = parent[mask * n + last];
        mask &= !(1 << last);
        last = p;
    }
    rev.reverse();
    rev
}

/// 가까운 곳 먼저: origin에서 가장 가까운 곳, 거기서 가장 가까운 곳 … 순서.
fn nearest_first(origin: Point, stops: &[Point]) -> Vec<usize> {
    let mut left: Vec<usize> = (0..stops.len()).collect();
    let mut at = origin;
    let mut order = Vec::with_capacity(stops.len());
    while !left.is_empty() {
        let (k, _) = left
            .iter()
            .enumerate()
            .min_by(|a, b| haversine_m(at, stops[*a.1]).total_cmp(&haversine_m(at, stops[*b.1])))
            .expect("left is not empty");
        let i = left.remove(k);
        order.push(i);
        at = stops[i];
    }
    order
}

/// 2-opt: 구간을 뒤집어 짧아지면 바꾸기를 더 줄지 않을 때까지 (열린 경로, 출발점 origin 고정).
fn two_opt(origin: Point, stops: &[Point], mut order: Vec<usize>) -> Vec<usize> {
    let n = order.len();
    let pt = |order: &[usize], k: usize| if k == 0 { origin } else { stops[order[k - 1]] };
    let mut improved = true;
    while improved {
        improved = false;
        // 경로 위치 0 = origin, 1..=n = order. 간선 (a-1,a)와 (b,b+1)을 (a-1,b)·(a,b+1)로 바꾼다 (b가 끝이면 뒤 간선 없음).
        for a in 1..n {
            for b in a + 1..=n {
                let (p, q) = (pt(&order, a - 1), pt(&order, a));
                let r = pt(&order, b);
                let before = haversine_m(p, q) + if b < n { haversine_m(r, pt(&order, b + 1)) } else { 0.0 };
                let after = haversine_m(p, r) + if b < n { haversine_m(q, pt(&order, b + 1)) } else { 0.0 };
                if after + 1e-3 < before {
                    order[a - 1..b].reverse();
                    improved = true;
                }
            }
        }
    }
    order
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

    /// 결정적인 흩어진 점 n개 (서울 근처 약 20km 안).
    fn scattered(n: usize, seed: u64) -> Vec<Point> {
        let mut x = seed.wrapping_mul(2_862_933_555_777_941_757).wrapping_add(3_037_000_493);
        (0..n)
            .map(|_| {
                x = x.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1_442_695_040_888_963_407);
                let a = (x >> 33) as f64 / (1u64 << 31) as f64;
                x = x.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1_442_695_040_888_963_407);
                let b = (x >> 33) as f64 / (1u64 << 31) as f64;
                Point { lat: 37.4 + a * 0.2, lon: 126.9 + b * 0.25 }
            })
            .collect()
    }

    fn brute_force(from: Point, s: &[Point]) -> f64 {
        fn go(from: Point, s: &[Point], used: &mut Vec<bool>, order: &mut Vec<usize>, best: &mut f64) {
            if order.len() == s.len() {
                *best = best.min(path_length_m(Some(from), s, order));
                return;
            }
            for i in 0..s.len() {
                if !used[i] {
                    used[i] = true;
                    order.push(i);
                    go(from, s, used, order, best);
                    order.pop();
                    used[i] = false;
                }
            }
        }
        let mut best = f64::INFINITY;
        go(from, s, &mut vec![false; s.len()], &mut Vec::new(), &mut best);
        best
    }

    fn is_permutation(order: &[usize], n: usize) -> bool {
        let mut seen = vec![false; n];
        order.len() == n && order.iter().all(|&i| i < n && !std::mem::replace(&mut seen[i], true))
    }

    #[test]
    fn exact_order_matches_brute_force() {
        for seed in 0..20 {
            let n = 1 + (seed as usize % 7);
            let s = scattered(n + 1, seed);
            let (from, rest) = (s[0], &s[1..]);
            let order = best_order(Some(from), rest);
            assert!(is_permutation(&order, n), "{order:?}");
            let got = path_length_m(Some(from), rest, &order);
            let want = brute_force(from, rest);
            assert!((got - want).abs() < 0.01, "seed {seed}: {got} vs {want}");
        }
    }

    #[test]
    fn already_short_order_is_kept_and_reversed_line_is_fixed() {
        // 북쪽으로 한 줄. 남쪽 끝에서 출발하면 그대로, 북쪽 끝에서 출발하면 거꾸로.
        let line: Vec<Point> = (1..=5).map(|i| Point { lat: 37.0 + i as f64 * 0.01, lon: 127.0 }).collect();
        assert_eq!(best_order(Some(Point { lat: 37.0, lon: 127.0 }), &line), vec![0, 1, 2, 3, 4]);
        assert_eq!(best_order(Some(Point { lat: 37.1, lon: 127.0 }), &line), vec![4, 3, 2, 1, 0]);
        // 출발점이 없으면 첫 곳은 그대로 두고 나머지만 바꾼다
        let mut shuffled = line.clone();
        shuffled.swap(1, 4);
        assert_eq!(best_order(None, &shuffled), vec![0, 4, 2, 3, 1]);
        assert!(best_order(None, &[]).is_empty());
    }

    #[test]
    fn many_stops_use_the_heuristic_and_stay_close_to_optimal() {
        let s = scattered(31, 99);
        let (from, rest) = (s[0], &s[1..]);
        let order = best_order(Some(from), rest);
        assert!(is_permutation(&order, 30));
        let got = path_length_m(Some(from), rest, &order);
        let nn = path_length_m(Some(from), rest, &nearest_first(from, rest));
        let given: Vec<usize> = (0..30).collect();
        assert!(got <= nn + 1e-6, "2-opt는 가까운 곳 먼저보다 길지 않다: {got} > {nn}");
        assert!(got < path_length_m(Some(from), rest, &given), "아무 순서보다 짧다");
    }
}

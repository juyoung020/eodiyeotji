//! 지하철 노선 따라가기 — 지하철 이동(`transit` 모듈)을 역을 따라 그린다.
//!
//! 폰이 지하철에서 받은 위치는 역 사이가 비어 있고(지하는 통째로 빈다) 곧은 선으로 이어진다.
//! 그래서 탄 역 · 내린 역을 찾아 그 사이 역들을 차례로 이은 길을 대신 쓴다 ([ride]).
//! 역 · 이웃 역 표는 `subway_data.rs` (OpenStreetMap, 수도권). 길은 역 사이 거리 합이 가장 짧은 것(다익스트라).
//! 역 사이는 곧은 선이라 굽은 선로와는 조금 다르다.

use std::cmp::Ordering;
use std::collections::BinaryHeap;
use std::sync::OnceLock;

use crate::geo::{haversine_m, Point};

#[path = "subway_data.rs"]
mod data;

/// 이 거리 안에 역이 있어야 그 역에서 탔다 · 내렸다고 본다.
pub(crate) const STATION_NEAR_M: f64 = 600.0;

/// 역을 따라 간 길.
#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Ride {
    /// 탄 역부터 내린 역까지 역 자리 (2개 이상)
    pub path: Vec<Point>,
    pub from: &'static str,
    pub to: &'static str,
    pub length_m: f64,
}

fn at(i: usize) -> Point {
    let (_, lat, lon) = data::STATIONS[i];
    Point { lat, lon }
}

/// 이웃 역 목록 (역 번호 → (이웃, 거리 m)). 처음 쓸 때 한 번 만든다.
fn links() -> &'static Vec<Vec<(u16, f64)>> {
    static LINKS: OnceLock<Vec<Vec<(u16, f64)>>> = OnceLock::new();
    LINKS.get_or_init(|| {
        let mut adj = vec![Vec::new(); data::STATIONS.len()];
        for &(a, b) in data::LINKS {
            let d = haversine_m(at(a as usize), at(b as usize));
            adj[a as usize].push((b, d));
            adj[b as usize].push((a, d));
        }
        adj
    })
}

/// [p]에서 [within_m] 안의 가장 가까운 역 번호.
fn nearest(p: Point, within_m: f64) -> Option<usize> {
    (0..data::STATIONS.len()).map(|i| (i, haversine_m(p, at(i)))).filter(|&(_, d)| d <= within_m).min_by(|a, b| a.1.total_cmp(&b.1)).map(|(i, _)| i)
}

/// [p]가 역 바로 앞([within_m] 안)인가.
pub(crate) fn near_station(p: Point, within_m: f64) -> bool {
    nearest(p, within_m).is_some()
}

#[derive(PartialEq)]
struct Visit(f64, usize);

impl Eq for Visit {}

impl Ord for Visit {
    fn cmp(&self, o: &Self) -> Ordering {
        o.0.total_cmp(&self.0) // 작은 거리가 먼저 나오게
    }
}

impl PartialOrd for Visit {
    fn partial_cmp(&self, o: &Self) -> Option<Ordering> {
        Some(self.cmp(o))
    }
}

/// [from] 근처 역에서 [to] 근처 역까지 역을 따라 간 길. 두 곳 중 하나라도 근처([STATION_NEAR_M])에 역이 없거나, 같은 역이거나, 이어진 길이 없으면 None.
pub(crate) fn ride(from: Point, to: Point) -> Option<Ride> {
    let (a, b) = (nearest(from, STATION_NEAR_M)?, nearest(to, STATION_NEAR_M)?);
    if a == b {
        return None;
    }
    let adj = links();
    let mut best = vec![f64::INFINITY; adj.len()];
    let mut prev = vec![usize::MAX; adj.len()];
    let mut heap = BinaryHeap::new();
    best[a] = 0.0;
    heap.push(Visit(0.0, a));
    while let Some(Visit(d, u)) = heap.pop() {
        if u == b {
            break;
        }
        if d > best[u] {
            continue;
        }
        for &(v, w) in &adj[u] {
            let (v, nd) = (v as usize, d + w);
            if nd < best[v] {
                best[v] = nd;
                prev[v] = u;
                heap.push(Visit(nd, v));
            }
        }
    }
    if !best[b].is_finite() {
        return None;
    }
    let mut order = vec![b];
    while *order.last()? != a {
        order.push(prev[*order.last()?]);
    }
    order.reverse();
    // 갈아타는 역은 같은 이름의 두 점이다 → 그대로 이어 그린다 (승강장 사이 짧은 선)
    Some(Ride { path: order.iter().map(|&i| at(i)).collect(), from: data::STATIONS[a].0, to: data::STATIONS[b].0, length_m: best[b] })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_ride_follows_the_stations_between() {
        // 쌍문역 → 서울역 (4호선): 직선 11km쯤, 역을 따라가면 조금 더 길고 역 여러 개를 지난다
        let (ssangmun, seoul) = (Point { lat: 37.6486, lon: 127.0347 }, Point { lat: 37.5541, lon: 126.9726 });
        let r = ride(ssangmun, seoul).expect("4호선으로 이어진다");
        assert_eq!((r.from, r.to), ("쌍문역", "서울역"));
        let straight = haversine_m(ssangmun, seoul);
        assert!(r.length_m > straight && r.length_m < straight * 1.5, "{} vs {straight}", r.length_m);
        assert!(r.path.len() >= 10, "{}", r.path.len());
        // 이웃 역 사이는 길어야 몇 km (급행 연결을 뺐다)
        assert!(r.path.windows(2).all(|w| haversine_m(w[0], w[1]) < 3_000.0));
    }

    #[test]
    fn no_ride_without_a_station_nearby() {
        let nowhere = Point { lat: 37.80, lon: 128.90 };
        assert!(ride(nowhere, Point { lat: 37.5541, lon: 126.9726 }).is_none());
        assert!(near_station(Point { lat: 37.6486, lon: 127.0347 }, 200.0));
        assert!(!near_station(nowhere, 200.0));
    }
}

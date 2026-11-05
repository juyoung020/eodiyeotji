//! 거리 계산과 경로 단순화.

const EARTH_RADIUS_M: f64 = 6_371_008.8;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Point {
    pub lat: f64,
    pub lon: f64,
}

/// 시각이 붙은 위치 한 점 (FusedLocation의 LocationData에 대응).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Fix {
    pub lat: f64,
    pub lon: f64,
    pub t_ms: i64,
}

impl Fix {
    pub fn point(&self) -> Point {
        Point { lat: self.lat, lon: self.lon }
    }
}

/// 두 점 사이의 대원 거리(미터).
pub fn haversine_m(a: Point, b: Point) -> f64 {
    let (la1, la2) = (a.lat.to_radians(), b.lat.to_radians());
    let dlat = la2 - la1;
    let dlon = (b.lon - a.lon).to_radians();
    let h = (dlat / 2.0).sin().powi(2) + la1.cos() * la2.cos() * (dlon / 2.0).sin().powi(2);
    2.0 * EARTH_RADIUS_M * h.sqrt().asin()
}

/// 기준점 주변을 평면(미터)으로 펼친 좌표. 수 km 범위에서는 오차가 무시할 수준이다.
fn to_local_m(origin: Point, p: Point) -> (f64, f64) {
    let k = origin.lat.to_radians().cos();
    let x = (p.lon - origin.lon).to_radians() * EARTH_RADIUS_M * k;
    let y = (p.lat - origin.lat).to_radians() * EARTH_RADIUS_M;
    (x, y)
}

/// 점 p에서 선분 ab까지의 거리(미터).
fn segment_distance_m(p: Point, a: Point, b: Point) -> f64 {
    let (px, py) = to_local_m(a, p);
    let (bx, by) = to_local_m(a, b);
    let len2 = bx * bx + by * by;
    if len2 == 0.0 {
        return (px * px + py * py).sqrt();
    }
    let t = ((px * bx + py * by) / len2).clamp(0.0, 1.0);
    let (dx, dy) = (px - t * bx, py - t * by);
    (dx * dx + dy * dy).sqrt()
}

/// Douglas-Peucker 경로 단순화. 남길 점의 인덱스를 오름차순으로 돌려준다.
///
/// 재귀 대신 스택을 써서 점이 많아도 스택 오버플로가 나지 않는다.
pub fn simplify(points: &[Point], tolerance_m: f64) -> Vec<usize> {
    let n = points.len();
    if n <= 2 {
        return (0..n).collect();
    }
    let mut keep = vec![false; n];
    keep[0] = true;
    keep[n - 1] = true;
    let mut stack = vec![(0usize, n - 1)];
    while let Some((start, end)) = stack.pop() {
        if end <= start + 1 {
            continue;
        }
        let (mut max_d, mut max_i) = (0.0, start);
        for i in start + 1..end {
            let d = segment_distance_m(points[i], points[start], points[end]);
            if d > max_d {
                max_d = d;
                max_i = i;
            }
        }
        if max_d > tolerance_m {
            keep[max_i] = true;
            stack.push((start, max_i));
            stack.push((max_i, end));
        }
    }
    (0..n).filter(|&i| keep[i]).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn p(lat: f64, lon: f64) -> Point {
        Point { lat, lon }
    }

    #[test]
    fn haversine_seoul_to_busan_is_about_325km() {
        let d = haversine_m(p(37.5665, 126.9780), p(35.1796, 129.0756));
        assert!((d - 325_000.0).abs() < 2_000.0, "got {d}");
    }

    #[test]
    fn haversine_same_point_is_zero() {
        assert_eq!(haversine_m(p(37.0, 127.0), p(37.0, 127.0)), 0.0);
    }

    #[test]
    fn simplify_straight_line_keeps_only_endpoints() {
        let line: Vec<Point> = (0..1000).map(|i| p(37.0 + i as f64 * 1e-5, 127.0)).collect();
        assert_eq!(simplify(&line, 5.0), vec![0, 999]);
    }

    #[test]
    fn simplify_keeps_the_corner_of_an_l_shape() {
        let mut pts: Vec<Point> = (0..=100).map(|i| p(37.0 + i as f64 * 1e-5, 127.0)).collect();
        pts.extend((1..=100).map(|i| p(37.001, 127.0 + i as f64 * 1e-5)));
        let kept = simplify(&pts, 5.0);
        assert_eq!(kept, vec![0, 100, 200]);
    }

    #[test]
    fn simplify_handles_tiny_inputs() {
        assert!(simplify(&[], 5.0).is_empty());
        assert_eq!(simplify(&[p(1.0, 1.0)], 5.0), vec![0]);
    }
}

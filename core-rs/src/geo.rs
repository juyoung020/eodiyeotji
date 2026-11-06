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

/// 혼자 튀는 점(GPS 점프)을 뺀 점 목록을 돌려준다. fixes는 시간순(같은 시각 없음)이어야 한다.
///
/// - 가운데 점: 앞(남긴 점)→이 점, 이 점→다음 점이 둘 다 `max_speed_mps`보다 빠른데,
///   앞→다음은 그보다 느리면 이 점만 튄 것이다.
/// - 첫 점: 첫 점→둘째가 너무 빠르고 둘째→셋째는 정상이면 첫 점이 튄 것이다 (GPS 막 켜졌을 때 흔함).
/// - 마지막 점: 그 앞 두 점은 정상인데 마지막 한 칸만 너무 빠르면 뺀다.
///
/// 점이 2개 이하이면 판단할 근거가 없어 그대로 둔다. 두 점 이상이 함께 튀면 걸러내지 않는다.
pub fn drop_spikes(fixes: &[Fix], max_speed_mps: f64) -> Vec<Fix> {
    let n = fixes.len();
    if n < 3 {
        return fixes.to_vec();
    }
    let fast = |a: &Fix, b: &Fix| {
        let dt_s = (b.t_ms - a.t_ms) as f64 / 1000.0;
        haversine_m(a.point(), b.point()) > max_speed_mps * dt_s
    };
    let mut out: Vec<Fix> = Vec::with_capacity(n);
    for (i, f) in fixes.iter().enumerate() {
        let next = fixes.get(i + 1);
        let spike = match (out.last(), next) {
            (Some(p), Some(nx)) => fast(p, f) && fast(f, nx) && !fast(p, nx),
            (None, Some(nx)) => fast(f, nx) && fixes.get(i + 2).is_some_and(|n2| !fast(nx, n2)),
            (Some(p), None) => out.len() >= 2 && fast(p, f) && !fast(&out[out.len() - 2], p),
            (None, None) => false,
        };
        if !spike {
            out.push(*f);
        }
    }
    out
}

/// 점 [p]에서 꺾은선 [line]까지 가장 가까운 거리(미터). 선이 비었으면 무한대, 점 하나면 그 점까지.
pub fn nearest_distance_m(p: Point, line: &[Point]) -> f64 {
    match line {
        [] => f64::INFINITY,
        [a] => haversine_m(p, *a),
        _ => line.windows(2).map(|w| segment_distance_m(p, w[0], w[1])).fold(f64::INFINITY, f64::min),
    }
}

/// 꺾은선 길이(미터).
pub fn line_length_m(line: &[Point]) -> f64 {
    line.windows(2).map(|w| haversine_m(w[0], w[1])).sum()
}

/// a에서 b를 보는 방위(도, 북 0 · 동 90, 0 이상 360 미만).
pub fn bearing_deg(a: Point, b: Point) -> f64 {
    let (la1, la2) = (a.lat.to_radians(), b.lat.to_radians());
    let dlon = (b.lon - a.lon).to_radians();
    let y = dlon.sin() * la2.cos();
    let x = la1.cos() * la2.sin() - la1.sin() * la2.cos() * dlon.cos();
    y.atan2(x).to_degrees().rem_euclid(360.0)
}

/// 시간순 점들의 이동 거리 합(미터).
pub fn path_length_m(fixes: &[Fix]) -> f64 {
    fixes.windows(2).map(|w| haversine_m(w[0].point(), w[1].point())).sum()
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

    /// 1초 간격, 북쪽으로 초속 약 11m(시속 40km)로 달리는 점 n개.
    fn drive(n: usize) -> Vec<Fix> {
        (0..n).map(|i| Fix { lat: 37.0 + i as f64 * 1e-4, lon: 127.0, t_ms: i as i64 * 1000 }).collect()
    }

    #[test]
    fn drop_spikes_removes_a_single_jump_in_the_middle() {
        let mut fixes = drive(10);
        fixes[5].lon += 0.5; // 약 44km 옆으로 튐
        let kept = drop_spikes(&fixes, 70.0);
        assert_eq!(kept.len(), 9);
        assert!(kept.iter().all(|f| f.lon == 127.0));
    }

    #[test]
    fn drop_spikes_removes_a_bad_first_and_last_point() {
        let mut fixes = drive(10);
        fixes[0].lat -= 0.3;
        fixes[9].lat += 0.3;
        let kept = drop_spikes(&fixes, 70.0);
        assert_eq!(kept.iter().map(|f| f.t_ms).collect::<Vec<_>>(), (1..9).map(|i| i * 1000).collect::<Vec<_>>());
    }

    #[test]
    fn drop_spikes_keeps_normal_and_fast_driving() {
        let fixes = drive(50);
        assert_eq!(drop_spikes(&fixes, 70.0), fixes);
        // 시속 200km(초속 약 55m)도 정상 주행으로 남긴다
        let fast: Vec<Fix> = (0..20).map(|i| Fix { lat: 37.0 + i as f64 * 5e-4, lon: 127.0, t_ms: i * 1000 }).collect();
        assert_eq!(drop_spikes(&fast, 70.0).len(), 20);
        assert_eq!(drop_spikes(&fixes[..2], 70.0).len(), 2);
    }

    #[test]
    fn path_length_sums_segments() {
        let d = path_length_m(&drive(11));
        assert!((d - haversine_m(p(37.0, 127.0), p(37.001, 127.0))).abs() < 0.01, "{d}");
        assert_eq!(path_length_m(&[]), 0.0);
    }

    #[test]
    fn nearest_distance_is_to_the_closest_segment_not_vertex() {
        let line = [p(37.0, 127.0), p(37.0, 127.1)];
        // 선분 가운데 바로 북쪽 0.001도(약 111m)
        let d = nearest_distance_m(p(37.001, 127.05), &line);
        assert!((d - 111.2).abs() < 1.0, "{d}");
        assert!(nearest_distance_m(p(37.0, 127.0), &[]).is_infinite());
        assert_eq!(nearest_distance_m(p(37.0, 127.0), &line[..1]), 0.0);
    }

    #[test]
    fn bearing_points_north_east_south_west() {
        let o = p(37.0, 127.0);
        for (q, want) in [(p(37.1, 127.0), 0.0), (p(37.0, 127.1), 90.0), (p(36.9, 127.0), 180.0), (p(37.0, 126.9), 270.0)] {
            let b = bearing_deg(o, q);
            assert!((b - want).abs() < 0.1, "{b} vs {want}");
        }
        assert!((line_length_m(&[o, p(37.1, 127.0)]) - haversine_m(o, p(37.1, 127.0))).abs() < 1e-6);
    }

    #[test]
    fn simplify_handles_tiny_inputs() {
        assert!(simplify(&[], 5.0).is_empty());
        assert_eq!(simplify(&[p(1.0, 1.0)], 5.0), vec![0]);
    }
}

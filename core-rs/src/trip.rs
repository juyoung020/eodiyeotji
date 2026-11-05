//! 위치 기록을 주행(trip)과 머문 곳(stay)으로 나눈다.
//!
//! - 시동이 꺼져 있으면 위치가 안 들어오므로, 위치 사이 공백이 길면 주행이 끊긴 것으로 본다.
//! - 주행 중이라도 반경 안에 오래 머물면(드라이브스루, 픽업 등) 머문 곳으로 본다.

use crate::geo::{haversine_m, Fix, Point};

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TripConfig {
    /// 위치 공백이 이 시간 이상이면 주행을 끊는다.
    pub max_gap_ms: i64,
    /// 머문 곳 판정 반경.
    pub stay_radius_m: f64,
    /// 이 시간 이상 반경 안에 있으면 머문 곳.
    pub stay_min_ms: i64,
}

impl Default for TripConfig {
    fn default() -> Self {
        Self { max_gap_ms: 5 * 60_000, stay_radius_m: 150.0, stay_min_ms: 10 * 60_000 }
    }
}

/// 주행 하나: fixes[start..=end].
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TripRange {
    pub start: usize,
    pub end: usize,
}

/// 머문 곳 하나.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Stay {
    pub center: Point,
    pub arrive_ms: i64,
    pub leave_ms: i64,
}

/// 위치 공백을 기준으로 주행을 나눈다. fixes는 시간순이어야 한다.
pub fn split_trips(fixes: &[Fix], cfg: &TripConfig) -> Vec<TripRange> {
    let mut out = Vec::new();
    if fixes.is_empty() {
        return out;
    }
    let mut start = 0;
    for i in 1..fixes.len() {
        if fixes[i].t_ms - fixes[i - 1].t_ms >= cfg.max_gap_ms {
            out.push(TripRange { start, end: i - 1 });
            start = i;
        }
    }
    out.push(TripRange { start, end: fixes.len() - 1 });
    out
}

/// 머문 곳 감지 (stay-point detection, Li et al. 2008 방식).
///
/// 기준점 i에서 반경 안에 있는 동안 j를 늘리고, 반경을 벗어난 시점까지 머문 시간이
/// 기준 이상이면 그 구간을 머문 곳으로 기록한 뒤 j부터 다시 찾는다.
pub fn detect_stays(fixes: &[Fix], cfg: &TripConfig) -> Vec<Stay> {
    let mut out = Vec::new();
    let n = fixes.len();
    let mut i = 0;
    while i < n {
        let anchor = fixes[i].point();
        let mut j = i + 1;
        while j < n && haversine_m(anchor, fixes[j].point()) <= cfg.stay_radius_m {
            j += 1;
        }
        let last = j - 1;
        if fixes[last].t_ms - fixes[i].t_ms >= cfg.stay_min_ms {
            let cluster = &fixes[i..=last];
            let k = cluster.len() as f64;
            let center = Point {
                lat: cluster.iter().map(|f| f.lat).sum::<f64>() / k,
                lon: cluster.iter().map(|f| f.lon).sum::<f64>() / k,
            };
            out.push(Stay { center, arrive_ms: fixes[i].t_ms, leave_ms: fixes[last].t_ms });
            i = j;
        } else {
            i += 1;
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const MIN: i64 = 60_000;

    fn fix(lat: f64, lon: f64, t_min: i64) -> Fix {
        Fix { lat, lon, t_ms: t_min * MIN }
    }

    /// 1분 간격으로 북쪽으로 약 1.1km씩 이동하는 주행.
    fn driving(from_min: i64, count: i64, lat0: f64) -> Vec<Fix> {
        (0..count).map(|k| fix(lat0 + k as f64 * 0.01, 127.0, from_min + k)).collect()
    }

    #[test]
    fn gap_longer_than_threshold_splits_trips() {
        let mut fixes = driving(0, 10, 37.0);
        fixes.extend(driving(60, 10, 38.0)); // 51분 공백 → 시동 꺼짐
        let trips = split_trips(&fixes, &TripConfig::default());
        assert_eq!(trips, vec![TripRange { start: 0, end: 9 }, TripRange { start: 10, end: 19 }]);
    }

    #[test]
    fn short_gap_does_not_split() {
        let mut fixes = driving(0, 5, 37.0);
        fixes.extend(driving(7, 5, 37.05)); // 3분 공백 (터널 등)
        assert_eq!(split_trips(&fixes, &TripConfig::default()).len(), 1);
    }

    #[test]
    fn empty_input_has_no_trips_or_stays() {
        assert!(split_trips(&[], &TripConfig::default()).is_empty());
        assert!(detect_stays(&[], &TripConfig::default()).is_empty());
    }

    #[test]
    fn detects_a_20_minute_stop_between_drives() {
        let mut fixes = driving(0, 5, 37.0);
        // 37.05 근처에서 20분 동안 조금씩 움직임 (반경 150m 안)
        fixes.extend((5..=25).map(|t| fix(37.05 + (t % 3) as f64 * 0.0003, 127.0, t)));
        fixes.extend(driving(26, 5, 37.06));
        let stays = detect_stays(&fixes, &TripConfig::default());
        assert_eq!(stays.len(), 1, "{stays:?}");
        let s = stays[0];
        assert_eq!((s.arrive_ms, s.leave_ms), (5 * MIN, 25 * MIN));
        assert!(haversine_m(s.center, Point { lat: 37.0503, lon: 127.0 }) < 100.0);
    }

    #[test]
    fn red_light_is_not_a_stay() {
        let mut fixes = driving(0, 5, 37.0);
        fixes.extend((5..=7).map(|t| fix(37.05, 127.0, t))); // 2분 정지
        fixes.extend(driving(8, 5, 37.06));
        assert!(detect_stays(&fixes, &TripConfig::default()).is_empty());
    }
}

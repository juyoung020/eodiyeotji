//! 위치 기록을 주행(trip)과 머문 곳(stay)으로 나눈다.
//!
//! - 시동이 꺼져 있으면 위치가 안 들어오므로, 위치 사이 공백이 길면 주행이 끊긴 것으로 본다.
//! - 시동·기어 신호(주행 시작·끝)가 있으면 그 시각에서도 끊는다 → [`is_cut`].
//! - 주차 중에는 위치가 안 들어오므로, 주차는 "주행과 주행 사이의 공백"으로 나타난다 → [`between_stay`].
//! - 주행 중이라도 반경 안에 오래 머물면(드라이브스루, 픽업 등) 머문 곳으로 본다 → [`detect_stays`].

use crate::geo::{haversine_m, Fix, Point};

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TripConfig {
    /// 위치 공백이 이 시간 이상이면 주행을 끊는다.
    pub max_gap_ms: i64,
    /// 머문 곳 판정 반경.
    pub stay_radius_m: f64,
    /// 이 시간 이상 반경 안에 있으면 머문 곳. 주행 사이 공백도 이 시간 이상이어야 머문 곳.
    pub stay_min_ms: i64,
    /// 앞 주행 끝과 다음 주행 시작이 이 거리 안이면 그 자리에 주차해 있던 것으로 본다.
    pub park_radius_m: f64,
    /// 이보다 빠르게 움직인 것처럼 보이는 점은 GPS 점프로 본다 (초속 70m = 시속 252km).
    pub max_speed_mps: f64,
}

impl Default for TripConfig {
    fn default() -> Self {
        Self {
            max_gap_ms: 5 * 60_000,
            stay_radius_m: 150.0,
            stay_min_ms: 10 * 60_000,
            park_radius_m: 300.0,
            max_speed_mps: 70.0,
        }
    }
}

/// 주행 시작·끝 신호 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MarkKind {
    Start,
    End,
}

/// 주행 시작·끝 신호 하나 (시동 또는 기어 P↔D).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DriveMark {
    pub t_ms: i64,
    pub kind: MarkKind,
}

/// 시간순으로 이웃한 두 점 a, b(a < b) 사이에서 주행을 끊어야 하는지.
///
/// - 공백이 `max_gap_ms` 이상이면 끊는다.
/// - 시작 신호 s가 a < s ≤ b 이면 끊는다 (s 시각의 점부터 새 주행).
/// - 끝 신호 e가 a ≤ e < b 이면 끊는다 (e 시각의 점까지 앞 주행).
///
/// `marks`는 시각 오름차순이어야 한다.
pub fn is_cut(a_ms: i64, b_ms: i64, marks: &[DriveMark], cfg: &TripConfig) -> bool {
    if b_ms - a_ms >= cfg.max_gap_ms {
        return true;
    }
    let from = marks.partition_point(|m| m.t_ms < a_ms);
    marks[from..].iter().take_while(|m| m.t_ms <= b_ms).any(|m| match m.kind {
        MarkKind::Start => m.t_ms > a_ms,
        MarkKind::End => m.t_ms < b_ms,
    })
}

/// `first_ms`에서 시작하는 구간이 "주행 끝 신호 직후의 주차 중 점"인지.
///
/// 기록기는 기어가 P로 돌아간 뒤 잠깐(흔들림 방지 대기) 위치를 더 받을 수 있다. 그 점들은 주행이 아니다.
/// 조건: 구간 시작 직전 신호가 끝 신호이고(그 뒤 시작 신호 없음), 끝 신호와의 간격이 `max_gap_ms` 미만.
/// 끝 신호 뒤 한참 지나 다시 움직이면(시작 신호를 못 받은 경우 등) 새 주행으로 본다.
/// `marks`는 시각 오름차순이어야 한다.
pub fn starts_parked(first_ms: i64, marks: &[DriveMark], cfg: &TripConfig) -> bool {
    let i = marks.partition_point(|m| m.t_ms <= first_ms);
    i > 0 && {
        let m = marks[i - 1];
        m.kind == MarkKind::End && m.t_ms < first_ms && first_ms - m.t_ms < cfg.max_gap_ms
    }
}

/// 점을 시간순으로 하나씩 받아 주행 구간으로 끊어 내는 흐름식 분할기.
///
/// 점을 한꺼번에 메모리에 올리지 않고 DB에서 읽으면서 바로 나눌 때 쓴다.
/// 결과는 [`split_trips_with_marks`]와 같다.
pub struct Segmenter<'a> {
    marks: &'a [DriveMark],
    cfg: TripConfig,
    cur: Vec<Fix>,
}

impl<'a> Segmenter<'a> {
    pub fn new(marks: &'a [DriveMark], cfg: TripConfig) -> Self {
        Self { marks, cfg, cur: Vec::new() }
    }

    /// 다음 점(앞 점보다 늦은 시각)을 넣는다. 여기서 구간이 끊기면 끝난 구간을 돌려준다.
    pub fn push(&mut self, f: Fix) -> Option<Vec<Fix>> {
        let cut = self.cur.last().is_some_and(|last| is_cut(last.t_ms, f.t_ms, self.marks, &self.cfg));
        if cut {
            Some(std::mem::replace(&mut self.cur, vec![f]))
        } else {
            self.cur.push(f);
            None
        }
    }

    /// 남은 마지막 구간.
    pub fn finish(self) -> Option<Vec<Fix>> {
        (!self.cur.is_empty()).then_some(self.cur)
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

/// 위치 공백과 주행 시작·끝 신호([`is_cut`])로 주행을 나눈다. fixes는 시간순, marks는 시각순이어야 한다.
pub fn split_trips_with_marks(fixes: &[Fix], marks: &[DriveMark], cfg: &TripConfig) -> Vec<TripRange> {
    let mut out = Vec::new();
    if fixes.is_empty() {
        return out;
    }
    let mut start = 0;
    for i in 1..fixes.len() {
        if is_cut(fixes[i - 1].t_ms, fixes[i].t_ms, marks, cfg) {
            out.push(TripRange { start, end: i - 1 });
            start = i;
        }
    }
    out.push(TripRange { start, end: fixes.len() - 1 });
    out
}

/// 주행과 주행 사이의 머문 곳(주차).
///
/// 앞 주행이 끝난 자리 `prev_end`(도착 `arrive_ms`)와 다음 주행이 시작한 자리 `next_start`(출발 `leave_ms`)가
/// `park_radius_m` 안이고 공백이 `stay_min_ms` 이상이면, 도착~출발 동안 앞 주행 끝 자리에 머문 것이다.
/// 멀리서 다시 출발했으면(기록이 빠진 이동) 어디 있었는지 모르므로 머문 곳으로 치지 않는다.
pub fn between_stay(prev_end: Point, arrive_ms: i64, next_start: Point, leave_ms: i64, cfg: &TripConfig) -> Option<Stay> {
    let close = haversine_m(prev_end, next_start) <= cfg.park_radius_m;
    (close && leave_ms - arrive_ms >= cfg.stay_min_ms).then_some(Stay { center: prev_end, arrive_ms, leave_ms })
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
    fn starts_parked_only_between_end_and_next_start() {
        let marks = [
            DriveMark { t_ms: 10, kind: MarkKind::Start },
            DriveMark { t_ms: 20, kind: MarkKind::End },
            DriveMark { t_ms: 30, kind: MarkKind::Start },
        ];
        let c = TripConfig::default();
        assert!(!starts_parked(5, &marks, &c), "신호 전");
        assert!(!starts_parked(15, &marks, &c), "주행 중");
        assert!(!starts_parked(20, &marks, &c), "끝 신호 시각의 점은 앞 주행");
        assert!(starts_parked(25, &marks, &c), "끝 직후 · 다음 시작 전");
        assert!(!starts_parked(30, &marks, &c), "시작 신호 시각부터 새 주행");
        assert!(!starts_parked(25, &[], &c), "신호가 없으면 주차로 보지 않는다");
        let end_only = [DriveMark { t_ms: 0, kind: MarkKind::End }];
        assert!(!starts_parked(c.max_gap_ms, &end_only, &c), "끝 신호 뒤 한참 지나 움직이면 새 주행");
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

    fn start(t_min: i64) -> DriveMark {
        DriveMark { t_ms: t_min * MIN, kind: MarkKind::Start }
    }

    fn end(t_min: i64) -> DriveMark {
        DriveMark { t_ms: t_min * MIN, kind: MarkKind::End }
    }

    #[test]
    fn marks_split_continuous_fixes() {
        let fixes = driving(0, 10, 37.0); // 1분 간격, 공백 없음
        let cfg = TripConfig::default();
        // 4분 점까지 앞 주행(끝 신호가 점과 같은 시각), 6분 점부터 새 주행(시작 신호가 점과 같은 시각)
        let trips = split_trips_with_marks(&fixes, &[end(4), start(6)], &cfg);
        assert_eq!(
            trips,
            vec![TripRange { start: 0, end: 4 }, TripRange { start: 5, end: 5 }, TripRange { start: 6, end: 9 }]
        );
        // 점 사이 시각의 신호도 그 사이에서 끊는다
        let mid = DriveMark { t_ms: 2 * MIN + 30_000, kind: MarkKind::Start };
        let trips = split_trips_with_marks(&fixes, &[mid], &cfg);
        assert_eq!(trips, vec![TripRange { start: 0, end: 2 }, TripRange { start: 3, end: 9 }]);
    }

    #[test]
    fn marks_at_the_edges_do_not_add_cuts() {
        let fixes = driving(0, 10, 37.0);
        let cfg = TripConfig::default();
        // 첫 점의 시작 신호, 마지막 점의 끝 신호는 주행 하나 그대로
        assert_eq!(split_trips_with_marks(&fixes, &[start(0), end(9)], &cfg).len(), 1);
        // 신호가 없으면 split_trips와 같다
        let mut gappy = driving(0, 10, 37.0);
        gappy.extend(driving(60, 10, 38.0));
        assert_eq!(split_trips_with_marks(&gappy, &[], &cfg), split_trips(&gappy, &cfg));
    }

    #[test]
    fn segmenter_matches_split_trips_with_marks() {
        let mut fixes = driving(0, 10, 37.0);
        fixes.extend(driving(30, 10, 38.0));
        let marks = [end(4), start(5), start(35)];
        let cfg = TripConfig::default();
        let mut seg = Segmenter::new(&marks, cfg);
        let mut got = Vec::new();
        for f in &fixes {
            got.extend(seg.push(*f).map(|s| s.len()));
        }
        got.extend(seg.finish().map(|s| s.len()));
        let want: Vec<usize> =
            split_trips_with_marks(&fixes, &marks, &cfg).iter().map(|r| r.end - r.start + 1).collect();
        assert_eq!(got, want);
        assert_eq!(want, vec![5, 5, 5, 5]);
    }

    #[test]
    fn between_stay_needs_same_place_and_ten_minutes() {
        let cfg = TripConfig::default();
        let here = Point { lat: 37.0, lon: 127.0 };
        let near = Point { lat: 37.001, lon: 127.0 }; // 약 111m
        let far = Point { lat: 37.01, lon: 127.0 }; // 약 1.1km
        let s = between_stay(here, 0, near, 60 * MIN, &cfg).expect("주차");
        assert_eq!((s.center, s.arrive_ms, s.leave_ms), (here, 0, 60 * MIN));
        assert!(between_stay(here, 0, far, 60 * MIN, &cfg).is_none());
        assert!(between_stay(here, 0, near, 9 * MIN, &cfg).is_none());
    }

    #[test]
    fn red_light_is_not_a_stay() {
        let mut fixes = driving(0, 5, 37.0);
        fixes.extend((5..=7).map(|t| fix(37.05, 127.0, t))); // 2분 정지
        fixes.extend(driving(8, 5, 37.06));
        assert!(detect_stays(&fixes, &TripConfig::default()).is_empty());
    }
}

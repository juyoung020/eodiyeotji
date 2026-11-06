//! 폰 주행 감지 — 차 신호(기어·시동)가 없는 폰에서 위치만 보고 "지금 운전 중인가"를 정한다.
//!
//! 규칙 (값은 [DetectorConfig]):
//! - **시작:** 시속 15km 이상이 20초 넘게 이어지고(빠른 점 3개 이상), 출발점에서 200m 넘게 멀어지면 주행으로 본다.
//!   주행 시작 시각은 빨라지기 직전 마지막 점(출발한 자리)으로 거슬러 잡고, 그사이 점들은 함께 돌려준다([DriveEdgeOut::backlog]).
//! - **끝:** 반경 80m 안에서 4분 넘게 머물면 끝. 끝 시각은 그 자리에 처음 선 시각이다.
//!   위치가 10분 넘게 안 들어오면(지하 주차장 등) 마지막 위치 시각으로 끝낸다.
//! - **흔들림:** 정확도가 100m보다 나쁜 점은 버리고, 속도는 10초 넘게 떨어진 두 점 사이 거리로 잰다
//!   (두 점의 정확도 합보다 덜 움직였으면 0). 기기가 잰 속도(GPS)가 있으면 둘 중 큰 값. 시속 250km를 넘는 점은 튄 점이라 버린다.
//! - **배터리:** 서 있을 때는 저전력(드물게) 위치만 받는다. 빠른 점이 보이거나 처음 자리에서 250m 넘게 멀어지면
//!   2분 동안 고정밀 위치를 켜서 확인한다([DriveDetectOut::high_rate]). 주행 중에는 늘 고정밀.
//!
//! Kotlin 입구는 `ffi::DriveDetector`. 계산은 모두 여기서 한다. Kotlin은 위치를 묶어 넘기고, 돌려받은 high_rate에 맞춰 위치 요청만 바꾼다.

use std::collections::VecDeque;

use crate::geo::{haversine_m, Point};
use crate::store::GeoFix;

/// 감지 기준값. 테스트에서 바꿔 볼 수 있게 묶어 둔다.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct DetectorConfig {
    pub start_speed_mps: f64,
    pub start_sustain_ms: i64,
    pub start_min_fast: u32,
    pub start_min_dist_m: f64,
    pub stop_speed_mps: f64,
    pub stop_radius_m: f64,
    pub stop_dwell_ms: i64,
    pub lost_signal_ms: i64,
    pub max_accuracy_m: f64,
    pub max_speed_mps: f64,
    pub speed_baseline_ms: i64,
    pub probe_move_m: f64,
    pub probe_ms: i64,
    pub backlog_window_ms: i64,
}

impl Default for DetectorConfig {
    fn default() -> Self {
        Self {
            start_speed_mps: 15.0 / 3.6,
            start_sustain_ms: 20_000,
            start_min_fast: 3,
            start_min_dist_m: 200.0,
            stop_speed_mps: 7.0 / 3.6,
            stop_radius_m: 80.0,
            stop_dwell_ms: 4 * 60_000,
            lost_signal_ms: 10 * 60_000,
            max_accuracy_m: 100.0,
            max_speed_mps: 250.0 / 3.6,
            speed_baseline_ms: 10_000,
            probe_move_m: 250.0,
            probe_ms: 2 * 60_000,
            backlog_window_ms: 15 * 60_000,
        }
    }
}

/// 폰이 준 위치 한 점. [speed_mps]는 기기가 잰 속도(GPS 도플러)가 있을 때만.
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct MotionFix {
    pub lat: f64,
    pub lon: f64,
    pub t_ms: i64,
    /// 위치 정확도(m). 모르면 0.
    pub accuracy_m: f32,
    pub speed_mps: Option<f32>,
}

/// 주행 시작·끝 한 번.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DriveEdgeOut {
    pub active: bool,
    pub t_ms: i64,
    /// 시작일 때: 출발 시각부터 감지 순간까지 받은 점 (기록 앞부분을 채운다). 끝일 때는 빈 목록.
    pub backlog: Vec<GeoFix>,
}

/// `ffi::DriveDetector`의 push·tick 결과.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DriveDetectOut {
    pub driving: bool,
    /// 고정밀 위치(1초 간격)가 필요한지. false면 저전력 위치로 충분하다.
    pub high_rate: bool,
    /// 이번 호출에서 바뀐 순간들 (보통 0개, 드물게 1개).
    pub edges: Vec<DriveEdgeOut>,
}

/// "같은 자리"로 볼 때 두 점 정확도 합에 더하는 여유(m).
const SAME_SPOT_SLACK_M: f64 = 10.0;

#[derive(Debug, Clone, Copy, PartialEq)]
struct Sample {
    p: Point,
    t_ms: i64,
    acc: f64,
}

impl Sample {
    fn dist(&self, o: &Sample) -> f64 {
        haversine_m(self.p, o.p)
    }
}

/// 상태 기계 (UniFFI 밖의 순수 로직).
#[derive(Debug, Clone)]
pub struct Detector {
    cfg: DetectorConfig,
    driving: bool,
    /// 속도 계산용 최근 점 (speed_baseline_ms의 몇 배만 둔다).
    recent: VecDeque<Sample>,
    last_t: Option<i64>,
    // ---- 서 있을 때 ----
    /// 빨라지기 직전 마지막 점 (출발한 자리·시각).
    departure: Option<Sample>,
    streak_first: Option<i64>,
    streak_fast: u32,
    /// 마지막으로 서 있던 자리 (저전력 위치로 "움직였나"를 본다).
    idle_anchor: Option<Sample>,
    /// 느린 점들이 모여 있는 지금 자리 (멀어지면 새 자리로 옮긴다). 출발 시각을 거슬러 잡는 기준.
    rest: Option<Sample>,
    /// 새 자리 후보: 여기 10초 넘게 머물면 [rest]를 옮긴다 (느리게 출발하는 동안 rest가 따라가지 않게).
    rest_candidate: Option<Sample>,
    probe_until: Option<i64>,
    backlog: VecDeque<GeoFix>,
    // ---- 주행 중 ----
    stop_anchor: Option<Sample>,
}

impl Default for Detector {
    fn default() -> Self {
        Self::new(DetectorConfig::default())
    }
}

impl Detector {
    pub fn new(cfg: DetectorConfig) -> Self {
        Self {
            cfg,
            driving: false,
            recent: VecDeque::new(),
            last_t: None,
            departure: None,
            streak_first: None,
            streak_fast: 0,
            idle_anchor: None,
            rest: None,
            rest_candidate: None,
            probe_until: None,
            backlog: VecDeque::new(),
            stop_anchor: None,
        }
    }

    /// 앱이 주행 기록 중에 죽었다 다시 뜬 경우: 주행 중으로 이어 간다 ([last_fix_ms] = 마지막으로 저장한 점 시각).
    pub fn resume_driving(&mut self, last_fix_ms: i64) {
        self.driving = true;
        self.last_t = Some(last_fix_ms);
        self.stop_anchor = None;
        self.reset_idle();
    }

    pub fn is_driving(&self) -> bool {
        self.driving
    }

    pub fn high_rate(&self, now_ms: i64) -> bool {
        self.driving || self.probe_until.is_some_and(|u| now_ms < u)
    }

    /// 점 하나. 바뀐 순간이 있으면 돌려준다.
    pub fn push(&mut self, f: &MotionFix) -> Option<DriveEdgeOut> {
        let acc = f64::from(f.accuracy_m.max(0.0));
        if !f.lat.is_finite() || !f.lon.is_finite() || acc > self.cfg.max_accuracy_m {
            return self.tick(f.t_ms);
        }
        if self.last_t.is_some_and(|t| f.t_ms <= t) {
            return None; // 같은 점이 두 번 왔거나 순서가 뒤바뀜
        }
        let s = Sample { p: Point { lat: f.lat, lon: f.lon }, t_ms: f.t_ms, acc };
        let speed = self.estimate_speed(&s, f.speed_mps)?; // None = 튄 점
        self.last_t = Some(s.t_ms);
        self.recent.push_back(s);
        let keep_from = s.t_ms - self.cfg.speed_baseline_ms * 6;
        while self.recent.front().is_some_and(|r| r.t_ms < keep_from) {
            self.recent.pop_front();
        }
        if self.driving {
            self.on_driving(s, speed)
        } else {
            self.on_idle(s, speed, f)
        }
    }

    /// 시간만 흐름 (위치가 안 들어올 때 끝을 알기 위해, Kotlin이 주기적으로 부른다).
    pub fn tick(&mut self, now_ms: i64) -> Option<DriveEdgeOut> {
        if self.driving {
            if let Some(a) = self.stop_anchor {
                if now_ms - a.t_ms >= self.cfg.stop_dwell_ms {
                    return Some(self.end(a.t_ms, Some(a)));
                }
            }
            if let Some(t) = self.last_t {
                if now_ms - t >= self.cfg.lost_signal_ms {
                    return Some(self.end(t, None));
                }
            }
        } else if self.probe_until.is_some_and(|u| now_ms >= u) {
            self.expire_probe();
        }
        None
    }

    /// 속도(m/s). 튄 점이면 None.
    fn estimate_speed(&self, s: &Sample, reported: Option<f32>) -> Option<f64> {
        if let Some(prev) = self.recent.back() {
            let dt = (s.t_ms - prev.t_ms) as f64 / 1000.0;
            let d = s.dist(prev) - (s.acc + prev.acc);
            if dt > 0.0 && d / dt > self.cfg.max_speed_mps {
                return None;
            }
        }
        let reported = reported.map(f64::from).filter(|v| v.is_finite() && *v >= 0.0).map(|v| v.min(self.cfg.max_speed_mps));
        // 거리로 잰 속도와 기기 속도 중 큰 값 (기기가 속도를 0으로만 주는 경우·에뮬레이터 대비)
        Some(self.displacement_speed(s).max(reported.unwrap_or(0.0)))
    }

    /// 10초 넘게 떨어진 점과의 거리로 잰 속도 (두 점 정확도 합보다 덜 움직였으면 0).
    fn displacement_speed(&self, s: &Sample) -> f64 {
        // 기준점: speed_baseline_ms 이상 지난 점 중 가장 최근 것. 없으면 가장 오래된 점.
        let base = self
            .recent
            .iter()
            .rev()
            .find(|r| s.t_ms - r.t_ms >= self.cfg.speed_baseline_ms)
            .or_else(|| self.recent.front());
        let Some(b) = base else { return 0.0 };
        let dt = (s.t_ms - b.t_ms) as f64 / 1000.0;
        let moved = s.dist(b) - (s.acc + b.acc);
        if dt <= 0.0 || moved <= 0.0 {
            0.0
        } else {
            moved / dt
        }
    }

    fn on_idle(&mut self, s: Sample, speed: f64, f: &MotionFix) -> Option<DriveEdgeOut> {
        self.backlog.push_back(GeoFix { lat: f.lat, lon: f.lon, t_ms: f.t_ms, accuracy_m: f.accuracy_m });
        let keep_from = s.t_ms - self.cfg.backlog_window_ms;
        while self.backlog.front().is_some_and(|b| b.t_ms < keep_from) {
            self.backlog.pop_front();
        }

        if speed >= self.cfg.start_speed_mps {
            if self.streak_first.is_none() {
                self.streak_first = Some(s.t_ms);
                self.departure = self.last_at_rest(s).or(self.departure).or(Some(s));
            }
            self.streak_fast += 1;
            self.probe_until = Some(s.t_ms + self.cfg.probe_ms);
            let dep = self.departure.unwrap_or(s);
            let sustained = s.t_ms - self.streak_first.unwrap_or(s.t_ms) >= self.cfg.start_sustain_ms;
            if sustained && self.streak_fast >= self.cfg.start_min_fast && s.dist(&dep) >= self.cfg.start_min_dist_m {
                return Some(self.start(dep));
            }
            return None;
        }

        // 느린 점: 빠른 구간이 끊겼다 → 출발점은 이 자리로 다시 잡는다
        self.streak_first = None;
        self.streak_fast = 0;
        self.departure = Some(s);
        self.track_rest(s);
        match self.idle_anchor {
            None => self.idle_anchor = Some(s),
            Some(a) => {
                if s.dist(&a) > self.cfg.probe_move_m + s.acc + a.acc && self.probe_until.is_none() {
                    // 저전력 위치로 보니 꽤 움직였다 → 잠깐 고정밀로 확인
                    self.probe_until = Some(s.t_ms + self.cfg.probe_ms);
                }
            }
        }
        if self.probe_until.is_some_and(|u| s.t_ms >= u) {
            self.expire_probe();
        }
        None
    }

    fn on_driving(&mut self, s: Sample, speed: f64) -> Option<DriveEdgeOut> {
        match self.stop_anchor {
            Some(a) => {
                let radius = self.cfg.stop_radius_m.max(2.0 * s.acc);
                if speed >= self.cfg.start_speed_mps || s.dist(&a) > radius {
                    // 다시 움직임: 느린 점이면 여기서 새로 머무는지 본다
                    self.stop_anchor = (speed < self.cfg.stop_speed_mps).then(|| self.settled_since(s));
                } else if s.t_ms - a.t_ms >= self.cfg.stop_dwell_ms {
                    return Some(self.end(a.t_ms, Some(a)));
                }
            }
            None => {
                if speed < self.cfg.stop_speed_mps {
                    self.stop_anchor = Some(self.settled_since(s));
                }
            }
        }
        None
    }

    /// 속도는 10초 기준이라 늦게 바뀐다 → 실제로 선 순간은 지금 자리에 이미 와 있던 가장 이른 점으로 거슬러 잡는다.
    fn settled_since(&self, s: Sample) -> Sample {
        self.recent
            .iter()
            .rev()
            .take_while(|r| s.dist(r) <= r.acc + s.acc + SAME_SPOT_SLACK_M)
            .last()
            .copied()
            .unwrap_or(s)
    }

    fn track_rest(&mut self, s: Sample) {
        let near = |a: &Sample| s.dist(a) <= s.acc + a.acc + SAME_SPOT_SLACK_M;
        match self.rest {
            None => self.rest = Some(s),
            Some(r) if near(&r) => self.rest_candidate = None,
            Some(_) => match self.rest_candidate {
                Some(c) if near(&c) => {
                    if s.t_ms - c.t_ms >= self.cfg.speed_baseline_ms {
                        self.rest = Some(c);
                        self.rest_candidate = None;
                    }
                }
                _ => self.rest_candidate = Some(s),
            },
        }
    }

    /// 빨라지기 시작한 순간 [s] 기준, 서 있던 자리([Detector::rest])에 아직 있던 마지막 점 = 출발한 순간.
    fn last_at_rest(&self, s: Sample) -> Option<Sample> {
        let rest = self.rest.or(self.departure)?;
        self.recent
            .iter()
            .rev()
            .filter(|r| r.t_ms < s.t_ms)
            .find(|r| r.dist(&rest) <= r.acc + rest.acc + SAME_SPOT_SLACK_M)
            .copied()
    }

    fn start(&mut self, dep: Sample) -> DriveEdgeOut {
        let backlog: Vec<GeoFix> = self.backlog.drain(..).filter(|b| b.t_ms >= dep.t_ms).collect();
        self.driving = true;
        self.stop_anchor = None;
        self.reset_idle();
        DriveEdgeOut { active: true, t_ms: dep.t_ms, backlog }
    }

    fn end(&mut self, t_ms: i64, at: Option<Sample>) -> DriveEdgeOut {
        self.driving = false;
        self.stop_anchor = None;
        self.reset_idle();
        self.idle_anchor = at;
        self.rest = at;
        self.departure = at;
        DriveEdgeOut { active: false, t_ms, backlog: Vec::new() }
    }

    fn expire_probe(&mut self) {
        self.probe_until = None;
        self.streak_first = None;
        self.streak_fast = 0;
        self.idle_anchor = self.recent.back().copied();
    }

    fn reset_idle(&mut self) {
        self.departure = None;
        self.streak_first = None;
        self.streak_fast = 0;
        self.idle_anchor = None;
        self.rest = None;
        self.rest_candidate = None;
        self.probe_until = None;
        self.backlog.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const LAT0: f64 = 37.5665;
    const LON0: f64 = 126.9780;
    /// 위도 1도 ≈ 111,195m
    const M_PER_DEG: f64 = 111_195.0;

    fn fix(t_s: i64, north_m: f64, acc: f32) -> MotionFix {
        MotionFix { lat: LAT0 + north_m / M_PER_DEG, lon: LON0, t_ms: 1_700_000_000_000 + t_s * 1000, accuracy_m: acc, speed_mps: None }
    }

    /// 1초 간격으로 [from_s]부터 [secs]초 동안 [kmh]로 북쪽으로 달린다. 반환: 끝난 위치(m).
    fn drive(d: &mut Detector, from_s: i64, secs: i64, start_m: f64, kmh: f64, edges: &mut Vec<DriveEdgeOut>) -> f64 {
        let v = kmh / 3.6;
        let mut m = start_m;
        for i in 0..secs {
            m = start_m + v * i as f64;
            edges.extend(d.push(&fix(from_s + i, m, 5.0)));
        }
        m
    }

    /// 1초 간격으로 제자리 (±[jitter_m] 흔들림).
    fn park(d: &mut Detector, from_s: i64, secs: i64, at_m: f64, jitter_m: f64, edges: &mut Vec<DriveEdgeOut>) {
        for i in 0..secs {
            let j = if i % 2 == 0 { jitter_m } else { -jitter_m };
            edges.extend(d.push(&fix(from_s + i, at_m + j, 8.0)));
        }
    }

    #[test]
    fn sustained_speed_starts_a_drive_backdated_to_departure() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        park(&mut d, 0, 60, 0.0, 3.0, &mut e);
        assert!(e.is_empty());
        assert!(!d.high_rate(fix(60, 0.0, 5.0).t_ms));
        drive(&mut d, 60, 90, 0.0, 40.0, &mut e);
        assert_eq!(e.len(), 1, "{e:?}");
        let start = &e[0];
        assert!(start.active);
        // 출발 시각은 감지 순간이 아니라 빨라지기 직전 (60초 근처)
        let t0 = fix(0, 0.0, 5.0).t_ms;
        let at = (start.t_ms - t0) / 1000;
        assert!((57..=63).contains(&at), "start at {at}s");
        assert!(!start.backlog.is_empty());
        assert!(start.backlog.iter().all(|b| b.t_ms >= start.t_ms));
        assert!(d.is_driving() && d.high_rate(0));
    }

    #[test]
    fn gps_jitter_and_single_jumps_do_not_start_a_drive() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        // 정확도 20m짜리 흔들림 ±15m, 10분
        for i in 0..600 {
            let j = ((i * 37) % 31) as f64 - 15.0;
            e.extend(d.push(&fix(i, j, 20.0)));
        }
        // 한 번 300m 튀었다 돌아옴
        e.extend(d.push(&fix(600, 300.0, 20.0)));
        e.extend(d.push(&fix(601, 0.0, 20.0)));
        park(&mut d, 602, 120, 0.0, 5.0, &mut e);
        assert!(e.is_empty(), "{e:?}");
        assert!(!d.is_driving());
    }

    #[test]
    fn walking_speed_does_not_start_a_drive() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        drive(&mut d, 0, 900, 0.0, 6.0, &mut e);
        assert!(e.is_empty(), "{e:?}");
    }

    #[test]
    fn traffic_light_stop_does_not_end_but_parking_does() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        let m = drive(&mut d, 0, 120, 0.0, 40.0, &mut e);
        assert_eq!(e.len(), 1);
        // 신호 대기 2분
        park(&mut d, 120, 120, m, 2.0, &mut e);
        assert_eq!(e.len(), 1, "신호 대기로 끝나면 안 된다");
        let m = drive(&mut d, 240, 120, m, 40.0, &mut e);
        // 주차: 5분
        park(&mut d, 360, 300, m, 3.0, &mut e);
        assert_eq!(e.len(), 2, "{e:?}");
        let end = &e[1];
        assert!(!end.active);
        let t0 = fix(0, 0.0, 5.0).t_ms;
        let at = (end.t_ms - t0) / 1000;
        assert!((356..=362).contains(&at), "끝 시각은 선 순간(359초) 근처: {at}s");
        assert!(!d.is_driving());
        assert!(!d.high_rate(fix(700, 0.0, 5.0).t_ms));
    }

    #[test]
    fn lost_signal_ends_the_drive_at_the_last_fix() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        drive(&mut d, 0, 120, 0.0, 50.0, &mut e);
        assert_eq!(e.len(), 1);
        let last = fix(119, 0.0, 5.0).t_ms;
        assert_eq!(d.tick(last + 5 * 60_000), None);
        let end = d.tick(last + 10 * 60_000).expect("10분 동안 위치 없음 → 끝");
        assert!(!end.active);
        assert_eq!(end.t_ms, last);
    }

    #[test]
    fn parked_without_new_fixes_ends_by_tick() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        let m = drive(&mut d, 0, 120, 0.0, 40.0, &mut e);
        park(&mut d, 120, 30, m, 1.0, &mut e);
        assert_eq!(e.len(), 1);
        let end = d.tick(fix(120, 0.0, 5.0).t_ms + 4 * 60_000).expect("4분 머묾");
        assert!(!end.active);
        assert!((end.t_ms - fix(120, 0.0, 5.0).t_ms).abs() <= 3_000, "끝 시각은 선 순간(119초) 근처");
    }

    #[test]
    fn low_rate_movement_turns_on_high_rate_then_expires() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        // 저전력 위치: 1분 간격, 정확도 40m
        for i in 0..5 {
            e.extend(d.push(&fix(i * 60, 0.0, 40.0)));
        }
        assert!(!d.high_rate(fix(300, 0.0, 5.0).t_ms));
        // 1분에 400m 이동 (걷기보다 빠름) → 고정밀로 확인
        e.extend(d.push(&fix(300, 400.0, 40.0)));
        assert!(d.high_rate(fix(301, 0.0, 5.0).t_ms));
        // 그 뒤 계속 제자리 → 2분 지나면 저전력으로
        assert_eq!(d.tick(fix(300 + 121, 0.0, 5.0).t_ms), None);
        assert!(!d.high_rate(fix(300 + 122, 0.0, 5.0).t_ms));
        assert!(e.is_empty());
    }

    #[test]
    fn reported_speed_is_used_when_present() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        for i in 0..40 {
            let mut f = fix(i, i as f64 * 11.0, 5.0);
            f.speed_mps = Some(11.0);
            e.extend(d.push(&f));
        }
        assert_eq!(e.len(), 1);
    }

    #[test]
    fn bad_accuracy_and_out_of_order_fixes_are_ignored() {
        let mut d = Detector::default();
        assert_eq!(d.push(&fix(10, 0.0, 5.0)), None);
        assert_eq!(d.push(&fix(5, 5000.0, 5.0)), None);
        assert_eq!(d.push(&fix(11, 5000.0, 500.0)), None);
        assert_eq!(d.recent.len(), 1);
    }

    #[test]
    fn resume_continues_a_drive_until_it_parks() {
        let mut d = Detector::default();
        d.resume_driving(fix(0, 0.0, 5.0).t_ms);
        assert!(d.is_driving());
        let mut e = Vec::new();
        park(&mut d, 10, 300, 0.0, 2.0, &mut e);
        assert_eq!(e.len(), 1);
        assert!(!e[0].active);
    }

    #[test]
    fn reported_zero_speed_does_not_hide_real_movement() {
        let mut d = Detector::default();
        let mut e = Vec::new();
        for i in 0..60 {
            let mut f = fix(i, i as f64 * 11.0, 5.0);
            f.speed_mps = Some(0.0);
            e.extend(d.push(&f));
        }
        assert_eq!(e.len(), 1);
    }

    #[test]
    fn departure_is_where_it_last_stood_not_an_earlier_spot() {
        // 다른 곳(2km 남쪽)에 있다가 → 출발점으로 옮겨져 1분 서 있다가 → 출발 (에뮬레이터 위치 점프·걸어서 차로 간 경우)
        let mut d = Detector::default();
        let mut e = Vec::new();
        park(&mut d, 0, 30, -2000.0, 2.0, &mut e);
        park(&mut d, 30, 60, 0.0, 2.0, &mut e);
        assert!(e.is_empty(), "{e:?}");
        drive(&mut d, 90, 90, 0.0, 40.0, &mut e);
        assert_eq!(e.len(), 1, "{e:?}");
        let at = (e[0].t_ms - fix(0, 0.0, 5.0).t_ms) / 1000;
        assert!((87..=93).contains(&at), "출발은 90초 근처: {at}s");
    }
}

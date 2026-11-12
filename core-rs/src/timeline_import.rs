//! 구글 타임라인 가져오기: 폰에서 내보낸 `Timeline.json`의 차 이동을 주행 기록으로 넣는다 ([EodiStore::import_google_timeline]).
//!
//! 앱을 처음 깔아도 지난 기록부터 기억이 시작되게 한다. 구글 지도 타임라인은 2024년부터 폰 안에만 저장돼서
//! 가져올 API가 없다 → 사용자가 폰에서 내보낸 파일(설정 › 위치 › 위치 서비스 › 타임라인 › 타임라인 데이터 내보내기)을 읽는다.
//!
//! # 파일 형식 (2026-11 갤럭시에서 내보낸 것 기준)
//! `semanticSegments` 배열의 항목마다 `startTime` · `endTime`("2026-11-12T16:09:20.000+09:00")과 아래 중 하나가 있다.
//! - `activity`: 이동 한 번. `start.latLng` · `end.latLng`("37.60…°, 126.86…°"), `topCandidate.type`(이동 수단).
//! - `timelinePath`: 2시간 창마다 지나간 점들 `[{point, time}]` (몇 분 간격).
//! - `visit` · `timelineMemory`: 쓰지 않는다 (머문 곳은 주행 사이 시간으로 앱이 다시 찾는다).
//!
//! `rawSignals` · `userLocationProfile`은 읽지 않고 건너뛴다 (파일이 수십 MB라 필요한 칸만 풀어 읽는다).
//!
//! # 넣는 규칙
//! - 차 이동만: 이동 수단이 `IN_PASSENGER_VEHICLE` · `IN_VEHICLE`인 것. 걷기 · 버스 · 지하철은 차 기록이 아니다.
//! - [MIN_DRIVE_MS](1분)보다 짧거나 [MIN_DRIVE_M](300m)보다 가까운 이동은 뺀다.
//! - 길: 출발점 → 그 시간 안의 `timelinePath` 점들 → 도착점. 점 사이가 멀어서 [STEP_MS](5초)마다 곧게 이어 채운다
//!   (주행 판정이 끊기지 않게. 실제 도로 모양은 아니다).
//! - 주행마다 시작 · 끝 신호를 넣는다. 탑승 인원 · 와이퍼 · 주행거리계는 폰이 모르므로 넣지 않는다.
//! - 이미 위치 점이 있는 날은 건너뛴다 (차가 직접 남긴 기록 · 시연 기록과 섞지 않는다).
//! - `from_day`("YYYY-MM-DD")를 주면 그날부터만 넣는다.

use std::collections::HashSet;
use std::fs::File;
use std::io::BufReader;

use rusqlite::params;
use serde::Deserialize;

use crate::geo::{haversine_m, Point};
use crate::store::{days_from_civil, format_day, mark_dirty, parse_day, CoreError, EodiStore, DAY_MS, KIND_END, KIND_START};

/// 이보다 짧은 이동은 넣지 않는다.
pub const MIN_DRIVE_MS: i64 = 60_000;
/// 이보다 가까운 이동은 넣지 않는다 (m).
pub const MIN_DRIVE_M: f64 = 300.0;
/// 점 사이를 이 간격으로 채운다.
pub const STEP_MS: i64 = 5_000;

/// 가져온 결과.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TimelineImport {
    /// 넣은 주행 수
    pub drives: u32,
    /// 넣은 위치 점 수
    pub fixes: u32,
    /// 파일에 있던 차 이동 수 (넣지 않은 것 포함)
    pub found: u32,
    /// 이미 기록이 있는 날이라 건너뛴 차 이동 수
    pub skipped_existing: u32,
    /// 넣은 첫 날 · 마지막 날 "YYYY-MM-DD" (없으면 빈 문자열)
    pub first_day: String,
    pub last_day: String,
}

#[derive(Deserialize)]
struct Root {
    #[serde(rename = "semanticSegments", default)]
    segments: Vec<Segment>,
}

#[derive(Deserialize)]
struct Segment {
    #[serde(rename = "startTime")]
    start: String,
    #[serde(rename = "endTime")]
    end: String,
    activity: Option<Activity>,
    #[serde(rename = "timelinePath")]
    path: Option<Vec<PathPoint>>,
}

#[derive(Deserialize)]
struct Activity {
    start: Option<LatLng>,
    end: Option<LatLng>,
    #[serde(rename = "topCandidate")]
    top: Option<Candidate>,
}

#[derive(Deserialize)]
struct LatLng {
    #[serde(rename = "latLng")]
    lat_lng: String,
}

#[derive(Deserialize)]
struct Candidate {
    #[serde(rename = "type", default)]
    kind: String,
}

#[derive(Deserialize)]
struct PathPoint {
    point: String,
    time: String,
}

/// "2026-11-12T16:09:20.000+09:00" · "…Z" → UTC ms.
pub(crate) fn parse_time(s: &str) -> Option<i64> {
    let (date, rest) = s.split_once('T')?;
    let mut d = date.split('-').map(|x| x.parse::<i64>().ok());
    let (y, m, day) = (d.next()??, d.next()??, d.next()??);
    // 시간대: 끝의 Z 또는 ±HH:MM
    let (clock, offset_min) = if let Some(c) = rest.strip_suffix('Z') {
        (c, 0)
    } else {
        let at = rest.rfind(['+', '-'])?;
        let (c, tz) = rest.split_at(at);
        let sign = if tz.starts_with('-') { -1 } else { 1 };
        let (h, mi) = tz[1..].split_once(':')?;
        (c, sign * (h.parse::<i64>().ok()? * 60 + mi.parse::<i64>().ok()?))
    };
    let mut c = clock.split(':');
    let (h, mi, sec) = (c.next()?.parse::<i64>().ok()?, c.next()?.parse::<i64>().ok()?, c.next()?.parse::<f64>().ok()?);
    Some(days_from_civil(y, m, day) * DAY_MS + (h * 60 + mi - offset_min) * 60_000 + (sec * 1000.0) as i64)
}

/// "37.6012°, 126.8612°" → 점.
pub(crate) fn parse_point(s: &str) -> Option<Point> {
    let (a, b) = s.split_once(',')?;
    let num = |x: &str| x.trim().trim_end_matches('°').trim().parse::<f64>().ok();
    let (lat, lon) = (num(a)?, num(b)?);
    ((-90.0..=90.0).contains(&lat) && (-180.0..=180.0).contains(&lon)).then_some(Point { lat, lon })
}

fn is_car(kind: &str) -> bool {
    matches!(kind, "IN_PASSENGER_VEHICLE" | "IN_VEHICLE")
}

/// 차 이동 하나: 시각순 점들 (출발 · 지나간 점 · 도착).
struct Drive {
    points: Vec<(i64, Point)>,
}

/// 파일에서 차 이동을 뽑는다 (시각순).
fn drives_of(root: Root, from_ms: i64) -> Vec<Drive> {
    let mut path: Vec<(i64, Point)> = Vec::new();
    let mut acts: Vec<(i64, i64, Point, Point)> = Vec::new();
    for s in root.segments {
        if let Some(points) = s.path {
            path.extend(points.iter().filter_map(|p| Some((parse_time(&p.time)?, parse_point(&p.point)?))));
        }
        let Some(a) = s.activity else { continue };
        if !a.top.as_ref().is_some_and(|t| is_car(&t.kind)) {
            continue;
        }
        let (Some(t0), Some(t1)) = (parse_time(&s.start), parse_time(&s.end)) else { continue };
        let (Some(p0), Some(p1)) = (a.start.and_then(|p| parse_point(&p.lat_lng)), a.end.and_then(|p| parse_point(&p.lat_lng))) else {
            continue;
        };
        if t0 >= from_ms && t1 - t0 >= MIN_DRIVE_MS && haversine_m(p0, p1) >= MIN_DRIVE_M {
            acts.push((t0, t1, p0, p1));
        }
    }
    path.sort_by_key(|p| p.0);
    acts.sort_by_key(|a| a.0);
    acts.into_iter()
        .map(|(t0, t1, p0, p1)| {
            let lo = path.partition_point(|p| p.0 <= t0);
            let hi = path.partition_point(|p| p.0 < t1);
            let mut points = Vec::with_capacity(hi.saturating_sub(lo) + 2);
            points.push((t0, p0));
            points.extend_from_slice(&path[lo..hi.max(lo)]);
            points.push((t1, p1));
            Drive { points }
        })
        .collect()
}

/// 점 사이를 [STEP_MS]마다 곧게 이어 채운 위치 점들 (끝점 포함).
fn densify(points: &[(i64, Point)]) -> Vec<(i64, f64, f64)> {
    let mut out = Vec::new();
    for w in points.windows(2) {
        let ((t0, a), (t1, b)) = (w[0], w[1]);
        let mut t = t0;
        while t < t1 {
            let f = (t - t0) as f64 / (t1 - t0) as f64;
            out.push((t, a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f));
            t += STEP_MS;
        }
    }
    if let Some(&(t, p)) = points.last() {
        out.push((t, p.lat, p.lon));
    }
    out
}

#[uniffi::export]
impl EodiStore {
    /// 구글 타임라인 내보내기 파일(`path`)의 차 이동을 주행 기록으로 넣는다 (모듈 설명).
    /// `from_day`("YYYY-MM-DD")가 있으면 그날부터만. 파일을 못 읽거나 형식이 다르면 `InvalidInput`.
    pub fn import_google_timeline(&self, path: String, from_day: Option<String>) -> Result<TimelineImport, CoreError> {
        let from_ms = match from_day {
            Some(d) => self.day_start_ms(parse_day(&d).ok_or_else(|| CoreError::InvalidInput(format!("from_day {d:?}")))?),
            None => i64::MIN,
        };
        let file = File::open(&path).map_err(|e| CoreError::InvalidInput(format!("타임라인 파일을 열 수 없다: {e}")))?;
        let root: Root =
            serde_json::from_reader(BufReader::new(file)).map_err(|e| CoreError::InvalidInput(format!("타임라인 파일 형식이 다르다: {e}")))?;
        let drives = drives_of(root, from_ms);
        let found = drives.len() as u32;

        let mut conn = self.lock()?;
        // 이미 위치 점이 있는 날
        let occupied: HashSet<i64> = {
            let tz_ms = -self.day_start_ms(0);
            let mut st = conn.prepare("SELECT DISTINCT (t_ms + ?1) / 86400000 FROM fix")?;
            let rows = st.query_map([tz_ms], |r| r.get::<_, i64>(0))?;
            rows.collect::<Result<_, _>>()?
        };
        let (mut made, mut fixes, mut skipped) = (0u32, 0u32, 0u32);
        let (mut first, mut last) = (i64::MAX, i64::MIN);
        {
            let tx = conn.transaction()?;
            {
                let mut fix = tx.prepare("INSERT OR IGNORE INTO fix (t_ms, lat, lon, accuracy_m) VALUES (?1, ?2, ?3, 30.0)")?;
                let mut event = tx.prepare("INSERT OR IGNORE INTO drive_event (t_ms, kind) VALUES (?1, ?2)")?;
                for d in &drives {
                    let (t0, t1) = (d.points[0].0, d.points[d.points.len() - 1].0);
                    if (self.local_day(t0)..=self.local_day(t1)).any(|day| occupied.contains(&day)) {
                        skipped += 1;
                        continue;
                    }
                    for (t, lat, lon) in densify(&d.points) {
                        fixes += fix.execute(params![t, lat, lon])? as u32;
                    }
                    event.execute([t0, KIND_START])?;
                    event.execute([t1, KIND_END])?;
                    made += 1;
                    first = first.min(t0);
                    last = last.max(t1);
                }
            }
            if made > 0 {
                mark_dirty(&tx, self.day_start_ms(self.local_day(first)), self.day_start_ms(self.local_day(last) + 1))?;
            }
            tx.commit()?;
        }
        self.flush(&mut conn)?;
        let day = |t: i64| if made > 0 { format_day(self.local_day(t)) } else { String::new() };
        Ok(TimelineImport { drives: made, fixes, found, skipped_existing: skipped, first_day: day(first), last_day: day(last) })
    }
}

#[cfg(test)]
mod tests;

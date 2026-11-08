//! 회상과 요약: "N년 전 오늘", 지나갈 때 떠오르는 기억, 이번 달 요약 · 올해 결산.
//!
//! 계산(고르기 · 횟수 제한 · 숫자 · 안내 문장)은 모두 여기서 하고, Kotlin은 받은 값을 그리거나 읽기만 한다.
//! 바탕은 기억 모듈이 만든 에피소드(`episode` 표: 종류 · 들른 곳 · 지역)와 날짜별 요약(`day_summary`: 거리 · 주행 시간)이다.
//! 표를 더하지 않는다: 횟수 제한 · 오늘 닫음 같은 상태는 기존 `meta` 표(키 → 정수)에 둔다.
//!
//! # 기억할 만한 날 · 곳
//! - 나들이 · 여행 에피소드만 본다 (출퇴근 · 동네 볼일은 회상하지 않는다).
//! - 들른 곳 중 이름이 없는 곳, 집 · 회사(지금 쓰는 집 · 회사에서 250m 안)는 뺀다.
//! - 같은 곳 = 이름과 주소가 같은 곳 (검색 화면과 같은 기준).
//!
//! # N년 전 오늘 ([EodiStore::on_this_day])
//! - 오늘과 같은 월 · 일(2월 29일은 없는 해면 2월 28일)을 1년 전부터 10년 전까지 가까운 해부터 찾아,
//!   나들이 · 여행이 있는 첫 날을 고른다. 그날 에피소드가 여럿이면 여행 > 나들이, 들른 곳이 많은 것, 먼 것 순.
//! - 사진용 들른 곳(`photos`)은 휴게소 · 주유소 · 충전소가 아닌 곳을 앞에 두고 최대 3곳.
//! - 음성 한 줄: "1년 전 오늘은 강릉 여행을 다녀왔어요" (규칙 이름 `rule_label`을 쓴다 — LLM 제목보다 말로 듣기 쉽다).
//! - 오늘 닫음(`dismiss_on_this_day`) · 오늘 읽음(`mark_on_this_day_spoken`)은 날짜 번호로 남긴다 → 다음 날 다시 나온다.
//!
//! # 지나갈 때 회상 ([EodiStore::recall_near])
//! 기록 서비스가 위치 묶음을 저장할 때마다(10초 또는 30개) 그 묶음의 점을 넘긴다. 아래를 모두 지키는 곳 하나를 고른다.
//! - 묶음의 어느 점에서든 [RECALL_RADIUS_M](300m) 안에 있는, 기억할 만한 곳.
//! - 최근 [RECENT_VISIT_DAYS](30)일 안에 들른 적이 없는 곳 (그 사이 에피소드의 들른 곳 · 도착지에서 200m 안이면 들른 것으로 본다.
//!   단 도착한 지 10분이 안 된 곳은 빼고 본다 — 지금 달리는 주행의 끝은 지금 위치일 뿐이라).
//! - 하루 [MAX_PER_DAY](3)번까지, 같은 곳은 [SAME_PLACE_DAYS](7)일에 한 번, 회상 사이 최소 [MIN_GAP_MS](15분).
//!   (경포해변 · 초당할머니순두부처럼 가까운 곳이 줄줄이 나오지 않게)
//! - 여럿이면 가장 가까운 곳, 같으면 여행 쪽. 그곳에 여러 번 갔으면 가장 최근 나들이 · 여행 날을 말한다.
//! - 문장: "1년 전 가을에 들렀던 초당할머니순두부가 근처에 있어요" (1년이 안 됐으면 "8월에" · "작년 12월에").
//! - 고르기만 하고 횟수는 세지 않는다. 앱이 실제로 보여 주거나 읽으면 [EodiStore::mark_recall_shown]으로 센다
//!   (내비가 말하는 중이라 못 읽었으면 세지 않게).
//! - 주행 중 조회라 주행 다시 나누기(`flush`)는 하지 않고, 밀린 기억(에피소드)만 다시 만든다. 지금 달리는 주행은 아직
//!   에피소드가 아니지만, 같은 곳 1주일 제한이 그 몫을 한다.
//!
//! meta 키: `recall_day`(센 날 번호) · `recall_day_count` · `recall_last_ms` · `recall_place:<이름|주소>`(마지막 회상 ms,
//! 1주일 지나면 지운다) · `otd_dismissed_day` · `otd_spoken_day`.
//!
//! # 이번 달 요약 ([EodiStore::month_recap]) · 올해 결산 ([EodiStore::year_recap])
//! - 거리 · 주행 시간 · 달린 날: 날짜별 요약의 합.
//! - 나들이 = 나들이가 있고 여행이 없는 날 수. 여행 = 여행이 있는 날이 이어진 묶음 수 (부산 2박 3일 = 1번).
//! - 처음 간 곳: 그 기간에 처음 들른(`first_visit`) 곳 수 (집 · 회사 · 이름 없는 곳 빼고, 같은 곳은 한 번).
//! - 자주 간 곳: 모든 에피소드의 들른 곳을 다녀간 날 수로 센다 (집 · 회사 빼고). 같으면 머문 시간 합, 이름 순.
//! - 지역: 나들이 · 여행 에피소드의 지역(예: "강원 강릉시")을 다녀간 날 수로 센다.
//! - 최고의 날: 나들이 · 여행 날 중 점수(여행 3 · 나들이 2 + 처음 간 곳 0.5 + 들른 곳 0.3 + 100km마다 1(최대 2) +
//!   AI 제목 0.5)가 가장 높은 날, 같으면 최근 날. 제목은 하루 제목(AI, 없으면 규칙 제목), 사진은 가장 오래 머문 곳.
//! - 지난달과 비교: 이번 달이 진행 중이면 지난달도 같은 날짜(1일 ~ 오늘의 일)까지만 비교한다.
//! - 올해: 위 합계 + 계절(봄 3~5월 · 여름 6~8월 · 가을 9~11월 · 겨울 1 · 2 · 12월)별 거리 · 나들이 · 여행 날 수 · 최고의 날,
//!   가장 긴 여행(이어진 여행 날들의 거리 합이 가장 큰 것), 달마다 거리(막대 그래프용 12칸).

use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};

use rusqlite::{params, Connection, OptionalExtension};

use crate::agent::dates::{civil_from_days, days_from_civil};
use crate::ffi::GeoPoint;
use crate::geo::{haversine_m, Point};
use crate::memory::{day_title, episode_from_row, flush_memory, kind_code, Episode, EpisodeKind, StopFact, EP_COLS};
use crate::store::{format_day, CoreError, EodiStore, DAY_MS};

/// 이 거리(m) 안을 지나면 회상한다.
pub const RECALL_RADIUS_M: f64 = 300.0;
/// 이 기간(일) 안에 들른 곳은 회상하지 않는다.
pub const RECENT_VISIT_DAYS: i64 = 30;
/// 하루 회상 횟수 상한.
pub const MAX_PER_DAY: i64 = 3;
/// 같은 곳은 이 기간(일)에 한 번만.
pub const SAME_PLACE_DAYS: i64 = 7;
/// 회상 사이 최소 간격 (ms).
pub const MIN_GAP_MS: i64 = 15 * 60_000;
/// 집 · 회사에서 이 거리(m) 안이면 집 · 회사 (기억 모듈과 같은 값).
const ANCHOR_M: f64 = 250.0;
/// 최근 들른 곳과 이 거리(m) 안이면 같은 곳으로 본다.
const SAME_SPOT_M: f64 = 200.0;
/// 도착한 지 이만큼(ms)이 안 된 곳은 아직 "들른 곳"이 아니다 (지금 달리는 주행의 끝 = 지금 위치).
const JUST_NOW_MS: i64 = 10 * 60_000;
/// "N년 전 오늘"을 찾는 가장 먼 해.
const MAX_YEARS_BACK: i64 = 10;

const KEY_DAY: &str = "recall_day";
const KEY_COUNT: &str = "recall_day_count";
const KEY_LAST: &str = "recall_last_ms";
const KEY_PLACE: &str = "recall_place:";
const KEY_OTD_DISMISSED: &str = "otd_dismissed_day";
const KEY_OTD_SPOKEN: &str = "otd_spoken_day";

/// "N년 전 오늘" 카드 · 음성 한 줄.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct OnThisDay {
    /// 몇 년 전 (1부터)
    pub years_ago: u32,
    /// 그날 "YYYY-MM-DD"
    pub day: String,
    /// 하루 제목 (AI 제목, 없으면 규칙 제목)
    pub title: String,
    /// AI 이야기 한두 문장 (없으면 빈 문자열)
    pub summary: String,
    /// 대표 에피소드 (코스 안내에 쓴다)
    pub episode_id: i64,
    pub kind: EpisodeKind,
    /// 규칙 이름 (예: "강릉 여행")
    pub label: String,
    /// 지역 (예: "강원 강릉시", 모르면 빈 문자열)
    pub region: String,
    /// 그날 나들이 · 여행 거리 합 (m)
    pub distance_m: f64,
    /// 들른 곳 (도착 순서)
    pub stops: Vec<StopFact>,
    /// 사진으로 보일 곳 (최대 3곳)
    pub photos: Vec<StopFact>,
    /// 음성 한 줄 ("1년 전 오늘은 강릉 여행을 다녀왔어요")
    pub line: String,
    /// 오늘 카드를 닫았다
    pub dismissed: bool,
    /// 오늘 음성으로 읽었다
    pub spoken: bool,
}

/// 지나갈 때 회상 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Recall {
    /// 같은 곳 표시 ([EodiStore::mark_recall_shown]에 그대로 넘긴다)
    pub key: String,
    pub place: StopFact,
    /// 지금 위치(묶음에서 가장 가까운 점)와의 거리 (m)
    pub distance_m: f64,
    /// 그때 그날 "YYYY-MM-DD"
    pub day: String,
    /// 그날 제목 (AI 제목, 없으면 규칙 제목)
    pub day_title: String,
    pub episode_id: i64,
    /// 몇 년 전 (1년이 안 됐으면 0)
    pub years_ago: u32,
    /// 도장 글 ("1년 전 가을", "8월", "작년 12월")
    pub when_text: String,
    /// 읽어 줄 한 줄 ("1년 전 가을에 들렀던 초당할머니순두부가 근처에 있어요")
    pub line: String,
}

/// 요약의 장소 한 줄.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RecapPlace {
    pub place: StopFact,
    /// 다녀간 날 수
    pub days: u32,
}

/// 요약의 지역 한 줄.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RecapRegion {
    /// 예: "강원 강릉시"
    pub region: String,
    pub days: u32,
}

/// 요약의 날 하나 (최고의 날 · 가장 긴 여행).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RecapDay {
    /// 첫날 "YYYY-MM-DD"
    pub day: String,
    /// 이어진 날 수 (하루면 1, 2박 3일 여행이면 3)
    pub span_days: u32,
    pub title: String,
    pub episode_id: i64,
    pub kind: EpisodeKind,
    pub region: String,
    /// 그 날(들) 나들이 · 여행 거리 합 (m)
    pub distance_m: f64,
    /// 사진으로 보일 곳 (가장 오래 머문 곳, 휴게소 · 주유소는 뒤로)
    pub photo: Option<StopFact>,
}

/// 기간 합계 (달 · 계절 · 해 공통).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RecapTotals {
    pub distance_m: f64,
    pub drive_millis: i64,
    /// 주행이 있었던 날 수
    pub drive_days: u32,
    /// 나들이 날 수 (여행 날 제외)
    pub outings: u32,
    /// 여행 수 (이어진 여행 날 묶음)
    pub trips: u32,
    /// 처음 간 곳 수
    pub new_places: u32,
}

/// 이번 달 요약.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct MonthRecap {
    pub year: i32,
    pub month: u32,
    /// 이 달이 아직 진행 중 (오늘이 이 달)
    pub in_progress: bool,
    /// 이 달에 기록이 있는 날 수 (0이면 빈 달)
    pub record_days: u32,
    pub totals: RecapTotals,
    /// 자주 간 곳 (최대 5곳)
    pub top_places: Vec<RecapPlace>,
    /// 다녀온 지역 (나들이 · 여행, 최대 5곳)
    pub regions: Vec<RecapRegion>,
    pub best_day: Option<RecapDay>,
    /// 지난달과 비교 (이번 달 - 지난달, 진행 중이면 같은 날짜까지)
    pub vs_prev_distance_m: f64,
    pub vs_prev_drive_millis: i64,
    pub vs_prev_outings: i32,
    /// 지난달에 기록이 있었나 (없으면 비교 줄을 숨긴다)
    pub has_prev_records: bool,
    /// 앞 · 뒤 달로 넘길 수 있나 (기록이 시작된 달 ~ 이번 달)
    pub has_prev: bool,
    pub has_next: bool,
}

/// 계절 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SeasonRecap {
    /// "봄" · "여름" · "가을" · "겨울"
    pub name: String,
    pub distance_m: f64,
    /// 나들이 · 여행 날 수
    pub outing_days: u32,
    pub best_day: Option<RecapDay>,
}

/// 올해 결산 ("올해의 어디였지").
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct YearRecap {
    pub year: i32,
    pub in_progress: bool,
    pub record_days: u32,
    pub totals: RecapTotals,
    /// 자주 간 곳 상위 5곳
    pub top_places: Vec<RecapPlace>,
    pub regions: Vec<RecapRegion>,
    /// 봄 · 여름 · 가을 · 겨울 순
    pub seasons: Vec<SeasonRecap>,
    pub longest_trip: Option<RecapDay>,
    pub best_day: Option<RecapDay>,
    /// 1월 ~ 12월 거리 (m)
    pub month_distance_m: Vec<f64>,
    pub has_prev: bool,
    pub has_next: bool,
}

fn bad(what: &str) -> CoreError {
    CoreError::InvalidInput(what.to_owned())
}

#[uniffi::export]
impl EodiStore {
    /// "N년 전 오늘" (모듈 설명). 1~10년 전 같은 날에 나들이 · 여행이 없으면 None. `now_ms` = 지금 (UTC ms).
    pub fn on_this_day(&self, now_ms: i64) -> Result<Option<OnThisDay>, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        on_this_day(self, &conn, now_ms)
    }

    /// 오늘은 "N년 전 오늘" 카드를 닫는다 (내일 다시 나온다).
    pub fn dismiss_on_this_day(&self, now_ms: i64) -> Result<(), CoreError> {
        let conn = self.lock()?;
        set_meta(&conn, KEY_OTD_DISMISSED, self.local_day(now_ms))?;
        Ok(())
    }

    /// 오늘 "N년 전 오늘"을 음성으로 읽었다 (오늘은 다시 읽지 않는다).
    pub fn mark_on_this_day_spoken(&self, now_ms: i64) -> Result<(), CoreError> {
        let conn = self.lock()?;
        set_meta(&conn, KEY_OTD_SPOKEN, self.local_day(now_ms))?;
        Ok(())
    }

    /// 위치 묶음 근처의 기억할 만한 곳 하나 (모듈 설명의 규칙 · 횟수 제한). 없거나 제한에 걸리면 None.
    /// 횟수는 세지 않는다 → 실제로 보여 주거나 읽었으면 [EodiStore::mark_recall_shown]. 좌표가 틀리면 `InvalidInput`.
    pub fn recall_near(&self, points: Vec<GeoPoint>, now_ms: i64) -> Result<Option<Recall>, CoreError> {
        if points.iter().any(|p| !(p.lat.is_finite() && p.lon.is_finite() && p.lat.abs() <= 90.0 && p.lon.abs() <= 180.0)) {
            return Err(bad("point"));
        }
        if points.is_empty() {
            return Ok(None);
        }
        let mut conn = self.lock()?;
        flush_memory(self, &mut conn, false)?;
        let pts: Vec<Point> = points.into_iter().map(Point::from).collect();
        recall_near(self, &conn, &pts, now_ms)
    }

    /// 회상을 보여 줬다 (횟수 · 같은 곳 제한에 센다). 1주일 지난 같은 곳 기록은 지운다.
    pub fn mark_recall_shown(&self, key: String, now_ms: i64) -> Result<(), CoreError> {
        if key.is_empty() {
            return Err(bad("empty key"));
        }
        let mut conn = self.lock()?;
        let today = self.local_day(now_ms);
        let tx = conn.transaction()?;
        let count = if get_meta(&tx, KEY_DAY)? == Some(today) { get_meta(&tx, KEY_COUNT)?.unwrap_or(0) } else { 0 };
        set_meta(&tx, KEY_DAY, today)?;
        set_meta(&tx, KEY_COUNT, count + 1)?;
        set_meta(&tx, KEY_LAST, now_ms)?;
        set_meta(&tx, &format!("{KEY_PLACE}{key}"), now_ms)?;
        tx.execute(
            "DELETE FROM meta WHERE key >= ?1 AND key < ?2 AND value < ?3",
            params![KEY_PLACE, "recall_place;", now_ms - SAME_PLACE_DAYS * DAY_MS],
        )?;
        tx.commit()?;
        Ok(())
    }

    /// 한 달 요약 (`month` 1~12). `now_ms` = 지금 (진행 중인 달 · 넘기기 판단). 월이 틀리면 `InvalidInput`.
    pub fn month_recap(&self, year: i32, month: u32, now_ms: i64) -> Result<MonthRecap, CoreError> {
        if !(1..=12).contains(&month) {
            return Err(bad("month"));
        }
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        month_recap(self, &conn, year as i64, month, now_ms)
    }

    /// 한 해 결산 ("올해의 어디였지"). 진행 중인 해면 오늘까지.
    pub fn year_recap(&self, year: i32, now_ms: i64) -> Result<YearRecap, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        year_recap(self, &conn, year as i64, now_ms)
    }
}

// ---------------------------------------------------------------- meta

fn get_meta(conn: &Connection, key: &str) -> rusqlite::Result<Option<i64>> {
    conn.prepare_cached("SELECT value FROM meta WHERE key = ?1")?.query_row([key], |r| r.get(0)).optional()
}

fn set_meta(conn: &Connection, key: &str, value: i64) -> rusqlite::Result<()> {
    conn.prepare_cached("INSERT OR REPLACE INTO meta (key, value) VALUES (?1, ?2)")?.execute(params![key, value])?;
    Ok(())
}

// ---------------------------------------------------------------- 공통

/// 지금 쓰는 집 · 회사 (기억 모듈이 정한 값, memory_anchor kind 2 · 3).
fn anchors(conn: &Connection) -> rusqlite::Result<Vec<Point>> {
    let mut st = conn.prepare_cached("SELECT lat, lon FROM memory_anchor WHERE kind IN (2, 3)")?;
    let rows = st.query_map([], |r| Ok(Point { lat: r.get(0)?, lon: r.get(1)? }))?;
    rows.collect()
}

fn stop_point(s: &StopFact) -> Point {
    Point { lat: s.lat, lon: s.lon }
}

/// 기억할 만한 들른 곳인가: 이름이 있고 집 · 회사가 아니다.
fn notable(s: &StopFact, anchors: &[Point]) -> bool {
    !s.name.trim().is_empty() && anchors.iter().all(|&a| haversine_m(a, stop_point(s)) > ANCHOR_M)
}

/// 같은 곳 표시 = 이름 | 주소.
pub(crate) fn place_key(s: &StopFact) -> String {
    format!("{}|{}", s.name.trim(), s.address.trim())
}

fn memorable(k: EpisodeKind) -> bool {
    matches!(k, EpisodeKind::Outing | EpisodeKind::Trip)
}

/// 휴게소 · 주유소 · 충전소처럼 지나가며 들른 곳 (사진 · 대표로는 뒤로 미룬다).
fn pass_through(s: &StopFact) -> bool {
    ["휴게소", "주유소", "충전소", "주차장"].iter().any(|w| s.category.contains(w) || s.name.ends_with(w))
}

pub(crate) fn episodes_between(conn: &Connection, d0: i64, d1: i64) -> Result<Vec<Episode>, CoreError> {
    let mut st =
        conn.prepare_cached(&format!("SELECT {EP_COLS} FROM episode e WHERE e.day BETWEEN ?1 AND ?2 ORDER BY e.day, e.ord"))?;
    let rows = st.query_map([d0, d1], |r| episode_from_row(r, 0))?;
    Ok(rows.collect::<Result<_, _>>()?)
}

fn day_of(e: &Episode) -> i64 {
    crate::store::parse_day(&e.facts.day).unwrap_or(0)
}

/// 사진으로 보일 곳: 지나가며 들른 곳이 아닌 곳을 앞에, 도착 순서 그대로 최대 n곳.
fn photo_stops(stops: &[StopFact], anchors: &[Point], n: usize) -> Vec<StopFact> {
    let named: Vec<&StopFact> = stops.iter().filter(|s| notable(s, anchors)).collect();
    named.iter().filter(|s| !pass_through(s)).chain(named.iter().filter(|s| pass_through(s))).take(n).map(|s| (*s).clone()).collect()
}

/// 대표 사진 한 곳: 가장 오래 머문 곳 (지나가며 들른 곳은 다른 곳이 없을 때만).
fn longest_stop<'a>(stops: impl Iterator<Item = &'a StopFact>, anchors: &[Point]) -> Option<StopFact> {
    stops
        .filter(|s| notable(s, anchors))
        .max_by_key(|s| (!pass_through(s), s.leave_ms - s.arrive_ms, -s.arrive_ms))
        .cloned()
}

/// 받침 (한글 마지막 글자, 숫자는 읽는 소리로). 모르면 None.
fn batchim(w: &str) -> Option<u32> {
    let c = w.trim_end_matches(['\'', '"', ')', ' ']).chars().last()?;
    if ('가'..='힣').contains(&c) {
        return Some((c as u32 - '가' as u32) % 28);
    }
    c.to_digit(10).map(|d| [1, 8, 0, 1, 0, 0, 1, 8, 1, 0][d as usize])
}

/// 이/가
fn ga(w: &str) -> &'static str {
    if batchim(w).is_some_and(|b| b != 0) { "이" } else { "가" }
}

/// 을/를
fn eul(w: &str) -> &'static str {
    if batchim(w).is_some_and(|b| b != 0) { "을" } else { "를" }
}

pub(crate) fn season_of(month: u32) -> &'static str {
    match month {
        3..=5 => "봄",
        6..=8 => "여름",
        9..=11 => "가을",
        _ => "겨울",
    }
}

/// 그날부터 오늘까지 몇 년 (만).
pub(crate) fn years_between(then: i64, today: i64) -> u32 {
    let (y0, m0, d0) = civil_from_days(then);
    let (y1, m1, d1) = civil_from_days(today);
    let years = y1 - y0 - i64::from((m1, d1) < (m0, d0));
    years.max(0) as u32
}

/// 도장 글: "1년 전 가을" · "8월"(올해) · "작년 12월".
pub(crate) fn when_text(then: i64, today: i64) -> String {
    let (y0, m0, _) = civil_from_days(then);
    let (y1, _, _) = civil_from_days(today);
    match years_between(then, today) {
        0 if y0 == y1 => format!("{m0}월"),
        0 => format!("작년 {m0}월"),
        n => format!("{n}년 전 {}", season_of(m0)),
    }
}

pub(crate) fn recall_line(when: &str, name: &str) -> String {
    format!("{when}에 들렀던 {name}{} 근처에 있어요", ga(name))
}

pub(crate) fn on_this_day_line(years: u32, label: &str) -> String {
    format!("{years}년 전 오늘은 {label}{} 다녀왔어요", eul(label))
}

/// 하루 제목 (AI, 없으면 규칙). 없으면 [fallback].
fn title_of(conn: &Connection, d: i64, fallback: &str) -> Result<String, CoreError> {
    Ok(day_title(conn, d)?.0.filter(|t| !t.trim().is_empty()).unwrap_or_else(|| fallback.to_owned()))
}

pub(crate) fn ep_rank(e: &Episode) -> (bool, usize, i64) {
    (e.facts.kind == EpisodeKind::Trip, e.facts.stops.len(), e.facts.distance_m as i64)
}

// ---------------------------------------------------------------- N년 전 오늘

fn on_this_day(store: &EodiStore, conn: &Connection, now_ms: i64) -> Result<Option<OnThisDay>, CoreError> {
    let today = store.local_day(now_ms);
    let (y, m, d) = civil_from_days(today);
    let anchors = anchors(conn)?;
    for k in 1..=MAX_YEARS_BACK {
        // 2월 29일 → 없는 해면 2월 28일
        let mut day = days_from_civil(y - k, m, d);
        if civil_from_days(day).1 != m {
            day = days_from_civil(y - k, m, d - 1);
        }
        let eps: Vec<Episode> = episodes_between(conn, day, day)?.into_iter().filter(|e| memorable(e.facts.kind)).collect();
        let Some(best) = eps.iter().max_by_key(|e| ep_rank(e)) else { continue };
        let f = &best.facts;
        let summary: String = conn
            .prepare_cached("SELECT summary FROM day_memory WHERE day = ?1")?
            .query_row([day], |r| r.get(0))
            .optional()?
            .unwrap_or_default();
        let stops: Vec<StopFact> = f.stops.iter().filter(|s| notable(s, &anchors)).cloned().collect();
        return Ok(Some(OnThisDay {
            years_ago: k as u32,
            day: format_day(day),
            title: title_of(conn, day, &f.rule_label)?,
            summary,
            episode_id: f.id,
            kind: f.kind,
            label: f.rule_label.clone(),
            region: f.region.clone(),
            distance_m: eps.iter().map(|e| e.facts.distance_m).sum(),
            photos: photo_stops(&stops, &anchors, 3),
            stops,
            line: on_this_day_line(k as u32, &f.rule_label),
            dismissed: get_meta(conn, KEY_OTD_DISMISSED)? == Some(today),
            spoken: get_meta(conn, KEY_OTD_SPOKEN)? == Some(today),
        }));
    }
    Ok(None)
}

// ---------------------------------------------------------------- 지나갈 때 회상

fn recall_near(store: &EodiStore, conn: &Connection, pts: &[Point], now_ms: i64) -> Result<Option<Recall>, CoreError> {
    let today = store.local_day(now_ms);
    // 횟수 제한
    let count = if get_meta(conn, KEY_DAY)? == Some(today) { get_meta(conn, KEY_COUNT)?.unwrap_or(0) } else { 0 };
    if count >= MAX_PER_DAY {
        return Ok(None);
    }
    if get_meta(conn, KEY_LAST)?.is_some_and(|last| now_ms - last < MIN_GAP_MS) {
        return Ok(None);
    }
    let anchors = anchors(conn)?;
    let recent_from = now_ms - RECENT_VISIT_DAYS * DAY_MS;

    // 1) 근처의 기억할 만한 곳 (오래된 나들이 · 여행만; 최근 30일 안 에피소드는 어차피 "최근에 들른 곳")
    struct Cand {
        stop: StopFact,
        dist: f64,
        trip: bool,
        day: i64,
        episode_id: i64,
        rule_label: String,
    }
    let mut best_by_key: HashMap<String, Cand> = HashMap::new();
    {
        let mut st = conn.prepare_cached(&format!(
            "SELECT {EP_COLS} FROM episode e WHERE e.kind IN (?1, ?2) AND e.start_ms < ?3 ORDER BY e.start_ms"
        ))?;
        let rows = st.query_map(
            params![kind_code(EpisodeKind::Outing), kind_code(EpisodeKind::Trip), recent_from],
            |r| episode_from_row(r, 0),
        )?;
        for e in rows {
            let e = e?;
            for s in &e.facts.stops {
                if !notable(s, &anchors) {
                    continue;
                }
                let dist = pts.iter().map(|&p| haversine_m(p, stop_point(s))).fold(f64::INFINITY, f64::min);
                if dist > RECALL_RADIUS_M {
                    continue;
                }
                // 같은 곳을 여러 번 갔으면 가장 최근 날 (start_ms 순으로 읽으므로 뒤가 최근)
                best_by_key.insert(
                    place_key(s),
                    Cand {
                        stop: s.clone(),
                        dist,
                        trip: e.facts.kind == EpisodeKind::Trip,
                        day: day_of(&e),
                        episode_id: e.facts.id,
                        rule_label: e.facts.rule_label.clone(),
                    },
                );
            }
        }
    }
    if best_by_key.is_empty() {
        return Ok(None);
    }

    // 2) 최근 30일 안에 들른 곳 · 1주일 안에 회상한 곳은 뺀다
    let recent: Vec<Point> = {
        let mut st = conn.prepare_cached(&format!("SELECT {EP_COLS}, e.to_lat, e.to_lon FROM episode e WHERE e.start_ms >= ?1"))?;
        let rows = st.query_map([recent_from], |r| {
            let e = episode_from_row(r, 0)?;
            Ok((e, Point { lat: r.get(13)?, lon: r.get(14)? }))
        })?;
        // 지금 달리는 주행의 끝(= 지금 위치)은 "들른 곳"이 아니다 → 도착한 지 [JUST_NOW_MS]가 지난 곳만 센다
        let settled = now_ms - JUST_NOW_MS;
        let mut out = Vec::new();
        for row in rows {
            let (e, to) = row?;
            if e.facts.end_ms < settled {
                out.push(to);
            }
            out.extend(e.facts.stops.iter().filter(|s| s.arrive_ms < settled).map(stop_point));
        }
        out
    };
    let mut cands: Vec<(String, Cand)> = Vec::new();
    for (key, c) in best_by_key {
        let p = stop_point(&c.stop);
        if recent.iter().any(|&q| haversine_m(p, q) <= SAME_SPOT_M) {
            continue;
        }
        if get_meta(conn, &format!("{KEY_PLACE}{key}"))?.is_some_and(|t| now_ms - t < SAME_PLACE_DAYS * DAY_MS) {
            continue;
        }
        cands.push((key, c));
    }
    // 3) 가장 가까운 곳, 같으면 여행 쪽, 그다음 이름 순 (결과가 늘 같게)
    cands.sort_by(|a, b| {
        a.1.dist.total_cmp(&b.1.dist).then(b.1.trip.cmp(&a.1.trip)).then(a.0.cmp(&b.0))
    });
    let Some((key, c)) = cands.into_iter().next() else { return Ok(None) };
    let when = when_text(c.day, today);
    Ok(Some(Recall {
        line: recall_line(&when, c.stop.name.trim()),
        key,
        distance_m: c.dist,
        day: format_day(c.day),
        day_title: title_of(conn, c.day, &c.rule_label)?,
        episode_id: c.episode_id,
        years_ago: years_between(c.day, today),
        when_text: when,
        place: c.stop,
    }))
}

// ---------------------------------------------------------------- 요약

/// 기간의 기록 (요약 계산 한 번에 필요한 것 전부).
struct Span {
    episodes: Vec<Episode>,
    /// 날짜 → (거리, 주행 시간, 주행 수)
    days: BTreeMap<i64, (f64, i64, i64)>,
}

fn load_span(conn: &Connection, d0: i64, d1: i64) -> Result<Span, CoreError> {
    let mut st = conn.prepare_cached(
        "SELECT day, distance_m, drive_millis, trip_count FROM day_summary WHERE day BETWEEN ?1 AND ?2",
    )?;
    let rows = st.query_map([d0, d1], |r| Ok((r.get::<_, i64>(0)?, (r.get(1)?, r.get(2)?, r.get(3)?))))?;
    let days = rows.collect::<Result<BTreeMap<_, _>, _>>()?;
    Ok(Span { episodes: episodes_between(conn, d0, d1)?, days })
}

impl Span {
    /// [lo, hi] 안의 에피소드
    fn eps(&self, lo: i64, hi: i64) -> impl Iterator<Item = &Episode> {
        self.episodes.iter().filter(move |e| (lo..=hi).contains(&day_of(e)))
    }

    fn trip_days(&self, lo: i64, hi: i64) -> BTreeSet<i64> {
        self.eps(lo, hi).filter(|e| e.facts.kind == EpisodeKind::Trip).map(day_of).collect()
    }

    fn outing_days(&self, lo: i64, hi: i64) -> BTreeSet<i64> {
        let trips = self.trip_days(lo, hi);
        self.eps(lo, hi).filter(|e| e.facts.kind == EpisodeKind::Outing).map(day_of).filter(|d| !trips.contains(d)).collect()
    }

    fn totals(&self, lo: i64, hi: i64, anchors: &[Point]) -> RecapTotals {
        let (mut distance_m, mut drive_millis, mut drive_days) = (0.0, 0, 0);
        for (_, &(dist, ms, trips)) in self.days.range(lo..=hi) {
            distance_m += dist;
            drive_millis += ms;
            drive_days += u32::from(trips > 0);
        }
        let new: HashSet<String> = self
            .eps(lo, hi)
            .flat_map(|e| e.facts.stops.iter())
            .filter(|s| s.first_visit && notable(s, anchors))
            .map(place_key)
            .collect();
        RecapTotals {
            distance_m,
            drive_millis,
            drive_days,
            outings: self.outing_days(lo, hi).len() as u32,
            trips: runs(&self.trip_days(lo, hi)).len() as u32,
            new_places: new.len() as u32,
        }
    }

    fn record_days(&self, lo: i64, hi: i64) -> u32 {
        self.days.range(lo..=hi).count() as u32
    }

    /// 자주 간 곳 상위 n곳.
    fn top_places(&self, lo: i64, hi: i64, anchors: &[Point], n: usize) -> Vec<RecapPlace> {
        // 같은 곳 → (다녀간 날들, 머문 시간 합, 가장 최근 들른 모습)
        type Visits = (BTreeSet<i64>, i64, StopFact);
        let mut by: HashMap<String, Visits> = HashMap::new();
        for e in self.eps(lo, hi) {
            let d = day_of(e);
            for s in e.facts.stops.iter().filter(|s| notable(s, anchors)) {
                let ent = by.entry(place_key(s)).or_insert_with(|| (BTreeSet::new(), 0, s.clone()));
                ent.0.insert(d);
                ent.1 += (s.leave_ms - s.arrive_ms).max(0);
                if s.arrive_ms >= ent.2.arrive_ms {
                    ent.2 = s.clone();
                }
            }
        }
        let mut v: Vec<(String, Visits)> = by.into_iter().collect();
        v.sort_by(|a, b| b.1 .0.len().cmp(&a.1 .0.len()).then(b.1 .1.cmp(&a.1 .1)).then(a.0.cmp(&b.0)));
        v.into_iter().take(n).map(|(_, (days, _, place))| RecapPlace { place, days: days.len() as u32 }).collect()
    }

    fn regions(&self, lo: i64, hi: i64, n: usize) -> Vec<RecapRegion> {
        let mut by: HashMap<&str, BTreeSet<i64>> = HashMap::new();
        for e in self.eps(lo, hi).filter(|e| memorable(e.facts.kind) && !e.facts.region.is_empty()) {
            by.entry(e.facts.region.as_str()).or_default().insert(day_of(e));
        }
        let mut v: Vec<(&str, usize)> = by.into_iter().map(|(r, d)| (r, d.len())).collect();
        v.sort_by(|a, b| b.1.cmp(&a.1).then(a.0.cmp(b.0)));
        v.into_iter().take(n).map(|(r, d)| RecapRegion { region: r.to_owned(), days: d as u32 }).collect()
    }

    /// 하루 점수 (모듈 설명). 나들이 · 여행이 없으면 None.
    fn day_score(&self, d: i64, anchors: &[Point], has_ai_title: bool) -> Option<f64> {
        let eps: Vec<&Episode> = self.eps(d, d).filter(|e| memorable(e.facts.kind)).collect();
        let kind = if eps.iter().any(|e| e.facts.kind == EpisodeKind::Trip) { 3.0 } else if eps.is_empty() { return None } else { 2.0 };
        let stops: Vec<&StopFact> = eps.iter().flat_map(|e| e.facts.stops.iter()).filter(|s| notable(s, anchors)).collect();
        let first = stops.iter().filter(|s| s.first_visit).count() as f64;
        let km: f64 = eps.iter().map(|e| e.facts.distance_m).sum::<f64>() / 1000.0;
        Some(kind + 0.5 * first + 0.3 * stops.len() as f64 + (km / 100.0).min(2.0) + if has_ai_title { 0.5 } else { 0.0 })
    }

    /// 날들(이어진 여행 등)을 요약의 날 하나로.
    fn recap_day(&self, conn: &Connection, days: &[i64], anchors: &[Point]) -> Result<Option<RecapDay>, CoreError> {
        let (Some(&first), Some(&last)) = (days.first(), days.last()) else { return Ok(None) };
        let eps: Vec<&Episode> = self.eps(first, last).filter(|e| memorable(e.facts.kind)).collect();
        let Some(main) = eps.iter().filter(|e| day_of(e) == first).max_by_key(|e| ep_rank(e)).or(eps.first()) else {
            return Ok(None);
        };
        let f = &main.facts;
        Ok(Some(RecapDay {
            day: format_day(first),
            span_days: (last - first + 1) as u32,
            title: title_of(conn, first, &f.rule_label)?,
            episode_id: f.id,
            kind: f.kind,
            region: f.region.clone(),
            distance_m: eps.iter().map(|e| e.facts.distance_m).sum(),
            photo: longest_stop(eps.iter().flat_map(|e| e.facts.stops.iter()), anchors),
        }))
    }

    fn best_day(&self, conn: &Connection, lo: i64, hi: i64, anchors: &[Point]) -> Result<Option<RecapDay>, CoreError> {
        let days: BTreeSet<i64> = self.eps(lo, hi).filter(|e| memorable(e.facts.kind)).map(day_of).collect();
        let mut best: Option<(f64, i64)> = None;
        for d in days {
            if let Some(s) = self.day_score(d, anchors, has_ai_title(conn, d)?) {
                if best.is_none_or(|(bs, _)| s >= bs) {
                    best = Some((s, d));
                }
            }
        }
        match best {
            Some((_, d)) => self.recap_day(conn, &[d], anchors),
            None => Ok(None),
        }
    }
}

fn has_ai_title(conn: &Connection, d: i64) -> rusqlite::Result<bool> {
    Ok(conn
        .prepare_cached("SELECT title IS NOT NULL FROM day_memory WHERE day = ?1")?
        .query_row([d], |r| r.get::<_, bool>(0))
        .optional()?
        .unwrap_or(false))
}

/// 이어진 날 묶음 (예: {7, 8, 9, 20} → [[7, 8, 9], [20]]).
pub(crate) fn runs(days: &BTreeSet<i64>) -> Vec<Vec<i64>> {
    let mut out: Vec<Vec<i64>> = Vec::new();
    for &d in days {
        match out.last_mut() {
            Some(run) if run.last() == Some(&(d - 1)) => run.push(d),
            _ => out.push(vec![d]),
        }
    }
    out
}

fn month_bounds(y: i64, m: u32) -> (i64, i64) {
    let first = days_from_civil(y, m, 1);
    let (ny, nm) = if m == 12 { (y + 1, 1) } else { (y, m + 1) };
    (first, days_from_civil(ny, nm, 1) - 1)
}

fn first_record_day(conn: &Connection) -> rusqlite::Result<Option<i64>> {
    conn.query_row("SELECT min(day) FROM day_summary", [], |r| r.get(0))
}

fn month_recap(store: &EodiStore, conn: &Connection, y: i64, m: u32, now_ms: i64) -> Result<MonthRecap, CoreError> {
    let today = store.local_day(now_ms);
    let (lo, hi) = month_bounds(y, m);
    let (py, pm) = if m == 1 { (y - 1, 12) } else { (y, m - 1) };
    let (plo, phi) = month_bounds(py, pm);
    let in_progress = (lo..=hi).contains(&today);
    // 진행 중이면 지난달도 같은 날짜까지 (지난달이 짧으면 그 달 끝까지)
    let prev_hi = if in_progress { (plo + (today - lo)).min(phi) } else { phi };
    let span = load_span(conn, plo, hi)?;
    let anchors = anchors(conn)?;
    let cur = span.totals(lo, hi, &anchors);
    let prev = span.totals(plo, prev_hi, &anchors);
    let first = first_record_day(conn)?;
    Ok(MonthRecap {
        year: y as i32,
        month: m,
        in_progress,
        record_days: span.record_days(lo, hi),
        top_places: span.top_places(lo, hi, &anchors, 5),
        regions: span.regions(lo, hi, 5),
        best_day: span.best_day(conn, lo, hi, &anchors)?,
        vs_prev_distance_m: cur.distance_m - prev.distance_m,
        vs_prev_drive_millis: cur.drive_millis - prev.drive_millis,
        vs_prev_outings: (cur.outings + cur.trips) as i32 - (prev.outings + prev.trips) as i32,
        has_prev_records: span.record_days(plo, prev_hi) > 0,
        has_prev: first.is_some_and(|f| f < lo),
        has_next: hi < today,
        totals: cur,
    })
}

fn year_recap(store: &EodiStore, conn: &Connection, y: i64, now_ms: i64) -> Result<YearRecap, CoreError> {
    let today = store.local_day(now_ms);
    let (lo, hi) = (days_from_civil(y, 1, 1), days_from_civil(y, 12, 31));
    let span = load_span(conn, lo, hi)?;
    let anchors = anchors(conn)?;
    let mut seasons = Vec::with_capacity(4);
    for (name, ranges) in [
        ("봄", vec![(3, 5)]),
        ("여름", vec![(6, 8)]),
        ("가을", vec![(9, 11)]),
        ("겨울", vec![(1, 2), (12, 12)]),
    ] {
        let mut distance_m = 0.0;
        let mut outing_days = 0;
        let mut best: Option<RecapDay> = None;
        let mut best_score = f64::MIN;
        for (m0, m1) in ranges {
            let (a, _) = month_bounds(y, m0);
            let (_, b) = month_bounds(y, m1);
            let t = span.totals(a, b, &anchors);
            distance_m += t.distance_m;
            outing_days += (span.outing_days(a, b).len() + span.trip_days(a, b).len()) as u32;
            if let Some(d) = span.best_day(conn, a, b, &anchors)? {
                let dn = crate::store::parse_day(&d.day).unwrap_or(0);
                let s = span.day_score(dn, &anchors, has_ai_title(conn, dn)?).unwrap_or(0.0);
                if s >= best_score {
                    best_score = s;
                    best = Some(d);
                }
            }
        }
        seasons.push(SeasonRecap { name: name.to_owned(), distance_m, outing_days, best_day: best });
    }
    // 가장 긴 여행: 이어진 여행 날들의 거리 합
    let mut longest: Option<(f64, Vec<i64>)> = None;
    for run in runs(&span.trip_days(lo, hi)) {
        let dist: f64 = span.eps(run[0], run[run.len() - 1]).filter(|e| memorable(e.facts.kind)).map(|e| e.facts.distance_m).sum();
        if longest.as_ref().is_none_or(|(d, _)| dist > *d) {
            longest = Some((dist, run));
        }
    }
    let month_distance_m = (1..=12)
        .map(|m| {
            let (a, b) = month_bounds(y, m);
            span.days.range(a..=b).map(|(_, v)| v.0).sum()
        })
        .collect();
    let first = first_record_day(conn)?;
    Ok(YearRecap {
        year: y as i32,
        in_progress: (lo..=hi).contains(&today),
        record_days: span.record_days(lo, hi),
        totals: span.totals(lo, hi, &anchors),
        top_places: span.top_places(lo, hi, &anchors, 5),
        regions: span.regions(lo, hi, 5),
        seasons,
        longest_trip: match longest {
            Some((_, run)) => span.recap_day(conn, &run, &anchors)?,
            None => None,
        },
        best_day: span.best_day(conn, lo, hi, &anchors)?,
        month_distance_m,
        has_prev: first.is_some_and(|f| f < lo),
        has_next: hi < today,
    })
}

#[cfg(test)]
mod tests;

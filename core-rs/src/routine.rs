//! 늘 가던 날: 정해진 요일마다 가던 곳을 찾아, 그날이 다가오면 먼저 알려 준다 ([EodiStore::routine_nudge]).
//!
//! 고르기 · 문장은 모두 여기서 만들고, Kotlin은 받은 값을 그리기만 한다. 표를 더하지 않는다 (닫음은 `meta` 표).
//!
//! # 늘 가던 곳
//! - 최근 [LOOKBACK_DAYS](180)일의 에피소드에서 들른 곳을 모은다. 이름 없는 곳, 집 · 회사는 뺀다 (회상과 같은 기준).
//!   같은 곳 = 이름과 주소가 같은 곳.
//! - **매주:** 그 요일 최근 [WEEKLY_WINDOW](8)번 중 [WEEKLY_MIN_HITS](6)번 이상 갔다 → "매주 토요일".
//!   요일이 [BUSY_WEEKDAYS](3)개 이상이면 거의 날마다 가는 곳이라 알려 줄 것이 없다 → 뺀다.
//! - **매달:** 같은 "몇째 주 무슨 요일"(예: 첫째 수요일)에 [MONTHLY_MIN_HITS](3)번 이상, 그리고 그 기간에 돌아온 그날의
//!   4번 중 3번 이상 갔다 → "매달 첫째 수요일". 한 곳에 요일이 같은 칸이 여럿이면 "매달 첫째 · 셋째 수요일"로 묶는다.
//!   다섯째 주는 없는 달이 많아 보지 않는다. 매주 가는 요일은 매달로 다시 세지 않는다.
//!
//! # 알려 주기
//! - 오늘부터 [AHEAD_DAYS](7)일 안에 그날이 돌아오는 곳 중 가장 가까운 날 하나. 같으면 더 자주 간 곳.
//! - 그날 이미 다녀왔으면(오늘이 그날이고 오늘 들렀다) 알려 주지 않는다.
//! - 문장: "매달 첫째 · 셋째 수요일마다 현대자동차 남양연구소에 갔어요. 다음 주 수요일이 그날이에요."
//!   (오늘 · 내일 · 이번 주 X요일 · 다음 주 X요일, 한 주는 월요일부터)
//! - 닫으면(`dismiss_routine_nudge`) 그날 것은 다시 나오지 않고, 다음에 돌아오는 날 다시 나온다.
//!   meta 키: `routine_dismissed:<이름|주소>` = 닫은 그날의 날짜 번호.

use std::collections::{BTreeMap, BTreeSet};

use rusqlite::Connection;

use crate::agent::dates::civil_from_days;
use crate::memory::StopFact;
use crate::recap::{anchors, episodes_between, ga, get_meta, notable, place_key, set_meta};
use crate::store::{format_day, parse_day, CoreError, EodiStore, DAY_MS};

/// 이 기간(일)의 기록에서 늘 가던 곳을 찾는다.
pub const LOOKBACK_DAYS: i64 = 180;
/// 오늘부터 이 기간(일) 안에 돌아오는 날만 알려 준다.
pub const AHEAD_DAYS: i64 = 7;
/// 매주: 그 요일 최근 몇 번을 볼까.
pub const WEEKLY_WINDOW: i64 = 8;
/// 매주: 그중 이만큼 갔으면 매주 가는 곳.
pub const WEEKLY_MIN_HITS: usize = 6;
/// 매주 가는 요일이 이만큼이면 거의 날마다 가는 곳 (알려 주지 않는다).
pub const BUSY_WEEKDAYS: usize = 3;
/// 매달: 같은 "몇째 주 무슨 요일"에 이만큼 갔으면 매달 가는 곳.
pub const MONTHLY_MIN_HITS: usize = 3;

const KEY_DISMISSED: &str = "routine_dismissed:";
const WEEKDAYS: [&str; 7] = ["일", "월", "화", "수", "목", "금", "토"];
const ORDINALS: [&str; 4] = ["첫째", "둘째", "셋째", "넷째"];
const MIN_MS: i64 = 60_000;

/// "늘 가던 날" 카드 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RoutineNudge {
    /// 같은 곳 표시 (이름|주소). 닫을 때 그대로 넘긴다.
    pub key: String,
    /// 그곳 (가장 최근에 들른 기록)
    pub place: StopFact,
    /// "매주 토요일" · "매달 첫째 · 셋째 수요일"
    pub pattern: String,
    /// 돌아오는 그날 "YYYY-MM-DD"
    pub day: String,
    /// 오늘부터 며칠 뒤 (0 = 오늘)
    pub days_until: u32,
    /// "오늘" · "내일" · "이번 주 수요일" · "다음 주 수요일"
    pub when_text: String,
    /// 그 요일 규칙대로 간 횟수 (최근 180일)
    pub visits: u32,
    /// 덧붙이는 한 줄 ("최근 6개월 동안 11번 · 보통 오전 9시 반쯤 도착")
    pub detail: String,
    /// 카드 · 음성 한 줄
    pub line: String,
}

#[uniffi::export]
impl EodiStore {
    /// 늘 가던 날이 다가온 곳 하나 (모듈 설명). 없으면 None. `now_ms` = 지금 (UTC ms).
    pub fn routine_nudge(&self, now_ms: i64) -> Result<Option<RoutineNudge>, CoreError> {
        let conn = self.read()?;
        routine_nudge(self, &conn, now_ms)
    }

    /// 이번에 돌아오는 날(`day`) 것은 닫는다. 다음에 돌아오는 날 다시 나온다.
    pub fn dismiss_routine_nudge(&self, key: String, day: String) -> Result<(), CoreError> {
        let d = parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day: {day}")))?;
        let conn = self.lock_side()?;
        set_meta(&conn, &format!("{KEY_DISMISSED}{key}"), d)?;
        Ok(())
    }
}

/// 요일 (0 = 일 ~ 6 = 토). 1970-01-01(날짜 번호 0)은 목요일.
fn weekday(day: i64) -> usize {
    (day + 4).rem_euclid(7) as usize
}

/// 그 달의 몇째 주 그 요일인가 (1부터). 1~7일 = 첫째.
fn nth_in_month(day: i64) -> u32 {
    (civil_from_days(day).2 - 1) / 7 + 1
}

/// 그 주 월요일의 날짜 번호.
fn monday_of(day: i64) -> i64 {
    day - (weekday(day) as i64 + 6) % 7
}

pub(crate) fn when_text(target: i64, today: i64) -> String {
    let w = WEEKDAYS[weekday(target)];
    match target - today {
        0 => "오늘".to_owned(),
        1 => "내일".to_owned(),
        _ if monday_of(target) == monday_of(today) => format!("이번 주 {w}요일"),
        _ => format!("다음 주 {w}요일"),
    }
}

/// "오전 9시" · "오후 2시 반" (30분 단위로 맞춘다).
pub(crate) fn clock_text(minutes: i64) -> String {
    let m = ((minutes + 15) / 30 * 30).rem_euclid(24 * 60);
    let (h, half) = (m / 60, m % 60 == 30);
    let ampm = if h < 12 { "오전" } else { "오후" };
    let h12 = if h % 12 == 0 { 12 } else { h % 12 };
    format!("{ampm} {h12}시{}", if half { " 반" } else { "" })
}

pub(crate) fn routine_line(pattern: &str, name: &str, when: &str) -> String {
    format!("{pattern}마다 {name}에 갔어요. {when}{} 그날이에요.", ga(when))
}

/// 한 곳의 규칙 하나: 요일과 (매달이면) 몇째 주들.
struct Rule {
    weekday: usize,
    /// 비어 있으면 매주
    nths: Vec<u32>,
    hits: usize,
}

impl Rule {
    fn text(&self) -> String {
        let w = WEEKDAYS[self.weekday];
        if self.nths.is_empty() {
            format!("매주 {w}요일")
        } else {
            let n: Vec<&str> = self.nths.iter().map(|&n| ORDINALS[n as usize - 1]).collect();
            format!("매달 {} {w}요일", n.join(" · "))
        }
    }

    fn matches(&self, day: i64) -> bool {
        weekday(day) == self.weekday && (self.nths.is_empty() || self.nths.contains(&nth_in_month(day)))
    }
}

/// 한 곳에 간 날들에서 규칙을 찾는다 (`from` ~ 어제. 오늘은 아직 안 갔을 수 있어 세지 않는다).
fn rules_of(days: &BTreeSet<i64>, from: i64, today: i64) -> Vec<Rule> {
    let mut weekly: Vec<Rule> = Vec::new();
    for w in 0..7 {
        let Some(last) = (1..=7).map(|k| today - k).find(|&d| weekday(d) == w) else { continue };
        let hits = (0..WEEKLY_WINDOW).filter(|k| days.contains(&(last - 7 * k))).count();
        if hits >= WEEKLY_MIN_HITS {
            weekly.push(Rule { weekday: w, nths: Vec::new(), hits });
        }
    }
    if weekly.len() >= BUSY_WEEKDAYS {
        return Vec::new();
    }
    let mut monthly: BTreeMap<usize, Rule> = BTreeMap::new();
    for w in (0..7).filter(|w| weekly.iter().all(|r| r.weekday != *w)) {
        for nth in 1..=4u32 {
            let (mut chances, mut hits) = (0usize, 0usize);
            for d in (from..today).filter(|&d| weekday(d) == w && nth_in_month(d) == nth) {
                chances += 1;
                hits += usize::from(days.contains(&d));
            }
            if hits >= MONTHLY_MIN_HITS && hits * 4 >= chances * 3 {
                let r = monthly.entry(w).or_insert(Rule { weekday: w, nths: Vec::new(), hits: 0 });
                r.nths.push(nth);
                r.hits += hits;
            }
        }
    }
    weekly.into_iter().chain(monthly.into_values()).collect()
}

fn routine_nudge(store: &EodiStore, conn: &Connection, now_ms: i64) -> Result<Option<RoutineNudge>, CoreError> {
    let today = store.local_day(now_ms);
    let from = today - LOOKBACK_DAYS;
    let anchors = anchors(conn)?;
    // 곳마다: 간 날들 · 도착 시각(현지 분) · 가장 최근 기록
    let mut places: BTreeMap<String, (BTreeSet<i64>, Vec<i64>, StopFact)> = BTreeMap::new();
    for e in episodes_between(conn, from, today)? {
        for s in e.facts.stops.iter().filter(|s| notable(s, &anchors)) {
            let local_ms = s.arrive_ms + store.tz_minutes() as i64 * MIN_MS;
            let p = places.entry(place_key(s)).or_insert_with(|| (BTreeSet::new(), Vec::new(), s.clone()));
            p.0.insert(store.local_day(s.arrive_ms));
            p.1.push(local_ms.rem_euclid(DAY_MS) / MIN_MS);
            if s.arrive_ms > p.2.arrive_ms {
                p.2 = s.clone();
            }
        }
    }
    // 가장 가까운 날, 같으면 더 자주 간 곳
    let mut best: Option<(i64, usize, RoutineNudge)> = None;
    for (key, (days, mut clocks, place)) in places {
        clocks.sort_unstable();
        for rule in rules_of(&days, from, today) {
            let Some(target) = (today..=today + AHEAD_DAYS).find(|&d| rule.matches(d)) else { continue };
            if days.contains(&target) || get_meta(conn, &format!("{KEY_DISMISSED}{key}"))? == Some(target) {
                continue;
            }
            let until = target - today;
            if best.as_ref().is_some_and(|(d, h, _)| until > *d || (until == *d && rule.hits <= *h)) {
                continue;
            }
            let usual = clock_text(clocks[clocks.len() / 2]);
            let (pattern, when) = (rule.text(), when_text(target, today));
            let nudge = RoutineNudge {
                key: key.clone(),
                line: routine_line(&pattern, &place.name, &when),
                detail: format!("최근 {}개월 동안 {}번 · 보통 {usual}쯤 도착", LOOKBACK_DAYS / 30, rule.hits),
                place: place.clone(),
                pattern,
                day: format_day(target),
                days_until: until as u32,
                when_text: when,
                visits: rule.hits as u32,
            };
            best = Some((until, rule.hits, nudge));
        }
    }
    Ok(best.map(|(_, _, n)| n))
}

#[cfg(test)]
mod tests;

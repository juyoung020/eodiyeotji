//! 날짜 계산과 한국어 날짜 표현 해석 ("작년 여름", "지난주", "8월 14일", "어린이날" …).
//!
//! 날짜는 "1970-01-01부터 며칠째"(현지 날짜) 정수로 다룬다. 시간대는 분 단위 오프셋(한국 = 540).

/// 하루 (ms)
pub const DAY_MS: i64 = 86_400_000;

/// 요일 이름 (월요일 = 0)
pub const WEEKDAYS: [&str; 7] = ["월", "화", "수", "목", "금", "토", "일"];

/// (연, 월, 일) → 1970-01-01부터 며칠째. (Howard Hinnant의 days_from_civil)
pub fn days_from_civil(y: i64, m: u32, d: u32) -> i64 {
    let y = if m <= 2 { y - 1 } else { y };
    let era = y.div_euclid(400);
    let yoe = y - era * 400;
    let m = m as i64;
    let doy = (153 * (if m > 2 { m - 3 } else { m + 9 }) + 2) / 5 + d as i64 - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe - 719_468
}

/// 며칠째 → (연, 월, 일).
pub fn civil_from_days(z: i64) -> (i64, u32, u32) {
    let z = z + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let m = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
    (yoe + era * 400 + i64::from(m <= 2), m, d)
}

/// 요일 (월요일 = 0). 1970-01-01은 목요일.
pub fn weekday(days: i64) -> usize {
    (days + 3).rem_euclid(7) as usize
}

pub fn local_days(ms: i64, tz_offset_minutes: i32) -> i64 {
    (ms + tz_offset_minutes as i64 * 60_000).div_euclid(DAY_MS)
}

/// 현지 날짜의 0시 (UTC epoch ms).
pub fn day_start_ms(days: i64, tz_offset_minutes: i32) -> i64 {
    days * DAY_MS - tz_offset_minutes as i64 * 60_000
}

pub fn fmt_day(days: i64) -> String {
    let (y, m, d) = civil_from_days(days);
    format!("{y:04}-{m:02}-{d:02}")
}

pub fn hhmm(ms: i64, tz_offset_minutes: i32) -> String {
    let local = (ms + tz_offset_minutes as i64 * 60_000).rem_euclid(DAY_MS) / 60_000;
    format!("{:02}:{:02}", local / 60, local % 60)
}

fn last_day_of_month(y: i64, m: u32) -> u32 {
    let (ny, nm) = if m == 12 { (y + 1, 1) } else { (y, m + 1) };
    (days_from_civil(ny, nm, 1) - days_from_civil(y, m, 1)) as u32
}

/// "YYYY-MM-DD" 또는 "YYYY-MM" → 그 날(또는 그 달의 첫날/마지막 날, [end]면 마지막 날).
pub fn parse_ymd(s: &str, end: bool) -> Option<i64> {
    let s = s.trim();
    let mut it = s.splitn(3, '-');
    let y: i64 = it.next()?.trim().parse().ok()?;
    let m: u32 = it.next()?.trim().parse().ok()?;
    if !(1..=12).contains(&m) || !(1900..=2200).contains(&y) {
        return None;
    }
    match it.next() {
        Some(d) => {
            let d: u32 = d.trim().get(..2.min(d.trim().len()))?.parse().ok()?;
            (1..=last_day_of_month(y, m)).contains(&d).then(|| days_from_civil(y, m, d))
        }
        None => Some(if end { days_from_civil(y, m, last_day_of_month(y, m)) } else { days_from_civil(y, m, 1) }),
    }
}

/// 기간 (현지 날짜, 양 끝 포함).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DayRange {
    pub from: i64,
    pub to: i64,
}

impl DayRange {
    pub fn day(d: i64) -> Self {
        Self { from: d, to: d }
    }
    pub fn to_ms(self, tz: i32) -> (i64, i64) {
        (day_start_ms(self.from, tz), day_start_ms(self.to + 1, tz) - 1)
    }
}

fn month_range(y: i64, m: u32) -> DayRange {
    DayRange { from: days_from_civil(y, m, 1), to: days_from_civil(y, m, last_day_of_month(y, m)) }
}

/// 계절의 기간. 겨울은 그해 12월 ~ 다음 해 2월.
fn season_range(y: i64, season: usize) -> DayRange {
    match season {
        0 => DayRange { from: days_from_civil(y, 3, 1), to: days_from_civil(y, 5, 31) },
        1 => DayRange { from: days_from_civil(y, 6, 1), to: days_from_civil(y, 8, 31) },
        2 => DayRange { from: days_from_civil(y, 9, 1), to: days_from_civil(y, 11, 30) },
        _ => DayRange { from: days_from_civil(y, 12, 1), to: days_from_civil(y + 1, 2, last_day_of_month(y + 1, 2)) },
    }
}

const SEASONS: [&str; 4] = ["봄", "여름", "가을", "겨울"];

/// 날짜가 정해진 기념일 (음력 명절은 뺀다).
const HOLIDAYS: [(&str, u32, u32); 8] = [
    ("크리스마스이브", 12, 24),
    ("크리스마스", 12, 25),
    ("성탄절", 12, 25),
    ("어린이날", 5, 5),
    ("현충일", 6, 6),
    ("광복절", 8, 15),
    ("개천절", 10, 3),
    ("한글날", 10, 9),
];

/// 한국어 말 속 날짜 표현 → 기간. 오늘([today]) 기준, 미래로 넘어가면 오늘까지로 자른다.
/// 날짜 말이 없으면 None. 어떤 낱말이 날짜 말로 쓰였는지는 [is_date_word]로 거른다.
pub fn parse_korean_range(text: &str, today: i64) -> Option<DayRange> {
    let t: String = text.split_whitespace().collect::<Vec<_>>().join(" ");
    let nospace: String = t.chars().filter(|c| !c.is_whitespace()).collect();
    let (ty, tm, _td) = civil_from_days(today);

    // 0) 숫자로 쓴 날짜 "2025-11-07", "2025.11.7", "2025/11/07" (손으로 치는 검색)
    if let Some(d) = t.split_whitespace().find_map(numeric_day) {
        return Some(DayRange::day(d));
    }

    // 1) 상대 날짜
    let rel = [("그저께", -2), ("그제", -2), ("엊그제", -2), ("어제", -1), ("오늘", 0)];
    for (w, off) in rel {
        if nospace.contains(w) {
            return Some(DayRange::day(today + off));
        }
    }
    if let Some(n) = number_before(&nospace, "일전") {
        return Some(DayRange::day(today - n));
    }
    let monday = today - weekday(today) as i64;
    if nospace.contains("지난주말") || nospace.contains("저번주말") {
        return Some(DayRange { from: monday - 2, to: monday - 1 });
    }
    if nospace.contains("이번주말") {
        return clamp(DayRange { from: monday + 5, to: monday + 6 }, today);
    }
    if nospace.contains("지난주") || nospace.contains("저번주") {
        return Some(DayRange { from: monday - 7, to: monday - 1 });
    }
    if nospace.contains("이번주") {
        return Some(DayRange { from: monday, to: today });
    }
    if let Some(n) = number_before(&nospace, "주전") {
        let m = monday - 7 * n;
        return Some(DayRange { from: m, to: m + 6 });
    }
    if nospace.contains("지난달") || nospace.contains("저번달") {
        let (y, m) = if tm == 1 { (ty - 1, 12) } else { (ty, tm - 1) };
        return Some(month_range(y, m));
    }
    if nospace.contains("이번달") {
        return Some(DayRange { from: days_from_civil(ty, tm, 1), to: today });
    }
    if let Some(n) = number_before(&nospace, "달전").or_else(|| number_before(&nospace, "개월전")) {
        let total = ty * 12 + (tm as i64 - 1) - n;
        return Some(month_range(total.div_euclid(12), (total.rem_euclid(12) + 1) as u32));
    }
    if nospace.contains("최근") || nospace.contains("요즘") || nospace.contains("요새") {
        return Some(DayRange { from: today - 30, to: today });
    }

    // 2) 해 기준 (작년·올해·재작년·N년 전·YYYY년)
    let year: Option<i64> = if nospace.contains("재작년") {
        Some(ty - 2)
    } else if nospace.contains("작년") || nospace.contains("지난해") {
        Some(ty - 1)
    } else if nospace.contains("올해") || nospace.contains("금년") {
        Some(ty)
    } else if let Some(n) = number_before(&nospace, "년전") {
        Some(ty - n)
    } else {
        number_before(&nospace, "년").filter(|y| (1990..=2200).contains(y))
    };

    // 3) 기념일 / N월 N일 / N월 / 계절
    for (name, m, d) in HOLIDAYS {
        if nospace.contains(name) {
            let y = year.unwrap_or_else(|| if days_from_civil(ty, m, d) <= today { ty } else { ty - 1 });
            return clamp(DayRange::day(days_from_civil(y, m, d)), today);
        }
    }
    if let Some(m) = number_before(&nospace, "월").filter(|m| (1..=12).contains(m)) {
        let m = m as u32;
        let y = year.unwrap_or(if m <= tm { ty } else { ty - 1 });
        let after_month = &nospace[nospace.find(&format!("{m}월")).map(|i| i + format!("{m}월").len()).unwrap_or(0)..];
        if let Some(d) = leading_number(after_month).filter(|_| after_month.trim_start_matches(|c: char| c.is_ascii_digit()).starts_with('일')) {
            if (1..=last_day_of_month(y, m) as i64).contains(&d) {
                return clamp(DayRange::day(days_from_civil(y, m, d as u32)), today);
            }
        }
        return clamp(month_range(y, m), today);
    }
    for (i, s) in SEASONS.iter().enumerate() {
        if nospace.contains(s) {
            let y = year.unwrap_or_else(|| {
                // 해 말이 없으면 이미 시작한 가장 최근 그 계절
                if season_range(ty, i).from <= today { ty } else { ty - 1 }
            });
            return clamp(season_range(y, i), today);
        }
    }
    let y = year?;
    clamp(DayRange { from: days_from_civil(y, 1, 1), to: days_from_civil(y, 12, 31) }, today)
}

/// 날짜 표현에 쓰이는 낱말 (검색어에서 뺀다).
/// "2025-11-07" · "2025.11.7" · "2025/11/07" → 그날 (연 · 월 · 일 셋 다 있을 때만).
pub(crate) fn numeric_day(w: &str) -> Option<i64> {
    let w: String = w.trim_end_matches(['.', ',']).chars().map(|c| if c == '.' || c == '/' { '-' } else { c }).collect();
    (w.matches('-').count() == 2 && w.chars().all(|c| c.is_ascii_digit() || c == '-')).then(|| parse_ymd(&w, false)).flatten()
}

pub fn is_date_word(w: &str) -> bool {
    const WORDS: [&str; 30] = [
        "작년", "올해", "재작년", "지난해", "금년", "지난주", "저번주", "이번주", "지난달", "저번달", "이번달", "어제", "그저께", "그제",
        "엊그제", "오늘", "최근", "요즘", "요새", "주말", "지난", "저번", "이번", "전", "봄", "여름", "가을", "겨울", "이브", "날",
    ];
    if WORDS.contains(&w) || HOLIDAYS.iter().any(|(h, _, _)| *h == w || h.starts_with(w) && w.chars().count() >= 3) {
        return true;
    }
    // 숫자로 쓴 날짜 "2025-11-07", "2025.11.7" (검색어가 아니라 날짜)
    if numeric_day(w).is_some() {
        return true;
    }
    // 숫자 + 단위 (8월, 14일, 2025년, 3주, 2달, 1년)
    let digits = w.chars().take_while(|c| c.is_ascii_digit()).count();
    digits > 0 && {
        let rest: String = w.chars().skip(digits).collect();
        ["월", "일", "년", "주", "달", "개월", "일전", "주전", "달전", "년전", "개월전"].contains(&rest.as_str())
    }
}

fn clamp(r: DayRange, today: i64) -> Option<DayRange> {
    if r.from > today {
        None
    } else {
        Some(DayRange { from: r.from, to: r.to.min(today) })
    }
}

/// "…3년전…"에서 [unit] 바로 앞의 숫자.
fn number_before(s: &str, unit: &str) -> Option<i64> {
    let i = s.find(unit)?;
    let head = &s[..i];
    let digits: String = head.chars().rev().take_while(|c| c.is_ascii_digit()).collect::<Vec<_>>().into_iter().rev().collect();
    if digits.is_empty() {
        return None;
    }
    digits.parse().ok()
}

fn leading_number(s: &str) -> Option<i64> {
    let d: String = s.chars().take_while(|c| c.is_ascii_digit()).collect();
    d.parse().ok()
}

#[cfg(test)]
mod numeric_tests {
    use super::*;

    #[test]
    fn numeric_dates_are_one_day() {
        let today = days_from_civil(2026, 11, 12);
        let d = days_from_civil(2025, 11, 7);
        for q in ["2025-11-07", "2025.11.7", "2025/11/07 강릉", "2025.11.07."] {
            assert_eq!(parse_korean_range(q, today), Some(DayRange::day(d)), "{q}");
        }
        assert_eq!(parse_korean_range("010-1234-5678", today), None);
    }
}


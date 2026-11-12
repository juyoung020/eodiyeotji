//! 늘 가던 날 테스트. 시연 1년치로 확인한다 (출장: 첫째 · 셋째 수요일 남양연구소, 둘째 목요일 판교, 첫 화요일 저녁 병원).
//! DB는 임시 파일, 끝나면 지운다.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

use super::*;
use crate::store::days_from_civil;

const KST: i32 = 540;

struct TempDb(PathBuf);

impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let db = TempDb(std::env::temp_dir().join(format!("eodi-routine-{name}-{}-{n}.db", std::process::id())));
        db.remove();
        db
    }

    fn remove(&self) {
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", self.0.display()));
        }
    }
}

impl Drop for TempDb {
    fn drop(&mut self) {
        self.remove();
    }
}

/// 한국 시각 정오 → UTC ms.
fn noon(y: i64, m: i64, d: i64) -> i64 {
    days_from_civil(y, m, d) * DAY_MS + (12 * 60 - KST as i64) * MIN_MS
}

/// 시연 기록을 `(y, m, d)`를 오늘로 넣는다 (어제까지 기록이 생긴다).
fn seeded(name: &str, y: i64, m: i64, d: i64) -> (TempDb, Arc<EodiStore>) {
    let db = TempDb::new(name);
    let store = EodiStore::open(db.0.to_string_lossy().into_owned(), KST).expect("open");
    assert!(store.seed_demo_data(noon(y, m, d)).unwrap() > 600);
    (db, store)
}

#[test]
fn words_for_the_day_and_the_clock() {
    let d = |m, dd| days_from_civil(2026, m, dd);
    // 2026-11-12 = 목요일
    assert_eq!(when_text(d(11, 12), d(11, 12)), "오늘");
    assert_eq!(when_text(d(11, 13), d(11, 12)), "내일");
    assert_eq!(when_text(d(11, 15), d(11, 12)), "이번 주 일요일", "한 주는 월요일부터 일요일까지");
    assert_eq!(when_text(d(11, 18), d(11, 12)), "다음 주 수요일");
    assert_eq!(clock_text(9 * 60 + 40), "오전 9시 반");
    assert_eq!(clock_text(13 * 60 + 50), "오후 2시");
    assert_eq!(clock_text(12 * 60), "오후 12시");
    assert_eq!(clock_text(10), "오전 12시");
    assert_eq!(
        routine_line("매달 첫째 · 셋째 수요일", "현대자동차 남양연구소", "다음 주 수요일"),
        "매달 첫째 · 셋째 수요일마다 현대자동차 남양연구소에 갔어요. 다음 주 수요일이 그날이에요."
    );
    assert_eq!(nth_in_month(d(11, 7)), 1);
    assert_eq!(nth_in_month(d(11, 8)), 2);
    assert_eq!(nth_in_month(d(11, 18)), 3);
}

#[test]
fn rules_from_visit_days() {
    let d = |m, dd| days_from_civil(2026, m, dd);
    let today = d(11, 12);
    let from = today - LOOKBACK_DAYS;
    // 첫째 · 셋째 수요일 (6월 ~ 11월 4일)
    let wed: BTreeSet<i64> =
        [(6, 3), (6, 17), (7, 1), (7, 15), (8, 5), (8, 19), (9, 2), (9, 16), (10, 7), (10, 21), (11, 4)].into_iter().map(|(m, dd)| d(m, dd)).collect();
    let rules = rules_of(&wed, from, today);
    assert_eq!(rules.iter().map(Rule::text).collect::<Vec<_>>(), ["매달 첫째 · 셋째 수요일"]);
    assert!(rules[0].matches(d(11, 18)) && !rules[0].matches(d(11, 11)) && !rules[0].matches(d(11, 19)));

    // 토요일마다 (한 번 빠짐) → 매주, 매달로 다시 세지 않는다
    let sat: BTreeSet<i64> = (0..10).filter(|k| *k != 3).map(|k| d(11, 7) - 7 * k).collect();
    assert_eq!(rules_of(&sat, from, today).iter().map(Rule::text).collect::<Vec<_>>(), ["매주 토요일"]);

    // 두 번 간 곳, 들쭉날쭉 간 곳은 규칙이 아니다
    let twice: BTreeSet<i64> = [d(10, 7), d(11, 4)].into_iter().collect();
    assert!(rules_of(&twice, from, today).is_empty());
    let random: BTreeSet<i64> = [d(7, 3), d(8, 11), d(9, 20), d(10, 1), d(10, 30)].into_iter().collect();
    assert!(rules_of(&random, from, today).is_empty());

    // 평일마다 가는 곳은 알려 줄 것이 없다
    let daily: BTreeSet<i64> = (from..today).filter(|&x| (1..=5).contains(&weekday(x))).collect();
    assert!(rules_of(&daily, from, today).is_empty());
}

#[test]
fn the_day_before_the_monthly_trip_says_tomorrow() {
    // 2026-11-11(수): 내일이 둘째 목요일 = 판교 가는 날
    let (_db, store) = seeded("tomorrow", 2026, 11, 11);
    let n = store.routine_nudge(noon(2026, 11, 11)).unwrap().expect("판교");
    assert_eq!(n.place.name, "판교테크노밸리 스타트업캠퍼스");
    assert_eq!((n.pattern.as_str(), n.day.as_str(), n.days_until), ("매달 둘째 목요일", "2026-11-12", 1));
    assert_eq!(n.when_text, "내일");
    assert_eq!(n.line, "매달 둘째 목요일마다 판교테크노밸리 스타트업캠퍼스에 갔어요. 내일이 그날이에요.");
    assert!(n.visits >= 4, "{}", n.visits);
    assert!(n.detail.starts_with("최근 6개월 동안 ") && n.detail.contains("쯤 도착"), "{}", n.detail);

    // 닫으면 그날 것은 안 나오고, 그다음으로 가까운 날(셋째 수요일 남양연구소)이 나온다
    store.dismiss_routine_nudge(n.key.clone(), n.day.clone()).unwrap();
    let next = store.routine_nudge(noon(2026, 11, 11)).unwrap().expect("남양연구소");
    assert_eq!(next.place.name, "현대자동차 남양연구소");
    assert_eq!((next.pattern.as_str(), next.day.as_str()), ("매달 첫째 · 셋째 수요일", "2026-11-18"));
    assert_eq!(next.when_text, "다음 주 수요일");
    assert!(store.dismiss_routine_nudge("x".into(), "어제".into()).is_err());
}

#[test]
fn nothing_when_no_routine_day_is_near() {
    // 2026-11-20(금): 앞으로 7일(~11/27) 안에 첫째 · 셋째 수요일, 둘째 목요일, 첫 화요일이 없다.
    // 넷째 목요일(11/26) 대전은 분기 마지막 달에만 가서 규칙이 아니다.
    let (_db, store) = seeded("none", 2026, 11, 20);
    assert_eq!(store.routine_nudge(noon(2026, 11, 20)).unwrap(), None);
    // 기록이 없으면 없다
    let db = TempDb::new("empty");
    let empty = EodiStore::open(db.0.to_string_lossy().into_owned(), KST).expect("open");
    assert_eq!(empty.routine_nudge(noon(2026, 11, 20)).unwrap(), None);
}

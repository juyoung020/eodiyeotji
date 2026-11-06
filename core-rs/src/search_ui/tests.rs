//! 검색 화면 테스트. 시연용 1년치 기록 + 시연 커뮤니티를 한 번 만들어 같이 쓴다 (읽기만 하는 테스트).

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::{Arc, OnceLock};

use super::*;
use crate::store::{days_from_civil as civil, DAY_MS};

const KST: i32 = 540;
const MIN: i64 = 60_000;

/// 한국 시각 → UTC ms.
fn kst(y: i64, m: i64, d: i64, h: i64) -> i64 {
    civil(y, m, d) * DAY_MS + h * 60 * MIN - KST as i64 * MIN
}

/// 2026-11-06(금) 정오.
fn now() -> i64 {
    kst(2026, 11, 6, 12)
}

fn today() -> i64 {
    civil(2026, 11, 6)
}

struct TempDb(PathBuf);

impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let db = TempDb(std::env::temp_dir().join(format!("eodi-search-{name}-{}-{n}.db", std::process::id())));
        db.remove();
        db
    }
    fn open(&self) -> Arc<EodiStore> {
        EodiStore::open(self.0.to_string_lossy().into_owned(), KST).expect("open")
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

/// 시연 기록 + 커뮤니티 (테스트끼리 같이 쓴다; 읽기만 한다).
fn demo() -> &'static Arc<EodiStore> {
    static STORE: OnceLock<(TempDb, Arc<EodiStore>)> = OnceLock::new();
    &STORE
        .get_or_init(|| {
            let db = TempDb::new("demo");
            let store = db.open();
            assert!(store.seed_demo_data(now()).unwrap() > 0);
            assert!(store.seed_community_demo(now()).unwrap() > 0);
            (db, store)
        })
        .1
}

fn find(text: &str) -> SearchAnswer {
    demo().search_typed(text.into(), SearchScope::Records, SearchFilter::none(), now()).unwrap()
}

fn group(a: &SearchAnswer, kind: SearchGroupKind) -> Option<&SearchGroup> {
    a.groups.iter().find(|g| g.kind == kind)
}

fn s(v: &[&str]) -> Vec<String> {
    v.iter().map(|x| x.to_string()).collect()
}

// ---- 말 풀기 ----

#[test]
fn keywords_are_not_questions() {
    let q = parse_query("바다", today());
    assert_eq!(q.terms, s(&["바다"]));
    assert!(!q.question && q.range.is_none() && q.single.is_none());
    let q = parse_query("순두부", today());
    assert_eq!(q.terms, s(&["순두부"]));
    assert!(!q.question);
}

#[test]
fn date_words_filter_and_are_not_terms() {
    let q = parse_query("지난주 카페", today());
    assert_eq!(q.terms, s(&["카페"]));
    assert!(!q.question);
    // 2026-11-06은 금요일 → 지난주 = 10월 26일(월) ~ 11월 1일(일)
    assert_eq!(q.range, Some(DayRange { from: civil(2026, 10, 26), to: civil(2026, 11, 1) }));
    assert_eq!(range_label(q.range.unwrap(), today()), "10월 26일 ~ 11월 1일");

    let q = parse_query("작년 이맘때", today());
    assert!(q.terms.is_empty(), "{q:?}");
    let c = civil(2025, 11, 6);
    assert_eq!(q.range, Some(DayRange { from: c - 14, to: c + 14 }));
    assert_eq!(range_label(q.range.unwrap(), today()), "2025년 10월 23일 ~ 11월 20일");
}

#[test]
fn sentences_go_to_the_agent() {
    let q = parse_query("작년 여름 바다 어디였지?", today());
    assert!(q.question);
    assert!(q.terms.contains(&"바다".to_string()), "{q:?}");
    assert_eq!(q.range, Some(DayRange { from: civil(2025, 6, 1), to: civil(2025, 8, 31) }));
    let q = parse_query("엄마랑 갔던 식당", today());
    assert!(q.question);
    assert!(q.terms.contains(&"식당".to_string()), "{q:?}");
    assert!(parse_query("근처 드라이브 코스 추천해줘", today()).question);
}

#[test]
fn one_letter_while_typing() {
    let q = parse_query("순", today());
    assert_eq!(q.single.as_deref(), Some("순"));
    assert!(q.terms.is_empty());
    let q = parse_query("산", today());
    assert_eq!(q.terms, s(&["산"]), "한 글자 뜻말은 낱말");
    assert!(q.single.is_none());
}

#[test]
fn date_lines_read_naturally() {
    let t = today();
    assert_eq!(when_text(t, t), "오늘 (금)");
    assert_eq!(when_text(t - 1, t), "어제 (목)");
    assert_eq!(when_text(t - 3, t), "3일 전 (화)");
    assert_eq!(when_text(civil(2026, 8, 14), t), "8월 14일 (금)");
    assert_eq!(when_text(civil(2025, 11, 6), t), "작년 11월 6일 (목)");
    assert_eq!(when_text(civil(2024, 5, 5), t), "2024년 5월 5일 (일)");
}

#[test]
fn chip_periods_and_text_dates_intersect() {
    let t = today();
    let mut f = SearchFilter::none();
    f.period = SearchPeriod::ThisMonth;
    let chip = period_range(&f, t);
    assert_eq!(chip, Some((Some(civil(2026, 11, 1)), Some(t))));
    // 지난주(10/26~11/1) ∩ 이번 달(11/1~) = 11월 1일 하루
    let r = parse_query("지난주", t).range;
    assert_eq!(combine_ranges(r, chip), Ok((Some(civil(2026, 11, 1)), Some(civil(2026, 11, 1)))));
    // 작년 여름 ∩ 이번 달 = 없음
    assert!(combine_ranges(parse_query("작년 여름", t).range, chip).is_err());
    f.period = SearchPeriod::Custom;
    f.from_day = Some("2026-10-01".into());
    f.to_day = None;
    assert_eq!(period_range(&f, t), Some((Some(civil(2026, 10, 1)), None)));
}

// ---- 내 기록 찾기 ----

#[test]
fn sundubu_finds_the_place_and_the_gangneung_day() {
    let a = find("순두부");
    let places = group(&a, SearchGroupKind::Places).expect("장소");
    assert_eq!(places.rows[0].title, "초당할머니순두부");
    assert_eq!(places.rows[0].kind, SearchRowKind::Place);
    assert!(places.rows[0].place.is_some() && places.rows[0].day.is_some());
    let days = group(&a, SearchGroupKind::Days).expect("날짜");
    assert_eq!(days.rows[0].day.as_deref(), Some("2025-11-06"), "1년 전 강릉 여행: {:?}", days.rows[0]);
    assert!(days.rows[0].date_line.starts_with("작년 11월 6일"), "{}", days.rows[0].date_line);
    assert_eq!(a.ask_ai, Some(AskAi { text: "순두부".into(), primary: false }));
    assert_eq!(a.groups[0].kind, SearchGroupKind::Places, "낱말이 있으면 장소가 먼저");
}

#[test]
fn sea_uses_synonyms_for_places() {
    let a = find("바다");
    let places = group(&a, SearchGroupKind::Places).expect("장소");
    let names: Vec<&str> = places.rows.iter().map(|r| r.title.as_str()).collect();
    assert!(names.iter().any(|n| n.contains("해변") || n.contains("해수욕장")), "{names:?}");
    // 바다 코스 (커뮤니티)도 몇 개
    assert!(group(&a, SearchGroupKind::Community).is_some_and(|g| g.rows.len() <= 3));
}

#[test]
fn last_month_cafe_stays_in_last_month() {
    let a = find("지난달 카페");
    assert_eq!(a.date_label.as_deref(), Some("10월 1일 ~ 10월 31일"));
    for g in &a.groups {
        for r in g.rows.iter().filter(|r| r.kind != SearchRowKind::Course && r.kind != SearchRowKind::Review) {
            let d = parse_day(r.day.as_deref().unwrap()).unwrap();
            assert!((civil(2026, 10, 1)..=civil(2026, 10, 31)).contains(&d), "{r:?}");
        }
    }
    let places = group(&a, SearchGroupKind::Places).expect("지난달 들른 카페");
    assert!(places.rows.iter().all(|r| r.subtitle.contains("카페")), "{:?}", places.rows);
}

#[test]
fn date_only_lists_days_first() {
    let a = find("지난주");
    assert!(a.terms.is_empty());
    assert_eq!(a.groups[0].kind, SearchGroupKind::Days, "날짜만 치면 날짜가 먼저");
    assert!(!a.groups[0].rows.is_empty());
    // 최신순
    let days: Vec<&str> = a.groups[0].rows.iter().map(|r| r.day.as_deref().unwrap()).collect();
    let mut sorted = days.clone();
    sorted.sort_by(|a, b| b.cmp(a));
    assert_eq!(days, sorted);
}

#[test]
fn question_offers_the_agent_and_still_searches() {
    // 시연 기록은 1년치라 올여름(2026)을 묻는다
    let a = find("여름 바다 어디였지?");
    assert_eq!(a.ask_ai.as_ref().map(|x| x.primary), Some(true));
    let days = group(&a, SearchGroupKind::Days).expect("여름 바다");
    for r in &days.rows {
        let d = parse_day(r.day.as_deref().unwrap()).unwrap();
        assert!((civil(2026, 6, 1)..=civil(2026, 8, 31)).contains(&d), "{r:?}");
    }
}

#[test]
fn one_letter_matches_place_names() {
    let a = find("순");
    let places = group(&a, SearchGroupKind::Places).expect("장소");
    assert!(places.rows.iter().all(|r| r.title.contains('순')), "{:?}", places.rows);
    assert!(group(&a, SearchGroupKind::Days).is_none());
}

#[test]
fn kind_chip_keeps_only_that_kind() {
    let mut f = SearchFilter::none();
    f.kinds = vec![EpisodeKind::Trip];
    let a = demo().search_typed("카페".into(), SearchScope::Records, f.clone(), now()).unwrap();
    let days = group(&a, SearchGroupKind::Days).expect("여행 중 카페");
    assert!(days.rows.iter().all(|r| r.date_line.ends_with("여행")), "{:?}", days.rows);
    assert!(group(&a, SearchGroupKind::Community).is_none(), "칩을 고르면 내 기록만");
    // 글 없이 칩만
    let a = demo().search_typed(String::new(), SearchScope::Records, f, now()).unwrap();
    assert!(group(&a, SearchGroupKind::Days).is_some());
    assert!(a.ask_ai.is_none());
}

#[test]
fn first_visit_chip() {
    let mut f = SearchFilter::none();
    f.first_visit_only = true;
    let a = demo().search_typed("카페".into(), SearchScope::Records, f, now()).unwrap();
    for r in a.groups.iter().flat_map(|g| g.rows.iter()) {
        assert!(r.first_visit, "{r:?}");
    }
}

#[test]
fn empty_text_without_chips_is_empty() {
    let a = find("   ");
    assert!(a.groups.is_empty() && a.ask_ai.is_none() && a.total == 0);
}

#[test]
fn search_is_fast() {
    for text in ["바다", "순두부", "지난주 카페", "작년 여름 바다 어디였지?", "카"] {
        let a = find(text);
        // 디버그 빌드 기준으로도 넉넉히 (릴리즈는 수 ms)
        assert!(a.elapsed_micros < 300_000, "{text}: {}µs", a.elapsed_micros);
        eprintln!("검색 {text:?}: {}µs, {}건", a.elapsed_micros, a.total);
    }
}

// ---- 커뮤니티 찾기 ----

#[test]
fn community_tabs_search_their_own_items() {
    let st = demo();
    let a = st.search_typed("바다".into(), SearchScope::Courses, SearchFilter::none(), now()).unwrap();
    let g = &a.groups[0];
    assert_eq!(a.groups.len(), 1);
    assert!(!g.rows.is_empty() && g.rows.iter().all(|r| r.course.is_some()));

    let a = st.search_typed("순두부".into(), SearchScope::Reviews, SearchFilter::none(), now()).unwrap();
    let g = &a.groups[0];
    assert!(g.rows[0].title.contains("순두부"), "{:?}", g.rows[0]);
    assert!(g.rows[0].review_place.is_some());

    let posts = st.board_posts(CommunitySort::Popular, 1).unwrap();
    // "어디였지로 기록한 코스 공유해 보신 분?"
    let word = "코스 공유".to_string();
    let a = st.search_typed(word.clone(), SearchScope::Board, SearchFilter::none(), now()).unwrap();
    assert!(a.groups[0].rows.iter().any(|r| r.post.as_ref().is_some_and(|p| p.id == posts[0].id)), "{word}");
    // 커뮤니티에서는 기간 칩을 쓰지 않는다
    let mut f = SearchFilter::none();
    f.kinds = vec![EpisodeKind::Trip];
    let b = st.search_typed("바다".into(), SearchScope::Courses, f, now()).unwrap();
    assert_eq!(b.total, st.search_typed("바다".into(), SearchScope::Courses, SearchFilter::none(), now()).unwrap().total);
}

// ---- 추천 · 최근 검색어 ----

#[test]
fn suggestions_have_base_chips_and_frequent_places() {
    let sg = demo().search_suggestions().unwrap();
    let texts: Vec<&str> = sg.chips.iter().map(|c| c.text.as_str()).collect();
    assert_eq!(&texts[..5], &["바다", "카페", "지난주", "휴게소", "작년 이맘때"]);
    let places: Vec<&str> = sg.chips.iter().filter(|c| c.kind == SearchChipKind::Place).map(|c| c.text.as_str()).collect();
    assert_eq!(places.len(), 3, "{texts:?}");
    assert!(!places.contains(&"응봉동 현대아파트") && !places.contains(&"센터원빌딩"), "집 · 회사는 빼고: {places:?}");
}

#[test]
fn recent_searches_keep_newest_twenty() {
    let db = TempDb::new("recent");
    let st = db.open();
    assert!(st.search_suggestions().unwrap().recent.is_empty());
    st.remember_search("  바다  ".into(), 1).unwrap();
    st.remember_search("카페".into(), 2).unwrap();
    st.remember_search("바다".into(), 3).unwrap();
    st.remember_search("   ".into(), 4).unwrap();
    assert_eq!(st.search_suggestions().unwrap().recent, s(&["바다", "카페"]));
    st.forget_search("카페".into()).unwrap();
    assert_eq!(st.search_suggestions().unwrap().recent, s(&["바다"]));
    for i in 0..30 {
        st.remember_search(format!("말{i}"), 10 + i).unwrap();
    }
    let n: i64 = st.lock().unwrap().query_row("SELECT count(*) FROM search_recent", [], |r| r.get(0)).unwrap();
    assert_eq!(n, 20);
    assert_eq!(st.search_suggestions().unwrap().recent[0], "말29");
    assert_eq!(st.search_suggestions().unwrap().recent.len(), 8);
    st.clear_recent_searches().unwrap();
    assert!(st.search_suggestions().unwrap().recent.is_empty());
}

#[test]
fn v5_database_gets_recent_table() {
    let db = TempDb::new("v5");
    {
        let st = db.open();
        let c = st.lock().unwrap();
        c.execute_batch(crate::edits::DROP_V7).unwrap();
        c.execute_batch("DROP TABLE search_recent;").unwrap();
        c.pragma_update(None, "user_version", 5).unwrap();
    }
    let st = db.open();
    let v: i32 = st.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, crate::store::SCHEMA_VERSION);
    st.remember_search("바다".into(), 1).unwrap();
    assert_eq!(st.search_suggestions().unwrap().recent, s(&["바다"]));
}

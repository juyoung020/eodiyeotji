//! 시연 두 장면 (시연 1년치 기록, LLM 없이 빠른 길로만).
//! 1. 이어 묻기: "작년 강릉 횟집 어디였지" → "거기 몇 명이서 갔어" → "거기로 안내해 줘"
//! 2. 한 문장 코스: "작년 강릉 여행 그대로, 순두부집만 빼고 안내해 줘"

use std::sync::{Arc, Mutex};

use super::*;

#[derive(Default)]
struct Rec {
    text: Mutex<String>,
    cards: Mutex<Vec<(MemoryHit, Option<CourseRoute>)>>,
    proposals: Mutex<Vec<NavProposal>>,
    confirmed: Mutex<Vec<NavAction>>,
    done: Mutex<Option<AgentDone>>,
}

impl AgentListener for Rec {
    fn on_answer_delta(&self, t: String) {
        self.text.lock().unwrap().push_str(&t);
    }
    fn on_recommendation(&self, h: MemoryHit, c: Option<CourseRoute>) {
        self.cards.lock().unwrap().push((h, c));
    }
    fn on_course_recommendations(&self, _c: Vec<DriveRouteRecommendation>, _f: u32) {}
    fn on_memory_candidates(&self, _c: Vec<MemoryHit>, _f: u32) {}
    fn on_action_proposal(&self, p: NavProposal) {
        self.proposals.lock().unwrap().push(p);
    }
    fn on_action_confirmed(&self, _: String, a: NavAction) {
        self.confirmed.lock().unwrap().push(a);
    }
    fn on_done(&self, d: AgentDone) {
        *self.done.lock().unwrap() = Some(d);
    }
    fn on_error(&self, m: String) {
        self.text.lock().unwrap().push_str(&m);
    }
}

impl Rec {
    fn text(&self) -> String {
        self.text.lock().unwrap().clone()
    }
    fn source(&self) -> AnswerSource {
        self.done.lock().unwrap().as_ref().map(|d| d.source).expect("on_done")
    }
}

const NOW: i64 = 1_794_013_200_000; // 2026-11-07 10:00 KST → 1년 전 강릉 여행 = 2025-11-07

fn agent(name: &str) -> Arc<MemoryAgent> {
    let path = crate::test_tmp::dir().join(format!("eodi-scene-{name}-{}.db", std::process::id()));
    let _ = std::fs::remove_file(&path);
    let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
    s.seed_demo_data(NOW).unwrap();
    let cfg = LlmConfig { base_url: String::new(), model: String::new(), api_key: String::new(), connect_timeout_ms: 500, read_timeout_ms: 500 };
    MemoryAgent::new(s, cfg)
}

fn ask(a: &MemoryAgent, q: &str) -> Arc<Rec> {
    let r = Arc::new(Rec::default());
    a.ask(q.into(), AgentContext { now_ms: NOW, tz_offset_minutes: 540, current_location: None, is_driving: false }, r.clone());
    r
}

fn names(stops: &[crate::memory::StopFact]) -> Vec<&str> {
    stops.iter().map(|s| s.name.as_str()).collect()
}

#[test]
fn words_find_skip_and_head_count() {
    let p = words::parse("작년 강릉 여행 그대로, 순두부집만 빼고 안내해 줘");
    assert_eq!(p.skip, ["순두부"]);
    assert!(!p.terms.iter().any(|w| w.contains("빼")), "{:?}", p.terms);
    assert_eq!(words::parse("휴게소는 제외하고 가자").skip, ["휴게소"]);
    assert_eq!(words::parse("카페빼고 안내해줘").skip, ["카페"]);
    assert!(words::parse("거기 말고 다른 데").skip.is_empty(), "'말고'는 후보 바꾸기");
    for q in ["거기 몇 명이서 갔어", "그때 몇명이었지", "누구랑 갔더라"] {
        assert!(words::parse(q).head_count, "{q}");
    }
    assert!(!words::parse("작년 강릉 횟집 어디였지").head_count);
}

/// 장면 1: 세 번 이어 물어 안내까지. 셋 다 LLM 없이 기기 안에서 바로 답한다.
#[test]
fn follow_up_questions_reach_navigation() {
    let a = agent("follow");
    let r = ask(&a, "작년 강릉 횟집 어디였지");
    let (hit, _) = r.cards.lock().unwrap().first().cloned().expect("카드");
    assert_eq!(hit.best_stop.as_ref().map(|s| s.name.as_str()), Some("안목항 바다횟집"), "{}", r.text());
    assert!(r.text().contains("안목항 바다횟집이에요"), "{}", r.text());
    assert!(r.text().ends_with("그곳으로 안내할까요?"), "{}", r.text());
    let p = r.proposals.lock().unwrap().last().cloned().expect("안내 제안");
    assert!(matches!(&p.action, NavAction::Place { stop } if stop.name == "안목항 바다횟집"), "{p:?}");
    assert_eq!(r.source(), AnswerSource::FastPath);

    let r = ask(&a, "거기 몇 명이서 갔어");
    assert!(r.text().starts_with("안목항 바다횟집엔 4명이서 가셨어요."), "{}", r.text());
    assert!(r.text().ends_with("거기로 안내할까요?"), "{}", r.text());
    let (hit, _) = r.cards.lock().unwrap().first().cloned().expect("카드를 다시 보인다");
    assert_eq!(hit.people, Some(4), "카드에 인원");
    assert_eq!(r.source(), AnswerSource::FastPath);

    let r = ask(&a, "거기로 안내해 줘");
    let done = r.confirmed.lock().unwrap().clone();
    assert!(matches!(done.as_slice(), [NavAction::Place { stop }] if stop.name == "안목항 바다횟집"), "{done:?} / {}", r.text());
}

/// 장면 2: 한 문장으로 코스. 그날 순서 그대로에서 순두부집만 빠지고, 뺀 곳은 지도에 흐리게 남는다.
#[test]
fn one_sentence_course_skips_a_stop() {
    let a = agent("skip");
    let r = ask(&a, "작년 강릉 여행 그대로, 순두부집만 빼고 안내해 줘");
    let (_, course) = r.cards.lock().unwrap().last().cloned().expect("카드");
    let course = course.expect("코스");
    assert_eq!(names(&course.stops), ["횡성휴게소", "경포해변", "안목해변 카페거리", "안목항 바다횟집"], "{}", r.text());
    assert_eq!(names(&course.skipped), ["초당할머니순두부"]);
    assert!(r.text().contains("초당할머니순두부를 빼고 4곳으로 다시 짰어요"), "{}", r.text());
    assert!(r.text().ends_with("이대로 안내할까요?"), "{}", r.text());
    assert_eq!(r.source(), AnswerSource::FastPath);
    let p = r.proposals.lock().unwrap().last().cloned().expect("안내 제안");
    let NavAction::Course { legs, .. } = &p.action else { panic!("{p:?}") };
    let all: Vec<&str> = legs.iter().flat_map(|l| l.waypoints.iter().chain(std::iter::once(&l.destination))).map(|s| s.name.as_str()).collect();
    assert_eq!(all, ["횡성휴게소", "경포해변", "안목해변 카페거리", "안목항 바다횟집"]);

    let r = ask(&a, "응");
    assert!(matches!(r.confirmed.lock().unwrap().as_slice(), [NavAction::Course { .. }]));

    // 코스에 없는 곳을 빼라면 그렇다고 말하고 그대로 안내를 묻는다
    a.new_session();
    let r = ask(&a, "작년 강릉 여행 그대로, 감자탕집만 빼고 안내해 줘");
    assert!(r.text().contains("맞는 곳이 없어요"), "{}", r.text());
}

/// 코스 카드 뒤 "거기 몇 명이서 갔어": 다시 묻는 말이 버튼("이 코스로 안내")과 맞게 "그 코스로".
#[test]
fn head_count_after_a_course_asks_about_the_course() {
    let a = agent("headcount-course");
    let r = ask(&a, "작년 강릉 여행 어디 갔었지");
    let p = r.proposals.lock().unwrap().last().cloned();
    assert!(matches!(p.as_ref().map(|p| &p.action), Some(NavAction::Course { .. })), "코스 제안: {p:?}");
    let r = ask(&a, "거기 몇 명이서 갔어");
    assert!(r.text().ends_with("그 코스로 안내할까요?"), "{}", r.text());
}

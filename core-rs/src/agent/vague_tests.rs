//! 흐릿한 질문 → 후보 여러 개 (시연 1년치 기록, LLM 없이).
//! "옛날에 갔던 바다", "예전에 갔던 감자탕집", "애들이랑 갔던 계곡"처럼 날짜도 이름도 없는 말로 찾고,
//! 후보를 1·2·3번으로 보인 뒤 "2번", "그거 말고 다른 거"로 고른다.

use std::sync::{Arc, Mutex};

use super::*;

#[derive(Default)]
struct Rec {
    text: Mutex<String>,
    cards: Mutex<Vec<MemoryHit>>,
    proposals: Mutex<Vec<NavProposal>>,
}

impl AgentListener for Rec {
    fn on_answer_delta(&self, t: String) {
        self.text.lock().unwrap().push_str(&t);
    }
    fn on_recommendation(&self, h: MemoryHit, _c: Option<CourseRoute>) {
        self.cards.lock().unwrap().push(h);
    }
    fn on_course_recommendations(&self, _c: Vec<DriveRouteRecommendation>, _f: u32) {}
    fn on_action_proposal(&self, p: NavProposal) {
        self.proposals.lock().unwrap().push(p);
    }
    fn on_action_confirmed(&self, _: String, _: NavAction) {}
    fn on_done(&self, _d: AgentDone) {}
    fn on_error(&self, m: String) {
        self.text.lock().unwrap().push_str(&m);
    }
}

impl Rec {
    /// 받은 카드의 기억 id (처음 받은 순서, 같은 기억은 한 번 — 화면이 같은 기억을 제자리에서 바꿔 끼우는 것과 같다)
    fn order(&self) -> Vec<i64> {
        let mut ids: Vec<i64> = vec![];
        for h in self.cards.lock().unwrap().iter() {
            if !ids.contains(&h.episode.facts.id) {
                ids.push(h.episode.facts.id);
            }
        }
        ids
    }
    fn first_place(&self) -> String {
        self.cards.lock().unwrap().first().and_then(|h| h.best_stop.as_ref().map(|s| s.name.clone())).unwrap_or_default()
    }
}

const NOW: i64 = 1_794_013_200_000; // 2026-11-07 10:00 KST

fn agent(name: &str) -> Arc<MemoryAgent> {
    let path = std::env::temp_dir().join(format!("eodi-vague-{name}-{}.db", std::process::id()));
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

#[test]
fn vague_words_find_places_without_dates_or_names() {
    let a = agent("words");
    for (q, place) in [
        ("예전에 갔던 감자탕집 어디였지", "태조감자국"),
        ("전에 먹었던 국밥집", "광화문국밥"),
        ("애들이랑 갔던 계곡", "용추계곡"),
        ("그 산 어디였더라", "북한산 우이탐방지원센터"),
    ] {
        a.new_session();
        let r = ask(&a, q);
        assert_eq!(r.first_place(), place, "{q} → {}", r.text.lock().unwrap());
    }
}

#[test]
fn vague_question_shows_numbered_candidates_and_picks_by_number() {
    let a = agent("pick");
    let r = ask(&a, "옛날에 놀러갔던 바다 어디였지");
    let order = r.order();
    assert!(order.len() >= 2 && order.len() <= runner::MAX_CANDIDATES, "후보 {order:?}");
    let text = r.text.lock().unwrap().clone();
    assert!(text.contains("비슷한 기억도 있어요: 2번"), "{text}");
    // "옛날에": 최근 한 달 기록(11월 7일 경포)보다 지난 기록이 1번
    let first_day = r.cards.lock().unwrap()[0].episode.facts.day.clone();
    assert!(first_day.as_str() < "2026-10-08", "1번 {first_day}");
    // 후보는 모두 바다 쪽 (해수욕장)
    assert!(r.cards.lock().unwrap().iter().all(|h| h.best_stop.as_ref().is_some_and(|s| s.category == "해수욕장")));

    // "2번" → 2번 후보가 앞 카드, 안내 제안도 그곳
    let r2 = ask(&a, "2번");
    assert_eq!(r2.order()[0], order[1]);
    assert_eq!(r2.order().len(), order.len(), "다른 후보도 같이 다시 보인다");
    // 고른 후보가 1번이 되고 나머지가 2·3번 (화면 줄 번호와 같다)
    assert!(r2.text.lock().unwrap().contains("비슷한 기억도 있어요: 2번"), "{}", r2.text.lock().unwrap());
    assert_eq!(r2.order()[1], order[2 % order.len()]);
    assert!(!r2.proposals.lock().unwrap().is_empty());

    // "그거 말고 다른 거" → 지금(2번) 다음 후보
    let r3 = ask(&a, "그거 말고 다른 거");
    assert_eq!(r3.order()[0], order[2 % order.len()]);

    // 없는 번호
    let r4 = ask(&a, "5번");
    assert!(r4.text.lock().unwrap().contains(&format!("{}번까지", order.len())));

    // 새 검색어가 있으면 고르는 말이 아니다
    let r5 = ask(&a, "감자탕집은 어디였지");
    assert_eq!(r5.first_place(), "태조감자국");
}

#[test]
fn matched_ai_tags_explain_the_hit() {
    let a = agent("tags");
    let r = ask(&a, "애들이랑 갔던 계곡");
    let first = r.cards.lock().unwrap().first().cloned().unwrap();
    assert!(first.matched_tags.contains(&"계곡".to_string()), "{:?}", first.matched_tags);
}

#[test]
fn shortlist_keeps_close_scores_and_distinct_places() {
    let a = agent("short");
    let r = ask(&a, "옛날에 놀러갔던 바다 어디였지");
    let cards = r.cards.lock().unwrap().clone();
    let mut places: Vec<String> = cards.iter().filter_map(|h| h.best_stop.as_ref().map(|s| s.name.clone())).collect();
    places.dedup();
    let unique: std::collections::HashSet<_> = places.iter().collect();
    assert_eq!(unique.len(), r.order().len(), "같은 곳은 후보에 한 번만: {places:?}");
}

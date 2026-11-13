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
    cands: Mutex<(Vec<MemoryHit>, u32)>,
}

impl AgentListener for Rec {
    fn on_answer_delta(&self, t: String) {
        self.text.lock().unwrap().push_str(&t);
    }
    fn on_recommendation(&self, h: MemoryHit, _c: Option<CourseRoute>) {
        self.cards.lock().unwrap().push(h);
    }
    fn on_course_recommendations(&self, _c: Vec<DriveRouteRecommendation>, _f: u32) {}
    fn on_memory_candidates(&self, c: Vec<MemoryHit>, f: u32) {
        *self.cands.lock().unwrap() = (c, f);
    }
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
    /// 후보 칸의 기억 id (번호 순서)와 지금 고른 자리
    fn order(&self) -> Vec<i64> {
        self.cands.lock().unwrap().0.iter().map(|h| h.episode.facts.id).collect()
    }
    fn focus(&self) -> u32 {
        self.cands.lock().unwrap().1
    }
    fn first_id(&self) -> i64 {
        self.cards.lock().unwrap().first().map(|h| h.episode.facts.id).unwrap_or_default()
    }
    fn first_place(&self) -> String {
        self.cards.lock().unwrap().first().and_then(|h| h.best_stop.as_ref().map(|s| s.name.clone())).unwrap_or_default()
    }
}

const NOW: i64 = 1_794_013_200_000; // 2026-11-07 10:00 KST

fn agent(name: &str) -> Arc<MemoryAgent> {
    agent_at(name, NOW)
}

/// 시연 기록을 [now] 기준으로 심은 에이전트 (시연 기록은 "오늘"에 맞춰 날짜가 정해진다).
fn agent_at(name: &str, now: i64) -> Arc<MemoryAgent> {
    let path = std::env::temp_dir().join(format!("eodi-vague-{name}-{now}-{}.db", std::process::id()));
    let _ = std::fs::remove_file(&path);
    let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
    s.seed_demo_data(now).unwrap();
    let cfg = LlmConfig { base_url: String::new(), model: String::new(), api_key: String::new(), connect_timeout_ms: 500, read_timeout_ms: 500 };
    MemoryAgent::new(s, cfg)
}

fn ask(a: &MemoryAgent, q: &str) -> Arc<Rec> {
    ask_at(a, q, NOW)
}

fn ask_at(a: &MemoryAgent, q: &str, now: i64) -> Arc<Rec> {
    let r = Arc::new(Rec::default());
    a.ask(q.into(), AgentContext { now_ms: now, tz_offset_minutes: 540, current_location: None, is_driving: false }, r.clone());
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
    assert!(order.len() >= 3, "맞는 기억은 모두 후보: {order:?}");
    assert_eq!((r.focus(), r.first_id()), (0, order[0]), "1번이 자세한 카드");
    // 목록은 화면 후보 칸이 보이고, 말은 짧게 한 줄
    let text = r.text.lock().unwrap().clone();
    assert!(text.starts_with(&format!("기억 {}개를 찾았어요. 1번은", order.len())), "{text}");
    // "옛날에": 최근 한 달 기록(11월 7일 경포)보다 지난 기록이 1번
    let cands = r.cands.lock().unwrap().0.clone();
    assert!(cands[0].episode.facts.day.as_str() < "2026-10-08", "1번 {}", cands[0].episode.facts.day);
    // 후보는 모두 바다 쪽 (해수욕장)
    assert!(cands.iter().all(|h| h.best_stop.as_ref().is_some_and(|s| s.category == "해수욕장")));

    // "2번" → 같은 번호 그대로, 2번이 자세한 카드 · 안내 제안
    let r2 = ask(&a, "2번");
    assert_eq!((r2.order(), r2.focus(), r2.first_id()), (order.clone(), 1, order[1]));
    assert!(r2.text.lock().unwrap().starts_with("2번은 "), "{}", r2.text.lock().unwrap());
    assert!(!r2.proposals.lock().unwrap().is_empty());

    // "그거 말고 다른 거" → 지금(2번) 다음 후보
    let r3 = ask(&a, "그거 말고 다른 거");
    assert_eq!(r3.focus() as usize, 2 % order.len());

    // 없는 번호
    let r4 = ask(&a, "50번");
    assert!(r4.text.lock().unwrap().contains(&format!("{}번까지", order.len())));

    // 새 검색어가 있으면 고르는 말이 아니다
    let r5 = ask(&a, "감자탕집은 어디였지");
    assert_eq!(r5.first_place(), "태조감자국");
}

#[test]
fn tapping_a_candidate_switches_the_proposal_silently() {
    let a = agent("tap");
    let r = ask(&a, "옛날에 놀러갔던 바다 어디였지");
    let order = r.order();
    let ctx = AgentContext { now_ms: NOW, tz_offset_minutes: 540, current_location: None, is_driving: false };
    let choice = a.select_candidate(order[1], ctx.clone()).expect("후보");
    assert!(choice.line.starts_with("2번은 "), "{}", choice.line);
    let p = choice.proposal.expect("안내 제안");
    assert_eq!(a.pending_proposal().map(|x| x.id), Some(p.id.clone()), "누른 후보가 대기 중인 제안");
    assert!(a.select_candidate(-1, ctx).is_none(), "후보가 아닌 기억");
    // 이어서 "다른 거"는 누른 후보(2번) 다음
    let r2 = ask(&a, "다른 거");
    assert_eq!(r2.focus() as usize, 2 % order.len());
}

#[test]
fn matched_ai_tags_explain_the_hit() {
    let a = agent("tags");
    let r = ask(&a, "애들이랑 갔던 계곡");
    let first = r.cards.lock().unwrap().first().cloned().unwrap();
    assert!(first.matched_tags.contains(&"계곡".to_string()), "{:?}", first.matched_tags);
}

/// UX 리뷰 6: "옛날에 놀러 갔던 바다" → 1년 전 경포해변(README 대표 장면)이 1번 (4개월 전 을왕리보다 위).
#[test]
fn long_ago_puts_the_year_old_gyeongpo_trip_first() {
    // 시연 기록은 "오늘"에 맞춰 날짜가 정해진다 → 두 날짜(테스트 기준일·리뷰한 날)로 확인
    for (now, day) in [(NOW, "2025-11-07"), (NOW + 4 * 86_400_000, "2025-11-11")] {
        let a = agent_at("longago", now);
        let r = ask_at(&a, "옛날에 놀러 갔던 바다", now);
        let cands = r.cands.lock().unwrap().0.clone();
        assert_eq!(cands[0].episode.facts.day, day);
        assert_eq!(r.first_place(), "경포해변", "{}", r.text.lock().unwrap());
        assert!(r.text.lock().unwrap().contains("작년 11월"), "{}", r.text.lock().unwrap());
        // 나머지(덜 오래된 바다)도 빠지지 않는다
        assert!(cands.len() >= 3, "{}", cands.len());
    }
}

/// UX 리뷰 6: 같은 곳을 다른 날 다녀온 기억은 후보 하나로 ("2번 다녀옴 · 6/6, 8/22").
#[test]
fn same_place_on_different_days_is_one_candidate() {
    let a = agent("merge");
    let r = ask(&a, "옛날에 놀러 갔던 바다");
    let cands = r.cands.lock().unwrap().0.clone();
    let names: Vec<String> = cands.iter().filter_map(|h| h.best_stop.as_ref().map(|s| s.name.clone())).collect();
    let unique: std::collections::HashSet<_> = names.iter().collect();
    assert_eq!(unique.len(), names.len(), "후보마다 다른 곳: {names:?}");
    let eulwang = cands.iter().find(|h| h.best_stop.as_ref().is_some_and(|s| s.name == "을왕리해수욕장")).expect("을왕리");
    assert_eq!(eulwang.visit_days.len(), 2, "{:?}", eulwang.visit_days);
    assert!(eulwang.visit_note.starts_with("2번 다녀옴 · "), "{}", eulwang.visit_note);
    // 한 번 다녀온 곳은 표시가 없다
    assert!(cands[0].visit_note.is_empty() && cands[0].visit_days.is_empty());

    // 후보가 하나로 합쳐지면 답이 다녀온 날을 함께 말한다
    a.new_session();
    let r = ask(&a, "예전에 갔던 감자탕집");
    let card = r.cards.lock().unwrap().first().cloned().unwrap();
    assert_eq!(card.visit_note, "2번 다녀옴 · 2/7, 10/18");
    let text = r.text.lock().unwrap().clone();
    assert!(text.starts_with("태조감자국이에요. 2월 7일, 10월 18일 두 번 다녀오셨어요."), "{text}");
    assert!(r.cands.lock().unwrap().0.is_empty(), "비교할 것이 없으면 후보 칸을 보이지 않는다");
}

/// UX 리뷰 2: 기록에 없는 질문은 오류가 아니라 보통 답 + 그 무렵 다녀온 곳 후보.
#[test]
fn not_found_is_a_plain_answer_with_places_from_that_time() {
    let a = agent("notfound");
    let r = ask(&a, "작년 겨울에 스키 타러 갔던 데 어디였지");
    let text = r.text.lock().unwrap().clone();
    assert!(text.starts_with("스키 기록은 없어요. 그 무렵에는 "), "{text}");
    let cands = r.cands.lock().unwrap().0.clone();
    assert!((2..=3).contains(&cands.len()), "{}", cands.len());
    // 후보는 모두 그 겨울(2025-12 ~ 2026-02)
    assert!(cands.iter().all(|h| ("2025-12-01".."2026-03-01").contains(&h.episode.facts.day.as_str())));
    assert!(r.proposals.lock().unwrap().is_empty());
    // 이어서 "2번"으로 볼 수 있다
    let r2 = ask(&a, "2번");
    assert_eq!(r2.first_id(), cands[1].episode.facts.id);
}

#[test]
fn visit_note_lists_up_to_three_days() {
    let d = |v: &[&str]| v.iter().map(|x| x.to_string()).collect::<Vec<_>>();
    assert_eq!(super::runner::visit_note(&d(&["2026-10-11", "2026-10-18"])), "2번 다녀옴 · 10/11, 10/18");
    assert_eq!(super::runner::visit_note(&d(&["2026-01-02", "2026-02-03", "2026-03-04", "2026-04-05"])), "4번 다녀옴 · 1/2, 2/3, 3/4 …");
    // 해가 섞이면 해를 붙인다
    assert_eq!(super::runner::visit_note(&d(&["2025-11-07", "2026-11-07"])), "2번 다녀옴 · '25.11/7, '26.11/7");
}

#[test]
fn candidates_are_every_close_memory_one_per_day() {
    let a = agent("short");
    let r = ask(&a, "옛날에 놀러갔던 바다 어디였지");
    let cands = r.cands.lock().unwrap().0.clone();
    let days: std::collections::HashSet<_> = cands.iter().map(|h| h.episode.facts.day.clone()).collect();
    assert_eq!(days.len(), cands.len(), "한 날 = 후보 하나");
    let top = cands[0].score;
    assert!(cands.iter().all(|h| h.score >= top * 0.4), "엉뚱한 날은 빠진다");
}

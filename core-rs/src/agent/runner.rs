//! 질문 한 번 처리: 확인 말 → 빠른 길(규칙 + 기기 검색) → 필요할 때만 LLM 도구 루프 (CAP loop.py의 멈춤 규칙을 따른다).
//!
//! LLM 루프 규칙 (작은 모델용):
//! - 도구 라운드는 [MAX_TOOL_ROUNDS]번까지, 그 뒤에는 도구 없이 마무리 답을 받는다.
//! - 도구 이름·JSON 인자를 검증하고, 틀리면 무엇이 틀렸는지 도구 결과로 돌려준다.
//! - 한 라운드의 도구 호출이 모두 실패하는 일이 [MAX_STRIKES]번 이어지면 정중히 멈춘다.
//! - 지난 질문의 도구 결과는 줄이고, 대화가 [MAX_HISTORY_CHARS]를 넘으면 오래된 질문부터 버린다 (16k 맥락).

use std::sync::atomic::Ordering;
use std::time::Instant;

use serde_json::{json, Value};

use super::courses;
use super::dates::{self, fmt_day, local_days, parse_ymd, DayRange};
use super::llm::{LlmError, Params};
use super::tools::{self, Ctx};
use super::words::{self, Answer};
use super::{prompts, AgentContext, AgentDone, AgentListener, AgentTiming, AnswerSource, MemoryAgent, NavProposal, Session};
use crate::geo::Point;
use crate::memory::{EpisodeKind, MemoryHit, MemoryQuery};

pub const MAX_TOOL_ROUNDS: usize = 4;
pub const MAX_STRIKES: usize = 3;
const MAX_TURNS: usize = 6;
const MAX_HISTORY_CHARS: usize = 12_000;
const OLD_TOOL_KEEP: usize = 700;
pub const SESSION_IDLE_MS: i64 = 10 * 60_000;

/// LLM 서버에 닿지 않아 기기 안 기록으로만 답했을 때의 안내 한 줄 ([AgentDone::notice], 답 문장과 따로 — 화면이 회색으로 보이고 읽지 않는다).
pub const SERVER_DOWN: &str = "AI 연결이 잠깐 끊겨 기기 안 기록으로 찾았어요.";
pub const CUT_OFF: &str = "답을 받다가 연결이 끊겼어요. 다시 물어봐 주세요.";
/// LLM 주소가 없어 기기 안 기록으로만 답했을 때의 안내 한 줄 ([AgentDone::notice]).
pub const NOT_CONFIGURED: &str = "AI 연결 없이 기기 안 기록으로 찾았어요.";
pub const GIVE_UP: &str = "죄송해요, 지금은 찾지 못했어요. 조금 다르게 말씀해 주시겠어요?";
const EMPTY_ANSWER: &str = "죄송해요, 답을 정리하지 못했어요. 다시 말씀해 주세요.";
/// 기록에 없고 검색어도 없을 때 (오류가 아닌 보통 답).
const NOT_FOUND: &str = "기록에서 비슷한 곳을 찾지 못했어요.";
/// 못 찾았고 대신 볼 곳도 없을 때 덧붙이는 말.
const ASK_AGAIN: &str = " 장소 이름이나 다녀온 때를 말씀해 주시면 다시 찾아볼게요.";

/// 질문 한 번의 진행 상태.
pub(crate) struct Turn<'a> {
    pub l: &'a dyn AgentListener,
    start: Instant,
    answer: String,
    first_text_ms: Option<u64>,
    to_rec_ms: Option<u64>,
    llm_calls: u32,
    llm_ms: u64,
    /// 이번 질문에서 새로 생긴 대화 (끝까지 가야 세션에 넣는다 → 중간 실패로 대화가 깨지지 않는다)
    msgs: Vec<Value>,
    last_focus: Option<(i64, bool)>,
    /// 이번 질문에서 추천 코스 목록 / 기억 카드를 보였나 (다음 질문의 번호 말을 코스 번호로 읽을지 정한다)
    pub(crate) course_shown: bool,
    memory_shown: bool,
    /// 이번 말이 후보 고르기("2번")였나 (답을 "2번, …예요"로)
    picked: bool,
    /// 답과 따로 보일 안내 한 줄 (연결 문제 등, [AgentDone::notice])
    notice: Option<String>,
    /// 질문이 기억 속 한 곳을 콕 집었나 ("강릉 횟집") → 코스 말고 그곳으로 안내를 제안한다
    place_asked: bool,
}

impl Turn<'_> {
    fn ms(&self) -> u64 {
        self.start.elapsed().as_millis() as u64
    }
    pub(crate) fn answer_text(&self) -> &str {
        &self.answer
    }
    pub(crate) fn say(&mut self, text: &str) {
        if text.is_empty() {
            return;
        }
        if self.first_text_ms.is_none() {
            self.first_text_ms = Some(self.ms());
        }
        self.answer.push_str(text);
        self.l.on_answer_delta(text.to_string());
    }
    fn recommend(&mut self, hit: &MemoryHit, course: Option<crate::memory::CourseRoute>) {
        let key = (hit.episode.facts.id, course.is_some());
        if self.last_focus == Some(key) {
            return;
        }
        self.last_focus = Some(key);
        if self.to_rec_ms.is_none() {
            self.to_rec_ms = Some(self.ms());
        }
        self.memory_shown = true;
        self.l.on_recommendation(hit.clone(), course);
    }
    /// 기억 후보 목록 (1번이 가장 비슷한 곳, [focus] = 지금 이야기하는 후보). 화면은 후보 칸에 나란히 보인다.
    fn candidates(&mut self, list: &[MemoryHit], focus: usize) {
        if list.len() < 2 {
            return;
        }
        if self.to_rec_ms.is_none() {
            self.to_rec_ms = Some(self.ms());
        }
        self.memory_shown = true;
        self.l.on_memory_candidates(list.to_vec(), focus as u32);
    }
    /// 추천 코스 목록 (같은 질문에서 다시 보내면 화면이 마지막 것으로 바꾼다).
    pub(crate) fn courses(&mut self, st: &courses::CourseState) {
        if self.to_rec_ms.is_none() {
            self.to_rec_ms = Some(self.ms());
        }
        self.course_shown = true;
        self.l.on_course_recommendations(st.list.clone(), st.focus as u32);
    }
}

impl MemoryAgent {
    pub(crate) fn run_turn(&self, utterance: &str, ctx: &AgentContext, l: &dyn AgentListener) {
        let gen = self.cancel_gen.load(Ordering::SeqCst);
        let mut s = self.session.lock().unwrap_or_else(|e| e.into_inner());
        let mut t = Turn { l, start: Instant::now(), answer: String::new(), first_text_ms: None, to_rec_ms: None, llm_calls: 0, llm_ms: 0, msgs: vec![], last_focus: None, course_shown: false, memory_shown: false, picked: false, notice: None, place_asked: false };
        if ctx.now_ms - s.last_activity_ms > SESSION_IDLE_MS {
            *s = Session::default();
            *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = None;
        }
        s.last_activity_ms = ctx.now_ms;
        let u = utterance.trim();
        if u.is_empty() {
            l.on_error("말씀을 듣지 못했어요. 다시 말씀해 주세요.".into());
            return;
        }

        // 1) 대기 중인 제안에 대한 "응 / 아니" — 모델 없이 바로 (빠르고, 실행 여부를 모델이 정하지 않는다)
        let pending = self.pending.lock().unwrap_or_else(|e| e.into_inner()).clone();
        if let Some(p) = pending {
            match words::classify(u) {
                Answer::Yes => {
                    *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = None;
                    l.on_action_confirmed(p.id.clone(), p.action.clone());
                    t.say("안내를 시작할게요.");
                    return self.finish(&mut s, t, u, AnswerSource::Confirm);
                }
                Answer::No => {
                    *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = None;
                    t.say("알겠어요, 안내하지 않을게요.");
                    return self.finish(&mut s, t, u, AnswerSource::Confirm);
                }
                Answer::Other => {}
            }
        }

        // 2) 추천 코스: "근처 드라이브 코스 추천", 목록이 있으면 "2번으로 가자"·"더 가까운 데"·"바다 쪽은?" (규칙, 수 ms)
        if self.opts.fast_path {
            if let Some(intent) = courses::detect(u, s.course_topic, s.courses.list.len()) {
                if self.course_fast_path(&mut s, &mut t, intent, ctx) {
                    return self.finish(&mut s, t, u, AnswerSource::FastPath);
                }
            }
        }

        let tz = ctx.tz_offset_minutes;
        let today = local_days(ctx.now_ms, tz);

        // 2-1) 기억 후보 고르기: 앞 질문에서 후보를 여럿 보였으면 "2번", "두 번째 거", "그거 말고 다른 거" (규칙, 수 ms)
        if self.opts.fast_path && !s.course_topic && s.candidates.len() > 1 {
            let at = s.focus.as_ref().and_then(|f| s.candidates.iter().position(|c| c.episode.facts.id == f.episode.facts.id)).unwrap_or(0);
            if let Some(pick) = candidate_pick(u, &s.candidates, at) {
                match pick {
                    Some(i) => {
                        // 번호는 처음 보인 그대로 둔다 (화면 후보 칸의 번호와 말 번호가 같게)
                        let chosen = s.candidates[i].clone();
                        let list = s.candidates.clone();
                        t.picked = true;
                        t.candidates(&list, i);
                        t.recommend(&chosen, None);
                        s.focus = Some(chosen.clone());
                        self.fast_answer(&mut s, &mut t, &chosen, ctx, today);
                    }
                    None => t.say(&format!("후보는 {}번까지 있어요. 몇 번으로 할까요?", s.candidates.len())),
                }
                return self.finish(&mut s, t, u, AnswerSource::FastPath);
            }
        }

        // 3) 빠른 길: 규칙으로 날짜·검색어 → 기기 검색 (수 ms)
        let parsed = words::parse(u);
        let range = dates::parse_korean_range(u, today);

        // 2-2) 이어 묻기 "거기 몇 명이서 갔어": 지금 이야기하는 기억의 좌석 센서 인원으로 바로 답한다 (새 곳 이름이 없을 때)
        if self.opts.fast_path && parsed.head_count && (parsed.has_reference || parsed.terms.is_empty()) && range.is_none() {
            if let Some(focus) = s.focus.clone() {
                self.head_count_answer(&mut s, &mut t, &focus, ctx);
                return self.finish(&mut s, t, u, AnswerSource::FastPath);
            }
        }
        let mut fast_hits: Vec<MemoryHit> = vec![];
        // 사람 말이 있어도 좌석 센서로 맞는 기억을 골랐으면 LLM 없이 바로 답한다
        let mut people_resolved = false;
        // 추천 코스 이야기 중의 규칙 밖 후속 말("그 중에 제일 짧은 걸로")은 기억 검색 없이 LLM이 목록을 보고 푼다.
        // 날짜 말이나 "갔던·였지" 같은 지난 일 말이 있으면 기억 질문이다.
        let course_follow_up = s.course_topic && !s.courses.list.is_empty() && range.is_none() && !courses::is_past(u);
        let mut rain_resolved = false;
        if self.opts.fast_path && !course_follow_up && (!parsed.terms.is_empty() || range.is_some() || parsed.rain) {
            let (from_ms, to_ms) = range.map(|r| r.to_ms(tz)).map_or((None, None), |(a, b)| (Some(a), Some(b)));
            // "비 오던 날": 와이퍼 기록으로 거르므로 넉넉히 받아 둔다 (낱말이 없으면 출퇴근은 빼고 최신순)
            let (limit, kinds) = if parsed.rain {
                (80, if parsed.terms.is_empty() { vec![EpisodeKind::Errand, EpisodeKind::Outing, EpisodeKind::Trip] } else { vec![] })
            } else {
                (CANDIDATE_SEARCH_LIMIT, vec![])
            };
            let q = MemoryQuery { terms: parsed.terms.clone(), from_ms, to_ms, kinds, limit };
            fast_hits = self.backend.search_memories(q).unwrap_or_default();
            if parsed.rain {
                let wet: Vec<MemoryHit> = fast_hits
                    .iter()
                    .filter(|h| self.backend.rain_between(h.episode.facts.start_ms, h.episode.facts.end_ms) == Some(true))
                    .take(5)
                    .cloned()
                    .collect();
                if !wet.is_empty() {
                    fast_hits = wet;
                    rain_resolved = true;
                } else {
                    fast_hits.truncate(5);
                }
            }
            // "옛날에 · 예전에": 잘 맞는 기억 중 오래된 것부터, 최근 [RECENT_DAYS]일 기록은 뒤로 (순서만 바꾸고 빼지는 않는다)
            if parsed.long_ago && range.is_none() {
                order_long_ago(&mut fast_hits, today);
            }
            // "엄마랑 · 가족이랑 · 혼자": 좌석 센서로 그때 탄 인원이 맞는 기억만 남긴다 (맞는 게 하나도 없으면 그대로 LLM에게)
            if let Some(want) = parsed.people {
                let fits: Vec<bool> =
                    fast_hits.iter().map(|h| want.matches(self.backend.people_between(h.episode.facts.start_ms, h.episode.facts.end_ms))).collect();
                if fits.iter().any(|f| *f) {
                    let mut ranked: Vec<(bool, MemoryHit)> = fits.into_iter().zip(fast_hits).collect();
                    ranked.sort_by_key(|(f, _)| !*f);
                    fast_hits = ranked.into_iter().filter(|(f, _)| *f).map(|(_, h)| h).collect();
                    people_resolved = true;
                }
            }
            // 흐릿한 질문("옛날에 갔던 바다")은 답이 하나가 아닐 수 있다 → 후보 몇 곳을 같이 보인다 (1번이 가장 비슷한 곳).
            // 같은 곳을 다른 날 다녀온 기억은 후보 하나로 합친다 ("2번 다녀옴 · 10/11, 10/18").
            let short = shortlist(&fast_hits);
            if let Some(top) = short.first() {
                t.recommend(top, None);
                s.tools.remember(&fast_hits);
                s.focus = Some(top.clone());
                t.candidates(&short, 0);
                s.candidates = short;
            }
        }
        // 3-1) 한 문장 코스 "작년 강릉 여행 그대로, 순두부집만 빼고 안내해 줘": 그 기억의 코스에서 뺄 곳을 빼고 다시 짠다
        if self.opts.fast_path && !parsed.skip.is_empty() {
            if let Some(top) = s.candidates.first().cloned().filter(|_| !fast_hits.is_empty()) {
                if self.skip_course(&mut s, &mut t, &top, &parsed.skip, ctx, today) {
                    return self.finish(&mut s, t, u, AnswerSource::FastPath);
                }
            }
        }
        // 기억 속 한 곳을 콕 집은 질문인가: 질문 낱말이 고른 곳의 이름 · 분류에 들고, 지역 이름(강릉)이 아니다
        if let Some(top) = s.candidates.first().filter(|_| !fast_hits.is_empty()) {
            let f = &top.episode.facts;
            t.place_asked = f.stops.len() > 1
                && top.best_stop.as_ref().is_some_and(|b| {
                    parsed.terms.iter().any(|w| {
                        w.chars().count() >= 2 && !f.region.contains(w.as_str()) && (b.name.contains(w.as_str()) || b.category.contains(w.as_str()))
                    })
                });
        }
        let narrow_date = range.is_some_and(|r| r.to - r.from <= 2);
        // 확실한가: 결과가 있고, 사람·앞 대화 말이 없고, 1위가 2위보다 분명히 앞서고, 한 글자 낱말(산·강…)만으로 찾은 게 아닐 때
        // (점수로 본다 — "옛날에"로 순서를 바꿔도 검색이 분명했으면 확실하다)
        let mut scores: Vec<f64> = fast_hits.iter().map(|h| h.score).collect();
        scores.sort_by(|a, b| b.total_cmp(a));
        let clear_top = match scores.as_slice() {
            [] => false,
            [_] => true,
            [a, b, ..] => a > b,
        };
        // 답할 기억 = 후보 1번 (같은 곳 합치기·"옛날에" 순서가 들어간 것)
        let lead: Option<MemoryHit> = if fast_hits.is_empty() { None } else { s.candidates.first().cloned() };
        let only_short_terms = !parsed.terms.is_empty() && parsed.terms.iter().all(|w| w.chars().count() < 2);
        let confident = clear_top && (!parsed.has_people || people_resolved) && (!parsed.rain || rain_resolved) && !parsed.has_reference && !only_short_terms && (!parsed.terms.is_empty() || narrow_date);
        let llm_usable = self.llm.is_some() && !self.server_down();

        if let (true, Some(top)) = (confident && !(self.opts.llm_when_confident && llm_usable), lead.as_ref()) {
            self.fast_answer(&mut s, &mut t, top, ctx, today);
            return self.finish(&mut s, t, u, AnswerSource::FastPath);
        }
        let ask = Asked { parsed: &parsed, range };
        if self.llm.is_none() {
            t.notice = Some(NOT_CONFIGURED.into());
            return self.offline_answer(&mut s, t, u, lead.as_ref(), &ask, ctx, today);
        }
        if !llm_usable {
            t.notice = Some(SERVER_DOWN.into());
            return self.offline_answer(&mut s, t, u, lead.as_ref(), &ask, ctx, today);
        }

        // 4) LLM 도구 루프
        let course_topic_before = s.course_topic;
        let pending_before = self.pending.lock().unwrap_or_else(|e| e.into_inner()).as_ref().map(|p| p.id.clone());
        match self.llm_loop(&mut s, &mut t, u, ctx, range, &fast_hits, gen) {
            Ok(()) => {
                if (course_topic_before || t.course_shown) && !t.memory_shown {
                    self.course_proposal_from_answer(&mut s, &mut t, pending_before);
                } else if t.memory_shown {
                    self.memory_proposal_after_llm(&mut s, &mut t, ctx, pending_before);
                }
                self.finish(&mut s, t, u, AnswerSource::Llm)
            }
            Err(LlmError::Cancelled) => l.on_error("취소했어요.".into()),
            Err(e) => {
                alog!("[agent] LLM 실패: {e}");
                if t.answer.trim().is_empty() {
                    if matches!(e, LlmError::Unreachable(_)) {
                        self.mark_server_down();
                    }
                    t.msgs.clear();
                    t.answer.clear();
                    t.notice = Some(SERVER_DOWN.into());
                    self.offline_answer(&mut s, t, u, lead.as_ref(), &ask, ctx, today)
                } else {
                    l.on_error(CUT_OFF.into());
                }
            }
        }
    }

    /// LLM 없이 답할 때 (주소 없음·서버에 닿지 않음): 기기 안 검색 결과가 있으면 그것으로 바로 답한다.
    /// 없으면 오류가 아니라 보통 답 "○○ 기록은 없어요" + 같은 때 다녀온 곳 후보. 연결 사정은 [Turn::notice]로 따로 (답 문장에 섞지 않는다).
    #[allow(clippy::too_many_arguments)]
    fn offline_answer(&self, s: &mut Session, mut t: Turn, u: &str, lead: Option<&MemoryHit>, asked: &Asked, ctx: &AgentContext, today: i64) {
        match lead {
            Some(top) => self.fast_answer(s, &mut t, top, ctx, today),
            None => self.not_found_answer(s, &mut t, asked, ctx),
        }
        self.finish(s, t, u, AnswerSource::Fallback)
    }

    /// 기록에서 못 찾았을 때의 답 (LLM 없이): "스키 기록은 없어요." + 질문한 때(있으면)에 다녀온 곳 2~3곳을 대신 볼 후보로.
    /// 후보는 화면 후보 칸에 나란히 보이고 "1번"·"2번"으로 고를 수 있다. 안내 제안은 하지 않는다 (찾던 곳이 아니므로).
    fn not_found_answer(&self, s: &mut Session, t: &mut Turn, asked: &Asked, ctx: &AgentContext) {
        let first = match asked.parsed.terms.first() {
            Some(w) => format!("{w} 기록은 없어요."),
            None => NOT_FOUND.to_string(),
        };
        let others = asked.range.map(|r| self.same_time_places(r, ctx.tz_offset_minutes)).unwrap_or_default();
        if others.is_empty() {
            t.say(&format!("{first}{ASK_AGAIN}"));
            return;
        }
        let names: Vec<String> = others.iter().map(hit_place).collect();
        t.say(&format!("{first} 그 무렵에는 {}에 다녀오셨어요.", names.join(", ")));
        s.tools.remember(&others);
        s.focus = Some(others[0].clone());
        t.recommend(&others[0], None);
        t.candidates(&others, 0);
        s.candidates = others;
    }

    /// 그 기간에 다녀온 나들이·여행 중 서로 다른 곳 [ALTERNATIVES]개 (최근 것부터).
    fn same_time_places(&self, r: DayRange, tz: i32) -> Vec<MemoryHit> {
        let (from_ms, to_ms) = r.to_ms(tz);
        let q = MemoryQuery { terms: vec![], from_ms: Some(from_ms), to_ms: Some(to_ms), kinds: vec![EpisodeKind::Outing, EpisodeKind::Trip], limit: CANDIDATE_SEARCH_LIMIT };
        let hits = self.backend.search_memories(q).unwrap_or_default();
        let mut out: Vec<MemoryHit> = vec![];
        for h in hits {
            if out.len() >= ALTERNATIVES {
                break;
            }
            let name = hit_place(&h);
            if !out.iter().any(|o| hit_place(o) == name) {
                out.push(h);
            }
        }
        out
    }

    /// 기억 하나로 갈 안내 제안: 들른 곳이 여럿이면 코스(카드도 코스로 바꾼다), 하나면 그곳.
    fn memory_proposal(&self, s: &mut Session, t: &mut Turn, top: &MemoryHit, ctx: &AgentContext) -> Option<NavProposal> {
        let (p, route) = self.proposal_for(s, top, ctx);
        if route.is_some() {
            t.recommend(top, route);
        }
        p
    }

    /// 기억 하나로 갈 안내 제안과 (코스면) 경로.
    fn proposal_for(&self, s: &mut Session, top: &MemoryHit, ctx: &AgentContext) -> (Option<NavProposal>, Option<crate::memory::CourseRoute>) {
        let f = &top.episode.facts;
        let title = top.episode.title.clone().or(top.day_title.clone()).unwrap_or(f.rule_label.clone());
        if f.stops.len() > 1 {
            let mut c = Ctx { backend: self.backend.as_ref(), ctx, cache: &mut s.tools, courses: &mut s.courses, proposal_seq: &mut s.proposal_seq };
            if let Ok((route, _)) = tools::plan(f.id, true, &mut c) {
                let p = tools::course_proposal(&mut s.proposal_seq, f.id, &title, &route);
                return (Some(p), Some(route));
            }
        }
        let stop = top.best_stop.clone().or_else(|| f.stops.first().cloned());
        (stop.filter(|x| !x.name.is_empty()).map(|x| tools::place_proposal(&mut s.proposal_seq, x)), None)
    }

    /// 화면 후보 칸에서 후보를 눌렀다: 그 후보가 지금 이야기하는 기억이 되고, 그곳으로 갈 안내 제안이 대기한다 (말 없이, 수 ms).
    pub(crate) fn select_candidate_inner(&self, episode_id: i64, ctx: &AgentContext) -> Option<super::CandidateChoice> {
        let mut s = self.session.lock().unwrap_or_else(|e| e.into_inner());
        let hit = s.candidates.iter().find(|c| c.episode.facts.id == episode_id)?.clone();
        s.focus = Some(hit.clone());
        s.last_activity_ms = ctx.now_ms;
        let (proposal, course) = self.proposal_for(&mut s, &hit, ctx);
        *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = proposal.clone();
        let rank = s.candidates.iter().position(|c| c.episode.facts.id == episode_id).unwrap_or(0) + 1;
        let ask = match &proposal {
            Some(p) if matches!(p.action, super::NavAction::Course { .. }) => " 그 코스로 안내할까요?",
            Some(_) => " 그곳으로 안내할까요?",
            None => "",
        };
        let line = format!("{rank}번은 {}{ask}", describe(&hit, local_days(ctx.now_ms, ctx.tz_offset_minutes)));
        Some(super::CandidateChoice { proposal, course, line })
    }

    /// LLM이 기억을 찾아 답했는데 안내 제안(nav)을 하지 않았으면, 찾은 기억으로 제안을 붙인다 —
    /// 다음 행동(안내)이 버튼 한 번, 또는 말 한마디("응")가 되게. 못 찾았다고 답했으면 붙이지 않는다.
    fn memory_proposal_after_llm(&self, s: &mut Session, t: &mut Turn, ctx: &AgentContext, pending_before: Option<String>) {
        let pending_now = self.pending.lock().unwrap_or_else(|e| e.into_inner()).as_ref().map(|p| p.id.clone());
        if pending_now.is_some() && pending_now != pending_before {
            return; // 이번 질문에서 이미 제안함
        }
        let answer = t.answer_text().to_string();
        if ["못 찾", "찾지 못"].iter().any(|w| answer.contains(w)) && !answer.contains("까요?") {
            return;
        }
        let Some(focus) = s.focus.clone() else { return };
        let Some(p) = self.memory_proposal(s, t, &focus, ctx) else { return };
        if !answer.contains("까요?") {
            t.say(if matches!(p.action, super::NavAction::Course { .. }) { " 그 코스로 안내할까요?" } else { " 그곳으로 안내할까요?" });
        }
        *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
        t.l.on_action_proposal(p);
    }

    /// LLM이 답에서 카드와 다른 후보를 말했으면(카드 "대림창고" · 답 "블루보틀 성수 기록이 있어요") 카드 · 안내 제안을
    /// 답이 말한 후보로 바꾼다. 답과 화면이 다르면 운전자가 무엇으로 안내되는지 알 수 없다.
    /// 한 곳으로 옮기는 것은 이번 질문에서 기억 카드를 보였을 때만, 여러 곳을 말했으면 후보 칸을 보인다 (코스 추천 중에는 하지 않는다).
    fn follow_answer_focus(&self, s: &mut Session, t: &mut Turn, ctx: &AgentContext) {
        if t.course_shown {
            return;
        }
        let Some(focus) = s.focus.clone() else { return };
        let Some(named) = answer_names(t.answer_text(), &focus, &s.candidates) else { return };
        // 이번 질문에서 기억 카드를 보였을 때만 (지난 질문의 후보를 다시 꺼내지 않는다)
        if named.is_empty() || !t.memory_shown {
            return;
        }
        // 여럿을 말했으면 후보 칸으로 고르게 하고, 카드 · 안내 제안은 처음 말한 곳으로 (아래에서 함께 옮긴다)
        let i = named[0];
        let hit = s.candidates[i].clone();
        alog!("[agent] 답이 다른 후보를 말함 → 카드를 {}로", hit_place(&hit));
        s.focus = Some(hit.clone());
        let (p, course) = self.proposal_for(s, &hit, ctx);
        t.recommend(&hit, course);
        // 후보 칸의 고른 표시도 같은 후보로
        let list = s.candidates.clone();
        t.candidates(&list, i);
        if let Some(p) = p {
            if !t.answer_text().contains("까요?") {
                t.say(if matches!(p.action, super::NavAction::Course { .. }) { " 그 코스로 안내할까요?" } else { " 그곳으로 안내할까요?" });
            }
            *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
            t.l.on_action_proposal(p);
        }
    }

    /// "거기 몇 명이서 갔어": 지금 이야기하는 기억 때 좌석 센서 인원. 카드에 인원을 붙여 다시 보이고, 안내 제안을 이어 묻는다.
    fn head_count_answer(&self, s: &mut Session, t: &mut Turn, focus: &MemoryHit, ctx: &AgentContext) {
        let f = &focus.episode.facts;
        let n = self.backend.people_between(f.start_ms, f.end_ms).filter(|&k| k > 0);
        let mut hit = focus.clone();
        hit.people = n;
        let course = s.tools.courses.get(&f.id).map(|(r, _)| r.clone());
        t.recommend(&hit, course);
        s.focus = Some(hit.clone());
        if let Some(i) = s.candidates.iter().position(|c| c.episode.facts.id == f.id) {
            s.candidates[i].people = n;
        }
        // 기다리는 안내 제안을 이 답에도 다시 보인다 (버튼이 답 바로 아래에 있게). 없으면 새로 만든다.
        let mut pending = self.pending.lock().unwrap_or_else(|e| e.into_inner()).clone();
        if pending.is_none() {
            pending = self.memory_proposal(s, t, &hit, ctx);
            if let Some(p) = &pending {
                *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
            }
        }
        if let Some(p) = &pending {
            t.l.on_action_proposal(p.clone());
        }
        // 제안이 코스면 "그 코스로", 한 곳이면 "거기로" (버튼 "이 코스로 안내"와 말이 맞게)
        let ask = match &pending {
            Some(p) if matches!(p.action, super::NavAction::Course { .. }) => " 그 코스로 안내할까요?",
            Some(_) => " 거기로 안내할까요?",
            None => "",
        };
        let place = hit_place(&hit);
        let text = match n {
            None => format!("그때 몇 분이 타셨는지는 기록이 없어요.{ask}"),
            Some(1) => format!("{place}엔 혼자 가셨어요.{ask}"),
            Some(k) => format!("{place}엔 {k}명이서 가셨어요. 그날 좌석 {k}자리가 찼어요.{ask}"),
        };
        t.say(&text);
    }

    /// "작년 강릉 여행 그대로, 순두부집만 빼고": 그날 순서 그대로의 코스에서 [skip] 낱말에 맞는 곳(이름 · 분류)을 빼고 다시 짠다.
    /// 뺀 곳은 [crate::memory::CourseRoute::skipped]로 지도에 흐리게 남는다. 다 빠지면 false (다른 길로 푼다).
    #[allow(clippy::too_many_arguments)]
    fn skip_course(&self, s: &mut Session, t: &mut Turn, top: &MemoryHit, skip: &[String], ctx: &AgentContext, today: i64) -> bool {
        let f = &top.episode.facts;
        let Ok(full) = self.backend.course_route(f.id, ctx.current_location, false) else { return false };
        let title = top.episode.title.clone().or(top.day_title.clone()).unwrap_or(f.rule_label.clone());
        let when = spoken_date(&f.day, today);
        let matches = |x: &crate::memory::StopFact| skip.iter().any(|w| x.name.contains(w.as_str()) || x.category.contains(w.as_str()));
        let (gone, keep): (Vec<_>, Vec<_>) = full.stops.iter().cloned().partition(|x| matches(x));
        if keep.is_empty() {
            return false;
        }
        s.focus = Some(top.clone());
        s.tools.remember(std::slice::from_ref(top));
        let (route, text) = if gone.is_empty() {
            let text = format!("{when} '{title}' 코스에는 '{}'에 맞는 곳이 없어요. 그대로 안내할까요?", skip.join(", "));
            (full, text)
        } else {
            let mut r = crate::memory::route_through(ctx.current_location, keep);
            r.skipped = gone.clone();
            let gone_names = gone.iter().map(|x| x.name.as_str()).collect::<Vec<_>>().join(", ");
            let names = r.stops.iter().map(|x| x.name.as_str()).collect::<Vec<_>>().join(", ");
            let text = format!(
                "{when} '{title}' 코스에서 {} 빼고 {}곳으로 다시 짰어요. {names} 순서예요. 이대로 안내할까요?",
                eul(&gone_names),
                r.stops.len()
            );
            (r, text)
        };
        s.tools.courses.insert(f.id, (route.clone(), false));
        t.recommend(top, Some(route.clone()));
        let p = tools::course_proposal(&mut s.proposal_seq, f.id, &title, &route);
        t.say(&text);
        *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
        t.l.on_action_proposal(p);
        true
    }

    /// 틀 문장 답 + 안내 제안 (LLM 없이).
    fn fast_answer(&self, s: &mut Session, t: &mut Turn, top: &MemoryHit, ctx: &AgentContext, today: i64) {
        let f = &top.episode.facts;
        let title = top.episode.title.clone().or(top.day_title.clone()).unwrap_or(f.rule_label.clone());
        let place = top.best_stop.as_ref().map(|b| b.name.clone()).filter(|n| !n.is_empty())
            .or_else(|| f.stops.first().map(|x| x.name.clone()).filter(|n| !n.is_empty()))
            .unwrap_or(title.clone());
        let when = spoken_date(&f.day, today);
        // 한 곳을 콕 집은 질문("강릉 횟집")은 하루 코스가 아니라 그곳 하나로
        let spot = top.best_stop.clone().filter(|b| t.place_asked && !b.name.is_empty());
        if let Some(b) = spot.as_ref().filter(|_| s.candidates.len() <= 1) {
            let p = tools::place_proposal(&mut s.proposal_seq, b.clone());
            t.say(&format!("{when} {title} 때 들른 {}. 그곳으로 안내할까요?", ieyo(&b.name)));
            *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
            t.l.on_action_proposal(p);
            return;
        }
        let proposal = match spot {
            Some(b) => Some(tools::place_proposal(&mut s.proposal_seq, b)),
            None => self.memory_proposal(s, t, top, ctx),
        };
        let ask = match &proposal {
            Some(p) if matches!(p.action, super::NavAction::Course { .. }) => " 그 코스로 안내할까요?",
            Some(_) => " 그곳으로 안내할까요?",
            None => "",
        };
        // 후보가 여럿이면 목록은 화면 후보 칸이 보이고, 말은 "몇 곳 · 지금 몇 번" 한 줄로 짧게
        let n = s.candidates.len();
        let at = s.candidates.iter().position(|c| c.episode.facts.id == f.id);
        let text = match at {
            Some(0) if n > 1 && !t.picked => {
                // "비슷한"은 빼고 개수만: "부산 여행 언제 갔지"처럼 딱 맞게 찾은 때도 얼버무리는 말로 들렸다
                format!("기억 {n}개를 찾았어요. 1번은 {}{ask}", describe(top, today))
            }
            Some(i) if n > 1 => format!("{}번은 {}{ask}", i + 1, describe(top, today)),
            // 같은 곳을 여러 번 다녀옴 (후보 하나로 합쳐짐): "태조감자국이에요. 1월 25일, 11월 1일 두 번 다녀오셨어요."
            _ if top.visit_days.len() > 1 => format!("{}{ask}", describe(top, today)),
            _ if ctx.is_driving || f.stops.len() <= 1 => format!("{when}, {place}에 다녀오셨어요.{ask}"),
            _ => {
                let names = f.stops.iter().take(3).map(|x| x.name.as_str()).filter(|n| !n.is_empty()).collect::<Vec<_>>().join(", ");
                format!("{when} '{title}' 기록이에요. {names}에 들르셨어요.{ask}")
            }
        };
        t.say(&text);
        if let Some(p) = proposal {
            *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
            t.l.on_action_proposal(p);
        }
    }

    #[allow(clippy::too_many_arguments)]
    fn llm_loop(&self, s: &mut Session, t: &mut Turn, u: &str, ctx: &AgentContext, range: Option<DayRange>, fast_hits: &[MemoryHit], gen: u64) -> Result<(), LlmError> {
        let llm = self.llm.as_ref().expect("llm_usable");
        let pending = self.pending.lock().unwrap_or_else(|e| e.into_inner()).clone();
        t.msgs.push(json!({"role": "user", "content": context_block(ctx, range, s, fast_hits, pending.as_ref()) + u}));
        let cancelled = || self.cancel_gen.load(Ordering::SeqCst) != gen;
        let mut strikes = 0;
        let mut nudged = false;
        // 빈 답을 받으면 도구 없이 "받은 결과로 답하라"로 한 번 더 묻는다 (Gemini는 결과가 비면 글 없이 끝내기도 한다)
        let mut wrap_retry = false;
        let mut round = 0;
        while round <= MAX_TOOL_ROUNDS + 1 {
            let allow_tools = round < MAX_TOOL_ROUNDS && !wrap_retry;
            if round > MAX_TOOL_ROUNDS && !wrap_retry {
                break;
            }
            round += 1;
            let round = round - 1;
            let mut messages: Vec<Value> = vec![json!({"role": "system", "content": prompts::AGENT_SYSTEM})];
            messages.extend(s.turns.iter().flatten().cloned());
            messages.extend(t.msgs.iter().cloned());
            if !allow_tools {
                messages.push(json!({"role": "user", "content": prompts::WRAP_UP}));
            }
            let max_tokens = match (allow_tools, ctx.is_driving) {
                (true, true) => 96,
                (true, false) => 160,
                (false, true) => 120,
                (false, false) => 200,
            };
            let mut body = llm.body(&messages, &Params { temperature: self.opts.temperature, top_p: 0.95, max_tokens });
            if allow_tools {
                body["tools"] = tools::definitions().clone();
                body["tool_choice"] = json!("auto");
                body["parallel_tool_calls"] = json!(true);
            }
            // 한 라운드의 글은 모았다가 도구를 안 부른 라운드에서만 다듬어 내보낸다: 작은 모델은 도구를 부르기 전에
            // "찾아보겠습니다" 같은 말을 붙이고, 목록·별표를 섞어 길게 쓴다 → 운전자에게는 마지막 답 한두 문장만.
            // (카드는 빠른 길에서 먼저 나오고, 도구 호출 한 번이 0.3~0.5초라 늦어지는 것은 그만큼이다.)
            let mut said = String::new();
            let r = llm.stream(body, &mut |p| said.push_str(p), &cancelled)?;
            t.llm_calls += 1;
            t.llm_ms += r.total_ms;
            alog!(
                "[agent] LLM {}: 첫 토큰 {:?}ms 전체 {}ms 도구 {:?} timings={}",
                t.llm_calls, r.first_token_ms, r.total_ms, r.tool_calls.iter().map(|c| c.name.as_str()).collect::<Vec<_>>(),
                r.timings.as_ref().map(|x| x.to_string()).unwrap_or_default()
            );
            // "찾아볼게요"라고 말만 하고 도구를 안 부른 경우 한 번만 다시 시킨다 (작은 모델에서 자주 나온다, CAP announce_nudge)
            if r.tool_calls.is_empty() && allow_tools && !nudged && fast_hits.is_empty() && is_promise(&r.content) {
                nudged = true;
                t.msgs.push(json!({"role": "assistant", "content": r.content}));
                t.msgs.push(json!({"role": "user", "content": "[앱] 말로만 하지 말고 지금 도구를 불러라."}));
                continue;
            }
            if r.tool_calls.is_empty() || !allow_tools {
                let text = tidy_answer(&said, ctx.is_driving);
                if text.is_empty() && !wrap_retry {
                    alog!("[agent] 빈 답 (finish={:?}, 도구 {}개) → 도구 없이 다시 물음", r.finish_reason, r.tool_calls.len());
                    wrap_retry = true;
                    continue;
                }
                t.say(if text.is_empty() { EMPTY_ANSWER } else { &text });
                t.msgs.push(json!({"role": "assistant", "content": t.answer.clone()}));
                self.follow_answer_focus(s, t, ctx);
                return Ok(());
            }
            let calls: Vec<_> = r.tool_calls.iter().enumerate()
                .map(|(i, c)| { let mut c = c.clone(); if c.id.is_empty() { c.id = format!("call_{round}_{i}"); } c })
                .collect();
            t.msgs.push(json!({
                "role": "assistant", "content": r.content,
                "tool_calls": calls.iter().map(|c| json!({"id": c.id, "type": "function", "function": {"name": c.name, "arguments": c.arguments}})).collect::<Vec<_>>(),
            }));
            // 도구는 기기 안 Rust라 수 ms → 차례로 실행 (저장소 연결이 하나라 동시에 돌려도 빨라지지 않는다)
            let outcomes: Vec<_> = calls.iter().map(|c| {
                let mut cx = Ctx { backend: self.backend.as_ref(), ctx, cache: &mut s.tools, courses: &mut s.courses, proposal_seq: &mut s.proposal_seq };
                tools::run(c, &mut cx)
            }).collect();
            for o in &outcomes {
                t.msgs.push(json!({"role": "tool", "tool_call_id": o.call.id, "name": o.call.name, "content": o.content}));
                if !o.hits.is_empty() {
                    s.candidates = o.hits.clone();
                }
                if let Some((hit, course)) = &o.focus {
                    s.focus = Some(hit.clone());
                    t.recommend(hit, course.clone());
                }
                if o.courses_changed {
                    t.courses(&s.courses);
                }
                if let Some(p) = &o.proposal {
                    *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
                    t.l.on_action_proposal(p.clone());
                }
            }
            alog!("[agent] 도구 라운드 {}: {}", round + 1, outcomes.iter().map(|o| format!("{}={}", o.call.name, if o.ok { "ok" } else { "fail" })).collect::<Vec<_>>().join(", "));
            strikes = if outcomes.iter().all(|o| !o.ok) { strikes + 1 } else { 0 };
            if strikes >= MAX_STRIKES {
                let sep = if t.answer.is_empty() { "" } else { " " };
                t.say(&format!("{sep}{GIVE_UP}"));
                t.msgs.push(json!({"role": "assistant", "content": t.answer.clone()}));
                return Ok(());
            }
        }
        Ok(())
    }

    fn finish(&self, s: &mut Session, t: Turn, u: &str, source: AnswerSource) {
        if t.course_shown {
            s.course_topic = true;
        } else if t.memory_shown {
            s.course_topic = false;
        }
        let mut msgs = t.msgs;
        if msgs.is_empty() {
            // 빠른 길·확인·대체 답: 사용자 말과 답만 남긴다 (찾은 기억은 다음 [상황]에 들어간다)
            msgs = vec![json!({"role": "user", "content": u}), json!({"role": "assistant", "content": t.answer})];
        }
        s.turns.push_back(msgs.into_iter().map(shrink_old).collect());
        while s.turns.len() > MAX_TURNS || (s.turns.len() > 1 && s.turns.iter().flatten().map(|m| m.to_string().len()).sum::<usize>() > MAX_HISTORY_CHARS) {
            s.turns.pop_front();
        }
        let timing = AgentTiming { to_recommendation_ms: t.to_rec_ms, first_text_ms: t.first_text_ms, total_ms: t.start.elapsed().as_millis() as u64, llm_calls: t.llm_calls, llm_ms: t.llm_ms };
        alog!("[agent] 끝 ({source:?}): 카드 {:?}ms 첫 글자 {:?}ms 전체 {}ms LLM {}회 {}ms", timing.to_recommendation_ms, timing.first_text_ms, timing.total_ms, timing.llm_calls, timing.llm_ms);
        t.l.on_done(AgentDone { answer: t.answer.trim().to_string(), source, timing, notice: t.notice });
    }

    pub(crate) fn warm_up_inner(&self) -> bool {
        let Some(llm) = self.llm.as_ref() else { return false };
        let messages = vec![json!({"role": "system", "content": prompts::AGENT_SYSTEM}), json!({"role": "user", "content": "[상황]\n안녕"})];
        let mut body = llm.body(&messages, &Params { temperature: 0.0, top_p: 0.95, max_tokens: 1 });
        body["tools"] = tools::definitions().clone();
        body["tool_choice"] = json!("auto");
        match llm.complete(body) {
            Ok(r) => {
                alog!("[agent] 미리 데우기 {}ms timings={}", r.total_ms, r.timings.map(|x| x.to_string()).unwrap_or_default());
                true
            }
            // 미리 데우기는 앱 시작 직후(망이 아직 덜 붙었을 때)라 실패해도 서버가 꺼졌다고 보지 않는다
            // — 예전에는 여기서 30초 동안 꺼짐으로 표시해 첫 질문이 "연결할 수 없어요"로 끝났다.
            Err(e) => {
                alog!("[agent] 미리 데우기 실패 (첫 질문은 그대로 LLM을 시도한다): {e}");
                false
            }
        }
    }
}

/// 모델 답을 소리로 읽기 좋게 다듬는다: 별표·제목표·목록 기호를 지우고 한 줄로, 문장 수를 줄인다
/// (운전 중 2문장·90자, 정차 중 3문장·120자 안 — Pleos 가독성 기준 "한 메시지 120자 이하"). 잘려 나간 끝 문장이 "…할까요?" 같은 물음이면 그것은 남긴다
/// (운전자가 "응"으로 답할 수 있게).
pub(crate) fn tidy_answer(raw: &str, driving: bool) -> String {
    let (max_sentences, max_chars) = if driving { (2, 90) } else { (3, 120) };
    let mut flat = String::new();
    for line in raw.lines() {
        let l = line.trim().trim_start_matches(['-', '*', '•', '#', '>']).trim();
        if l.is_empty() {
            continue;
        }
        if !flat.is_empty() {
            flat.push(' ');
        }
        flat.push_str(l);
    }
    let flat: String = flat.replace("**", "").replace(['*', '`', '#'], "");
    let flat = flat.split_whitespace().collect::<Vec<_>>().join(" ");
    // 문장 나누기: . ? ! 뒤 (숫자 사이의 점 "3.5km"은 나누지 않는다)
    let mut sentences: Vec<String> = vec![];
    let mut cur = String::new();
    let chars: Vec<char> = flat.chars().collect();
    for (i, &c) in chars.iter().enumerate() {
        cur.push(c);
        let end = matches!(c, '?' | '!' | '。') || (c == '.' && !chars.get(i + 1).is_some_and(|n| n.is_ascii_digit()));
        if end && chars.get(i + 1).is_none_or(|n| n.is_whitespace()) {
            sentences.push(cur.trim().to_string());
            cur.clear();
        }
    }
    if !cur.trim().is_empty() {
        sentences.push(cur.trim().to_string());
    }
    let mut out: Vec<String> = vec![];
    let mut len = 0;
    for s in &sentences {
        let n = s.chars().count();
        if out.len() >= max_sentences || (!out.is_empty() && len + n > max_chars) {
            break;
        }
        len += n + 1;
        out.push(s.clone());
    }
    if out.len() < sentences.len() {
        if let Some(q) = sentences.iter().rev().find(|s| s.ends_with("까요?")) {
            if !out.contains(q) {
                if out.len() >= max_sentences && out.len() > 1 {
                    out.pop();
                }
                out.push(q.clone());
            }
        }
    }
    out.join(" ")
}

/// "찾아볼게요·확인해 드릴게요"처럼 할 일을 말만 한 답인가.
fn is_promise(text: &str) -> bool {
    let t: String = text.chars().filter(|c| !c.is_whitespace()).collect();
    ["찾아볼", "찾아드릴", "찾아보겠", "찾아드리겠", "확인해볼", "확인해드릴", "확인해보겠", "검색해볼", "검색해보겠", "알아볼", "알아보겠"].iter().any(|w| t.contains(w))
}

/// 지난 질문의 도구 결과는 앞부분만 남긴다 (다음 질문에서는 id·이름만 알면 된다).
fn shrink_old(mut m: Value) -> Value {
    if m["role"] == "tool" {
        if let Some(c) = m["content"].as_str() {
            if c.chars().count() > OLD_TOOL_KEEP {
                let short: String = c.chars().take(OLD_TOOL_KEEP).collect();
                m["content"] = json!(format!("{short}…[이전 결과 줄임]"));
            }
        }
    }
    m
}

/// 매번 바뀌는 상황 블록 (사용자 말 바로 앞). 시스템 프롬프트를 바꾸지 않아 서버 캐시가 유지된다.
fn context_block(ctx: &AgentContext, range: Option<DayRange>, s: &Session, fast_hits: &[MemoryHit], pending: Option<&NavProposal>) -> String {
    let today = local_days(ctx.now_ms, ctx.tz_offset_minutes);
    let (y, _, _) = dates::civil_from_days(today);
    let mut b = format!(
        "[상황]\n오늘 {}({}) · 어제 {} · 올해 {y} · 작년 {}\n{}\n지금 위치: {}\n",
        fmt_day(today), dates::WEEKDAYS[dates::weekday(today)], fmt_day(today - 1), y - 1,
        if ctx.is_driving { prompts::DRIVING_RULE } else { prompts::PARKED_RULE },
        if ctx.current_location.is_some() { "앎" } else { "모름" },
    );
    if let Some(r) = range {
        b.push_str(&format!("해석된 기간: {}~{}\n", fmt_day(r.from), fmt_day(r.to)));
    }
    if !fast_hits.is_empty() {
        let hits: Vec<Value> = fast_hits.iter().take(5).map(|h| tools::hit_json(h, today)).collect();
        b.push_str(&format!("기기 검색 결과(데이터, 질문에 맞으면 find 없이 이것으로 답한다): {}\n", Value::Array(hits)));
    } else if let Some(f) = &s.focus {
        b.push_str(&format!("앞서 찾은 기억(데이터): {}\n", tools::hit_json(f, today)));
        let others: Vec<Value> = s.candidates.iter().filter(|c| c.episode.facts.id != f.episode.facts.id).take(3).map(|h| tools::hit_json(h, today)).collect();
        if !others.is_empty() {
            b.push_str(&format!("다른 후보(데이터): {}\n", Value::Array(others)));
        }
    }
    if s.course_topic && !s.courses.list.is_empty() {
        b.push_str(&format!(
            "추천 코스 목록(데이터, 번호로 고르면 nav mode=pick n): {}\n지금 고른 코스: {}번\n",
            courses::list_json(&s.courses), s.courses.focus + 1
        ));
    }
    if let Some(p) = pending {
        b.push_str(&format!("확인 대기 중인 안내 제안: {}\n", p.summary));
    }
    b.push_str("[운전자 말]\n");
    b
}

/// "옛날에 갔던"이라고 하면 이 날수 안의 기록은 뒤로 민다.
const RECENT_DAYS: i64 = 30;

/// "경포해변" → "경포해변이에요", "을왕리 조개구이촌" → "을왕리 조개구이촌이에요", "카페" → "카페예요" (받침 따라).
/// "순두부" → "순두부를", "경포해변" → "경포해변을"
fn eul(word: &str) -> String {
    let batchim = word.chars().last().is_some_and(|c| ('가'..='힣').contains(&c) && !(c as u32 - 0xAC00).is_multiple_of(28));
    format!("{word}{}", if batchim { "을" } else { "를" })
}

fn ieyo(word: &str) -> String {
    let batchim = word.chars().last().is_some_and(|c| ('가'..='힣').contains(&c) && !(c as u32 - 0xAC00).is_multiple_of(28));
    format!("{word}{}", if batchim { "이에요" } else { "예요" })
}

/// 후보를 찾을 때 검색 결과를 이만큼 받는다 (후보 수는 따로 자르지 않는다 — 맞는 기억은 모두 후보 칸에 보인다).
pub const CANDIDATE_SEARCH_LIMIT: u32 = 40;
/// 1위 점수의 이 비율보다 낮은 결과는 후보로 보이지 않는다 (낱말 하나 겨우 걸린 엉뚱한 날이 끼지 않게).
const CANDIDATE_MIN_RATIO: f64 = 0.4;

/// "옛날에"일 때 1위 점수의 이 비율 이상이면 "잘 맞는 기억"으로 보고 오래된 것부터 놓는다.
const LONG_AGO_STRONG_RATIO: f64 = 0.6;
/// 같은 이름의 들른 곳이 이 거리(m) 안이면 같은 곳으로 본다 (주차 위치가 조금씩 다르다).
const SAME_PLACE_M: f64 = 300.0;
/// 기록에 없을 때 대신 보일 곳 수.
const ALTERNATIVES: usize = 3;

/// 질문에서 뽑은 것 (못 찾았을 때 답을 만들 때 쓴다).
struct Asked<'a> {
    parsed: &'a words::Parsed,
    range: Option<DayRange>,
}

/// "옛날에 · 예전에" 순서: ① 잘 맞는 기억(1위 점수의 [LONG_AGO_STRONG_RATIO] 이상) 중 오래된 것부터 → ② 나머지는 점수순 →
/// ③ 최근 [RECENT_DAYS]일 기록은 맨 뒤. 빼지는 않는다 (순서만).
pub(crate) fn order_long_ago(hits: &mut [MemoryHit], today: i64) {
    let top = hits.iter().map(|h| h.score).fold(f64::MIN, f64::max);
    hits.sort_by_key(|h| {
        let recent = parse_ymd(&h.episode.facts.day, false).is_some_and(|d| today - d < RECENT_DAYS);
        let strong = !recent && h.score >= top * LONG_AGO_STRONG_RATIO;
        (recent, !strong, if strong { h.episode.facts.start_ms } else { 0 })
    });
}

/// 검색 결과 → 후보 목록: 가장 높은 점수와 가까운 기억 모두 (순서는 받은 그대로).
/// 검색은 하루에 하나로 묶어 주므로 같은 곳을 다른 날 다녀오면 여러 기억이 된다 → 같은 곳(들른 곳 이름이 같고 [SAME_PLACE_M] 안)은
/// 앞의 후보 하나로 합치고 다녀온 날을 [MemoryHit::visit_days]·[MemoryHit::visit_note]에 적는다.
pub(crate) fn shortlist(hits: &[MemoryHit]) -> Vec<MemoryHit> {
    let top = hits.iter().map(|h| h.score).fold(f64::MIN, f64::max);
    let mut out: Vec<MemoryHit> = vec![];
    for h in hits.iter().filter(|h| h.score >= top * CANDIDATE_MIN_RATIO) {
        match out.iter_mut().find(|o| same_place(o, h)) {
            Some(o) => {
                if o.visit_days.is_empty() {
                    o.visit_days.push(o.episode.facts.day.clone());
                }
                o.visit_days.push(h.episode.facts.day.clone());
            }
            None => out.push(h.clone()),
        }
    }
    for o in &mut out {
        o.visit_days.sort();
        o.visit_days.dedup();
        if o.visit_days.len() > 1 {
            o.visit_note = visit_note(&o.visit_days);
        } else {
            o.visit_days.clear();
        }
    }
    out
}

/// 두 기억이 같은 곳인가: 질문과 가장 맞는 들른 곳의 이름이 같고 아주 가깝다.
fn same_place(a: &MemoryHit, b: &MemoryHit) -> bool {
    match (&a.best_stop, &b.best_stop) {
        (Some(x), Some(y)) => !x.name.is_empty() && x.name == y.name && crate::geo::haversine_m(Point { lat: x.lat, lon: x.lon }, Point { lat: y.lat, lon: y.lon }) <= SAME_PLACE_M,
        _ => false,
    }
}

/// ["2026-10-11", "2026-10-18"] → "2번 다녀옴 · 10/11, 10/18" (날은 3개까지, 넘으면 "…").
pub(crate) fn visit_note(days: &[String]) -> String {
    let years: Vec<i64> = days.iter().filter_map(|d| parse_ymd(d, false)).map(|x| dates::civil_from_days(x).0).collect();
    let mixed = years.windows(2).any(|w| w[0] != w[1]);
    let short: Vec<String> = days
        .iter()
        .take(3)
        .map(|d| {
            parse_ymd(d, false)
                .map(|x| {
                    let (y, m, dd) = dates::civil_from_days(x);
                    // 해가 섞이면 "'25.11/7"처럼 해를 붙인다 (같은 달·날이 겹쳐 보이지 않게)
                    if mixed { format!("'{:02}.{m}/{dd}", y % 100) } else { format!("{m}/{dd}") }
                })
                .unwrap_or_else(|| d.clone())
        })
        .collect();
    let more = if days.len() > 3 { " …" } else { "" };
    format!("{}번 다녀옴 · {}{more}", days.len(), short.join(", "))
}

/// 후보 하나를 말로: "8월 7일 해운대해수욕장이에요." / 합친 후보면 "태조감자국이에요. 1월 25일, 11월 1일 두 번 다녀오셨어요."
fn describe(h: &MemoryHit, today: i64) -> String {
    let place = ieyo(&hit_place(h));
    if h.visit_days.len() < 2 {
        return format!("{} {place}.", spoken_date(&h.episode.facts.day, today));
    }
    // 해가 섞이면 올해 날짜에도 "올해"를 붙인다 ("작년 11월 7일, 올해 11월 7일").
    let ty = dates::civil_from_days(today).0;
    let mixed = h.visit_days.iter().filter_map(|d| parse_ymd(d, false)).any(|d| dates::civil_from_days(d).0 != ty);
    let days: Vec<String> = h
        .visit_days
        .iter()
        .take(3)
        .map(|d| {
            let s = spoken_date(d, today);
            let this_year = parse_ymd(d, false).is_some_and(|x| dates::civil_from_days(x).0 == ty);
            if mixed && this_year && s.contains('월') { format!("올해 {s}") } else { s }
        })
        .collect();
    let more = if h.visit_days.len() > 3 { " 등" } else { "" };
    format!("{place}. {}{more} {}다녀오셨어요.", days.join(", "), times(h.visit_days.len()))
}

/// 2 → "두 번 ", 3 → "세 번 " (소리로 읽기 좋게).
fn times(n: usize) -> String {
    match n {
        2 => "두 번 ".into(),
        3 => "세 번 ".into(),
        4 => "네 번 ".into(),
        5 => "다섯 번 ".into(),
        _ => format!("{n}번 "),
    }
}

/// 답 글이 [focus]가 아닌 후보 하나를 이름으로 말하나: 그 후보 번호. focus의 이름이 나오거나, 둘 이상이 나오거나, 아무것도 없으면 None.
/// 이름은 들른 곳 이름(띄어쓰기 무시, 세 글자 이상)으로 맞춘다.
#[cfg(test)]
pub(crate) fn answer_points_elsewhere(answer: &str, focus: &MemoryHit, cands: &[MemoryHit]) -> Option<usize> {
    match answer_names(answer, focus, cands) {
        Some(v) if v.len() == 1 => Some(v[0]),
        _ => None,
    }
}

/// 답 글이 이름으로 말한 후보들 (focus 아닌 것, 같은 곳은 한 번, 후보 순서대로). focus의 이름이 나오면 None.
/// 이름은 들른 곳 이름(띄어쓰기 무시, 세 글자 이상)으로 맞춘다.
pub(crate) fn answer_names(answer: &str, focus: &MemoryHit, cands: &[MemoryHit]) -> Option<Vec<usize>> {
    let a: String = answer.chars().filter(|c| !c.is_whitespace()).collect();
    // 답에 나온 들른 곳 이름 (없으면 None)
    let said = |h: &MemoryHit| {
        h.episode.facts.stops.iter().find_map(|st| {
            // 세 글자 이상만: "카페" · "회사" · "마트" 같은 두 글자 이름은 다른 말 속에도 흔해 잘못 옮겨 갔다
            let n: String = st.name.chars().filter(|c| !c.is_whitespace()).collect();
            (n.chars().count() >= 3 && a.contains(&n)).then_some(n)
        })
    };
    if said(focus).is_some() {
        return None;
    }
    // 같은 곳을 여러 번 간 기록은 한 곳으로 센다 (앞 후보 = 더 맞는 기록)
    let mut seen: Vec<String> = Vec::new();
    let mut out = Vec::new();
    for (i, h) in cands.iter().enumerate().filter(|(_, h)| h.episode.facts.id != focus.episode.facts.id) {
        let Some(n) = said(h) else { continue };
        if !seen.contains(&n) {
            seen.push(n);
            out.push(i);
        }
    }
    Some(out)
}

/// 후보를 부를 이름: 질문과 가장 맞는 들른 곳, 없으면 첫 곳, 없으면 제목.
fn hit_place(h: &MemoryHit) -> String {
    h.best_stop
        .as_ref()
        .map(|b| b.name.clone())
        .filter(|n| !n.is_empty())
        .or_else(|| h.episode.facts.stops.first().map(|x| x.name.clone()).filter(|n| !n.is_empty()))
        .or_else(|| h.episode.title.clone())
        .or_else(|| h.day_title.clone())
        .unwrap_or_else(|| h.episode.facts.rule_label.clone())
}

/// 후보 고르는 말인가. Some(Some(i)) = i번째 후보, Some(None) = 없는 번호, None = 고르는 말이 아님 (새 질문).
/// "2번", "두 번째 거", "마지막 거로 안내해줘" / 새 검색어 없이 "그거 말고", "다른 거" → 지금([at]) 다음 후보.
fn candidate_pick(u: &str, cands: &[MemoryHit], at: usize) -> Option<Option<usize>> {
    let c = courses::compact(u);
    // 번호 말·고르는 말을 뺀 검색어가 남으면 새 질문이다 ("2번 국도 옆 식당"은 고르는 말이 아니다)
    let new_terms = words::parse(u).terms.iter().any(|w| {
        let w = courses::compact(w);
        courses::ordinal(&w, cands.len()).is_none()
            && !courses::OTHER_WORDS.iter().any(|x| w.contains(x))
            && !["거", "걸로", "거로", "곳", "후보", "번", "번째", "안내", "다른", "딴"].iter().any(|x| w.contains(x))
            // "다른 날도 보여줘"의 "날" · "보여"는 낱말 그대로일 때만 (날씨 · 날짜는 새 검색어)
            && w != "날" && w != "날도" && !w.starts_with("보여")
    });
    if new_terms || courses::is_past(u) {
        return None;
    }
    if let Some(i) = courses::ordinal(&c, cands.len()) {
        return Some((i < cands.len()).then_some(i));
    }
    // "다른 날도 보여줘"(답 아래 버튼)도 다음 후보로: LLM에 맡기면 카드 없이 "안내할까요?"만 남았다
    if courses::OTHER_WORDS.iter().any(|w| c.contains(w)) || c.contains("다른날") {
        return Some(Some((at + 1) % cands.len()));
    }
    None
}

/// "2025-08-14" → "작년 8월 14일" / "8월 14일" / "어제" …
pub fn spoken_date(day: &str, today: i64) -> String {
    let Some(d) = parse_ymd(day, false) else { return day.to_string() };
    let (y, m, dd) = dates::civil_from_days(d);
    let (ty, _, _) = dates::civil_from_days(today);
    match today - d {
        0 => return "오늘".into(),
        1 => return "어제".into(),
        2 => return "그저께".into(),
        _ => {}
    }
    match ty - y {
        0 => format!("{m}월 {dd}일"),
        1 => format!("작년 {m}월 {dd}일"),
        2 => format!("재작년 {m}월 {dd}일"),
        _ => format!("{y}년 {m}월 {dd}일"),
    }
}

#[cfg(test)]
mod tidy_tests {
    #[test]
    fn hit_data_carries_the_spoken_date() {
        use crate::memory::MemoryHit;
        let eps = super::super::tests::episodes();
        let e = eps.iter().find(|e| e.facts.id == 31).unwrap().clone(); // 2026-10-29
        let h = MemoryHit { day_title: None, episode: e, score: 1.0, best_stop: None, matched_tags: vec![], visit_days: vec![], visit_note: String::new(), people: None };
        let today = super::parse_ymd("2026-11-14", false).unwrap();
        // 올해 일은 "작년"이 붙지 않는다 (LLM이 day만 보고 "작년 10월 29일"이라 말했다)
        assert_eq!(super::tools::hit_json(&h, today)["say"], "10월 29일");
        let last_year = super::parse_ymd("2027-01-02", false).unwrap();
        assert_eq!(super::tools::hit_json(&h, last_year)["say"], "작년 10월 29일");
    }
    #[test]
    fn answer_naming_another_candidate_moves_the_card() {
        use crate::memory::MemoryHit;
        let eps = super::super::tests::episodes();
        let hit = |id: i64| {
            let e = eps.iter().find(|e| e.facts.id == id).unwrap().clone();
            MemoryHit { day_title: e.title.clone(), episode: e, score: 1.0, best_stop: None, matched_tags: vec![], visit_days: vec![], visit_note: String::new(), people: None }
        };
        let cands = vec![hit(80), hit(31), hit(12), hit(31)];
        // 카드는 두물머리(80)인데 답은 어니언 성수(31)를 말했다 → 31로
        assert_eq!(super::answer_points_elsewhere("작년 10월에 다녀오신 어니언 성수 기록이 있어요. 안내할까요?", &cands[0], &cands), Some(1));
        // (같은 곳 기록이 둘이어도 한 곳 — 앞 후보로)
        // 카드와 같은 곳을 말하면 그대로
        assert_eq!(super::answer_points_elsewhere("두물머리에 다녀오셨어요.", &cands[0], &cands), None);
        // 둘 이상을 말하거나 아무 곳도 말하지 않으면 그대로
        assert_eq!(super::answer_points_elsewhere("어니언 성수와 경포해변 중 어디요?", &cands[0], &cands), None);
        assert_eq!(super::answer_points_elsewhere("기록이 있어요.", &cands[0], &cands), None);
        // "다른 날도 보여줘"는 다음 후보 (새 검색어가 아니다)
        assert_eq!(super::candidate_pick("다른 날도 보여줘", &cands, 0), Some(Some(1)));
        assert_eq!(super::candidate_pick("다른 날 바다 간 데", &cands, 0), None);
        assert_eq!(super::candidate_pick("날씨 좋은 다른 데 없어", &cands, 0), None, "날씨는 새 검색어");
        // 둘 이상이면 후보 칸으로 (후보 순서대로)
        assert_eq!(super::answer_names("어니언 성수와 경포해변 기록이 있어요.", &cands[0], &cands), Some(vec![1, 2]));
    }

    use super::tidy_answer;

    #[test]
    fn parked_answer_stays_within_120_chars() {
        let s = "강릉 경포해변에 다녀오셨어요. ".repeat(3) + "그날은 순두부집에도 들르셨고 바다를 오래 보셨어요. 다시 가 볼까요?";
        let out = tidy_answer(&s, false);
        assert!(out.chars().count() <= 120 + 12, "{out}");
        assert!(out.ends_with("까요?"));
    }
}

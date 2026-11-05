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

use super::dates::{self, fmt_day, local_days, parse_ymd, DayRange};
use super::llm::{LlmError, Params};
use super::tools::{self, Ctx};
use super::words::{self, Answer};
use super::{prompts, AgentContext, AgentDone, AgentListener, AgentTiming, AnswerSource, MemoryAgent, NavProposal, Session};
use crate::memory::{MemoryHit, MemoryQuery};

pub const MAX_TOOL_ROUNDS: usize = 4;
pub const MAX_STRIKES: usize = 3;
const MAX_TURNS: usize = 6;
const MAX_HISTORY_CHARS: usize = 12_000;
const OLD_TOOL_KEEP: usize = 700;
pub const SESSION_IDLE_MS: i64 = 10 * 60_000;

pub const SERVER_DOWN: &str = "지금 AI 서버에 연결할 수 없어요.";
pub const NOT_CONFIGURED: &str = "AI 연결이 아직 설정되지 않았어요.";
pub const GIVE_UP: &str = "죄송해요, 지금은 찾지 못했어요. 조금 다르게 말씀해 주시겠어요?";
const EMPTY_ANSWER: &str = "죄송해요, 답을 정리하지 못했어요. 다시 말씀해 주세요.";
const NOT_FOUND: &str = "기록에서 비슷한 곳을 찾지 못했어요.";

/// 질문 한 번의 진행 상태.
struct Turn<'a> {
    l: &'a dyn AgentListener,
    start: Instant,
    answer: String,
    first_text_ms: Option<u64>,
    to_rec_ms: Option<u64>,
    llm_calls: u32,
    llm_ms: u64,
    /// 이번 질문에서 새로 생긴 대화 (끝까지 가야 세션에 넣는다 → 중간 실패로 대화가 깨지지 않는다)
    msgs: Vec<Value>,
    last_focus: Option<(i64, bool)>,
}

impl Turn<'_> {
    fn ms(&self) -> u64 {
        self.start.elapsed().as_millis() as u64
    }
    fn say(&mut self, text: &str) {
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
        self.l.on_recommendation(hit.clone(), course);
    }
}

impl MemoryAgent {
    pub(crate) fn run_turn(&self, utterance: &str, ctx: &AgentContext, l: &dyn AgentListener) {
        let gen = self.cancel_gen.load(Ordering::SeqCst);
        let mut s = self.session.lock().unwrap_or_else(|e| e.into_inner());
        let mut t = Turn { l, start: Instant::now(), answer: String::new(), first_text_ms: None, to_rec_ms: None, llm_calls: 0, llm_ms: 0, msgs: vec![], last_focus: None };
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

        // 2) 빠른 길: 규칙으로 날짜·검색어 → 기기 검색 (수 ms)
        let tz = ctx.tz_offset_minutes;
        let today = local_days(ctx.now_ms, tz);
        let parsed = words::parse(u);
        let range = dates::parse_korean_range(u, today);
        let mut fast_hits: Vec<MemoryHit> = vec![];
        if self.opts.fast_path && (!parsed.terms.is_empty() || range.is_some()) {
            let (from_ms, to_ms) = range.map(|r| r.to_ms(tz)).map_or((None, None), |(a, b)| (Some(a), Some(b)));
            let q = MemoryQuery { terms: parsed.terms.clone(), from_ms, to_ms, kinds: vec![], limit: 5 };
            fast_hits = self.backend.search_memories(q).unwrap_or_default();
            if let Some(top) = fast_hits.first() {
                t.recommend(top, None);
                s.tools.remember(&fast_hits);
                s.focus = Some(top.clone());
                s.candidates = fast_hits.clone();
            }
        }
        let narrow_date = range.is_some_and(|r| r.to - r.from <= 2);
        // 확실한가: 결과가 있고, 사람·앞 대화 말이 없고, 1위가 2위보다 분명히 앞서고, 한 글자 낱말(산·강…)만으로 찾은 게 아닐 때
        let clear_top = match fast_hits.as_slice() {
            [] => false,
            [_] => true,
            [a, b, ..] => a.score > b.score,
        };
        let only_short_terms = !parsed.terms.is_empty() && parsed.terms.iter().all(|w| w.chars().count() < 2);
        let confident = clear_top && !parsed.has_people && !parsed.has_reference && !only_short_terms && (!parsed.terms.is_empty() || narrow_date);
        let llm_usable = self.llm.is_some() && !self.server_down();

        if confident && !(self.opts.llm_when_confident && llm_usable) {
            self.fast_answer(&mut s, &mut t, &fast_hits[0], ctx, today);
            return self.finish(&mut s, t, u, AnswerSource::FastPath);
        }
        if !llm_usable {
            t.say(if self.llm.is_none() { NOT_CONFIGURED } else { SERVER_DOWN });
            t.say(" ");
            match fast_hits.first() {
                Some(top) => self.fast_answer(&mut s, &mut t, top, ctx, today),
                None => t.say(NOT_FOUND),
            }
            return self.finish(&mut s, t, u, AnswerSource::Fallback);
        }

        // 3) LLM 도구 루프
        match self.llm_loop(&mut s, &mut t, u, ctx, range, &fast_hits, gen) {
            Ok(()) => self.finish(&mut s, t, u, AnswerSource::Llm),
            Err(LlmError::Cancelled) => l.on_error("취소했어요.".into()),
            Err(e) => {
                eprintln!("[agent] LLM 실패: {e}");
                if t.answer.is_empty() {
                    if matches!(e, LlmError::Unreachable(_)) {
                        self.mark_server_down();
                    }
                    t.msgs.clear();
                    t.say(SERVER_DOWN);
                    t.say(" ");
                    match fast_hits.first() {
                        Some(top) => self.fast_answer(&mut s, &mut t, top, ctx, today),
                        None => t.say(NOT_FOUND),
                    }
                    self.finish(&mut s, t, u, AnswerSource::Fallback)
                } else {
                    l.on_error("답을 받다가 연결이 끊겼어요.".into());
                }
            }
        }
    }

    /// 틀 문장 답 + 안내 제안 (LLM 없이).
    fn fast_answer(&self, s: &mut Session, t: &mut Turn, top: &MemoryHit, ctx: &AgentContext, today: i64) {
        let f = &top.episode.facts;
        let title = top.episode.title.clone().or(top.day_title.clone()).unwrap_or(f.rule_label.clone());
        let place = top.best_stop.as_ref().map(|b| b.name.clone()).filter(|n| !n.is_empty())
            .or_else(|| f.stops.first().map(|x| x.name.clone()).filter(|n| !n.is_empty()))
            .unwrap_or(title.clone());
        let when = spoken_date(&f.day, today);
        // 안내 제안: 들른 곳이 여럿이면 코스, 하나면 그곳
        let mut proposal: Option<NavProposal> = None;
        if f.stops.len() > 1 {
            let mut c = Ctx { backend: self.backend.as_ref(), ctx, cache: &mut s.tools, proposal_seq: &mut s.proposal_seq };
            if let Ok((route, _)) = tools::plan(f.id, true, &mut c) {
                let p = tools::course_proposal(&mut s.proposal_seq, f.id, &title, &route);
                t.recommend(top, Some(route));
                proposal = Some(p);
            }
        }
        if proposal.is_none() {
            let stop = top.best_stop.clone().or_else(|| f.stops.first().cloned());
            proposal = stop.filter(|x| !x.name.is_empty()).map(|x| tools::place_proposal(&mut s.proposal_seq, x));
        }
        let ask = match &proposal {
            Some(p) if matches!(p.action, super::NavAction::Course { .. }) => " 그 코스로 안내할까요?",
            Some(_) => " 그곳으로 안내할까요?",
            None => "",
        };
        let text = if ctx.is_driving || f.stops.len() <= 1 {
            format!("{when}, {place}에 다녀오셨어요.{ask}")
        } else {
            let names = f.stops.iter().take(3).map(|x| x.name.as_str()).filter(|n| !n.is_empty()).collect::<Vec<_>>().join(", ");
            format!("{when} '{title}' 기록이에요. {names}에 들르셨어요.{ask}")
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
        for round in 0..=MAX_TOOL_ROUNDS {
            let allow_tools = round < MAX_TOOL_ROUNDS;
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
            let r = llm.stream(body, &mut |p| t.say(p), &cancelled)?;
            t.llm_calls += 1;
            t.llm_ms += r.total_ms;
            eprintln!(
                "[agent] LLM {}: 첫 토큰 {:?}ms 전체 {}ms 도구 {:?} timings={}",
                t.llm_calls, r.first_token_ms, r.total_ms, r.tool_calls.iter().map(|c| c.name.as_str()).collect::<Vec<_>>(),
                r.timings.as_ref().map(|x| x.to_string()).unwrap_or_default()
            );
            // "찾아볼게요"라고 말만 하고 도구를 안 부른 경우 한 번만 다시 시킨다 (작은 모델에서 자주 나온다, CAP announce_nudge)
            if r.tool_calls.is_empty() && allow_tools && !nudged && fast_hits.is_empty() && is_promise(&r.content) {
                nudged = true;
                t.msgs.push(json!({"role": "assistant", "content": r.content}));
                t.msgs.push(json!({"role": "user", "content": "[앱] 말로만 하지 말고 지금 도구를 불러라."}));
                t.say(" ");
                continue;
            }
            if r.tool_calls.is_empty() || !allow_tools {
                if r.content.is_empty() {
                    t.say(EMPTY_ANSWER);
                }
                t.msgs.push(json!({"role": "assistant", "content": t.answer.clone()}));
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
                let mut cx = Ctx { backend: self.backend.as_ref(), ctx, cache: &mut s.tools, proposal_seq: &mut s.proposal_seq };
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
                if let Some(p) = &o.proposal {
                    *self.pending.lock().unwrap_or_else(|e| e.into_inner()) = Some(p.clone());
                    t.l.on_action_proposal(p.clone());
                }
            }
            eprintln!("[agent] 도구 라운드 {}: {}", round + 1, outcomes.iter().map(|o| format!("{}={}", o.call.name, if o.ok { "ok" } else { "fail" })).collect::<Vec<_>>().join(", "));
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
        eprintln!("[agent] 끝 ({source:?}): 카드 {:?}ms 첫 글자 {:?}ms 전체 {}ms LLM {}회 {}ms", timing.to_recommendation_ms, timing.first_text_ms, timing.total_ms, timing.llm_calls, timing.llm_ms);
        t.l.on_done(AgentDone { answer: t.answer.trim().to_string(), source, timing });
    }

    pub(crate) fn warm_up_inner(&self) -> bool {
        let Some(llm) = self.llm.as_ref() else { return false };
        let messages = vec![json!({"role": "system", "content": prompts::AGENT_SYSTEM}), json!({"role": "user", "content": "[상황]\n안녕"})];
        let mut body = llm.body(&messages, &Params { temperature: 0.0, top_p: 0.95, max_tokens: 1 });
        body["tools"] = tools::definitions().clone();
        body["tool_choice"] = json!("auto");
        match llm.complete(body) {
            Ok(r) => {
                eprintln!("[agent] 미리 데우기 {}ms timings={}", r.total_ms, r.timings.map(|x| x.to_string()).unwrap_or_default());
                true
            }
            Err(e) => {
                eprintln!("[agent] 미리 데우기 실패: {e}");
                if matches!(e, LlmError::Unreachable(_)) {
                    self.mark_server_down();
                }
                false
            }
        }
    }
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
        let hits: Vec<Value> = fast_hits.iter().take(5).map(|h| tools::hit_json(h, ctx.tz_offset_minutes)).collect();
        b.push_str(&format!("기기 검색 결과(데이터, 질문에 맞으면 find 없이 이것으로 답한다): {}\n", Value::Array(hits)));
    } else if let Some(f) = &s.focus {
        b.push_str(&format!("앞서 찾은 기억(데이터): {}\n", tools::hit_json(f, ctx.tz_offset_minutes)));
        let others: Vec<Value> = s.candidates.iter().filter(|c| c.episode.facts.id != f.episode.facts.id).take(3).map(|h| tools::hit_json(h, ctx.tz_offset_minutes)).collect();
        if !others.is_empty() {
            b.push_str(&format!("다른 후보(데이터): {}\n", Value::Array(others)));
        }
    }
    if let Some(p) = pending {
        b.push_str(&format!("확인 대기 중인 안내 제안: {}\n", p.summary));
    }
    b.push_str("[운전자 말]\n");
    b
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

//! Gemini(중계 /llm, OpenAI 호환) 응답 모양 회귀 테스트 — 2026-11-13 실제로 받은 스트림을 줄여 옮겼다.
//! 다른 점: 도구 호출 조각에 `index`가 없고 호출 하나가 한 조각에 통째로, `extra_content.google.thought_signature`,
//! 도구를 불러도 finish_reason은 "stop", 마지막 조각은 글 없이 서명만.

use serde_json::json;

use super::llm::SseAccumulator;
use super::tests::{ask, ctx, fake_server, llm_only, ms, sse_text, Ev, FakeBackend};
use super::*;

fn sig() -> serde_json::Value {
    json!({"google": {"thought_signature": "EmAKXgFpFH0T"}})
}

fn gemini_tool(id: &str, name: &str, args: &str) -> String {
    let call = json!({"extra_content": sig(), "function": {"arguments": args, "name": name}, "id": id, "type": "function"});
    let o = json!({"choices": [{"delta": {"role": "assistant", "tool_calls": [call]}, "index": 0}], "model": "gemini-flash-lite-latest", "object": "chat.completion.chunk"});
    format!("data: {o}\n\n")
}

fn gemini_stop() -> String {
    let o = json!({"choices": [{"delta": {"role": "assistant", "extra_content": sig()}, "finish_reason": "stop", "index": 0}], "object": "chat.completion.chunk"});
    format!("data: {o}\n\ndata: [DONE]\n\n")
}

fn gemini_text(pieces: &[&str]) -> String {
    let mut s: String = pieces
        .iter()
        .map(|p| format!("data: {}\n\n", json!({"choices": [{"delta": {"content": p, "role": "assistant"}, "index": 0}], "object": "chat.completion.chunk"})))
        .collect();
    s += &gemini_stop();
    s
}

fn feed(raw: &str) -> llm::ChatResult {
    let mut acc = SseAccumulator::default();
    for line in raw.lines() {
        acc.on_line(line).unwrap();
    }
    acc.finish(None, 0)
}

fn done(r: &tests::Recorder) -> AgentDone {
    r.events().into_iter().find_map(|e| if let Ev::Done(d) = e { Some(d) } else { None }).expect("끝나야 함")
}

#[test]
fn gemini_tool_call_without_index_and_stop_finish() {
    let r = feed(&(gemini_tool("call_255395114723916087", "find", r#"{"from":"2026-11-12","to":"2026-11-12"}"#) + &gemini_stop()));
    assert_eq!(r.tool_calls.len(), 1);
    assert_eq!(r.tool_calls[0].id, "call_255395114723916087");
    assert_eq!(r.tool_calls[0].name, "find");
    assert_eq!(r.tool_calls[0].arguments, r#"{"from":"2026-11-12","to":"2026-11-12"}"#);
    assert_eq!(r.finish_reason.as_deref(), Some("stop"));
    assert_eq!(r.content, "");
}

#[test]
fn gemini_parallel_calls_in_separate_chunks_stay_separate() {
    let raw = gemini_tool("call_a", "find", r#"{"q":["바다"]}"#) + &gemini_tool("call_b", "day", r#"{"d":"2026-11-01"}"#) + &gemini_stop();
    let r = feed(&raw);
    let got: Vec<_> = r.tool_calls.iter().map(|c| (c.id.as_str(), c.name.as_str(), c.arguments.as_str())).collect();
    assert_eq!(got, vec![("call_a", "find", r#"{"q":["바다"]}"#), ("call_b", "day", r#"{"d":"2026-11-01"}"#)]);
}

#[test]
fn llama_cpp_indexed_fragments_still_join() {
    let mut acc = SseAccumulator::default();
    for l in [
        r#"data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"x","function":{"name":"find","arguments":""}}]}}]}"#,
        r#"data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"q\":"}}]}}]}"#,
        r#"data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"[\"산\"]}"}}]}}]}"#,
        r#"data: {"choices":[{"delta":{"content":null},"finish_reason":"tool_calls"}]}"#,
    ] {
        acc.on_line(l).unwrap();
    }
    let r = acc.finish(None, 0);
    assert_eq!(r.tool_calls.len(), 1);
    assert_eq!(r.tool_calls[0].arguments, r#"{"q":["산"]}"#);
}

#[test]
fn arguments_as_object_and_content_parts() {
    let r = feed(r#"data: {"choices":[{"delta":{"tool_calls":[{"id":"c1","function":{"name":"find","arguments":{"q":["카페"]}}}]}}]}"#);
    assert_eq!(r.tool_calls[0].arguments, r#"{"q":["카페"]}"#);
    let r = feed(r#"data: {"choices":[{"delta":{"content":[{"type":"text","text":"생각","thought":true},{"type":"text","text":"어제는 이마트예요."}]}}]}"#);
    assert_eq!(r.content, "어제는 이마트예요.");
    let r = llm::parse_completion(
        r#"{"choices":[{"message":{"content":null,"tool_calls":[{"function":{"name":"day","arguments":{"d":"2026-11-01"}}}]},"finish_reason":"stop"}]}"#,
        0,
    )
    .unwrap();
    assert_eq!(r.tool_calls[0].arguments, r#"{"d":"2026-11-01"}"#);
    assert_eq!(r.tool_calls[0].id, "call_0");
}

/// 오늘 2026-11-02 → 어제 11-01 (이마트 장보기 기록이 있는 날)
fn yesterday_ctx() -> AgentContext {
    AgentContext { now_ms: ms(2026, 11, 2, 10), ..ctx(true) }
}

#[test]
fn yesterday_question_has_no_search_words() {
    assert!(words::parse("어제 어디 갔지").terms.is_empty());
    assert!(words::parse("어제 어디 갔었어").terms.is_empty());
    assert!(words::parse("그저께 어디 다녀왔지").terms.is_empty());
}

#[test]
fn yesterday_fast_path_finds_the_day() {
    let agent = MemoryAgent::with_backend(FakeBackend::new(), tests::cfg(""), AgentOptions::default());
    let r = ask(&agent, "어제 어디 갔지", yesterday_ctx());
    assert!(r.events().iter().any(|e| matches!(e, Ev::Rec(60, _))), "{:?}", r.events());
}

/// Gemini가 실제로 보낸 모양: q에 "어디·곳·방문"을 채운다 → 예전에는 아무것도 못 찾았다.
#[test]
fn gemini_filler_terms_still_find_yesterday() {
    let srv = fake_server(
        vec![
            gemini_tool("call_1", "find", r#"{"from":"2026-11-01","to":"2026-11-01","q":["어디","곳","방문"]}"#) + &gemini_stop(),
            gemini_text(&["어제", "는 이마트 목동점에서 장을 보셨어요", "."]),
        ],
        200,
    );
    let backend = FakeBackend::new();
    let agent = srv.agent(backend.clone(), llm_only());
    let r = ask(&agent, "어제 어디 갔지", yesterday_ctx());
    let q = backend.queries.lock().unwrap().clone();
    assert!(q.iter().any(|q| q.terms.is_empty() && q.from_ms.is_some()), "{q:?}");
    assert_eq!(backend.results.lock().unwrap().last().unwrap(), &vec![60]);
    assert!(done(&r).answer.starts_with("어제는 이마트 목동점에서 장을 보셨어요."), "{}", done(&r).answer);
    // 도구 결과 메시지가 Gemini 호출 id를 그대로 이어 받는다
    let second = &srv.bodies()[1];
    let tool_msg = second["messages"].as_array().unwrap().iter().find(|m| m["role"] == "tool").unwrap().clone();
    assert_eq!(tool_msg["tool_call_id"], "call_1");
}

/// 좁은 기간에 낱말이 하나도 안 맞으면 그 기간 기록 전체 ("들른곳" 같은 말을 넣어도)
#[test]
fn narrow_range_falls_back_to_whole_day() {
    let srv = fake_server(
        vec![gemini_tool("c", "find", r#"{"from":"2026-11-01","to":"2026-11-01","q":["주말나들이"]}"#) + &gemini_stop(), gemini_text(&["이마트예요."])],
        200,
    );
    let backend = FakeBackend::new();
    ask(&srv.agent(backend.clone(), llm_only()), "어제 어디 갔지", yesterday_ctx());
    assert_eq!(backend.results.lock().unwrap().last().unwrap(), &vec![60]);
}

/// 도구 결과 뒤 글 없이 "stop"만 오면 도구 없이 한 번 더 묻는다 ("답을 정리하지 못했어요" 대신).
#[test]
fn empty_answer_after_tool_result_is_asked_again() {
    let srv = fake_server(
        vec![
            gemini_tool("call_1", "find", r#"{"from":"2026-11-01","to":"2026-11-01"}"#) + &gemini_stop(),
            gemini_stop(),
            sse_text(&["어제는 이마트에 다녀오셨어요."]),
        ],
        200,
    );
    let agent = srv.agent(FakeBackend::new(), llm_only());
    let r = ask(&agent, "어제 어디 갔지", yesterday_ctx());
    assert!(done(&r).answer.starts_with("어제는 이마트에 다녀오셨어요."), "{}", done(&r).answer);
    let bodies = srv.bodies();
    assert_eq!(bodies.len(), 3);
    assert!(bodies[2].get("tools").is_none(), "다시 물을 때는 도구 없이");
}

#[test]
fn find_without_terms_or_dates_is_rejected() {
    let srv = fake_server(vec![gemini_tool("c", "find", r#"{"q":["어디"]}"#) + &gemini_stop(), gemini_text(&["어느 때인지 말씀해 주세요."])], 200);
    let agent = srv.agent(FakeBackend::new(), llm_only());
    ask(&agent, "어디 갔었지", yesterday_ctx());
    let tool_msg = srv.bodies()[1]["messages"].as_array().unwrap().iter().find(|m| m["role"] == "tool").unwrap().clone();
    assert!(tool_msg["content"].as_str().unwrap().contains("\"ok\":false"));
}

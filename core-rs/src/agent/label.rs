//! 하루 라벨링 — 하루에 한 번 LLM이 제목·이야기·검색 태그를 붙인다 (docs/70_기억_검색_설계.md §2).
//!
//! 흐름: `pending_day_enrichments` → 하루씩 LLM (JSON 스키마로 출력 강제, 형식 예시 1개) → 검증 → `set_day_enrichment`.
//! - JSON이 틀리거나 제목이 규칙(짧고 자연스럽게, 경로 나열·상투어 금지, [title_problem])에 안 맞으면 무엇이 틀렸는지
//!   알려 주고 다시 묻는다 ([MAX_ATTEMPTS]번까지). 그래도 틀리면 6시간 뒤에.
//! - 제목 규칙이 바뀐 뒤 처음 도는 차례에서, 예전에 붙은 제목 중 규칙에 안 맞는 날을 한 번 다시 대기열에 넣는다.
//! - 서버가 꺼져 있으면 1분·2분·4분… 최대 1시간 뒤로 미루고 이번 차례를 멈춘다.
//! - 입력에 없는 에피소드 id는 버리고, 이모지·따옴표는 지운다 (지어낸 사실이 색인에 들어가지 않게).

use serde_json::{json, Value};

use std::sync::atomic::Ordering;

use super::dates::{civil_from_days, hhmm, parse_ymd};
use super::llm::{LlmClient, LlmError, Params};
use super::tools::{kind_name, km};
use super::{prompts, LabelReport, MemoryAgent};
use crate::memory::{is_short_move, DayEnrichment, DayFacts, EpisodeFacts, EpisodeLabel};

pub const BAD_OUTPUT_RETRY_MS: i64 = 6 * 60 * 60_000;
const MAX_STOPS: usize = 12;
const MAX_TITLE: usize = 30;
/// 제목 길이 상한 (글자, 띄어쓰기 포함). 더 길면 다시 쓰게 한다.
pub const TITLE_LIMIT: usize = 20;
/// 하루에 묻는 최대 횟수 (첫 요청 + 고쳐 쓰기)
const MAX_ATTEMPTS: usize = 3;
const MAX_SUMMARY: usize = 200;
const MAX_TAGS: usize = 20;
const MAX_EPISODE_TAGS: usize = 10;
const MAX_TAG_LEN: usize = 20;

/// 서버 꺼짐 n번째 → 1분, 2분, 4분 … 최대 1시간.
pub fn server_retry_ms(attempt: u32) -> i64 {
    (60_000i64 << attempt.saturating_sub(1).min(10)).min(60 * 60_000)
}

pub enum Labeled {
    Ok(DayEnrichment),
    BadOutput,
    ServerDown(LlmError),
}

impl MemoryAgent {
    pub(crate) fn label_pending(&self, limit: u32, tz: i32) -> LabelReport {
        let mut report = LabelReport { labeled: 0, bad_output: 0, server_down: false };
        let Some(llm) = self.llm.as_ref() else { return report };
        if !self.label_audit_done.swap(true, Ordering::SeqCst) {
            match self.backend.requeue_day_labels_where(&|t: &str| title_problem(t, &[]).is_some()) {
                Ok(0) => {}
                Ok(n) => alog!("[label] 예전 규칙으로 붙은 어색한 제목 {n}일을 다시 라벨링한다"),
                Err(e) => alog!("[label] 예전 제목 점검 실패: {e}"),
            }
        }
        let days = match self.backend.pending_day_enrichments(limit) {
            Ok(d) => d,
            Err(e) => {
                alog!("[label] pending 읽기 실패: {e}");
                return report;
            }
        };
        for facts in days {
            match label_one(llm, &facts, tz) {
                Labeled::Ok(e) => {
                    let title = e.title.clone();
                    match self.backend.set_day_enrichment(e) {
                        Ok(()) => {
                            alog!("[label] {} → \"{title}\"", facts.day);
                            report.labeled += 1;
                            self.label_failures.lock().unwrap_or_else(|e| e.into_inner()).remove(&facts.day);
                        }
                        Err(err) => alog!("[label] {} 저장 실패: {err}", facts.day),
                    }
                }
                Labeled::BadOutput => {
                    report.bad_output += 1;
                    let _ = self.backend.mark_day_enrichment_failed(facts.day.clone(), BAD_OUTPUT_RETRY_MS);
                }
                Labeled::ServerDown(e) => {
                    let n = {
                        let mut f = self.label_failures.lock().unwrap_or_else(|e| e.into_inner());
                        let n = f.entry(facts.day.clone()).or_insert(0);
                        *n += 1;
                        *n
                    };
                    alog!("[label] {} 서버 오류 ({n}번째): {e}", facts.day);
                    let _ = self.backend.mark_day_enrichment_failed(facts.day.clone(), server_retry_ms(n));
                    report.server_down = true;
                    return report; // 서버가 꺼져 있으면 다음 날도 실패하니 멈춘다
                }
            }
        }
        report
    }
}

/// 하루 하나 라벨링.
pub fn label_one(llm: &LlmClient, facts: &DayFacts, tz: i32) -> Labeled {
    let mut messages = vec![
        json!({"role": "system", "content": prompts::LABEL_SYSTEM}),
        json!({"role": "user", "content": prompts::LABEL_EXAMPLE_INPUT}),
        json!({"role": "assistant", "content": prompts::LABEL_EXAMPLE_OUTPUT}),
        json!({"role": "user", "content": facts_json(facts, tz).to_string()}),
    ];
    for attempt in 0..MAX_ATTEMPTS {
        let mut body = llm.body(&messages, &Params { temperature: 0.6, top_p: 0.9, max_tokens: 900 });
        body["response_format"] = response_format();
        let r = match llm.complete(body) {
            Ok(r) => r,
            Err(e @ (LlmError::Unreachable(_) | LlmError::Cancelled)) => return Labeled::ServerDown(e),
            Err(e) => {
                alog!("[label] {} 요청 오류: {e}", facts.day);
                return Labeled::BadOutput;
            }
        };
        alog!("[label] {} {}ms (시도 {}) timings={}", facts.day, r.total_ms, attempt + 1, r.timings.as_ref().map(|t| t.to_string()).unwrap_or_default());
        match parse_enrichment(facts, &r.content).and_then(|e| match title_problem(&e.title, &stop_names(facts)).or_else(|| short_move_problem(&e, facts)) {
            Some(p) => Err(format!("title \"{}\"이 규칙에 맞지 않습니다: {p}", e.title)),
            None => Ok(e),
        }) {
            Ok(e) => return Labeled::Ok(e),
            Err(problem) => {
                alog!(
                    "[label] {} 출력 오류 (시도 {}, 끝 이유 {:?}): {problem} — {}",
                    facts.day, attempt + 1, r.finish_reason, r.content.chars().take(160).collect::<String>()
                );
                messages.push(json!({"role": "assistant", "content": r.content.chars().take(2000).collect::<String>()}));
                messages.push(json!({"role": "user", "content": format!("출력이 규칙에 맞지 않아요: {problem}. 같은 입력으로 JSON 객체 하나만 다시 써 주세요.")}));
            }
        }
    }
    Labeled::BadOutput
}

/// llama.cpp `response_format` (문법 제약 디코딩으로 이 스키마만 나오게 한다).
pub fn response_format() -> Value {
    let strs = json!({"type": "array", "items": {"type": "string"}});
    json!({"type": "json_schema", "json_schema": {"name": "day_label", "strict": true, "schema": {
        "type": "object",
        "properties": {
            "title": {"type": "string"}, "summary": {"type": "string"}, "tags": strs,
            "episodes": {"type": "array", "items": {"type": "object",
                "properties": {"episode_id": {"type": "integer"}, "title": {"type": "string"}, "tags": strs},
                "required": ["episode_id", "title", "tags"], "additionalProperties": false}},
        },
        "required": ["title", "summary", "tags", "episodes"], "additionalProperties": false,
    }}})
}

/// 모델 출력 검증 → DayEnrichment.
pub fn parse_enrichment(facts: &DayFacts, raw: &str) -> Result<DayEnrichment, String> {
    let text = raw.trim().trim_start_matches("```json").trim_start_matches("```").trim_end_matches("```").trim();
    // 앞뒤에 설명 글이 붙어 와도 가장 바깥 {…}만 꺼내 본다 (CAP llm.chat_json과 같은 처리)
    let o: Value = serde_json::from_str(text)
        .ok()
        .or_else(|| {
            let (s, e) = (text.find('{')?, text.rfind('}')?);
            (e > s).then(|| serde_json::from_str(&text[s..=e]).ok()).flatten()
        })
        .filter(Value::is_object)
        .ok_or("JSON 객체가 아닙니다")?;
    let title = cut(&clean(o["title"].as_str().unwrap_or("")), MAX_TITLE);
    if title.is_empty() {
        return Err("title이 비었습니다".into());
    }
    let summary = cut(&clean(o["summary"].as_str().unwrap_or("")), MAX_SUMMARY);
    if summary.is_empty() {
        return Err("summary가 비었습니다".into());
    }
    let tags = strings(&o["tags"], MAX_TAGS);
    if tags.is_empty() {
        return Err("tags가 비었습니다".into());
    }
    let known: Vec<i64> = facts.episodes.iter().map(|e| e.id).collect();
    let eps = o["episodes"].as_array().ok_or("episodes 배열이 없습니다")?;
    let mut labels: Vec<EpisodeLabel> = vec![];
    for e in eps {
        let Some(id) = e["episode_id"].as_i64().filter(|id| known.contains(id)) else { continue };
        let t = cut(&clean(e["title"].as_str().unwrap_or("")), MAX_TITLE);
        if t.is_empty() || labels.iter().any(|l| l.episode_id == id) {
            continue;
        }
        labels.push(EpisodeLabel { episode_id: id, title: t, tags: strings(&e["tags"], MAX_EPISODE_TAGS) });
    }
    if labels.is_empty() && !known.is_empty() {
        return Err(format!("episodes에 입력의 episode_id가 하나도 없습니다 ({known:?})"));
    }
    Ok(DayEnrichment { day: facts.day.clone(), title, summary, tags, episodes: labels })
}

fn strings(v: &Value, max: usize) -> Vec<String> {
    let mut out: Vec<String> = vec![];
    for s in v.as_array().into_iter().flatten().filter_map(Value::as_str) {
        let c = cut(&clean(s), MAX_TAG_LEN);
        if !c.is_empty() && !out.contains(&c) {
            out.push(c);
        }
    }
    out.truncate(max);
    out
}

fn cut(s: &str, n: usize) -> String {
    s.chars().take(n).collect::<String>().trim().to_string()
}

/// 이모지·기호·따옴표 제거, 공백 정리.
fn clean(s: &str) -> String {
    let kept: String = s
        .chars()
        .filter(|&c| {
            let u = c as u32;
            !matches!(u, 0x1F000..=0x1FAFF | 0x2600..=0x27BF | 0x2B00..=0x2BFF | 0xFE0F | 0x200D | 0x2300..=0x23FF)
                && !"\"“”‘’`*#".contains(c)
        })
        .collect();
    kept.split_whitespace().collect::<Vec<_>>().join(" ")
}

/// 제목이 규칙에 안 맞으면 무엇이 문제인지 (모델에게 그대로 돌려준다). [stops]: 그날 들른 곳 이름.
/// - 너무 김, 날짜 숫자, 상투어, 경로 말(출발·도착·경유), 들른 곳 이름 늘어놓기(3곳 이상).
/// - 휴게소·주유소는 그날 다른 곳에 들렀으면 제목에 넣지 않는다 (지나가는 곳이지 그날의 이야기가 아니다).
pub fn title_problem(title: &str, stops: &[String]) -> Option<String> {
    let t = title.trim();
    let len = t.chars().count();
    if len > TITLE_LIMIT {
        return Some(format!("{len}자로 너무 깁니다. {TITLE_LIMIT}자 안으로 줄이세요"));
    }
    if t.chars().any(|c| c.is_ascii_digit()) {
        return Some("숫자(날짜·시각)를 넣지 마세요".into());
    }
    for w in ["행복한", "소중한", "특별한", "힐링", "즐거운", "기록", "하루 일과"] {
        if t.contains(w) {
            return Some(format!("상투적인 말 '{w}'을 빼세요"));
        }
    }
    // " · "는 규칙 제목("퇴근길 · 이마트 들름")의 모양이다 ("바다·순두부"처럼 붙여 쓴 가운뎃점은 괜찮다)
    for w in ["출발", "도착", "경유", "→", "->", " · "] {
        if t.contains(w) {
            return Some(format!("'{w}'처럼 경로를 늘어놓지 말고 그날의 핵심 하나로 쓰세요"));
        }
    }
    let passing = |n: &str| n.contains("휴게소") || n.contains("주유소") || n.contains("충전소");
    // 들른 곳을 모르면(예전 제목 점검) 이 규칙은 건너뛴다 — 주유소만 들른 날의 "응봉 주유소 들르기"를 매번 다시 묻지 않게
    let has_main = stops.iter().any(|n| !passing(n));
    if has_main && ["휴게소", "주유소", "충전소"].iter().any(|w| t.contains(w)) {
        return Some("휴게소·주유소는 지나가는 곳이라 제목에 넣지 마세요".into());
    }
    let named = stops.iter().filter(|n| n.chars().count() >= 2 && t.contains(n.as_str())).count();
    if named >= 3 {
        return Some("들른 곳 이름을 늘어놓지 말고 가장 기억에 남을 곳 하나만 쓰세요".into());
    }
    None
}

/// 짧게 잠깐 움직인 날·에피소드(예: 747m · 1분)를 "여행"이라 부풀리면 문제 (모델에게 그대로 돌려준다).
/// 에피소드 제목은 그 에피소드가, 하루 제목은 그날 모든 에피소드가 짧을 때만 본다.
pub fn short_move_problem(e: &DayEnrichment, f: &DayFacts) -> Option<String> {
    let short = |x: &EpisodeFacts| is_short_move(x.distance_m, x.end_ms - x.start_ms);
    let exaggerates = |t: &str| ["여행", "원정", "투어"].iter().find(|w| t.contains(*w)).copied();
    let msg = |w: &str| format!("잠깐({}m 남짓) 움직인 기록이라 '{w}'은 과장입니다. \"경포해변 들름\"처럼 사실대로 쓰세요", f.total_distance_m.round());
    if !f.episodes.is_empty() && f.episodes.iter().all(short) {
        if let Some(w) = exaggerates(&e.title) {
            return Some(msg(w));
        }
    }
    for l in &e.episodes {
        if f.episodes.iter().any(|x| x.id == l.episode_id && short(x)) {
            if let Some(w) = exaggerates(&l.title) {
                return Some(msg(w));
            }
        }
    }
    None
}

fn stop_names(f: &DayFacts) -> Vec<String> {
    f.episodes.iter().flat_map(|e| e.stops.iter().map(|s| s.name.clone())).filter(|n| !n.is_empty()).collect()
}

/// 날짜("2025-08-14") → 계절 (3~5월 봄, 6~8월 여름, 9~11월 가을, 12~2월 겨울).
pub fn season(day: &str) -> &'static str {
    match parse_ymd(day, false).map(|d| civil_from_days(d).1) {
        Some(3..=5) => "봄",
        Some(6..=8) => "여름",
        Some(9..=11) => "가을",
        Some(_) => "겨울",
        None => "",
    }
}

/// 프롬프트에 넣는 하루 사실 (작게: 시각은 HH:mm, 거리는 km, 좌표 없음).
pub fn facts_json(f: &DayFacts, tz: i32) -> Value {
    json!({
        "day": f.day, "weekday": f.weekday, "season": season(&f.day), "total_km": km(f.total_distance_m),
        "episodes": f.episodes.iter().map(|e| json!({
            "episode_id": e.id, "kind": kind_name(e.kind), "rule_label": e.rule_label,
            "start": hhmm(e.start_ms, tz), "end": hhmm(e.end_ms, tz), "km": km(e.distance_m),
            "from": e.from_name, "to": e.to_name, "region": e.region,
            "stops": e.stops.iter().take(MAX_STOPS).map(|s| json!({
                "name": s.name, "category": s.category, "arrive": hhmm(s.arrive_ms, tz),
                "stay_min": ((s.leave_ms - s.arrive_ms) / 60_000).max(0), "first_visit": s.first_visit,
            })).collect::<Vec<_>>(),
        })).collect::<Vec<_>>(),
    })
}

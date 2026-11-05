//! 하루 라벨링 — 하루에 한 번 LLM이 제목·이야기·검색 태그를 붙인다 (docs/70_기억_검색_설계.md §2).
//!
//! 흐름: `pending_day_enrichments` → 하루씩 LLM (JSON 스키마로 출력 강제, 형식 예시 1개) → 검증 → `set_day_enrichment`.
//! - JSON이 틀리면 무엇이 틀렸는지 알려 주고 한 번 더 묻는다. 그래도 틀리면 6시간 뒤에.
//! - 서버가 꺼져 있으면 1분·2분·4분… 최대 1시간 뒤로 미루고 이번 차례를 멈춘다.
//! - 입력에 없는 에피소드 id는 버리고, 이모지·따옴표는 지운다 (지어낸 사실이 색인에 들어가지 않게).

use serde_json::{json, Value};

use super::dates::hhmm;
use super::llm::{LlmClient, LlmError, Params};
use super::tools::{kind_name, km};
use super::{prompts, LabelReport, MemoryAgent};
use crate::memory::{DayEnrichment, DayFacts, EpisodeLabel};

pub const BAD_OUTPUT_RETRY_MS: i64 = 6 * 60 * 60_000;
const MAX_STOPS: usize = 12;
const MAX_TITLE: usize = 30;
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
        let days = match self.backend.pending_day_enrichments(limit) {
            Ok(d) => d,
            Err(e) => {
                eprintln!("[label] pending 읽기 실패: {e}");
                return report;
            }
        };
        for facts in days {
            match label_one(llm, &facts, tz) {
                Labeled::Ok(e) => {
                    if self.backend.set_day_enrichment(e).is_ok() {
                        report.labeled += 1;
                        self.label_failures.lock().unwrap_or_else(|e| e.into_inner()).remove(&facts.day);
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
                    eprintln!("[label] {} 서버 오류 ({n}번째): {e}", facts.day);
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
    for attempt in 0..2 {
        let mut body = llm.body(&messages, &Params { temperature: 0.3, top_p: 0.9, max_tokens: 900 });
        body["response_format"] = response_format();
        let r = match llm.complete(body) {
            Ok(r) => r,
            Err(e @ (LlmError::Unreachable(_) | LlmError::Cancelled)) => return Labeled::ServerDown(e),
            Err(e) => {
                eprintln!("[label] {} 요청 오류: {e}", facts.day);
                return Labeled::BadOutput;
            }
        };
        eprintln!("[label] {} {}ms (시도 {}) timings={}", facts.day, r.total_ms, attempt + 1, r.timings.as_ref().map(|t| t.to_string()).unwrap_or_default());
        match parse_enrichment(facts, &r.content) {
            Ok(e) => return Labeled::Ok(e),
            Err(problem) => {
                eprintln!("[label] {} JSON 오류: {problem}", facts.day);
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

/// 프롬프트에 넣는 하루 사실 (작게: 시각은 HH:mm, 거리는 km, 좌표 없음).
pub fn facts_json(f: &DayFacts, tz: i32) -> Value {
    json!({
        "day": f.day, "weekday": f.weekday, "total_km": km(f.total_distance_m),
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

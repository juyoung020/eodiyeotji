//! OpenAI 호환 `/v1/chat/completions` 최소 클라이언트 (개발: llama.cpp `llama-server` + Qwen3.5-9B, `--jinja`).
//!
//! - 스트리밍(SSE): 글 조각은 바로 콜백으로, 도구 호출 조각은 `index`별로 id·name·arguments를 이어 붙인다.
//! - Qwen 생각 글: `chat_template_kwargs.enable_thinking=false`로 끄고, 그래도 오는 `reasoning_content`·`<think>`는 버린다.
//! - 서버 파서가 놓쳐 본문으로 샌 `<tool_call>{json}</tool_call>`은 도구 호출로 되살린다 (CAP `llm.parse_leaked_tool_calls`).
//! - 연결 시간 제한은 짧게(서버가 꺼져 있으면 바로 알아채 기기 안 검색으로 넘어간다), 취소는 줄마다 확인한다.

use std::io::{BufRead, BufReader, Read};
use std::time::{Duration, Instant};

use serde_json::{json, Value};

/// LLM 서버 설정 (Kotlin: local.properties → BuildConfig → 여기).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct LlmConfig {
    /// `/v1`까지 (예: 에뮬레이터 `http://10.0.2.2:8181/v1`). 비어 있으면 LLM을 쓰지 않는다.
    pub base_url: String,
    pub model: String,
    /// 로컬 서버는 빈 값. 값이 있으면 `Authorization: Bearer`.
    pub api_key: String,
    /// 연결 시간 제한 (서버 꺼짐을 빨리 알아채도록 짧게, 예: 2000)
    pub connect_timeout_ms: u32,
    /// 읽기 시간 제한 (스트림 조각 사이 최대 간격, 예: 60000)
    pub read_timeout_ms: u32,
}

impl LlmConfig {
    pub fn is_configured(&self) -> bool {
        !self.base_url.trim().is_empty() && !self.model.trim().is_empty()
    }
}

#[derive(Debug, Clone, PartialEq)]
pub enum LlmError {
    /// 연결 실패·시간 초과·서버 오류(5xx)·끊김 → 서버가 없다고 보고 기기 안 검색으로
    Unreachable(String),
    /// 4xx (요청이 틀림)
    Http(u16, String),
    /// 응답 형식이 이상함
    BadResponse(String),
    Cancelled,
}

impl std::fmt::Display for LlmError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            LlmError::Unreachable(m) => write!(f, "LLM 서버 연결 실패: {m}"),
            LlmError::Http(c, m) => write!(f, "LLM HTTP {c}: {m}"),
            LlmError::BadResponse(m) => write!(f, "LLM 응답 이상: {m}"),
            LlmError::Cancelled => write!(f, "취소됨"),
        }
    }
}

/// 모델이 부른 도구 하나. [arguments]는 모델이 쓴 JSON 문자열 그대로 (검증은 부르는 쪽에서).
#[derive(Debug, Clone, PartialEq)]
pub struct ToolCall {
    pub id: String,
    pub name: String,
    pub arguments: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ChatResult {
    /// 보이는 답 글 (생각 글·샌 도구 호출 태그는 뺀 것)
    pub content: String,
    pub tool_calls: Vec<ToolCall>,
    pub finish_reason: Option<String>,
    /// 요청 → 첫 출력(글 또는 도구 호출 조각) ms (스트리밍일 때만)
    pub first_token_ms: Option<u64>,
    pub total_ms: u64,
    /// llama.cpp 처리 통계 (prompt_n, prompt_ms, predicted_n, predicted_per_second …)
    pub timings: Option<Value>,
}

/// 요청 한 번의 생성 설정.
#[derive(Debug, Clone)]
pub struct Params {
    pub temperature: f64,
    pub top_p: f64,
    pub max_tokens: u32,
}

pub struct LlmClient {
    cfg: LlmConfig,
    http: ureq::Agent,
    url: String,
}

impl LlmClient {
    pub fn new(cfg: LlmConfig) -> Self {
        let http = ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_millis(cfg.connect_timeout_ms.max(200) as u64))
            .timeout_read(Duration::from_millis(cfg.read_timeout_ms.max(1_000) as u64))
            .build();
        let url = format!("{}/chat/completions", cfg.base_url.trim().trim_end_matches('/'));
        Self { cfg, http, url }
    }

    pub fn config(&self) -> &LlmConfig {
        &self.cfg
    }

    /// 요청 본문 (공통 부분). Qwen 생각 끔, llama.cpp 앞부분 캐시 재사용.
    pub fn body(&self, messages: &[Value], p: &Params) -> Value {
        json!({
            "model": self.cfg.model,
            "messages": messages,
            "temperature": p.temperature,
            "top_p": p.top_p,
            "max_tokens": p.max_tokens,
            "cache_prompt": true,
            "chat_template_kwargs": {"enable_thinking": false},
        })
    }

    /// 스트리밍 호출. 글 조각마다 [on_text], 줄마다 [cancelled]를 확인한다.
    pub fn stream(&self, mut body: Value, on_text: &mut dyn FnMut(&str), cancelled: &dyn Fn() -> bool) -> Result<ChatResult, LlmError> {
        body["stream"] = json!(true);
        let start = Instant::now();
        let reader = self.post(&body)?;
        let mut acc = SseAccumulator::default();
        let mut first: Option<u64> = None;
        for line in BufReader::new(reader).lines() {
            if cancelled() {
                return Err(LlmError::Cancelled);
            }
            let line = line.map_err(|e| LlmError::Unreachable(format!("읽기 실패: {e}")))?;
            let piece = acc.on_line(&line)?;
            if first.is_none() && acc.saw_output {
                first = Some(start.elapsed().as_millis() as u64);
            }
            if let Some(p) = piece.filter(|p| !p.is_empty()) {
                on_text(&p);
            }
        }
        Ok(acc.finish(first, start.elapsed().as_millis() as u64))
    }

    /// 한 번에 받는 호출 (하루 라벨링, 미리 데우기).
    pub fn complete(&self, mut body: Value) -> Result<ChatResult, LlmError> {
        body["stream"] = json!(false);
        let start = Instant::now();
        let mut raw = String::new();
        self.post(&body)?.read_to_string(&mut raw).map_err(|e| LlmError::Unreachable(format!("읽기 실패: {e}")))?;
        parse_completion(&raw, start.elapsed().as_millis() as u64)
    }

    fn post(&self, body: &Value) -> Result<Box<dyn Read + Send + Sync>, LlmError> {
        let mut req = self.http.post(&self.url).set("Content-Type", "application/json").set("Accept", "application/json, text/event-stream");
        if !self.cfg.api_key.trim().is_empty() {
            req = req.set("Authorization", &format!("Bearer {}", self.cfg.api_key.trim()));
        }
        match req.send_string(&body.to_string()) {
            Ok(resp) => Ok(resp.into_reader()),
            Err(ureq::Error::Status(code, resp)) => {
                let msg: String = resp.into_string().unwrap_or_default().chars().take(300).collect();
                if code >= 500 || code == 429 {
                    Err(LlmError::Unreachable(format!("HTTP {code}: {msg}")))
                } else {
                    Err(LlmError::Http(code, msg))
                }
            }
            Err(e) => Err(LlmError::Unreachable(e.to_string())),
        }
    }
}

/// 스트리밍하지 않은 응답 본문 해석.
pub fn parse_completion(raw: &str, total_ms: u64) -> Result<ChatResult, LlmError> {
    let root: Value = serde_json::from_str(raw).map_err(|_| LlmError::BadResponse(raw.chars().take(120).collect()))?;
    let choice = root["choices"].get(0).ok_or_else(|| LlmError::BadResponse("choices 없음".into()))?;
    let msg = &choice["message"];
    let mut calls: Vec<ToolCall> = msg["tool_calls"]
        .as_array()
        .map(|a| {
            a.iter()
                .enumerate()
                .filter_map(|(i, c)| {
                    let f = c.get("function")?;
                    Some(ToolCall {
                        id: c["id"].as_str().filter(|s| !s.is_empty()).map(String::from).unwrap_or(format!("call_{i}")),
                        name: f["name"].as_str().unwrap_or("").trim().to_string(),
                        arguments: f["arguments"].as_str().map(String::from).unwrap_or_else(|| f["arguments"].to_string()),
                    })
                })
                .collect()
        })
        .unwrap_or_default();
    let mut vis = VisibleText::default();
    let mut content = vis.feed(msg["content"].as_str().unwrap_or(""));
    content.push_str(&vis.flush());
    if calls.is_empty() {
        calls = vis.leaked_tool_calls();
    }
    Ok(ChatResult {
        content: content.trim().to_string(),
        tool_calls: calls,
        finish_reason: choice["finish_reason"].as_str().map(String::from),
        first_token_ms: None,
        total_ms,
        timings: root.get("timings").cloned(),
    })
}

#[derive(Default)]
struct CallParts {
    id: Option<String>,
    name: String,
    args: String,
}

/// SSE(`data: {...}` 줄)를 모은다. 한 줄 넣으면 그 줄에서 나온 보이는 글 조각을 돌려준다.
#[derive(Default)]
pub struct SseAccumulator {
    text: String,
    visible: VisibleText,
    calls: std::collections::BTreeMap<i64, CallParts>,
    finish: Option<String>,
    timings: Option<Value>,
    /// 글 또는 도구 호출 조각을 받았는지 (첫 토큰 시간 재기용)
    pub saw_output: bool,
}

impl SseAccumulator {
    pub fn on_line(&mut self, raw: &str) -> Result<Option<String>, LlmError> {
        let line = raw.trim();
        let Some(data) = line.strip_prefix("data:") else { return Ok(None) }; // 빈 줄·주석(:)·event:
        let data = data.trim();
        if data.is_empty() || data == "[DONE]" {
            return Ok(None);
        }
        let Ok(obj) = serde_json::from_str::<Value>(data) else { return Ok(None) }; // 깨진 조각 하나로 답 전체를 버리지 않는다
        if let Some(e) = obj.get("error") {
            return Err(LlmError::Unreachable(format!("스트림 오류: {}", e["message"].as_str().unwrap_or(&e.to_string()))));
        }
        if let Some(t) = obj.get("timings") {
            self.timings = Some(t.clone());
        }
        let Some(choice) = obj["choices"].get(0) else { return Ok(None) };
        if let Some(f) = choice["finish_reason"].as_str() {
            self.finish = Some(f.to_string());
        }
        let delta = &choice["delta"];
        // reasoning_content(생각 글)는 버린다
        if let Some(arr) = delta["tool_calls"].as_array() {
            for (i, c) in arr.iter().enumerate() {
                let parts = self.calls.entry(c["index"].as_i64().unwrap_or(i as i64)).or_default();
                if let Some(id) = c["id"].as_str().filter(|s| !s.is_empty()) {
                    parts.id = Some(id.to_string());
                }
                if let Some(f) = c.get("function") {
                    if let Some(n) = f["name"].as_str().filter(|s| !s.is_empty()) {
                        if parts.name.is_empty() {
                            parts.name.push_str(n);
                        }
                    }
                    if let Some(a) = f["arguments"].as_str() {
                        parts.args.push_str(a);
                    }
                }
                self.saw_output = true;
            }
        }
        let Some(piece) = delta["content"].as_str() else { return Ok(None) };
        if !piece.is_empty() {
            self.saw_output = true;
        }
        let shown = self.visible.feed(piece);
        self.text.push_str(&shown);
        Ok(Some(shown))
    }

    pub fn finish(mut self, first_token_ms: Option<u64>, total_ms: u64) -> ChatResult {
        let rest = self.visible.flush();
        self.text.push_str(&rest);
        let mut calls: Vec<ToolCall> = self
            .calls
            .iter()
            .filter(|(_, p)| !p.name.trim().is_empty())
            .map(|(i, p)| ToolCall { id: p.id.clone().unwrap_or(format!("call_{i}")), name: p.name.trim().to_string(), arguments: p.args.clone() })
            .collect();
        if calls.is_empty() {
            calls = self.visible.leaked_tool_calls();
        }
        ChatResult {
            content: self.text.trim().to_string(),
            tool_calls: calls,
            finish_reason: self.finish,
            first_token_ms,
            total_ms,
            timings: self.timings,
        }
    }
}

/// 답 글에서 `open…close` 구간을 걸러 낸다 (태그가 조각 경계에서 잘려 와도). 걸러 낸 글은 [captured]에.
pub struct TagFilter {
    open: &'static str,
    close: &'static str,
    inside: bool,
    pending: String,
    current: String,
    pub captured: Vec<String>,
}

impl TagFilter {
    pub fn new(open: &'static str, close: &'static str) -> Self {
        Self { open, close, inside: false, pending: String::new(), current: String::new(), captured: Vec::new() }
    }

    pub fn feed(&mut self, piece: &str) -> String {
        self.pending.push_str(piece);
        let mut out = String::new();
        loop {
            let tag = if self.inside { self.close } else { self.open };
            if let Some(i) = self.pending.find(tag) {
                if self.inside {
                    self.current.push_str(&self.pending[..i]);
                    self.captured.push(std::mem::take(&mut self.current));
                } else {
                    out.push_str(&self.pending[..i]);
                }
                self.pending.drain(..i + tag.len());
                self.inside = !self.inside;
                continue;
            }
            // 태그 앞부분일 수 있는 끝 글자는 다음 조각까지 남긴다
            let keep = partial_suffix(&self.pending, tag);
            let cut = self.pending.len() - keep;
            if self.inside {
                self.current.push_str(&self.pending[..cut]);
            } else {
                out.push_str(&self.pending[..cut]);
            }
            self.pending.drain(..cut);
            break;
        }
        out
    }

    pub fn flush(&mut self) -> String {
        let rest = std::mem::take(&mut self.pending);
        if self.inside {
            self.current.push_str(&rest);
            self.captured.push(std::mem::take(&mut self.current));
            String::new()
        } else {
            rest
        }
    }
}

fn partial_suffix(s: &str, tag: &str) -> usize {
    (1..tag.len().min(s.len() + 1)).rev().find(|&n| s.is_char_boundary(s.len() - n) && tag.as_bytes()[..n] == s.as_bytes()[s.len() - n..]).unwrap_or(0)
}

/// 보이는 답 글: 생각 글을 빼고, 본문으로 샌 도구 호출도 빼서 되살린다.
pub struct VisibleText {
    think: TagFilter,
    tool: TagFilter,
}

impl Default for VisibleText {
    fn default() -> Self {
        Self { think: TagFilter::new("<think>", "</think>"), tool: TagFilter::new("<tool_call>", "</tool_call>") }
    }
}

impl VisibleText {
    pub fn feed(&mut self, piece: &str) -> String {
        let a = self.think.feed(piece);
        self.tool.feed(&a)
    }

    pub fn flush(&mut self) -> String {
        let a = self.think.flush();
        let mut out = self.tool.feed(&a);
        out.push_str(&self.tool.flush());
        out
    }

    pub fn leaked_tool_calls(&self) -> Vec<ToolCall> {
        self.tool
            .captured
            .iter()
            .enumerate()
            .filter_map(|(i, raw)| {
                let o: Value = serde_json::from_str(raw.trim()).ok()?;
                let name = o["name"].as_str()?.trim().to_string();
                if name.is_empty() {
                    return None;
                }
                let args = o.get("arguments").or_else(|| o.get("parameters")).cloned().unwrap_or(json!({}));
                let arguments = args.as_str().map(String::from).unwrap_or_else(|| args.to_string());
                Some(ToolCall { id: format!("leak_{i}"), name, arguments })
            })
            .collect()
    }
}

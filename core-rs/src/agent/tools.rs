//! 에이전트 도구 5개 — 작은 모델은 도구가 많으면 잘못 고른다 → 꼭 필요한 것만, 이름·인자는 짧게
//! (도구 호출 JSON 생성이 LLM 지연의 대부분이라 글자 수가 곧 시간이다).
//!
//! | 도구 | 하는 일 | 인자 |
//! |---|---|---|
//! | `find` | 기억 검색 (Rust search_memories) | q: 낱말 ≤3, from/to: YYYY-MM(-DD), kind |
//! | `day` | 하루의 에피소드·들른 곳 | d: YYYY-MM-DD |
//! | `course` | 에피소드 코스 (순서·거리·내비 구간) | id, here: 지금 위치에서 최적 순서 |
//! | `courses` | 근처 인기 드라이브 추천 코스 (Rust recommend_drive_courses, 커뮤니티) | q: 테마·지역, km, k |
//! | `nav` | 안내 **제안**만 (실행은 운전자 확인 뒤 confirm) | mode: course/place/search/pick, id, stop, kw, n |
//!
//! 결과는 언제나 `{"ok":true,"data":{…}}` 또는 `{"ok":false,"error":"…"}` 봉투에 담은 데이터이고, 작게 줄인다.

use std::collections::HashMap;
use std::sync::OnceLock;

use serde_json::{json, Value};

use super::courses::{self, CourseState};
use super::dates::{self, fmt_day, hhmm, local_days, parse_ymd, WEEKDAYS};
use super::llm::ToolCall;
use super::{AgentContext, MemoryBackend, NavAction, NavProposal};
use crate::memory::{CourseRoute, Episode, EpisodeKind, MemoryHit, MemoryQuery, StopFact};

pub const FIND: &str = "find";
pub const DAY: &str = "day";
pub const COURSE: &str = "course";
pub const NAV: &str = "nav";
pub const COURSES: &str = "courses";

const MAX_TERMS: usize = 3;
const MAX_HITS: usize = 5;
const MAX_STOP_NAMES: usize = 5;
const MAX_DAY_EPISODES: usize = 6;
const MAX_DAY_STOPS: usize = 8;

/// 도구 정의 (OpenAI 함수 도구 형식). 바이트가 바뀌지 않게 한 번만 만든다 (서버 앞부분 캐시).
pub fn definitions() -> &'static Value {
    static DEFS: OnceLock<Value> = OnceLock::new();
    DEFS.get_or_init(|| {
        let f = |name: &str, desc: &str, props: Value, req: &[&str]| {
            json!({"type":"function","function":{"name":name,"description":desc,
                "parameters":{"type":"object","properties":props,"required":req}}})
        };
        json!([
            f(FIND, "기억 검색. 기억 질문이면 먼저 쓴다.", json!({
                "q":{"type":"array","items":{"type":"string"},"description":"낱말+동의어 3개까지"},
                "from":{"type":"string","description":"YYYY-MM 또는 YYYY-MM-DD"},
                "to":{"type":"string"},
                "kind":{"type":"string","enum":["trip","outing","errand","commute"]}
            }), &["q"]),
            f(DAY, "하루의 들른 곳 보기.", json!({"d":{"type":"string","description":"YYYY-MM-DD"}}), &["d"]),
            f(COURSE, "기억의 코스 순서·거리.", json!({"id":{"type":"integer"},"here":{"type":"boolean","description":"지금 위치에서 가장 짧은 순서"}}), &["id"]),
            f(COURSES, "근처 인기 드라이브 코스 추천(남들이 공유한 코스). 추천·갈 만한 곳 질문에 쓴다.", json!({
                "q":{"type":"string","description":"테마·지역 낱말 (예: 바다, 강릉). 없으면 비움"},
                "km":{"type":"number","description":"반경, 기본 50"},
                "k":{"type":"integer","description":"개수, 기본 3"}
            }), &[]),
            f(NAV, "안내 제안(실행은 운전자 허락 뒤). course=그날 코스, place=한 곳, search=내비 검색, pick=추천 코스 n번.", json!({
                "mode":{"type":"string","enum":["course","place","search","pick"]},
                "id":{"type":"integer"},
                "stop":{"type":"string"},
                "kw":{"type":"string"},
                "n":{"type":"integer"}
            }), &["mode"]),
        ])
    })
}

fn example(name: &str) -> &'static str {
    match name {
        FIND => r#"{"q":["바다","해변"],"from":"2025-06","to":"2025-08"}"#,
        DAY => r#"{"d":"2025-08-14"}"#,
        COURSE => r#"{"id":12}"#,
        COURSES => r#"{"q":"바다"}"#,
        _ => r#"{"mode":"course","id":12}"#,
    }
}

/// 세션 동안 본 에피소드·짠 코스 (다음 질문 "거기로 안내해줘"에서 다시 쓴다).
#[derive(Default)]
pub struct ToolCache {
    pub episodes: HashMap<i64, MemoryHit>,
    /// id → (코스, 지금 위치 기준 최적 순서였는지)
    pub courses: HashMap<i64, (CourseRoute, bool)>,
}

impl ToolCache {
    pub fn remember(&mut self, hits: &[MemoryHit]) {
        for h in hits {
            self.episodes.insert(h.episode.facts.id, h.clone());
        }
    }
}

/// 도구 하나 실행 결과.
pub struct Outcome {
    pub call: ToolCall,
    pub ok: bool,
    pub content: String,
    /// 이 도구가 가리킨 기억 (추천 카드)
    pub focus: Option<(MemoryHit, Option<CourseRoute>)>,
    /// 검색 결과 전체 (후보)
    pub hits: Vec<MemoryHit>,
    pub proposal: Option<NavProposal>,
    /// 추천 코스 목록·고른 코스가 바뀌었다 (화면에 다시 보낸다)
    pub courses_changed: bool,
}

/// 잘못된 인자 → 모델에게 무엇이 틀렸는지 돌려준다.
pub struct Bad(pub String);

pub struct Ctx<'a> {
    pub backend: &'a dyn MemoryBackend,
    pub ctx: &'a AgentContext,
    pub cache: &'a mut ToolCache,
    pub courses: &'a mut CourseState,
    pub proposal_seq: &'a mut u64,
}

pub fn run(call: &ToolCall, c: &mut Ctx) -> Outcome {
    let raw = call.arguments.trim();
    let args: Value = if raw.is_empty() {
        json!({})
    } else {
        match serde_json::from_str::<Value>(raw) {
            Ok(v) if v.is_object() => v,
            _ => return fail(call, format!("arguments가 올바른 JSON 객체가 아닙니다. 예: {}", example(&call.name))),
        }
    };
    let r = match call.name.as_str() {
        FIND => find(call, &args, c),
        DAY => day(call, &args, c),
        COURSE => course(call, &args, c),
        NAV => nav(call, &args, c),
        COURSES => recommend(call, &args, c),
        other => return fail(call, format!("'{other}' 도구는 없습니다. 쓸 수 있는 도구: {FIND}, {DAY}, {COURSE}, {COURSES}, {NAV}")),
    };
    r.unwrap_or_else(|Bad(m)| fail(call, format!("{m} 예: {}", example(&call.name))))
}

fn find(call: &ToolCall, a: &Value, c: &mut Ctx) -> Result<Outcome, Bad> {
    let terms: Vec<String> = strings(a.get("q").or_else(|| a.get("terms")))?.into_iter().take(MAX_TERMS).collect();
    if terms.is_empty() {
        return Err(Bad("q(찾을 낱말 목록)가 필요합니다.".into()));
    }
    let tz = c.ctx.tz_offset_minutes;
    let today = local_days(c.ctx.now_ms, tz);
    let from = opt_day(a, "from", false)?;
    let to = opt_day(a, "to", true)?.or(from.map(|_| today));
    let (from, to) = match (from, to) {
        (Some(f), Some(t)) if t < f => (Some(t), Some(f)),
        x => x,
    };
    let kinds = match a.get("kind").and_then(Value::as_str) {
        None | Some("") => vec![],
        Some("trip") => vec![EpisodeKind::Trip],
        Some("outing") => vec![EpisodeKind::Outing],
        Some("errand") => vec![EpisodeKind::Errand],
        Some("commute") => vec![EpisodeKind::CommuteToWork, EpisodeKind::CommuteHome],
        Some(k) => return Err(Bad(format!("kind '{k}'는 없습니다 (trip, outing, errand, commute)."))),
    };
    let q = MemoryQuery {
        terms,
        from_ms: from.map(|d| dates::day_start_ms(d, tz)),
        to_ms: to.map(|d| dates::day_start_ms(d + 1, tz) - 1),
        kinds,
        limit: MAX_HITS as u32,
    };
    let hits: Vec<MemoryHit> = c.backend.search_memories(q).map_err(|e| Bad(format!("검색 오류: {e}.")))?.into_iter().take(MAX_HITS).collect();
    c.cache.remember(&hits);
    let mut data = json!({"count": hits.len(), "hits": hits.iter().map(|h| hit_json(h, tz)).collect::<Vec<_>>()});
    if hits.is_empty() {
        data["hint"] = json!("결과 없음. 다른 동의어로 한 번 더 찾거나, 못 찾았다고 답하세요.");
    }
    let focus = hits.first().map(|h| (h.clone(), c.cache.courses.get(&h.episode.facts.id).map(|x| x.0.clone())));
    Ok(Outcome { call: call.clone(), ok: true, content: ok_json(data), focus, hits, proposal: None, courses_changed: false })
}

fn day(call: &ToolCall, a: &Value, c: &mut Ctx) -> Result<Outcome, Bad> {
    let d = opt_day(a, "d", false)?.or(opt_day(a, "day", false)?).ok_or_else(|| Bad("d(YYYY-MM-DD)가 필요합니다.".into()))?;
    let list = c.backend.episodes_for_day(fmt_day(d)).map_err(|e| Bad(format!("기록 오류: {e}.")))?;
    for ep in &list {
        let e = c.cache.episodes.entry(ep.facts.id).or_insert_with(|| MemoryHit { episode: ep.clone(), day_title: None, score: 0.0, best_stop: None });
        e.episode = ep.clone();
    }
    let tz = c.ctx.tz_offset_minutes;
    let mut data = json!({
        "day": fmt_day(d), "weekday": WEEKDAYS[dates::weekday(d)],
        "episodes": list.iter().take(MAX_DAY_EPISODES).map(|e| episode_detail(e, tz)).collect::<Vec<_>>(),
    });
    if list.is_empty() {
        data["hint"] = json!("그날 기록이 없습니다.");
    }
    Ok(Outcome { call: call.clone(), ok: true, content: ok_json(data), focus: None, hits: vec![], proposal: None, courses_changed: false })
}

fn course(call: &ToolCall, a: &Value, c: &mut Ctx) -> Result<Outcome, Bad> {
    let id = episode_id(a)?;
    let want = a.get("here").and_then(as_bool).unwrap_or(true);
    let (route, optimized) = plan(id, want, c)?;
    let data = json!({
        "id": id,
        "order": if optimized { "지금 위치에서 가장 짧은 순서" } else { "그날 들른 순서" },
        "stops": route.stops.iter().map(|s| s.name.clone()).collect::<Vec<_>>(),
        "km": km(route.distance_m),
        "navi_legs": route.legs.len(),
    });
    let focus = c.cache.episodes.get(&id).map(|h| (h.clone(), Some(route.clone())));
    Ok(Outcome { call: call.clone(), ok: true, content: ok_json(data), focus, hits: vec![], proposal: None, courses_changed: false })
}

/// 코스를 짠다 (Rust course_route). 지금 위치를 알고 [want]면 최적 순서.
pub fn plan(id: i64, want: bool, c: &mut Ctx) -> Result<(CourseRoute, bool), Bad> {
    let loc = c.ctx.current_location.filter(|_| want);
    let r = c.backend.course_route(id, loc, loc.is_some()).map_err(|e| Bad(format!("에피소드 {id} 코스를 만들 수 없습니다 ({e}).")))?;
    if r.stops.is_empty() {
        return Err(Bad(format!("에피소드 {id}에 들른 곳이 없습니다.")));
    }
    c.cache.courses.insert(id, (r.clone(), loc.is_some()));
    Ok((r, loc.is_some()))
}

fn recommend(call: &ToolCall, a: &Value, c: &mut Ctx) -> Result<Outcome, Bad> {
    let q: Vec<String> = strings(a.get("q"))?.into_iter().filter(|w| !["근처", "드라이브", "코스", "인기", "추천"].contains(&w.as_str())).take(3).collect();
    let radius = a.get("km").and_then(|v| v.as_f64().or_else(|| v.as_str().and_then(|s| s.trim().parse().ok()))).filter(|x| *x > 0.0).unwrap_or(courses::DEFAULT_RADIUS_KM).min(500.0);
    let k = a.get("k").and_then(|v| v.as_u64().or_else(|| v.as_str().and_then(|s| s.trim().parse().ok()))).unwrap_or(courses::SHOW_K as u64).clamp(1, 5) as usize;
    let loc = c.ctx.current_location;
    let f = courses::fetch(c.backend, loc, &q, radius, k, &[]);
    let scope = if f.no_location { "전국 (지금 위치 모름)".to_string() } else if f.widened { format!("전국 (근처 {}km 안에 없음)", radius.round()) } else { format!("근처 {}km", radius.round()) };
    let mut data = json!({"scope": scope, "count": f.list.len()});
    if f.theme_missed {
        data["hint"] = json!(format!("'{}'에 맞는 코스가 없어 낱말 없이 골랐다. 그렇다고 말하라.", q.join(",")));
    }
    let changed = !f.list.is_empty();
    if changed {
        *c.courses = CourseState { list: f.list, focus: 0, q, radius_km: radius };
        data["courses"] = courses::list_json(c.courses);
        data["next"] = json!("1~3번을 번호·이름·하트 수로 짧게 말하고 1번으로 안내할지 물어라. 고르면 nav mode=pick n.");
    } else {
        data["hint"] = json!("공유된 코스가 없다. 없다고 짧게 답하라.");
    }
    Ok(Outcome { call: call.clone(), ok: true, content: ok_json(data), focus: None, hits: vec![], proposal: None, courses_changed: changed })
}

fn nav(call: &ToolCall, a: &Value, c: &mut Ctx) -> Result<Outcome, Bad> {
    let mode = a.get("mode").and_then(Value::as_str).map(str::trim).unwrap_or("").to_lowercase();
    let mut courses_changed = false;
    let (proposal, focus) = match mode.as_str() {
        "pick" => {
            if c.courses.list.is_empty() {
                return Err(Bad("추천 코스 목록이 없습니다. 먼저 courses를 부르세요.".into()));
            }
            let n = a.get("n").or_else(|| a.get("id")).and_then(|v| v.as_i64().or_else(|| v.as_str().and_then(|s| s.trim().parse().ok())))
                .ok_or_else(|| Bad("mode=pick에는 n(추천 코스 번호, 1부터)이 필요합니다.".into()))?;
            let len = c.courses.list.len();
            if n < 1 || n as usize > len {
                return Err(Bad(format!("n은 1~{len} 사이입니다.")));
            }
            c.courses.focus = n as usize - 1;
            courses_changed = true;
            let p = courses::shared_proposal(c.proposal_seq, &c.courses.list[c.courses.focus], c.ctx.current_location)
                .ok_or_else(|| Bad("그 코스에는 들른 곳 정보가 없어 안내할 수 없습니다.".into()))?;
            (p, None)
        }
        "course" => {
            let id = episode_id(a)?;
            let (route, _) = match c.cache.courses.get(&id).cloned() {
                Some(x) => x,
                None => plan(id, true, c)?,
            };
            let hit = c.cache.episodes.get(&id).cloned();
            let title = hit.as_ref().map(|h| h.episode.title.clone().unwrap_or(h.episode.facts.rule_label.clone())).unwrap_or("그날".into());
            (course_proposal(c.proposal_seq, id, &title, &route), hit.map(|h| (h, Some(route))))
        }
        "place" => {
            let id = episode_id(a)?;
            let name = a.get("stop").and_then(Value::as_str).map(str::trim).filter(|s| !s.is_empty()).ok_or_else(|| Bad("mode=place에는 stop(들른 곳 이름)이 필요합니다.".into()))?;
            let hit = c.cache.episodes.get(&id).cloned().ok_or_else(|| Bad(format!("기억 {id}를 아직 모릅니다. 먼저 find나 day로 찾으세요.")))?;
            let stops = &hit.episode.facts.stops;
            let stop = stops
                .iter()
                .find(|s| s.name == name)
                .or_else(|| stops.iter().find(|s| s.name.contains(name) || name.contains(s.name.as_str())))
                .cloned()
                .ok_or_else(|| Bad(format!("'{name}'은(는) 그 기억에 없습니다. 들른 곳: {}.", stops.iter().map(|s| s.name.as_str()).collect::<Vec<_>>().join(", "))))?;
            (place_proposal(c.proposal_seq, stop), Some((hit, None)))
        }
        "search" => {
            let kw: String = a.get("kw").or_else(|| a.get("keyword")).and_then(Value::as_str).map(str::trim).filter(|s| !s.is_empty())
                .ok_or_else(|| Bad("mode=search에는 kw(검색어)가 필요합니다.".into()))?.chars().take(40).collect();
            (search_proposal(c.proposal_seq, kw), None)
        }
        _ => return Err(Bad("mode는 course, place, search, pick 중 하나입니다.".into())),
    };
    let data = json!({
        "proposal_id": proposal.id,
        "status": "운전자 확인 대기 (아직 안내하지 않음)",
        "will_do": proposal.summary,
        "next": "운전자에게 이렇게 할지 한 문장으로 물어보세요.",
    });
    Ok(Outcome { call: call.clone(), ok: true, content: ok_json(data), focus, hits: vec![], proposal: Some(proposal), courses_changed })
}

// ---- 제안 만들기 (빠른 길과 nav가 같이 쓴다) ----

pub(crate) fn next_id(seq: &mut u64) -> String {
    *seq += 1;
    format!("p{seq}")
}

pub fn course_proposal(seq: &mut u64, episode_id: i64, title: &str, route: &CourseRoute) -> NavProposal {
    let legs = if route.legs.is_empty() && (1..=3).contains(&route.stops.len()) {
        // Rust가 구간을 주지 않은 짧은 코스는 한 구간으로
        let s = &route.stops;
        vec![crate::memory::CourseLeg { waypoints: s[..s.len() - 1].to_vec(), destination: s[s.len() - 1].clone() }]
    } else {
        route.legs.clone()
    };
    let names = route.stops.iter().map(|s| s.name.as_str()).collect::<Vec<_>>().join(" → ");
    let split = if legs.len() > 1 { format!(" ({}구간으로 나눠 안내)", legs.len()) } else { String::new() };
    NavProposal {
        id: next_id(seq),
        summary: format!("{title} 코스 안내: {names}{split}"),
        action: NavAction::Course { episode_id, title: title.to_string(), legs },
    }
}

pub fn place_proposal(seq: &mut u64, stop: StopFact) -> NavProposal {
    NavProposal { id: next_id(seq), summary: format!("{}(으)로 안내", stop.name), action: NavAction::Place { stop } }
}

pub fn search_proposal(seq: &mut u64, keyword: String) -> NavProposal {
    NavProposal { id: next_id(seq), summary: format!("내비에서 '{keyword}' 검색"), action: NavAction::Search { keyword } }
}

// ---- 결과 JSON (작게) ----

pub fn kind_name(k: EpisodeKind) -> &'static str {
    match k {
        EpisodeKind::Trip => "trip",
        EpisodeKind::Outing => "outing",
        EpisodeKind::Errand => "errand",
        EpisodeKind::CommuteToWork | EpisodeKind::CommuteHome => "commute",
    }
}

pub fn hit_json(h: &MemoryHit, _tz: i32) -> Value {
    let f = &h.episode.facts;
    let wd = parse_ymd(&f.day, false).map(|d| WEEKDAYS[dates::weekday(d)]).unwrap_or("");
    json!({
        "id": f.id, "day": f.day, "wd": wd, "kind": kind_name(f.kind),
        "title": h.episode.title.clone().unwrap_or(f.rule_label.clone()),
        "day_title": h.day_title.clone().unwrap_or_default(),
        "region": f.region,
        "stops": f.stops.iter().take(MAX_STOP_NAMES).map(|s| s.name.clone()).collect::<Vec<_>>(),
        "best": h.best_stop.as_ref().map(|s| s.name.clone()).unwrap_or_default(),
    })
}

fn episode_detail(e: &Episode, tz: i32) -> Value {
    let f = &e.facts;
    json!({
        "id": f.id, "kind": kind_name(f.kind), "title": e.title.clone().unwrap_or(f.rule_label.clone()),
        "start": hhmm(f.start_ms, tz), "end": hhmm(f.end_ms, tz), "from": f.from_name, "to": f.to_name,
        "region": f.region, "km": km(f.distance_m),
        "stops": f.stops.iter().take(MAX_DAY_STOPS).map(|s| json!({
            "name": s.name, "cat": s.category, "arrive": hhmm(s.arrive_ms, tz),
            "min": ((s.leave_ms - s.arrive_ms) / 60_000).max(0), "first": s.first_visit,
        })).collect::<Vec<_>>(),
    })
}

pub fn km(m: f64) -> f64 {
    (m / 100.0).round() / 10.0
}

fn ok_json(data: Value) -> String {
    json!({"ok": true, "data": data}).to_string()
}

fn fail(call: &ToolCall, error: String) -> Outcome {
    Outcome { call: call.clone(), ok: false, content: json!({"ok": false, "error": error}).to_string(), focus: None, hits: vec![], proposal: None, courses_changed: false }
}

// ---- 인자 읽기 (작은 모델이 문자열·숫자를 섞어 보내도 받아 준다) ----

fn strings(v: Option<&Value>) -> Result<Vec<String>, Bad> {
    match v {
        None | Some(Value::Null) => Ok(vec![]),
        Some(Value::Array(a)) => Ok(a.iter().filter_map(|x| match x {
            Value::String(s) => Some(s.trim().to_string()),
            Value::Null => None,
            other => Some(other.to_string()),
        }).filter(|s| !s.is_empty()).collect()),
        Some(Value::String(s)) => Ok(s.split([',', ' ', '·']).map(str::trim).filter(|s| !s.is_empty()).map(String::from).collect()),
        Some(_) => Err(Bad("q는 문자열 배열이어야 합니다.".into())),
    }
}

fn opt_day(a: &Value, key: &str, end: bool) -> Result<Option<i64>, Bad> {
    match a.get(key).and_then(Value::as_str).map(str::trim).filter(|s| !s.is_empty()) {
        None => Ok(None),
        Some(s) => parse_ymd(s, end).map(Some).ok_or_else(|| Bad(format!("{key}는 YYYY-MM 또는 YYYY-MM-DD 형식입니다 (받은 값: {s})."))),
    }
}

fn episode_id(a: &Value) -> Result<i64, Bad> {
    let v = a.get("id").or_else(|| a.get("episode_id")).ok_or_else(|| Bad("id가 필요합니다 (find 결과의 id).".into()))?;
    v.as_i64().or_else(|| v.as_str().and_then(|s| s.trim().parse().ok())).ok_or_else(|| Bad("id는 숫자입니다.".into()))
}

fn as_bool(v: &Value) -> Option<bool> {
    v.as_bool().or_else(|| v.as_str().map(|s| s.eq_ignore_ascii_case("true")))
}

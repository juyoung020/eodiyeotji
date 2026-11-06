//! 기억 검색: SQLite FTS5 + 한국어 2글자 조각 + 동의어 + 순위.
//!
//! # 문서 단위
//! 문서 하나 = 에피소드 하나 (`memory_fts` rowid = episode.id). 칸 4개:
//! - `day_bi`: 그날 제목(LLM, 없으면 규칙 제목)·이야기·태그 — 하루 맥락
//! - `ep_bi`: 에피소드 LLM 제목·태그, 규칙 라벨, 지역, 출발·도착 이름
//! - `stop_bi`: 들른 곳 이름·분류·주소
//! - `uni`: 위 글의 한글 한 글자씩 (중복 없이). 한 글자 뜻말("산", "강", "섬")에만 쓴다.
//!
//! # 한국어 맞추기
//! FTS5 기본 토크나이저는 띄어쓰기 단위라 "초당할머니순두부"에서 "순두부"를 못 찾고, trigram 토크나이저는
//! 3글자 미만 질의("바다", "카페")를 못 찾는다. 그래서 저장·질의 모두 Rust에서 미리 조각낸다 ([`tokens`]):
//! 한글(CJK) 연속 구간은 2글자씩 겹쳐 자르고(“순두부” → "순두 두부"), 한 글자 구간은 그대로, 영문·숫자는 단어(소문자).
//! 질의 단어는 조각들을 붙은 구(phrase)로 찾으므로 결과는 "글자 그대로 포함"과 같다 ("순두부" ⊂ "초당할머니순두부").
//! FTS5 토크나이저는 `ascii`(ASCII 밖 글자는 모두 토큰 글자)라 미리 자른 조각이 그대로 토큰이 된다.
//!
//! # 질의
//! - 낱말(terms)은 OR. 한 낱말 안 띄어쓰기는 AND ("경포 해변" = 경포 AND 해변).
//! - 동의어: Kotlin(LLM)이 넣어 주는 것에 더해 작은 기본 묶음([`SYNONYMS`])으로 넓힌다 ("바다" → 해변·해수욕장…).
//! - 한 글자 낱말: 뜻이 분명한 몇 개([`SINGLE_OK`]: 산·강·섬·숲 …)만 `uni` 칸에서 낮은 무게로 찾고,
//!   나머지("바" 같은 잘린 낱말)는 버린다. 그래서 LLM이 잘못 넣은 한 글자 낱말이 결과를 덮지 않는다.
//!   쓸 낱말이 하나도 안 남으면 결과 없음 (낱말이 처음부터 비었으면 기간·종류만으로 최신순).
//!
//! # 순위 (높을수록 앞)
//! 후보 = FTS bm25 상위 300개(기간·종류로 거른 것). 점수 =
//! - FTS: bm25(칸 무게 하루 2 · 에피소드 2 · 들른 곳 1.5 · 한 글자 0.2)를 0~4로 눌러 담음
//! - 낱말 덮기: 질문의 뜻 묶음(동의어 묶음은 하나로 셈) 중 이 에피소드 글에 든 비율 × 1.5
//!   ("속초 시장"이면 시장만 맞는 부산보다 속초·시장이 다 맞는 속초가 앞)
//! - 태그 일치: 낱말이 하루·에피소드 태그와 똑같으면 하나에 +1 (최대 3)
//! - 들른 곳: 낱말이 들른 곳 이름(3)·분류(2)·주소(1)에 들어 있는 정도 × 0.25 (최대 1.5),
//!   그 곳에 오래 머물수록 +0.5까지(4시간), 처음 간 곳이면 +0.3 → 가장 맞는 곳이 `best_stop`
//! - 종류: 여행 +0.4, 나들이 +0.3, 출퇴근 -0.3
//! - 최신성(약하게): +0.5 × exp(-(가장 최근 에피소드와의 거리)/1년)
//!
//! 같은 날의 에피소드는 가장 높은 하나만 남긴다 (한 날 = 기억 하나). 낱말이 없으면 점수 = 최신성뿐 (최신순).
//!
//! # 벡터 재순위 자리
//! `episode.embedding`·`day_memory.embedding` 칸이 비어 있다. 나중에 임베딩을 넣으면 위 후보(최대 300개)에
//! 코사인 유사도를 한 항으로 더하면 된다 (후보만 비교하므로 1ms 미만). 지금은 LLM이 저장할 때 태그를,
//! 찾을 때 동의어를 붙여 의미 연결을 대신한다.

use std::collections::{BTreeSet, HashMap};

use rusqlite::Connection;

use super::episode::{episode_from_row, kind_code, EP_COLS};
use super::{EpisodeKind, MemoryHit, MemoryQuery, StopFact};
use crate::store::{split_names, CoreError};

const CANDIDATES: u32 = 300;
const YEAR_MS: f64 = 365.0 * 86_400_000.0;

/// 기본 동의어 묶음. 낱말이 묶음의 한 말과 같으면 묶음 전체로 넓힌다.
pub(crate) const SYNONYMS: &[&[&str]] = &[
    &["바다", "해변", "해수욕장", "바닷가", "해안", "비치"],
    &["산", "등산", "산행", "국립공원"],
    &["카페", "커피", "카페거리"],
    &["맛집", "음식점", "식당"],
    &["시장", "전통시장"],
    &["마트", "대형마트", "장보기"],
    &["병원", "종합병원", "의원"],
    &["쇼핑", "쇼핑몰", "백화점", "아울렛"],
    &["공원", "한강공원"],
    &["휴게소", "고속도로"],
];

/// 혼자서도 뜻이 분명한 한 글자 낱말. 나머지 한 글자 낱말은 버린다.
pub(crate) const SINGLE_OK: &[&str] = &["산", "강", "섬", "숲", "절", "궁", "탑", "굴", "못"];

fn is_cjk(c: char) -> bool {
    matches!(c as u32, 0xAC00..=0xD7A3 | 0x1100..=0x11FF | 0x3130..=0x318F | 0x3040..=0x30FF | 0x4E00..=0x9FFF)
}

/// 글을 색인 조각으로 자른다 (모듈 설명): 한글 구간은 2글자씩 겹쳐서, 한 글자 구간은 그대로, 영문·숫자는 소문자 단어.
pub(crate) fn tokens(s: &str) -> Vec<String> {
    fn flush_run(run: &mut Vec<char>, out: &mut Vec<String>) {
        match run.len() {
            0 => {}
            1 => out.push(run[0].to_string()),
            _ => out.extend(run.windows(2).map(|w| w.iter().collect::<String>())),
        }
        run.clear();
    }
    fn flush_word(word: &mut String, out: &mut Vec<String>) {
        if !word.is_empty() {
            out.push(std::mem::take(word));
        }
    }
    let mut out = Vec::new();
    let mut run: Vec<char> = Vec::new();
    let mut word = String::new();
    for c in s.chars() {
        if is_cjk(c) {
            flush_word(&mut word, &mut out);
            run.push(c);
        } else if c.is_alphanumeric() {
            flush_run(&mut run, &mut out);
            word.extend(c.to_lowercase());
        } else {
            flush_run(&mut run, &mut out);
            flush_word(&mut word, &mut out);
        }
    }
    flush_run(&mut run, &mut out);
    flush_word(&mut word, &mut out);
    out
}

/// 색인 칸에 넣을 글 (조각을 띄어쓰기로 이은 것).
pub(crate) fn index_text(s: &str) -> String {
    tokens(s).join(" ")
}

/// `uni` 칸: 글들의 한글 글자 (중복 없이).
pub(crate) fn unigram_text(parts: &[&str]) -> String {
    let set: BTreeSet<char> = parts.iter().flat_map(|p| p.chars()).filter(|&c| is_cjk(c)).collect();
    set.into_iter().map(String::from).collect::<Vec<_>>().join(" ")
}

/// 낱말 정리 + 동의어 넓히기. 한 글자 낱말은 [`SINGLE_OK`]만 남긴다. 순서 유지, 중복 없음.
pub(crate) fn expand_terms(terms: &[String]) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    let push = |t: &str, out: &mut Vec<String>| {
        let t = t.split_whitespace().collect::<Vec<_>>().join(" ");
        let single = t.chars().count() == 1 && t.chars().all(is_cjk);
        if t.is_empty() || (single && !SINGLE_OK.contains(&t.as_str())) || out.contains(&t) {
            return;
        }
        out.push(t);
    };
    for t in terms {
        push(t.trim(), &mut out);
        let key = t.trim();
        for group in SYNONYMS.iter().filter(|g| g.contains(&key)) {
            for s in group.iter() {
                push(s, &mut out);
            }
        }
    }
    out
}

/// FTS5 MATCH 식. 쓸 조각이 없으면 None.
pub(crate) fn match_expr(terms: &[String]) -> Option<String> {
    let parts: Vec<String> = terms
        .iter()
        .filter_map(|t| {
            let phrases: Vec<String> = t
                .split_whitespace()
                .filter_map(|w| {
                    let tk = tokens(w);
                    if tk.is_empty() {
                        return None;
                    }
                    // 한 글자 뜻말은 uni 칸에서만 (2글자 조각 칸의 한 글자 토큰은 "성수동2가"의 "가" 같은 잔여물)
                    let single = tk.len() == 1 && tk[0].chars().count() == 1 && tk[0].chars().all(is_cjk);
                    Some(if single { format!("uni : \"{}\"", tk[0]) } else { format!("\"{}\"", tk.join(" ")) })
                })
                .collect();
            match phrases.len() {
                0 => None,
                1 => Some(phrases.into_iter().next().unwrap_or_default()),
                _ => Some(format!("({})", phrases.join(" AND "))),
            }
        })
        .collect();
    (!parts.is_empty()).then(|| parts.join(" OR "))
}

/// 낱말을 뜻 묶음으로 모은다: 같은 동의어 묶음에 든 낱말은 한 묶음, 나머지는 낱말 하나가 한 묶음.
pub(crate) fn concepts(terms: &[String]) -> Vec<Vec<String>> {
    let mut out: Vec<(Option<usize>, Vec<String>)> = Vec::new();
    for t in terms {
        let g = SYNONYMS.iter().position(|g| g.contains(&t.as_str()));
        match out.iter_mut().find(|(k, _)| g.is_some() && *k == g) {
            Some((_, v)) => v.push(t.clone()),
            None => out.push((g, vec![t.clone()])),
        }
    }
    out.into_iter().map(|(_, v)| v).collect()
}

/// 띄어쓰기를 빼고 소문자로 (글자 그대로 포함 비교용).
pub(crate) fn squash(s: &str) -> String {
    s.chars().filter(|c| !c.is_whitespace()).flat_map(char::to_lowercase).collect()
}

/// 낱말과 가장 맞는 들른 곳과 그 맞춤 점수 (이름 3·분류 2·주소 1, 낱말마다 더함).
/// 아무 곳도 안 맞으면 가장 오래 머문 곳 (점수 0). 들른 곳이 없으면 None.
pub(crate) fn best_stop(stops: &[StopFact], terms: &[String]) -> Option<(usize, u32)> {
    let terms: Vec<String> = terms.iter().map(|t| squash(t)).filter(|t| !t.is_empty()).collect();
    stops
        .iter()
        .enumerate()
        .map(|(i, s)| {
            let (n, c, a) = (squash(&s.name), squash(&s.category), squash(&s.address));
            let m: u32 = terms
                .iter()
                .map(|t| {
                    if n.contains(t.as_str()) {
                        3
                    } else if c.contains(t.as_str()) {
                        2
                    } else if a.contains(t.as_str()) {
                        1
                    } else {
                        0
                    }
                })
                .sum();
            (i, m, s.leave_ms - s.arrive_ms)
        })
        .max_by(|x, y| (x.1, x.2, std::cmp::Reverse(x.0)).cmp(&(y.1, y.2, std::cmp::Reverse(y.0))))
        .map(|(i, m, _)| (i, m))
}

/// 기억 검색 (상위 `search_memories`).
pub(crate) fn search(conn: &Connection, q: &MemoryQuery) -> Result<Vec<MemoryHit>, CoreError> {
    let limit = if q.limit == 0 { 10 } else { q.limit.min(200) } as usize;
    let terms = expand_terms(&q.terms);
    let asked = q.terms.iter().any(|t| !t.trim().is_empty());
    let expr = match_expr(&terms);
    if asked && expr.is_none() {
        return Ok(Vec::new());
    }
    let (from, to) = (q.from_ms.unwrap_or(i64::MIN), q.to_ms.unwrap_or(i64::MAX));
    let kinds = if q.kinds.is_empty() {
        String::new()
    } else {
        let codes: Vec<String> = q.kinds.iter().map(|&k| kind_code(k).to_string()).collect();
        format!(" AND e.kind IN ({})", codes.join(","))
    };
    let newest: i64 = conn.query_row("SELECT coalesce(max(end_ms), 0) FROM episode", [], |r| r.get(0))?;

    // (에피소드, fts 점수, 하루 제목, 하루 태그)
    let rows: Vec<_> = match &expr {
        Some(m) => {
            let sql = format!(
                "SELECT {EP_COLS}, -bm25(memory_fts, 2.0, 2.0, 1.5, 0.2), coalesce(d.title, d.rule_title),
                        CASE WHEN d.title IS NULL THEN '' ELSE d.tags END
                 FROM memory_fts JOIN episode e ON e.id = memory_fts.rowid LEFT JOIN day_memory d ON d.day = e.day
                 WHERE memory_fts MATCH ?1 AND e.end_ms >= ?2 AND e.start_ms <= ?3{kinds}
                 ORDER BY bm25(memory_fts, 2.0, 2.0, 1.5, 0.2) LIMIT {CANDIDATES}"
            );
            let mut st = conn.prepare_cached(&sql)?;
            let rows = st.query_map(rusqlite::params![m, from, to], |r| {
                Ok((episode_from_row(r, 0)?, r.get::<_, f64>(13)?, r.get::<_, Option<String>>(14)?, r.get::<_, String>(15)?))
            })?;
            rows.collect::<Result<_, _>>()?
        }
        None => {
            let sql = format!(
                "SELECT {EP_COLS}, 0.0, coalesce(d.title, d.rule_title), CASE WHEN d.title IS NULL THEN '' ELSE d.tags END
                 FROM episode e LEFT JOIN day_memory d ON d.day = e.day
                 WHERE e.end_ms >= ?1 AND e.start_ms <= ?2{kinds}
                 ORDER BY e.start_ms DESC LIMIT {CANDIDATES}"
            );
            let mut st = conn.prepare_cached(&sql)?;
            let rows = st.query_map(rusqlite::params![from, to], |r| {
                Ok((episode_from_row(r, 0)?, r.get::<_, f64>(13)?, r.get::<_, Option<String>>(14)?, r.get::<_, String>(15)?))
            })?;
            rows.collect::<Result<_, _>>()?
        }
    };

    let groups = concepts(&terms);
    let mut hits: Vec<MemoryHit> = rows
        .into_iter()
        .map(|(ep, fts, day_title, day_tags)| {
            let day_tags = split_names(&day_tags);
            if terms.is_empty() {
                let recency = (-((newest - ep.facts.start_ms).max(0) as f64) / YEAR_MS).exp();
                let best_stop = best_stop(&ep.facts.stops, &[]).map(|(i, _)| ep.facts.stops[i].clone());
                return MemoryHit { episode: ep, day_title, score: recency, best_stop };
            }
            let coverage = {
                let f = &ep.facts;
                let mut text = format!(
                    "{} {} {} {} {} {}",
                    day_title.as_deref().unwrap_or(""),
                    day_tags.join(" "),
                    ep.title.as_deref().unwrap_or(""),
                    ep.tags.join(" "),
                    f.rule_label,
                    f.region
                );
                for s in &f.stops {
                    text.push_str(&format!(" {} {} {}", s.name, s.category, s.address));
                }
                let text = squash(&text);
                let hit = groups.iter().filter(|g| g.iter().any(|t| text.contains(&squash(t)))).count();
                if groups.is_empty() { 0.0 } else { hit as f64 / groups.len() as f64 }
            };
            let s_fts = if fts > 0.0 { fts / (fts + 4.0) * 4.0 } else { 0.0 };
            let tag_hits = terms.iter().filter(|t| ep.tags.contains(t) || day_tags.contains(t)).count().min(3);
            let best = best_stop(&ep.facts.stops, &terms);
            let stop_score = best.map_or(0.0, |(i, m)| {
                let s = &ep.facts.stops[i];
                let dwell_h = (s.leave_ms - s.arrive_ms) as f64 / 3_600_000.0;
                let matched = m > 0 || terms.is_empty();
                (m as f64 * 0.25).min(1.5)
                    + if matched { dwell_h.clamp(0.0, 4.0) / 4.0 * 0.5 } else { 0.0 }
                    + if matched && s.first_visit { 0.3 } else { 0.0 }
            });
            let kind_bonus = match ep.facts.kind {
                EpisodeKind::Trip => 0.4,
                EpisodeKind::Outing => 0.3,
                EpisodeKind::Errand => 0.0,
                EpisodeKind::CommuteToWork | EpisodeKind::CommuteHome => -0.3,
            };
            let recency = 0.5 * (-((newest - ep.facts.start_ms).max(0) as f64) / YEAR_MS).exp();
            let score = s_fts + 1.5 * coverage + tag_hits as f64 + stop_score + kind_bonus + recency;
            let best_stop = best.map(|(i, _)| ep.facts.stops[i].clone());
            MemoryHit { episode: ep, day_title, score, best_stop }
        })
        .collect();
    hits.sort_by(|a, b| b.score.total_cmp(&a.score).then(b.episode.facts.start_ms.cmp(&a.episode.facts.start_ms)));
    // 한 날 = 기억 하나: 같은 날은 가장 높은 에피소드만
    let mut seen: HashMap<String, ()> = HashMap::new();
    hits.retain(|h| seen.insert(h.episode.facts.day.clone(), ()).is_none());
    hits.truncate(limit);
    Ok(hits)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn s(v: &[&str]) -> Vec<String> {
        v.iter().map(|x| x.to_string()).collect()
    }

    #[test]
    fn korean_text_is_cut_into_overlapping_pairs() {
        assert_eq!(tokens("초당할머니순두부"), s(&["초당", "당할", "할머", "머니", "니순", "순두", "두부"]));
        assert_eq!(tokens("성수동2가 GS25, 산"), s(&["성수", "수동", "2", "가", "gs25", "산"]));
        assert_eq!(tokens(""), Vec::<String>::new());
        assert_eq!(unigram_text(&["설악산", "부산"]), "부 산 설 악");
    }

    #[test]
    fn stray_single_letters_are_dropped_and_synonyms_added() {
        assert_eq!(expand_terms(&s(&["바", "  ", "해변"])), s(&["해변", "바다", "해수욕장", "바닷가", "해안", "비치"]));
        assert_eq!(expand_terms(&s(&["산"]))[0], "산");
        assert_eq!(match_expr(&expand_terms(&s(&["바", " "]))), None);
        assert_eq!(
            match_expr(&s(&["순두부", "경포 해변", "산"])).unwrap(),
            "\"순두 두부\" OR (\"경포\" AND \"해변\") OR uni : \"산\""
        );
    }

    #[test]
    fn best_stop_prefers_name_then_dwell() {
        let stop = |name: &str, cat: &str, mins: i64| StopFact {
            name: name.into(),
            category: cat.into(),
            address: "강원 강릉시".into(),
            arrive_ms: 0,
            leave_ms: mins * 60_000,
            lat: 0.0,
            lon: 0.0,
            first_visit: false,
        };
        let stops = vec![stop("횡성휴게소", "휴게소", 20), stop("경포해변", "해수욕장", 110), stop("안목해변 카페거리", "카페거리", 80)];
        assert_eq!(best_stop(&stops, &s(&["해변"])), Some((1, 3)), "이름 둘 다 맞으면 오래 머문 곳");
        assert_eq!(best_stop(&stops, &s(&["카페"])), Some((2, 3)));
        assert_eq!(best_stop(&stops, &s(&["없는말"])), Some((1, 0)), "안 맞으면 가장 오래 머문 곳");
        assert_eq!(best_stop(&[], &s(&["해변"])), None);
    }
}

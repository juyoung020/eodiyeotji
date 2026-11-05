//! 에피소드 만들기·저장, 집·회사, 하루 라벨링 대기열 (규칙은 상위 `memory` 모듈 설명).

use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};

use rusqlite::{params, Connection, OptionalExtension, Row};

use super::search;
use super::{DayEnrichment, DayFacts, Episode, EpisodeFacts, EpisodeKind, StopFact};
use crate::geo::{haversine_m, Point};
use crate::places::{REF_END, REF_START, REF_STAY};
use crate::store::{format_day, join_names, split_names, CoreError, EodiStore, DAY_MS};

/// 집·회사에서 이 거리(m) 안이면 집·회사다.
pub(crate) const ANCHOR_RADIUS_M: f64 = 250.0;
/// 생활권: 집·회사에서 이 거리(m) 안.
pub(crate) const USUAL_AREA_M: f64 = 10_000.0;
/// 집에서 이 거리(m)를 넘는 곳에 가면 여행.
pub(crate) const FAR_M: f64 = 80_000.0;
/// 집에서 이 거리(m) 이상인 곳에서 자정을 넘겨 머물면(외박) 여행.
pub(crate) const OVERNIGHT_AWAY_M: f64 = 20_000.0;
/// 머문 날이 이 수 이상인 장소는 자주 가는 곳 (미만이면 드문 곳).
pub(crate) const FREQUENT_DAYS: i64 = 3;
/// 마지막 위치 점에서 이만큼(ms) 지나면 그날 기록이 끝난 것으로 본다 (라벨링 대기열).
pub(crate) const SETTLE_MS: i64 = 10 * 60_000;

const HOUR: i64 = 3_600_000;
const HOME_MIN_NIGHT_MS: i64 = 4 * HOUR;
const OFFICE_MIN_WORK_MS: i64 = 8 * HOUR;
const OFFICE_MIN_FROM_HOME_M: f64 = 500.0;

// memory_anchor.kind
const NAVI_HOME: i64 = 0;
const NAVI_OFFICE: i64 = 1;
const CUR_HOME: i64 = 2;
const CUR_OFFICE: i64 = 3;

/// 문자열 목록 칸(tags·stops)의 구분자: 항목 사이 = U+001F, 들른 곳 사이 = U+001E.
const REC_SEP: char = '\u{1e}';
const FIELD_SEP: char = '\u{1f}';

/// DB 구조 버전 3에서 더하는 표.
pub(crate) const SCHEMA_V3: &str = "
-- 에피소드를 다시 만들 날 (현지 날짜 번호).
CREATE TABLE memory_dirty_day (day INTEGER PRIMARY KEY);
-- 집·회사. kind 0 = 내비 집, 1 = 내비 회사 (set_home_office), 2 = 지금 쓰는 집, 3 = 지금 쓰는 회사 (내비 또는 추정).
CREATE TABLE memory_anchor (kind INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL);
-- 날짜·장소별 머문 시간(ms): 밤(22~06시), 평일 낮(09~18시), 전체. 집·회사 추정과 자주 가는 곳 판단에 쓴다.
CREATE TABLE day_place_dwell (
    day INTEGER NOT NULL,
    place_id INTEGER NOT NULL,
    night_ms INTEGER NOT NULL,
    work_ms INTEGER NOT NULL,
    total_ms INTEGER NOT NULL,
    PRIMARY KEY (day, place_id)
) WITHOUT ROWID;
CREATE INDEX day_place_dwell_place ON day_place_dwell (place_id, day);
-- 장소별 머문 날 수·처음 머문 날 (day_place_dwell 요약).
CREATE TABLE place_stat (place_id INTEGER PRIMARY KEY, days INTEGER NOT NULL, first_day INTEGER NOT NULL);
-- 에피소드. id = 첫 주행 출발 시각(ms). stops = 들른 곳(U+001E로 나눈 줄, 줄 안은 U+001F로 나눈 칸).
-- title·tags = LLM 라벨. embedding = 나중 벡터 재순위용 (지금은 비어 있음).
CREATE TABLE episode (
    id INTEGER PRIMARY KEY,
    day INTEGER NOT NULL,
    ord INTEGER NOT NULL,
    kind INTEGER NOT NULL,
    rule_label TEXT NOT NULL,
    start_ms INTEGER NOT NULL,
    end_ms INTEGER NOT NULL,
    distance_m REAL NOT NULL,
    from_name TEXT NOT NULL,
    to_name TEXT NOT NULL,
    to_lat REAL NOT NULL,
    to_lon REAL NOT NULL,
    region TEXT NOT NULL,
    stops TEXT NOT NULL,
    title TEXT,
    tags TEXT NOT NULL DEFAULT '',
    embedding BLOB
);
CREATE INDEX episode_day ON episode (day, ord);
CREATE INDEX episode_start ON episode (start_ms);
-- 하루 기억. needs_llm = 라벨링이 필요한 날, sig = 그날 나들이·여행 구성(바뀌면 다시 라벨링),
-- title·summary·tags·enriched_sig = LLM 결과와 그때의 sig, retry_at_ms = 실패 뒤 다시 시도할 시각.
CREATE TABLE day_memory (
    day INTEGER PRIMARY KEY,
    needs_llm INTEGER NOT NULL,
    sig TEXT NOT NULL,
    rule_title TEXT,
    title TEXT,
    summary TEXT NOT NULL DEFAULT '',
    tags TEXT NOT NULL DEFAULT '',
    enriched_sig TEXT,
    retry_at_ms INTEGER NOT NULL DEFAULT 0,
    embedding BLOB
);
-- 검색 색인 (rowid = episode.id). 글은 search::tokens로 미리 조각낸 것 (한글은 2글자씩, uni = 한 글자씩).
CREATE VIRTUAL TABLE memory_fts USING fts5(day_bi, ep_bi, stop_bi, uni, tokenize = 'ascii');
";

pub(crate) fn kind_code(k: EpisodeKind) -> i64 {
    match k {
        EpisodeKind::CommuteToWork => 0,
        EpisodeKind::CommuteHome => 1,
        EpisodeKind::Errand => 2,
        EpisodeKind::Outing => 3,
        EpisodeKind::Trip => 4,
    }
}

fn kind_from(code: i64) -> EpisodeKind {
    match code {
        0 => EpisodeKind::CommuteToWork,
        1 => EpisodeKind::CommuteHome,
        2 => EpisodeKind::Errand,
        3 => EpisodeKind::Outing,
        _ => EpisodeKind::Trip,
    }
}

/// 요일 ("일"~"토"). 1970-01-01(날짜 번호 0)은 목요일.
pub(crate) fn weekday_index(day: i64) -> usize {
    (day + 4).rem_euclid(7) as usize
}

const WEEKDAYS: [&str; 7] = ["일", "월", "화", "수", "목", "금", "토"];

// ---- 집 · 회사 ----

#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub(crate) struct Anchors {
    pub home: Option<Point>,
    pub office: Option<Point>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Anchor {
    Home,
    Office,
}

impl Anchors {
    fn which(&self, p: Point) -> Option<Anchor> {
        let near = |a: Option<Point>| a.is_some_and(|a| haversine_m(a, p) <= ANCHOR_RADIUS_M);
        if near(self.home) {
            Some(Anchor::Home)
        } else if near(self.office) {
            Some(Anchor::Office)
        } else {
            None
        }
    }

    /// 생활권 밖인지 (집·회사를 모르면 모든 곳이 생활권).
    fn outside(&self, p: Point) -> bool {
        let known: Vec<Point> = [self.home, self.office].into_iter().flatten().collect();
        !known.is_empty() && known.iter().all(|&a| haversine_m(a, p) > USUAL_AREA_M)
    }
}

fn read_anchor(conn: &Connection, kind: i64) -> rusqlite::Result<Option<Point>> {
    conn.prepare_cached("SELECT lat, lon FROM memory_anchor WHERE kind = ?1")?
        .query_row([kind], |r| Ok(Point { lat: r.get(0)?, lon: r.get(1)? }))
        .optional()
}

fn write_anchor(conn: &Connection, kind: i64, p: Option<Point>) -> rusqlite::Result<()> {
    match p {
        Some(p) => conn.execute(
            "INSERT OR REPLACE INTO memory_anchor (kind, lat, lon) VALUES (?1, ?2, ?3)",
            params![kind, p.lat, p.lon],
        )?,
        None => conn.execute("DELETE FROM memory_anchor WHERE kind = ?1", [kind])?,
    };
    Ok(())
}

pub(crate) fn set_navi_anchors(conn: &Connection, home: Option<Point>, office: Option<Point>) -> rusqlite::Result<()> {
    write_anchor(conn, NAVI_HOME, home)?;
    write_anchor(conn, NAVI_OFFICE, office)
}

/// 지금 쓸 집·회사: 내비 즐겨찾기가 있으면 그것, 없으면 머문 시간으로 추정 (상위 모듈 설명).
fn resolve_anchors(conn: &Connection) -> rusqlite::Result<Anchors> {
    let home = match read_anchor(conn, NAVI_HOME)? {
        Some(p) => Some(p),
        None => conn
            .query_row(
                "SELECT p.lat, p.lon FROM day_place_dwell d JOIN place p ON p.id = d.place_id
                 GROUP BY d.place_id HAVING sum(d.night_ms) >= ?1 ORDER BY sum(d.night_ms) DESC, d.place_id LIMIT 1",
                [HOME_MIN_NIGHT_MS],
                |r| Ok(Point { lat: r.get(0)?, lon: r.get(1)? }),
            )
            .optional()?,
    };
    let office = match read_anchor(conn, NAVI_OFFICE)? {
        Some(p) => Some(p),
        None => {
            let mut st = conn.prepare_cached(
                "SELECT p.lat, p.lon FROM day_place_dwell d JOIN place p ON p.id = d.place_id
                 GROUP BY d.place_id HAVING sum(d.work_ms) >= ?1 ORDER BY sum(d.work_ms) DESC, d.place_id LIMIT 8",
            )?;
            let cands: Vec<Point> =
                st.query_map([OFFICE_MIN_WORK_MS], |r| Ok(Point { lat: r.get(0)?, lon: r.get(1)? }))?.collect::<Result<_, _>>()?;
            cands.into_iter().find(|&p| home.is_none_or(|h| haversine_m(h, p) > OFFICE_MIN_FROM_HOME_M))
        }
    };
    Ok(Anchors { home, office })
}

/// 지금 쓰는 집·회사 (flush_memory가 정해 둔 값).
pub(crate) fn current_anchors(conn: &Connection) -> rusqlite::Result<Anchors> {
    Ok(Anchors { home: read_anchor(conn, CUR_HOME)?, office: read_anchor(conn, CUR_OFFICE)? })
}

// ---- 하루 기록 읽기 ----

struct TripRec {
    start_ms: i64,
    end_ms: i64,
    distance_m: f64,
    start: Point,
    end: Point,
    start_place: Option<i64>,
    end_place: Option<i64>,
}

struct StayRec {
    center: Point,
    arrive_ms: i64,
    leave_ms: i64,
    place: Option<i64>,
}

#[derive(Default)]
struct DayRec {
    trips: Vec<TripRec>,
    stays: Vec<StayRec>,
}

struct PlaceInfo {
    name: String,
    category: String,
    address: String,
}

/// 날짜들을 가까운 것끼리(7일 안) 묶은 범위들.
fn ranges(days: &BTreeSet<i64>) -> Vec<(i64, i64)> {
    let mut out: Vec<(i64, i64)> = Vec::new();
    for &d in days {
        match out.last_mut() {
            Some((_, hi)) if d <= *hi + 7 => *hi = d,
            _ => out.push((d, d)),
        }
    }
    out
}

/// 날짜들의 주행·머문 곳과 그 지점의 장소 번호.
fn load_records(store: &EodiStore, conn: &Connection, days: &BTreeSet<i64>) -> Result<BTreeMap<i64, DayRec>, CoreError> {
    let mut out = BTreeMap::new();
    for (d0, d1) in ranges(days) {
        let recs = store.day_records(conn, d0, d1)?;
        // 장소 잇기 (kind, t_ms) → place_id. 그 범위 날짜에 속한 주행은 3일 전 이후에 시작했다.
        let refs: HashMap<(i64, i64), i64> = {
            let mut st = conn.prepare_cached(
                "SELECT kind, t_ms, place_id FROM trip_place WHERE seg_start >= ?1 AND seg_start < ?2",
            )?;
            let rows = st.query_map([store.day_start_ms(d0) - 3 * DAY_MS, store.day_start_ms(d1 + 1)], |r| {
                Ok(((r.get(0)?, r.get(1)?), r.get(2)?))
            })?;
            rows.collect::<Result<_, _>>()?
        };
        for (d, (trips, stays)) in recs {
            if !days.contains(&d) {
                continue;
            }
            let rec = DayRec {
                trips: trips
                    .iter()
                    .map(|t| TripRec {
                        start_ms: t.start_ms,
                        end_ms: t.end_ms,
                        distance_m: t.distance_m,
                        start: t.start.into(),
                        end: t.end.into(),
                        start_place: refs.get(&(REF_START, t.start_ms)).copied(),
                        end_place: refs.get(&(REF_END, t.end_ms)).copied(),
                    })
                    .collect(),
                stays: stays
                    .iter()
                    .map(|s| StayRec {
                        center: s.center.into(),
                        arrive_ms: s.arrive_ms,
                        leave_ms: s.leave_ms,
                        place: refs.get(&(REF_STAY, s.arrive_ms)).or_else(|| refs.get(&(REF_END, s.arrive_ms))).copied(),
                    })
                    .collect(),
            };
            out.insert(d, rec);
        }
    }
    Ok(out)
}

fn load_places(conn: &Connection) -> rusqlite::Result<HashMap<i64, PlaceInfo>> {
    let mut st = conn.prepare("SELECT id, name, category, address FROM place")?;
    let rows = st.query_map([], |r| {
        Ok((
            r.get::<_, i64>(0)?,
            PlaceInfo {
                name: r.get::<_, Option<String>>(1)?.unwrap_or_default(),
                category: r.get(2)?,
                address: r.get(3)?,
            },
        ))
    })?;
    rows.collect()
}

fn load_stats(conn: &Connection) -> rusqlite::Result<HashMap<i64, (i64, i64)>> {
    let mut st = conn.prepare("SELECT place_id, days, first_day FROM place_stat")?;
    let rows = st.query_map([], |r| Ok((r.get(0)?, (r.get(1)?, r.get(2)?))))?;
    rows.collect()
}

/// 머문 곳 하나의 (밤, 평일 낮, 전체) 머문 시간.
fn dwell(store: &EodiStore, s: &StayRec) -> (i64, i64, i64) {
    let overlap = |a0: i64, a1: i64| (s.leave_ms.min(a1) - s.arrive_ms.max(a0)).max(0);
    let (mut night, mut work) = (0, 0);
    for day in store.local_day(s.arrive_ms) - 1..=store.local_day(s.leave_ms) {
        let ds = store.day_start_ms(day);
        night += overlap(ds + 22 * HOUR, ds + 30 * HOUR);
        if (1..=5).contains(&weekday_index(day)) {
            work += overlap(ds + 9 * HOUR, ds + 18 * HOUR);
        }
    }
    (night, work, s.leave_ms - s.arrive_ms)
}

// ---- 다시 계산 ----

/// 밀린 날(`memory_dirty_day`)의 에피소드를 다시 만든다 (상위 모듈 설명).
/// `force_anchors`면 밀린 날이 없어도 집·회사를 다시 정한다 (`set_home_office` 뒤).
pub(crate) fn flush_memory(store: &EodiStore, conn: &mut Connection, force_anchors: bool) -> Result<(), CoreError> {
    let dirty: BTreeSet<i64> = {
        let mut st = conn.prepare_cached("SELECT day FROM memory_dirty_day")?;
        let rows = st.query_map([], |r| r.get(0))?;
        rows.collect::<Result<_, _>>()?
    };
    if dirty.is_empty() && !force_anchors {
        return Ok(());
    }
    let tx = conn.transaction()?;
    let mut records = load_records(store, &tx, &dirty)?;

    // 1. 날짜·장소별 머문 시간
    let mut touched: BTreeSet<i64> = BTreeSet::new();
    for &d in &dirty {
        {
            let mut st = tx.prepare_cached("SELECT place_id FROM day_place_dwell WHERE day = ?1")?;
            let rows = st.query_map([d], |r| r.get::<_, i64>(0))?;
            for id in rows {
                touched.insert(id?);
            }
        }
        tx.execute("DELETE FROM day_place_dwell WHERE day = ?1", [d])?;
        let Some(rec) = records.get(&d) else { continue };
        let mut sums: BTreeMap<i64, (i64, i64, i64)> = BTreeMap::new();
        for s in &rec.stays {
            if let Some(id) = s.place {
                let (n, w, t) = dwell(store, s);
                let e = sums.entry(id).or_default();
                *e = (e.0 + n, e.1 + w, e.2 + t);
            }
        }
        let mut st = tx.prepare_cached(
            "INSERT INTO day_place_dwell (day, place_id, night_ms, work_ms, total_ms) VALUES (?1, ?2, ?3, ?4, ?5)",
        )?;
        for (id, (n, w, t)) in sums {
            st.execute(params![d, id, n, w, t])?;
            touched.insert(id);
        }
    }

    // 2. 장소 요약: 자주 가는 곳 여부나 처음 머문 날이 바뀌면 그 장소가 나오는 날도 다시 만든다.
    let mut days = dirty.clone();
    for &id in &touched {
        let (n, first): (i64, Option<i64>) = tx
            .prepare_cached("SELECT count(*), min(day) FROM day_place_dwell WHERE place_id = ?1")?
            .query_row([id], |r| Ok((r.get(0)?, r.get(1)?)))?;
        let old: Option<(i64, i64)> = tx
            .prepare_cached("SELECT days, first_day FROM place_stat WHERE place_id = ?1")?
            .query_row([id], |r| Ok((r.get(0)?, r.get(1)?)))
            .optional()?;
        let (old_n, old_first) = old.map_or((0, None), |(n, f)| (n, Some(f)));
        if (old_n >= FREQUENT_DAYS) != (n >= FREQUENT_DAYS) {
            let mut st = tx.prepare_cached("SELECT day FROM day_place_dwell WHERE place_id = ?1")?;
            let rows = st.query_map([id], |r| r.get::<_, i64>(0))?;
            for d in rows {
                days.insert(d?);
            }
        }
        if old_first != first {
            days.extend(old_first);
            days.extend(first);
        }
        match first {
            Some(f) => tx.execute(
                "INSERT OR REPLACE INTO place_stat (place_id, days, first_day) VALUES (?1, ?2, ?3)",
                params![id, n, f],
            )?,
            None => tx.execute("DELETE FROM place_stat WHERE place_id = ?1", [id])?,
        };
    }

    // 3. 집·회사: 바뀌면 모든 날
    let anchors = resolve_anchors(&tx)?;
    if anchors != current_anchors(&tx)? {
        write_anchor(&tx, CUR_HOME, anchors.home)?;
        write_anchor(&tx, CUR_OFFICE, anchors.office)?;
        let mut st = tx.prepare("SELECT day FROM day_summary UNION SELECT day FROM episode")?;
        let rows = st.query_map([], |r| r.get::<_, i64>(0))?;
        for d in rows {
            days.insert(d?);
        }
    }

    // 4. 에피소드
    let extra: BTreeSet<i64> = days.difference(&dirty).copied().collect();
    if !extra.is_empty() {
        records.extend(load_records(store, &tx, &extra)?);
    }
    let places = load_places(&tx)?;
    let stats = load_stats(&tx)?;
    let ctx = Ctx { store, places: &places, stats: &stats, anchors };
    for &d in &days {
        rebuild_day(&tx, &ctx, d, records.get(&d))?;
    }
    tx.execute("DELETE FROM memory_dirty_day", [])?;
    tx.commit()?;
    Ok(())
}

struct Ctx<'a> {
    store: &'a EodiStore,
    places: &'a HashMap<i64, PlaceInfo>,
    stats: &'a HashMap<i64, (i64, i64)>,
    anchors: Anchors,
}

/// 만든 에피소드 하나.
struct Built {
    facts: EpisodeFacts,
    to: Point,
    stop_places: Vec<Option<i64>>,
    /// 나들이·여행이거나 처음 들른 곳이 있음 → 라벨링이 필요
    notable: bool,
}

/// 하루를 다시 만든다: 에피소드 표·하루 기억·검색 색인. 기록이 없으면 지운다.
fn rebuild_day(tx: &Connection, ctx: &Ctx, d: i64, rec: Option<&DayRec>) -> Result<(), CoreError> {
    let built = rec.map(|r| build_episodes(ctx, d, r)).unwrap_or_default();

    // LLM 라벨 옮기기: 같은 id, 없으면 같은 날·같은 순서·같은 종류였던 에피소드.
    type Label = (Option<String>, String);
    let old: Vec<(i64, i64, i64, Label)> = {
        let mut st = tx.prepare_cached("SELECT id, ord, kind, title, tags FROM episode WHERE day = ?1")?;
        let rows = st.query_map([d], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, (r.get(3)?, r.get(4)?))))?;
        rows.collect::<Result<_, _>>()?
    };
    let mut labels: Vec<Label> = Vec::with_capacity(built.len());
    for (ord, b) in built.iter().enumerate() {
        let by_id: Option<Label> = tx
            .prepare_cached("SELECT title, tags FROM episode WHERE id = ?1")?
            .query_row([b.facts.id], |r| Ok((r.get(0)?, r.get(1)?)))
            .optional()?;
        let by_ord = || {
            old.iter().find(|o| o.1 == ord as i64 && o.2 == kind_code(b.facts.kind)).map(|o| o.3.clone())
        };
        labels.push(by_id.filter(|l| l.0.is_some()).or_else(by_ord).unwrap_or((None, String::new())));
    }
    {
        let mut del_fts = tx.prepare_cached("DELETE FROM memory_fts WHERE rowid = ?1")?;
        let mut del_ep = tx.prepare_cached("DELETE FROM episode WHERE id = ?1")?;
        for id in old.iter().map(|o| o.0).chain(built.iter().map(|b| b.facts.id)) {
            del_fts.execute([id])?;
            del_ep.execute([id])?;
        }
    }
    {
        let mut st = tx.prepare_cached(
            "INSERT INTO episode (id, day, ord, kind, rule_label, start_ms, end_ms, distance_m, from_name, to_name,
                                  to_lat, to_lon, region, stops, title, tags)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16)",
        )?;
        for (ord, (b, (title, tags))) in built.iter().zip(&labels).enumerate() {
            let f = &b.facts;
            st.execute(params![
                f.id,
                d,
                ord as i64,
                kind_code(f.kind),
                f.rule_label,
                f.start_ms,
                f.end_ms,
                f.distance_m,
                f.from_name,
                f.to_name,
                b.to.lat,
                b.to.lon,
                f.region,
                encode_stops(&f.stops),
                title,
                tags
            ])?;
        }
    }
    if built.is_empty() {
        tx.execute("DELETE FROM day_memory WHERE day = ?1", [d])?;
        return Ok(());
    }
    let notable: Vec<&Built> = built.iter().filter(|b| b.notable).collect();
    let sig = notable
        .iter()
        .map(|b| {
            let ids: Vec<String> = b.stop_places.iter().flatten().map(|i| i.to_string()).collect();
            format!("{}:{}", kind_code(b.facts.kind), ids.join(","))
        })
        .collect::<Vec<_>>()
        .join("|");
    let rule_title = day_rule_title(&built);
    tx.execute(
        "INSERT INTO day_memory (day, needs_llm, sig, rule_title) VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT (day) DO UPDATE SET needs_llm = excluded.needs_llm, sig = excluded.sig, rule_title = excluded.rule_title",
        params![d, !notable.is_empty(), sig, rule_title],
    )?;
    reindex_day(tx, d)?;
    Ok(())
}

/// 하루 규칙 제목 (상위 모듈 설명).
fn day_rule_title(built: &[Built]) -> Option<String> {
    let main = built
        .iter()
        .filter(|b| matches!(b.facts.kind, EpisodeKind::Trip | EpisodeKind::Outing))
        .max_by_key(|b| (b.facts.kind == EpisodeKind::Trip, b.facts.end_ms - b.facts.start_ms));
    if let Some(b) = main.or_else(|| built.iter().find(|b| b.notable)) {
        return Some(b.facts.rule_label.clone());
    }
    let commute = built.iter().any(|b| matches!(b.facts.kind, EpisodeKind::CommuteToWork | EpisodeKind::CommuteHome));
    let mut names: Vec<&str> = Vec::new();
    for s in built.iter().flat_map(|b| &b.facts.stops) {
        if !s.name.is_empty() && !names.contains(&s.name.as_str()) && names.len() < 2 {
            names.push(&s.name);
        }
    }
    Some(match (commute, names.is_empty()) {
        (true, true) => "평소 출퇴근".to_owned(),
        (true, false) => format!("출퇴근 · {} 들름", names.join(" · ")),
        (false, false) => format!("동네 볼일 · {}", names.join(" · ")),
        (false, true) => "동네 드라이브".to_owned(),
    })
}

/// 하루 기록을 에피소드로 묶는다 (상위 모듈 설명).
fn build_episodes(ctx: &Ctx, d: i64, rec: &DayRec) -> Vec<Built> {
    let a = ctx.anchors;
    let trips = &rec.trips;
    // 집·회사에서 출발하면 열고, 집·회사에 도착하면 닫는다.
    let mut groups: Vec<(usize, usize)> = Vec::new();
    let mut open: Option<usize> = None;
    for (i, t) in trips.iter().enumerate() {
        if let Some(s) = open {
            if a.which(t.start).is_some() {
                groups.push((s, i - 1));
                open = None;
            }
        }
        let s = *open.get_or_insert(i);
        if a.which(t.end).is_some() {
            groups.push((s, i));
            open = None;
        }
    }
    if let Some(s) = open {
        groups.push((s, trips.len() - 1));
    }

    let place = |id: Option<i64>| id.and_then(|i| ctx.places.get(&i));
    let name_of = |p: Point, id: Option<i64>| match a.which(p) {
        Some(Anchor::Home) => "집".to_owned(),
        Some(Anchor::Office) => "회사".to_owned(),
        None => place(id).map(|pl| pl.name.clone()).unwrap_or_default(),
    };
    let mut seen: HashSet<i64> = HashSet::new();
    let mut out = Vec::with_capacity(groups.len());
    for (i0, i1) in groups {
        let (first, last) = (&trips[i0], &trips[i1]);
        let (t0, t1) = (first.start_ms, last.end_ms);
        let mut stays: Vec<&StayRec> = rec
            .stays
            .iter()
            .filter(|s| s.arrive_ms >= t0 && s.arrive_ms <= t1 && a.which(s.center).is_none())
            .collect();
        stays.sort_by_key(|s| s.arrive_ms);
        let dest_extra = a.which(last.end).is_none()
            && last.end_place.is_some()
            && !rec.stays.iter().any(|s| s.arrive_ms == t1);
        let dest = StayRec { center: last.end, arrive_ms: t1, leave_ms: t1, place: last.end_place };
        if dest_extra {
            stays.push(&dest);
        }

        let mut stops = Vec::with_capacity(stays.len());
        let mut rare = Vec::with_capacity(stays.len());
        for s in &stays {
            let info = place(s.place);
            let first_visit = s.place.is_some_and(|id| {
                ctx.stats.get(&id).is_some_and(|st| st.1 == d) && seen.insert(id)
            });
            if let Some(id) = s.place {
                seen.insert(id);
            }
            rare.push(s.place.is_some_and(|id| ctx.stats.get(&id).is_none_or(|st| st.0 < FREQUENT_DAYS)));
            stops.push(StopFact {
                name: info.map(|p| p.name.clone()).unwrap_or_default(),
                category: info.map(|p| p.category.clone()).unwrap_or_default(),
                address: info.map(|p| p.address.clone()).unwrap_or_default(),
                arrive_ms: s.arrive_ms,
                leave_ms: s.leave_ms,
                lat: s.center.lat,
                lon: s.center.lon,
                first_visit,
            });
        }

        let (from_a, to_a) = (a.which(first.start), a.which(last.end));
        let mut pts: Vec<Point> = stays.iter().map(|s| s.center).collect();
        pts.extend(trips[i0..=i1].iter().flat_map(|t| [t.start, t.end]));
        let from_home = |p: Point| a.home.map_or(0.0, |h| haversine_m(h, p));
        let far = pts.iter().any(|&p| from_home(p) > FAR_M);
        let overnight = to_a.is_none()
            && a.home.is_some()
            && from_home(last.end) >= OVERNIGHT_AWAY_M
            && rec.stays.iter().any(|s| s.arrive_ms == t1 && ctx.store.local_day(s.leave_ms) > d);
        let outside: Vec<bool> = stays.iter().map(|s| a.outside(s.center)).collect();
        let kind = match (from_a, to_a) {
            (Some(Anchor::Home), Some(Anchor::Office)) => EpisodeKind::CommuteToWork,
            (Some(Anchor::Office), Some(Anchor::Home)) => EpisodeKind::CommuteHome,
            _ if far || overnight => EpisodeKind::Trip,
            _ if rare.iter().any(|&r| r) || outside.iter().any(|&o| o) || a.outside(last.end) => EpisodeKind::Outing,
            _ => EpisodeKind::Errand,
        };

        // 대표 들른 곳
        let dwell = |i: usize| stops[i].leave_ms - stops[i].arrive_ms;
        let main: Option<usize> = match kind {
            EpisodeKind::Trip => (0..stops.len()).max_by(|&x, &y| from_home(pts[x]).total_cmp(&from_home(pts[y]))),
            EpisodeKind::Outing => (0..stops.len())
                .filter(|&i| rare[i] || outside[i])
                .max_by_key(|&i| dwell(i))
                .or_else(|| (0..stops.len()).max_by_key(|&i| dwell(i))),
            _ => (0..stops.len()).max_by_key(|&i| (dwell(i), std::cmp::Reverse(i))),
        };
        let main_stop = main.map(|i| &stops[i]);
        let region = if kind == EpisodeKind::Trip {
            // 여행은 출발지(전날 묵은 곳 등)·도착지까지 넣어 집에서 가장 먼 곳의 지역 (돌아오는 날도 "부산 여행")
            let mut cands: Vec<(Point, String)> =
                stops.iter().map(|s| (Point { lat: s.lat, lon: s.lon }, region_of(&s.address))).collect();
            for (p, id) in [(first.start, first.start_place), (last.end, last.end_place)] {
                if a.which(p).is_none() {
                    cands.extend(place(id).map(|pl| (p, region_of(&pl.address))));
                }
            }
            cands
                .into_iter()
                .filter(|c| !c.1.is_empty())
                .max_by(|x, y| from_home(x.0).total_cmp(&from_home(y.0)))
                .map(|c| c.1)
                .unwrap_or_default()
        } else {
            main_stop
                .map(|s| region_of(&s.address))
                .filter(|r| !r.is_empty())
                .or_else(|| place(last.end_place).map(|p| region_of(&p.address)))
                .unwrap_or_default()
        };
        let first_named = stops.iter().find(|s| !s.name.is_empty()).map(|s| s.name.as_str());
        let rule_label = match kind {
            EpisodeKind::CommuteToWork => with_stop("출근길", first_named),
            EpisodeKind::CommuteHome => with_stop("퇴근길", first_named),
            EpisodeKind::Errand => {
                let near_home = from_a == Some(Anchor::Home) || to_a == Some(Anchor::Home);
                let what = main_stop.map(|s| if s.name.is_empty() { s.category.as_str() } else { s.name.as_str() });
                match (stops.is_empty(), what.filter(|w| !w.is_empty())) {
                    (true, _) => if near_home { "동네 드라이브" } else { "드라이브" }.to_owned(),
                    (false, Some(w)) => format!("{} · {w}", if near_home { "동네 볼일" } else { "볼일" }),
                    (false, None) => if near_home { "동네 볼일" } else { "볼일" }.to_owned(),
                }
            }
            EpisodeKind::Outing => {
                let area = main_stop.map(area_name).unwrap_or_else(|| short_city(&region));
                if area.is_empty() { "나들이".to_owned() } else { format!("{area} 나들이") }
            }
            EpisodeKind::Trip => {
                let city = short_city(&region);
                if city.is_empty() { "여행".to_owned() } else { format!("{city} 여행") }
            }
        };
        let notable = matches!(kind, EpisodeKind::Outing | EpisodeKind::Trip) || stops.iter().any(|s| s.first_visit);
        out.push(Built {
            facts: EpisodeFacts {
                id: first.start_ms,
                day: format_day(d),
                kind,
                rule_label,
                start_ms: t0,
                end_ms: t1,
                distance_m: trips[i0..=i1].iter().map(|t| t.distance_m).sum(),
                from_name: name_of(first.start, first.start_place),
                to_name: name_of(last.end, last.end_place),
                stops,
                region,
            },
            to: last.end,
            stop_places: stays.iter().map(|s| s.place).collect(),
            notable,
        });
    }
    out
}

fn with_stop(base: &str, stop: Option<&str>) -> String {
    match stop {
        Some(n) => format!("{base} · {n} 들름"),
        None => base.to_owned(),
    }
}

// ---- 주소 → 지역·지명 ----

const SIDO: &[(&str, &str)] = &[
    ("서울특별시", "서울"),
    ("부산광역시", "부산"),
    ("대구광역시", "대구"),
    ("인천광역시", "인천"),
    ("광주광역시", "광주"),
    ("대전광역시", "대전"),
    ("울산광역시", "울산"),
    ("세종특별자치시", "세종"),
    ("경기도", "경기"),
    ("강원특별자치도", "강원"),
    ("강원도", "강원"),
    ("충청북도", "충북"),
    ("충청남도", "충남"),
    ("전북특별자치도", "전북"),
    ("전라북도", "전북"),
    ("전라남도", "전남"),
    ("경상북도", "경북"),
    ("경상남도", "경남"),
    ("제주특별자치도", "제주"),
];

/// 시·도 이름만으로 지명을 쓰는 곳 (광역시·특별시·제주).
const METRO: &[&str] = &["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "제주"];

fn short_sido(t: &str) -> Option<&'static str> {
    SIDO.iter().find(|(long, short)| *long == t || *short == t).map(|(_, s)| *s)
}

/// 주소 → "시도 시군구" (예: "강원 강릉시"). 한국 주소가 아니면 빈 문자열.
pub(crate) fn region_of(address: &str) -> String {
    let mut it = address.split_whitespace();
    let Some(sido) = it.next().and_then(short_sido) else { return String::new() };
    match it.next().filter(|t| t.ends_with('시') || t.ends_with('군') || t.ends_with('구')) {
        Some(sgg) => format!("{sido} {sgg}"),
        None => sido.to_owned(),
    }
}

/// 여행 지명: 시·군 이름 ("강원 강릉시" → "강릉"), 광역시·제주는 시·도 ("부산 해운대구" → "부산").
fn short_city(region: &str) -> String {
    let mut it = region.split_whitespace();
    let Some(sido) = it.next() else { return String::new() };
    match it.next() {
        Some(sgg) if !METRO.contains(&sido) => strip_suffix(sgg, &['시', '군', '구']),
        _ => sido.to_owned(),
    }
}

/// 끝 글자(시·군·구·동 등)를 떼되 한 글자만 남으면 그대로 둔다 ("중구" → "중구").
fn strip_suffix(s: &str, suffixes: &[char]) -> String {
    match s.strip_suffix(suffixes) {
        Some(core) if core.chars().count() >= 2 => core.to_owned(),
        _ => s.to_owned(),
    }
}

/// 이름을 그대로 지명으로 쓰는 분류 (관광지·자연·시장 등).
const LANDMARK: &[&str] = &[
    "관광", "명소", "공원", "해수욕장", "해변", "섬", "유원지", "테마파크", "시장", "박물관", "미술관", "궁", "전망대",
    "타워", "한옥", "국립공원", "휴양림", "계곡", "폭포", "사찰", "카페거리", "산",
];

/// 그 자체가 나들이 목적지인 큰 가게 분류 (이름을 지명으로 쓴다).
const DESTINATION: &[&str] = &["쇼핑몰", "백화점", "아울렛", "가구", "놀이공원", "리조트", "수목원", "동물원"];

/// 나들이 지명 (상위 모듈 설명): 관광지·큰 가게면 장소 이름, 광역시 안이면 동 이름("성수동2가" → "성수"),
/// 그 밖은 시·군 이름("하남"), 주소가 없으면 장소 이름.
fn area_name(s: &StopFact) -> String {
    if !s.name.is_empty() && LANDMARK.iter().chain(DESTINATION).any(|k| s.category.contains(k)) {
        return s.name.clone();
    }
    let region = region_of(&s.address);
    let metro = region.split_whitespace().next().is_some_and(|sido| METRO.contains(&sido));
    if metro {
        // 시도 · 구 다음의 동 (도로명 주소면 없음)
        for t in s.address.split_whitespace().skip(2) {
            let head: String = t.chars().take_while(|c| !c.is_ascii_digit()).collect();
            if head.ends_with('동') {
                return strip_suffix(&head, &['동']);
            }
        }
        if let Some(gu) = region.split_whitespace().nth(1) {
            return strip_suffix(gu, &['구', '군']);
        }
    }
    match short_city(&region) {
        c if !c.is_empty() => c,
        _ => s.name.clone(),
    }
}

// ---- 저장 형식 ----

fn clean(s: &str) -> String {
    s.chars().map(|c| if c == REC_SEP || c == FIELD_SEP { ' ' } else { c }).collect()
}

fn encode_stops(stops: &[StopFact]) -> String {
    stops
        .iter()
        .map(|s| {
            format!(
                "{}{FIELD_SEP}{}{FIELD_SEP}{}{FIELD_SEP}{}{FIELD_SEP}{}{FIELD_SEP}{}{FIELD_SEP}{}{FIELD_SEP}{}",
                clean(&s.name),
                clean(&s.category),
                clean(&s.address),
                s.arrive_ms,
                s.leave_ms,
                s.lat,
                s.lon,
                u8::from(s.first_visit)
            )
        })
        .collect::<Vec<_>>()
        .join(&REC_SEP.to_string())
}

fn decode_stops(s: &str) -> Vec<StopFact> {
    if s.is_empty() {
        return Vec::new();
    }
    s.split(REC_SEP)
        .filter_map(|rec| {
            let f: Vec<&str> = rec.split(FIELD_SEP).collect();
            (f.len() == 8).then(|| StopFact {
                name: f[0].to_owned(),
                category: f[1].to_owned(),
                address: f[2].to_owned(),
                arrive_ms: f[3].parse().unwrap_or(0),
                leave_ms: f[4].parse().unwrap_or(0),
                lat: f[5].parse().unwrap_or(0.0),
                lon: f[6].parse().unwrap_or(0.0),
                first_visit: f[7] == "1",
            })
        })
        .collect()
}

/// 태그 정리: 앞뒤 공백 제거, 빈 것·중복 제거, 구분자 글자 제거.
fn clean_tags(tags: &[String]) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for t in tags {
        let t = clean(t.trim()).trim().to_owned();
        if !t.is_empty() && !out.contains(&t) {
            out.push(t);
        }
    }
    out
}

/// episode 표에서 읽을 칸 (`episode_from_row`와 같은 순서).
pub(crate) const EP_COLS: &str =
    "e.id, e.day, e.kind, e.rule_label, e.start_ms, e.end_ms, e.distance_m, e.from_name, e.to_name, e.region, e.stops, e.title, e.tags";

/// `EP_COLS` 순서의 줄 → Episode (칸 번호 `at`부터).
pub(crate) fn episode_from_row(r: &Row, at: usize) -> rusqlite::Result<Episode> {
    Ok(Episode {
        facts: EpisodeFacts {
            id: r.get(at)?,
            day: format_day(r.get(at + 1)?),
            kind: kind_from(r.get(at + 2)?),
            rule_label: r.get(at + 3)?,
            start_ms: r.get(at + 4)?,
            end_ms: r.get(at + 5)?,
            distance_m: r.get(at + 6)?,
            from_name: r.get(at + 7)?,
            to_name: r.get(at + 8)?,
            region: r.get(at + 9)?,
            stops: decode_stops(&r.get::<_, String>(at + 10)?),
        },
        title: r.get(at + 11)?,
        tags: split_names(&r.get::<_, String>(at + 12)?),
    })
}

pub(crate) fn episodes_of_day(conn: &Connection, d: i64) -> Result<Vec<Episode>, CoreError> {
    let mut st = conn.prepare_cached(&format!("SELECT {EP_COLS} FROM episode e WHERE e.day = ?1 ORDER BY e.ord"))?;
    let rows = st.query_map([d], |r| episode_from_row(r, 0))?;
    Ok(rows.collect::<Result<_, _>>()?)
}

/// 코스로 쓸 들른 곳 (없으면 도착지 하나). 없는 에피소드면 None.
pub(crate) fn course_stops(conn: &Connection, id: i64) -> Result<Option<Vec<StopFact>>, CoreError> {
    let row: Option<(String, String, i64, f64, f64)> = conn
        .query_row("SELECT stops, to_name, end_ms, to_lat, to_lon FROM episode WHERE id = ?1", [id], |r| {
            Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?))
        })
        .optional()?;
    Ok(row.map(|(stops, to_name, end_ms, lat, lon)| {
        let stops = decode_stops(&stops);
        if stops.is_empty() {
            vec![StopFact {
                name: to_name,
                category: String::new(),
                address: String::new(),
                arrive_ms: end_ms,
                leave_ms: end_ms,
                lat,
                lon,
                first_visit: false,
            }]
        } else {
            stops
        }
    }))
}

/// 하루 제목·태그 (`DaySummary.title`·`tags`): LLM 제목이 있으면 그것과 LLM 태그, 없으면 규칙 제목, 없으면 None.
pub(crate) fn day_title(conn: &Connection, d: i64) -> Result<(Option<String>, Vec<String>), CoreError> {
    let row: Option<(Option<String>, Option<String>, String)> = conn
        .prepare_cached("SELECT title, rule_title, tags FROM day_memory WHERE day = ?1")?
        .query_row([d], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)))
        .optional()?;
    Ok(match row {
        Some((Some(t), _, tags)) => (Some(t), split_names(&tags)),
        Some((None, rule, _)) => (rule, Vec::new()),
        None => (None, Vec::new()),
    })
}

/// 하루의 검색 색인을 다시 쓴다 (에피소드마다 한 줄).
pub(crate) fn reindex_day(conn: &Connection, d: i64) -> Result<(), CoreError> {
    let (title, summary, tags): (String, String, String) = conn
        .prepare_cached("SELECT coalesce(title, rule_title, ''), summary, tags FROM day_memory WHERE day = ?1")?
        .query_row([d], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)))
        .optional()?
        .unwrap_or_default();
    let day_text = format!("{title} {summary} {}", split_names(&tags).join(" "));
    let mut del = conn.prepare_cached("DELETE FROM memory_fts WHERE rowid = ?1")?;
    let mut ins = conn.prepare_cached(
        "INSERT INTO memory_fts (rowid, day_bi, ep_bi, stop_bi, uni) VALUES (?1, ?2, ?3, ?4, ?5)",
    )?;
    for e in episodes_of_day(conn, d)? {
        let f = &e.facts;
        let ep_text = format!(
            "{} {} {} {} {} {}",
            e.title.as_deref().unwrap_or(""),
            e.tags.join(" "),
            f.rule_label,
            f.region,
            f.from_name,
            f.to_name
        );
        let stop_text: String =
            f.stops.iter().map(|s| format!("{} {} {}", s.name, s.category, s.address)).collect::<Vec<_>>().join(" ");
        del.execute([f.id])?;
        ins.execute(params![
            f.id,
            search::index_text(&day_text),
            search::index_text(&ep_text),
            search::index_text(&stop_text),
            search::unigram_text(&[&day_text, &ep_text, &stop_text]),
        ])?;
    }
    Ok(())
}

// ---- 라벨링 대기열 ----

/// 라벨링이 필요한 날 (상위 모듈 설명). `now` = 지금 시각 (UTC ms).
pub(crate) fn pending_days(store: &EodiStore, conn: &Connection, limit: u32, now: i64) -> Result<Vec<DayFacts>, CoreError> {
    let last_fix: Option<i64> = conn.query_row("SELECT max(t_ms) FROM fix", [], |r| r.get(0))?;
    let idle = last_fix.is_none_or(|t| t + SETTLE_MS <= now);
    let mut st = conn.prepare_cached(
        "SELECT day FROM day_memory
         WHERE needs_llm = 1 AND (enriched_sig IS NULL OR enriched_sig != sig) AND retry_at_ms <= ?1
         ORDER BY day DESC",
    )?;
    let mut q = st.query([now])?;
    let mut out = Vec::new();
    while let Some(r) = q.next()? {
        if out.len() >= limit as usize {
            break;
        }
        let d: i64 = r.get(0)?;
        if !(idle || store.day_start_ms(d + 1) <= now) {
            continue;
        }
        let episodes: Vec<EpisodeFacts> = episodes_of_day(conn, d)?.into_iter().map(|e| e.facts).collect();
        out.push(DayFacts {
            day: format_day(d),
            weekday: WEEKDAYS[weekday_index(d)].to_owned(),
            total_distance_m: episodes.iter().map(|e| e.distance_m).sum(),
            episodes,
        });
    }
    Ok(out)
}

/// 붙어 있는 라벨 중 제목이 [bad]인 날을 다시 대기열에 넣는다 (라벨 규칙이 바뀐 뒤 예전 규칙으로 붙은 어색한 제목 고치기).
/// 라벨은 다음 라벨이 붙을 때까지 그대로 보인다. 다시 넣은 날 수를 돌려준다.
pub(crate) fn requeue_labels_where(conn: &Connection, bad: &dyn Fn(&str) -> bool) -> Result<u32, CoreError> {
    let days: Vec<(i64, String)> = {
        let mut st = conn.prepare_cached(
            "SELECT day, title FROM day_memory WHERE needs_llm = 1 AND title IS NOT NULL AND enriched_sig = sig",
        )?;
        let rows = st.query_map([], |r| Ok((r.get(0)?, r.get(1)?)))?;
        rows.collect::<Result<_, _>>()?
    };
    let mut n = 0;
    let mut up = conn.prepare_cached("UPDATE day_memory SET enriched_sig = NULL, retry_at_ms = 0 WHERE day = ?1")?;
    for (d, title) in days {
        if bad(&title) {
            up.execute([d])?;
            n += 1;
        }
    }
    Ok(n)
}

/// LLM 라벨 저장 (상위 모듈 `set_day_enrichment`).
pub(crate) fn save_enrichment(conn: &mut Connection, d: i64, e: &DayEnrichment) -> Result<(), CoreError> {
    let title = clean(e.title.trim()).trim().to_owned();
    if title.is_empty() {
        return Err(CoreError::InvalidInput(format!("day {}: empty title", e.day)));
    }
    let tx = conn.transaction()?;
    let sig: Option<String> =
        tx.query_row("SELECT sig FROM day_memory WHERE day = ?1", [d], |r| r.get(0)).optional()?;
    let Some(sig) = sig else {
        return Err(CoreError::InvalidInput(format!("day {}: no episodes", e.day)));
    };
    tx.execute(
        "UPDATE day_memory SET title = ?2, summary = ?3, tags = ?4, enriched_sig = ?5, retry_at_ms = 0 WHERE day = ?1",
        params![d, title, e.summary.trim(), join_names(&clean_tags(&e.tags)), sig],
    )?;
    {
        let mut st = tx.prepare_cached("UPDATE episode SET title = ?3, tags = ?4 WHERE id = ?1 AND day = ?2")?;
        for l in &e.episodes {
            let t = clean(l.title.trim()).trim().to_owned();
            st.execute(params![l.episode_id, d, (!t.is_empty()).then_some(t), join_names(&clean_tags(&l.tags))])?;
        }
    }
    reindex_day(&tx, d)?;
    tx.commit()?;
    Ok(())
}

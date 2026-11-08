//! 기록 고치기: 숨기기 · 지우기 · 되살리기, 장소 이름 고치기, 집 · 회사 직접 정하기.
//!
//! 역할 분담: Rust(여기)가 무엇을 숨길지 · 지울지 · 다시 계산할 날을 정하고, Kotlin은 메뉴 · 확인 창만 그린다.
//!
//! # 숨기기 (되살릴 수 있다)
//! - 주행: 출발 시각(`TripSummary.start_ms`)으로 고른다. `[start_ms, end_ms]` 범위를 저장하고,
//!   출발 시각이 그 안인 주행이 숨는다. 그 주행 안 머문 곳(드라이브스루 등)도 같이 숨는다.
//! - 머문 곳: 도착 시각(`StaySummary.arrive_ms`)으로 고른다. `[arrive_ms, leave_ms]` 범위, 도착 시각이 그 안인 머문 곳.
//! - 하루: 그날 0시~24시 범위. 출발 시각이 그 안인 주행 · 도착 시각이 그 안인 머문 곳이 모두 숨는다.
//! - 숨긴 것은 날짜별로 모을 때(`store`의 `collect_days`) 빠지므로 타임라인 · 지도 · 날짜 목록 · 에피소드 ·
//!   검색 · 자주 가는 곳 · 기억 에이전트 · 커뮤니티 공유 후보 어디에도 나오지 않는다.
//!   원본 위치 점은 그대로라 앞뒤 주행 · 머문 곳 계산은 바뀌지 않는다.
//! - [EodiStore::hidden_records]가 "숨긴 기록" 목록(최근에 숨긴 것부터), [EodiStore::restore_hidden]이 되살리기.
//!
//! # 지우기 (되돌릴 수 없다)
//! - 주행: 그 주행 구간의 원본 위치 점 · 주행 신호 · 내비 힌트를 지우고 다시 계산한다. 그 주행이 앞뒤 머문 곳을
//!   잇고 있었다면(주행 사이 머문 곳은 앞 주행 도착과 다음 주행 출발로 정해진다) 그 머문 곳도 같이 사라질 수 있다.
//! - 머문 곳: 머문 동안(도착 ~ 떠남 사이, 양 끝 제외)의 위치 점을 지우고, 그 자리를 영구히 숨긴다
//!   (머문 곳은 앞뒤 주행으로 정해지므로 점을 지워도 다시 생길 수 있어서). 주행 안 머문 곳이면 그 주행이 둘로 나뉜다.
//! - 하루: 그날 출발한 주행의 점(자정을 넘겨도 끝까지), 그날 0시~24시의 남은 점(앞날 주행에 속한 점은 빼고)을 지우고,
//!   그날을 지금 시각까지 영구히 숨긴다 (앞날 주행 뒤 머문 곳이 그날 도착으로 남지 않게).
//! - 영구히 숨긴 흔적은 "숨긴 기록" 목록에 나오지 않는다.
//!
//! # AI 라벨
//! 숨기기 · 지우기 · 되살리기를 하면 그날의 LLM 제목 · 이야기 · 태그와 에피소드 제목을 지운다 (숨긴 곳 이름이
//! 제목에 남지 않게). 그날은 규칙 제목으로 보이다가 라벨링 대기열에 다시 나온다.
//! 장소 이름을 고치면 옛 이름이 든 LLM 제목 · 이야기만 같은 방법으로 지운다.
//!
//! # 하루 제목 고치기
//! [EodiStore::set_day_title]: 사용자가 쓴 제목은 `day_user_title`에 따로 두어 AI · 규칙 제목보다 우선하고,
//! 숨기기 · 다시 라벨링해도 남는다. 빈 제목(None)이면 지워 AI · 규칙 제목으로 돌아간다. 그날을 지우면 같이 지운다.
//!
//! # 장소 이름 고치기
//! [EodiStore::rename_place]: 출처 `User`로 저장한다 (모든 출처보다 우선, `places` 모듈). 이름은 장소에 붙으므로
//! 그 장소의 모든 방문(다른 날 포함)에 같이 보인다. 분류 · 주소를 비워 넘기면 원래 값을 그대로 둔다.
//!
//! # 집 · 회사
//! [EodiStore::set_my_anchor]: 사용자가 정한 집 · 회사는 내비 즐겨찾기 · 기록 추정보다 우선한다 (`memory` 모듈).
//! None을 넘기면 지우고 내비 · 추정으로 돌아간다. [EodiStore::home_office]는 지금 쓰는 집 · 회사와 누가 정했는지.
//!
//! # 저장 (DB 구조 버전 7)
//! `hidden`(숨긴 범위), `saved_place`(저장한 곳, `saved` 모듈), 사용자 집 · 회사는 `memory_anchor`의 kind 4 · 5.

use rusqlite::{params, Connection, OptionalExtension};

use crate::ffi::GeoPoint;
use crate::geo::Point;
use crate::places::{self, PlaceLabel, PlaceSource};
use crate::store::{format_day, mark_dirty, parse_day, CoreError, EodiStore, StaySummary, TripSummary};

/// DB 구조 버전 7에서 더하는 표 (다른 작업과 버전 번호를 합칠 때 다시 실행돼도 되게 IF NOT EXISTS).
pub(crate) const SCHEMA_V7: &str = "
-- 숨긴 기록. kind 0 = 주행(출발 시각이 [from_ms, to_ms] 안), 1 = 머문 곳(도착 시각), 2 = 하루(둘 다).
-- permanent = 1이면 지운 기록의 흔적이라 '숨긴 기록' 목록에 나오지 않고 되살릴 수 없다.
CREATE TABLE IF NOT EXISTS hidden (
    id INTEGER PRIMARY KEY,
    kind INTEGER NOT NULL,
    from_ms INTEGER NOT NULL,
    to_ms INTEGER NOT NULL,
    label TEXT NOT NULL DEFAULT '',
    hidden_ms INTEGER NOT NULL,
    permanent INTEGER NOT NULL DEFAULT 0
);
-- 저장한 곳 (♥, saved 모듈).
CREATE TABLE IF NOT EXISTS saved_place (place_id INTEGER PRIMARY KEY, saved_ms INTEGER NOT NULL);
";

/// DB 구조 버전 11: 사용자가 직접 고친 하루 제목. AI·규칙 제목보다 우선하고, 다시 라벨링해도 남는다.
pub(crate) const SCHEMA_V11: &str = "CREATE TABLE IF NOT EXISTS day_user_title (day INTEGER PRIMARY KEY, title TEXT NOT NULL);";

/// 하루 제목 최대 글자 수 ([EodiStore::set_day_title]).
pub const DAY_TITLE_MAX: usize = 30;

/// 예전 버전 DB를 흉내 내는 테스트용 (v11 표 지우기).
#[cfg(test)]
pub(crate) const DROP_V11: &str = "DROP TABLE day_user_title;";

/// 예전 버전 DB를 흉내 내는 테스트용 (v7 표 지우기).
#[cfg(test)]
pub(crate) const DROP_V7: &str = "DROP TABLE hidden; DROP TABLE saved_place;";

const KIND_TRIP: i64 = 0;
const KIND_STAY: i64 = 1;
const KIND_DAY: i64 = 2;

/// 이름을 고칠 때 옛 이름이 이 글자 수 이상이어야 그 이름이 든 AI 제목을 지운다 (한 글자로 많은 날을 지우지 않게).
const MIN_OLD_NAME_CHARS: usize = 2;

/// 숨긴 것의 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum HiddenKind {
    Trip,
    Stay,
    Day,
}

/// "숨긴 기록" 목록 한 줄.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct HiddenRecord {
    /// 되살릴 때 넘긴다 ([EodiStore::restore_hidden])
    pub id: i64,
    pub kind: HiddenKind,
    /// 그 기록이 있던 날 "YYYY-MM-DD"
    pub day: String,
    /// 주행 = 출발 · 도착 시각, 머문 곳 = 도착 · 떠난 시각, 하루 = 그날 0시 ~ 24시 직전 (UTC epoch ms)
    pub from_ms: i64,
    pub to_ms: i64,
    /// 숨길 때의 이름 (주행 "광장시장 → 집", 머문 곳 "광장시장", 하루 "광장시장 · 경복궁"). 모르면 빈 문자열.
    pub label: String,
    /// 숨긴 시각
    pub hidden_ms: i64,
}

/// 집 · 회사.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AnchorRole {
    Home,
    Office,
}

/// 집 · 회사를 누가 정했나.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AnchorSource {
    /// 사용자가 "여기를 집으로/회사로"
    User,
    /// 차량 내비 즐겨찾기
    Navi,
    /// 기록에서 추정 (밤 · 평일 낮에 오래 머문 곳)
    Guessed,
}

/// 지금 쓰는 집 또는 회사.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AnchorInfo {
    pub role: AnchorRole,
    pub lat: f64,
    pub lon: f64,
    /// 그 근처(250m) 장소 이름. 모르면 빈 문자열.
    pub name: String,
    pub source: AnchorSource,
}

/// 설정 화면의 집 · 회사.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct HomeOffice {
    pub home: Option<AnchorInfo>,
    pub office: Option<AnchorInfo>,
}

#[uniffi::export]
impl EodiStore {
    /// 주행 하나를 숨긴다 (`start_ms` = 그 주행의 `TripSummary.start_ms`). 지금 보이는 주행이 아니면 `InvalidInput`.
    pub fn hide_trip(&self, start_ms: i64, now_ms: i64) -> Result<(), CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let t = self.visible_trip(&conn, start_ms)?;
        let tx = conn.transaction()?;
        insert_hidden(&tx, KIND_TRIP, t.start_ms, t.end_ms, &trip_label(&t), now_ms, false)?;
        self.after_edit(&tx, t.start_ms, t.start_ms)?;
        tx.commit()?;
        Ok(())
    }

    /// 하루 제목을 사용자가 고친다 (`day` = "YYYY-MM-DD"). 앞뒤 공백을 자르고, 비었거나 None이면 사용자 제목을 지운다.
    /// [DAY_TITLE_MAX]자를 넘거나 틀린 날짜면 `InvalidInput`. 검색 색인도 새 제목으로 다시 쓴다.
    pub fn set_day_title(&self, day: String, title: Option<String>) -> Result<(), CoreError> {
        let d = parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let t = title.map(|t| t.split_whitespace().collect::<Vec<_>>().join(" ")).filter(|t| !t.is_empty());
        if t.as_ref().is_some_and(|t| t.chars().count() > DAY_TITLE_MAX) {
            return Err(CoreError::InvalidInput(format!("title longer than {DAY_TITLE_MAX}")));
        }
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let tx = conn.transaction()?;
        match &t {
            Some(t) => tx.execute(
                "INSERT INTO day_user_title (day, title) VALUES (?1, ?2) ON CONFLICT(day) DO UPDATE SET title = excluded.title",
                params![d, t],
            )?,
            None => tx.execute("DELETE FROM day_user_title WHERE day = ?1", [d])?,
        };
        crate::memory::reindex_day(&tx, d)?;
        tx.commit()?;
        Ok(())
    }

    /// 머문 곳 하나를 숨긴다 (`arrive_ms` = 그 머문 곳의 `StaySummary.arrive_ms`). 지금 보이는 곳이 아니면 `InvalidInput`.
    pub fn hide_stay(&self, arrive_ms: i64, now_ms: i64) -> Result<(), CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let s = self.visible_stay(&conn, arrive_ms)?;
        let tx = conn.transaction()?;
        insert_hidden(&tx, KIND_STAY, s.arrive_ms, s.leave_ms.max(s.arrive_ms), &stay_label(&s), now_ms, false)?;
        self.after_edit(&tx, s.arrive_ms, s.arrive_ms)?;
        tx.commit()?;
        Ok(())
    }

    /// 하루를 통째로 숨긴다 ("YYYY-MM-DD"). 날짜 형식이 틀리거나 보이는 기록이 없는 날이면 `InvalidInput`.
    pub fn hide_day(&self, day: String, now_ms: i64) -> Result<(), CoreError> {
        let d = parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let (trips, stays) = self
            .day_records(&conn, d, d)?
            .remove(&d)
            .ok_or_else(|| CoreError::InvalidInput(format!("day {day}: no records")))?;
        let tx = conn.transaction()?;
        let (ds, de) = (self.day_start_ms(d), self.day_start_ms(d + 1) - 1);
        insert_hidden(&tx, KIND_DAY, ds, de, &day_label(&trips, &stays), now_ms, false)?;
        self.after_edit(&tx, ds, ds)?;
        tx.commit()?;
        Ok(())
    }

    /// "숨긴 기록" 목록 (최근에 숨긴 것부터). 지운 기록의 흔적은 나오지 않는다.
    pub fn hidden_records(&self) -> Result<Vec<HiddenRecord>, CoreError> {
        let conn = self.lock()?;
        let mut st = conn.prepare_cached(
            "SELECT id, kind, from_ms, to_ms, label, hidden_ms FROM hidden WHERE permanent = 0 ORDER BY hidden_ms DESC, id DESC",
        )?;
        let rows = st.query_map([], |r| {
            let (kind, from_ms): (i64, i64) = (r.get(1)?, r.get(2)?);
            Ok(HiddenRecord {
                id: r.get(0)?,
                kind: match kind {
                    KIND_TRIP => HiddenKind::Trip,
                    KIND_STAY => HiddenKind::Stay,
                    _ => HiddenKind::Day,
                },
                day: format_day(self.local_day(from_ms)),
                from_ms,
                to_ms: r.get(3)?,
                label: r.get(4)?,
                hidden_ms: r.get(5)?,
            })
        })?;
        Ok(rows.collect::<Result<_, _>>()?)
    }

    /// 숨긴 기록을 되살린다. 없거나 지운 기록의 흔적이면 `InvalidInput`.
    pub fn restore_hidden(&self, id: i64) -> Result<(), CoreError> {
        let mut conn = self.lock()?;
        self.flush(&mut conn)?;
        let tx = conn.transaction()?;
        let row: Option<(i64, i64)> = tx
            .query_row("SELECT from_ms, to_ms FROM hidden WHERE id = ?1 AND permanent = 0", [id], |r| {
                Ok((r.get(0)?, r.get(1)?))
            })
            .optional()?;
        let Some((from_ms, to_ms)) = row else {
            return Err(CoreError::InvalidInput(format!("hidden {id} not found")));
        };
        tx.execute("DELETE FROM hidden WHERE id = ?1", [id])?;
        self.after_edit(&tx, from_ms, to_ms)?;
        tx.commit()?;
        Ok(())
    }

    /// 주행 하나를 지운다 (되돌릴 수 없다, 규칙은 모듈 설명). 지금 보이는 주행이 아니면 `InvalidInput`.
    pub fn delete_trip(&self, start_ms: i64) -> Result<(), CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let t = self.visible_trip(&conn, start_ms)?;
        let (seg_start, seg_end): (i64, i64) = conn.query_row(
            "SELECT seg_start, seg_end FROM trip WHERE start_ms = ?1 ORDER BY seg_start LIMIT 1",
            [t.start_ms],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )?;
        let tx = conn.transaction()?;
        delete_raw(&tx, seg_start, seg_end)?;
        forget_labels(&tx, self.local_day(t.start_ms), self.local_day(t.end_ms))?;
        mark_dirty(&tx, seg_start, seg_end)?;
        tx.commit()?;
        self.flush_all(&mut conn)
    }

    /// 머문 곳 하나를 지운다 (되돌릴 수 없다). 지금 보이는 곳이 아니면 `InvalidInput`.
    pub fn delete_stay(&self, arrive_ms: i64, now_ms: i64) -> Result<(), CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let s = self.visible_stay(&conn, arrive_ms)?;
        let tx = conn.transaction()?;
        let leave = s.leave_ms.max(s.arrive_ms);
        insert_hidden(&tx, KIND_STAY, s.arrive_ms, leave, "", now_ms, true)?;
        // 머문 동안의 점 (양 끝은 앞 · 뒤 주행의 끝 · 처음이라 남긴다)
        if leave - s.arrive_ms > 1 {
            delete_raw(&tx, s.arrive_ms + 1, leave - 1)?;
            mark_dirty(&tx, s.arrive_ms, leave)?;
        }
        self.after_edit(&tx, s.arrive_ms, s.arrive_ms)?;
        tx.commit()?;
        self.flush_all(&mut conn)
    }

    /// 하루를 통째로 지운다 (되돌릴 수 없다, 규칙은 모듈 설명). 날짜 형식이 틀리거나 기록이 없는 날이면 `InvalidInput`.
    pub fn delete_day(&self, day: String, now_ms: i64) -> Result<(), CoreError> {
        let d = parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let (ds, de) = (self.day_start_ms(d), self.day_start_ms(d + 1));
        let has_records = self.day_records(&conn, d, d)?.contains_key(&d);
        let has_fixes = conn
            .query_row("SELECT 1 FROM fix WHERE t_ms >= ?1 AND t_ms < ?2 LIMIT 1", [ds, de], |_| Ok(()))
            .optional()?
            .is_some();
        if !has_records && !has_fixes {
            return Err(CoreError::InvalidInput(format!("day {day}: no records")));
        }
        // 그날 출발한 주행 (자정을 넘겨도 끝까지 지운다)
        let own: Vec<(i64, i64)> = {
            let mut st = conn.prepare("SELECT seg_start, seg_end FROM trip WHERE start_ms >= ?1 AND start_ms < ?2")?;
            let rows = st.query_map([ds, de], |r| Ok((r.get(0)?, r.get(1)?)))?;
            rows.collect::<Result<_, _>>()?
        };
        // 앞날 출발해 그날로 넘어온 주행의 점은 남긴다
        let prev_end: Option<i64> =
            conn.query_row("SELECT max(seg_end) FROM trip WHERE start_ms < ?1 AND seg_end >= ?1", [ds], |r| r.get(0))?;
        let stray_lo = prev_end.map_or(ds, |e| e.saturating_add(1).max(ds));
        let tx = conn.transaction()?;
        let (mut lo, mut hi) = (stray_lo, de - 1);
        for &(a, b) in &own {
            delete_raw(&tx, a, b)?;
            lo = lo.min(a);
            hi = hi.max(b);
        }
        if stray_lo < de {
            delete_raw(&tx, stray_lo, de - 1)?;
        }
        insert_hidden(&tx, KIND_DAY, ds, (de - 1).min(now_ms).max(ds), "", now_ms, true)?;
        tx.execute("DELETE FROM day_user_title WHERE day = ?1", [d])?;
        forget_labels(&tx, d, self.local_day(hi))?;
        mark_dirty(&tx, lo, hi)?;
        self.refresh_days(&tx, d, d)?;
        tx.commit()?;
        self.flush_all(&mut conn)
    }

    /// 장소 이름을 고친다 (출처 `User`, 모든 출처보다 우선). `category` · `address`가 비면 원래 값을 둔다.
    /// 없는 장소이거나 이름이 비었으면 `InvalidInput`.
    pub fn rename_place(&self, place_id: i64, name: String, category: String, address: String) -> Result<(), CoreError> {
        let name = name.trim().to_owned();
        if name.is_empty() {
            return Err(CoreError::InvalidInput(format!("place {place_id}: empty name")));
        }
        let mut conn = self.lock()?;
        self.flush(&mut conn)?;
        let tx = conn.transaction()?;
        let old: Option<(Option<String>, String, String)> = tx
            .query_row("SELECT name, category, address FROM place WHERE id = ?1", [place_id], |r| {
                Ok((r.get(0)?, r.get(1)?, r.get(2)?))
            })
            .optional()?;
        let Some((old_name, old_category, old_address)) = old else {
            return Err(CoreError::InvalidInput(format!("place {place_id} not found")));
        };
        let keep = |new: String, old: String| if new.trim().is_empty() { old } else { new.trim().to_owned() };
        let label = PlaceLabel {
            name: name.clone(),
            category: keep(category, old_category),
            address: keep(address, old_address),
            source: PlaceSource::User,
        };
        if places::set_label(&tx, place_id, &label)? {
            if let Some(old) = old_name.filter(|o| *o != name && o.chars().count() >= MIN_OLD_NAME_CHARS) {
                forget_labels_mentioning(&tx, &old)?;
            }
            self.refresh_place_days(&tx, &[place_id])?;
        }
        tx.commit()?;
        Ok(())
    }

    /// "여기를 집으로 / 회사로". `at`이 None이면 사용자가 정한 것을 지운다 (내비 · 기록 추정으로 돌아간다).
    /// 바뀌면 모든 날의 에피소드 종류를 다시 정한다. 좌표가 범위 밖 · NaN이면 `InvalidInput`.
    pub fn set_my_anchor(&self, role: AnchorRole, at: Option<GeoPoint>) -> Result<(), CoreError> {
        if let Some(p) = at {
            if !(p.lat.is_finite() && p.lon.is_finite() && p.lat.abs() <= 90.0 && p.lon.abs() <= 180.0) {
                return Err(CoreError::InvalidInput(format!("point {p:?}")));
            }
        }
        let mut conn = self.lock()?;
        self.flush(&mut conn)?;
        crate::memory::set_user_anchor(&conn, role == AnchorRole::Office, at.map(Point::from))?;
        crate::memory::flush_memory(self, &mut conn, true)
    }

    /// 지금 쓰는 집 · 회사와 누가 정했는지 (설정 화면). 모르면 None.
    pub fn home_office(&self) -> Result<HomeOffice, CoreError> {
        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;
        let [home, office] = crate::memory::anchor_sources(&conn)?;
        let info = |role: AnchorRole, a: Option<(Point, u8)>| -> Result<Option<AnchorInfo>, CoreError> {
            let Some((p, src)) = a else { return Ok(None) };
            Ok(Some(AnchorInfo {
                role,
                lat: p.lat,
                lon: p.lon,
                name: name_near(&conn, p)?.unwrap_or_default(),
                source: match src {
                    0 => AnchorSource::User,
                    1 => AnchorSource::Navi,
                    _ => AnchorSource::Guessed,
                },
            }))
        };
        Ok(HomeOffice { home: info(AnchorRole::Home, home)?, office: info(AnchorRole::Office, office)? })
    }
}

// ---- 내부 구현 ----

/// 숨긴 범위들 (store의 `collect_days`가 쓴다). 표가 작아(수십 줄) 한 번에 읽어 훑는다.
#[derive(Default)]
pub(crate) struct Hidden(Vec<(i64, i64, i64)>);

impl Hidden {
    pub(crate) fn load(conn: &Connection) -> rusqlite::Result<Self> {
        let mut st = conn.prepare_cached("SELECT kind, from_ms, to_ms FROM hidden")?;
        let rows = st.query_map([], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)))?;
        Ok(Hidden(rows.collect::<Result<_, _>>()?))
    }

    fn covers(&self, kind: i64, t: i64) -> bool {
        self.0.iter().any(|&(k, a, b)| (k == kind || k == KIND_DAY) && a <= t && t <= b)
    }

    /// 출발 시각이 `start_ms`인 주행이 숨었는지.
    pub(crate) fn trip(&self, start_ms: i64) -> bool {
        self.covers(KIND_TRIP, start_ms)
    }

    /// 도착 시각이 `arrive_ms`인 머문 곳이 숨었는지.
    pub(crate) fn stay(&self, arrive_ms: i64) -> bool {
        self.covers(KIND_STAY, arrive_ms)
    }
}

impl EodiStore {
    fn visible_trip(&self, conn: &Connection, start_ms: i64) -> Result<TripSummary, CoreError> {
        let d = self.local_day(start_ms);
        self.day_records(conn, d, d)?
            .remove(&d)
            .and_then(|(trips, _)| trips.into_iter().find(|t| t.start_ms == start_ms))
            .ok_or_else(|| CoreError::InvalidInput(format!("trip {start_ms} not found")))
    }

    fn visible_stay(&self, conn: &Connection, arrive_ms: i64) -> Result<StaySummary, CoreError> {
        let d = self.local_day(arrive_ms);
        self.day_records(conn, d, d)?
            .remove(&d)
            .and_then(|(_, stays)| stays.into_iter().find(|s| s.arrive_ms == arrive_ms))
            .ok_or_else(|| CoreError::InvalidInput(format!("stay {arrive_ms} not found")))
    }

    /// 숨기기 · 되살리기 뒤: 그 시각들이 속한 날의 AI 라벨을 지우고 하루 요약 · 에피소드를 다시 만들게 한다.
    fn after_edit(&self, conn: &Connection, t0: i64, t1: i64) -> Result<(), CoreError> {
        let (d0, d1) = (self.local_day(t0), self.local_day(t1.max(t0)));
        forget_labels(conn, d0, d1)?;
        self.refresh_days(conn, d0, d1)
    }
}

fn insert_hidden(
    conn: &Connection,
    kind: i64,
    from_ms: i64,
    to_ms: i64,
    label: &str,
    now_ms: i64,
    permanent: bool,
) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO hidden (kind, from_ms, to_ms, label, hidden_ms, permanent) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
        params![kind, from_ms, to_ms, label, now_ms, permanent],
    )?;
    Ok(())
}

/// 시각 [a, b]의 원본(위치 점 · 주행 신호 · 내비 힌트)을 지운다.
fn delete_raw(conn: &Connection, a: i64, b: i64) -> rusqlite::Result<()> {
    conn.execute("DELETE FROM fix WHERE t_ms BETWEEN ?1 AND ?2", [a, b])?;
    conn.execute("DELETE FROM drive_event WHERE t_ms BETWEEN ?1 AND ?2", [a, b])?;
    conn.execute("DELETE FROM navi_hint WHERE t_ms BETWEEN ?1 AND ?2", [a, b])?;
    Ok(())
}

/// 날짜 d0..=d1의 LLM 라벨(하루 제목 · 이야기 · 태그, 에피소드 제목 · 태그)을 지운다 → 라벨링 대기열에 다시 나온다.
fn forget_labels(conn: &Connection, d0: i64, d1: i64) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE day_memory SET title = NULL, summary = '', tags = '', enriched_sig = NULL, retry_at_ms = 0
         WHERE day BETWEEN ?1 AND ?2",
        [d0, d1],
    )?;
    conn.execute("UPDATE episode SET title = NULL, tags = '' WHERE day BETWEEN ?1 AND ?2", [d0, d1])?;
    Ok(())
}

/// LLM 제목 · 이야기에 `name`이 든 날의 라벨을 지운다 (장소 이름을 고쳤을 때).
fn forget_labels_mentioning(conn: &Connection, name: &str) -> rusqlite::Result<()> {
    let days: Vec<i64> = {
        let mut st = conn.prepare(
            "SELECT day FROM day_memory WHERE instr(coalesce(title, ''), ?1) > 0 OR instr(summary, ?1) > 0
             UNION SELECT day FROM episode WHERE instr(coalesce(title, ''), ?1) > 0",
        )?;
        let rows = st.query_map([name], |r| r.get(0))?;
        rows.collect::<Result<_, _>>()?
    };
    for d in days {
        forget_labels(conn, d, d)?;
    }
    Ok(())
}

/// 점 p 근처(집 · 회사 반경) 가장 가까운 이름 있는 장소의 이름.
fn name_near(conn: &Connection, p: Point) -> rusqlite::Result<Option<String>> {
    let Some(id) = places::nearest_place(conn, p, crate::memory::ANCHOR_RADIUS_M)? else { return Ok(None) };
    conn.query_row("SELECT name FROM place WHERE id = ?1", [id], |r| r.get(0))
}

fn place_name(p: &Option<PlaceLabel>) -> Option<&str> {
    p.as_ref().map(|l| l.name.as_str()).filter(|n| !n.is_empty())
}

fn trip_label(t: &TripSummary) -> String {
    match (place_name(&t.start_place), place_name(&t.end_place)) {
        (Some(a), Some(b)) => format!("{a} → {b}"),
        (Some(a), None) => format!("{a} →"),
        (None, Some(b)) => format!("→ {b}"),
        (None, None) => String::new(),
    }
}

fn stay_label(s: &StaySummary) -> String {
    place_name(&s.place).unwrap_or_default().to_owned()
}

fn day_label(trips: &[TripSummary], stays: &[StaySummary]) -> String {
    let mut names: Vec<&str> = Vec::new();
    for n in stays.iter().filter_map(|s| place_name(&s.place)) {
        if !names.contains(&n) {
            names.push(n);
        }
    }
    if names.is_empty() {
        return trips.first().map(trip_label).unwrap_or_default();
    }
    names.truncate(3);
    names.join(" · ")
}

#[cfg(test)]
pub(crate) mod tests;

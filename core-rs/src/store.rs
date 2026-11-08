//! 기록 저장소 (Kotlin에 공개되는 `EodiStore` 객체).
//!
//! 계약(시그니처·의미)은 이 파일의 doc 주석이 기준이다. 구현은 SQLite(rusqlite, bundled — FTS5·R-tree 포함)다.
//!
//! # 데이터가 흐르는 길
//! 1. `ingest` / `mark_drive_event`는 원본(위치 점, 주행 신호)만 저장하고 "바뀐 시간 범위"(`dirty`)를 넓힌다.
//!    주행 나누기는 하지 않으므로 묶음 하나가 1ms 안팎이고, 점이 아무리 쌓여도 저장 비용은 묶음 크기에만 비례한다.
//! 2. 조회(`day_summaries` / `day_detail`)가 오면 먼저 밀린 범위만 다시 계산한다(`flush`):
//!    바뀐 범위를 감싸는 기존 주행들만 지우고, 그 구간의 점을 시간순으로 읽으며 주행을 다시 나눠 `trip` 표에 넣고,
//!    영향받은 날의 `day_summary` 줄만 고친다. 그래서 1년치가 쌓여도 조회는 표 몇 줄 읽기다.
//!    주행 중에 화면이 매번 조회하면 지금 달리는 주행 하나(최대 수천 점)만 다시 계산한다.
//!    다시 만든 주행의 출발·도착·머문 곳은 같은 자리에서 장소에 이어 이름을 붙인다 (`places` 모듈).
//!
//! # 주행(trip)
//! - 위치 점을 시간순으로 놓고, 이웃한 두 점 사이 공백이 5분 이상이거나 그 사이에 주행 시작·끝 신호가 있으면 끊는다
//!   (`trip::is_cut`: 시작 신호 시각의 점은 새 주행, 끝 신호 시각의 점은 앞 주행에 들어간다).
//! - 끊긴 구간마다 혼자 튀는 점(GPS 점프, 시속 252km 초과처럼 보이는 점)을 빼고(`geo::drop_spikes`),
//!   남은 점이 2개 미만이면 주행으로 치지 않는다 (점은 지우지 않고 남겨 둔다).
//! - 끝 신호 직후(5분 안, 다음 시작 신호 전)에 시작하는 구간은 주차 중 점이라 주행으로 치지 않는다 (`trip::starts_parked`).
//!   기록기가 기어 P 확인을 기다리는 몇 초 동안 받은 점이 여기에 해당한다.
//! - `start_ms`/`end_ms` = 남은 첫·마지막 점 시각, `distance_m` = 점 사이 대원 거리 합,
//!   `path` = `geo::simplify`(허용 오차 5m)로 줄인 점.
//! - 주행은 **시작 시각의 현지 날짜**에 속한다 (자정을 넘겨도 출발한 날).
//!
//! # 머문 곳(stay)
//! 앱은 기어가 P가 아닐 때만 위치를 기록하므로, 주차는 점이 모인 곳이 아니라 "주행과 주행 사이의 공백"으로 나타난다.
//! - (가) 주행 사이: 다음 주행이 앞 주행 끝 자리에서 300m 안에서 시작하고 공백이 10분 이상이면,
//!   앞 주행 끝 자리에 머문 곳 하나 (도착 = 앞 주행 `end_ms`, 떠남 = 다음 주행 `start_ms`, 중심 = 앞 주행 끝 점).
//!   다음 주행이 다음 날이어도 같다 (밤새 주차 = 도착한 날의 머문 곳 하나).
//!   멀리서 다시 출발했거나 공백이 10분 미만이면 머문 곳이 아니다.
//! - (나) 주행 안: `trip::detect_stays` (반경 150m 안에서 10분 이상, 드라이브스루·픽업 대기 등).
//! - (다) 마지막 도착: 전체 기록의 마지막 주행 뒤에 끝 신호(`DriveEvent::End`)가 있으면, 아직 다음 주행이 없어도
//!   그 끝 자리를 "지금 머무는 중"인 머문 곳으로 둔다. 이때 `leave_ms == arrive_ms`다.
//!   끝 신호가 없으면(아직 달리는 중이거나 신호를 못 받음) 머문 곳을 만들지 않고, 다음 주행이 오면 (가)로 정해진다.
//! - 머문 곳은 **도착 시각의 현지 날짜**에 속한다. 그래서 자정을 넘긴 주행 뒤의 머문 곳만 있는 날
//!   (주행 0개, 머문 곳 1개 이상)도 생길 수 있고, 그런 날도 `day_summaries`에 나온다.
//!
//! # 하루(day)
//! 현지 날짜는 `open`에 준 `tz_offset_minutes` 기준이다. 다른 오프셋으로 다시 열면 날짜별 요약을 전부 다시 만든다.
//!
//! # 숨긴 기록
//! 사용자가 숨기거나 지운 주행·머문 곳·하루(`edits` 모듈)는 날짜별로 모을 때(`collect_days`) 빠진다.
//! 머문 곳은 숨긴 주행이 있어도 원래대로 계산한 뒤에 뺀다 (주행 하나를 숨겼다고 앞뒤 머문 곳이 바뀌지 않게).
//! 주행 안 머문 곳(드라이브스루 등)은 그 주행을 숨기면 같이 숨는다.
//! 그래서 하루 상세·지도·날짜 목록뿐 아니라 이것을 바탕으로 만드는 에피소드·검색 색인·자주 가는 곳·
//! 기억 에이전트·커뮤니티 공유 후보에서도 모두 빠진다.

use std::collections::BTreeMap;
use std::sync::{Arc, Mutex};

use rusqlite::{params, Connection, OptionalExtension};

use crate::ffi::GeoPoint;
use crate::geo::{self, Fix, Point};
use crate::places::{self, PlaceLabel, REF_END, REF_START, REF_STAY};
use crate::trip::{self, DriveMark, MarkKind, Segmenter, Stay, TripConfig};

/// DB 구조 버전 (`PRAGMA user_version`). 구조를 바꾸면 올리고 `migrate`에 단계를 추가한다.
pub(crate) const SCHEMA_VERSION: i32 = 12;
/// 이보다 정확도가 나쁜(m) 점은 버린다.
pub const MAX_ACCURACY_M: f32 = 100.0;
/// 지도에 넘길 경로의 단순화 허용 오차(m).
pub const PATH_TOLERANCE_M: f64 = 5.0;
pub(crate) const DAY_MS: i64 = 86_400_000;
/// 하루 요약을 만드는 규칙의 버전. 규칙이 바뀌면(예: 잡음 주행 빼기) 올린다 → 다음에 열 때 날짜별 요약을 다시 만든다.
const DAY_RULES: i64 = 2;

/// 위치 한 점 (FusedLocation `LocationData`에서 필요한 값만).
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct GeoFix {
    pub lat: f64,
    pub lon: f64,
    /// UTC epoch 밀리초 (LocationData.timestampMillis)
    pub t_ms: i64,
    /// 위치 정확도(m). 모르면 0.
    pub accuracy_m: f32,
}

/// 주행 시작·끝 신호. 시동(IGNITION_STATE) 또는 기어(P↔D)에서 온다.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DriveEvent {
    Start,
    End,
}

/// 타임라인 목록 한 줄 = 하루.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DaySummary {
    /// 현지 날짜 "YYYY-MM-DD" (open 때 받은 tz_offset_minutes 기준)
    pub day: String,
    /// 그날 출발한 주행 수
    pub trip_count: u32,
    /// 그날 도착한 머문 곳 수
    pub stay_count: u32,
    /// 그날 출발한 모든 주행의 거리 합 (m)
    pub distance_m: f64,
    /// 그날 출발한 주행 시간(end_ms - start_ms) 합 (ms)
    pub drive_millis: i64,
    /// 그날 첫 위치 / 마지막 위치 시각 (UTC epoch ms).
    /// 그날 주행의 시작·끝과 머문 곳 도착 시각 중 가장 이른 것 / 가장 늦은 것.
    pub first_ms: i64,
    pub last_ms: i64,
    /// 그날 머문 곳 중 이름이 있는 것의 이름, 도착 시각순으로 앞에서 최대 3개 (이름 없는 머문 곳은 건너뛴다).
    /// 목록의 "경포해변 · 초당순두부마을 · 안목해변" 한 줄용. 이름이 바뀌면 그 장소가 나오는 날의 요약을 다시 만든다.
    pub stop_names: Vec<String>,
    /// LLM이 붙인 하루 제목 (예: "비 오는 날 강릉 순두부 원정"). 출퇴근뿐인 날은 규칙 제목 ("평소 출퇴근"). 아직 없으면 None.
    pub title: Option<String>,
    /// 하루 태그 (예: "바다", "여행", "가족")
    pub tags: Vec<String>,
    /// "그날로 돌아가기"로 다시 볼 것이 있는 날: 이름 있는 머문 곳이 1곳 이상이거나 5km 이상 달렸다 ([`is_replayable`]).
    pub replayable: bool,
}

/// 다시 보기를 보여 줄 최소 거리 (m). 이보다 짧고 이름 있는 곳도 없는 날은 다시 볼 장면이 없다.
pub const REPLAY_MIN_DISTANCE_M: f64 = 5_000.0;

/// 그날로 돌아가기를 띄울 만한 날인가: 이름 있는 머문 곳 1곳 이상 또는 거리 5km 이상.
pub fn is_replayable(named_stops: usize, distance_m: f64) -> bool {
    named_stops > 0 || distance_m >= REPLAY_MIN_DISTANCE_M
}

/// 주행 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TripSummary {
    pub start_ms: i64,
    pub end_ms: i64,
    pub distance_m: f64,
    pub start: GeoPoint,
    pub end: GeoPoint,
    /// 지도에 그릴 경로: geo::simplify(허용 오차 5m)로 줄인 점
    pub path: Vec<GeoPoint>,
    /// 출발·도착 장소 이름 (아직 모르면 None, `places` 모듈)
    pub start_place: Option<crate::places::PlaceLabel>,
    pub end_place: Option<crate::places::PlaceLabel>,
}

/// 머문 곳 하나. `leave_ms == arrive_ms`면 아직 머무는 중(다음 주행이 아직 없음).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StaySummary {
    pub center: GeoPoint,
    pub arrive_ms: i64,
    pub leave_ms: i64,
    /// 장소 이름 (아직 모르면 None, `places` 모듈)
    pub place: Option<crate::places::PlaceLabel>,
    /// 그날 머문 곳 중 몇 번째인지 (도착 시각순, 1부터). 지도 GeoJSON의 `order`와 같다.
    pub order: u32,
}

/// 하루 타임라인의 한 칸: 주행 또는 머문 곳.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum DayEntry {
    Trip { trip: TripSummary },
    Stay { stay: StaySummary },
}

/// 하루 상세 (trips는 출발 시각순, stays는 도착 시각순).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DayDetail {
    pub day: String,
    pub trips: Vec<TripSummary>,
    pub stays: Vec<StaySummary>,
    /// 그날 요약. `day_summaries`가 그날에 돌려주는 값과 같다.
    /// 기록이 없는 날은 개수·합계가 0이고 first_ms = last_ms = 0.
    pub summary: DaySummary,
    /// trips와 stays를 한 줄로 합친 타임라인. 주행은 `start_ms`, 머문 곳은 `arrive_ms` 기준 시간순이고,
    /// 같은 시각이면 주행이 먼저다 (주행 첫 점에서 시작한 주행 안 머문 곳). 화면은 이 순서대로 그리기만 한다.
    pub entries: Vec<DayEntry>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum CoreError {
    #[error("db: {0}")]
    Db(String),
    #[error("invalid input: {0}")]
    InvalidInput(String),
}

impl From<rusqlite::Error> for CoreError {
    fn from(e: rusqlite::Error) -> Self {
        CoreError::Db(e.to_string())
    }
}

/// 기기 안 기록 저장소. 한 앱에서 하나만 열어 계속 쓴다. 여러 스레드에서 불러도 안전하다
/// (쓰기 연결 하나 + 읽기 전용 연결 여러 개, 조회 결과 캐시: [`conn`] 모듈 설명).
#[derive(uniffi::Object)]
pub struct EodiStore {
    tz_offset_minutes: i32,
    cfg: TripConfig,
    conn: Mutex<Connection>,
    pool: conn::Pool,
}

#[uniffi::export]
impl EodiStore {
    /// DB 파일을 열거나 만든다. `tz_offset_minutes`: 날짜를 나눌 현지 시간대 (한국 = 540).
    ///
    /// 처음이면 표를 만들고, 예전 버전 DB면 올린다. 앱보다 새 버전의 DB면 `CoreError::Db`.
    /// 지난번과 다른 시간대로 열면 날짜별 요약을 새 시간대로 다시 만든다.
    #[uniffi::constructor]
    pub fn open(path: String, tz_offset_minutes: i32) -> Result<Arc<Self>, CoreError> {
        if path.is_empty() {
            return Err(CoreError::InvalidInput("empty path".into()));
        }
        if !(-24 * 60..=24 * 60).contains(&tz_offset_minutes) {
            return Err(CoreError::InvalidInput(format!("tz_offset_minutes {tz_offset_minutes}")));
        }
        let mut conn = Connection::open(&path)?;
        // WAL: 쓰기 중에도 읽기가 막히지 않고, 커밋마다 fsync하지 않아 묶음 저장이 빠르다.
        conn.query_row("PRAGMA journal_mode = WAL", [], |r| r.get::<_, String>(0))?;
        conn.pragma_update(None, "synchronous", "NORMAL")?;
        migrate(&mut conn)?;
        let store = Self { tz_offset_minutes, cfg: TripConfig::default(), conn: Mutex::new(conn), pool: conn::Pool::new(&path) };
        store.apply_timezone()?;
        Ok(Arc::new(store))
    }

    /// 위치 묶음을 저장한다 (수 초~수십 개 단위로 부른다). 반환: 실제로 저장한 점 수.
    ///
    /// - 시간순이 아니어도 된다. 이미 지난 시각의 점이 늦게 와도 그 부분의 주행·머문 곳이 다시 계산된다.
    /// - 같은 시각(`t_ms`)의 점이 이미 있으면 무시한다 (먼저 들어온 점이 남는다).
    /// - 노이즈는 저장하지 않는다: `accuracy_m > 100`(또는 NaN), 좌표가 NaN·무한대·범위 밖(위도 ±90, 경도 ±180),
    ///   (0, 0) 좌표, `t_ms <= 0`. 정확도 0(모름)은 받는다.
    /// - 혼자 튀는 점(GPS 점프)은 저장은 하되 주행 계산에서 뺀다 (앞뒤 점이 있어야 판단할 수 있어서).
    pub fn ingest(&self, fixes: Vec<GeoFix>) -> Result<u32, CoreError> {
        let mut conn = self.lock()?;
        let tx = conn.transaction()?;
        let mut stored = 0u32;
        let mut span = Span::default();
        {
            let mut st =
                tx.prepare_cached("INSERT OR IGNORE INTO fix (t_ms, lat, lon, accuracy_m) VALUES (?1, ?2, ?3, ?4)")?;
            for f in fixes.iter().filter(|f| is_clean(f)) {
                if st.execute(params![f.t_ms, f.lat, f.lon, f.accuracy_m])? == 1 {
                    stored += 1;
                    span.add(f.t_ms);
                }
            }
        }
        if let Some((lo, hi)) = span.range() {
            mark_dirty(&tx, lo, hi)?;
        }
        tx.commit()?;
        Ok(stored)
    }

    /// 주행 시작·끝 신호를 기록한다. 주행 나누기에서 위치 공백 기준과 함께 경계로 쓴다 (`trip::is_cut`).
    /// 같은 시각·같은 종류의 신호는 한 번만 저장한다. 위치보다 늦게 와도 된다. `t_ms <= 0`이면 `InvalidInput`.
    pub fn mark_drive_event(&self, event: DriveEvent, t_ms: i64) -> Result<(), CoreError> {
        if t_ms <= 0 {
            return Err(CoreError::InvalidInput(format!("t_ms {t_ms}")));
        }
        let mut conn = self.lock()?;
        let tx = conn.transaction()?;
        let kind = match event {
            DriveEvent::Start => KIND_START,
            DriveEvent::End => KIND_END,
        };
        if tx.execute("INSERT OR IGNORE INTO drive_event (t_ms, kind) VALUES (?1, ?2)", params![t_ms, kind])? == 1 {
            mark_dirty(&tx, t_ms, t_ms)?;
        }
        tx.commit()?;
        Ok(())
    }

    /// 기록(주행 또는 머문 곳)이 있는 날을 최신순으로 최대 `limit`개. 1년치(365일)도 100ms 이내.
    pub fn day_summaries(&self, limit: u32) -> Result<Vec<DaySummary>, CoreError> {
        self.cached(Domain::Records, format!("day_summaries {limit}"), || self.day_summaries_uncached(limit))
    }

    /// 하루 상세 (`day` = "YYYY-MM-DD"). 기록이 없는 날이면 빈 trips/stays. 날짜 형식이 틀리면 `InvalidInput`.
    /// 점 1만 개인 날도 50ms 이내 (경로는 저장할 때 이미 단순화돼 있다).
    /// 하루치 지도 데이터: 주행 경로(LineString)·머문 곳(Point) GeoJSON과 화면 맞춤 범위 (`mapdata` 모듈 설명).
    /// 하루 1만 점 경로도 수 ms 안에 만든다.
    pub fn day_map(&self, day: String) -> Result<crate::mapdata::DayMap, CoreError> {
        Ok(crate::mapdata::day_map(&self.day_detail(day)?))
    }

    pub fn day_detail(&self, day: String) -> Result<DayDetail, CoreError> {
        let d = parse_day(&day).ok_or_else(|| CoreError::InvalidInput(format!("day {day:?}")))?;
        let conn = self.read()?;
        let acc = self.collect_days(&conn, d, d, true)?.remove(&d).unwrap_or_default();
        let mut summary = acc.summary(d);
        (summary.title, summary.tags) = crate::memory::day_title(&conn, d)?;
        let entries = acc.entries();
        Ok(DayDetail { day: format_day(d), trips: acc.trips, stays: acc.stays, summary, entries })
    }

    /// 저장된 위치 점 수 (디버그·검증용).
    pub fn fix_count(&self) -> Result<u64, CoreError> {
        let conn = self.snapshot()?;
        let n: i64 = conn.query_row("SELECT count(*) FROM fix", [], |r| r.get(0))?;
        Ok(n as u64)
    }
}

// ---- 내부 구현 ----

pub(crate) const KIND_START: i64 = 0;
pub(crate) const KIND_END: i64 = 1;

const SCHEMA_V1: &str = "
CREATE TABLE meta (key TEXT PRIMARY KEY, value INTEGER NOT NULL) WITHOUT ROWID;
-- 원본 위치. t_ms가 rowid라 시간순 범위 읽기가 가장 빠르고, 같은 시각 중복은 여기서 막힌다.
CREATE TABLE fix (t_ms INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL, accuracy_m REAL NOT NULL);
-- 원본 주행 신호. kind 0 = 시작, 1 = 끝.
CREATE TABLE drive_event (t_ms INTEGER NOT NULL, kind INTEGER NOT NULL, PRIMARY KEY (t_ms, kind)) WITHOUT ROWID;
-- 계산된 주행. seg_start/seg_end = 끊긴 구간의 첫·마지막 점(튄 점 포함), start_ms/end_ms = 남은 점 기준.
-- path = 단순화한 경로(f64 위도·경도), stays = 주행 안 머문 곳(f64 위도·경도, i64 도착·떠남).
CREATE TABLE trip (
    seg_start INTEGER PRIMARY KEY,
    seg_end INTEGER NOT NULL,
    start_ms INTEGER NOT NULL,
    end_ms INTEGER NOT NULL,
    distance_m REAL NOT NULL,
    start_lat REAL NOT NULL,
    start_lon REAL NOT NULL,
    end_lat REAL NOT NULL,
    end_lon REAL NOT NULL,
    path BLOB NOT NULL,
    stays BLOB NOT NULL
);
CREATE INDEX trip_seg_end ON trip (seg_end);
-- 날짜별 요약 (day = 1970-01-01부터 센 현지 날짜 번호).
CREATE TABLE day_summary (
    day INTEGER PRIMARY KEY,
    trip_count INTEGER NOT NULL,
    stay_count INTEGER NOT NULL,
    distance_m REAL NOT NULL,
    drive_millis INTEGER NOT NULL,
    first_ms INTEGER NOT NULL,
    last_ms INTEGER NOT NULL
);
-- 아직 trip/day_summary에 반영하지 않은 원본 변경의 시간 범위 (한 줄).
CREATE TABLE dirty (id INTEGER PRIMARY KEY CHECK (id = 1), lo_ms INTEGER NOT NULL, hi_ms INTEGER NOT NULL);
";

fn migrate(conn: &mut Connection) -> Result<(), CoreError> {
    let version: i32 = conn.pragma_query_value(None, "user_version", |r| r.get(0))?;
    if version > SCHEMA_VERSION {
        return Err(CoreError::Db(format!("DB 버전 {version}이 앱({SCHEMA_VERSION})보다 새롭다")));
    }
    let tx = conn.transaction()?;
    if version < 1 {
        tx.execute_batch(SCHEMA_V1)?;
    }
    if version < 2 {
        tx.execute_batch(places::SCHEMA_V2)?;
        // stop_names = DaySummary.stop_names를 STOP_SEP로 이은 것
        tx.execute_batch("ALTER TABLE day_summary ADD COLUMN stop_names TEXT NOT NULL DEFAULT '';")?;
        // 버전 1에서 올릴 때: 계산된 주행을 다음 조회 때 전부 다시 계산해 지점을 장소에 잇는다.
        // 원본(fix, drive_event)은 건드리지 않는다. 새 DB면 점이 없어 아무 일도 없다.
        let range: (Option<i64>, Option<i64>) =
            tx.query_row("SELECT min(t_ms), max(t_ms) FROM fix", [], |r| Ok((r.get(0)?, r.get(1)?)))?;
        if let (Some(a), Some(b)) = range {
            mark_dirty(&tx, a, b)?;
        }
    }
    if version < 3 {
        // 기억(에피소드·AI 라벨·검색 색인). 기존 날은 다음 조회 때 에피소드를 만든다 (원본·주행은 그대로).
        tx.execute_batch(crate::memory::SCHEMA_V3)?;
        tx.execute("INSERT OR IGNORE INTO memory_dirty_day (day) SELECT day FROM day_summary", [])?;
    }
    if version < 4 {
        // 들른 곳 대표 사진 저장 (구글 Places)
        tx.execute_batch(crate::photos::SCHEMA_V4)?;
    }
    if version < 5 {
        // 커뮤니티 코스·여행 리뷰·하트 (기기 안, 2차 단계에서 서버와 맞춘다)
        tx.execute_batch(crate::community::SCHEMA_V5)?;
    }
    if version < 6 {
        // 검색 화면의 최근 검색어
        tx.execute_batch(crate::search_ui::SCHEMA_V6)?;
    }
    if version < 7 {
        // 숨긴 기록 · 저장한 곳 (사용자가 고친 기록). 기존 기록은 그대로 보인다.
        tx.execute_batch(crate::edits::SCHEMA_V7)?;
    }
    if version < 8 {
        // 받은 제휴 쿠폰 (쿠폰함)
        tx.execute_batch(crate::offers::SCHEMA_V8)?;
    }
    if version < 9 {
        // 자세한 리뷰(키워드 · 누구와 · 때 · 머문 시간 · 몇 번째 방문). 기존 줄은 기본값으로 읽힌다.
        crate::community::migrate_v9(&tx)?;
    }
    if version < 10 {
        // 드라이브 코스 = 들를 곳 없는 경치 길 (선·풍경·언제). 기존 코스 글은 여행 코스 후기로 그대로 남는다.
        tx.execute_batch(crate::community::drive::SCHEMA_V10)?;
    }
    if version < 11 {
        // 사용자가 직접 고친 하루 제목 (edits 모듈)
        tx.execute_batch(crate::edits::SCHEMA_V11)?;
    }
    if version < 12 {
        // 출발 시각으로 주행을 찾는 조회(기억 지도 · 기록 지우기)가 표 전체를 훑지 않게
        tx.execute_batch("CREATE INDEX IF NOT EXISTS trip_start ON trip (start_ms);")?;
    }
    // 다음 버전은 여기에: if version < 13 { ... }
    tx.pragma_update(None, "user_version", SCHEMA_VERSION)?;
    tx.commit()?;
    Ok(())
}

pub(crate) fn mark_dirty(conn: &Connection, lo: i64, hi: i64) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO dirty (id, lo_ms, hi_ms) VALUES (1, ?1, ?2)
         ON CONFLICT (id) DO UPDATE SET lo_ms = min(lo_ms, excluded.lo_ms), hi_ms = max(hi_ms, excluded.hi_ms)",
        params![lo, hi],
    )?;
    Ok(())
}

fn is_clean(f: &GeoFix) -> bool {
    f.t_ms > 0
        && f.lat.is_finite()
        && f.lon.is_finite()
        && (-90.0..=90.0).contains(&f.lat)
        && (-180.0..=180.0).contains(&f.lon)
        && !(f.lat == 0.0 && f.lon == 0.0)
        && f.accuracy_m <= MAX_ACCURACY_M // NaN이면 false
}

/// 시각 범위를 넓혀 가며 모은다.
#[derive(Default)]
struct Span(Option<(i64, i64)>);

impl Span {
    fn add(&mut self, t: i64) {
        self.0 = Some(match self.0 {
            Some((lo, hi)) => (lo.min(t), hi.max(t)),
            None => (t, t),
        });
    }

    fn range(&self) -> Option<(i64, i64)> {
        self.0
    }
}

/// trip 표 한 줄.
struct TripRow {
    seg_start: i64,
    seg_end: i64,
    start_ms: i64,
    end_ms: i64,
    distance_m: f64,
    start: Point,
    end: Point,
    path: Vec<Point>,
    stays: Vec<Stay>,
}

/// 끊긴 구간 하나로 주행을 만든다. 튄 점을 빼고 2개 미만이 남으면 주행이 아니다.
fn build_trip(seg: &[Fix], cfg: &TripConfig) -> Option<TripRow> {
    let kept = geo::drop_spikes(seg, cfg.max_speed_mps);
    let (first, last) = (*kept.first()?, *kept.last()?);
    if kept.len() < 2 {
        return None;
    }
    let pts: Vec<Point> = kept.iter().map(Fix::point).collect();
    let path = geo::simplify(&pts, PATH_TOLERANCE_M).into_iter().map(|i| pts[i]).collect();
    Some(TripRow {
        seg_start: seg[0].t_ms,
        seg_end: seg[seg.len() - 1].t_ms,
        start_ms: first.t_ms,
        end_ms: last.t_ms,
        distance_m: geo::path_length_m(&kept),
        start: first.point(),
        end: last.point(),
        path,
        stays: trip::detect_stays(&kept, cfg),
    })
}

fn encode_points(pts: &[Point]) -> Vec<u8> {
    let mut b = Vec::with_capacity(pts.len() * 16);
    for p in pts {
        b.extend_from_slice(&p.lat.to_le_bytes());
        b.extend_from_slice(&p.lon.to_le_bytes());
    }
    b
}

pub(crate) fn decode_points(b: &[u8]) -> Vec<Point> {
    b.as_chunks::<16>().0.iter().map(|c| Point { lat: f64_at(c, 0), lon: f64_at(c, 8) }).collect()
}

fn encode_stays(stays: &[Stay]) -> Vec<u8> {
    let mut b = Vec::with_capacity(stays.len() * 32);
    for s in stays {
        b.extend_from_slice(&s.center.lat.to_le_bytes());
        b.extend_from_slice(&s.center.lon.to_le_bytes());
        b.extend_from_slice(&s.arrive_ms.to_le_bytes());
        b.extend_from_slice(&s.leave_ms.to_le_bytes());
    }
    b
}

pub(crate) fn decode_stays(b: &[u8]) -> Vec<Stay> {
    b.as_chunks::<32>()
        .0
        .iter()
        .map(|c| Stay {
            center: Point { lat: f64_at(c, 0), lon: f64_at(c, 8) },
            arrive_ms: i64_at(c, 16),
            leave_ms: i64_at(c, 24),
        })
        .collect()
}

fn f64_at(c: &[u8], i: usize) -> f64 {
    f64::from_le_bytes(c[i..i + 8].try_into().expect("8 bytes"))
}

fn i64_at(c: &[u8], i: usize) -> i64 {
    i64::from_le_bytes(c[i..i + 8].try_into().expect("8 bytes"))
}

fn gp(p: Point) -> GeoPoint {
    GeoPoint { lat: p.lat, lon: p.lon }
}

fn stay_summary(s: &Stay, place: Option<PlaceLabel>) -> StaySummary {
    // order는 하루치를 다 모은 뒤 매긴다 (collect_days).
    StaySummary { center: gp(s.center), arrive_ms: s.arrive_ms, leave_ms: s.leave_ms, place, order: 0 }
}

/// day_summary.stop_names 칸의 구분자 (이름에 들어갈 일이 없는 제어 문자).
pub(crate) const STOP_SEP: char = '\u{1f}';

pub(crate) fn split_names(s: &str) -> Vec<String> {
    if s.is_empty() {
        Vec::new()
    } else {
        s.split(STOP_SEP).map(str::to_owned).collect()
    }
}

pub(crate) fn join_names(names: &[String]) -> String {
    let mut out = String::new();
    for (i, n) in names.iter().enumerate() {
        if i > 0 {
            out.push(STOP_SEP);
        }
        out.extend(n.chars().map(|c| if c == STOP_SEP { ' ' } else { c }));
    }
    out
}

/// `link_places`가 보는 바로 앞 주행. `place` = 도착점의 장소 (아직 안 이었으면 None).
struct PrevTrip {
    seg_start: i64,
    seg_end: i64,
    start_ms: i64,
    end_ms: i64,
    end: Point,
    place: Option<i64>,
}

impl PrevTrip {
    /// 도착점의 장소. 뒤에 주행이 있어 끝난 것이 확실하므로, 아직 안 이었으면 지금 잇는다.
    fn ensure_place(&mut self, tx: &Connection, span: &mut Span) -> rusqlite::Result<i64> {
        if let Some(id) = self.place {
            return Ok(id);
        }
        let id = places::assign_place(tx, self.end, false)?;
        places::link(tx, self.seg_start, REF_END, self.end_ms, id)?;
        span.add(self.start_ms);
        span.add(self.end_ms);
        self.place = Some(id);
        Ok(id)
    }
}

/// `seg_end` 이후에 주행 끝 신호가 있는지.
fn ended_after(conn: &Connection, seg_end: i64) -> rusqlite::Result<bool> {
    Ok(conn
        .prepare_cached("SELECT 1 FROM drive_event WHERE kind = ?1 AND t_ms >= ?2 LIMIT 1")?
        .query_row(params![KIND_END, seg_end], |_| Ok(()))
        .optional()?
        .is_some())
}

/// 하루의 주행(출발 시각순)·머문 곳(도착 시각순). 에피소드 계산용 (`day_records`).
pub(crate) type DayRecords = (Vec<TripSummary>, Vec<StaySummary>);

/// 하루치 모음 (계산 중간값).
#[derive(Default)]
struct DayAcc {
    trips: Vec<TripSummary>,
    stays: Vec<StaySummary>,
}

impl DayAcc {
    /// 기록이 없으면 개수·합계 0, first_ms = last_ms = 0.
    fn summary(&self, day: i64) -> DaySummary {
        let times = self
            .trips
            .iter()
            .flat_map(|t| [t.start_ms, t.end_ms])
            .chain(self.stays.iter().map(|s| s.arrive_ms));
        let (first_ms, last_ms) = times
            .fold(None, |acc: Option<(i64, i64)>, t| Some(acc.map_or((t, t), |(a, b)| (a.min(t), b.max(t)))))
            .unwrap_or((0, 0));
        let distance_m: f64 = self.trips.iter().map(|t| t.distance_m).sum();
        let named = self.stays.iter().filter(|s| s.place.as_ref().is_some_and(|p| !p.name.trim().is_empty())).count();
        DaySummary {
            day: format_day(day),
            trip_count: self.trips.len() as u32,
            stay_count: self.stays.len() as u32,
            replayable: is_replayable(named, distance_m),
            distance_m,
            drive_millis: self.trips.iter().map(|t| t.end_ms - t.start_ms).sum(),
            first_ms,
            last_ms,
            stop_names: self.stays.iter().filter_map(|s| Some(s.place.as_ref()?.name.clone())).take(3).collect(),
            title: None,
            tags: Vec::new(),
        }
    }

    /// 주행·머문 곳을 시간순 한 줄로 (같은 시각이면 주행 먼저).
    fn entries(&self) -> Vec<DayEntry> {
        let mut keyed: Vec<(i64, u8, DayEntry)> = self
            .trips
            .iter()
            .map(|t| (t.start_ms, 0, DayEntry::Trip { trip: t.clone() }))
            .chain(self.stays.iter().map(|s| (s.arrive_ms, 1, DayEntry::Stay { stay: s.clone() })))
            .collect();
        keyed.sort_by_key(|(t, k, _)| (*t, *k));
        keyed.into_iter().map(|(_, _, e)| e).collect()
    }
}

impl EodiStore {
    fn day_summaries_uncached(&self, limit: u32) -> Result<Vec<DaySummary>, CoreError> {
        let conn = self.read()?;
        let mut st = conn.prepare_cached(
            "SELECT s.day, s.trip_count, s.stay_count, s.distance_m, s.drive_millis, s.first_ms, s.last_ms, s.stop_names,
                    coalesce(u.title, m.title, m.rule_title), CASE WHEN m.title IS NULL THEN '' ELSE m.tags END
             FROM day_summary s LEFT JOIN day_memory m ON m.day = s.day LEFT JOIN day_user_title u ON u.day = s.day ORDER BY s.day DESC LIMIT ?1",
        )?;
        let rows = st.query_map([limit], |r| {
            let distance_m: f64 = r.get(3)?;
            let stop_names = split_names(&r.get::<_, String>(7)?);
            Ok(DaySummary {
                day: format_day(r.get(0)?),
                trip_count: r.get(1)?,
                stay_count: r.get(2)?,
                distance_m,
                drive_millis: r.get(4)?,
                first_ms: r.get(5)?,
                last_ms: r.get(6)?,
                replayable: is_replayable(stop_names.iter().filter(|n| !n.trim().is_empty()).count(), distance_m),
                stop_names,
                title: r.get(8)?,
                tags: split_names(&r.get::<_, Option<String>>(9)?.unwrap_or_default()),
            })
        })?;
        Ok(rows.collect::<Result<_, _>>()?)
    }

    /// 현지 시간대 (분, 한국 = 540).
    pub(crate) fn tz_minutes(&self) -> i32 {
        self.tz_offset_minutes
    }

    fn tz_ms(&self) -> i64 {
        self.tz_offset_minutes as i64 * 60_000
    }

    /// UTC 시각 → 현지 날짜 번호 (1970-01-01 = 0).
    pub(crate) fn local_day(&self, t_ms: i64) -> i64 {
        (t_ms + self.tz_ms()).div_euclid(DAY_MS)
    }

    /// 현지 날짜 번호 → 그날 0시의 UTC 시각.
    pub(crate) fn day_start_ms(&self, day: i64) -> i64 {
        day * DAY_MS - self.tz_ms()
    }

    /// 지난번에 연 시간대나 하루 요약 규칙([`DAY_RULES`])과 다르면 날짜별 요약을 전부 다시 만든다 (주행 자체는 그대로).
    fn apply_timezone(&self) -> Result<(), CoreError> {
        let mut conn = self.lock()?;
        let tx = conn.transaction()?;
        let stored: Option<i64> = tx
            .query_row("SELECT value FROM meta WHERE key = 'tz_offset_minutes'", [], |r| r.get(0))
            .optional()?;
        let rules: Option<i64> =
            tx.query_row("SELECT value FROM meta WHERE key = 'day_rules'", [], |r| r.get(0)).optional()?;
        if stored != Some(self.tz_offset_minutes as i64) || rules != Some(DAY_RULES) {
            tx.execute(
                "INSERT OR REPLACE INTO meta (key, value) VALUES ('tz_offset_minutes', ?1)",
                [self.tz_offset_minutes],
            )?;
            tx.execute("INSERT OR REPLACE INTO meta (key, value) VALUES ('day_rules', ?1)", [DAY_RULES])?;
            tx.execute("DELETE FROM day_summary", [])?;
            // 날짜 번호가 바뀌므로 지금 에피소드가 있는 날도 다시 만든다 (새 날짜는 refresh_days가 표시한다).
            tx.execute(
                "INSERT OR IGNORE INTO memory_dirty_day (day) SELECT day FROM episode UNION SELECT day FROM day_place_dwell",
                [],
            )?;
            let range: (Option<i64>, Option<i64>) =
                tx.query_row("SELECT min(start_ms), max(end_ms) FROM trip", [], |r| Ok((r.get(0)?, r.get(1)?)))?;
            if let (Some(t0), Some(t1)) = range {
                self.refresh_days(&tx, self.local_day(t0), self.local_day(t1))?;
            }
        }
        tx.commit()?;
        Ok(())
    }

    /// 밀린 원본 변경(`dirty`)을 trip·day_summary에 반영한다.
    ///
    /// 주행 경계는 이웃한 두 점(과 그 사이 신호)만 보고 정해지므로, 바뀐 범위 [a, b] 바깥의 경계는 그대로다.
    /// 그래서 a보다 앞에서 시작한 마지막 주행의 시작(lo)부터, b보다 뒤에서 끝나는 첫 주행의 끝(hi)까지만
    /// 지우고 다시 나누면 전체를 처음부터 다시 나눈 것과 같다.
    pub(crate) fn flush(&self, conn: &mut Connection) -> Result<(), CoreError> {
        let dirty: Option<(i64, i64)> =
            conn.query_row("SELECT lo_ms, hi_ms FROM dirty WHERE id = 1", [], |r| Ok((r.get(0)?, r.get(1)?))).optional()?;
        let Some((a, b)) = dirty else { return Ok(()) };
        let tx = conn.transaction()?;
        let lo: i64 = tx
            .query_row("SELECT max(seg_start) FROM trip WHERE seg_start < ?1", [a], |r| r.get::<_, Option<i64>>(0))?
            .unwrap_or(i64::MIN);
        let hi: i64 = tx
            .query_row("SELECT min(seg_end) FROM trip WHERE seg_end > ?1", [b], |r| r.get::<_, Option<i64>>(0))?
            .unwrap_or(i64::MAX);

        // 다시 계산할 날짜 범위: 지우는 주행, 새로 만든 주행, 그리고 창 바로 앞 주행의 끝
        // (그 뒤의 주행 사이 머문 곳은 창 안 첫 주행의 출발에 따라 달라진다).
        let mut touched = Span::default();
        let prev_end: Option<i64> = tx
            .query_row("SELECT end_ms FROM trip WHERE seg_start < ?1 ORDER BY seg_start DESC LIMIT 1", [lo], |r| r.get(0))
            .optional()?;
        if let Some(t) = prev_end {
            touched.add(t);
        }
        {
            let mut st = tx.prepare("SELECT start_ms, end_ms FROM trip WHERE seg_start BETWEEN ?1 AND ?2")?;
            let mut q = st.query([lo, hi])?;
            while let Some(r) = q.next()? {
                touched.add(r.get(0)?);
                touched.add(r.get(1)?);
            }
        }
        tx.execute("DELETE FROM trip WHERE seg_start BETWEEN ?1 AND ?2", [lo, hi])?;
        tx.execute("DELETE FROM trip_place WHERE seg_start BETWEEN ?1 AND ?2", [lo, hi])?;

        // 창 안의 신호 + 창 바로 앞 신호 하나 (창 첫 구간이 주차 중인지 판단하는 데 필요).
        let marks: Vec<DriveMark> = {
            let mut st = tx.prepare(
                "SELECT t_ms, kind FROM (SELECT t_ms, kind FROM drive_event WHERE t_ms < ?1 ORDER BY t_ms DESC LIMIT 1)
                 UNION ALL
                 SELECT t_ms, kind FROM drive_event WHERE t_ms BETWEEN ?1 AND ?2
                 ORDER BY t_ms",
            )?;
            let rows = st.query_map([lo, hi], |r| {
                let kind = if r.get::<_, i64>(1)? == KIND_START { MarkKind::Start } else { MarkKind::End };
                Ok(DriveMark { t_ms: r.get(0)?, kind })
            })?;
            rows.collect::<Result<_, _>>()?
        };
        // 주행 끝 신호 뒤(다음 시작 전)에 들어온 점들은 주차 중 점이라 주행으로 만들지 않는다.
        let build = |done: Vec<Fix>, built: &mut Vec<TripRow>| {
            if !trip::starts_parked(done[0].t_ms, &marks, &self.cfg) {
                built.extend(build_trip(&done, &self.cfg));
            }
        };

        // 점은 시간순으로 흘려 읽으며 구간이 끊길 때마다 주행을 만든다 (한 번에 주행 하나만 메모리에).
        let mut built = Vec::new();
        {
            let mut st = tx.prepare("SELECT t_ms, lat, lon FROM fix WHERE t_ms BETWEEN ?1 AND ?2 ORDER BY t_ms")?;
            let mut q = st.query([lo, hi])?;
            let mut seg = Segmenter::new(&marks, self.cfg);
            while let Some(r) = q.next()? {
                let f = Fix { t_ms: r.get(0)?, lat: r.get(1)?, lon: r.get(2)? };
                if let Some(done) = seg.push(f) {
                    build(done, &mut built);
                }
            }
            if let Some(done) = seg.finish() {
                build(done, &mut built);
            }
        }
        {
            let mut st = tx.prepare(
                "INSERT INTO trip (seg_start, seg_end, start_ms, end_ms, distance_m,
                                   start_lat, start_lon, end_lat, end_lon, path, stays)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
            )?;
            for t in &built {
                st.execute(params![
                    t.seg_start,
                    t.seg_end,
                    t.start_ms,
                    t.end_ms,
                    t.distance_m,
                    t.start.lat,
                    t.start.lon,
                    t.end.lat,
                    t.end.lon,
                    encode_points(&t.path),
                    encode_stays(&t.stays),
                ])?;
                touched.add(t.start_ms);
                touched.add(t.end_ms);
            }
        }
        // 지점을 장소에 잇고, 그 주행 근처 시각의 내비 힌트를 다시 짝짓는다.
        let renamed = match self.link_places(&tx, &built, lo, hi)? {
            Some((t0, t1)) => places::apply_hints_between(&tx, t0, t1.saturating_add(places::HINT_AFTER_ARRIVAL_MS))?,
            None => Vec::new(),
        };
        tx.execute("DELETE FROM dirty", [])?;
        if let Some((t0, t1)) = touched.range() {
            self.refresh_days(&tx, self.local_day(t0), self.local_day(t1))?;
        }
        // 힌트로 이름이 바뀐 장소는 다른 날에도 나올 수 있다 (그날들의 stop_names).
        self.refresh_place_days(&tx, &renamed)?;
        tx.commit()?;
        Ok(())
    }

    /// 다시 만든 주행(`built`, 창 [lo, hi] 안)의 지점을 장소에 잇는다 (규칙은 `places` 모듈 설명).
    /// 창 바로 앞 주행의 도착점과 창 바로 뒤 주행의 출발점도 창 안 주행에 따라 달라지므로 같이 고친다.
    /// 반환: 잇기가 바뀐 주행들의 시각 범위 (내비 힌트를 다시 짝지을 범위).
    fn link_places(
        &self,
        tx: &Connection,
        built: &[TripRow],
        lo: i64,
        hi: i64,
    ) -> Result<Option<(i64, i64)>, CoreError> {
        let mut span = Span::default();
        let mut prev: Option<PrevTrip> = tx
            .query_row(
                "SELECT seg_start, seg_end, start_ms, end_ms, end_lat, end_lon FROM trip
                 WHERE seg_start < ?1 ORDER BY seg_start DESC LIMIT 1",
                [lo],
                |r| {
                    Ok(PrevTrip {
                        seg_start: r.get(0)?,
                        seg_end: r.get(1)?,
                        start_ms: r.get(2)?,
                        end_ms: r.get(3)?,
                        end: Point { lat: r.get(4)?, lon: r.get(5)? },
                        place: None,
                    })
                },
            )
            .optional()?;
        if let Some(p) = prev.as_mut() {
            p.place = places::linked(tx, p.seg_start, REF_END, p.end_ms)?;
        }
        let next: Option<(i64, Point, i64)> = tx
            .query_row(
                "SELECT seg_start, start_lat, start_lon, start_ms FROM trip WHERE seg_start > ?1 ORDER BY seg_start LIMIT 1",
                [hi],
                |r| Ok((r.get(0)?, Point { lat: r.get(1)?, lon: r.get(2)? }, r.get(3)?)),
            )
            .optional()?;

        for (k, t) in built.iter().enumerate() {
            // 출발점: 앞 주행 끝 자리에 머물다 떠났으면 그 머문 곳의 장소, 아니면 가까운 장소.
            let start_place = match prev.as_mut() {
                Some(pv) => {
                    let pid = pv.ensure_place(tx, &mut span)?;
                    if trip::between_stay(pv.end, pv.end_ms, t.start, t.start_ms, &self.cfg).is_some() {
                        places::mark_stay(tx, pid)?;
                        pid
                    } else {
                        places::assign_place(tx, t.start, false)?
                    }
                }
                None => places::assign_place(tx, t.start, false)?,
            };
            places::link(tx, t.seg_start, REF_START, t.start_ms, start_place)?;
            // 끝난 것이 확실한 주행만 도착점·마지막 머문 곳을 잇는다 (달리는 중이면 끝 자리가 계속 움직인다).
            let settled = k + 1 < built.len() || next.is_some() || ended_after(tx, t.seg_end)?;
            for s in &t.stays {
                if settled || s.leave_ms < t.end_ms {
                    let id = places::assign_place(tx, s.center, true)?;
                    places::link(tx, t.seg_start, REF_STAY, s.arrive_ms, id)?;
                }
            }
            let place = if settled {
                let id = places::assign_place(tx, t.end, false)?;
                places::link(tx, t.seg_start, REF_END, t.end_ms, id)?;
                Some(id)
            } else {
                None
            };
            span.add(t.start_ms);
            span.add(t.end_ms);
            prev = Some(PrevTrip {
                seg_start: t.seg_start,
                seg_end: t.seg_end,
                start_ms: t.start_ms,
                end_ms: t.end_ms,
                end: t.end,
                place,
            });
        }

        match (next, prev.as_mut()) {
            // 창 뒤 주행의 출발점: 창 안 마지막 주행(또는 창 앞 주행)과의 관계가 바뀌었을 수 있다.
            (Some((seg, start, start_ms)), pv) => {
                let id = match pv {
                    Some(pv) => {
                        let pid = pv.ensure_place(tx, &mut span)?;
                        if trip::between_stay(pv.end, pv.end_ms, start, start_ms, &self.cfg).is_some() {
                            places::mark_stay(tx, pid)?;
                            pid
                        } else {
                            places::assign_place(tx, start, false)?
                        }
                    }
                    None => places::assign_place(tx, start, false)?,
                };
                places::link(tx, seg, REF_START, start_ms, id)?;
            }
            // 전체 마지막 주행 뒤에 끝 신호가 있으면 그 도착점은 "지금 머무는 중"인 머문 곳이다.
            (None, Some(pv)) => {
                if ended_after(tx, pv.seg_end)? {
                    let pid = pv.ensure_place(tx, &mut span)?;
                    places::mark_stay(tx, pid)?;
                }
            }
            (None, None) => {}
        }
        Ok(span.range())
    }

    /// 이름이 바뀐 장소들이 나오는 날의 요약(stop_names)을 다시 만든다.
    /// 장소에 이어진 지점의 첫 날~마지막 날을 한 번에 다시 만든다 (이름은 장소마다 보통 한 번만 바뀐다).
    pub(crate) fn refresh_place_days(&self, conn: &Connection, ids: &[i64]) -> Result<(), CoreError> {
        for &id in ids {
            let range: (Option<i64>, Option<i64>) = conn
                .prepare_cached("SELECT min(t_ms), max(t_ms) FROM trip_place WHERE place_id = ?1")?
                .query_row([id], |r| Ok((r.get(0)?, r.get(1)?)))?;
            if let (Some(t0), Some(t1)) = range {
                self.refresh_days(conn, self.local_day(t0), self.local_day(t1))?;
            }
        }
        Ok(())
    }

    /// 현지 날짜 d0..=d1의 day_summary 줄을 trip 표에서 다시 만든다.
    /// 그날들의 에피소드도 다음 조회 때 다시 만들도록 표시한다 (`memory_dirty_day`).
    pub(crate) fn refresh_days(&self, conn: &Connection, d0: i64, d1: i64) -> Result<(), CoreError> {
        let days = self.collect_days(conn, d0, d1, false)?;
        {
            let mut st = conn.prepare_cached("INSERT OR IGNORE INTO memory_dirty_day (day) VALUES (?1)")?;
            for d in d0..=d1 {
                st.execute([d])?;
            }
        }
        conn.execute("DELETE FROM day_summary WHERE day BETWEEN ?1 AND ?2", [d0, d1])?;
        let mut st = conn.prepare_cached(
            "INSERT INTO day_summary (day, trip_count, stay_count, distance_m, drive_millis, first_ms, last_ms, stop_names)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
        )?;
        for (d, acc) in &days {
            let s = acc.summary(*d);
            st.execute(params![
                d,
                s.trip_count,
                s.stay_count,
                s.distance_m,
                s.drive_millis,
                s.first_ms,
                s.last_ms,
                join_names(&s.stop_names)
            ])?;
        }
        Ok(())
    }

    /// 밀린 주행 계산(`flush`)과 밀린 에피소드 계산(`memory::flush_memory`)을 차례로 반영한다.
    pub(crate) fn flush_all(&self, conn: &mut Connection) -> Result<(), CoreError> {
        self.flush(conn)?;
        crate::memory::flush_memory(self, conn, false)
    }

    /// 현지 날짜 d0..=d1의 주행(경로 없이)·머문 곳을 날짜별로 (에피소드 계산용). 기록 없는 날은 빠진다.
    pub(crate) fn day_records(
        &self,
        conn: &Connection,
        d0: i64,
        d1: i64,
    ) -> Result<BTreeMap<i64, DayRecords>, CoreError> {
        Ok(self.collect_days(conn, d0, d1, false)?.into_iter().map(|(d, a)| (d, (a.trips, a.stays))).collect())
    }

    /// 현지 날짜 d0..=d1에 속한 주행(출발 날짜)과 머문 곳(도착 날짜)을 날짜별로 모은다. 기록 없는 날은 빠진다.
    /// 숨긴 주행·머문 곳(`edits::Hidden`)은 뺀다.
    fn collect_days(
        &self,
        conn: &Connection,
        d0: i64,
        d1: i64,
        with_path: bool,
    ) -> Result<BTreeMap<i64, DayAcc>, CoreError> {
        let (ds, de) = (self.day_start_ms(d0), self.day_start_ms(d1 + 1));
        // 범위 직전 주행도 읽는다: 자정을 넘긴 주행 안의 머문 곳, 그 뒤 주행 사이 머문 곳이 범위에 들 수 있다.
        let from: i64 = conn
            .query_row("SELECT max(seg_start) FROM trip WHERE seg_start < ?1", [ds], |r| r.get::<_, Option<i64>>(0))?
            .unwrap_or(ds);
        let path_col = if with_path { "path" } else { "x''" };
        let mut trips: Vec<TripRow> = {
            let sql = format!(
                "SELECT seg_start, seg_end, start_ms, end_ms, distance_m, start_lat, start_lon, end_lat, end_lon,
                        {path_col}, stays
                 FROM trip WHERE seg_start >= ?1 AND seg_start < ?2 ORDER BY seg_start"
            );
            let mut st = conn.prepare_cached(&sql)?;
            let rows = st.query_map([from, de], |r| {
                Ok(TripRow {
                    seg_start: r.get(0)?,
                    seg_end: r.get(1)?,
                    start_ms: r.get(2)?,
                    end_ms: r.get(3)?,
                    distance_m: r.get(4)?,
                    start: Point { lat: r.get(5)?, lon: r.get(6)? },
                    end: Point { lat: r.get(7)?, lon: r.get(8)? },
                    path: decode_points(&r.get::<_, Vec<u8>>(9)?),
                    stays: decode_stays(&r.get::<_, Vec<u8>>(10)?),
                })
            })?;
            rows.collect::<Result<_, _>>()?
        };
        let next: Option<(Point, i64)> = conn
            .query_row(
                "SELECT start_lat, start_lon, start_ms FROM trip WHERE seg_start >= ?1 ORDER BY seg_start LIMIT 1",
                [de],
                |r| Ok((Point { lat: r.get(0)?, lon: r.get(1)? }, r.get(2)?)),
            )
            .optional()?;
        // 전체 마지막 주행 뒤에 끝 신호가 있으면 "지금 머무는 중"
        let ended = match (next, trips.last()) {
            (None, Some(last)) => conn
                .query_row(
                    "SELECT 1 FROM drive_event WHERE kind = ?1 AND t_ms >= ?2 LIMIT 1",
                    params![KIND_END, last.seg_end],
                    |_| Ok(()),
                )
                .optional()?
                .is_some(),
            _ => false,
        };

        // 장소 이름 (쿼리 한 번으로 그 주행들의 이름 전부). 요약의 stop_names에도 쓴다.
        let labels = places::labels_between(conn, from, de)?;
        let label = |seg: i64, kind: i64, t: i64| labels.get(&(seg, kind, t)).cloned();
        let hidden = crate::edits::Hidden::load(conn)?;

        // 주차 중 GPS 흔들림으로 생긴 잡음 주행은 빼고 본다 (trip::real_trips). 그 앞뒤 사이 주차는 아래에서 머문 곳 하나로 잡힌다.
        let real = trip::real_trips(&trips.iter().map(|t| t.distance_m).collect::<Vec<_>>(), &self.cfg);
        // 마지막 진짜 주행 뒤에 잡음 주행만 남았으면 차는 그 자리에 서 있는 중이다.
        let trailing_jitter = real.last().is_some_and(|&i| i + 1 < trips.len());

        let mut days: BTreeMap<i64, DayAcc> = BTreeMap::new();
        let in_range = |t: i64| Some(self.local_day(t)).filter(|d| (d0..=d1).contains(d));
        let trip_day = |t: i64| in_range(t).filter(|_| !hidden.trip(t));
        let stay_day = |t: i64| in_range(t).filter(|_| !hidden.stay(t));
        for (k, &i) in real.iter().enumerate() {
            let next_start = real.get(k + 1).map(|&n| (trips[n].start, trips[n].start_ms)).or(next);
            let t = &mut trips[i];
            let end_place = label(t.seg_start, REF_END, t.end_ms);
            if let Some(d) = trip_day(t.start_ms) {
                days.entry(d).or_default().trips.push(TripSummary {
                    start_ms: t.start_ms,
                    end_ms: t.end_ms,
                    distance_m: t.distance_m,
                    start: gp(t.start),
                    end: gp(t.end),
                    path: std::mem::take(&mut t.path).into_iter().map(gp).collect(),
                    start_place: label(t.seg_start, REF_START, t.start_ms),
                    end_place: end_place.clone(),
                });
            }
            // 주행 안 머문 곳(드라이브스루 등)은 그 주행의 일부라 주행을 숨기면 같이 숨는다.
            let trip_hidden = hidden.trip(t.start_ms);
            for s in t.stays.iter().filter(|_| !trip_hidden) {
                if let Some(d) = stay_day(s.arrive_ms) {
                    days.entry(d).or_default().stays.push(stay_summary(s, label(t.seg_start, REF_STAY, s.arrive_ms)));
                }
            }
            // 주행 사이 머문 곳·지금 머무는 중인 곳은 중심이 이 주행의 도착점이라 도착점의 장소 이름을 쓴다.
            let after = match next_start {
                Some((p, leave)) => trip::between_stay(t.end, t.end_ms, p, leave, &self.cfg),
                None if ended || trailing_jitter => Some(Stay { center: t.end, arrive_ms: t.end_ms, leave_ms: t.end_ms }),
                None => None,
            };
            if let Some(s) = after {
                if let Some(d) = stay_day(s.arrive_ms) {
                    days.entry(d).or_default().stays.push(stay_summary(&s, end_place));
                }
            }
        }
        // 머문 곳은 도착 시각순으로 쌓였다 → 하루 안 순서를 매긴다.
        for acc in days.values_mut() {
            for (i, s) in acc.stays.iter_mut().enumerate() {
                s.order = i as u32 + 1;
            }
        }
        Ok(days)
    }
}

/// 현지 날짜 번호 → "YYYY-MM-DD" (그레고리력, Howard Hinnant의 civil_from_days).
pub(crate) fn format_day(day: i64) -> String {
    let z = day + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097);
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + era * 400 + i64::from(m <= 2);
    format!("{y:04}-{m:02}-{d:02}")
}

/// 연·월·일 → 날짜 번호 (days_from_civil).
pub(crate) fn days_from_civil(y: i64, m: i64, d: i64) -> i64 {
    let y = if m <= 2 { y - 1 } else { y };
    let era = y.div_euclid(400);
    let yoe = y - era * 400;
    let mp = (m + 9) % 12;
    let doy = (153 * mp + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe - 719_468
}

/// "YYYY-MM-DD" → 날짜 번호. 형식이 틀리거나 없는 날짜(2월 30일 등)면 None.
pub(crate) fn parse_day(s: &str) -> Option<i64> {
    let b = s.as_bytes();
    if b.len() != 10 || b[4] != b'-' || b[7] != b'-' {
        return None;
    }
    let num = |r: std::ops::Range<usize>| s.get(r)?.parse::<i64>().ok();
    let (y, m, d) = (num(0..4)?, num(5..7)?, num(8..10)?);
    if !(1..=12).contains(&m) || !(1..=31).contains(&d) {
        return None;
    }
    let day = days_from_civil(y, m, d);
    (format_day(day) == s).then_some(day)
}

mod conn;
pub(crate) use conn::Domain;

#[cfg(test)]
mod tests;

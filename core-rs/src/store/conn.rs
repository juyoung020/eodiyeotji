//! 연결 묶음과 계산 결과 캐시 (`EodiStore`의 동시성 모델).
//!
//! - **쓰기 연결 하나**(`Mutex`): 위치 저장·주행 신호·사용자 편집·밀린 계산(`flush_all`)은 여기서 한 번에 하나씩.
//! - **읽기 연결 여러 개**(읽기 전용, WAL): 화면 조회는 각자 연결을 빌려 동시에 돈다. 쓰기(기록 중 위치 묶음 저장)가
//!   진행 중이어도 기다리지 않고, 느린 조회(결산·기억 지도)가 다른 조회를 막지도 않는다.
//!   빌린 동안은 읽기 트랜잭션 하나라 조회 한 번은 같은 시점의 데이터를 본다.
//! - **밀린 계산 표시**(`pending`): 쓰기 연결을 쓴 뒤에는 trip·에피소드 계산이 밀렸을 수 있다. 다음 조회가
//!   쓰기 연결에서 한 번 반영(`flush_all`)한 뒤 읽는다. 밀린 게 없으면 쓰기 연결을 건드리지 않는다.
//! - **세대 번호**: 기록(records)·커뮤니티(community) 데이터가 바뀔 때마다 오른다. 캐시는 계산할 때의 세대가
//!   지금과 같을 때만 그대로 돌려준다 → 데이터가 바뀌기 전까지 같은 조회는 O(1).

use std::any::Any;
use std::collections::HashMap;
use std::ops::{Deref, DerefMut};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard};

use rusqlite::{Connection, OpenFlags};

use super::{CoreError, EodiStore};

/// 쉬는 읽기 연결을 이만큼까지 남겨 둔다 (더 많이 동시에 읽으면 그때 열고, 돌려줄 때 닫는다).
const MAX_IDLE_READERS: usize = 4;
/// 캐시 항목 수 상한 (넘으면 비운다. 항목은 화면 하나가 쓰는 결과 하나라 몇십 개면 충분하다).
const MAX_CACHE_ENTRIES: usize = 64;

/// 캐시가 따라가는 데이터 갈래.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Domain {
    /// 기록(위치·주행·머문 곳·에피소드·장소·편집). 커뮤니티 결과와 섞인 조회도 이 갈래 + 커뮤니티를 같이 본다.
    Records,
    /// 커뮤니티(코스·리뷰·게시판·하트)만 보는 조회.
    Community,
    /// 둘 다 보는 조회 (리뷰 피드의 "내 리뷰" 표시 등).
    Both,
}

/// (기록 세대, 커뮤니티 세대, 결과)
type CacheEntry = (u64, u64, Arc<dyn Any + Send + Sync>);

pub(crate) struct Pool {
    path: String,
    idle: Mutex<Vec<Connection>>,
    /// 쓰기 연결을 쓴 뒤 아직 밀린 계산을 반영하지 않았을 수 있음.
    pending: AtomicBool,
    records_gen: AtomicU64,
    community_gen: AtomicU64,
    cache: Mutex<HashMap<String, CacheEntry>>,
}

impl Pool {
    pub(crate) fn new(path: &str) -> Self {
        Self {
            path: path.to_owned(),
            idle: Mutex::new(Vec::new()),
            pending: AtomicBool::new(true),
            records_gen: AtomicU64::new(0),
            community_gen: AtomicU64::new(0),
            cache: Mutex::new(HashMap::new()),
        }
    }

    fn take(&self) -> Result<Connection, CoreError> {
        if let Some(c) = self.idle.lock().map_err(poisoned)?.pop() {
            return Ok(c);
        }
        let c = Connection::open_with_flags(
            &self.path,
            OpenFlags::SQLITE_OPEN_READ_ONLY | OpenFlags::SQLITE_OPEN_NO_MUTEX | OpenFlags::SQLITE_OPEN_URI,
        )?;
        // 쓰기 연결이 체크포인트 중이면 잠깐 기다린다 (WAL에서는 드물다)
        c.busy_timeout(std::time::Duration::from_secs(5))?;
        c.set_prepared_statement_cache_capacity(64);
        Ok(c)
    }

    fn give_back(&self, c: Connection) {
        if let Ok(mut idle) = self.idle.lock() {
            if idle.len() < MAX_IDLE_READERS {
                idle.push(c);
            }
        }
    }

    fn bump(&self, kind: WriteKind) {
        if kind == WriteKind::Side {
            return;
        }
        if kind == WriteKind::Any {
            self.records_gen.fetch_add(1, Ordering::AcqRel);
            self.pending.store(true, Ordering::Release);
        }
        self.community_gen.fetch_add(1, Ordering::AcqRel);
    }

    fn gens(&self, d: Domain) -> (u64, u64) {
        let r = self.records_gen.load(Ordering::Acquire);
        let c = self.community_gen.load(Ordering::Acquire);
        match d {
            Domain::Records => (r, 0),
            Domain::Community => (0, c),
            Domain::Both => (r, c),
        }
    }
}

fn poisoned<T>(e: std::sync::PoisonError<T>) -> CoreError {
    CoreError::Db(e.to_string())
}

/// 쓰기 연결이 무엇을 바꾸나 (놓을 때 어느 세대를 올릴지).
#[derive(Clone, Copy, PartialEq, Eq)]
enum WriteKind {
    /// 무엇이든 (기록·편집·장소 이름…). 모든 캐시가 낡고, 밀린 계산이 생겼을 수 있다.
    Any,
    /// 커뮤니티 표만.
    Community,
    /// 어떤 캐시도 읽지 않는 곁가지 표만 (최근 검색어·받은 쿠폰·사진 캐시·회상 안내 기록).
    Side,
}

/// 쓰기 연결. 놓을 때 세대 번호를 올리고 밀린 계산 표시를 켠다 (무엇을 썼는지 몰라도 안전한 쪽으로).
pub(crate) struct Writer<'a> {
    guard: MutexGuard<'a, Connection>,
    pool: &'a Pool,
    kind: WriteKind,
}

impl Deref for Writer<'_> {
    type Target = Connection;
    fn deref(&self) -> &Connection {
        &self.guard
    }
}

impl DerefMut for Writer<'_> {
    fn deref_mut(&mut self) -> &mut Connection {
        &mut self.guard
    }
}

impl Drop for Writer<'_> {
    fn drop(&mut self) {
        self.pool.bump(self.kind);
    }
}

/// 빌린 읽기 연결 (읽기 트랜잭션 안). 놓으면 트랜잭션을 끝내고 묶음에 돌려준다.
pub(crate) struct Reader<'a> {
    conn: Option<Connection>,
    pool: &'a Pool,
}

impl Deref for Reader<'_> {
    type Target = Connection;
    fn deref(&self) -> &Connection {
        self.conn.as_ref().expect("reader connection")
    }
}

impl Drop for Reader<'_> {
    fn drop(&mut self) {
        if let Some(c) = self.conn.take() {
            if c.execute_batch("COMMIT").is_ok() || c.is_autocommit() {
                self.pool.give_back(c);
            }
        }
    }
}

impl EodiStore {
    /// 쓰기 연결 (기록·편집 등 무엇이든 쓸 때). 놓으면 기록·커뮤니티 캐시가 모두 낡은 것이 된다.
    pub(crate) fn lock(&self) -> Result<Writer<'_>, CoreError> {
        Ok(Writer { guard: self.conn.lock().map_err(poisoned)?, pool: &self.pool, kind: WriteKind::Any })
    }

    /// 커뮤니티 표(코스·리뷰·게시판·하트)만 쓸 때의 쓰기 연결. 기록 캐시와 밀린 계산 표시는 그대로 둔다.
    pub(crate) fn lock_community(&self) -> Result<Writer<'_>, CoreError> {
        Ok(Writer { guard: self.conn.lock().map_err(poisoned)?, pool: &self.pool, kind: WriteKind::Community })
    }

    /// 곁가지 표(최근 검색어 `search_recent`·받은 쿠폰 `claimed_coupon`·사진 `place_photo`·회상 안내 기록 `meta`)만 쓸 때.
    /// 세대를 올리지 않는다 → 이 표를 읽는 조회는 캐시(`cached`)에 넣지 않는다.
    pub(crate) fn lock_side(&self) -> Result<Writer<'_>, CoreError> {
        Ok(Writer { guard: self.conn.lock().map_err(poisoned)?, pool: &self.pool, kind: WriteKind::Side })
    }

    /// 밀린 계산을 반영한 뒤 읽기 연결을 빌린다 (예전 `lock()` + `flush_all()` 자리).
    pub(crate) fn read(&self) -> Result<Reader<'_>, CoreError> {
        self.ensure_flushed()?;
        self.snapshot()
    }

    /// 쓰기 뒤 밀린 계산이 있으면 쓰기 연결에서 한 번 반영한다. 없으면 아무 잠금도 잡지 않는다.
    pub(crate) fn ensure_flushed(&self) -> Result<(), CoreError> {
        if self.pool.pending.load(Ordering::Acquire) {
            let mut w = self.conn.lock().map_err(poisoned)?;
            // 표시는 반영을 **마친 뒤에** 끈다: 반영 중에 들어온 다른 조회는 표시를 보고 잠금에서 기다린다
            // (먼저 끄면 그 조회가 반영 전 데이터를 읽어 지금 세대로 캐시에 넣는다). 쓰기는 같은 잠금 안에서만 표시를 켠다.
            if self.pool.pending.load(Ordering::Acquire) {
                self.flush_all(&mut w)?;
                self.pool.records_gen.fetch_add(1, Ordering::AcqRel);
                self.pool.pending.store(false, Ordering::Release);
            }
        }
        Ok(())
    }

    /// 밀린 계산 반영 없이 읽기 연결을 빌린다 (커뮤니티 표·최근 검색어처럼 기록 계산과 상관없는 조회).
    pub(crate) fn snapshot(&self) -> Result<Reader<'_>, CoreError> {
        let c = self.pool.take()?;
        c.execute_batch("BEGIN")?;
        Ok(Reader { conn: Some(c), pool: &self.pool })
    }

    /// `key` 조회 결과를 `domain` 데이터가 바뀔 때까지 기억해 둔다. 바뀌었으면 `f`로 새로 계산한다.
    /// 세대는 계산 **전에** 읽는다 → 계산 중에 데이터가 바뀌면 다음 호출이 다시 계산한다 (낡은 결과를 새 것으로 착각하지 않는다).
    pub(crate) fn cached<T, F>(&self, domain: Domain, key: String, f: F) -> Result<T, CoreError>
    where
        T: Clone + Send + Sync + 'static,
        F: FnOnce() -> Result<T, CoreError>,
    {
        // 기록 갈래면 밀린 계산을 먼저 반영해 세대가 확정되게 한다 (반영 자체는 세대를 올리지 않는다)
        if domain != Domain::Community {
            self.ensure_flushed()?;
        }
        let (r, c) = self.pool.gens(domain);
        if let Some((cr, cc, v)) = self.pool.cache.lock().map_err(poisoned)?.get(&key) {
            if (*cr, *cc) == (r, c) {
                if let Some(v) = v.downcast_ref::<T>() {
                    return Ok(v.clone());
                }
            }
        }
        let v = f()?;
        let mut cache = self.pool.cache.lock().map_err(poisoned)?;
        if cache.len() >= MAX_CACHE_ENTRIES {
            cache.clear();
        }
        cache.insert(key, (r, c, Arc::new(v.clone())));
        Ok(v)
    }
}

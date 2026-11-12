//! 거래처 목록 — 근무 시간이 사람마다 다르고, 영업직처럼 회사에 오래 머물지 않는 사람은 "평일 07~20시 · 회사에서 멀리"
//! 규칙(`biztrip.rs`)만으로 업무 주행을 가릴 수 없다. 그래서 사용자가 거래처를 정해 두면, **거래처에 들른 주행은
//! 요일 · 시각 · 거리와 상관없이 출장**으로 묶는다 (주말 방문 · 야간 근무 · 회사 근처 고객사도 잡힌다).
//!
//! # 거래처 ([EodiStore::set_biz_client] · [EodiStore::biz_clients])
//! - 한 곳 = 이름 · 주소 · 위치. 같은 곳 = 이름과 주소가 같거나, 위치가 [CLIENT_RADIUS_M](150m) 안.
//!   (이름 없는 들른 곳도 위치로 맞는다.)
//! - 목록에는 최근 [RECENT_DAYS](90)일에 들른 날 수와 마지막으로 들른 날을 같이 준다 (자주 간 순).
//!
//! # 권하기 ([EodiStore::biz_client_suggestions])
//! 최근 90일의 **평일**에 [SUGGEST_MIN_DAYS](3)일 이상 들른 곳 중 집 · 회사가 아니고 이름이 있으며 아직 거래처가
//! 아닌 곳을 자주 간 순으로 [SUGGEST_MAX](5)곳까지 권한다 → "여기를 거래처로 할까요?"
//!
//! # 저장 (DB 구조 버전 20)
//! `biz_client(key, name, address, lat, lon, added_ms)`: key = "이름|주소" (이름이 없으면 "@위도,경도").

use std::collections::{BTreeMap, BTreeSet};

use rusqlite::{params, Connection};

use crate::geo::{haversine_m, Point};
use crate::memory::StopFact;
use crate::recap::{anchors, episodes_between, notable, place_key};
use crate::store::{format_day, CoreError, EodiStore};

pub(crate) const SCHEMA_V20: &str = "
CREATE TABLE IF NOT EXISTS biz_client (
    key TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL,
    lat REAL NOT NULL, lon REAL NOT NULL, added_ms INTEGER NOT NULL
) WITHOUT ROWID;
";

/// 옛 버전 DB를 흉내 내는 테스트용.
#[cfg(test)]
pub(crate) const DROP_V20: &str = "DROP TABLE biz_client;";

/// 거래처 위치에서 이 거리(m) 안에 들르면 그 거래처에 간 것으로 본다.
pub const CLIENT_RADIUS_M: f64 = 150.0;
/// 들른 날 수를 세고 권할 곳을 찾는 기간 (일).
pub const RECENT_DAYS: i64 = 90;
/// 평일에 이만큼(일) 들른 곳을 거래처로 권한다.
pub const SUGGEST_MIN_DAYS: usize = 3;
/// 권하는 곳 수 상한.
pub const SUGGEST_MAX: usize = 5;

/// 거래처 한 곳 (또는 권하는 곳).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BizClient {
    /// 같은 곳 표시. 뺄 때 이름 · 주소 · 위치와 같이 넘기지 않아도 되게 그대로 쓴다.
    pub key: String,
    pub name: String,
    pub address: String,
    pub lat: f64,
    pub lon: f64,
    /// 최근 90일에 들른 날 수
    pub visit_days: u32,
    /// 마지막으로 들른 날 "YYYY-MM-DD" (최근 90일에 없으면 빈 문자열)
    pub last_day: String,
}

/// 저장된 거래처 한 줄 (출장 묶기에서 맞춰 볼 때 쓴다).
pub(crate) struct ClientSpot {
    pub key: String,
    pub name: String,
    pub address: String,
    pub at: Point,
}

fn key_of(name: &str, address: &str, lat: f64, lon: f64) -> String {
    if name.trim().is_empty() {
        format!("@{lat:.5},{lon:.5}")
    } else {
        format!("{}|{}", name.trim(), address.trim())
    }
}

pub(crate) fn client_spots(conn: &Connection) -> rusqlite::Result<Vec<ClientSpot>> {
    let mut st = conn.prepare_cached("SELECT key, name, address, lat, lon FROM biz_client ORDER BY added_ms")?;
    let rows = st.query_map([], |r| {
        Ok(ClientSpot { key: r.get(0)?, name: r.get(1)?, address: r.get(2)?, at: Point { lat: r.get(3)?, lon: r.get(4)? } })
    })?;
    rows.collect()
}

/// 이 들른 곳이 거래처인가 (같은 이름 · 주소이거나 150m 안). 맞으면 그 거래처.
pub(crate) fn client_at<'a>(clients: &'a [ClientSpot], s: &StopFact) -> Option<&'a ClientSpot> {
    let key = place_key(s);
    clients.iter().find(|c| (!s.name.trim().is_empty() && c.key == key) || haversine_m(c.at, Point { lat: s.lat, lon: s.lon }) <= CLIENT_RADIUS_M)
}

#[uniffi::export]
impl EodiStore {
    /// 이곳을 거래처로 정하거나(`on`) 거래처에서 뺀다. 뺄 때는 같은 이름 · 주소이거나 150m 안의 거래처를 모두 뺀다.
    pub fn set_biz_client(&self, name: String, address: String, lat: f64, lon: f64, on: bool, now_ms: i64) -> Result<(), CoreError> {
        if !(-90.0..=90.0).contains(&lat) || !(-180.0..=180.0).contains(&lon) {
            return Err(CoreError::InvalidInput(format!("위치 {lat}, {lon}")));
        }
        let conn = self.lock()?;
        let key = key_of(&name, &address, lat, lon);
        if on {
            conn.execute(
                "INSERT OR REPLACE INTO biz_client (key, name, address, lat, lon, added_ms) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
                params![key, name.trim(), address.trim(), lat, lon, now_ms],
            )?;
        } else {
            let at = Point { lat, lon };
            for c in client_spots(&conn)? {
                if c.key == key || haversine_m(c.at, at) <= CLIENT_RADIUS_M {
                    conn.execute("DELETE FROM biz_client WHERE key = ?1", [c.key])?;
                }
            }
        }
        Ok(())
    }

    /// 거래처 목록 (최근 90일에 자주 간 순, 같으면 이름 순).
    pub fn biz_clients(&self, now_ms: i64) -> Result<Vec<BizClient>, CoreError> {
        let conn = self.read()?;
        let clients = client_spots(&conn)?;
        if clients.is_empty() {
            return Ok(Vec::new());
        }
        let today = self.local_day(now_ms);
        let mut days: BTreeMap<&str, BTreeSet<i64>> = BTreeMap::new();
        for e in episodes_between(&conn, today - RECENT_DAYS, today)? {
            for s in &e.facts.stops {
                if let Some(c) = client_at(&clients, s) {
                    days.entry(c.key.as_str()).or_default().insert(self.local_day(s.arrive_ms));
                }
            }
        }
        let mut out: Vec<BizClient> = clients
            .iter()
            .map(|c| {
                let d = days.get(c.key.as_str());
                BizClient {
                    key: c.key.clone(),
                    name: c.name.clone(),
                    address: c.address.clone(),
                    lat: c.at.lat,
                    lon: c.at.lon,
                    visit_days: d.map_or(0, |d| d.len() as u32),
                    last_day: d.and_then(|d| d.iter().next_back()).map(|&d| format_day(d)).unwrap_or_default(),
                }
            })
            .collect();
        out.sort_by(|a, b| b.visit_days.cmp(&a.visit_days).then_with(|| a.name.cmp(&b.name)));
        Ok(out)
    }

    /// 거래처로 권할 곳 (모듈 설명): 최근 90일 평일에 3일 이상 들른, 집 · 회사가 아닌, 아직 거래처가 아닌 곳.
    pub fn biz_client_suggestions(&self, now_ms: i64) -> Result<Vec<BizClient>, CoreError> {
        let conn = self.read()?;
        let clients = client_spots(&conn)?;
        let anchors = anchors(&conn)?;
        let today = self.local_day(now_ms);
        // 곳마다: 들른 평일들 · 가장 최근 기록
        let mut places: BTreeMap<String, (BTreeSet<i64>, StopFact)> = BTreeMap::new();
        for e in episodes_between(&conn, today - RECENT_DAYS, today)? {
            for s in e.facts.stops.iter().filter(|s| notable(s, &anchors) && client_at(&clients, s).is_none()) {
                let d = self.local_day(s.arrive_ms);
                if !(1..=5).contains(&crate::memory::episode::weekday_index(d)) {
                    continue;
                }
                let p = places.entry(place_key(s)).or_insert_with(|| (BTreeSet::new(), s.clone()));
                p.0.insert(d);
                if s.arrive_ms > p.1.arrive_ms {
                    p.1 = s.clone();
                }
            }
        }
        let mut out: Vec<BizClient> = places
            .into_iter()
            .filter(|(_, (days, _))| days.len() >= SUGGEST_MIN_DAYS)
            .map(|(key, (days, s))| BizClient {
                key,
                name: s.name.trim().to_owned(),
                address: s.address.trim().to_owned(),
                lat: s.lat,
                lon: s.lon,
                visit_days: days.len() as u32,
                last_day: days.iter().next_back().map(|&d| format_day(d)).unwrap_or_default(),
            })
            .collect();
        out.sort_by(|a, b| b.visit_days.cmp(&a.visit_days).then_with(|| a.name.cmp(&b.name)));
        out.truncate(SUGGEST_MAX);
        Ok(out)
    }
}

#[cfg(test)]
mod tests;

//! 차 → 폰 기록 동기화: 차에 쌓인 기록을 연결된 폰 앱에서도 똑같이 보게 한다.
//!
//! 원본만 보낸다: 위치 점(`fix`), 주행 시작·끝 신호(`drive_event`), 내비 목적지(`navi_hint`), 이름이 붙은 장소.
//! 주행 나누기 · 머문 곳 · 날짜별 요약 · 기억은 원본에서 계산되는 값이라 폰이 받은 원본으로 직접 다시 만든다
//! (같은 Rust 코드라 결과가 같다). 장소 이름은 폰이 카카오에 다시 묻지 않도록 같이 보내, 가까운 장소에 붙인다.
//!
//! # 묶음 형식 (압축 전, 리틀 엔디언)
//! `EDS1` · 위치 점 수(u32) · 점마다 [시각 차, 위도 차(1e-7도), 경도 차, 정확도(0.1m)] (zigzag varint)
//! · 신호 수 · 신호마다 [시각 차(zigzag varint), 종류(u8)] · 힌트 수 · 힌트마다 [시각 i64, 종류 u8, 위도 f64, 경도 f64, 이름, 주소]
//! · 장소 수 · 장소마다 [위도 f64, 경도 f64, 출처 u8, 이름, 분류, 주소]. 글자는 varint 길이 + UTF-8.
//! 전체를 deflate로 줄인다 (1년치 37만 점 ≈ 1~2MB). 같은 묶음을 두 번 받아도 겹치는 줄은 무시된다.

use miniz_oxide::deflate::compress_to_vec;
use miniz_oxide::inflate::decompress_to_vec_with_limit;
use rusqlite::params;

use crate::geo::{haversine_m, Point};
use crate::store::{mark_dirty, CoreError, EodiStore};

const MAGIC: &[u8; 4] = b"EDS1";
/// 풀었을 때 최대 크기 (잘못된 묶음이 메모리를 다 쓰지 않게).
const MAX_RAW: usize = 64 * 1024 * 1024;
/// 받은 장소 이름을 이 거리(m) 안의 장소에 붙인다 (`places::PLACE_RADIUS_M`보다 좁게).
pub const NAME_MATCH_M: f64 = 60.0;

/// 보낼 묶음 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SyncChunk {
    /// 압축한 묶음 (비어 있으면 보낼 것이 없다)
    pub data: Vec<u8>,
    /// 이 묶음이 담은 마지막 시각. 다음에는 이 시각 뒤부터 보낸다.
    pub until_ms: i64,
    pub fixes: u32,
    /// true면 지금 있는 것을 다 담았다
    pub done: bool,
}

/// 받은 결과.
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct SyncImport {
    /// 새로 저장한 위치 점 수 (이미 있던 점은 세지 않는다)
    pub fixes: u32,
    /// 이름을 붙인 장소 수
    pub named: u32,
}

#[uniffi::export]
impl EodiStore {
    /// `since_ms` 뒤의 원본을 위치 점 `max_fixes`개까지 묶는다 (그 시각 범위의 신호·힌트, 이름 붙은 장소 전부 포함).
    pub fn sync_export(&self, since_ms: i64, max_fixes: u32) -> Result<SyncChunk, CoreError> {
        let conn = self.read()?;
        let mut st = conn.prepare_cached("SELECT t_ms, lat, lon, accuracy_m FROM fix WHERE t_ms > ?1 ORDER BY t_ms LIMIT ?2")?;
        let fixes: Vec<(i64, f64, f64, f64)> =
            st.query_map(params![since_ms, max_fixes], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)))?.collect::<Result<_, _>>()?;
        let done = (fixes.len() as u32) < max_fixes;
        let until = if done { i64::MAX } else { fixes.last().map(|f| f.0).unwrap_or(since_ms) };
        let mut st = conn.prepare_cached("SELECT t_ms, kind FROM drive_event WHERE t_ms > ?1 AND t_ms <= ?2 ORDER BY t_ms")?;
        let events: Vec<(i64, i64)> = st.query_map(params![since_ms, until], |r| Ok((r.get(0)?, r.get(1)?)))?.collect::<Result<_, _>>()?;
        let mut st = conn.prepare_cached(
            "SELECT t_ms, kind, lat, lon, name, address FROM navi_hint WHERE t_ms > ?1 AND t_ms <= ?2 ORDER BY t_ms",
        )?;
        let hints: Vec<(i64, i64, f64, f64, String, String)> = st
            .query_map(params![since_ms, until], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?)))?
            .collect::<Result<_, _>>()?;
        let mut st = conn.prepare_cached(
            "SELECT lat, lon, source, name, category, address FROM place WHERE name IS NOT NULL AND source IS NOT NULL",
        )?;
        let places: Vec<(f64, f64, i64, String, String, String)> = st
            .query_map([], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?)))?
            .collect::<Result<_, _>>()?;

        if fixes.is_empty() && events.is_empty() && hints.is_empty() {
            return Ok(SyncChunk { data: Vec::new(), until_ms: since_ms, fixes: 0, done: true });
        }
        let mut w = Writer::default();
        w.0.extend_from_slice(MAGIC);
        w.u32(fixes.len() as u32);
        let (mut pt, mut plat, mut plon) = (0i64, 0i64, 0i64);
        for &(t, lat, lon, acc) in &fixes {
            let (la, lo) = (e7(lat), e7(lon));
            w.zz(t - pt);
            w.zz(la - plat);
            w.zz(lo - plon);
            w.var((acc.max(0.0) * 10.0).round() as u64);
            (pt, plat, plon) = (t, la, lo);
        }
        w.u32(events.len() as u32);
        let mut pt = 0i64;
        for &(t, k) in &events {
            w.zz(t - pt);
            w.0.push(k as u8);
            pt = t;
        }
        w.u32(hints.len() as u32);
        for (t, k, lat, lon, name, addr) in &hints {
            w.0.extend_from_slice(&t.to_le_bytes());
            w.0.push(*k as u8);
            w.0.extend_from_slice(&lat.to_le_bytes());
            w.0.extend_from_slice(&lon.to_le_bytes());
            w.str(name);
            w.str(addr);
        }
        w.u32(places.len() as u32);
        for (lat, lon, src, name, cat, addr) in &places {
            w.0.extend_from_slice(&lat.to_le_bytes());
            w.0.extend_from_slice(&lon.to_le_bytes());
            w.0.push(*src as i8 as u8);
            w.str(name);
            w.str(cat);
            w.str(addr);
        }
        let until_ms = if done { fixes.last().map(|f| f.0).unwrap_or(since_ms).max(events.last().map(|e| e.0).unwrap_or(0)) } else { until };
        Ok(SyncChunk { data: compress_to_vec(&w.0, 6), until_ms, fixes: fixes.len() as u32, done })
    }

    /// 받은 묶음을 저장하고 주행·요약을 다시 계산한 뒤, 받은 장소 이름을 가까운 이름 없는 장소에 붙인다.
    pub fn sync_import(&self, data: Vec<u8>) -> Result<SyncImport, CoreError> {
        let raw = decompress_to_vec_with_limit(&data, MAX_RAW).map_err(|_| bad())?;
        let mut r = Reader { b: &raw, i: 0 };
        if r.take(4)? != MAGIC {
            return Err(bad());
        }
        let mut conn = self.lock()?;
        let tx = conn.transaction()?;
        let (mut lo, mut hi) = (i64::MAX, i64::MIN);
        let mut stored = 0u32;
        {
            let mut ins = tx.prepare_cached("INSERT OR IGNORE INTO fix (t_ms, lat, lon, accuracy_m) VALUES (?1, ?2, ?3, ?4)")?;
            let (mut t, mut la, mut lo7) = (0i64, 0i64, 0i64);
            for _ in 0..r.u32()? {
                t += r.zz()?;
                la += r.zz()?;
                lo7 += r.zz()?;
                let acc = r.var()? as f64 / 10.0;
                if t > 0 && ins.execute(params![t, la as f64 / 1e7, lo7 as f64 / 1e7, acc])? == 1 {
                    stored += 1;
                    lo = lo.min(t);
                    hi = hi.max(t);
                }
            }
            let mut ins = tx.prepare_cached("INSERT OR IGNORE INTO drive_event (t_ms, kind) VALUES (?1, ?2)")?;
            let mut t = 0i64;
            for _ in 0..r.u32()? {
                t += r.zz()?;
                let k = r.u8()? as i64;
                if t > 0 && ins.execute(params![t, k])? == 1 {
                    lo = lo.min(t);
                    hi = hi.max(t);
                }
            }
            let mut ins = tx.prepare_cached(
                "INSERT OR IGNORE INTO navi_hint (t_ms, kind, lat, lon, name, address) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            )?;
            for _ in 0..r.u32()? {
                let t = r.i64()?;
                let k = r.u8()? as i64;
                let (lat, lon) = (r.f64()?, r.f64()?);
                let (name, addr) = (r.str()?, r.str()?);
                if ins.execute(params![t, k, lat, lon, name, addr])? == 1 {
                    lo = lo.min(t);
                    hi = hi.max(t);
                }
            }
        }
        if lo <= hi {
            mark_dirty(&tx, lo, hi)?;
        }
        tx.commit()?;
        let mut names = Vec::new();
        for _ in 0..r.u32()? {
            let (lat, lon) = (r.f64()?, r.f64()?);
            let src = r.u8()? as i8 as i64;
            names.push((Point { lat, lon }, src, r.str()?, r.str()?, r.str()?));
        }
        // 받은 원본으로 주행·장소를 다시 나눈 뒤 이름 붙이기
        self.flush(&mut conn)?;
        let mut named = 0u32;
        if !names.is_empty() {
            let tx = conn.transaction()?;
            {
                let mut st = tx.prepare_cached("SELECT id, lat, lon FROM place WHERE source IS NULL")?;
                let open: Vec<(i64, Point)> =
                    st.query_map([], |r| Ok((r.get(0)?, Point { lat: r.get(1)?, lon: r.get(2)? })))?.collect::<Result<_, _>>()?;
                let mut up = tx.prepare_cached("UPDATE place SET name = ?2, category = ?3, address = ?4, source = ?5 WHERE id = ?1")?;
                for (id, p) in open {
                    let best = names
                        .iter()
                        .map(|n| (haversine_m(p, n.0), n))
                        .filter(|(d, _)| *d <= NAME_MATCH_M)
                        .min_by(|a, b| a.0.total_cmp(&b.0));
                    if let Some((_, (_, src, name, cat, addr))) = best {
                        up.execute(params![id, name, cat, addr, src])?;
                        named += 1;
                    }
                }
            }
            tx.commit()?;
        }
        drop(conn);
        Ok(SyncImport { fixes: stored, named })
    }
}

fn bad() -> CoreError {
    CoreError::InvalidInput("동기화 묶음 형식".into())
}

fn e7(v: f64) -> i64 {
    (v * 1e7).round() as i64
}

#[derive(Default)]
struct Writer(Vec<u8>);

impl Writer {
    fn var(&mut self, mut v: u64) {
        while v >= 0x80 {
            self.0.push((v as u8) | 0x80);
            v >>= 7;
        }
        self.0.push(v as u8);
    }
    fn zz(&mut self, v: i64) {
        self.var(((v << 1) ^ (v >> 63)) as u64);
    }
    fn u32(&mut self, v: u32) {
        self.0.extend_from_slice(&v.to_le_bytes());
    }
    fn str(&mut self, s: &str) {
        self.var(s.len() as u64);
        self.0.extend_from_slice(s.as_bytes());
    }
}

struct Reader<'a> {
    b: &'a [u8],
    i: usize,
}

impl Reader<'_> {
    fn take(&mut self, n: usize) -> Result<&[u8], CoreError> {
        let s = self.b.get(self.i..self.i + n).ok_or_else(bad)?;
        self.i += n;
        Ok(s)
    }
    fn u8(&mut self) -> Result<u8, CoreError> {
        Ok(self.take(1)?[0])
    }
    fn u32(&mut self) -> Result<u32, CoreError> {
        Ok(u32::from_le_bytes(self.take(4)?.try_into().map_err(|_| bad())?))
    }
    fn i64(&mut self) -> Result<i64, CoreError> {
        Ok(i64::from_le_bytes(self.take(8)?.try_into().map_err(|_| bad())?))
    }
    fn f64(&mut self) -> Result<f64, CoreError> {
        Ok(f64::from_le_bytes(self.take(8)?.try_into().map_err(|_| bad())?))
    }
    fn var(&mut self) -> Result<u64, CoreError> {
        let mut v = 0u64;
        for shift in (0..64).step_by(7) {
            let b = self.u8()?;
            v |= ((b & 0x7f) as u64) << shift;
            if b & 0x80 == 0 {
                return Ok(v);
            }
        }
        Err(bad())
    }
    fn zz(&mut self) -> Result<i64, CoreError> {
        let v = self.var()?;
        Ok(((v >> 1) as i64) ^ -((v & 1) as i64))
    }
    fn str(&mut self) -> Result<String, CoreError> {
        let n = self.var()? as usize;
        String::from_utf8(self.take(n)?.to_vec()).map_err(|_| bad())
    }
}

#[cfg(test)]
mod tests;

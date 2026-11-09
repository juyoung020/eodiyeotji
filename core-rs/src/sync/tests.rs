use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};

use super::*;
use crate::places::{PlaceLabel, PlaceName, PlaceSource};
use crate::store::{DriveEvent, GeoFix};

struct TempDb(PathBuf);
impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let p = std::env::temp_dir().join(format!("eodi-sync-{name}-{}-{}.db", std::process::id(), N.fetch_add(1, Ordering::Relaxed)));
        let d = TempDb(p);
        d.rm();
        d
    }
    fn open(&self) -> std::sync::Arc<EodiStore> {
        EodiStore::open(self.0.to_string_lossy().into_owned(), 540).unwrap()
    }
    fn rm(&self) {
        for s in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{s}", self.0.display()));
        }
    }
}
impl Drop for TempDb {
    fn drop(&mut self) {
        self.rm();
    }
}

const T0: i64 = 1_762_500_000_000;
const SEC: i64 = 1_000;

/// 북쪽으로 달리기 → 20분 머물기 → 다시 달리기 (하루, 머문 곳 하나).
fn record(store: &EodiStore) {
    store.mark_drive_event(DriveEvent::Start, T0).unwrap();
    let a: Vec<GeoFix> = (0..300).map(|i| GeoFix { lat: 37.5 + i as f64 * 1e-4, lon: 127.0, t_ms: T0 + i * SEC, accuracy_m: 5.0 }).collect();
    store.ingest(a).unwrap();
    store.mark_drive_event(DriveEvent::End, T0 + 300 * SEC).unwrap();
    let t1 = T0 + 1_500 * SEC;
    store.mark_drive_event(DriveEvent::Start, t1).unwrap();
    let b: Vec<GeoFix> =
        (0..300).map(|i| GeoFix { lat: 37.53 + i as f64 * 1e-4, lon: 127.0, t_ms: t1 + i * SEC, accuracy_m: 7.5 }).collect();
    store.ingest(b).unwrap();
    store.mark_drive_event(DriveEvent::End, t1 + 300 * SEC).unwrap();
}

#[test]
fn records_and_names_arrive_the_same_in_chunks() {
    let (da, db) = (TempDb::new("a"), TempDb::new("b"));
    let (car, phone) = (da.open(), db.open());
    record(&car);
    // 차에서 장소 하나에 이름을 붙여 둔다
    let q = car.pending_places(10).unwrap();
    assert!(!q.is_empty());
    let named = q[0];
    car.set_place_name(PlaceName {
        id: named.id,
        label: PlaceLabel { name: "바다 카페".into(), category: "카페".into(), address: "강원".into(), source: PlaceSource::Kakao },
    })
    .unwrap();

    // 작은 묶음 여러 개로 보낸다
    let (mut since, mut chunks) = (0i64, 0);
    loop {
        let c = car.sync_export(since, 250).unwrap();
        if !c.data.is_empty() {
            phone.sync_import(c.data).unwrap();
            chunks += 1;
        }
        since = c.until_ms;
        if c.done {
            break;
        }
    }
    assert!(chunks >= 3, "묶음 {chunks}개");
    assert_eq!(phone.fix_count().unwrap(), car.fix_count().unwrap());
    assert_eq!(phone.day_summaries(10).unwrap().iter().map(|d| (d.day.clone(), d.trip_count, d.stay_count)).collect::<Vec<_>>(),
        car.day_summaries(10).unwrap().iter().map(|d| (d.day.clone(), d.trip_count, d.stay_count)).collect::<Vec<_>>());
    // 이름 붙인 장소는 폰에서 다시 물을 필요가 없다
    let left: Vec<_> = phone.pending_places(10).unwrap();
    assert_eq!(left.len(), q.len() - 1, "이름 받은 곳은 빠진다");

    // 같은 것을 다시 받아도 늘지 않는다
    let again = car.sync_export(0, 100_000).unwrap();
    assert_eq!(phone.sync_import(again.data).unwrap().fixes, 0);
    // 더 보낼 것이 없으면 빈 묶음
    let end = car.sync_export(since, 100).unwrap();
    assert!(end.data.is_empty() && end.done);
}

#[test]
fn rejects_garbage() {
    let d = TempDb::new("g");
    let s = d.open();
    assert!(s.sync_import(vec![1, 2, 3]).is_err());
    assert!(s.sync_import(miniz_oxide::deflate::compress_to_vec(b"NOPE", 6)).is_err());
}

/// 실제 DB로 크기·시간 재기: `EODI_DB=경로 cargo test --release sync::tests::real_db -- --ignored --nocapture`
#[test]
#[ignore]
fn real_db() {
    let Ok(path) = std::env::var("EODI_DB") else { return };
    let src = EodiStore::open(path, 540).unwrap();
    let d = TempDb::new("real");
    let dst = d.open();
    let (mut since, mut total, mut n) = (0i64, 0usize, 0);
    let t = std::time::Instant::now();
    loop {
        let c = src.sync_export(since, 50_000).unwrap();
        total += c.data.len();
        n += 1;
        if !c.data.is_empty() {
            dst.sync_import(c.data).unwrap();
        }
        since = c.until_ms;
        if c.done {
            break;
        }
    }
    println!("묶음 {n}개 · {} KB · {:?} · 날 {} = {}", total / 1024, t.elapsed(), dst.day_summaries(400).unwrap().len(), src.day_summaries(400).unwrap().len());
}

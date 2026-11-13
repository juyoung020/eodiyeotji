//! 장소 이름 테스트. DB는 임시 폴더의 파일을 쓰고 끝나면 지운다.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

use super::*;
use crate::store::{DayDetail, DaySummary, DriveEvent, GeoFix, StaySummary};

const SEC: i64 = 1000;
const MIN: i64 = 60 * SEC;
const HOUR: i64 = 60 * MIN;
const KST: i32 = 540;
/// 2026-11-05 00:00 KST (UTC epoch ms)
const NOV5: i64 = 1_793_804_400_000;
const DAY: &str = "2026-11-05";

struct TempDb(PathBuf);

impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let db = TempDb(std::env::temp_dir().join(format!("eodi-places-{name}-{}-{n}.db", std::process::id())));
        db.remove();
        db
    }

    fn path(&self) -> String {
        self.0.to_string_lossy().into_owned()
    }

    fn open(&self) -> Arc<EodiStore> {
        EodiStore::open(self.path(), KST).expect("open")
    }

    fn remove(&self) {
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", self.0.display()));
        }
    }
}

impl Drop for TempDb {
    fn drop(&mut self) {
        self.remove();
    }
}

fn at(h: i64, m: i64) -> i64 {
    NOV5 + h * HOUR + m * MIN
}

fn gf(lat: f64, lon: f64, t_ms: i64) -> GeoFix {
    GeoFix { lat, lon, t_ms, accuracy_m: 5.0 }
}

/// t0부터 1초 간격 n개, 초당 위도 `dlat`씩 (1e-4 ≈ 11m).
fn drive(t0: i64, n: i64, lat0: f64, lon0: f64, dlat: f64) -> Vec<GeoFix> {
    (0..n).map(|i| gf(lat0 + i as f64 * dlat, lon0, t0 + i * SEC)).collect()
}

fn end_of(fixes: &[GeoFix]) -> (f64, f64, i64) {
    let f = fixes.last().unwrap();
    (f.lat, f.lon, f.t_ms)
}

fn label(name: &str, source: PlaceSource) -> PlaceLabel {
    PlaceLabel { name: name.into(), category: String::new(), address: String::new(), source }
}

fn hint(name: &str, lat: f64, lon: f64, t_ms: i64, kind: NaviHintKind) -> NaviPlaceHint {
    NaviPlaceHint { name: name.into(), lat, lon, t_ms, kind, address: format!("{name} 주소") }
}

fn name_of(p: &Option<PlaceLabel>) -> Option<&str> {
    p.as_ref().map(|l| l.name.as_str())
}

/// 가장 가까운 대기 장소 id.
fn pending_near(store: &EodiStore, lat: f64, lon: f64) -> PlaceQuery {
    let p = Point { lat, lon };
    store
        .pending_places(1000)
        .unwrap()
        .into_iter()
        .min_by(|a, b| {
            haversine_m(p, Point { lat: a.lat, lon: a.lon }).total_cmp(&haversine_m(p, Point { lat: b.lat, lon: b.lon }))
        })
        .expect("대기 장소가 있어야 한다")
}

#[test]
fn running_trip_end_waits_and_nearby_points_share_one_place() {
    let db = TempDb::new("group");
    let store = db.open();
    // 주행 A: 9시, 북쪽으로 10분 (약 6.6km)
    let a = drive(at(9, 0), 600, 37.5, 127.0, 1e-4);
    let (alat, alon, a_end) = end_of(&a);
    store.ingest(a).unwrap();

    // 아직 달리는 중일 수 있다 → 출발점만
    let p = store.pending_places(10).unwrap();
    assert_eq!(p.len(), 1, "{p:?}");
    assert_eq!((p[0].lat, p[0].lon, p[0].is_stay), (37.5, 127.0, false));

    // 끝 신호 → 도착점이 "머무는 중"인 장소로 (최신순이라 먼저)
    store.mark_drive_event(DriveEvent::End, a_end).unwrap();
    let p = store.pending_places(10).unwrap();
    assert_eq!(p.len(), 2, "{p:?}");
    assert_eq!((p[0].lat, p[0].lon, p[0].is_stay), (alat, alon, true));
    assert_eq!(store.pending_places(1).unwrap(), vec![p[0]], "limit");

    // 주행 B: 11시에 50m 떨어진 곳(같은 주차장)에서 출발해 남쪽으로 돌아와 A 출발점 60m 앞에서 끝
    let b = drive(at(11, 0), 600, alat + 0.00045, alon, -1e-4);
    let (blat, _, b_end) = end_of(&b);
    assert!(haversine_m(Point { lat: blat, lon: 127.0 }, Point { lat: 37.5, lon: 127.0 }) < 100.0);
    store.ingest(b).unwrap();
    store.mark_drive_event(DriveEvent::End, b_end).unwrap();

    let p = store.pending_places(10).unwrap();
    assert_eq!(p.len(), 2, "같은 주차장·같은 출발지는 장소 하나씩: {p:?}");
    assert!(p.iter().all(|q| q.is_stay), "둘 다 머문 곳이 됐다: {p:?}");
    // 최신순: B 도착(= A 출발 장소)이 먼저
    assert_eq!((p[0].lat, p[0].lon), (37.5, 127.0));

    store.set_place_name(PlaceName { id: p[1].id, label: label("광장시장 주차장", PlaceSource::Kakao) }).unwrap();
    store.set_place_name(PlaceName { id: p[0].id, label: label("우리 집", PlaceSource::Osm) }).unwrap();
    assert!(store.pending_places(10).unwrap().is_empty());

    let d = store.day_detail(DAY.into()).unwrap();
    assert_eq!(d.trips.len(), 2);
    assert_eq!(d.stays.len(), 2);
    assert_eq!(name_of(&d.trips[0].start_place), Some("우리 집"));
    assert_eq!(name_of(&d.trips[0].end_place), Some("광장시장 주차장"));
    assert_eq!(name_of(&d.stays[0].place), Some("광장시장 주차장"));
    assert_eq!(name_of(&d.trips[1].start_place), Some("광장시장 주차장"), "머문 곳에서 떠난 출발점은 그 장소");
    assert_eq!(name_of(&d.trips[1].end_place), Some("우리 집"));
    assert_eq!(name_of(&d.stays[1].place), Some("우리 집"));
    // 타임라인 항목에도 같은 이름
    assert!(d.entries.iter().all(|e| match e {
        crate::store::DayEntry::Trip { trip, .. } => trip.start_place.is_some() && trip.end_place.is_some(),
        crate::store::DayEntry::Stay { stay } => stay.place.is_some(),
    }));

    // 다음 날 같은 주차장에 또 가면 다시 묻지 않고 같은 이름
    let c = drive(at(24 + 9, 0), 600, 37.5, 127.0, 1e-4);
    let c_end = c.last().unwrap().t_ms;
    store.ingest(c).unwrap();
    store.mark_drive_event(DriveEvent::End, c_end).unwrap();
    assert!(store.pending_places(10).unwrap().is_empty());
    let d6 = store.day_detail("2026-11-06".into()).unwrap();
    assert_eq!(name_of(&d6.trips[0].end_place), Some("광장시장 주차장"));
}

#[test]
fn name_priority_and_bad_input() {
    let db = TempDb::new("priority");
    let store = db.open();
    let a = drive(at(9, 0), 300, 37.5, 127.0, 1e-4);
    let end = a.last().unwrap().t_ms;
    store.ingest(a).unwrap();
    store.mark_drive_event(DriveEvent::End, end).unwrap();
    let id = store.pending_places(1).unwrap()[0].id;
    let set = |n: &str, s| store.set_place_name(PlaceName { id, label: label(n, s) }).unwrap();
    let now = || name_of(&store.day_detail(DAY.into()).unwrap().stays[0].place).map(str::to_owned);

    set("오스엠", PlaceSource::Osm);
    assert_eq!(now().as_deref(), Some("오스엠"));
    set("카카오", PlaceSource::Kakao);
    assert_eq!(now().as_deref(), Some("카카오"), "높은 출처가 바꾼다");
    set("오스엠2", PlaceSource::Osm);
    assert_eq!(now().as_deref(), Some("카카오"), "낮은 출처는 못 바꾼다");
    set("카카오2", PlaceSource::Kakao);
    assert_eq!(now().as_deref(), Some("카카오2"), "같은 출처는 바꾼다");
    set("내비", PlaceSource::Navi);
    set("카카오3", PlaceSource::Kakao);
    let d = store.day_detail(DAY.into()).unwrap();
    assert_eq!(d.stays[0].place, Some(label("내비", PlaceSource::Navi)));

    let bad = |r: Result<(), CoreError>| matches!(r, Err(CoreError::InvalidInput(_)));
    assert!(bad(store.set_place_name(PlaceName { id: id + 1000, label: label("x", PlaceSource::Osm) })));
    assert!(bad(store.set_place_name(PlaceName { id, label: label("  ", PlaceSource::Navi) })));
    assert!(bad(store.mark_place_unresolved(id + 1000, 0)));
    assert!(bad(store.add_navi_hint(hint("", 37.5, 127.0, end, NaviHintKind::Destination))));
    assert!(bad(store.add_navi_hint(hint("x", f64::NAN, 127.0, end, NaviHintKind::Destination))));
    assert!(bad(store.add_navi_hint(hint("x", 37.5, 181.0, end, NaviHintKind::Destination))));
    assert!(bad(store.add_navi_hint(hint("x", 37.5, 127.0, 0, NaviHintKind::Destination))));
}

#[test]
fn unresolved_places_come_back_after_the_retry_time() {
    let db = TempDb::new("retry");
    let store = db.open();
    let a = drive(at(9, 0), 300, 37.5, 127.0, 1e-4);
    let end = a.last().unwrap().t_ms;
    store.ingest(a).unwrap();
    store.mark_drive_event(DriveEvent::End, end).unwrap();
    let p = store.pending_places(10).unwrap();
    assert_eq!(p.len(), 2);

    store.mark_place_unresolved(p[0].id, HOUR).unwrap();
    assert_eq!(store.pending_places(10).unwrap(), vec![p[1]], "기다리는 동안 빠진다");
    // 시간이 지난 것처럼 다시 시도 시각을 과거로
    store.lock().unwrap().execute("UPDATE place SET retry_at_ms = 1 WHERE id = ?1", [p[0].id]).unwrap();
    assert_eq!(store.pending_places(10).unwrap(), p);
    store.mark_place_unresolved(p[1].id, 0).unwrap();
    assert_eq!(store.pending_places(10).unwrap(), p, "0이면 바로 다시 나온다");
    // 기다리는 중에 이름이 오면 저장된다
    store.mark_place_unresolved(p[1].id, HOUR).unwrap();
    store.set_place_name(PlaceName { id: p[1].id, label: label("출발지", PlaceSource::Osm) }).unwrap();
    assert_eq!(name_of(&store.day_detail(DAY.into()).unwrap().trips[0].start_place), Some("출발지"));
    assert_eq!(store.pending_places(10).unwrap(), vec![p[0]]);
}

/// 목적지 힌트는 주행이 계산되기 전에 와도, 뒤에 와도 같은 결과다.
#[test]
fn destination_hint_names_the_trip_end_in_either_order() {
    let a = drive(at(9, 0), 600, 37.5, 127.0, 1e-4);
    let (lat, lon, end) = end_of(&a);
    let dest = (lat + 0.0005, lon); // 내비 목적지 좌표는 주차 자리와 약 55m 다르다

    // 1) 주행 중(끝나기 1분 전)에 받은 목적지 → 끝 신호가 오면 붙는다
    let db1 = TempDb::new("hint-during");
    let s1 = db1.open();
    s1.ingest(a[..300].to_vec()).unwrap();
    s1.add_navi_hint(hint("스타벅스 광화문점", dest.0, dest.1, end - MIN, NaviHintKind::Destination)).unwrap();
    s1.ingest(a[300..].to_vec()).unwrap();
    assert_eq!(s1.day_detail(DAY.into()).unwrap().trips[0].end_place, None, "끝나기 전에는 붙이지 않는다");
    s1.mark_drive_event(DriveEvent::End, end).unwrap();

    // 2) 힌트가 계산 전에 먼저 저장됨 (점보다 먼저 옴)
    let db2 = TempDb::new("hint-first");
    let s2 = db2.open();
    s2.add_navi_hint(hint("스타벅스 광화문점", dest.0, dest.1, end + 10 * MIN, NaviHintKind::Destination)).unwrap();
    s2.mark_drive_event(DriveEvent::End, end).unwrap();
    s2.ingest(a.clone()).unwrap();

    // 3) 주행·머문 곳이 다 계산된 뒤 도착 10분 후에 옴
    let db3 = TempDb::new("hint-after");
    let s3 = db3.open();
    s3.ingest(a.clone()).unwrap();
    s3.mark_drive_event(DriveEvent::End, end).unwrap();
    s3.day_detail(DAY.into()).unwrap();
    s3.add_navi_hint(hint("스타벅스 광화문점", dest.0, dest.1, end + 10 * MIN, NaviHintKind::Destination)).unwrap();

    let want = PlaceLabel {
        name: "스타벅스 광화문점".into(),
        category: String::new(),
        address: "스타벅스 광화문점 주소".into(),
        source: PlaceSource::Navi,
    };
    for s in [&s1, &s2, &s3] {
        let d = s.day_detail(DAY.into()).unwrap();
        assert_eq!(d.trips[0].end_place.as_ref(), Some(&want));
        assert_eq!(d.stays[0].place.as_ref(), Some(&want));
        assert_eq!(d.trips[0].start_place, None, "출발점에는 붙지 않는다");
        // 내비 이름보다 낮은 출처는 덮지 못한다
        let id = s.pending_places(10).unwrap();
        assert_eq!(id.len(), 1, "도착 장소는 이름이 있어 빠진다: {id:?}");
    }
    assert_eq!(s1.day_detail(DAY.into()).unwrap(), s3.day_detail(DAY.into()).unwrap());
}

#[test]
fn hints_that_are_too_far_or_too_late_are_kept_but_not_used() {
    let db = TempDb::new("hint-miss");
    let store = db.open();
    let a = drive(at(9, 0), 600, 37.5, 127.0, 1e-4);
    let (lat, lon, end) = end_of(&a);
    store.ingest(a).unwrap();
    store.mark_drive_event(DriveEvent::End, end).unwrap();
    let d = NaviHintKind::Destination;
    store.add_navi_hint(hint("40분 뒤", lat, lon, end + 40 * MIN, d)).unwrap();
    store.add_navi_hint(hint("500m 밖", lat + 0.0045, lon, end, d)).unwrap();
    store.add_navi_hint(hint("출발 전", lat, lon, at(8, 50), d)).unwrap();
    let detail = store.day_detail(DAY.into()).unwrap();
    assert_eq!(detail.trips[0].end_place, None);
    assert_eq!(store.pending_places(10).unwrap().len(), 2);
    // 같은 힌트를 두 번 넣어도 한 번만 저장
    store.add_navi_hint(hint("40분 뒤", lat, lon, end + 40 * MIN, d)).unwrap();
    let n: i64 = store.lock().unwrap().query_row("SELECT count(*) FROM navi_hint", [], |r| r.get(0)).unwrap();
    assert_eq!(n, 3);
}

#[test]
fn waypoint_hint_names_a_stop_inside_the_drive() {
    let db = TempDb::new("waypoint");
    let store = db.open();
    let t0 = at(12, 0);
    let mut fixes = drive(t0, 600, 37.5, 127.0, 1e-4);
    let (lat, lon, _) = end_of(&fixes);
    // 드라이브스루: 기어 D로 15분, 30m 안에서 꿈틀
    let wait0 = t0 + 600 * SEC;
    fixes.extend((0..900).map(|i| gf(lat + (i % 7) as f64 * 4e-5, lon, wait0 + i * SEC)));
    fixes.extend(drive(wait0 + 900 * SEC, 600, lat + 3e-4, lon, 1e-4));
    let end = fixes.last().unwrap().t_ms;
    // 목적지 힌트는 주행 안 머문 곳에 붙지 않는다 (주행 끝은 6km 밖)
    store.add_navi_hint(hint("맥도날드 DT", lat, lon, wait0 + MIN, NaviHintKind::Destination)).unwrap();
    for c in fixes.chunks(60) {
        store.ingest(c.to_vec()).unwrap();
        store.pending_places(5).unwrap();
    }
    store.mark_drive_event(DriveEvent::End, end).unwrap();
    let d = store.day_detail(DAY.into()).unwrap();
    assert_eq!(d.stays.len(), 2, "드라이브스루 + 도착: {:?}", d.stays);
    assert_eq!(d.stays[0].place, None);
    let q = pending_near(&store, lat, lon);
    assert!(q.is_stay);

    store.add_navi_hint(hint("맥도날드 DT", lat, lon, wait0 + MIN, NaviHintKind::Waypoint)).unwrap();
    let d = store.day_detail(DAY.into()).unwrap();
    assert_eq!(name_of(&d.stays[0].place), Some("맥도날드 DT"));
    assert_eq!(d.stays[1].place, None);
    assert_eq!(d.trips[0].end_place, None);
}

#[test]
fn day_map_carries_names_and_stay_order() {
    let db = TempDb::new("map");
    let store = db.open();
    let a = drive(at(9, 0), 600, 37.5, 127.0, 1e-4);
    let (alat, alon, a_end) = end_of(&a);
    let b = drive(at(11, 0), 600, alat, alon, 1e-4);
    let b_end = b.last().unwrap().t_ms;
    store.ingest(a).unwrap();
    store.ingest(b).unwrap();
    store.mark_drive_event(DriveEvent::End, b_end).unwrap();
    store.add_navi_hint(hint("서울 \"중앙\" 시장\\", alat, alon, a_end, NaviHintKind::Destination)).unwrap();

    let m = store.day_map(DAY.into()).unwrap();
    assert!(m.geojson.contains(&format!(r#""key":"s{a_end}","arrive_ms":{a_end},"leave_ms":{},"open":false,"order":1,"name":"서울 \"중앙\" 시장\\"}}"#, at(11, 0))), "{}", m.geojson);
    // 지금 머무는 곳(그날 도착 지점)은 들른 곳에 세지 않으므로 번호가 없다 (`store::visit_indices`), 이름을 모르면 name도 없다
    assert!(m.geojson.contains(r#""open":true}"#), "{}", m.geojson);
    assert!(m.geojson.contains(r#","to":"서울 \"중앙\" 시장\\"}"#));
    assert!(m.geojson.contains(r#","from":"서울 \"중앙\" 시장\\"}"#));
    assert_eq!(m.geojson.matches(r#""from""#).count(), 1, "첫 출발지는 모른다");
    assert_eq!(m.geojson.matches(r#""to""#).count(), 1);
}

/// 버전 1 DB(장소 표 없음, 주행은 이미 계산됨)를 열면 원본·계산 결과가 그대로이고 지점이 장소에 이어진다.
#[test]
fn v1_database_is_migrated_without_losing_records() {
    let db = TempDb::new("migrate");
    let t0 = at(9, 0);
    let a = drive(t0, 600, 37.5, 127.0, 1e-4);
    let (lat, lon, a_end) = end_of(&a);
    let b = drive(at(11, 0), 600, lat, lon, 1e-4);
    let b_end = b.last().unwrap().t_ms;
    let (before_days, before_detail) = {
        let store = db.open();
        store.ingest(a).unwrap();
        store.ingest(b).unwrap();
        store.mark_drive_event(DriveEvent::End, b_end).unwrap();
        let days = store.day_summaries(10).unwrap();
        let detail = store.day_detail(DAY.into()).unwrap();
        // 계산 안 된 변경도 하나 남긴다
        store.ingest(drive(at(15, 0), 120, 36.0, 128.0, 1e-4)).unwrap();
        // 버전 1로 되돌린다: 버전 2에서 더한 표·칸을 지우고 user_version = 1
        let c = store.lock().unwrap();
        c.execute_batch(crate::bizclient::DROP_V20).unwrap();
        c.execute_batch(crate::community::road::DROP_V19).unwrap();
        c.execute_batch(crate::hotplace::DROP_V17).unwrap();
        c.execute_batch(crate::biztrip::DROP_V16).unwrap();
        c.execute_batch(crate::readings::DROP_V15).unwrap();
        c.execute_batch(crate::energy::DROP_V14).unwrap();
        c.execute_batch(crate::cabin::DROP_V13).unwrap();
        c.execute_batch(crate::edits::DROP_V11).unwrap();
        c.execute_batch(crate::community::drive::DROP_V10).unwrap();
        c.execute_batch(crate::offers::DROP_V8).unwrap();
        c.execute_batch(crate::edits::DROP_V7).unwrap();
        c.execute_batch(crate::search_ui::DROP_V6).unwrap();
        c.execute_batch(crate::community::DROP_V5).unwrap();
        c.execute_batch(crate::photos::DROP_V4).unwrap();
        c.execute_batch(crate::memory::DROP_V3).unwrap();
        c.execute_batch("DROP TABLE place; DROP TABLE trip_place; DROP TABLE navi_hint; ALTER TABLE day_summary DROP COLUMN stop_names; PRAGMA user_version = 1;").unwrap();
        (days, detail)
    };
    {
        let c = rusqlite::Connection::open(db.path()).unwrap();
        let v: i32 = c.pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
        assert_eq!(v, 1);
        let tables: Vec<String> = c
            .prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
            .unwrap()
            .query_map([], |r| r.get(0))
            .unwrap()
            .collect::<Result<_, _>>()
            .unwrap();
        assert_eq!(tables, ["day_summary", "dirty", "drive_event", "fix", "meta", "trip"]);
    }

    let store = db.open();
    let v: i32 = store.lock().unwrap().pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(v, crate::store::SCHEMA_VERSION);
    assert_eq!(store.fix_count().unwrap(), 1320);
    let days = store.day_summaries(10).unwrap();
    assert_eq!(days[0].trip_count, before_days[0].trip_count + 1, "밀려 있던 변경도 반영");
    let detail = store.day_detail(DAY.into()).unwrap();
    assert_eq!(&detail.trips[..2], &before_detail.trips[..]);
    assert_eq!(detail.stays[0], before_detail.stays[0]);
    // 기존 기록의 지점도 장소에 이어져 이름을 받을 수 있다
    let p = store.pending_places(10).unwrap();
    assert!(p.iter().any(|q| q.is_stay && haversine_m(Point { lat: q.lat, lon: q.lon }, Point { lat, lon }) < 1.0), "{p:?}");
    store.add_navi_hint(hint("회사", lat, lon, a_end + MIN, NaviHintKind::Destination)).unwrap();
    assert_eq!(name_of(&store.day_detail(DAY.into()).unwrap().stays[0].place), Some("회사"));
}

/// 점·신호·힌트를 조금씩, 순서 없이, 조회를 섞어 넣어도 한 번에 넣은 것과 이름이 같다.
#[test]
fn incremental_hints_match_one_shot() {
    // 하루 네 번 주행: 집 → 회사 → 식당 → 회사 → 집. 도착마다 목적지 힌트.
    let stops = [(37.50, 127.00), (37.53, 127.00), (37.53, 127.03), (37.53, 127.00), (37.50, 127.00)];
    let names = ["회사", "식당", "회사", "집"];
    let mut fixes = Vec::new();
    let mut events = Vec::new();
    let mut hints = Vec::new();
    let mut t = at(8, 0);
    for k in 0..4 {
        let (a, b) = (stops[k], stops[k + 1]);
        let n = 600;
        let start = t;
        for i in 0..n {
            let r = i as f64 / (n - 1) as f64;
            fixes.push(gf(a.0 + (b.0 - a.0) * r, a.1 + (b.1 - a.1) * r, t + i * SEC));
        }
        let end = t + (n - 1) * SEC;
        events.push((DriveEvent::Start, start));
        events.push((DriveEvent::End, end));
        hints.push(hint(names[k], b.0 + 0.0003, b.1, end - 2 * MIN, NaviHintKind::Destination));
        t = end + 2 * HOUR;
    }

    let one_db = TempDb::new("hints-one");
    let one = one_db.open();
    one.ingest(fixes.clone()).unwrap();
    for (e, t) in &events {
        one.mark_drive_event(*e, *t).unwrap();
    }
    for h in &hints {
        one.add_navi_hint(h.clone()).unwrap();
    }
    let want = one.day_detail(DAY.into()).unwrap();
    let ends: Vec<_> = want.trips.iter().map(|t| name_of(&t.end_place)).collect();
    assert_eq!(ends, [Some("회사"), Some("식당"), Some("회사"), Some("집")]);
    let starts: Vec<_> = want.trips.iter().map(|t| name_of(&t.start_place)).collect();
    assert_eq!(starts, [Some("집"), Some("회사"), Some("식당"), Some("회사")], "집은 같은 장소라 첫 출발점도 '집'");

    for seed in [3u64, 11, 29] {
        let mut rng = seed;
        let mut next = |n: usize| {
            rng = rng.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1_442_695_040_888_963_407);
            ((rng >> 33) as usize) % n
        };
        let db = TempDb::new("hints-inc");
        let inc = db.open();
        let mut chunks: Vec<Vec<GeoFix>> = fixes.chunks(97).map(<[GeoFix]>::to_vec).collect();
        for _ in 0..chunks.len() / 3 {
            let (i, j) = (next(chunks.len()), next(chunks.len()));
            chunks.swap(i, j);
        }
        let mut pending_events = events.clone();
        let mut pending_hints = hints.clone();
        for c in chunks {
            inc.ingest(c).unwrap();
            if next(3) == 0 && !pending_events.is_empty() {
                let (e, t) = pending_events.swap_remove(next(pending_events.len()));
                inc.mark_drive_event(e, t).unwrap();
            }
            if next(3) == 0 && !pending_hints.is_empty() {
                inc.add_navi_hint(pending_hints.swap_remove(next(pending_hints.len()))).unwrap();
            }
            if next(3) == 0 {
                inc.pending_places(5).unwrap();
            }
        }
        for (e, t) in pending_events {
            inc.mark_drive_event(e, t).unwrap();
        }
        for h in pending_hints {
            inc.add_navi_hint(h).unwrap();
        }
        let got = inc.day_detail(DAY.into()).unwrap();
        assert_eq!(got, want, "seed {seed}");
        assert_eq!(inc.pending_places(10).unwrap().len(), one.pending_places(10).unwrap().len(), "seed {seed}");
    }
}

#[test]
fn stay_summary_place_round_trips_through_detail() {
    // 이름 없는 머문 곳은 place None (기존 동작 그대로)
    let db = TempDb::new("none");
    let store = db.open();
    let a = drive(at(18, 0), 600, 37.5, 127.0, 1e-4);
    let (lat, lon, end) = end_of(&a);
    store.ingest(a).unwrap();
    store.mark_drive_event(DriveEvent::End, end).unwrap();
    let d: DayDetail = store.day_detail(DAY.into()).unwrap();
    assert_eq!(d.stays, vec![StaySummary {
        center: crate::ffi::GeoPoint { lat, lon },
        arrive_ms: end,
        leave_ms: end,
        place: None,
        order: 1
    }]);
}

/// 하루 목록의 stop_names: 이름 있는 머문 곳 앞에서 3개, 도착 시각순. 이름이 바뀌면 그 장소가 나온 모든 날이 바뀐다.
#[test]
fn day_list_shows_first_three_named_stops_and_follows_renames() {
    let db = TempDb::new("stops");
    let store = db.open();
    // 하루 다섯 곳: 각 1시간 주차 (주행 사이 머문 곳 4 + 마지막 머무는 중 1)
    let mut t = at(8, 0);
    let mut lat = 37.5;
    let mut ends = Vec::new();
    for _ in 0..5 {
        let a = drive(t, 300, lat, 127.0, 1e-4);
        let (elat, elon, end) = end_of(&a);
        store.ingest(a).unwrap();
        ends.push((elat, elon, end));
        lat = elat;
        t = end + HOUR;
    }
    store.mark_drive_event(DriveEvent::End, ends[4].2).unwrap();
    // 다음 날 마지막 장소에서 출발해 첫 번째 장소에 다시 간다
    let next = drive(at(24 + 9, 0), 1197, ends[4].0, 127.0, -1e-4);
    let next_end = next.last().unwrap().t_ms;
    store.ingest(next).unwrap();
    store.mark_drive_event(DriveEvent::End, next_end).unwrap();

    let d = store.day_detail(DAY.into()).unwrap();
    assert_eq!(d.stays.iter().map(|s| s.order).collect::<Vec<_>>(), [1, 2, 3, 4, 5]);
    assert!(store.day_summaries(5).unwrap().iter().all(|s| s.stop_names.is_empty()));

    let name = |i: usize, n: &str| {
        let q = pending_near(&store, ends[i].0, ends[i].1);
        store.set_place_name(PlaceName { id: q.id, label: label(n, PlaceSource::Kakao) }).unwrap();
    };
    name(4, "안목해변");
    name(1, "경포해변");
    name(2, "초당순두부마을");
    let days = store.day_summaries(5).unwrap();
    assert_eq!(days[1].day, DAY);
    assert_eq!(days[1].stop_names, ["경포해변", "초당순두부마을", "안목해변"]);
    name(0, "주문진항");
    let days = store.day_summaries(5).unwrap();
    assert_eq!(days[1].stop_names, ["주문진항", "경포해변", "초당순두부마을"], "앞에서 3개");
    assert_eq!(days[0].stop_names, ["주문진항"], "다음 날도 같은 장소");
    // 하루 요약 글(story)은 하루 상세에서만 채운다 (날짜 목록은 쓰지 않는다) → 그것만 빼고 같아야 한다
    let detail = store.day_detail(DAY.into()).unwrap().summary;
    assert!(detail.story.is_some());
    assert_eq!(days[1], DaySummary { story: None, ..detail });

    // 내비 힌트로 이름이 바뀌어도 (다른 날 요약까지) 따라간다
    store.add_navi_hint(hint("주문진 수산시장", ends[0].0, ends[0].1, ends[0].2 + MIN, NaviHintKind::Destination)).unwrap();
    let days = store.day_summaries(5).unwrap();
    assert_eq!(days[1].stop_names[0], "주문진 수산시장");
    assert_eq!(days[0].stop_names, ["주문진 수산시장"]);
    // 다시 열어도 그대로 (표에 저장돼 있다)
    drop(store);
    let store = db.open();
    assert_eq!(store.day_summaries(5).unwrap(), days);
}

#[test]
fn map_names_keep_only_drawable_characters() {
    use crate::mapdata::map_safe_name;
    assert_eq!(map_safe_name("스타벅스 광화문점").as_deref(), Some("스타벅스 광화문점"));
    assert_eq!(map_safe_name("★카페☆ 라떼①  하우스").as_deref(), Some("카페 라떼 하우스"));
    assert_eq!(map_safe_name("℃ 온천 Ⅱ호점 😀").as_deref(), Some("온천 호점"));
    assert_eq!(map_safe_name("Москва 식당").as_deref(), Some("식당"));
    assert_eq!(map_safe_name("景福宮 · Café (본점) & Co., 1/2 ~ 'A'-B").as_deref(), Some("景福宮 · Café (본점) & Co., 1/2 ~ 'A'-B"));
    assert_eq!(map_safe_name("ㄱㄴ 한글 자모"), Some("ㄱㄴ 한글 자모".into()));
    assert_eq!(map_safe_name("줄\n바꿈\t탭"), Some("줄 바꿈 탭".into()));
    assert_eq!(map_safe_name("★♡😀"), None);
    assert_eq!(map_safe_name("   "), None);

    // GeoJSON에는 거른 이름, day_detail에는 원래 이름
    let db = TempDb::new("map-safe");
    let store = db.open();
    let a = drive(at(9, 0), 300, 37.5, 127.0, 1e-4);
    let (lat, lon, end) = end_of(&a);
    store.ingest(a).unwrap();
    store.mark_drive_event(DriveEvent::End, end).unwrap();
    let start = pending_near(&store, 37.5, 127.0).id;
    let stop = pending_near(&store, lat, lon).id;
    store.set_place_name(PlaceName { id: stop, label: label("★맛집★ 😀", PlaceSource::Kakao) }).unwrap();
    store.set_place_name(PlaceName { id: start, label: label("♡♡", PlaceSource::Kakao) }).unwrap();
    let d = store.day_detail(DAY.into()).unwrap();
    assert_eq!(name_of(&d.stays[0].place), Some("★맛집★ 😀"));
    let m = store.day_map(DAY.into()).unwrap();
    assert!(m.geojson.contains(r#""open":true,"name":"맛집"}"#), "{}", m.geojson);
    assert!(m.geojson.contains(r#","to":"맛집"}"#));
    assert!(!m.geojson.contains(r#""from""#), "거르고 남는 게 없으면 뺀다");
}

/// 머문 곳이 하나도 잡히지 않은 날(퇴근길 하나만 있고 다음 주행이 다른 곳에서 시작)도
/// stop_names는 그날 주행의 출발 · 도착 장소 이름으로 채워진다 (날짜 줄 귀띔이 비지 않게).
#[test]
fn day_without_stays_names_trip_endpoints() {
    let db = TempDb::new("endpoints");
    let store = db.open();
    let a = drive(at(18, 0), 300, 37.5, 127.0, 1e-4);
    let (elat, elon, _) = end_of(&a);
    store.ingest(a).unwrap();
    // 다음 주행은 이틀 뒤 멀리서 시작 → 도착점은 머문 곳이 아니다
    let b = drive(at(48 + 9, 0), 300, 37.7, 127.2, 1e-4);
    store.ingest(b).unwrap();
    assert!(store.day_detail(DAY.into()).unwrap().stays.is_empty());

    let set = |lat: f64, lon: f64, n: &str| {
        let q = pending_near(&store, lat, lon);
        store.set_place_name(PlaceName { id: q.id, label: label(n, PlaceSource::Kakao) }).unwrap();
    };
    set(37.5, 127.0, "센터원빌딩");
    set(elat, elon, "응봉동 현대아파트");
    let days = store.day_summaries(5).unwrap();
    let d = days.iter().find(|d| d.day == DAY).unwrap();
    assert_eq!(d.stop_names, ["센터원빌딩", "응봉동 현대아파트"]);
}

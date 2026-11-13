//! 거래처 목록 테스트. DB는 임시 파일, 끝나면 지운다.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

use super::*;
use crate::ffi::GeoPoint;
use crate::store::{days_from_civil, DriveEvent, GeoFix, DAY_MS};

const KST: i32 = 540;
const HOME: (f64, f64) = (37.5530, 127.0335);
const OFFICE: (f64, f64) = (37.5672, 126.9844);

struct TempDb(PathBuf);

impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        let db = TempDb(crate::test_tmp::dir().join(format!("eodi-client-{name}-{}-{n}.db", std::process::id())));
        db.remove();
        db
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

fn open(name: &str) -> (TempDb, Arc<EodiStore>) {
    let db = TempDb::new(name);
    let store = EodiStore::open(db.0.to_string_lossy().into_owned(), KST).expect("open");
    (db, store)
}

/// 한국 시각 → UTC ms.
fn kst(y: i64, m: i64, d: i64, h: i64) -> i64 {
    days_from_civil(y, m, d) * DAY_MS + (h * 60 - KST as i64) * 60_000
}

/// 직선으로 n점 (10초 간격) 달린다. 반환: 도착 시각.
fn drive(s: &EodiStore, t: i64, from: (f64, f64), to: (f64, f64), n: i64) -> i64 {
    s.mark_drive_event(DriveEvent::Start, t).unwrap();
    let fixes = (0..n)
        .map(|i| {
            let k = i as f64 / (n - 1) as f64;
            GeoFix { t_ms: t + i * 10_000, lat: from.0 + (to.0 - from.0) * k, lon: from.1 + (to.1 - from.1) * k, accuracy_m: 5.0 }
        })
        .collect();
    s.ingest(fixes).unwrap();
    let end = t + (n - 1) * 10_000;
    s.mark_drive_event(DriveEvent::End, end).unwrap();
    end
}

/// 집 → 그곳(2시간 머묾) → 집.
fn visit(s: &EodiStore, t: i64, at: (f64, f64)) {
    let arrive = drive(s, t, HOME, at, 90);
    s.ingest((0..240).map(|i| GeoFix { t_ms: arrive + 30_000 + i * 30_000, lat: at.0, lon: at.1, accuracy_m: 5.0 }).collect()).unwrap();
    drive(s, arrive + 2 * 3_600_000 + 60_000, at, HOME, 90);
}

#[test]
fn weekend_visit_near_home_is_a_business_trip_only_when_it_is_a_client() {
    let (_db, s) = open("weekend");
    s.set_home_office(Some(GeoPoint { lat: HOME.0, lon: HOME.1 }), Some(GeoPoint { lat: OFFICE.0, lon: OFFICE.1 })).unwrap();
    // 2026-11-07(토) 10:00: 집에서 5km쯤 떨어진 고객사에 2시간
    let shop = (37.5100, 127.0600);
    visit(&s, kst(2026, 11, 7, 10), shop);
    let now = kst(2026, 11, 8, 12);
    assert!(s.biz_trips("2026-11".into()).unwrap().trips.is_empty(), "주말 · 가까운 곳은 출장이 아니다");
    assert!(s.biz_clients(now).unwrap().is_empty());

    // 거래처로 정하면 요일 · 거리와 상관없이 출장
    s.set_biz_client("한빛전자".into(), "서울 강남구 삼성동 1".into(), shop.0, shop.1, true, now).unwrap();
    let m = s.biz_trips("2026-11".into()).unwrap();
    assert_eq!(m.trips.len(), 1, "{:?}", m.trips);
    let t = &m.trips[0];
    assert!(t.client && !t.marked);
    assert_eq!((t.day.as_str(), t.weekday.as_str(), t.title.as_str()), ("2026-11-07", "토", "한빛전자 방문"));
    assert_eq!(t.stops.len(), 1, "거래처가 행선지로 들어간다");

    // 목록: 들른 날 수 · 마지막 날
    let list = s.biz_clients(now).unwrap();
    assert_eq!(list.len(), 1);
    assert_eq!((list[0].name.as_str(), list[0].visit_days, list[0].last_day.as_str()), ("한빛전자", 1, "2026-11-07"));

    // "출장 아님"으로 정한 날은 거래처라도 뺀다
    s.set_biz_mark("2026-11-07".into(), Some(false)).unwrap();
    assert!(s.biz_trips("2026-11".into()).unwrap().trips.is_empty());
    s.set_biz_mark("2026-11-07".into(), None).unwrap();

    // 거래처에서 빼면 (100m 옆 자리로 불러도) 다시 출장이 아니다
    s.set_biz_client(String::new(), String::new(), shop.0 + 0.0009, shop.1, false, now).unwrap();
    assert!(s.biz_clients(now).unwrap().is_empty());
    assert!(s.biz_trips("2026-11".into()).unwrap().trips.is_empty());
    assert!(s.set_biz_client("x".into(), String::new(), 99.0, 0.0, true, now).is_err());
}

#[test]
fn weekday_regulars_are_suggested_until_they_become_clients() {
    // 시연 1년치: 평일 출장지(남양연구소 첫째 · 셋째 수요일, 판교 둘째 목요일)가 권할 곳에 나온다
    let (_db, s) = open("suggest");
    let today = kst(2026, 11, 12, 12);
    assert!(s.seed_demo_data(today).unwrap() > 600);
    let names = |v: &[BizClient]| v.iter().map(|c| c.name.clone()).collect::<Vec<_>>();
    let sug = s.biz_client_suggestions(today).unwrap();
    assert!(!sug.is_empty() && sug.len() <= SUGGEST_MAX);
    assert!(names(&sug).contains(&"현대자동차 남양연구소".to_owned()), "{:?}", names(&sug));
    assert!(sug.iter().all(|c| c.visit_days as usize >= SUGGEST_MIN_DAYS && !c.last_day.is_empty()));
    assert!(sug.windows(2).all(|w| w[0].visit_days >= w[1].visit_days), "자주 간 순");
    assert!(!names(&sug).iter().any(|n| n == "응봉동 현대아파트" || n == "센터원빌딩"), "집 · 회사는 권하지 않는다");

    // 거래처로 정하면 권할 곳에서 빠지고 목록에 들어간다
    let c = sug.iter().find(|c| c.name == "현대자동차 남양연구소").unwrap();
    s.set_biz_client(c.name.clone(), c.address.clone(), c.lat, c.lon, true, today).unwrap();
    assert!(!names(&s.biz_client_suggestions(today).unwrap()).contains(&c.name));
    let list = s.biz_clients(today).unwrap();
    assert_eq!(names(&list), std::slice::from_ref(&c.name));
    assert_eq!(list[0].visit_days, c.visit_days);
    // 11월 출장 제목이 거래처 이름으로 바뀐다
    let m = s.biz_trips("2026-11".into()).unwrap();
    assert!(m.trips.iter().any(|t| t.client && t.title == "현대자동차 남양연구소 방문"), "{:?}", m.trips.iter().map(|t| &t.title).collect::<Vec<_>>());
}

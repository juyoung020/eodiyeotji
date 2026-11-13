//! 먼저 말 걸기 테스트: 계기 세 가지, 횟수 · 간격, 말하지 않는 경우, 응/아니 · 대화 다음 말 알아듣기.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};

use super::*;
use crate::store::days_from_civil;

const KST: i32 = 540;

/// 한국 시각 → UTC ms.
fn kst(y: i64, m: i64, d: i64, h: i64, min: i64) -> i64 {
    days_from_civil(y, m, d) * DAY_MS + (h * 60 + min - KST as i64) * MIN_MS
}

fn stop(name: &str, category: &str, lat: f64, lon: f64, arrive_ms: i64) -> StopFact {
    StopFact {
        name: name.into(),
        category: category.into(),
        address: format!("{name} 주소"),
        arrive_ms,
        leave_ms: arrive_ms + 3_600_000,
        lat,
        lon,
        first_visit: false,
    }
}

fn snap() -> VehicleSnapshot {
    VehicleSnapshot { here: None, destination: None, waypoint_count: 0, range_km: None, energy_pct: None, ev: true, nav_voice: false }
}

fn input(event: ProactiveEvent, now_ms: i64) -> ProactiveInput {
    ProactiveInput { event, now_ms, tz_minutes: KST, vehicle: snap(), visits: Vec::new(), routine_today: None, biz: None, caps: CapsState::default() }
}

const NAMYANG: (f64, f64) = (37.2057, 126.8259);
const SEOUL: (f64, f64) = (37.5665, 126.9780);
const GANGNEUNG: (f64, f64) = (37.7519, 128.8761);

// 2026-11-18 = 수요일
fn wed_morning() -> i64 {
    kst(2026, 11, 18, 8, 30)
}

/// 지난 수요일들 오전 10시에 남양연구소, 화요일에 다른 곳.
fn wednesday_history() -> Vec<StopFact> {
    let mut v = Vec::new();
    for w in 1..=3 {
        v.push(stop("현대자동차 남양연구소", "연구소", NAMYANG.0, NAMYANG.1, kst(2026, 11, 18 - 7 * w, 10, 0)));
    }
    v.push(stop("동네 카페", "카페", SEOUL.0, SEOUL.1, kst(2026, 11, 17, 10, 0)));
    v.push(stop("동네 카페", "카페", SEOUL.0, SEOUL.1, kst(2026, 11, 10, 10, 0)));
    v
}

#[test]
fn departure_suggests_the_weekday_routine_place() {
    let mut i = input(ProactiveEvent::Departure, wed_morning());
    i.visits = wednesday_history();
    let s = decide(&i).expect("제안");
    assert_eq!(s.kind, ProactiveKind::Routine);
    assert_eq!(s.line, "수요일이네요. 늘 가던 현대자동차 남양연구소로 안내할까요?");
    assert!(s.ask);
    assert!(matches!(s.action, ProactiveAction::Navigate { ref stop } if stop.name == "현대자동차 남양연구소"));
    assert!(s.detail.contains("3번"), "{}", s.detail);
}

#[test]
fn departure_prefers_routine_of_today_and_uses_ro_josa() {
    let mut i = input(ProactiveEvent::Departure, wed_morning());
    i.routine_today = Some((stop("서울숲", "공원", SEOUL.0, SEOUL.1, kst(2026, 11, 4, 9, 0)), "매달 첫째 · 셋째 수요일".into()));
    let s = decide(&i).expect("제안");
    assert_eq!(s.line, "수요일이네요. 늘 가던 서울숲으로 안내할까요?");
    assert_eq!(s.detail, "매달 첫째 · 셋째 수요일마다 가던 곳");
}

#[test]
fn departure_stays_quiet_without_routine_or_with_destination_or_already_visited() {
    // 한 번만 간 곳은 버릇이 아니다
    let mut i = input(ProactiveEvent::Departure, wed_morning());
    i.visits = vec![stop("현대자동차 남양연구소", "연구소", NAMYANG.0, NAMYANG.1, kst(2026, 11, 11, 10, 0))];
    assert_eq!(decide(&i), None);
    // 다른 시간대(저녁 출발)면 아침 버릇은 묻지 않는다
    let mut i = input(ProactiveEvent::Departure, kst(2026, 11, 18, 19, 0));
    i.visits = wednesday_history();
    assert_eq!(decide(&i), None);
    // 이미 내비 목적지가 있다
    let mut i = input(ProactiveEvent::Departure, wed_morning());
    i.visits = wednesday_history();
    i.vehicle.destination = Some(NavTarget { name: "어딘가".into(), lat: 0.0, lon: 0.0 });
    assert_eq!(decide(&i), None);
    // 오늘 이미 다녀왔다
    let mut i = input(ProactiveEvent::Departure, kst(2026, 11, 18, 8, 30));
    i.visits = wednesday_history();
    i.visits.push(stop("현대자동차 남양연구소", "연구소", NAMYANG.0, NAMYANG.1, kst(2026, 11, 18, 7, 0)));
    assert_eq!(decide(&i), None);
}

fn energy_input(range_km: f64, ev: bool) -> ProactiveInput {
    let mut i = input(ProactiveEvent::LowEnergy, wed_morning());
    i.vehicle.here = Some(GeoPoint { lat: SEOUL.0, lon: SEOUL.1 });
    i.vehicle.destination = Some(NavTarget { name: "강릉".into(), lat: GANGNEUNG.0, lon: GANGNEUNG.1 });
    i.vehicle.range_km = Some(range_km);
    i.vehicle.ev = ev;
    i.visits = vec![
        // 가는 길 (원주 근처)
        stop("횡성휴게소 전기차 충전소", "전기차충전소", 37.48, 128.0, kst(2026, 9, 1, 10, 0)),
        // 반대쪽 (인천)
        stop("인천 충전소", "전기차충전소", 37.45, 126.70, kst(2026, 9, 2, 10, 0)),
        stop("SK에너지 응봉주유소", "주유소", 37.552, 127.0405, kst(2026, 9, 3, 10, 0)),
        stop("동네 카페", "카페", SEOUL.0, SEOUL.1, kst(2026, 9, 4, 10, 0)),
    ];
    i
}

#[test]
fn low_energy_picks_the_visited_charger_on_the_way() {
    let s = decide(&energy_input(120.0, true)).expect("제안");
    assert_eq!(s.kind, ProactiveKind::Charge);
    assert_eq!(s.line, "강릉까지 배터리가 부족해요. 예전에 들렀던 횡성휴게소 전기차 충전소를 경유지로 넣을까요?");
    match s.action {
        ProactiveAction::AddWaypoint { stop, destination } => {
            assert_eq!(stop.name, "횡성휴게소 전기차 충전소");
            assert_eq!(destination.name, "강릉");
        }
        a => panic!("{a:?}"),
    }
}

#[test]
fn low_energy_uses_gas_stations_for_fuel_cars_and_pct_when_range_unknown() {
    let mut i = energy_input(0.0, false);
    i.vehicle.range_km = None;
    i.vehicle.energy_pct = Some(15.0); // 600km × 15% = 90km
    let s = decide(&i).expect("제안");
    assert!(s.line.starts_with("강릉까지 연료가 부족해요. 예전에 들렀던 SK에너지 응봉주유소를"), "{}", s.line);
}

#[test]
fn low_energy_stays_quiet_when_enough_or_no_room_or_no_station() {
    assert_eq!(decide(&energy_input(400.0, true)), None, "넉넉하다");
    let mut i = energy_input(120.0, true);
    i.vehicle.waypoint_count = 2;
    assert_eq!(decide(&i), None, "경유지 2개가 이미 찼다");
    let mut i = energy_input(120.0, true);
    i.visits.retain(|s| !s.category.contains("충전"));
    assert_eq!(decide(&i), None, "들렀던 충전소가 없다");
    let mut i = energy_input(120.0, true);
    i.vehicle.destination = None;
    assert_eq!(decide(&i), None, "목적지를 모른다");
}

fn arrival_input(now: i64) -> ProactiveInput {
    let mut i = input(ProactiveEvent::Arrival, now);
    i.vehicle.here = Some(GeoPoint { lat: GANGNEUNG.0 + 0.0005, lon: GANGNEUNG.1 });
    i
}

#[test]
fn arrival_recalls_same_day_years_ago() {
    let mut i = arrival_input(kst(2026, 11, 6, 15, 0));
    i.visits = vec![
        stop("경포해변", "해수욕장", GANGNEUNG.0, GANGNEUNG.1, kst(2025, 11, 6, 13, 0)),
        stop("경포해변", "해수욕장", GANGNEUNG.0, GANGNEUNG.1, kst(2026, 8, 1, 13, 0)),
    ];
    let s = decide(&i).expect("제안");
    assert_eq!(s.kind, ProactiveKind::Recall);
    assert_eq!(s.line, "1년 전 오늘도 여기 왔었어요.");
    assert!(!s.ask);
    assert_eq!(s.action, ProactiveAction::Inform);
}

#[test]
fn arrival_recalls_last_visit_otherwise() {
    let mut i = arrival_input(kst(2026, 11, 6, 15, 0));
    i.visits = vec![stop("경포해변", "해수욕장", GANGNEUNG.0, GANGNEUNG.1, kst(2026, 8, 1, 13, 0))];
    assert_eq!(decide(&i).unwrap().line, "지난번엔 8월 1일에 왔었어요. 97일 만이에요.");
    i.visits = vec![stop("경포해변", "해수욕장", GANGNEUNG.0, GANGNEUNG.1, kst(2024, 7, 1, 13, 0))];
    assert_eq!(decide(&i).unwrap().line, "2년 만이에요. 지난번엔 2024년 7월 1일에 왔었어요.");
}

#[test]
fn arrival_asks_biz_when_new_place_and_trip_qualifies() {
    let mut i = arrival_input(kst(2026, 11, 18, 15, 0));
    // 오늘 처음 온 곳 (오늘 기록은 회상 대상이 아니다)
    i.visits = vec![stop("경포해변", "해수욕장", GANGNEUNG.0, GANGNEUNG.1, kst(2026, 11, 18, 14, 50))];
    i.biz = Some(BizCandidate { day: "2026-11-18".into(), place: "현대자동차 남양연구소".into() });
    let s = decide(&i).expect("제안");
    assert_eq!(s.kind, ProactiveKind::Biz);
    assert_eq!(s.line, "현대자동차 남양연구소에 다녀온 길, 출장으로 기록할까요?");
    assert_eq!(s.action, ProactiveAction::MarkBiz { day: "2026-11-18".into() });
    i.biz = None;
    assert_eq!(decide(&i), None, "기억도 출장도 아니면 말하지 않는다");
}

#[test]
fn caps_cooldowns_and_nav_voice_block_suggestions() {
    let base = || {
        let mut i = input(ProactiveEvent::Departure, wed_morning());
        i.visits = wednesday_history();
        i
    };
    assert!(decide(&base()).is_some());
    let mut i = base();
    i.vehicle.nav_voice = true;
    assert_eq!(decide(&i), None, "내비 음성 중");
    let mut i = base();
    i.caps.today_count = MAX_PER_DAY;
    assert_eq!(decide(&i), None, "하루 횟수");
    let mut i = base();
    i.caps.last_any_ms = Some(wed_morning() - 60_000);
    assert_eq!(decide(&i), None, "방금 다른 제안");
    let mut i = base();
    i.caps.last_event_ms = Some(wed_morning() - 60 * 60_000);
    assert_eq!(decide(&i), None, "출발 제안은 6시간 쉰다");
    let mut i = base();
    i.caps.last_event_ms = Some(wed_morning() - 7 * 60 * 60_000);
    assert!(decide(&i).is_some(), "쉬는 시간이 지났다");
    let mut i = base();
    i.caps.declined_today = true;
    assert_eq!(decide(&i), None, "오늘 아니라고 했다");
}

#[test]
fn reply_and_follow_up_intents() {
    for t in ["응", "응 넣어줘", "네", "그래", "좋아요", "안내해줘", "기록해 줘", "오케이"] {
        assert_eq!(reply_intent(t), ReplyIntent::Yes, "{t}");
    }
    for t in ["아니", "아니요", "괜찮아", "나중에", "아니 안 해", "됐어"] {
        assert_eq!(reply_intent(t), ReplyIntent::No, "{t}");
    }
    for t in ["", "어디였지", "음악 틀어"] {
        assert_eq!(reply_intent(t), ReplyIntent::Unknown, "{t}");
    }
    assert_eq!(
        follow_up_intent("주말에 바다 가자", false),
        FollowUpIntent::Plan { query: "바다 보러 가기 좋은 드라이브 코스 추천해줘".into(), theme: "바다".into() }
    );
    assert_eq!(follow_up_intent("작년 여름에 갔던 바다 어디였지?", false), FollowUpIntent::Pass);
    assert_eq!(follow_up_intent("같이 갈 사람 초대해줘", true), FollowUpIntent::Invite);
    assert_eq!(follow_up_intent("출발하자", true), FollowUpIntent::Start);
    assert_eq!(follow_up_intent("안내해줘", true), FollowUpIntent::Start);
    assert_eq!(follow_up_intent("출발하자", false), FollowUpIntent::Pass, "짠 코스가 없으면 에이전트에 그대로");
    assert_eq!(follow_up_intent("2번으로 가자", true), FollowUpIntent::Pass);
}

// ---- 저장소 ----

struct TempDb(PathBuf);

impl TempDb {
    fn new(name: &str) -> Self {
        static N: AtomicU32 = AtomicU32::new(0);
        let n = N.fetch_add(1, Ordering::Relaxed);
        TempDb(crate::test_tmp::dir().join(format!("eodi-proactive-{name}-{}-{n}.db", std::process::id())))
    }
}

impl Drop for TempDb {
    fn drop(&mut self) {
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", self.0.display()));
        }
    }
}

#[test]
fn store_counts_shown_and_declined_then_resets() {
    let db = TempDb::new("caps");
    let store = EodiStore::open(db.0.to_string_lossy().into_owned(), KST).expect("open");
    // 2026-11-04 (첫째 수요일) 오늘로 시연 기록 → 출발 때 남양연구소
    let today = kst(2026, 11, 4, 12, 0);
    assert!(store.seed_demo_data(today).unwrap() > 600);
    let now = kst(2026, 11, 4, 8, 30);
    let s = crate::proactive::suggestion(&store, ProactiveEvent::Departure, now, snap()).unwrap().expect("출발 제안");
    assert_eq!(s.kind, ProactiveKind::Routine);
    assert!(s.line.starts_with("수요일이네요. 늘 가던 "), "{}", s.line);
    crate::proactive::mark_shown(&store, ProactiveEvent::Departure, now).unwrap();
    assert_eq!(crate::proactive::suggestion(&store, ProactiveEvent::Departure, now + 3 * 60_000, snap()).unwrap(), None, "쉬는 시간");
    crate::proactive::reset(&store).unwrap();
    assert!(crate::proactive::suggestion(&store, ProactiveEvent::Departure, now, snap()).unwrap().is_some());
    crate::proactive::mark_declined(&store, ProactiveEvent::Departure, now).unwrap();
    assert_eq!(crate::proactive::suggestion(&store, ProactiveEvent::Departure, now + 60 * 60_000, snap()).unwrap(), None, "아니라고 했다");
    // 도착: 회상이나 출장 중 하나, 오류 없이
    let mut v = snap();
    v.here = Some(GeoPoint { lat: NAMYANG.0, lon: NAMYANG.1 });
    let _ = crate::proactive::suggestion(&store, ProactiveEvent::Arrival, kst(2026, 11, 4, 15, 0), v).unwrap();
}

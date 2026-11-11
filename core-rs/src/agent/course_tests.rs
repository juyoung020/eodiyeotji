//! 근처 드라이브 추천(경치 길) 테스트: 말 알아듣기(규칙), 빠른 길 답·목록·제안(경유지 없는 두 구간), 후속 말("2번으로 가자"·
//! "더 가까운 데"·"바다 쪽"), 확인 전 실행 금지, LLM 도구(courses·nav pick), 빠른 길 지연.

use std::time::Instant;

use serde_json::Value;

use super::courses::{self, detect, CourseIntent};
use super::tests::{ask, cfg, ctx, fake_server, last_msg, llm_only, sse_text, tool, Ev, FakeBackend, Recorder};
use super::*;
use crate::community::drive::{BestTime, DriveRoutePost, RouteHighlight};

// ---------------------------------------------------------------- 가짜 드라이브 길 (지금 위치 = 서울 목동)

type End = (&'static str, f64, f64);

#[allow(clippy::too_many_arguments)]
fn route(id: &str, title: &str, region: &str, hearts: u32, start: End, end: End, km: f64, min: i64, best: BestTime, sights: &[&str]) -> DriveRoutePost {
    DriveRoutePost {
        id: id.into(),
        title: title.into(),
        body: format!("{title} 길이에요."),
        author: "가상의작성자".into(),
        mine: false,
        region: region.into(),
        start_name: start.0.into(),
        end_name: end.0.into(),
        start: GeoPoint { lat: start.1, lon: start.2 },
        end: GeoPoint { lat: end.1, lon: end.2 },
        best_time: best,
        theme: best.theme(),
        highlights: sights.iter().map(|n| RouteHighlight { name: n.to_string(), lat: start.1, lon: start.2 }).collect(),
        distance_m: km * 1000.0,
        duration_ms: min * 60_000,
        created_ms: 0,
        hearts,
        hearted_by_me: false,
        visited_day: "2026-10-25".into(),
        season: "가을 · 단풍철".into(),
        weather: Some("맑음 · 14°C".into()),
        photos: vec![],
    }
}

pub(super) fn routes() -> Vec<DriveRoutePost> {
    vec![
        route("r-gangbyeon", "강변북로 한강 야경 드라이브", "서울", 486, ("월드컵대교 북단", 37.5558, 126.8934), ("광진교 북단", 37.5392, 127.0950),
            22.4, 25, BestTime::Night, &["반포대교 달빛무지개분수", "남산서울타워"]),
        route("r-olympic", "올림픽대로 한강 노을 드라이브", "서울", 402, ("잠실", 37.5170, 127.0880), ("가양대교 남단", 37.5639, 126.8499),
            31.2, 35, BestTime::Sunset, &["여의도 한강공원", "선유도공원"]),
        route("r-jayuro", "자유로 따라 임진각 드라이브", "경기", 214, ("가양대교 북단", 37.5801, 126.8598), ("임진각", 37.8889, 126.7402),
            48.4, 57, BestTime::Morning, &["행주산성", "임진각 평화누리"]),
        route("r-incheon", "인천대교 바다 위 드라이브", "인천", 377, ("송도", 37.4060, 126.6090), ("영종도", 37.4641, 126.5036),
            14.7, 16, BestTime::Sunset, &["인천대교 주탑"]),
        route("r-sihwa", "시화방조제 바닷길", "경기", 189, ("오이도", 37.3440, 126.6898), ("대부도 방아머리", 37.3111, 126.6099),
            9.4, 31, BestTime::Sunset, &["오이도 빨강등대", "시화나래 달전망대"]),
        route("r-bukhan", "북한강 391번 지방도", "경기", 368, ("두물머리", 37.5450, 127.3180), ("청평", 37.7315, 127.4254),
            26.6, 31, BestTime::Blossom, &["물의정원", "청평호"]),
        route("r-route7", "7번 국도 동해안 드라이브", "강원 강릉시", 533, ("경포해변", 37.8037, 128.9077), ("하조대", 38.0278, 128.7177),
            36.4, 38, BestTime::Morning, &["주문진항", "하조대"]),
        route("r-heonhwa", "헌화로 바닷길", "강원 강릉시", 451, ("정동진", 37.6907, 129.0338), ("금진항", 37.6446, 129.0455),
            7.6, 12, BestTime::Morning, &["심곡항"]),
    ]
}

fn no_llm(backend: Arc<FakeBackend>) -> Arc<MemoryAgent> {
    MemoryAgent::with_backend(backend, cfg(""), AgentOptions::default())
}

fn parked() -> AgentContext {
    ctx(false)
}

fn course_lists(r: &Recorder) -> Vec<(Vec<String>, u32)> {
    r.events().into_iter().filter_map(|e| if let Ev::Courses(ids, f) = e { Some((ids, f)) } else { None }).collect()
}

fn shared(p: &NavProposal) -> (&str, &Vec<CourseLeg>) {
    match &p.action {
        NavAction::SharedCourse { post_id, legs, .. } => (post_id.as_str(), legs),
        other => panic!("드라이브 길 제안이 아님: {other:?}"),
    }
}

/// 안내 구간: 시작점까지 → 끝점까지, 둘 다 경유지 없음.
fn assert_two_plain_legs(legs: &[CourseLeg], start: &str, end: &str) {
    assert_eq!(legs.len(), 2, "{legs:?}");
    assert!(legs.iter().all(|l| l.waypoints.is_empty()), "경유지가 없어야 한다: {legs:?}");
    assert_eq!((legs[0].destination.name.as_str(), legs[1].destination.name.as_str()), (start, end));
}

// ---------------------------------------------------------------- 말 알아듣기

#[test]
fn course_intents_by_rules() {
    let rec = |q: &[&str]| Some(CourseIntent::Recommend { q: q.iter().map(|s| s.to_string()).collect() });
    // 새 추천 (앞 대화 없음)
    for u in ["여기 근처 드라이브 추천코스 안내해줘", "여기 근처 갈 만한 데", "인기 코스 알려줘", "주변 드라이브 코스 추천", "근처에 드라이브 코스 있어?", "요즘 인기 많은 드라이브 코스"] {
        assert_eq!(detect(u, false, 0), rec(&[]), "{u}");
    }
    assert_eq!(detect("바다 보러 가기 좋은 코스 있어?", false, 0), rec(&["바다"]));
    assert_eq!(detect("강릉 쪽 드라이브 코스 추천해줘", false, 0), rec(&["강릉"]));
    assert_eq!(detect("심심한데 차 끌고 바람 쐬러 나갈 만한 곳 없을까?", false, 0), rec(&[]), "동사 꼴 낱말은 지역·테마가 아니다");
    // 기억 질문·앞 대화 없는 번호는 추천 말이 아니다
    for u in ["작년 여름에 갔던 바다 어디였지?", "작년에 갔던 드라이브 코스 어디였지?", "그 코스로 안내해줘", "2번으로 가자", "더 가까운 데", "엄마랑 갔던 식당"] {
        assert_eq!(detect(u, false, 0), None, "{u}");
    }
    // 목록이 있을 때 후속 말
    let pick = |i: usize, go: bool| Some(CourseIntent::Pick { index: Some(i), go });
    assert_eq!(detect("2번으로 가자", true, 3), pick(1, true));
    assert_eq!(detect("두 번째 거", true, 3), pick(1, false));
    assert_eq!(detect("3번은 어떤 길이야?", true, 3), pick(2, false));
    assert_eq!(detect("첫 번째 코스로 안내해줘", true, 3), pick(0, true));
    assert_eq!(detect("마지막 거 어때", true, 3), pick(2, false));
    assert_eq!(detect("2번 코스 알려줘", true, 3), pick(1, false), "번호가 새 추천보다 먼저");
    assert_eq!(detect("더 가까운 데", true, 3), Some(CourseIntent::Closer));
    assert_eq!(detect("그거 말고 바다 쪽", true, 3), Some(CourseIntent::Theme { q: vec!["바다".into()] }));
    assert_eq!(detect("바다 쪽은?", true, 3), Some(CourseIntent::Theme { q: vec!["바다".into()] }));
    assert_eq!(detect("다른 거 없어?", true, 3), Some(CourseIntent::Other));
    assert_eq!(detect("어떤 길이야?", true, 3), Some(CourseIntent::Describe));
    assert_eq!(detect("뭐가 보여?", true, 3), Some(CourseIntent::Describe));
    assert_eq!(detect("그 코스로 가자", true, 3), Some(CourseIntent::Pick { index: None, go: true }));
    // 목록이 있어도 기억 질문은 기억으로
    assert_eq!(detect("작년 여름에 갔던 바다 어디였지?", true, 3), None);
    assert_eq!(courses::ordinal("5번으로가자", 3), Some(4));
}

#[test]
fn route_matches_theme_by_title_sights_and_best_time() {
    let all = routes();
    let hit = |q: &str| -> Vec<&str> { all.iter().filter(|r| courses::matches(r, &[q.to_string()])).map(|r| r.id.as_str()).collect() };
    assert_eq!(hit("바다"), vec!["r-incheon", "r-sihwa", "r-route7", "r-heonhwa"]);
    assert_eq!(hit("야경"), vec!["r-gangbyeon", "r-sihwa"], "야경 · 전망(달전망대)");
    assert_eq!(hit("노을"), vec!["r-olympic", "r-incheon", "r-sihwa"], "언제(노을)로도 찾는다");
    assert_eq!(hit("벚꽃"), vec!["r-bukhan"]);
    assert_eq!(hit("남산서울타워"), vec!["r-gangbyeon"], "가는 길 풍경 이름");
    assert_eq!(hit("강릉"), vec!["r-route7", "r-heonhwa"]);
}

// ---------------------------------------------------------------- 빠른 길

#[test]
fn nearby_routes_fast_path_lists_top3_and_only_proposes_two_plain_legs() {
    let server = fake_server(vec![sse_text(&["쓰이면 안 된다"])], 200);
    let backend = FakeBackend::new();
    let agent = server.agent(backend.clone(), AgentOptions::default());
    let r = ask(&agent, "여기 근처 드라이브 추천코스 안내해줘", parked());

    assert!(server.bodies().is_empty(), "LLM을 부르지 않는다");
    let ev = r.events();
    assert!(matches!(&ev[0], Ev::Courses(ids, 0) if ids == &["r-gangbyeon", "r-olympic", "r-jayuro"]), "목록이 답보다 먼저: {ev:?}");
    let a = r.answer();
    assert!(a.starts_with("근처 인기 드라이브 길 3개를 찾았어요. 하트가 가장 많은 길은 ‘강변북로 한강 야경 드라이브’예요. 여기서 4.1km, 22km·25분 코스예요. 안내할까요?"), "{a}");
    assert!(!a.contains('♥') && !a.contains("2번") && !a.contains("쿠폰"), "목록은 화면 카드에, 말은 1위만: {a}");
    let p = r.proposals().pop().expect("1번 안내 제안");
    let (id, legs) = shared(&p);
    assert_eq!(id, "r-gangbyeon");
    assert_two_plain_legs(legs, "월드컵대교 북단", "광진교 북단");
    assert!(p.summary.contains("월드컵대교 북단 → 광진교 북단"), "{}", p.summary);
    assert!(ev.iter().all(|e| !matches!(e, Ev::Confirmed(..))), "확인 전에는 실행하지 않는다");
    assert_eq!(agent.pending_proposal(), Some(p));
    let first_call = backend.reco_calls.lock().unwrap()[0];
    assert_eq!((first_call.1, first_call.2), (courses::DEFAULT_RADIUS_KM, 20));
    let d = r.done();
    assert_eq!((d.source, d.timing.llm_calls), (AnswerSource::FastPath, 0));
    assert!(d.timing.to_recommendation_ms.unwrap() < 50 && d.timing.total_ms < 50, "{:?}", d.timing);
}

#[test]
fn follow_ups_pick_describe_closer_theme_other_then_confirm() {
    let backend = FakeBackend::new();
    let agent = no_llm(backend.clone());
    ask(&agent, "여기 근처 드라이브 추천코스", parked());

    // "2번으로 가자" → 2번 제안만 (1번 제안을 실행하지 않는다)
    let r = ask(&agent, "2번으로 가자", parked());
    assert_eq!(course_lists(&r), vec![(vec!["r-gangbyeon".into(), "r-olympic".into(), "r-jayuro".into()], 1)]);
    assert_eq!(r.answer(), "2번 ‘올림픽대로 한강 노을 드라이브’로 안내할까요?");
    assert!(r.events().iter().all(|e| !matches!(e, Ev::Confirmed(..))));
    assert_two_plain_legs(shared(&r.proposals()[0]).1, "잠실", "가양대교 남단");
    assert_eq!(r.done().source, AnswerSource::FastPath);

    // "3번은 어떤 길이야?" → 시작·끝·거리·시간·풍경·언제 + 3번 제안
    let r = ask(&agent, "3번은 어떤 길이야?", parked());
    assert_eq!(
        r.answer(),
        "3번 ‘자유로 따라 임진각 드라이브’는 가양대교 북단에서 임진각까지 48km, 57분쯤 걸리는 길이에요. 가는 길에 행주산성, 임진각 평화누리가 보여요. 아침에 가장 좋고, 여기서 6.0km예요. 안내할까요?"
    );
    assert_eq!(shared(&r.proposals()[0]).0, "r-jayuro");

    // "더 가까운 데" (지금 3번 자유로, 약 6km) → 그보다 가까운 길을 가까운 순으로
    let r = ask(&agent, "더 가까운 데", parked());
    assert_eq!(course_lists(&r)[0].0, vec!["r-gangbyeon", "r-olympic"]);
    assert!(r.answer().starts_with("더 가까운 드라이브 길 2개를 찾았어요. 하트가 가장 많은 길은 ‘강변북로 한강 야경 드라이브’예요."), "{}", r.answer());
    // 가장 가까운 것에서 다시 "더 가까운 데" → 없다고
    let r = ask(&agent, "더 가까운 데", parked());
    assert_eq!(r.answer(), "근처에서는 ‘강변북로 한강 야경 드라이브’가 가장 가까운 드라이브 길이에요. 안내할까요?");

    // "그거 말고 바다 쪽" → 바다 길만 (지금 것 빼고)
    let r = ask(&agent, "그거 말고 바다 쪽", parked());
    assert_eq!(course_lists(&r)[0].0, vec!["r-incheon", "r-sihwa"]);
    assert!(r.answer().starts_with("근처 ‘바다’ 드라이브 길 2개를 찾았어요. 하트가 가장 많은 길은 ‘인천대교 바다 위 드라이브’예요. 여기서 "), "{}", r.answer());

    // "다른 거" → 다음 번호
    let r = ask(&agent, "다른 거 없어?", parked());
    assert!(r.answer().starts_with("그럼 2번 ‘시화방조제 바닷길’은 어떠세요? 하트 189개, 여기서 "), "{}", r.answer());
    let p = r.proposals()[0].clone();

    // 목록 밖 번호
    let r = ask(&agent, "5번으로 가자", parked());
    assert_eq!(r.answer(), "목록에는 2번까지 있어요. 몇 번으로 할까요?");
    assert!(r.proposals().is_empty());

    // "응" → 그때서야 실행 (마지막 제안 = 시화방조제)
    let r = ask(&agent, "응", parked());
    match &r.events()[0] {
        Ev::Confirmed(id, NavAction::SharedCourse { post_id, legs, .. }) => {
            assert_eq!((id, post_id.as_str()), (&p.id, "r-sihwa"));
            assert_two_plain_legs(legs, "오이도", "대부도 방아머리");
        }
        e => panic!("{e:?}"),
    }
    assert_eq!(r.done().source, AnswerSource::Confirm);
    assert!(agent.confirm(p.id).is_err(), "두 번 실행하지 않는다");
}

#[test]
fn driving_answer_is_one_short_sentence_about_the_top_route() {
    let agent = no_llm(FakeBackend::new());
    let r = ask(&agent, "근처 드라이브 코스 추천해줘", ctx(true));
    let a = r.answer();
    assert_eq!(a, "근처 인기 드라이브 길 1위는 ‘강변북로 한강 야경 드라이브’예요. 여기서 4.1km, 하트 486개예요. 안내할까요?");
    assert!(a.chars().count() < 75, "{a}");
    assert_eq!(course_lists(&r)[0].0.len(), 3, "화면 목록은 그대로 (운전 중 화면이 1위만 크게 보인다)");
    // 운전 중 설명도 짧게
    let r = ask(&agent, "2번은 어떤 길이야?", ctx(true));
    assert_eq!(r.answer(), "2번은 잠실에서 가양대교 남단까지 31km 길이에요. 안내할까요?");
}

#[test]
fn no_location_far_theme_and_empty_cases() {
    // 위치를 모르면 전국 하트순, 거리 없이
    let agent = no_llm(FakeBackend::new());
    let r = ask(&agent, "인기 코스 알려줘", AgentContext { current_location: None, ..parked() });
    assert_eq!(
        r.answer(),
        "지금 위치를 몰라서 전국 인기 드라이브 길 3개를 찾았어요. 하트가 가장 많은 길은 ‘7번 국도 동해안 드라이브’예요. 36km·38분 코스예요. 안내할까요?"
    );

    // 근처에 없는 지역 → 전국에서 찾고 거리는 지금 위치에서 잰다
    let agent = no_llm(FakeBackend::new());
    let r = ask(&agent, "강릉 쪽 드라이브 코스 추천해줘", parked());
    assert!(r.answer().starts_with("근처 50km 안에는 없어서 전국 ‘강릉’ 인기 드라이브 길 2개를 찾았어요. 하트가 가장 많은 길은 ‘7번 국도 동해안 드라이브’예요. 여기서 1"), "{}", r.answer());
    assert_eq!(course_lists(&r)[0].0, vec!["r-route7", "r-heonhwa"]);

    // 없는 지역 → 근처 인기 길로 대신
    let agent = no_llm(FakeBackend::new());
    let r = ask(&agent, "제주 쪽 드라이브 코스 추천해줘", parked());
    assert!(r.answer().starts_with("‘제주’ 길은 아직 없어서 대신 근처 인기 드라이브 길 3개를 찾았어요."), "{}", r.answer());

    // 공유된 길이 하나도 없으면 짧게, 제안 없음
    let backend = Arc::new(FakeBackend { routes: vec![], ..Default::default() });
    let agent = no_llm(backend);
    let r = ask(&agent, "여기 근처 드라이브 추천코스", parked());
    assert_eq!(r.answer(), "아직 공유된 드라이브 길이 없어요.");
    assert!(r.proposals().is_empty() && course_lists(&r).is_empty());
}

#[test]
fn llm_tool_result_describes_the_road_not_stops_or_coupons() {
    let backend = FakeBackend::new();
    let st = courses::CourseState { list: backend.recommend_drive_routes(ctx(false).current_location.unwrap(), 50.0, 20).unwrap(), ..Default::default() };
    let j = courses::list_json(&st);
    let top = &j.as_array().unwrap()[0];
    assert_eq!(top["title"], "강변북로 한강 야경 드라이브");
    assert_eq!((top["from"].as_str(), top["to"].as_str(), top["best"].as_str()), (Some("월드컵대교 북단"), Some("광진교 북단"), Some("야경")));
    assert_eq!(top["minutes"], 25);
    assert_eq!(top["sights"][1], "남산서울타워");
    assert!(top.get("stops").is_none() && top.get("partner_coupon_places").is_none(), "{top}");
}

#[test]
fn memory_question_ends_course_topic() {
    let agent = no_llm(FakeBackend::new());
    ask(&agent, "여기 근처 드라이브 추천코스", parked());
    let r = ask(&agent, "작년 여름에 갔던 바다 어디였지?", parked());
    assert!(r.events().contains(&Ev::Rec(12, false)), "기억 찾기로 간다");
    let r = ask(&agent, "2번으로 가자", parked());
    assert!(course_lists(&r).is_empty(), "기억 이야기 뒤의 번호는 코스 번호가 아니다");
}

// ---------------------------------------------------------------- LLM 도구

#[test]
fn llm_courses_tool_and_nav_pick() {
    let server = fake_server(
        vec![
            tool("c1", "courses", r#"{"q":"바다"}"#),
            sse_text(&["근처 바다 길은 1번 인천대교예요. 안내할까요?"]),
            tool("c2", "nav", r#"{"mode":"pick","n":2}"#),
            sse_text(&["2번 시화방조제로 안내할까요?"]),
        ],
        200,
    );
    let backend = FakeBackend::new();
    let agent = server.agent(backend.clone(), llm_only());
    let r = ask(&agent, "바다 보러 갈 만한 코스", parked());
    assert_eq!(course_lists(&r)[0].0, vec!["r-incheon", "r-sihwa"]);
    let b = server.bodies();
    let env: Value = serde_json::from_str(last_msg(&b[1])["content"].as_str().unwrap()).unwrap();
    assert_eq!(env["data"]["courses"][0]["title"], "인천대교 바다 위 드라이브");
    assert_eq!(env["data"]["courses"][0]["n"], 1);
    assert!(r.proposals().is_empty(), "courses는 목록만, 제안은 nav로");

    // 다음 질문: [상황]에 목록이 들어가고, nav pick은 제안만
    let r = ask(&agent, "두 번째 걸로 해줘", parked());
    let user = last_msg(&server.bodies()[2])["content"].as_str().unwrap().to_string();
    assert!(user.contains("추천 코스 목록") && user.contains("시화방조제 바닷길"), "{user}");
    let p = r.proposals().pop().expect("제안");
    assert_eq!(shared(&p).0, "r-sihwa");
    assert_two_plain_legs(shared(&p).1, "오이도", "대부도 방아머리");
    assert_eq!(course_lists(&r).last().unwrap().1, 1);
    assert!(r.events().iter().all(|e| !matches!(e, Ev::Confirmed(..))));
    let b = server.bodies();
    assert!(last_msg(&b[3])["content"].as_str().unwrap().contains("운전자 확인 대기"));
}

/// 작은 모델이 nav 없이 "…로 안내해 드릴까요?"라고만 하면, 답에 나온 길 하나로 제안을 만든다 (실행은 여전히 확인 뒤).
#[test]
fn llm_asking_without_nav_still_gets_a_confirmable_proposal() {
    let server = fake_server(vec![sse_text(&["가장 짧은 건 1번 '강변북로 한강 야경 드라이브'예요. 안내해 드릴까요?"])], 200);
    let agent = server.agent(FakeBackend::new(), AgentOptions::default());
    ask(&agent, "여기 근처 드라이브 추천코스", parked());
    ask(&agent, "2번으로 가자", parked());
    let r = ask(&agent, "그 중에 제일 짧게 다녀올 수 있는 걸로 안내해줘", parked());
    assert_eq!(r.done().source, AnswerSource::Llm);
    let p = r.proposals().pop().expect("안전망 제안");
    assert_eq!(shared(&p).0, "r-gangbyeon");
    assert_eq!(course_lists(&r).last().unwrap().1, 0);
    assert!(r.events().iter().all(|e| !matches!(e, Ev::Confirmed(..))));
    let r = ask(&agent, "응", parked());
    assert!(matches!(&r.events()[0], Ev::Confirmed(_, NavAction::SharedCourse { post_id, .. }) if post_id == "r-gangbyeon"));
}

#[test]
fn nav_pick_without_list_is_an_error_for_the_model() {
    let server = fake_server(vec![tool("c1", "nav", r#"{"mode":"pick","n":1}"#), sse_text(&["먼저 찾아볼게요."])], 200);
    let r = ask(&server.agent(FakeBackend::new(), llm_only()), "1번 코스로 가자", parked());
    let env: Value = serde_json::from_str(last_msg(&server.bodies()[1])["content"].as_str().unwrap()).unwrap();
    assert_eq!(env["ok"], false);
    assert!(env["error"].as_str().unwrap().contains("courses"));
    assert!(r.proposals().is_empty());
}

/// 빠른 길 지연 (기기 검색은 가짜라 규칙·말 만들기 비용만 잰다).
#[test]
fn course_fast_path_latency() {
    let backend = FakeBackend::new();
    let mut times = vec![];
    for _ in 0..50 {
        let agent = no_llm(backend.clone());
        let t0 = Instant::now();
        ask(&agent, "여기 근처 드라이브 추천코스 안내해줘", parked());
        ask(&agent, "2번으로 가자", parked());
        times.push(t0.elapsed().as_micros() as u64);
    }
    times.sort();
    println!("추천 + 후속 두 질문 p50={}µs max={}µs", times[times.len() / 2], times[times.len() - 1]);
    assert!(times[times.len() / 2] < 20_000);
}

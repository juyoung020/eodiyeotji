//! 근처 드라이브 추천 코스 테스트: 말 알아듣기(규칙), 빠른 길 답·목록·제안, 후속 말("2번으로 가자"·"더 가까운 데"·"바다 쪽"),
//! 확인 전 실행 금지, LLM 도구(courses·nav pick), 빠른 길 지연.

use std::time::Instant;

use serde_json::Value;

use super::courses::{self, detect, shared_route, CourseIntent};
use super::tests::{ask, cfg, ctx, fake_server, last_msg, llm_only, sse_text, tool, Ev, FakeBackend, Recorder};
use super::*;
use crate::community::CoursePost;

// ---------------------------------------------------------------- 가짜 커뮤니티 코스 (지금 위치 = 서울 목동)

fn stop(name: &str, cat: &str, region: &str, lat: f64, lon: f64) -> StopFact {
    StopFact { name: name.into(), category: cat.into(), address: region.into(), arrive_ms: 0, leave_ms: 0, lat, lon, first_visit: false }
}

#[allow(clippy::too_many_arguments)]
fn post(id: &str, title: &str, author: &str, region: &str, tags: &[&str], hearts: u32, km: f64, hours: f64, stops: &[(&str, &str, f64, f64)]) -> CoursePost {
    CoursePost {
        id: id.into(), title: title.into(), body: format!("{title} 코스예요."), author: author.into(), mine: false, region: region.into(),
        tags: tags.iter().map(|s| s.to_string()).collect(),
        stops: stops.iter().map(|(n, c, la, lo)| stop(n, c, region, *la, *lo)).collect(),
        distance_m: km * 1000.0, duration_ms: (hours * 3_600_000.0) as i64, created_ms: 0, hearts, hearted_by_me: false,
        visited_day: "2025-10-25".into(), season: "가을 · 단풍철".into(), weather: Some("맑음 · 14°C".into()),
    }
}

pub(super) fn posts() -> Vec<CoursePost> {
    vec![
        post("c-yp", "양평 두물머리 한 바퀴", "강바람", "경기 양평군", &["강", "카페", "드라이브"], 312, 92.0, 5.5,
            &[("두물머리", "관광명소", 37.5330, 127.3140), ("세미원", "정원", 37.5385, 127.3175), ("구벼울 카페", "카페", 37.5420, 127.3260)]),
        post("c-eul", "을왕리 노을 해안 드라이브", "노을러", "인천 중구", &["바다", "노을", "조개구이"], 245, 88.0, 6.0,
            &[("을왕리해수욕장", "해수욕장", 37.4475, 126.3722), ("선녀바위해변", "해변", 37.4434, 126.3807), ("마시안해변", "해변", 37.4140, 126.4250)]),
        post("c-bukak", "북악스카이웨이 야경 코스", "서울밤", "서울 종로구", &["야경", "서울", "드라이브"], 198, 31.0, 2.5,
            &[("북악스카이웨이 팔각정", "전망대", 37.6020, 126.9820), ("부암동 카페거리", "카페", 37.5925, 126.9625)]),
        post("c-nhss", "남한산성 한 바퀴 백숙", "산타는곰", "경기 광주시", &["산", "백숙", "맛집"], 150, 64.0, 4.0,
            &[("남한산성 북문", "산성", 37.4800, 127.1830), ("남한산성 닭백숙 거리", "음식점", 37.4785, 127.1800)]),
        post("c-sihwa", "시화방조제 바닷길", "서해바다", "경기 안산시", &["바다", "방조제"], 90, 70.0, 3.5,
            &[("시화나래 휴게소", "휴게소", 37.3110, 126.6060), ("방아머리해변", "해수욕장", 37.2890, 126.5800)]),
        post("c-gn", "강릉 바다 순두부 당일치기", "바다좋아", "강원 강릉시", &["바다", "순두부", "여행"], 520, 470.0, 12.0,
            &[("경포해변", "해수욕장", 37.8055, 128.9089), ("초당순두부마을", "두부전문점", 37.7913, 128.9147), ("안목해변 카페거리", "카페", 37.7722, 128.9479)]),
        post("c-sc", "속초 설악 단풍 드라이브", "단풍길", "강원 속초시", &["산", "단풍", "시장"], 410, 420.0, 11.0,
            &[("설악산 소공원", "국립공원", 38.1720, 128.4880), ("속초중앙시장", "전통시장", 38.2040, 128.5900)]),
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
        other => panic!("공유 코스 제안이 아님: {other:?}"),
    }
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
    assert_eq!(detect("3번은 어디 들러?", true, 3), pick(2, false));
    assert_eq!(detect("첫 번째 코스로 안내해줘", true, 3), pick(0, true));
    assert_eq!(detect("마지막 거 어때", true, 3), pick(2, false));
    assert_eq!(detect("2번 코스 알려줘", true, 3), pick(1, false), "번호가 새 추천보다 먼저");
    assert_eq!(detect("더 가까운 데", true, 3), Some(CourseIntent::Closer));
    assert_eq!(detect("그거 말고 바다 쪽", true, 3), Some(CourseIntent::Theme { q: vec!["바다".into()] }));
    assert_eq!(detect("바다 쪽은?", true, 3), Some(CourseIntent::Theme { q: vec!["바다".into()] }));
    assert_eq!(detect("다른 거 없어?", true, 3), Some(CourseIntent::Other));
    assert_eq!(detect("어디 들러?", true, 3), Some(CourseIntent::Describe));
    assert_eq!(detect("그 코스로 가자", true, 3), Some(CourseIntent::Pick { index: None, go: true }));
    // 목록이 있어도 기억 질문은 기억으로
    assert_eq!(detect("작년 여름에 갔던 바다 어디였지?", true, 3), None);
    assert_eq!(courses::ordinal("5번으로가자", 3), Some(4));
}

// ---------------------------------------------------------------- 빠른 길

#[test]
fn nearby_courses_fast_path_lists_top3_and_only_proposes() {
    let server = fake_server(vec![sse_text(&["쓰이면 안 된다"])], 200);
    let backend = FakeBackend::new();
    let agent = server.agent(backend.clone(), AgentOptions::default());
    let r = ask(&agent, "여기 근처 드라이브 추천코스 안내해줘", parked());

    assert!(server.bodies().is_empty(), "LLM을 부르지 않는다");
    let ev = r.events();
    assert!(matches!(&ev[0], Ev::Courses(ids, 0) if ids == &["c-bukak", "c-yp", "c-eul"]), "목록이 답보다 먼저: {ev:?}");
    let a = r.answer();
    assert!(a.starts_with("근처 인기 코스 3개예요. 1번 '북악스카이웨이 야경 코스'(♥ 198, 여기서 1"), "{a}");
    assert!(a.contains("2번 '양평 두물머리 한 바퀴'(♥ 312, ") && a.ends_with("1번으로 안내할까요?"), "{a}");
    let p = r.proposals().pop().expect("1번 안내 제안");
    let (id, legs) = shared(&p);
    assert_eq!(id, "c-bukak");
    assert!(legs.iter().all(|l| l.waypoints.len() <= 2));
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
    assert_eq!(course_lists(&r), vec![(vec!["c-bukak".into(), "c-yp".into(), "c-eul".into()], 1)]);
    assert_eq!(r.answer(), "2번 '양평 두물머리 한 바퀴'로 안내할까요?");
    assert!(r.events().iter().all(|e| !matches!(e, Ev::Confirmed(..))));
    assert_eq!(shared(&r.proposals()[0]).0, "c-yp");
    assert_eq!(r.done().source, AnswerSource::FastPath);

    // "3번은 어디 들러?" → 들른 곳·거리·시간 + 3번 제안
    let r = ask(&agent, "3번은 어디 들러?", parked());
    let a = r.answer();
    assert!(a.starts_with("3번 '을왕리 노을 해안 드라이브'는 을왕리해수욕장, 선녀바위해변, 마시안해변에 들러요.") && a.contains("전체 88km, 6시간쯤"), "{a}");
    assert_eq!(shared(&r.proposals()[0]).0, "c-eul");

    // "더 가까운 데" (지금 3번 을왕리, 약 44km) → 그보다 가까운 코스를 가까운 순으로
    let r = ask(&agent, "더 가까운 데", parked());
    let lists = course_lists(&r);
    assert_eq!(lists[0].0, vec!["c-bukak", "c-nhss", "c-sihwa"], "{lists:?}");
    assert!(r.answer().starts_with("더 가까운 코스 3개예요. 1번 '북악스카이웨이 야경 코스'"), "{}", r.answer());
    // 가장 가까운 것에서 다시 "더 가까운 데" → 없다고
    let r = ask(&agent, "더 가까운 데", parked());
    assert!(r.answer().starts_with("근처에서는 '북악스카이웨이 야경 코스'가 가장 가까운 추천 코스예요."), "{}", r.answer());

    // "그거 말고 바다 쪽" → 바다 코스만 (지금 것 빼고)
    let r = ask(&agent, "그거 말고 바다 쪽", parked());
    assert_eq!(course_lists(&r)[0].0, vec!["c-eul", "c-sihwa"]);
    assert!(r.answer().starts_with("근처 '바다' 코스 2개예요. 1번 '을왕리 노을 해안 드라이브'"), "{}", r.answer());

    // "다른 거" → 다음 번호
    let r = ask(&agent, "다른 거 없어?", parked());
    assert!(r.answer().starts_with("그럼 2번 '시화방조제 바닷길'은 어떠세요? ♥ 90, 여기서 "), "{}", r.answer());
    let p = r.proposals()[0].clone();

    // 목록 밖 번호
    let r = ask(&agent, "5번으로 가자", parked());
    assert_eq!(r.answer(), "목록에는 2번까지 있어요. 몇 번으로 할까요?");
    assert!(r.proposals().is_empty());

    // "응" → 그때서야 실행 (마지막 제안 = 시화방조제)
    let r = ask(&agent, "응", parked());
    match &r.events()[0] {
        Ev::Confirmed(id, NavAction::SharedCourse { post_id, legs, .. }) => {
            assert_eq!((id, post_id.as_str()), (&p.id, "c-sihwa"));
            assert!(legs.iter().all(|l| l.waypoints.len() <= 2));
        }
        e => panic!("{e:?}"),
    }
    assert_eq!(r.done().source, AnswerSource::Confirm);
    assert!(agent.confirm(p.id).is_err(), "두 번 실행하지 않는다");
}

#[test]
fn driving_answer_is_one_short_sentence_about_the_top_course() {
    let agent = no_llm(FakeBackend::new());
    let r = ask(&agent, "근처 드라이브 코스 추천해줘", ctx(true));
    let a = r.answer();
    assert!(a.starts_with("근처 인기 코스 1위는 '북악스카이웨이 야경 코스'예요. 여기서 1") && a.ends_with("♥ 198. 안내할까요?"), "{a}");
    assert!(a.chars().count() < 70, "{a}");
    assert_eq!(course_lists(&r)[0].0.len(), 3, "화면 목록은 그대로 (운전 중 화면이 1위만 크게 보인다)");
}

#[test]
fn no_location_far_theme_and_empty_cases() {
    // 위치를 모르면 전국 하트순, 거리 없이
    let agent = no_llm(FakeBackend::new());
    let r = ask(&agent, "인기 코스 알려줘", AgentContext { current_location: None, ..parked() });
    assert!(r.answer().starts_with("지금 위치를 몰라서 전국 인기 코스 3개예요. 1번 '강릉 바다 순두부 당일치기'(♥ 520), 2번 '속초 설악 단풍 드라이브'(♥ 410)"), "{}", r.answer());

    // 근처에 없는 지역 → 전국에서 찾고 거리는 지금 위치에서 잰다
    let agent = no_llm(FakeBackend::new());
    let r = ask(&agent, "강릉 쪽 드라이브 코스 추천해줘", parked());
    assert!(r.answer().starts_with("근처 50km 안에는 없어서 전국 '강릉' 인기 코스 1개예요. 1번 '강릉 바다 순두부 당일치기'(♥ 520, 여기서 1"), "{}", r.answer());
    assert_eq!(course_lists(&r)[0].0, vec!["c-gn"]);

    // 없는 지역 → 근처 인기 코스로 대신
    let agent = no_llm(FakeBackend::new());
    let r = ask(&agent, "제주 쪽 드라이브 코스 추천해줘", parked());
    assert!(r.answer().starts_with("'제주' 코스는 아직 없어서 대신 근처 인기 코스 3개예요."), "{}", r.answer());

    // 공유된 코스가 하나도 없으면 짧게, 제안 없음
    let backend = Arc::new(FakeBackend { posts: vec![], ..Default::default() });
    let agent = no_llm(backend);
    let r = ask(&agent, "여기 근처 드라이브 추천코스", parked());
    assert_eq!(r.answer(), "아직 공유된 드라이브 코스가 없어요.");
    assert!(r.proposals().is_empty() && course_lists(&r).is_empty());
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

#[test]
fn shared_route_keeps_author_order_but_starts_from_nearer_end() {
    let s = |n: &str, lat: f64| stop(n, "", "", lat, 127.0);
    let stops = vec![s("A", 37.0), s("B", 37.1), s("C", 37.2), s("D", 37.3)];
    let near_d = GeoPoint { lat: 37.35, lon: 127.0 };
    let r = shared_route(&stops, Some(near_d));
    assert_eq!(r.stops.iter().map(|x| x.name.as_str()).collect::<String>(), "DCBA");
    assert_eq!(r.legs.len(), 2);
    assert!(r.legs.iter().all(|l| l.waypoints.len() <= 2));
    assert_eq!(r.legs[0].destination.name, "B");
    let r = shared_route(&stops, Some(GeoPoint { lat: 36.9, lon: 127.0 }));
    assert_eq!(r.stops.iter().map(|x| x.name.as_str()).collect::<String>(), "ABCD");
    assert_eq!(shared_route(&stops, None).stops[0].name, "A");
}

// ---------------------------------------------------------------- LLM 도구

#[test]
fn llm_courses_tool_and_nav_pick() {
    let server = fake_server(
        vec![
            tool("c1", "courses", r#"{"q":"바다"}"#),
            sse_text(&["근처 바다 코스는 1번 을왕리예요. 안내할까요?"]),
            tool("c2", "nav", r#"{"mode":"pick","n":2}"#),
            sse_text(&["2번 시화방조제로 안내할까요?"]),
        ],
        200,
    );
    let backend = FakeBackend::new();
    let agent = server.agent(backend.clone(), llm_only());
    let r = ask(&agent, "바다 보러 갈 만한 코스", parked());
    assert_eq!(course_lists(&r)[0].0, vec!["c-eul", "c-sihwa"]);
    let b = server.bodies();
    let env: Value = serde_json::from_str(last_msg(&b[1])["content"].as_str().unwrap()).unwrap();
    assert_eq!(env["data"]["courses"][0]["title"], "을왕리 노을 해안 드라이브");
    assert_eq!(env["data"]["courses"][0]["n"], 1);
    assert!(r.proposals().is_empty(), "courses는 목록만, 제안은 nav로");

    // 다음 질문: [상황]에 목록이 들어가고, nav pick은 제안만
    let r = ask(&agent, "두 번째 걸로 해줘", parked());
    let user = last_msg(&server.bodies()[2])["content"].as_str().unwrap().to_string();
    assert!(user.contains("추천 코스 목록") && user.contains("시화방조제 바닷길"), "{user}");
    let p = r.proposals().pop().expect("제안");
    assert_eq!(shared(&p).0, "c-sihwa");
    assert_eq!(course_lists(&r).last().unwrap().1, 1);
    assert!(r.events().iter().all(|e| !matches!(e, Ev::Confirmed(..))));
    let b = server.bodies();
    assert!(last_msg(&b[3])["content"].as_str().unwrap().contains("운전자 확인 대기"));
}

/// 작은 모델이 nav 없이 "…로 안내해 드릴까요?"라고만 하면, 답에 나온 코스 하나로 제안을 만든다 (실행은 여전히 확인 뒤).
#[test]
fn llm_asking_without_nav_still_gets_a_confirmable_proposal() {
    let server = fake_server(vec![sse_text(&["가장 짧은 건 2번 '양평 두물머리 한 바퀴'예요. 안내해 드릴까요?"])], 200);
    let agent = server.agent(FakeBackend::new(), AgentOptions::default());
    ask(&agent, "여기 근처 드라이브 추천코스", parked());
    let r = ask(&agent, "그 중에 제일 짧게 다녀올 수 있는 걸로 안내해줘", parked());
    assert_eq!(r.done().source, AnswerSource::Llm);
    let p = r.proposals().pop().expect("안전망 제안");
    assert_eq!(shared(&p).0, "c-yp");
    assert_eq!(course_lists(&r).last().unwrap().1, 1);
    assert!(r.events().iter().all(|e| !matches!(e, Ev::Confirmed(..))));
    let r = ask(&agent, "응", parked());
    assert!(matches!(&r.events()[0], Ev::Confirmed(_, NavAction::SharedCourse { post_id, .. }) if post_id == "c-yp"));
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

/// 빠른 길 지연 (기기 검색은 가짜라 규칙·말 만들기·경로 짜기 비용만 잰다).
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

use super::*;

fn gp(lat: f64, lon: f64) -> GeoPoint {
    GeoPoint { lat, lon }
}

fn place(name: &str, cat: RoadsideCategory, lat: f64, lon: f64, one_way: bool) -> RoadsidePlace {
    RoadsidePlace { name: name.into(), category: cat, lat, lon, address: String::new(), one_way, source: "test".into(), food: String::new() }
}

/// 남쪽으로 곧게 가는 길 (경도 127.1, 위도 37.4 → 36.9, 약 55 km).
fn south_route() -> Vec<GeoPoint> {
    (0..=50).map(|i| gp(37.4 - i as f64 * 0.01, 127.1)).collect()
}

/// 경도 0.001도 ≈ 88 m (위도 37도)
const E100: f64 = 0.00113;

#[test]
fn need_understands_common_phrases() {
    assert_eq!(need("나 똥마려").unwrap().kind, RoadsideNeedKind::Toilet);
    assert_eq!(need("화장실 급해").unwrap().categories[0], RoadsideCategory::RestArea);
    assert_eq!(need("아 졸려").unwrap().categories[0], RoadsideCategory::DrowsyShelter);
    assert_eq!(need("배고파").unwrap().kind, RoadsideNeedKind::Hungry);
    assert_eq!(need("커피 마시고 싶어").unwrap().kind, RoadsideNeedKind::Coffee);
    assert_eq!(need("기름 넣어야 돼").unwrap().kind, RoadsideNeedKind::Fuel);
    assert_eq!(need("휴게소 들렀다 가자").unwrap().kind, RoadsideNeedKind::Rest);
}

#[test]
fn need_leaves_memory_questions_and_others_alone() {
    assert!(need("작년에 들렀던 휴게소 어디였지").is_none());
    assert!(need("지난주 카페 어디였지").is_none());
    assert!(need("바다 가자").is_none());
    assert!(need("").is_none());
}

#[test]
fn rank_keeps_only_places_ahead_sorted_by_distance() {
    let here = gp(37.35, 127.1);
    let c = vec![
        place("뒤쪽", RoadsideCategory::RestArea, 37.38, 127.1 + E100, false),
        place("멀리", RoadsideCategory::RestArea, 37.0, 127.1 + E100, false),
        place("가까이", RoadsideCategory::RestArea, 37.30, 127.1 + E100, false),
    ];
    let hits = rank(&south_route(), here, None, Some(100.0), c, 2_000.0, 5);
    let names: Vec<&str> = hits.iter().map(|h| h.place.name.as_str()).collect();
    assert_eq!(names, vec!["가까이", "멀리"]);
    // 약 5.5 km 앞, 100 km/h → 4분
    assert!((hits[0].along_m - 5_530.0).abs() < 150.0, "{}", hits[0].along_m);
    assert_eq!(hits[0].eta_min, 4);
}

#[test]
fn rank_drops_opposite_direction_rest_area_on_the_left() {
    // 남쪽으로 갈 때 오른쪽 = 서쪽 (경도가 작은 쪽)
    let c = vec![
        place("휴게소 서울방향", RoadsideCategory::RestArea, 37.30, 127.1 + 2.0 * E100, true),
        place("휴게소 부산방향", RoadsideCategory::RestArea, 37.30, 127.1 - 2.0 * E100, true),
    ];
    let hits = rank(&south_route(), gp(37.35, 127.1), None, None, c, 2_000.0, 5);
    assert_eq!(hits.len(), 1);
    assert_eq!(hits[0].place.name, "휴게소 부산방향");
}

#[test]
fn rank_filters_by_detour() {
    let c = vec![place("먼 카페", RoadsideCategory::Cafe, 37.30, 127.1 + 30.0 * E100, false)];
    assert!(rank(&south_route(), gp(37.35, 127.1), None, None, c.clone(), 2_000.0, 5).is_empty());
    assert_eq!(rank(&south_route(), gp(37.35, 127.1), None, None, c, 8_000.0, 5).len(), 1);
}

#[test]
fn rank_uses_heading_when_there_is_no_route() {
    let c = vec![
        place("남쪽", RoadsideCategory::DrowsyShelter, 37.30, 127.1, false),
        place("북쪽", RoadsideCategory::DrowsyShelter, 37.40, 127.1, false),
    ];
    let hits = rank(&[], gp(37.35, 127.1), Some(180.0), None, c, 2_000.0, 5);
    assert_eq!(hits.len(), 1);
    assert_eq!(hits[0].place.name, "남쪽");
}

#[test]
fn rank_dedupes_same_place_from_two_sources() {
    let mut a = place("기흥휴게소 부산방향", RoadsideCategory::RestArea, 37.30, 127.1 - E100, true);
    let mut b = a.clone();
    b.lat += 0.0003;
    a.source = "기기 목록".into();
    b.source = "카카오".into();
    assert_eq!(rank(&south_route(), gp(37.35, 127.1), None, None, vec![a, b], 2_000.0, 5).len(), 1);
}

#[test]
fn probe_points_walk_ahead_on_route() {
    let pts = probe_points(&south_route(), gp(37.35, 127.1), None, 5_000.0, 3);
    assert_eq!(pts.len(), 3);
    assert!(pts.iter().all(|p| p.lat < 37.35));
    assert!(pts[0].lat > pts[1].lat && pts[1].lat > pts[2].lat);
    assert_eq!(probe_points(&[], gp(37.0, 127.0), None, 5_000.0, 3), vec![gp(37.0, 127.0)]);
}

#[test]
fn bundled_is_public_data_with_real_rows() {
    let rest = bundled(&[RoadsideCategory::RestArea]);
    // 표준데이터 215곳 중 화물차휴게소(21) 뺀 것
    assert_eq!(rest.len(), 194);
    // 실제 행: 기흥(부산), 경부선 하행, 37.2350/127.1046 근처
    let giheung = rest.iter().find(|p| p.name == "기흥휴게소 부산방향").expect("기흥");
    assert!(giheung.one_way && giheung.address == "경부고속도로" && giheung.source == BUNDLED_SOURCE);
    assert!((giheung.lat - 37.235).abs() < 0.01 && (giheung.lon - 127.104).abs() < 0.01, "{giheung:?}");
    // 양방향 휴게소(행담도)는 방향이 없다
    assert!(rest.iter().any(|p| p.name == "행담도휴게소" && !p.one_way));
    let shelters = bundled(&[RoadsideCategory::DrowsyShelter]);
    assert_eq!(shelters.len(), 253);
    // 실제 행: 오산 졸음쉼터 "서울기점 + 부산종점" → 부산방향
    assert!(shelters.iter().any(|p| p.name == "오산졸음쉼터 부산방향" && p.one_way));
    assert!(shelters.iter().all(|p| p.category == RoadsideCategory::DrowsyShelter));
    // 모든 좌표가 우리나라 안
    assert!(rest.iter().chain(&shelters).all(|p| (33.0..38.7).contains(&p.lat) && (124.5..131.0).contains(&p.lon)));
}

#[test]
fn bundled_maps_categories_to_amenity_flags() {
    use RoadsideCategory as C;
    // 화장실: 휴게소 + 졸음쉼터 (화장실유무 Y), 종류는 화장실로
    let toilets = bundled(&[C::Toilet]);
    assert_eq!(toilets.len(), 194 + 253);
    assert!(toilets.iter().all(|p| p.category == C::Toilet));
    // 충전: 전기차충전소유무 Y 휴게소만 (졸음쉼터 없음)
    let ev = bundled(&[C::EvCharger]);
    assert!(!ev.is_empty() && ev.len() <= 194);
    assert!(ev.iter().all(|p| p.name.contains("휴게소") && p.category == C::EvCharger));
    // 식당: 음식점유무 Y
    assert!(bundled(&[C::Food]).iter().all(|p| p.name.contains("휴게소")));
    // 카페는 표준데이터에 없다
    assert!(bundled(&[C::Cafe]).is_empty());
    // 두 종류를 찾으면 각각
    assert_eq!(bundled(&[C::RestArea, C::DrowsyShelter]).len(), 194 + 253);
}

#[test]
fn bundled_matches_reads_flags_and_skips_truck_stops() {
    let ok: Value = serde_json::from_str(r#"{"kind":"rest_area","type":"일반휴게소","toilet":true,"gas":true,"ev_charger":false,"restaurant":true,"store":true}"#).unwrap();
    let truck: Value = serde_json::from_str(r#"{"kind":"rest_area","type":"화물차휴게소","toilet":true,"gas":true}"#).unwrap();
    assert!(bundled_matches(&ok, RoadsideCategory::Gas));
    assert!(!bundled_matches(&ok, RoadsideCategory::EvCharger));
    assert!(!bundled_matches(&truck, RoadsideCategory::RestArea));
    let shelter: Value = serde_json::from_str(r#"{"dir":"순천기점 + 천안종점","toward":"천안"}"#).unwrap();
    assert!(bundled_one_way(&shelter));
    let both: Value = serde_json::from_str(r#"{"dir":"양방향","toward":""}"#).unwrap();
    assert!(!bundled_one_way(&both));
}

#[test]
fn direction_uses_public_toward_city() {
    // 북쪽으로 가는 길 위 "하남방향" 졸음쉼터 (중부선, 하남은 북쪽) → 같은 방향, "통영방향" → 반대
    let north: Vec<GeoPoint> = (0..=50).map(|i| gp(36.9 + i as f64 * 0.01, 127.4)).collect();
    let c = vec![
        place("곤지암졸음쉼터 하남방향", RoadsideCategory::DrowsyShelter, 37.1, 127.4 + 2.0 * E100, true),
        place("곤지암졸음쉼터 통영방향", RoadsideCategory::DrowsyShelter, 37.1, 127.4 - 2.0 * E100, true),
    ];
    let hits = rank(&north, gp(36.95, 127.4), None, None, c, 2_000.0, 5);
    assert_eq!(hits.iter().map(|h| h.place.name.as_str()).collect::<Vec<_>>(), vec!["곤지암졸음쉼터 하남방향"]);
}

#[test]
fn parse_kakao_reads_documents_and_marks_direction() {
    let j = r#"{"documents":[
        {"place_name":"기흥휴게소 부산방향","category_name":"교통,수송 > 휴게소 > 고속도로휴게소","x":"127.10457","y":"37.235019","road_address_name":"경기 용인시"},
        {"place_name":"기흥휴게소 부산방향 푸드코트","category_name":"음식점 > 푸드코트","x":"127.1045","y":"37.2364"}
    ]}"#;
    let p = parse_kakao(j, RoadsideCategory::RestArea);
    assert_eq!(p.len(), 1);
    assert!(p[0].one_way);
    assert_eq!(p[0].address, "경기 용인시");
    assert!(parse_kakao("nope", RoadsideCategory::Cafe).is_empty());
}

#[test]
fn motion_reads_heading_speed_and_drive_time() {
    let t0 = 1_000_000_000_000i64;
    // 30분 서 있다가(같은 자리) 40분 남쪽으로 달림 (1분에 1.5 km = 90 km/h)
    let mut fixes: Vec<(i64, Point)> = (0..30).map(|i| (t0 + i * 60_000, Point { lat: 37.4, lon: 127.1 })).collect();
    for i in 0..=40 {
        fixes.push((t0 + (30 + i) * 60_000, Point { lat: 37.4 - i as f64 * 0.0135, lon: 127.1 }));
    }
    let now = fixes.last().unwrap().0 + 5_000;
    let m = motion(&fixes, now);
    assert!((m.heading_deg.unwrap() - 180.0).abs() < 1.0);
    assert!((m.speed_kmh.unwrap() - 90.0).abs() < 5.0, "{:?}", m.speed_kmh);
    let d = m.driving_min.unwrap();
    assert!((39..=41).contains(&d), "{d}");
    assert_eq!(motion(&[], now).here, None);
}

#[test]
fn parse_call_validates_and_clamps() {
    assert_eq!(
        parse_call("search_along_route", r#"{"category":"toilet","limit":99,"max_detour_km":-3}"#),
        RoadsideCall::Search { category: RoadsideCategory::Toilet, max_detour_km: 0.2, limit: 5 }
    );
    assert!(matches!(parse_call("search_along_route", r#"{"category":"spa"}"#), RoadsideCall::Invalid { .. }));
    assert!(matches!(parse_call("search_along_route", "not json"), RoadsideCall::Invalid { .. }));
    assert_eq!(parse_call("get_drive_context", ""), RoadsideCall::DriveContext);
    assert_eq!(parse_call("propose_waypoint", r#"{"place_id":"p2"}"#), RoadsideCall::Waypoint { place_id: "p2".into() });
    assert!(matches!(parse_call("rm_rf", "{}"), RoadsideCall::Invalid { .. }));
}

#[test]
fn parse_json_call_finds_the_fallback_protocol() {
    let (n, a) = parse_json_call(r#"네, 찾아볼게요 {"tool": "search_along_route", "args": {"category": "rest_area"}}"#).unwrap();
    assert_eq!(n, "search_along_route");
    assert_eq!(parse_call(&n, &a), RoadsideCall::Search { category: RoadsideCategory::RestArea, max_detour_km: 2.0, limit: 3 });
    assert!(parse_json_call("그냥 답이에요 {not json}").is_none());
    assert!(parse_json_call(r#"{"x": "}"}"#).is_none());
}

#[test]
fn tool_specs_are_neutral_and_convert_to_openai() {
    let specs: Value = serde_json::from_str(&tool_specs()).unwrap();
    assert_eq!(specs.as_array().unwrap().len(), 5);
    let oa = openai_tools(&specs);
    assert_eq!(oa[1]["type"], "function");
    assert_eq!(oa[1]["function"]["name"], TOOL_SEARCH);
    assert_eq!(oa[1]["function"]["parameters"]["required"][0], "category");
}

fn hit(name: &str, cat: RoadsideCategory, along: f64, eta: u32) -> RoadsideHit {
    RoadsideHit { place: place(name, cat, 0.0, 0.0, false), along_m: along, detour_m: 200.0, eta_min: eta }
}

#[test]
fn pick_says_one_short_sentence_like_the_spec() {
    let hits = vec![hit("졸음쉼터A", RoadsideCategory::DrowsyShelter, 4_000.0, 3), hit("화성휴게소", RoadsideCategory::RestArea, 12_000.0, 8)];
    let p = pick(&[RoadsideCategory::RestArea, RoadsideCategory::DrowsyShelter], &hits, true);
    assert_eq!(p.line, "다음 화성휴게소까지 12km, 약 8분이에요. 졸음쉼터는 4km 앞이에요. 경유지로 넣을까요?");
    assert_eq!(p.primary.unwrap().place.name, "화성휴게소");
    assert_eq!(p.second.unwrap().place.name, "졸음쉼터A");
    let none = pick(&[RoadsideCategory::DrowsyShelter], &[], false);
    assert!(none.primary.is_none());
    assert_eq!(none.line, "가는 길 앞쪽에서 졸음쉼터를 찾지 못했어요.");
    // 다른 종류가 1순위보다 멀면 카드의 다른 선택으로만 (문장엔 말하지 않는다)
    let far = vec![hit("기흥휴게소", RoadsideCategory::RestArea, 8_400.0, 6), hit("안성휴게소", RoadsideCategory::RestArea, 34_900.0, 21), hit("오산졸음쉼터", RoadsideCategory::DrowsyShelter, 17_500.0, 11)];
    let p2 = pick(&[RoadsideCategory::RestArea, RoadsideCategory::DrowsyShelter], &far, true);
    assert_eq!(p2.second.unwrap().place.name, "오산졸음쉼터");
    assert_eq!(p2.line, "다음 기흥휴게소까지 8.4km, 약 6분이에요. 경유지로 넣을까요?");
    let jj = r#"{"documents":[{"place_name":"오산시운수종사자쉼터","category_name":"교통,수송 > 휴게소","x":"127.0","y":"37.1"}]}"#;
    assert!(parse_kakao(jj, RoadsideCategory::RestArea).is_empty());
    // 배고플 때 대표 음식이 있으면 짧게 덧붙인다
    let mut f = hit("안성휴게소 부산방향", RoadsideCategory::RestArea, 5_000.0, 4);
    f.place.food = "안성국밥, 우동".into();
    let hungry = pick(&[RoadsideCategory::RestArea, RoadsideCategory::Food], &[f.clone()], true);
    assert_eq!(hungry.line, "다음 안성휴게소 부산방향까지 5km, 약 4분이에요. 대표 음식은 안성국밥이에요. 경유지로 넣을까요?");
    assert!(!pick(&[RoadsideCategory::RestArea], &[f], true).line.contains("대표 음식"));
    let nav = pick(&[RoadsideCategory::Cafe], &[hit("카페B", RoadsideCategory::Cafe, 900.0, 1)], false);
    assert_eq!(nav.line, "다음 카페B까지 900m, 약 1분이에요. 안내할까요?");
}

#[test]
fn distance_words_are_readable() {
    assert_eq!(distance_words(940.0), "900m");
    assert_eq!(distance_words(4_000.0), "4km");
    assert_eq!(distance_words(4_260.0), "4.3km");
    assert_eq!(distance_words(12_400.0), "12km");
}

#[test]
fn rank_10k_route_is_fast() {
    let route: Vec<GeoPoint> = (0..10_000).map(|i| gp(37.4 - i as f64 * 0.00005, 127.1)).collect();
    let cands: Vec<RoadsidePlace> = bundled(&[RoadsideCategory::RestArea, RoadsideCategory::DrowsyShelter]);
    let t = std::time::Instant::now();
    let _ = rank(&route, gp(37.39, 127.1), None, None, cands, 2_000.0, 5);
    // 디버그 빌드 기준 넉넉히 (release는 수 ms)
    assert!(t.elapsed().as_millis() < 2_000, "{:?}", t.elapsed());
}

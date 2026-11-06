//! 시연용 드라이브 코스(경치 길) 10개. 길 모양은 `drive_routes.json` (OSRM으로 받은 실제 도로 선, 시작·끝·거리·시간),
//! 글쓴이·소개·언제·풍경·하트는 여기 [ROUTES]에 있다 (가상의 작성자, 그럴듯한 값).
//!
//! `meta`의 `drive_routes_seeded`로 한 번만 넣는다. 커뮤니티 시연을 버전 10 전에 넣은 기기도 다음 시연 넣기 때 길만 더 들어간다.
//! 이미 넣은 기기는 시연 넣기를 다시 부를 때 소개·언제·풍경 글만 지금 내용으로 맞춘다 (하트는 그대로).

use rusqlite::{params, Connection, OptionalExtension};
use serde_json::Value;

use super::demo::demo_weather;
use super::drive::{encode_highlights, encode_line, BestTime, RouteHighlight};
use crate::geo::Point;
use crate::store::{format_day, CoreError, EodiStore};

/// 실제 도로 선 (id, title, region, start, end, distance_m, duration_s, line).
pub(crate) const ROUTES_JSON: &str = include_str!("drive_routes.json");

const HOUR: i64 = 3_600_000;

/// (id, 글쓴이, 소개, 언제, 풍경(이름, 위도, 경도), 다른 사람 하트, 내가 누름, 다녀온 날(며칠 전)).
pub(crate) type RouteMeta = (&'static str, &'static str, &'static str, BestTime, &'static [(&'static str, f64, f64)], u32, bool, i64);

pub(crate) const ROUTES: &[RouteMeta] = &[
    ("d-gangbyeon-night", "야경헌터",
     "월드컵대교에서 광진교까지 한강을 오른쪽에 끼고 달리는 길이에요. 해 지고 30분쯤 지나 다리 조명이 켜질 때 출발하면 \
      반포대교 무지개분수 불빛과 남산타워가 차창 가득 들어와요. 퇴근 정체가 풀리는 밤 9시 넘어서가 가장 시원하게 달려요. \
      세워서 보고 싶으면 잠원·뚝섬 한강공원 주차장에 들어가세요.",
     BestTime::Night,
     &[("여의도 63빌딩", 37.5198, 126.9402), ("남산서울타워", 37.5512, 126.9882), ("반포대교 달빛무지개분수", 37.5110, 126.9960), ("서울숲", 37.5444, 127.0374)],
     486, true, 5),
    ("d-olympic-sunset", "노을수집가",
     "잠실에서 서쪽으로 달리면 해가 정면으로 지는 길이에요. 반포를 지날 즈음 강물이 주황빛으로 물들고, 여의도 빌딩 사이로 \
      해가 떨어져요. 일몰 40분 전에 잠실에서 출발하면 딱 맞아요. 눈이 부시니 선글라스를 챙기세요.",
     BestTime::Sunset,
     &[("잠실 롯데월드타워", 37.5126, 127.1025), ("반포대교 달빛무지개분수", 37.5110, 126.9960), ("노들섬", 37.5176, 126.9583), ("여의도 한강공원", 37.5284, 126.9330), ("선유도공원", 37.5435, 126.8990)],
     402, false, 9),
    ("d-incheon-bridge", "서해바람",
     "송도에서 영종도까지 바다 위로만 15km를 달려요. 주탑 사이를 지날 때 양쪽이 온통 바다라 비행기가 뜨는 기분이에요. \
      해 질 무렵엔 서해 노을이 다리 케이블에 걸리고, 돌아올 땐 송도 스카이라인이 반짝여요. 다리 위는 정차 금지라 \
      쉬려면 영종 쪽 인천대교 기념관 전망대로 가세요.",
     BestTime::Sunset,
     &[("송도 스카이라인", 37.4062, 126.6100), ("인천대교 주탑", 37.4139, 126.5664), ("영종도 해안", 37.4700, 126.5128)],
     377, false, 12),
    ("d-yeongjong-eurwang", "주말드라이버",
     "청라에서 영종대교를 건너 공항 옆 해안도로를 따라 을왕리까지 가는 길이에요. 영종대교 위에서 보는 갯벌과 바다가 넓고, \
      용유 쪽으로 들어서면 창밖이 바로 해변이에요. 해 지는 시간에 맞춰 을왕리에 닿게 출발하세요. 주말 저녁엔 입구가 막히니 조금 일찍.",
     BestTime::Sunset,
     &[("영종대교 위 서해 갯벌", 37.5400, 126.5661), ("선녀바위", 37.4400, 126.3745), ("을왕리해수욕장", 37.4472, 126.3725)],
     298, false, 20),
    ("d-jayuro-imjin", "북쪽으로",
     "가양대교에서 임진각까지 한강 하구를 따라 북쪽으로 쭉 뻗은 길이에요. 강 건너 철책이 이어지고, 이른 아침엔 강 위로 \
      물안개가 피어올라요. 신호가 거의 없어 정속으로 편하게 달려요. 끝의 임진각 평화누리 주차장에 세우고 바람개비 언덕을 걸어 보세요.",
     BestTime::Morning,
     &[("행주산성", 37.5970, 126.8280), ("한강 하구 철새 습지", 37.6689, 126.7402), ("임진각 평화누리", 37.8913, 126.7404)],
     214, false, 27),
    ("d-sihwa", "서해바다",
     "오이도에서 대부도까지 바다 한가운데 놓인 둑길이에요. 양쪽이 다 물이라 밀물·썰물에 따라 풍경이 달라져요. \
      노을 무렵 달전망대 뒤로 해가 지는 모습이 예뻐요. 중간 시화나래 휴게소에 세우고 전망대에 올라가 보세요.",
     BestTime::Sunset,
     &[("오이도 빨강등대", 37.3456, 126.6878), ("시화나래 조력공원 달전망대", 37.3105, 126.6085)],
     189, false, 33),
    ("d-route7", "바다좋아",
     "경포에서 하조대까지 바다를 오른쪽에 두고 달리는 7번 국도예요. 아침 7시쯤 출발하면 막 떠오른 해에 바다가 반짝여요. \
      주문진·남애항을 지날 땐 창문을 내리고 파도 소리를 들어 보세요. 바다 쪽 차로로 천천히 달리는 게 좋아요.",
     BestTime::Morning,
     &[("경포호", 37.7960, 128.9050), ("주문진항", 37.8935, 128.8290), ("소돌 아들바위", 37.9095, 128.8216), ("남애항", 37.9443, 128.7883)],
     533, false, 4),
    ("d-heonhwa", "해뜨는집",
     "정동진에서 금진항까지 차가 바다에 닿을 듯 붙어 달리는 해안길이에요. 바위 절벽과 파도가 바로 옆이라 창문을 열면 \
      물보라가 느껴져요. 해 뜰 때 정동진에서 출발하면 바다가 금빛이에요. 길이 좁으니 천천히, 쉬려면 심곡항 주차장에.",
     BestTime::Morning,
     &[("정동진 썬크루즈", 37.6855, 129.0418), ("심곡항", 37.6655, 129.0499), ("헌화로 바다 절벽길", 37.6560, 129.0520)],
     451, true, 14),
    ("d-daegwallyeong", "단풍놀이",
     "대관령 마을에서 강릉으로 내려가는 옛 영동고속도로 굽잇길이에요. 10월 중순부터 산 전체가 붉게 물들고, 굽이를 돌 때마다 \
      멀리 강릉 시내와 동해가 보여요. 커브가 많아 저단 기어로 천천히. 경치는 대관령 옛길 쉼터에 세우고 보세요.",
     BestTime::Foliage,
     &[("대관령 옛길 굽잇길", 37.7100, 128.7800), ("강릉 시내와 동해 전망", 37.7270, 128.8300)],
     276, false, 18),
    ("d-bukhangang-391", "양평주민",
     "두물머리에서 청평까지 북한강을 왼쪽에 끼고 달리는 391번 지방도예요. 4월이면 길 양쪽 벚꽃이 터널을 만들고 강물에 꽃잎이 \
      떠다녀요. 굽이마다 강이 다른 각도로 보여 지루할 틈이 없어요. 주말 오후엔 카페 앞이 붐비니 오전에 가세요.",
     BestTime::Blossom,
     &[("두물머리", 37.5344, 127.3189), ("물의정원", 37.5847, 127.3265), ("청평호", 37.7270, 127.4300)],
     368, false, 212),
];

/// 이미 넣은 시연 길의 소개·언제·풍경을 지금 내용으로 맞춘다 (하트·날짜·내가 누른 하트는 그대로). 넣는 수는 0.
fn refresh_text(conn: &Connection) -> Result<(), CoreError> {
    let mut st = conn.prepare_cached("UPDATE drive_route SET body = ?2, best_time = ?3, highlights = ?4 WHERE id = ?1 AND mine = 0")?;
    for &(id, _, body, best, highlights, ..) in ROUTES {
        let hl: Vec<RouteHighlight> = highlights.iter().map(|&(name, lat, lon)| RouteHighlight { name: name.into(), lat, lon }).collect();
        st.execute(params![id, body.split_whitespace().collect::<Vec<_>>().join(" "), best.key(), encode_highlights(&hl)])?;
    }
    Ok(())
}

/// json 한 줄 (길 모양).
pub(crate) struct RouteShape {
    pub id: String,
    pub title: String,
    pub region: String,
    pub start: String,
    pub end: String,
    pub distance_m: f64,
    pub duration_s: i64,
    pub line: Vec<Point>,
}

pub(crate) fn shapes() -> Vec<RouteShape> {
    let v: Value = serde_json::from_str(ROUTES_JSON).unwrap_or(Value::Null);
    v.as_array()
        .map(|a| {
            a.iter()
                .filter_map(|r| {
                    Some(RouteShape {
                        id: r["id"].as_str()?.to_string(),
                        title: r["title"].as_str()?.to_string(),
                        region: r["region"].as_str().unwrap_or("").to_string(),
                        start: r["start"].as_str()?.to_string(),
                        end: r["end"].as_str()?.to_string(),
                        distance_m: r["distance_m"].as_f64()?,
                        duration_s: r["duration_s"].as_i64()?,
                        line: r["line"]
                            .as_array()?
                            .iter()
                            .filter_map(|p| Some(Point { lat: p.get(0)?.as_f64()?, lon: p.get(1)?.as_f64()? }))
                            .collect(),
                    })
                })
                .collect()
        })
        .unwrap_or_default()
}

/// 시연 드라이브 코스를 넣는다 (한 번만). 반환: 넣은 수.
pub(super) fn seed(store: &EodiStore, conn: &Connection, now_ms: i64) -> Result<u32, CoreError> {
    let done: Option<i64> = conn.query_row("SELECT value FROM meta WHERE key = 'drive_routes_seeded'", [], |r| r.get(0)).optional()?;
    if done.is_some() {
        refresh_text(conn)?;
        return Ok(0);
    }
    let shapes = shapes();
    let mut n = 0u32;
    {
        let mut st = conn.prepare(
            "INSERT OR IGNORE INTO drive_route (id, title, body, author, mine, region, start_name, end_name, line, highlights, best_time,
                                                distance_m, duration_ms, created_ms, visited_day, weather, others_hearts, my_heart)
             VALUES (?1, ?2, ?3, ?4, 0, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17)",
        )?;
        for (i, &(id, author, body, best, highlights, hearts, my_heart, visit_days_ago)) in ROUTES.iter().enumerate() {
            let Some(s) = shapes.iter().find(|s| s.id == id) else { continue };
            // 다녀온 날 = 며칠 전, 올린 시각 = 그다음 날 저녁 무렵 (길마다 조금씩 다르게)
            let visited = store.local_day(now_ms) - visit_days_ago;
            let created = (store.day_start_ms(visited + 1) + (19 + (i as i64 * 5) % 4) * HOUR).min(now_ms - (i as i64 + 1) * 60_000);
            let day = format_day(visited);
            let hl: Vec<RouteHighlight> = highlights.iter().map(|&(name, lat, lon)| RouteHighlight { name: name.into(), lat, lon }).collect();
            // 문장 사이 줄 이음(\\)이 남긴 공백을 하나로
            let body = body.split_whitespace().collect::<Vec<_>>().join(" ");
            n += st.execute(params![
                id,
                s.title,
                body,
                author,
                s.region,
                s.start,
                s.end,
                encode_line(&s.line),
                encode_highlights(&hl),
                best.key(),
                s.distance_m,
                s.duration_s * 1000,
                created,
                day,
                demo_weather(&day, i + 5),
                hearts,
                my_heart as i64
            ])? as u32;
        }
    }
    conn.execute("INSERT OR REPLACE INTO meta (key, value) VALUES ('drive_routes_seeded', ?1)", [now_ms])?;
    Ok(n)
}

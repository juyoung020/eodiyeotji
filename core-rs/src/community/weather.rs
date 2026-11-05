//! 다녀온 날의 계절·날씨.
//!
//! - 계절은 날짜(월·일)로 정한다 ([season_label]). 3~5월 봄, 6~8월 여름, 9~11월 가을, 12~2월 겨울에
//!   철 이름이 맞는 때는 덧붙인다: 3/25~4/15 "봄 · 벚꽃철", 7/15~8/20 "여름 · 휴가철", 10/10~11/10 "가을 · 단풍철",
//!   12/20~2/10 "겨울 · 눈꽃철".
//! - 날씨는 공유·리뷰를 쓰는 순간 한 번 받아 저장한다. 네트워크 호출은 Kotlin이 하고(짧은 타임아웃, 실패하면 날씨 없이),
//!   여기서는 Open-Meteo 주소를 만들고([open_meteo_url]) 응답을 "맑음 · 14°C"로 바꾼다([parse_open_meteo_weather]).
//!   출처: Open-Meteo (무료, 키 없음). 지난 날은 archive API(5일쯤 늦게 채워진다), 최근 5일 안은 forecast API의 past_days.

use serde_json::Value;

use crate::store::parse_day;

/// 이 날수 안이면 forecast API(past_days), 넘으면 archive API.
pub const RECENT_DAYS: i64 = 5;

/// 날짜("YYYY-MM-DD")의 계절 이름 (모듈 설명). 형식이 틀리면 빈 문자열.
#[uniffi::export]
pub fn season_label(day: String) -> String {
    let Some((m, d)) = month_day(&day) else { return String::new() };
    let md = m * 100 + d;
    let base = match m {
        3..=5 => "봄",
        6..=8 => "여름",
        9..=11 => "가을",
        _ => "겨울",
    };
    let extra = match md {
        325..=415 => Some("벚꽃철"),
        715..=820 => Some("휴가철"),
        1010..=1110 => Some("단풍철"),
        1220..=1231 | 101..=210 => Some("눈꽃철"),
        _ => None,
    };
    match extra {
        Some(e) => format!("{base} · {e}"),
        None => base.to_string(),
    }
}

fn month_day(day: &str) -> Option<(u32, u32)> {
    parse_day(day)?;
    let mut it = day.split('-').skip(1);
    Some((it.next()?.parse().ok()?, it.next()?.parse().ok()?))
}

/// 다녀온 날 [day]의 일별 날씨를 받을 Open-Meteo 주소. [today]에서 [RECENT_DAYS]일 안이면 forecast(past_days),
/// 넘으면 archive. 날짜 형식이 틀리면 빈 문자열.
#[uniffi::export]
pub fn open_meteo_url(lat: f64, lon: f64, day: String, today: String) -> String {
    let (Some(d), Some(t)) = (parse_day(&day), parse_day(&today)) else { return String::new() };
    const DAILY: &str = "daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=Asia%2FSeoul";
    let ago = t - d;
    if (0..=RECENT_DAYS).contains(&ago) {
        format!(
            "https://api.open-meteo.com/v1/forecast?latitude={lat:.4}&longitude={lon:.4}&{DAILY}&past_days={}&forecast_days=1",
            ago + 1
        )
    } else {
        format!("https://archive-api.open-meteo.com/v1/archive?latitude={lat:.4}&longitude={lon:.4}&start_date={day}&end_date={day}&{DAILY}")
    }
}

/// WMO 날씨 코드 → 짧은 한국어.
pub fn wmo_text(code: i64) -> &'static str {
    match code {
        0 => "맑음",
        1 | 2 => "구름 조금",
        3 => "흐림",
        45 | 48 => "안개",
        51..=57 => "이슬비",
        61..=67 => "비",
        71..=77 | 85 | 86 => "눈",
        80..=82 => "소나기",
        95..=99 => "뇌우",
        _ => "흐림",
    }
}

/// Open-Meteo 일별 응답에서 [day]의 날씨를 "맑음 · 14°C"(낮 최고 기온)로. 그날 값이 없거나 JSON이 아니면 None.
#[uniffi::export]
pub fn parse_open_meteo_weather(json: String, day: String) -> Option<String> {
    let v: Value = serde_json::from_str(&json).ok()?;
    let daily = &v["daily"];
    let i = daily["time"].as_array()?.iter().position(|t| t.as_str() == Some(day.as_str()))?;
    let code = daily["weather_code"][i].as_f64()? as i64;
    let text = wmo_text(code);
    match daily["temperature_2m_max"][i].as_f64() {
        Some(t) => Some(format!("{text} · {}°C", t.round() as i64)),
        None => Some(text.to_string()),
    }
}

/// 저장할 날씨 글: 앞뒤 공백을 지우고 40자까지, 비면 None.
pub(crate) fn clean_weather(w: Option<String>) -> Option<String> {
    w.map(|w| w.trim().chars().take(40).collect::<String>()).filter(|w| !w.is_empty())
}

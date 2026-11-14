//! 시연용 1년치 기록 (`seed_demo_data`). 네트워크 없이, 같은 `today_ms`면 언제나 같은 기록이 나온다.
//!
//! # 내용 (오늘 = `today_ms`의 현지 날짜)
//! - 기간: 오늘 365일 전(또는 1년 전 같은 날짜 중 이른 쪽) ~ 어제. 오늘은 실제 기록용으로 비워 둔다.
//! - 집: 서울 성동구 응봉동, 회사: 서울 중구 을지로(센터원빌딩). 내비 즐겨찾기처럼 `set_home_office`로도 넣는다.
//! - 평일: 출근(07:45~08:20 출발)·퇴근(18:10~19:20 출발). 가끔 아침 스타벅스·주유소, 저녁 이마트·스타벅스,
//!   매달 첫 화요일 저녁 병원. 6%는 차를 안 쓴 날. 출장: 첫째 · 셋째 수요일 화성 남양연구소, 둘째 목요일 판교,
//!   분기 마지막 달 넷째 목요일 대전컨벤션센터 (회사 → 행선지 → 회사, `biztrip.rs`가 찾는다).
//! - 주말: 토요일 42%·일요일 20% 나들이(성수 카페거리 3곳·서울숲, 남산서울타워+광장시장, 여의도 한강공원+더현대,
//!   북촌+인사동, 롯데월드몰, 스타필드 하남, 이케아 광명, 북한산, 을왕리(여름), 남이섬+쁘띠프랑스(가을), 성북동 감자탕·광화문 국밥(겨울), 가평 계곡 물놀이(여름, 가족) — 계절에 맞는
//!   것 중 덜 간 곳부터, 같은 곳은 1년에 2번까지), 그 밖에는 이마트·주유소 볼일.
//! - 여행: 정확히 1년 전 같은 날짜에 강릉 가족 당일 여행 (집 → 횡성휴게소 → 경포해변 → 초당할머니순두부 →
//!   안목해변 카페거리 → 안목항 바다횟집 → 집), 8월 첫 금요일부터 2박 3일 부산 (해운대·감천문화마을·자갈치시장·광안리),
//!   10월 18일 이후 첫 토요일부터 1박 2일 설악산 단풍 (내린천휴게소 → 설악산 소공원 → 속초 숙소 → 속초관광수산시장).
//! - 크리스마스(시연 기간 안의 12월 25일): 휴일이라 출근 · 출장 없이 점심에 집 근처 순두부집(금호동 할머니순두부)에 둘이 다녀온다.
//! - 위치 점: 시내 1초, 고속도로(직선 30km 넘는 구간) 4초 간격, 도로처럼 휘는 길 + GPS 흔들림 2m. 주행마다 시작·끝 신호.
//!   2026-11-06 기준 1년치 약 39만 점, 주행 745개 (PC 릴리즈 빌드 0.3초, 디버그 1초).
//! - 장소 이름: 카카오처럼 이름·분류·지번 주소를 붙인다 (출처 Kakao).
//! - 배터리(전기차 흉내): 1km에 0.22%p, 30% 아래면 그날 아침 전에 90%로 충전. 주행마다 출발 · 도착 잔량.
//! - 탑승 인원(좌석 센서 흉내): 여행(강릉 · 부산 · 설악산)과 "가족" 나들이 4명, 주말 2명, 평일 혼자. 주행마다 출발 20초 뒤 한 번.
//! - LLM 라벨: 30일보다 오래된 날 중 라벨링이 필요한 날은 시나리오 제목·이야기·태그를 미리 넣는다 (시연 첫 실행에
//!   LLM을 수십 번 부르지 않도록). 최근 30일과 1년 전 강릉 날은 비워 두어 앱이 실제로 라벨링하는 모습을 보인다.
//!
//! # 실제 기록과 섞이지 않게
//! - `meta` 표에 `demo_seeded`(오늘 날짜 번호)·`demo_from_ms`·`demo_to_ms`를 남긴다. 이미 있으면 아무것도 하지 않고 0.
//! - 이미 위치 점이 있는 날(여러 날 여행이면 그중 하루라도)은 건너뛴다. 기존 기록은 지우지 않는다.

use std::collections::{BTreeMap, HashSet};

use rusqlite::{params, OptionalExtension};

use super::episode::{self, weekday_index};
use super::{DayEnrichment, EpisodeKind, EpisodeLabel};
use crate::geo::{haversine_m, Point};
use crate::places::{self, PlaceLabel, PlaceSource};
use crate::store::{days_from_civil, format_day, mark_dirty, CoreError, EodiStore, KIND_END, KIND_START};

const MIN: i64 = 60_000;
/// 시연 전기차가 1km에 쓰는 배터리 (%p): 77kWh · 약 6km/kWh
const SOC_PER_KM: f64 = 0.22;

/// 장소 (이름, 분류, 지번 주소, 위도, 경도).
type Poi = (&'static str, &'static str, &'static str, f64, f64);

const POIS: &[Poi] = &[
    ("응봉동 현대아파트", "아파트", "서울 성동구 응봉동 265", 37.5530, 127.0335),
    ("센터원빌딩", "빌딩", "서울 중구 수하동 67", 37.5672, 126.9844),
    ("이마트 성수점", "대형마트", "서울 성동구 성수동2가 333-16", 37.5395, 127.0531),
    ("스타벅스 왕십리역점", "카페", "서울 성동구 행당동 168-1", 37.5614, 127.0369),
    ("한양대학교병원", "종합병원", "서울 성동구 사근동 17", 37.5594, 127.0443),
    ("SK에너지 응봉주유소", "주유소", "서울 성동구 응봉동 100", 37.5520, 127.0405),
    ("대림창고", "카페", "서울 성동구 성수동2가 322-32", 37.5419, 127.0566),
    ("어니언 성수", "카페", "서울 성동구 성수동2가 277-135", 37.5447, 127.0583),
    ("블루보틀 성수", "카페", "서울 성동구 성수동1가 668-1", 37.5481, 127.0453),
    ("서울숲", "공원", "서울 성동구 성수동1가 678-1", 37.5444, 127.0374),
    ("남산서울타워", "관광명소", "서울 용산구 용산동2가 산1-3", 37.5512, 126.9882),
    ("광장시장", "전통시장", "서울 종로구 예지동 6-1", 37.5700, 126.9996),
    ("여의도한강공원", "공원", "서울 영등포구 여의도동 8", 37.5284, 126.9336),
    ("더현대 서울", "백화점", "서울 영등포구 여의도동 22", 37.5259, 126.9284),
    ("북촌한옥마을", "관광명소", "서울 종로구 계동 105", 37.5826, 126.9831),
    ("인사동 쌈지길", "관광명소", "서울 종로구 관훈동 38", 37.5743, 126.9853),
    ("롯데월드몰", "쇼핑몰", "서울 송파구 신천동 29", 37.5126, 127.1025),
    ("스타필드 하남", "쇼핑몰", "경기 하남시 신장동 750", 37.5453, 127.2237),
    ("이케아 광명점", "가구", "경기 광명시 일직동 507", 37.4231, 126.8826),
    ("북한산 우이탐방지원센터", "국립공원", "서울 강북구 우이동 262", 37.6620, 127.0120),
    ("을왕리해수욕장", "해수욕장", "인천 중구 을왕동 678-19", 37.4472, 126.3725),
    ("을왕리 조개구이촌", "조개구이", "인천 중구 을왕동 742", 37.4495, 126.3770),
    ("쁘띠프랑스", "관광명소", "경기 가평군 청평면 고성리 616", 37.7178, 127.4870),
    ("남이섬", "관광명소", "강원 춘천시 남산면 방하리 198", 37.8136, 127.5250),
    ("횡성휴게소", "휴게소", "강원 횡성군 우천면 정금리 133", 37.4700, 128.0300),
    ("경포해변", "해수욕장", "강원 강릉시 안현동 산1", 37.8055, 128.9083),
    ("초당할머니순두부", "두부전문점", "강원 강릉시 초당동 309-1", 37.7915, 128.9142),
    ("안목해변 카페거리", "카페거리", "강원 강릉시 견소동 286", 37.7722, 128.9474),
    ("안성휴게소", "휴게소", "경기 안성시 원곡면 외가천리 128", 37.0010, 127.1820),
    ("해운대해수욕장", "해수욕장", "부산 해운대구 우동 1411", 35.1587, 129.1604),
    ("파라다이스호텔 부산", "호텔", "부산 해운대구 중동 1408-5", 35.1600, 129.1650),
    ("감천문화마을", "관광명소", "부산 사하구 감천동 10-13", 35.0975, 129.0106),
    ("자갈치시장", "전통시장", "부산 중구 남포동4가 37-1", 35.0966, 129.0306),
    ("광안리해수욕장", "해수욕장", "부산 수영구 광안동 192-20", 35.1532, 129.1186),
    ("내린천휴게소", "휴게소", "강원 인제군 상남면 상남리 1093", 37.9470, 128.3180),
    ("설악산 소공원", "국립공원", "강원 속초시 설악동 산41", 38.1727, 128.4890),
    ("롯데리조트 속초", "호텔", "강원 속초시 대포동 763", 38.1695, 128.6080),
    ("속초관광수산시장", "전통시장", "강원 속초시 중앙동 471-4", 38.2046, 128.5905),
    ("태조감자국", "감자탕", "서울 성북구 성북동1가 104-5", 37.5895, 127.0065),
    ("광화문국밥", "국밥", "서울 중구 정동 1-19", 37.5680, 126.9745),
    ("용추계곡", "계곡", "경기 가평군 가평읍 승안리 산1", 37.8705, 127.4600),
    ("가평 막국수촌", "막국수", "경기 가평군 가평읍 읍내리 450", 37.8290, 127.5100),
    ("현대자동차 남양연구소", "연구소", "경기 화성시 남양읍 장덕리 772-1", 37.2012, 126.8295),
    ("판교테크노밸리 스타트업캠퍼스", "업무시설", "경기 성남시 분당구 삼평동 694", 37.4110, 127.0990),
    ("대전컨벤션센터", "컨벤션센터", "대전 유성구 도룡동 4-19", 36.3755, 127.3880),
    ("안목항 바다횟집", "횟집", "강원 강릉시 견소동 274", 37.7690, 128.9520),
    ("금호동 할머니순두부", "두부전문점", "서울 성동구 금호동2가 511", 37.5480, 127.0190),
];

const HOME: usize = 0;
const OFFICE: usize = 1;
const EMART: usize = 2;
const STARBUCKS: usize = 3;
const HOSPITAL: usize = 4;
const GAS: usize = 5;
const DAELIM: usize = 6;
const ONION: usize = 7;
const BLUEBOTTLE: usize = 8;
const SEOUL_FOREST: usize = 9;
const NAMSAN: usize = 10;
const GWANGJANG: usize = 11;
const YEOUIDO: usize = 12;
const HYUNDAI: usize = 13;
const BUKCHON: usize = 14;
const INSADONG: usize = 15;
const LOTTE_MALL: usize = 16;
const STARFIELD: usize = 17;
const IKEA: usize = 18;
const BUKHANSAN: usize = 19;
const EULWANGNI: usize = 20;
const JOGAE: usize = 21;
const PETITE: usize = 22;
const NAMI: usize = 23;
const HOENGSEONG: usize = 24;
const GYEONGPO: usize = 25;
const CHODANG: usize = 26;
const ANMOK: usize = 27;
const ANSEONG: usize = 28;
const HAEUNDAE: usize = 29;
const PARADISE: usize = 30;
const GAMCHEON: usize = 31;
const JAGALCHI: usize = 32;
const GWANGALLI: usize = 33;
const NAERINCHEON: usize = 34;
const SEORAK: usize = 35;
const SOKCHO_RESORT: usize = 36;
const SOKCHO_MARKET: usize = 37;
const GAMJATANG: usize = 38;
const GUKBAP: usize = 39;
const YONGCHU: usize = 40;
const MAKGUKSU: usize = 41;
const NAMYANG: usize = 42;
const PANGYO: usize = 43;
const DCC: usize = 44;
const SEAFOOD: usize = 45;
const SUNDUBU: usize = 46;

type Via = &'static [(f64, f64)];

const NO_VIA: Via = &[];
const COMMUTE_OUT: Via = &[(37.5585, 127.0300), (37.5655, 127.0080)];
const COMMUTE_BACK: Via = &[(37.5655, 127.0080), (37.5585, 127.0300)];
// 영동고속도로
const GN_OUT1: Via = &[
    (37.5450, 127.0900),
    (37.5300, 127.1350),
    (37.4700, 127.2400),
    (37.3900, 127.3500),
    (37.3300, 127.4700),
    (37.3300, 127.6300),
    (37.3400, 127.8200),
    (37.3900, 127.9300),
];
const GN_OUT2: Via = &[(37.5300, 128.1500), (37.5700, 128.3500), (37.6200, 128.5300), (37.6700, 128.6800), (37.6900, 128.7600), (37.7500, 128.8600)];
const GN_BACK: Via = &[
    (37.7500, 128.8600),
    (37.6900, 128.7600),
    (37.6700, 128.6800),
    (37.6200, 128.5300),
    (37.5700, 128.3500),
    (37.5300, 128.1500),
    (37.3900, 127.9300),
    (37.3400, 127.8200),
    (37.3300, 127.6300),
    (37.3300, 127.4700),
    (37.3900, 127.3500),
    (37.4700, 127.2400),
    (37.5300, 127.1350),
    (37.5450, 127.0900),
];
// 경부고속도로
const BS_OUT1: Via = &[(37.5400, 127.0450), (37.4900, 127.0400), (37.4300, 127.0700), (37.3200, 127.1000), (37.2000, 127.1100), (37.0900, 127.1500)];
const BS_OUT2: Via = &[
    (36.8000, 127.2500),
    (36.6000, 127.4000),
    (36.3700, 127.4300),
    (36.2000, 127.7000),
    (36.1000, 127.9500),
    (35.9500, 128.3500),
    (35.8800, 128.6000),
    (35.8300, 128.9000),
    (35.7500, 129.1500),
    (35.5000, 129.1300),
    (35.3200, 129.1200),
    (35.2200, 129.1300),
];
const BS_BACK1: Via = &[
    (35.2200, 129.1300),
    (35.3200, 129.1200),
    (35.5000, 129.1300),
    (35.7500, 129.1500),
    (35.8300, 128.9000),
    (35.8800, 128.6000),
    (35.9500, 128.3500),
    (36.1000, 127.9500),
    (36.2000, 127.7000),
    (36.3700, 127.4300),
    (36.6000, 127.4000),
    (36.8000, 127.2500),
];
const BS_BACK2: Via = &[(37.0900, 127.1500), (37.2000, 127.1100), (37.3200, 127.1000), (37.4300, 127.0700), (37.4900, 127.0400), (37.5400, 127.0450)];
// 서울양양고속도로
const SR_OUT1: Via = &[(37.5600, 127.0900), (37.6000, 127.1500), (37.6400, 127.3200), (37.7200, 127.5000), (37.7700, 127.7000), (37.8300, 127.9000), (37.8800, 128.1200)];
const SR_OUT2: Via = &[(37.9900, 128.4500), (38.0700, 128.6000), (38.1300, 128.5800)];
const SR_BACK: Via = &[
    (38.0700, 128.6000),
    (37.9900, 128.4500),
    (37.9470, 128.3180),
    (37.8800, 128.1200),
    (37.8300, 127.9000),
    (37.7700, 127.7000),
    (37.7200, 127.5000),
    (37.6400, 127.3200),
    (37.6000, 127.1500),
    (37.5600, 127.0900),
];
const NAMI_OUT: Via = &[(37.5600, 127.0900), (37.6000, 127.1500), (37.6400, 127.3200), (37.6900, 127.4500)];
const NAMI_BACK: Via = &[(37.7600, 127.5000), (37.6900, 127.4500), (37.6400, 127.3200), (37.6000, 127.1500), (37.5600, 127.0900)];
const EULWANG_OUT: Via = &[(37.5400, 127.0000), (37.5300, 126.9400), (37.5500, 126.8500), (37.5300, 126.7000), (37.5000, 126.5800), (37.4800, 126.4800)];
const EULWANG_BACK: Via = &[(37.4800, 126.4800), (37.5000, 126.5800), (37.5300, 126.7000), (37.5500, 126.8500), (37.5300, 126.9400), (37.5400, 127.0000)];

// 출장길 (회사 → 남서쪽 서해안고속도로 / 남쪽 경부고속도로)
const NAMYANG_OUT: Via = &[(37.5300, 126.9600), (37.4600, 126.8900), (37.3500, 126.8500), (37.2600, 126.8350)];
const NAMYANG_BACK: Via = &[(37.2600, 126.8350), (37.3500, 126.8500), (37.4600, 126.8900), (37.5300, 126.9600)];
const PANGYO_OUT: Via = &[(37.5300, 127.0100), (37.4800, 127.0400), (37.4400, 127.0800)];
const PANGYO_BACK: Via = &[(37.4400, 127.0800), (37.4800, 127.0400), (37.5300, 127.0100)];
const DCC_OUT: Via = &[(37.5300, 127.0100), (37.4300, 127.0700), (37.2000, 127.1100), (36.8000, 127.2500), (36.5000, 127.3600)];
const DCC_BACK: Via = &[(36.5000, 127.3600), (36.8000, 127.2500), (37.2000, 127.1100), (37.4300, 127.0700), (37.5300, 127.0100)];

/// 그 평일의 출장 (행선지, 가는 길, 오는 길, 머무는 분): 매달 첫째 · 셋째 수요일 화성 남양연구소, 둘째 목요일 판교,
/// 분기 마지막 달(3 · 6 · 9 · 12월) 넷째 목요일 대전컨벤션센터.
fn biz_trip_on(d: i64) -> Option<(usize, Via, Via, i64)> {
    let (_, m, dom) = civil(d);
    match (weekday_index(d), (dom - 1) / 7) {
        (3, 0) | (3, 2) => Some((NAMYANG, NAMYANG_OUT, NAMYANG_BACK, 150)),
        (4, 1) => Some((PANGYO, PANGYO_OUT, PANGYO_BACK, 120)),
        (4, 3) if m % 3 == 0 => Some((DCC, DCC_OUT, DCC_BACK, 240)),
        _ => None,
    }
}

/// 한 구간: (도착 장소, 지나는 점, 도착 뒤 머무는 분).
type Leg = (usize, Via, i64);

/// 주말 나들이 시나리오.
struct Outing {
    months: &'static [i64],
    depart_h: i64,
    via_out: Via,
    via_back: Via,
    stops: &'static [(usize, i64)],
    title: &'static str,
    summary: &'static str,
    tags: &'static [&'static str],
}

/// [OUTINGS]에서 보충 판 1([top_up])로 더한 첫 나들이 자리.
const FIRST_TOPUP_OUTING: usize = 12;

const ALL_YEAR: &[i64] = &[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

const OUTINGS: &[Outing] = &[
    Outing {
        months: ALL_YEAR,
        depart_h: 13,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(DAELIM, 90)],
        title: "성수 대림창고에서 느긋한 주말 커피",
        summary: "오후에 성수동 대림창고에 들러 커피 한 잔 하며 쉬었다.",
        tags: &["카페", "커피", "성수", "주말", "데이트"],
    },
    Outing {
        months: &[3, 4, 5, 6, 9, 10, 11],
        depart_h: 11,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(SEOUL_FOREST, 70), (ONION, 70)],
        title: "서울숲 산책하고 어니언 빵",
        summary: "서울숲을 한 바퀴 걷고 성수 어니언에서 빵과 커피를 먹었다.",
        tags: &["공원", "산책", "카페", "빵", "성수"],
    },
    Outing {
        months: ALL_YEAR,
        depart_h: 14,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(BLUEBOTTLE, 60)],
        title: "블루보틀 성수에서 핸드드립",
        summary: "성수 블루보틀에서 핸드드립 커피를 마셨다.",
        tags: &["카페", "커피", "성수"],
    },
    Outing {
        months: &[3, 4, 5, 9, 10, 11, 12],
        depart_h: 16,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(NAMSAN, 120), (GWANGJANG, 80)],
        title: "남산타워 야경 보고 광장시장 빈대떡",
        summary: "남산서울타워에서 서울 야경을 보고 광장시장에서 빈대떡과 마약김밥을 먹었다.",
        tags: &["야경", "전망", "시장", "빈대떡", "맛집", "데이트"],
    },
    Outing {
        months: &[4, 5, 6, 9, 10],
        depart_h: 11,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(YEOUIDO, 130), (HYUNDAI, 90)],
        title: "여의도 한강 피크닉",
        summary: "여의도 한강공원에서 돗자리 펴고 쉬다가 더현대 서울을 구경했다.",
        tags: &["한강", "공원", "피크닉", "쇼핑", "봄나들이"],
    },
    Outing {
        months: &[3, 4, 5, 9, 10, 11],
        depart_h: 10,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(BUKCHON, 100), (INSADONG, 60)],
        title: "북촌 한옥 골목 산책",
        summary: "북촌한옥마을 골목을 걷고 인사동 쌈지길을 둘러봤다.",
        tags: &["한옥", "산책", "전통", "골목"],
    },
    Outing {
        months: &[1, 2, 7, 8, 12],
        depart_h: 12,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(LOTTE_MALL, 160)],
        title: "롯데월드몰에서 실내 데이트",
        summary: "추위·더위를 피해 롯데월드몰에서 영화 보고 밥을 먹었다.",
        tags: &["쇼핑", "영화", "실내", "외식"],
    },
    Outing {
        months: &[1, 2, 6, 7, 8, 12],
        depart_h: 12,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(STARFIELD, 180)],
        title: "스타필드 하남 나들이",
        summary: "스타필드 하남에서 쇼핑하고 저녁까지 먹고 왔다.",
        tags: &["쇼핑", "실내", "외식"],
    },
    Outing {
        months: ALL_YEAR,
        depart_h: 11,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(IKEA, 150)],
        title: "이케아 광명에서 집 꾸미기 쇼핑",
        summary: "이케아 광명점에서 수납장을 고르고 미트볼을 먹었다.",
        tags: &["가구", "쇼핑", "집꾸미기", "인테리어"],
    },
    Outing {
        months: &[4, 5, 9, 10],
        depart_h: 8,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(BUKHANSAN, 240)],
        title: "북한산 아침 등산",
        summary: "아침 일찍 우이동에서 북한산에 올랐다.",
        tags: &["등산", "산", "운동", "북한산"],
    },
    Outing {
        months: &[6, 7, 8, 9],
        depart_h: 13,
        via_out: EULWANG_OUT,
        via_back: EULWANG_BACK,
        stops: &[(EULWANGNI, 120), (JOGAE, 80)],
        title: "을왕리 바다 노을과 조개구이",
        summary: "을왕리해수욕장에서 노을을 보고 조개구이를 먹었다.",
        tags: &["바다", "해변", "해수욕장", "노을", "조개구이", "맛집"],
    },
    Outing {
        months: &[10, 11],
        depart_h: 9,
        via_out: NAMI_OUT,
        via_back: NAMI_BACK,
        stops: &[(PETITE, 60), (NAMI, 150)],
        title: "남이섬 가을 단풍 나들이",
        summary: "쁘띠프랑스를 들렀다가 남이섬 은행나무길을 걸었다.",
        tags: &["단풍", "가을", "섬", "산책", "가족"],
    },
    // ---- 여기부터 보충 판 1에서 더한 나들이 ([FIRST_TOPUP_OUTING]) ----
    Outing {
        months: &[1, 2, 3, 10, 11, 12],
        depart_h: 18,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(GAMJATANG, 70)],
        title: "성북동 태조감자국에서 감자탕 저녁",
        summary: "쌀쌀한 저녁에 성북동 오래된 감자탕집에서 뼈해장국과 감자탕을 먹었다.",
        tags: &["감자탕", "뼈해장국", "맛집", "노포", "저녁", "한식"],
    },
    Outing {
        months: &[1, 2, 11, 12],
        depart_h: 11,
        via_out: NO_VIA,
        via_back: NO_VIA,
        stops: &[(GUKBAP, 50), (BUKCHON, 60)],
        title: "광화문 국밥 한 그릇, 북촌 한 바퀴",
        summary: "광화문국밥에서 돼지국밥으로 늦은 아침을 먹고 북촌 골목을 걸었다.",
        tags: &["국밥", "돼지국밥", "맛집", "한식", "산책"],
    },
    Outing {
        months: &[7, 8],
        depart_h: 9,
        via_out: NAMI_OUT,
        via_back: NAMI_BACK,
        stops: &[(YONGCHU, 180), (MAKGUKSU, 60)],
        title: "아이들과 가평 계곡 물놀이",
        summary: "아이들과 용추계곡에서 발 담그고 물놀이를 한 뒤 막국수를 먹고 돌아왔다.",
        tags: &["계곡", "물놀이", "여름", "가족", "아이", "막국수"],
    },
];

/// 결정적인 의사 난수.
struct Rng(u64);

impl Rng {
    fn next(&mut self) -> u64 {
        self.0 = self.0.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1_442_695_040_888_963_407);
        self.0 >> 33
    }

    fn below(&mut self, n: i64) -> i64 {
        (self.next() % n.max(1) as u64) as i64
    }

    fn unit(&mut self) -> f64 {
        self.next() as f64 / (1u64 << 31) as f64
    }

    fn chance(&mut self, percent: i64) -> bool {
        self.below(100) < percent
    }
}

fn poi(i: usize) -> Point {
    Point { lat: POIS[i].3, lon: POIS[i].4 }
}

/// 날짜 번호 → (연, 월, 일).
fn civil(d: i64) -> (i64, i64, i64) {
    let s = format_day(d);
    (s[0..4].parse().unwrap_or(1970), s[5..7].parse().unwrap_or(1), s[8..10].parse().unwrap_or(1))
}

fn days_in_month(y: i64, m: i64) -> i64 {
    let (ny, nm) = if m == 12 { (y + 1, 1) } else { (y, m + 1) };
    days_from_civil(ny, nm, 1) - days_from_civil(y, m, 1)
}

/// 시연 기간과 특별한 날.
pub(crate) struct Plan {
    pub first: i64,
    pub last: i64,
    /// 1년 전 같은 날짜 (강릉 당일 여행)
    pub gangneung: i64,
    /// 부산 2박 3일 첫날 (금요일)
    pub busan: Option<i64>,
    /// 설악산 1박 2일 첫날 (토요일)
    pub seorak: Option<i64>,
    /// 크리스마스 (시연 기간 안의 가장 최근 12월 25일): 집 근처 순두부집 점심
    pub christmas: Option<i64>,
}

pub(crate) fn plan(today: i64) -> Plan {
    let (y, m, d) = civil(today);
    let gangneung = days_from_civil(y - 1, m, d.min(days_in_month(y - 1, m)));
    let first = (today - 365).min(gangneung);
    let last = today - 1;
    let ly = civil(last).0;
    let first_weekday = |from: i64, wd: usize| (from..from + 7).find(|&x| weekday_index(x) == wd).unwrap_or(from);
    let free = |a: i64, b: i64, taken: &[(i64, i64)]| a >= first && b <= last && taken.iter().all(|&(x, y)| b < x || a > y);
    let busan = [ly, ly - 1]
        .into_iter()
        .map(|yy| first_weekday(days_from_civil(yy, 8, 1), 5))
        .find(|&f| free(f, f + 2, &[(gangneung, gangneung)]));
    let mut taken = vec![(gangneung, gangneung)];
    taken.extend(busan.map(|b| (b, b + 2)));
    let seorak = [ly, ly - 1]
        .into_iter()
        .map(|yy| first_weekday(days_from_civil(yy, 10, 18), 6))
        .find(|&s| free(s, s + 1, &taken));
    taken.extend(seorak.map(|s| (s, s + 1)));
    let christmas = [ly, ly - 1].into_iter().map(|yy| days_from_civil(yy, 12, 25)).find(|&x| free(x, x, &taken));
    Plan { first, last, gangneung, busan, seorak, christmas }
}

struct Label {
    title: String,
    summary: String,
    tags: Vec<String>,
}

struct Gen<'a> {
    store: &'a EodiStore,
    rng: Rng,
    fixes: Vec<(i64, f64, f64)>,
    events: Vec<(i64, i64)>,
    labels: BTreeMap<i64, Label>,
    trips: u32,
    outing_uses: Vec<u32>,
    /// 지금 만드는 하루에 탄 인원 (좌석 센서 흉내): 여행 = 가족 4명, 주말 = 2명, 평일 = 혼자
    people_now: u32,
    /// (읽은 시각, 인원) — 주행마다 출발 20초 뒤 한 번
    people: Vec<(i64, u32)>,
    /// 전기차 배터리 잔량 (%) 흉내: 1km에 0.22%p, 30% 아래로 내려가면 그날 밤 집에서 90%까지 충전
    soc: f64,
    /// (읽은 시각, 잔량) — 주행마다 출발 · 도착 한 번씩
    energy: Vec<(i64, f64)>,
    /// 주행거리계 (km) 흉내: GPS 거리보다 2% 길게 오른다
    odo: f64,
    /// 오늘 비가 오는가 (와이퍼 흉내): 강릉 당일치기 날 + 날짜로 정한 약 12%의 날
    rain_now: bool,
    /// (읽은 시각, 계기 코드, 값) — 주행마다 출발 · 도착 주행거리계, 비 오는 날은 와이퍼
    readings: Vec<(i64, i64, f64)>,
}

impl Gen<'_> {
    fn at(&self, d: i64, h: i64, m: i64) -> i64 {
        self.store.day_start_ms(d) + (h * 60 + m) * MIN
    }

    fn jitter(&mut self, p: Point, m: f64) -> Point {
        let (a, b) = (self.rng.unit() - 0.5, self.rng.unit() - 0.5);
        Point { lat: p.lat + a * 2.0 * m / 111_000.0, lon: p.lon + b * 2.0 * m / 88_000.0 }
    }

    fn label(&mut self, d: i64, title: &str, summary: &str, tags: &[&str]) {
        self.labels.insert(
            d,
            Label { title: title.into(), summary: summary.into(), tags: tags.iter().map(|t| t.to_string()).collect() },
        );
    }

    /// t0에 a에서 출발해 via를 지나 b까지 달린다 (도로처럼 휘게, 시작·끝 신호 포함). 반환: 도착 시각.
    fn drive(&mut self, t0: i64, a: Point, via: Via, b: Point) -> i64 {
        let mut ctrl = vec![a];
        ctrl.extend(via.iter().map(|&(lat, lon)| Point { lat, lon }));
        ctrl.push(b);
        let straight: f64 = ctrl.windows(2).map(|w| haversine_m(w[0], w[1])).sum();
        let highway = straight > 30_000.0;
        let bend_k = if highway { 0.06 } else { 0.2 };
        let mut dense = vec![a];
        for w in ctrl.windows(2) {
            let (p, q) = (w[0], w[1]);
            let k = p.lat.to_radians().cos();
            let (dx, dy) = ((q.lon - p.lon) * k, q.lat - p.lat);
            let bend = (self.rng.unit() - 0.5) * 2.0 * bend_k;
            let c = Point { lat: (p.lat + q.lat) / 2.0 + dx * bend, lon: (p.lon + q.lon) / 2.0 - dy * bend / k };
            let n = (haversine_m(p, q) / 40.0).ceil().max(1.0) as usize;
            for i in 1..=n {
                let t = i as f64 / n as f64;
                let (u, v, w2) = ((1.0 - t) * (1.0 - t), 2.0 * (1.0 - t) * t, t * t);
                dense.push(Point { lat: u * p.lat + v * c.lat + w2 * q.lat, lon: u * p.lon + v * c.lon + w2 * q.lon });
            }
        }
        let mut cum = Vec::with_capacity(dense.len());
        let mut acc = 0.0;
        cum.push(0.0);
        for w in dense.windows(2) {
            acc += haversine_m(w[0], w[1]);
            cum.push(acc);
        }
        let total = acc;
        let (v, dt) = if highway { (23.0 + 4.0 * self.rng.unit(), 4i64) } else { (7.5 + 2.5 * self.rng.unit(), 1i64) };
        let phase = self.rng.unit() * 6.0;
        let (mut s, mut k, mut seg) = (0.0f64, 0i64, 0usize);
        let mut t;
        loop {
            while seg + 1 < cum.len() - 1 && cum[seg + 1] < s {
                seg += 1;
            }
            let span = (cum[seg + 1] - cum[seg]).max(1e-9);
            let f = ((s - cum[seg]) / span).clamp(0.0, 1.0);
            let (p, q) = (dense[seg], dense[seg + 1]);
            let at = Point { lat: p.lat + (q.lat - p.lat) * f, lon: p.lon + (q.lon - p.lon) * f };
            let at = self.jitter(at, 2.0);
            t = t0 + k * dt * 1000;
            self.fixes.push((t, at.lat, at.lon));
            if s >= total {
                break;
            }
            let speed = v * (1.0 + 0.25 * ((k * dt) as f64 / 50.0 + phase).sin());
            s = (s + speed * dt as f64).min(total);
            k += 1;
        }
        self.events.push((t0, KIND_START));
        self.events.push((t, KIND_END));
        self.people.push((t0 + 20_000, self.people_now));
        self.energy.push((t0, (self.soc * 10.0).round() / 10.0));
        self.soc = (self.soc - total / 1000.0 * SOC_PER_KM).max(3.0);
        self.energy.push((t, (self.soc * 10.0).round() / 10.0));
        let odo = crate::readings::ReadingKind::Odometer.code();
        self.readings.push((t0, odo, (self.odo * 10.0).round() / 10.0));
        self.odo += total / 1000.0 * 1.02;
        self.readings.push((t, odo, (self.odo * 10.0).round() / 10.0));
        if self.rain_now {
            self.readings.push((t0 + 60_000, crate::readings::ReadingKind::Rain.code(), 1.0));
        }
        self.trips += 1;
        t
    }

    /// `from` 장소에서 t0에 출발해 구간들을 차례로 달린다. 반환: 마지막 도착 시각.
    fn route(&mut self, t0: i64, from: usize, legs: &[Leg]) -> i64 {
        let mut t = t0;
        let mut here = from;
        let mut arrive = t0;
        for (k, &(to, via, dwell)) in legs.iter().enumerate() {
            let a = self.jitter(poi(here), 15.0);
            let b = self.jitter(poi(to), 15.0);
            arrive = self.drive(t, a, via, b);
            here = to;
            if k + 1 < legs.len() {
                t = arrive + (dwell + self.rng.below(8)) * MIN;
            }
        }
        arrive
    }

    fn weekday(&mut self, d: i64) {
        if self.rng.chance(6) {
            return; // 차를 안 쓴 날
        }
        let m = 45 + self.rng.below(35);
        let t = self.at(d, 7, m);
        let mut legs: Vec<Leg> = Vec::new();
        if self.rng.chance(10) {
            legs.push((STARBUCKS, NO_VIA, 14 + self.rng.below(8)));
        } else if self.rng.chance(7) {
            legs.push((GAS, NO_VIA, 11));
        }
        legs.push((OFFICE, if legs.is_empty() { COMMUTE_OUT } else { &COMMUTE_OUT[1..] }, 0));
        let mut arrived = self.route(t, HOME, &legs);
        // 출장: 출근해서 한 시간쯤 뒤 회사 → 행선지 → 회사
        if let Some((to, out, back, dwell)) = biz_trip_on(d) {
            let go = arrived + (50 + self.rng.below(40)) * MIN;
            let stay = dwell + self.rng.below(30);
            arrived = self.route(go, OFFICE, &[(to, out, stay), (OFFICE, back, 0)]);
        }
        let m = 10 + self.rng.below(70);
        let leave = (arrived + 8 * 60 * MIN).max(self.at(d, 18, m));
        let dom = civil(d).2;
        let errand = if weekday_index(d) == 2 && dom <= 7 {
            Some((HOSPITAL, 45 + self.rng.below(20)))
        } else if self.rng.chance(13) {
            Some((EMART, 30 + self.rng.below(25)))
        } else if self.rng.chance(5) {
            Some((STARBUCKS, 20 + self.rng.below(15)))
        } else {
            None
        };
        let legs: Vec<Leg> = match errand {
            Some((p, m)) => vec![(p, NO_VIA, m), (HOME, NO_VIA, 0)],
            None => vec![(HOME, COMMUTE_BACK, 0)],
        };
        self.route(leave, OFFICE, &legs);
    }

    /// 계절에 맞는 나들이 중 덜 간 곳 (1년에 2번까지).
    fn pick_outing(&mut self, d: i64) -> Option<usize> {
        let m = civil(d).1;
        (0..OUTINGS.len())
            .filter(|&i| OUTINGS[i].months.contains(&m) && self.outing_uses[i] < 2)
            .min_by_key(|&i| (self.outing_uses[i], i))
    }

    fn outing(&mut self, d: i64, i: usize) {
        self.outing_uses[i] += 1;
        let o = &OUTINGS[i];
        if o.tags.contains(&"가족") {
            self.people_now = 4; // 가족 나들이 (좌석 센서 흉내)
        }
        let mut legs: Vec<Leg> = Vec::new();
        for (k, &(p, dwell)) in o.stops.iter().enumerate() {
            legs.push((p, if k == 0 { o.via_out } else { NO_VIA }, dwell));
        }
        legs.push((HOME, o.via_back, 0));
        let m = self.rng.below(40);
        let t = self.at(d, o.depart_h, m);
        self.route(t, HOME, &legs);
        self.label(d, o.title, o.summary, o.tags);
    }

    fn errand(&mut self, d: i64, p: usize, dwell: i64) {
        let m = 30 + self.rng.below(180);
        let t = self.at(d, 10, m);
        self.route(t, HOME, &[(p, NO_VIA, dwell), (HOME, NO_VIA, 0)]);
    }

    fn weekend(&mut self, d: i64, saturday: bool) {
        let go = self.rng.chance(if saturday { 42 } else { 20 });
        if let Some(i) = go.then(|| self.pick_outing(d)).flatten() {
            self.outing(d, i);
        } else if self.rng.chance(if saturday { 45 } else { 22 }) {
            let dwell = 40 + self.rng.below(25);
            self.errand(d, EMART, dwell);
        } else if self.rng.chance(15) {
            self.errand(d, GAS, 12);
        }
    }

    fn gangneung(&mut self, d: i64) {
        let m = self.rng.below(20);
        let t = self.at(d, 7, m);
        let legs: [Leg; 6] = [
            (HOENGSEONG, GN_OUT1, 25),
            (GYEONGPO, GN_OUT2, 110),
            (CHODANG, NO_VIA, 60),
            (ANMOK, NO_VIA, 80),
            (SEAFOOD, NO_VIA, 70),
            (HOME, GN_BACK, 0),
        ];
        self.route(t, HOME, &legs);
        // 강릉 날은 라벨을 미리 넣지 않는다 (앱이 LLM으로 라벨링하는 모습을 보인다). 참고용으로만 남긴다.
        self.label(
            d,
            "가족과 강릉 바다·순두부 당일치기",
            "아침 일찍 영동고속도로를 달려 경포해변을 걷고, 초당할머니순두부에서 점심, 안목해변 카페거리에서 커피, 안목항 바다횟집에서 회로 저녁을 먹었다.",
            &["바다", "해변", "해수욕장", "가족", "여행", "순두부", "맛집", "카페", "횟집", "회", "강릉"],
        );
    }

    /// 크리스마스(휴일): 출근 없이 점심에 집 근처 순두부집에 다녀온다.
    fn christmas(&mut self, d: i64) {
        let m = self.rng.below(25);
        let t = self.at(d, 11, 30 + m);
        self.route(t, HOME, &[(SUNDUBU, NO_VIA, 65), (HOME, NO_VIA, 0)]);
        self.label(
            d,
            "크리스마스 점심, 집 근처 순두부집",
            "크리스마스 낮에 집 근처 금호동 할머니순두부에서 따뜻한 순두부로 점심을 먹고 돌아왔다.",
            &["크리스마스", "성탄절", "순두부", "맛집", "점심", "휴일"],
        );
    }

    fn busan(&mut self, d: i64) {
        let t = self.at(d, 7, 30);
        self.route(t, HOME, &[(ANSEONG, BS_OUT1, 20), (HAEUNDAE, BS_OUT2, 150), (PARADISE, NO_VIA, 0)]);
        self.label(d, "여름휴가 첫날, 해운대 바다", "경부고속도로로 부산까지 달려 해운대해수욕장에서 물놀이를 했다.", &[
            "바다", "해변", "해수욕장", "여름휴가", "여행", "부산", "가족",
        ]);
        let t = self.at(d + 1, 10, 0);
        self.route(t, PARADISE, &[(GAMCHEON, NO_VIA, 100), (JAGALCHI, NO_VIA, 90), (GWANGALLI, NO_VIA, 120), (PARADISE, NO_VIA, 0)]);
        self.label(d + 1, "감천문화마을부터 광안리 야경까지", "감천문화마을 골목을 걷고 자갈치시장에서 회를 먹고 광안대교 야경을 봤다.", &[
            "바다", "야경", "시장", "회", "여행", "부산", "골목",
        ]);
        let t = self.at(d + 2, 10, 30);
        self.route(t, PARADISE, &[(ANSEONG, BS_BACK1, 25), (HOME, BS_BACK2, 0)]);
        self.label(d + 2, "부산 여행 마치고 집으로", "해운대에서 출발해 안성휴게소에서 쉬고 집에 왔다.", &["여행", "부산", "귀가", "휴게소"]);
    }

    fn seorak(&mut self, d: i64) {
        let t = self.at(d, 6, 30);
        self.route(t, HOME, &[(NAERINCHEON, SR_OUT1, 25), (SEORAK, SR_OUT2, 240), (SOKCHO_RESORT, NO_VIA, 0)]);
        self.label(d, "설악산 단풍 산행", "서울양양고속도로를 달려 설악산 소공원에서 단풍 든 비선대 길을 걸었다.", &[
            "단풍", "산", "등산", "가을", "여행", "설악산", "국립공원",
        ]);
        let t = self.at(d + 1, 10, 0);
        self.route(t, SOKCHO_RESORT, &[(SOKCHO_MARKET, NO_VIA, 80), (HOME, SR_BACK, 0)]);
        self.label(d + 1, "속초관광수산시장 닭강정 사서 귀가", "속초관광수산시장에서 닭강정과 오징어순대를 사서 돌아왔다.", &[
            "시장", "닭강정", "속초", "여행", "가을",
        ]);
    }
}

/// 이미 넣은 시연 기록에 나중에 더한 장면만 덧붙인다 (기존 기록은 그대로): 평일 출장(출근해 회사에 있던 낮 시간),
/// 새 나들이(감자탕 · 국밥 · 가족 계곡 — 기록이 없는 주말). 시연 기록이 없으면 처음부터 만든다. 반환: 더한 주행 수.
pub(crate) fn top_up(store: &EodiStore, today_ms: i64) -> Result<u32, CoreError> {
    let (seeded, done): (Option<i64>, Option<i64>) = {
        let conn = store.lock()?;
        let q = |k: &str| conn.query_row("SELECT value FROM meta WHERE key = ?1", [k], |r| r.get(0)).optional();
        (q("demo_seeded")?, q("demo_topup")?)
    };
    let Some(seeded) = seeded else { return seed(store, today_ms) };
    if done.unwrap_or(0) >= TOPUP_VERSION {
        return Ok(0);
    }
    let p = plan(seeded);
    let (range_lo, range_hi) = (store.day_start_ms(p.first), store.day_start_ms(p.last + 1));
    let (mut occupied, fix_times): (HashSet<i64>, Vec<i64>) = {
        let conn = store.lock()?;
        let tz_ms = -store.day_start_ms(0);
        let mut st = conn.prepare("SELECT DISTINCT (t_ms + ?1) / 86400000 FROM fix WHERE t_ms >= ?2 AND t_ms < ?3")?;
        let days = st.query_map([tz_ms, range_lo, range_hi], |r| r.get::<_, i64>(0))?.collect::<Result<_, _>>()?;
        let mut st = conn.prepare("SELECT t_ms FROM fix WHERE t_ms >= ?1 AND t_ms < ?2 ORDER BY t_ms")?;
        let times = st.query_map([range_lo, range_hi], |r| r.get::<_, i64>(0))?.collect::<Result<_, _>>()?;
        (days, times)
    };
    let busy = |a: i64, b: i64| {
        let i = fix_times.partition_point(|&t| t < a);
        fix_times.get(i).is_some_and(|&t| t < b)
    };
    let before = |table: &str, extra: &str, t: i64| -> Result<Option<f64>, CoreError> {
        let conn = store.snapshot()?;
        let sql = format!("SELECT {} FROM {table} WHERE t_ms <= ?1{extra} ORDER BY t_ms DESC LIMIT 1", if table == "drive_energy" { "pct" } else { "value" });
        Ok(conn.query_row(&sql, [t], |r| r.get(0)).optional()?)
    };
    let mut g = Gen {
        store,
        rng: Rng(0x70b5_2026),
        fixes: Vec::new(),
        events: Vec::new(),
        labels: BTreeMap::new(),
        trips: 0,
        outing_uses: vec![0; OUTINGS.len()],
        people_now: 1,
        people: Vec::new(),
        soc: 70.0,
        energy: Vec::new(),
        odo: 18_240.0,
        rain_now: false,
        readings: Vec::new(),
    };
    let mut shifts: Vec<(i64, f64, f64)> = Vec::new();
    // 1) 평일 출장: 시연 출퇴근이 있는 날, 회사에 있던 10:00~16:30에 기록이 없을 때만
    for d in p.first..=p.last {
        let Some((to, out, back, dwell)) = biz_trip_on(d) else { continue };
        if !occupied.contains(&d) || busy(g.at(d, 9, 50), g.at(d, 17, 0)) {
            continue;
        }
        let m = g.rng.below(30);
        let go = g.at(d, 10, m);
        g.soc = before("drive_energy", "", go)?.unwrap_or(70.0);
        g.odo = before("drive_reading", &format!(" AND kind = {}", crate::readings::ReadingKind::Odometer.code()), go)?.unwrap_or(18_240.0);
        let (soc0, odo0) = (g.soc, g.odo);
        g.people_now = 1;
        g.rain_now = false;
        let stay = dwell + g.rng.below(30);
        let end = g.route(go, OFFICE, &[(to, out, stay), (OFFICE, back, 0)]);
        shifts.push((end, soc0 - g.soc, g.odo - odo0));
    }
    // 2) 새 나들이: 계절이 맞고 기록이 없는 주말 (나들이마다 한 번)
    for (i, o) in OUTINGS.iter().enumerate().skip(FIRST_TOPUP_OUTING) {
        // 이미 그 나들이의 첫 곳에 간 기록이 있으면 (새 판으로 처음부터 만든 기록) 넣지 않는다
        let at = poi(o.stops[0].0);
        let been: i64 = store.snapshot()?.query_row(
            "SELECT count(*) FROM place WHERE abs(lat - ?1) < 0.002 AND abs(lon - ?2) < 0.002",
            params![at.lat, at.lon],
            |r| r.get(0),
        )?;
        if been > 0 {
            continue;
        }
        let day = (p.first..=p.last).rev().find(|&d| {
            matches!(weekday_index(d), 0 | 6) && !occupied.contains(&d) && o.months.contains(&civil(d).1) && d < p.last - 7
        });
        if let Some(d) = day {
            g.people_now = 2;
            g.rain_now = false;
            g.soc = before("drive_energy", "", g.at(d, 6, 0))?.unwrap_or(80.0).max(40.0);
            g.odo = before("drive_reading", &format!(" AND kind = {}", crate::readings::ReadingKind::Odometer.code()), g.at(d, 6, 0))?.unwrap_or(18_240.0);
            let (soc0, odo0) = (g.soc, g.odo);
            let n = g.events.len();
            g.outing(d, i);
            let end = g.events[n..].iter().map(|e| e.0).max().unwrap_or(g.at(d, 20, 0));
            shifts.push((end, soc0 - g.soc, g.odo - odo0));
            occupied.insert(d);
        }
    }
    // 3) 1년 전 강릉 여행에 저녁 횟집이 없으면 그날만 다시 만든다 (그날은 시연 기록뿐이다 — 오늘 이전 1년 전 날)
    let gn = p.gangneung;
    let (gs, ge) = (store.day_start_ms(gn), store.day_start_ms(gn + 1));
    let at = poi(SEAFOOD);
    let (has_day, has_seafood): (bool, bool) = {
        let conn = store.snapshot()?;
        let day = conn.query_row("SELECT 1 FROM fix WHERE t_ms >= ?1 AND t_ms < ?2 LIMIT 1", [gs, ge], |_| Ok(())).optional()?.is_some();
        let near: i64 = conn.query_row(
            "SELECT count(*) FROM fix WHERE t_ms >= ?1 AND t_ms < ?2 AND abs(lat - ?3) < 0.0015 AND abs(lon - ?4) < 0.0015",
            params![gs, ge, at.lat, at.lon],
            |r| r.get(0),
        )?;
        (day, near > 0)
    };
    if has_day && !has_seafood {
        {
            let conn = store.lock()?;
            for t in ["fix", "drive_event", "navi_hint", "drive_people", "drive_energy", "drive_reading"] {
                conn.execute(&format!("DELETE FROM {t} WHERE t_ms >= ?1 AND t_ms < ?2"), [gs, ge])?;
            }
            conn.execute(
                "UPDATE day_memory SET title = NULL, summary = '', tags = '', enriched_sig = NULL, retry_at_ms = 0 WHERE day = ?1",
                [gn],
            )?;
            conn.execute("UPDATE episode SET title = NULL, tags = '' WHERE day = ?1", [gn])?;
        }
        g.people_now = 4;
        g.rain_now = true;
        g.soc = before("drive_energy", "", gs)?.unwrap_or(82.0).max(60.0);
        g.odo = before("drive_reading", &format!(" AND kind = {}", crate::readings::ReadingKind::Odometer.code()), gs)?.unwrap_or(18_240.0);
        g.gangneung(gn);
    }
    // 4) 크리스마스에 순두부집이 없으면 (출근 · 출장으로 심어 둔 예전 시연 기록) 그날만 다시 만든다.
    //    그날이 시연 기록일 때만 (시연 집에서 출발한 점이 있다) — 실제 기록이 있는 날은 건드리지 않는다.
    if let Some(x) = p.christmas {
        let (xs, xe) = (store.day_start_ms(x), store.day_start_ms(x + 1));
        let near = |at: Point| -> Result<bool, CoreError> {
            let n: i64 = store.snapshot()?.query_row(
                "SELECT count(*) FROM fix WHERE t_ms >= ?1 AND t_ms < ?2 AND abs(lat - ?3) < 0.0015 AND abs(lon - ?4) < 0.0015",
                params![xs, xe, at.lat, at.lon],
                |r| r.get(0),
            )?;
            Ok(n > 0)
        };
        if near(poi(HOME))? && !near(poi(SUNDUBU))? {
            {
                let conn = store.lock()?;
                for t in ["fix", "drive_event", "navi_hint", "drive_people", "drive_energy", "drive_reading"] {
                    conn.execute(&format!("DELETE FROM {t} WHERE t_ms >= ?1 AND t_ms < ?2"), [xs, xe])?;
                }
                conn.execute(
                    "UPDATE day_memory SET title = NULL, summary = '', tags = '', enriched_sig = NULL, retry_at_ms = 0 WHERE day = ?1",
                    [x],
                )?;
                conn.execute("UPDATE episode SET title = NULL, tags = '' WHERE day = ?1", [x])?;
            }
            g.people_now = 2;
            g.rain_now = false;
            g.soc = before("drive_energy", "", xs)?.unwrap_or(80.0).max(40.0);
            g.odo = before("drive_reading", &format!(" AND kind = {}", crate::readings::ReadingKind::Odometer.code()), xs)?.unwrap_or(18_240.0);
            g.rng = Rng(XMAS_SEED);
            g.christmas(x);
        }
    }
    let made = g.trips;
    // 강릉 날은 라벨을 미리 넣지 않는다 (처음 만들 때와 같이 앱이 라벨링한다)
    let new_days: HashSet<i64> = g.labels.keys().copied().filter(|&d| d != gn).collect();
    let meta = [("demo_topup", TOPUP_VERSION)];
    write_out(store, &mut g, &meta, p.first, p.last, |d| new_days.contains(&d), &shifts)?;
    Ok(made)
}

/// 시연 데이터를 만든다 (모듈 설명). 반환: 만든 주행 수 (이미 만들었으면 0).
pub(crate) fn seed(store: &EodiStore, today_ms: i64) -> Result<u32, CoreError> {
    let today = store.local_day(today_ms);
    let p = plan(today);
    let (range_lo, range_hi) = (store.day_start_ms(p.first), store.day_start_ms(p.last + 1));
    let occupied: HashSet<i64> = {
        let conn = store.lock()?;
        let seeded: Option<i64> =
            conn.query_row("SELECT value FROM meta WHERE key = 'demo_seeded'", [], |r| r.get(0)).optional()?;
        if seeded.is_some() {
            return Ok(0);
        }
        let tz_ms = -store.day_start_ms(0);
        let mut st = conn.prepare("SELECT DISTINCT (t_ms + ?1) / 86400000 FROM fix WHERE t_ms >= ?2 AND t_ms < ?3")?;
        let rows = st.query_map([tz_ms, range_lo, range_hi], |r| r.get::<_, i64>(0))?;
        rows.collect::<Result<_, _>>()?
    };
    let free = |a: i64, b: i64| (a..=b).all(|d| !occupied.contains(&d));

    let mut g = Gen {
        store,
        rng: Rng(0x5eed_2025),
        fixes: Vec::with_capacity(600_000),
        events: Vec::new(),
        labels: BTreeMap::new(),
        trips: 0,
        outing_uses: vec![0; OUTINGS.len()],
        people_now: 1,
        people: Vec::new(),
        soc: 82.0,
        energy: Vec::new(),
        odo: 18_240.0,
        rain_now: false,
        readings: Vec::new(),
    };
    let busan = p.busan.filter(|&b| free(b, b + 2));
    let seorak = p.seorak.filter(|&s| free(s, s + 1));
    let mut d = p.first;
    while d <= p.last {
        if !free(d, d) {
            d += 1;
            continue;
        }
        if g.soc < 30.0 {
            g.soc = 90.0; // 전날 밤 집에서 충전
        }
        g.rain_now = d == p.gangneung || (d as u64).wrapping_mul(2_654_435_761) % 100 < 12;
        g.people_now = match weekday_index(d) {
            0 | 6 => 2,
            _ => 1,
        };
        if d == p.gangneung {
            g.people_now = 4;
            g.gangneung(d);
        } else if Some(d) == busan {
            g.people_now = 4;
            g.busan(d);
            d += 3;
            continue;
        } else if Some(d) == seorak {
            g.people_now = 4;
            g.seorak(d);
            d += 2;
            continue;
        } else {
            let xmas = Some(d) == p.christmas;
            let mark = (g.fixes.len(), g.events.len(), g.people.len(), g.energy.len(), g.readings.len(), g.trips, g.soc, g.odo);
            match weekday_index(d) {
                6 => g.weekend(d, true),
                0 => g.weekend(d, false),
                _ => g.weekday(d),
            }
            if xmas {
                // 크리스마스는 휴일: 위에서 만든 평소 하루(출근 · 출장)는 버리고 순두부집 점심으로 바꾼다.
                // 평소 하루를 먼저 만들었다 버리는 까닭: 그날 쓰는 난수 개수가 그대로여야 다른 날의 기록이 이전 판과 같다.
                g.fixes.truncate(mark.0);
                g.events.truncate(mark.1);
                g.people.truncate(mark.2);
                g.energy.truncate(mark.3);
                g.readings.truncate(mark.4);
                (g.trips, g.soc, g.odo) = (mark.5, mark.6, mark.7);
                g.labels.remove(&d);
                let main = std::mem::replace(&mut g.rng, Rng(XMAS_SEED));
                g.people_now = 2;
                g.rain_now = false;
                g.christmas(d);
                g.rng = main;
            }
        }
        d += 1;
    }
    let made = g.trips;
    let meta = [("demo_seeded", today), ("demo_from_ms", range_lo), ("demo_to_ms", range_hi), ("demo_topup", TOPUP_VERSION)];
    write_out(store, &mut g, &meta, p.first, p.last, |d| d != p.gangneung && d < today - 30, &[])?;
    Ok(made)
}

/// 시연 기록 보충 판 (새 장면을 더할 때마다 올린다). 1 = 평일 출장 · 감자탕 · 국밥 · 가족 계곡 나들이,
/// 2 = 1판에서 일부 날만 다시 계산하던 것을 시연 기간 전체로 (덧붙일 것은 1판과 같다 — 이미 있으면 건너뛴다),
/// 3 = 1년 전 강릉 여행에 저녁 횟집(안목항 바다횟집)을 더한다 (그날 시연 기록만 지우고 새로 만든다),
/// 4 = 크리스마스를 휴일로: 출근 · 출장 대신 집 근처 순두부집 점심 (그날 시연 기록만 지우고 새로 만든다).
const TOPUP_VERSION: i64 = 4;

/// 크리스마스 하루를 만드는 난수 씨앗 (처음 만들 때와 보충할 때 같은 하루가 나오게 따로 둔다)
const XMAS_SEED: u64 = 0xc4a1_1225;

/// 만든 주행을 DB에 쓰고, 장소 이름 · 하루 요약 · 기억을 다시 만들고, 라벨이 필요한 날 중 [label_day]가 참인 날의 LLM 라벨을 미리 넣는다.
/// [shifts]: (덧붙인 주행 끝, 쓴 배터리 %p, 달린 km) — 보충 때 그 뒤 기록을 옮긴다.
#[allow(clippy::too_many_arguments)]
fn write_out(
    store: &EodiStore,
    g: &mut Gen,
    meta: &[(&str, i64)],
    first: i64,
    last: i64,
    label_day: impl Fn(i64) -> bool,
    shifts: &[(i64, f64, f64)],
) -> Result<(), CoreError> {
    let (fixes, events, labels) = (std::mem::take(&mut g.fixes), std::mem::take(&mut g.events), std::mem::take(&mut g.labels));
    let people = std::mem::take(&mut g.people);
    let energy = std::mem::take(&mut g.energy);
    let readings = std::mem::take(&mut g.readings);

    let mut conn = store.lock()?;
    {
        let tx = conn.transaction()?;
        {
            let mut st = tx.prepare("INSERT OR IGNORE INTO fix (t_ms, lat, lon, accuracy_m) VALUES (?1, ?2, ?3, 5.0)")?;
            for &(t, lat, lon) in &fixes {
                st.execute(params![t, lat, lon])?;
            }
            let mut st = tx.prepare("INSERT OR IGNORE INTO drive_event (t_ms, kind) VALUES (?1, ?2)")?;
            for &(t, k) in &events {
                st.execute([t, k])?;
            }
            let mut st = tx.prepare("INSERT OR IGNORE INTO drive_people (t_ms, people) VALUES (?1, ?2)")?;
            for &(t, n) in &people {
                st.execute(params![t, n as i64])?;
            }
            let mut st = tx.prepare("INSERT OR IGNORE INTO drive_energy (t_ms, pct, ev) VALUES (?1, ?2, 1)")?;
            for &(t, pct) in &energy {
                st.execute(params![t, pct])?;
            }
            let mut st = tx.prepare("INSERT OR IGNORE INTO drive_reading (t_ms, kind, value) VALUES (?1, ?2, ?3)")?;
            for &(t, k, v) in &readings {
                st.execute(params![t, k, v])?;
            }
        }
        // 시연 기간 전체를 다시 계산한다 (보충은 여러 날에 흩어져 들어가고 시각순이 아니다)
        mark_dirty(&tx, store.day_start_ms(first), store.day_start_ms(last + 1))?;
        // 보충이면 덧붙인 출장 뒤 기록(그날 배터리 · 그 뒤 주행거리계)을 출장에 쓴 만큼 옮긴다
        for &(end, used_pct, km) in shifts {
            let day_end = store.day_start_ms(store.local_day(end) + 1);
            tx.execute("UPDATE drive_energy SET pct = max(0.0, pct - ?1) WHERE t_ms > ?2 AND t_ms < ?3", params![used_pct, end, day_end])?;
            tx.execute(
                "UPDATE drive_reading SET value = value + ?1 WHERE kind = ?2 AND t_ms > ?3",
                params![km, crate::readings::ReadingKind::Odometer.code(), end],
            )?;
        }
        let mut st = tx.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES (?1, ?2)")?;
        for (k, v) in meta {
            st.execute(params![k, v])?;
        }
        drop(st);
        tx.commit()?;
    }
    store.flush(&mut conn)?;

    // 장소 이름 (카카오처럼): 이름 없는 장소를 200m 안 가장 가까운 시연 장소 이름으로.
    {
        let tx = conn.transaction()?;
        let unnamed: Vec<(i64, Point)> = {
            let mut st = tx.prepare("SELECT id, lat, lon FROM place WHERE source IS NULL")?;
            let rows = st.query_map([], |r| Ok((r.get(0)?, Point { lat: r.get(1)?, lon: r.get(2)? })))?;
            rows.collect::<Result<_, _>>()?
        };
        for (id, at) in unnamed {
            let best = (0..POIS.len()).min_by(|&a, &b| haversine_m(at, poi(a)).total_cmp(&haversine_m(at, poi(b))));
            if let Some(i) = best.filter(|&i| haversine_m(at, poi(i)) <= 200.0) {
                let (name, category, address, _, _) = POIS[i];
                let label =
                    PlaceLabel { name: name.into(), category: category.into(), address: address.into(), source: PlaceSource::Kakao };
                places::set_label(&tx, id, &label)?;
            }
        }
        store.refresh_days(&tx, first, last + 1)?;
        episode::set_navi_anchors(&tx, Some(poi(HOME)), Some(poi(OFFICE)))?;
        tx.commit()?;
    }
    episode::flush_memory(store, &mut conn, true)?;

    // 30일보다 오래된 날의 LLM 라벨을 미리 넣는다 (강릉 날 제외).
    let old_days: Vec<i64> = {
        let mut st = conn.prepare("SELECT day FROM day_memory WHERE needs_llm = 1 AND day >= ?1")?;
        let rows = st.query_map([first], |r| r.get(0))?;
        rows.collect::<Result<Vec<i64>, _>>()?.into_iter().filter(|&d| label_day(d)).collect()
    };
    for d in old_days {
        let episodes = episode::episodes_of_day(&conn, d)?;
        let (title, summary, tags) = match labels.get(&d) {
            Some(l) => (l.title.clone(), l.summary.clone(), l.tags.clone()),
            None => (episode::day_title(&conn, d)?.0.unwrap_or_else(|| "평소 하루".into()), String::new(), Vec::new()),
        };
        let ep_labels = episodes
            .iter()
            .filter(|e| matches!(e.facts.kind, EpisodeKind::Outing | EpisodeKind::Trip))
            .map(|e| EpisodeLabel { episode_id: e.facts.id, title: title.clone(), tags: tags.clone() })
            .collect();
        let e = DayEnrichment { day: format_day(d), title, summary, tags, episodes: ep_labels };
        episode::save_enrichment(&mut conn, d, &e)?;
    }
    Ok(())
}

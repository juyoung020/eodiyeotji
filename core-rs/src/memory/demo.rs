//! 시연용 1년치 기록 (`seed_demo_data`). 네트워크 없이, 같은 `today_ms`면 언제나 같은 기록이 나온다.
//!
//! # 내용 (오늘 = `today_ms`의 현지 날짜)
//! - 기간: 오늘 365일 전(또는 1년 전 같은 날짜 중 이른 쪽) ~ 어제. 오늘은 실제 기록용으로 비워 둔다.
//! - 집: 서울 성동구 응봉동, 회사: 서울 중구 을지로(센터원빌딩). 내비 즐겨찾기처럼 `set_home_office`로도 넣는다.
//! - 평일: 출근(07:45~08:20 출발)·퇴근(18:10~19:20 출발). 가끔 아침 스타벅스·주유소, 저녁 이마트·스타벅스,
//!   매달 첫 화요일 저녁 병원. 6%는 차를 안 쓴 날.
//! - 주말: 토요일 42%·일요일 20% 나들이(성수 카페거리 3곳·서울숲, 남산서울타워+광장시장, 여의도 한강공원+더현대,
//!   북촌+인사동, 롯데월드몰, 스타필드 하남, 이케아 광명, 북한산, 을왕리(여름), 남이섬+쁘띠프랑스(가을) — 계절에 맞는
//!   것 중 덜 간 곳부터, 같은 곳은 1년에 2번까지), 그 밖에는 이마트·주유소 볼일.
//! - 여행: 정확히 1년 전 같은 날짜에 강릉 가족 당일 여행 (집 → 횡성휴게소 → 경포해변 → 초당할머니순두부 →
//!   안목해변 카페거리 → 집), 8월 첫 금요일부터 2박 3일 부산 (해운대·감천문화마을·자갈치시장·광안리),
//!   10월 18일 이후 첫 토요일부터 1박 2일 설악산 단풍 (내린천휴게소 → 설악산 소공원 → 속초 숙소 → 속초관광수산시장).
//! - 위치 점: 시내 1초, 고속도로(직선 30km 넘는 구간) 4초 간격, 도로처럼 휘는 길 + GPS 흔들림 2m. 주행마다 시작·끝 신호.
//!   2026-11-06 기준 1년치 약 39만 점, 주행 745개 (PC 릴리즈 빌드 0.3초, 디버그 1초).
//! - 장소 이름: 카카오처럼 이름·분류·지번 주소를 붙인다 (출처 Kakao).
//! - 배터리(전기차 흉내): 1km에 0.22%p, 30% 아래면 그날 아침 전에 90%로 충전. 주행마다 출발 · 도착 잔량.
//! - 탑승 인원(좌석 센서 흉내): 여행(강릉 · 부산 · 설악산) 4명, 주말 2명, 평일 혼자. 주행마다 출발 20초 뒤 한 번.
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
    Plan { first, last, gangneung, busan, seorak }
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
        let arrived = self.route(t, HOME, &legs);
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
        let legs: [Leg; 5] =
            [(HOENGSEONG, GN_OUT1, 25), (GYEONGPO, GN_OUT2, 110), (CHODANG, NO_VIA, 60), (ANMOK, NO_VIA, 80), (HOME, GN_BACK, 0)];
        self.route(t, HOME, &legs);
        // 강릉 날은 라벨을 미리 넣지 않는다 (앱이 LLM으로 라벨링하는 모습을 보인다). 참고용으로만 남긴다.
        self.label(
            d,
            "가족과 강릉 바다·순두부 당일치기",
            "아침 일찍 영동고속도로를 달려 경포해변을 걷고, 초당할머니순두부에서 점심, 안목해변 카페거리에서 커피를 마셨다.",
            &["바다", "해변", "해수욕장", "가족", "여행", "순두부", "맛집", "카페", "강릉"],
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
            match weekday_index(d) {
                6 => g.weekend(d, true),
                0 => g.weekend(d, false),
                _ => g.weekday(d),
            }
        }
        d += 1;
    }
    let made = g.trips;
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
        if let (Some(lo), Some(hi)) = (fixes.first(), fixes.last()) {
            mark_dirty(&tx, lo.0, hi.0)?;
        }
        let mut meta = tx.prepare("INSERT OR REPLACE INTO meta (key, value) VALUES (?1, ?2)")?;
        meta.execute(params!["demo_seeded", today])?;
        meta.execute(params!["demo_from_ms", range_lo])?;
        meta.execute(params!["demo_to_ms", range_hi])?;
        drop(meta);
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
        store.refresh_days(&tx, p.first, p.last + 1)?;
        episode::set_navi_anchors(&tx, Some(poi(HOME)), Some(poi(OFFICE)))?;
        tx.commit()?;
    }
    episode::flush_memory(store, &mut conn, true)?;

    // 30일보다 오래된 날의 LLM 라벨을 미리 넣는다 (강릉 날 제외).
    let old_days: Vec<i64> = {
        let mut st = conn.prepare("SELECT day FROM day_memory WHERE needs_llm = 1 AND day < ?1 AND day >= ?2 AND day != ?3")?;
        let rows = st.query_map([today - 30, p.first, p.gangneung], |r| r.get(0))?;
        rows.collect::<Result<_, _>>()?
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
    Ok(made)
}

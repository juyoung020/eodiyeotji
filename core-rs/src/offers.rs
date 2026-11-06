//! 제휴 혜택 · 할인 쿠폰 — 음식점 · 카페 · 주유소 · 휴게소 · 놀 거리가 코스 위 손님에게 주는 쿠폰 (`docs/80_사업모델_수익성.md` ②).
//!
//! **[OFFERS]는 공모전 시연용 예시다.** 실제 매장과 맺은 제휴가 아니고, 이름난 장소 이름에 그럴듯한 혜택을 붙여
//! 앱 안에서 "제휴 쿠폰이 이렇게 보인다"를 보여 주려고 코드에 넣어 둔 값이다 (네트워크 없이, 언제나 같은 내용).
//! 2차 단계에서 서버가 제휴처 쿠폰을 내려 주면 이 표 자리를 그 목록이 대신한다.
//!
//! # 어느 장소에 붙나 ([offer_matches])
//! - 이름이 같으면(띄어쓰기 · 대소문자 무시, 또는 4글자 이상인 한쪽을 다른 쪽이 품으면) 같은 곳이다.
//!   단 양쪽 좌표가 있으면 [NAME_MAX_M] 안이어야 한다 (멀리 있는 같은 이름 지점은 다른 곳).
//! - 이름이 조금 달라도(카카오 "강릉 초당할머니순두부 본점" ↔ "초당할머니순두부") 좌표가 [NEAR_M] 안이고 짧은 쪽 이름의
//!   대부분([SIMILAR_RATIO])이 이어서 겹치면 같은 곳. 맛집 골목처럼 이웃 가게 이름이 몇 글자 겹치는 곳
//!   ("초당할머니순두부" ↔ "원조초당순두부")은 다른 곳으로 본다 — 남의 가게 쿠폰 · 리뷰가 섞이면 안 된다.
//! - 아직 이름을 모르는 곳은 좌표가 [UNNAMED_M] 안일 때만.
//!
//! # 코스 ([course_offers])
//! 들른 곳 순서대로 쿠폰을 모은다 (같은 쿠폰은 한 번). `place_count` = 쿠폰이 있는 들른 곳 수 ("쿠폰 2곳").
//!
//! # 쿠폰 받기 ([EodiStore::claim_offer])
//! 받은 쿠폰은 기기 DB `claimed_coupon` 표에 남는다 (같은 쿠폰을 다시 받으면 처음 받은 것 그대로).
//! 쿠폰 번호는 쿠폰 · 받은 시각으로 만든 짧은 글자("EODI-7K3Q")라 매장에서 보여 주기 쉽다.

use rusqlite::{params, OptionalExtension};

use crate::ffi::GeoPoint;
use crate::geo::{haversine_m, Point};
use crate::memory::StopFact;
use crate::store::{CoreError, EodiStore};

/// 이름이 같아도 이보다 멀면 다른 지점 (m).
pub const NAME_MAX_M: f64 = 5_000.0;
/// 이름이 조금 달라도 이 안이고 이름이 거의 같으면 같은 곳 (m).
pub const NEAR_M: f64 = 300.0;
/// 짧은 쪽 이름 중 이만큼이 이어서 겹쳐야 "거의 같은 이름".
pub const SIMILAR_RATIO: f64 = 0.7;
/// 이름을 아직 모르는 곳은 이 안일 때만 (m).
pub const UNNAMED_M: f64 = 150.0;

pub(crate) const SCHEMA_V8: &str = "
-- 받은 쿠폰 (offers 모듈). offer_id = PartnerOffer.id
CREATE TABLE IF NOT EXISTS claimed_coupon (offer_id TEXT PRIMARY KEY, code TEXT NOT NULL, claimed_ms INTEGER NOT NULL);
";

#[cfg(test)]
pub(crate) const DROP_V8: &str = "DROP TABLE claimed_coupon;";

/// 제휴처 종류.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum OfferCategory {
    Restaurant,
    Cafe,
    Fuel,
    RestArea,
    Attraction,
    Market,
    Shopping,
}

impl OfferCategory {
    fn label(self) -> &'static str {
        match self {
            OfferCategory::Restaurant => "음식점",
            OfferCategory::Cafe => "카페",
            OfferCategory::Fuel => "주유소",
            OfferCategory::RestArea => "휴게소",
            OfferCategory::Attraction => "놀 거리",
            OfferCategory::Market => "시장",
            OfferCategory::Shopping => "마트",
        }
    }
}

/// 제휴 혜택 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PartnerOffer {
    pub id: String,
    /// 쿠폰이 붙는 장소 이름 (예: "초당할머니순두부")
    pub place_name: String,
    /// 화면에 보이는 제휴처 이름 (보통 장소 이름과 같고, 카페거리처럼 넓은 곳이면 그 안의 가게)
    pub partner_name: String,
    /// 짧은 배지 (예: "10% 할인", "아메리카노 1+1", "리터당 100원 할인")
    pub badge: String,
    /// 혜택 한 줄
    pub title: String,
    /// 조건 한 줄
    pub conditions: String,
    /// 마지막 날 "YYYY-MM-DD"
    pub valid_until: String,
    /// 화면용 "12월 31일까지"
    pub valid_text: String,
    pub category: OfferCategory,
    /// 화면용 종류 이름 ("음식점", "놀 거리")
    pub category_label: String,
    pub lat: f64,
    pub lon: f64,
}

/// 쿠폰을 찾을 장소 (이름 · 좌표, 좌표를 모르면 None).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct OfferPlace {
    pub name: String,
    pub point: Option<GeoPoint>,
}

/// 코스 하나의 쿠폰. [place_count] = 쿠폰이 있는 들른 곳 수.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CourseOffers {
    pub place_count: u32,
    pub offers: Vec<PartnerOffer>,
}

/// 받은 쿠폰.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ClaimedCoupon {
    pub offer: PartnerOffer,
    /// 매장에서 보여 줄 번호 (예: "EODI-7K3Q")
    pub code: String,
    pub claimed_ms: i64,
}

/// 시연용 쿠폰 한 줄: (id, 장소 이름, 제휴처 이름, 배지, 혜택, 조건, 마지막 날, 종류, 위도, 경도).
type Row = (&'static str, &'static str, &'static str, &'static str, &'static str, &'static str, &'static str, OfferCategory, f64, f64);

use OfferCategory::*;

/// 시연용 제휴 쿠폰 (모듈 설명: 실제 제휴가 아닌 공모전 예시). 장소 이름 · 좌표는 시연 기록(`memory::demo`)과
/// 커뮤니티 시연 코스 · 리뷰(`community::demo`)에 나오는 곳과 같다.
const OFFERS: &[Row] = &[
    // 강릉
    ("o-chodang-halmeoni", "초당할머니순두부", "초당할머니순두부", "10% 할인", "순두부 정식 10% 할인", "식사 1인 1메뉴 주문 시 · 다른 할인과 함께 쓸 수 없어요", "2026-12-31", Restaurant, 37.7915, 128.9142),
    ("o-chodang-wonjo", "원조초당순두부", "원조초당순두부", "모두부 서비스", "2인 이상 식사 시 모두부 반 모 서비스", "2인 이상 식사 시 · 테이블당 1회", "2026-12-31", Restaurant, 37.7902, 128.9131),
    ("o-terarosa", "테라로사 커피공장 강릉본점", "테라로사 커피공장 강릉본점", "원두 10% 할인", "원두 200g 이상 사면 10% 할인", "매장 원두 한정 · 1인 1회", "2027-02-28", Cafe, 37.7070, 128.8690),
    ("o-anmok-cafe", "안목해변 카페거리", "안목 바다정원 카페", "아메리카노 1+1", "아메리카노 한 잔 사면 한 잔 더", "매장에서 마실 때 · 1일 1회", "2026-12-31", Cafe, 37.7722, 128.9474),
    ("o-jumunjin", "주문진수산시장", "주문진수산시장 상인회", "5,000원 할인", "회 3만 원 이상 사면 5,000원 할인", "참여 점포 한정 · 시장 안내소에서 확인", "2026-12-31", Market, 37.8925, 128.8270),
    // 영동고속도로 · 대관령
    ("o-hoengseong", "횡성휴게소", "횡성휴게소 한우국밥", "1,000원 할인", "한우국밥 1,000원 할인", "푸드코트 한우국밥 한정", "2027-02-28", RestArea, 37.4700, 128.0300),
    ("o-sheep", "대관령 양떼목장", "대관령 양떼목장", "입장료 20% 할인", "입장료 20% 할인 (건초 주기 포함)", "매표소에서 쿠폰 보여 주기 · 최대 4명", "2026-12-31", Attraction, 37.6877, 128.7537),
    // 가평 · 춘천
    ("o-petite-france", "쁘띠프랑스", "쁘띠프랑스", "입장료 15% 할인", "입장료 15% 할인", "매표소에서 쿠폰 보여 주기 · 최대 4명", "2026-12-31", Attraction, 37.7178, 127.4870),
    ("o-nami", "남이섬", "남이섬", "2,000원 할인", "입장권 1인 2,000원 할인", "선착장 매표소 · 최대 4명", "2026-12-31", Attraction, 37.7907, 127.5257),
    ("o-samak-cable", "삼악산 호수케이블카", "삼악산 호수케이블카", "10% 할인", "크리스탈 캐빈 왕복 10% 할인", "현장 발권 시 · 최대 4명", "2027-02-28", Attraction, 37.8763, 127.7068),
    ("o-dakgalbi", "춘천명동닭갈비골목", "명동 원조닭갈비", "볶음밥 서비스", "닭갈비 2인분 이상 시 볶음밥 1인분 서비스", "닭갈비 2인분 이상 주문 시", "2026-12-31", Restaurant, 37.8810, 127.7290),
    // 속초 · 인천
    ("o-sokcho-market", "속초관광수산시장", "속초 중앙닭강정", "1,000원 할인", "닭강정 한 박스 1,000원 할인", "한 박스 이상 살 때 · 1일 1회", "2026-12-31", Market, 38.2046, 128.5905),
    ("o-eulwang-clam", "을왕리 조개구이촌", "을왕리 바다조개구이", "음료 서비스", "조개구이 2인 이상 시 음료 1병 서비스", "조개구이 2인 이상 주문 시", "2026-12-31", Restaurant, 37.4495, 126.3770),
    // 서울 성동 (시연 기록의 동네)
    ("o-starbucks-wangsimni", "스타벅스 왕십리역점", "스타벅스 왕십리역점", "사이즈 업", "제조 음료 사이즈 한 단계 무료 업", "1일 1회 · 다른 쿠폰과 함께 쓸 수 없어요", "2026-12-31", Cafe, 37.5614, 127.0369),
    ("o-emart-seongsu", "이마트 성수점", "이마트 성수점", "5,000원 할인", "5만 원 이상 사면 5,000원 할인", "주류 · 담배 제외 · 1인 1회", "2026-12-31", Shopping, 37.5395, 127.0531),
    ("o-sk-eungbong", "SK에너지 응봉주유소", "SK에너지 응봉주유소", "리터당 100원 할인", "휘발유 · 경유 리터당 100원 할인", "3만 원 이상 주유 시 · 1일 1회", "2027-02-28", Fuel, 37.5520, 127.0405),
];

fn offer(r: &Row) -> PartnerOffer {
    let (id, place, partner, badge, title, cond, until, cat, lat, lon) = *r;
    PartnerOffer {
        id: id.into(),
        place_name: place.into(),
        partner_name: partner.into(),
        badge: badge.into(),
        title: title.into(),
        conditions: cond.into(),
        valid_until: until.into(),
        valid_text: valid_text(until),
        category: cat,
        category_label: cat.label().into(),
        lat,
        lon,
    }
}

/// "2026-12-31" → "12월 31일까지"
fn valid_text(day: &str) -> String {
    let mut it = day.split('-').skip(1).map(|s| s.trim_start_matches('0'));
    match (it.next(), it.next()) {
        (Some(m), Some(d)) => format!("{m}월 {d}일까지"),
        _ => format!("{day}까지"),
    }
}

/// 띄어쓰기 · 대소문자를 뺀 이름
pub(crate) fn norm(s: &str) -> Vec<char> {
    s.chars().filter(|c| !c.is_whitespace() && !matches!(c, '·' | '-' | '_' | '(' | ')')).flat_map(char::to_lowercase).collect()
}

fn contains(hay: &[char], needle: &[char]) -> bool {
    needle.is_empty() || hay.windows(needle.len()).any(|w| w == needle)
}

/// 같은 이름 (모듈 설명)
pub(crate) fn same_name(a: &[char], b: &[char]) -> bool {
    if a.is_empty() || b.is_empty() {
        return false;
    }
    let (short, long) = if a.len() <= b.len() { (a, b) } else { (b, a) };
    short == long || (short.len() >= 4 && contains(long, short))
}

/// 거의 같은 이름: 이어서 겹치는 가장 긴 부분이 3글자 이상이고 짧은 쪽 이름의 [SIMILAR_RATIO] 이상.
pub(crate) fn shares_word(a: &[char], b: &[char]) -> bool {
    let shorter = a.len().min(b.len());
    if shorter < 3 {
        return false;
    }
    let need = ((shorter as f64) * SIMILAR_RATIO).ceil().max(3.0) as usize;
    longest_common_run(a, b) >= need
}

/// 두 이름에서 이어서 겹치는 가장 긴 글자 수 (이름이 짧아 단순 이중 반복으로 충분)
fn longest_common_run(a: &[char], b: &[char]) -> usize {
    let mut best = 0;
    for i in 0..a.len() {
        for j in 0..b.len() {
            let mut k = 0;
            while i + k < a.len() && j + k < b.len() && a[i + k] == b[j + k] {
                k += 1;
            }
            best = best.max(k);
        }
    }
    best
}

/// 이 장소에 이 쿠폰이 붙나 (모듈 설명).
pub fn offer_matches(o: &PartnerOffer, name: &str, at: Option<Point>) -> bool {
    let at = at.filter(|p| p.lat.is_finite() && p.lon.is_finite() && p.lat.abs() <= 90.0 && p.lon.abs() <= 180.0);
    let d = at.map(|p| haversine_m(p, Point { lat: o.lat, lon: o.lon }));
    let (n, on) = (norm(name), norm(&o.place_name));
    if same_name(&n, &on) {
        return d.is_none_or(|d| d <= NAME_MAX_M);
    }
    match d {
        Some(d) if n.is_empty() => d <= UNNAMED_M,
        Some(d) => d <= NEAR_M && shares_word(&n, &on),
        None => false,
    }
}

/// 모든 시연 쿠폰.
pub fn all_offers() -> Vec<PartnerOffer> {
    OFFERS.iter().map(offer).collect()
}

/// 한 장소의 쿠폰 (표 순서).
pub fn offers_at(name: &str, at: Option<Point>) -> Vec<PartnerOffer> {
    OFFERS.iter().map(offer).filter(|o| offer_matches(o, name, at)).collect()
}

/// 코스(들른 곳 순서)의 쿠폰 (모듈 설명).
pub fn course_offers_of(stops: &[StopFact]) -> CourseOffers {
    let mut offers: Vec<PartnerOffer> = Vec::new();
    let mut place_count = 0;
    for s in stops {
        let found = offers_at(&s.name, Some(Point { lat: s.lat, lon: s.lon }));
        // 같은 곳을 두 번 들르면 새 쿠폰이 없으므로 곳 수도 한 번만 센다
        let before = offers.len();
        for o in found {
            if !offers.iter().any(|x| x.id == o.id) {
                offers.push(o);
            }
        }
        if offers.len() > before {
            place_count += 1;
        }
    }
    CourseOffers { place_count, offers }
}

/// 쿠폰 번호: 쿠폰 · 받은 시각으로 만든 4글자 (헷갈리는 0·O·1·I·B·8·S·5 빼고).
pub(crate) fn coupon_code(offer_id: &str, claimed_ms: i64) -> String {
    const ABC: &[u8] = b"ACDEFGHJKLMNPQRTUVWXY234679";
    let mut h: u64 = 0xcbf2_9ce4_8422_2325;
    for b in offer_id.bytes().chain(claimed_ms.to_le_bytes()) {
        h ^= u64::from(b);
        h = h.wrapping_mul(0x0100_0000_01b3);
    }
    let code: String = (0..4)
        .map(|i| ABC[((h >> (i * 13)) % ABC.len() as u64) as usize] as char)
        .collect();
    format!("EODI-{code}")
}

/// 장소마다 붙는 쿠폰 (넘긴 순서대로). Kotlin 공개는 `ffi::offers_for_places`.
pub fn offers_for(places: &[OfferPlace]) -> Vec<Vec<PartnerOffer>> {
    places.iter().map(|p| offers_at(&p.name, p.point.map(Point::from))).collect()
}

/// 쿠폰 하나 (없는 번호면 None).
pub fn find_offer(id: &str) -> Option<PartnerOffer> {
    OFFERS.iter().find(|r| r.0 == id).map(offer)
}

#[uniffi::export]
impl EodiStore {
    /// 쿠폰 받기. 이미 받았으면 처음 받은 것 그대로. 없는 쿠폰이면 `InvalidInput`.
    pub fn claim_offer(&self, offer_id: String, now_ms: i64) -> Result<ClaimedCoupon, CoreError> {
        let Some(o) = find_offer(&offer_id) else {
            return Err(CoreError::InvalidInput(format!("offer {offer_id} not found")));
        };
        let conn = self.lock()?;
        conn.execute(
            "INSERT INTO claimed_coupon (offer_id, code, claimed_ms) VALUES (?1, ?2, ?3) ON CONFLICT (offer_id) DO NOTHING",
            params![offer_id, coupon_code(&offer_id, now_ms), now_ms],
        )?;
        let (code, claimed_ms): (String, i64) = conn
            .prepare_cached("SELECT code, claimed_ms FROM claimed_coupon WHERE offer_id = ?1")?
            .query_row([&offer_id], |r| Ok((r.get(0)?, r.get(1)?)))?;
        Ok(ClaimedCoupon { offer: o, code, claimed_ms })
    }

    /// 받은 쿠폰 하나 (안 받았으면 None). 쿠폰 창이 "받았어요" 상태를 보일 때.
    pub fn claimed_coupon(&self, offer_id: String) -> Result<Option<ClaimedCoupon>, CoreError> {
        let conn = self.lock()?;
        let row: Option<(String, i64)> = conn
            .prepare_cached("SELECT code, claimed_ms FROM claimed_coupon WHERE offer_id = ?1")?
            .query_row([&offer_id], |r| Ok((r.get(0)?, r.get(1)?)))
            .optional()?;
        Ok(row.and_then(|(code, claimed_ms)| find_offer(&offer_id).map(|offer| ClaimedCoupon { offer, code, claimed_ms })))
    }

    /// 쿠폰함: 받은 쿠폰 (최근에 받은 것부터). 지금 목록에 없는 쿠폰은 뺀다.
    pub fn claimed_coupons(&self) -> Result<Vec<ClaimedCoupon>, CoreError> {
        let conn = self.lock()?;
        let mut st = conn.prepare_cached("SELECT offer_id, code, claimed_ms FROM claimed_coupon ORDER BY claimed_ms DESC, offer_id")?;
        let rows = st.query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?, r.get::<_, i64>(2)?)))?;
        let mut out = Vec::new();
        for row in rows {
            let (id, code, claimed_ms) = row?;
            if let Some(offer) = find_offer(&id) {
                out.push(ClaimedCoupon { offer, code, claimed_ms });
            }
        }
        Ok(out)
    }
}

#[cfg(test)]
mod tests;

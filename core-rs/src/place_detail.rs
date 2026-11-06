//! 장소 상세 창 (네이버 지도 장소 창처럼): 별점 요약 · 리뷰 · 내 방문 · 저장(♥) · 제휴 쿠폰을 한 번에 돌려준다.
//!
//! 화면 어디서든 장소(이름 · 좌표)를 누르면 Kotlin이 [EodiStore::place_detail]을 한 번 부르고 받은 그대로 그린다.
//!
//! # 같은 곳 고르기 ([same_spot])
//! 리뷰 · 코스의 들른 곳은 장소 번호가 없다 (이름 · 좌표). 누른 곳과
//! - 이름이 같으면(띄어쓰기 · 대소문자 무시, 또는 4글자 이상인 한쪽을 다른 쪽이 품으면) [SAME_PLACE_M](500m) 안,
//! - 이름이 조금 달라도(카카오 "초당순두부마을" ↔ 리뷰 "초당할머니순두부") 3글자가 겹치면 [NEAR_M](300m) 안,
//! - 한쪽 이름을 모르면 [UNNAMED_M](150m) 안일 때 같은 곳이다.
//!
//! 쿠폰은 `offers` 모듈 규칙 그대로 ([offers_at]).
//!
//! # 별점 · 하트
//! - 리뷰는 하트 많은 순(같으면 최신, 그다음 id). 최대 [MAX_REVIEWS]개.
//! - 평균 별점은 리뷰가 없으면 0. [PlaceDetail::rating_halves] = 평균을 반 별 단위로 반올림한 값 × 2 (별 그리기용, 0~10).
//! - 하트 = 이곳 리뷰 하트 합 + 이곳이 들어간 코스 하트 합 (인기 장소 순위와 같은 셈).
//!
//! # 내 방문
//! 내 기록의 장소(`place` 표) 중 누른 곳에서 [SAME_PLACE_M] 안이고 이름이 같은 곳, 또는 [PLACE_RADIUS_M](100m) 안이고
//! 이름을 모르거나 이름이 겹치는 곳을 모두 이 장소로 본다 (같은 가게가 주차 자리마다 장소 둘로 나뉜 경우).
//! - 다녀온 날 수 = 그 장소들에 머문 날 수 (같은 날은 한 번, 숨긴 기록 제외 — `day_place_dwell`), 마지막으로 머문 날.
//! - 저장(♥)은 그중 저장된 곳이 있으면 그곳, 없으면 가장 가까운 곳의 번호로 한다. 내 기록에 없는 곳은 None (저장할 수 없다).
//! - 리뷰 쓰기용 마지막 방문 = 리뷰 쓸 수 있는 곳(`review_candidates`, 실제로 머문 곳) 중 같은 곳.

use std::collections::HashSet;

use rusqlite::params;

use crate::community::{load_courses, load_reviews, sort_by_hearts, stop_point, valid_point, CommunitySort, PlaceReview, SAME_PLACE_M};
use crate::geo::{haversine_m, Point};
use crate::memory::StopFact;
use crate::offers::{norm, offers_at, same_name, shares_word, PartnerOffer, NEAR_M, UNNAMED_M};
use crate::places::PLACE_RADIUS_M;
use crate::saved::is_saved;
use crate::store::{format_day, CoreError, EodiStore};

/// 장소 창에 담는 리뷰 최대 수.
pub const MAX_REVIEWS: usize = 100;
/// 리뷰 쓰기용 마지막 방문을 찾을 때 보는 최근 들른 곳 수.
const VISIT_CANDIDATES: u32 = 200;

/// 장소 상세 창 한 장 (모듈 설명).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PlaceDetail {
    /// 누른 곳. 분류 · 주소가 비었으면 내 기록 · 리뷰에서 채운다.
    pub place: StopFact,
    /// 리뷰 평균 별점 (리뷰 없으면 0)
    pub avg_rating: f32,
    /// 평균 별점을 반 별 단위로 반올림한 값 × 2 (0~10, 예: 4.3 → 9 = 별 4개 반)
    pub rating_halves: u8,
    pub review_count: u32,
    /// 리뷰 하트 합 + 이곳이 들어간 코스 하트 합
    pub hearts: u32,
    /// 이곳 리뷰 (하트 많은 순)
    pub reviews: Vec<PlaceReview>,
    /// 내가 이곳에 머문 날 수 (숨긴 기록 제외)
    pub visit_days: u32,
    /// 마지막으로 머문 날 "YYYY-MM-DD" (없으면 None)
    pub last_visit_day: Option<String>,
    /// 리뷰 쓰기에 넘길 내 마지막 방문 (들른 시각 · 날씨 기준). 다녀온 적이 없으면 None.
    pub last_visit: Option<StopFact>,
    /// 저장(♥)에 쓸 내 장소 번호 (`set_place_saved`). 내 기록에 없는 곳이면 None.
    pub place_id: Option<i64>,
    pub saved: bool,
    /// 이곳 제휴 쿠폰
    pub offers: Vec<PartnerOffer>,
}

/// 누른 곳(정리한 이름 [qn], 좌표 [q])과 이름 · 좌표가 같은 곳인지 (모듈 설명).
pub(crate) fn same_spot(qn: &[char], q: Point, name: &str, at: Point) -> bool {
    let n = norm(name);
    let d = haversine_m(q, at);
    if qn.is_empty() || n.is_empty() {
        return d <= UNNAMED_M;
    }
    if same_name(qn, &n) {
        return d <= SAME_PLACE_M;
    }
    d <= NEAR_M && (shares_word(qn, &n) || shares_word(&n, qn))
}

/// 내 기록의 장소가 누른 곳인지 (모듈 설명 "내 방문").
fn same_my_place(qn: &[char], q: Point, name: Option<&str>, at: Point) -> bool {
    let n = norm(name.unwrap_or(""));
    let d = haversine_m(q, at);
    if !qn.is_empty() && !n.is_empty() && same_name(qn, &n) {
        return d <= SAME_PLACE_M;
    }
    d <= PLACE_RADIUS_M && (qn.is_empty() || n.is_empty() || shares_word(qn, &n) || shares_word(&n, qn))
}

/// 평균 별점 → 반 별 단위 × 2 (0~10).
pub(crate) fn rating_halves(avg: f64) -> u8 {
    if !avg.is_finite() || avg <= 0.0 {
        return 0;
    }
    (avg * 2.0).round().clamp(0.0, 10.0) as u8
}

#[uniffi::export]
impl EodiStore {
    /// 장소 상세 창 (모듈 설명). 좌표가 틀리면 `InvalidInput`.
    pub fn place_detail(&self, place: StopFact) -> Result<PlaceDetail, CoreError> {
        if !valid_point(place.lat, place.lon) {
            return Err(CoreError::InvalidInput(format!("장소 {:?} ({}, {})", place.name, place.lat, place.lon)));
        }
        let q = stop_point(&place);
        let qn = norm(&place.name);
        // 리뷰 쓸 수 있는 곳은 기억 검색을 거친다 (자기 잠금) → 잠그기 전에
        let last_visit = if qn.is_empty() {
            None
        } else {
            self.review_candidates(VISIT_CANDIDATES)?.into_iter().find(|s| same_spot(&qn, q, &s.name, stop_point(s)))
        };

        let mut conn = self.lock()?;
        self.flush_all(&mut conn)?;

        // 리뷰 · 하트
        let mut reviews: Vec<PlaceReview> =
            load_reviews(&conn, None)?.into_iter().filter(|r| same_spot(&qn, q, &r.place.name, stop_point(&r.place))).collect();
        sort_by_hearts(&mut reviews, CommunitySort::Popular, |r| (r.hearts, r.created_ms, r.id.as_str()));
        let review_count = reviews.len() as u32;
        let avg = if reviews.is_empty() { 0.0 } else { reviews.iter().map(|r| f64::from(r.rating)).sum::<f64>() / reviews.len() as f64 };
        let course_hearts: u64 = load_courses(&conn, None)?
            .iter()
            .filter(|c| c.stops.iter().any(|s| same_spot(&qn, q, &s.name, stop_point(s))))
            .map(|c| u64::from(c.hearts))
            .sum();
        let hearts = reviews.iter().map(|r| u64::from(r.hearts)).sum::<u64>() + course_hearts;

        // 내 방문 · 저장
        let dlat = SAME_PLACE_M / 111_000.0;
        let dlon = SAME_PLACE_M / (111_000.0 * q.lat.to_radians().cos().max(0.01));
        type Mine = (i64, Point, Option<String>, String, String);
        let mut mine: Vec<(f64, Mine)> = Vec::new();
        {
            let mut st = conn.prepare_cached(
                "SELECT id, lat, lon, name, category, address FROM place WHERE lat BETWEEN ?1 AND ?2 AND lon BETWEEN ?3 AND ?4",
            )?;
            let mut rows = st.query(params![q.lat - dlat, q.lat + dlat, q.lon - dlon, q.lon + dlon])?;
            while let Some(r) = rows.next()? {
                let at = Point { lat: r.get(1)?, lon: r.get(2)? };
                let name: Option<String> = r.get(3)?;
                if same_my_place(&qn, q, name.as_deref(), at) {
                    mine.push((haversine_m(q, at), (r.get(0)?, at, name, r.get(4)?, r.get(5)?)));
                }
            }
        }
        mine.sort_by(|a, b| a.0.total_cmp(&b.0).then(a.1 .0.cmp(&b.1 .0)));
        let mut days: HashSet<i64> = HashSet::new();
        let mut saved_id: Option<i64> = None;
        {
            let mut st = conn.prepare_cached("SELECT day FROM day_place_dwell WHERE place_id = ?1")?;
            for (_, (id, ..)) in &mine {
                let rows = st.query_map([id], |r| r.get::<_, i64>(0))?;
                for d in rows {
                    days.insert(d?);
                }
                if saved_id.is_none() && is_saved(&conn, *id)? {
                    saved_id = Some(*id);
                }
            }
        }
        let place_id = saved_id.or_else(|| mine.first().map(|m| m.1 .0));

        // 분류 · 주소 채우기: 내 기록 이름 → 리뷰
        let mut place = place;
        if place.category.trim().is_empty() || place.address.trim().is_empty() {
            let from_mine = mine.iter().find(|m| m.1 .2.is_some()).map(|m| (m.1 .3.clone(), m.1 .4.clone()));
            let from_review = reviews.first().map(|r| (r.place.category.clone(), r.place.address.clone()));
            for (cat, addr) in from_mine.into_iter().chain(from_review) {
                if place.category.trim().is_empty() && !cat.trim().is_empty() {
                    place.category = cat;
                }
                if place.address.trim().is_empty() && !addr.trim().is_empty() {
                    place.address = addr;
                }
            }
        }

        reviews.truncate(MAX_REVIEWS);
        let offers = offers_at(&place.name, Some(q));
        Ok(PlaceDetail {
            avg_rating: avg as f32,
            rating_halves: rating_halves(avg),
            review_count,
            hearts: hearts.min(u64::from(u32::MAX)) as u32,
            reviews,
            visit_days: days.len() as u32,
            last_visit_day: days.iter().max().map(|d| format_day(*d)),
            last_visit,
            place_id,
            saved: saved_id.is_some(),
            offers,
            place,
        })
    }
}

#[cfg(test)]
mod tests;

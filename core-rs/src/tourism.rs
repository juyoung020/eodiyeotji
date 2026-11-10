//! 관광 제휴 리포트 — 기록이 모여 생긴 커뮤니티 코스 · 리뷰를 지역(시 · 군)별로 묶어, 지자체 · 관광지 · 가게에
//! "우리 지역에 사람들이 어떻게 오고 어디를 좋아하는지"를 보여 주는 한 장.
//!
//! "어디였지"의 바탕은 차가 남기는 이동 기록이다. 각자의 기록은 비공개지만, 사람들이 직접 올린 코스 후기 · 리뷰는
//! 모일수록 지역 관광 데이터가 된다. 이 리포트가 그 데이터를 제휴(쿠폰 · 코스 패키지 · 시즌 홍보)로 잇는 화면이다.
//!
//! # 무엇을 세나 ([EodiStore::tourism_report])
//! - 코스: 그 지역으로 올라온 여행 코스 후기 (`community_course.region`). 하트 · 다녀온 계절.
//! - 리뷰: 주소가 그 지역인 장소 리뷰. 별점 · 키워드.
//! - 장소: 코스의 들른 곳과 리뷰를 같은 장소끼리 묶어 (이름이 같고 가까우면 같은 곳, `offers::same_name`)
//!   코스에 들어간 수 · 리뷰 수 · 평균 별점 · 하트로 순위. 제휴 쿠폰이 있는 곳인지도 붙인다.
//! - 쿠폰: 그 지역 장소의 제휴 쿠폰 수와 이 기기에서 받은 수 (`claimed_coupon`).
//! - 제안 ([TourismReport::ideas]): 가장 많이 오는 계절, 가장 인기 코스, 쿠폰이 없는 인기 장소, 리뷰 대표 키워드를
//!   제휴 문장으로 (규칙, LLM 없이).
//!
//! 개인정보: 내 기록이 아니라 사람들이 공개로 올린 글만 세고, 사람 이름 대신 수만 보인다 (글쓴이 수 = 참여한 사람 수).
//! 지금은 기기 안 시연 커뮤니티로 계산한다. 2차 단계에서 서버가 같은 계산을 전체 사용자로 한다.

use std::collections::{HashMap, HashSet};

use crate::community::{CoursePost, PlaceReview};
use crate::geo::{haversine_m, Point};
use crate::memory::episode::region_of;
use crate::store::{CoreError, EodiStore};

/// 이 거리(m) 안이고 이름이 같으면 같은 장소.
const SAME_PLACE_M: f64 = 300.0;
const TOP_N: usize = 5;
const SEASONS: [&str; 4] = ["봄", "여름", "가을", "겨울"];

/// 지역 하나의 활동량 (리포트 고르는 목록).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct RegionActivity {
    /// "강원 강릉시"
    pub region: String,
    pub courses: u32,
    pub reviews: u32,
    pub hearts: u32,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TourismCourse {
    pub id: String,
    pub title: String,
    pub hearts: u32,
    /// 들른 곳 이름 (순서대로)
    pub stops: Vec<String>,
    /// "가을 · 단풍철"
    pub season: String,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TourismPlace {
    pub name: String,
    pub category: String,
    pub address: String,
    pub lat: f64,
    pub lon: f64,
    /// 이 장소가 들어간 코스 수
    pub course_count: u32,
    pub review_count: u32,
    /// 리뷰 평균 별점 (없으면 0)
    pub avg_rating: f32,
    /// 코스 하트 + 리뷰 하트
    pub hearts: u32,
    /// 제휴 쿠폰이 있는 곳
    pub has_offer: bool,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SeasonShare {
    /// "봄" "여름" "가을" "겨울"
    pub name: String,
    /// 그 계절에 다녀온 코스 · 리뷰 수
    pub count: u32,
    /// 0~1
    pub share: f32,
}

/// 지역 리포트 한 장.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TourismReport {
    pub region: String,
    pub course_count: u32,
    pub review_count: u32,
    /// 코스 · 리뷰를 올린 사람 수
    pub contributors: u32,
    pub total_hearts: u32,
    /// 리뷰 평균 별점 (없으면 0)
    pub avg_rating: f32,
    pub seasons: Vec<SeasonShare>,
    pub top_courses: Vec<TourismCourse>,
    pub top_places: Vec<TourismPlace>,
    /// 리뷰에서 많이 고른 키워드 (많은 순, 최대 5)
    pub keywords: Vec<String>,
    /// 그 지역 장소의 제휴 쿠폰 수 · 이 기기에서 받은 수
    pub offer_count: u32,
    pub coupons_claimed: u32,
    /// 제휴 제안 (한 줄씩)
    pub ideas: Vec<String>,
}

#[uniffi::export]
impl EodiStore {
    /// 코스 · 리뷰가 있는 지역 (활동 많은 순 — 코스 × 3 + 리뷰 + 하트 / 100).
    pub fn tourism_regions(&self) -> Result<Vec<RegionActivity>, CoreError> {
        let (courses, reviews) = self.tourism_data()?;
        let mut m: HashMap<String, RegionActivity> = HashMap::new();
        let rows = courses.iter().map(|c| (c.region.clone(), true, c.hearts)).chain(reviews.iter().map(|r| (region_of(&r.place.address), false, r.hearts)));
        for (region, course, hearts) in rows {
            if region.is_empty() {
                continue;
            }
            let e = m.entry(region.clone()).or_insert(RegionActivity { region, courses: 0, reviews: 0, hearts: 0 });
            if course {
                e.courses += 1;
            } else {
                e.reviews += 1;
            }
            e.hearts += hearts;
        }
        let mut out: Vec<RegionActivity> = m.into_values().collect();
        out.sort_by(|a, b| activity(b).total_cmp(&activity(a)).then(a.region.cmp(&b.region)));
        Ok(out)
    }

    /// 지역("강원 강릉시") 하나의 관광 제휴 리포트.
    pub fn tourism_report(&self, region: String) -> Result<TourismReport, CoreError> {
        let (all_courses, all_reviews) = self.tourism_data()?;
        let courses: Vec<&CoursePost> = all_courses.iter().filter(|c| c.region == region).collect();
        let reviews: Vec<&PlaceReview> = all_reviews.iter().filter(|r| region_of(&r.place.address) == region).collect();
        let claimed: HashSet<String> = self.claimed_coupons()?.into_iter().map(|c| c.offer.id).collect();
        Ok(build(&region, &courses, &reviews, &claimed))
    }
}

impl EodiStore {
    fn tourism_data(&self) -> Result<(Vec<CoursePost>, Vec<PlaceReview>), CoreError> {
        let conn = self.snapshot()?;
        Ok((crate::community::load_courses(&conn, None)?, crate::community::load_reviews(&conn, None)?))
    }
}

fn activity(a: &RegionActivity) -> f64 {
    a.courses as f64 * 3.0 + a.reviews as f64 + a.hearts as f64 / 100.0
}

/// 같은 장소끼리 묶을 때 쓰는 한 곳.
struct Spot {
    place: TourismPlace,
    key: Vec<char>,
    rating_sum: u32,
    courses: HashSet<String>,
}

/// 같은 장소를 찾고, 없으면 새로 더한다. 반환: 자리.
fn find(spots: &mut Vec<Spot>, name: &str, category: &str, address: &str, lat: f64, lon: f64) -> usize {
    let key = crate::offers::norm(name);
    let at = Point { lat, lon };
    if let Some(i) = spots.iter().position(|s| {
        crate::offers::same_name(&s.key, &key) && haversine_m(Point { lat: s.place.lat, lon: s.place.lon }, at) <= SAME_PLACE_M
    }) {
        return i;
    }
    spots.push(Spot {
        place: TourismPlace {
            name: name.into(),
            category: category.into(),
            address: address.into(),
            lat,
            lon,
            course_count: 0,
            review_count: 0,
            avg_rating: 0.0,
            hearts: 0,
            has_offer: !crate::offers::offers_at(name, Some(at)).is_empty(),
        },
        key,
        rating_sum: 0,
        courses: HashSet::new(),
    });
    spots.len() - 1
}

fn build(region: &str, courses: &[&CoursePost], reviews: &[&PlaceReview], claimed: &HashSet<String>) -> TourismReport {
    // 장소 묶기
    let mut spots: Vec<Spot> = Vec::new();
    for c in courses {
        for s in &c.stops {
            if s.name.is_empty() {
                continue;
            }
            let i = find(&mut spots, &s.name, &s.category, &s.address, s.lat, s.lon);
            if spots[i].courses.insert(c.id.clone()) {
                spots[i].place.course_count += 1;
                spots[i].place.hearts += c.hearts;
            }
        }
    }
    for r in reviews {
        let p = &r.place;
        let i = find(&mut spots, &p.name, &p.category, &p.address, p.lat, p.lon);
        spots[i].place.review_count += 1;
        spots[i].place.hearts += r.hearts;
        spots[i].rating_sum += r.rating as u32;
    }
    for s in &mut spots {
        if s.place.review_count > 0 {
            s.place.avg_rating = s.rating_sum as f32 / s.place.review_count as f32;
        }
    }
    let score = |p: &TourismPlace| p.course_count as f64 * 3.0 + p.review_count as f64 * 2.0 + p.hearts as f64 / 50.0 + p.avg_rating as f64;
    let mut places: Vec<TourismPlace> = spots.into_iter().map(|s| s.place).collect();
    places.sort_by(|a, b| score(b).total_cmp(&score(a)).then(a.name.cmp(&b.name)));

    // 계절 (코스는 다녀온 날, 리뷰도 다녀온 날)
    let mut season_n = [0u32; 4];
    for day in courses.iter().map(|c| &c.visited_day).chain(reviews.iter().map(|r| &r.visited_day)) {
        if let Some(m) = day.get(5..7).and_then(|m| m.parse::<u32>().ok()) {
            let name = crate::recap::season_of(m);
            if let Some(i) = SEASONS.iter().position(|s| *s == name) {
                season_n[i] += 1;
            }
        }
    }
    let season_total: u32 = season_n.iter().sum();
    let seasons: Vec<SeasonShare> = SEASONS
        .iter()
        .zip(season_n)
        .map(|(n, c)| SeasonShare { name: (*n).into(), count: c, share: if season_total > 0 { c as f32 / season_total as f32 } else { 0.0 } })
        .collect();

    let mut top: Vec<&&CoursePost> = courses.iter().collect();
    top.sort_by(|a, b| b.hearts.cmp(&a.hearts).then(a.title.cmp(&b.title)));
    let top_courses: Vec<TourismCourse> = top
        .iter()
        .take(TOP_N)
        .map(|c| TourismCourse {
            id: c.id.clone(),
            title: c.title.clone(),
            hearts: c.hearts,
            stops: c.stops.iter().map(|s| s.name.clone()).filter(|n| !n.is_empty()).collect(),
            season: c.season.clone(),
        })
        .collect();

    let (kw, _) = crate::community::keyword_summary(reviews.iter().copied());
    let keywords: Vec<String> = kw.into_iter().take(TOP_N).map(|k| k.keyword).collect();

    // 쿠폰: 그 지역 장소에 붙은 제휴 쿠폰
    let mut offer_ids: HashSet<String> = HashSet::new();
    for p in &places {
        for o in crate::offers::offers_at(&p.name, Some(Point { lat: p.lat, lon: p.lon })) {
            offer_ids.insert(o.id);
        }
    }
    let coupons_claimed = offer_ids.iter().filter(|id| claimed.contains(*id)).count() as u32;

    let contributors: HashSet<&str> =
        courses.iter().map(|c| c.author.as_str()).chain(reviews.iter().map(|r| r.author.as_str())).collect();
    let total_hearts = courses.iter().map(|c| c.hearts).sum::<u32>() + reviews.iter().map(|r| r.hearts).sum::<u32>();
    let avg_rating = if reviews.is_empty() { 0.0 } else { reviews.iter().map(|r| r.rating as f32).sum::<f32>() / reviews.len() as f32 };

    let mut report = TourismReport {
        region: region.into(),
        course_count: courses.len() as u32,
        review_count: reviews.len() as u32,
        contributors: contributors.len() as u32,
        total_hearts,
        avg_rating,
        seasons,
        top_courses,
        top_places: places.into_iter().take(TOP_N).collect(),
        keywords,
        offer_count: offer_ids.len() as u32,
        coupons_claimed,
        ideas: vec![],
    };
    report.ideas = ideas(&report);
    report
}

/// 제휴 제안 문장 (규칙).
fn ideas(r: &TourismReport) -> Vec<String> {
    let mut out = Vec::new();
    let city = short_name(&r.region);
    if let Some(peak) = r.seasons.iter().max_by(|a, b| a.share.total_cmp(&b.share)).filter(|s| s.count > 0) {
        out.push(format!(
            "{} 후기의 {}%가 {}에 다녀온 기록이에요 → {} 시즌에 맞춘 제휴 쿠폰 · 축제 홍보를 이때 회상 알림과 함께 보내요",
            city,
            (peak.share * 100.0).round() as i64,
            peak.name,
            peak.name
        ));
    }
    if let Some(c) = r.top_courses.first().filter(|c| c.stops.len() >= 2) {
        out.push(format!(
            "가장 인기 코스 '{}' ({}) → 이 길의 곳들을 묶은 코스 쿠폰 패키지를 만들면 코스를 고른 차에 바로 안내돼요",
            c.title,
            c.stops.iter().take(4).cloned().collect::<Vec<_>>().join(" → ")
        ));
    }
    if let Some(p) = r.top_places.iter().find(|p| !p.has_offer && (p.review_count > 0 || p.course_count > 1)) {
        let rating = if p.review_count > 0 { format!(" · 별점 {:.1}", p.avg_rating) } else { String::new() };
        out.push(format!(
            "{}은(는) 코스 {}개 · 리뷰 {}개{}로 인기인데 제휴 쿠폰이 없어요 → 첫 제휴 대상",
            p.name, p.course_count, p.review_count, rating
        ));
    }
    if let Some(k) = r.keywords.first() {
        out.push(format!("리뷰에서 '{k}'을(를) 가장 많이 골라요 → 지역 홍보 문구 · 추천 카드 제목에 써요"));
    }
    out
}

/// "강원 강릉시" → "강릉", "부산 해운대구" → "부산 해운대구".
fn short_name(region: &str) -> String {
    let mut it = region.split_whitespace();
    let (Some(a), b) = (it.next(), it.next()) else { return region.to_string() };
    match b {
        Some(sgg) if sgg.ends_with('시') || sgg.ends_with('군') => sgg.trim_end_matches(['시', '군']).to_string(),
        Some(sgg) => format!("{a} {sgg}"),
        None => a.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn demo_community_makes_a_partner_report() {
        let path = std::env::temp_dir().join(format!("eodi-tour-{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        let s = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        s.seed_community_demo(1_794_013_200_000).unwrap();
        let regions = s.tourism_regions().unwrap();
        assert!(!regions.is_empty());
        assert!(regions.windows(2).all(|w| activity(&w[0]) >= activity(&w[1])), "활동 많은 순");
        let top = &regions[0];
        let r = s.tourism_report(top.region.clone()).unwrap();
        assert_eq!(r.course_count, top.courses);
        assert_eq!(r.review_count, top.reviews);
        assert!(r.contributors > 0 && r.total_hearts > 0);
        assert_eq!(r.seasons.len(), 4);
        let share: f32 = r.seasons.iter().map(|x| x.share).sum();
        assert!((share - 1.0).abs() < 1e-3, "{share}");
        assert!(!r.top_places.is_empty() && r.top_places.len() <= 5);
        assert!(r.top_courses.windows(2).all(|w| w[0].hearts >= w[1].hearts));
        assert!(!r.ideas.is_empty(), "제휴 제안");

        // 강릉: 초당할머니순두부는 쿠폰이 있는 곳
        if let Some(g) = regions.iter().find(|x| x.region == "강원 강릉시") {
            let r = s.tourism_report(g.region.clone()).unwrap();
            if let Some(p) = r.top_places.iter().find(|p| p.name.contains("초당할머니")) {
                assert!(p.has_offer);
            }
            assert!(r.offer_count > 0);
        }
        assert_eq!(s.tourism_report("없는 지역".into()).unwrap().course_count, 0);
        drop(s);
        let _ = std::fs::remove_file(&path);
    }

    #[test]
    fn short_region_names() {
        assert_eq!(short_name("강원 강릉시"), "강릉");
        assert_eq!(short_name("경기 가평군"), "가평");
        assert_eq!(short_name("부산 해운대구"), "부산 해운대구");
        assert_eq!(short_name("제주"), "제주");
    }
}

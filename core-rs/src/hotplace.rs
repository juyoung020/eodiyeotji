//! 핫플 · 가고 싶은 곳.
//!
//! 이용자가 아직 적어 코스 · 리뷰가 얇은 지역도 "갈 만한 곳"이 보이도록, SNS에서 이름난 곳 목록을 먼저 보여 주고
//! 그 위에 **어디였지 이용자가 실제로 다녀온 흔적**을 얹는다. 그래서 목록은 남이 만든 것이어도, 믿을 근거는 우리 기록이다.
//!
//! # 핫플 ([EodiStore::hot_places])
//! - 목록 [HOT]: SNS에서 이름난 곳의 **시연용 예시**다. 인스타그램은 장소 · 인기 게시물을 내주는 공개 통로가 없어
//!   실제 수집이 아니다 → 화면은 "예시"라고 밝힌다. 장소 정보(이름 · 좌표)는 시연 커뮤니티의 실제 장소([PLACES])를 쓴다.
//! - 다녀온 이용자 수 `visitors`: 그 장소에 리뷰를 썼거나 그 장소가 든 코스를 올린 **서로 다른 작성자** 수 (나는 뺀다).
//!   리뷰와 코스는 내 기록에서만 올릴 수 있으므로 "실제로 다녀온 사람"이다. 같은 장소 판정은 커뮤니티와 같다([same_place]).
//! - 내가 다녀온 날 `my_visit_days` · `my_last_day`: 그 좌표 [MY_VISIT_M] 안의 내 장소들에 머문 날 (숨긴 기록 제외).
//! - 순서: 목록 순서 그대로. [near]가 있으면 반경 안만.
//! - 핀 분류 `kind`: 음식 · 카페 · 여행 ([pin_kind], 장소 분류 글자로 정한다) → 지도 핀 모양과 "근처 카페 핫플" 같은 거르기에 쓴다.
//!
//! # 가고 싶은 곳 ([EodiStore::add_wish_place] 등, DB 구조 버전 17 `wish_place`)
//! 핫플의 ♥, 또는 폰에서 다른 앱(인스타그램 · 지도)의 공유로 들어온 곳. 아직 가 보지 않은 곳이라 `place`(다녀온 장소) 표와 따로 둔다.
//! 기록 계산과 상관없는 곁가지 표라 캐시에 넣지 않는다. 같은 장소([same_place])를 두 번 넣으면 처음 것을 돌려준다. 근처를 지날 때 알려 줄 후보는 [EodiStore::wish_places_near]
//! (아직 안 가 본 곳만, 가까운 순).
//!
//! # 공유로 받은 글 ([parse_shared_text])
//! 다른 앱의 "공유"는 글 한 덩어리(설명 + 주소)로 온다. 첫 `http(s)://` 주소를 떼어 출처를 가리고, 남은 글을 장소 이름 힌트로 준다.
//! 인스타그램 주소는 `?` 뒤(공유한 사람을 가리키는 값)를 버린다. 게시물 내용은 읽지 않는다 — 장소는 사용자가 이름으로 찾아 고른다.

use std::collections::HashSet;

use rusqlite::{params, Connection};

use crate::community::demo::PLACES;
use crate::community::{load_courses, load_reviews, same_place, stop_point, valid_point, CoursePost, NearFilter, PlaceReview};
use crate::ffi::GeoPoint;
use crate::geo::{haversine_m, Point};
use crate::memory::StopFact;
use crate::store::{format_day, CoreError, EodiStore};

/// DB 구조 버전 17: 가고 싶은 곳.
pub(crate) const SCHEMA_V17: &str = "
CREATE TABLE IF NOT EXISTS wish_place (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT '',
    address TEXT NOT NULL DEFAULT '',
    lat REAL NOT NULL,
    lon REAL NOT NULL,
    -- WishSource 번호
    source INTEGER NOT NULL,
    url TEXT NOT NULL DEFAULT '',
    saved_ms INTEGER NOT NULL
);
";

/// 버전 17 표를 지운다 (예전 DB에서 올리는 테스트용).
#[cfg(test)]
pub(crate) const DROP_V17: &str = "DROP TABLE wish_place;";

/// 내 장소가 이 거리 안이면 "나도 다녀온 곳"으로 본다 (해변 · 시장처럼 넓은 곳을 생각한 값).
pub const MY_VISIT_M: f64 = 300.0;
/// 장소 이름 힌트 최대 글자 수.
const HINT_MAX: usize = 60;

/// 핫플 예시 목록 (이름, 한마디 꼬리표). 이름은 [PLACES]에 있는 것만 쓴다 (좌표 · 주소를 거기서 가져온다).
const HOT: &[(&str, &str)] = &[
    ("서피비치", "서핑"),
    ("안목해변 카페거리", "오션뷰 카페"),
    ("어니언 성수", "성수 카페"),
    ("테라로사 커피공장 강릉본점", "커피"),
    ("대관령 양떼목장", "초원 산책"),
    ("경포해변", "일출"),
    ("죽도해변", "서핑"),
    ("초당할머니순두부", "순두부"),
    ("서울숲", "피크닉"),
    ("낙산사", "바다 절"),
    ("주문진수산시장", "시장 먹거리"),
    ("영금정", "바다 전망"),
];

/// 가고 싶은 곳이 어디서 왔나.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum WishSource {
    /// 핫플 목록의 ♥
    HotList,
    Instagram,
    NaverMap,
    KakaoMap,
    /// 그 밖의 주소
    Web,
    /// 주소 없이 이름만
    Manual,
}

impl WishSource {
    fn code(self) -> i64 {
        match self {
            WishSource::HotList => 0,
            WishSource::Instagram => 1,
            WishSource::NaverMap => 2,
            WishSource::KakaoMap => 3,
            WishSource::Web => 4,
            WishSource::Manual => 5,
        }
    }

    fn from_code(c: i64) -> Self {
        match c {
            0 => WishSource::HotList,
            1 => WishSource::Instagram,
            2 => WishSource::NaverMap,
            3 => WishSource::KakaoMap,
            4 => WishSource::Web,
            _ => WishSource::Manual,
        }
    }
}

/// 지도 핀 분류 (장소 분류 글자에서 정한다, [pin_kind]).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum PinKind {
    Food,
    Cafe,
    /// 관광지 · 해변 · 공원 등 그 밖의 갈 곳
    Travel,
}

/// 장소 분류 글자(예: "두부전문점", "카페거리", "해수욕장") → 핀 분류. 카페를 먼저 본다 ("카페거리"는 카페).
#[uniffi::export]
pub fn pin_kind(category: String) -> PinKind {
    const CAFE: &[&str] = &["카페", "커피", "베이커리", "디저트", "제과"];
    const FOOD: &[&str] = &["식당", "음식", "전문점", "맛집", "시장", "횟집", "국밥", "고기", "분식", "레스토랑", "뷔페"];
    let has = |words: &[&str]| words.iter().any(|w| category.contains(w));
    if has(CAFE) {
        PinKind::Cafe
    } else if has(FOOD) {
        PinKind::Food
    } else {
        PinKind::Travel
    }
}

/// 핫플 하나 (모듈 설명).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct HotPlace {
    pub place: StopFact,
    pub kind: PinKind,
    /// 목록 순위 (1부터)
    pub rank: u32,
    /// 한마디 꼬리표 (예: "오션뷰 카페")
    pub tag: String,
    /// 실제로 다녀온 어디였지 이용자 수 (리뷰 · 코스 작성자, 나 제외)
    pub visitors: u32,
    pub review_count: u32,
    /// 리뷰 평균 별점 (리뷰 없으면 0)
    pub avg_rating: f32,
    /// 내가 머문 날 수 (0 = 아직 안 가 봄)
    pub my_visit_days: u32,
    /// 내가 마지막으로 간 날 "YYYY-MM-DD"
    pub my_last_day: Option<String>,
    /// 가고 싶은 곳에 넣었으면 그 번호
    pub wish_id: Option<i64>,
}

/// 가고 싶은 곳 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct WishPlace {
    pub id: i64,
    pub place: StopFact,
    pub kind: PinKind,
    pub source: WishSource,
    /// 받은 주소 (없으면 빈 문자열)
    pub url: String,
    pub saved_ms: i64,
    pub visitors: u32,
    pub my_visit_days: u32,
    pub my_last_day: Option<String>,
}

/// 공유로 받은 글을 나눈 것.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct SharedLink {
    /// 주소 (없으면 빈 문자열)
    pub url: String,
    pub source: WishSource,
    /// 장소 이름 힌트 (주소를 뺀 나머지 글, 없으면 빈 문자열)
    pub hint: String,
}

/// 공유로 받은 글 → 주소 · 출처 · 이름 힌트 (모듈 설명).
#[uniffi::export]
pub fn parse_shared_text(text: String) -> SharedLink {
    let start = text.find("https://").or_else(|| text.find("http://"));
    let (url, rest) = match start {
        Some(i) => {
            let end = text[i..].find(char::is_whitespace).map_or(text.len(), |n| i + n);
            (text[i..end].to_string(), format!("{} {}", &text[..i], &text[end..]))
        }
        None => (String::new(), text.clone()),
    };
    let host = url.split("://").nth(1).and_then(|s| s.split(['/', '?', '#']).next()).unwrap_or("").to_ascii_lowercase();
    let host = host.strip_prefix("www.").unwrap_or(&host);
    let is = |names: &[&str]| names.iter().any(|n| host == *n || host.ends_with(&format!(".{n}")));
    let source = if url.is_empty() {
        WishSource::Manual
    } else if is(&["instagram.com", "instagr.am"]) {
        WishSource::Instagram
    } else if is(&["naver.me", "map.naver.com", "place.naver.com"]) {
        WishSource::NaverMap
    } else if is(&["kko.to", "kko.kakao.com", "map.kakao.com"]) {
        WishSource::KakaoMap
    } else {
        WishSource::Web
    };
    let url = match (source, url.find('?')) {
        (WishSource::Instagram, Some(q)) => url[..q].to_string(),
        _ => url,
    };
    let hint: String = rest.split_whitespace().collect::<Vec<_>>().join(" ").chars().take(HINT_MAX).collect();
    SharedLink { url, source, hint }
}

#[uniffi::export]
impl EodiStore {
    /// 핫플 목록 (모듈 설명). `limit` 0이면 전부.
    pub fn hot_places(&self, near: Option<NearFilter>, limit: u32) -> Result<Vec<HotPlace>, CoreError> {
        let near = match near {
            None => None,
            Some(n) if valid_point(n.center.lat, n.center.lon) && n.radius_km.is_finite() && n.radius_km > 0.0 => {
                Some((Point::from(n.center), n.radius_km * 1000.0))
            }
            Some(n) => return Err(CoreError::InvalidInput(format!("near {n:?}"))),
        };
        let conn = self.read()?;
        let feed = Feed::load(&conn)?;
        let wishes = load_wishes(&conn)?;
        let mut out = Vec::new();
        for (i, &(name, tag)) in HOT.iter().enumerate() {
            let Some(place) = poi(name) else { continue };
            if near.is_some_and(|(c, r)| haversine_m(c, stop_point(&place)) > r) {
                continue;
            }
            let (review_count, rating_sum) = feed.rating(&place);
            let (my_visit_days, my_last_day) = my_visits(&conn, &place)?;
            out.push(HotPlace {
                kind: pin_kind(place.category.clone()),
                rank: i as u32 + 1,
                tag: tag.to_string(),
                visitors: feed.visitors(&place),
                review_count,
                avg_rating: if review_count == 0 { 0.0 } else { rating_sum as f32 / review_count as f32 },
                my_visit_days,
                my_last_day,
                wish_id: wishes.iter().find(|w| same_place(&w.1, &place)).map(|w| w.0),
                place,
            });
        }
        if limit > 0 {
            out.truncate(limit as usize);
        }
        Ok(out)
    }

    /// 가고 싶은 곳에 넣는다. 같은 장소가 이미 있으면 그것을 돌려준다 (주소가 비어 있었으면 채운다).
    /// 이름이 비었거나 좌표가 틀리면 `InvalidInput`.
    pub fn add_wish_place(&self, place: StopFact, source: WishSource, url: String, now_ms: i64) -> Result<WishPlace, CoreError> {
        let name = place.name.trim().to_string();
        if name.is_empty() || !valid_point(place.lat, place.lon) {
            return Err(CoreError::InvalidInput(format!("wish place {:?} ({}, {})", place.name, place.lat, place.lon)));
        }
        let url = url.trim().to_string();
        let id = {
            let conn = self.lock_side()?;
            match load_wishes(&conn)?.into_iter().find(|w| same_place(&w.1, &place)) {
                Some(w) => {
                    if w.3.is_empty() && !url.is_empty() {
                        conn.execute("UPDATE wish_place SET url = ?2, source = ?3 WHERE id = ?1", params![w.0, url, source.code()])?;
                    }
                    w.0
                }
                None => {
                    conn.execute(
                        "INSERT INTO wish_place (name, category, address, lat, lon, source, url, saved_ms)
                         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
                        params![name, place.category.trim(), place.address.trim(), place.lat, place.lon, source.code(), url, now_ms],
                    )?;
                    conn.last_insert_rowid()
                }
            }
        };
        self.wish_places()?.into_iter().find(|w| w.id == id).ok_or_else(|| CoreError::Db(format!("wish {id} 없음")))
    }

    /// 가고 싶은 곳에서 뺀다. 반환: 뺐으면 true.
    pub fn remove_wish_place(&self, id: i64) -> Result<bool, CoreError> {
        let conn = self.lock_side()?;
        Ok(conn.execute("DELETE FROM wish_place WHERE id = ?1", [id])? > 0)
    }

    /// 가고 싶은 곳 (최근에 넣은 것부터).
    pub fn wish_places(&self) -> Result<Vec<WishPlace>, CoreError> {
        let conn = self.read()?;
        let feed = Feed::load(&conn)?;
        let mut out = Vec::new();
        for (id, place, source, url, saved_ms) in load_wishes(&conn)? {
            let (my_visit_days, my_last_day) = my_visits(&conn, &place)?;
            out.push(WishPlace { id, kind: pin_kind(place.category.clone()), visitors: feed.visitors(&place), my_visit_days, my_last_day, place, source, url, saved_ms });
        }
        Ok(out)
    }

    /// 지금 위치 [center] 반경 `radius_m` 안의, 아직 가 보지 않은 가고 싶은 곳 (가까운 순). 지나갈 때 알려 줄 후보.
    pub fn wish_places_near(&self, center: GeoPoint, radius_m: f64) -> Result<Vec<WishPlace>, CoreError> {
        if !valid_point(center.lat, center.lon) || !radius_m.is_finite() || radius_m <= 0.0 {
            return Err(CoreError::InvalidInput(format!("near {center:?} {radius_m}")));
        }
        let c = Point::from(center);
        let mut near: Vec<(f64, WishPlace)> = self
            .wish_places()?
            .into_iter()
            .filter(|w| w.my_visit_days == 0)
            .map(|w| (haversine_m(c, stop_point(&w.place)), w))
            .filter(|(d, _)| *d <= radius_m)
            .collect();
        near.sort_by(|a, b| a.0.total_cmp(&b.0).then(a.1.id.cmp(&b.1.id)));
        Ok(near.into_iter().map(|(_, w)| w).collect())
    }
}

/// 목록 이름 → 장소 (시연 커뮤니티의 장소 표에서).
fn poi(name: &str) -> Option<StopFact> {
    PLACES.iter().find(|p| p.0 == name).map(|&(name, category, address, lat, lon)| StopFact {
        name: name.into(),
        category: category.into(),
        address: address.into(),
        arrive_ms: 0,
        leave_ms: 0,
        lat,
        lon,
        first_visit: false,
    })
}

/// 커뮤니티 리뷰 · 코스 (다녀온 이용자 수 · 별점 계산용).
struct Feed {
    reviews: Vec<PlaceReview>,
    courses: Vec<CoursePost>,
}

impl Feed {
    fn load(conn: &Connection) -> Result<Self, CoreError> {
        Ok(Feed { reviews: load_reviews(conn, None)?, courses: load_courses(conn, None)? })
    }

    /// 그 장소에 리뷰를 썼거나 그 장소가 든 코스를 올린 서로 다른 작성자 수 (나 제외).
    fn visitors(&self, place: &StopFact) -> u32 {
        let mut who: HashSet<&str> = HashSet::new();
        who.extend(self.reviews.iter().filter(|r| !r.mine && same_place(&r.place, place)).map(|r| r.author.as_str()));
        who.extend(
            self.courses.iter().filter(|c| !c.mine && c.stops.iter().any(|s| same_place(s, place))).map(|c| c.author.as_str()),
        );
        who.len() as u32
    }

    /// (리뷰 수, 별점 합)
    fn rating(&self, place: &StopFact) -> (u32, u32) {
        self.reviews.iter().filter(|r| same_place(&r.place, place)).fold((0, 0), |(n, sum), r| (n + 1, sum + r.rating as u32))
    }
}

type WishRow = (i64, StopFact, WishSource, String, i64);

/// 가고 싶은 곳 줄 (최근에 넣은 것부터).
fn load_wishes(conn: &Connection) -> rusqlite::Result<Vec<WishRow>> {
    let mut st = conn.prepare_cached(
        "SELECT id, name, category, address, lat, lon, source, url, saved_ms FROM wish_place ORDER BY saved_ms DESC, id DESC",
    )?;
    let rows = st.query_map([], |r| {
        let place = StopFact {
            name: r.get(1)?,
            category: r.get(2)?,
            address: r.get(3)?,
            arrive_ms: 0,
            leave_ms: 0,
            lat: r.get(4)?,
            lon: r.get(5)?,
            first_visit: false,
        };
        Ok((r.get(0)?, place, WishSource::from_code(r.get(6)?), r.get(7)?, r.get(8)?))
    })?;
    rows.collect()
}

/// 내가 그 장소에 머문 날 수와 마지막 날 (모듈 설명). 반경 안의 내 장소가 여럿이면(주차한 곳 · 머문 곳) 모두 합쳐 날짜로 센다.
fn my_visits(conn: &Connection, place: &StopFact) -> rusqlite::Result<(u32, Option<String>)> {
    let p = stop_point(place);
    let dlat = MY_VISIT_M / 111_000.0;
    let dlon = MY_VISIT_M / (111_000.0 * p.lat.to_radians().cos().max(0.01));
    let mut st = conn.prepare_cached(
        "SELECT d.day, p.lat, p.lon FROM place p JOIN day_place_dwell d ON d.place_id = p.id
         WHERE p.lat BETWEEN ?1 AND ?2 AND p.lon BETWEEN ?3 AND ?4",
    )?;
    let mut rows = st.query(params![p.lat - dlat, p.lat + dlat, p.lon - dlon, p.lon + dlon])?;
    let mut days: HashSet<i64> = HashSet::new();
    while let Some(r) = rows.next()? {
        if haversine_m(p, Point { lat: r.get(1)?, lon: r.get(2)? }) <= MY_VISIT_M {
            days.insert(r.get(0)?);
        }
    }
    Ok((days.len() as u32, days.iter().max().copied().map(format_day)))
}

#[cfg(test)]
mod tests;

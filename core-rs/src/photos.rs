//! 들른 곳 대표 사진 (구글 Places API (New) 사진).
//!
//! 사진을 받아 오는 네트워크 호출은 Kotlin(`place/PlacePhotoRepository.kt`)이 하고, 여기서는
//! ① 장소를 가리키는 키를 만들고 ② 구글 응답(JSON)에서 장소 id·사진 목록을 골라내고 ③ 결과를 기기 DB에 저장해
//! 같은 곳을 다시 묻지 않게 한다.
//!
//! 구글 약관상 장소 id는 계속 둬도 되지만, 그 밖의 내용(사진 이름·작성자)은 30일까지만 둔다 → [PHOTO_TTL_MS]가 지나면
//! 저장분을 없는 것으로 보고 다시 받는다. 사진 파일 자체는 저장하지 않는다(화면의 이미지 캐시만).

use rusqlite::{params, OptionalExtension};
use serde_json::Value;

use crate::store::{CoreError, EodiStore};

/// 저장한 사진 정보를 믿는 기간 (구글 Places 약관: 장소 id 외 내용은 30일까지).
pub const PHOTO_TTL_MS: i64 = 30 * 86_400_000;
/// 한 장소에서 남기는 사진 수 (카드에서 넘겨 보는 만큼).
pub const MAX_PHOTOS: usize = 6;

pub(crate) const SCHEMA_V4: &str = "
-- 들른 곳 대표 사진 (구글 Places). key = place_photo_key(이름, 좌표). photos = PlacePhoto 목록(JSON).
-- found = 0: 구글에서 그 장소를 못 찾음(사진 없음) — 이것도 저장해서 다시 묻지 않는다.
CREATE TABLE place_photo (
    key TEXT PRIMARY KEY,
    found INTEGER NOT NULL,
    google_place_id TEXT,
    photos TEXT NOT NULL,
    fetched_ms INTEGER NOT NULL
);
";

/// 예전 버전 DB를 흉내 내는 테스트용 (v4 표 지우기).
#[cfg(test)]
pub(crate) const DROP_V4: &str = "DROP TABLE place_photo;";

/// 사진 한 장. [name]은 구글 사진 리소스 이름(`places/…/photos/…`) — 이미지는 `…/{name}/media`로 받는다.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PlacePhoto {
    pub name: String,
    pub width_px: u32,
    pub height_px: u32,
    /// 사진 올린 사람 (구글 약관: 사진과 함께 보여 줘야 한다). 없으면 빈 값.
    pub author: String,
}

/// 한 장소의 사진 묶음. [found]가 false면 구글에서 그 장소를 못 찾은 것(다시 묻지 않음).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PlacePhotos {
    pub found: bool,
    pub google_place_id: Option<String>,
    pub photos: Vec<PlacePhoto>,
}

/// 장소를 가리키는 저장 키: 이름 + 좌표(소수 4자리 ≈ 10 m). 같은 가게를 여러 번 들러도 한 번만 묻는다.
#[uniffi::export]
pub fn place_photo_key(name: String, lat: f64, lon: f64) -> String {
    format!("{}@{:.4},{:.4}", name.trim(), lat, lon)
}

/// 구글 Text Search(New) 검색어: 이름에 지역(주소 앞 두 단어 중 시·군·구)을 붙인다 ("강릉시 초당할머니순두부").
#[uniffi::export]
pub fn place_photo_query(name: String, address: String) -> String {
    let city = address.split_whitespace().nth(1).unwrap_or("");
    let name = name.trim();
    if city.is_empty() || name.contains(city.trim_end_matches(['시', '군', '구'])) {
        name.to_string()
    } else {
        format!("{city} {name}")
    }
}

/// 구글 Text Search(New) 응답에서 첫 장소의 id·사진을 골라낸다 (필드 마스크 `places.id,places.photos`).
/// 장소가 없으면 found = false. JSON이 아니면 오류.
#[uniffi::export]
pub fn parse_google_place_photos(json: String) -> Result<PlacePhotos, CoreError> {
    let v: Value = serde_json::from_str(&json).map_err(|e| CoreError::InvalidInput(format!("구글 응답 JSON: {e}")))?;
    let Some(place) = v["places"].as_array().and_then(|a| a.first()) else {
        return Ok(PlacePhotos { found: false, google_place_id: None, photos: vec![] });
    };
    let photos = place["photos"]
        .as_array()
        .map(|a| {
            a.iter()
                .filter_map(|p| {
                    let name = p["name"].as_str()?.to_string();
                    let author = p["authorAttributions"]
                        .as_array()
                        .and_then(|a| a.first())
                        .and_then(|a| a["displayName"].as_str())
                        .unwrap_or("")
                        .to_string();
                    Some(PlacePhoto {
                        name,
                        width_px: p["widthPx"].as_u64().unwrap_or(0) as u32,
                        height_px: p["heightPx"].as_u64().unwrap_or(0) as u32,
                        author,
                    })
                })
                .take(MAX_PHOTOS)
                .collect()
        })
        .unwrap_or_default();
    Ok(PlacePhotos { found: true, google_place_id: place["id"].as_str().map(str::to_string), photos })
}

#[uniffi::export]
impl EodiStore {
    /// 저장해 둔 사진 (없거나 [PHOTO_TTL_MS]보다 오래됐으면 None → 다시 받는다).
    pub fn cached_place_photos(&self, key: String, now_ms: i64) -> Result<Option<PlacePhotos>, CoreError> {
        let conn = self.lock()?;
        let row: Option<(bool, Option<String>, String, i64)> = conn
            .query_row(
                "SELECT found, google_place_id, photos, fetched_ms FROM place_photo WHERE key = ?1",
                params![key],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
            )
            .optional()?;
        let Some((found, id, photos, fetched)) = row else { return Ok(None) };
        if now_ms - fetched > PHOTO_TTL_MS {
            return Ok(None);
        }
        Ok(Some(PlacePhotos { found, google_place_id: id, photos: decode_photos(&photos) }))
    }

    /// 받은 사진을 저장한다 (같은 키는 덮어쓴다).
    pub fn save_place_photos(&self, key: String, photos: PlacePhotos, now_ms: i64) -> Result<(), CoreError> {
        let conn = self.lock()?;
        conn.execute(
            "INSERT INTO place_photo (key, found, google_place_id, photos, fetched_ms) VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT (key) DO UPDATE SET found = excluded.found, google_place_id = excluded.google_place_id,
                 photos = excluded.photos, fetched_ms = excluded.fetched_ms",
            params![key, photos.found, photos.google_place_id, encode_photos(&photos.photos), now_ms],
        )?;
        Ok(())
    }
}

fn encode_photos(photos: &[PlacePhoto]) -> String {
    Value::Array(
        photos
            .iter()
            .map(|p| serde_json::json!({ "n": p.name, "w": p.width_px, "h": p.height_px, "a": p.author }))
            .collect(),
    )
    .to_string()
}

fn decode_photos(s: &str) -> Vec<PlacePhoto> {
    serde_json::from_str::<Value>(s)
        .ok()
        .and_then(|v| v.as_array().cloned())
        .unwrap_or_default()
        .iter()
        .filter_map(|p| {
            Some(PlacePhoto {
                name: p["n"].as_str()?.to_string(),
                width_px: p["w"].as_u64().unwrap_or(0) as u32,
                height_px: p["h"].as_u64().unwrap_or(0) as u32,
                author: p["a"].as_str().unwrap_or("").to_string(),
            })
        })
        .collect()
}

#[cfg(test)]
mod tests;

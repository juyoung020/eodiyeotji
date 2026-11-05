//! 대표 사진: 키·검색어·구글 응답 고르기·저장(30일).

use super::*;

const GOOGLE: &str = r#"{"places":[{"id":"ChIJabc","photos":[
  {"name":"places/ChIJabc/photos/P1","widthPx":4032,"heightPx":3024,"authorAttributions":[{"displayName":"한동희","uri":"x"}]},
  {"name":"places/ChIJabc/photos/P2","widthPx":1200,"heightPx":1600}
]},{"id":"other"}]}"#;

#[test]
fn key_rounds_coordinates() {
    assert_eq!(place_photo_key(" 경포해변 ".into(), 37.805_512, 128.908_349), "경포해변@37.8055,128.9083");
    // 10 m 안에서 흔들려도 같은 키
    assert_eq!(place_photo_key("A".into(), 37.805_51, 128.908_34), place_photo_key("A".into(), 37.805_54, 128.908_31));
}

#[test]
fn query_adds_city_unless_already_named() {
    assert_eq!(place_photo_query("초당할머니순두부".into(), "강원특별자치도 강릉시 초당동 307-4".into()), "강릉시 초당할머니순두부");
    assert_eq!(place_photo_query("강릉 커피거리".into(), "강원 강릉시 견소동".into()), "강릉 커피거리");
    assert_eq!(place_photo_query("광장시장".into(), "".into()), "광장시장");
}

#[test]
fn parses_first_place_photos() {
    let p = parse_google_place_photos(GOOGLE.into()).unwrap();
    assert!(p.found);
    assert_eq!(p.google_place_id.as_deref(), Some("ChIJabc"));
    assert_eq!(p.photos.len(), 2);
    assert_eq!(p.photos[0].author, "한동희");
    assert_eq!((p.photos[0].width_px, p.photos[0].height_px), (4032, 3024));
    assert_eq!(p.photos[1].author, "");
}

#[test]
fn empty_response_is_not_found_and_bad_json_is_error() {
    let p = parse_google_place_photos("{}".into()).unwrap();
    assert!(!p.found && p.photos.is_empty());
    assert!(parse_google_place_photos("<html>".into()).is_err());
}

#[test]
fn caches_for_30_days() {
    let path = std::env::temp_dir().join(format!("eodi-photos-{}.db", std::process::id()));
    let _ = std::fs::remove_file(&path);
    {
        let store = EodiStore::open(path.to_string_lossy().into_owned(), 540).unwrap();
        let key = "경포해변@37.8055,128.9083".to_string();
        assert_eq!(store.cached_place_photos(key.clone(), 0).unwrap(), None);
        let photos = parse_google_place_photos(GOOGLE.into()).unwrap();
        store.save_place_photos(key.clone(), photos.clone(), 1_000).unwrap();
        assert_eq!(store.cached_place_photos(key.clone(), 1_000 + PHOTO_TTL_MS).unwrap(), Some(photos));
        assert_eq!(store.cached_place_photos(key.clone(), 1_001 + PHOTO_TTL_MS).unwrap(), None);
        // 못 찾은 곳도 저장해 다시 묻지 않는다
        let none = PlacePhotos { found: false, google_place_id: None, photos: vec![] };
        store.save_place_photos(key.clone(), none.clone(), 2_000).unwrap();
        assert_eq!(store.cached_place_photos(key, 2_000).unwrap(), Some(none));
    }
    for ext in ["", "-wal", "-shm"] {
        let _ = std::fs::remove_file(format!("{}{ext}", path.display()));
    }
}

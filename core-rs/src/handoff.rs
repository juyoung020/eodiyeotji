//! 차 → 폰 넘기기: 차에서 "공유"를 누르면 폰 앱에 알림이 가고, 폰에서 카톡으로 보낸다.
//!
//! 전달은 공개 알림 중계(ntfy)의 주제(topic) 하나를 쓴다. 중계 서버는 내용을 읽지 못한다:
//! 모든 메시지는 페어링 때 나눠 가진 32바이트 키로 ChaCha20-Poly1305 암호화한다.
//!
//! # 페어링 (처음 한 번)
//! 1. 차: [new_pairing]으로 6자리 코드 · 주제 · 키를 만들고, [seal_pairing]한 글을 [pair_topic]`(코드)`에 올린다.
//! 2. 폰: 사람이 코드를 입력하면 같은 주제에서 글을 받아 [open_pairing]으로 주제 · 키를 꺼낸다.
//!    코드에서 만든 키로 잠그므로 코드를 모르면 열 수 없다. 코드 글은 차가 페어링 창을 띄운 동안만 쓴다.
//! 3. 폰: 본 주제에 "연결됨" 인사를 [seal]해 올린다 → 차가 받아 연결을 끝낸다.
//!
//! # 공유
//! 차: 공유 카드(`share.rs`)를 [seal_card]해 본 주제에 올린다. 폰: [open_card]로 꺼내 카드 이미지를 그려 카톡으로 보낸다.
//! 카드의 보낼 글 끝에는 카카오맵 경로 링크([kakao_route_url])가 붙어 있어, 받은 사람이 누르면 그 길이 바로 열린다.

use base64::engine::general_purpose::URL_SAFE_NO_PAD as B64;
use base64::Engine;
use chacha20poly1305::aead::{Aead, KeyInit};
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};
use sha2::{Digest, Sha256};

use crate::share::{ShareCard, ShareStop};
use crate::store::CoreError;

/// 카카오맵 경로 링크에 넣는 최대 지점 수 (출발 + 경유 + 도착).
pub const ROUTE_MAX_STOPS: usize = 5;
const NONCE_LEN: usize = 12;
const PAIR_TOPIC_PREFIX: &str = "eodiyeotji-pair-";
const TOPIC_PREFIX: &str = "eodiyeotji-";

/// 페어링 한 쌍 (차와 폰이 같은 값을 갖는다).
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct HandoffPairing {
    /// 사람이 폰에 입력하는 6자리 숫자
    pub code: String,
    /// 본 주제 (추측할 수 없는 긴 이름)
    pub topic: String,
    /// 암호 키 (base64url, 32바이트)
    pub key: String,
}

/// QR 칸 (size × size, 행 우선, true = 검은 칸).
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct QrMatrix {
    pub size: u32,
    pub modules: Vec<bool>,
}

fn invalid(msg: &str) -> CoreError {
    CoreError::InvalidInput(msg.to_string())
}

fn random<const N: usize>() -> Result<[u8; N], CoreError> {
    let mut b = [0u8; N];
    getrandom::getrandom(&mut b).map_err(|_| invalid("난수를 만들 수 없음"))?;
    Ok(b)
}

/// 새 페어링 (차에서).
pub fn new_pairing() -> Result<HandoffPairing, CoreError> {
    let r: [u8; 4] = random()?;
    let code = format!("{:06}", u32::from_le_bytes(r) % 1_000_000);
    let t: [u8; 12] = random()?;
    let k: [u8; 32] = random()?;
    Ok(HandoffPairing { code, topic: format!("{TOPIC_PREFIX}{}", hex(&t)), key: B64.encode(k) })
}

/// 코드로 페어링 글을 올리고 받는 주제.
pub fn pair_topic(code: &str) -> String {
    format!("{PAIR_TOPIC_PREFIX}{}", code.trim())
}

fn code_key(code: &str) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(b"eodiyeotji-pair-v1:");
    h.update(code.trim().as_bytes());
    h.finalize().into()
}

fn key_bytes(key: &str) -> Result<[u8; 32], CoreError> {
    let v = B64.decode(key.trim()).map_err(|_| invalid("키 형식"))?;
    v.try_into().map_err(|_| invalid("키 길이"))
}

fn seal_raw(key: &[u8; 32], plain: &[u8]) -> Result<String, CoreError> {
    let n: [u8; NONCE_LEN] = random()?;
    let ct = ChaCha20Poly1305::new(Key::from_slice(key)).encrypt(Nonce::from_slice(&n), plain).map_err(|_| invalid("암호화 실패"))?;
    let mut out = n.to_vec();
    out.extend(ct);
    Ok(B64.encode(out))
}

fn open_raw(key: &[u8; 32], msg: &str) -> Result<Vec<u8>, CoreError> {
    let v = B64.decode(msg.trim()).map_err(|_| invalid("메시지 형식"))?;
    if v.len() <= NONCE_LEN {
        return Err(invalid("메시지 길이"));
    }
    ChaCha20Poly1305::new(Key::from_slice(key))
        .decrypt(Nonce::from_slice(&v[..NONCE_LEN]), &v[NONCE_LEN..])
        .map_err(|_| invalid("열 수 없는 메시지"))
}

/// 페어링 글 (코드에서 만든 키로 잠금).
pub fn seal_pairing(p: &HandoffPairing) -> Result<String, CoreError> {
    seal_raw(&code_key(&p.code), format!("{}\n{}", p.topic, p.key).as_bytes())
}

/// 폰: 입력한 코드로 페어링 글을 연다. 코드가 틀리거나 다른 글이면 None.
pub fn open_pairing(code: &str, msg: &str) -> Option<HandoffPairing> {
    let plain = open_raw(&code_key(code), msg).ok()?;
    let s = String::from_utf8(plain).ok()?;
    let (topic, key) = s.split_once('\n')?;
    if !topic.starts_with(TOPIC_PREFIX) || key_bytes(key).is_err() {
        return None;
    }
    Some(HandoffPairing { code: code.trim().to_string(), topic: topic.to_string(), key: key.to_string() })
}

/// 짧은 글 잠그기 (연결 인사 등).
pub fn seal(key: &str, text: &str) -> Result<String, CoreError> {
    seal_raw(&key_bytes(key)?, text.as_bytes())
}

/// 짧은 글 열기.
pub fn open(key: &str, msg: &str) -> Result<String, CoreError> {
    String::from_utf8(open_raw(&key_bytes(key)?, msg)?).map_err(|_| invalid("글자 형식"))
}

/// 바이트 잠그기 · 열기 (기록 동기화 묶음, `sync.rs`).
pub fn seal_bytes(key: &str, data: &[u8]) -> Result<String, CoreError> {
    seal_raw(&key_bytes(key)?, data)
}

pub fn open_bytes(key: &str, msg: &str) -> Result<Vec<u8>, CoreError> {
    open_raw(&key_bytes(key)?, msg)
}

/// 공유 카드 잠그기.
pub fn seal_card(key: &str, card: &ShareCard) -> Result<String, CoreError> {
    let json = serde_json::to_string(card).map_err(|_| invalid("카드 묶기 실패"))?;
    seal(key, &json)
}

/// 공유 카드 열기.
pub fn open_card(key: &str, msg: &str) -> Result<ShareCard, CoreError> {
    serde_json::from_str(&open(key, msg)?).map_err(|_| invalid("카드 형식"))
}

/// 들른 곳을 순서대로 잇는 카카오맵 운전 경로 링크. 앞에서부터 [ROUTE_MAX_STOPS]곳. 없으면 빈 문자열.
/// 형식: `https://map.kakao.com/link/by/car/이름,위도,경도/이름,위도,경도` (한 곳뿐이면 `link/to/`).
pub fn kakao_route_url(stops: &[ShareStop]) -> String {
    let parts: Vec<String> =
        stops.iter().take(ROUTE_MAX_STOPS).map(|s| format!("{},{:.6},{:.6}", encode_name(&s.name), s.lat, s.lon)).collect();
    match parts.len() {
        0 => String::new(),
        1 => format!("https://map.kakao.com/link/to/{}", parts[0]),
        _ => format!("https://map.kakao.com/link/by/car/{}", parts.join("/")),
    }
}

/// 이름 안의 `,` `/`는 링크 구분자라 띄어쓰기로 바꾸고, 영숫자 · `-_.~` 밖의 바이트는 %로 적는다.
fn encode_name(name: &str) -> String {
    let clean: String = name.trim().chars().map(|c| if c == ',' || c == '/' { ' ' } else { c }).collect();
    let mut out = String::new();
    for b in clean.bytes() {
        if b.is_ascii_alphanumeric() || b"-_.~".contains(&b) {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

/// 글 → QR 칸.
pub fn qr_matrix(text: &str) -> Result<QrMatrix, CoreError> {
    let code = qrcode::QrCode::new(text.as_bytes()).map_err(|_| invalid("QR로 만들 수 없는 글"))?;
    let size = code.width() as u32;
    let modules = code.to_colors().into_iter().map(|c| c == qrcode::Color::Dark).collect();
    Ok(QrMatrix { size, modules })
}

fn hex(b: &[u8]) -> String {
    b.iter().map(|x| format!("{x:02x}")).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::share::{ShareKind, SketchPoint};

    fn stop(order: u32, name: &str, lat: f64, lon: f64) -> ShareStop {
        ShareStop { order, name: name.into(), category: String::new(), address: String::new(), lat, lon, at: SketchPoint { x: 0.0, y: 0.0 } }
    }

    #[test]
    fn route_url_joins_stops_in_order() {
        let s = vec![stop(1, "경포해변", 37.805, 128.908), stop(2, "초당/순두부", 37.791, 128.915)];
        let u = kakao_route_url(&s);
        assert!(u.starts_with("https://map.kakao.com/link/by/car/%EA%B2%BD"));
        assert!(u.contains(",37.805000,128.908000/"));
        // 이름 속 '/'는 구분자로 남지 않는다 → 지점 사이 '/'는 하나뿐
        assert_eq!(u.trim_start_matches("https://map.kakao.com/link/by/car/").matches('/').count(), 1);
    }

    #[test]
    fn route_url_single_empty_and_cap() {
        assert_eq!(kakao_route_url(&[]), "");
        assert_eq!(kakao_route_url(&[stop(1, "A", 1.0, 2.0)]), "https://map.kakao.com/link/to/A,1.000000,2.000000");
        let many: Vec<_> = (0..8).map(|i| stop(i, "X", 1.0, 2.0)).collect();
        assert_eq!(kakao_route_url(&many).matches("X,").count(), ROUTE_MAX_STOPS);
    }

    #[test]
    fn pairing_round_trip_and_wrong_code() {
        let p = new_pairing().unwrap();
        assert_eq!(p.code.len(), 6);
        assert!(p.topic.starts_with(TOPIC_PREFIX));
        let msg = seal_pairing(&p).unwrap();
        assert_eq!(open_pairing(&p.code, &msg), Some(p.clone()));
        let wrong = if p.code == "000000" { "000001" } else { "000000" };
        assert_eq!(open_pairing(wrong, &msg), None);
        assert_eq!(pair_topic(" 123456 "), "eodiyeotji-pair-123456");
    }

    #[test]
    fn card_round_trip_and_wrong_key() {
        let p = new_pairing().unwrap();
        let card = ShareCard {
            kind: ShareKind::Day,
            title: "강릉 바다".into(),
            day: "2025-11-07".into(),
            date_text: String::new(),
            season: String::new(),
            weather: None,
            region: String::new(),
            stops: vec![stop(1, "경포해변", 37.8, 128.9)],
            lines: vec![],
            straight: false,
            distance_m: 1000.0,
            duration_ms: 0,
            hidden_count: 0,
            share_text: "t".into(),
            route_url: "u".into(),
            map_box: None,
        };
        let m = seal_card(&p.key, &card).unwrap();
        assert_eq!(open_card(&p.key, &m).unwrap(), card);
        let other = new_pairing().unwrap();
        assert!(open_card(&other.key, &m).is_err(), "다른 키로는 열리지 않는다");
        assert!(open(&p.key, "zz").is_err());
        let b = seal_bytes(&p.key, &[0, 159, 255]).unwrap();
        assert_eq!(open_bytes(&p.key, &b).unwrap(), vec![0, 159, 255]);
    }

    #[test]
    fn qr_is_square() {
        let q = qr_matrix("eodiyeotji://pair?c=123456").unwrap();
        assert_eq!(q.modules.len() as u32, q.size * q.size);
        assert!(q.modules.iter().any(|&m| m));
    }
}

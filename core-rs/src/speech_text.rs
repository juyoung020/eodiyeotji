//! 소리 내어 읽을 글 다듬기 ([speakable]).
//!
//! 화면용 글을 그대로 음성 합성에 넘기면 기호를 이상하게 읽거나("·", "→"), 그림 문자에서 끊기고,
//! 단위("2.1km", "80%")를 영어 글자로 읽는다. 읽기 전에 한 번 다듬는다:
//! - 그림 문자(이모지) · 꾸밈 기호는 지운다. 화살표 · 가운뎃점 · 세로줄 · 빗금은 쉼표(잠깐 쉼)로 바꾼다.
//! - 숫자 뒤 단위를 한국어로: km → 킬로미터, m → 미터, km/h → 킬로미터, kWh → 킬로와트시, % → 퍼센트, ℃ → 도.
//! - 숫자 사이 천 단위 쉼표는 지운다 ("5,200원" → "5200원"), 숫자 사이 물결은 "에서" ("10~20분" → "10에서 20분").
//! - 줄바꿈은 문장 끝으로, 여는 괄호는 쉼표로(닫는 괄호는 지운다). 겹친 공백 · 쉼표는 하나로.

/// 화면용 글을 음성 합성에 넘기기 좋게 다듬는다. 읽을 것이 없으면 빈 문자열.
pub fn speakable(text: &str) -> String {
    let chars: Vec<char> = text.chars().collect();
    let mut out = String::with_capacity(text.len() + 16);
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        let prev = out.chars().last();
        let prev_digit = prev.is_some_and(|p| p.is_ascii_digit());
        let next = chars.get(i + 1).copied();
        let next_digit = next.is_some_and(|n| n.is_ascii_digit());

        // 숫자 뒤 단위
        if prev_digit {
            if let Some((word, len)) = unit_at(&chars, i) {
                out.push_str(word);
                i += len;
                continue;
            }
        }
        match c {
            ',' if prev_digit && next_digit => {} // 천 단위 쉼표
            '~' | '∼' | '〜' if prev_digit && next_digit => out.push_str("에서 "),
            '~' | '∼' | '〜' => out.push(' '),
            '\n' | '\r' => {
                if !matches!(prev, None | Some('.' | '!' | '?' | ',' | '。')) && !out.trim_end().is_empty() {
                    let t = out.trim_end().len();
                    out.truncate(t);
                    out.push('.');
                }
                out.push(' ');
            }
            '·' | '•' | '∙' | '|' | '/' | '→' | '←' | '↔' | '⇒' | '▶' | '►' | '➜' | '➔' | '(' | '[' => {
                pause(&mut out)
            }
            ')' | ']' => {} // 여는 괄호에서 이미 쉰다
            '♥' | '❤' => out.push_str("하트 "),
            '\u{2190}'..='\u{21FF}' | '\u{27F0}'..='\u{27FF}' | '\u{2B00}'..='\u{2BFF}' => pause(&mut out), // 그 밖의 화살표
            c if is_decoration(c) => {}
            c if c.is_whitespace() => out.push(' '),
            c => out.push(c),
        }
        i += 1;
    }
    tidy(&out)
}

/// 쉼표 하나로 잠깐 쉰다 (앞에 이미 쉼표가 있으면 그대로).
fn pause(out: &mut String) {
    let t = out.trim_end().len();
    out.truncate(t);
    if !out.is_empty() && !out.ends_with([',', '.', '!', '?']) {
        out.push(',');
    }
    out.push(' ');
}

/// 숫자 바로 뒤 `chars[i..]`가 단위면 (읽을 말, 글자 수).
fn unit_at(chars: &[char], i: usize) -> Option<(&'static str, usize)> {
    const UNITS: [(&str, &str); 9] = [
        ("km/h", "킬로미터"),
        ("kWh", "킬로와트시"),
        ("km", "킬로미터"),
        ("㎞", "킬로미터"),
        ("m", "미터"),
        ("㎡", "제곱미터"),
        ("%", "퍼센트"),
        ("℃", "도"),
        ("°C", "도"),
    ];
    for (unit, word) in UNITS {
        let u: Vec<char> = unit.chars().collect();
        if chars.len() < i + u.len() || chars[i..i + u.len()] != u[..] {
            continue;
        }
        // 영어 낱말 앞부분이면 단위가 아니다 ("5min", "3mm")
        if chars.get(i + u.len()).is_some_and(|n| n.is_ascii_alphabetic()) {
            continue;
        }
        return Some((word, u.len()));
    }
    None
}

/// 읽지 않을 그림 문자 · 꾸밈 기호.
fn is_decoration(c: char) -> bool {
    matches!(c,
        '\u{1F000}'..='\u{1FAFF}' // 이모지 · 그림 문자
        | '\u{2600}'..='\u{27BF}' // 기타 기호 · 딩뱃
        | '\u{FE00}'..='\u{FE0F}' // 모양 고르개
        | '\u{200B}'..='\u{200D}' // 폭 없는 글자
        | '\u{E000}'..='\u{F8FF}' // 사용자 정의 영역
        | '※' | '#' | '*' | '_' | '`' | '"' | '“' | '”' | '「' | '」' | '『' | '』')
}

/// 겹친 공백 · 쉼표를 하나로, 앞뒤 쉼표 · 공백을 뺀다.
fn tidy(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            ' ' if out.is_empty() || out.ends_with(' ') => {}
            ',' if out.is_empty() => {}
            ',' | '.' if out.trim_end().ends_with([',', '.', '!', '?']) => {}
            ',' | '.' | '!' | '?' if out.ends_with(' ') => {
                out.pop();
                out.push(c);
            }
            c => out.push(c),
        }
    }
    out.trim_matches(|c: char| c == ',' || c.is_whitespace()).to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn units_after_numbers_are_read_in_korean() {
        assert_eq!(speakable("2.1km 남았어요"), "2.1킬로미터 남았어요");
        assert_eq!(speakable("배터리 80%"), "배터리 80퍼센트");
        assert_eq!(speakable("300m 앞"), "300미터 앞");
        assert_eq!(speakable("시속 60km/h"), "시속 60킬로미터");
        assert_eq!(speakable("12kWh 충전"), "12킬로와트시 충전");
        // 숫자 뒤가 아니거나 영어 낱말이면 그대로
        assert_eq!(speakable("m 사이즈"), "m 사이즈");
        assert_eq!(speakable("5min"), "5min");
    }

    #[test]
    fn thousands_commas_and_ranges() {
        assert_eq!(speakable("5,200원"), "5200원");
        assert_eq!(speakable("1,234,000원"), "1234000원");
        assert_eq!(speakable("10~20분"), "10에서 20분");
        assert_eq!(speakable("사과, 배"), "사과, 배");
    }

    #[test]
    fn symbols_become_pauses_and_emojis_vanish() {
        assert_eq!(speakable("강릉 · 안목해변 → 경포대"), "강릉, 안목해변, 경포대");
        assert_eq!(speakable("도착했어요 🎉☕"), "도착했어요");
        assert_eq!(speakable("카페 (2층)에 있어요"), "카페, 2층에 있어요");
        assert_eq!(speakable("좋아요 ♥ 3"), "좋아요 하트 3");
        assert_eq!(speakable("★ 추천 ★"), "추천");
    }

    #[test]
    fn newlines_end_sentences_and_spaces_collapse() {
        assert_eq!(speakable("첫 줄\n둘째 줄"), "첫 줄. 둘째 줄");
        assert_eq!(speakable("끝났어요.\n다음"), "끝났어요. 다음");
        assert_eq!(speakable("  여러   칸  "), "여러 칸");
        assert_eq!(speakable("🎉"), "");
        assert_eq!(speakable(""), "");
    }
}

//! 홈 날짜 줄의 그날 귀띔 한 단어 ([day_hints]).
//!
//! - 그날 이름 있는 머문 곳(`DaySummary.stop_names`, 도착 순서) 중 **늘 가는 곳이 아닌** 첫 곳을 고른다.
//!   늘 가는 곳 = 보이는 날들 중 [ROUTINE_MIN_DAYS](3)일 이상, 그리고 전체 날의 1/[ROUTINE_SHARE_DIV](4) 이상에 나온 이름
//!   (집 · 회사 · 출퇴근길에 매일 들르는 곳).
//! - 늘 가는 곳만 있는 날은 [ROUTINE_LABEL]("출퇴근"), 이름 있는 곳이 없는 날은 빈 문자열.
//! - 이름은 줄이지 않는다 (알아볼 수 있게). [short_place_name]은 괄호 안 부연 설명만 뺀다.
//!   화면은 두 줄까지 낱말 단위로 감싼다.

use std::collections::HashMap;

pub const ROUTINE_MIN_DAYS: usize = 3;
pub const ROUTINE_SHARE_DIV: usize = 4;
pub const ROUTINE_LABEL: &str = "출퇴근";

/// 날마다(`days[i]` = 그날 stop_names) 귀띔 한 단어. 결과 길이 = 입력 길이.
pub fn day_hints(days: &[Vec<String>]) -> Vec<String> {
    let mut seen: HashMap<&str, usize> = HashMap::new();
    for names in days {
        let mut uniq: Vec<&str> = names.iter().map(|n| n.trim()).filter(|n| !n.is_empty()).collect();
        uniq.sort_unstable();
        uniq.dedup();
        for n in uniq {
            *seen.entry(n).or_default() += 1;
        }
    }
    let need = ROUTINE_MIN_DAYS.max(days.len().div_ceil(ROUTINE_SHARE_DIV));
    days.iter()
        .map(|names| {
            let named: Vec<&str> = names.iter().map(|n| n.trim()).filter(|n| !n.is_empty()).collect();
            if named.is_empty() {
                return String::new();
            }
            match named.iter().find(|n| seen.get(*n).copied().unwrap_or(0) < need) {
                Some(n) => short_place_name(n),
                None => ROUTINE_LABEL.to_owned(),
            }
        })
        .collect()
}

/// 장소 이름을 뜻을 잃지 않게만 다듬는다: 괄호 안 부연 설명만 빼고 이름은 그대로 둔다 ("스타벅스(강남R점)" → "스타벅스").
pub fn short_place_name(name: &str) -> String {
    let base = match name.find(['(', '[']) {
        Some(i) if i > 0 => &name[..i],
        _ => name,
    };
    base.split_whitespace().collect::<Vec<_>>().join(" ")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn v(names: &[&str]) -> Vec<String> {
        names.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn keeps_names_whole() {
        assert_eq!(short_place_name("이마트 성수점"), "이마트 성수점");
        assert_eq!(short_place_name("스카이베이호텔 경포"), "스카이베이호텔 경포");
        assert_eq!(short_place_name("스타벅스(강남R점)"), "스타벅스");
        assert_eq!(short_place_name("  센터원빌딩 "), "센터원빌딩");
        assert_eq!(short_place_name(""), "");
    }

    #[test]
    fn skips_routine_places() {
        let mut days = Vec::new();
        for _ in 0..8 {
            days.push(v(&["센터원빌딩"]));
        }
        days.push(v(&["센터원빌딩", "이마트 성수점"]));
        days.push(v(&["스카이베이호텔 경포", "센터원빌딩"]));
        days.push(v(&[]));
        let h = day_hints(&days);
        assert_eq!(h[0], ROUTINE_LABEL);
        assert_eq!(h[8], "이마트 성수점");
        assert_eq!(h[9], "스카이베이호텔 경포");
        assert_eq!(h[10], "");
    }

    #[test]
    fn few_days_keep_names() {
        let h = day_hints(&[v(&["센터원빌딩"]), v(&["센터원빌딩"])]);
        assert_eq!(h, ["센터원빌딩", "센터원빌딩"]);
    }

    /// 출퇴근길 하나만 있는 날(출발 · 도착이 둘 다 늘 가는 곳) → "출퇴근".
    #[test]
    fn commute_only_day_is_routine() {
        let mut days = vec![v(&["센터원빌딩", "응봉동 현대아파트"])];
        for _ in 0..6 {
            days.push(v(&["응봉동 현대아파트", "센터원빌딩"]));
        }
        days.push(v(&["응봉동 현대아파트", "이마트 성수점", "센터원빌딩"]));
        let h = day_hints(&days);
        assert_eq!(h[0], ROUTINE_LABEL);
        assert_eq!(h[7], "이마트 성수점");
    }

    /// 빈 이름 · 공백뿐인 이름은 없는 것으로 본다 → 빈 귀띔.
    #[test]
    fn blank_names_give_empty_hint() {
        let h = day_hints(&[v(&["", "  "]), v(&[])]);
        assert_eq!(h, ["", ""]);
        assert!(day_hints(&[]).is_empty());
    }
}

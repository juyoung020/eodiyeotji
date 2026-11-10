//! 말 다루기 규칙 (LLM 없이, 수 µs): 짧은 예/아니오 판별, 검색어 뽑기, LLM이 필요한 말인지 판단.
//! 장소 사전은 두지 않는다 — 일반적인 질문·군말·조사·어미 목록만 쓴다.

use super::dates::is_date_word;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Answer {
    Yes,
    No,
    Other,
}

const YES: &[&str] = &[
    "응", "어", "네", "예", "넹", "웅", "ㅇㅇ", "그래", "그래요", "좋아", "좋아요", "좋지", "오케이", "ok", "okay", "안내해줘", "안내해",
    "안내해주세요", "안내", "해줘", "해", "줘", "주세요", "가자", "가줘", "가", "출발", "출발해", "출발해줘", "부탁해", "부탁해요", "그렇게",
    "해줘요", "시작", "시작해", "시작해줘",
];
const FILLER: &[&str] = &["거기로", "거기", "그걸로", "그거로", "그", "이", "코스로", "그곳으로", "바로", "지금", "좀", "빨리", "길", "길로"];
const NO: &[&str] = &[
    "아니", "아니야", "아니요", "아뇨", "아니오", "됐어", "됐어요", "취소", "싫어", "그만", "괜찮아", "괜찮아요", "안", "해", "할래", "안할래",
    "안해", "하지마", "말아", "말자", "나중에",
];
const NO_CORE: &[&str] = &[
    "안", "아니", "아니야", "아니요", "아뇨", "아니오", "됐어", "됐어요", "취소", "싫어", "그만", "괜찮아", "괜찮아요", "안할래", "안해", "하지마",
    "말자", "나중에",
];

pub fn tokens(s: &str) -> Vec<String> {
    s.to_lowercase()
        .split(|c: char| c.is_whitespace() || ",.!?~·…\"'()[]".contains(c))
        .filter(|w| !w.is_empty())
        .map(String::from)
        .collect()
}

/// 대기 중인 안내 제안에 대한 짧은 대답인가. 말의 **모든 낱말**이 목록에 있을 때만 예/아니오 —
/// "강릉 안내해줘"처럼 다른 낱말이 섞이면 Other (엉뚱한 제안을 실행하지 않게).
pub fn classify(utterance: &str) -> Answer {
    let w = tokens(utterance);
    if w.is_empty() || w.len() > 5 {
        return Answer::Other;
    }
    let is = |set: &[&str], x: &String| set.contains(&x.as_str());
    if w.iter().all(|x| is(NO, x) || is(FILLER, x)) && w.iter().any(|x| is(NO_CORE, x)) {
        return Answer::No;
    }
    if w.iter().all(|x| is(YES, x) || is(FILLER, x)) && w.iter().any(|x| is(YES, x)) {
        return Answer::Yes;
    }
    Answer::Other
}

/// 질문 말투·군말 (검색어가 아니다).
const STOP: &[&str] = &[
    "거기", "그때", "그", "저", "이", "나", "내가", "우리", "우리가", "같이", "있잖아", "기억", "기록", "알려줘", "찾아줘", "알려", "찾아",
    "이름", "곳", "데", "적", "때", "간", "갔던", "들른", "다녀온", "먹은", "마신", "본", "보러", "있어", "있어요", "있었지", "했었지", "좀",
    "혹시", "가본", "갔었던", "갔었는데", "갔는데", "들렀던", "갔다온", "다녀왔던", "했던", "먹었던", "마셨던", "봤던", "샀던", "놀러",
    "뭐", "무슨", "어떤", "어느", "누구", "몇", "그거", "그곳", "거", "것", "데가", "곳이", "덴", "건", "게", "얘기", "말야", "말이야",
    "알아", "아니", "응", "네", "좀요", "해줘", "줘", "안내해줘", "가자", "었지", "였지", "지", "한", "하던", "할", "하는", "했던가",
    "근처", "주변", "옆", "앞", "쪽", "근방", "되나", "돼",
    // 흐릿한 때 말 ("옛날에 갔던 바다"): 기간을 정하지 않고 전체에서 찾는다
    "전", "언젠가", "그전", "지난번", "이전",
    // "비 오는 날"의 나머지 말 (비는 와이퍼 기록으로 따로 거른다)
    "오는", "오던", "온", "내리는", "내리던", "날",
];

/// 사람 말 (기록에 사람 정보가 없어 검색어로 쓰지 않는다. 있으면 LLM에게 넘긴다).
const PEOPLE: &[&str] = &[
    "엄마", "아빠", "가족", "친구", "친구들", "아내", "남편", "와이프", "애들", "아이들", "아이", "아들", "딸", "부모님", "동생", "형", "누나",
    "언니", "오빠", "할머니", "할아버지", "여자친구", "남자친구", "여친", "남친", "동료", "회사사람들", "어머니", "아버지",
];

/// 혼자 갔다는 말 (좌석 센서로 운전자만 탄 때를 고른다).
const ALONE: &[&str] = &["혼자", "혼자서", "나혼자"];

/// 비 온 날 말 (차 와이퍼 기록으로 그날 비가 왔는지 거른다, `readings.rs`).
fn is_rain(w: &str, stem: &str) -> bool {
    stem == "비" || ["비오", "비올", "비내", "빗길", "빗속", "우중"].iter().any(|p| w.starts_with(p))
}

/// 오래전 일을 묻는 말 ("옛날에 갔던 바다"): 기간은 정하지 않고, 최근 한 달 기록을 뒤로 민다.
const LONG_AGO: &[&str] = &["옛날", "예전", "오래전", "한참전", "어릴"];

/// 앞 대화를 가리키는 말 (LLM이 대화를 보고 풀어야 한다).
const REFERENCE: &[&str] = &["거기", "그때", "아까", "방금", "말고", "다른", "그곳", "거기서", "그날", "저번에", "그거"];

const JOSA: &[&str] = &[
    "에서는", "에서", "으로", "에는", "에도", "까지", "부터", "이랑", "하고", "에게", "한테", "에", "을", "를", "이", "가", "은", "는", "도", "로",
    "랑", "와", "과", "의", "만",
];

const AMBIGUOUS_JOSA: &[&str] = &["이", "가", "도", "로", "의", "만", "과", "와", "은"];

/// 어미 (동사 꼴 낱말은 검색어에서 뺀다).
const VERB_ENDINGS: &[&str] = &[
    "었는데", "았는데", "였는데", "했는데", "었던", "았던", "였던", "했던", "었지", "았지", "였지", "했지", "더라", "었어", "았어", "였어",
    "했어", "는데", "은데", "던", "려고", "줘", "주세요", "을까", "까", "니", "나요", "까요", "어요", "아요", "해요", "었나", "았나", "였나",
    "도돼", "도 돼", "해도"
];

#[derive(Debug, Clone, Default, PartialEq)]
pub struct Parsed {
    /// 검색어 (원래 낱말 + 조사 뗀 꼴, 겹치지 않게)
    pub terms: Vec<String>,
    pub has_people: bool,
    pub has_reference: bool,
    /// 사람 말이 바라는 동행 (좌석 센서 인원으로 거른다, `cabin.rs`)
    pub people: Option<crate::cabin::PeopleWant>,
    /// "비 오던 날 · 빗길": 와이퍼가 돈 때만
    pub rain: bool,
    /// "옛날에 · 예전에 · 오래전": 최근 기록보다 지난 기록을 앞에
    pub long_ago: bool,
}

pub fn parse(utterance: &str) -> Parsed {
    let mut p = Parsed::default();
    for w in tokens(utterance) {
        let stem = strip_josa(&w);
        if REFERENCE.contains(&w.as_str()) || REFERENCE.contains(&stem.as_str()) {
            p.has_reference = true;
            continue;
        }
        if PEOPLE.contains(&stem.as_str()) || PEOPLE.contains(&w.as_str()) {
            p.has_people = true;
            p.people = Some(crate::cabin::PeopleWant::Together);
            continue;
        }
        if ALONE.contains(&stem.as_str()) || ALONE.contains(&w.as_str()) {
            p.has_people = true;
            p.people = Some(crate::cabin::PeopleWant::Alone);
            continue;
        }
        if is_rain(&w, &stem) {
            p.rain = true;
            continue;
        }
        if LONG_AGO.iter().any(|x| stem == *x || w.starts_with(x)) {
            p.long_ago = true;
            continue;
        }
        if is_noise(&w) || is_noise(&stem) {
            continue;
        }
        // 한 글자는 뜻이 분명한 것("산", "강", "섬")만 — 잘린 말("오는" → "오")이 검색어가 되지 않게
        let usable = |x: &str| x.chars().count() >= 2 || crate::memory::search::SINGLE_OK.contains(&x);
        if stem != w {
            // 받침 없는 낱말 끝 "이·가·도…"는 조사인지 낱말 일부("조개구이")인지 모른다 → 둘 다 넣는다
            if AMBIGUOUS_JOSA.iter().any(|j| w.ends_with(j)) && w.chars().count() >= 2 {
                push(&mut p.terms, &place_word(&w));
            }
            if usable(&stem) {
                push(&mut p.terms, &place_word(&stem));
            }
        } else if usable(&w) {
            push(&mut p.terms, &place_word(&w));
        }
    }
    p.terms.truncate(6);
    p
}

/// "감자탕집", "국밥집" → "감자탕", "국밥": 가게 이름에는 "집"이 없는 때가 많다 ("태조감자국"·"광화문국밥").
/// 두 글자("맛집")는 그대로 둔다.
fn place_word(w: &str) -> String {
    match w.strip_suffix('집') {
        Some(head) if head.chars().count() >= 2 => head.to_string(),
        _ => w.to_string(),
    }
}

fn push(v: &mut Vec<String>, s: &str) {
    if !v.iter().any(|x| x == s) {
        v.push(s.to_string());
    }
}

fn is_noise(w: &str) -> bool {
    w.is_empty()
        || STOP.contains(&w)
        || is_date_word(w)
        || w.starts_with("어디")
        || w.starts_with("언제")
        || w.starts_with("뭐")
        || w.starts_with("어땠")
        || VERB_ENDINGS.iter().any(|e| w.ends_with(e) && w.chars().count() > e.chars().count())
}

/// 조사를 하나 뗀다 (낱말이 조사보다 길 때만).
fn strip_josa(w: &str) -> String {
    for j in JOSA {
        if w.ends_with(j) && w.chars().count() > j.chars().count() {
            return w[..w.len() - j.len()].to_string();
        }
    }
    w.to_string()
}

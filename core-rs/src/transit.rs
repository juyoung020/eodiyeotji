//! 지하철 이동 가려내기 — 폰은 위치만 보고 주행을 잡아서, 지하철을 타도 "주행"으로 기록된다.
//!
//! 구글 타임라인처럼 한 가지 신호로 정하지 않고 근거를 겹쳐 본다 (`EodiStore::classify_transit`이 끝난 주행마다 부른다):
//! - **신호 모양:** 지하 · 역 사이에서는 위치가 끊겼다가 다음 역에서 나타난다 → [JUMP_GAP_MS] 넘게 끊긴 뒤
//!   [JUMP_MIN_M] 넘게 떨어진 점이 [MIN_JUMPS]번 이상 (차는 1초마다 이어서 들어온다. 터널 한두 번은 이 수에 못 미친다).
//! - **내 차가 아님:** 출발점이 마지막으로 차를 세운 자리에서 [CAR_FAR_M] 넘게 떨어져 있다 (차는 세운 자리에서만 출발한다).
//!   단 [CAR_FAR_MAX_M]보다 멀면 근거로 쓰지 않는다 — 제주에서 렌터카를 모는 것처럼 다른 차를 모는 경우다.
//! - **역:** 출발 · 도착점이 역에서 [STATION_AT_M] 안이거나(`subway` 모듈의 역 표), 장소 이름이 역이다 ("쌍문역", "치킨뱅이 서울역점").
//!
//! 신호 모양이 맞고, 나머지 둘 중 하나가 맞으면 지하철 이동이다. 틀리면 사용자가 주행 ⋯ 메뉴에서 바꾼다 (`set_trip_transit`).
//! 판정한 주행은 `hidden` 표에 종류 3으로 적는다 → 차 기록(주행 목록 · 통계 · 기억 · 주차 자리)에서 빠지고,
//! 하루 상세에는 `DayEntry::Transit` · 지도에는 `kind: "transit"` 선으로 따로 나온다.

use crate::geo::{haversine_m, Point};

/// 이만큼 넘게 위치가 끊기고
pub(crate) const JUMP_GAP_MS: i64 = 20_000;
/// 이만큼 넘게 떨어진 곳에 나타나면 "뛴 점" 하나
pub(crate) const JUMP_MIN_M: f64 = 300.0;
/// 뛴 점이 이만큼 이상이면 신호 모양이 지하철
pub(crate) const MIN_JUMPS: u32 = 3;
/// 차를 세운 자리에서 이만큼 넘게 떨어져 출발했으면 내 차가 아니다 (늦게 깨어난 출발을 잇는 거리 `drivedetect`와 같다)
pub(crate) const CAR_FAR_M: f64 = 1_500.0;

/// 차를 세운 자리에서 이보다도 멀면 "내 차가 아니다"의 근거로 쓰지 않는다: 비행기 · 기차로 멀리 가서 렌터카를 몰거나(제주),
/// 돌아와서 집에 둔 차를 모는 경우다. 그때는 차 주행으로 두고, 세운 자리도 그 차를 따라간다.
pub(crate) const CAR_FAR_MAX_M: f64 = 60_000.0;

/// 차를 세운 자리 [car]에서 [p]가 "내 차가 여기 없다"고 볼 만큼 떨어져 있는가 ([CAR_FAR_M] 초과 ~ [CAR_FAR_MAX_M] 이하).
pub(crate) fn away_from_car(car: Point, p: Point) -> bool {
    let d = haversine_m(car, p);
    d > CAR_FAR_M && d <= CAR_FAR_MAX_M
}

/// 출발 · 도착점이 역에서 이만큼 안이면 역에서 타고 내린 것으로 본다
pub(crate) const STATION_AT_M: f64 = 200.0;

/// 시각순 위치 점에서 "뛴 점" 수.
pub(crate) fn jumps(fixes: &[(i64, Point)]) -> u32 {
    fixes.windows(2).filter(|w| w[1].0 - w[0].0 >= JUMP_GAP_MS && haversine_m(w[0].1, w[1].1) >= JUMP_MIN_M).count() as u32
}

/// 장소 이름이 역인가 ("쌍문역", "서울역 1호선", "치킨뱅이 서울역점").
pub(crate) fn station_name(name: &str) -> bool {
    let n = name.trim();
    n.ends_with('역') || n.contains("역 ") || n.contains("역점")
}

/// 지하철 이동인가. [car_at] = 이 주행 전에 마지막으로 차를 세운 자리 (모르면 None).
pub(crate) fn is_transit(jumps: u32, start: Point, end: Point, car_at: Option<Point>, start_name: &str, end_name: &str) -> bool {
    if jumps < MIN_JUMPS {
        return false;
    }
    let far_from_car = car_at.is_some_and(|c| away_from_car(c, start));
    let at_station = |p: Point| crate::subway::near_station(p, STATION_AT_M);
    far_from_car || at_station(start) || at_station(end) || station_name(start_name) || station_name(end_name)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn at(north_m: f64) -> Point {
        Point { lat: 37.55 + north_m / 111_195.0, lon: 126.97 }
    }

    #[test]
    fn station_gaps_count_as_jumps_but_steady_fixes_do_not() {
        // 차: 1초마다 10m
        let car: Vec<(i64, Point)> = (0..600).map(|i| (i * 1000, at(i as f64 * 10.0))).collect();
        assert_eq!(jumps(&car), 0);
        // 지하철: 역마다 60초 끊기고 900m 뒤에 나타난다 (역에서는 6초마다 같은 자리)
        let mut sub = Vec::new();
        for s in 0..5i64 {
            for k in 0..5 {
                sub.push((s * 90_000 + k * 6_000, at(s as f64 * 900.0)));
            }
        }
        assert_eq!(jumps(&sub), 4);
    }

    #[test]
    fn transit_needs_the_signal_shape_plus_another_reason() {
        // 역이 없는 곳 (강릉)
        let (start, end) = (Point { lat: 37.80, lon: 128.90 }, Point { lat: 37.82, lon: 128.90 });
        let at = |north_m: f64| Point { lat: start.lat + north_m / 111_195.0, lon: start.lon };
        // 신호가 이어졌으면 역 앞에서 출발해도 차
        assert!(!is_transit(0, start, end, Some(at(12_000.0)), "쌍문역", ""));
        // 뛴 점이 많아도 차를 세운 자리에서 출발했고 역이 아니면 차 (터널)
        assert!(!is_transit(4, start, end, Some(at(200.0)), "응봉동 현대아파트", "센터원빌딩"));
        // 뛴 점 + 차에서 12km 떨어진 출발
        assert!(is_transit(4, start, end, Some(at(12_000.0)), "", ""));
        // 차에서 400km 떨어진 곳(제주 렌터카 · 돌아와서 집 차): 뛴 점이 있어도 "내 차가 아님"의 근거가 아니다
        assert!(!is_transit(4, start, end, Some(at(400_000.0)), "", ""));
        // 뛴 점 + 역 (차를 역 앞에 세우고 탄 경우)
        assert!(is_transit(3, start, end, Some(at(200.0)), "치킨뱅이 서울역점", ""));
        assert!(is_transit(3, start, end, None, "", "쌍문역"));
        // 뛴 점 + 역 바로 앞에서 출발 (쌍문역)
        assert!(is_transit(3, Point { lat: 37.6486, lon: 127.0347 }, end, None, "", ""));
        assert!(!station_name("역삼동 주민센터") && !station_name("광역버스 정류장"));
    }
}

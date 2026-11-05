//! 기록 저장소 (Kotlin에 공개되는 `EodiStore` 객체).
//!
//! 계약(시그니처·의미)은 이 파일의 doc 주석이 기준이다. 구현은 SQLite(rusqlite, bundled)로 한다.
//!
//! [계약 단계 껍데기] 지금은 메모리에만 쌓고, 조회는 빈 결과를 돌려준다. 실제 구현으로 교체될 예정.

use std::sync::{Arc, Mutex};

use crate::ffi::GeoPoint;

/// 위치 한 점 (FusedLocation `LocationData`에서 필요한 값만).
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct GeoFix {
    pub lat: f64,
    pub lon: f64,
    /// UTC epoch 밀리초 (LocationData.timestampMillis)
    pub t_ms: i64,
    /// 위치 정확도(m). 모르면 0.
    pub accuracy_m: f32,
}

/// 주행 시작·끝 신호. 시동(IGNITION_STATE) 또는 기어(P↔D)에서 온다.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DriveEvent {
    Start,
    End,
}

/// 타임라인 목록 한 줄 = 하루.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DaySummary {
    /// 현지 날짜 "YYYY-MM-DD" (open 때 받은 tz_offset_minutes 기준)
    pub day: String,
    pub trip_count: u32,
    pub stay_count: u32,
    /// 그날 모든 주행 거리 합 (m)
    pub distance_m: f64,
    /// 그날 주행 시간 합 (ms)
    pub drive_millis: i64,
    /// 그날 첫 위치 / 마지막 위치 시각 (UTC epoch ms)
    pub first_ms: i64,
    pub last_ms: i64,
}

/// 주행 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TripSummary {
    pub start_ms: i64,
    pub end_ms: i64,
    pub distance_m: f64,
    pub start: GeoPoint,
    pub end: GeoPoint,
    /// 지도에 그릴 경로: geo::simplify(허용 오차 5m)로 줄인 점
    pub path: Vec<GeoPoint>,
}

/// 머문 곳 하나.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct StaySummary {
    pub center: GeoPoint,
    pub arrive_ms: i64,
    pub leave_ms: i64,
}

/// 하루 상세 (시간순).
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DayDetail {
    pub day: String,
    pub trips: Vec<TripSummary>,
    pub stays: Vec<StaySummary>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum CoreError {
    #[error("db: {0}")]
    Db(String),
    #[error("invalid input: {0}")]
    InvalidInput(String),
}

/// 기기 안 기록 저장소. 한 앱에서 하나만 열어 계속 쓴다. 여러 스레드에서 불러도 안전해야 한다.
#[derive(uniffi::Object)]
pub struct EodiStore {
    #[allow(dead_code)]
    tz_offset_minutes: i32,
    fixes: Mutex<Vec<GeoFix>>,
}

#[uniffi::export]
impl EodiStore {
    /// DB 파일을 열거나 만든다. `tz_offset_minutes`: 날짜를 나눌 현지 시간대 (한국 = 540).
    #[uniffi::constructor]
    pub fn open(path: String, tz_offset_minutes: i32) -> Result<Arc<Self>, CoreError> {
        if path.is_empty() {
            return Err(CoreError::InvalidInput("empty path".into()));
        }
        Ok(Arc::new(Self { tz_offset_minutes, fixes: Mutex::new(Vec::new()) }))
    }

    /// 위치 묶음을 저장한다 (수 초~수십 개 단위로 부른다). 시간순이 아니어도 되고, 같은 시각 중복은 무시한다.
    /// 정확도가 나쁜 점(accuracy_m > 100) 같은 노이즈는 여기서 거른다. 반환: 실제로 저장한 점 수.
    pub fn ingest(&self, fixes: Vec<GeoFix>) -> Result<u32, CoreError> {
        let n = fixes.len() as u32;
        self.fixes.lock().map_err(|e| CoreError::Db(e.to_string()))?.extend(fixes);
        Ok(n)
    }

    /// 주행 시작·끝 신호를 기록한다. 주행 나누기에서 위치 공백 기준과 함께 경계로 쓴다.
    pub fn mark_drive_event(&self, event: DriveEvent, t_ms: i64) -> Result<(), CoreError> {
        let _ = (event, t_ms);
        Ok(())
    }

    /// 기록이 있는 날을 최신순으로 최대 `limit`개. 1년치(365일)도 100ms 이내.
    pub fn day_summaries(&self, limit: u32) -> Result<Vec<DaySummary>, CoreError> {
        let _ = limit;
        Ok(Vec::new())
    }

    /// 하루 상세. 기록이 없는 날이면 빈 trips/stays.
    pub fn day_detail(&self, day: String) -> Result<DayDetail, CoreError> {
        Ok(DayDetail { day, trips: Vec::new(), stays: Vec::new() })
    }

    /// 저장된 위치 점 수 (디버그·검증용).
    pub fn fix_count(&self) -> Result<u64, CoreError> {
        Ok(self.fixes.lock().map_err(|e| CoreError::Db(e.to_string()))?.len() as u64)
    }
}

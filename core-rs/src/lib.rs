//! 어디였지 네이티브 코어.
//!
//! Kotlin(UI · Pleos SDK)은 위치를 묶음으로 넘기고, 계산은 모두 여기서 한다.
//! Kotlin에서 부르는 함수는 `ffi` 모듈에만 있다 (UniFFI가 Kotlin 코드를 자동 생성).

uniffi::setup_scaffolding!();

pub mod course;
pub mod ffi;
pub mod geo;
pub mod mapdata;
pub mod memory;
pub mod places;
pub mod store;
pub mod trip;

pub use geo::{Fix, Point};

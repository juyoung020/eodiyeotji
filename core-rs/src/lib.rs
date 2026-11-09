//! 어디였지 네이티브 코어.
//!
//! Kotlin(UI · Pleos SDK)은 위치를 묶음으로 넘기고, 계산은 모두 여기서 한다.
//! Kotlin에서 부르는 함수는 `ffi` 모듈에만 있다 (UniFFI가 Kotlin 코드를 자동 생성).

uniffi::setup_scaffolding!();

pub mod agent;
pub mod cabin;
pub mod community;
pub mod course;
pub mod drivedetect;
pub mod edits;
pub mod energy;
pub mod ffi;
pub mod geo;
pub mod handoff;
pub mod lastfix;
pub mod mapdata;
pub mod memmap;
pub mod memory;
pub mod nav;
pub mod offers;
pub mod parking;
pub mod photos;
pub mod place_detail;
pub mod places;
pub mod recap;
pub mod replay;
pub mod saved;
pub mod search_ui;
pub mod share;
pub mod store;
pub mod sync;
pub mod trip;
#[cfg(test)]
mod perf_tests;

pub use geo::{Fix, Point};

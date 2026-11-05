//! 어디였지 네이티브 코어.
//!
//! Kotlin(UI · Pleos SDK)은 위치를 묶음으로 넘기고, 계산은 모두 여기서 한다.

pub mod course;
pub mod geo;
pub mod trip;

pub use geo::{Fix, Point};

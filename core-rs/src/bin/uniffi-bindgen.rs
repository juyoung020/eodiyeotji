//! Kotlin 바인딩 생성기. Gradle 빌드가 자동으로 실행한다.
//!
//! cargo run --features bindgen --bin uniffi-bindgen -- generate --library <libeodi_core.so> --language kotlin --out-dir <dir>

fn main() {
    uniffi::uniffi_bindgen_main()
}

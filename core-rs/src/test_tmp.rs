//! 테스트용 임시 DB 자리: `temp/eodi-tests/<pid>/`.
//!
//! 테스트가 만든 SQLite 파일(+ -wal 16MB)은 Windows에서 연결이 남아 있으면 지워지지 않고, 지우지 않는 테스트도 많아
//! 실행마다 수백 MB가 temp에 쌓였다 (11/14 새벽 33GB로 디스크가 가득 차 테스트 · 빌드가 "disk is full"로 실패).
//! 실행마다 자기 pid 폴더에 모으고, 처음 쓸 때 1시간 넘은 다른 실행의 폴더를 지운다 (지금 도는 다른 실행은 건드리지 않게).

use std::path::PathBuf;
use std::sync::OnceLock;
use std::time::{Duration, SystemTime};

/// 이 테스트 실행의 임시 폴더 (없으면 만든다).
pub(crate) fn dir() -> PathBuf {
    static DIR: OnceLock<PathBuf> = OnceLock::new();
    DIR.get_or_init(|| {
        let root = std::env::temp_dir().join("eodi-tests");
        if let Ok(entries) = std::fs::read_dir(&root) {
            let old = SystemTime::now() - Duration::from_secs(3600);
            for e in entries.flatten() {
                let stale = e.metadata().and_then(|m| m.modified()).is_ok_and(|t| t < old);
                if stale {
                    let _ = std::fs::remove_dir_all(e.path());
                }
            }
        }
        let d = root.join(std::process::id().to_string());
        let _ = std::fs::create_dir_all(&d);
        d
    })
    .clone()
}

#[test]
fn test_dir_is_per_process_and_exists() {
    let d = dir();
    assert!(d.is_dir());
    assert!(d.ends_with(std::process::id().to_string()));
}

package app.eodiyeotji.core

import androidx.compose.runtime.Immutable

/*
 * 구글 타임라인 가져오기 입구 (core-rs/src/timeline_import.rs). 파일 읽기 · 고르기 · 저장은 Rust가 하고, 여기서는 옮겨 담기만 한다.
 */

/** 가져온 결과. */
@Immutable
data class TimelineImport(
    /** 넣은 주행 수 */
    val drives: Int,
    /** 넣은 위치 점 수 */
    val fixes: Int,
    /** 파일에 있던 차 이동 수 */
    val found: Int,
    /** 이미 기록이 있는 날이라 건너뛴 차 이동 수 */
    val skippedExisting: Int,
    val firstDay: String,
    val lastDay: String,
)

/**
 * 폰에서 내보낸 구글 타임라인 파일([path])의 차 이동을 주행 기록으로 넣는다. [fromDay]("YYYY-MM-DD")가 있으면 그날부터만.
 * 파일을 못 읽거나 형식이 다르면 예외.
 */
suspend fun EodiCore.importGoogleTimeline(path: String, fromDay: String? = null): TimelineImport = coreCall("importGoogleTimeline") {
    rust.importGoogleTimeline(path, fromDay).let {
        TimelineImport(it.drives.toInt(), it.fixes.toInt(), it.found.toInt(), it.skippedExisting.toInt(), it.firstDay, it.lastDay)
    }
}

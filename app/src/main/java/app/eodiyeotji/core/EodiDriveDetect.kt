package app.eodiyeotji.core

import app.eodiyeotji.core.rust.DriveDetector
import app.eodiyeotji.core.rust.MotionFix
import app.eodiyeotji.platform.DriveEdge

/** 폰 위치 한 점 + 기기가 잰 속도 (없으면 null). 주행 감지에만 쓴다. */
data class MotionSample(val fix: LocationFix, val speedMps: Float?)

/** 주행 감지 결과: 지금 주행 중인지, 고정밀 위치가 필요한지, 이번에 바뀐 순간들. */
data class DriveDetectResult(val driving: Boolean, val highRate: Boolean, val edges: List<DriveEdge>)

/**
 * 폰 주행 감지기 (Rust `drivedetect`, 규칙은 그 파일 머리말). 값 옮겨 담기만 한다.
 * 상태를 가진 Rust 객체 하나를 감싼다 — 한 코루틴에서만 부른다. Rust 호출이 짧아(점 수십 개 µs 단위) 호출한 스레드에서 바로 돈다.
 */
class EodiDriveDetect : AutoCloseable {
    private val rust = DriveDetector()

    override fun close() = rust.close()

    /** 앱이 주행 기록 중에 죽었다 다시 뜬 경우: 주행 중 상태로 이어 간다. */
    fun resumeDriving(lastFixMs: Long) = rust.resumeDriving(lastFixMs)

    /** 위치 묶음 (시각 순서대로). */
    fun push(samples: List<MotionSample>): DriveDetectResult =
        rust.push(samples.map { MotionFix(it.fix.lat, it.fix.lon, it.fix.tMs, it.fix.accuracyM, it.speedMps) }).toModel()

    /** 위치가 안 들어와도 시간이 흐른 것을 알린다 (머문 시간·위치 끊김으로 주행 끝을 알기 위해). */
    fun tick(nowMs: Long): DriveDetectResult = rust.tick(nowMs).toModel()

    private fun app.eodiyeotji.core.rust.DriveDetectOut.toModel() = DriveDetectResult(
        driving = driving,
        highRate = highRate,
        edges = edges.map { e ->
            DriveEdge(e.active, e.tMs, e.backlog.map { LocationFix(it.lat, it.lon, it.tMs, it.accuracyM) })
        },
    )
}

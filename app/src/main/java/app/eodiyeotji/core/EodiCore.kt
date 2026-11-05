package app.eodiyeotji.core

import app.eodiyeotji.core.rust.coreSelfCheck
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rust 코어(eodi-core) 입구. 앱의 다른 코드는 Rust 바인딩(app.eodiyeotji.core.rust)을 직접 부르지 않고 여기를 거친다.
 *
 * 모든 호출은 Dispatchers.Default에서 한다 → UI 스레드를 막지 않는다.
 */
@Singleton
class EodiCore @Inject constructor() {

    suspend fun selfCheck(): CoreStatus = withContext(Dispatchers.Default) {
        val c = coreSelfCheck()
        CoreStatus(
            version = c.version,
            pointsIn = c.pointsIn.toInt(),
            pointsOut = c.pointsOut.toInt(),
            elapsedMicros = c.elapsedMicros.toLong(),
        )
    }
}

/** 화면에 넘기는 불변 값 (Rust 바인딩의 record는 var라서 그대로 쓰지 않는다). */
data class CoreStatus(
    val version: String,
    val pointsIn: Int,
    val pointsOut: Int,
    val elapsedMicros: Long,
)

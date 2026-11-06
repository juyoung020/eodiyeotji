package app.eodiyeotji.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.platform.LocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Pleos FusedLocation SDK 얇은 어댑터. 위치를 [LocationFix] 흐름으로 바꿔 줄 뿐, 가공은 하지 않는다.
 *
 * 생명주기(공식 문서 순서): isSupported → initialize → registerFusedLocationCallback
 * → unregisterFusedLocationCallback → release.
 * release 뒤에는 같은 인스턴스를 다시 쓸 수 없으므로 [updates]를 모을 때마다 새 인스턴스를 만든다.
 */
@Singleton
class PleosLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {
    /** 이 차(에뮬레이터)에 Pleos 위치 서비스가 있는지. 패키지 조회라서 백그라운드 스레드에서 부른다. */
    override fun isSupported(): Boolean =
        runCatching { FusedLocation(context).isSupported() }
            .onFailure { Log.w(TAG, "isSupported 확인 실패", it) }
            .getOrDefault(false)

    override fun hasPermission(): Boolean = hasPermission(context)

    /**
     * 위치 업데이트 흐름. 모으는 동안만 SDK가 켜져 있고, 취소하면 콜백 해제 → release 한다.
     *
     * - `onChanged(null)`은 유효한 위치가 아니므로 버린다 (공식 오류 처리 가이드).
     * - `onError`는 기록만 하고 흐름은 유지한다. SDK가 서비스에 다시 연결되면 위치가 다시 들어온다.
     * - 권한이 없으면 SDK가 SecurityException을 던진다 → 흐름을 그 예외로 닫는다.
     */
    override fun updates(): Flow<LocationFix> = callbackFlow {
        val fused = FusedLocation(context)
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                location ?: return
                trySend(location.toFix())
            }

            override fun onError(exception: Exception) {
                Log.w(TAG, "FusedLocation onError", exception)
            }
        }
        try {
            fused.initialize()
            fused.registerFusedLocationCallback(listener)
        } catch (e: RuntimeException) {
            runCatching { fused.release() }
            throw e
        }
        awaitClose {
            runCatching { fused.unregisterFusedLocationCallback(listener) }
            runCatching { fused.release() }
        }
    }

    companion object {
        private const val TAG = "EodiPleosLocation"

        /** Pleos 차량 위치 권한 (dangerous → 첫 실행 때 한 번 사용자에게 묻는다). */
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}

/** SDK의 confidence를 정확도(m)로 넘긴다. Rust가 정확도가 나쁜 점을 거른다. */
private fun LocationData.toFix() = LocationFix(
    lat = latitude,
    lon = longitude,
    tMs = timestampMillis,
    accuracyM = confidence,
)

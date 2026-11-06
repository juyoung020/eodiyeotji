package app.eodiyeotji.phone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.core.MotionSample
import app.eodiyeotji.platform.LocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/** 위치를 얼마나 자주·정확히 받을지. */
enum class LocationRate {
    /** 서 있을 때: 1분 간격 저전력(와이파이·기지국 위주) + 다른 앱이 켠 위치를 공짜로 같이 받기(passive). */
    LOW,

    /** 주행 중·출발 확인 중: 1초 간격 GPS. */
    HIGH,
}

/**
 * 폰 위치 (Android 표준 [LocationManager]). 위치를 받아 넘길 뿐, 가공은 하지 않는다.
 *
 * 구글 Play 서비스(FusedLocationProviderClient)를 쓰지 않는 이유: Android 12+의 `LocationManager.FUSED_PROVIDER`가
 * 같은 일을 하고(구글 서비스가 있는 폰에서는 그 구현이 곧 Play 서비스의 융합 위치다), 의존성이 없어 앱이 가볍고
 * Play 서비스가 없는 기기·에뮬레이터에서도 똑같이 돈다. 융합 위치가 없는 기기(Android 11 이하 등)는 GPS·네트워크 위치를 직접 쓴다.
 *
 * 콜백은 전용 스레드에서 받는다 → UI 스레드를 쓰지 않는다.
 */
@Singleton
class PhoneLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {
    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)
    private val executor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "eodi-location") } }

    override fun isSupported(): Boolean = manager != null && provider(LocationRate.HIGH) != null

    override fun hasPermission(): Boolean = hasPermission(context)

    /** 주행 기록용: 1초 간격 고정밀. */
    override fun updates(): Flow<LocationFix> = motion(LocationRate.HIGH).map { it.fix }

    /** 주행 감지용: [rate]에 맞춘 위치 + 기기가 잰 속도. 모으는 동안만 켜져 있다. 권한이 없으면 SecurityException으로 닫힌다. */
    @SuppressLint("MissingPermission") // hasPermission()을 먼저 확인한다. 없으면 SecurityException으로 흐름이 닫힌다
    fun motion(rate: LocationRate): Flow<MotionSample> = callbackFlow {
        val lm = manager ?: run { close(); return@callbackFlow }
        if (!hasPermission()) throw SecurityException("위치 권한 없음")
        val listener = LocationListenerCompat { loc -> trySend(loc.toSample()) }
        val main = provider(rate)
        if (main != null) {
            LocationManagerCompat.requestLocationUpdates(lm, main, request(rate), executor, listener)
        }
        // 서 있을 때는 다른 앱(지도 앱 내비 등)이 켠 위치도 같이 받는다 (전력 추가 없음)
        val passive = rate == LocationRate.LOW && LocationManagerCompat.hasProvider(lm, LocationManager.PASSIVE_PROVIDER)
        if (passive) {
            LocationManagerCompat.requestLocationUpdates(
                lm, LocationManager.PASSIVE_PROVIDER,
                LocationRequestCompat.Builder(LocationRequestCompat.PASSIVE_INTERVAL).setMinUpdateIntervalMillis(1_000).build(),
                executor, listener,
            )
        }
        Log.i(TAG, "위치 요청 $rate: ${main ?: "없음"}${if (passive) " + passive" else ""}")
        awaitClose { runCatching { LocationManagerCompat.removeUpdates(lm, listener) } }
    }

    /** 쓸 위치 제공자: 융합 위치가 있으면 그것, 없으면 고정밀은 GPS, 저전력은 네트워크(없으면 GPS). */
    private fun provider(rate: LocationRate): String? {
        val lm = manager ?: return null
        fun has(p: String) = LocationManagerCompat.hasProvider(lm, p)
        if (Build.VERSION.SDK_INT >= 31 && has(LocationManager.FUSED_PROVIDER)) return LocationManager.FUSED_PROVIDER
        return when (rate) {
            LocationRate.HIGH -> listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            LocationRate.LOW -> listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        }.firstOrNull(::has)
    }

    private fun request(rate: LocationRate): LocationRequestCompat = when (rate) {
        LocationRate.HIGH -> LocationRequestCompat.Builder(HIGH_INTERVAL_MS)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(HIGH_INTERVAL_MS)
            .build()
        LocationRate.LOW -> LocationRequestCompat.Builder(LOW_INTERVAL_MS)
            .setQuality(LocationRequestCompat.QUALITY_LOW_POWER)
            .setMinUpdateIntervalMillis(LOW_MIN_INTERVAL_MS)
            .build()
    }

    companion object {
        private const val TAG = "EodiPhoneLocation"
        private const val HIGH_INTERVAL_MS = 1_000L
        private const val LOW_INTERVAL_MS = 60_000L
        private const val LOW_MIN_INTERVAL_MS = 15_000L

        fun hasPermission(context: Context): Boolean =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
    }
}

private fun Location.toSample() = MotionSample(
    fix = LocationFix(lat = latitude, lon = longitude, tMs = time, accuracyM = if (hasAccuracy()) accuracy else 0f),
    speedMps = if (hasSpeed()) speed else null,
)

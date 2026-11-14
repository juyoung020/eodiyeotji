package app.eodiyeotji.platform

import android.content.Context
import android.content.pm.PackageManager
import app.eodiyeotji.core.LocationFix
import app.eodiyeotji.pleos.PleosLocationSource
import app.eodiyeotji.record.VehicleDriveSignals
import app.eodiyeotji.ui.agent.ManualDrivingStatePort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/*
 * 차가 아닌 기기(태블릿)에 차 판을 깔았을 때의 가짜 차. 진짜 차(자동차용 안드로이드)에서는 쓰이지 않는다.
 * 태블릿에는 기어 · 속도 · 차 위치 신호가 없어서, 시연용 인포 화면(ui/shell/FakeCarShell.kt)의 기어 버튼이 이 값을 바꾸고
 * 앱은 그것을 차 신호처럼 받는다:
 * - 기어 D · R → 주행 시작 (주행 화면으로 바뀌고 기록이 시작된다), P · N → 주행 끝.
 * - 주행하는 동안 위치는 앱에 넣어 둔 시연 주행(서울시청 → 광장시장 → 경복궁, 실제 도로를 따라 1초에 한 점)을 그대로 흘려보낸다.
 *   태블릿 GPS는 쓰지 않는다 (차 판은 Android 위치 권한을 갖지 않는다).
 */

/** 가짜 차의 상태: 기어와 속도. */
@Singleton
class TabletVehicle @Inject constructor(
    @ApplicationContext context: Context,
    private val manual: ManualDrivingStatePort,
) {
    /** 차가 아닌 기기인가 (자동차용 안드로이드가 아니다). */
    val isTablet: Boolean = !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private val _gear = MutableStateFlow("P")
    val gear: StateFlow<String> = _gear.asStateFlow()

    /**
     * 시연 내비가 달리는 흉내를 내는 중인가 (platform/TabletNavigator.kt). 그동안 인포 화면은 기어를 D로 보인다.
     * 보이는 것만 바뀐다: 진짜 기어([gear])는 그대로라 앱이 주행 중으로 바뀌거나 시연 주행을 기록하지 않는다.
     */
    val navDriving = MutableStateFlow(false)

    /** 지금 속도 (km/h, 시연 주행의 값). 서 있으면 0. */
    val speedKmh = MutableStateFlow(0f)

    fun setGear(gear: String) {
        _gear.value = gear
        if (!isMoving(gear)) speedKmh.value = 0f
        // 주행 화면 전환은 디버그 화면과 같은 길로 (CarDrivingStatePort가 이 값도 주행으로 본다)
        manual.setDriving(isMoving(gear))
    }

    companion object {
        fun isMoving(gear: String) = gear == "D" || gear == "R"
    }
}

/** 가짜 차의 주행 시작 · 끝: 기어가 D · R이 되면 시작, 벗어나면 끝. 모으기 시작하면 지금 상태가 한 번 온다 (차량 신호와 같다). */
@Singleton
class TabletDriveSignals @Inject constructor(private val vehicle: TabletVehicle) : DriveSignalSource {
    override fun edges(resumeDriving: Boolean, lastFixMs: Long): Flow<DriveEdge> =
        vehicle.gear.map(TabletVehicle::isMoving).distinctUntilChanged().map { DriveEdge(it, System.currentTimeMillis()) }

    override val linked: StateFlow<Boolean> = MutableStateFlow(true)
}

/**
 * 가짜 차의 위치: 넣어 둔 시연 주행을 1초에 한 점씩 흘려보낸다 (시각은 지금 시각으로 찍는다).
 * 모을 때마다 다음 구간을 처음부터 달린다 (구간 1 → 구간 2 → 다시 1). 구간 끝에 닿으면 그 자리에 서 있는다.
 */
@Singleton
class TabletLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicle: TabletVehicle,
) : LocationSource {
    private var next = 0

    override fun isSupported(): Boolean = true

    override fun hasPermission(): Boolean = true

    override fun updates(): Flow<LocationFix> = flow {
        val leg = LEGS[next % LEGS.size]
        next++
        val points = context.assets.open(leg).bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull(::parse).toList()
        }
        if (points.isEmpty()) return@flow
        for (p in points) {
            vehicle.speedKmh.value = p.kmh
            emit(LocationFix(p.lat, p.lon, System.currentTimeMillis(), ACCURACY_M))
            delay(STEP_MS)
        }
        // 도착: 기어를 P로 바꿀 때까지 그 자리에 서 있는다
        vehicle.speedKmh.value = 0f
        val last = points.last()
        while (true) {
            emit(LocationFix(last.lat, last.lon, System.currentTimeMillis(), ACCURACY_M))
            delay(STEP_MS)
        }
    }.flowOn(Dispatchers.IO)

    /** "초,위도,경도,속도" 한 줄. 모양이 다르면 null. */
    private fun parse(line: String): Point? {
        val f = line.split(',')
        if (f.size < 4) return null
        return Point(f[1].toDoubleOrNull() ?: return null, f[2].toDoubleOrNull() ?: return null, f[3].toFloatOrNull() ?: 0f)
    }

    private class Point(val lat: Double, val lon: Double, val kmh: Float)

    private companion object {
        val LEGS = listOf("demo-drive/leg1.csv", "demo-drive/leg2.csv")
        const val STEP_MS = 1_000L
        const val ACCURACY_M = 5f
    }
}

/** 차 판의 주행 신호: 진짜 차는 기어 · 시동([VehicleDriveSignals]), 태블릿은 가짜 차([TabletDriveSignals]). */
@Singleton
class CarDriveSignals @Inject constructor(vehicle: TabletVehicle, car: VehicleDriveSignals, tablet: TabletDriveSignals) :
    DriveSignalSource by (if (vehicle.isTablet) tablet else car)

/** 차 판의 위치: 진짜 차는 Pleos 차량 위치([PleosLocationSource]), 태블릿은 가짜 차([TabletLocationSource]). */
@Singleton
class CarLocationSource @Inject constructor(vehicle: TabletVehicle, car: PleosLocationSource, tablet: TabletLocationSource) :
    LocationSource by (if (vehicle.isTablet) tablet else car)

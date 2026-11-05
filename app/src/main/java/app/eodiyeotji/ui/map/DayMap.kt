package app.eodiyeotji.ui.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.eodiyeotji.R
import app.eodiyeotji.core.DayMapData
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.MapInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.LibraryLoader
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/**
 * MapView를 만들기 전 준비. 둘 다 UI 스레드에서 하면 디스크를 읽어서 IO 스레드로 뺀다 (StrictMode 실측):
 * - 네이티브 라이브러리(libmaplibre.so) 올리기: 처음 한 번 100ms 넘게 걸린다. 이미 올렸으면 바로 돌아온다.
 * - 기본 옵션 만들기(createFromAttributes): 안에서 filesDir을 확인한다.
 */
private object MapLibreNative {
    suspend fun prepare(context: Context): MapLibreMapOptions = withContext(Dispatchers.IO) {
        LibraryLoader.load()
        MapLibreMapOptions.createFromAttributes(context)
    }
}

/** 기록이 아직 없을 때 처음 보이는 곳 (서울 시청 근처). */
private val InitialCenter = LatLng(37.5665, 126.9780)
private const val INITIAL_ZOOM = 11.0

/** 한글 지명은 지도 파일의 글꼴 대신 기기 글꼴로 그린다 (Hangul Syllables · CJK 범위). */
private const val LOCAL_IDEOGRAPH_FONT = "sans-serif"

/**
 * 하루 지도. 기기 안 지도 파일 위에 그날의 주행 경로(선)와 머문 곳(핀)을 그린다.
 *
 * @param data Rust가 만든 하루치 GeoJSON + 범위. null이면(불러오는 중) 경로를 비운다.
 *   날([DayMapData.day])이 바뀌면 카메라를 범위에 맞추고, 같은 날이 다시 읽히면 데이터만 바꾼다.
 * @param selectedKey 강조할 타임라인 항목 키("t<start_ms>" / "s<arrive_ms>"), 없으면 null.
 * @param onSelectKey 지도에서 경로·핀을 눌렀을 때 그 항목 키, 빈 곳을 누르면 null.
 *
 * MapView는 이 컴포저블이 화면에 있는 동안 하나만 만들어 재구성 사이에 그대로 쓴다.
 * 생명주기(onStart~onDestroy)와 메모리 부족 알림은 화면 생명주기를 따라 MapView에 넘긴다.
 */
@Composable
fun DayMap(
    data: DayMapData?,
    selectedKey: String?,
    onSelectKey: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.map_content_description)
    val inPreview = LocalInspectionMode.current
    val appContext = LocalContext.current.applicationContext
    // 지도 엔진 준비는 IO 스레드에서. 그동안(처음 한 번 0.1~0.2초)은 바탕색 자리만 보인다.
    val baseOptions by produceState<MapLibreMapOptions?>(initialValue = null) {
        if (!inPreview) value = MapLibreNative.prepare(appContext)
    }
    val options = baseOptions
    if (options == null) {
        // 미리보기에는 네이티브 지도가 없다 → 자리만 잡는다
        Box(modifier.background(EodiTheme.colors.surfaceHigh).semantics { contentDescription = description })
        return
    }
    DayMapContent(data, selectedKey, onSelectKey, options, modifier.semantics { contentDescription = description })
}

@Composable
private fun DayMapContent(
    data: DayMapData?,
    selectedKey: String?,
    onSelectKey: (String?) -> Unit,
    baseOptions: MapLibreMapOptions,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    // 경로·핀 색은 라이트 바탕 지도에 맞춘 고정값(MapInk), 지도 파일이 없을 때의 바탕색만 테마를 따른다
    val loadingBackground = EodiTheme.colors.surfaceHigh
    val palette = remember(loadingBackground) {
        MapPalette(
            trip = MapInk.trip.toArgb(),
            casing = MapInk.casing.toArgb(),
            stay = MapInk.stay.toArgb(),
            openStay = MapInk.here.toArgb(),
            selectedStroke = MapInk.selectedStroke.toArgb(),
            background = loadingBackground.toArgb(),
            onStay = MapInk.onMarker.toArgb(),
            onOpenStay = MapInk.onMarker.toArgb(),
            label = MapInk.label.toArgb(),
            labelHalo = MapInk.labelHalo.toArgb(),
        )
    }

    val currentOnSelect by rememberUpdatedState(onSelectKey)
    var style by remember { mutableStateOf<MapStyleSpec?>(null) }

    val mapView = rememberMapViewWithLifecycle(context, baseOptions, palette.background)
    val controller = remember(mapView) {
        DayMapController(
            mapView = mapView,
            density = density,
            onSelectKey = { currentOnSelect(it) },
            // 오프라인 스타일이 깨져 있어도 앱은 멈추지 않는다 → 바탕색 스타일로 바꾸고 안내만 띄운다
            onStyleFailed = { if (style is MapStyleSpec.Offline) style = MapStyleSpec.Fallback },
        )
    }

    // 지도 파일·스타일 찾기는 IO 스레드에서 (UI 스레드 블로킹 0)
    LaunchedEffect(Unit) { style = MapStyleSource.resolve(context.applicationContext) }

    val backgroundArgb by rememberUpdatedState(palette.background)
    LaunchedEffect(controller, style) {
        when (val s = style) {
            null -> Unit
            is MapStyleSpec.Offline -> controller.setStyle(s.json)
            MapStyleSpec.Fallback -> controller.setStyle(MapStyleSource.fallbackJson(backgroundArgb))
        }
    }
    LaunchedEffect(controller, palette) { controller.setPalette(palette) }
    LaunchedEffect(controller, data) { controller.setData(data) }
    LaunchedEffect(controller, selectedKey) { controller.setSelection(selectedKey) }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        when (style) {
            MapStyleSpec.Fallback -> MapNotice(
                stringResource(R.string.map_offline_missing),
                Modifier.align(Alignment.TopStart).padding(16.dp),
            )
            // 지도 데이터 출처 표시 (OSM 라이선스). 지도 파일을 쓸 때 항상 보인다.
            is MapStyleSpec.Offline -> MapNotice(
                stringResource(R.string.map_attribution),
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                small = true,
            )
            null -> Unit
        }
    }
}

/** 지도 위 작은 안내 (출처 · 지도 파일 없음). 지도 위에서도 읽히게 반투명 흰 바탕 + 진한 글자. */
@Composable
private fun MapNotice(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        text,
        style = if (small) MapNoticeSmall else EodiTheme.type.caption,
        color = MapInk.label.copy(alpha = 0.78f),
        maxLines = 1,
        modifier = modifier
            .background(MapInk.casing.copy(alpha = 0.86f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** 출처 표시 글자: 지도를 가리지 않게 작지만 차 안에서 읽히는 크기. */
private val MapNoticeSmall = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)

/**
 * MapView 하나를 만들고 화면 생명주기를 그대로 넘긴다.
 * 컴포저블이 사라지면(다른 날 상세로 바뀌는 게 아니라 화면에서 빠질 때) 남은 단계를 마저 내리고 onDestroy 한다.
 */
@Composable
private fun rememberMapViewWithLifecycle(context: Context, baseOptions: MapLibreMapOptions, loadingColor: Int): MapView {
    val mapView = remember {
        MapLibre.getInstance(context)
        val options = baseOptions
            .foregroundLoadColor(loadingColor)
            .camera(CameraPosition.Builder().target(InitialCenter).zoom(INITIAL_ZOOM).build())
            .localIdeographFontFamily(LOCAL_IDEOGRAPH_FONT)
            .logoEnabled(false)
            // 출처는 MapNotice로 직접 보여 준다 (차 안에서 i 버튼 → 외부 링크 대화상자를 띄우지 않는다)
            .attributionEnabled(false)
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
        MapView(context, options)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        // 화면이 이미 RESUMED여도 addObserver가 ON_CREATE → ON_START → ON_RESUME을 차례로 다시 보내 준다
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit // ON_DESTROY는 아래 onDispose에서 한 번만
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // 앱이 뒤로 가서 시스템이 메모리를 거둘 때 지도 타일 캐시를 비운다
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) mapView.onLowMemory()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("ComponentCallbacks 호환")
            override fun onLowMemory() = mapView.onLowMemory()
        }
        context.registerComponentCallbacks(memory)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

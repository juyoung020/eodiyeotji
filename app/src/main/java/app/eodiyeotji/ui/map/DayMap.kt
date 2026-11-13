package app.eodiyeotji.ui.map

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import app.eodiyeotji.place.PlacePhotoRepository
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
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
import app.eodiyeotji.core.MapPhotoSpot
import app.eodiyeotji.ui.gallery.MyPhotoViewer
import app.eodiyeotji.core.MapBoundsLatLon
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.mapInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
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
internal object MapLibreNative {
    suspend fun prepare(context: Context): MapLibreMapOptions = withContext(Dispatchers.IO) {
        LibraryLoader.load()
        MapLibreMapOptions.createFromAttributes(context)
    }
}

/** 기록이 아직 없을 때 처음 보이는 곳 (서울 시청 근처). */
private val InitialCenter = LatLng(37.5665, 126.9780)
private const val INITIAL_ZOOM = 11.0

/** 범위 가운데 · 범위가 대략 들어오는 줌 (처음 카메라용, 기울기 없음). */
internal fun roughFlatCamera(b: MapBoundsLatLon): CameraPosition {
    val span = maxOf(b.maxLat - b.minLat, (b.maxLon - b.minLon) * 0.8, 0.002)
    val zoom = (kotlin.math.ln(180.0 / span) / kotlin.math.ln(2.0)).coerceIn(4.0, 15.0)
    return CameraPosition.Builder().target(LatLng((b.minLat + b.maxLat) / 2, (b.minLon + b.maxLon) / 2)).zoom(zoom).build()
}

/** 한글 지명은 지도 파일의 글꼴 대신 기기 글꼴로 그린다 (Hangul Syllables · CJK 범위). */
internal const val LOCAL_IDEOGRAPH_FONT = "sans-serif"

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
    photoPins: List<MapPhotoPin> = emptyList(),
    /** 그날 내 사진: 찍은 곳에 작은 사진 카드로 꽂고, 누르면 크게 본다 */
    myPhotos: List<MapPhotoSpot> = emptyList(),
    /** 지도 위에 얹은 판이 가리는 폭 (왼쪽 · 위 · 오른쪽 · 아래 px): 그만큼 비켜서 길을 맞춘다 */
    fitInsets: IntArray? = null,
) {
    val description = stringResource(R.string.map_content_description)
    val inPreview = LocalInspectionMode.current
    val appContext = LocalContext.current.applicationContext
    val night = EodiTheme.colors.isDark
    // 지도 엔진 준비는 IO 스레드에서. 그동안(처음 한 번 0.1~0.2초)은 바탕색 자리만 보인다.
    val baseOptions by produceState<MapLibreMapOptions?>(initialValue = null) {
        if (inPreview) return@produceState
        // 스타일 만들기(asset 읽기·JSON 합치기)를 엔진 준비와 동시에 시작한다 → 지도 칸이 생길 때는 캐시에서 바로 나온다
        val styleWarm = launch { MapStyleSource.resolve(appContext, night) }
        value = MapLibreNative.prepare(appContext)
        styleWarm.join()
    }
    val options = baseOptions
    if (options == null) {
        // 미리보기에는 네이티브 지도가 없다 → 자리만 잡는다
        Box(modifier.background(EodiTheme.colors.surfaceHigh).semantics { contentDescription = description })
        return
    }
    DayMapContent(data, selectedKey, onSelectKey, photoPins, myPhotos, fitInsets, options, modifier.semantics { contentDescription = description })
}

@Composable
private fun DayMapContent(
    data: DayMapData?,
    selectedKey: String?,
    onSelectKey: (String?) -> Unit,
    photoPins: List<MapPhotoPin>,
    myPhotos: List<MapPhotoSpot>,
    fitInsets: IntArray?,
    baseOptions: MapLibreMapOptions,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    // 경로·핀 색은 낮 · 밤 지도에 맞춘 값(MapInk), 지도 파일이 없을 때의 바탕색은 테마를 따른다
    val loadingBackground = EodiTheme.colors.surfaceHigh
    val night = EodiTheme.colors.isDark
    val ink = mapInk(night)
    val palette = remember(loadingBackground, ink) {
        MapPalette(
            trip = ink.trip.toArgb(),
            casing = ink.casing.toArgb(),
            stay = ink.stay.toArgb(),
            openStay = ink.here.toArgb(),
            selectedStroke = ink.selectedStroke.toArgb(),
            background = loadingBackground.toArgb(),
            onStay = ink.onMarker.toArgb(),
            onOpenStay = ink.onMarker.toArgb(),
            label = ink.label.toArgb(),
        )
    }

    val currentOnSelect by rememberUpdatedState(onSelectKey)
    val currentPhotos by rememberUpdatedState(myPhotos)
    // 크게 보는 내 사진 (사진 번호들, 처음 볼 장)
    var viewing by remember { mutableStateOf<List<MapPhotoSpot>?>(null) }
    // 고른 카메라 핀: 사진 번호들과 핀 자리 (없으면 null) → 말풍선을 그려 지도에 얹는다
    var picked by remember { mutableStateOf<Triple<List<Int>, Double, Double>?>(null) }
    var style by remember { mutableStateOf<MapStyleSpec?>(null) }

    val mapView = rememberMapViewWithLifecycle(
        context,
        baseOptions,
        palette.background,
        // 그날 범위를 이미 알면 거기서 시작한다 (서울 기본 위치 타일을 헛받지 않는다, 정확한 맞춤은 데이터가 들어갈 때)
        initialCamera = remember { data?.bounds?.let(::roughFlatCamera) },
        label = "day",
    )
    val controller = remember(mapView) {
        DayMapController(
            mapView = mapView,
            density = density,
            onSelectKey = { currentOnSelect(it) },
            onOpenPhotos = { idx -> viewing = idx.mapNotNull { currentPhotos.getOrNull(it) }.takeIf { it.isNotEmpty() } },
            onSelectPhotos = { idx, lat, lon -> picked = if (idx.isEmpty()) null else Triple(idx, lat, lon) },
            // 불러오기에 실패해도 앱은 멈추지 않는다: 온라인 밑그림(네트워크) 탓이면 오프라인만으로,
            // 오프라인 스타일이 깨졌으면 바탕색 스타일로 바꾸고 안내만 띄운다
            onStyleFailed = {
                when (val s = style) {
                    is MapStyleSpec.Offline -> style = s.offlineOnly?.let { MapStyleSpec.Offline(it) } ?: MapStyleSpec.Fallback
                    is MapStyleSpec.Online -> style = MapStyleSpec.Fallback
                    else -> Unit
                }
            },
        )
    }

    // 지도 파일·스타일 찾기는 IO 스레드에서 (UI 스레드 블로킹 0)
    LaunchedEffect(night) { style = MapStyleSource.resolve(context.applicationContext, night) }

    val backgroundArgb by rememberUpdatedState(palette.background)
    LaunchedEffect(controller, style) {
        when (val s = style) {
            null -> Unit
            is MapStyleSpec.Offline -> controller.setStyle(s.json)
            is MapStyleSpec.Online -> controller.setStyle(s.json)
            MapStyleSpec.Fallback -> controller.setStyle(MapStyleSource.fallbackJson(backgroundArgb))
        }
    }
    LaunchedEffect(controller, palette) { controller.setPalette(palette) }
    LaunchedEffect(controller, fitInsets?.toList()) { controller.setFitInsets(fitInsets) }
    LaunchedEffect(controller, data) { controller.setData(data) }
    LaunchedEffect(controller, selectedKey) { controller.setSelection(selectedKey) }
    // 머문 곳 카드·주행 글·깃발: 지도 데이터에서 Rust가 만든 글을 꺼내(백그라운드) 그림으로 그려 얹는다.
    // 사진은 따로 받아 오고(같은 곳은 캐시), 오면 카드를 사진 든 것으로 다시 그린다 → 카드가 먼저 뜨고 사진이 채워진다.
    val marks by produceState<MapMarks?>(initialValue = null, data) {
        value = data?.let { d -> withContext(Dispatchers.Default) { parseMapMarks(d.geojson) } }
    }
    // 받은 사진은 핀(키 + 내 사진 주소)별로 기억한다: 핀 목록이 바뀌어도(다른 날, 내 사진이 뒤늦게 붙음) 그대로인 핀의 사진은 다시 받지 않고,
    // 바뀐 핀의 예전 사진(다른 날 · 장소 사진)은 버린다. 예전에는 "새로 받은 수 > 이미 가진 수"일 때만 바꿔서, 핀 목록이 바뀌면
    // 앞 목록의 사진이 남아 새 키에는 사진이 들어가지 않고 번호 칸만 남았다.
    val photoCache = remember { java.util.concurrent.ConcurrentHashMap<MapPhotoPin, Bitmap>() }
    val stayPhotos by produceState<Map<String, Bitmap>>(initialValue = emptyMap(), photoPins) {
        val pins = photoPins.distinctBy { it.key }
        photoCache.keys.retainAll(pins.toSet())
        val got = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
        photoCache.forEach { (pin, b) -> got[pin.key] = b }
        value = HashMap(got)
        if (pins.isEmpty()) return@produceState
        val repo = EntryPointAccessors.fromApplication(context.applicationContext, MapPhotoEntryPoint::class.java).photos()
        // 사진은 오는 대로 하나씩 채운다 (모두 기다리면 가장 느린 한 장 때문에 카드 사진이 늦게 뜬다).
        // 연달아 오면 카드 그림을 매번 다시 만들지 않게 잠깐([PHOTO_BATCH_MS]) 모아서 내보낸다.
        val flush = kotlinx.coroutines.flow.MutableStateFlow(0)
        val emitter = launch {
            flush.collect {
                if (got.size != value.size) value = HashMap(got)
                delay(PHOTO_BATCH_MS)
            }
        }
        val byKey = pins.associateBy { it.key }
        val load = suspend {
            withContext(Dispatchers.Default) {
                loadStayPhotos(context.applicationContext, repo, pins.filter { !got.containsKey(it.key) }, density) { k, b ->
                    got[k] = b
                    byKey[k]?.let { photoCache[it] = b }
                    flush.value++
                }
            }
        }
        load()
        // 앱을 막 켠 때처럼 사진 몇 장이 잠깐 안 받아지면 숫자 칸으로 남는다 → 빠진 게 있으면 잠시 뒤 한 번 더 (받은 건 캐시라 바로)
        if (got.size < pins.size) {
            delay(PHOTO_RETRY_MS)
            load()
        }
        emitter.cancel()
        value = HashMap(got)
    }
    // 내 사진: 찍은 곳마다 카메라 핀 (사진은 읽지 않는다)
    LaunchedEffect(controller, myPhotos) {
        picked = null
        controller.setMyPhotos(withContext(Dispatchers.Default) { buildMyPhotoIcons(myPhotos, density) })
    }
    // 고른 핀의 사진 말풍선: 그 한 장만 줄여 읽어 그린다 (배경)
    LaunchedEffect(controller, picked) {
        val p = picked
        if (p == null) {
            controller.setPhotoBubble(null)
            return@LaunchedEffect
        }
        val spots = p.first.mapNotNull { myPhotos.getOrNull(it) }
        val image = withContext(Dispatchers.Default) { buildPhotoBubble(context.applicationContext, spots, density, mapView.height) }
        controller.setPhotoBubble(image?.let { PhotoBubble(p.second, p.third, p.first, it) })
    }
    LaunchedEffect(controller, marks, stayPhotos, palette) {
        val m = marks
        controller.setIcons(m?.let { withContext(Dispatchers.Default) { buildMarkerIcons(it, stayPhotos, palette, density) } })
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        when (val s = style) {
            MapStyleSpec.Fallback -> MapNotice(
                stringResource(R.string.map_offline_missing),
                Modifier.align(Alignment.TopStart).padding(16.dp),
            )
            // 지도 데이터 출처 표시 (OSM·OpenFreeMap 라이선스). 지도를 그릴 때 항상 보인다.
            is MapStyleSpec.Offline, is MapStyleSpec.Online -> MapNotice(
                stringResource(
                    if (s is MapStyleSpec.Offline && s.offlineOnly == null) R.string.map_attribution else R.string.map_attribution_online,
                ),
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                small = true,
            )
            null -> Unit
        }
    }
    viewing?.let { v ->
        MyPhotoViewer(v.map { it.uri }, 0, stringResource(R.string.map_my_photo_title), onDismiss = { viewing = null })
    }
}

/** 지도 위 작은 안내 (출처 · 지도 파일 없음). 지도 위에서도 읽히게 반투명 바탕 + 지도 글자색. */
@Composable
private fun MapNotice(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val ink = mapInk(EodiTheme.colors.isDark)
    Text(
        text,
        style = if (small) MapNoticeSmall else EodiTheme.type.caption,
        color = ink.label.copy(alpha = 0.78f),
        maxLines = 1,
        modifier = modifier
            .background(ink.casing.copy(alpha = 0.86f), RoundedCornerShape(10.dp))
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
internal fun rememberMapViewWithLifecycle(
    context: Context,
    baseOptions: MapLibreMapOptions,
    loadingColor: Int,
    /** 기울이기·돌리기 손짓 (3D 미리보기 지도) */
    tiltAndRotate: Boolean = false,
    /** 처음 카메라. 스타일이 뜨자마자 다른 곳으로 옮길 화면은 여기서 바로 그곳을 주면 서울 타일을 헛받지 않는다. */
    initialCamera: CameraPosition? = null,
    /** 성능 기록([MapFrameProbe])에 붙일 이름 */
    label: String = "map",
): MapView {
    val mapView = remember {
        MapLibre.getInstance(context)
        val options = baseOptions
            .foregroundLoadColor(loadingColor)
            .camera(initialCamera ?: CameraPosition.Builder().target(InitialCenter).zoom(INITIAL_ZOOM).build())
            .localIdeographFontFamily(LOCAL_IDEOGRAPH_FONT)
            .logoEnabled(false)
            // 출처는 MapNotice로 직접 보여 준다 (차 안에서 i 버튼 → 외부 링크 대화상자를 띄우지 않는다)
            .attributionEnabled(false)
            .compassEnabled(false)
            .rotateGesturesEnabled(tiltAndRotate)
            .tiltGesturesEnabled(tiltAndRotate)
        MapView(context, options)
    }
    val frameProbe = remember(mapView) { MapFrameProbe.attach(mapView, label) }
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
            frameProbe?.dump()
            mapView.onDestroy()
        }
    }
    return mapView
}

/**
 * 지금 화면의 타일이 다 그려질 때까지(최대 [timeoutMs]) 기다린다. 다 그렸으면 true.
 * 3D 비행을 빈 타일 위에서 시작하지 않게, 첫 장면이 다 뜬 뒤 출발한다.
 */
internal suspend fun MapView.awaitFullyRendered(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) {
    suspendCancellableCoroutine { cont ->
        val listener = object : MapView.OnDidFinishRenderingFrameListener {
            override fun onDidFinishRenderingFrame(fully: Boolean, frameEncodingTime: Double, frameRenderingTime: Double) {
                if (!fully) return
                removeOnDidFinishRenderingFrameListener(this)
                if (cont.isActive) cont.resume(Unit)
            }
        }
        addOnDidFinishRenderingFrameListener(listener)
        cont.invokeOnCancellation { post { removeOnDidFinishRenderingFrameListener(listener) } }
    }
} != null

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MapPhotoEntryPoint {
    fun photos(): PlacePhotoRepository
}

/** 지도 사진이 빠졌을 때 다시 받기까지 기다리는 시간 */
private const val PHOTO_RETRY_MS = 3_000L

/** 사진이 연달아 올 때 카드 그림을 다시 만드는 최소 간격 */
private const val PHOTO_BATCH_MS = 120L

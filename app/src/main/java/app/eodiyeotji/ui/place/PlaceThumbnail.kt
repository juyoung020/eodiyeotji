package app.eodiyeotji.ui.place

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.eodiyeotji.core.PlacePhoto
import coil.compose.SubcomposeAsyncImage
import dagger.hilt.android.EntryPointAccessors

/** 사진 찾기 상태: 아직 묻는 중 / 사진 없음 / 첫 사진. */
private sealed interface Thumb {
    data object Loading : Thumb
    data object None : Thumb
    data class Found(val photo: PlacePhoto) : Thumb
}

/**
 * 정차한 곳 대표 사진 한 장 (작은 썸네일, 구글 Places 첫 사진). 모양·크기는 [modifier]로 정한다(clip 포함).
 * - 사진을 찾거나 받는 동안: [loading] (기본은 [fallback]). 사진 자리가 "깨진 화면"처럼 보이지 않게 은은하게 깜빡이는 자리를 권한다.
 * - 사진이 없거나 못 받으면: [fallback] (예: 핀 아이콘, 순서 번호 동그라미).
 * 같은 곳은 기기에 저장된 결과를 써서 다시 묻지 않는다 (PlacePhotoRepository).
 */
@Composable
fun PlaceThumbnail(
    name: String,
    address: String,
    lat: Double,
    lon: Double,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
    loading: @Composable () -> Unit = fallback,
) {
    val context = LocalContext.current
    val repo = remember { EntryPointAccessors.fromApplication(context.applicationContext, PhotoEntryPoint::class.java).photos() }
    val thumb by produceState<Thumb>(Thumb.Loading, name, lat, lon) {
        value = repo.photos(name, address, lat, lon).firstOrNull()?.let { Thumb.Found(it) } ?: Thumb.None
    }
    when (val t = thumb) {
        Thumb.Loading -> Box(modifier) { loading() }
        Thumb.None -> Box(modifier) { fallback() }
        is Thumb.Found -> SubcomposeAsyncImage(
            model = repo.imageRequest(t.photo),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            loading = { loading() },
            error = { fallback() },
            modifier = modifier,
        )
    }
}

/** 사진을 기다리는 동안의 자리: [color] 바탕이 천천히 밝아졌다 어두워진다 (불러오는 중이라는 표시). */
@Composable
fun PhotoLoadingPlaceholder(color: Color, modifier: Modifier = Modifier) {
    // 그리기 단계에서만 읽는다 → 사진 칸마다 매 프레임 재구성하지 않는다
    val pulse = rememberInfiniteTransition(label = "photo-loading").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(modifier.fillMaxSize().graphicsLayer { alpha = pulse.value }.background(color))
}

private const val PULSE_MS = 700

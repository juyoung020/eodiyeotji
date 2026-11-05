package app.eodiyeotji.ui.place

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.eodiyeotji.core.PlacePhoto
import coil.compose.SubcomposeAsyncImage
import dagger.hilt.android.EntryPointAccessors

/**
 * 정차한 곳 대표 사진 한 장 (작은 썸네일, 구글 Places 첫 사진). 모양·크기는 [modifier]로 정한다(clip 포함).
 * 사진을 받는 동안이나 사진이 없으면 [fallback]을 그린다 (예: 순서 번호 동그라미).
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
) {
    val context = LocalContext.current
    val repo = remember { EntryPointAccessors.fromApplication(context.applicationContext, PhotoEntryPoint::class.java).photos() }
    val first by produceState<PlacePhoto?>(null, name, lat, lon) { value = repo.photos(name, address, lat, lon).firstOrNull() }
    val photo = first
    if (photo == null) {
        Box(modifier) { fallback() }
        return
    }
    SubcomposeAsyncImage(
        model = repo.imageUrl(photo, THUMB_PX),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        loading = { fallback() },
        error = { fallback() },
        modifier = modifier,
    )
}

/** 썸네일 크기(긴 변 px): 화면에서 최대 약 96dp × 2배 밀도. */
private const val THUMB_PX = 240

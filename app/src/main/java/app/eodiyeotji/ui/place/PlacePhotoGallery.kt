package app.eodiyeotji.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import app.eodiyeotji.R
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eodiyeotji.core.PlacePhoto
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.place.PlacePhotoRepository
import coil.compose.AsyncImage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 들른 곳 대표 사진 (구글 지도에서 가게를 찾으면 뜨는 사진들). 옆으로 넘겨 본다.
 * 사진 곁에 올린 사람 이름과 Google 표시를 둔다 (구글 Places 약관). 사진이 없으면 안내 글. 사진을 누르면 크게 본다.
 */
@Composable
fun PlacePhotoGallery(stop: StopFact, modifier: Modifier = Modifier) =
    PlacePhotoGallery(stop.name, stop.address, stop.lat, stop.lon, modifier)

@Composable
fun PlacePhotoGallery(name: String, address: String, lat: Double, lon: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { EntryPointAccessors.fromApplication(context.applicationContext, PhotoEntryPoint::class.java).photos() }
    val photos by produceState<List<PlacePhoto>?>(null, name, lat, lon) { value = repo.photos(name, address, lat, lon) }
    val c = EodiTheme.colors
    var viewing by remember { mutableStateOf<Int?>(null) }
    Box(modifier.background(c.ink), contentAlignment = Alignment.Center) {
        val list = photos
        when {
            list == null -> app.eodiyeotji.ui.common.EodiLoader(size = 64.dp, kind = app.eodiyeotji.ui.common.LoaderKind.Tension, dark = true)
            list.isEmpty() -> Text(stringResource(R.string.place_no_photos), style = EodiTheme.type.label, color = c.onInkMuted)
            else -> {
                val pager = rememberPagerState { list.size }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                    AsyncImage(
                        model = repo.imageRequest(list[i]),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clickable(onClickLabel = stringResource(R.string.place_photo_enlarge)) { viewing = i },
                    )
                }
                Text(
                    credit(list[pager.currentPage]),
                    color = c.onInk,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, top = 12.dp, end = 96.dp, bottom = 12.dp)
                        .background(c.scrim, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(
                    "${pager.currentPage + 1} / ${list.size}",
                    color = c.onInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                        .background(c.scrim, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                viewing?.let { PlacePhotoViewer(list, it, name, repo, onDismiss = { viewing = null }) }
            }
        }
    }
}

/** 크게 보기: 검은 바탕에 사진 전체(자르지 않는다), 옆으로 넘기기, 닫기. 사진 곁에 출처를 둔다. */
@Composable
private fun PlacePhotoViewer(photos: List<PlacePhoto>, start: Int, title: String, repo: PlacePhotoRepository, onDismiss: () -> Unit) {
    val c = EodiTheme.colors
    val pager = rememberPagerState(initialPage = start.coerceIn(0, photos.lastIndex)) { photos.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss)) {
            HorizontalPager(pager, Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 72.dp)) { i ->
                AsyncImage(
                    model = repo.imageRequest(photos[i]),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                "$title · ${pager.currentPage + 1} / ${photos.size}",
                style = EodiTheme.type.bodyStrong,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 28.dp, end = 112.dp),
            )
            Text(
                credit(photos[pager.currentPage]),
                style = EodiTheme.type.meta,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(28.dp),
            )
            val close = stringResource(R.string.community_close)
            Box(
                Modifier.align(Alignment.TopEnd).padding(16.dp).size(64.dp).background(c.surface, CircleShape)
                    .clickable(onClick = onDismiss).semantics { contentDescription = close },
                contentAlignment = Alignment.Center,
            ) { Icon(EodiIcons.Close, null, tint = c.textPrimary, modifier = Modifier.size(32.dp)) }
        }
    }
}

/** 사진 출처 한 줄. 구글 사진은 올린 사람 + Google(약관), 기기에 넣어 둔 시연 사진은 저작자 · 라이선스만. */
private fun credit(photo: PlacePhoto): String = when {
    PlacePhotoRepository.isLocal(photo) -> "사진: ${photo.author}"
    photo.author.isBlank() -> "Google"
    else -> "사진: ${photo.author} · Google"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PhotoEntryPoint {
    fun photos(): PlacePhotoRepository
}


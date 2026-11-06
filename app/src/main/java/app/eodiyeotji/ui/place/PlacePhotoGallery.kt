package app.eodiyeotji.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.eodiyeotji.R
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
 * 사진 곁에 올린 사람 이름과 Google 표시를 둔다 (구글 Places 약관). 사진이 없으면 안내 글.
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
    Box(modifier.background(c.ink), contentAlignment = Alignment.Center) {
        val list = photos
        when {
            list == null -> CircularProgressIndicator(color = c.onInk)
            list.isEmpty() -> Text(stringResource(R.string.place_no_photos), style = EodiTheme.type.label, color = c.onInkMuted)
            else -> {
                val pager = rememberPagerState { list.size }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                    AsyncImage(
                        model = repo.imageRequest(list[i]),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val author = list[pager.currentPage].author
                Text(
                    if (author.isBlank()) "Google" else "사진: $author · Google",
                    color = c.onInk,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
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
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PhotoEntryPoint {
    fun photos(): PlacePhotoRepository
}


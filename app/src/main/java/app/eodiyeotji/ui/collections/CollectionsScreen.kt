package app.eodiyeotji.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.eodiyeotji.R
import app.eodiyeotji.ui.biz.BizViewModel
import app.eodiyeotji.ui.common.pressScale
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.recap.RecapMode
import app.eodiyeotji.ui.recap.RecapViewModel
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme

/**
 * 모아보기 타일 색: 타일마다 또렷한 색 하나(위에서 아래로 짙어지는 그라디언트), 글자 · 아이콘은 흰색.
 * 아래쪽(글자가 놓이는 쪽) 색은 흰 글자와 대비 4.5:1 이상. 어두운 화면은 한 단계 깊은 색.
 */
@Immutable
private data class TileColors(val top: Color, val bottom: Color)

private object TilePalette {
    val ocean = TileColors(Color(0xFF0284C7), Color(0xFF075985))
    val oceanDark = TileColors(Color(0xFF0369A1), Color(0xFF0C3B5C))
    val blue = TileColors(Color(0xFF3B6CF6), Color(0xFF1D4ED8))
    val blueDark = TileColors(Color(0xFF2B55D6), Color(0xFF1E3A8A))
    val violet = TileColors(Color(0xFF7C4DEB), Color(0xFF5B21B6))
    val violetDark = TileColors(Color(0xFF6236C9), Color(0xFF431A86))
    val teal = TileColors(Color(0xFF14A098), Color(0xFF0F766E))
    val tealDark = TileColors(Color(0xFF0F807A), Color(0xFF0B524D))
    val coral = TileColors(Color(0xFFF26B3A), Color(0xFFC2410C))
    val coralDark = TileColors(Color(0xFFD9542A), Color(0xFF9A3412))
}

/** 모아보기 타일 하나. */
@Immutable
private data class CollectionTile(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val colors: TileColors,
    val onClick: () -> Unit,
)

/**
 * 차 홈 "모아보기" 탭: 기억 지도(넓은 타일) · 이번 달 요약 · 올해 결산 · 여행 모음 · 출장 관리 색 타일 (한 번 누르면 바로 그 화면).
 * 요약 · 결산 · 기억 지도 · 출장 창은 내 기록 화면(TimelineRoute)이 그리므로 [onOpenRecords]로 내 기록 탭으로 옮긴 뒤 연다.
 * 주행 중에는 타일을 그리지 않고 "정차 중에 볼 수 있어요" 한 줄만.
 */
@Composable
fun CollectionsRoute(driving: Boolean, onOpenTrips: () -> Unit, onOpenRecords: () -> Unit) {
    // 내 기록 화면과 같은 인스턴스 (같은 화면 주인 · 같은 키)
    val recap: RecapViewModel = hiltViewModel()
    val biz: BizViewModel = hiltViewModel()
    val dark = EodiTheme.colors.isDark
    val tripsTitle = stringResource(R.string.trips_entry)
    val monthTitle = stringResource(R.string.collections_month_title)
    val yearTitle = stringResource(R.string.recap_year_entry)
    val mapTitle = stringResource(R.string.memory_map_entry)
    val bizTitle = stringResource(R.string.biz_entry)
    val tiles = remember(dark, tripsTitle, monthTitle, yearTitle, mapTitle, bizTitle, recap, biz) {
        val p = TilePalette
        listOf(
            CollectionTile("memory-map", EodiIcons.Map, mapTitle, if (dark) p.oceanDark else p.ocean) {
                onOpenRecords(); recap.openMemoryMap()
            },
            CollectionTile("month", EodiIcons.Calendar, monthTitle, if (dark) p.blueDark else p.blue) {
                onOpenRecords(); recap.openRecap(RecapMode.MONTH)
            },
            CollectionTile("year", EodiIcons.Sparkle, yearTitle, if (dark) p.violetDark else p.violet) {
                onOpenRecords(); recap.openRecap(RecapMode.YEAR)
            },
            CollectionTile("trips", EodiIcons.Pin, tripsTitle, if (dark) p.tealDark else p.teal, onOpenTrips),
            CollectionTile("biz", CommunityIcons.Clock, bizTitle, if (dark) p.coralDark else p.coral) {
                onOpenRecords(); biz.open()
            },
        )
    }
    CollectionsScreen(tiles = tiles, driving = driving)
}

/** 벤토 배치: 첫 타일은 두 칸 넓게, 나머지는 두 개씩. 화면 높이를 줄마다 나눠 빈 칸 없이 채운다. */
@Composable
private fun CollectionsScreen(tiles: List<CollectionTile>, driving: Boolean) {
    val c = EodiTheme.colors
    val sp = EodiTheme.space
    Column(Modifier.fillMaxSize().background(c.background)) {
        if (driving) {
            Box(Modifier.fillMaxSize().padding(sp.page), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.collections_driving_locked), style = EodiTheme.type.drivingBody, color = c.textSecondary)
            }
            return@Column
        }
        Column(Modifier.fillMaxSize().padding(sp.page), verticalArrangement = Arrangement.spacedBy(sp.l)) {
            Text(stringResource(R.string.collections_title), style = EodiTheme.type.title, color = c.textPrimary)
            val hero = tiles.first()
            key(hero.key) { ColorTile(hero, hero = true, modifier = Modifier.fillMaxWidth().weight(1.15f)) }
            tiles.drop(1).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(sp.l)) {
                    pair.forEach { t ->
                        key(t.key) { ColorTile(t, hero = false, modifier = Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorTile(tile: CollectionTile, hero: Boolean, modifier: Modifier) {
    val sp = EodiTheme.space
    val press = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(sp.radiusL)
    val brush = remember(tile.colors) { Brush.verticalGradient(listOf(tile.colors.top, tile.colors.bottom)) }
    val deco = if (hero) 280.dp else 190.dp
    Box(
        modifier
            .pressScale(press)
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(brush)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = tile.onClick),
    ) {
        // 오른쪽 아래로 넘치는 크고 흐린 아이콘 (깊이감)
        Icon(
            tile.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.14f),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = deco * 0.18f, y = deco * 0.22f).size(deco),
        )
        Icon(
            tile.icon, contentDescription = null, tint = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(sp.l).size(if (hero) 64.dp else 56.dp),
        )
        Text(
            tile.title, style = EodiTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(sp.l),
        )
    }
}

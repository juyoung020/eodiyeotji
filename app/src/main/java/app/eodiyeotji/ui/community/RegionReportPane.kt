package app.eodiyeotji.ui.community

import app.eodiyeotji.ui.common.PressSurface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.rust.RegionActivity
import app.eodiyeotji.core.rust.TourismPlace
import app.eodiyeotji.core.rust.TourismReport
import app.eodiyeotji.ui.recap.Load
import app.eodiyeotji.ui.theme.EodiTheme

/** 이 폭 이상이면 왼쪽 지역 목록 + 오른쪽 리포트, 좁으면 위에 지역 칩 줄. */
private val TwoPane = 1100.dp

/**
 * 지역 리포트 (커뮤니티 하위 탭): 사람들이 올린 코스 후기 · 장소 리뷰를 지역별로 묶어 지자체 · 관광지 · 가게에 보여 줄 한 장.
 * 숫자 · 순위 · 제휴 제안은 Rust(`tourism.rs`)가 만들고 여기서는 그리기만 한다.
 */
@Composable
fun RegionReportPane(viewModel: RegionReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoPane
        when (val regions = state.regions) {
            Load.Loading -> LoadingBox(Modifier.fillMaxSize())
            Load.Failed -> CenterMessage(stringResource(R.string.region_failed), Modifier.fillMaxSize())
            is Load.Ready -> if (regions.value.isEmpty()) {
                CenterMessage(stringResource(R.string.region_empty), Modifier.fillMaxSize())
            } else if (wide) {
                Row(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    // 지역이 많아도 보이는 줄만 그린다
                    LazyColumn(
                        Modifier.width(360.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        items(regions.value, key = { it.region }) { r -> RegionRow(r, r.region == state.selected) { viewModel.select(r.region) } }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                        ReportBody(state.report, wide = true)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(regions.value, key = { it.region }) { r ->
                            RegionRow(r, r.region == state.selected, Modifier.width(260.dp)) { viewModel.select(r.region) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    ReportBody(state.report, wide = false)
                }
            }
        }
    }
}

@Composable
private fun RegionRow(r: RegionActivity, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = EodiTheme.colors
    PressSurface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) c.accentSoft else c.surface,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(r.region, style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold, color = if (selected) c.accent else c.textPrimary)
            Text(
                stringResource(R.string.region_row, r.courses.toInt(), r.reviews.toInt(), "%,d".format(r.hearts.toInt())),
                style = EodiTheme.type.meta,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun ReportBody(report: Load<TourismReport>, wide: Boolean) {
    when (report) {
        Load.Loading -> LoadingBox(Modifier.fillMaxWidth().padding(top = 80.dp))
        Load.Failed -> CenterMessage(stringResource(R.string.region_failed), Modifier.fillMaxWidth().padding(top = 48.dp))
        is Load.Ready -> Report(report.value, wide)
    }
}

@Composable
private fun Report(r: TourismReport, wide: Boolean) {
    val c = EodiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text(stringResource(R.string.region_report_title, r.region), style = EodiTheme.type.title, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.region_report_hint), style = EodiTheme.type.meta, color = c.textSecondary)
        }
        // 한눈에 숫자
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi("${r.courseCount}", stringResource(R.string.region_kpi_courses), Modifier.weight(1f))
            Kpi("${r.reviewCount}", stringResource(R.string.region_kpi_reviews), Modifier.weight(1f))
            Kpi("${r.contributors}", stringResource(R.string.region_kpi_people), Modifier.weight(1f))
            Kpi("%,d".format(r.totalHearts.toInt()), stringResource(R.string.region_kpi_hearts), Modifier.weight(1f))
            Kpi(if (r.reviewCount > 0u) "%.1f".format(r.avgRating) else "-", stringResource(R.string.region_kpi_rating), Modifier.weight(1f))
            Kpi("${r.offerCount}", stringResource(R.string.region_kpi_offers), Modifier.weight(1f), accent = true)
        }
        // 제휴 제안 (맨 위에: 이 화면이 하려는 말)
        if (r.ideas.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(24.dp), color = c.accentSoft, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.region_ideas), style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold, color = c.accent)
                    r.ideas.forEachIndexed { i, idea ->
                        Row {
                            Text("${i + 1}", style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, color = c.accent, modifier = Modifier.width(28.dp))
                            Text(idea, style = EodiTheme.type.body, color = c.textPrimary)
                        }
                    }
                }
            }
        }
        SeasonBars(r)
        val courses: @Composable (Modifier) -> Unit = { m ->
            Section(stringResource(R.string.region_top_courses), m) {
                r.topCourses.forEachIndexed { i, course ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Rank(i + 1)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(course.title, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(course.stops.joinToString(" → "), style = EodiTheme.type.meta, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("♥ ${course.hearts}", style = EodiTheme.type.meta, fontWeight = FontWeight.Bold, color = c.accent)
                    }
                }
            }
        }
        val places: @Composable (Modifier) -> Unit = { m ->
            Section(stringResource(R.string.region_top_places), m) {
                r.topPlaces.forEachIndexed { i, p -> PlaceRow(i + 1, p) }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                courses(Modifier.weight(1f))
                places(Modifier.weight(1f))
            }
        } else {
            courses(Modifier.fillMaxWidth())
            places(Modifier.fillMaxWidth())
        }
        if (r.keywords.isNotEmpty()) {
            Section(stringResource(R.string.region_keywords), Modifier.fillMaxWidth()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    r.keywords.forEach { k ->
                        Text(k, style = EodiTheme.type.meta, fontWeight = FontWeight.SemiBold, color = c.textPrimary,
                            modifier = Modifier.clip(CircleShape).background(c.surfaceHigh).padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier, accent: Boolean = false) {
    val c = EodiTheme.colors
    Surface(shape = RoundedCornerShape(20.dp), color = if (accent) c.accentSoft else c.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(value, style = EodiTheme.type.title, fontWeight = FontWeight.Bold, color = if (accent) c.accent else c.textPrimary, maxLines = 1)
            Text(label, style = EodiTheme.type.meta, color = c.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = EodiTheme.colors.surface, modifier = modifier) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = EodiTheme.type.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** 계절별 방문 막대 (코스 후기 · 리뷰의 다녀온 날). */
@Composable
private fun SeasonBars(r: TourismReport) {
    val c = EodiTheme.colors
    Section(stringResource(R.string.region_seasons), Modifier.fillMaxWidth()) {
        r.seasons.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(s.name, style = EodiTheme.type.bodyStrong, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f).height(22.dp).clip(CircleShape).background(c.surfaceHigh)) {
                    if (s.share > 0f) Box(Modifier.fillMaxWidth(s.share).fillMaxHeight().clip(CircleShape).background(c.accent))
                }
                Text("${(s.share * 100).toInt()}%", style = EodiTheme.type.meta, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp).padding(start = 12.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlaceRow(rank: Int, p: TourismPlace) {
    val c = EodiTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Rank(rank)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(64.dp)) { StopPhotoBox(p.name, p.address, p.lat, p.lon, null, Modifier.fillMaxSize(), radius = 14.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = EodiTheme.type.bodyStrong, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val rating = if (p.reviewCount > 0u) " · ★ %.1f".format(p.avgRating) else ""
            Text(stringResource(R.string.region_place_line, p.courseCount.toInt(), p.reviewCount.toInt()) + rating, style = EodiTheme.type.meta, color = c.textSecondary)
        }
        Text(
            stringResource(if (p.hasOffer) R.string.region_offer_yes else R.string.region_offer_no),
            style = EodiTheme.type.meta,
            fontWeight = FontWeight.Bold,
            color = if (p.hasOffer) c.accent else c.textTertiary,
            modifier = Modifier.clip(CircleShape).background(if (p.hasOffer) c.accentSoft else c.surfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Rank(n: Int) {
    val c = EodiTheme.colors
    Box(Modifier.size(36.dp).clip(CircleShape).background(if (n == 1) c.accent else c.surfaceHigh), contentAlignment = Alignment.Center) {
        Text("$n", style = EodiTheme.type.meta, fontWeight = FontWeight.Bold, color = if (n == 1) c.onAccent else c.textPrimary)
    }
}

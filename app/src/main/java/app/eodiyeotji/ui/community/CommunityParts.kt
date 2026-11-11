package app.eodiyeotji.ui.community

import app.eodiyeotji.ui.gallery.MyPhotoImage
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.heading
import app.eodiyeotji.ui.theme.EodiIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.core.Visit
import app.eodiyeotji.ui.place.PhotoLoadingPlaceholder
import app.eodiyeotji.ui.place.PlaceThumbnail
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.common.pressScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/* 커뮤니티(게시판 · 드라이브 코스 · 여행 리뷰)가 같이 쓰는 작은 부품. */

/** 커뮤니티 아이콘 (24 격자, 선 2.25, 둥근 끝 — EodiIcons와 같은 규칙). */
object CommunityIcons {
    private val Ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 2.25f, b: PathBuilder.() -> Unit) =
        path(stroke = Ink, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = b)

    /** 구름 윤곽 (24 칸 기준, [dy]만큼 위아래로 옮김) */
    private fun PathBuilder.cloud(dy: Float = 0f) {
        moveTo(7f, 18f + dy)
        arcToRelative(4f, 4f, 0f, false, true, -0.6f, -7.95f)
        arcToRelative(6f, 6f, 0f, false, true, 11.4f, 1.2f)
        arcToRelative(3.4f, 3.4f, 0f, false, true, -0.3f, 6.75f)
        close()
    }

    private fun PathBuilder.heart() {
        moveTo(12f, 20.5f)
        curveTo(12f, 20.5f, 3f, 15f, 3f, 8.8f)
        curveTo(3f, 6f, 5.2f, 3.8f, 7.9f, 3.8f)
        curveTo(9.6f, 3.8f, 11.1f, 4.7f, 12f, 6.1f)
        curveTo(12.9f, 4.7f, 14.4f, 3.8f, 16.1f, 3.8f)
        curveTo(18.8f, 3.8f, 21f, 6f, 21f, 8.8f)
        curveTo(21f, 15f, 12f, 20.5f, 12f, 20.5f)
        close()
    }

    private fun PathBuilder.star() {
        moveTo(12f, 2.8f); lineTo(14.8f, 8.7f); lineTo(21.2f, 9.4f); lineTo(16.4f, 13.8f); lineTo(17.7f, 20.2f)
        lineTo(12f, 17f); lineTo(6.3f, 20.2f); lineTo(7.6f, 13.8f); lineTo(2.8f, 9.4f); lineTo(9.2f, 8.7f); close()
    }

    val Heart: ImageVector by lazy { icon("Heart") { stroke(2.2f) { heart() } } }
    val HeartFilled: ImageVector by lazy { icon("HeartFilled") { path(fill = Ink) { heart() } } }
    val Star: ImageVector by lazy { icon("Star") { path(fill = Ink) { star() } } }
    val StarOutline: ImageVector by lazy { icon("StarOutline") { stroke(1.8f) { star() } } }

    /** 커뮤니티 (사람 둘) */
    val People: ImageVector by lazy {
        icon("People") {
            stroke(2.2f) {
                moveTo(9f, 11f); arcToRelative(3.5f, 3.5f, 0f, true, true, 0f, -7f); arcToRelative(3.5f, 3.5f, 0f, true, true, 0f, 7f); close()
                moveTo(2.5f, 20f); curveTo(2.5f, 16.4f, 5.4f, 14f, 9f, 14f); curveTo(12.6f, 14f, 15.5f, 16.4f, 15.5f, 20f)
                moveTo(16f, 4.3f); arcToRelative(3.4f, 3.4f, 0f, false, true, 0f, 6.4f)
                moveTo(18f, 14.4f); curveTo(20.1f, 15.2f, 21.5f, 17.3f, 21.5f, 20f)
            }
        }
    }

    /** 내 기록 (시계) */
    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroke(2.2f) {
                moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, true, 0f, 18f); arcToRelative(9f, 9f, 0f, true, true, 0f, -18f); close()
                moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15.2f, 14f)
            }
        }
    }

    /** 댓글 (말풍선) */
    val Chat: ImageVector by lazy {
        icon("Chat") {
            stroke(2.1f) {
                moveTo(4f, 5.5f); lineTo(20f, 5.5f); lineTo(20f, 16f); lineTo(10f, 16f); lineTo(5.5f, 20f); lineTo(5.5f, 16f)
                lineTo(4f, 16f); close()
            }
        }
    }

    /** 쓰기 (연필) */
    val Pen: ImageVector by lazy {
        icon("Pen") {
            stroke(2.2f) { moveTo(4f, 20f); lineTo(4.8f, 15.6f); lineTo(15.8f, 4.6f); lineTo(19.4f, 8.2f); lineTo(8.4f, 19.2f); close() }
        }
    }

    /** 올리기 (위 화살표 + 받침) */
    val Upload: ImageVector by lazy {
        icon("Upload") {
            stroke(2.3f) { moveTo(12f, 16f); lineTo(12f, 4f); moveTo(7f, 9f); lineTo(12f, 4f); lineTo(17f, 9f); moveTo(4.5f, 20f); lineTo(19.5f, 20f) }
        }
    }


    /** 길 (굽은 도로) */
    val Road: ImageVector by lazy {
        icon("Road") {
            stroke(2.2f) {
                moveTo(5f, 21f); curveTo(5f, 15f, 19f, 15f, 19f, 9f); curveTo(19f, 6f, 16f, 3f, 12f, 3f)
            }
            path(fill = Ink) {
                moveTo(12f, 1f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f); close()
            }
        }
    }

    /** 재생 (미리보기 비행) */
    val Play: ImageVector by lazy { icon("Play") { path(fill = Ink) { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() } } }

    /** 멈춤 */
    val Stop: ImageVector by lazy { icon("Stop") { path(fill = Ink) { moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close() } } }

    /** 구름 (흐림·안개) */
    val Cloud: ImageVector by lazy {
        icon("Cloud") {
            stroke(2f) { cloud() }
        }
    }

    /** 비 (구름 + 빗줄기: 비·이슬비·소나기·뇌우) */
    val Rain: ImageVector by lazy {
        icon("Rain") {
            stroke(2f) {
                cloud(dy = -3f)
                moveTo(8.5f, 17.5f); lineTo(7.5f, 20.5f); moveTo(12.5f, 17.5f); lineTo(11.5f, 20.5f); moveTo(16.5f, 17.5f); lineTo(15.5f, 20.5f)
            }
        }
    }

    /** 눈 (구름 + 눈송이 점) */
    val Snow: ImageVector by lazy {
        icon("Snow") {
            stroke(2f) { cloud(dy = -3f) }
            path(fill = Ink) {
                for (x in listOf(8f, 12f, 16f)) {
                    moveTo(x, 18f); arcToRelative(1.2f, 1.2f, 0f, true, true, 0f, 2.4f); arcToRelative(1.2f, 1.2f, 0f, true, true, 0f, -2.4f); close()
                }
            }
        }
    }

    /** 날씨 (해: 맑음·구름 조금) */
    val Sun: ImageVector by lazy {
        icon("Sun") {
            stroke(2f) {
                moveTo(12f, 8f); arcToRelative(4f, 4f, 0f, true, true, 0f, 8f); arcToRelative(4f, 4f, 0f, true, true, 0f, -8f); close()
                moveTo(12f, 2.5f); lineTo(12f, 4.5f); moveTo(12f, 19.5f); lineTo(12f, 21.5f)
                moveTo(2.5f, 12f); lineTo(4.5f, 12f); moveTo(19.5f, 12f); lineTo(21.5f, 12f)
                moveTo(5.3f, 5.3f); lineTo(6.7f, 6.7f); moveTo(17.3f, 17.3f); lineTo(18.7f, 18.7f)
                moveTo(5.3f, 18.7f); lineTo(6.7f, 17.3f); moveTo(17.3f, 6.7f); lineTo(18.7f, 5.3f)
            }
        }
    }
}

/**
 * 하트 버튼 (76dp 터치). 누른 상태면 채운 빨간 하트. 숫자는 옆에.
 * 누르면 바로 바뀐 것처럼 보이고(낙관적), 실제 저장은 ViewModel이 Rust로 한다.
 */
@Composable
fun HeartButton(hearts: Int, hearted: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = EodiTheme.colors
    val desc = stringResource(R.string.community_hearts_description, hearts) + ", " +
        stringResource(if (hearted) R.string.community_heart_remove else R.string.community_heart_add)
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (hearted) c.heartSoft else c.surfaceHigh,
        contentColor = if (hearted) c.heart else c.textSecondary,
        interactionSource = press,
        modifier = modifier.pressScale(press).heightIn(min = if (compact) 64.dp else 76.dp).semantics { contentDescription = desc },
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 18.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // 하트를 켜는 순간 한 번 톡 커졌다 돌아온다 (눌렀다는 확실한 반응)
            val pop = remember { androidx.compose.animation.core.Animatable(1f) }
            var first by remember { androidx.compose.runtime.mutableStateOf(true) }
            LaunchedEffect(hearted) {
                if (first) { first = false; return@LaunchedEffect }
                if (hearted) {
                    pop.snapTo(1.3f)
                    pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f))
                }
            }
            Icon(
                if (hearted) CommunityIcons.HeartFilled else CommunityIcons.Heart,
                null,
                Modifier.size(if (compact) 26.dp else 30.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            )
            Spacer(Modifier.width(8.dp))
            Text(hearts.toString(), style = EodiTheme.type.label, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * 누를 수 없는 하트 수 ([HeartButton]과 같은 아이콘 · 색). 남의 글 미리보기, 에이전트 추천 카드 등.
 * [large]는 주행 중 큰 카드용 (멀리서도 읽히는 크기).
 */
@Composable
fun HeartCount(hearts: Int, hearted: Boolean, modifier: Modifier = Modifier, large: Boolean = false) {
    val c = EodiTheme.colors
    val desc = stringResource(R.string.community_hearts_description, hearts)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (hearted) CommunityIcons.HeartFilled else CommunityIcons.Heart, null, tint = c.heart, modifier = Modifier.size(if (large) 36.dp else 26.dp))
        Spacer(Modifier.width(if (large) 10.dp else 8.dp))
        Text(
            hearts.toString(),
            style = if (large) EodiTheme.type.title else EodiTheme.type.label,
            fontWeight = FontWeight.Bold,
            color = if (large) c.heart else c.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * 고르는 칩 줄 (분절 버튼). 정렬(인기순/최신순), 하위 탭 등에 쓴다. 항목 하나 높이 64dp 이상.
 */
@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, big: Boolean = false) {
    val c = EodiTheme.colors
    Row(
        modifier.background(c.surfaceHigh, CircleShape).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) c.surface else c.surface.copy(alpha = 0f), tween(EodiTheme.motion.medium), label = "choice")
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .heightIn(min = 76.dp) // 정렬 · 탭 칩도 Pleos 터치 크기
                    .padding(horizontal = if (big) 30.dp else 24.dp)
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = if (big) EodiTheme.type.bodyStrong else EodiTheme.type.label,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) c.accent else c.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 켜고 끄는 알약 (예: "내 근처 30km", 리뷰의 "누구와"). 켜지면 파란 테두리 · 옅은 파랑 · 체크.
 * [role]은 읽기 도구에 알릴 종류 (켜기/끄기 = Switch, 여러 개 중 고르기 = Checkbox). [minHeight]는 누르는 높이.
 */
@Composable
fun TogglePill(
    text: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Switch,
    minHeight: Dp = 64.dp,
) {
    val c = EodiTheme.colors
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = CircleShape,
        color = if (on) c.accentSoft else c.surfaceHigh,
        contentColor = when {
            on -> c.accent
            enabled -> c.textSecondary
            else -> c.textTertiary
        },
        border = BorderStroke(2.dp, if (on) c.accent else Color.Transparent),
        modifier = modifier.heightIn(min = minHeight).semantics { this.role = role; selected = on },
    ) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (on) {
                Icon(EodiIcons.Check, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = EodiTheme.type.label, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}

/**
 * 주된 동작 버튼 (파란 알약, 76dp). [large]는 운전 중 큰 버튼 (높이 144dp · 큰 글자 · 큰 아이콘).
 */
@Composable
fun PrimaryPill(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, large: Boolean = false) {
    val c = EodiTheme.colors
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) c.accent else c.surfaceHigh,
        contentColor = if (enabled) c.onAccent else c.textTertiary,
        interactionSource = press,
        modifier = modifier.pressScale(press).let { m -> if (large) m.height(LargePillHeight) else m.heightIn(min = 76.dp) },
    ) {
        PillContent(text, icon, iconTint = null, large = large, bold = true, horizontal = 30.dp)
    }
}

/** 보조 버튼 (회색 알약, 76dp). [large]는 운전 중 큰 버튼 (높이 144dp). */
@Composable
fun SecondaryPill(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, large: Boolean = false) {
    val c = EodiTheme.colors
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = c.surfaceHigh,
        contentColor = if (enabled) c.textPrimary else c.textTertiary,
        interactionSource = press,
        modifier = modifier.pressScale(press).let { m -> if (large) m.height(LargePillHeight) else m.heightIn(min = 76.dp) },
    ) {
        PillContent(text, icon, iconTint = if (enabled && !large) c.accent else null, large = large, bold = true, horizontal = 28.dp, normalStyle = EodiTheme.type.label)
    }
}

private val LargePillHeight = 144.dp

@Composable
private fun PillContent(
    text: String,
    icon: ImageVector?,
    iconTint: Color?,
    large: Boolean,
    bold: Boolean,
    horizontal: Dp,
    normalStyle: androidx.compose.ui.text.TextStyle = EodiTheme.type.bodyStrong,
) {
    Row(
        Modifier.padding(horizontal = if (large) 36.dp else horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            val size = if (large) 48.dp else 28.dp
            if (iconTint != null) Icon(icon, null, Modifier.size(size), tint = iconTint) else Icon(icon, null, Modifier.size(size))
            Spacer(Modifier.width(if (large) 20.dp else 12.dp))
        }
        Text(
            text,
            style = if (large) EodiTheme.type.drivingTitle else normalStyle,
            fontWeight = if (bold) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 두 줄 알약 버튼: 굵은 [title] + 작은 [detail] (예: "이 코스로 안내" / "3곳 · 42km", "여기만 가기" / 장소 이름).
 * [primary]면 파란 바탕, 아니면 회색 바탕에 파란 아이콘. [running]이면 아이콘 자리에 도는 고리.
 * [large]는 제목을 한 단계 크게 (안내 제안처럼 한눈에 읽어야 하는 버튼).
 */
@Composable
fun TwoLinePill(
    title: String,
    detail: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    running: Boolean = false,
    large: Boolean = false,
    detailLines: Int = 1,
) {
    val c = EodiTheme.colors
    val content = if (primary) c.onAccent else c.textPrimary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(EodiTheme.space.radiusL),
        color = if (primary) c.accent else c.surfaceHigh,
        contentColor = content,
        modifier = modifier.heightIn(min = if (large) 96.dp else 88.dp),
    ) {
        Row(
            Modifier.padding(horizontal = if (large) 28.dp else 24.dp, vertical = if (large) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconSize = if (large) 32.dp else 28.dp
            if (running) {
                CircularProgressIndicator(strokeWidth = 3.dp, color = content, modifier = Modifier.size(iconSize))
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(iconSize), tint = if (primary) content else c.accent)
            }
            if (running || icon != null) Spacer(Modifier.width(if (large) 20.dp else 14.dp))
            Column(Modifier.weight(1f, fill = large)) {
                Text(
                    title,
                    style = if (large) EodiTheme.type.drivingBody else EodiTheme.type.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!detail.isNullOrBlank()) {
                    // 파랑 바탕 위 작은 글자는 굵게 · 흐리지 않게 (다크 파랑 위 흰 글자 대비 3.5:1 → 굵은 글자 기준 AA)
                    Text(
                        detail,
                        style = SubTextStyle,
                        fontWeight = if (primary) FontWeight.Bold else null,
                        color = if (primary) content else c.textSecondary,
                        maxLines = detailLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 보조 글자(설명 · 분류 · 요약 줄): Pleos 가장 작은 글자 크기(20sp)에 줄 간격만 넉넉히. */
val SubTextStyle: androidx.compose.ui.text.TextStyle
    @Composable @ReadOnlyComposable get() = EodiTheme.type.bodyStrong.copy(fontSize = 20.sp, lineHeight = 28.sp)

/** 순위 동그라미 (드라이브 코스 카드 · 에이전트 추천). [focused]면 파란 바탕 (지금 안내 제안 중인 것). */
@Composable
fun RankBadge(rank: Int, focused: Boolean, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val c = EodiTheme.colors
    Box(
        modifier.size(size).background(if (focused) c.accent else c.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rank.toString(),
            style = if (size >= 52.dp) EodiTheme.type.title else EodiTheme.type.label,
            fontWeight = FontWeight.Bold,
            color = if (focused) c.onAccent else c.textPrimary,
        )
    }
}

/**
 * 둥근 아이콘 버튼 (76dp, 뒤로 · 닫기 · 앞뒤 넘기기 · 말하기). [filled]면 파란 바탕 · 흰 아이콘.
 * 바탕색이 화면에 묻히면 [containerColor]로 바꾼다 (예: 회색 바탕 위에서는 흰 카드색). 못 누를 때는 흐리게.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    iconSize: Dp = 32.dp,
    iconModifier: Modifier = Modifier,
) {
    val c = EodiTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = containerColor ?: if (filled) c.accent else c.surfaceHigh,
        contentColor = when {
            !enabled -> c.textTertiary
            contentColor != null -> contentColor
            filled -> c.onAccent
            else -> c.textPrimary
        },
        modifier = modifier.size(EodiTheme.space.touch),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, iconModifier.size(iconSize)) }
    }
}

/** 구역 머리말 (작은 회색 대문자 줄, 읽기 도구에는 제목). [icon]이 있으면 앞에 금색 아이콘 (결산의 반짝임). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = EodiTheme.colors
    Row(modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.memoryInk, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = EodiTheme.type.eyebrow, color = c.textTertiary)
    }
}

/** 정보 칩의 색 결: [Accent] = 옅은 파랑 · 파란 글자 (태그 · 키워드), [Quiet] = 옅은 회색 · 회색 글자 (거리 · 날짜 같은 정보). */
enum class ChipTone { Accent, Quiet }

/** "✓ 직접 달린 기록": 코스는 내 기록에서만 올릴 수 있으니(올리기 창), 모든 코스 카드에 붙는다. */
@Composable
fun FromRecordChip(modifier: Modifier = Modifier) {
    val c = EodiTheme.colors
    val desc = stringResource(R.string.community_from_record_description)
    InfoChip(
        stringResource(R.string.community_from_record),
        modifier.semantics { contentDescription = desc },
        tone = ChipTone.Accent,
        icon = { Icon(EodiIcons.Check, null, tint = c.accent, modifier = Modifier.size(20.dp)) },
    )
}

/**
 * 보여 주기만 하는 작은 알약 칩 (누르지 않는다). 태그("#해안도로")는 [text]에 #을 붙여 넘긴다.
 * [icon]이 있으면 글자 앞에 작은 아이콘.
 */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, tone: ChipTone = ChipTone.Quiet, icon: (@Composable () -> Unit)? = null) {
    val c = EodiTheme.colors
    val (bg, fg) = when (tone) {
        ChipTone.Accent -> c.accentSoft to c.accent
        ChipTone.Quiet -> c.surfaceHigh to c.textSecondary
    }
    Row(
        modifier.background(bg, CircleShape).padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = EodiTheme.type.caption, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 별점 (채운 별 + 숫자). */
@Composable
fun RatingStars(rating: Int, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val c = EodiTheme.colors
    val desc = stringResource(R.string.review_rating_description, rating)
    Row(modifier.semantics { contentDescription = desc }, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            Icon(if (i < rating) CommunityIcons.Star else CommunityIcons.StarOutline, null, Modifier.size(size), tint = if (i < rating) c.star else c.textTertiary)
        }
    }
}

/**
 * 들른 곳 사진. 받는 동안은 옅은 자리가 은은하게 깜빡이고(불러오는 중), 사진이 없으면 핀 아이콘.
 * 순서 번호는 부르는 쪽이 [OrderDot]으로 사진 위에 얹는다 (자리 안에 번호를 또 그리지 않는다).
 */
@Composable
fun StopPhoto(
    name: String, address: String, lat: Double, lon: Double, order: Int?, size: Dp, modifier: Modifier = Modifier, radius: Dp = 16.dp,
    localPhoto: String? = null,
) {
    StopPhotoBox(name, address, lat, lon, order, modifier.size(size), radius = radius, iconSize = size / 3, localPhoto = localPhoto)
}

/**
 * [StopPhoto]의 크기 자유판: 크기는 [modifier]로 (예: 카드 맨 위 큰 사진은 폭 가득 · 16:9). 사진 요청은 같다.
 * 앱 전체가 쓰는 장소 사진 칸: 받는 동안 [loadingColor]로 은은하게 깜빡이고, 사진이 없거나 이름을 모르면([name]이 비면) 핀.
 * [showName]이면 아래쪽에 이름 띠, [onClick]이 있으면 누를 수 있다 (보통 그곳 장소 창).
 */
@Composable
fun StopPhotoBox(
    name: String,
    address: String,
    lat: Double,
    lon: Double,
    order: Int?,
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    iconSize: Dp = 48.dp,
    loadingColor: Color = EodiTheme.colors.staySoft,
    showName: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    /** 글쓴이가 붙인 내 사진 (앱 안 사본 경로나 `content://`). 있으면 장소 대표 사진 대신 이것. */
    localPhoto: String? = null,
) {
    val c = EodiTheme.colors
    val shape = RoundedCornerShape(radius)
    val description = if (order != null) "$order $name" else name
    val box = modifier
        .clip(shape)
        .background(c.surfaceHigh, shape)
        .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
        .semantics { if (name.isNotBlank()) contentDescription = description }
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(c.staySoft), contentAlignment = Alignment.Center) {
            Icon(EodiIcons.Pin, null, tint = c.stay.copy(alpha = 0.7f), modifier = Modifier.size(iconSize))
        }
    }
    Box(box) {
        // 미리보기(@Preview)에는 사진 저장소(Hilt)가 없으니 사진 없는 자리로 그린다
        if (!localPhoto.isNullOrBlank()) {
            MyPhotoImage(localPhoto, Modifier.fillMaxSize(), sizePx = 480)
        } else if (LocalInspectionMode.current || name.isBlank()) {
            fallback()
        } else {
            PlaceThumbnail(
                name = name,
                address = address,
                lat = lat,
                lon = lon,
                modifier = Modifier.fillMaxSize(),
                loading = { PhotoLoadingPlaceholder(loadingColor) },
                fallback = fallback,
            )
        }
        if (showName && name.isNotBlank()) {
            Text(
                name,
                style = EodiTheme.type.caption,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(c.scrim).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 글쓴이 동그라미: 이름 첫 글자 + 이름마다 정해진 옅은 색 (같은 사람은 늘 같은 색).
 * 색은 이름 해시로 테마 색 짝(옅은 바탕 · 진한 글자) 가운데 하나를 고른다.
 */
@Composable
fun AuthorAvatar(author: String, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    val c = EodiTheme.colors
    val palette = listOf(
        c.accentSoft to c.accent,
        c.staySoft to c.stay,
        c.hereSoft to c.here,
        c.memorySoft to c.memoryInk,
        c.couponSoft to c.couponInk,
        c.heartSoft to c.heart,
    )
    val (bg, ink) = palette[Math.floorMod(author.hashCode(), palette.size)]
    Box(modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            author.trim().take(1).ifEmpty { "?" },
            style = EodiTheme.type.bodyStrong,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * 글 머리 (게시글처럼): 동그라미 + 글쓴이(굵게) + 아랫줄 "강원 강릉시 · 4일 전" + 내 글이면 "내 글".
 * 드라이브 코스 카드·상세가 쓴다 (여행 리뷰 카드도 같은 머리를 쓸 수 있게 공용으로 둔다).
 */
@Composable
fun PostHeader(author: String, sub: String, mine: Boolean, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = EodiTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AuthorAvatar(author)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    author,
                    style = EodiTheme.type.bodyStrong,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (mine) MineBadge()
            }
            Text(sub, style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 다녀온 날 줄: "11월 6일 · 가을 단풍철 · 맑음 14°C" (올해가 아니면 연도까지). */
@Composable
@ReadOnlyComposable
fun visitText(v: Visit): String {
    val parts = buildList {
        runCatching { LocalDate.parse(v.day) }.getOrNull()?.let { add(dateText(it)) }
        if (v.season.isNotBlank()) add(v.season.replace(" · ", " "))
        v.weather?.takeIf { it.isNotBlank() }?.let { add(it.replace(" · ", " ")) }
    }
    return parts.joinToString(" · ")
}

@Composable
@ReadOnlyComposable
fun dateText(d: LocalDate): String =
    if (d.year == LocalDate.now().year) {
        stringResource(R.string.community_date_md, d.monthValue, d.dayOfMonth)
    } else {
        stringResource(R.string.community_date_ymd, d.year, d.monthValue, d.dayOfMonth)
    }

/** 시각 → 현지 날짜 (리뷰 쓸 곳 목록의 "11월 2일"). */
fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** 날씨 글("흐림 · 19°C") → 아이콘과 색. 글이 없으면 해. */
@Composable
fun weatherIcon(weather: String?): Pair<ImageVector, Color> {
    val c = EodiTheme.colors
    val w = weather.orEmpty()
    return when {
        "눈" in w -> CommunityIcons.Snow to c.trip
        "비" in w || "소나기" in w || "뇌우" in w -> CommunityIcons.Rain to c.trip
        "흐림" in w || "안개" in w -> CommunityIcons.Cloud to c.textSecondary
        else -> CommunityIcons.Sun to c.stay
    }
}

/**
 * 날씨 아이콘이 붙은 다녀온 날 줄 (아이콘은 그날 날씨에 맞춘다).
 * [extra]는 뒤에 이어 붙일 조각 (리뷰: "아침 · 가족과 · 1시간 머묾"), [maxLines]는 넘칠 때 줄 수.
 */
@Composable
fun VisitLine(v: Visit, modifier: Modifier = Modifier, extra: List<String> = emptyList(), maxLines: Int = 1) {
    val c = EodiTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val (icon, tint) = weatherIcon(v.weather)
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        val text = (listOf(visitText(v)) + extra).filter { it.isNotBlank() }.joinToString(" · ")
        Text(text, style = EodiTheme.type.meta, color = c.textSecondary, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 목록 맨 위 항목이 바뀌면(정렬을 바꿈·새 글을 올림) 맨 위로 올라가는 격자 상태.
 * Lazy 목록은 보던 항목을 key로 붙잡고 있어서, 새 항목이 위에 생기면 화면 밖(위)에 숨어 버린다.
 */
@Composable
fun rememberTopGridState(firstKey: Any?): LazyGridState {
    val s = rememberLazyGridState()
    LaunchedEffect(firstKey) { s.scrollToItem(0) }
    return s
}

/** [rememberTopGridState]의 세로 목록판. */
@Composable
fun rememberTopListState(firstKey: Any?): LazyListState {
    val s = rememberLazyListState()
    LaunchedEffect(firstKey) { s.scrollToItem(0) }
    return s
}

/**
 * 빈 목록 · 오류 안내. [icon]이 있으면 옅은 원 안의 큰 아이콘 + 굵은 [title] + 회색 설명 — 빈 화면도 "앨범의 첫 장"처럼 보이게.
 * 없으면 가운데 글 한 줄. [action]이 있으면 아래에 버튼.
 */
@Composable
fun CenterMessage(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String? = null,
    tint: Color? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = EodiTheme.colors
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            val ink = tint ?: c.accent
            Box(Modifier.size(144.dp).background(ink.copy(alpha = if (c.isDark) 0.16f else 0.10f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.size(32.dp))
        }
        if (title != null) {
            Text(title, style = EodiTheme.type.title, color = c.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.size(12.dp))
        }
        Text(
            text,
            style = EodiTheme.type.body,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 760.dp),
        )
        if (action != null) {
            Spacer(Modifier.size(if (icon != null) 36.dp else 20.dp))
            action()
        }
    }
}

/**
 * 불러오는 중: 가운데 둥근 진행 고리 (옅은 파란 바탕 위 파란 고리). [message]가 있으면 아래에 한 줄.
 * [compact]는 작은 칸 · 상세 창 안에서 쓰는 작은 고리.
 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier, message: String? = null, compact: Boolean = false) {
    val c = EodiTheme.colors
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            color = c.accent,
            trackColor = c.accentSoft,
            strokeWidth = if (compact) 5.dp else 6.dp,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.size(if (compact) 48.dp else 64.dp),
        )
        if (message != null) {
            Spacer(Modifier.height(24.dp))
            Text(message, style = EodiTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** 다시 시도 버튼 (모든 화면 같은 모양: 파란 알약 + 새로고침 아이콘). */
@Composable
fun RetryButton(onRetry: () -> Unit, modifier: Modifier = Modifier, label: String = stringResource(R.string.community_retry)) {
    PrimaryPill(label, EodiIcons.Refresh, onRetry, modifier)
}

/**
 * 불러오기 실패: [title]이 있으면 큰 판 (빨간 느낌표 · 큰 제목 · 작은 사유 · 다시 시도) — 화면 전체가 비었을 때,
 * 없으면 가운데 글 한 줄 + 다시 시도 — 목록 · 칸 안에서.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    retryLabel: String = stringResource(R.string.community_retry),
) {
    val c = EodiTheme.colors
    if (title == null) {
        CenterMessage(message, modifier) { RetryButton(onRetry, label = retryLabel) }
        return
    }
    Column(
        modifier.padding(EodiTheme.space.page),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(120.dp).background(c.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(EodiIcons.Alert, contentDescription = null, tint = c.error, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(title, style = EodiTheme.type.hero, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = EodiTheme.type.meta,
            color = c.textTertiary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Spacer(Modifier.height(40.dp))
        RetryButton(onRetry, Modifier.widthIn(min = 320.dp), label = retryLabel)
    }
}

/**
 * [OverlayCard]를 판마다 다르게 띄우는 곳. 기본(null, 차)은 화면 가운데 카드.
 * 폰 판은 아래에서 올라오는 시트를 넣는다 (src/phone의 PhoneSheet) — 공유 시트·리뷰 쓰기 같은 화면 코드는 그대로 둔다.
 */
val LocalOverlayPresenter = staticCompositionLocalOf<(@Composable (onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) -> Unit)?> { null }

/**
 * 화면 위에 뜨는 카드 (뒤는 어둡게). 바깥을 누르면 닫힌다. 차 화면에서 시트 대신 쓴다 — 넓은 창에서도 폭 [maxWidth]까지만.
 * 폰 판은 [LocalOverlayPresenter]가 아래 시트로 띄운다.
 */
@Composable
fun OverlayCard(onDismiss: () -> Unit, modifier: Modifier = Modifier, maxWidth: Dp = 1080.dp, content: @Composable BoxScope.() -> Unit) {
    LocalOverlayPresenter.current?.let { present ->
        present(onDismiss, content)
        return
    }
    val c = EodiTheme.colors
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = none, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = c.surface,
            contentColor = c.textPrimary,
            shape = RoundedCornerShape(EodiTheme.space.radiusL),
            modifier = modifier
                .padding(24.dp)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                // 카드 안을 눌러도 닫히지 않게 클릭을 먹는다
                .clickable(interactionSource = none, indication = null) {},
        ) {
            Box(content = content)
        }
    }
}

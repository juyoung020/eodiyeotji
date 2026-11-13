package app.eodiyeotji.ui.share

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import app.eodiyeotji.ui.common.PressSurface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.EntryPointAccessors

/** 버튼 모양: 글자 있는 알약(하루 상세) · 동그란 아이콘(기억 카드 · 코스 상세). */
enum class ShareButtonStyle { PILL, ICON }

/**
 * "카드로 공유" 버튼: 누르면 [target]의 공유 카드(Rust가 만든 내용 + 들른 곳 사진)를 이미지로 그려 공유 시트를 띄운다.
 * 카카오톡은 폰의 공유 시트에 나온다. 보낼 글은 클립보드에도 넣는다 (카톡이 글을 버릴 때 붙여 넣게).
 * 차 판에는 공유 시트·메신저가 없으므로 연결된 폰으로 카드를 보낸다 ([CarHandoffButton]) → 폰 알림에서 카톡으로.
 */
@Composable
fun ShareImageButton(target: ShareTarget, modifier: Modifier = Modifier, style: ShareButtonStyle = ShareButtonStyle.PILL) {
    if (!ComposableImageShare.supported) {
        CarHandoffButton(target, modifier, style)
        return
    }
    val context = LocalContext.current
    val sharer = rememberImageSharer()
    if (!sharer.available) return
    val deps = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, ShareEntryPoint::class.java) }
    val notice = LocalShareNotice.current
    val onClick = {
        sharer.share(
            fileName = "eodiyeotji-${target.fileTag()}",
            widthDp = SHARE_CARD_WIDTH_DP,
            prepare = {
                val card = deps.core().shareCard(target)
                card to loadCardPhotos(context.applicationContext, deps.photos(), card)
            },
            text = { (card, _) -> card.shareText },
            onDone = { data, ok ->
                val msg = when {
                    !ok -> context.getString(R.string.share_card_failed)
                    (data?.first?.hiddenCount ?: 0) > 0 -> context.getString(R.string.share_card_hidden, data!!.first.hiddenCount)
                    else -> context.getString(R.string.share_card_text_copied)
                }
                notice.notify(context, msg)
            },
        ) { (card, photos) -> ShareCardView(card, photos) }
    }
    when (style) {
        ShareButtonStyle.PILL -> SecondaryPill(
            stringResource(if (sharer.busy) R.string.share_card_making else R.string.share_card_button),
            ShareIcon,
            { if (!sharer.busy) onClick() },
            modifier,
        )
        ShareButtonStyle.ICON -> {
            val c = EodiTheme.colors
            val label = stringResource(R.string.share_card_icon)
            PressSurface(
                onClick = { if (!sharer.busy) onClick() },
                shape = CircleShape,
                color = c.surfaceHigh,
                contentColor = c.textPrimary,
                loading = sharer.busy,
                modifier = modifier.heightIn(min = EodiTheme.space.touch).semantics { contentDescription = label },
            ) {
                // 아이콘 + 짧은 글자 (Pleos 가이드: 아이콘은 텍스트 라벨과 함께)
                Row(Modifier.padding(start = 18.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (sharer.busy) {
                        app.eodiyeotji.ui.common.EodiLoader(size = 30.dp)
                    } else {
                        Icon(ShareIcon, null, Modifier.size(30.dp), tint = c.accent)
                    }
                    Text(stringResource(R.string.share_icon_label), style = EodiTheme.type.label, maxLines = 1)
                }
            }
        }
    }
}

private fun ShareTarget.fileTag(): String = when (this) {
    is ShareTarget.Day -> "day-$day"
    is ShareTarget.Episode -> "trip-$episodeId"
    is ShareTarget.Course -> "course-${postId.hashCode().toUInt()}"
}

/** 공유 아이콘: 세 점을 잇는 선 (24 격자, 선 2.25, 둥근 끝 — EodiIcons와 같은 규칙). */
val ShareIcon: ImageVector by lazy {
    ImageVector.Builder("Share", 24.dp, 24.dp, 24f, 24f).apply {
        val ink = SolidColor(Color.Black)
        path(stroke = ink, strokeLineWidth = 2.25f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(8.4f, 10.6f); lineTo(15.6f, 6.9f)
            moveTo(8.4f, 13.4f); lineTo(15.6f, 17.1f)
            circle(6f, 12f, 2.8f)
            circle(18f, 5.6f, 2.8f)
            circle(18f, 18.4f, 2.8f)
        }
    }.build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    close()
}

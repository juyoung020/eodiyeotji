package app.eodiyeotji.ui.agent

import app.eodiyeotji.ui.common.eodiShadow
import app.eodiyeotji.ui.common.PressIconButton
import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.EodiIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R

/**
 * 말로 묻기 층 (에이전트 화면 위에 겹친다, 레이아웃은 건드리지 않는다).
 * - 듣는 중: 아래쪽 큰 카드 — 출렁이는 마이크 + 알아들은 말. 마이크를 누르면 "다 말했다", ×는 취소.
 * - 주행 중 답 화면: 오른쪽 위 마이크 (이어서 "응"·"2번으로 가자"처럼 말하기). 읽는 말은 앱 전체 자막([SpokenCaption])이 보여 준다.
 * - 짧은 안내("말씀을 듣지 못했어요")는 아래쪽 알약으로 잠깐.
 */
@Composable
internal fun BoxScope.AgentVoiceLayer(
    voice: VoiceUi,
    driving: Boolean,
    hasTurn: Boolean,
    onMic: () -> Unit,
    onCancelListening: () -> Unit,
    /** 읽는 말 자막이 떠 있는 동안 내려 준 만큼 (마이크가 자막 밑에 가리지 않게) */
    topSpace: androidx.compose.ui.unit.Dp = 0.dp,
) {
    // 주행 중 답 화면에는 마이크가 없다 → 오른쪽 위에 하나 둔다 (처음 화면은 가운데 큰 마이크가 있다)
    if (driving && hasTurn && !voice.listening) {
        PressSurface(
            onClick = onMic,
            shape = CircleShape,
            color = EodiTheme.colors.accent,
            contentColor = EodiTheme.colors.onAccent,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 56.dp + topSpace, end = 56.dp).size(96.dp).eodiShadow(EodiTheme.elevation.level2, CircleShape)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(AgentIcons.Mic, contentDescription = stringResource(R.string.agent_mic), modifier = Modifier.size(48.dp))
            }
        }
    }

    AnimatedVisibility(
        visible = voice.listening,
        enter = fadeIn(),
        exit = fadeOut(),
        // 주행 중에는 가운데 (아래쪽 큰 "안내"·"취소" 버튼을 가리지 않게), 정차 중에는 아래쪽
        modifier = Modifier.align(if (driving) Alignment.Center else Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
    ) {
        ListeningCard(voice, driving, onMic, onCancelListening)
    }

    val note = voice.note
    AnimatedVisibility(
        visible = note != null && !voice.listening,
        enter = fadeIn(),
        exit = fadeOut(),
        // 토스트 규격 (Pleos 가이드 components/toast.md): 좌우 · 아래 24dp, 최대 792dp, 글 712dp
        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(EodiTheme.space.radiusM),
            color = EodiTheme.colors.ink,
            contentColor = EodiTheme.colors.onInk,
            modifier = Modifier.widthIn(max = 792.dp).eodiShadow(EodiTheme.elevation.level2, RoundedCornerShape(EodiTheme.space.radiusM)),
        ) {
            Text(
                note.orEmpty(),
                style = EodiTheme.type.bodyStrong,
                modifier = Modifier.widthIn(max = 712.dp).padding(horizontal = 32.dp, vertical = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun ListeningCard(voice: VoiceUi, driving: Boolean, onMic: () -> Unit, onCancel: () -> Unit) {
    val pulse by animateFloatAsState(targetValue = 1f + voice.level * 0.35f, label = "micLevel")
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = EodiTheme.colors.surfaceHigh,
        modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth().eodiShadow(EodiTheme.elevation.level2, RoundedCornerShape(36.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                PressSurface(
                    onClick = onMic,
                    shape = CircleShape,
                    color = EodiTheme.colors.accent,
                    contentColor = EodiTheme.colors.onAccent,
                    modifier = Modifier.size(104.dp).scale(pulse),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AgentIcons.Mic, contentDescription = stringResource(R.string.agent_voice_done), modifier = Modifier.size(52.dp))
                    }
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                // Gleo 상태: 듣는 중(Passive → 2초 뒤 "운전석에서 듣는 중…") / 말하는 중(Active) / 되묻는 중(Asking)
                GleoStatusChip(
                    when {
                        voice.followUp -> GleoStatus.Asking
                        voice.partial.isBlank() -> GleoStatus.ListeningPassive
                        else -> GleoStatus.ListeningActive
                    },
                    level = voice.level,
                )
                Text(
                    voice.partial.ifBlank {
                        stringResource(
                            when {
                                !voice.followUp -> R.string.agent_voice_hint
                                voice.followUpCourses -> R.string.agent_voice_follow_up_courses_hint
                                else -> R.string.agent_voice_follow_up_hint
                            }
                        )
                    },
                    style = if (driving) EodiTheme.type.drivingTitle else EodiTheme.type.drivingBody,
                    color = if (voice.partial.isBlank()) EodiTheme.colors.textSecondary else EodiTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(2.dp))
            }
            PressIconButton(onClick = onCancel, modifier = Modifier.size(AgentTouchMin)) {
                Icon(EodiIcons.Close, contentDescription = stringResource(R.string.agent_voice_cancel), modifier = Modifier.size(36.dp))
            }
        }
    }
}

package app.eodiyeotji.ui.agent

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
import androidx.compose.material3.MaterialTheme
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
) {
    // 주행 중 답 화면에는 마이크가 없다 → 오른쪽 위에 하나 둔다 (처음 화면은 가운데 큰 마이크가 있다)
    if (driving && hasTurn && !voice.listening) {
        Surface(
            onClick = onMic,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 56.dp, end = 56.dp).size(96.dp),
        ) {
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
        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
            Text(
                note.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun ListeningCard(voice: VoiceUi, driving: Boolean, onMic: () -> Unit, onCancel: () -> Unit) {
    val pulse by animateFloatAsState(targetValue = 1f + voice.level * 0.35f, label = "micLevel")
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 12.dp,
        modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                Surface(
                    onClick = onMic,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(104.dp).scale(pulse),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AgentIcons.Mic, contentDescription = stringResource(R.string.agent_voice_done), modifier = Modifier.size(52.dp))
                    }
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(if (voice.followUp) R.string.agent_voice_follow_up else R.string.agent_voice_listening),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
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
                    style = if (driving) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                    color = if (voice.partial.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(2.dp))
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(AgentTouchMin)) {
                Icon(EodiIcons.Close, contentDescription = stringResource(R.string.agent_voice_cancel), modifier = Modifier.size(36.dp))
            }
        }
    }
}

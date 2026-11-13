package app.eodiyeotji.ui.agent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.agent.ProactiveUi
import app.eodiyeotji.ui.common.EodiLoader
import app.eodiyeotji.ui.theme.EodiTheme
import kotlinx.coroutines.delay

/**
 * Gleo AI 대화 상태 7단계 (Pleos 가이드 Experiences/gleo-ai.md "Status"). 차 기본 비서와 같은 이름 · 같은 흐름으로 보인다.
 * - [Sleep] 대화 없음 (표시 없음)
 * - [ListeningPassive] 듣기 시작, 아직 말이 없음 → 2초 동안 말이 없으면 "운전석에서 듣는 중…"
 * - [ListeningActive] 말하는 중 (알아들은 말이 오는 중)
 * - [Thinking] 말이 끝나고 찾는 중
 * - [Asking] 답 전에 되묻는 중 ("응"이면 안내 · "2번으로 가자")
 * - [Speaking] 답을 읽는 중
 * - [Standby] 대화가 끝나고 응답 화면을 5초 더 보이는 중 → 그 뒤 Sleep (화면이 내려간다)
 */
enum class GleoStatus { Sleep, ListeningPassive, ListeningActive, Thinking, Asking, Speaking, Standby }

/** 에이전트 화면 상태 → Gleo 상태 (계산 없음: 이미 있는 표시를 이름에 맞춰 고르기만 한다). */
internal fun gleoStatus(voice: VoiceUi, turn: AgentTurn?): GleoStatus = when {
    voice.listening && voice.followUp -> GleoStatus.Asking
    voice.listening && voice.partial.isBlank() -> GleoStatus.ListeningPassive
    voice.listening -> GleoStatus.ListeningActive
    voice.speaking -> GleoStatus.Speaking
    turn != null && (turn.phase == AnswerPhase.Thinking || turn.phase == AnswerPhase.Streaming || turn.action is ActionState.Running) -> GleoStatus.Thinking
    voice.standby -> GleoStatus.Standby
    else -> GleoStatus.Sleep
}

/** 먼저 말 걸기 카드 → Gleo 상태. */
internal fun gleoStatus(ui: ProactiveUi, speaking: Boolean, standby: Boolean): GleoStatus = when (ui.phase) {
    ProactiveUi.Phase.Listening -> GleoStatus.Asking
    ProactiveUi.Phase.Running -> GleoStatus.Thinking
    else -> when {
        speaking -> GleoStatus.Speaking
        standby -> GleoStatus.Standby
        ui.suggestion.ask && ui.phase == ProactiveUi.Phase.Asking -> GleoStatus.Asking
        else -> GleoStatus.Standby
    }
}

/**
 * 상태 표시 알약 (Gleo Chrome 자리): 상태마다 다른 빛(색 점) + 이름. 생각 중은 공식 로딩 애니메이션.
 * 움직임은 듣는 중의 목소리 크기에 따른 점 크기뿐이다 (주행 중 반복 애니메이션 없음 — 생각 중 로더는 짧게만 보인다).
 * [passiveSeatHint]: 듣기 시작 후 2초 동안 말이 없으면 "운전석에서 듣는 중…"으로 바꾼다.
 */
@Composable
fun GleoStatusChip(status: GleoStatus, modifier: Modifier = Modifier, level: Float = 0f, large: Boolean = false) {
    if (status == GleoStatus.Sleep) return
    val c = EodiTheme.colors
    val passiveMs = EodiTheme.motion.passiveHintMs
    var seat by remember { mutableStateOf(false) }
    LaunchedEffect(status) {
        seat = false
        if (status == GleoStatus.ListeningPassive) {
            delay(passiveMs)
            seat = true
        }
    }
    val (dot, label) = when (status) {
        GleoStatus.ListeningPassive -> c.accent to stringResource(if (seat) R.string.gleo_listening_passive_seat else R.string.gleo_listening_passive)
        GleoStatus.ListeningActive -> c.accent to stringResource(R.string.gleo_listening_active)
        GleoStatus.Thinking -> c.memory to stringResource(R.string.gleo_thinking)
        GleoStatus.Asking -> c.stay to stringResource(R.string.gleo_asking)
        GleoStatus.Speaking -> c.accent to stringResource(R.string.gleo_speaking)
        GleoStatus.Standby -> c.textTertiary to stringResource(R.string.gleo_standby)
        GleoStatus.Sleep -> Color.Transparent to ""
    }
    val pulse by animateFloatAsState(1f + level.coerceIn(0f, 1f) * 0.5f, label = "gleoLevel")
    Surface(shape = CircleShape, color = c.surfaceHigh, contentColor = c.textPrimary, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(min = if (large) 64.dp else 52.dp).padding(start = 16.dp, end = 24.dp),
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (status == GleoStatus.Thinking) {
                    EodiLoader(size = 32.dp)
                } else {
                    Box(
                        Modifier.size(18.dp)
                            .scale(if (status == GleoStatus.ListeningActive || status == GleoStatus.ListeningPassive) pulse else 1f)
                            .background(dot, CircleShape),
                    )
                }
            }
            AnimatedContent(label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "gleoLabel") { text ->
                Text(
                    text,
                    style = if (large) EodiTheme.type.titleSmall else EodiTheme.type.label,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

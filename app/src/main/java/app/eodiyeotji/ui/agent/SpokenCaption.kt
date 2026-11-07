package app.eodiyeotji.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.platform.SpeechOutput
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** 앱이 소리 내어 읽는 말 (에이전트 답 · 지나가며 회상 · "N년 전 오늘"). 계산 없음: 읽는 글을 그대로 넘긴다. */
@HiltViewModel
class SpokenCaptionViewModel @Inject constructor(private val tts: SpeechOutput) : ViewModel() {
    val text: StateFlow<String?> = tts.spokenText

    fun stop() = tts.stop()
}

/**
 * 읽는 말 자막: 앱이 말하는 동안 화면 위쪽 가운데에 스피커 + 읽는 글(3줄까지) + [멈춤].
 * 소리를 못 듣는 때(시끄러운 차 안 · 소리 끈 영상)에도 무엇을 말하는지 보이게 한다. 모든 화면 위에 하나만 둔다 (AppRoot · PhoneApp).
 */
@Composable
fun BoxScope.SpokenCaption(viewModel: SpokenCaptionViewModel = hiltViewModel()) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    // 사라지는 동안에도 마지막 글을 그대로 보여 준다
    var shown by remember { mutableStateOf("") }
    text?.let { shown = it }
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 16.dp, start = 24.dp, end = 24.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xEB1B1F27),
            contentColor = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 1180.dp),
        ) {
            Row(Modifier.padding(start = 26.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AgentIcons.Speaker, contentDescription = null, tint = Color(0xFF8DB8FF), modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(18.dp))
                Text(
                    shown,
                    style = EodiTheme.type.bodyStrong,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.width(18.dp))
                Surface(
                    onClick = viewModel::stop,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White,
                    modifier = Modifier.heightIn(min = EodiTheme.space.touch),
                ) {
                    Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AgentIcons.Close, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.agent_voice_stop_speaking), style = EodiTheme.type.label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

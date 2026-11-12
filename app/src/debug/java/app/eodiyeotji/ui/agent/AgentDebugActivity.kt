package app.eodiyeotji.ui.agent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 기억 에이전트 화면만 띄워 보는 디버그 화면 (디버그 빌드에만 있다). 실제 기억 에이전트(MemoryAgentPort)가 답한다.
 *
 * ```
 * adb shell am start -n app.eodiyeotji/.ui.agent.AgentDebugActivity                 # 처음 화면
 * adb shell am start -n app.eodiyeotji/.ui.agent.AgentDebugActivity --ei ask 1      # 예시 질문 1~3 바로 묻기
 * adb shell am start -n app.eodiyeotji/.ui.agent.AgentDebugActivity --es q "거기 몇 명이서 갔어"  # 아무 질문이나 (이어 묻기)
 * adb shell am start -n app.eodiyeotji/.ui.agent.AgentDebugActivity --ez driving true  # 주행 화면
 * adb shell am start -n app.eodiyeotji/.ui.agent.AgentDebugActivity --ez entry true    # 입구 버튼만
 * ```
 * 이미 떠 있으면 같은 화면에 그대로 적용된다 (singleTop, 대화 상태 유지).
 */
@AndroidEntryPoint
class AgentDebugActivity : ComponentActivity() {
    @Inject lateinit var drivingState: ManualDrivingStatePort

    private val viewModel: AgentViewModel by viewModels()
    private val entryOnly = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EodiTheme {
                if (entryOnly.value) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(32.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        AgentEntryButton(
                            onClick = { entryOnly.value = false },
                            modifier = Modifier.fillMaxWidth(),
                            driving = drivingState.isDriving.value,
                        )
                    }
                } else {
                    AgentPanel(onClose = { entryOnly.value = true }, viewModel = viewModel)
                }
            }
        }
        // 화면 회전·밤 모드 전환으로 다시 만들어질 때는 같은 질문을 다시 묻지 않는다
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.hasExtra(EXTRA_DRIVING)) drivingState.setDriving(intent.getBooleanExtra(EXTRA_DRIVING, false))
        if (intent.hasExtra(EXTRA_ENTRY)) entryOnly.value = intent.getBooleanExtra(EXTRA_ENTRY, false)
        if (intent.getBooleanExtra(EXTRA_CLEAR, false)) viewModel.clear()
        when (intent.getIntExtra(EXTRA_ASK, 0)) {
            1 -> viewModel.ask(getString(R.string.agent_example_1))
            2 -> viewModel.ask(getString(R.string.agent_example_2))
            3 -> viewModel.ask(getString(R.string.agent_example_3))
            4 -> viewModel.ask(getString(R.string.agent_followup_other_days))
        }
        intent.getStringExtra(EXTRA_Q)?.takeIf { it.isNotBlank() }?.let { viewModel.ask(it) }
    }

    private companion object {
        const val EXTRA_ASK = "ask"
        const val EXTRA_DRIVING = "driving"
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_CLEAR = "clear"
        const val EXTRA_Q = "q"
    }
}

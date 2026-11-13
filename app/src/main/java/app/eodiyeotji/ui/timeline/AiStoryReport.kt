package app.eodiyeotji.ui.timeline

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.hideDayStory
import app.eodiyeotji.ui.agent.DrivingStatePort
import app.eodiyeotji.ui.community.CommunityIcons
import app.eodiyeotji.ui.community.SecondaryPill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * "이 요약이 이상해요" (Pleos 앱 심사 가이드: AI가 만든 콘텐츠에 신고 수단).
 * 누르면 그날 AI 요약을 숨기고 규칙 제목 · 요약으로 돌아간다 (Rust `hide_day_story`, 기기 안 `hidden_item`).
 * 바뀐 기록은 `notifyChanged`로 타임라인이 다시 읽는다.
 */
@HiltViewModel
class AiStoryReportViewModel @Inject constructor(private val core: EodiCore, driving: DrivingStatePort) : ViewModel() {
    val isDriving: StateFlow<Boolean> = driving.isDriving

    fun hide(day: String) {
        viewModelScope.launch {
            try {
                core.hideDayStory(day)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("EodiStoryReport", "AI 요약 숨기기 실패", e)
            }
        }
    }
}

/** AI가 쓴 요약이 있는 날에만, 정차 중에만 보인다 (주행 중에는 버튼을 감춘다). */
@Composable
fun AiStoryReportButton(day: String, aiStory: Boolean, modifier: Modifier = Modifier, vm: AiStoryReportViewModel = hiltViewModel()) {
    if (!aiStory) return
    val driving by vm.isDriving.collectAsStateWithLifecycle()
    if (driving) return
    val description = stringResource(R.string.timeline_story_wrong_description)
    SecondaryPill(
        stringResource(R.string.timeline_story_wrong),
        CommunityIcons.Flag,
        { vm.hide(day) },
        modifier.semantics { contentDescription = description },
    )
}

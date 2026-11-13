package app.eodiyeotji.ui.timeline

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.seedCommunityDemo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 기록이 하나도 없는 첫 화면의 "시연 기록 넣기": 심사위원이 APK만 깔아도 1년치 시연 기록 · 커뮤니티 예시로 바로 둘러보게 한다.
 * 넣기는 Rust(`seed_demo_data` · `seed_community_demo`)가 하고, 끝나면 코어가 바뀜을 알려 날짜 목록이 다시 읽힌다.
 */
@HiltViewModel
class DemoSeedViewModel @Inject constructor(private val core: EodiCore) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun seed() {
        if (!app.eodiyeotji.BuildConfig.CONTEST_DEMO || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                core.seedDemoData()
                core.seedCommunityDemo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("EodiDemo", "시연 기록 넣기 실패", e)
            } finally {
                _busy.value = false
            }
        }
    }
}

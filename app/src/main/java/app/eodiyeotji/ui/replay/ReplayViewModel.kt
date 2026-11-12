package app.eodiyeotji.ui.replay

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.R
import app.eodiyeotji.core.DayReplay
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.MapPhotoSpot
import app.eodiyeotji.core.dayReplay
import app.eodiyeotji.gallery.GalleryRepository
import app.eodiyeotji.place.PlacePhotoRepository
import app.eodiyeotji.pleos.CourseGuide
import coil.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import app.eodiyeotji.ui.agent.DrivingStatePort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 그날로 돌아가기 화면 상태. [day]가 null이면 닫힘, [replay]가 null이면 불러오는 중. */
@Immutable
data class ReplayUiState(
    val day: String? = null,
    val replay: DayReplay? = null,
    val failed: Boolean = false,
    val driving: Boolean = false,
    /** 그날 달리는 중에 찍은 내 사진 (들른 곳 사진은 뺀다): 비행이 그 자리를 지날 때 띄운다 */
    val roadPhotos: List<MapPhotoSpot> = emptyList(),
    @StringRes val note: Int? = null,
)

/**
 * 그날로 돌아가기 (정차 중에만). 재료는 모두 Rust(`replay.rs`)가 만들고 여기서는 불러 담기만 한다.
 * 주행이 시작되면 저절로 닫는다 (운전 중에는 큰 사진 · 움직이는 화면을 보이지 않는다).
 */
@HiltViewModel
class ReplayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
    private val photos: PlacePhotoRepository,
    private val gallery: GalleryRepository,
    private val guide: CourseGuide,
    driving: DrivingStatePort,
    private val mood: app.eodiyeotji.platform.MoodLight,
) : ViewModel() {

    private val _state = MutableStateFlow(ReplayUiState())
    val state: StateFlow<ReplayUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var noteJob: Job? = null

    init {
        viewModelScope.launch {
            driving.isDriving.collect { d ->
                _state.update { it.copy(driving = d) }
                if (d) close()
            }
        }
    }

    fun open(day: String) {
        if (_state.value.driving) return
        _state.update { ReplayUiState(day = day, driving = it.driving) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val r = core.dayReplay(day)
                _state.update { if (it.day == day) it.copy(replay = r) else it }
                // 무드등을 그날 하늘빛으로 (정차 중에만 열리는 화면, 닫으면 되돌린다)
                launch { runCatching { mood.tint(moodColor(r.sky)) } }
                launch {
                    val road = gallery.dayPhotos(day).spots.filter { it.stayArriveMs == null }
                    _state.update { if (it.day == day) it.copy(roadPhotos = road) else it }
                }
                prefetchPhotos(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "그날 다시 보기 실패 ($day)", e)
                _state.update { if (it.day == day) it.copy(failed = true) else it }
            }
        }
    }

    /**
     * 장면 사진을 미리 받아 둔다: 장면마다 사진을 그 장면이 뜰 때 찾으면 2.6초 비행 뒤에 빈 칸이 먼저 보인다.
     * 순서대로 찾고(구글 요청이 한꺼번에 몰리지 않게) 이미지는 Coil 디스크·메모리 캐시에 넣어 둔다 → 장면이 뜨면 바로 그려진다.
     */
    private suspend fun prefetchPhotos(r: DayReplay) {
        for (scene in r.scenes) {
            val stop = scene.stop
            val photo = try {
                photos.photos(stop.name, stop.address, stop.lat, stop.lon).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            context.imageLoader.enqueue(photos.imageRequest(photo))
        }
    }

    fun close() {
        loadJob?.cancel()
        _state.update { ReplayUiState(driving = it.driving) }
        restoreMood()
    }

    override fun onCleared() {
        restoreMood()
        super.onCleared()
    }

    /** 무드등을 원래 색으로 (화면이 사라져도 끝까지 하도록 앱 범위에서). */
    private fun restoreMood() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { mood.restore() } }
    }

    /** "그 코스로 안내": 그날 대표 에피소드를 내비 구간으로 나눠(Rust) 코스 안내로 넘긴다. */
    fun navigate() {
        val r = _state.value.replay ?: return
        val id = r.episodeId ?: return
        viewModelScope.launch {
            val ok = try {
                val route = core.courseRoute(id, from = null, optimize = false)
                route.legs.isNotEmpty() && guide.startShared("replay-$id", r.title, route.legs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "코스 안내 실패", e)
                false
            }
            if (ok) close() else showNote(R.string.recap_navi_failed)
        }
    }

    private fun showNote(@StringRes res: Int) {
        noteJob?.cancel()
        _state.update { it.copy(note = res) }
        noteJob = viewModelScope.launch {
            delay(NOTE_MS)
            _state.update { it.copy(note = null) }
        }
    }

    private companion object {
        const val TAG = "EodiReplay"
        const val NOTE_MS = 2_500L
    }
}

/** 그날 하늘빛 → 무드등 색 (지도 하늘 그라데이션 `Atmosphere.skyPalette`의 지평선 빛과 같은 계열). */
private fun moodColor(sky: app.eodiyeotji.core.SkyPhase): Int = when (sky) {
    app.eodiyeotji.core.SkyPhase.DAWN -> 0xFFF6B49A.toInt()
    app.eodiyeotji.core.SkyPhase.DAY -> 0xFF7FB6E8.toInt()
    app.eodiyeotji.core.SkyPhase.GOLDEN -> 0xFFFFA05A.toInt()
    app.eodiyeotji.core.SkyPhase.BLUE -> 0xFF4A62C8.toInt()
    app.eodiyeotji.core.SkyPhase.NIGHT -> 0xFFFFB25A.toInt()
}

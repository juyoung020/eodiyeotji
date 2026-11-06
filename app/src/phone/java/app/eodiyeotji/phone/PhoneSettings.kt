package app.eodiyeotji.phone

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 길 안내를 넘길 지도 앱. [AUTO]는 설치된 앱 중 네이버 → 카카오 → 티맵 → 구글 순서로 처음 찾은 앱. */
enum class MapApp { AUTO, NAVER, KAKAO, TMAP, GOOGLE }

/**
 * 폰 판 작은 설정 3개.
 * @param loaded 파일을 다 읽었는지 (읽기 전에는 첫 실행 안내를 띄울지 정하지 않는다)
 * @param onboarded 첫 실행 권한 안내를 끝냈는지 (다음부터는 안내 화면 없이 바로 기록 화면)
 * @param backgroundAsked "항상 허용" 위치를 한 번 물어봤는지 (다시 묻지 않고, 설정 화면에서 "설정 열기"만 보인다)
 */
@Immutable
data class PhonePrefs(
    val loaded: Boolean = false,
    val onboarded: Boolean = false,
    val backgroundAsked: Boolean = false,
    val mapApp: MapApp = MapApp.AUTO,
)

/**
 * 폰 판 설정 저장소. 값이 셋뿐이라 DataStore 의존성을 새로 넣지 않고 SharedPreferences를 쓴다.
 * 파일은 처음 한 번 IO 스레드에서 읽어 [prefs]로 내보내고, 쓰기는 apply()(디스크 쓰기는 뒤에서)로 한다 → UI 스레드가 디스크를 기다리지 않는다.
 * 기록 데이터(Rust 저장소)와 섞지 않는다: 이 값들은 이 폰의 화면 설정일 뿐이고 기록·공유와 상관없다.
 */
@Singleton
class PhoneSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _prefs = MutableStateFlow(PhonePrefs())
    val prefs: StateFlow<PhonePrefs> = _prefs.asStateFlow()

    @Volatile
    private var sp: SharedPreferences? = null

    init {
        scope.launch { read() }
    }

    /** 지도 앱 설정 (PhoneNavigator가 IO 스레드에서 부른다). */
    suspend fun mapApp(): MapApp = withContext(Dispatchers.IO) { read().mapApp }

    fun setMapApp(app: MapApp) = write { putString(KEY_MAP_APP, app.name) }.also { _prefs.update { it.copy(mapApp = app) } }

    fun markBackgroundAsked() = write { putBoolean(KEY_BG_ASKED, true) }.also { _prefs.update { it.copy(backgroundAsked = true) } }

    fun markOnboarded() = write { putBoolean(KEY_ONBOARDED, true) }.also { _prefs.update { it.copy(onboarded = true) } }

    private fun read(): PhonePrefs {
        val current = _prefs.value
        if (current.loaded) return current
        val p = file()
        val v = PhonePrefs(
            loaded = true,
            onboarded = p.getBoolean(KEY_ONBOARDED, false),
            backgroundAsked = p.getBoolean(KEY_BG_ASKED, false),
            mapApp = p.getString(KEY_MAP_APP, null)?.let { n -> MapApp.entries.firstOrNull { it.name == n } } ?: MapApp.AUTO,
        )
        _prefs.update { if (it.loaded) it else v }
        return _prefs.value
    }

    private fun file(): SharedPreferences = sp ?: context.getSharedPreferences(FILE, Context.MODE_PRIVATE).also { sp = it }

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        scope.launch { file().edit().apply(block).apply() }
    }

    private companion object {
        const val FILE = "phone_settings"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_BG_ASKED = "background_location_asked"
        const val KEY_MAP_APP = "map_app"
    }
}

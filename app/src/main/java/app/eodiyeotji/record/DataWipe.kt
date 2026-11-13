package app.eodiyeotji.record

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.platform.RecordingIssue
import app.eodiyeotji.platform.RecordingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 기록 모두 지우기 = 동의 철회 (Pleos 앱마켓 심사 "사용자 개인정보 및 보안": 동의를 철회하면 활용을 멈추고 저장 데이터를 지운다).
 *
 * 1. 기록 DB의 모든 표 (위치·주행·머문 곳·장소·사진 색인·검색 색인·기억 에이전트·커뮤니티 캐시) → Rust `wipe_all`
 * 2. 앱이 저장한 파일 (장소 사진·게시 사진·폰 사진·캐시 폴더: 지도 사진·경로 그림·공유 이미지)
 * 3. 설정 파일(SharedPreferences) 전부 (같이 가기 상태·이어 보기·기록 세션·폰 첫 실행 안내 등) → 첫 실행 안내가 다시 뜬다
 * 4. 위치 권한 되돌리기 (Android 13+: `revokeSelfPermissionsOnKill`) → 다음 실행 때 위치 고지 · 권한 요청부터 다시
 * 5. 앱을 다시 띄운다 (메모리에 남은 상태까지 비우려고 프로세스를 끝내고 첫 화면부터)
 *
 * 오프라인 지도 파일(`map/`)은 개인 정보가 아니라 지우지 않는다. 주행 중에는 부르지 않는다 (화면이 막는다).
 */
@Singleton
class DataWipe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: EodiCore,
    private val platform: RecordingPlatform,
) {
    /** 1~4를 하고 지운 DB 줄 수를 돌려준다. 끝나면 [restartApp]을 부른다. */
    suspend fun wipeAll(): Long = withContext(Dispatchers.IO) {
        val rows = runCatching { core.rust.wipeAll().toLong() }.onFailure { Log.e(TAG, "DB 지우기 실패", it) }.getOrThrow()
        FILE_DIRS.forEach { File(context.filesDir, it).deleteRecursively() }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            val name = f.name.removeSuffix(".xml")
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perms = (platform.permissionsFor(RecordingIssue.LOCATION_OFF) + LOCATION_PERMS).distinct()
            runCatching { context.revokeSelfPermissionsOnKill(perms) }.onFailure { Log.w(TAG, "권한 되돌리기 실패", it) }
        }
        core.notifyChanged()
        Log.i(TAG, "기록 모두 지움: DB $rows 줄")
        rows
    }

    /** 첫 화면부터 다시 (프로세스를 끝내 메모리 상태와 권한 되돌리기를 반영한다). */
    fun restartApp() {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(it)
        }
        Process.killProcess(Process.myPid())
    }

    private companion object {
        const val TAG = "EodiWipe"
        /** filesDir 안 개인 데이터 폴더 (PlacePhotoRepository · GalleryRepository · PhonePhotoStore). */
        val FILE_DIRS = listOf("place_photos", "post_photos", "phone_photos")
        val LOCATION_PERMS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
    }
}

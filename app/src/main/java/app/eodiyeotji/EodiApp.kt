package app.eodiyeotji

import android.app.Application
import android.os.StrictMode
import app.eodiyeotji.agent.DayLabeler
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.seedCommunityDemo
import app.eodiyeotji.place.PlaceResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EodiApp : Application(), ImageLoaderFactory {
    /** 머문 곳·출발/도착 지점의 장소 이름을 뒤에서 찾는다 (화면과 상관없이 앱 전체에서 한 번). */
    @Inject lateinit var placeResolver: PlaceResolver

    /** 하루 기록에 LLM 제목·이야기·검색 태그를 뒤에서 붙인다 (하루 1번). */
    @Inject lateinit var dayLabeler: DayLabeler

    @Inject lateinit var core: EodiCore

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        placeResolver.start()
        dayLabeler.start()
        // 공모전 판: 커뮤니티 서버가 생기기 전까지 미리 만든 커뮤니티(게시판 · 드라이브 코스 · 리뷰)를 처음 실행 때 넣어 둔다.
        // Rust가 이미 넣었으면 아무것도 하지 않는다(meta community_seeded). 마켓판(-PCONTEST_DEMO=false)에서는 빠진다.
        if (BuildConfig.CONTEST_DEMO) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { core.seedCommunityDemo() }
            }
        }
        // 이미지 로더는 첫 사진 칸이 그려질 때 UI 스레드에서 만들어지며 디버그 빌드에서 수백 ms 멈췄다 → 미리 뒤에서 만들어 둔다
        // 첫 String.format은 Formatter·로캘 숫자 자료를 읽느라 오래 걸린다 (타임라인 첫 행 요약 줄에서 UI 스레드가 멈췄다) → 같이 미리
        Thread({
            coil.Coil.imageLoader(this)
            String.format(java.util.Locale.getDefault(), "%d %.1f %s", 1, 1.0, "")
            getString(R.string.timeline_trips, 1)
        }, "EodiWarmUp").start()
    }

    /**
     * 장소 사진(구글 Places) 이미지 로더. 구글 응답의 캐시 헤더를 따르면 같은 사진을 볼 때마다 다시 받아 하루 한도를 쓴다
     * → 헤더와 상관없이 디스크에 계속 둔다 (이름표는 바뀌지 않는 값이라 장소 결과를 30일마다 새로 받아도 같은 사진은 그대로 쓴다). 크기 한도를 넘을 때만 가장 오래 안 본 것부터 밀려난다.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            // 하드웨어 비트맵은 에뮬레이터(시연 기기) GPU에서 가끔 까만 칸으로 그려진다 → 소프트웨어 비트맵 (사진이 작아 메모리 부담 적음)
            .allowHardware(false)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("place_photos")).maxSizeBytes(PHOTO_DISK_BYTES).build() }
            .crossfade(true)
            .build()

    /** UI 스레드에서 디스크·네트워크를 건드리면 logcat에 StrictMode 경고가 남는다 (CLAUDE.md 원칙 2). */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build()
        )
    }

    private companion object {
        const val PHOTO_DISK_BYTES = 300L * 1024 * 1024
    }
}

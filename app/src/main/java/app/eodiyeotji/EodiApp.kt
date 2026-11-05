package app.eodiyeotji

import android.app.Application
import android.os.StrictMode
import app.eodiyeotji.agent.DayLabeler
import app.eodiyeotji.place.PlaceResolver
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

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        placeResolver.start()
        dayLabeler.start()
    }

    /**
     * 장소 사진(구글 Places) 이미지 로더. 구글 응답의 캐시 헤더를 따르면 같은 사진을 볼 때마다 다시 받아 하루 한도를 쓴다
     * → 헤더와 상관없이 디스크에 둔다 (장소 결과도 Rust DB에 30일 두는 것과 같은 기준, 오래된 것은 크기 한도로 밀려난다).
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .respectCacheHeaders(false)
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
        const val PHOTO_DISK_BYTES = 150L * 1024 * 1024
    }
}

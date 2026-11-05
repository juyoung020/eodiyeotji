package app.eodiyeotji

import android.app.Application
import android.os.StrictMode
import app.eodiyeotji.place.PlaceResolver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EodiApp : Application() {
    /** 머문 곳·출발/도착 지점의 장소 이름을 뒤에서 찾는다 (화면과 상관없이 앱 전체에서 한 번). */
    @Inject lateinit var placeResolver: PlaceResolver

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        placeResolver.start()
    }

    /** UI 스레드에서 디스크·네트워크를 건드리면 logcat에 StrictMode 경고가 남는다 (CLAUDE.md 원칙 2). */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build()
        )
    }
}

package app.eodiyeotji

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.eodiyeotji.record.RecordingPermissionGate
import app.eodiyeotji.ui.home.HomeRoute
import app.eodiyeotji.ui.place.PlaceDetailHost

/**
 * 차 판 화면 뼈대: 첫 실행에만 위치 권한을 묻고, 허용되면 자동 기록 서비스를 켠다. 화면은 왼쪽 레일 + 타임라인·커뮤니티.
 * 장소 상세 창(별점 · 리뷰)은 모든 화면 위에 오른쪽 큰 창으로 뜬다.
 */
@Composable
fun AppRoot() {
    Box(Modifier.fillMaxSize()) {
        RecordingPermissionGate {
            HomeRoute()
        }
        PlaceDetailHost()
    }
}

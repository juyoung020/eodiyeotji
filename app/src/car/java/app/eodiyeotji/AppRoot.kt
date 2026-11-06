package app.eodiyeotji

import androidx.compose.runtime.Composable
import app.eodiyeotji.record.RecordingPermissionGate
import app.eodiyeotji.ui.home.HomeRoute

/** 차 판 화면 뼈대: 첫 실행에만 위치 권한을 묻고, 허용되면 자동 기록 서비스를 켠다. 화면은 왼쪽 레일 + 타임라인·커뮤니티. */
@Composable
fun AppRoot() {
    RecordingPermissionGate {
        HomeRoute()
    }
}

package app.eodiyeotji

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.eodiyeotji.record.RecordingPermissionGate
import app.eodiyeotji.ui.home.HomeRoute
import app.eodiyeotji.ui.place.PlaceDetailHost
import app.eodiyeotji.ui.agent.SpokenCaption

/**
 * 차 판 화면 뼈대: 첫 실행에만 위치 권한을 묻고, 허용되면 자동 기록 서비스를 켠다. 화면은 왼쪽 레일 + 타임라인·커뮤니티.
 * 장소 상세 창(별점 · 리뷰)은 모든 화면 위에 오른쪽 큰 창으로 뜬다. 앱이 읽는 말은 맨 위 자막.
 */
@Composable
fun AppRoot() {
    Box(Modifier.fillMaxSize()) {
        RecordingPermissionGate {
            HomeRoute()
        }
        PlaceDetailHost()
        // 앱이 소리 내어 읽는 말 (답 · 회상)을 맨 위에 자막으로
        SpokenCaption()
        // 에이전트가 먼저 거는 말 한 장 (출발 · 잔량 · 도착, ui/agent/ProactiveCard.kt)
        app.eodiyeotji.ui.agent.ProactiveCard()
        // 공유 결과 같은 앱 안 알림을 화면 아래에 (ui/share/ShareNotice.kt)
        app.eodiyeotji.ui.share.ShareNoticeHost()
        // 연결된 휴대폰으로 기록 맞추기 (ui/share/CarHandoff.kt)
        app.eodiyeotji.ui.share.HandoffSyncLoop()
    }
}

/** 앱 화면 위를 덮는 화면(그날로 돌아가기)까지 포함한 전체 크기 비율. 차 판은 토큰 크기 그대로 그린다. */
@Composable
fun AppScale(content: @Composable () -> Unit) = content()

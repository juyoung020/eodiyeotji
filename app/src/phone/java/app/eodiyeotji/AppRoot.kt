package app.eodiyeotji

import androidx.compose.runtime.Composable
import app.eodiyeotji.phone.ui.PhoneApp

/** 폰 판 화면 뼈대: 첫 실행 권한 안내 → 아래 탭(내 기록 · 물어보기 · 커뮤니티) 세로 화면 (phone/ui/PhoneApp.kt). */
@Composable
fun AppRoot() {
    PhoneApp()
}

/**
 * 앱 화면 위를 덮는 화면(그날로 돌아가기)까지 포함한 전체 크기 비율과 색. 폰 판은 [PhoneScale] 비율로 그리고
 * (이게 없으면 덮는 화면만 차 크기로 그려져 폰에서 두 배로 커 보인다), 폰 판 색([PhoneTheme])을 입힌다.
 */
@Composable
fun AppScale(content: @Composable () -> Unit) = app.eodiyeotji.phone.ui.PhoneTheme { app.eodiyeotji.phone.ui.PhoneScale(content) }

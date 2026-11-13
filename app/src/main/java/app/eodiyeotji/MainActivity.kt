package app.eodiyeotji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 맨 위 화면에서 뒤로(CCP Back 하드키 포함): 앱을 끝내지 않고 숨긴다 (Pleos CCP 가이드 필수: finish 대신 moveTaskToBack(true)).
        // 화면 안 창 · 시트의 BackHandler는 나중에 등록돼 먼저 받으므로, 이것은 더 닫을 것이 없을 때만 불린다
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        setContent {
            EodiTheme {
                // 화면 뼈대는 판마다 다르다: 차 = 왼쪽 레일 + 넓은 화면(src/car/…/AppRoot.kt), 폰 = 아래 탭 + 세로 화면(src/phone/…/AppRoot.kt)
                // 쿠폰 배지 · 쿠폰함이 어느 화면에서든 같은 받은 쿠폰 · 주행 상태를 본다
                // 장소 상세 창(별점 · 리뷰)도 어느 화면에서든 LocalOpenPlace로 연다 (그리는 자리는 판마다 PlaceDetailHost)
                app.eodiyeotji.ui.offers.CouponScope {
                    app.eodiyeotji.ui.place.PlaceDetailScope {
                        // 그날로 돌아가기도 어느 화면에서든 LocalOpenReplay로 연다 (앱 화면 위를 전부 덮는다)
                        // AppScale: 판마다의 크기 비율 (폰은 0.62배). 덮는 화면도 같은 비율로 그리도록 여기서 감싼다
                        AppScale { app.eodiyeotji.ui.replay.ReplayScope { app.eodiyeotji.ui.share.ShareNoticeScope { AppRoot() } } }
                    }
                }
            }
        }
    }
}

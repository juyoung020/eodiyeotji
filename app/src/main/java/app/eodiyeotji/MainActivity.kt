package app.eodiyeotji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EodiTheme {
                // 화면 뼈대는 판마다 다르다: 차 = 왼쪽 레일 + 넓은 화면(src/car/…/AppRoot.kt), 폰 = 아래 탭 + 세로 화면(src/phone/…/AppRoot.kt)
                // 쿠폰 배지 · 쿠폰함이 어느 화면에서든 같은 받은 쿠폰 · 주행 상태를 본다
                // 장소 상세 창(별점 · 리뷰)도 어느 화면에서든 LocalOpenPlace로 연다 (그리는 자리는 판마다 PlaceDetailHost)
                app.eodiyeotji.ui.offers.CouponScope {
                    app.eodiyeotji.ui.place.PlaceDetailScope {
                        // 그날로 돌아가기도 어느 화면에서든 LocalOpenReplay로 연다 (앱 화면 위를 전부 덮는다)
                        app.eodiyeotji.ui.replay.ReplayScope { AppRoot() }
                    }
                }
            }
        }
    }
}

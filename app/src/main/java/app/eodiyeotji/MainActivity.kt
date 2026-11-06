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
                AppRoot()
            }
        }
    }
}

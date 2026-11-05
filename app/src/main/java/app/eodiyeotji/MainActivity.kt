package app.eodiyeotji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.eodiyeotji.record.RecordingPermissionGate
import app.eodiyeotji.ui.home.HomeRoute
import app.eodiyeotji.ui.theme.EodiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EodiTheme {
                // 첫 실행에만 위치 권한을 묻고, 허용되면 자동 기록 서비스를 켠다. 화면은 항상 타임라인.
                RecordingPermissionGate {
                    HomeRoute()
                }
            }
        }
    }
}

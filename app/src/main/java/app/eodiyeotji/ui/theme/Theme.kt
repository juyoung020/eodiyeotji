package app.eodiyeotji.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1E5BD8)

private val Light = lightColorScheme(primary = Blue)
private val Dark = darkColorScheme(primary = Color(0xFF8DB0FF))

/** 임시 테마. 화면 시안(U1)과 Pleos 디자인 토큰이 정해지면 바꾼다. */
@Composable
fun EodiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

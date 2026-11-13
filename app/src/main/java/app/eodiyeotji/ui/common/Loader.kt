package app.eodiyeotji.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.ui.theme.EodiTheme
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Pleos 공식 로딩 애니메이션 (foundations/animation.md의 Loading.zip, res/raw/pleos_loading_*).
 * - [LoaderKind.Linear]: 짧은 로딩 (버튼 안 · 생각 중 · 한 줄 결과 기다리기)
 * - [LoaderKind.Tension]: 긴 로딩 (화면 · 페이지 불러오기)
 * 낮/밤 테마에 맞는 판을 고른다.
 */
enum class LoaderKind { Linear, Tension }

@Composable
fun EodiLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    kind: LoaderKind = LoaderKind.Linear,
    /** 어두운 바탕 위(사진 보기 · 진한 띠)면 true — 밝은 판 로더를 쓴다 */
    dark: Boolean = EodiTheme.colors.isDark,
) {
    val res = when (kind) {
        LoaderKind.Linear -> if (dark) R.raw.pleos_loading_linear_dark else R.raw.pleos_loading_linear_light
        LoaderKind.Tension -> if (dark) R.raw.pleos_loading_tension_dark else R.raw.pleos_loading_tension_light
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    )
}

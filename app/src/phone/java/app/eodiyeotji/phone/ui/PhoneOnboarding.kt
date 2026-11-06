package app.eodiyeotji.phone.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.eodiyeotji.R
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.theme.BrandMark
import app.eodiyeotji.ui.theme.DottedRoute
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.timeline.EmptyRoadArt

/** 첫 실행 안내 단계. 순서: 위치 → "항상 허용" 위치 → 알림. */
private enum class OnboardStep { LOCATION, BACKGROUND, NOTIFICATIONS }

/**
 * 폰 첫 실행 안내 (권한). 한 화면에 한 가지씩, 왜 필요한지 먼저 말하고 시스템 창을 띄운다.
 * - 위치: 허용되면 바로 기록 서비스를 켠다 ([onLocationGranted]).
 * - "항상 허용": 위치가 허용됐을 때만, **한 번만** 묻는다 ([onBackgroundAsked]로 물었음을 저장 → 다음부터는 설정 시트의 "설정 열기"만).
 * - 알림: Android 13 이상만.
 * 이미 허용된 단계는 건너뛴다. "나중에"를 눌러도 다음 단계로 간다. 다 끝나면 [onDone].
 */
@Composable
fun PhoneOnboarding(
    backgroundAsked: Boolean,
    onLocationGranted: () -> Unit,
    onBackgroundAsked: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // 화면을 처음 그릴 때 물을 단계를 정한다 (단계 수 표시가 중간에 바뀌지 않게)
    val steps = remember {
        buildList {
            if (!context.granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(OnboardStep.LOCATION)
            if (Build.VERSION.SDK_INT >= 29 && !backgroundAsked && !context.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                add(OnboardStep.BACKGROUND)
            }
            if (Build.VERSION.SDK_INT >= 33 && !context.granted(Manifest.permission.POST_NOTIFICATIONS)) add(OnboardStep.NOTIFICATIONS)
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }

    fun next() {
        var i = index + 1
        // "항상 허용"은 위치가 먼저 허용돼 있어야 물을 수 있다
        while (i < steps.size && steps[i] == OnboardStep.BACKGROUND && !context.granted(Manifest.permission.ACCESS_FINE_LOCATION)) i++
        if (i >= steps.size) onDone() else index = i
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            onLocationGranted()
        }
        next()
    }

    if (steps.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
        return
    }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val c = EodiTheme.colors
    Surface(Modifier.fillMaxSize(), color = c.background, contentColor = c.textPrimary) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { (slideInHorizontally(tween(240)) { it / 8 } + fadeIn(tween(240))) togetherWith fadeOut(tween(120)) },
            label = "onboard",
        ) { s ->
            OnboardPage(
                step = s,
                position = steps.indexOf(s) + 1,
                total = steps.size,
                onAllow = {
                    when (s) {
                        OnboardStep.LOCATION -> launcher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                        OnboardStep.BACKGROUND -> {
                            onBackgroundAsked()
                            launcher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                        }
                        OnboardStep.NOTIFICATIONS -> launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                },
                onLater = {
                    // "항상 허용"은 나중에를 눌러도 물어본 것으로 친다 (매번 다시 묻지 않는다)
                    if (s == OnboardStep.BACKGROUND) onBackgroundAsked()
                    next()
                },
            )
        }
    }
}

@Composable
private fun OnboardPage(step: OnboardStep, position: Int, total: Int, onAllow: () -> Unit, onLater: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    val (title, body, button) = when (step) {
        OnboardStep.LOCATION -> Triple(R.string.onboard_location_title, R.string.onboard_location_body, R.string.onboard_location_button)
        OnboardStep.BACKGROUND -> Triple(R.string.onboard_background_title, R.string.onboard_background_body, R.string.onboard_background_button)
        OnboardStep.NOTIFICATIONS -> Triple(R.string.onboard_notify_title, R.string.onboard_notify_body, R.string.onboard_notify_button)
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 40.dp, vertical = 32.dp)) {
        // 머리: 앱 표시 + 점선 길 위 단계 표시
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.width(20.dp))
            DottedRoute(Modifier.weight(1f), pin = c.stay.copy(alpha = 0.8f))
            Spacer(Modifier.width(20.dp))
            Text(stringResource(R.string.onboard_step, position, total), style = type.eyebrow, color = c.textTertiary)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (step) {
                    OnboardStep.LOCATION -> EmptyRoadArt()
                    OnboardStep.BACKGROUND -> StepBadge(PhoneIcons.Locate, c.accent, c.accentSoft)
                    OnboardStep.NOTIFICATIONS -> StepBadge(PhoneIcons.Bell, c.memoryInk, c.memorySoft)
                }
            }
            Spacer(Modifier.height(48.dp))
            Text(stringResource(title), style = type.hero, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(20.dp))
            Text(stringResource(body), style = type.body, color = c.textSecondary)
        }
        PrimaryPill(stringResource(button), if (step == OnboardStep.NOTIFICATIONS) null else EodiIcons.Pin, onAllow, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onLater,
            color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = c.textSecondary,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = EodiTheme.space.touch),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.onboard_later), style = type.label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 단계 그림: 옅은 동그라미 두 겹 안의 아이콘 (주행 화면 마이크와 같은 모양). */
@Composable
private fun StepBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, ink: androidx.compose.ui.graphics.Color, soft: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(280.dp).background(soft.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(180.dp).background(soft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(96.dp))
        }
    }
}

private fun Context.granted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

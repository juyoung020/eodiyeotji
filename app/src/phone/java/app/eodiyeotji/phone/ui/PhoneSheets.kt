package app.eodiyeotji.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.eodiyeotji.R
import app.eodiyeotji.phone.MapApp
import app.eodiyeotji.phone.NoMapAppRequest
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme

/**
 * 설정 시트: 길 안내를 넘길 지도 앱 고르기 + 기록 권한 상태.
 * 권한은 여기서 다시 묻지 않는다 ("항상 허용"은 첫 실행에 한 번만) → 꺼져 있으면 "설정 열기"로 시스템 설정 화면을 연다.
 */
@Composable
fun PhoneSettingsSheet(
    mapApp: MapApp,
    installed: Set<MapApp>?,
    permissions: PhonePermissions,
    onMapApp: (MapApp) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    PhoneSheet(onDismiss = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_title), style = type.title, modifier = Modifier.weight(1f).semantics { heading() })
                RoundIconButton(EodiIcons.Close, stringResource(R.string.settings_close), onClose)
            }
            Spacer(Modifier.size(24.dp))
            SectionTitle(stringResource(R.string.settings_map_app))
            Column(Modifier.clip(RoundedCornerShape(EodiTheme.space.radiusM)).background(c.surfaceHigh)) {
                MapApp.entries.forEach { app ->
                    val missing = app != MapApp.AUTO && installed != null && app !in installed
                    MapAppRow(label = stringResource(app.label()), note = if (missing) stringResource(R.string.settings_map_not_installed) else null, selected = app == mapApp) {
                        onMapApp(app)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.settings_map_hint), style = type.caption, color = c.textTertiary)

            Spacer(Modifier.size(32.dp))
            SectionTitle(stringResource(R.string.settings_permissions))
            Column(
                Modifier.clip(RoundedCornerShape(EodiTheme.space.radiusM)).background(c.surfaceHigh).padding(vertical = 8.dp),
            ) {
                PermissionRow(stringResource(R.string.settings_perm_location), permissions.location)
                PermissionRow(stringResource(R.string.settings_perm_background), permissions.background)
                PermissionRow(stringResource(R.string.settings_perm_notify), permissions.notifications)
            }
            if (!permissions.location || !permissions.background || !permissions.notifications) {
                Spacer(Modifier.size(16.dp))
                SecondaryPill(stringResource(R.string.settings_open_system), PhoneIcons.Tune, onOpenSystemSettings, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun MapApp.label(): Int = when (this) {
    MapApp.AUTO -> R.string.settings_map_auto
    MapApp.NAVER -> R.string.settings_map_naver
    MapApp.KAKAO -> R.string.settings_map_kakao
    MapApp.TMAP -> R.string.settings_map_tmap
    MapApp.GOOGLE -> R.string.settings_map_google
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = EodiTheme.type.eyebrow,
        color = EodiTheme.colors.textTertiary,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp).semantics { heading() },
    )
}

/** 고르는 줄: 이름 (+ 설치 안 됨) · 오른쪽 동그라미 (고르면 파랑 채움 + 체크). */
@Composable
private fun MapAppRow(label: String, note: String?, selected: Boolean, onClick: () -> Unit) {
    val c = EodiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = EodiTheme.space.touch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = EodiTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f, fill = false))
            if (note != null) {
                Spacer(Modifier.width(12.dp))
                Text(note, style = EodiTheme.type.caption, color = c.textTertiary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier.size(40.dp).background(if (selected) c.accent else c.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(EodiIcons.Check, null, tint = c.onAccent, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun PermissionRow(label: String, on: Boolean) {
    val c = EodiTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = EodiTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (on) R.string.settings_perm_on else R.string.settings_perm_off),
            style = EodiTheme.type.label,
            fontWeight = FontWeight.Bold,
            color = if (on) c.here else c.stay,
        )
    }
}

/** 지도 앱이 없을 때: 무엇을 하려던 참인지 + 구글 지도 웹으로 열기 · 네이버 지도 설치 · 닫기. */
@Composable
fun NoMapAppSheet(request: NoMapAppRequest, onWeb: () -> Unit, onInstall: () -> Unit, onClose: () -> Unit) {
    val c = EodiTheme.colors
    val type = EodiTheme.type
    PhoneSheet(onDismiss = onClose) {
        Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(88.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(PhoneIcons.Map, null, tint = c.accent, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(24.dp))
                Text(stringResource(R.string.nomap_title), style = type.title, modifier = Modifier.weight(1f).semantics { heading() })
            }
            Text(stringResource(R.string.nomap_body, request.title), style = type.body, color = c.textSecondary)
            Spacer(Modifier.size(4.dp))
            PrimaryPill(stringResource(R.string.nomap_web), EodiIcons.Navi, onWeb, Modifier.fillMaxWidth())
            SecondaryPill(stringResource(R.string.nomap_install), PhoneIcons.Map, onInstall, Modifier.fillMaxWidth())
            Box(
                Modifier.fillMaxWidth().heightIn(min = EodiTheme.space.touch).clip(CircleShape)
                    .selectable(selected = false, role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.nomap_close), style = type.label, color = c.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

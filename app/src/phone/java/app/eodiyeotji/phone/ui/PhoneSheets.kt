package app.eodiyeotji.phone.ui

import app.eodiyeotji.ui.community.SectionTitle
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
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme

/**
 * 설정 시트: 차와 연결 + 기록 권한 상태.
 * 권한은 여기서 다시 묻지 않는다 ("항상 허용"은 첫 실행에 한 번만) → 꺼져 있으면 "설정 열기"로 시스템 설정 화면을 연다.
 */
@Composable
fun PhoneSettingsSheet(
    permissions: PhonePermissions,
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
            CarLinkSection()

            Spacer(Modifier.size(32.dp))
            LogbookSection()

            Spacer(Modifier.size(32.dp))
            SectionTitle(stringResource(R.string.settings_permissions), Modifier.padding(start = 4.dp, bottom = 12.dp))
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


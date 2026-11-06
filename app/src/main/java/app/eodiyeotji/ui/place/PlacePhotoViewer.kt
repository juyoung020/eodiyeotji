package app.eodiyeotji.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.eodiyeotji.R
import app.eodiyeotji.ui.agent.AgentIcons
import app.eodiyeotji.ui.theme.EodiTheme

/**
 * 들른 곳 사진 크게 보기: 화면 가운데 큰 창에 그곳 사진(최대 6장)을 넘겨 본다. 위에 장소 이름·주소, 닫기.
 * 타임라인의 작은 사진을 누르면 뜬다.
 */
@Composable
fun PlacePhotoViewer(name: String, address: String, lat: Double, lon: Double, onDismiss: () -> Unit) {
    val c = EodiTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surface)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = EodiTheme.type.title, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (address.isNotBlank()) {
                        Text(address, style = EodiTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(64.dp)) {
                    Icon(AgentIcons.Close, contentDescription = stringResource(R.string.guide_close), tint = c.textSecondary, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
                PlacePhotoGallery(name, address, lat, lon, Modifier.fillMaxWidth().height(460.dp))
            }
        }
    }
}

package app.eodiyeotji.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.ui.theme.EodiTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 인터넷이 끊겼을 때만 보이는 작은 표시 (Pleos 기획 가이드 "주행 중 네트워크 환경 대응: 오프라인 모드 안내 UI").
 * 끊겨도 기록 · 검색 · 지도는 기기 안 데이터로 계속 되므로 막는 창이 아니라 글자 표시 하나만. 연결되면 사라진다.
 * 화면이 보이는 동안만 연결 상태를 듣는다 (collectAsStateWithLifecycle).
 */
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val flow = remember(context) { context.onlineFlow() }
    val online by flow.collectAsStateWithLifecycle(initialValue = true)
    if (online) return
    val c = EodiTheme.colors
    val a11y = stringResource(R.string.offline_a11y)
    Text(
        stringResource(R.string.offline_badge),
        style = EodiTheme.type.caption,
        fontWeight = FontWeight.Bold,
        color = c.textPrimary,
        maxLines = 1,
        modifier = modifier
            .semantics { contentDescription = a11y }
            .background(c.staySoft, CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

private fun Context.onlineFlow(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    if (cm == null) {
        trySend(true); awaitClose { }; return@callbackFlow
    }
    fun has(caps: NetworkCapabilities?) = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    trySend(has(cm.getNetworkCapabilities(cm.activeNetwork)))
    val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(has(caps)) }
        override fun onLost(network: Network) { trySend(false) }
    }
    runCatching { cm.registerDefaultNetworkCallback(cb) }
    awaitClose { runCatching { cm.unregisterNetworkCallback(cb) } }
}.distinctUntilChanged()

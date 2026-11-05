package app.eodiyeotji.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 지금 차 위치 = 마지막으로 저장된 위치 점 (Rust `last_fix`, 표 끝 한 줄이라 1ms 안쪽).
 * 기록기는 기어가 P가 아닐 때만 위치를 저장하므로, 정차 중이면 이 점이 차가 서 있는 자리다. 기록이 없으면 null.
 * 기억 에이전트가 "근처 추천 코스"·"지금 위치에서 가장 짧은 순서"에 쓴다.
 */
suspend fun EodiCore.lastKnownLocation(): LatLon? = withContext(Dispatchers.IO) {
    runCatching { rust.lastFix() }.getOrNull()?.let { LatLon(it.lat, it.lon) }
}

package app.eodiyeotji.core

import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint

/**
 * 같이 가기: 잔량이 부족한 차와 함께 들를 충전소(ev) · 주유소를 기록에서 고른다 (Rust `convoy::energy_stop`).
 * 지금 위치 → 목적지 길에서 덜 돌아가는 곳 (15km 안). 없으면 null.
 */
suspend fun EodiCore.convoyEnergyStop(from: LatLon, dest: LatLon, ev: Boolean): Triple<String, Double, Double>? = coreCall("convoyEnergyStop") {
    runCatching { rust.convoyEnergyStop(RGeoPoint(from.lat, from.lon), RGeoPoint(dest.lat, dest.lon), ev, System.currentTimeMillis()) }
        .getOrNull()
        ?.let { Triple(it.name, it.lat, it.lon) }
}

/** 같이 가기: 지금 위치를 다른 차에 보내도 되는가. 집 · 회사 근처(공유와 같은 1km)면 false. 오류면 보내지 않는다(안전한 쪽). */
suspend fun EodiCore.convoyPositionShareable(at: LatLon): Boolean = coreCall("convoyPositionShareable") {
    runCatching { rust.convoyPositionShareable(RGeoPoint(at.lat, at.lon)) }.getOrDefault(false)
}

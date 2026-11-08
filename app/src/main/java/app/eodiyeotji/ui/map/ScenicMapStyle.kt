package app.eodiyeotji.ui.map

import android.content.Context
import app.eodiyeotji.core.RouteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 드라이브 코스 3D 미리보기 스타일 (`assets/map/scenic-3d.json`): OpenFreeMap 벡터 타일 + 건물 3D + 산 음영 + 빛나는 길.
 * 때(낮·노을·밤, Rust가 언제 좋은지에서 정한 [RouteTheme])마다 색만 바꾸고, 길 GeoJSON은 Rust가 만든 것을 그대로 넣는다.
 * 라이브 지도와 목록 카드 스냅숏이 같은 스타일을 쓴다. 파일 읽기는 IO 스레드에서.
 */
internal object ScenicMapStyle {
    private const val ASSET = "map/scenic-3d.json"
    private const val ROUTE_SLOT = "\"__ROUTE_GEOJSON__\""

    @Volatile private var template: String? = null

    suspend fun build(context: Context, theme: RouteTheme, routeGeojson: String): String = withContext(Dispatchers.IO) {
        val t = template ?: context.assets.open(ASSET).bufferedReader().use { it.readText() }.also { template = it }
        var json = t
        for ((k, v) in palette(theme)) json = json.replace("{{$k}}", v)
        json.replace(ROUTE_SLOT, routeGeojson)
    }

    /** 때별 색. 낮 = 따뜻한 미색 땅·맑은 물·코랄 길, 노을 = 살구빛 땅·청록에서 보랏빛으로 저무는 물(먼 바다일수록 깊게)·금빛 길, 밤 = 남색 땅·불 켜진 건물·환한 길. */
    private fun palette(theme: RouteTheme): Map<String, String> = when (theme) {
        RouteTheme.DAY -> mapOf(
            "land" to "#ECE7DE", "green" to "#D3E6C6", "park" to "#C6E1BA", "water" to "#9CCFE6", "waterDeep" to "#9CCFE6", "waterLabel" to "#4F7FA8",
            "hillShadow" to "#7C7266", "hillHighlight" to "#FFFDF8", "hillAccent" to "#9A8E7E",
            "roadMinor" to "#FFFFFF", "roadMajor" to "#FFFCF5", "roadCasing" to "#D8D0C3", "roadLight" to "#FFFFFF",
            "buildingLow" to "#F2ECE3", "buildingMid" to "#E6DCCD", "buildingHigh" to "#D4C6B3", "boundary" to "#B5ADA2",
            "label" to "#3E3A35", "labelHalo" to "#FFFFFF", "sightText" to "#B4401F",
            "routeHalo" to "#FF8A65", "routeGlow" to "#FF7043", "routeCore" to "#FFF3EC",
            "start" to "#00A35A", "end" to "#E0522A", "light" to "#FFFFFF",
        )
        RouteTheme.SUNSET -> mapOf(
            "land" to "#F1CDAE", "green" to "#E3C29A", "park" to "#DDB98E", "water" to "#6B4E71", "waterDeep" to "#2B3A55", "waterLabel" to "#E9D6EC",
            "hillShadow" to "#8E4E44", "hillHighlight" to "#FFE7CC", "hillAccent" to "#B77058",
            "roadMinor" to "#FBE3CC", "roadMajor" to "#FFEBD6", "roadCasing" to "#E0A884", "roadLight" to "#FFD9A8",
            "buildingLow" to "#F6D0AA", "buildingMid" to "#ECAE84", "buildingHigh" to "#D4876A", "boundary" to "#C99780",
            "label" to "#6B3A2E", "labelHalo" to "#FCE6D2", "sightText" to "#7A2F3A",
            "routeHalo" to "#FF9E3D", "routeGlow" to "#FFC15E", "routeCore" to "#FFF6DD",
            "start" to "#2E9E6B", "end" to "#C2453B", "light" to "#FFB27A",
        )
        RouteTheme.NIGHT -> mapOf(
            "land" to "#0F1626", "green" to "#121C2F", "park" to "#13223A", "water" to "#08213B", "waterDeep" to "#08213B", "waterLabel" to "#6E8FC7",
            "hillShadow" to "#000000", "hillHighlight" to "#2A3858", "hillAccent" to "#111A2C",
            "roadMinor" to "#1C2640", "roadMajor" to "#2F3D60", "roadCasing" to "#111829", "roadLight" to "#F0B45C",
            "buildingLow" to "#1F2742", "buildingMid" to "#4C4A6E", "buildingHigh" to "#F2C26B", "boundary" to "#2B3554",
            "label" to "#DCE4F7", "labelHalo" to "#0F1626", "sightText" to "#FFD98A",
            "routeHalo" to "#FFC94A", "routeGlow" to "#FFD978", "routeCore" to "#FFFBEA",
            "start" to "#3DDC97", "end" to "#FF6B6B", "light" to "#AFC2FF",
        )
    }
}

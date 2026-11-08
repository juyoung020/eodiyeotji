package app.eodiyeotji.ui.map

import android.content.Context
import app.eodiyeotji.core.RouteTheme
import app.eodiyeotji.core.SkyPhase
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

    suspend fun build(context: Context, theme: RouteTheme, routeGeojson: String): String = fill(context, palette(theme), routeGeojson)

    /** 그날로 돌아가기: 그날 하늘 때([SkyPhase], Rust가 도착 시각으로 정함)의 색. 새벽 · 푸른 시간은 따로 섞은 색을 쓴다. */
    suspend fun build(context: Context, sky: SkyPhase, routeGeojson: String): String = fill(
        context,
        when (sky) {
            SkyPhase.DAWN -> DAWN
            SkyPhase.DAY -> palette(RouteTheme.DAY)
            SkyPhase.GOLDEN -> palette(RouteTheme.SUNSET)
            SkyPhase.BLUE -> BLUE
            SkyPhase.NIGHT -> palette(RouteTheme.NIGHT)
        },
        routeGeojson,
    )

    private suspend fun fill(context: Context, colors: Map<String, String>, routeGeojson: String): String = withContext(Dispatchers.IO) {
        val t = template ?: context.assets.open(ASSET).bufferedReader().use { it.readText() }.also { template = it }
        var json = t
        for ((k, v) in colors) json = json.replace("{{$k}}", v)
        json.replace(ROUTE_SLOT, routeGeojson)
    }

    /** 새벽: 라벤더빛 땅 · 분홍으로 밝아 오는 산등성이 · 아직 불이 남은 높은 건물. */
    private val DAWN = mapOf(
        "land" to "#D9CCD6", "green" to "#C9C6CC", "park" to "#C2C2C6", "water" to "#7E86B0", "waterDeep" to "#3E4A78", "waterLabel" to "#F2E6F2",
        "hillShadow" to "#3E3358", "hillHighlight" to "#FFE1D6", "hillAccent" to "#7D6E8E", "hillRim" to "#FFB9A8", "coast" to "#FFC7B8",
        "roadMinor" to "#EADDE4", "roadMajor" to "#F6E9EE", "roadCasing" to "#B9A8BC", "roadLight" to "#FFD3B8",
        "buildingLow" to "#D7C9D6", "buildingMid" to "#B7A3C0", "buildingHigh" to "#F4C59A", "boundary" to "#A898B0",
        "label" to "#3D3150", "labelHalo" to "#F4ECF2", "sightText" to "#8A3A5A",
        "routeHalo" to "#FF9E8A", "routeGlow" to "#FFC2A0", "routeCore" to "#FFF4EC",
        "start" to "#2E9E6B", "end" to "#C2453B", "light" to "#FFE0D0",
    )

    /** 푸른 시간: 해가 진 직후 남보랏빛 땅 · 막 켜진 건물 불빛 · 수평선 쪽 남은 분홍. */
    private val BLUE = mapOf(
        "land" to "#2A3358", "green" to "#2B375A", "park" to "#2D3A5E", "water" to "#1B2A55", "waterDeep" to "#0F1A3A", "waterLabel" to "#AFC0EE",
        "hillShadow" to "#0B1026", "hillHighlight" to "#5A64A0", "hillAccent" to "#202A4E", "hillRim" to "#C77FA0", "coast" to "#6E8FE0",
        "roadMinor" to "#38426A", "roadMajor" to "#4A5480", "roadCasing" to "#1F2746", "roadLight" to "#FFC67A",
        "buildingLow" to "#3A4270", "buildingMid" to "#7B6E9E", "buildingHigh" to "#FFC874", "boundary" to "#4A5480",
        "label" to "#E6EAFB", "labelHalo" to "#2A3358", "sightText" to "#FFD3A0",
        "routeHalo" to "#FFB35A", "routeGlow" to "#FFD08A", "routeCore" to "#FFF8E8",
        "start" to "#3DDC97", "end" to "#FF6B6B", "light" to "#FFD9A0",
    )

    /** 때별 색. 낮 = 따뜻한 미색 땅·맑은 물·코랄 길, 노을 = 살구빛 땅·청록에서 보랏빛으로 저무는 물(먼 바다일수록 깊게)·금빛 길, 밤 = 남색 땅·불 켜진 건물·환한 길. */
    private fun palette(theme: RouteTheme): Map<String, String> = when (theme) {
        RouteTheme.DAY -> mapOf(
            "land" to "#ECE7DE", "green" to "#D3E6C6", "park" to "#C6E1BA", "water" to "#9CCFE6", "waterDeep" to "#9CCFE6", "waterLabel" to "#4F7FA8",
            "hillShadow" to "#7C7266", "hillHighlight" to "#FFFDF8", "hillAccent" to "#9A8E7E", "hillRim" to "#FFF4E0", "coast" to "#FFFFFF",
            "roadMinor" to "#FFFFFF", "roadMajor" to "#FFFCF5", "roadCasing" to "#D8D0C3", "roadLight" to "#FFFFFF",
            "buildingLow" to "#F2ECE3", "buildingMid" to "#E6DCCD", "buildingHigh" to "#D4C6B3", "boundary" to "#B5ADA2",
            "label" to "#3E3A35", "labelHalo" to "#FFFFFF", "sightText" to "#B4401F",
            "routeHalo" to "#FF8A65", "routeGlow" to "#FF7043", "routeCore" to "#FFF3EC",
            "start" to "#00A35A", "end" to "#E0522A", "light" to "#FFFFFF",
        )
        RouteTheme.SUNSET -> mapOf(
            "land" to "#F1CDAE", "green" to "#E3C29A", "park" to "#DDB98E", "water" to "#6B4E71", "waterDeep" to "#2B3A55", "waterLabel" to "#E9D6EC",
            "hillShadow" to "#6E3440", "hillHighlight" to "#FFE7CC", "hillAccent" to "#B77058", "hillRim" to "#FFB070", "coast" to "#FFD39A",
            "roadMinor" to "#FBE3CC", "roadMajor" to "#FFEBD6", "roadCasing" to "#E0A884", "roadLight" to "#FFD9A8",
            "buildingLow" to "#F6D0AA", "buildingMid" to "#F0A878", "buildingHigh" to "#FFC27A", "boundary" to "#C99780",
            "label" to "#6B3A2E", "labelHalo" to "#FCE6D2", "sightText" to "#7A2F3A",
            "routeHalo" to "#FF9E3D", "routeGlow" to "#FFC15E", "routeCore" to "#FFF6DD",
            "start" to "#2E9E6B", "end" to "#C2453B", "light" to "#FFB27A",
        )
        RouteTheme.NIGHT -> mapOf(
            "land" to "#0F1626", "green" to "#121C2F", "park" to "#13223A", "water" to "#08213B", "waterDeep" to "#08213B", "waterLabel" to "#6E8FC7",
            "hillShadow" to "#000000", "hillHighlight" to "#34477A", "hillAccent" to "#111A2C", "hillRim" to "#4A3E6E", "coast" to "#3E6FC8",
            "roadMinor" to "#1C2640", "roadMajor" to "#2F3D60", "roadCasing" to "#111829", "roadLight" to "#F0B45C",
            "buildingLow" to "#1F2742", "buildingMid" to "#6A5A70", "buildingHigh" to "#FFD27A", "boundary" to "#2B3554",
            "label" to "#DCE4F7", "labelHalo" to "#0F1626", "sightText" to "#FFD98A",
            "routeHalo" to "#FFC94A", "routeGlow" to "#FFD978", "routeCore" to "#FFFBEA",
            "start" to "#3DDC97", "end" to "#FF6B6B", "light" to "#AFC2FF",
        )
    }
}

package app.eodiyeotji.ui.map

import android.content.Context
import android.util.Log
import app.eodiyeotji.BuildConfig
import androidx.compose.runtime.Immutable
import java.io.File
import java.io.IOException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 지도에 넣을 스타일.
 * - [Offline]: 기기 안 지도 파일 + (있으면) 그 아래 온라인 밑그림. 지도 파일 밖 지역(강원 북부·남해·제주 등)도 회색이 아니라 지도로 보인다.
 * - [Online]: 지도 파일이 없을 때 온라인 밑그림만.
 * - [Fallback]: 둘 다 못 쓸 때 단색 바탕 + 경로·핀만.
 */
@Immutable
internal sealed interface MapStyleSpec {
    /** 오프라인 스타일 JSON. [offlineOnly]: 온라인 밑그림을 뺀 같은 스타일 (네트워크 오류로 불러오기에 실패하면 이걸로 바꾼다). */
    data class Offline(val json: String, val offlineOnly: String? = null) : MapStyleSpec

    /** 지도 파일 없음 → 온라인 밑그림만 (네트워크가 없으면 바탕색만 보인다). */
    data class Online(val json: String) : MapStyleSpec

    /** 바탕색만 칠한다. 경로·핀은 그대로 그려진다. */
    data object Fallback : MapStyleSpec
}

/**
 * 지도 스타일 찾기. 파일 확인·asset 읽기는 디스크 I/O라 IO 스레드에서 한다 (UI 스레드 블로킹 0).
 *
 * 계약 (지도 데이터 담당과 맞춘 것):
 * - 스타일: `assets/map/style.json`. 벡터 소스 `protomaps`의 `"url": "{{PMTILES_URL}}"`를
 *   `pmtiles://file://<절대 경로>`로 바꾼다. 글꼴·스프라이트는 스타일 안에서 `asset://map/...`로 가리킨다.
 * - 지도 파일: `getExternalFilesDir(null)/map/korea-demo.pmtiles` → 없으면 `filesDir/map/korea-demo.pmtiles`.
 * - 온라인 밑그림: `assets/map/online-underlay.json` (OpenFreeMap 벡터 타일, 키 없음). 배경 바로 위·오프라인 레이어 아래에 끼운다.
 *   오프라인 지도가 있는 곳은 오프라인 레이어가 덮어서 시연 지역 모습은 그대로고, 없는 곳만 온라인 지도가 보인다.
 *   배경색은 땅 색(오프라인 `earth`)으로 바꿔 두 지도의 경계가 티 나지 않게 한다.
 */
internal object MapStyleSource {
    private const val TAG = "EodiMap"
    private const val STYLE_ASSET = "map/style.json"
    private const val UNDERLAY_ASSET = "map/online-underlay.json"
    private const val MAP_FILE = "map/korea-demo.pmtiles"
    private const val PLACEHOLDER = "{{PMTILES_URL}}"
    private const val GLYPHS = "asset://map/fonts/{fontstack}/{range}.pbf"
    private const val ONLINE_SOURCE = "ofm"
    private const val LAND = "#1B1E22"

    /**
     * 밤 지도: 스타일 파일(Protomaps 밝은 지도)은 그대로 두고, 읽을 때 색만 어두운 앨범 바탕에 맞게 바꾼다.
     * 땅 · 물 · 길은 검은 종이에 가까운 회색, 글자는 밝은 회색 (달린 길의 금색이 가장 눈에 띄게).
     */
    private val NIGHT = listOf(
        "#cccccc" to "#15181B", "#e2dfda" to "#1B1E22", "#9cd3b4" to "#1F2C26", "#e8e4d0" to "#2B2A24",
        "#dadbdf" to "#22262A", "#e9e9ed" to "#2C3035", "#80deea" to "#10242F", "#e0e0e0" to "#14171A",
        "#ffffff" to "#3A3F45", "#ebebeb" to "#30353A", "#a7b1b3" to "#3C4246", "#adadad" to "#4A5056",
        "#728dd4" to "#6F93B5", "#91888b" to "#8D949C", "#938a8d" to "#8D949D", "#8f8f8f" to "#9AA1A8",
        "#b3b3b3" to "#7D848C", "#5c5c5c" to "#D5D8DA", "#a3a3a3" to "#9AA1A9",
        "rgba(210, 239, 207, 1)" to "#1F2A22", "rgba(255, 243, 215, 1)" to "#2A2820", "rgba(230, 230, 230, 1)" to "#1F2327",
        "rgba(216, 239, 210, 1)" to "#202A22", "rgba(255, 255, 255, 1)" to "#2C3036", "rgba(234, 239, 210, 1)" to "#24291F",
        "rgba(196, 231, 210, 1)" to "#1C2A24",
        "#1A8CBD" to "#4FB3DD", "#20834D" to "#4FB47E", "#315BCF" to "#7C9BEA", "#6A5B8F" to "#A493CC", "#CB6704" to "#E59A4A",
    )

    private fun night(json: String): String = NIGHT.fold(json) { s, (from, to) -> s.replace(from, to) }

    /** 마지막으로 만든 스타일과 그때의 지도 파일 상태. 지도를 열 때마다 asset 읽기·JSON 합치기(수십 ms)를 다시 하지 않는다. */
    @Volatile private var cached: Pair<String, MapStyleSpec>? = null

    /** 지도 파일이 그대로면(경로·크기·수정 시각) 만들어 둔 스타일을 바로 돌려준다. 파일을 새로 넣으면 다시 만든다. */
    suspend fun resolve(context: Context): MapStyleSpec = withContext(Dispatchers.IO) {
        val file = mapFile(context)
        val stamp = file?.let { "${it.absolutePath}:${it.length()}:${it.lastModified()}" } ?: "none"
        cached?.takeIf { it.first == stamp }?.let { return@withContext it.second }
        val t0 = System.nanoTime()
        build(context, file).also {
            cached = stamp to it
            if (BuildConfig.DEBUG) Log.d(TAG, "스타일 만들기 ${(System.nanoTime() - t0) / 1_000_000}ms")
        }
    }

    private fun build(context: Context, file: File?): MapStyleSpec {
        val underlay = readAsset(context, UNDERLAY_ASSET)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (file == null) {
            Log.i(TAG, "오프라인 지도 파일 없음 → 온라인 밑그림")
            return underlay?.let { MapStyleSpec.Online(onlineOnly(it)) } ?: MapStyleSpec.Fallback
        }
        val template = readAsset(context, STYLE_ASSET)
        if (template == null || !template.contains(PLACEHOLDER)) {
            Log.w(TAG, "스타일 asset이 없거나 $PLACEHOLDER 자리가 없음 → 온라인 밑그림")
            return underlay?.let { MapStyleSpec.Online(onlineOnly(it)) } ?: MapStyleSpec.Fallback
        }
        Log.i(TAG, "오프라인 지도: ${file.absolutePath} (${file.length() / 1_048_576}MB)")
        val offline = template.replace(PLACEHOLDER, "pmtiles://file://${file.absolutePath}")
        val hybrid = underlay?.let { u ->
            runCatching { withUnderlay(offline, u) }.onFailure { Log.w(TAG, "밑그림 끼우기 실패", it) }.getOrNull()
        }
        return if (hybrid != null) MapStyleSpec.Offline(hybrid, offlineOnly = offline) else MapStyleSpec.Offline(offline)
    }

    private fun readAsset(context: Context, path: String): String? = try {
        night(context.assets.open(path).bufferedReader().use { it.readText() })
    } catch (e: IOException) {
        Log.w(TAG, "asset 없음 ($path)", e)
        null
    }

    /** 오프라인 스타일의 배경 바로 위에 온라인 밑그림 소스·레이어를 끼운다. 배경이 없으면 그대로 돌려준다. */
    private fun withUnderlay(styleJson: String, underlay: JSONObject): String {
        val style = JSONObject(styleJson)
        val layers = style.getJSONArray("layers")
        val merged = JSONArray()
        var inserted = false
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            merged.put(layer)
            if (!inserted && layer.optString("type") == "background") {
                layer.optJSONObject("paint")?.put("background-color", LAND)
                underlayLayers(underlay).forEach { merged.put(it) }
                inserted = true
            }
        }
        if (!inserted) return styleJson
        style.getJSONObject("sources").put(ONLINE_SOURCE, underlay.getJSONObject("source"))
        style.put("layers", merged)
        return style.toString()
    }

    /** 지도 파일이 없을 때: 땅 색 배경 + 온라인 밑그림. 글꼴은 APK 안 것. */
    private fun onlineOnly(underlay: JSONObject): String {
        val background = JSONObject()
            .put("id", "background")
            .put("type", "background")
            .put("paint", JSONObject().put("background-color", LAND))
        val layers = JSONArray().put(background)
        underlayLayers(underlay).forEach { layers.put(it) }
        return JSONObject()
            .put("version", 8)
            .put("name", "eodi-online")
            .put("glyphs", GLYPHS)
            .put("sources", JSONObject().put(ONLINE_SOURCE, underlay.getJSONObject("source")))
            .put("layers", layers)
            .toString()
    }

    private fun underlayLayers(underlay: JSONObject): List<JSONObject> {
        val arr = underlay.getJSONArray("layers")
        return (0 until arr.length()).map { arr.getJSONObject(it).put("source", ONLINE_SOURCE) }
    }

    /** 바깥 저장소(adb push로 넣기 쉬움) → 앱 내부 저장소 순서로 찾는다. */
    private fun mapFile(context: Context): File? {
        context.getExternalFilesDir(null)?.let { dir ->
            File(dir, MAP_FILE).takeIf { it.isFile && it.length() > 0 }?.let { return it }
        }
        return File(context.filesDir, MAP_FILE).takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * 지도 파일이 없을 때 쓰는 스타일: 바탕색 한 장 (네트워크 없이 바로 뜬다).
     * 글꼴(glyphs)은 APK 안 것을 가리킨다 → 지도 파일이 없어도 머문 곳 순서 숫자·이름이 그려진다.
     */
    fun fallbackJson(backgroundArgb: Int): String {
        val rgb = String.format(Locale.ROOT, "#%06X", backgroundArgb and 0xFFFFFF)
        return """{"version":8,"name":"eodi-fallback","glyphs":"$GLYPHS","sources":{},"layers":[""" +
            """{"id":"background","type":"background","paint":{"background-color":"$rgb"}}]}"""
    }
}

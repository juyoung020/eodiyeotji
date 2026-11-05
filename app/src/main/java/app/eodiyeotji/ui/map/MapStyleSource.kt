package app.eodiyeotji.ui.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 지도에 넣을 스타일. [Offline]이 기본이고, 지도 파일이나 스타일이 없으면 [Fallback](단색 바탕 + 경로·핀만). */
@Immutable
internal sealed interface MapStyleSpec {
    /** 기기 안 PMTiles 지도 파일을 읽는 스타일 JSON (자리표시자를 실제 파일 경로로 바꾼 것). */
    data class Offline(val json: String) : MapStyleSpec

    /** 지도 파일 없음 → 바탕색만 칠한다. 경로·핀은 그대로 그려진다. */
    data object Fallback : MapStyleSpec
}

/**
 * 오프라인 지도 스타일 찾기. 파일 확인·asset 읽기는 디스크 I/O라 IO 스레드에서 한다 (UI 스레드 블로킹 0).
 *
 * 계약 (지도 데이터 담당과 맞춘 것):
 * - 스타일: `assets/map/style.json`. 벡터 소스 `protomaps`의 `"url": "{{PMTILES_URL}}"`를
 *   `pmtiles://file://<절대 경로>`로 바꾼다. 글꼴·스프라이트는 스타일 안에서 `asset://map/...`로 가리킨다.
 * - 지도 파일: `getExternalFilesDir(null)/map/korea-demo.pmtiles` → 없으면 `filesDir/map/korea-demo.pmtiles`.
 */
internal object MapStyleSource {
    private const val TAG = "EodiMap"
    private const val STYLE_ASSET = "map/style.json"
    private const val MAP_FILE = "map/korea-demo.pmtiles"
    private const val PLACEHOLDER = "{{PMTILES_URL}}"

    suspend fun resolve(context: Context): MapStyleSpec = withContext(Dispatchers.IO) {
        val file = mapFile(context)
        if (file == null) {
            Log.i(TAG, "오프라인 지도 파일 없음 → 바탕색 스타일")
            return@withContext MapStyleSpec.Fallback
        }
        val template = try {
            context.assets.open(STYLE_ASSET).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.w(TAG, "스타일 asset 없음 ($STYLE_ASSET) → 바탕색 스타일", e)
            return@withContext MapStyleSpec.Fallback
        }
        if (!template.contains(PLACEHOLDER)) {
            Log.w(TAG, "스타일에 $PLACEHOLDER 자리가 없음 → 바탕색 스타일")
            return@withContext MapStyleSpec.Fallback
        }
        Log.i(TAG, "오프라인 지도: ${file.absolutePath} (${file.length() / 1_048_576}MB)")
        MapStyleSpec.Offline(template.replace(PLACEHOLDER, "pmtiles://file://${file.absolutePath}"))
    }

    /** 바깥 저장소(adb push로 넣기 쉬움) → 앱 내부 저장소 순서로 찾는다. */
    private fun mapFile(context: Context): File? {
        context.getExternalFilesDir(null)?.let { dir ->
            File(dir, MAP_FILE).takeIf { it.isFile && it.length() > 0 }?.let { return it }
        }
        return File(context.filesDir, MAP_FILE).takeIf { it.isFile && it.length() > 0 }
    }

    /** 지도 파일이 없을 때 쓰는 스타일: 바탕색 한 장 (네트워크 없이 바로 뜬다). */
    fun fallbackJson(backgroundArgb: Int): String {
        val rgb = String.format(Locale.ROOT, "#%06X", backgroundArgb and 0xFFFFFF)
        return """{"version":8,"name":"eodi-fallback","sources":{},"layers":[""" +
            """{"id":"background","type":"background","paint":{"background-color":"$rgb"}}]}"""
    }
}

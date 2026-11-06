package app.eodiyeotji.ui.places

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 기록 고치기 · 내 장소 아이콘 (24 격자, 선 2.25, 둥근 끝 — EodiIcons와 같은 규칙). */
object PlacesIcons {
    private val Ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 2.25f, b: PathBuilder.() -> Unit) =
        path(stroke = Ink, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = b)

    private fun PathBuilder.dot(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy); arcToRelative(r, r, 0f, true, true, 2 * r, 0f); arcToRelative(r, r, 0f, true, true, -2 * r, 0f); close()
    }

    /** ⋯ (점 셋) */
    val More: ImageVector by lazy { icon("More") { path(fill = Ink) { dot(5f, 12f, 2f); dot(12f, 12f, 2f); dot(19f, 12f, 2f) } } }

    /** 집 */
    val Home: ImageVector by lazy {
        icon("Home") { stroke { moveTo(3.5f, 11f); lineTo(12f, 4f); lineTo(20.5f, 11f); moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f); moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f) } }
    }

    /** 회사 (건물) */
    val Office: ImageVector by lazy {
        icon("Office") {
            stroke {
                moveTo(5f, 20.5f); lineTo(5f, 4f); lineTo(15f, 4f); lineTo(15f, 20.5f); moveTo(15f, 9f); lineTo(19f, 9f); lineTo(19f, 20.5f)
                moveTo(3f, 20.5f); lineTo(21f, 20.5f); moveTo(8.5f, 8f); lineTo(11.5f, 8f); moveTo(8.5f, 12f); lineTo(11.5f, 12f); moveTo(8.5f, 16f); lineTo(11.5f, 16f)
            }
        }
    }

    /** 숨기기 (눈 + 사선) */
    val Hide: ImageVector by lazy {
        icon("Hide") {
            stroke {
                moveTo(2.5f, 12f); curveTo(5f, 7f, 8.5f, 5f, 12f, 5f); curveTo(15.5f, 5f, 19f, 7f, 21.5f, 12f)
                curveTo(19f, 17f, 15.5f, 19f, 12f, 19f); curveTo(8.5f, 19f, 5f, 17f, 2.5f, 12f); close()
                moveTo(4f, 20f); lineTo(20f, 4f)
            }
        }
    }

    /** 지우기 (휴지통) */
    val Trash: ImageVector by lazy {
        icon("Trash") {
            stroke {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f); moveTo(9f, 6.5f); lineTo(9.5f, 3.5f); lineTo(14.5f, 3.5f); lineTo(15f, 6.5f)
                moveTo(6f, 6.5f); lineTo(7f, 20.5f); lineTo(17f, 20.5f); lineTo(18f, 6.5f); moveTo(10f, 10.5f); lineTo(10f, 16.5f); moveTo(14f, 10.5f); lineTo(14f, 16.5f)
            }
        }
    }

    /** 되살리기 (되돌아가는 화살표) */
    val Restore: ImageVector by lazy {
        icon("Restore") {
            stroke {
                moveTo(4f, 12f); curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f); curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f); curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
                curveTo(9.5f, 20f, 7.3f, 18.9f, 5.8f, 17.1f); moveTo(4f, 7f); lineTo(4f, 12f); lineTo(9f, 12f)
            }
        }
    }
}

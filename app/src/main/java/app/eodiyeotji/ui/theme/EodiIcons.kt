package app.eodiyeotji.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 앱이 쓰는 아이콘 (아이콘 라이브러리 없이 직접 그린 벡터). 24 단위 격자, 선 굵기 2.25, 둥근 끝.
 * 색은 Icon(tint)로 칠한다 → path 색은 아무 값(검정)이어도 된다.
 */
object EodiIcons {
    private val Ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 2.25f, pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(stroke = Ink, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = pathBuilder)

    /** 뒤로 (목록으로) */
    val Back: ImageVector by lazy {
        icon("Back") { stroke(2.6f) { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) } }
    }

    /** 오른쪽 꺾쇠 (좁은 창에서 목록 행 끝) */
    val Chevron: ImageVector by lazy {
        icon("Chevron") { stroke(2.4f) { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) } }
    }

    /** 위치 핀 (머문 곳 · 앱 표시) — 채운 모양, 가운데 구멍 */
    val Pin: ImageVector by lazy {
        icon("Pin") {
            path(fill = Ink, pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                curveTo(7.86f, 2f, 4.5f, 5.3f, 4.5f, 9.4f)
                curveTo(4.5f, 14.9f, 12f, 22f, 12f, 22f)
                curveTo(12f, 22f, 19.5f, 14.9f, 19.5f, 9.4f)
                curveTo(19.5f, 5.3f, 16.14f, 2f, 12f, 2f)
                close()
                moveTo(12f, 12.4f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
                arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
                close()
            }
        }
    }

    /** 내비 (위를 향한 화살촉) */
    val Navi: ImageVector by lazy {
        icon("Navi") {
            path(fill = Ink) {
                moveTo(12f, 2.5f); lineTo(20.5f, 21f); lineTo(12f, 16.8f); lineTo(3.5f, 21f); close()
            }
        }
    }

    /** 반짝임 (기억 · AI 회상). 큰 별 하나 + 작은 별 하나, 채운 모양 */
    val Sparkle: ImageVector by lazy {
        icon("Sparkle") {
            path(fill = Ink) {
                moveTo(10f, 3f)
                curveTo(10.6f, 7.4f, 12.6f, 9.4f, 17f, 10f)
                curveTo(12.6f, 10.6f, 10.6f, 12.6f, 10f, 17f)
                curveTo(9.4f, 12.6f, 7.4f, 10.6f, 3f, 10f)
                curveTo(7.4f, 9.4f, 9.4f, 7.4f, 10f, 3f)
                close()
                moveTo(18f, 14f)
                curveTo(18.3f, 16.1f, 18.9f, 16.7f, 21f, 17f)
                curveTo(18.9f, 17.3f, 18.3f, 17.9f, 18f, 20f)
                curveTo(17.7f, 17.9f, 17.1f, 17.3f, 15f, 17f)
                curveTo(17.1f, 16.7f, 17.7f, 16.1f, 18f, 14f)
                close()
            }
        }
    }

    /** 다시 불러오기 */
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            stroke(2.4f) {
                moveTo(19.5f, 12f)
                arcToRelative(7.5f, 7.5f, 0f, true, true, -2.2f, -5.3f)
                moveTo(19.5f, 4.5f); lineTo(19.5f, 8.5f); lineTo(15.5f, 8.5f)
            }
        }
    }

    /** 문제가 생김 (원 안 느낌표) */
    val Alert: ImageVector by lazy {
        icon("Alert") {
            stroke(2.2f) {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
                arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
                close()
                moveTo(12f, 7.5f); lineTo(12f, 13f)
            }
            path(fill = Ink) {
                moveTo(12f, 15.2f)
                arcToRelative(1.35f, 1.35f, 0f, true, true, 0f, 2.7f)
                arcToRelative(1.35f, 1.35f, 0f, true, true, 0f, -2.7f)
                close()
            }
        }
    }

    /** 재생 (그날로 돌아가기) — 채운 삼각형 */
    val Play: ImageVector by lazy {
        icon("Play") {
            path(fill = Ink) { moveTo(7.5f, 4.8f); lineTo(19f, 12f); lineTo(7.5f, 19.2f); close() }
        }
    }

    /** 멈춤 — 채운 막대 두 개 */
    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = Ink) {
                moveTo(6.5f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6.5f, 19f); close()
                moveTo(14f, 5f); lineTo(17.5f, 5f); lineTo(17.5f, 19f); lineTo(14f, 19f); close()
            }
        }
    }
}

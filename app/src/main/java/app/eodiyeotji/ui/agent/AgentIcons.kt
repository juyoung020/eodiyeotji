package app.eodiyeotji.ui.agent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import app.eodiyeotji.ui.theme.EodiIcons

/**
 * 기억 에이전트 화면 아이콘 (24×24 격자에 직접 그린 벡터). 색은 Icon의 tint가 칠한다.
 * 아이콘 라이브러리를 더 넣지 않으려고 필요한 몇 개만 여기 둔다.
 */
internal object AgentIcons {

    /** 마이크 (말로 묻기). */
    val Mic: ImageVector by lazy {
        icon("AgentMic") {
            fill {
                moveTo(12f, 2.5f)
                curveTo(10.07f, 2.5f, 8.5f, 4.07f, 8.5f, 6f)
                verticalLineTo(11.5f)
                curveTo(8.5f, 13.43f, 10.07f, 15f, 12f, 15f)
                curveTo(13.93f, 15f, 15.5f, 13.43f, 15.5f, 11.5f)
                verticalLineTo(6f)
                curveTo(15.5f, 4.07f, 13.93f, 2.5f, 12f, 2.5f)
                close()
            }
            stroke {
                moveTo(5.5f, 11f)
                curveTo(5.5f, 14.6f, 8.4f, 17.5f, 12f, 17.5f)
                curveTo(15.6f, 17.5f, 18.5f, 14.6f, 18.5f, 11f)
                moveTo(12f, 17.5f)
                verticalLineTo(21f)
            }
        }
    }

    /** 스피커 (읽는 말 자막). */
    val Speaker: ImageVector by lazy {
        icon("AgentSpeaker") {
            fill {
                moveTo(4f, 9f)
                horizontalLineTo(8f)
                lineTo(13f, 4.5f)
                verticalLineTo(19.5f)
                lineTo(8f, 15f)
                horizontalLineTo(4f)
                close()
            }
            stroke {
                moveTo(16.5f, 8.5f)
                curveTo(17.8f, 10.5f, 17.8f, 13.5f, 16.5f, 15.5f)
                moveTo(19f, 6f)
                curveTo(21.3f, 9.5f, 21.3f, 14.5f, 19f, 18f)
            }
        }
    }




    /** 번개 (가장 빠른 순서). */
    val Fast: ImageVector by lazy {
        icon("AgentFast") {
            fill {
                moveTo(13.5f, 2f)
                lineTo(5f, 13.5f)
                horizontalLineTo(11f)
                lineTo(10f, 22f)
                lineTo(19f, 10f)
                horizontalLineTo(13f)
                close()
            }
        }
    }




    /** 오른쪽 화살표 (질문 보내기). */
    val Send: ImageVector by lazy {
        icon("AgentSend") {
            stroke {
                moveTo(4f, 12f); horizontalLineTo(19f)
                moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
            }
        }
    }

    /** 닫기 · 안내 화살표는 앱 공통 아이콘과 같은 것 ([EodiIcons]). */
    val Close: ImageVector get() = EodiIcons.Close
    val Navigate: ImageVector get() = EodiIcons.Navi

    // ---- 그리기 도우미 ----

    private class IconScope(val builder: ImageVector.Builder) {
        fun fill(type: PathFillType = PathFillType.NonZero, block: PathBuilder.() -> Unit) {
            builder.path(fill = SolidColor(Color.Black), pathFillType = type, pathBuilder = block)
        }

        fun stroke(block: PathBuilder.() -> Unit) {
            builder.path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }
    }

    private fun icon(name: String, block: IconScope.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        IconScope(b).block()
        return b.build()
    }
}

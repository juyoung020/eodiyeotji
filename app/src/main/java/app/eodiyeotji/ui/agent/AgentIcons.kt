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

    /** 닫기 ×. */
    val Close: ImageVector by lazy {
        icon("AgentClose") {
            stroke {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    /** 반짝임 (AI 답). 큰 별 하나 + 작은 별 하나. */
    val Sparkle: ImageVector by lazy {
        icon("AgentSparkle") {
            fill {
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

    /** 내비 화살표 (안내). */
    val Navigate: ImageVector by lazy {
        icon("AgentNavigate") {
            fill {
                moveTo(12f, 2.5f)
                lineTo(20f, 21f)
                lineTo(12f, 17f)
                lineTo(4f, 21f)
                close()
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

    /** 장소 핀 (여기만 가기). */
    val Pin: ImageVector by lazy {
        icon("AgentPin") {
            fill(PathFillType.EvenOdd) {
                moveTo(12f, 2f)
                curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
                curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
                curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
                curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
                close()
                moveTo(12f, 6.5f)
                curveTo(10.62f, 6.5f, 9.5f, 7.62f, 9.5f, 9f)
                curveTo(9.5f, 10.38f, 10.62f, 11.5f, 12f, 11.5f)
                curveTo(13.38f, 11.5f, 14.5f, 10.38f, 14.5f, 9f)
                curveTo(14.5f, 7.62f, 13.38f, 6.5f, 12f, 6.5f)
                close()
            }
        }
    }

    /** 달력 (다른 날도 보기). */
    val Calendar: ImageVector by lazy {
        icon("AgentCalendar") {
            stroke {
                moveTo(5.5f, 5f)
                horizontalLineTo(18.5f)
                curveTo(19.6f, 5f, 20.5f, 5.9f, 20.5f, 7f)
                verticalLineTo(19f)
                curveTo(20.5f, 20.1f, 19.6f, 21f, 18.5f, 21f)
                horizontalLineTo(5.5f)
                curveTo(4.4f, 21f, 3.5f, 20.1f, 3.5f, 19f)
                verticalLineTo(7f)
                curveTo(3.5f, 5.9f, 4.4f, 5f, 5.5f, 5f)
                close()
                moveTo(3.5f, 10f); horizontalLineTo(20.5f)
                moveTo(8f, 3f); verticalLineTo(7f)
                moveTo(16f, 3f); verticalLineTo(7f)
            }
        }
    }

    /** 체크 (안내 시작됨). */
    val Check: ImageVector by lazy {
        icon("AgentCheck") {
            stroke {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 7f)
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

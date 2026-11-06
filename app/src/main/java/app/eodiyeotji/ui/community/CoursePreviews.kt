package app.eodiyeotji.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.eodiyeotji.core.CoursePost
import app.eodiyeotji.core.DriveCourse
import app.eodiyeotji.core.StopFact
import app.eodiyeotji.core.Visit
import app.eodiyeotji.ui.theme.EodiTheme

// 드라이브 코스 글 카드 미리보기 전용 예시 (사진은 미리보기에서 핀 자리로 그려진다). 크기는 Pleos 앱 영역(넓게 1682, 나눈 창 841).

private const val PreviewNow = 1_793_900_000_000L
private const val Day = 86_400_000L

private fun stop(name: String, category: String, lat: Double, lon: Double) = StopFact(name, category, "", 0, 0, lat, lon, false)

private val Gangneung = listOf(
    stop("경포해변", "해수욕장", 37.8055, 128.9083),
    stop("주문진수산시장", "전통시장", 37.8906, 128.8296),
    stop("하조대", "관광명소", 38.0217, 128.7243),
    stop("낙산사", "사찰", 38.1246, 128.6290),
    stop("속초중앙시장", "전통시장", 38.2045, 128.5903),
)

private val SampleCourses = listOf(
    DriveCourse(
        CoursePost(
            "c1", "7번 국도 따라 동해안 한 바퀴", "아침 일찍 경포에서 출발해서 바다 보면서 천천히 올라갔어요. 주문진 시장 회는 꼭 드세요! 하조대 정자에서 보는 바다가 제일 좋았어요.",
            "바다좋아", false, "강원 강릉시", listOf("해안도로", "회", "가을"), Gangneung.take(4),
            56_200.0, 4 * 3_600_000L, PreviewNow - 4 * Day, 128, true,
        ),
        Visit("2026-10-28", "가을 · 단풍철", "맑음 · 14°C"),
    ),
    DriveCourse(
        CoursePost(
            "c2", "비 오는 날 속초까지", "", "나", true, "강원 속초시", emptyList(), Gangneung,
            98_400.0, 6 * 3_600_000L, PreviewNow - 2 * 3_600_000L, 3, false,
        ),
        Visit("2026-11-06", "가을", "비 · 9°C"),
    ),
    DriveCourse(
        CoursePost(
            "c3", "경포 한 곳만", "짧게 바다만 보고 왔어요.", "드라이버K", false, "강원 강릉시", emptyList(), Gangneung.take(1),
            12_000.0, 3_600_000L, PreviewNow - 20 * Day, 41, false,
        ),
        Visit("2026-10-18", "가을", null),
    ),
)

@Composable
private fun PreviewFeed(dark: Boolean = false) {
    EodiTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(EodiTheme.colors.background)) {
            CourseGrid(SampleCourses, onOpen = {}, onHeart = {}, onOpenPlace = {})
        }
    }
}

@Preview(name = "코스 글 피드 (넓은 창)", widthDp = 1682, heightDp = 1210)
@Composable
private fun CourseFeedWidePreview() = PreviewFeed()

@Preview(name = "코스 글 피드 (나눈 창)", widthDp = 841, heightDp = 1210)
@Composable
private fun CourseFeedNarrowPreview() = PreviewFeed()

@Preview(name = "코스 글 피드 (넓은 창, 다크)", widthDp = 1682, heightDp = 1210)
@Composable
private fun CourseFeedDarkPreview() = PreviewFeed(dark = true)

@Preview(name = "코스 글 카드 한 장", widthDp = 640, heightDp = 1000)
@Composable
private fun CoursePostCardPreview() {
    EodiTheme {
        Box(Modifier.background(EodiTheme.colors.background).padding(24.dp)) {
            Box(Modifier.width(560.dp)) {
                CoursePostCard(SampleCourses.first(), onOpen = {}, onHeart = {}, onOpenPlace = {})
            }
        }
    }
}

package app.eodiyeotji.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/**
 * 오늘 날짜. 자정이 지나면 새 날짜로 바뀐다 ("오늘/어제" 표시가 화면을 켜 둔 채 날을 넘겨도 맞게).
 * 다음 자정까지 한 번만 기다린다 (매초 깨지 않는다).
 */
@Composable
fun rememberToday(): State<LocalDate> = produceState(LocalDate.now()) {
    while (true) {
        delay(millisUntilNextMidnight(ZonedDateTime.now(ZoneId.systemDefault())))
        value = LocalDate.now()
    }
}

/** [now]에서 다음 자정(그 시간대 기준)까지 남은 밀리초. 자정을 막 넘긴 직후 다시 깨도록 조금(50ms) 더한다. */
internal fun millisUntilNextMidnight(now: ZonedDateTime): Long {
    val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return Duration.between(now, next).toMillis().coerceAtLeast(0) + 50
}

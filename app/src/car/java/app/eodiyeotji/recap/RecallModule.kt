package app.eodiyeotji.recap

import app.eodiyeotji.core.OnThisDay
import app.eodiyeotji.core.Recall
import app.eodiyeotji.platform.SpeechInput
import app.eodiyeotji.platform.SpeechOutput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** 차 판의 회상 내보내기. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecallModule {
    @Binds abstract fun output(impl: CarRecallOutput): RecallOutput
}

/**
 * 차: 한 줄을 짧게 읽는다 (Pleos TextToSpeech). 카드는 주행 화면이 [RecallCenter.current]로 그린다.
 * 내비 안내가 말하는 중이면 TTS가 오디오 포커스를 기다렸다가(최대 8초) 읽거나 버린다 → 겹치지 않는다 (`PleosTts`).
 * 에이전트가 답을 읽거나 마이크가 듣는 중이면 끼어들지 않고 false (다음 묶음에 다시).
 */
@Singleton
class CarRecallOutput @Inject constructor(
    private val speech: SpeechOutput,
    private val listen: SpeechInput,
) : RecallOutput {
    override suspend fun showRecall(recall: Recall): Boolean = say(recall.line)

    override suspend fun showOnThisDay(otd: OnThisDay): Boolean = say(otd.line)

    private fun say(line: String): Boolean {
        if (speech.speaking.value || listen.state.value.listening) return false
        speech.speak(line)
        return true
    }
}

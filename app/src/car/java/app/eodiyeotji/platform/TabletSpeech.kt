package app.eodiyeotji.platform

import android.content.Context
import android.content.pm.PackageManager
import app.eodiyeotji.pleos.PleosSpeech
import app.eodiyeotji.pleos.PleosTts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 차 판의 말로 묻기. 진짜 차(자동차용 안드로이드)에서는 Pleos 음성 인식([PleosSpeech]) 그대로이고,
 * 차가 아닌 기기(태블릿에 차 판을 깔았을 때)에는 Pleos 음성이 없으므로 Android 표준 음성 인식([AndroidSpeechInput])을 쓴다.
 * 쓰는 쪽 하나만 만든다 (둘 다 만들면 디버그 음성 입력을 둘이 같이 받는다).
 */
@Singleton
class CarSpeechInput @Inject constructor(
    @ApplicationContext context: Context,
    pleos: Provider<PleosSpeech>,
    tablet: Provider<AndroidSpeechInput>,
) : SpeechInput by (if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) pleos.get() else tablet.get())

/** 차 판의 답 읽어 주기. 진짜 차에서는 Pleos 음성([PleosTts]), 차가 아닌 기기에서는 Android 표준 음성([AndroidSpeechOutput]). */
@Singleton
class CarSpeechOutput @Inject constructor(
    @ApplicationContext context: Context,
    pleos: Provider<PleosTts>,
    tablet: Provider<AndroidSpeechOutput>,
) : SpeechOutput by (if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) pleos.get() else tablet.get())

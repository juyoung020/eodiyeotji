package app.eodiyeotji.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * [디버그 빌드 전용] 시연 녹화용: 앱 음성과 같은 출력(비서 음성 `USAGE_ASSISTANT`)을 무음으로 계속 열어 둔다.
 *
 * 에뮬레이터 오디오 장치는 쉬다가(standby) 앱이 말을 시작할 때 다시 열리는데, 그 첫 0.1~0.3초가
 * "Not supplying enough data to HAL"로 끊겨 녹음된다. 무음을 흘려 출력을 깨워 두면 첫머리가 끊기지 않는다.
 * 오디오 포커스는 잡지 않으므로 앱 음성 · 내비 음성에 영향이 없다.
 *
 * ```
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.record.AudioKeepAliveReceiver --ez on true
 * adb shell am broadcast --user 10 -n app.eodiyeotji/.record.AudioKeepAliveReceiver --ez on false
 * ```
 */
class AudioKeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("on", true)) start() else stop()
    }

    private companion object {
        const val TAG = "EodiKeepAlive"
        @Volatile var thread: Thread? = null

        @Synchronized fun start() {
            if (thread != null) return
            thread = Thread({
                val rate = 48000
                val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                    )
                    .setBufferSizeInBytes(min * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                val zeros = ByteArray(min)
                runCatching {
                    track.play()
                    Log.i(TAG, "무음 출력 시작")
                    while (!Thread.currentThread().isInterrupted) track.write(zeros, 0, zeros.size)
                }
                runCatching { track.stop() }
                track.release()
                Log.i(TAG, "무음 출력 끝")
            }, "EodiKeepAlive").apply { isDaemon = true; start() }
        }

        @Synchronized fun stop() {
            thread?.interrupt(); thread = null
        }
    }
}

package dev.hmg.pleoskit

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * TextToSpeech SDK 래퍼. 기본은 ON_DEVICE 모드라 Playground 앱 등록(Client ID/Secret) 없이 쓸 수 있다.
 * 권한: `pleos.car.permission.TTS_SERVICE`.
 *
 * 한 번에 한 문장만 보낸다. SDK 이벤트에 요청 번호가 없어서, 멈춘 문장의 끝남 이벤트(onStop)를 받고 나서 다음 문장을 보낸다
 * (Turns.kt, 서비스 감사 P13).
 */
class TtsKit(
    context: Context,
    private val mode: TtsMode = TtsMode.ON_DEVICE,
    private val locale: Locale = Locale.KOREA,
) : SpeechOutput {
    private val appContext = context.applicationContext
    private val sdk = LazySdk("TextToSpeech") { TextToSpeech(appContext, mode) }

    /**
     * SDK 원본 객체. SERVER/HYBRID 모드의 `registerApp`이나 오디오 존 설정에 쓴다.
     * 처음 쓰는 곳에서 만들어진다: [connect]를 먼저 부르면 SDK 전용 스레드에서 만들어진다 (메인 스레드에서 처음 쓰지 말 것).
     */
    val raw: TextToSpeech get() = sdk.value

    private sealed interface End {
        data object Done : End
        data object Stopped : End
        data class Failed(val type: TtsErrorType) : End
    }

    private val turns = TurnRunner<End>("TTS", SdkThreads.voice) { raw.stop() }
    private val connectMutex = Mutex()

    @Volatile
    private var connected = false

    private val listener = object : TtsEventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onDone() = turns.onEnd(End.Done)
        override fun onStop() = turns.onEnd(End.Stopped)
        override fun onError(errorType: TtsErrorType) = turns.onEnd(End.Failed(errorType))
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override suspend fun connect() = connect(CONNECT_TIMEOUT_MS)

    suspend fun connect(timeoutMs: Long) = connectMutex.withLock {
        if (connected) return@withLock
        val ok = withTimeoutOrNull(timeoutMs) {
            withContext(SdkThreads.voice) {
                val tts = sdk.value
                suspendCancellableCoroutine { cont ->
                    val done = AtomicBoolean(false)
                    tts.initialize(object : TtsInitListener {
                        override fun onSuccess() {
                            if (done.compareAndSet(false, true)) cont.resume(Unit)
                        }

                        override fun onFailure(errorType: TtsErrorType) {
                            if (done.compareAndSet(false, true)) {
                                cont.resumeWithException(PleosSdkException("TextToSpeech.initialize", errorType.name))
                            }
                        }
                    })
                }
                tts.addEventListener(listener)
                tts.setLocale(locale)
            }
        }
        if (ok == null) throw PleosSdkException("TextToSpeech.initialize", "no response in ${timeoutMs}ms")
        connected = true
    }

    override suspend fun speak(text: String): Boolean = speak(text, SPEAK_TIMEOUT_MS)

    /**
     * [text]를 읽고, 재생이 끝날 때까지 기다린다. 한 번에 한 문장만 재생한다(나중 호출은 앞 문장이 끝난 뒤 시작).
     * 호출한 코루틴이 취소되면 재생도 멈춘다. 끝까지 재생했으면 true, 중간에 멈췄으면(또는 보내기 전에 [stop]이 왔으면) false.
     */
    suspend fun speak(text: String, timeoutMs: Long): Boolean {
        val end = turns.run(send = { raw.speak(text) }) { turn ->
            turns.awaitEnd(turn, timeoutMs, onUnconfirmedStop = End.Stopped) ?: run {
                turns.stop()
                throw PleosSdkException("TextToSpeech.speak", "not finished in ${timeoutMs}ms")
            }
        }
        return when (end) {
            End.Done -> true
            End.Stopped, null -> false
            is End.Failed -> throw PleosSdkException("TextToSpeech.speak", end.type.name)
        }
    }

    /** 재생 중인 음성을 멈춘다. 말하는 중이 아니면 아무것도 하지 않는다 */
    override fun stop() = turns.stop()

    fun release() {
        SdkThreads.voice.executor.execute {
            sdk.ifCreated { tts ->
                runCatching { tts.removeEventListener(listener) }
                runCatching { tts.release() }
            }
            connected = false
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val SPEAK_TIMEOUT_MS = 60_000L
    }
}

/**
 * SpeechToText SDK 래퍼. 기본은 ON_DEVICE 모드.
 * 권한: `pleos.car.permission.STT_SERVICE`.
 *
 * 한 번에 한 번만 듣는다. 멈추거나 끝난 듣기의 끝남 이벤트(onEndedRecognition)를 받고 나서 다음 듣기를 시작한다 (Turns.kt).
 */
class SttKit(
    context: Context,
    private val mode: SttMode = SttMode.ON_DEVICE,
    private val locale: Locale = Locale.KOREA,
) : SpeechInput {
    private val appContext = context.applicationContext
    private val sdk = LazySdk("SpeechToText") { SpeechToText(appContext, mode) }

    /** SDK 원본 객체. 처음 쓰는 곳에서 만들어진다 ([connect]를 먼저 부르면 SDK 전용 스레드) */
    val raw: SpeechToText get() = sdk.value

    private sealed interface End {
        data object Ended : End
        data class Failed(val type: SttErrorType) : End
    }

    /** 듣기 한 번이 받은 문장. 최종 결과(completed)가 오면 끝남 이벤트를 기다리지 않고 돌려준다 */
    private class Heard(micOpen: Boolean = false) {
        @Volatile var last: String? = null
        val completed = CompletableDeferred<Unit>()

        /** 녹음을 시작할 준비가 됐다 (onReady). 마이크를 쓰지 않는 인식(PCM 입력)은 처음부터 열린 것으로 본다 */
        val ready = CompletableDeferred<Unit>().apply { if (micOpen) complete(Unit) }
    }

    // 멈춘 듣기의 끝남 이벤트는 짧게만 기다린다: 에뮬레이터의 STT는 request 뒤 약 8초에 Ready를 주고, 그 전에 stop 하면
    // onEndedRecognition이 오지 않았다 (2026-11-12 SdkProbe `stt`). 길게 기다리면 다음 듣기의 마이크가 그만큼 늦게 열린다
    private val turns = TurnRunner<End>("STT", SdkThreads.voice, confirmMs = STOP_CONFIRM_MS) { raw.stop() }
    private val connectMutex = Mutex()

    @Volatile
    private var connected = false

    @Volatile
    private var heard: Heard? = null

    private val listener = object : ResultListener {
        override fun onReady() {
            heard?.ready?.complete(Unit)
        }

        override fun onStartedRecognition() {
            // 말소리가 시작됐다: 준비 이벤트를 놓쳤어도 마이크는 열려 있다
            heard?.ready?.complete(Unit)
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            val h = heard ?: return
            h.ready.complete(Unit)
            if (stt.isNotBlank()) h.last = stt
            if (completed) h.completed.complete(Unit)
        }

        override fun onEndedRecognition() = turns.onEnd(End.Ended)
        override fun onError(errorType: SttErrorType) = turns.onEnd(End.Failed(errorType))
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override suspend fun connect() = connect(CONNECT_TIMEOUT_MS)

    suspend fun connect(timeoutMs: Long) = connectMutex.withLock {
        if (connected) return@withLock
        val ok = withTimeoutOrNull(timeoutMs) {
            withContext(SdkThreads.voice) {
                val stt = sdk.value
                suspendCancellableCoroutine { cont ->
                    val done = AtomicBoolean(false)
                    stt.initialize(object : SttInitListener {
                        override fun onSuccess() {
                            if (done.compareAndSet(false, true)) cont.resume(Unit)
                        }

                        override fun onFailure(errorType: SttErrorType) {
                            if (done.compareAndSet(false, true)) {
                                cont.resumeWithException(PleosSdkException("SpeechToText.initialize", errorType.name))
                            }
                        }
                    })
                }
                stt.addListener(listener)
                stt.setLocale(locale)
            }
        }
        if (ok == null) throw PleosSdkException("SpeechToText.initialize", "no response in ${timeoutMs}ms")
        connected = true
    }

    /**
     * 차량 마이크로 한 번 듣고 인식한 문장을 돌려준다. 마이크가 열린 뒤 [timeoutMs] 안에 인식이 끝나지 않으면 그때까지의
     * 중간 결과를 돌려준다(아무 말도 없었으면 null).
     *
     * 듣는 시간은 마이크가 열린 뒤(SDK의 onReady)부터 잰다 ([listenWindow]). 에뮬레이터의 STT 서비스는 request 뒤 0.2~10초에
     * 녹음을 시작해서, request부터 재면 6초 듣기가 1초 남짓이거나 0초였다(2026-11-12). [MIC_OPEN_WAIT_MS] 안에 열리지 않으면 null.
     */
    override suspend fun listen(timeoutMs: Long, onMicOpen: () -> Unit): String? =
        recognize("SpeechToText.request", timeoutMs, Heard(), onMicOpen) { raw.request() }

    suspend fun listen(): String? = listen(LISTEN_TIMEOUT_MS)

    /**
     * 마이크 대신 PCM 오디오(16kHz, 16bit, mono)를 넣어 인식한다. ON_DEVICE 모드에서만 동작한다.
     * 마이크가 없는 에뮬레이터에서 음성 대화 흐름을 시험할 때 쓴다.
     */
    suspend fun recognizePcm(pcm: ByteArray, timeoutMs: Long = 20_000): String? =
        recognize("SpeechToText.sendAudio", timeoutMs, Heard(micOpen = true)) {
            raw.sendAudio(pcm)
            raw.completeAudioSend()
        }

    private sealed interface Outcome {
        data class Finished(val end: End) : Outcome
        data object Completed : Outcome
        data object TimedOut : Outcome
    }

    private suspend fun recognize(name: String, timeoutMs: Long, h: Heard, onMicOpen: () -> Unit = {}, start: () -> Unit): String? {
        val outcome = turns.run(send = {
            heard = h
            start()
        }) { turn ->
            listenWindow(h.ready, MIC_OPEN_WAIT_MS, timeoutMs, onMicOpen) {
                select<Outcome> {
                    turn.end.onAwait { Outcome.Finished(it) }
                    // 최종 결과가 왔다. 끝남 이벤트는 다음 요청을 보내기 전에 기다린다 (TurnRunner.settle)
                    h.completed.onAwait { Outcome.Completed }
                    turn.stopAsked.onAwait { Outcome.Finished(turns.confirmStop(turn, onUnconfirmedStop = End.Ended)) }
                }
            } ?: Outcome.TimedOut
        }
        if (outcome == Outcome.TimedOut && !h.ready.isCompleted) KitLog.w("$name: the microphone did not open in ${MIC_OPEN_WAIT_MS}ms")
        if (outcome is Outcome.Finished && outcome.end is End.Failed && h.last == null) {
            throw PleosSdkException(name, outcome.end.type.name)
        }
        // 시간 안에 끝나지 않았으면 마이크를 닫는다
        if (outcome == Outcome.TimedOut) turns.stop()
        return h.last
    }

    /** 듣기를 멈추고 마이크를 닫는다. 듣는 중이 아니면 아무것도 하지 않는다 */
    override fun stop() = turns.stop()

    fun release() {
        SdkThreads.voice.executor.execute {
            sdk.ifCreated { stt ->
                runCatching { stt.removeListener(listener) }
                runCatching { stt.release() }
            }
            connected = false
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val LISTEN_TIMEOUT_MS = 10_000L
        const val STOP_CONFIRM_MS = 400L

        /** request 뒤 마이크가 열리기(onReady)를 기다리는 시간. 에뮬레이터에서 0.2~10.3초였다 (부하가 높을 때 길다) */
        const val MIC_OPEN_WAIT_MS = 15_000L
    }
}

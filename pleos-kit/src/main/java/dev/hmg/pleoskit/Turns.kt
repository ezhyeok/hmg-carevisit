package dev.hmg.pleoskit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.EmptyCoroutineContext

/*
 * 음성 SDK(TextToSpeech, SpeechToText)의 요청과 이벤트를 짝짓는 방법 (서비스 감사 P13).
 *
 * 두 SDK의 이벤트에는 요청 번호가 없다 (API 레퍼런스: speak(text, isFinal)·request()는 값을 돌려주지 않고, onDone()·onStop()·
 * onEndedRecognition()은 인자가 없다). 그래서 멈춘 요청의 늦은 '멈춤' 이벤트가 바로 다음 요청의 끝으로 읽힐 수 있었다:
 * 다음 말이 아직 나오는 중인데 끝났다고 보고 마이크를 열었다. 예전에는 멈춘 뒤 0.4초 쉬는 것으로 줄였을 뿐이다.
 *
 * 규칙: 요청은 하나씩 보내고, 보낸 요청의 끝남 이벤트를 받기 전에는 다음 요청을 보내지 않는다. 그러면 끝남 이벤트는 늘
 * '지금 열려 있는 요청'의 것이다. 멈추라고 한 요청의 끝남 이벤트가 [TurnRunner.confirmMs] 안에 오지 않으면 더 기다리지 않는다
 * (그 뒤 늦게 오는 이벤트는 다음 요청의 것으로 읽힐 수 있다: 로그를 남긴다). 열린 요청이 없을 때 온 이벤트는 버린다.
 */

/** 열려 있는 요청 하나와 요청 번호 없이 오는 끝남 이벤트를 짝짓는다 (순수 상태, TurnGateTest) */
internal class TurnGate<E : Any> {
    class Turn<E : Any> internal constructor(val seq: Long) {
        /** 이 요청의 끝남 이벤트 */
        val end = CompletableDeferred<E>()
        /** 멈추라는 요청이 왔다 */
        val stopAsked = CompletableDeferred<Unit>()
        /** SDK에 보냈다 (SDK 전용 스레드에서만 바꾼다) */
        @Volatile var sent = false
        /** SDK에 멈춤을 보냈다 (SDK 전용 스레드에서만 바꾼다) */
        @Volatile var stopSent = false
    }

    private var open: Turn<E>? = null
    private var seq = 0L

    /** 끝남 이벤트를 기다리는 요청. 없으면 null */
    val pending: Turn<E>?
        @Synchronized get() = open

    /** 새 요청을 연다. 앞 요청이 열려 있으면 안 된다 (끝남을 기다리거나 [abandon]한 뒤에 연다) */
    @Synchronized
    fun begin(): Turn<E> {
        check(open == null) { "turn ${open?.seq} is still open" }
        return Turn<E>(++seq).also { open = it }
    }

    /** SDK의 끝남 이벤트. 열린 요청이 있으면 그 요청의 것이다(true). 없으면 떠도는 이벤트라 버린다(false) */
    @Synchronized
    fun end(event: E): Boolean {
        val turn = open ?: return false
        open = null
        turn.end.complete(event)
        return true
    }

    /** 이 요청의 끝남 이벤트를 더 기다리지 않는다 (보내지 못했거나, 멈춘 뒤 확인 이벤트가 오지 않았다) */
    @Synchronized
    fun abandon(turn: Turn<E>) {
        if (open === turn) open = null
    }
}

/**
 * 요청을 하나씩 차례로 돌린다: SDK 전용 스레드([dispatcher])에서 보내고, 끝남 이벤트를 기다리고, 도중에 멈출 수 있다.
 * TtsKit·SttKit이 같이 쓴다. SDK를 모르므로 가짜 SDK와 가상 시간으로 시험한다 (TurnRunnerTest).
 *
 * @param stopSdk SDK의 stop(). SDK 전용 스레드에서, 보낸 요청이 열려 있을 때만, 요청마다 한 번 부른다
 */
internal class TurnRunner<E : Any>(
    private val name: String,
    private val dispatcher: CoroutineDispatcher,
    val confirmMs: Long = STOP_CONFIRM_MS,
    private val stopSdk: () -> Unit,
) {
    private val gate = TurnGate<E>()
    private val mutex = Mutex()

    /** SDK 리스너가 끝남 이벤트를 받았다 (어느 스레드에서나) */
    fun onEnd(event: E) {
        if (!gate.end(event)) KitLog.d("$name: $event with no request in flight (ignored)")
    }

    /**
     * 지금 요청을 멈춘다 (어느 스레드에서나, 기다리지 않는다). 열린 요청이 없으면 아무것도 하지 않는다:
     * 쉬고 있는 SDK에 stop을 보내 떠도는 멈춤 이벤트를 만들지 않는다.
     */
    fun stop() {
        dispatcher.dispatch(EmptyCoroutineContext, Runnable {
            val turn = gate.pending ?: return@Runnable
            turn.stopAsked.complete(Unit)
            if (turn.sent && !turn.stopSent) {
                turn.stopSent = true
                runCatching { stopSdk() }.onFailure { KitLog.w("$name: stop failed: ${it.message}") }
            }
        })
    }

    /**
     * 요청 하나. 앞 요청이 끝나기를 기다렸다가 [send]로 보내고 [await]로 결과를 기다린다.
     * 보내기 전에 [stop]이 왔으면 보내지 않고 null. 부른 코루틴이 취소되면 멈춤을 보내고 취소를 그대로 던진다.
     * [await]는 [awaitEnd]로 끝남 이벤트를 기다린다.
     */
    suspend fun <R : Any> run(send: () -> Unit, await: suspend (TurnGate.Turn<E>) -> R): R? = mutex.withLock {
        settle()
        val turn = gate.begin()
        try {
            val sent = withContext(dispatcher) {
                if (turn.stopAsked.isCompleted) return@withContext false
                send()
                turn.sent = true
                true
            }
            if (!sent) {
                gate.abandon(turn)
                return@withLock null
            }
            await(turn)
        } catch (c: CancellationException) {
            stop()
            throw c
        } catch (e: Exception) {
            // 보내다 실패했으면 끝남 이벤트가 오지 않는다. 보낸 뒤의 실패면 다음 요청이 settle로 기다린다
            if (!turn.sent) gate.abandon(turn)
            throw e
        }
    }

    /**
     * [turn]의 끝남 이벤트. [timeoutMs] 안에 오지 않으면 null. 멈추라고 한 뒤에는 [confirmMs]만 더 기다리고,
     * 그래도 없으면 [onUnconfirmedStop]의 값으로 끝낸다 (SDK가 멈춤 이벤트를 주지 않았다).
     */
    suspend fun awaitEnd(turn: TurnGate.Turn<E>, timeoutMs: Long, onUnconfirmedStop: E): E? = withTimeoutOrNull(timeoutMs) {
        select {
            turn.end.onAwait { it }
            turn.stopAsked.onAwait { confirmStop(turn, onUnconfirmedStop) }
        }
    }

    /** 멈추라고 한 [turn]의 끝남 이벤트를 [confirmMs]까지 기다린다 */
    suspend fun confirmStop(turn: TurnGate.Turn<E>, onUnconfirmedStop: E): E =
        withTimeoutOrNull(confirmMs) { turn.end.await() } ?: run {
            KitLog.w("$name: stop of request ${turn.seq} not confirmed in ${confirmMs}ms")
            gate.abandon(turn)
            onUnconfirmedStop
        }

    /** 앞 요청이 끝남 이벤트 없이 남아 있으면(취소, 시간 초과, 최종 결과만 받고 돌아감) 그 이벤트를 받고 나서 다음 요청을 보낸다 */
    private suspend fun settle() {
        val prev = gate.pending ?: return
        stop()
        if (withTimeoutOrNull(confirmMs) { prev.end.await() } == null) {
            // 에뮬레이터의 STT는 멈춘 듣기의 끝남 이벤트를 주지 않아 듣기마다 여기로 온다: 경고가 아니라 기록으로 남긴다
            KitLog.d("$name: no end event for request ${prev.seq} in ${confirmMs}ms; a late one may be read as the next request's")
            gate.abandon(prev)
        }
    }

    companion object {
        /** 멈춘 요청의 끝남 이벤트를 기다리는 시간 */
        const val STOP_CONFIRM_MS = 1_500L
    }
}

/**
 * 듣기 한 번의 시간 규칙: 듣는 시간([openMs])은 마이크가 열린 뒤([opened])부터 잰다.
 *
 * STT 서비스는 request를 받고 인식기를 준비한 뒤에야 녹음을 시작한다(onReady). 에뮬레이터에서는 그 준비가 약 5~8초였다
 * (2026-11-12). 듣는 시간을 request부터 재면 준비 시간이 듣는 시간을 먹어서, 6초 듣기가 실제로는 1초 남짓이거나 0초였다.
 *
 * - [opened]가 [openWaitMs] 안에 오지 않으면 null (마이크가 열리지 않았다. 듣는 시간만큼 더 기다리지 않는다)
 * - 열리면 [onOpen]을 부르고 [finish]를 [openMs]까지 기다린다. 그 안에 끝나지 않으면 null
 * - [finish]가 열리기 전에 끝나면(오류, 멈춤) 곧바로 그 값. [finish]의 예외는 그대로 던진다
 *
 * @param finish 듣기가 끝나기를 기다린다 (끝남 이벤트, 최종 결과, 멈춤 요청). 시간 제한 없이 기다리게 쓴다
 */
internal suspend fun <T : Any> listenWindow(
    opened: Deferred<Unit>,
    openWaitMs: Long,
    openMs: Long,
    onOpen: () -> Unit = {},
    finish: suspend () -> T,
): T? = coroutineScope {
    val done = async { finish() }
    try {
        val isOpen: Boolean = withTimeoutOrNull(openWaitMs) {
            select<Boolean> {
                // 끝난 것을 먼저 본다: 열림과 끝이 함께 와 있으면 끝난 값을 잃지 않는다
                done.onAwait { false }
                opened.onAwait { true }
            }
        } ?: return@coroutineScope null
        if (!isOpen) return@coroutineScope done.await()
        onOpen()
        withTimeoutOrNull(openMs) { done.await() }
    } finally {
        done.cancel()
    }
}

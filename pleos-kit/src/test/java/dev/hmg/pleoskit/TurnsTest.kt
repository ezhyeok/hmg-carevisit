package dev.hmg.pleoskit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 음성 요청과 요청 번호 없는 SDK 이벤트의 짝짓기 (Turns.kt, 서비스 감사 P13).
 * 회귀: 멈춘 말의 늦은 '멈춤' 이벤트가 바로 다음 말을 끝낸 것으로 읽혀, 다음 말이 나오는 중에 마이크를 열었다.
 * 가짜 SDK(보낸 호출을 적고 이벤트는 시험이 넣는다)와 가상 시간으로 돈다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TurnsTest {

    /** TtsKit이 TurnRunner를 쓰는 모양 그대로: 보낸 호출을 [calls]에 적는다 */
    private class FakeSpeech(scope: TestScope, confirmMs: Long = 1_500) {
        val calls = mutableListOf<String>()
        var failSend = false
        val runner = TurnRunner<String>("TTS", StandardTestDispatcher(scope.testScheduler), confirmMs) { calls += "stop" }

        suspend fun speak(text: String, timeoutMs: Long = 60_000): String? =
            runner.run(send = {
                if (failSend) throw IllegalStateException("service not bound")
                calls += "speak $text"
            }) { turn ->
                runner.awaitEnd(turn, timeoutMs, onUnconfirmedStop = "unconfirmed") ?: "timeout".also { runner.stop() }
            }
    }

    // ------------------------------------------------------------ TurnGate

    @Test
    fun anEndEventBelongsToTheOpenRequestAndStrayOnesAreDropped() {
        val gate = TurnGate<String>()
        assertFalse("열린 요청이 없으면 버린다", gate.end("stopped"))
        val a = gate.begin()
        assertSame(a, gate.pending)
        assertTrue(gate.end("done"))
        assertEquals("done", a.end.getCompleted())
        assertNull(gate.pending)
        assertFalse("같은 요청에 두 번째 이벤트는 없다", gate.end("stopped"))
        val b = gate.begin()
        assertEquals(a.seq + 1, b.seq)
        assertFalse(b.end.isCompleted)
    }

    @Test
    fun aRequestCannotOpenWhileAnotherIsOpenUntilItEndsOrIsAbandoned() {
        val gate = TurnGate<String>()
        val a = gate.begin()
        try {
            gate.begin()
            fail("열린 요청이 있는데 또 열렸다")
        } catch (e: IllegalStateException) {
            // 기대한 대로
        }
        gate.abandon(a)
        val b = gate.begin()
        gate.abandon(a) // 지난 요청을 다시 버려도 지금 요청은 그대로다
        assertSame(b, gate.pending)
    }

    // ------------------------------------------------------------ TurnRunner

    @Test
    fun aRequestEndsWithItsOwnEndEvent() = runTest {
        val tts = FakeSpeech(this)
        val a = async { tts.speak("A") }
        runCurrent()
        assertEquals(listOf("speak A"), tts.calls)
        assertFalse(a.isCompleted)
        tts.runner.onEnd("done")
        assertEquals("done", a.await())
    }

    @Test
    fun theLateStopEventOfACancelledRequestDoesNotEndTheNextOne() = runTest {
        // P13: A를 말하는 중에 취소하고 곧바로 B를 말한다. A의 '멈춤' 이벤트는 0.3초 뒤에 온다
        val tts = FakeSpeech(this)
        val a = launch { tts.speak("A") }
        runCurrent()
        a.cancelAndJoin()
        val b = async { tts.speak("B") }
        runCurrent()
        assertEquals("B는 A의 끝남 이벤트를 받기 전에는 보내지 않는다", listOf("speak A", "stop"), tts.calls)
        advanceTimeBy(300)
        tts.runner.onEnd("stopped") // A의 이벤트
        runCurrent()
        assertEquals(listOf("speak A", "stop", "speak B"), tts.calls)
        assertFalse("A의 멈춤이 B를 끝내지 않는다", b.isCompleted)
        tts.runner.onEnd("done") // B의 이벤트
        assertEquals("done", b.await())
    }

    @Test
    fun stoppingWithNothingInFlightDoesNotCallTheSdk() = runTest {
        // 쉬고 있는 SDK에 stop을 보내면 떠도는 멈춤 이벤트가 생겨 다음 요청을 끝낼 수 있다
        val tts = FakeSpeech(this)
        tts.runner.stop()
        runCurrent()
        assertTrue(tts.calls.isEmpty())
        tts.runner.onEnd("stopped") // 그래도 온 이벤트는 버린다
        val a = async { tts.speak("A") }
        runCurrent()
        assertFalse(a.isCompleted)
        tts.runner.onEnd("done")
        assertEquals("done", a.await())
    }

    @Test
    fun aStopThatComesBeforeTheRequestIsSentSendsNothing() = runTest {
        val tts = FakeSpeech(this)
        val a = async { tts.speak("A") }
        tts.runner.stop() // 요청이 SDK 스레드에 닿기 전에 멈추라고 했다
        runCurrent()
        assertNull("보내지 않았다", a.await())
        assertTrue(tts.calls.isEmpty())
        // 다음 요청은 기다릴 것 없이 바로 간다
        val b = async { tts.speak("B") }
        runCurrent()
        assertEquals(listOf("speak B"), tts.calls)
        tts.runner.onEnd("done")
        assertEquals("done", b.await())
    }

    @Test
    fun aStopIsConfirmedByTheSdksEndEvent() = runTest {
        val tts = FakeSpeech(this)
        val a = async { tts.speak("A") }
        runCurrent()
        tts.runner.stop()
        tts.runner.stop() // 두 번 멈춰도 SDK에는 한 번만 보낸다
        runCurrent()
        assertEquals(listOf("speak A", "stop"), tts.calls)
        assertFalse("멈춤 이벤트를 기다린다", a.isCompleted)
        advanceTimeBy(200)
        tts.runner.onEnd("stopped")
        assertEquals("stopped", a.await())
    }

    @Test
    fun aStopTheSdkNeverConfirmsGivesUpAfterTheConfirmTime() = runTest {
        val tts = FakeSpeech(this, confirmMs = 1_500)
        val a = async { tts.speak("A") }
        runCurrent()
        tts.runner.stop()
        advanceTimeBy(1_499)
        runCurrent()
        assertFalse(a.isCompleted)
        advanceTimeBy(1)
        assertEquals("unconfirmed", a.await())
        // 더 기다리지 않는다: 다음 요청은 바로 간다
        val b = async { tts.speak("B") }
        runCurrent()
        assertEquals(listOf("speak A", "stop", "speak B"), tts.calls)
        tts.runner.onEnd("done")
        assertEquals("done", b.await())
    }

    @Test
    fun aRequestThatTimedOutIsSettledBeforeTheNextOneIsSent() = runTest {
        val tts = FakeSpeech(this)
        val a = async { tts.speak("A", timeoutMs = 5_000) }
        advanceTimeBy(5_000)
        assertEquals("timeout", a.await())
        runCurrent()
        assertEquals(listOf("speak A", "stop"), tts.calls)
        val b = async { tts.speak("B") }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("A의 끝남 이벤트를 기다리는 중", listOf("speak A", "stop"), tts.calls)
        tts.runner.onEnd("stopped")
        runCurrent()
        assertEquals(listOf("speak A", "stop", "speak B"), tts.calls)
        tts.runner.onEnd("done")
        assertEquals("done", b.await())
    }

    @Test
    fun aRequestThatCouldNotBeSentDoesNotHoldUpTheNext() = runTest {
        val tts = FakeSpeech(this)
        tts.failSend = true
        val a = async { runCatching { tts.speak("A") } }
        assertTrue(a.await().exceptionOrNull() is IllegalStateException)
        tts.failSend = false
        val b = async { tts.speak("B") }
        runCurrent()
        assertEquals("끝남 이벤트를 기다리지 않는다 (오지 않는다)", listOf("speak B"), tts.calls)
        tts.runner.onEnd("done")
        assertEquals("done", b.await())
    }

    @Test
    fun aRequestThatReturnedEarlyIsSettledWhenItsEndEventArrives() = runTest {
        // 듣기: 최종 결과(completed)가 오면 끝남 이벤트를 기다리지 않고 돌아간다. 그 끝남 이벤트는 다음 듣기의 것이 아니다
        val calls = mutableListOf<String>()
        val runner = TurnRunner<String>("STT", StandardTestDispatcher(testScheduler), 1_500) { calls += "stop" }
        val completed = CompletableDeferred<Unit>()
        val first = async {
            runner.run(send = { calls += "request 1" }) { turn ->
                select<String> {
                    turn.end.onAwait { it }
                    completed.onAwait { "final result" }
                }
            }
        }
        runCurrent()
        completed.complete(Unit)
        assertEquals("final result", first.await())
        val second = async { runner.run(send = { calls += "request 2" }) { turn -> turn.end.await() } }
        runCurrent()
        assertEquals("첫 듣기의 끝남 이벤트를 받고 나서 보낸다", listOf("request 1", "stop"), calls)
        runner.onEnd("ended") // 첫 듣기의 이벤트
        runCurrent()
        assertEquals(listOf("request 1", "stop", "request 2"), calls)
        assertFalse(second.isCompleted)
        runner.onEnd("ended")
        assertEquals("ended", second.await())
    }
}

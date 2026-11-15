package dev.hmg.pleoskit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 듣는 시간은 마이크가 열린 뒤부터 잰다 (Turns.kt [listenWindow]).
 *
 * 회귀(2026-11-12 에뮬레이터, 귀성길 시연): STT 서비스는 request 뒤 4.8초에 녹음을 시작했는데(ReadyToStartRecognition)
 * 듣는 시간 6초를 request부터 재서 1.2초 뒤에 마이크를 닫았다. 서비스 로그의 녹음 횟수는 10번(약 1초)이었다.
 * 질문을 듣고 바로 한 대답은 마이크가 열리기 전이라 들리지 않았다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenWindowTest {
    private val openWaitMs = 12_000L
    private val openMs = 6_000L

    @Test
    fun theWindowIsCountedFromTheMomentTheMicOpens() = runTest {
        val opened = CompletableDeferred<Unit>()
        var openedAt = -1L
        launch {
            delay(4_800)
            opened.complete(Unit)
        }
        val result = listenWindow(opened, openWaitMs, openMs, onOpen = { openedAt = currentTime }) { awaitCancellation() }
        assertNull("아무 말도 없었다", result)
        assertEquals(4_800L, openedAt)
        assertEquals("마이크가 열린 뒤 6초를 듣는다 (request부터 6초가 아니다)", 10_800L, currentTime)
    }

    @Test
    fun anAnswerGivenAfterALateOpenIsHeard() = runTest {
        // 예전 규칙이면 request 뒤 6초에 닫혀 이 대답(8초)을 듣지 못했다
        val opened = CompletableDeferred<Unit>()
        launch {
            delay(4_800)
            opened.complete(Unit)
        }
        val result = listenWindow(opened, openWaitMs, openMs) {
            delay(8_000)
            "응"
        }
        assertEquals("응", result)
        assertEquals(8_000L, currentTime)
    }

    @Test
    fun aMicThatOpensAtOnceListensForTheWindowOnly() = runTest {
        val result = listenWindow<String>(CompletableDeferred(Unit), openWaitMs, openMs) { awaitCancellation() }
        assertNull(result)
        assertEquals(6_000L, currentTime)
    }

    @Test
    fun aMicThatNeverOpensIsGivenUpAfterTheWait() = runTest {
        var opens = 0
        var finishCancelled = false
        val result = listenWindow<String>(CompletableDeferred(), openWaitMs, openMs, onOpen = { opens++ }) {
            try {
                awaitCancellation()
            } finally {
                finishCancelled = true
            }
        }
        assertNull(result)
        assertEquals("열리지 않은 마이크를 듣는 시간만큼 더 기다리지 않는다", 12_000L, currentTime)
        assertEquals(0, opens)
        assertTrue("기다리던 일을 남기지 않는다", finishCancelled)
    }

    @Test
    fun anEndBeforeTheMicOpensReturnsAtOnce() = runTest {
        // 오류, 멈춤: 마이크가 열리기를 기다리지 않는다
        var opens = 0
        val result = listenWindow(CompletableDeferred(), openWaitMs, openMs, onOpen = { opens++ }) {
            delay(1_000)
            "stopped"
        }
        assertEquals("stopped", result)
        assertEquals(1_000L, currentTime)
        assertEquals(0, opens)
    }

    @Test
    fun aFailureWhileWaitingIsThrownToTheCaller() = runTest {
        val failure = runCatching {
            listenWindow<String>(CompletableDeferred(Unit), openWaitMs, openMs) {
                delay(500)
                throw PleosSdkException("SpeechToText.request", "ERROR_NETWORK")
            }
        }.exceptionOrNull()
        assertTrue(failure is PleosSdkException)
        assertEquals(500L, currentTime)
    }

    @Test
    fun cancellingTheListenStopsTheWait() = runTest {
        var finishCancelled = false
        val opened = CompletableDeferred<Unit>()
        val job = launch {
            listenWindow<String>(opened, openWaitMs, openMs) {
                try {
                    awaitCancellation()
                } finally {
                    finishCancelled = true
                }
            }
        }
        advanceTimeBy(2_000)
        job.cancelAndJoin()
        assertTrue(finishCancelled)
        // 취소 뒤에 열렸다는 이벤트가 와도 아무 일도 없다
        opened.complete(Unit)
        runCurrent()
        assertFalse(job.isActive)
    }

    @Test
    fun theResultThatArrivesWithTheOpenEventIsNotLost() = runTest {
        // 열림과 결과가 같은 순간에 왔다 (PCM 입력처럼 곧바로 끝나는 인식)
        val opened = CompletableDeferred<Unit>()
        val heard = CompletableDeferred<String>()
        val result = async { listenWindow(opened, openWaitMs, openMs) { heard.await() } }
        runCurrent()
        opened.complete(Unit)
        heard.complete("응")
        assertEquals("응", result.await())
        assertEquals(0L, currentTime)
    }
}

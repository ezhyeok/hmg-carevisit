package dev.hmg.pleoskit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** SDK 호출 어댑터의 규칙 (SdkCall.kt) */
class SdkCallTest {

    @Test
    fun anSdkExceptionBecomesAPleosSdkExceptionNamedAfterTheCall() = runBlocking {
        val e = runCatching { sdkSend(Dispatchers.Default, "addWaypoint") { throw IllegalStateException("not bound") } }.exceptionOrNull()
        assertTrue(e is PleosSdkException)
        assertEquals("addWaypoint", (e as PleosSdkException).call)
        assertEquals("addWaypoint: not bound", e.message)
        assertTrue(e.cause is IllegalStateException)
    }

    @Test
    fun cancellationStaysCancellation() {
        // 리뷰 R-F13: SDK 스레드가 바쁜 동안 부른 코루틴이 취소되면 CancellationException이 그대로 나와야 한다.
        // 예전 NaviKit은 `catch (e: Exception)`이 취소까지 잡아 PleosSdkException으로 바꿨다
        val executor = Executors.newSingleThreadExecutor()
        val sdkThread = executor.asCoroutineDispatcher()
        val busy = CountDownLatch(1)
        executor.execute { busy.await() }
        var thrown: Throwable? = null
        var sent = false
        try {
            runBlocking {
                val job = launch(Dispatchers.Default) {
                    try {
                        sdkSend(sdkThread, "requestTrip") { sent = true }
                    } catch (e: Throwable) {
                        thrown = e
                        throw e
                    }
                }
                delay(100)
                job.cancel()
                busy.countDown()
                job.join()
                assertTrue(job.isCancelled)
            }
        } finally {
            sdkThread.close()
        }
        assertTrue("취소는 취소로 나온다: $thrown", thrown is CancellationException)
        assertFalse(thrown is PleosSdkException)
        assertFalse("취소된 요청은 보내지 않는다", sent)
    }

    @Test
    fun theSdkObjectIsCreatedOnFirstUseAndNeverJustToBeReleased() {
        var created = 0
        val sdk = LazySdk("Sdk") {
            created++
            Any()
        }
        var released = 0
        sdk.ifCreated { released++ }
        assertEquals("kit을 만들기만 해서는 SDK 객체를 만들지 않는다 (놓을 때도)", 0, created)
        assertEquals(0, released)
        val first = sdk.value
        assertSame(first, sdk.value)
        assertEquals(1, created)
        sdk.ifCreated { released++ }
        assertEquals(1, released)
        // 다시 붙을 때: 지금 것을 버리고 다음에 쓸 때 새로 만든다
        assertSame(first, sdk.drop())
        assertEquals(1, created)
        assertNotSame(first, sdk.value)
        assertEquals(2, created)
    }

    @Test
    fun aFailureThatSaysNotSupportedIsNotRetriedAsATransientOne() {
        // 메시지에 든 오류 코드 이름으로 가린다 (2.3.2 jar의 VehicleException은 난독화돼 타입으로 쓸 수 없다)
        val notSupported = PleosSdkException("getTripCurrentDrivingTime", "ERROR_NOT_SUPPORTED_PROPERTY", RuntimeException("ERROR_NOT_SUPPORTED_PROPERTY"))
        assertTrue(VehicleKit.notSupported(notSupported))
        assertTrue(VehicleKit.notSupported(RuntimeException("x", RuntimeException("code ERROR_NOT_SUPPORTED_AREA"))))
        assertFalse(VehicleKit.notSupported(PleosSdkException("checkDoorPositionCapabilities", "no response in 20000ms")))
        assertFalse(VehicleKit.notSupported(PleosSdkException("getHvacTemperature", "ERROR_SERVICE_CONNECTION_FAILURE")))
        assertFalse(VehicleKit.notSupported(IllegalStateException()))
    }
}

package dev.hmg.pleoskit

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** SDK 호출이 실패했거나 제한 시간 안에 응답이 없을 때. [call]은 실패한 호출 이름. */
class PleosSdkException(val call: String, message: String, cause: Throwable? = null) :
    Exception("$call: $message", cause)

internal object KitLog {
    private const val TAG = "PleosKit"
    fun d(msg: String) {
        Log.d(TAG, msg)
    }

    fun w(msg: String, t: Throwable? = null) {
        Log.w(TAG, msg, t)
    }
}

private class Box<T>(val value: T)

/**
 * SDK 객체를 처음 쓸 때 만든다. kit 객체는 어느 스레드에서 만들어도 되고(메인 스레드 포함), SDK 객체는 kit이 처음 쓰는
 * 곳(`connect()`, SDK 전용 스레드)에서 만들어진다. 예전에는 kit 생성자가 SDK 객체를 만들어서, 서비스가 메인 스레드에서
 * kit을 조립하면 NaviHelper·Vehicle·TTS·STT·FusedLocation이 모두 메인 스레드에서 만들어졌다 (서비스 감사 SV-N4).
 * [ifCreated]는 만든 적이 있을 때만 돈다: 놓으려고(release) SDK 객체를 새로 만들지 않는다 (서비스 감사 D11).
 */
internal class LazySdk<T : Any>(private val name: String, private val create: () -> T) {
    @Volatile
    private var instance: T? = null

    val value: T
        get() = instance ?: synchronized(this) {
            instance ?: create().also {
                instance = it
                // 어느 스레드에서 만들었는지 남긴다. 메인 스레드면 경고: connect() 전에 raw를 메인 스레드에서 건드렸다는 뜻이다
                val thread = Thread.currentThread().name
                if (thread == "main") KitLog.w("$name created on the main thread (call connect() first)") else KitLog.d("$name created on $thread")
            }
        }

    fun ifCreated(block: (T) -> Unit) {
        instance?.let(block)
    }

    /** 지금 것을 버린다. 다음에 쓸 때 새로 만든다 (서비스가 죽어 다시 붙을 때). 버린 것을 돌려준다 */
    fun drop(): T? = synchronized(this) { instance.also { instance = null } }
}

/**
 * 값을 돌려주지 않는 SDK 요청 하나를 [dispatcher](SDK 전용 스레드)에서 보낸다. SDK가 던진 예외는 [PleosSdkException]으로 바꾼다.
 * 취소는 바꾸지 않고 그대로 던진다: 예전 NaviKit은 `catch (e: Exception)`이 취소까지 잡아 PleosSdkException으로 바꿨고,
 * 부른 쪽은 취소를 내비 실패로 세었다 (리뷰 R-F13).
 */
internal suspend fun sdkSend(dispatcher: CoroutineDispatcher, name: String, send: () -> Unit) {
    try {
        withContext(dispatcher) { send() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw PleosSdkException(name, e.message ?: e.javaClass.simpleName, e)
    }
}

/**
 * `onComplete`/`onFailed` 콜백으로 끝나는 SDK 호출 하나를 suspend 함수로 바꾼다.
 * [block]은 [dispatcher](SDK 전용 스레드)에서 실행되고, 결과 콜백이 올 때까지 호출한 코루틴만 멈춘다.
 * [timeoutMs] 안에 콜백이 오지 않으면 [PleosSdkException]을 던진다.
 */
internal suspend fun <T> sdkCall(
    dispatcher: CoroutineDispatcher,
    name: String,
    timeoutMs: Long,
    block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit,
): T {
    val started = SystemClock.elapsedRealtime()
    val box = withTimeoutOrNull(timeoutMs) {
        withContext(dispatcher) {
            suspendCancellableCoroutine { cont ->
                // SDK가 콜백을 두 번 부르거나 동기 예외와 콜백이 겹쳐도 한 번만 재개한다
                val done = AtomicBoolean(false)
                try {
                    block(
                        { value -> if (done.compareAndSet(false, true)) cont.resume(Box(value)) },
                        { e -> if (done.compareAndSet(false, true)) cont.resumeWithException(PleosSdkException(name, e.message ?: e.javaClass.simpleName, e)) },
                    )
                } catch (t: Throwable) {
                    if (done.compareAndSet(false, true)) cont.resumeWithException(PleosSdkException(name, t.message ?: t.javaClass.simpleName, t))
                }
            }
        }
    }
    val took = SystemClock.elapsedRealtime() - started
    if (box == null) throw PleosSdkException(name, "no response in ${timeoutMs}ms")
    if (took > 1000) KitLog.d("$name took ${took}ms")
    return box.value
}

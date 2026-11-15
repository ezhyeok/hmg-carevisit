package dev.hmg.pleoskit

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * 읽을 수 있는 차량 신호 하나. `get`(한 번 조회)과 `changes`(변경 구독)를 같은 모양으로 제공한다.
 * 값이 null이면 차량이 아직 값을 주지 않았다는 뜻이다.
 */
open class ReadSignal<T : Any> internal constructor(
    val name: String,
    private val getter: suspend () -> T?,
    private val changesFactory: () -> Flow<T?>,
) {
    /** 현재 값을 한 번 조회한다. 실패하면 [PleosSdkException]. */
    suspend fun get(): T? = getter()

    /**
     * 값이 바뀔 때마다 방출하는 cold Flow. 수집을 시작하면 리스너를 등록하고, 수집을 취소하면 해제한다.
     * 등록 직후 현재 값이 한 번 온다(에뮬레이터에서 확인). 리스너 오류(onFailed)는 로그만 남기고 Flow는 유지한다.
     */
    fun changes(): Flow<T?> = changesFactory()
}

/** 제어할 수 있는 차량 신호. `CONTROL_CAR_*` 권한이 필요하다. */
class ReadWriteSignal<T : Any> internal constructor(
    name: String,
    getter: suspend () -> T?,
    changesFactory: () -> Flow<T?>,
    private val setter: suspend (T) -> Unit,
) : ReadSignal<T>(name, getter, changesFactory) {

    /** 제어 요청만 보낸다. 성공 콜백이 와도 실제 값이 바뀌었다는 보장은 없다. 확인이 필요하면 [setAndConfirm]. */
    suspend fun set(value: T) = setter(value)

    /**
     * 제어 요청을 보내고, 값을 다시 읽어 실제로 반영됐는지 확인한다. 반영되면 true.
     *
     * 에뮬레이터에서 Virtual Console 시뮬레이터가 꺼져 있으면 성공 콜백은 오지만 값은 그대로였다.
     * 그래서 성공 콜백이 아니라 다시 읽은 값으로 판단한다.
     */
    suspend fun setAndConfirm(
        value: T,
        timeoutMs: Long = 3000,
        matches: (actual: T?, requested: T) -> Boolean = { actual, requested -> actual == requested },
    ): Boolean {
        setter(value)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (matches(get(), value)) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(300)
        }
    }
}

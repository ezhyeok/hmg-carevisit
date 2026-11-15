package dev.hmg.pleoskit

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 이미 연결한 [Car]에서 UX 제한을 구독해 [state]로 낸다. [CarState]와 [UxRestrictionWatcher]가 같이 쓴다.
 *
 * 리스너(메인 스레드)를 먼저 걸고 현재 값을 읽는다. 둘 사이에 온 변경을 놓치지 않으려는 순서이고, 현재 값은
 * 아직 리스너 값이 없을 때만(Unknown일 때만) 쓴다. 그렇지 않으면 늦게 읽은 옛 값이 새 리스너 값을 덮을 수 있다.
 */
internal class UxRestrictionTracker {
    private val _state = MutableStateFlow(UxRestrictions.NOT_CONNECTED)
    private val _restricted = MutableStateFlow(true)
    val state: StateFlow<UxRestrictions> = _state
    /** [state]의 [restricted]. 기존 `CarState.drivingRestricted`(Boolean) 사용처를 위해 같이 낸다 */
    val restricted: StateFlow<Boolean> = _restricted
    private var manager: CarUxRestrictionsManager? = null

    private fun set(value: UxRestrictions) {
        _state.value = value
        _restricted.value = value.restricted
    }

    fun attach(car: Car) {
        runCatching {
            val ux = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            manager = ux
            ux.registerListener { r -> set(r.toKnown()) }
            val before = _state.value
            val current = ux.currentCarUxRestrictions.toKnown()
            if (before is UxRestrictions.Unknown && _state.compareAndSet(before, current)) _restricted.value = current.restricted
        }.onFailure {
            KitLog.w("ux restriction subscription failed: ${it.message}")
            set(UxRestrictions.Unknown("구독 실패: ${it.message}"))
        }
    }

    /** Car 연결 실패처럼 붙이기 전에 끝난 경우 */
    fun fail(cause: String) = set(UxRestrictions.Unknown(cause))

    fun detach() {
        runCatching { manager?.unregisterListener() }
        manager = null
        set(UxRestrictions.Unknown("연결 해제"))
    }
}

private fun CarUxRestrictions.toKnown() = UxRestrictions.Known(
    requiresDistractionOptimization = isRequiresDistractionOptimization,
    activeRestrictions = activeRestrictions,
    maxStringLength = maxRestrictedStringLength,
    maxContentItems = maxCumulativeContentItems,
    maxContentDepth = maxContentDepth,
)

/**
 * UX 제한만 구독하는 Car 연결. 화면(Activity)이 서비스 없이 주행 제한을 알아야 할 때 쓴다
 * (서비스가 늦게 뜨거나 죽어도 화면은 제한을 스스로 안다). 권한이 필요 없다.
 *
 * 연결은 IO 스레드에서 한다. Car가 없거나(null) 구독에 실패하면 [Unknown](= 제한)으로 두고 [connectWithRetry]가 간격을 늘려
 * 가며 다시 연결한다: 한 번 실패로 프로세스 내내 '모름'에 갇히지 않게.
 *
 * Car 서비스가 죽어도 프로세스가 함께 죽지 않게 상태 리스너를 받는 `createCar`로 만든다([CarState]와 같은 방식, API 30+).
 * 리스너 없이 만든 연결이 한 프로세스에 하나라도 있으면 플랫폼이 그 프로세스를 끝낸다: 2026-11-12 에뮬레이터에서 Car 서비스를
 * 죽이자 이 클래스의 연결 때문에 앱이 죽었다(logcat `Car service has crashed. Client(null) is not handling it`).
 * 죽으면 '모름'(= 제한)으로 돌아가고 [connectWithRetry]가 다시 연결한다.
 */
class UxRestrictionWatcher(context: Context) {
    private val appContext = context.applicationContext
    private val tracker = UxRestrictionTracker()
    @Volatile private var car: Car? = null
    /** Car 서비스가 죽었다는 신호. 여러 번 와도 한 번으로 본다 */
    private val lost = Channel<Unit>(Channel.CONFLATED)
    private val lifecycle by lazy {
        Car.CarServiceLifecycleListener { c, ready ->
            if (!ready && car === c) {
                KitLog.w("car service died: driving restriction is unknown (restricted) until reconnected")
                tracker.detach()
                lost.trySend(Unit)
            }
        }
    }

    val state: StateFlow<UxRestrictions> = tracker.state

    /** 한 번 연결한다. 이미 붙었으면 아무것도 하지 않는다 */
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (car != null) return@withContext
        // createCar는 차 환경이 아니면 null을 준다(플랫폼 타입이라 그냥 쓰면 NPE로 프로세스가 죽는다)
        val c: Car? = runCatching { createCar() }.onFailure { KitLog.w("Car connect failed: ${it.message}") }.getOrNull()
        if (c == null || !c.isConnected) {
            // 리스너를 넘겨 만든 Car는 서비스가 아직 없어도 객체를 준다: 놓고 다시 시도하게 한다
            runCatching { c?.disconnect() }
            tracker.fail("Car 연결 실패")
            return@withContext
        }
        car = c
        tracker.attach(c)
    }

    private fun createCar(): Car? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Car.createCar(appContext, null, CONNECT_WAIT_MS, lifecycle)
        else Car.createCar(appContext)

    /**
     * 제한을 읽을 때까지 다시 연결하고(1초부터 두 배씩, 최대 [maxDelayMs]), 그 뒤 Car 서비스가 죽으면 다시 연결한다.
     * 코루틴을 취소할 때까지 돌아오지 않는다
     */
    suspend fun connectWithRetry(maxDelayMs: Long = 30_000L) {
        while (true) {
            var wait = 1_000L
            while (true) {
                connect()
                if (state.value is UxRestrictions.Known) break
                release()
                delay(wait)
                wait = (wait * 2).coerceAtMost(maxDelayMs)
            }
            lost.receive()
            release()
        }
    }

    fun release() {
        tracker.detach()
        runCatching { car?.disconnect() }
        car = null
    }

    private companion object {
        /** Car 서비스가 준비되기를 기다리는 시간 ([CarState]와 같다) */
        const val CONNECT_WAIT_MS = 5_000L
    }
}

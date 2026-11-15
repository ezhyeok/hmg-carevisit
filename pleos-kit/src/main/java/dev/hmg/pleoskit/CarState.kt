package dev.hmg.pleoskit

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * AAOS 표준 Car API로 얻는 주행 상태. 차속과 기어는 Vehicle SDK 대신 `CarPropertyManager`를 쓴다 (CLAUDE.md).
 * 기어와 차속은 Vehicle 신호와 같은 모양([ReadSignal]: 한 번 읽기 + 변경 구독)으로 준다.
 *
 * 필요한 권한
 * - 기어: `android.car.permission.CAR_POWERTRAIN` (normal)
 * - 차속: `android.car.permission.CAR_SPEED` (dangerous, 실행 중 요청 필요). 없으면 [speedMps]의 조회·구독이 실패한다
 *
 * 실패는 삼키지 않는다 (리뷰 R-F5, 서비스 감사 SV-D13). 예전에는 `Car.createCar` 실패와 구독 실패를 로그만 남기고 넘어가서,
 * 쓰는 쪽은 값이 오지 않는 흐름을 재시작할 때까지 기다렸다: [connect]는 붙지 못하면 예외를 던지고, 신호의 조회·구독도
 * 실패하면 예외로 끝난다. 다시 시도는 쓰는 쪽이 한다 (쉼표: coach 모듈 SignalCollector).
 *
 * Car 서비스가 죽으면 [connected]가 false가 된다 (서비스 감사 P8). `Car.createCar(context)`로 만든 연결은 Car 서비스가 죽을 때
 * 플랫폼이 앱 프로세스를 함께 죽인다. 상태 리스너를 받는 `createCar(Context, Handler, long, CarServiceLifecycleListener)`
 * (android.car.jar, API 30+)로 만들면 죽이지 않고 알려 준다. 그때 [release] 후 다시 [connect] 한다.
 * 단, 같은 프로세스의 다른 Car 연결(화면의 UxRestrictionWatcher)이 리스너 없이 만든 것이면 프로세스는 여전히 죽는다.
 */
class CarState(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private val ux = UxRestrictionTracker()
    private val _connected = MutableStateFlow(false)

    /** Car 서비스에 붙어 있는지. 붙기 전과 Car 서비스가 죽은 뒤에는 false */
    val connected: StateFlow<Boolean> = _connected

    /**
     * 주행 중 UX 제한이 걸려 있는지(`CarUxRestrictions.isRequiresDistractionOptimization`). true면 화면 조작을 줄여야 한다.
     * 모르면(연결 전·구독 실패·해제) true다. 예전 기본값 false는 시작 직후 주행 중에도 정차 화면을 열었다(UI 감사 #2).
     * 모르는 상태와 기능별 제한 비트까지 보려면 [uxRestrictions]를 쓴다.
     */
    val drivingRestricted: StateFlow<Boolean> = ux.restricted

    /** UX 제한 전체 (모르는 상태를 [UxRestrictions.Unknown]으로 구분한다) */
    val uxRestrictions: StateFlow<UxRestrictions> = ux.state

    /**
     * Car 서비스의 생사 (메인 스레드로 온다). 죽으면 이 연결에서 얻은 매니저와 구독은 모두 못 쓴다.
     * API 30 미만에는 이 인터페이스가 없으므로 쓸 때 만든다.
     */
    private val lifecycle by lazy { Car.CarServiceLifecycleListener { c, ready -> if (!ready) onLost(c) } }

    /**
     * Car 서비스에 연결한다. 이미 붙어 있으면 아무것도 하지 않는다. 붙지 못하면 [PleosSdkException]
     * (차 환경이 아니거나, Car 서비스가 [CONNECT_WAIT_MS] 안에 준비되지 않았다).
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        synchronized(lock) { if (car != null && _connected.value) return@withContext }
        release()
        val c = try {
            createCar()
        } catch (e: Exception) {
            null.also { KitLog.w("Car.createCar failed: ${e.message}") }
        }
        if (c == null) {
            // createCar는 차 환경이 아니면 null을 준다. 그때 주행 제한은 '모름'(= 제한)으로 남는다
            ux.fail("Car 연결 실패")
            throw PleosSdkException("Car.createCar", "no car service")
        }
        if (!c.isConnected) {
            // 리스너를 넘겨 만든 Car는 서비스가 아직 없어도 객체를 준다. 기다리지 않고 놓은 뒤 다시 시도하게 한다
            runCatching { c.disconnect() }
            ux.fail("Car 연결 실패")
            throw PleosSdkException("Car.createCar", "car service not ready in ${CONNECT_WAIT_MS}ms")
        }
        synchronized(lock) { car = c }
        // 주행 제한부터: 권한이 필요 없고 화면이 가장 먼저 알아야 한다
        ux.attach(c)
        val pm = try {
            c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        } catch (e: Exception) {
            release()
            throw PleosSdkException("Car.getCarManager", e.message ?: e.javaClass.simpleName, e)
        }
        synchronized(lock) { propertyManager = pm }
        _connected.value = true
    }

    private fun createCar(): Car? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Car.createCar(appContext, null, CONNECT_WAIT_MS, lifecycle)
        } else {
            Car.createCar(appContext)
        }

    private fun onLost(c: Car) {
        synchronized(lock) { if (car !== c) return }
        KitLog.w("car service died: gear, speed and driving restriction are unknown until reconnected")
        // 주행 제한도 모르게 된다 (= 제한). 죽은 매니저에서 리스너를 푸는 것은 실패해도 된다
        ux.detach()
        _connected.value = false
    }

    /** 현재 기어. `android.car.VehicleGear` 상수(GEAR_PARK = 4, GEAR_DRIVE = 8 등). 구독하면 지금 값이 곧 온다 */
    fun gear(): ReadSignal<Int> =
        property("gear", VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) { it as? Int }

    /** 차속(m/s). km/h는 3.6을 곱한다 */
    fun speedMps(): ReadSignal<Float> =
        property("speed", VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) { it as? Float }

    /** API 34 INFO_FUEL_TYPE is Integer[] (some VHAL bridges expose IntArray). CAR_INFO. */
    suspend fun fuelTypes(): Set<Int> = withContext(Dispatchers.IO) {
        when (val value = manager("fuelTypes").getProperty<Any>(VehiclePropertyIds.INFO_FUEL_TYPE, 0)?.value) {
            is Array<*> -> value.filterIsInstance<Int>().toSet()
            is IntArray -> value.toSet()
            else -> emptySet()
        }
    }
    /** A non-plug-in hybrid may omit ELECTRIC in INFO_FUEL_TYPE. Positive EV battery capacity is corroboration. */
    suspend fun evBatteryCapacityWh(): Float? = withContext(Dispatchers.IO) {
        manager("evBatteryCapacity").getProperty<Float>(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, 0)?.value
    }
    companion object Fuel {
        const val ELECTRIC_FUEL = android.car.FuelType.ELECTRIC
        const val PARK_GEAR = android.car.VehicleGear.GEAR_PARK
        const val CONNECT_WAIT_MS = 5_000L
    }

    private fun <T : Any> property(name: String, propertyId: Int, rateHz: Float, cast: (Any?) -> T?) = ReadSignal(
        name = name,
        getter = {
            withContext(Dispatchers.IO) {
                val pm = manager(name)
                try {
                    cast(pm.getProperty<Any>(propertyId, 0)?.value)
                } catch (e: Exception) {
                    throw PleosSdkException(name, e.message ?: e.javaClass.simpleName, e)
                }
            }
        },
        changesFactory = {
            callbackFlow {
                val pm = manager(name)
                val callback = object : CarPropertyManager.CarPropertyEventCallback {
                    override fun onChangeEvent(value: CarPropertyValue<*>) {
                        trySend(cast(value.value))
                    }

                    override fun onErrorEvent(propId: Int, zone: Int) = KitLog.w("$name error zone=$zone")
                }
                val registered = try {
                    withContext(Dispatchers.IO) { pm.registerCallback(callback, propertyId, rateHz) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw PleosSdkException(name, e.message ?: e.javaClass.simpleName, e)
                }
                // 읽을 권한이 없는 속성은 예외 없이 false가 온다. 예전에는 이 값을 보지 않아서 CAR_SPEED 권한이 없으면
                // 차속이 로그도 없이 비어 있었고, 나중에 권한을 받아도 다시 걸지 않았다 (서비스 감사 SV-D13)
                if (!registered) throw PleosSdkException(name, "registerCallback refused (missing permission or unsupported property)")
                awaitClose { runCatching { pm.unregisterCallback(callback) } }
            }
        },
    )

    private fun manager(name: String): CarPropertyManager =
        synchronized(lock) { propertyManager } ?: throw PleosSdkException(name, "car service not connected")

    /** 연결을 놓는다. 구독 중인 흐름은 먼저 취소할 것 */
    fun release() {
        val c = synchronized(lock) {
            val current = car
            car = null
            propertyManager = null
            current
        }
        ux.detach()
        _connected.value = false
        runCatching { c?.disconnect() }
    }


}

package dev.hmg.pleoskit

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import ai.pleos.playground.vehicle.listener.SeatBackrestAnglePositionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Vehicle SDK를 코루틴/Flow로 감싼 진입점.
 *
 * - 모든 호출은 SDK 전용 스레드에서 실행된다(호출 스레드를 수 초간 막기 때문).
 * - 자주 쓰는 신호는 [ReadSignal]/[ReadWriteSignal]로 미리 감싸 두었다. 여기에 없는 API는 [call], [call2],
 *   [observe]로 감쌀 수 있고, [raw]로 SDK 객체를 직접 쓸 수도 있다.
 * - 영역(area)은 하드코딩하지 말고 `*Areas()`(capability 조회)로 얻는다. 차종마다 지원 영역이 다르다.
 * - 필요한 Manifest 권한은 각 함수 주석에 적었다.
 */
class VehicleKit(context: Context) {
    private val appContext = context.applicationContext

    /**
     * SDK 객체와 거기서 얻은 도메인 객체 한 벌. [reconnect]가 통째로 새로 만든다: 신호 객체([ReadSignal])는 호출할 때마다
     * 지금 것을 쓰므로 다시 붙은 뒤에도 그대로 쓸 수 있다.
     */
    private class Link(context: Context) {
        val vehicle = Vehicle(context)
        val hvac by lazy { vehicle.getHVAC() }
        val door by lazy { vehicle.getDoor() }
        val window by lazy { vehicle.getWindow() }
        val seat by lazy { vehicle.getSeat() }
        val drivingMode by lazy { vehicle.getDrivingMode() }
        val odometer by lazy { vehicle.getOdometer() }
        val brake by lazy { vehicle.getBrake() }
        val evBattery by lazy { vehicle.getEvBattery() }
        val steering by lazy { vehicle.getSteeringWheel() }
        val turnSignal by lazy { vehicle.getTurnSignal() }
    }

    private val link = LazySdk("Vehicle") { Link(appContext) }

    /**
     * SDK 원본 객체. kit에 없는 API를 쓸 때 사용한다. 호출은 [call]/[observe] 안에서 할 것.
     * 처음 쓰는 곳에서 만들어진다: [connect]를 먼저 부르면 SDK 전용 스레드에서 만들어진다 (메인 스레드에서 처음 쓰지 말 것).
     * [reconnect] 뒤에는 새 객체다 (들고 있지 말고 쓸 때마다 읽는다).
     */
    val raw: Vehicle get() = link.value.vehicle

    private val hvac get() = link.value.hvac
    private val door get() = link.value.door
    private val window get() = link.value.window
    private val seat get() = link.value.seat
    private val drivingMode get() = link.value.drivingMode
    private val odometer get() = link.value.odometer
    private val brake get() = link.value.brake
    private val evBattery get() = link.value.evBattery
    private val steering get() = link.value.steering
    private val turnSignalDomain get() = link.value.turnSignal

    @Volatile
    private var connected = false

    /** Vehicle 서비스에 연결한다. 다른 호출보다 먼저 한 번 부른다. 여러 번 불러도 된다. */
    suspend fun connect() = withContext(SdkThreads.vehicle) {
        if (!connected) {
            link.value.vehicle.initialize()
            connected = true
        }
    }

    /**
     * 연결이 죽었다고 보고 SDK 객체를 버린 뒤 새로 붙는다. 걸어 둔 리스너는 사라지므로 수집 중인 Flow는 다시 수집해야 한다.
     *
     * 2026-11-12 에뮬레이터: Pleos 차량 서비스가 죽었다 살아나면(`am crash`) SDK가 스스로 다시 붙고 리스너도 다시 걸어 준다.
     * 서비스가 멈추면(`am force-stop`) SDK는 다시 붙지 않는다: 이벤트가 끊기고 값이 멈춘다. 그때 이것을 부른다
     * (언제 부를지는 쓰는 쪽이 정한다. 쉼표는 읽기가 모두 실패하고 이벤트가 없을 때: coach 모듈 SignalCollector).
     */
    suspend fun reconnect() = withContext(SdkThreads.vehicle) {
        connected = false
        link.drop()?.let { old -> runCatching { old.vehicle.release() } }
        link.value.vehicle.initialize()
        connected = true
    }

    /** 진행 중인 호출이 끝난 뒤에 부를 것. 수집 중인 Flow는 먼저 취소한다. */
    fun release() {
        SdkThreads.vehicle.executor.execute {
            link.ifCreated { runCatching { it.vehicle.release() } }
            connected = false
        }
    }

    // ------------------------------------------------------------ 범용 어댑터

    /** 콜백 인자가 하나인 SDK 호출(get*, 영역 없는 check*)을 suspend로 바꾼다. */
    suspend fun <T> call(
        name: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): T = sdkCall(SdkThreads.vehicle, name, timeoutMs, block)

    /** 콜백 인자가 둘인 SDK 호출(set*, 영역 있는 check*)을 suspend로 바꾼다. */
    suspend fun <A, B> call2(
        name: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: (ok: (A, B) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): Pair<A, B> = sdkCall(SdkThreads.vehicle, name, timeoutMs) { ok, fail ->
        block({ a, b -> ok(a to b) }, fail)
    }

    /**
     * `registerXxx`와 `unregisterXxx` 쌍을 cold Flow로 바꾼다. [listener]는 SDK 리스너 객체를 만들어 돌려준다.
     * 리스너의 onFailed는 로그만 남기고 Flow는 닫지 않는다.
     */
    fun <L : Any, T> observe(
        name: String,
        listener: (emit: (T) -> Unit, fail: (Exception) -> Unit) -> L,
        register: (L) -> Unit,
        unregister: (L) -> Unit,
    ): Flow<T> = callbackFlow {
        val l = listener({ trySend(it) }, { e -> KitLog.w("$name listener failed: ${e.message}") })
        withContext(SdkThreads.vehicle) { register(l) }
        awaitClose {
            SdkThreads.vehicle.executor.execute { runCatching { unregister(l) } }
        }
    }

    // ------------------------------------------------------------ 공조 (CAR_CLIMATE / CONTROL_CAR_CLIMATE)

    /** 내기순환을 지원하는 영역. 에뮬레이터에서는 1열 전체를 묶은 영역 하나가 온다. */
    suspend fun hvacAreas(): List<SeatArea> =
        call2<CapabilityConfig, List<SeatArea>>("checkHvacRecirculationCapabilities") { ok, fail ->
            hvac.checkHvacRecirculationCapabilities(ok, fail)
        }.second

    /** 내기순환. true = 내기, false = 외기. */
    fun recirculation(area: SeatArea) = ReadWriteSignal<Boolean>(
        name = "hvacRecirculation",
        getter = { call<Boolean?>("getHvacRecirculationState") { ok, fail -> hvac.getHvacRecirculationState(area, ok, fail) } },
        changesFactory = {
            observe<HvacRecirculationStateListener, Boolean?>(
                "hvacRecirculation",
                { emit, fail ->
                    object : HvacRecirculationStateListener {
                        override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) = emit(state)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { hvac.registerHvacRecirculationState(area, it) },
                { hvac.unregisterHvacRecirculationState(area, it) },
            )
        },
        setter = { v ->
            call2<SeatArea, Boolean?>("setHvacRecirculation") { ok, fail -> hvac.setHvacRecirculation(area, v, ok, fail) }
        },
    )

    /** 공조 전원. */
    fun hvacPower(area: SeatArea) = ReadWriteSignal<Boolean>(
        name = "hvacPower",
        getter = { call<Boolean?>("getHvacPowerState") { ok, fail -> hvac.getHvacPowerState(area, ok, fail) } },
        changesFactory = {
            observe<HvacPowerStateListener, Boolean?>(
                "hvacPower",
                { emit, fail ->
                    object : HvacPowerStateListener {
                        override fun onHvacPowerStateUpdated(state: Boolean?) = emit(state)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { hvac.registerHvacPowerState(area, it) },
                { hvac.unregisterHvacPowerState(area, it) },
            )
        },
        setter = { v -> call2<SeatArea, Boolean?>("setHvacPower") { ok, fail -> hvac.setHvacPower(area, v, ok, fail) } },
    )

    /** 온도 설정을 지원하는 영역과 설정 가능 범위(섭씨). */
    suspend fun hvacTemperatureAreas(): Map<SeatArea, AreaBounds<Float>> =
        call2<CapabilityConfig, Map<SeatArea, AreaBounds<Float>>>("checkHvacTemperatureCapabilities") { ok, fail ->
            hvac.checkHvacTemperatureCapabilities(ok, fail)
        }.second

    /**
     * 설정 온도(섭씨). [ReadWriteSignal.setAndConfirm]에는 [temperatureMatches]를 넘길 것.
     * 공조 전원이 꺼져 있으면 조회·제어 모두 `PropertyNotAvailableException`으로 실패한다(에뮬레이터 확인).
     * 먼저 [hvacPower]를 켠다.
     */
    fun hvacTemperatureC(area: SeatArea) = ReadWriteSignal<Float>(
        name = "hvacTemperature",
        getter = {
            call<Float?>("getHvacTemperature") { ok, fail -> hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail) }
        },
        changesFactory = {
            observe<HvacTemperatureValueListener, Float?>(
                "hvacTemperature",
                { emit, fail ->
                    object : HvacTemperatureValueListener {
                        override fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?) = emit(celsius)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { hvac.registerHvacTemperature(area, it) },
                { hvac.unregisterHvacTemperature(area, it) },
            )
        },
        setter = { v ->
            call2<SeatArea, Float?>("setHvacTemperature") { ok, fail ->
                hvac.setHvacTemperature(area, v, TemperatureUnit.CELSIUS, ok, fail)
            }
        },
    )

    /** 풍량을 지원하는 영역과 단계 범위. */
    suspend fun hvacFanSpeedAreas(): Map<SeatArea, AreaBounds<Int>> =
        call2<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>("checkHvacFanSpeedCapabilities") { ok, fail ->
            hvac.checkHvacFanSpeedCapabilities(ok, fail)
        }.second

    /** 풍량 단계. */
    fun hvacFanSpeed(area: SeatArea) = ReadWriteSignal<Int>(
        name = "hvacFanSpeed",
        getter = { call<Int?>("getHvacFanSpeed") { ok, fail -> hvac.getHvacFanSpeed(area, ok, fail) } },
        changesFactory = {
            observe<HvacFanSpeedListener, Int?>(
                "hvacFanSpeed",
                { emit, fail ->
                    object : HvacFanSpeedListener {
                        override fun onHvacFanSpeedUpdated(speed: Int?) = emit(speed)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { hvac.registerHvacFanSpeed(area, it) },
                { hvac.unregisterHvacFanSpeed(area, it) },
            )
        },
        setter = { v -> call2<SeatArea, Int?>("setHvacFanSpeed") { ok, fail -> hvac.setHvacFanSpeed(area, v, ok, fail) } },
    )

    // ------------------------------------------------------------ 도어 (CAR_DOORS)

    /** 도어 위치를 지원하는 영역과 값 범위(에뮬레이터: 0 = 닫힘, 1 = 열림). */
    suspend fun doorAreas(): Map<DoorArea, AreaBounds<Int>> =
        call2<CapabilityConfig, Map<DoorArea, AreaBounds<Int>>>("checkDoorPositionCapabilities") { ok, fail ->
            door.checkDoorPositionCapabilities(ok, fail)
        }.second

    /** 도어 위치. 0이면 닫힘. */
    fun doorPosition(area: DoorArea) = ReadSignal<Int>(
        name = "doorPosition",
        getter = { call<Int?>("getCurrentDoorPosition") { ok, fail -> door.getCurrentDoorPosition(area, ok, fail) } },
        changesFactory = {
            observe<DoorPositionListener, Int?>(
                "doorPosition",
                { emit, fail ->
                    object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) = emit(pos)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { door.registerDoorPosition(area, it) },
                { door.unregisterDoorPosition(area, it) },
            )
        },
    )

    // ------------------------------------------------------------ 창문 (CAR_WINDOWS / CONTROL_CAR_WINDOWS)

    /** 창문 위치를 지원하는 영역과 값 범위. */
    suspend fun windowAreas(): Map<WindowArea, AreaBounds<Int>> =
        call2<CapabilityConfig, Map<WindowArea, AreaBounds<Int>>>("checkWindowPositionCapabilities") { ok, fail ->
            window.checkWindowPositionCapabilities(ok, fail)
        }.second

    /** 창문 위치. 범위는 [windowAreas]의 AreaBounds. */
    fun windowPosition(area: WindowArea) = ReadWriteSignal<Int>(
        name = "windowPosition",
        getter = { call<Int?>("getCurrentWindowPosition") { ok, fail -> window.getCurrentWindowPosition(area, ok, fail) } },
        changesFactory = {
            observe<WindowPositionListener, Int?>(
                "windowPosition",
                { emit, fail ->
                    object : WindowPositionListener {
                        override fun onWindowPositionUpdated(position: Int?) = emit(position)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { window.registerWindowPosition(area, it) },
                { window.unregisterWindowPosition(area, it) },
            )
        },
        setter = { v -> call2<WindowArea, Int?>("setWindowPosition") { ok, fail -> window.setWindowPosition(area, v, ok, fail) } },
    )

    // ------------------------------------------------------------ 좌석 (CAR_SEATS)

    /** 좌석 점유를 지원하는 영역. */
    suspend fun seatOccupancyAreas(): List<SeatArea> =
        call2<CapabilityConfig, List<SeatArea>>("checkSeatOccupancyCapabilities") { ok, fail ->
            seat.checkSeatOccupancyCapabilities(ok, fail)
        }.second

    /** 좌석 점유 여부. */
    fun seatOccupancy(area: SeatArea) = ReadSignal<Boolean>(
        name = "seatOccupancy",
        getter = { call<Boolean?>("getCurrentSeatOccupancy") { ok, fail -> seat.getCurrentSeatOccupancy(area, ok, fail) } },
        changesFactory = {
            observe<SeatOccupancyListener, Boolean?>(
                "seatOccupancy",
                { emit, fail ->
                    object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupancy: Boolean?) = emit(occupancy)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { seat.registerSeatOccupancy(area, it) },
                { seat.unregisterSeatOccupancy(area, it) },
            )
        },
    )

    /** 등받이 각도 위치를 지원하는 영역과 값 범위. */
    suspend fun seatBackrestAreas(): Map<SeatArea, AreaBounds<Int>> =
        call2<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>("checkSeatBackrestAnglePositionCapabilities") { ok, fail ->
            seat.checkSeatBackrestAnglePositionCapabilities(ok, fail)
        }.second

    /** 등받이 각도 위치(읽기 전용). 움직이려면 [moveSeatBackrest]. */
    fun seatBackrestPosition(area: SeatArea) = ReadSignal<Int>(
        name = "seatBackrestPosition",
        getter = {
            call<Int?>("getCurrentSeatBackrestAnglePosition") { ok, fail -> seat.getCurrentSeatBackrestAnglePosition(area, ok, fail) }
        },
        changesFactory = {
            observe<SeatBackrestAnglePositionListener, Int?>(
                "seatBackrestPosition",
                { emit, fail ->
                    object : SeatBackrestAnglePositionListener {
                        override fun onSeatBackrestAnglePositionUpdated(position: Int?) = emit(position)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { seat.registerSeatBackrestAnglePosition(area, it) },
                { seat.unregisterSeatBackrestAnglePosition(area, it) },
            )
        },
    )

    /**
     * 등받이를 움직인다. 위치를 지정하는 API는 없고 방향 명령만 있다: `BACKWARD`/`FORWARD`로 움직이기 시작하고
     * `STOP`으로 멈춘다. 원하는 위치에서 멈추려면 [seatBackrestPosition]을 보면서 `STOP`을 보낸다.
     * 권한: `CONTROL_CAR_SEATS`.
     */
    suspend fun moveSeatBackrest(area: SeatArea, direction: MoveControl) {
        val ack = call2<SeatArea, MoveControl?>("moveSeatBackrestAngle") { ok, fail -> seat.moveSeatBackrestAngle(area, direction, ok, fail) }
        if (ack.first != area || ack.second != direction) throw PleosSdkException("moveSeatBackrestAngle", "direction acknowledgement mismatch")
    }

    // ------------------------------------------------------------ 주행 (CAR_DRIVE)

    /** 크루즈 컨트롤 상태. */
    fun cruiseControl() = ReadSignal<CruiseControl>(
        name = "cruiseControl",
        getter = { call<CruiseControl?>("getCruiseControlState") { ok, fail -> drivingMode.getCruiseControlState(ok, fail) } },
        changesFactory = {
            observe<CruiseControlStateListener, CruiseControl?>(
                "cruiseControl",
                { emit, fail ->
                    object : CruiseControlStateListener {
                        override fun onCruiseControlStateUpdated(state: CruiseControl?) = emit(state)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { drivingMode.registerCruiseControlState(it) },
                { drivingMode.unregisterCruiseControlState(it) },
            )
        },
    )

    /** 이번 주행의 누적 주행 시간(분). 에뮬레이터는 이 속성을 지원하지 않는다(ERROR_NOT_SUPPORTED_PROPERTY). */
    fun tripDrivingTimeMin() = ReadSignal<Int>(
        name = "tripCurrentDrivingTime",
        getter = { call<Int?>("getTripCurrentDrivingTime") { ok, fail -> odometer.getTripCurrentDrivingTime(ok, fail) } },
        changesFactory = {
            observe<DrivingTimeListener, Int?>(
                "tripCurrentDrivingTime",
                { emit, fail ->
                    object : DrivingTimeListener {
                        override fun onDrivingTimeUpdated(value: Int?) = emit(value)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { odometer.registerTripCurrentDrivingTime(it) },
                { odometer.unregisterTripCurrentDrivingTime(it) },
            )
        },
    )

    /** 브레이크 페달을 밟고 있는지. */
    fun brakePedal() = ReadSignal<Boolean>(
        name = "brakePedal",
        getter = { call<Boolean?>("getCurrentBrakePedalStatus") { ok, fail -> brake.getCurrentBrakePedalStatus(ok, fail) } },
        changesFactory = {
            observe<BrakePedalStatusListener, Boolean?>(
                "brakePedal",
                { emit, fail ->
                    object : BrakePedalStatusListener {
                        override fun onBrakePedalStatusUpdated(status: Boolean?) = emit(status)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { brake.registerBrakePedalStatus(it) },
                { brake.unregisterBrakePedalStatus(it) },
            )
        },
    )

    // ------------------------------------------------------------ 에너지 (CAR_ENERGY)

    /** 배터리 잔량(%). */
    fun evStateOfCharge() = ReadSignal<Int>(
        name = "evStateOfCharge",
        getter = { call<Int?>("getEvBatteryStateOfCharge") { ok, fail -> evBattery.getEvBatteryStateOfCharge(ok, fail) } },
        changesFactory = {
            observe<EvBatteryStateOfChargeListener, Int?>(
                "evStateOfCharge",
                { emit, fail ->
                    object : EvBatteryStateOfChargeListener {
                        override fun onEvBatteryStateOfChargeUpdated(value: Int?) = emit(value)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { evBattery.registerEvBatteryStateOfCharge(it) },
                { evBattery.unregisterEvBatteryStateOfCharge(it) },
            )
        },
    )

    // ------------------------------------------------------------ 주행 입력 (CAR_DRIVE / CAR_LIGHTS)

    /** 조향각(도). 운전 스타일 분석에 쓴다. CAR_DRIVE */
    fun steeringAngle() = ReadSignal<Float>(
        name = "steeringWheelAngle",
        getter = { call<Float?>("getCurrentSteeringWheelAngle") { ok, fail -> steering.getCurrentSteeringWheelAngle(ok, fail) } },
        changesFactory = {
            observe<SteeringWheelAngleListener, Float?>(
                "steeringWheelAngle",
                { emit, fail ->
                    object : SteeringWheelAngleListener {
                        override fun onSteeringWheelAngleUpdated(angle: Float?) = emit(angle)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { steering.registerSteeringWheelAngle(it) },
                { steering.unregisterSteeringWheelAngle(it) },
            )
        },
    )

    /** 방향지시등 상태(OFF / LEFT / RIGHT). CAR_LIGHTS */
    fun turnSignal() = ReadSignal<TurnSignalValue>(
        name = "turnSignalState",
        getter = { call<TurnSignalValue?>("getCurrentTurnSignalState") { ok, fail -> turnSignalDomain.getCurrentTurnSignalState(ok, fail) } },
        changesFactory = {
            observe<TurnSignalStateListener, TurnSignalValue?>(
                "turnSignalState",
                { emit, fail ->
                    object : TurnSignalStateListener {
                        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = emit(state)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                },
                { turnSignalDomain.registerTurnSignalState(it) },
                { turnSignalDomain.unregisterTurnSignalState(it) },
            )
        },
    )

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L

        /** 온도는 차량이 0.5도 단위로 맞추므로 근삿값으로 비교한다. */
        val temperatureMatches: (Float?, Float) -> Boolean = { actual, requested ->
            actual != null && abs(actual - requested) < 0.6f
        }

        /**
         * 이 차가 그 신호를 지원하지 않아서 난 실패인지 (다시 물어도 같다). 지원 영역 조회(`*Areas()`)가 이렇게 실패하면
         * 그 기능은 없는 것으로 본다.
         *
         * 메시지에 든 오류 코드 이름으로 가린다. 문서의 `VehicleException.errorCode`는 2.3.2 jar에 난독화된 이름
         * (`callback.exception.a`)으로 들어 있어 타입으로 쓸 수 없다. 에뮬레이터에서 주행 시간 조회가
         * `ERROR_NOT_SUPPORTED_PROPERTY`로 실패했다 (docs/kit-selftest.md).
         */
        fun notSupported(e: Throwable): Boolean =
            generateSequence(e) { it.cause }.take(8).any { it.message?.contains("ERROR_NOT_SUPPORTED") == true }
    }
}

/** 로그나 화면에 쓸 영역 이름. SDK 영역 클래스는 toString이 해시값만 준다. */
fun SeatArea.label(): String = values.joinToString("+")
fun DoorArea.label(): String = values.joinToString("+")
fun WindowArea.label(): String = values.joinToString("+")

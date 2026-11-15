package dev.hmg.pleoskit

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 내비 앱이 보내는 이벤트. [NaviKit.events]로 받는다. */
sealed interface NaviEvent {
    data class CurrentLocation(val info: CurrentLocationInfo) : NaviEvent
    data class Destination(val info: DestinationInfo) : NaviEvent
    data class RouteState(val info: RouteStateInfo) : NaviEvent
    data class Tbt(val list: List<TBTInfo>) : NaviEvent
    data class Waypoints(val list: List<WaypointInfo>) : NaviEvent
    data class Trip(val info: TripInfo) : NaviEvent
    data class Driving(val info: DrivingInfo) : NaviEvent
    data class Status(val status: NavigationStatus) : NaviEvent
    data class SpeedLimit(val kph: Int) : NaviEvent
    data class RouteStarted(val info: RouteStartInfo) : NaviEvent
    data object RoutingCompleted : NaviEvent
    data object RouteCancelled : NaviEvent
    data object ReRouteEnded : NaviEvent
    data object DestinationChanged : NaviEvent
    data class DestinationArrived(val info: DestinationArrivedInfo) : NaviEvent
    data class WaypointChanged(val info: WaypointChangedInfo) : NaviEvent
    data class WaypointArrived(val info: WaypointArrivedInfo) : NaviEvent
    data class Error(val code: NaviErrorCode) : NaviEvent
}

/**
 * NaviHelper SDK를 코루틴으로 감싼 진입점. 요청은 Pleos 내비 앱으로 전달되고 응답은 이벤트로 온다.
 *
 * 에뮬레이터에서 확인한 동작을 반영했다.
 * - 조회(`get*`)는 호출해야 응답 이벤트가 온다. 요청과 응답을 짝짓는 ID가 없으므로 한 번에 하나씩만 보낸다.
 * - 경로 요청을 연달아 보내면 시작된 경로가 곧바로 취소되는 일이 있었다. 명령 사이에 [COMMAND_GAP_MS]만큼 간격을 둔다.
 * - `onRouteStarted`는 앱에 오지 않았다. 길안내 시작 여부는 [routeState]의 `naviMode`로 확인한다.
 * - `requestRoute`를 보내면 내비 앱이 화면 앞으로 온다. 이벤트를 계속 받으려면 포그라운드 서비스에서 써야 한다.
 *
 * 권한: 조회는 `pleos.car.permission.NAVI_ROUTE`, 경로 변경은 `NAVI_ROUTE_SEARCH`.
 */
class NaviKit(context: Context) {
    private val appContext = context.applicationContext
    private val sdk = LazySdk("NaviHelper") { NaviHelper(appContext) }

    /**
     * SDK 원본 객체. kit에 없는 API(즐겨찾기, 최근 목적지, 화면 전환 등)에 쓴다.
     * 처음 쓰는 곳에서 만들어진다: [connect]를 먼저 부르면 SDK 전용 스레드에서 만들어진다 (메인 스레드에서 처음 쓰지 말 것).
     */
    val raw: NaviHelper get() = sdk.value

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 내비가 보내는 모든 이벤트. 구독하기 전에 온 이벤트는 받지 못한다. */
    val events: SharedFlow<NaviEvent> = _events

    private val mutex = Mutex()
    private val connectMutex = Mutex()
    private var lastCommandAt = 0L

    @Volatile
    private var connected = false

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = emit(NaviEvent.CurrentLocation(currentLocationInfo))
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = emit(NaviEvent.Destination(destinationInfo))
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = emit(NaviEvent.RouteState(routeStateInfo))
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = emit(NaviEvent.Tbt(tbtInfoList))
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = emit(NaviEvent.Waypoints(waypointInfoList))
        override fun onTripInfo(tripInfo: TripInfo) = emit(NaviEvent.Trip(tripInfo))
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = emit(NaviEvent.Driving(drivingInfo))
        override fun onNavigationStatus(status: NavigationStatus) = emit(NaviEvent.Status(status))
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = emit(NaviEvent.SpeedLimit(speedLimit))
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = emit(NaviEvent.RouteStarted(routeStartInfo))
        override fun onRoutingCompleted() = emit(NaviEvent.RoutingCompleted)
        override fun onRouteCancelled() = emit(NaviEvent.RouteCancelled)
        override fun onReRouteEnded() = emit(NaviEvent.ReRouteEnded)
        override fun onDestinationChanged() = emit(NaviEvent.DestinationChanged)
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = emit(NaviEvent.DestinationArrived(destinationArrivedInfo))
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = emit(NaviEvent.WaypointChanged(waypointChangedInfo))
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = emit(NaviEvent.WaypointArrived(waypointArrivedInfo))
        override fun onError(errorCode: NaviErrorCode) = emit(NaviEvent.Error(errorCode))
    }

    private fun emit(event: NaviEvent) {
        _events.tryEmit(event)
    }

    /** 내비 앱에 연결하고 리스너를 등록한다. 다른 호출보다 먼저 한 번 부른다. */
    suspend fun connect(timeoutMs: Long = 30_000) = connectMutex.withLock {
        if (connected) return@withLock
        val ok = withTimeoutOrNull(timeoutMs) {
            withContext(SdkThreads.navi) {
                val helper = sdk.value
                suspendCancellableCoroutine { cont ->
                    val done = AtomicBoolean(false)
                    helper.initialize(
                        onConnected = { if (done.compareAndSet(false, true)) cont.resume(Unit) },
                        onFailed = { t ->
                            if (done.compareAndSet(false, true)) {
                                cont.resumeWithException(PleosSdkException("NaviHelper.initialize", t.message ?: "failed", t))
                            }
                        },
                    )
                }
                helper.addListener(listener)
            }
        }
        if (ok == null) throw PleosSdkException("NaviHelper.initialize", "no response in ${timeoutMs}ms")
        connected = true
    }

    fun release() {
        SdkThreads.navi.executor.execute {
            sdk.ifCreated { helper ->
                runCatching { helper.removeListener(listener) }
                runCatching { helper.release() }
            }
            connected = false
        }
    }

    // ------------------------------------------------------------ 조회 (NAVI_ROUTE)

    /** 내비가 보는 현재 위치와 주소. */
    suspend fun currentLocation(): CurrentLocationInfo =
        mutex.withLock { query<NaviEvent.CurrentLocation>("getCurrentLocationInfo") { raw.getCurrentLocationInfo() } }.info

    /** 현재 목적지. 길안내 중이 아니면 이름이 빈 값으로 온다. */
    suspend fun destination(): DestinationInfo =
        mutex.withLock { query<NaviEvent.Destination>("getDestinationInfo") { raw.getDestinationInfo() } }.info

    /** 내비 모드(`ROUTE_GUIDING`이면 길안내 중), 경로 옵션, 교통 요약. */
    suspend fun routeState(): RouteStateInfo = mutex.withLock { routeStateLocked() }

    /** 앞으로 남은 회전 안내 목록. 길안내 중이 아니면 빈 목록. */
    suspend fun tbt(): List<TBTInfo> = mutex.withLock { query<NaviEvent.Tbt>("getTBTInfo") { raw.getTBTInfo() } }.list

    /** 현재 경유지 목록. */
    suspend fun waypoints(): List<WaypointInfo> = mutex.withLock { waypointsLocked() }

    /** 경로가 있는 길안내인지 (내비 모의 주행 포함) */
    suspend fun isGuiding(): Boolean = hasRoute(routeState().naviMode)

    /**
     * 길안내를 시작하지 않고 목적지까지의 거리(m), 소요 시간(초), 도착 시 예상 SOC(%)를 받는다.
     * 경로를 찾지 못하면 [PleosSdkException].
     */
    suspend fun tripEstimate(request: TripRequest, timeoutMs: Long = 30_000): TripInfo = mutex.withLock {
        val event = awaitEvent("requestTrip", timeoutMs, { it is NaviEvent.Trip || it is NaviEvent.Error }) {
            raw.requestTrip(request)
        }
        when (event) {
            is NaviEvent.Trip -> event.info
            is NaviEvent.Error -> throw PleosSdkException("requestTrip", event.code.name)
            else -> error("unreachable")
        }
    }

    // ------------------------------------------------------------ 경로 변경 (NAVI_ROUTE_SEARCH)

    /**
     * 목적지로 길안내를 시작한다. 내비 앱이 화면 앞으로 온다.
     * [confirmTimeoutMs] 안에 경로가 있는 모드(`ROUTE_GUIDING`, 모의 주행 `SIMULATION_GUIDING`)가 되면 true.
     * 예전에는 `ROUTE_GUIDING`만 봐서 모의 주행 중에는 길안내가 시작돼도 실패로 알렸다 (구조 리뷰 D12, B8).
     */
    suspend fun startRoute(destination: RouteInfo, confirmTimeoutMs: Long = 45_000): Boolean = mutex.withLock {
        command("requestRoute") { raw.requestRoute(destination) }
        awaitGuiding(true, confirmTimeoutMs)
    }

    /** 목적지와 경유지(최대 2개)를 한 번에 지정해 길안내를 시작한다. */
    suspend fun startRouteWithWaypoints(request: RouteWithWaypointsRequest, confirmTimeoutMs: Long = 45_000): Boolean =
        mutex.withLock {
            command("requestRouteWithWaypoints") { raw.requestRouteWithWaypoints(request) }
            awaitGuiding(true, confirmTimeoutMs)
        }

    /**
     * 길안내 중인 경로에 경유지를 추가한다. 경유지 목록에 반영되면 true.
     * 좌표와 이름은 앱이 직접 구해야 한다(NaviHelper에는 POI 검색이 없다).
     *
     * Pleos 내비의 동작 (2026-11-12 에뮬레이터, SdkProbe `wp-add`)
     * - 경유지는 둘까지다. 둘이 차 있으면 요청을 조용히 버린다 (이벤트도 오류도 없다): 보내지 않고 곧바로 false.
     * - `waypointIndex`와 상관없이 **이미 있는 경유지 뒤에 붙는다**. 더 가까운 곳을 나중에 넣으면 먼 경유지에 먼저 갔다가
     *   되돌아오는 경로가 된다 (옥산 101km 뒤에 안성을 넣으니 안성이 177km가 됐다). 순서를 지키려면 [setWaypoints]를 쓴다.
     * - 경유지 목록(`WaypointInfo`)의 좌표는 0.0이다. 같은 곳인지는 보낸 이름(`poiName`이 `name`으로 돌아온다)으로 본다.
     * - 넣는 동안 길안내가 취소됐다가 다시 시작된다 (RouteCancelled → WaypointChanged(ADD)). 그 사이 목록은 잠깐 비어 있다.
     *
     * 확인: 목록에 요청한 곳이 있거나(이름, 좌표가 있으면 약 [WAYPOINT_MATCH_M] 안), 보내기 전 목록을 읽었고 개수가 늘었으면 들어간 것이다.
     * 예전에는 보내기 전 조회가 실패하면 0개로 보고 '1개 이상'이면 성공이라 해서, 원래 있던 경유지만으로도 거짓 성공을 냈다 (서비스 감사 P11).
     */
    suspend fun addWaypoint(waypoint: RequestWaypointInfo, confirmTimeoutMs: Long = 30_000): Boolean = mutex.withLock {
        val before = runCatching { waypointsLocked() }.getOrNull()
        if (before != null) {
            // 이미 경유지면 다시 넣지 않는다: 같은 곳을 또 보내면 목록이 늘지 않아 '확인 실패'가 됐다 (2026-11-12 에뮬레이터)
            if (isWaypoint(before, waypoint.latitude, waypoint.longitude, waypoint.poiName)) return@withLock true
            if (before.size >= MAX_WAYPOINTS) {
                KitLog.w("addWaypoint ${waypoint.poiName}: the navi already has $MAX_WAYPOINTS waypoints and ignores another")
                return@withLock false
            }
        }
        command("addWaypoint") { raw.addWaypoint(waypoint) }
        pollUntil(confirmTimeoutMs) { waypointAdded(before, waypointsLocked(), waypoint.latitude, waypoint.longitude, waypoint.poiName) }
    }

    /**
     * 목적지는 그대로 두고 경유지를 [waypoints] 순서로 다시 정한다 (최대 [MAX_WAYPOINTS]개). 길안내가 새로 시작된다.
     * 경유지 목록이 그 이름들로, 그 순서로 바뀌면 true. [addWaypoint]는 늘 뒤에 붙이므로, 이미 있는 경유지보다 가까운 곳을
     * 넣을 때 쓴다. 이미 있는 경유지를 지키려면 그 좌표를 알아야 한다 (내비는 경유지 좌표를 돌려주지 않는다).
     */
    suspend fun setWaypoints(destination: RouteInfo, waypoints: List<RequestWaypointInfo>, confirmTimeoutMs: Long = 45_000): Boolean = mutex.withLock {
        require(waypoints.size <= MAX_WAYPOINTS) { "at most $MAX_WAYPOINTS waypoints" }
        val indexed = waypoints.mapIndexed { i, w -> w.copy(waypointIndex = if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND) }
        command("requestRouteWithWaypoints") { raw.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination, indexed)) }
        val names = waypoints.map { it.poiName }
        pollUntil(confirmTimeoutMs) { hasRoute(routeStateLocked().naviMode) && waypointsLocked().map { it.name } == names }
    }

    /** 경유지를 지운다. */
    suspend fun removeWaypoint(index: WaypointIndex) = mutex.withLock {
        command("removeWaypoint") { raw.removeWaypoint(index) }
    }

    /** 내비 화면에서 장소 검색을 연다. 좌표를 모르는 곳(이름만 아는 휴게소 등)으로 안내할 때 쓴다 */
    suspend fun showPlaceSearch(keyword: String) = sdkSend(SdkThreads.navi, "requestNavigationScreen") {
        raw.requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword))
    }

    /** 길안내를 끝낸다. 경로가 있는 길안내 모드에서 벗어나면 true. */
    suspend fun cancelRoute(confirmTimeoutMs: Long = 20_000): Boolean = mutex.withLock {
        command("cancelRoute") { raw.cancelRoute() }
        awaitGuiding(false, confirmTimeoutMs)
    }

    // ------------------------------------------------------------ 내부

    private suspend fun routeStateLocked(): RouteStateInfo =
        query<NaviEvent.RouteState>("getRouteStateInfo") { raw.getRouteStateInfo() }.info

    private suspend fun waypointsLocked(): List<WaypointInfo> =
        query<NaviEvent.Waypoints>("getWaypointInfo") { raw.getWaypointInfo() }.list

    private suspend fun awaitGuiding(guiding: Boolean, timeoutMs: Long): Boolean =
        pollUntil(timeoutMs) { hasRoute(routeStateLocked().naviMode) == guiding }

    /** [check]가 true를 줄 때까지 2초 간격으로 다시 확인한다. 조회 실패는 다음 시도로 넘긴다. */
    private suspend fun pollUntil(timeoutMs: Long, check: suspend () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (runCatching { check() }.getOrDefault(false)) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(2000)
        }
    }

    /** 경로를 바꾸는 요청을 보낸다. 직전 명령과 [COMMAND_GAP_MS] 이상 간격을 둔다. */
    private suspend fun command(name: String, send: () -> Unit) {
        val wait = lastCommandAt + COMMAND_GAP_MS - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        try {
            // 취소는 PleosSdkException으로 바꾸지 않는다 (sdkSend, 리뷰 R-F13)
            sdkSend(SdkThreads.navi, name, send)
        } finally {
            lastCommandAt = SystemClock.elapsedRealtime()
        }
    }

    private suspend inline fun <reified E : NaviEvent> query(name: String, noinline send: () -> Unit): E =
        awaitEvent(name, QUERY_TIMEOUT_MS, { it is E }, send) as E

    /** 이벤트 구독을 먼저 걸고 요청을 보낸 뒤, [match]에 맞는 첫 이벤트를 기다린다. */
    private suspend fun awaitEvent(
        name: String,
        timeoutMs: Long,
        match: (NaviEvent) -> Boolean,
        send: () -> Unit,
    ): NaviEvent = coroutineScope {
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { _events.first(match) }
        try {
            sdkSend(SdkThreads.navi, name, send)
        } catch (e: Throwable) {
            // 보내지 못했거나 취소됐다: 기다리던 구독을 걷는다. 취소는 그대로 던진다 (리뷰 R-F13)
            waiter.cancel()
            throw e
        }
        val event = withTimeoutOrNull(timeoutMs) { waiter.await() }
        if (event == null) {
            waiter.cancel()
            throw PleosSdkException(name, "no response in ${timeoutMs}ms")
        }
        event
    }

    companion object {
        /** 경로 변경 명령 사이의 최소 간격. */
        const val COMMAND_GAP_MS = 3000L
        const val QUERY_TIMEOUT_MS = 15_000L
        /** 경유지 좌표가 요청과 같은 곳으로 볼 거리 (내비가 도로 위로 옮겨 붙일 수 있다) */
        const val WAYPOINT_MATCH_M = 300.0

        /** 경로가 있는 길안내 모드. 길안내 여부는 이것 하나로만 판단한다 (모의 주행도 경로가 있다) */
        fun hasRoute(mode: NaviMode): Boolean = mode == NaviMode.ROUTE_GUIDING || mode == NaviMode.SIMULATION_GUIDING

        /** Pleos 내비가 받는 경유지 수 (NaviHelper 가이드: FIRST, SECOND) */
        const val MAX_WAYPOINTS = 2

        /**
         * 경유지가 들어갔는지: 요청한 곳이 [after]에 있거나, 보내기 전 목록([before])을 읽었고 개수가 늘었다.
         * 못 읽었으면(null) 개수로는 알 수 없다: 원래 있던 다른 경유지만으로 성공이라 하지 않는다 (서비스 감사 P11).
         * 순수 함수라 JVM 테스트(NaviRulesTest)로 지킨다.
         */
        fun waypointAdded(before: List<WaypointInfo>?, after: List<WaypointInfo>, lat: Double, lon: Double, name: String? = null): Boolean =
            isWaypoint(after, lat, lon, name) || (before != null && after.size > before.size)

        /**
         * 요청한 곳이 경유지 목록에 있는지. 내비는 경유지 좌표를 0.0으로 돌려주므로(2026-11-12 에뮬레이터) 보낸 이름으로 본다.
         * 좌표가 있는 항목은 약 [WAYPOINT_MATCH_M] 안이면 같은 곳이다 (내비가 도로 위로 옮겨 붙일 수 있다).
         * 예전에는 좌표만 봐서 기기에서는 한 번도 맞지 않았다: 같은 곳을 다시 넣지 않는 장치가 죽어 있었다.
         */
        fun isWaypoint(list: List<WaypointInfo>, lat: Double, lon: Double, name: String? = null): Boolean = list.any { w ->
            (!name.isNullOrBlank() && w.name == name) ||
                ((w.latitude != 0.0 || w.longitude != 0.0) && distanceM(w.latitude, w.longitude, lat, lon) <= WAYPOINT_MATCH_M)
        }

        private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
            return 2 * r * Math.asin(Math.sqrt(a))
        }
    }
}

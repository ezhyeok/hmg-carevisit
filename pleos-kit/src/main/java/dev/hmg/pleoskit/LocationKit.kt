package dev.hmg.pleoskit

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * FusedLocation SDK 래퍼.
 * 권한: `pleos.car.permission.FUSED_LOCATION` (dangerous, 실행 중 요청 필요. [PleosPermissions] 참고).
 */
class LocationKit(context: Context) {
    private val appContext = context.applicationContext
    private val sdk = LazySdk("FusedLocation") { FusedLocation(appContext) }

    /** SDK 원본 객체. 처음 쓰는 곳에서 만들어진다 ([connect]를 먼저 부르면 SDK 전용 스레드). [reconnect] 뒤에는 새 객체다 */
    val raw: FusedLocation get() = sdk.value

    @Volatile
    private var connected = false

    /** 이 차량이 FusedLocation을 지원하는지. 지원하지 않으면 다른 호출을 하지 말 것. */
    suspend fun isSupported(): Boolean = withContext(SdkThreads.location) { raw.isSupported() }

    suspend fun connect() = withContext(SdkThreads.location) {
        if (!connected) {
            sdk.value.initialize()
            connected = true
        }
    }

    /** 연결이 죽었다고 보고 SDK 객체를 버린 뒤 새로 붙는다. 수집 중인 Flow는 다시 수집해야 한다 ([VehicleKit.reconnect]와 같다) */
    suspend fun reconnect() = withContext(SdkThreads.location) {
        connected = false
        sdk.drop()?.let { old -> runCatching { old.release() } }
        sdk.value.initialize()
        connected = true
    }

    /** 마지막으로 알려진 위치. 없으면 null. */
    suspend fun lastKnown(): LocationData? = sdkCall(SdkThreads.location, "getLastKnownLocation", 15_000) { ok, fail ->
        raw.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) = ok(location)
            override fun onError(exception: Exception) = fail(exception)
        })
    }

    /** 위치가 바뀔 때마다 방출한다. 수집을 취소하면 구독을 해제한다. */
    fun updates(): Flow<LocationData> = callbackFlow {
        val l = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                if (location != null) trySend(location)
            }

            override fun onError(exception: Exception) = KitLog.w("location update failed: ${exception.message}")
        }
        withContext(SdkThreads.location) { raw.registerFusedLocationCallback(l) }
        awaitClose {
            SdkThreads.location.executor.execute { runCatching { raw.unregisterFusedLocationCallback(l) } }
        }
    }

    /** 지정한 반경에 들어오거나 나갈 때 방출한다. 수집을 취소하면 알림을 지운다. */
    fun proximity(request: ProximityAlertRequest): Flow<ProximityAlertEvent> = callbackFlow {
        var alertId: String? = null
        val l = object : ProximityAlertListener {
            override fun onRegistered(id: String) {
                alertId = id
            }

            override fun onTransition(event: ProximityAlertEvent) {
                trySend(event)
            }

            override fun onError(exception: Exception) = KitLog.w("proximity alert failed: ${exception.message}")
        }
        withContext(SdkThreads.location) { raw.addProximityAlert(request, l) }
        awaitClose {
            SdkThreads.location.executor.execute { alertId?.let { id -> runCatching { raw.removeProximityAlert(id) } } }
        }
    }

    fun release() {
        SdkThreads.location.executor.execute {
            sdk.ifCreated { runCatching { it.release() } }
            connected = false
        }
    }
}

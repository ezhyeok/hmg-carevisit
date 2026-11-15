package dev.hmg.pleoskit

import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

data class VisitDestination(val name: String,val latitude: Double,val longitude: Double,val remainingM: Int,val address: String = "")
data class VisitArrival(val name: String,val address: String)
sealed interface VisitNavigationEvent {
    data class Arrived(val info: VisitArrival) : VisitNavigationEvent
    data object Invalidated : VisitNavigationEvent
}
/** Small domain-neutral bridges keep SDK data classes and area constants out of the application. */
class VisitNaviKit(context: Context) {
    private val kit=NaviKit(context)
    suspend fun connect()=kit.connect()
    suspend fun guiding()=kit.isGuiding()
    suspend fun destination(): VisitDestination = kit.destination().let { VisitDestination(it.name,it.latitude,it.longitude,it.remainDistance,it.address) }
    fun arrivals(): Flow<VisitArrival> = kit.events.filterIsInstance<NaviEvent.DestinationArrived>().map { VisitArrival(it.info.name,it.info.address) }
    fun navigationEvents(): Flow<VisitNavigationEvent> = kit.events.mapNotNull { event ->
        when(event) {
            is NaviEvent.DestinationArrived -> VisitNavigationEvent.Arrived(VisitArrival(event.info.name,event.info.address))
            NaviEvent.RouteCancelled,NaviEvent.DestinationChanged,is NaviEvent.Error -> VisitNavigationEvent.Invalidated
            else -> null
        }
    }
    suspend fun route(name: String,latitude: Double,longitude: Double): Boolean = kit.startRoute(RouteInfo(latitude=latitude,longitude=longitude,poiName=name,poiId="",address="",poiSubId=""))
    fun release()=kit.release()
}
suspend fun VehicleKit.passengerDoor(): ReadSignal<Int>? {
    val area=DoorArea(DoorValue.ROW_1_RIGHT)
    return if(doorAreas().containsKey(area)) doorPosition(area) else null
}

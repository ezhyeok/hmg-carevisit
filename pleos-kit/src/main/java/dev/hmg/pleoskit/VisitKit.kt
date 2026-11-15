package dev.hmg.pleoskit

import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

data class VisitDestination(val name: String,val latitude: Double,val longitude: Double,val remainingM: Int)
/** Small domain-neutral bridges keep SDK data classes and area constants out of the application. */
class VisitNaviKit(context: Context) {
    private val kit=NaviKit(context)
    suspend fun connect()=kit.connect()
    suspend fun guiding()=kit.isGuiding()
    suspend fun destination(): VisitDestination = kit.destination().let { VisitDestination(it.name,it.latitude,it.longitude,it.remainDistance) }
    fun arrivals(): Flow<Unit> = kit.events.filterIsInstance<NaviEvent.DestinationArrived>().map { Unit }
    suspend fun route(name: String,latitude: Double,longitude: Double): Boolean = kit.startRoute(RouteInfo(latitude=latitude,longitude=longitude,poiName=name,poiId="",address="",poiSubId=""))
    fun release()=kit.release()
}
suspend fun VehicleKit.passengerDoor(): ReadSignal<Int>? {
    val area=DoorArea(DoorValue.ROW_1_RIGHT)
    return if(doorAreas().containsKey(area)) doorPosition(area) else null
}

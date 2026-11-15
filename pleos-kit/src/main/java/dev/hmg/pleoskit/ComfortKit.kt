package dev.hmg.pleoskit

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** SDK-independent descriptors: the app never calls raw SDK objects. Keys retain the exact discovered area across restarts. */
data class ComfortControl(val key:String,val kind:String,val min:Double=0.0,val max:Double=100.0)
class ComfortKit(private val vehicle:VehicleKit) {
    private data class Binding(val descriptor:ComfortControl,val read:suspend ()->String?,val write:suspend (String)->Boolean)
    private val bindings=linkedMapOf<String,Binding>()
    private val mutex=Mutex()
    private fun CapabilityConfig.writable()=accessType==AccessType.READ_WRITE
    private fun SeatArea.driver()=SeatValue.ROW_1_LEFT in values
    private fun SeatArea.key()=values.map{it.name}.sorted().joinToString("+")
    private fun WindowArea.key()=values.map{it.name}.sorted().joinToString("+")
    private fun add(d:ComfortControl,read:suspend ()->String?,write:suspend (String)->Boolean) {bindings[d.key]=Binding(d,read,write)}
    private suspend fun attempt(block:suspend ()->Unit) {
        try{block()}catch(e:CancellationException){throw e}catch(e:Exception){KitLog.w("comfort capability unavailable: ${e.message}")}
    }
    suspend fun available():List<ComfortControl> = mutex.withLock {
        vehicle.connect();bindings.clear()
        attempt {
            val (config,choices)=vehicle.call2<CapabilityConfig,List<LightSwitch>>("checkCabinLightSwitchCapabilities") {ok,fail->vehicle.raw.getLight().checkCabinLightSwitchCapabilities(ok,fail)}
            if(config.writable() && LightSwitch.OFF in choices) {
                suspend fun read()=vehicle.call<LightSwitch?>("getCabinLightSwitch") {ok,fail->vehicle.raw.getLight().getCabinLightSwitch(ok,fail)}
                add(ComfortControl("cabin","CABIN"),{read()?.name}) {value->
                    val target=LightSwitch.valueOf(value)
                    if(target !in choices) false else {
                        val ack=vehicle.call<LightSwitch?>("setCabinLightSwitch") {ok,fail->vehicle.raw.getLight().setCabinLightSwitch(target,ok,fail)}
                        ack==target && confirm {read()==target}
                    }
                }
            }
        }
        attempt {
            val (config,areas)=vehicle.call2<CapabilityConfig,Map<SeatArea,AreaBounds<Int>>>("checkAmbientLightBrightnessCapabilities") {ok,fail->vehicle.raw.getLight().checkAmbientLightBrightnessCapabilities(ok,fail)}
            if(config.writable()) for((area,bounds) in areas.filterKeys{it.driver()}) {
                suspend fun read()=vehicle.call<Int?>("getAmbientLightBrightness") {ok,fail->vehicle.raw.getLight().getAmbientLightBrightness(area,ok,fail)}
                add(ComfortControl("ambient:${area.key()}","AMBIENT",bounds.minValue.toDouble(),bounds.maxValue.toDouble()),{read()?.toString()}) {value->
                    val target=value.toInt();require(target in bounds.minValue..bounds.maxValue)
                    val ack=vehicle.call2<SeatArea,Int>("setAmbientLightBrightness") {ok,fail->vehicle.raw.getLight().setAmbientLightBrightness(area,target,ok,fail)}
                    ack.first==area && ack.second==target && confirm {read()==target}
                }
            }
        }
        attempt {
            val (config,areas)=vehicle.call2<CapabilityConfig,List<SeatArea>>("checkHvacPowerCapabilities") {ok,fail->vehicle.raw.getHVAC().checkHvacPowerCapabilities(ok,fail)}
            if(config.writable()) for(area in areas.filter{it.driver()}.take(1)) {
                val signal=vehicle.hvacPower(area)
                add(ComfortControl("power:${area.key()}","POWER"),{signal.get()?.toString()}) {signal.setAndConfirm(it.toBooleanStrict())}
            }
        }
        attempt {
            val (config,areas)=vehicle.call2<CapabilityConfig,Map<SeatArea,AreaBounds<Float>>>("checkHvacTemperatureCapabilities") {ok,fail->vehicle.raw.getHVAC().checkHvacTemperatureCapabilities(ok,fail)}
            if(config.writable()) for((area,bounds) in areas.filterKeys{it.driver()}) {
                val signal=vehicle.hvacTemperatureC(area)
                add(ComfortControl("temperature:${area.key()}","TEMPERATURE",bounds.minValue.toDouble(),bounds.maxValue.toDouble()),{signal.get()?.toString()}) {value->
                    val target=value.toFloat();require(target.isFinite() && target in bounds.minValue..bounds.maxValue)
                    signal.setAndConfirm(target,matches={actual,requested->actual!=null && kotlin.math.abs(actual-requested)<0.2f})
                }
            }
        }
        attempt {
            val (config,areas)=vehicle.call2<CapabilityConfig,List<SeatArea>>("checkHvacRecirculationCapabilities") {ok,fail->vehicle.raw.getHVAC().checkHvacRecirculationCapabilities(ok,fail)}
            if(config.writable()) for(area in areas.filter{it.driver()}.take(1)) {
                val signal=vehicle.recirculation(area)
                add(ComfortControl("recirculation:${area.key()}","RECIRCULATION"),{signal.get()?.toString()}) {signal.setAndConfirm(it.toBooleanStrict())}
            }
        }
        attempt {
            val (config,areas)=vehicle.call2<CapabilityConfig,Map<WindowArea,AreaBounds<Int>>>("checkWindowPositionCapabilities") {ok,fail->vehicle.raw.getWindow().checkWindowPositionCapabilities(ok,fail)}
            if(config.writable()) for((area,bounds) in areas.filterKeys{it.values.size==1 && WindowValue.ROW_1_LEFT in it.values}) {
                val signal=vehicle.windowPosition(area)
                add(ComfortControl("window:${area.key()}","WINDOW",bounds.minValue.toDouble(),bounds.maxValue.toDouble()),{signal.get()?.toString()}) {value->
                    val target=value.toInt();require(target in bounds.minValue..bounds.maxValue);signal.setAndConfirm(target)
                }
            }
        }
        attempt {
            val (config,areas)=vehicle.call2<CapabilityConfig,Map<SeatArea,AreaBounds<Int>>>("checkSeatBackrestAnglePositionCapabilities") {ok,fail->vehicle.raw.getSeat().checkSeatBackrestAnglePositionCapabilities(ok,fail)}
            if(config.writable()) for(area in areas.keys.filter{it.driver() && it.values.size==1}) {
                add(ComfortControl("seat:${area.key()}","SEAT"),{vehicle.seatBackrestPosition(area).get()?.toString()}) {value->
                    when(value) {
                        "stop"->{vehicle.moveSeatBackrest(area,MoveControl.STOP);true}
                        "backward:700"->pulse(area,MoveControl.BACKWARD)
                        "forward:700"->pulse(area,MoveControl.FORWARD)
                        else->false
                    }
                }
            }
        }
        bindings.values.map{it.descriptor}
    }
    suspend fun read(key:String):String? = binding(key).read()
    suspend fun write(key:String,value:String):Boolean = binding(key).write(value)
    private suspend fun binding(key:String):Binding {
        mutex.withLock {bindings[key]}?.let{return it}
        available()
        return mutex.withLock {bindings[key]} ?: throw PleosSdkException(key,"capability unavailable; undo retained")
    }
    private suspend fun confirm(check:suspend ()->Boolean):Boolean {
        repeat(10){if(check())return true;delay(300)};return false
    }
    /** Timer starts BEFORE the request acknowledgement. Always attempt STOP, including timeout/cancellation. */
    private suspend fun pulse(area:SeatArea,direction:MoveControl):Boolean=coroutineScope {
        val stop=async {delay(700);withContext(NonCancellable){vehicle.moveSeatBackrest(area,MoveControl.STOP)}}
        try {vehicle.moveSeatBackrest(area,direction);stop.await();true}
        finally {withContext(NonCancellable){stop.cancelAndJoin();vehicle.moveSeatBackrest(area,MoveControl.STOP)}}
    }
}

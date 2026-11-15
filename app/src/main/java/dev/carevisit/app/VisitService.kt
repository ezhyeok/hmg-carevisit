package dev.carevisit.app

import android.car.VehicleGear
import android.os.SystemClock
import android.util.Log
import dev.carevisit.core.*
import dev.hmg.pleoskit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VisitService : PleosForegroundService() {
    override val notificationTitle: String get()=getString(AppIdentity.NAME)
    private val lane=Dispatchers.Default.limitedParallelism(1)
    private val car by lazy {CarState(this)}
    private val vehicle by lazy {VehicleKit(this)}
    private val navi by lazy {VisitNaviKit(this)}
    private val tts by lazy {TtsKit(this)}
    private val stt by lazy {SttKit(this)}
    private val llm by lazy {LlmKit(this)}
    private val family=HttpFamilyLink()
    private var core: VisitCore?=null
    private var coreJob: Job?=null
    private var viewJob: Job?=null
    private var demo=false
    private var demoOffset=0L
    private var gear=Gear.UNKNOWN
    private var speed: Double?=null
    private var door=false
    private var restricted=true
    private val signalJobs=mutableListOf<Job>()
    private fun signal() {
        if(demo && gear==Gear.P && door && core?.state?.value?.archive?.session?.phase==Phase.MEMO) core?.send(Event.Arrived)
        core?.send(Event.Vehicle(gear,speed,restricted,door))
    }
    override fun onServiceStarted() {
        serviceScope.launch(lane) {
            switch(false)
            launch { for(command in VisitStore.commands) when(command) {
                is AppCommand.Core -> if(command.belongsTo(demo)) core?.send(command.event)
                is AppCommand.Demo -> switch(command.enabled)
                AppCommand.Next -> demoNext()
            } }
            signalJobs+=launch { collectCar() }
            signalJobs+=launch { collectDoor() }
            signalJobs+=launch { collectNavi() }
            signalJobs+=launch {
                while(isActive) {
                    val active=core; val state=active?.state?.value
                    if(state?.archive?.consent?.familyAt!=null) {
                        try {family.poll(state.archive.settings).forEach { (id,text)-> if(core===active) active.send(Event.FamilyReply(id,text)) }}
                        catch(e: CancellationException) {throw e} catch(_: Exception) { /* send errors are visible; poll retries quietly */ }
                    }
                    delay(5_000)
                }
            }
        }
    }
    private suspend fun switch(enabled: Boolean) {
        if(core!=null && demo==enabled) return
        // Clear outgoing records before any suspend point in a mode switch.
        VisitStore.publish(ViewState(demo=enabled))
        viewJob?.cancelAndJoin(); coreJob?.cancelAndJoin(); tts.stop(); stt.stop()
        demo=enabled; demoOffset=0
        val repository=VisitRepositories.open(demo) { AtomicRepository(File(filesDir,"visits.json")) }
        lateinit var next: VisitCore
        val speech=VoiceAgent(tts,stt,demo,AudioFocusKit(this)) { next.send(Event.Voice(it)) }
        next=VisitCore(repository,speech,family,Polisher { llm.punctuation(it) },Navigator { h ->
            if(h.routable) {navi.connect(); check(navi.route(h.name,h.latitude!!,h.longitude!!))}
        },{System.currentTimeMillis()+if(demo) demoOffset else 0L},{SystemClock.elapsedRealtime()+if(demo) demoOffset else 0L})
        core=next
        coreJob=serviceScope.launch(lane) {next.run()}
        viewJob=serviceScope.launch(lane) {
            var announced=false
            next.state.collect {
                VisitStore.publish(it.copy(demo=enabled))
                if(enabled && !announced && it.archive.session.visit!=null) {announced=true;Log.i("CareVisit","demo started: visit")}
            }
        }
        signal()
        if(demo) next.send(Event.Manual(Hospital("○○대학교병원")))
    }
    private fun demoNext() {
        if(!demo) return
        when(core?.state?.value?.archive?.session?.phase) {
            Phase.IDLE,Phase.DONE -> core?.send(Event.Manual(Hospital("○○대학교병원")))
            Phase.WAIT_SAFE -> if(gear==Gear.P && !restricted) core?.send(Event.PrepareParked) else {demoOffset+=Policy.SAFE_DRIVE_MS; core?.send(Event.Tick)}
            Phase.MEMO -> {core?.send(Event.Arrived); signal()}
            Phase.AT_HOSPITAL -> {demoOffset+=Policy.STAY_MS; core?.send(Event.Notice("시연: 진료 15분 경과 · Virtual Console에서 기어 D로 출발하세요."))}
            else -> core?.send(Event.Notice("시연 질문과 대답이 진행 중이에요."))
        }
    }
    private suspend fun collectCar() {
        while(currentCoroutineContext().isActive) {
            try {
                car.connect()
                coroutineScope {
                    launch {car.gear().changes().collect { gear=when(it) {VehicleGear.GEAR_PARK->Gear.P;VehicleGear.GEAR_DRIVE->Gear.D;null->Gear.UNKNOWN;else->Gear.OTHER}; signal()}}
                    launch {car.speedMps().changes().collect {speed=it?.toDouble();signal()}}
                    launch {car.uxRestrictions.collect {restricted=it.restricted;signal()}}
                    launch {car.connected.collect {if(!it) {gear=Gear.UNKNOWN;speed=null;restricted=true;signal()}}}
                    launch {while(isActive) {delay(10_000); gear=when(car.gear().get()) {VehicleGear.GEAR_PARK->Gear.P;VehicleGear.GEAR_DRIVE->Gear.D;else->Gear.UNKNOWN};speed=car.speedMps().get()?.toDouble();signal()}}
                }
            } catch(e: CancellationException) {throw e} catch(_: Exception) {
                gear=Gear.UNKNOWN;speed=null;restricted=true;signal();car.release();delay(3_000)
            }
        }
    }
    private suspend fun collectDoor() {
        while(currentCoroutineContext().isActive) {
            try {
                vehicle.connect()
                val passenger=vehicle.passengerDoor()
                if(passenger==null) {core?.send(Event.Notice("동승석 도어 조회 미지원 · 도착 후 화면으로 확인하세요.")); delay(30_000);continue}
                coroutineScope {
                    launch {passenger.changes().collect {door=it?.let {p->p>0} ?: false;signal()}}
                    launch {while(isActive) {delay(30_000);door=passenger.get()?.let {it>0} ?: false;signal()}}
                }
            } catch(e: CancellationException) {throw e} catch(_: Exception) {
                door=false;signal();delay(3_000);runCatching {vehicle.reconnect()}
            }
        }
    }
    private suspend fun collectNavi() {
        while(currentCoroutineContext().isActive) {
            try {
                navi.connect()
                coroutineScope {
                    launch {navi.arrivals().collect {if(!demo) core?.send(Event.Arrived)}}
                    launch {
                        var wasGuiding=false; var nearHospital=false
                        while(isActive) {
                            val guiding=navi.guiding()
                            if(guiding) {
                                val d=navi.destination()
                                if(!demo) core?.send(Event.Destination(Hospital(d.name,d.latitude,d.longitude)))
                                nearHospital=d.remainingM in 0..200 && TripRules.isHospital(d.name,core?.state?.value?.archive?.settings?.hospitals.orEmpty())
                            } else if(wasGuiding && nearHospital && !demo) core?.send(Event.Arrived)
                            wasGuiding=guiding
                            delay(20_000)
                        }
                    }
                }
            } catch(e: CancellationException) {throw e} catch(_: Exception) {delay(10_000)}
        }
    }
    override fun onDestroy() {
        signalJobs.forEach {it.cancel()}; coreJob?.cancel(); viewJob?.cancel()
        tts.stop();stt.stop();tts.release();stt.release();navi.release();vehicle.release();car.release()
        super.onDestroy()
    }
}

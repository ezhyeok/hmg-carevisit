package dev.carevisit.core

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

sealed interface Event {
    data object Boot : Event
    data object Tick : Event
    data class Destination(val hospital: Hospital) : Event
    data class Manual(val hospital: Hospital = Hospital("병원")) : Event
    data class Vehicle(val gear: Gear, val speedMps: Double?, val restricted: Boolean, val passengerOpen: Boolean) : Event
    data class Answer(val id: Long,val text: String?,val touch: Boolean=false) : Event
    data object Arrived : Event
    data class NavigationArrived(val name: String,val address: String) : Event
    data object NavigationCleared : Event
    data object ConfirmDestination : Event
    data object DismissDestination : Event
    data object ParkingMode : Event
    data object ShareMemo : Event
    data object ShareBriefing : Event
    data class FamilyReply(val id: String,val text: String) : Event
    data class FamilyConsent(val enabled: Boolean, val token: String = "") : Event
    data class Configure(val settings: Settings) : Event
    data object PrepareParked : Event
    data object RequestConsent : Event
    data object Withdraw : Event
    data object DeleteAll : Event
    data object Resume : Event
    data object Abandon : Event
    data object RetryVoice : Event
    data class Checked(val utteranceId: String) : Event
    data class Polished(val visitId: String,val source: String,val text: String?) : Event
    data class Notice(val text: String) : Event
    data class Voice(val state: VoiceState) : Event
    data object CommandListen : Event
    data class CommandHeard(val text: String?) : Event
}
sealed interface Effect {
    data class Ask(val prompt: Prompt) : Effect
    data class Say(val text: String) : Effect
    data object StopVoice : Effect
    data object ListenCommand : Effect
    data class Send(val message: ApprovedMessage) : Effect
    data object RevokeFamily : Effect
    data class Polish(val visitId: String,val source: String) : Effect
    data class Route(val hospital: Hospital) : Effect
}
/** FamilyLink has no access to Archive, coordinates or utterance lists. Only this narrow DTO crosses it. */
data class ApprovedMessage internal constructor(val id: String,val kind: String,val text: String)

/** Deterministic state transitions. Only VisitCore invokes handle; no IO or coroutines here. */
class VisitMachine(initial: Archive = Archive(), private val createdAt: Long) {
    var archive = initial; private set
    var view = ViewState(initial); private set
    private var sequence = (initial.session.prompt?.id ?: 0L) + 1
    private var lastElapsed: Long? = null
    private var movingMs = 0L
    private var gear = Gear.UNKNOWN
    private var speed: Double? = null
    private var restricted = true
    private var open = false
    private var arrivalSignal = false
    private var arrivalElapsed: Long? = null
    private var navigationDestination: Hospital? = null
    private var recovered: Session? = null
    private val replyIds = mutableSetOf<String>()
    private val reminded = mutableSetOf<String>()
    private var routing: Hospital? = null
    private var notice = ""
    private var voice = VoiceState.IDLE
    private val effects = mutableListOf<Effect>()
    private val session get() = archive.session
    private val health get() = archive.consent.healthAt != null
    private val family get() = health && archive.consent.familyAt != null
    private val parked get() = gear == Gear.P && !restricted
    private fun session(s: Session) { archive = archive.copy(session=s) }
    private fun ask(kind: AskKind,text: String,slot: Slot?=null,retry: Int=0,confirming: Boolean=false,correction: Boolean=false) {
        val p=Prompt(sequence++,kind,text,slot,retry,confirming,correction); session(session.copy(prompt=p)); effects += Effect.Ask(p)
    }
    private fun cancelPrompt() { effects += Effect.StopVoice; session(session.copy(prompt=null)) }
    fun handle(e: Event,now: Long,elapsed: Long): List<Effect> {
        effects.clear()
        if(arrivalElapsed?.let { elapsed-it !in 0..Policy.ARRIVAL_CANDIDATE_MS }==true) clearArrival()
        val delta=lastElapsed?.let { (elapsed-it).coerceIn(0,60_000) } ?: 0L
        // Tick cadence is normally 1s; cap a service scheduling gap at one minute.
        if((e is Event.Tick || e is Event.Vehicle) && gear==Gear.D && speed?.let { it.isFinite() && it>0.5 }==true) movingMs += delta
        if(e is Event.Tick || e is Event.Vehicle) lastElapsed=elapsed
        when(e) {
            Event.Boot -> {
                archive=Policy.purge(archive,now)
                if(!health) archive=archive.copy(visits=emptyList(),session=Session())
                if(session.visit != null && session.phase !in setOf(Phase.DONE,Phase.IDLE)) { recovered=session.copy(phase=session.resumePhase ?: session.phase); session(session.copy(phase=Phase.RECOVERY,resumePhase=recovered!!.phase)); notice="중단된 진료 기록이 있어요. 주차 후 이어가거나 폐기하세요." }
            }
            is Event.Manual -> start(e.hospital,now)
            is Event.Destination -> destination(e.hospital,now)
            is Event.Vehicle -> {
                val previous=gear; gear=e.gear; speed=e.speedMps; restricted=e.restricted; open=e.passengerOpen
                arrive(now)
                if(health && session.phase==Phase.AT_HOSPITAL && previous==Gear.P && gear==Gear.D && session.arrivedAt?.let { now-it>=Policy.STAY_MS }==true) {
                    movingMs=0; clearArrival()
                    session(session.copy(phase=Phase.WAIT_SAFE,returnPending=true,offered=false))
                    notice="오는 길 · 누적 주행 60초 뒤 여쭤볼게요"
                }
            }
            Event.Tick -> {
                archive=Policy.purge(archive,now)
                if(session.phase==Phase.WAIT_SAFE && movingMs>=Policy.SAFE_DRIVE_MS && !session.offered && gear==Gear.D && speed?.let { it.isFinite() && it>0.5 }==true) offer()
                if(session.phase in setOf(Phase.IDLE,Phase.DONE) && session.prompt==null && health) remind(now)
            }
            Event.PrepareParked -> if(parked && session.phase==Phase.WAIT_SAFE) offer()
            Event.RequestConsent -> if(parked && !health) { session(session.copy(phase=Phase.HEALTH_CONSENT)); ask(AskKind.HEALTH,Policy.healthLine(archive.settings.retentionDays)) }
            is Event.Answer -> if(!e.touch || touchAllowed(e)) answer(e,now)
            Event.Arrived -> { arrivalSignal=true; arrive(now) }
            is Event.NavigationArrived -> {
                val target=navigationDestination
                val visit=session.visit
                if(target!=null && visit!=null && sameHospital(visit.hospital,target) &&
                    sameHospital(target,Hospital(e.name,address=e.address))) {
                    arrivalSignal=true;arrivalElapsed=elapsed;arrive(now)
                }
            }
            Event.NavigationCleared -> { navigationDestination=null;clearArrival();session(session.copy(pendingHospital=null)) }
            Event.ConfirmDestination -> if(parked) session.pendingHospital?.takeIf { it==navigationDestination }?.let { target ->
                if(health) session.visit?.let { old -> archive=archive.copy(visits=archive.visits.filterNot { it.id==old.id }+old) }
                cancelPrompt();session(Session());start(target,now)
            }
            Event.DismissDestination -> if(parked) session(session.copy(pendingHospital=null))
            Event.ParkingMode -> if(parked && health && session.phase==Phase.AT_HOSPITAL) {
                session(session.copy(parkingMode=true,rendezvous=Rendezvous.PARKING)); notice="정문에서 내려 드리고 주차 후 합류"
                if(family) effects += Effect.Send(ApprovedMessage(UUID.randomUUID().toString(),"status","주차 중"))
            }
            Event.ShareMemo -> if(parked && family && session.visit!=null && session.prompt==null) ask(AskKind.SHARE_MEMO,Templates.memo(session.visit!!)+". 이 진료 메모를 가족에게 보낼까요?")
            Event.ShareBriefing -> if(parked && family && session.visit!=null && session.briefing.isNotBlank() && session.prompt==null) { session(session.copy(phase=Phase.SHARE)); ask(AskKind.SHARE_BRIEFING,session.briefing+". 가족에게 보낼까요?") }
            is Event.FamilyReply -> if(family && replyIds.add(e.id)) {
                if(e.text=="나왔어" && session.parkingMode) { session(session.copy(rendezvous=Rendezvous.OUTSIDE)); notice="${archive.settings.honorific}가 정문에 나오셨대요"; effects += Effect.Say(notice) }
                else if(e.text=="잘 받았어") { notice="가족이 잘 받았대요"; effects += Effect.Say(notice) }
            }
            is Event.FamilyConsent -> if(parked) {
                if(e.enabled && health && e.token.matches(Regex("[0-9a-f]{32}"))) { archive=archive.copy(consent=archive.consent.copy(familyAt=now),settings=archive.settings.copy(familyToken=e.token)); effects+=Effect.Send(ApprovedMessage(UUID.randomUUID().toString(),"status","가족 연결됨")) }
                else if(!e.enabled) { effects += Effect.RevokeFamily; archive=archive.copy(consent=archive.consent.copy(familyAt=null),settings=archive.settings.copy(familyToken="")) }
            }
            is Event.Configure -> if(parked) {
                val endpointChanged=e.settings.relayUrl!=archive.settings.relayUrl
                if(endpointChanged && family) effects+=Effect.RevokeFamily
                archive=archive.copy(consent=if(endpointChanged) archive.consent.copy(familyAt=null) else archive.consent,
                    settings=e.settings.copy(retentionDays=e.settings.retentionDays.coerceIn(1,365),honorific=e.settings.honorific.takeIf { it in setOf("어머니","아버지","부모님","보호자") } ?: "어머니",familyToken=if(endpointChanged) "" else archive.settings.familyToken))
            }
            Event.DeleteAll,Event.Withdraw -> {
                effects += Effect.StopVoice; effects += Effect.RevokeFamily
                archive=Archive(); recovered=null;clearArrival();navigationDestination=null;movingMs=0; notice="이 기기의 진료 기록과 동의를 모두 삭제했어요."
            }
            Event.Resume -> if(parked && health) recovered?.let { s -> session(s.copy(prompt=null,resumePhase=null)); recovered=null; s.prompt?.let { ask(it.kind,it.text,it.slot,it.retry,it.confirming,it.correction) }; if(s.phase==Phase.BRIEFING) finishPost() }
            Event.Abandon -> if(parked) { cancelPrompt(); session(Session()); recovered=null; arrivalSignal=false; notice="중단된 기록을 폐기했어요." }
            Event.RetryVoice -> session.prompt?.let { effects+=Effect.StopVoice; ask(it.kind,it.text,it.slot,it.retry,it.confirming,it.correction) }
            is Event.Checked -> if(parked && health) session.visit?.let { v -> if(v.slots[Slot.QUESTION].orEmpty().any { it.utteranceId==e.utteranceId }) { val checked=v.checkedQuestions.toMutableSet(); if(!checked.add(e.utteranceId)) checked.remove(e.utteranceId); updateVisit(v.copy(checkedQuestions=checked)) } }
            is Event.Polished -> if(health && session.phase==Phase.BRIEFING && session.visit?.id==e.visitId && session.briefing==e.source) {
                session(session.copy(briefing=e.text?.takeIf { PolishGuard.accept(e.source,it) } ?: e.source)); readback()
            }
            is Event.Notice -> notice=e.text
            is Event.Voice -> voice=e.state
            Event.CommandListen -> if(session.prompt==null) effects+=Effect.ListenCommand
            is Event.CommandHeard -> {
                when { Korean.safety(e.text.orEmpty())==Safety.EMERGENCY -> effects+=Effect.Say(Policy.EMERGENCY_LINE)
                    Korean.intent(e.text)==Intent.VISIT -> start(Hospital("병원"),now)
                    Korean.intent(e.text)==Intent.STOP -> { cancelPrompt(); notice="대화를 멈췄어요." }
                }
            }
        }
        archive=Policy.purge(archive,now)
        view=ViewState(archive,restricted,gear,voice,notice)
        return effects.toList()
    }
    private fun start(h: Hospital,now: Long) {
        if(session.phase !in setOf(Phase.IDLE,Phase.DONE)) return
        movingMs=0;clearArrival()
        session(Session(phase=Phase.WAIT_SAFE,visit=Visit(UUID.randomUUID().toString(),h,now)))
        notice="병원 가는 길 · 주행 1분 후 여쭤볼게요"
    }
    private fun clearArrival() { arrivalSignal=false;arrivalElapsed=null }
    private fun sameHospital(expected: Hospital,actual: Hospital): Boolean {
        fun key(s: String)=s.filterNot { it.isWhitespace() }
        return key(expected.name).isNotEmpty() && key(expected.name)==key(actual.name) &&
            (expected.address.isBlank() || key(expected.address)==key(actual.address))
    }
    private fun destination(target: Hospital,now: Long) {
        val changed=navigationDestination!=target
        if(changed) {clearArrival();session(session.copy(pendingHospital=null))}
        navigationDestination=target
        if(!TripRules.isHospital(target.name,archive.settings.hospitals)) return
        val current=session.visit
        when {
            session.phase in setOf(Phase.IDLE,Phase.DONE) -> start(target,now)
            current?.hospital?.name=="병원" -> updateVisit(current.copy(hospital=target))
            current!=null && sameHospital(current.hospital,target) -> {
                if(current.hospital.address.isBlank() && target.address.isNotBlank()) updateVisit(current.copy(hospital=target))
            }
            changed && current!=null -> {
                session(session.copy(pendingHospital=target));notice="목적지가 바뀌었어요. 주차 후 방문 병원을 확인해 주세요."
            }
        }
    }
    private fun offer() {
        if(session.returnPending) {
            session(session.copy(offered=true,returnPending=false,phase=Phase.OFFER_POST))
            ask(AskKind.POST_OFFER,"진료 어떠셨어요? 의사 선생님 말씀 정리해 둘까요?")
            return
        }
        session(session.copy(offered=true,phase=if(health) Phase.OFFER_PRE else Phase.HEALTH_CONSENT))
        if(!health) ask(AskKind.HEALTH,Policy.healthLine(archive.settings.retentionDays)) else ask(AskKind.PRE_OFFER,"${archive.settings.honorific} 진료 가시는 길이죠? 의사 선생님께 드릴 말씀을 차에서 정리해 둘까요?")
    }
    private fun touchAllowed(e: Event.Answer): Boolean {
        if(parked) return true
        val p=session.prompt ?: return false
        if(gear==Gear.UNKNOWN || p.id!=e.id || p.kind==AskKind.HEALTH) return false
        if(e.text !in setOf("네","아니요","넘어가")) return false
        // A moving screen never shows the full outgoing text. A yes requires the completed readback.
        if(p.kind in setOf(AskKind.SHARE_MEMO,AskKind.SHARE_BRIEFING) && e.text=="네" && voice!=VoiceState.LISTENING) return false
        return true
    }
    private fun answer(e: Event.Answer,now: Long) {
        val p=session.prompt?.takeIf { it.id==e.id } ?: return
        val text=e.text?.trim()?.takeIf { it.isNotBlank() && it.length<=1500 }
        val intent=Korean.intent(text)
        if(Korean.safety(text.orEmpty())==Safety.EMERGENCY) { cancelPrompt(); recovered=session.copy(prompt=p); session(recovered!!.copy(phase=Phase.RECOVERY,resumePhase=recovered!!.phase)); effects+=Effect.Say(Policy.EMERGENCY_LINE); return }
        if(intent==Intent.STOP) { cancelPrompt(); notice="대화를 멈췄어요. 주차 후 이어갈 수 있어요."; recovered=session.copy(prompt=p); session(recovered!!.copy(phase=Phase.RECOVERY,resumePhase=recovered!!.phase)); return }
        if(p.confirming) { confirmRecorded(p,text,intent); return }
        val valid=when(p.kind) {
            AskKind.SLOT -> text!=null && (intent != Intent.MORE)
            AskKind.MORE_QUESTIONS -> intent in setOf(Intent.MORE,Intent.YES,Intent.NO,Intent.SKIP)
            else -> intent in setOf(Intent.YES,Intent.NO,Intent.SKIP)
        }
        if(!valid) {
            if(p.retry==0) ask(p.kind,"잘 듣지 못했어요. ${p.text}",p.slot,1,p.confirming,p.correction)
            else { cancelPrompt(); if(p.kind==AskKind.SLOT) nextSlot() else decline(p.kind) }
            return
        }
        cancelPrompt()
        if(p.kind==AskKind.HEALTH) {
            if(intent==Intent.YES) { archive=archive.copy(consent=archive.consent.copy(healthAt=now)); if(session.visit!=null) offer() else session(Session()) }
            else decline(p.kind)
            return
        }
        if(!health) return
        when(p.kind) {
            AskKind.PRE_OFFER -> if(intent==Intent.YES) {
                session(session.copy(phase=Phase.PRE,slotIndex=0))
                val previous=archive.visits.lastOrNull { it.hospital.name==session.visit?.hospital?.name }
                val old=previous?.text(Slot.NEXT_CHECK)?.ifBlank { previous.text(Slot.MED_CHANGE).takeUnless { Korean.intent(it)==Intent.NO }.orEmpty() }.orEmpty()
                if(old.isNotBlank()) ask(AskKind.SLOT,"지난번에 ‘$old’라고 기록하셨는데, 그 뒤로 어떠세요?",Slot.CONTINUITY) else askSlot()
            } else decline(p.kind)
            AskKind.POST_OFFER -> if(intent==Intent.YES) { session(session.copy(phase=Phase.POST,slotIndex=0)); askSlot() } else decline(p.kind)
            AskKind.SLOT -> {
                val recorded=intent !in setOf(Intent.SKIP,Intent.NO)
                if(recorded) record(p.slot!!,text!!,now,p.correction)
                if(Korean.safety(text.orEmpty())==Safety.DISCOMFORT) effects+=Effect.Say(Policy.DISCOMFORT_LINE)
                if(recorded && p.slot in checkedSlots) readbackSlot(p.slot!!,p.correction)
                else if(p.slot==Slot.CONTINUITY) askSlot()
                else if(p.slot==Slot.QUESTION && intent !in setOf(Intent.SKIP,Intent.NO) && session.visit!!.slots[Slot.QUESTION].orEmpty().size<5) ask(AskKind.MORE_QUESTIONS,"또 물어볼 것이 있나요? 있으면 또 있어, 없으면 없다고 말씀해 주세요.")
                else nextSlot()
            }
            AskKind.MORE_QUESTIONS -> if(intent in setOf(Intent.MORE,Intent.YES)) ask(AskKind.SLOT,"또 물어볼 것을 말씀해 주세요.",Slot.QUESTION) else nextSlot()
            AskKind.MED_CHANGED -> if(intent==Intent.YES) ask(AskKind.SLOT,Templates.questions.getValue(Slot.MED_CHANGE),Slot.MED_CHANGE) else { if(text!=null && intent==Intent.NO) { record(Slot.MED_CHANGE,text,now); readbackSlot(Slot.MED_CHANGE,false) } else nextSlot() }
            AskKind.SHARE_MEMO -> if(intent==Intent.YES && family) effects+=Effect.Send(ApprovedMessage(UUID.randomUUID().toString(),"memo",Templates.memo(session.visit!!)))
            AskKind.SHARE_BRIEFING -> { if(intent==Intent.YES && family) effects+=Effect.Send(ApprovedMessage(UUID.randomUUID().toString(),"briefing",session.briefing)); offerReminder() }
            AskKind.REMINDER -> { if(intent==Intent.YES) updateVisit(session.visit!!.copy(reminder=true)); session(session.copy(phase=Phase.DONE)) }
            AskKind.ROUTE -> { if(intent==Intent.YES) routing?.takeIf { it.routable }?.let { effects+=Effect.Route(it) }; routing=null }
            else -> Unit
        }
    }
    private fun decline(kind: AskKind) {
        when(kind) {
            AskKind.HEALTH,AskKind.PRE_OFFER -> session(session.copy(phase=Phase.MEMO))
            AskKind.POST_OFFER -> { session.visit?.let { updateVisit(it.copy(complete=true)) }; session(session.copy(phase=Phase.DONE)) }
            AskKind.MORE_QUESTIONS,AskKind.MED_CHANGED -> nextSlot()
            AskKind.SHARE_BRIEFING -> offerReminder()
            AskKind.REMINDER -> session(session.copy(phase=Phase.DONE))
            else -> Unit
        }
    }
    private fun askSlot() {
        val list=if(session.phase==Phase.PRE) Templates.pre else Templates.post
        val s=list[session.slotIndex]
        if(s==Slot.MED_CHANGE) ask(AskKind.MED_CHANGED,"약이 바뀌었나요?",s) else ask(AskKind.SLOT,Templates.questions.getValue(s),s)
    }
    private fun nextSlot() {
        val n=session.slotIndex+1
        if(n<5) { session(session.copy(slotIndex=n)); askSlot() }
        else if(session.phase==Phase.PRE) { session(session.copy(phase=Phase.MEMO)); notice="진료 메모가 준비됐어요. 주차 후 확인하세요."; effects+=Effect.Say(notice) }
        else finishPost()
    }
    private val checkedSlots=setOf(Slot.MEDICATION,Slot.MED_CHANGE,Slot.APPOINTMENT)
    private fun readbackSlot(slot: Slot,correction: Boolean) {
        val text=session.visit?.text(slot).orEmpty()
        ask(AskKind.SLOT,"‘$text’라고 기록했어요. 맞으면 맞아요, 고치려면 다시 말할게요, 잘 모르시면 확인 필요라고 말씀해 주세요.",slot,confirming=true,correction=correction)
    }
    private fun confirmRecorded(p: Prompt,text: String?,intent: Intent) {
        val compact=Korean.compact(text.orEmpty())
        val correct=compact in setOf("다시말할게요","다시말할게","다시말할","다시말","다시","고칠게요") || intent==Intent.NO
        val yes=intent==Intent.YES || compact in setOf("맞아요","맞아","맞습니다","맞아여")
        val skip=intent==Intent.SKIP || compact in setOf("확인필요","확인이필요해","확인필요해요")
        if(!yes && !correct && !skip && p.retry==0) {
            ask(p.kind,"잘 듣지 못했어요. ${p.text}",p.slot,1,true,p.correction);return
        }
        cancelPrompt()
        if(correct && !p.correction) {
            ask(AskKind.SLOT,Templates.questions.getValue(p.slot!!),p.slot,correction=true);return
        }
        if(yes) session.visit?.let { v ->
            val slot=p.slot!!
            val appointment=if(slot==Slot.APPOINTMENT) v.appointment?.let { a ->
                val at=v.utterances.first { it.id==a.utteranceId }.at
                Dates.parse(a.verbatim,Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Seoul")).toLocalDate()).copy(utteranceId=a.utteranceId)
            } else v.appointment
            updateVisit(v.copy(slots=v.slots+(slot to v.slots[slot].orEmpty().map { it.copy(needsConfirmation=false) }),appointment=appointment))
        }
        nextSlot()
    }
    private fun record(s: Slot,text: String,now: Long,replace: Boolean=false) {
        val v=session.visit ?: return
        val u=Utterance(UUID.randomUUID().toString(),text,now)
        val value=SlotValue(u.id,text,if(s==Slot.DURATION) Korean.duration(text) else null,s in checkedSlots)
        val appointment=if(s==Slot.APPOINTMENT) Dates.parse(text,Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Seoul")).toLocalDate()).copy(utteranceId=u.id,needsConfirmation=true) else v.appointment
        updateVisit(v.copy(utterances=v.utterances+u,slots=v.slots+(s to (if(replace) listOf(value) else v.slots[s].orEmpty()+value)),appointment=appointment))
    }
    private fun updateVisit(v: Visit) {
        check(v.traceable())
        session(session.copy(visit=v))
        if(v.complete) archive=archive.copy(visits=archive.visits.filterNot { it.id==v.id }+v)
    }
    private fun finishPost() {
        val v=session.visit ?: return
        updateVisit(v.copy(complete=true)); session(session.copy(phase=Phase.BRIEFING,briefing=Templates.briefing(v)))
        if(archive.settings.polish) effects+=Effect.Polish(v.id,session.briefing) else readback()
    }
    private fun readback() {
        if(family) { session(session.copy(phase=Phase.SHARE)); ask(AskKind.SHARE_BRIEFING,session.briefing+". 가족에게 보낼까요?") }
        else { effects+=Effect.Say(session.briefing); offerReminder() }
    }
    private fun offerReminder() {
        val a=session.visit?.appointment
        if(a?.date!=null && !a.needsConfirmation && session.visit?.reminder!=true) { session(session.copy(phase=Phase.REMINDER)); ask(AskKind.REMINDER,"다음 진료일에 앱이 켜져 있을 때 알려 드릴까요?") }
        else session(session.copy(phase=Phase.DONE))
    }
    private fun arrive(now: Long) {
        if(!health || gear!=Gear.P || session.visit==null) return
        if(arrivalSignal && !session.returnPending && session.phase in setOf(Phase.MEMO,Phase.PRE,Phase.OFFER_PRE,Phase.WAIT_SAFE)) {
            cancelPrompt(); session(session.copy(phase=Phase.AT_HOSPITAL,arrivedAt=now,rendezvous=Rendezvous.AT_GATE)); notice="정문 도착"
            if(family) effects+=Effect.Send(ApprovedMessage(UUID.randomUUID().toString(),"status","정문 도착"))
        }
        if(session.phase==Phase.AT_HOSPITAL && open && !session.droppedOff) {
            session(session.copy(droppedOff=true))
            effects+=Effect.Say(if(family) "천천히 내리세요. 진료 메모는 가족 화면에서도 볼 수 있어요" else "천천히 내리세요.")
        }
    }
    private fun remind(now: Long) {
        val today=Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString()
        val v=archive.visits.lastOrNull { it.reminder && it.appointment?.date==today && it.id !in reminded } ?: return
        reminded+=v.id; val text="오늘 ${v.appointment?.time.orEmpty()} ${v.hospital.name} 진료가 있어요."
        if(v.hospital.routable) { routing=v.hospital; ask(AskKind.ROUTE,"$text 길안내 할까요?") } else effects+=Effect.Say(text)
    }
}

package dev.carevisit.core

import java.time.Instant
import kotlin.test.*
class FlowTest {
    private val now=Instant.parse("2026-11-15T01:00:00Z").toEpochMilli()
    private fun parked(m:VisitMachine,t:Long=0) {m.handle(Event.Vehicle(Gear.P,0.0,false,false),now+t,t)}
    private fun answer(m:VisitMachine,text:String?,t:Long=0): List<Effect> {
        val effects=m.handle(Event.Answer(m.archive.session.prompt!!.id,text),now+t,t)
        return if(m.archive.session.prompt?.confirming==true) effects+m.handle(Event.Answer(m.archive.session.prompt!!.id,"맞아요"),now+t,t) else effects
    }
    private fun ready(family:Boolean=false):VisitMachine {
        val m=VisitMachine(Archive(consent=Consent(now,if(family)now else null)),now)
        parked(m);m.handle(Event.Manual(Hospital("○○병원",37.4,127.1)),now,0);m.handle(Event.PrepareParked,now,0);answer(m,"네");repeat(5){answer(m,"넘어가")}
        m.handle(Event.Arrived,now,0);m.handle(Event.Vehicle(Gear.D,10.0,true,false),now+840_000,840_000)
        parked(m,900_000);m.handle(Event.Vehicle(Gear.D,10.0,true,false),now+900_000,900_000)
        m.handle(Event.Tick,now+960_000,960_000);answer(m,"네",960_000)
        return m
    }
    private fun finish(m:VisitMachine):List<Effect> {
        answer(m,"검사 결과는 다음에 보자고 하셨어",900_000)
        answer(m,"네",900_000);answer(m,"처방전 약으로 바뀌었대",900_000)
        answer(m,"2주 뒤 오전 10시",900_000)
        answer(m,"무리하지 말라고 하셨어",900_000)
        return answer(m,"검사 결과 물어보기",900_000)
    }
    @Test fun completePostAndTemplateWithoutLlm() {val m=ready();val e=finish(m);assertEquals(1,m.archive.visits.size);assertTrue(m.archive.visits.single().traceable());assertTrue(m.archive.session.briefing.contains("11월 29일(일) 오전 10시"));assertTrue(e.any{it is Effect.Say});assertEquals(AskKind.REMINDER,m.archive.session.prompt?.kind)}
    @Test fun noSendBeforeConfirmationAndNoMeansNo() {val m=ready(true);val e=finish(m);assertFalse(e.any{it is Effect.Send});assertEquals(AskKind.SHARE_BRIEFING,m.archive.session.prompt?.kind);assertFalse(answer(m,"아니",900_000).any{it is Effect.Send})}
    @Test fun yesSendsExactlyReadBackText() {val m=ready(true);finish(m);val text=m.archive.session.briefing;val e=answer(m,"응",900_000);assertEquals(text,(e.single{it is Effect.Send} as Effect.Send).message.text)}
    @Test fun noMedicationChangeIsTraceable() {val m=ready();answer(m,"그대로 지내래",900_000);answer(m,"아니요",900_000);assertEquals("아니요",m.archive.session.visit!!.text(Slot.MED_CHANGE));assertEquals(Slot.APPOINTMENT,m.archive.session.prompt?.slot)}
    @Test fun appointmentReminderOnlyOnItsDateAndOnce() {val m=ready();finish(m);answer(m,"네",900_000);val next=VisitMachine(m.archive,now);next.handle(Event.Boot,now,0);val early=next.handle(Event.Tick,now,0);assertFalse(early.any{it is Effect.Ask});val day=now+14*86_400_000L;val e=next.handle(Event.Tick,day,1);assertTrue(e.any{it is Effect.Ask && it.prompt.kind==AskKind.ROUTE});answer(next,"네",14*86_400_000L);assertFalse(next.handle(Event.Tick,day+1000,2).any{it is Effect.Ask})}
    @Test fun noCoordinatesNeverRequestsRoute() {val m=ready();finish(m);answer(m,"네",900_000);val a=m.archive.copy(visits=m.archive.visits.map{it.copy(hospital=Hospital("○○병원"))});val n=VisitMachine(a,now);val e=n.handle(Event.Tick,now+14*86_400_000L,0);assertTrue(e.any{it is Effect.Say});assertFalse(e.any{it is Effect.Route || it is Effect.Ask})}
    @Test fun repeatedRestartPreservesPendingQuestion() {val m=ready();answer(m,"다음에 보자고 하셨어",900_000);val one=VisitMachine(m.archive,now);one.handle(Event.Boot,now+900_001,0);val two=VisitMachine(one.archive,now);two.handle(Event.Boot,now+900_002,0);parked(two,900_003);two.handle(Event.Resume,now+900_003,0);assertEquals(AskKind.MED_CHANGED,two.archive.session.prompt?.kind)}
    @Test fun denseVehicleUpdatesDoNotResetDrivingClock() {
        val m=VisitMachine(Archive(consent=Consent(now)),now);m.handle(Event.Manual(),now,0)
        for(t in 0L..60_000L step 100) {m.handle(Event.Vehicle(Gear.D,10.0,true,false),now+t,t);if(t%1000==0L)m.handle(Event.Tick,now+t,t)}
        assertEquals(AskKind.PRE_OFFER,m.archive.session.prompt?.kind)
    }
    @Test fun declineIsOncePerTrip() {val m=VisitMachine(Archive(consent=Consent(now)),now);parked(m);m.handle(Event.Manual(),now,0);m.handle(Event.PrepareParked,now,0);answer(m,"아니");repeat(10){m.handle(Event.Tick,now+60_000L*it,60_000L*it)};assertNull(m.archive.session.prompt)}
    @Test fun familySetupDoesNotGrantHealthConsent() {val m=VisitMachine(Archive(),now);parked(m);m.handle(Event.FamilyConsent(true,"ab".repeat(16)),now,0);assertNull(m.archive.consent.healthAt);assertNull(m.archive.consent.familyAt)}
    @Test fun familyRevokeInvalidatesPendingShareAnswer() {val m=ready(true);finish(m);val id=m.archive.session.prompt!!.id;parked(m,900_000);m.handle(Event.FamilyConsent(false),now+900_000,0);assertFalse(m.handle(Event.Answer(id,"네"),now+900_000,0).any{it is Effect.Send})}
    @Test fun corruptAppointmentProvenanceRejected() {val v=Visit("v",Hospital("병원"),now,appointment=Appointment("2026-12-01",verbatim="예약",utteranceId="missing"));assertFalse(v.traceable())}
    @Test fun appointmentMissingTimeIsExplicit() {assertTrue(Dates.display(Dates.parse("2주 뒤",java.time.LocalDate.of(2026,11,15))).contains("시간 확인 필요"))}
    @Test fun explicitBriefingReshareStillNeedsASeparateYes() {
        val m=ready(true);finish(m);answer(m,"아니",900_000);answer(m,"아니",900_000);parked(m,900_000)
        val ask=m.handle(Event.ShareBriefing,now+900_000,900_000)
        assertTrue(ask.any{it is Effect.Ask && it.prompt.kind==AskKind.SHARE_BRIEFING});assertFalse(ask.any{it is Effect.Send})
        assertTrue(answer(m,"응",900_000).any{it is Effect.Send})
    }
    @Test fun familyOutsideHasStructuredRendezvousState() {
        val m=ready(true);parked(m,900_000)
        // A family reply only changes rendezvous after explicit parking mode at the hospital.
        val a=m.archive.copy(session=m.archive.session.copy(phase=Phase.AT_HOSPITAL,prompt=null))
        val n=VisitMachine(a,now);parked(n);n.handle(Event.ParkingMode,now,0);n.handle(Event.FamilyReply("1","나왔어"),now,0)
        assertEquals(Rendezvous.OUTSIDE,n.archive.session.rendezvous)
    }

    @Test fun decliningReturnInterviewStillPreservesThePreparedMemo() {
        val m=VisitMachine(Archive(consent=Consent(now)),now);parked(m);m.handle(Event.Manual(Hospital("○○병원")),now,0);m.handle(Event.PrepareParked,now,0);answer(m,"네");answer(m,"무릎이 아프");repeat(4){answer(m,"넘어가")}
        m.handle(Event.Arrived,now,0);m.handle(Event.Vehicle(Gear.D,10.0,true,false),now+900_000,900_000);m.handle(Event.Tick,now+960_000,960_000);answer(m,"아니요",960_000)
        m.handle(Event.Manual(Hospital("다음 병원")),now+1_000_000,1_000_000)
        assertEquals("무릎이 아프",m.archive.visits.single().text(Slot.SYMPTOM))
        assertTrue(m.archive.visits.single().traceable())
    }

}

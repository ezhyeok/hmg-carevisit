package dev.carevisit.core

import java.time.LocalDate
import kotlin.test.*
class BoundaryTest {
    @Test fun nextWeekWeekdayUsesCalendarWeek() {assertEquals("2026-11-18",Dates.parse("다음 주 수요일",LocalDate.of(2026,11,15)).date)}
    @Test fun conflictingSpokenWeekdayIsNotSilentlyCorrected() {assertTrue(Dates.parse("12월 10일 수요일",LocalDate.of(2026,11,15)).needsConfirmation)}
    @Test fun passedLeapDayDoesNotClampToFeb28() {assertNull(Dates.parse("2월 29일",LocalDate.of(2024,3,1)).date)}
    @Test fun endpointChangeRevokesOldLink() {
        val m=VisitMachine(Archive(consent=Consent(1,1),settings=Settings(familyToken="ab".repeat(16))),1)
        m.handle(Event.Vehicle(Gear.P,0.0,false,false),1,0)
        val e=m.handle(Event.Configure(m.archive.settings.copy(relayUrl="http://10.0.2.2:8766")),1,0)
        assertNull(m.archive.consent.familyAt);assertTrue(m.archive.settings.familyToken.isEmpty());assertTrue(e.contains(Effect.RevokeFamily))
    }
    @Test fun silentConsentIsNeverImplicitYes() {
        val m=VisitMachine(Archive(),1);m.handle(Event.Vehicle(Gear.P,0.0,false,false),1,0);m.handle(Event.RequestConsent,1,0)
        repeat(2){m.handle(Event.Answer(m.archive.session.prompt!!.id,null),1,0)}
        assertNull(m.archive.consent.healthAt);assertTrue(m.archive.visits.isEmpty())
    }
    @Test fun safetyStringsHaveNoMedicalAssessment() {
        assertEquals("불편이 심하면 진료 전에 병원에 바로 말씀하세요",Policy.DISCOMFORT_LINE)
        val source=java.io.File("src/main/kotlin/dev/carevisit/core").walkTopDown().filter{it.extension=="kt"}.joinToString("\n"){it.readText()}
        listOf("심각합니다","응급 증상입니다","위험하지 않습니다","복용하세요","진단됩니다").forEach {assertFalse(source.contains(it))}
    }
    @Test fun oversizedAnswerIsNotSilentlyTruncated() {
        val m=VisitMachine(Archive(consent=Consent(1)),1);m.handle(Event.Vehicle(Gear.P,0.0,false,false),1,0);m.handle(Event.Manual(),1,0);m.handle(Event.PrepareParked,1,0)
        m.handle(Event.Answer(m.archive.session.prompt!!.id,"네"),1,0)
        m.handle(Event.Answer(m.archive.session.prompt!!.id,"아프".repeat(1000)+"지 않아"),1,0)
        assertTrue(m.archive.session.visit!!.utterances.isEmpty());assertEquals(Slot.SYMPTOM,m.archive.session.prompt?.slot)
    }
    @Test fun retryCancelsPreviousListenBeforeOpeningAnother() {
        val m=VisitMachine(Archive(),1);m.handle(Event.Vehicle(Gear.P,0.0,false,false),1,0);m.handle(Event.RequestConsent,1,0)
        val e=m.handle(Event.RetryVoice,1,0);assertEquals(Effect.StopVoice,e.first());assertTrue(e.last() is Effect.Ask)
    }

    @Test fun fixedDrivingTouchAnswersAreAllowedButFreeTextIsNot() {
        val m=VisitMachine(Archive(consent=Consent(1)),1);m.handle(Event.Manual(),1,0);m.handle(Event.Vehicle(Gear.D,10.0,true,false),1,0);m.handle(Event.Tick,60001,60000)
        val id=m.archive.session.prompt!!.id
        m.handle(Event.Answer(id,"무릎이 아파",true),60001,60000);assertEquals(AskKind.PRE_OFFER,m.archive.session.prompt?.kind)
        m.handle(Event.Answer(id,"네",true),60001,60000);assertEquals(Slot.SYMPTOM,m.archive.session.prompt?.slot)
        m.handle(Event.Answer(m.archive.session.prompt!!.id,"넘어가",true),60001,60000);assertEquals(Slot.DURATION,m.archive.session.prompt?.slot)
    }
    @Test fun consentSpeaksTheSelectedRetentionPeriod() {
        val m=VisitMachine(Archive(settings=Settings(retentionDays=30)),1);m.handle(Event.Vehicle(Gear.P,0.0,false,false),1,0);m.handle(Event.RequestConsent,1,0)
        assertTrue(m.archive.session.prompt!!.text.contains("30일"));assertFalse(m.archive.session.prompt!!.text.contains("180일"))
    }

}

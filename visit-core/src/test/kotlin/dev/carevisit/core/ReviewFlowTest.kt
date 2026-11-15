package dev.carevisit.core

import kotlin.test.*

class ReviewFlowTest {
    private val now=1_800_000_000_000L
    private fun atHospital() = VisitMachine(Archive(consent=Consent(now),session=Session(
        phase=Phase.AT_HOSPITAL,visit=Visit("v",Hospital("○○병원"),now),arrivedAt=now)),now).also {
        it.handle(Event.Vehicle(Gear.P,0.0,false,false),now,0)
    }
    @Test fun leavingAfterFifteenMinutesWaitsForSixtySecondsMoving() {
        val m=atHospital()
        val effects=m.handle(Event.Vehicle(Gear.D,10.0,true,false),now+900_000,900_000)
        assertFalse(effects.any { it is Effect.Ask }); assertNull(m.archive.session.prompt)
        m.handle(Event.Tick,now+959_999,959_999);assertNull(m.archive.session.prompt)
        m.handle(Event.Tick,now+960_000,960_000);assertEquals(AskKind.POST_OFFER,m.archive.session.prompt?.kind)
        assertFalse(m.handle(Event.Tick,now+961_000,961_000).any { it is Effect.Ask })
    }
    @Test fun stoppedUnknownAndReverseDoNotStartReturnQuestion() {
        for(gear in listOf(Gear.UNKNOWN,Gear.OTHER,Gear.D)) {
            val m=atHospital();m.handle(Event.Vehicle(Gear.D,10.0,true,false),now+900_000,900_000)
            m.handle(Event.Tick,now+959_999,959_999)
            m.handle(Event.Vehicle(gear,0.0,true,false),now+960_000,960_000)
            assertFalse(m.handle(Event.Tick,now+1_020_000,1_020_000).any { it is Effect.Ask })
            assertNull(m.archive.session.prompt)
            m.handle(Event.Vehicle(Gear.D,10.0,true,false),now+1_020_000,1_020_000)
            assertEquals(AskKind.POST_OFFER,(m.handle(Event.Tick,now+1_020_001,1_020_001).filterIsInstance<Effect.Ask>().single()).prompt.kind)
        }
    }
    @Test fun parkedExplicitReturnPreparationCanStartWithoutDriving() {
        val m=atHospital();m.handle(Event.Vehicle(Gear.D,0.0,true,false),now+900_000,900_000)
        m.handle(Event.Vehicle(Gear.P,0.0,false,false),now+901_000,901_000)
        assertEquals(AskKind.POST_OFFER,m.handle(Event.PrepareParked,now+901_000,901_000).filterIsInstance<Effect.Ask>().single().prompt.kind)
    }
    private fun slot(slot: Slot): VisitMachine {
        val pre=slot==Slot.MEDICATION
        return VisitMachine(Archive(consent=Consent(now),session=Session(phase=if(pre) Phase.PRE else Phase.POST,
            visit=Visit("v",Hospital("○○병원"),now),slotIndex=if(pre) 3 else if(slot==Slot.MED_CHANGE) 1 else 2,
            prompt=Prompt(10,AskKind.SLOT,Templates.questions.getValue(slot),slot))),now).also {
            it.handle(Event.Vehicle(Gear.P,0.0,false,false),now,0)
        }
    }
    private fun answer(m: VisitMachine,text: String?) = m.handle(Event.Answer(m.archive.session.prompt!!.id,text),now,0)
    @Test fun medicationReadbackAndOneCorrectionKeepBothUtterances() {
        val m=slot(Slot.MEDICATION);answer(m,"약 1정")
        val old=m.archive.session.visit!!.slots.getValue(Slot.MEDICATION).single().utteranceId
        assertEquals(Slot.MEDICATION,m.archive.session.prompt?.slot)
        assertTrue(m.archive.session.prompt!!.text.contains("약 1정"))
        answer(m,"다시 말할게요");answer(m,"약 2정");answer(m,"맞아요")
        val v=m.archive.session.visit!!
        assertEquals(listOf("약 1정","약 2정"),v.utterances.map { it.text })
        assertEquals("약 2정",v.text(Slot.MEDICATION));assertNotEquals(old,v.slots.getValue(Slot.MEDICATION).single().utteranceId)
        assertTrue(v.traceable());assertEquals(Slot.QUESTION,m.archive.session.prompt?.slot)
    }
    @Test fun appointmentReadbackCannotConfirmConflictingTime() {
        val m=slot(Slot.APPOINTMENT);answer(m,"12월 10일 오전 3시 아니 4시")
        assertEquals(Slot.APPOINTMENT,m.archive.session.prompt?.slot)
        answer(m,"맞아요")
        assertTrue(m.archive.session.visit!!.appointment!!.needsConfirmation)
        assertEquals(Slot.CAUTION,m.archive.session.prompt?.slot)
    }
    @Test fun medicationChangeAlsoRequiresReadback() {
        val m=slot(Slot.MED_CHANGE);answer(m,"처방전에 적힌 약으로 바꿨어")
        assertEquals(Slot.MED_CHANGE,m.archive.session.prompt?.slot)
        assertTrue(m.archive.session.prompt!!.text.contains("맞아요"))
        answer(m,"네");assertEquals(Slot.APPOINTMENT,m.archive.session.prompt?.slot)
    }
    @Test fun failedCorrectionPreservesOldIdAndMarksItUnconfirmed() {
        val m=slot(Slot.MEDICATION);answer(m,"약 1정")
        val old=m.archive.session.visit!!.slots.getValue(Slot.MEDICATION).single()
        answer(m,"다시 말할게요");answer(m,null);answer(m,null)
        assertEquals(old,m.archive.session.visit!!.slots.getValue(Slot.MEDICATION).single())
        assertTrue(Templates.memo(m.archive.session.visit!!).contains("약 1정 (확인 필요)"))
        assertEquals(Slot.QUESTION,m.archive.session.prompt?.slot)
    }
    @Test fun aSecondCorrectionAfterRestartDoesNotResetTheBudget() {
        val m=slot(Slot.MEDICATION);answer(m,"약 1정");answer(m,"다시 말할게요");answer(m,"약 2정")
        val restored=VisitMachine(m.archive,now)
        restored.handle(Event.Boot,now,0);restored.handle(Event.Vehicle(Gear.P,0.0,false,false),now,0);restored.handle(Event.Resume,now,0)
        assertTrue(restored.archive.session.prompt!!.confirming);assertTrue(restored.archive.session.prompt!!.correction)
        answer(restored,"다시 말할게요")
        assertEquals(2,restored.archive.session.visit!!.utterances.size)
        assertTrue(restored.archive.session.visit!!.slots.getValue(Slot.MEDICATION).single().needsConfirmation)
        assertEquals(Slot.QUESTION,restored.archive.session.prompt?.slot)
    }
    @Test fun correctedAppointmentUsesNewUtteranceButKeepsTheOldOne() {
        val m=slot(Slot.APPOINTMENT);answer(m,"12월 10일 오전 3시 아니 4시")
        val old=m.archive.session.visit!!.appointment!!.utteranceId
        answer(m,"다시 말할게요");answer(m,"12월 10일 오전 10시 반");answer(m,"맞아요")
        val v=m.archive.session.visit!!
        assertEquals("10:30",v.appointment!!.time);assertFalse(v.appointment!!.needsConfirmation)
        assertNotEquals(old,v.appointment!!.utteranceId);assertTrue(v.utterances.any { it.id==old })
        assertEquals(2,v.utterances.size);assertTrue(v.traceable())
    }
    @Test fun unsureOrSilenceAfterReadbackNeverConfirms() {
        for(response in listOf("확인 필요", "넘어가", null)) {
            val m=slot(Slot.APPOINTMENT);answer(m,"2주 뒤 오전 10시")
            answer(m,response);if(response==null) answer(m,null)
            assertTrue(m.archive.session.visit!!.appointment!!.needsConfirmation)
            assertEquals(1,m.archive.session.visit!!.utterances.size)
            assertEquals(Slot.CAUTION,m.archive.session.prompt?.slot)
        }
    }
}

package dev.carevisit.app

import dev.carevisit.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoTripTest {
    @Test fun demoNeverAdvancesTheReturnWaitWithoutMovingDAndOnlyAdvancesOnce() {
        val trip=DemoTrip()
        val state=ViewState(Archive(session=Session(phase=Phase.WAIT_SAFE,returnPending=true,visit=Visit("v",trip.hospital,1))))
        for(gear in listOf(Gear.UNKNOWN,Gear.OTHER,Gear.P)) assertEquals(0L,trip.returnAdvance(state.copy(gear=gear),10.0))
        for(speed in listOf(null,0.0,Double.NaN,Double.POSITIVE_INFINITY)) assertEquals(0L,trip.returnAdvance(state.copy(gear=Gear.D),speed))
        assertEquals(Policy.SAFE_DRIVE_MS,trip.returnAdvance(state.copy(gear=Gear.D),10.0))
        assertEquals(0L,trip.returnAdvance(state.copy(gear=Gear.D),10.0))
    }
    @Test fun completeDemoStillVisitsSharesAndReceivesBothReplies() = runTest {
        val demo=DemoTrip();var offset=0L
        val repo=VisitRepositories.open(true) { error("No real archive in demo") }
        val family=FamilyLinks.open(true,{testScheduler.currentTime}) { error("No real relay in demo") } as DemoFamilyLink
        val spoken=mutableListOf<String>()
        val speech=object : SpeechPort {
            override suspend fun say(text: String): Boolean {spoken+=text;return true}
            override suspend fun ask(prompt: Prompt,onListening: ()->Unit): String {spoken+=prompt.text;onListening();delay(1_800);return DemoAnswers.answer(prompt)}
            override suspend fun listen(onListening: ()->Unit): String?=null
            override fun stop() {}
        }
        val core=VisitCore(repo,speech,family,Polisher { it },Navigator {},{1_800_000_000_000L+testScheduler.currentTime+offset},{testScheduler.currentTime+offset})
        backgroundScope.launch {core.run()};runCurrent()
        core.send(Event.Vehicle(Gear.P,0.0,false,false));core.send(demo.destination);core.send(Event.PrepareParked)
        advanceTimeBy(30_000);runCurrent()
        assertEquals(Phase.MEMO,core.state.value.archive.session.phase)
        core.send(DemoFamilyActions.consent(true,family,core.state.value)!!);core.send(demo.arrival);core.send(Event.ParkingMode);runCurrent()
        assertEquals(Phase.AT_HOSPITAL,core.state.value.archive.session.phase)
        assertTrue(DemoFamilyActions.outside(true,family,core.state.value))
        suspend fun replies() {family.poll(core.state.value.archive.settings).forEach { (id,text)->core.send(Event.FamilyReply(id,text)) };runCurrent()}
        replies();assertEquals(Rendezvous.OUTSIDE,core.state.value.archive.session.rendezvous)
        offset+=Policy.STAY_MS;core.send(Event.Vehicle(Gear.D,10.0,true,false));runCurrent()
        assertTrue(core.state.value.archive.session.returnPending);assertNull(core.state.value.archive.session.prompt)
        offset+=demo.returnAdvance(core.state.value,10.0);core.send(Event.Tick)
        advanceTimeBy(30_000);runCurrent();replies()
        assertEquals(Phase.DONE,core.state.value.archive.session.phase)
        assertEquals(1,family.messages.count { it.kind=="briefing" })
        assertTrue(spoken.contains("가족이 잘 받았대요"));assertTrue(spoken.contains("어머니가 정문에 나오셨대요"))
        assertTrue(core.state.value.archive.visits.single().traceable())
        assertFalse(core.state.value.archive.visits.single().slots.values.flatten().any { it.needsConfirmation })
    }
}

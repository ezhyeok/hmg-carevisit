package dev.carevisit.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.nio.file.Files
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeTest {
    class Memory : Repository {
        var data=Archive(); var fail=false
        override suspend fun load()=data
        override suspend fun save(archive: Archive) { if(fail) error("disk full"); data=archive }
    }
    class Speech : SpeechPort {
        val spoken=mutableListOf<String>()
        override suspend fun say(text: String): Boolean { spoken+=text; return true }
        override suspend fun ask(prompt: Prompt,onListening: ()->Unit): String? { spoken+=prompt.text; onListening(); awaitCancellation() }
        override suspend fun listen(onListening: ()->Unit): String? = null
        override fun stop() {}
    }
    class Family : FamilyLink {
        val messages=mutableListOf<ApprovedMessage>(); var revoked=0
        override suspend fun send(settings: Settings,message: ApprovedMessage) { messages+=message }
        override suspend fun revoke(settings: Settings) { revoked++ }
    }
    @Test fun atomicRoundTripAndRecovery() = runTest {
        val dir=Files.createTempDirectory("visit-record-test").toFile()
        try {
            val r=AtomicRepository(dir.resolve("visits.json")); val u=Utterance("u","무릎",1)
            val a=Archive(consent=Consent(1),session=Session(Phase.PRE,Visit("v",Hospital("○○병원"),1,listOf(u),mapOf(Slot.SYMPTOM to listOf(SlotValue("u",u.text))))))
            r.save(a); assertEquals(a,r.load()); assertFalse(dir.resolve("visits.json.tmp").exists())
            dir.resolve("visits.json.tmp").writeText("interrupted write")
            assertEquals(a,r.load()); r.save(Archive()); assertEquals(Archive(),r.load())
        } finally { dir.deleteRecursively() }
    }
    @Test fun brokenTraceIsRejected()= runTest {
        val dir=Files.createTempDirectory("visit-trace").toFile()
        try { val r=AtomicRepository(dir.resolve("visits.json")); assertFailsWith<IllegalArgumentException> { r.save(Archive(visits=listOf(Visit("v",Hospital("병원"),1,slots=mapOf(Slot.SYMPTOM to listOf(SlotValue("missing","허리"))))))) } } finally {dir.deleteRecursively()}
    }
    @Test fun unreadableFileFailsClosed()=runTest {
        val dir=Files.createTempDirectory("visit-corrupt").toFile()
        try { val f=dir.resolve("visits.json"); f.writeText("broken"); assertFails { AtomicRepository(f).load() } } finally {dir.deleteRecursively()}
    }
    @Test fun virtualTimeSafeWindowAndWithdrawalInterruptsSpeech()=runTest {
        val repo=Memory(); val speech=Speech(); val family=Family()
        val core=VisitCore(repo,speech,family,Polisher { it },Navigator {},{1_800_000_000_000L+testScheduler.currentTime},{testScheduler.currentTime})
        backgroundScope.launch {core.run()}; runCurrent()
        core.send(Event.Manual()); core.send(Event.Vehicle(Gear.D,10.0,true,false)); runCurrent()
        advanceTimeBy(59_000); runCurrent(); assertTrue(speech.spoken.isEmpty())
        advanceTimeBy(1_001); runCurrent(); assertTrue(speech.spoken.single().contains("민감정보")); assertNull(repo.data.consent.healthAt)
        core.send(Event.Withdraw); runCurrent(); assertEquals(Archive(),repo.data); assertNull(core.state.value.archive.session.prompt)
    }
    @Test fun failedCommitCannotStartInterview()=runTest {
        val repo=Memory(); val speech=Speech()
        val core=VisitCore(repo,speech,Family(),Polisher{it},Navigator{}, {100L},{testScheduler.currentTime})
        backgroundScope.launch {core.run()}; runCurrent(); repo.fail=true
        core.send(Event.Manual()); runCurrent(); assertTrue(speech.spoken.isEmpty()); assertTrue(core.state.value.notice.contains("저장"))
    }
    @Test fun polisherTimesOutAtEightSeconds()=runTest {
        val source="무릎: 사흘"; var result:String?=null
        val job=launch { result=safePolish(Polisher { delay(40_000); "새 내용" },source) }
        advanceTimeBy(7_999); assertTrue(job.isActive); advanceTimeBy(2); runCurrent(); assertEquals(source,result)
    }
    @Test fun polishErrorFallsBack()=runTest { assertEquals("기록",safePolish(Polisher { error("offline") },"기록")) }
    @Test fun purgeUsesVirtualWallTime()=runTest {
        val repo=Memory(); repo.data=Archive(consent=Consent(1),settings=Settings(retentionDays=1),visits=listOf(Visit("v",Hospital("○○병원"),1)))
        val core=VisitCore(repo,Speech(),Family(),Polisher{it},Navigator{}, {86_399_000L+testScheduler.currentTime},{testScheduler.currentTime})
        backgroundScope.launch {core.run()}; runCurrent(); assertEquals(1,repo.data.visits.size)
        advanceTimeBy(2_001); runCurrent(); assertTrue(repo.data.visits.isEmpty())
    }
    @Test fun queuedLaunchCommandRunsAfterBootRecovery()=runTest {
        val core=VisitCore(Memory(),Speech(),Family(),Polisher{it},Navigator{}, {100L},{testScheduler.currentTime})
        core.send(Event.Manual(Hospital("○○병원")))
        backgroundScope.launch {core.run()};runCurrent()
        assertEquals(Phase.WAIT_SAFE,core.state.value.archive.session.phase)
        assertEquals("○○병원",core.state.value.archive.session.visit?.hospital?.name)
    }

    @Test fun destinationBeforeConsentIsMemoryOnly()=runTest {
        val repo=Memory();val core=VisitCore(repo,Speech(),Family(),Polisher{it},Navigator{}, {100L},{testScheduler.currentTime})
        backgroundScope.launch {core.run()};runCurrent();core.send(Event.Manual(Hospital("○○병원",37.4,127.1)));runCurrent()
        assertNotNull(core.state.value.archive.session.visit)
        assertNull(repo.data.session.visit)
        assertTrue(repo.data.visits.isEmpty())
    }
    @Test fun repositoryRejectsEvenEmptyHospitalRecordWithoutConsent()=runTest {
        val dir=Files.createTempDirectory("visit-no-consent").toFile()
        try {assertFailsWith<IllegalArgumentException>{AtomicRepository(dir.resolve("visits.json")).save(Archive(session=Session(visit=Visit("v",Hospital("○○병원"),1))))}} finally {dir.deleteRecursively()}
    }

    @Test fun storageFailureCannotBeBypassedByVehicleEvents()=runTest {
        val repo=Memory();repo.data=Archive(consent=Consent(1),session=Session(phase=Phase.AT_HOSPITAL,visit=Visit("v",Hospital("○○병원"),1),arrivedAt=1));repo.fail=true
        val speech=Speech();val core=VisitCore(repo,speech,Family(),Polisher{it},Navigator{}, {1_000_000L},{testScheduler.currentTime})
        backgroundScope.launch{core.run()};runCurrent();repo.fail=false
        core.send(Event.Vehicle(Gear.P,0.0,false,false));core.send(Event.Vehicle(Gear.D,10.0,true,false));runCurrent()
        assertTrue(speech.spoken.isEmpty());assertTrue(core.state.value.notice.contains("저장"))
    }

}

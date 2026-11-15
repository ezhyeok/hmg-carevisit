package dev.carevisit.app

import dev.carevisit.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoFamilyLinkTest {
    private val now = 1_800_000_000_000L
    private val done = Archive(consent = Consent(healthAt = now), session = Session(
        phase = Phase.DONE, visit = Visit("demo-visit", Hospital("○○대학교병원"), now),
        briefing = "의사 말씀: 다음 진료 때 검사 결과를 같이 보자고 하셨어"))

    private class Speech : SpeechPort {
        val spoken = mutableListOf<String>()
        override suspend fun say(text: String): Boolean { spoken += text; return true }
        override suspend fun ask(prompt: Prompt, onListening: () -> Unit): String? {
            spoken += prompt.text; onListening(); awaitCancellation()
        }
        override suspend fun listen(onListening: () -> Unit): String? = null
        override fun stop() {}
    }

    private inner class Harness(val scope: TestScope, private val initial: Archive = done) {
        val repository = VisitRepositories.open(true) { error("Demo touched real disk") }
        val family = DemoFamilyLink { scope.testScheduler.currentTime }
        val speech = Speech()
        val core = VisitCore(repository, speech, family, Polisher { it }, Navigator {},
            { now + scope.testScheduler.currentTime }, { scope.testScheduler.currentTime })
        suspend fun start() {
            repository.save(initial)
            scope.backgroundScope.launch { core.run() }
            scope.runCurrent()
            core.send(Event.Vehicle(Gear.P, 0.0, false, false)); scope.runCurrent()
        }
        fun consent() {
            val event = DemoFamilyActions.consent(true, family, core.state.value)
            assertNotNull("Explicit demo consent must produce a guarded core event", event)
            core.send(event!!); scope.runCurrent()
        }
        fun answer(text: String) {
            core.send(Event.Answer(core.state.value.archive.session.prompt!!.id, text, touch = true))
            scope.runCurrent()
        }
        suspend fun poll() {
            family.poll(core.state.value.archive.settings).forEach { (id, text) ->
                core.send(Event.FamilyReply(id, text))
            }
            scope.runCurrent()
        }
    }

    @Test fun demoFactoryNeverConstructsOrCallsRealRelayEvenWithARealUrl() = runTest {
        val connection = FamilyLinks.open(true, { testScheduler.currentTime }) {
            error("Demo must never construct the real HTTP relay")
        }
        assertTrue(connection is DemoFamilyLink)
        val link = connection as DemoFamilyLink
        val machine = VisitMachine(done, now)
        machine.handle(Event.Vehicle(Gear.P, 0.0, false, false), now, 0)
        val status = machine.handle(Event.FamilyConsent(true, link.token), now, 0)
            .filterIsInstance<Effect.Send>().single().message
        val settings = machine.archive.settings.copy(relayUrl = "http://127.0.0.1:1")
        connection.send(settings, status)
        assertEquals(listOf(status), link.messages)
        assertTrue(connection.poll(settings).isEmpty())
        connection.revoke(settings)
        assertTrue(link.messages.isEmpty())
    }

    @Test fun explicitConsentThenIndividualApprovalSendsAndReadsOneDelayedReply() = runTest {
        val h = Harness(this); h.start()
        h.core.send(Event.ShareBriefing); runCurrent()
        assertNull(h.core.state.value.archive.session.prompt)
        assertTrue(h.family.messages.isEmpty())
        h.consent()
        assertNotNull(h.repository.load().consent.familyAt)
        assertEquals(listOf("status"), h.family.messages.map { it.kind })
        h.core.send(Event.ShareBriefing); runCurrent()
        assertEquals(AskKind.SHARE_BRIEFING, h.core.state.value.archive.session.prompt?.kind)
        assertFalse(h.family.messages.any { it.kind == "briefing" })
        h.answer("네")
        assertEquals(done.session.briefing, h.family.messages.single { it.kind == "briefing" }.text)
        assertEquals("가족 화면에 전달했어요.", h.core.state.value.notice)
        advanceTimeBy(2_999); h.poll()
        assertFalse(h.speech.spoken.contains("가족이 잘 받았대요"))
        advanceTimeBy(1); h.poll(); h.poll()
        assertEquals(1, h.speech.spoken.count { it == "가족이 잘 받았대요" })
        assertEquals("가족이 잘 받았대요", h.core.state.value.notice)
    }

    @Test fun decliningIndividualShareNeverSchedulesAReply() = runTest {
        val h = Harness(this); h.start(); h.consent()
        h.core.send(Event.ShareBriefing); runCurrent(); h.answer("아니요")
        advanceTimeBy(5_000); h.poll()
        assertFalse(h.family.messages.any { it.kind == "briefing" })
        assertFalse(h.speech.spoken.contains("가족이 잘 받았대요"))
    }

    @Test fun outsideReplyUsesTheSameConnectionAndOnlyTheParkingMode() = runTest {
        val h = Harness(this, done.copy(session = done.session.copy(phase = Phase.MEMO)))
        h.start(); h.core.send(Event.Resume); runCurrent(); h.consent()
        assertFalse(DemoFamilyActions.outside(true, h.family, h.core.state.value))
        h.core.send(Event.Arrived); runCurrent()
        h.core.send(Event.ParkingMode); runCurrent()
        assertEquals(Rendezvous.PARKING, h.core.state.value.archive.session.rendezvous)
        assertTrue(DemoFamilyActions.outside(true, h.family, h.core.state.value))
        h.poll()
        assertEquals(Rendezvous.OUTSIDE, h.core.state.value.archive.session.rendezvous)
        assertTrue(h.speech.spoken.contains("어머니가 정문에 나오셨대요"))
        assertFalse(DemoFamilyActions.outside(true, h.family, h.core.state.value))
    }

    @Test fun revokeAndCloseDiscardMessagesAndPendingReplies() = runTest {
        val h = Harness(this); h.start(); h.consent()
        h.core.send(Event.ShareBriefing); runCurrent(); h.answer("네")
        val settings = h.core.state.value.archive.settings
        h.core.send(Event.FamilyConsent(false)); runCurrent()
        advanceTimeBy(5_000)
        assertTrue(h.family.poll(settings).isEmpty())
        assertTrue(h.family.messages.isEmpty())
        h.consent(); h.core.send(Event.ShareBriefing); runCurrent(); h.answer("네")
        h.family.close(); advanceTimeBy(5_000)
        assertTrue(h.family.poll(h.core.state.value.archive.settings).isEmpty())
        assertTrue(h.family.messages.isEmpty())
    }

    @Test fun demoConsentIsNeverAutomaticAndNeverChangesRealConsent() = runTest {
        val original = done.copy(consent = Consent(now - 500, now - 100),
            settings = Settings(familyToken = "cd".repeat(16)))
        var realData = original
        val real = object : Repository {
            override suspend fun load() = realData
            override suspend fun save(archive: Archive) { realData = archive }
        }
        val demoRepo = VisitRepositories.open(true) { real }
        assertEquals(Consent(), demoRepo.load().consent)
        val link = DemoFamilyLink { testScheduler.currentTime }
        assertNull(DemoFamilyActions.consent(true, link, ViewState(gear = Gear.P, restricted = false)))
        assertNull(DemoFamilyActions.consent(false, link, ViewState(done, gear = Gear.P, restricted = false)))
        assertNull(DemoFamilyActions.consent(true, link, ViewState(done, gear = Gear.D, restricted = true)))
        val h = Harness(this); h.start(); h.consent()
        assertEquals(original, VisitRepositories.open(false) { real }.load())
        assertNotEquals(original.settings.familyToken, h.repository.load().settings.familyToken)
        assertEquals(Consent(), VisitRepositories.open(true) { real }.load().consent)
    }

    @Test fun realModeStillUsesItsOwnAdapterAndIgnoresDemoActions() {
        var opened = 0
        val real = object : FamilyConnection {
            override suspend fun send(settings: Settings, message: ApprovedMessage) { error("Unexpected real send") }
            override suspend fun revoke(settings: Settings) { error("Unexpected real revoke") }
            override suspend fun poll(settings: Settings): List<Pair<String, String>> = error("Unexpected real poll")
        }
        val link = FamilyLinks.open(false, { 0L }) { opened++; real }
        assertSame(real, link); assertEquals(1, opened)
        val state = ViewState(done.copy(consent = Consent(now, now), session = done.session.copy(
            phase = Phase.AT_HOSPITAL, parkingMode = true)), gear = Gear.P, restricted = false)
        assertNull(DemoFamilyActions.consent(false, link, state))
        assertFalse(DemoFamilyActions.outside(false, link, state))
        assertFalse(DemoFamilyActions.outside(true, link, state))
    }

    @Test fun memoRetriesProduceOneReplyAndWrongTokenCannotConsumeIt() = runTest {
        val h = Harness(this); h.start(); h.consent()
        h.core.send(Event.ShareMemo); runCurrent(); h.answer("네")
        val memo = h.family.messages.single { it.kind == "memo" }
        val settings = h.core.state.value.archive.settings
        h.family.send(settings, memo)
        assertEquals(1, h.family.messages.count { it.kind == "memo" })
        advanceTimeBy(3_000)
        assertTrue(h.family.poll(settings.copy(familyToken = "wrong-token")).isEmpty())
        h.poll(); h.poll()
        assertEquals(1, h.speech.spoken.count { it == "가족이 잘 받았대요" })
    }

    @Test fun deleteAllClearsPendingRepliesAndNewDemoStartsUnpaired() = runTest {
        val h = Harness(this); h.start(); h.consent()
        h.core.send(Event.ShareBriefing); runCurrent(); h.answer("네")
        val oldSettings = h.core.state.value.archive.settings
        h.core.send(Event.DeleteAll); runCurrent()
        assertEquals(Archive(), h.repository.load())
        advanceTimeBy(5_000)
        assertTrue(h.family.messages.isEmpty())
        assertTrue(h.family.poll(oldSettings).isEmpty())
        val next = DemoFamilyLink { testScheduler.currentTime }
        assertTrue(next.token.matches(Regex("[0-9a-f]{32}")))
        assertNotEquals(h.family.token, next.token)
        assertTrue(next.messages.isEmpty())
        assertTrue(next.poll(oldSettings).isEmpty())
        assertFalse(next.outside(oldSettings))
    }
}

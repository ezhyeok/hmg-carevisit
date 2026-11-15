package dev.carevisit.app

import dev.carevisit.core.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DemoPrivacyTest {
    private val visit = Visit("private-visit", Hospital("저장된 병원"), 1L,
        utterances = listOf(Utterance("private-utterance", "보관된 진료 원문", 1L)))
    private val real = ViewState(
        archive = Archive(consent = Consent(1L, 1L),
            settings = Settings(familyToken = "private-token", hospitals = listOf(visit.hospital)),
            visits = listOf(visit), session = Session(visit = visit, briefing = "보관된 브리핑")),
        gear = Gear.P, restricted = false, notice = "보관된 안내")

    @Test fun leavingDemoHidesEntireRealArchiveUntilWindowIsSecure() {
        val visible = SecureWindowPolicy.visibleState(real, appliedDemo = true)
        assertEquals(Archive(), visible.archive)
        assertEquals("", visible.notice)
        assertFalse(visible.fullScreen)
        assertFalse(visible.demo)
    }

    @Test fun unacknowledgedWindowCannotExposeRealRecordsOnRecreation() {
        assertEquals(Archive(), SecureWindowPolicy.visibleState(real, appliedDemo = null).archive)
    }

    @Test fun securedWindowCanShowRealRecordsAndDemoKeepsItsOwnState() {
        assertEquals(real, SecureWindowPolicy.visibleState(real, appliedDemo = false))
        val demo = ViewState(demo = true, notice = "시연", archive = Archive(
            session = Session(visit = Visit("demo", Hospital("○○대학교병원"), 2L))))
        for (applied in listOf(null, false, true)) {
            assertEquals(demo, SecureWindowPolicy.visibleState(demo, applied))
        }
    }

    @Test fun demoNeverOpensRealStorageAndEveryDemoStartsEmpty() = runTest {
        val demo = VisitRepositories.open(true) { error("Demo must never open the real archive") }
        assertEquals(Archive(), demo.load())
        val scripted = Archive(session = Session(visit = Visit("demo", Hospital("○○대학교병원"), 2L)))
        demo.save(scripted)
        assertEquals(scripted, demo.load())
        assertEquals(Archive(), VisitRepositories.open(true) { error("Unexpected disk access") }.load())
    }

    @Test fun demoEditsAndDeletionCannotChangeRealArchiveAndReturningRestoresIt() = runTest {
        var stored = real.archive
        val disk = object : Repository {
            override suspend fun load() = stored
            override suspend fun save(archive: Archive) { stored = archive }
        }
        assertEquals(real.archive, VisitRepositories.open(false) { disk }.load())
        val demo = VisitRepositories.open(true) { disk }
        demo.save(Archive(settings = Settings(honorific = "아버지")))
        demo.save(Archive())
        assertEquals(real.archive, VisitRepositories.open(false) { disk }.load())
    }

    @Test fun queuedRealSettingsCannotEnterDemoAfterSwitchCommand() = runTest {
        // The service has a pending mode switch while the old screen is still on display.
        VisitStore.renderedDemo = false
        VisitStore.commands.trySend(AppCommand.Demo(true))
        VisitStore.send(Event.Configure(real.archive.settings))
        assertEquals(AppCommand.Demo(true), VisitStore.commands.receive())
        val stale = VisitStore.commands.receive() as AppCommand.Core
        assertFalse(stale.belongsTo(demo = true))
        assertTrue(stale.belongsTo(demo = false))
    }

    @Test fun queuedDemoActionsCannotModifyRealRecordsOnExit() {
        val stale = AppCommand.Core(Event.DeleteAll, sourceDemo = true)
        assertFalse(stale.belongsTo(demo = false))
        assertTrue(stale.belongsTo(demo = true))
    }
}

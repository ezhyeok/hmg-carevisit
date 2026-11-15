package dev.carevisit.app.ui

import dev.carevisit.core.*
import org.junit.Assert.*
import org.junit.Test

class NewVisitTest {
    private val now = 1_800_000_000_000L
    private val last = Hospital("지난 병원", 37.4, 127.1)
    private val saved = Hospital("저장 병원", 37.5, 127.2)
    private val previous = Visit("completed", last, now - 86_400_000,
        utterances = listOf(Utterance("check", "검사 날짜 확인하기", now - 86_400_000)),
        slots = mapOf(Slot.NEXT_CHECK to listOf(SlotValue("check", "검사 날짜 확인하기"))), complete = true)
    private fun finished() = ViewState(archive = Archive(consent = Consent(1),
        settings = Settings(hospitals = listOf(saved)), visits = listOf(previous),
        session = Session(phase = Phase.DONE, visit = previous, briefing = Templates.briefing(previous))),
        gear = Gear.P, restricted = false)

    @Test fun completionOffersSavedAndLastHospitalWithoutDuplicatesOrInventedCoordinates() {
        val s = finished()
        assertEquals(listOf(saved, last), ScreenModel.from(s, false).parked!!.completion!!.hospitals)
        val duplicate = s.copy(archive = s.archive.copy(settings = Settings(hospitals = listOf(saved, last))))
        assertEquals(listOf(saved, last), ScreenModel.from(duplicate, false).parked!!.completion!!.hospitals)
        val unsaved = s.copy(archive = s.archive.copy(settings = Settings(),
            session = s.archive.session.copy(visit = previous.copy(hospital = Hospital("이름만 병원")))))
        val choice = ScreenModel.from(unsaved, false).parked!!.completion!!.hospitals.single()
        assertEquals("이름만 병원", choice.name)
        assertFalse(choice.routable)
    }

    @Test fun newVisitRequiresParkAndBothRestrictionChecks() {
        for (gear in Gear.entries) for (service in listOf(false, true)) for (platform in listOf(false, true)) {
            val s = finished().copy(gear = gear, restricted = service)
            val allowed = gear == Gear.P && !service && !platform
            assertEquals("$gear/$service/$platform", allowed, newVisitCommand(s, platform, saved) != null)
            assertEquals(allowed, ScreenModel.from(s, platform).parked?.completion?.hospitals?.isNotEmpty() == true)
        }
    }

    @Test fun staleChoiceCannotStartWhileAnotherPromptOrVisitIsActive() {
        val s = finished()
        for (phase in Phase.entries.filter { it != Phase.DONE }) {
            val active = s.copy(archive = s.archive.copy(session = s.archive.session.copy(phase = phase)))
            assertNull(newVisitCommand(active, false, saved))
        }
        val busy = s.copy(archive = s.archive.copy(session = s.archive.session.copy(prompt = Prompt(1, AskKind.SHARE_BRIEFING, "공유 확인"))))
        assertNull(newVisitCommand(busy, false, saved))
        assertNull(newVisitCommand(s.copy(archive = s.archive.copy(consent = Consent())), false, saved))
        assertNull(newVisitCommand(s, false, Hospital("목록에 없는 병원")))
    }

    @Test fun selectedHospitalStartsNewIdAndKeepsCompletedHistoryAndContinuity() {
        val s = finished()
        val machine = VisitMachine(s.archive, now)
        machine.handle(Event.Vehicle(Gear.P, 0.0, false, false), now, now)
        val command = newVisitCommand(machine.view, false, last)
        assertNotNull("완료 화면에서 지난 병원을 선택할 수 있어야 합니다", command)
        machine.handle(command!!, now + 1, now + 1)
        assertEquals(Phase.WAIT_SAFE, machine.archive.session.phase)
        assertNotEquals(previous.id, machine.archive.session.visit!!.id)
        assertEquals(last, machine.archive.session.visit!!.hospital)
        assertEquals(listOf(previous), machine.archive.visits)
        machine.handle(Event.PrepareParked, now + 2, now + 2)
        val offer = machine.archive.session.prompt!!
        assertEquals(AskKind.PRE_OFFER, offer.kind)
        machine.handle(Event.Answer(offer.id, "네", touch = true), now + 3, now + 3)
        assertEquals(Slot.CONTINUITY, machine.archive.session.prompt!!.slot)
        assertTrue(machine.archive.session.prompt!!.text.contains("검사 날짜 확인하기"))
        assertEquals(listOf(previous), machine.archive.visits)
    }

    @Test fun anotherSavedHospitalDoesNotReuseTheLastHospitalsQuestions() {
        val s = finished()
        val machine = VisitMachine(s.archive, now)
        machine.handle(Event.Vehicle(Gear.P, 0.0, false, false), now, now)
        val command = newVisitCommand(machine.view, false, saved)
        assertNotNull(command)
        machine.handle(command!!, now + 1, now + 1)
        assertEquals(saved, machine.archive.session.visit!!.hospital)
        machine.handle(Event.PrepareParked, now + 2, now + 2)
        machine.handle(Event.Answer(machine.archive.session.prompt!!.id, "네", touch = true), now + 3, now + 3)
        assertEquals(Slot.SYMPTOM, machine.archive.session.prompt!!.slot)
        assertEquals(listOf(previous), machine.archive.visits)
    }

    @Test fun completionAndReminderQuestionDescribeAppRunningRatherThanBoot() {
        val s = finished()
        val reminded = s.copy(archive = s.archive.copy(session = s.archive.session.copy(
            visit = previous.copy(reminder = true, appointment = Appointment("2026-12-10", "10:00", false)))))
        assertEquals("앱이 켜져 있을 때 진료일에 알려 드려요.", ScreenModel.from(reminded, false).parked!!.completion!!.reminderNotice)
        assertNull(ScreenModel.from(s, false).parked!!.completion!!.reminderNotice)
        val asking = s.copy(archive = s.archive.copy(session = s.archive.session.copy(
            phase = Phase.REMINDER, prompt = Prompt(2, AskKind.REMINDER, "다음 진료일에 차를 켜면 알려 드릴까요?"))))
        assertEquals("앱이 켜져 있을 때 진료일에 알려 드릴까요?", ScreenModel.from(asking, false).question)
    }

    @Test fun doorSignalDoesNotClaimPassengerExit() {
        val s = finished()
        val door = s.copy(archive = s.archive.copy(session = s.archive.session.copy(
            phase = Phase.AT_HOSPITAL, rendezvous = Rendezvous.AT_GATE, droppedOff = true)))
        val meeting = ScreenModel.from(door, false).parked!!.rendezvous!!
        assertEquals("동승석 문이 열렸어요", meeting.label)
        assertEquals("하차 여부는 확인하지 않아요.", meeting.detail)
    }
}

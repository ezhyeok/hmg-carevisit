package dev.carevisit.app.ui

import dev.carevisit.core.*
import org.junit.Assert.*
import org.junit.Test

class ReviewStateTest {
    private val now = 1_800_000_000_000L
    private val visit = Visit("review", Hospital("○○병원"), now)
    private fun state(session: Session) = ViewState(Archive(consent = Consent(1), session = session), gear = Gear.P, restricted = false, demo = true)

    @Test fun returnWaitKeepsBriefingContextAndDemoStep() {
        val s = state(Session(phase = Phase.WAIT_SAFE, visit = visit, returnPending = true))
        val model = ScreenModel.from(s, false)
        assertEquals("오는 길 · 진료 브리핑", model.title)
        assertEquals("누적 주행 60초 뒤 진료 내용을 여쭤볼게요", model.question)
        assertEquals("브리핑", model.parked!!.demoSteps.single { it.current }.label)
        assertEquals(model.question, ScreenModel.from(s.copy(gear = Gear.D), true).question)
    }

    @Test fun confirmingAndCorrectingGlanceNeverExposesReadback() {
        for (slot in listOf(Slot.MEDICATION, Slot.MED_CHANGE, Slot.APPOINTMENT)) {
            val p = Prompt(1, AskKind.SLOT, "공유하면 안 되는 긴 기록".repeat(30), slot, confirming = true)
            val s = state(Session(phase = Phase.POST, visit = visit, prompt = p))
            val glance = ScreenModel.from(s, true)
            assertEquals("기록한 내용이 맞나요?", glance.question)
            assertFalse(glance.toString().contains("공유하면"))
            assertEquals(listOf("예", "아니요", "넘어가기"), glance.answers.map { it.label })
            val correcting = s.copy(archive = s.archive.copy(session = s.archive.session.copy(prompt = p.copy(confirming = false, correction = true))))
            assertEquals("기록할 내용을 다시 말씀해 주세요", ScreenModel.from(correcting, true).question)
        }
    }

    @Test fun parkedConfirmationUsesCoreResponsesAndOnlyOneCorrection() {
        val p = Prompt(1, AskKind.SLOT, "기록 확인", Slot.MEDICATION, confirming = true)
        val s = state(Session(phase = Phase.PRE, visit = visit, prompt = p))
        val first = ScreenModel.from(s, false).answers
        assertEquals(listOf("맞아요", "다시 말할게요", "확인 필요"), first.map { it.label })
        assertEquals(listOf("네", "아니요", "넘어가"), first.map { it.answer })
        val corrected = s.copy(archive = s.archive.copy(session = s.archive.session.copy(prompt = p.copy(correction = true))))
        assertEquals(listOf("맞아요", "확인 필요"), ScreenModel.from(corrected, false).answers.map { it.label })
    }

    @Test fun uncertainSlotsShowTextMarksInMemoAndBriefing() {
        val v = visit.copy(slots = mapOf(
            Slot.MEDICATION to listOf(SlotValue("med", "약 봉투 확인", needsConfirmation = true)),
            Slot.MED_CHANGE to listOf(SlotValue("change", "바뀐 약 확인", needsConfirmation = true)),
            Slot.APPOINTMENT to listOf(SlotValue("date", "12월 10일", needsConfirmation = true))),
            appointment = Appointment("2026-12-10", "10:00", false))
        assertTrue(memoModel(v).rows.single { it.label == "약" }.confirmation)
        assertTrue(briefingModel(v).rows.single { it.label == "약 변경" }.confirmation)
        assertTrue(briefingModel(v).rows.single { it.label == "다음 예약" }.confirmation)
    }

    @Test fun parkedCorrectionButtonRequestsAReplacementWithoutLosingOriginalUtterance() {
        val s = state(Session(phase = Phase.PRE, visit = visit, slotIndex = 3,
            prompt = Prompt(1, AskKind.SLOT, "드시는 약", Slot.MEDICATION)))
        val machine = VisitMachine(s.archive, now)
        machine.handle(Event.Vehicle(Gear.P, 0.0, false, false), now, now)
        machine.handle(Event.Answer(1, "원래 기록", touch = true), now + 1, now + 1)
        assertTrue(machine.archive.session.prompt!!.confirming)
        val button = ScreenModel.from(machine.view, false).answers.single { it.label == "다시 말할게요" }
        machine.handle(Event.Answer(machine.archive.session.prompt!!.id, button.answer, touch = true), now + 2, now + 2)
        assertTrue(machine.archive.session.prompt!!.correction)
        machine.handle(Event.Answer(machine.archive.session.prompt!!.id, "고친 기록", touch = true), now + 3, now + 3)
        assertEquals("고친 기록", machine.archive.session.visit!!.text(Slot.MEDICATION))
        assertEquals(listOf("원래 기록", "고친 기록"), machine.archive.session.visit!!.utterances.map { it.text })
    }

    @Test fun pendingHospitalOnlyAppearsInParkedChangeConfirmation() {
        val target = Hospital("새 목적지 병원", 37.4, 127.1)
        val s = state(Session(phase = Phase.MEMO, visit = visit, pendingHospital = target))
        val parked = ScreenModel.from(s, false)
        assertEquals(target, parked.parked!!.destinationChange)
        assertEquals("방문 병원을 바꿀까요?", parked.question)
        val driving = ScreenModel.from(s, true)
        assertEquals("병원 변경은 주차 후 확인하세요", driving.question)
        assertFalse(driving.toString().contains(target.name))
        assertTrue(driving.answers.isEmpty())
        assertFalse(driving.showVoiceStart)
        assertTrue(parked.parked.demoSteps.none { it.enabled })
    }

    @Test fun destinationButtonsRecheckRestrictionsAndTheCurrentCandidate() {
        val target = Hospital("새 목적지 병원")
        val s = state(Session(phase = Phase.MEMO, visit = visit, pendingHospital = target))
        assertEquals(Event.ConfirmDestination, destinationCommand(s, false, target, true))
        assertEquals(Event.DismissDestination, destinationCommand(s, false, target, false))
        assertNull(destinationCommand(s, false, Hospital("이전 후보 병원"), true))
        for (gear in Gear.entries) for (restricted in listOf(false, true)) for (platform in listOf(false, true)) {
            val current = s.copy(gear = gear, restricted = restricted)
            assertEquals(gear == Gear.P && !restricted && !platform, destinationCommand(current, platform, target, true) != null)
        }
        val withoutHealth = s.copy(archive = s.archive.copy(consent = Consent()))
        assertNull(destinationCommand(withoutHealth, false, target, true))
    }

    @Test fun pendingDestinationTakesPriorityOverNewVisitAndOldPrompt() {
        val s = state(Session(phase = Phase.DONE, visit = visit.copy(complete = true),
            pendingHospital = Hospital("새 병원"), prompt = Prompt(1, AskKind.REMINDER, "지난 안내")))
        val model = ScreenModel.from(s, false)
        assertEquals("방문 병원을 바꿀까요?", model.question)
        assertNull(model.parked!!.completion)
        assertTrue(model.answers.isEmpty())
        assertNull(newVisitCommand(s, false, visit.hospital))
    }
}

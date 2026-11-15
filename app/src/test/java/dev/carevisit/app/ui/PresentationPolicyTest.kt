package dev.carevisit.app.ui

import dev.carevisit.core.*
import org.junit.Assert.*
import org.junit.Test

class PresentationPolicyTest {
    private val secret = "기록에만 남을 원문"
    private val visit = Visit("visit-1", Hospital("○○병원", 37.4, 127.1), 1_800_000_000_000L,
        slots = Slot.entries.associateWith { listOf(SlotValue(it.name, "$secret ${it.name}")) },
        appointment = Appointment(verbatim = "다음 달쯤", needsConfirmation = true), checkedQuestions = setOf(Slot.QUESTION.name))
    private fun state(phase: Phase = Phase.PRE, consent: Consent = Consent(1, 2), demo: Boolean = false) = ViewState(
        archive = Archive(consent = consent, visits = listOf(visit.copy(complete = true)), session = Session(phase = phase, visit = visit, briefing = secret)),
        restricted = false, gear = Gear.P, notice = secret, demo = demo)

    @Test fun everyPhaseHasAReadableTitleAndBoundedGlance() {
        for (phase in Phase.entries) {
            val model = ScreenModel.from(state(phase), true)
            assertTrue(phase.name, model.title.isNotBlank())
            assertTrue(model.question.length <= DriveLimits.QUESTION_CHARS)
            assertFalse(model.toString().contains(secret))
            assertNull(model.parked)
        }
    }
    @Test fun allGearAndRestrictionCombinationsFailClosed() {
        for (gear in Gear.entries) for (service in listOf(false, true)) for (platform in listOf(false, true)) {
            val model = ScreenModel.from(state().copy(gear = gear, restricted = service), platform)
            val expected = gear != Gear.P || service || platform
            assertEquals("$gear/$service/$platform", expected, model.glance)
            assertEquals(expected, model.parked == null)
        }
    }
    @Test fun everyPromptAndSlotUsesFixedDrivingCopy() {
        for (kind in AskKind.entries) for (slot in Slot.entries) {
            val initial = state()
            val archive = initial.archive.copy(session = initial.archive.session.copy(prompt = Prompt(55, kind, secret.repeat(500), slot)))
            val model = ScreenModel.from(initial.copy(archive = archive), true)
            assertFalse("$kind/$slot", model.question.contains(secret))
            assertTrue(model.question.length <= DriveLimits.QUESTION_CHARS)
            assertEquals(55L, model.promptId)
            assertEquals(3, model.answers.size)
            assertTrue(model.answers.size <= DriveLimits.TOUCH_TARGETS)
        }
    }
    @Test fun allVoiceLabelsAreShortAndOnlyOnReadyMeansListening() {
        val expected = mapOf(VoiceState.IDLE to "음성 대기", VoiceState.SPEAKING to "말하는 중", VoiceState.PREPARING to "마이크 준비 중", VoiceState.LISTENING to "듣는 중", VoiceState.UNAVAILABLE to "음성 연결 확인 필요")
        expected.forEach { (voice, label) ->
            val model = ScreenModel.from(state().copy(voice = voice), true)
            assertEquals(label, model.voice)
            assertTrue(model.voice.length <= DriveLimits.LABEL_CHARS)
        }
    }
    @Test fun drivingProgressIsBoundedAndOnlyForInterview() {
        for (phase in Phase.entries) for (index in listOf(-5, 0, 1, 4, 99)) {
            val initial = state(phase)
            val model = ScreenModel.from(initial.copy(archive = initial.archive.copy(session = initial.archive.session.copy(slotIndex = index))), true)
            if (phase in setOf(Phase.PRE, Phase.POST)) assertEquals("${index.coerceIn(0, 4) + 1}/5", model.progress)
            else assertEquals("", model.progress)
        }
    }
    @Test fun drivingButtonsAreFixedResponsesAndNoButtonsExistWithoutPrompt() {
        assertTrue(ScreenModel.from(state(), true).answers.isEmpty())
        val initial = state()
        val s = initial.copy(archive = initial.archive.copy(session = initial.archive.session.copy(prompt = Prompt(1, AskKind.SLOT, secret))))
        val answers = ScreenModel.from(s, true).answers
        assertEquals(listOf("예", "아니요", "넘어가기"), answers.map { it.label })
        assertEquals(listOf("네", "아니요", "넘어가"), answers.map { it.answer })
        assertTrue(answers.all { it.enabled })
    }
    @Test fun healthTouchConsentIsDisabledInGlance() {
        val initial = state(Phase.HEALTH_CONSENT, Consent())
        val s = initial.copy(archive = initial.archive.copy(session = initial.archive.session.copy(prompt = Prompt(1, AskKind.HEALTH, Policy.HEALTH_LINE))))
        assertTrue(ScreenModel.from(s, true).answers.none { it.enabled })
        assertTrue(ScreenModel.from(s, false).answers.all { it.enabled })
    }
    @Test fun healthConsentGatesAllParkedHealthContent() {
        for (familyAt in listOf(null, 5L)) {
            val p = ScreenModel.from(state(consent = Consent(familyAt = familyAt)), false).parked!!
            assertNull(p.memo)
            assertNull(p.briefing)
            assertNull(p.rendezvous)
            assertTrue(p.history.isEmpty())
            assertTrue(p.consent.showHealth)
            assertFalse(p.consent.canPair)
            assertFalse(p.consent.canShare)
        }
    }
    @Test fun healthAndFamilyConsentAreSeparate() {
        val healthOnly = ScreenModel.from(state(consent = Consent(1)), false).parked!!
        assertNotNull(healthOnly.memo)
        assertTrue(healthOnly.consent.canPair)
        assertFalse(healthOnly.consent.canShare)
        val both = ScreenModel.from(state(), false).parked!!
        assertTrue(both.consent.canShare)
        assertFalse(both.consent.canPair)
    }
    @Test fun withdrawalImmediatelyRemovesDocuments() {
        val s = state()
        assertNotNull(ScreenModel.from(s, false).parked!!.memo)
        val withdrawn = s.copy(archive = Archive())
        val p = ScreenModel.from(withdrawn, false).parked!!
        assertNull(p.memo)
        assertTrue(p.history.isEmpty())
        assertTrue(p.consent.showHealth)
    }
    @Test fun emptyStateDoesNotOfferSharing() {
        val p = ScreenModel.from(ViewState(Archive(consent = Consent(1, 2)), restricted = false, gear = Gear.P), false).parked!!
        assertNull(p.memo)
        assertFalse(p.consent.canShare)
    }
    @Test fun memoUsesVerbatimAndQuestionIdsNotNormalizedWords() {
        val v = visit.copy(slots = mapOf(Slot.SYMPTOM to listOf(SlotValue("s1", "무릎이 불편해", "REPLACEMENT")), Slot.QUESTION to listOf(SlotValue("q1", "지난 검사 물어보기"))), checkedQuestions = setOf("q1"))
        val memo = memoModel(v)
        assertEquals(listOf("불편한 곳", "언제부터", "양상", "약"), memo.rows.map { it.label })
        assertEquals("무릎이 불편해", memo.rows.first().text)
        assertFalse(memo.toString().contains("REPLACEMENT"))
        assertEquals(QuestionItem("q1", "지난 검사 물어보기", true), memo.questions.single())
    }
    @Test fun missingSlotsAreNotInvented() {
        val empty = visit.copy(slots = emptyMap(), appointment = null)
        assertTrue(memoModel(empty).rows.all { it.text == "아직 기록 없음" })
        assertTrue(briefingModel(empty).rows.all { it.text == "아직 기록 없음" })
    }
    @Test fun appointmentUncertaintyHasTextNotJustColor() {
        val row = briefingModel(visit).rows.single { it.label == "다음 예약" }
        assertTrue(row.confirmation)
        assertTrue(appointmentLabel(visit.appointment!!).contains("확인 필요"))
        val exact = visit.copy(appointment = Appointment("2026-12-10", "10:00", false))
        assertFalse(briefingModel(exact).rows.single { it.label == "다음 예약" }.confirmation)
    }
    @Test fun rawAppointmentWithoutParsedDateStillNeedsConfirmation() {
        val raw = visit.copy(appointment = null)
        assertTrue(briefingModel(raw).rows.single { it.label == "다음 예약" }.confirmation)
    }
    @Test fun historyIsGroupedPerHospitalNewestFirst() {
        val initial = state()
        val old = visit.copy(id = "old", createdAt = visit.createdAt - 86_400_000)
        val other = visit.copy(id = "other", hospital = Hospital("△△의원"))
        val s = initial.copy(archive = initial.archive.copy(visits = listOf(old, other, visit)))
        val groups = ScreenModel.from(s, false).parked!!.history
        assertEquals(2, groups.size)
        assertEquals(listOf("visit-1", "old"), groups.single { it.hospital == "○○병원" }.entries.map { it.id })
        assertEquals(visitDay(visit.createdAt), groups.single { it.hospital == "○○병원" }.entries.first().date)
    }
    @Test fun demoLabelAndPanelOnlyExistWhenDemoIsActiveAndParked() {
        for (phase in Phase.entries) {
            val normal = ScreenModel.from(state(phase), false)
            assertNull(normal.demoLabel)
            assertTrue(normal.parked!!.demoSteps.isEmpty())
            val demo = ScreenModel.from(state(phase, demo = true), false)
            assertEquals("시연: 미리 정한 대답 · 가족 답장 모의", demo.demoLabel)
            assertEquals(UiCopy.DEMO_STEPS, demo.parked!!.demoSteps.map { it.label })
            assertEquals(1, demo.parked.demoSteps.count { it.current })
            assertTrue(demo.parked.demoSteps.count { it.enabled } <= 1)
            val driving = ScreenModel.from(state(phase, demo = true), true)
            assertEquals(UiCopy.DEMO, driving.demoLabel)
            assertNull(driving.parked)
        }
    }
    @Test fun allChipTabAndStepLabelsFitTheShortLabelBudget() {
        val labels = UiCopy.TABS + UiCopy.DEMO_STEPS + UiCopy.HONORIFICS + listOf("수집·보관 동의함", "별도 동의함", "나왔어 · 수신", "시연 · 주차 중", "확인 필요", "보내기 전 확인")
        labels.forEach { assertTrue(it, it.length <= DriveLimits.LABEL_CHARS) }
    }
    @Test fun parkedDetailsPreserveLongTextRatherThanEllipsizing() {
        val long = "말씀하신 내용 ".repeat(200)
        val v = visit.copy(slots = mapOf(Slot.DOCTOR to listOf(SlotValue("doctor", long))))
        assertEquals(long, briefingModel(v).rows.first().text)
    }
    @Test fun dropOffAndReceivedRendezvousHaveDifferentLabels() {
        val initial = state(Phase.AT_HOSPITAL)
        fun label(session: Session, notice: String = "") = ScreenModel.from(initial.copy(archive = initial.archive.copy(session = session), notice = notice), false).parked!!.rendezvous!!.label
        val arrived = initial.archive.session.copy(arrivedAt = 100, rendezvous = Rendezvous.AT_GATE)
        assertEquals("정문 도착", label(arrived))
        assertEquals("동승석 문이 열렸어요", label(arrived.copy(droppedOff = true)))
        val parking = arrived.copy(parkingMode = true, rendezvous = Rendezvous.PARKING)
        assertEquals("주차 후 합류", label(parking))
        assertEquals("나왔어 · 수신", label(parking.copy(rendezvous = Rendezvous.OUTSIDE), "다른 안내"))
        assertEquals("주차 후 합류", label(parking, "임의의 원문"))
    }
    @Test fun demoStagesFollowTheVisitLifecycleWithoutRestartingOnSend() {
        val expected = mapOf(
            Phase.IDLE to 0, Phase.WAIT_SAFE to 0,
            Phase.HEALTH_CONSENT to 1, Phase.OFFER_PRE to 1, Phase.PRE to 1, Phase.RECOVERY to 1,
            Phase.MEMO to 2, Phase.AT_HOSPITAL to 3,
            Phase.OFFER_POST to 4, Phase.POST to 4, Phase.BRIEFING to 4,
            Phase.SHARE to 5, Phase.REMINDER to 5, Phase.DONE to 5,
        )
        expected.forEach { (phase, index) -> assertEquals(phase.name, index, demoSteps(phase).indexOfFirst { it.current }) }
        assertTrue(demoSteps(Phase.BRIEFING, true).none { it.enabled })
        assertTrue(demoSteps(Phase.DONE, false).none { it.enabled })
        assertEquals("보내기", demoSteps(Phase.DONE, true).single { it.enabled }.label)
    }
    @Test fun lastVisitContinuityIsVisibleOnlyInTheParkedMemo() {
        val model = ScreenModel.from(state(), false)
        assertEquals(visit.text(Slot.CONTINUITY), model.parked!!.memo!!.rows.single { it.label == "지난 진료 이후" }.text)
        assertNull(ScreenModel.from(state(), true).parked)
    }
    @Test fun drivingTextBudgetIncludesStepVoiceAndDemoLabel() {
        for (phase in Phase.entries) for (kind in AskKind.entries) {
            val initial = state(phase, demo = true)
            val s = initial.copy(archive = initial.archive.copy(session = initial.archive.session.copy(prompt = Prompt(1, kind, secret))))
            val model = ScreenModel.from(s, true)
            val visible = listOf(model.title, model.question, model.progress, model.voice, model.demoLabel.orEmpty()) + model.answers.map { it.label }
            assertTrue("$phase/$kind", visible.sumOf { it.length } <= 150)
        }
    }

    @Test fun sharingYesWaitsForCompletedReadbackInGlance() {
        for (kind in listOf(AskKind.SHARE_MEMO, AskKind.SHARE_BRIEFING)) for (voice in VoiceState.entries) {
            val initial = state(Phase.SHARE)
            val s = initial.copy(voice = voice, archive = initial.archive.copy(session = initial.archive.session.copy(prompt = Prompt(1, kind, secret))))
            val model = ScreenModel.from(s, true)
            assertEquals(voice == VoiceState.LISTENING, model.answers.single { it.label == "예" }.enabled)
            assertTrue(model.answers.filter { it.label != "예" }.all { it.enabled })
        }
    }
    @Test fun unknownVehicleStateDisablesTouchResponses() {
        val initial = state()
        val s = initial.copy(gear = Gear.UNKNOWN, archive = initial.archive.copy(session = initial.archive.session.copy(prompt = Prompt(1, AskKind.SLOT, secret))))
        assertTrue(ScreenModel.from(s, false).answers.none { it.enabled })
    }

}

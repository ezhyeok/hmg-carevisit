package dev.carevisit.app.ui

import dev.carevisit.core.*
import org.junit.Assert.*
import org.junit.Test

class CompletionScreenTest {
    private fun finished() = ViewState(
        archive = Archive(consent = Consent(healthAt = 1), session = Session(
            phase = Phase.DONE,
            visit = Visit("today", Hospital("○○병원"), 1_800_000_000_000L,
                appointment = Appointment("2026-12-10", "10:00", false), complete = true),
            briefing = "의사 말씀: 다음에 검사 결과를 함께 보기로 했어요",
            rendezvous = Rendezvous.OUTSIDE,
        )),
        gear = Gear.P, restricted = false, demo = true,
        notice = "시연: 진료 15분 경과 · Virtual Console에서 기어 D로 출발하세요.",
    )

    @Test fun parkedCompletionDoesNotAskToStartAnotherTrip() {
        val model = ScreenModel.from(finished(), false)
        assertEquals("오늘 진료 기록을 마쳤어요", model.question)
        assertFalse(model.question.contains("병원 가는 길"))
    }

    @Test fun drivingCompletionIsCalmAndContainsNoStartPrompt() {
        val model = ScreenModel.from(finished().copy(gear = Gear.D, restricted = true), true)
        assertEquals("진료 기록 완료 · 주차 후 확인하세요", model.question)
        assertTrue(model.answers.isEmpty())
        assertNull(model.parked)
    }
    @Test fun completionOffersTheBriefingAndAppointmentWithoutVoiceStart() {
        val model = ScreenModel.from(finished(), false)
        assertFalse(model.showVoiceStart)
        assertFalse(model.showVoiceStatus)
        assertEquals("브리핑 보기", model.parked!!.completion!!.actionLabel)
        assertEquals("12월 10일(목) 오전 10시 00분", model.parked.completion!!.appointment)
        assertFalse(ScreenModel.from(finished(), true).showVoiceStart)
    }

    @Test fun briefingMemoThenRendezvousIsTheSharedDocumentOrder() {
        assertEquals(listOf(TodayCard.BRIEFING, TodayCard.MEMO, TodayCard.RENDEZVOUS),
            ScreenModel.from(finished(), false).parked!!.cards)
    }

    @Test fun elapsedVisitDemoHintExpiresOnDepartureButRealNoticesRemain() {
        val s = finished()
        assertNull(ScreenModel.from(s, false).parked!!.notice)
        val waiting = s.copy(archive = s.archive.copy(session = s.archive.session.copy(phase = Phase.AT_HOSPITAL)))
        assertNotNull(ScreenModel.from(waiting, false).parked!!.notice)
        val returning = s.copy(archive = s.archive.copy(session = s.archive.session.copy(phase = Phase.POST)))
        assertNull(ScreenModel.from(returning, false).parked!!.notice)
        assertEquals("보내지 못했어요. 다시 시도해 주세요.", ScreenModel.from(s.copy(notice = "보내지 못했어요. 다시 시도해 주세요."), false).parked!!.notice)
        assertEquals("시연 가족 · 가족이 잘 받았대요", ScreenModel.from(s.copy(notice = "가족이 잘 받았대요"), false).parked!!.notice)
    }

    @Test fun completedMemoWithoutBriefingDoesNotInventABriefingOrAppointment() {
        val s = finished()
        val memoOnly = s.copy(archive = s.archive.copy(session = s.archive.session.copy(briefing = "", visit = s.archive.session.visit!!.copy(appointment = null))))
        val model = ScreenModel.from(memoOnly, false).parked!!
        assertEquals("진료 메모 보기", model.completion!!.actionLabel)
        assertNull(model.completion.appointment)
        assertFalse(TodayCard.BRIEFING in model.cards)
    }

    @Test fun missingDemoFamilyConsentLeadsToConsentInsteadOfDisabledSend() {
        val p = ScreenModel.from(finished(), false).parked!!
        assertEquals(BriefingShareAction.CONNECT_DEMO, p.shareAction)
        assertFalse(p.consent.canShare)
        assertTrue(p.demoSteps.single { it.label == "보내기" }.enabled)
    }

    @Test fun realModeStillRequiresFamilyConsentAndEveryShareOpensConfirmation() {
        val s = finished().copy(demo = false)
        assertEquals(BriefingShareAction.NONE, ScreenModel.from(s, false).parked!!.shareAction)
        val consented = s.copy(archive = s.archive.copy(consent = Consent(1, 2)))
        assertEquals(BriefingShareAction.CONFIRM, ScreenModel.from(consented, false).parked!!.shareAction)
        val withoutHealth = finished().copy(archive = finished().archive.copy(consent = Consent()))
        val p = ScreenModel.from(withoutHealth, false).parked!!
        assertEquals(BriefingShareAction.NONE, p.shareAction)
        assertNull(p.completion)
        assertTrue(p.cards.isEmpty())
    }

    @Test fun pendingPromptOverridesCompletionAndIdleStillCanStart() {
        val s = finished()
        val asking = s.copy(archive = s.archive.copy(session = s.archive.session.copy(prompt = Prompt(8, AskKind.SHARE_BRIEFING, "원문"))))
        assertEquals("가족에게 보낼까요?", ScreenModel.from(asking, true).question)
        assertNull(ScreenModel.from(asking, false).parked!!.completion)
        assertTrue(ScreenModel.from(s.copy(archive = Archive()), false).showVoiceStart)
    }

    @Test fun consentMustBeObservedForTheSameVisitBeforeRequestingShare() {
        val s = finished()
        assertFalse(shouldRequestBriefingAfterDemoConsent(s, "today"))
        val connected = s.copy(archive = s.archive.copy(consent = Consent(1, 2)))
        assertTrue(shouldRequestBriefingAfterDemoConsent(connected, "today"))
        assertFalse(shouldRequestBriefingAfterDemoConsent(connected, null))
        assertFalse(shouldRequestBriefingAfterDemoConsent(connected, "another-visit"))
        assertFalse(shouldRequestBriefingAfterDemoConsent(connected.copy(demo = false), "today"))
        assertFalse(shouldRequestBriefingAfterDemoConsent(connected.copy(restricted = true), "today"))
        assertFalse(shouldRequestBriefingAfterDemoConsent(connected.copy(archive = connected.archive.copy(consent = Consent(familyAt = 2))), "today"))
        val busy = connected.copy(archive = connected.archive.copy(session = connected.archive.session.copy(prompt = Prompt(9, AskKind.REMINDER, "예약 알림"))))
        assertFalse(shouldRequestBriefingAfterDemoConsent(busy, "today"))
        val changing = connected.copy(archive = connected.archive.copy(session = connected.archive.session.copy(pendingHospital = Hospital("다른 병원"))))
        assertFalse(shouldRequestBriefingAfterDemoConsent(changing, "today"))
        assertEquals(BriefingShareAction.NONE, ScreenModel.from(changing, false).parked!!.shareAction)
    }

    @Test fun sharingAfterDemoConsentStillOnlyOpensThePerBriefingQuestion() {
        val s = finished()
        val now = s.archive.session.visit!!.createdAt
        val machine = VisitMachine(s.archive.copy(consent = Consent(1, 2)), now)
        machine.handle(Event.Vehicle(Gear.P, 0.0, false, false), now, now)
        val request = machine.handle(Event.ShareBriefing, now + 1, now + 1)
        assertTrue(request.any { it is Effect.Ask })
        assertFalse(request.any { it is Effect.Send })
        val prompt = machine.archive.session.prompt!!
        assertEquals(AskKind.SHARE_BRIEFING, prompt.kind)
        assertEquals("가족에게 보낼까요?", ScreenModel.from(machine.view, false).question)
        val approved = machine.handle(Event.Answer(prompt.id, "네", touch = true), now + 2, now + 2)
        assertTrue(approved.any { it is Effect.Send })
    }

}

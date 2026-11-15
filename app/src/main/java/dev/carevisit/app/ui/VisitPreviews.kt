package dev.carevisit.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.carevisit.core.*

/** Fictional, deterministic fixtures for the laptop Layout Preview. No real names or records. */
private fun previewState(phase: Phase = Phase.MEMO): ViewState {
    val visit = Visit("preview", Hospital("○○대학교병원", 37.4, 127.1), 1_800_000_000_000L,
        slots = mapOf(
            Slot.SYMPTOM to listOf(SlotValue("1", "오른쪽 무릎이 불편해요")),
            Slot.DURATION to listOf(SlotValue("2", "사흘 전부터요")),
            Slot.DETAIL to listOf(SlotValue("3", "계단을 내려갈 때 그래요")),
            Slot.MEDICATION to listOf(SlotValue("4", "가져온 약 봉투를 보여 드릴게요")),
            Slot.QUESTION to listOf(SlotValue("5", "지난 검사 결과 물어보기"), SlotValue("6", "다음 예약 날짜 확인하기")),
            Slot.DOCTOR to listOf(SlotValue("7", "다음에 검사 결과를 함께 보자고 하셨어요")),
            Slot.MED_CHANGE to listOf(SlotValue("8", "그대로라고 하셨어요")),
            Slot.NEXT_CHECK to listOf(SlotValue("9", "검사 날짜 다시 확인하기")),
        ), appointment = Appointment(verbatim = "다음 달쯤", needsConfirmation = true), checkedQuestions = setOf("5"))
    return ViewState(Archive(consent = Consent(1, 2), visits = listOf(visit.copy(complete = true)),
        session = Session(phase = phase, visit = visit, slotIndex = 1,
            prompt = if (phase == Phase.PRE) Prompt(1, AskKind.SLOT, "언제부터 그러셨어요?", Slot.DURATION) else null)),
        gear = Gear.P, restricted = false, voice = if (phase == Phase.PRE) VoiceState.LISTENING else VoiceState.IDLE, demo = true)
}

@Preview(name = "Split · light", widthDp = 840, heightDp = 1184, showBackground = true)
@Preview(name = "Full · dark", widthDp = 2560, heightDp = 1440, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ParkedPreview() { VisitScreen(previewState(), false) }

@Preview(name = "Driving · split", widthDp = 840, heightDp = 1184)
@Preview(name = "Driving · dark", widthDp = 1700, heightDp = 1184, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Driving · large text", widthDp = 840, heightDp = 1184, fontScale = 1.3f)
@Composable private fun DrivingPreview() { VisitScreen(previewState(Phase.PRE), true) }

@Preview(name = "Consent", widthDp = 840, heightDp = 1184)
@Composable private fun ConsentPreview() { VisitScreen(ViewState(gear = Gear.P, restricted = false), false) }

@Preview(name = "Settings · dark", widthDp = 840, heightDp = 1184, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun SettingsPreview() { VisitTheme { SettingsScreen(previewState()) } }

private fun completedPreviewState(): ViewState {
    val state = previewState(Phase.DONE)
    val visit = state.archive.session.visit!!.copy(complete = true)
    return state.copy(archive = state.archive.copy(consent = Consent(healthAt = 1),
        session = state.archive.session.copy(visit = visit, briefing = Templates.briefing(visit), rendezvous = Rendezvous.OUTSIDE)),
        notice = "시연: 진료 15분 경과 · Virtual Console에서 기어 D로 출발하세요.")
}

@Preview(name = "Completed · split · demo unpaired", widthDp = 840, heightDp = 1184)
@Preview(name = "Completed · full · dark", widthDp = 2560, heightDp = 1440, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun CompletedPreview() { VisitScreen(completedPreviewState(), false) }

@Preview(name = "Completed · driving", widthDp = 840, heightDp = 1184)
@Composable private fun CompletedDrivingPreview() { VisitScreen(completedPreviewState(), true) }

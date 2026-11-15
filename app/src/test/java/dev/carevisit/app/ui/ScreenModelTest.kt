package dev.carevisit.app.ui

import dev.carevisit.core.*
import org.junit.Assert.*
import org.junit.Test

class ScreenModelTest {
    @Test fun unknownAndMovingAreGlanceOnly() {
        assertTrue(ScreenModel.from(ViewState(), false).glance)
        assertTrue(ScreenModel.from(ViewState(gear = Gear.D, restricted = false), false).glance)
    }
    @Test fun platformRestrictionWins() {
        assertTrue(ScreenModel.from(ViewState(gear = Gear.P, restricted = false), true).glance)
        assertFalse(ScreenModel.from(ViewState(gear = Gear.P, restricted = false), false).glance)
    }
    @Test fun longReadbackNeverInGlance() {
        for (kind in listOf(AskKind.SHARE_BRIEFING, AskKind.SHARE_MEMO)) {
            val archive = Archive(session = Session(prompt = Prompt(1, kind, "개인 원문".repeat(100))))
            assertEquals("가족에게 보낼까요?", ScreenModel.from(ViewState(archive), true).question)
        }
    }
    @Test fun drivingQuestionDoesNotExposeLongPromptOrContinuityTranscript() {
        val secret = "지난번에 약을 바꾸셨는데 " + "개인 원문".repeat(100)
        for (kind in AskKind.entries) {
            val archive = Archive(session = Session(prompt = Prompt(1, kind, secret, Slot.CONTINUITY)))
            val question = ScreenModel.from(ViewState(archive), true).question
            assertTrue("$kind: $question", question.length <= 48)
            assertFalse(question.contains("개인 원문"))
        }
    }
    @Test fun speakingIsLabelledAsSpeakingNotAlwaysAsQuestion() {
        assertEquals("말하는 중", ScreenModel.from(ViewState(voice = VoiceState.SPEAKING), false).voice)
    }
}

package dev.carevisit.app.ui

import dev.carevisit.core.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises the UI's actual touch=true contract against the pure service state machine. */
class GlanceInteractionTest {
    private fun machine(kind: AskKind = AskKind.SLOT): VisitMachine {
        val initial = Archive(
            consent = if (kind == AskKind.HEALTH) Consent() else Consent(1),
            session = Session(phase = if (kind == AskKind.HEALTH) Phase.HEALTH_CONSENT else Phase.PRE,
                visit = Visit("ui-test", Hospital("○○병원"), 1000),
                prompt = Prompt(11, kind, "원래 질문", Slot.SYMPTOM)),
        )
        return VisitMachine(initial, 1000).also { it.handle(Event.Vehicle(Gear.D, 10.0, true, false), 1000, 1000) }
    }
    @Test fun visibleDrivingSkipActuallyAdvancesTheCurrentQuestion() {
        val machine = machine()
        val screen = ScreenModel.from(machine.view, true)
        val skip = screen.answers.single { it.label == "넘어가기" }
        assertTrue(skip.enabled)
        machine.handle(Event.Answer(screen.promptId!!, skip.answer, touch = true), 1001, 1001)
        assertEquals("An enabled driving button must not silently do nothing", 1, machine.archive.session.slotIndex)
    }
    @Test fun drivingTouchCannotGrantHealthConsent() {
        val machine = machine(AskKind.HEALTH)
        val screen = ScreenModel.from(machine.view, true)
        assertTrue(screen.answers.none { it.enabled })
        machine.handle(Event.Answer(screen.promptId!!, "네", touch = true), 1001, 1001)
        assertNull(machine.archive.consent.healthAt)
    }
    @Test fun oldDrivingButtonCannotAnswerANewPrompt() {
        val machine = machine()
        machine.handle(Event.Answer(10, "넘어가", touch = true), 1001, 1001)
        assertEquals(0, machine.archive.session.slotIndex)
        assertEquals(11L, machine.archive.session.prompt!!.id)
    }
}

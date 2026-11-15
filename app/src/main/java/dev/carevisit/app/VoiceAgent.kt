package dev.carevisit.app

import dev.carevisit.core.*
import dev.hmg.pleoskit.SpeechInput
import dev.hmg.pleoskit.SpeechOutput
import dev.hmg.pleoskit.SpeechFocus
import dev.hmg.pleoskit.NoSpeechFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VoiceAgent(private val output: SpeechOutput,private val input: SpeechInput,private val demo: Boolean,private val focus: SpeechFocus = NoSpeechFocus,private val status: (VoiceState)->Unit) : SpeechPort {
    private val lock=Mutex()
    @Volatile private var focusLost=false
    private fun begin() {
        focusLost=false
        check(focus.acquire {focusLost=true;output.stop();input.stop()}) {"speech focus unavailable"}
    }
    private suspend fun speak(text: String): Boolean {
        output.connect(); status(VoiceState.SPEAKING)
        // Long doctor answers are read completely in bounded TTS turns.
        for(chunk in text.chunked(160)) if(focusLost || !output.speak(chunk) || focusLost) return false
        return true
    }
    override suspend fun say(text: String): Boolean=lock.withLock { try { begin(); speak(text) } finally {focus.release();status(VoiceState.IDLE)} }
    override suspend fun ask(prompt: Prompt,onListening: ()->Unit): String?=lock.withLock {
        try {
            begin()
            check(speak(prompt.text)) {"speech interrupted"}
            status(VoiceState.PREPARING)
            if(demo) { onListening(); delay(1800); check(!focusLost) {"speech interrupted"}; DemoAnswers.answer(prompt) }
            else { input.connect(); input.listen(Policy.LISTEN_MS,onListening).also {check(!focusLost) {"speech interrupted"}} }
        } finally {focus.release();status(VoiceState.IDLE)}
    }
    override suspend fun listen(onListening: ()->Unit): String?=lock.withLock {
        try { begin(); if(demo) "병원가는길" else {input.connect(); input.listen(Policy.LISTEN_MS,onListening).also {check(!focusLost) {"speech interrupted"}}} } finally {focus.release();status(VoiceState.IDLE)}
    }
    override fun stop() { focusLost=true;output.stop(); input.stop();focus.release() }
}
/**
 * 시연용 대답. 에뮬레이터 Pleos STT(ON_DEVICE)는 띄어쓰기를 지킨 문장을 주고 끝 글자만 잘리는 일이 있었다
 * ("화장실 급해" → '화장실 급', CLAUDE.md STT). 그래서 띄어쓰기는 실제처럼 두고, 끝이 잘린 대답 하나('아파' → '아프')만 섞는다.
 * 메모에는 이 원문이 그대로 남으므로 의사에게 보여 줄 화면에서 읽을 수 있어야 한다.
 */
object DemoAnswers {
    fun answer(p: Prompt): String = when(p.kind) {
        AskKind.HEALTH,AskKind.PRE_OFFER,AskKind.POST_OFFER,AskKind.MED_CHANGED,AskKind.SHARE_MEMO,AskKind.SHARE_BRIEFING,AskKind.REMINDER,AskKind.ROUTE -> "네"
        AskKind.MORE_QUESTIONS -> "없어"
        AskKind.SLOT -> when(p.slot) {
            Slot.CONTINUITY -> "지난번이랑 비슷해"
            Slot.SYMPTOM -> "오른쪽 무릎이 좀 아프"
            Slot.DURATION -> "사흘 전부터"
            Slot.DETAIL -> "계단 올라갈 때 더 그래"
            Slot.MEDICATION -> "전에 먹던 약 그대로 먹어"
            Slot.QUESTION -> "산책은 해도 되는지 물어볼래"
            Slot.DOCTOR -> "선생님이 검사 결과는 다음 진료 때 같이 보자고 하셨어"
            Slot.MED_CHANGE -> "이번에는 약을 바꾸셨대 이름은 처방전에 있어"
            Slot.APPOINTMENT -> "2주 뒤 오전 10시"
            Slot.CAUTION -> "무리하지 말라고 하셨어"
            Slot.NEXT_CHECK -> "다음에는 검사 결과 물어보기"
            null -> "넘어가"
        }
    }
}

package dev.carevisit.app.ui

import dev.carevisit.core.*
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure UI policy. Glance receives no archive, transcript, notice or document model. */
object DriveLimits {
    const val QUESTION_CHARS = 48
    const val LABEL_CHARS = 12
    const val QUESTION_LINES = 3
    const val TOUCH_TARGETS = 3
    const val MIN_TEXT_SP = 28
    const val TOUCH_DP = 100
}
object UiCopy {
    const val DEMO = "시연: 미리 녹음한 대답"
    val TABS = listOf("오늘 동행", "진료 기록", "설정")
    val HONORIFICS = listOf("어머니", "아버지", "부모님", "보호자")
    val RETENTION = listOf(30, 90, 180, 365)
    val DEMO_STEPS = listOf("병원 가는 길", "인터뷰", "도착", "진료 끝", "브리핑", "보내기")
}
data class AnswerButton(val label: String, val answer: String, val enabled: Boolean)
data class MemoRow(val label: String, val text: String, val confirmation: Boolean = false)
data class QuestionItem(val id: String, val text: String, val checked: Boolean)
data class MemoModel(val hospital: String, val date: String, val rows: List<MemoRow>, val questions: List<QuestionItem>)
data class BriefingModel(val rows: List<MemoRow>, val approvedText: String)
data class HistoryEntry(val id: String, val date: String, val memo: MemoModel, val briefing: BriefingModel, val appointment: String?, val reminder: Boolean)
data class HospitalHistory(val hospital: String, val entries: List<HistoryEntry>)
data class RendezvousModel(val label: String, val detail: String)
data class DemoStep(val label: String, val current: Boolean, val enabled: Boolean, val complete: Boolean)
data class ConsentVisibility(val showHealth: Boolean, val canPair: Boolean, val canShare: Boolean)
data class ParkedModel(
    val memo: MemoModel?, val briefing: BriefingModel?, val history: List<HospitalHistory>,
    val rendezvous: RendezvousModel?, val consent: ConsentVisibility, val demoSteps: List<DemoStep>,
)
data class ScreenModel(
    val glance: Boolean, val title: String, val question: String, val progress: String, val voice: String,
    val promptId: Long?, val answers: List<AnswerButton>, val demoLabel: String?, val parked: ParkedModel?,
) {
    companion object {
        fun from(s: ViewState, platformRestricted: Boolean): ScreenModel {
            val glance = platformRestricted || !s.fullScreen
            val session = s.archive.session
            val prompt = session.prompt
            val touchAllowed = !glance || (s.gear != Gear.UNKNOWN && prompt?.kind != AskKind.HEALTH)
            val shareReadbackReady = !glance || prompt?.kind !in setOf(AskKind.SHARE_MEMO, AskKind.SHARE_BRIEFING) || s.voice == VoiceState.LISTENING
            val progress = if (session.phase in setOf(Phase.PRE, Phase.POST)) "${session.slotIndex.coerceIn(0, 4) + 1}/5" else ""
            return ScreenModel(
                glance = glance,
                title = when (session.phase) {
                    Phase.IDLE -> "진료를 함께 준비해요"
                    Phase.WAIT_SAFE -> "가는 길 · 진료 준비"
                    Phase.HEALTH_CONSENT -> "시작 전 · 보관 동의"
                    Phase.OFFER_PRE, Phase.PRE -> "가는 길 · 진료 메모"
                    Phase.MEMO -> "진료 메모 준비 완료"
                    Phase.AT_HOSPITAL -> "진료 중 · 다시 만나요"
                    Phase.OFFER_POST, Phase.POST -> "오는 길 · 진료 브리핑"
                    Phase.BRIEFING -> "진료 브리핑 정리 중"
                    Phase.SHARE -> "가족에게 전할 말씀"
                    Phase.REMINDER -> "다음 진료 준비"
                    Phase.DONE -> "오늘의 동행 기록"
                    Phase.RECOVERY -> "중단된 기록이 있어요"
                },
                question = if (glance) shortQuestion(session) else parkedQuestion(session),
                progress = progress,
                voice = when (s.voice) {
                    VoiceState.IDLE -> "음성 대기"
                    VoiceState.SPEAKING -> "말하는 중"
                    VoiceState.PREPARING -> "마이크 준비 중"
                    VoiceState.LISTENING -> "듣는 중"
                    VoiceState.UNAVAILABLE -> "음성 연결 확인 필요"
                },
                promptId = prompt?.id,
                answers = if (prompt == null) emptyList() else listOf(
                    AnswerButton("예", "네", touchAllowed && shareReadbackReady),
                    AnswerButton("아니요", "아니요", touchAllowed),
                    AnswerButton("넘어가기", "넘어가", touchAllowed),
                ),
                demoLabel = UiCopy.DEMO.takeIf { s.demo },
                parked = if (glance) null else parkedModel(s),
            )
        }
    }
}

private fun shortQuestion(session: Session): String = session.prompt?.let { p ->
    // A prompt can include a full readback, hospital name or previous transcript. Never truncate it into a glance.
    when (p.kind) {
        AskKind.HEALTH -> "보관 동의 안내를 들어 주세요"
        AskKind.PRE_OFFER -> "진료 때 드릴 말씀을 정리할까요?"
        AskKind.POST_OFFER -> "의사 선생님 말씀을 정리할까요?"
        AskKind.MORE_QUESTIONS -> "또 물어볼 것이 있으세요?"
        AskKind.MED_CHANGED -> "약이 바뀌었나요?"
        AskKind.SHARE_MEMO, AskKind.SHARE_BRIEFING -> "가족에게 보낼까요?"
        AskKind.REMINDER -> "다음 진료를 기억해 둘까요?"
        AskKind.ROUTE -> "저장한 병원으로 안내할까요?"
        AskKind.SLOT -> when (p.slot) {
            Slot.CONTINUITY -> "지난 진료 뒤로 어떠셨어요?"
            Slot.SYMPTOM -> "어디가 불편하세요?"
            Slot.DURATION -> "언제부터 그러셨어요?"
            Slot.DETAIL -> "어떨 때, 어떻게 불편하세요?"
            Slot.MEDICATION -> "드시는 약이나 바뀐 게 있나요?"
            Slot.QUESTION -> "꼭 물어볼 것이 있으세요?"
            Slot.DOCTOR -> "의사 선생님이 뭐라고 하셨어요?"
            Slot.MED_CHANGE -> "약이 어떻게 바뀌었나요?"
            Slot.APPOINTMENT -> "검사나 다음 예약이 있나요?"
            Slot.CAUTION -> "조심하라고 하신 게 있나요?"
            Slot.NEXT_CHECK -> "다음에 확인할 것이 있나요?"
            null -> "말씀으로 답해 주세요"
        }
    }
} ?: when (session.phase) {
    Phase.IDLE, Phase.DONE -> "음성으로 ‘병원 가는 길’이라고 말씀하세요"
    Phase.WAIT_SAFE -> "잠시 뒤 진료 준비를 시작해요"
    Phase.MEMO -> "진료 메모는 주차 후 확인하세요"
    Phase.AT_HOSPITAL -> "정문에서 다시 만나요"
    Phase.RECOVERY -> "주차 후 기록을 이어갈 수 있어요"
    Phase.BRIEFING -> "말씀하신 내용을 정리하고 있어요"
    else -> "말씀으로 기록을 남겨요"
}
private fun parkedQuestion(session: Session): String {
    val prompt = session.prompt ?: return shortQuestion(session)
    return when (prompt.kind) {
        AskKind.HEALTH -> "건강 정보를 이 기기에 보관할까요?"
        AskKind.SHARE_MEMO, AskKind.SHARE_BRIEFING -> "가족에게 보낼까요?"
        else -> prompt.text
    }
}
private fun parkedModel(s: ViewState): ParkedModel {
    val a = s.archive
    val health = a.consent.healthAt != null
    val family = health && a.consent.familyAt != null
    val visit = a.session.visit.takeIf { health }
    return ParkedModel(
        memo = visit?.let(::memoModel),
        briefing = visit?.takeIf { v -> a.session.briefing.isNotBlank() || v.slots.keys.any { it in POST_SLOTS } }?.let { briefingModel(it, a.session.briefing) },
        history = if (!health) emptyList() else a.visits.sortedByDescending { it.createdAt }.groupBy { it.hospital.name }.map { (name, visits) ->
            HospitalHistory(name, visits.map { v -> HistoryEntry(v.id, visitDay(v.createdAt), memoModel(v), briefingModel(v), v.appointment?.let(::appointmentLabel), v.reminder) })
        },
        rendezvous = if (health) rendezvous(s) else null,
        consent = ConsentVisibility(!health, health && !family, family && visit != null),
        demoSteps = if (s.demo) demoSteps(a.session.phase, family && a.session.briefing.isNotBlank()) else emptyList(),
    )
}
private val POST_SLOTS = setOf(Slot.DOCTOR, Slot.MED_CHANGE, Slot.APPOINTMENT, Slot.CAUTION, Slot.NEXT_CHECK)
fun memoModel(v: Visit) = MemoModel(
    hospital = v.hospital.name, date = visitDay(v.createdAt),
    rows = (listOf(Slot.SYMPTOM to "불편한 곳", Slot.DURATION to "언제부터", Slot.DETAIL to "양상", Slot.MEDICATION to "약") + if (v.text(Slot.CONTINUITY).isNotBlank()) listOf(Slot.CONTINUITY to "지난 진료 이후") else emptyList()).map { (slot, title) -> MemoRow(title, v.text(slot).ifBlank { "아직 기록 없음" }) },
    questions = v.slots[Slot.QUESTION].orEmpty().map { QuestionItem(it.utteranceId, it.verbatim, it.utteranceId in v.checkedQuestions) },
)
fun briefingModel(v: Visit, approvedText: String = "") = BriefingModel(
    rows = listOf(
        MemoRow("의사 말씀", v.text(Slot.DOCTOR).ifBlank { "아직 기록 없음" }),
        MemoRow("약 변경", v.text(Slot.MED_CHANGE).ifBlank { "아직 기록 없음" }),
        MemoRow("다음 예약", v.appointment?.let { Dates.display(it) } ?: v.text(Slot.APPOINTMENT).ifBlank { "아직 기록 없음" }, v.appointment?.needsConfirmation == true || (v.appointment == null && v.text(Slot.APPOINTMENT).isNotBlank())),
        MemoRow("주의", v.text(Slot.CAUTION).ifBlank { "아직 기록 없음" }),
        MemoRow("다음에 확인할 것", v.text(Slot.NEXT_CHECK).ifBlank { "아직 기록 없음" }),
    ),
    approvedText = approvedText.ifBlank { Templates.briefing(v) },
)
fun appointmentLabel(a: Appointment): String = Dates.display(a) + if (a.needsConfirmation && !Dates.display(a).contains("확인 필요")) " · 확인 필요" else ""
fun visitDay(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

/** Typed service state survives unrelated notices without losing the rendezvous reply. */
private fun rendezvous(s: ViewState): RendezvousModel? {
    val session = s.archive.session
    return when (session.rendezvous) {
        Rendezvous.OUTSIDE -> RendezvousModel("나왔어 · 수신", "${s.archive.settings.honorific}가 정문에서 기다려요")
        Rendezvous.PARKING -> RendezvousModel("주차 후 합류", "가족 화면에서 ‘나왔어’를 보내면 알려 드려요")
        Rendezvous.AT_GATE -> if (session.droppedOff) RendezvousModel("정문에서 내리셨어요", "주차 후 합류할 수 있어요") else RendezvousModel("정문 도착", "진료 메모를 챙겨 주세요")
        Rendezvous.NONE -> null
    }
}
fun demoSteps(phase: Phase, canShareBriefing: Boolean = false): List<DemoStep> {
    val current = when (phase) {
        Phase.IDLE, Phase.WAIT_SAFE -> 0
        Phase.HEALTH_CONSENT, Phase.OFFER_PRE, Phase.PRE, Phase.RECOVERY -> 1
        Phase.MEMO -> 2
        Phase.AT_HOSPITAL -> 3
        Phase.OFFER_POST, Phase.POST, Phase.BRIEFING -> 4
        Phase.SHARE, Phase.REMINDER, Phase.DONE -> 5
    }
    return UiCopy.DEMO_STEPS.mapIndexed { index, label -> DemoStep(label, index == current, index == current && phase != Phase.BRIEFING && (phase != Phase.DONE || canShareBriefing), index < current) }
}

object SettingsInput {
    fun validBaseUrl(text: String): Boolean {
        val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https") && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty() in setOf("", "/") && (uri.port == -1 || uri.port in 1..65535)
    }
    fun hospital(name: String): Hospital? = name.trim().takeIf { it.isNotBlank() && it.length <= 40 && it.none(Char::isISOControl) }?.let { Hospital(it) }
    // An identification hint, never a replacement for the 128-bit access token.
    fun pairingCode(token: String): String = if (token.matches(Regex("[0-9a-f]{32}"))) token.takeLast(8).uppercase().chunked(4).joinToString("-") else "연결 안 됨"
    fun familyUrl(base: String, token: String): String? = if (validBaseUrl(base) && token.matches(Regex("[0-9a-f]{32}"))) "${base.trim().trimEnd('/')}/#$token" else null
}

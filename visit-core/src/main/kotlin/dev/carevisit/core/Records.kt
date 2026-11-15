package dev.carevisit.core

import kotlinx.serialization.Serializable

@Serializable data class Hospital(val name: String, val latitude: Double? = null, val longitude: Double? = null) {
    val routable: Boolean get() = latitude?.let { it.isFinite() && it in -90.0..90.0 && it != 0.0 } == true && longitude?.let { it.isFinite() && it in -180.0..180.0 && it != 0.0 } == true
}
@Serializable data class Utterance(val id: String, val text: String, val at: Long)
@Serializable enum class Slot { CONTINUITY, SYMPTOM, DURATION, DETAIL, MEDICATION, QUESTION, DOCTOR, MED_CHANGE, APPOINTMENT, CAUTION, NEXT_CHECK }
@Serializable data class SlotValue(val utteranceId: String, val verbatim: String, val normalized: String? = null)
@Serializable data class Appointment(val date: String? = null, val time: String? = null, val needsConfirmation: Boolean = true, val verbatim: String = "", val utteranceId: String = "")
@Serializable data class Visit(
    val id: String, val hospital: Hospital, val createdAt: Long,
    val utterances: List<Utterance> = emptyList(), val slots: Map<Slot,List<SlotValue>> = emptyMap(),
    val appointment: Appointment? = null, val reminder: Boolean = false,
    val checkedQuestions: Set<String> = emptySet(), val complete: Boolean = false,
) {
    fun text(slot: Slot): String = slots[slot].orEmpty().joinToString(" / ") { it.verbatim }
    fun traceable(): Boolean = slots.values.flatten().all { value -> utterances.any { it.id == value.utteranceId && it.text == value.verbatim } } && (appointment == null || utterances.any { it.id == appointment.utteranceId && it.text == appointment.verbatim })
}
@Serializable data class Consent(val healthAt: Long? = null, val familyAt: Long? = null, val version: Int = 1)
@Serializable data class Settings(
    val honorific: String = "어머니", val retentionDays: Int = 180, val polish: Boolean = false,
    val hospitals: List<Hospital> = emptyList(), val relayUrl: String = "http://10.0.2.2:8765", val familyToken: String = "",
)
@Serializable enum class Phase { IDLE, WAIT_SAFE, HEALTH_CONSENT, OFFER_PRE, PRE, MEMO, AT_HOSPITAL, OFFER_POST, POST, BRIEFING, SHARE, REMINDER, DONE, RECOVERY }
@Serializable enum class Rendezvous { NONE, AT_GATE, PARKING, OUTSIDE }
@Serializable enum class AskKind { HEALTH, PRE_OFFER, SLOT, MORE_QUESTIONS, MED_CHANGED, POST_OFFER, SHARE_MEMO, SHARE_BRIEFING, REMINDER, ROUTE }
@Serializable data class Prompt(val id: Long, val kind: AskKind, val text: String, val slot: Slot? = null, val retry: Int = 0)
@Serializable data class Session(
    val phase: Phase = Phase.IDLE, val visit: Visit? = null, val slotIndex: Int = 0,
    val arrivedAt: Long? = null, val rendezvous: Rendezvous = Rendezvous.NONE, val droppedOff: Boolean = false, val parkingMode: Boolean = false,
    val prompt: Prompt? = null, val resumePhase: Phase? = null, val briefing: String = "", val offered: Boolean = false,
)
@Serializable data class Archive(val schema: Int = 1, val consent: Consent = Consent(), val settings: Settings = Settings(), val visits: List<Visit> = emptyList(), val session: Session = Session())
enum class Gear { UNKNOWN, P, D, OTHER }
enum class VoiceState { IDLE, SPEAKING, PREPARING, LISTENING, UNAVAILABLE }
data class ViewState(val archive: Archive = Archive(), val restricted: Boolean = true, val gear: Gear = Gear.UNKNOWN, val voice: VoiceState = VoiceState.IDLE, val notice: String = "", val demo: Boolean = false) {
    val fullScreen: Boolean get() = !restricted && gear == Gear.P
}
object Policy {
    const val SAFE_DRIVE_MS = 60_000L
    const val STAY_MS = 15 * 60_000L
    const val LISTEN_MS = 12_000L
    const val POLISH_MS = 8_000L
    const val HEALTH_LINE = "진료 준비를 위해 말씀하신 건강 정보를 이 기기에만 기본 180일 보관합니다. 언제든 전체 삭제할 수 있어요. 민감정보 수집과 보관에 동의하시나요?"
    fun healthLine(days: Int) = HEALTH_LINE.replace("기본 180일", "설정한 ${days.coerceIn(1,365)}일")
    const val DISCOMFORT_LINE = "불편이 심하면 진료 전에 병원에 바로 말씀하세요"
    const val EMERGENCY_LINE = "응급 도움을 원하시면 119에 직접 연락하세요. 이 앱은 전화하거나 상태를 판단하지 않습니다."
    const val FAMILY_LINE = "가족 연결을 켜면 확인한 메모와 브리핑, 정문·주차 상태만 연결된 가족 화면에 보냅니다. 위치와 대화 원본은 보내지 않습니다. 가족 공유에 별도로 동의합니다."
    fun purge(a: Archive, now: Long): Archive {
        val cutoff = now - a.settings.retentionDays.coerceIn(1,365) * 86_400_000L
        return a.copy(visits = a.visits.filter { it.createdAt > cutoff }, session = a.session.takeIf { it.visit == null || it.visit.createdAt > cutoff } ?: Session())
    }
}
object TripRules {
    private val words = listOf("병원","의원","의료원","클리닉","한의원","치과","보건소")
    fun isHospital(name: String, saved: List<Hospital>) = words.any { it in name } || saved.any { Korean.compact(it.name) == Korean.compact(name) }
}

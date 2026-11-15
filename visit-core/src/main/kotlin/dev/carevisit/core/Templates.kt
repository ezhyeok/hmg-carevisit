package dev.carevisit.core

object Templates {
    val pre = listOf(Slot.SYMPTOM,Slot.DURATION,Slot.DETAIL,Slot.MEDICATION,Slot.QUESTION)
    val post = listOf(Slot.DOCTOR,Slot.MED_CHANGE,Slot.APPOINTMENT,Slot.CAUTION,Slot.NEXT_CHECK)
    val labels = mapOf(Slot.CONTINUITY to "지난 진료 이후",Slot.SYMPTOM to "불편한 곳",Slot.DURATION to "언제부터",Slot.DETAIL to "어떨 때 / 어떻게",Slot.MEDICATION to "드시는 약",Slot.QUESTION to "물어볼 것",Slot.DOCTOR to "의사 말씀",Slot.MED_CHANGE to "약 변경",Slot.APPOINTMENT to "다음 검사·예약",Slot.CAUTION to "주의 말씀",Slot.NEXT_CHECK to "다음에 확인할 것")
    val questions = mapOf(Slot.SYMPTOM to "어디가 불편하세요?",Slot.DURATION to "언제부터요?",Slot.DETAIL to "어떨 때 더 그러세요? 어떻게 아프세요?",Slot.MEDICATION to "요즘 드시는 약이나 바뀐 게 있으세요?",Slot.QUESTION to "의사 선생님께 꼭 물어볼 거 있으세요?",Slot.DOCTOR to "의사 선생님이 뭐라고 하셨어요?",Slot.MED_CHANGE to "바뀐 약에 대해 들으신 말씀을 그대로 알려 주세요.",Slot.APPOINTMENT to "검사나 다음 예약이 있나요? 날짜와 시간을 말씀해 주세요.",Slot.CAUTION to "조심하라고 하신 거 있나요?",Slot.NEXT_CHECK to "다음 진료 때 확인할 것이 있나요?")
    private fun value(v: Visit,s: Slot) = v.text(s).ifBlank { "기록 없음" }
    fun memo(v: Visit): String = "${v.hospital.name} 진료 메모\n" + (listOf(Slot.CONTINUITY)+pre).filter { it != Slot.CONTINUITY || v.text(it).isNotBlank() }.joinToString("\n") { "${labels[it]}: ${value(v,it)}" }
    fun briefing(v: Visit): String = "오늘 ${v.hospital.name} 진료 — " + post.joinToString(", ") { s -> "${labels[s]}: ${if(s==Slot.APPOINTMENT && v.appointment!=null) Dates.display(v.appointment) else value(v,s)}" }
}

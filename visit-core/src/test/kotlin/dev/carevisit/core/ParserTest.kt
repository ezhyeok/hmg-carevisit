package dev.carevisit.core

import java.time.LocalDate
import kotlin.test.*

class ParserTest {
    @Test fun noisyYes() { listOf("응", "네", "어", "그래", "네 할게", "응그래").forEach { assertEquals(Intent.YES, Korean.intent(it)) } }
    @Test fun noisyNo() { listOf("아니", "아니요", "없어", "없어요", "아니 괜찮").forEach { assertEquals(Intent.NO, Korean.intent(it)) } }
    @Test fun skips() { listOf("넘어가", "넘 어 가", "다음", "몰라").forEach { assertEquals(Intent.SKIP, Korean.intent(it)) } }
    @Test fun noBroadPrefixConsent() { listOf("네시간", "어지러워", "그래도 안 해", "응급실 가자").forEach { assertNotEquals(Intent.YES, Korean.intent(it)) } }
    @Test fun repeatAndManual() { assertEquals(Intent.MORE, Korean.intent("또있어")); assertEquals(Intent.VISIT, Korean.intent("병원가는길")) }
    @Test fun durations() {
        mapOf("사흘 전부터" to "P3D", "이틀" to "P2D", "일주일" to "P7D", "열흘" to "P10D", "한 달" to "P1M", "지난달" to "P1M", "어제부터" to "P1D").forEach { (a,b) -> assertEquals(b, Korean.duration(a)) }
    }
    @Test fun bodyWords() { assertEquals(setOf("무릎", "허리"), Korean.bodyWords("무릎이랑 허리가 아프")) }
    @Test fun onlyLiteralEmergency() { assertEquals(Safety.EMERGENCY, Korean.safety("119")); assertEquals(Safety.EMERGENCY, Korean.safety("응급실 가자")); assertEquals(Safety.DISCOMFORT, Korean.safety("가슴이 아파")); assertEquals(Safety.DISCOMFORT, Korean.safety("숨이차")); assertEquals(Safety.NONE, Korean.safety("무릎 아파")) }
    @Test fun relativeDate() { assertEquals("2026-11-29", Dates.parse("2주 뒤", LocalDate.of(2026,11,15)).date) }
    @Test fun koreanRelativeDate() { assertEquals("2026-12-01", Dates.parse("이틀 뒤", LocalDate.of(2026,11,29)).date) }
    @Test fun monthRollover() { assertEquals("2027-01-03", Dates.parse("다음 달 3일", LocalDate.of(2026,12,15)).date) }
    @Test fun absoluteTimeWeekday() { val a = Dates.parse("12월 10일 오전 10시", LocalDate.of(2026,11,15)); assertEquals("2026-12-10", a.date); assertEquals("10:00",a.time); assertTrue(Dates.display(a).contains("목")) }
    @Test fun nextYear() { assertEquals("2027-01-02", Dates.parse("1월 2일", LocalDate.of(2026,12,31)).date) }
    @Test fun ambiguity() { listOf("다음에", "3일", "다음 달", "12월 32일", "오전 15시", "12월 10일 10시").forEach { assertTrue(Dates.parse(it, LocalDate.of(2026,11,15)).needsConfirmation, it) } }
    @Test fun noClampedInvalidDate() { assertNull(Dates.parse("다음 달 31일", LocalDate.of(2026,1,10)).date) }
    @Test fun leapDay() { assertEquals("2028-02-29",Dates.parse("2028년 2월 29일",LocalDate.of(2026,11,15)).date) }
    @Test fun polishAcceptsOnlySafeLayoutChanges() { assertTrue(PolishGuard.accept("무릎: 사흘 전부터\n약: 기존 약", "무릎: 사흘 전부터. 약: 기존 약")) }
    @Test fun polishRejectsNewMedicalMeaning() { listOf("무릎: 심각합니다", "허리: 사흘 전부터", "무릎: 3일, 아스피린 2정", "무릎: 괜찮습니다").forEach { assertFalse(PolishGuard.accept("무릎: 사흘 전부터", it)) } }
    @Test fun polishRejectsOmissionNegationAndLongText() { assertFalse(PolishGuard.accept("통증 없음", "통증")); assertFalse(PolishGuard.accept("약 1정", "약 2정")); assertFalse(PolishGuard.accept("약 1정", "약 1정".repeat(4))) }
    @Test fun hospitalKeywordsAndSaved() { listOf("병원","의원","의료원","클리닉","한의원","치과","보건소").forEach { assertTrue(TripRules.isHospital("○○$it",emptyList())) }; assertTrue(TripRules.isHospital("정기 방문처", listOf(Hospital("정기 방문처")))); assertFalse(TripRules.isHospital("공원",emptyList())) }
}

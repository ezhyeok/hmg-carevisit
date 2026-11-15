package dev.carevisit.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Intent { YES, NO, SKIP, MORE, VISIT, STOP, UNKNOWN }
enum class Safety { NONE, DISCOMFORT, EMERGENCY }
object Korean {
    fun compact(text: String) = text.replace(Regex("[\\s,.!?。]"), "")
    fun intent(text: String?): Intent {
        val t = compact(text.orEmpty())
        return when {
            t in setOf("응","네","예","어","그래","응그래","네할게","네할게요","좋아","동의","동의해","동의합니다","보내","보내줘") -> Intent.YES
            t in setOf("아니","아니요","아뇨","없어","없어요","아니괜찮","아니괜찮아","안해","싫어") -> Intent.NO
            t in setOf("넘어가","넘어가요","다음","몰라","모르겠어","모르겠어요") -> Intent.SKIP
            t in setOf("또있어","또있어요","하나더") -> Intent.MORE
            t in setOf("병원가는길","병원가는중","병원가요") -> Intent.VISIT
            t in setOf("그만","그만해","중단","멈춰") -> Intent.STOP
            else -> Intent.UNKNOWN
        }
    }
    fun duration(text: String): String? {
        val t = compact(text)
        val tokens = linkedMapOf("일주일" to "P7D","사흘" to "P3D","이틀" to "P2D","열흘" to "P10D","한달" to "P1M","지난달" to "P1M","어제" to "P1D")
        return tokens.entries.firstOrNull { it.key in t }?.value ?: Regex("(\\d+)(일|주|개월)").find(t)?.let { "P${it.groupValues[1]}${mapOf("일" to "D","주" to "W","개월" to "M")[it.groupValues[2]]}" }
    }
    val body = setOf("머리","목","어깨","팔","손","손목","가슴","배","허리","등","다리","무릎","발","발목","눈","귀","코","입","치아","잇몸","심장","폐","위","간","피부")
    fun bodyWords(text: String) = body.filterTo(mutableSetOf()) { it in text }
    fun safety(text: String): Safety {
        val t = compact(text)
        return when {
            t == "119" || t in setOf("119불러","119불러줘","119에전화해","응급실가자","응급실가줘") -> Safety.EMERGENCY
            "가슴" in t && ("아파" in t || "아프" in t) || "숨이차" in t || "숨차" in t -> Safety.DISCOMFORT
            else -> Safety.NONE
        }
    }
}
object Dates {
    fun parse(text: String, today: LocalDate): Appointment {
        val t = text.filterNot { it.isWhitespace() }
        var date: LocalDate? = null
        var time: LocalTime? = null
        var ambiguous = false
        try {
            val absolute = Regex("(?:(\\d{4})년)?(\\d{1,2})월(\\d{1,2})일").find(t)
            val nextMonth = Regex("다음달(\\d{1,2})일").find(t)
            val weekdayNames=listOf("월요일","화요일","수요일","목요일","금요일","토요일","일요일")
            val nextWeekday=weekdayNames.indexOfFirst { "다음주$it" in t }
            val relative = Regex("(\\d+)(일|주|달|개월)(뒤|후)").find(t)
            date = when {
                absolute != null -> {
                    val y = absolute.groupValues[1].toIntOrNull()
                    var d = LocalDate.of(y ?: today.year,absolute.groupValues[2].toInt(),absolute.groupValues[3].toInt())
                    if (y == null && d < today) d = LocalDate.of(today.year+1,absolute.groupValues[2].toInt(),absolute.groupValues[3].toInt())
                    d
                }
                nextMonth != null -> today.plusMonths(1).withDayOfMonth(nextMonth.groupValues[1].toInt())
                nextWeekday>=0 -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1).plusDays(nextWeekday.toLong())
                relative != null -> {
                    val n = relative.groupValues[1].toLong()
                    when(relative.groupValues[2]) { "주" -> today.plusWeeks(n); "달","개월" -> today.plusMonths(n); else -> today.plusDays(n) }
                }
                t.startsWith("이틀뒤") || t.startsWith("이틀후") -> today.plusDays(2)
                t.startsWith("사흘뒤") || t.startsWith("사흘후") -> today.plusDays(3)
                t.startsWith("일주일뒤") || t.startsWith("일주일후") -> today.plusWeeks(1)
                t.startsWith("한달뒤") || t.startsWith("한달후") -> today.plusMonths(1)
                t.startsWith("내일") -> today.plusDays(1)
                else -> null
            }
            val dateCandidates = Regex("(?:(\\d{4})년)?(\\d{1,2})월(\\d{1,2})일|다음달\\d{1,2}일|\\d+(?:일|주|달|개월)(?:뒤|후)|(?:이틀|사흘|일주일|한달)(?:뒤|후)|내일|다음주[월화수목금토일]요일").findAll(t).count()
            if(dateCandidates>1 || Regex("\\d{1,2}일").findAll(t).count()>1 || listOf("아니","또는","혹은").any { it in t }) ambiguous=true
            val tm = Regex("(오전|오후)(\\d{1,2})시(?:(\\d{1,2})분)?(반)?").find(t)
            val multipleTimes=Regex("\\d{1,2}시").findAll(t).count()>1 || Regex("오전|오후").findAll(t).count()>1
            if (tm != null) {
                val hour = tm.groupValues[2].toInt()
                require(hour in 1..12)
                val half=tm.groupValues[4].isNotEmpty()
                val minutes=Regex("\\d{1,2}분").findAll(t).count()
                if(multipleTimes || minutes>1 || half && minutes>0) ambiguous=true
                else time = LocalTime.of(hour % 12 + if(tm.groupValues[1] == "오후") 12 else 0, if(half) 30 else tm.groupValues[3].toIntOrNull() ?: 0)
            } else if ("시" in t) ambiguous = true
            if (date != null && date < today) ambiguous = true
            val spokenWeekday=weekdayNames.indexOfFirst { it in t }
            if(date!=null && spokenWeekday>=0 && date.dayOfWeek.value!=spokenWeekday+1) ambiguous=true
        } catch (_: Exception) { ambiguous = true }
        return Appointment(date?.toString(),time?.format(DateTimeFormatter.ofPattern("HH:mm")), date == null || ambiguous, text)
    }
    fun display(a: Appointment): String {
        if (a.needsConfirmation) return "${a.verbatim} (확인 필요)"
        val d = a.date?.let(LocalDate::parse) ?: return "${a.verbatim} (확인 필요)"
        return d.format(DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN)) + (a.time?.let { " " + LocalTime.parse(it).format(DateTimeFormatter.ofPattern("a h시 mm분", Locale.KOREAN)) } ?: " (시간 확인 필요)")
    }
}
/** Conservative allowlist: polish may only alter whitespace. This also rules out
 * diagnosis, removed negations, novel drug names and reordered doses that token checks alone miss. */
object PolishGuard {
    private fun lexical(s: String) = Regex("[\\p{L}\\p{N}]+").findAll(s).map { it.value }.toList()
    fun accept(source: String, output: String): Boolean = output.isNotBlank() && output.length <= source.length * 1.5 && source.filterNot { it.isWhitespace() } == output.filterNot { it.isWhitespace() } && lexical(source) == lexical(output) && Korean.bodyWords(output).all { it in Korean.bodyWords(source) }
}

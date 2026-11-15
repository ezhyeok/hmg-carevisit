package dev.carevisit.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One mailbox owns all state. A small disk transaction completes before its external effects.
 * Slow voice, route, relay and polish calls run as cancellable children and return correlated events. */
class VisitCore(
    private val repository: Repository,private val speech: SpeechPort,private val family: FamilyLink,
    private val polisher: Polisher,private val navigator: Navigator,
    private val wall: ()->Long,private val elapsed: ()->Long,
) {
    private val mailbox=Channel<Event>(Channel.UNLIMITED)
    private val mutable=MutableStateFlow(ViewState())
    val state: StateFlow<ViewState> = mutable
    fun send(e: Event) { mailbox.trySend(e) }
    private fun persistent(a: Archive): Archive = if(a.consent.healthAt!=null) a else
        a.copy(visits=emptyList(),session=Session(),settings=a.settings.copy(hospitals=emptyList()))
    suspend fun run(): Unit = supervisorScope {
        var machine: VisitMachine
        var diskFailed=false
        try { machine=VisitMachine(repository.load(),wall()) }
        catch(_: Exception) { machine=VisitMachine(Archive(),wall()); diskFailed=true }
        var generation=0L
        var voiceJob: Job?=null
        val jobs=mutableListOf<Job>()
        val voiceLock=Mutex()
        val networkLock=Mutex()
        fun dispatch(effect: Effect,settings: Settings,gen: Long) {
            if(effect==Effect.StopVoice) { voiceJob?.cancel(); speech.stop(); return }
            val job=launch {
                try {
                    when(effect) {
                        is Effect.Ask -> voiceLock.withLock {
                            send(Event.Voice(VoiceState.SPEAKING))
                            val heard=speech.ask(effect.prompt) { if(gen==generation) send(Event.Voice(VoiceState.LISTENING)) }
                            if(gen==generation) { send(Event.Voice(VoiceState.IDLE)); send(Event.Answer(effect.prompt.id,heard)) }
                        }
                        is Effect.Say -> voiceLock.withLock { speech.say(effect.text); Unit }
                        Effect.ListenCommand -> voiceLock.withLock { val heard=speech.listen { send(Event.Voice(VoiceState.LISTENING)) }; if(gen==generation) {send(Event.Voice(VoiceState.IDLE)); send(Event.CommandHeard(heard))} }
                        is Effect.Send -> networkLock.withLock { if(gen==generation) {family.send(settings,effect.message); send(Event.Notice("가족 화면에 전달했어요."))} }
                        Effect.RevokeFamily -> networkLock.withLock { family.revoke(settings) }
                        is Effect.Polish -> send(Event.Polished(effect.visitId,effect.source,safePolish(polisher,effect.source)))
                        is Effect.Route -> { navigator.route(effect.hospital) }
                        Effect.StopVoice -> Unit
                    }
                } catch(e: CancellationException) { throw e }
                catch(_: Exception) { if(gen==generation) send(Event.Notice(when(effect) { is Effect.Send -> "가족 연결 실패 · 확인 후 다시 공유해 주세요."; Effect.RevokeFamily -> "기기 삭제 완료 · 가족 연결 삭제는 실패했어요. relay는 24시간 뒤 만료됩니다."; is Effect.Ask,Effect.ListenCommand -> "음성을 사용할 수 없어요. 주차 후 다시 듣기를 눌러 주세요."; else -> "연결을 확인해 주세요." })) }
            }
            if(effect is Effect.Ask || effect==Effect.ListenCommand) voiceJob=job
            jobs.removeAll { it.isCompleted }; jobs+=job
        }
        var bootPending=true
        val timer=launch { while(isActive) { delay(1_000); send(Event.Tick) } }
        try {
            while(isActive) {
                val event=if(bootPending) {bootPending=false;Event.Boot} else mailbox.receive()
                if(diskFailed && event !in listOf(Event.DeleteAll,Event.Withdraw)) {
                    val message="기록 저장소를 읽거나 저장할 수 없어요. 주차 후 전체 삭제로 초기화하세요."
                    // Still let the UI unlock deletion in P, but never run domain transitions/effects.
                    mutable.value=if(event is Event.Vehicle) ViewState(restricted=event.restricted,gear=event.gear,notice=message) else mutable.value.copy(notice=message)
                    continue
                }
                val before=machine.archive
                if(event==Event.Withdraw || event==Event.DeleteAll || event is Event.FamilyConsent && !event.enabled || event is Event.Configure && event.settings.relayUrl!=before.settings.relayUrl) {
                    generation++; jobs.forEach { it.cancel() }; speech.stop()
                }
                val effects=machine.handle(event,wall(),elapsed())
                try {
                    if(before!=machine.archive || event==Event.DeleteAll || event==Event.Withdraw) repository.save(persistent(machine.archive))
                    if(event==Event.DeleteAll || event==Event.Withdraw) diskFailed=false
                } catch(e: CancellationException) { throw e }
                catch(_: Exception) {
                    generation++; jobs.forEach {it.cancel()}; speech.stop()
                    machine=VisitMachine(before,wall()); diskFailed=true
                    mutable.value=ViewState(notice="기록 저장 실패 · 인터뷰를 멈췄어요. 저장 공간을 확인하세요.")
                    continue
                }
                mutable.value=machine.view
                effects.forEach { dispatch(it,if(it==Effect.RevokeFamily) before.settings else machine.archive.settings,generation) }
            }
        } finally { timer.cancel(); jobs.forEach { it.cancel() }; speech.stop() }
    }
}

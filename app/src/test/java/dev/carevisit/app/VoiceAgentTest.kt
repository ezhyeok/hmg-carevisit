package dev.carevisit.app

import dev.carevisit.core.*
import dev.hmg.pleoskit.SpeechInput
import dev.hmg.pleoskit.SpeechOutput
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceAgentTest {
    class Output(var completed:Boolean=true):SpeechOutput {
        val spoken=mutableListOf<String>()
        override suspend fun connect() {}
        override suspend fun speak(text:String):Boolean {spoken+=text;return completed}
        override fun stop() {}
    }
    class Input:SpeechInput {
        var called=false;var ready=CompletableDeferred<Unit>()
        override suspend fun connect() {}
        override suspend fun listen(timeoutMs:Long,onMicOpen:()->Unit):String? {called=true;ready.await();onMicOpen();return "무릎이 아프"}
        override fun stop() {}
    }
    @Test fun interruptedTtsNeverOpensMicrophone()=runTest {
        val input=Input();val a=VoiceAgent(Output(false),input,false){}
        try {a.ask(Prompt(1,AskKind.SLOT,"어디가 불편하세요?",Slot.SYMPTOM)){};fail("must report interrupted speech")} catch(_: IllegalStateException) {}
        assertFalse(input.called)
    }
    @Test fun listeningOnlyAfterOnReady()=runTest {
        val input=Input();var listening=false;val a=VoiceAgent(Output(),input,false){}
        val job=async{a.ask(Prompt(1,AskKind.SLOT,"질문",Slot.SYMPTOM)){listening=true}}
        runCurrent();assertFalse(listening);input.ready.complete(Unit);runCurrent();assertTrue(listening);assertEquals("무릎이 아프",job.await())
    }
    @Test fun demoStillSpeaksRealQuestionAndDoesNotUseMicrophone()=runTest {
        val out=Output();val input=Input();val a=VoiceAgent(out,input,true){}
        val job=async{a.ask(Prompt(1,AskKind.SLOT,"언제부터요?",Slot.DURATION)){}}
        runCurrent();assertEquals(listOf("언제부터요?"),out.spoken);assertFalse(job.isCompleted);advanceTimeBy(1801);assertEquals("사흘 전부터",job.await());assertFalse(input.called)
    }
    @Test fun longMemoIsReadCompletely()=runTest {val out=Output();val a=VoiceAgent(out,Input(),false){};val text="검사 결과 말씀 ".repeat(100);a.say(text);assertEquals(text,out.spoken.joinToString(""));assertTrue(out.spoken.all{it.length<=160})}
}

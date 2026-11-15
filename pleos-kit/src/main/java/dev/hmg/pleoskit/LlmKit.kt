package dev.hmg.pleoskit

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.ResultListener
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Optional ON_DEVICE punctuation-only request; never blocks a voice or vehicle thread.
 * No server registration, no secrets. Each request has its own listener and SDK instance. */
class LlmKit(context: Context) {
    private val appContext=context.applicationContext
    suspend fun punctuation(source: String): String? {
        val current=AtomicReference<LLM?>()
        return try {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine<String?> { cont ->
                    val done=AtomicBoolean(false)
                    fun finish(value: String?) { if(done.compareAndSet(false,true)) cont.resume(value) }
                    SdkThreads.llm.executor.execute {
                        if(!cont.isActive) return@execute
                        try {
                            val sdk=LLM(appContext,Mode.ON_DEVICE); current.set(sdk); sdk.initialize()
                            sdk.generateContent("다음 기록의 모든 단어와 순서를 그대로 유지하세요. 띄어쓰기와 문장부호만 정리하고 기록만 출력하세요. 내용 추가, 삭제, 판단 금지.\n"+source,object: ResultListener {
                                override fun onResponse(response: String,completed: Boolean) { if(completed) finish(response) }
                                override fun onError(reason: String) { finish(null) }
                            })
                        } catch(_: Exception) { finish(null) }
                    }
                }
            }
        } finally { SdkThreads.llm.executor.execute { current.getAndSet(null)?.let { runCatching { it.release() } } } }
    }
}

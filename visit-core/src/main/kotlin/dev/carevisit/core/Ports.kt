package dev.carevisit.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

interface Repository { suspend fun load(): Archive; suspend fun save(archive: Archive) }
interface SpeechPort {
    suspend fun say(text: String): Boolean
    suspend fun ask(prompt: Prompt,onListening: () -> Unit): String?
    suspend fun listen(onListening: () -> Unit): String?
    fun stop()
}
interface FamilyLink {
    suspend fun send(settings: Settings,message: ApprovedMessage)
    suspend fun revoke(settings: Settings)
}
fun interface Polisher { suspend fun polish(source: String): String? }
fun interface Navigator { suspend fun route(hospital: Hospital) }
suspend fun safePolish(polisher: Polisher,source: String): String = try {
    withTimeoutOrNull(Policy.POLISH_MS) { polisher.polish(source) }?.takeIf { PolishGuard.accept(source,it) } ?: source
} catch(e: CancellationException) { throw e } catch(_: Exception) { source }

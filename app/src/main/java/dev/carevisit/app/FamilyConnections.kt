package dev.carevisit.app

import dev.carevisit.core.*
import java.security.SecureRandom

interface FamilyConnection : FamilyLink {
    suspend fun poll(settings: Settings): List<Pair<String, String>>
    fun close() {}
}

internal object FamilyLinks {
    // The real adapter factory is deliberately lazy: demo cannot even construct an HTTP client.
    fun open(demo: Boolean, clock: () -> Long, real: () -> FamilyConnection): FamilyConnection =
        if (demo) DemoFamilyLink(clock) else real()
}

/** Session-scoped fake relay. Stores only approved DTOs; no sockets, files or background jobs. */
internal class DemoFamilyLink(private val clock: () -> Long) : FamilyConnection {
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private val lock = Any()
    private val received = linkedMapOf<String, ApprovedMessage>()
    private data class Reply(val id: String, val text: String, val due: Long)
    private val pending = mutableListOf<Reply>()
    private var pairedToken = ""
    private var sequence = 0L
    private var closed = false
    private var outsideQueued = false
    val messages: List<ApprovedMessage> get() = synchronized(lock) { received.values.toList() }

    override suspend fun send(settings: Settings, message: ApprovedMessage) = synchronized(lock) {
        check(!closed) { "Demo family session closed" }
        require(settings.familyToken.matches(Regex("[0-9a-f]{32}")))
        if (pairedToken != settings.familyToken) { clear(); pairedToken = settings.familyToken }
        if (received.containsKey(message.id)) return@synchronized
        received[message.id] = message
        if (message.kind in setOf("memo", "briefing")) {
            pending += Reply("demo-${++sequence}", "잘 받았어", clock() + 3_000)
        }
        if (message.kind == "status" && message.text == "주차 중") outsideQueued = false
    }

    override suspend fun revoke(settings: Settings) = synchronized(lock) {
        if (settings.familyToken == pairedToken) clear()
    }

    override suspend fun poll(settings: Settings): List<Pair<String, String>> = synchronized(lock) {
        if (closed || pairedToken.isEmpty() || pairedToken != settings.familyToken) return@synchronized emptyList()
        val ready = pending.filter { it.due <= clock() }
        pending.removeAll(ready.toSet())
        ready.map { it.id to it.text }
    }

    fun outside(settings: Settings): Boolean = synchronized(lock) {
        if (closed || outsideQueued || pairedToken.isEmpty() || pairedToken != settings.familyToken) return@synchronized false
        pending += Reply("demo-${++sequence}", "나왔어", clock())
        outsideQueued = true
        true
    }

    override fun close() = synchronized(lock) { closed = true; clear() }
    private fun clear() { received.clear(); pending.clear(); pairedToken = ""; outsideQueued = false }
}

internal object DemoFamilyActions {
    fun consent(demo: Boolean, link: FamilyConnection, state: ViewState): Event.FamilyConsent? =
        if (demo && link is DemoFamilyLink && state.fullScreen && state.archive.consent.healthAt != null &&
            state.archive.consent.familyAt == null) Event.FamilyConsent(true, link.token) else null

    fun outside(demo: Boolean, link: FamilyConnection, state: ViewState): Boolean {
        val archive = state.archive
        return demo && link is DemoFamilyLink && archive.consent.healthAt != null && archive.consent.familyAt != null &&
            archive.session.phase == Phase.AT_HOSPITAL && archive.session.parkingMode &&
            archive.session.rendezvous != Rendezvous.OUTSIDE && link.outside(archive.settings)
    }
}

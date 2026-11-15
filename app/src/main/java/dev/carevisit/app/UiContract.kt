package dev.carevisit.app

import dev.carevisit.core.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LaunchExtras {
    const val DEMO = "demo"
    fun pick(value: String?): String? = value?.let { if(it=="visit") "visit" else "off" }
}
sealed interface AppCommand {
    data class Core(val event: Event, val sourceDemo: Boolean = VisitStore.renderedDemo) : AppCommand {
        fun belongsTo(demo: Boolean): Boolean = sourceDemo == demo
    }
    data class Demo(val enabled: Boolean) : AppCommand
    data object Next : AppCommand
}
object VisitStore {
    // Written after composition, not when the service publishes: old-screen callbacks retain
    // their origin while a mode switch is queued or the replacement screen is still pending.
    @Volatile internal var renderedDemo: Boolean = false
    val commands=Channel<AppCommand>(Channel.UNLIMITED)
    private val mutable=MutableStateFlow(ViewState())
    val state: StateFlow<ViewState> = mutable.asStateFlow()
    internal fun publish(value: ViewState) {mutable.value=value}
    fun send(e: Event) { commands.trySend(AppCommand.Core(e)) }
}

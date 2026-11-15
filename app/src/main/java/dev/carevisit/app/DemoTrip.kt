package dev.carevisit.app

import dev.carevisit.core.*

/** Fictional navigation and an explicitly simulated wait; vehicle gear/speed remain real. */
internal class DemoTrip {
    val hospital = Hospital("○○대학교병원")
    val destination get() = Event.Destination(hospital)
    val arrival get() = Event.NavigationArrived(hospital.name,hospital.address)
    private var advancedVisit: String?=null
    fun returnAdvance(state: ViewState,speedMps: Double?): Long {
        val session=state.archive.session
        val id=session.visit?.id ?: return 0L
        if(session.phase!=Phase.WAIT_SAFE || !session.returnPending || state.gear!=Gear.D ||
            speedMps?.let { it.isFinite() && it>0.5 }!=true || advancedVisit==id) return 0L
        advancedVisit=id
        return Policy.SAFE_DRIVE_MS
    }
}

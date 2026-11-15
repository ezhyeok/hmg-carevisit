package dev.carevisit.app

import dev.carevisit.core.ViewState

/** 화면 캡처 차단(FLAG_SECURE) 여부. 실제 기록이 보일 수 있으면 막고, 지어낸 기록만 보이는 시연 모드에서만 푼다. */
object SecureWindowPolicy {
    fun secure(demo: Boolean): Boolean = !demo

    /** Fail closed while a real-mode frame is waiting for FLAG_SECURE to be applied. */
    fun visibleState(state: ViewState, appliedDemo: Boolean?): ViewState =
        if (state.demo || appliedDemo == false) state else ViewState()
}

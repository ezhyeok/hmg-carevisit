package dev.hmg.pleoskit

import android.car.drivingstate.CarUxRestrictions

/**
 * 주행 중 UX 제한 (AAOS `CarUxRestrictions`). 주행 규제 개발 가이드: `CarUxRestrictionsManager.registerListener()`로 받은
 * 값의 `isRequiresDistractionOptimization`과 `getActiveRestrictions()` 비트로 기능별 제한을 적용한다.
 * 구독은 [UxRestrictionWatcher]·[CarState]가 한다. 이 파일의 타입은 런타임에 android.car를 쓰지 않는다
 * (상수는 컴파일 때 값으로 들어간다): 앱의 JVM 시험이 그대로 만들어 쓴다.
 *
 * 모르는 상태를 [Unknown]으로 따로 둔다. 연결 전, 구독 실패, 연결 해제 때 '제한 없음(false)'으로 두면 주행 중에도
 * 정차 화면이 열린다(2026-11-11 UI 감사 #2). 쓰는 쪽은 [restricted]로 읽어서 모르면 제한으로 본다.
 */
sealed interface UxRestrictions {

    /** 주행 중 화면 조작을 줄여야 하는지. 모르면 true (fail closed) */
    val restricted: Boolean
        get() = this !is Known || requiresDistractionOptimization

    /** 아직 모르거나 읽지 못했다. [cause]는 로그·시연 패널용 */
    data class Unknown(val cause: String) : UxRestrictions

    data class Known(
        val requiresDistractionOptimization: Boolean,
        /** `CarUxRestrictions.getActiveRestrictions()` 비트 ([UxRestrictionFlags]) */
        val activeRestrictions: Int,
        /** `getMaxRestrictedStringLength()`: [UxRestrictionFlags.LIMIT_STRING_LENGTH]일 때 한 문구의 최대 글자 수 */
        val maxStringLength: Int,
        /** `getMaxCumulativeContentItems()`: [UxRestrictionFlags.LIMIT_CONTENT]일 때 보여 줄 항목 수 */
        val maxContentItems: Int,
        /** `getMaxContentDepth()`: [UxRestrictionFlags.LIMIT_CONTENT]일 때 들어갈 수 있는 깊이 */
        val maxContentDepth: Int,
    ) : UxRestrictions {
        fun has(flag: Int): Boolean = requiresDistractionOptimization && activeRestrictions and flag != 0
    }

    companion object {
        val NOT_CONNECTED: UxRestrictions = Unknown("연결 전")
    }
}

/** `android.car.drivingstate.CarUxRestrictions`의 비트. 앱 모듈은 android.car를 컴파일하지 않으므로 여기서 내보낸다 */
object UxRestrictionFlags {
    const val NO_DIALPAD = CarUxRestrictions.UX_RESTRICTIONS_NO_DIALPAD
    const val NO_FILTERING = CarUxRestrictions.UX_RESTRICTIONS_NO_FILTERING
    const val LIMIT_STRING_LENGTH = CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH
    const val NO_KEYBOARD = CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD
    const val NO_VIDEO = CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO
    const val LIMIT_CONTENT = CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT
    const val NO_SETUP = CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP
    const val NO_TEXT_MESSAGE = CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE
    const val NO_VOICE_TRANSCRIPTION = CarUxRestrictions.UX_RESTRICTIONS_NO_VOICE_TRANSCRIPTION
}

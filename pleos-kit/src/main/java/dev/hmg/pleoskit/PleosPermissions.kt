package dev.hmg.pleoskit

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager

/**
 * 실행 중에 사용자에게 요청해야 하는(dangerous) 권한.
 *
 * `pleos.car.permission.*`의 대부분은 Manifest 선언만으로 되지만, 아래 둘은 선언만 하면 SecurityException이 난다
 * (에뮬레이터에서 확인). adb로 줄 때는 AAOS 포그라운드 사용자에게 줘야 한다:
 * `adb shell pm grant --user current <패키지> <권한>`
 */
object PleosPermissions {
    const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"

    val RUNTIME = listOf(CAR_SPEED, FUSED_LOCATION)

    /** [permissions] 중 Manifest에 선언했지만 아직 허용되지 않은 것. */
    fun missing(context: Context, permissions: List<String> = RUNTIME): List<String> {
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toSet() ?: emptySet()
        return permissions.filter {
            it in declared && context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 빠진 권한이 있으면 시스템 권한 창을 띄우고 false를 돌려준다(결과는 `onRequestPermissionsResult`로 온다).
     * 모두 허용돼 있으면 true.
     */
    fun requestIfNeeded(activity: Activity, requestCode: Int = 7001, permissions: List<String> = RUNTIME): Boolean {
        val missing = missing(activity, permissions)
        if (missing.isEmpty()) return true
        activity.requestPermissions(missing.toTypedArray(), requestCode)
        return false
    }
}

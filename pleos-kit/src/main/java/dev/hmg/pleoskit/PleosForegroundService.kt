package dev.hmg.pleoskit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 차량 신호와 내비 이벤트를 받는 로직을 올려 두는 포그라운드 서비스의 바탕.
 *
 * Activity만 있는 앱은 내비 앱이 화면 앞으로 오면 cached 프로세스가 되어 Android 14 freezer에 얼려지고,
 * 그동안 SDK 이벤트를 받지 못한다. 주행 중 계속 동작해야 하는 로직은 이 서비스를 상속한 서비스에 둔다.
 *
 * 앱 Manifest에 서비스를 선언해야 한다.
 * ```xml
 * <service android:name=".DriveService" android:exported="false" android:foregroundServiceType="specialUse">
 *     <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="용도 설명" />
 * </service>
 * ```
 */
abstract class PleosForegroundService : Service() {

    /** 서비스가 살아 있는 동안 유지되는 스코프. 자식 하나가 실패해도 나머지는 계속 돈다. */
    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 상태 표시줄 알림 제목. */
    protected abstract val notificationTitle: String

    protected open val notificationChannelId: String = "pleos_drive"
    protected open val notificationIcon: Int = android.R.drawable.stat_notify_sync
    protected open val foregroundType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

    private var started = false
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var foregroundRetry: Runnable? = null

    /** 서비스가 처음 시작될 때 한 번 불린다. kit 연결과 신호 수집은 여기서 [serviceScope]로 시작한다. */
    protected abstract fun onServiceStarted()

    final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()
        ensureStarted()
        onCommand(intent)
        return START_STICKY
    }

    /**
     * 앱의 화면(태스크)이 닫혔다. 서비스는 계속 돈다.
     *
     * 앱이 죽은 뒤 시스템이 서비스를 다시 만들 때는 이것이 시작 신호다: 죽으면서 화면이 함께 닫히면 다시 만든 서비스에
     * `onStartCommand` 없이 이것만 온다. 예전에는 `onStartCommand`에서만 시작해서, 다시 만들어진 서비스가 알림만 띄운 채
     * 아무것도 하지 않았다 (2026-11-12 에뮬레이터 `am crash`: 4분 넘게 로그 없음, 앱 화면을 열어야 시작).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (started) return
        KitLog.w("service recreated without a start command (the app died with its screen): starting")
        enterForeground()
        ensureStarted()
    }

    private fun ensureStarted() {
        if (started) return
        started = true
        onServiceStarted()
    }

    /**
     * 포그라운드 서비스로 올린다. 시스템이 백그라운드에서 다시 띄운 서비스는 올리지 못할 수 있다
     * (Android 12+, `ForegroundServiceStartNotAllowedException`은 IllegalStateException이다). 예전에는 그 예외를 그대로 맞아
     * 다시 시작·죽기를 되풀이할 수 있었다 (서비스 감사). 못 올리면 죽지 않고 [FOREGROUND_RETRY_MS]마다 다시 시도한다:
     * 화면이 다시 보이면 올라간다. 그동안은 보통 서비스라 시스템이 멈출 수 있다 (그때는 onDestroy로 정상 종료한다).
     * 권한이나 서비스 종류 선언이 빠진 것(SecurityException 등)은 개발 오류라 잡지 않는다.
     */
    private fun enterForeground() {
        foregroundRetry?.let(main::removeCallbacks)
        foregroundRetry = null
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), foregroundType)
        } catch (e: IllegalStateException) {
            KitLog.w("startForeground refused (${e.javaClass.simpleName}); retry in ${FOREGROUND_RETRY_MS / 1000}s")
            val retry = Runnable { enterForeground() }
            foregroundRetry = retry
            main.postDelayed(retry, FOREGROUND_RETRY_MS)
        }
    }

    /** `startForegroundService`로 들어온 인텐트를 처리한다. 필요할 때만 재정의한다. */
    protected open fun onCommand(intent: Intent?) = Unit

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(notificationChannelId, notificationTitle, NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, notificationChannelId)
            .setSmallIcon(notificationIcon)
            .setContentTitle(notificationTitle)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        foregroundRetry?.let(main::removeCallbacks)
        foregroundRetry = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val FOREGROUND_RETRY_MS = 10_000L

        /** Activity가 화면에 있을 때 호출한다(백그라운드에서는 포그라운드 서비스를 시작할 수 없다). */
        fun start(context: Context, service: Class<out PleosForegroundService>, intent: Intent? = null) {
            val i = (intent ?: Intent()).setClass(context, service)
            context.startForegroundService(i)
        }
    }
}

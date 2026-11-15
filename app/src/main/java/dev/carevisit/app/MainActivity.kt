package dev.carevisit.app

import dev.carevisit.app.ui.VisitScreen
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hmg.pleoskit.PleosForegroundService
import dev.hmg.pleoskit.PleosPermissions
import dev.hmg.pleoskit.UxRestrictionWatcher
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val ux by lazy {UxRestrictionWatcher(this)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 실제 진료 기록은 화면 캡처·최근 앱 미리보기·화면 미러링에 남기지 않는다(FLAG_SECURE).
        // 시연 모드는 지어낸 기록만 보여 주므로 녹화·캡처할 수 있게 푼다(아래 LaunchedEffect, SecureWindowPolicy).
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        PleosForegroundService.start(this,VisitService::class.java)
        PleosPermissions.requestIfNeeded(this,permissions=listOf(PleosPermissions.CAR_SPEED))
        scope.launch {ux.connectWithRetry()}
        if(savedInstanceState==null) launchIntent(intent)
        setContent {
            val state=VisitStore.state.collectAsStateWithLifecycle().value
            val restriction=ux.state.collectAsStateWithLifecycle().value
            var appliedDemo by remember { mutableStateOf<Boolean?>(null) }
            SideEffect { VisitStore.renderedDemo = state.demo }
            LaunchedEffect(state.demo) {
                // Render the new, isolated demo subtree before allowing capture of its surface.
                // A mode change cancels this effect, so an obsolete demo cannot clear security.
                if (state.demo) { withFrameNanos { }; withFrameNanos { } }
                if(SecureWindowPolicy.secure(state.demo)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                appliedDemo = state.demo
            }
            BackHandler {moveTaskToBack(true)}
            // Discard remembered hospital/relay input and history selection at the mode boundary.
            key(state.demo) {
                VisitScreen(SecureWindowPolicy.visibleState(state, appliedDemo),restriction.restricted)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);launchIntent(intent)}
    private fun launchIntent(intent: Intent) {
        LaunchExtras.pick(intent.getStringExtra(LaunchExtras.DEMO))?.let { VisitStore.commands.trySend(AppCommand.Demo(it=="visit")) }
    }
    override fun onDestroy() {scope.cancel();ux.release();super.onDestroy()}
}

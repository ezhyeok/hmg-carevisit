package dev.carevisit.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureWindowPolicyTest {
    @Test fun realRecordsAreNeverCapturableOnlyDemoIs() {
        assertTrue(SecureWindowPolicy.secure(demo = false))
        assertFalse(SecureWindowPolicy.secure(demo = true))
    }

    @Test fun activitySetsSecureBeforeAnyContentAndFollowsThePolicy() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "pleos-kit").isDirectory }
        val src = File(root, "app/src/main/java/dev/carevisit/app/MainActivity.kt").readText()
        val secure = src.indexOf("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)")
        assertTrue("FLAG_SECURE는 onCreate에서 setContent보다 먼저", secure in 0 until src.indexOf("setContent {"))
        assertTrue(src.contains("SecureWindowPolicy.secure(state.demo)"))
    }
}

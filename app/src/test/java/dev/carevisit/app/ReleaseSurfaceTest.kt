package dev.carevisit.app
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class ReleaseSurfaceTest {
    private fun root(): File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it,"pleos-kit").isDirectory }
    @Test fun productPermissionsMatchUses() {
        val uses=mapOf("android.permission.INTERNET" to "approved family relay", "android.car.permission.CAR_POWERTRAIN" to "park and leave", "android.car.permission.CAR_SPEED" to "one minute of driving", "pleos.car.permission.CAR_DOORS" to "passenger dropoff", "pleos.car.permission.NAVI_ROUTE" to "hospital destination and arrival", "pleos.car.permission.NAVI_ROUTE_SEARCH" to "saved appointment route", "pleos.car.permission.TTS_SERVICE" to "spoken fixed prompts", "pleos.car.permission.STT_SERVICE" to "verbatim slots", "pleos.car.permission.LLM_SERVICE" to "optional on device punctuation polish")
        val manifest=File(root(),"app/src/main/AndroidManifest.xml").readText()
        val actual=Regex("<uses-permission android:name=\"([^\"]+)\"").findAll(manifest).map{it.groupValues[1]}.toSet()
        assertEquals(uses.keys,actual)
        assertTrue(manifest.contains("android:allowBackup=\"false\"")); assertFalse(manifest.contains("CONTROL_CAR")); assertFalse(manifest.contains("CAR_SEATS"))
    }
    @Test fun releaseHasNoDevelopmentEntryPoint() {
        val main=File(root(),"app/src/main/java").walkTopDown().filter{it.extension=="kt"}.joinToString("\n"){it.readText()}
        assertFalse(main.contains("getBooleanExtra(")); assertFalse(main.contains("putExtras(")); assertFalse(Regex("\\.raw\\b").containsMatchIn(main)); assertFalse(main.contains("ai.pleos.playground"))
        val intentReads=Regex("getStringExtra\\(([^)]+)\\)").findAll(main).map{it.groupValues[1]}.toSet()
        assertEquals(setOf("LaunchExtras.DEMO"),intentReads)
    }
}

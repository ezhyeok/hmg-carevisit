package dev.carevisit.app.ui

import dev.carevisit.core.Settings
import org.junit.Assert.*
import org.junit.Test

class SettingsInputTest {
    private val token = "0123456789abcdef0123456789abcdef"
    @Test fun baseUrlAllowsOnlyHttpOriginsWithoutCredentialsOrPaths() {
        listOf("http://10.0.2.2:8765", "https://family.example/", "http://192.168.0.10:8765").forEach { assertTrue(it, SettingsInput.validBaseUrl(it)) }
        listOf("file:///tmp/a", "ftp://example.com", "http://user:pass@example.com", "http://host/path", "http://host/?token=x", "http://host/#x", "http://host:70000", "http://host:0", "", "노트북-IP").forEach { assertFalse(it, SettingsInput.validBaseUrl(it)) }
    }
    @Test fun shortCodeIsAnIdentificationHintAndFullTokenRemainsInFragment() {
        assertEquals("89AB-CDEF", SettingsInput.pairingCode(token))
        assertEquals("http://192.168.0.10:8765/#$token", SettingsInput.familyUrl("http://192.168.0.10:8765/", token))
        assertNull(SettingsInput.familyUrl("http://host", token.take(8)))
        assertEquals("연결 안 됨", SettingsInput.pairingCode(""))
    }
    @Test fun invalidFamilyBaseDoesNotCreateClickableAccessUrl() {
        assertNull(SettingsInput.familyUrl("", token))
        assertNull(SettingsInput.familyUrl("javascript:alert(1)", token))
    }
    @Test fun manuallySavedHospitalHasNoInventedCoordinates() {
        val hospital = SettingsInput.hospital(" ○○병원 ")!!
        assertEquals("○○병원", hospital.name)
        assertFalse(hospital.routable)
        assertNull(hospital.latitude)
        assertNull(hospital.longitude)
        assertNull(SettingsInput.hospital("  "))
        assertNull(SettingsInput.hospital("병원\n주소"))
        assertNull(SettingsInput.hospital("병".repeat(41)))
    }
    @Test fun defaultsAreDeviceOnlyAndPolishIsOff() {
        val settings = Settings()
        assertFalse(settings.polish)
        assertEquals(180, settings.retentionDays)
        assertEquals("", settings.familyToken)
        assertTrue(settings.honorific in UiCopy.HONORIFICS)
    }
}

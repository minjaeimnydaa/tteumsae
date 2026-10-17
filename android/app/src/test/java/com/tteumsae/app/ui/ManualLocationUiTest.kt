package com.tteumsae.app.ui

import com.tteumsae.app.ui.route.initialRouteStartQuery
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLocationUiTest {
    @Test
    fun manualInputDoesNotAssumeCurrentLocationEvenWithPreviouslyGrantedPermission() {
        assertEquals("", initialRouteStartQuery(null, automaticLocationEnabled = false, hasPermission = true))
        assertEquals("강릉역", initialRouteStartQuery("강릉역", automaticLocationEnabled = false, hasPermission = true))
    }

    @Test
    fun automaticInputPreservesTheExistingPermissionBasedDefault() {
        assertEquals("현재 위치", initialRouteStartQuery(null, automaticLocationEnabled = true, hasPermission = true))
        assertEquals("", initialRouteStartQuery(null, automaticLocationEnabled = true, hasPermission = false))
    }

    @Test
    fun manualInputCannotAutomaticallyResolveAnOldCurrentLocationLabel() {
        assertFalse(shouldAutoLocateStart("현재 위치", hasLocation = false, automaticLocationEnabled = false))
        assertTrue(shouldAutoLocateStart("현재 위치", hasLocation = false, automaticLocationEnabled = true))
    }

    @Test
    fun locationUiUsesBuildPolicyForAutomaticControlsAndExplainsManualSelection() {
        val input = source("ui/route/LocationScreen.kt")
        assertTrue(input.contains("LocationAccessPolicy.automaticLocationEnabled"))
        assertTrue(input.contains("onUseCurrentLocation = useCurrentLocation.takeIf { automaticLocationEnabled }"))
        assertTrue(input.contains("if (!automaticLocationEnabled) return@LaunchedEffect"))
        assertTrue(input.contains("선택한 출발지 기준"))
        val home = source("ui/TteumsaeApp.kt")
        assertTrue(home.contains("currentLocationTarget.takeIf { automaticLocationEnabled }"))
        assertTrue(home.contains("if (automaticLocationEnabled) RoundMapButton("))
        val settings = source("ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("if (automaticLocationEnabled) {"))
        assertTrue(settings.contains("if (automaticLocationEnabled && locationTermsAvailable) {"))
    }

    private fun source(relativePath: String): String =
        listOf(
            File("src/main/java/com/tteumsae/app/$relativePath"),
            File("app/src/main/java/com/tteumsae/app/$relativePath"),
        ).firstOrNull(File::isFile)?.readText() ?: error("소스 파일이 없습니다: $relativePath")
}

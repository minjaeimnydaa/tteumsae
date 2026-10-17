package com.tteumsae.app.ui.route

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultClusterViewportTransitionTest {
    @Test
    fun `explicit cluster reveal uses balanced viewport before sheet animation starts`() {
        assertEquals(
            ResultSheetPosition.BALANCED,
            resultSheetViewportPosition(ResultSheetPosition.BALANCED, ResultSheetPosition.MAP, ResultSheetPosition.MAP),
        )
    }

    @Test
    fun `active user drag uses gesture target when no new explicit request exists`() {
        assertEquals(
            ResultSheetPosition.BALANCED,
            resultSheetViewportPosition(ResultSheetPosition.MAP, ResultSheetPosition.MAP, ResultSheetPosition.BALANCED),
        )
        assertEquals(
            ResultSheetPosition.MAP,
            resultSheetViewportPosition(ResultSheetPosition.BALANCED, ResultSheetPosition.BALANCED, ResultSheetPosition.MAP),
        )
    }

    @Test
    fun `new explicit list or map request wins over the previous animation target`() {
        assertEquals(
            ResultSheetPosition.LIST,
            resultSheetViewportPosition(ResultSheetPosition.LIST, ResultSheetPosition.BALANCED, ResultSheetPosition.MAP),
        )
        assertEquals(
            ResultSheetPosition.MAP,
            resultSheetViewportPosition(ResultSheetPosition.MAP, ResultSheetPosition.LIST, ResultSheetPosition.BALANCED),
        )
    }

    @Test
    fun `cluster callback defers camera fit and consumes the request using current padding`() {
        val source = readSource("ui/TteumsaeApp.kt")
        val callback = source.substringAfter("is RouteMapLabelTarget.Cluster -> {")
            .substringBefore("else -> false")
        assertTrue(callback.contains("pendingClusterFocus = target"))
        assertFalse(callback.contains("map.moveCamera("))

        val focus = source.substringAfter("LaunchedEffect(kakaoMap, pendingClusterFocus) {")
            .substringBefore("// Move only the SDK attribution")
        assertTrue(focus.contains("withFrameNanos { }"))
        assertTrue(focus.contains("pendingClusterFocus != target"))
        val padding = focus.indexOf("map.setPadding(")
        val fit = focus.indexOf("map.moveCamera(")
        assertTrue("The current viewport must be applied before fitting", padding >= 0 && fit > padding)
        assertTrue(focus.contains("pendingClusterFocus = null"))
        assertFalse(focus.contains("routeStops"))
    }

    @Test
    fun `user map gestures cancel a queued cluster fit`() {
        val source = readSource("ui/TteumsaeApp.kt")
        val gesture = source.substringAfter("map.setOnCameraMoveStartListener { _, gesture ->")
            .substringBefore("map.setOnCameraMoveEndListener")
        assertTrue(gesture.contains("gesture != GestureType.Unknown"))
        assertTrue(gesture.contains("pendingClusterFocus = null"))
        val mapClick = source.substringAfter("map.setOnMapClickListener { _, _, _, _ ->")
            .substringBefore("map.setOnCameraMoveStartListener")
        assertTrue(mapClick.contains("pendingClusterFocus = null"))
    }

    private fun readSource(relativePath: String): String = listOf(
        File("src/main/java/com/tteumsae/app/$relativePath"),
        File("app/src/main/java/com/tteumsae/app/$relativePath"),
    ).first(File::isFile).readText()
}

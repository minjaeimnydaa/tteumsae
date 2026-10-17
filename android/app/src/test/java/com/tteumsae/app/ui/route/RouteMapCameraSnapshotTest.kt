package com.tteumsae.app.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteMapCameraSnapshotTest {
    @Test
    fun `a panned and zoomed camera survives a saved-state round trip`() {
        val snapshot = RouteMapCameraSnapshot(37.7518, 128.8761, 17)
        assertEquals(snapshot, restoredRouteMapCamera(snapshot.savedValues()))
    }

    @Test
    fun `empty or malformed restored state falls back to the route camera`() {
        assertNull(restoredRouteMapCamera(emptyList()))
        assertNull(restoredRouteMapCamera(listOf(37.0, 128.0)))
        assertNull(restoredRouteMapCamera(listOf(Double.NaN, 128.0, 17.0)))
        assertNull(restoredRouteMapCamera(listOf(37.0, 181.0, 17.0)))
        assertNull(restoredRouteMapCamera(listOf(91.0, 128.0, 17.0)))
        assertNull(restoredRouteMapCamera(listOf(37.0, 128.0, -1.0)))
        assertNull(restoredRouteMapCamera(listOf(37.0, 128.0, 17.5)))
    }
}

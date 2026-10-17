package com.tteumsae.app.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RouteDetailRequestKeyTest {
    @Test
    fun `the same place and arrival instant may reuse its forecast`() {
        assertEquals(routeDetailRequestKey("A", 1_000_000L, 10), routeDetailRequestKey("A", 1_000_000L, 10))
    }

    @Test
    fun `a new calculation or first leg invalidates the same place forecast`() {
        val original = routeDetailRequestKey("A", 1_000_000L, 10)
        assertNotEquals(original, routeDetailRequestKey("A", 1_060_000L, 10))
        assertNotEquals(original, routeDetailRequestKey("A", 1_000_000L, 20))
        assertNotEquals(original, routeDetailRequestKey("B", 1_000_000L, 10))
        assertEquals(1_600_000L, original.expectedArrivalEpochMillis)
    }

    @Test
    fun `missing calculation leaves the arrival instant unset`() {
        assertEquals(null, routeDetailRequestKey("A", null, 10).expectedArrivalEpochMillis)
    }
}

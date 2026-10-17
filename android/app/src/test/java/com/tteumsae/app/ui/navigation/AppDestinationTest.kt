package com.tteumsae.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun previous_destinations_skip_the_conditions_step() {
        assertEquals(AppDestination.HOME, previousDestination(AppDestination.SAVED))
        assertEquals(AppDestination.HOME, previousDestination(AppDestination.SETTINGS))
        assertEquals(AppDestination.HOME, previousDestination(AppDestination.LOCATION))
        assertEquals(AppDestination.LOCATION, previousDestination(AppDestination.LOADING))
        assertEquals(AppDestination.LOCATION, previousDestination(AppDestination.RESULTS))
        assertEquals(AppDestination.RESULTS, previousDestination(AppDestination.DETAIL))
        assertEquals(AppDestination.HOME, previousDestination(AppDestination.HOME))
    }

    @Test
    fun payload_less_route_destinations_fall_back_safely() {
        assertEquals(
            AppDestination.LOCATION,
            safeRestoredDestination(AppDestination.LOADING, false, false, false),
        )
        assertEquals(
            AppDestination.LOCATION,
            safeRestoredDestination(AppDestination.RESULTS, false, false, false),
        )
        assertEquals(
            AppDestination.LOCATION,
            safeRestoredDestination(AppDestination.RESULTS, true, false, false),
        )
        assertEquals(
            AppDestination.RESULTS,
            safeRestoredDestination(AppDestination.DETAIL, true, true, false),
        )
    }

    @Test
    fun complete_route_payload_keeps_the_requested_destination() {
        assertEquals(
            AppDestination.RESULTS,
            safeRestoredDestination(AppDestination.RESULTS, true, true, false),
        )
        assertEquals(
            AppDestination.DETAIL,
            safeRestoredDestination(AppDestination.DETAIL, true, true, true),
        )
    }
}

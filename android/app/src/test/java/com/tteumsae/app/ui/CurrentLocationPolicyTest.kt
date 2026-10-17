package com.tteumsae.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationPolicyTest {
    @Test
    fun `accepts a recent location and small forward clock skew`() {
        val now = 1_000_000L

        assertTrue(isLocationTimestampFresh(now - 60_000L, now, 60_000L))
        assertTrue(isLocationTimestampFresh(now + 5_000L, now, 60_000L))
    }

    @Test
    fun `rejects stale invalid and implausibly future locations`() {
        val now = 1_000_000L

        assertFalse(isLocationTimestampFresh(now - 60_001L, now, 60_000L))
        assertFalse(isLocationTimestampFresh(0L, now, 60_000L))
        assertFalse(isLocationTimestampFresh(now + 10_001L, now, 60_000L))
    }
}

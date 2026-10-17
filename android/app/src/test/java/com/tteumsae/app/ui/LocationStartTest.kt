package com.tteumsae.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStartTest {
    @Test
    fun autoLocateOnlyWhileCurrentLocationIsUnresolved() {
        assertTrue(shouldAutoLocateStart("현재 위치", hasLocation = false))
        assertFalse(shouldAutoLocateStart("현재 위치", hasLocation = true))
        assertFalse(shouldAutoLocateStart("강릉역", hasLocation = false))
    }
}

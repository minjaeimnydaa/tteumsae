package com.tteumsae.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TteumsaeApiTest {
    @Test
    fun limitsOnlyGangwonScopedSearches() {
        assertEquals("서울역", locationSearchQuery("서울역", gangwonOnly = false))
        assertEquals("강원 강릉역", locationSearchQuery("강릉역", gangwonOnly = true))
        assertEquals("강원 원주시청", locationSearchQuery("강원 원주시청", gangwonOnly = true))
    }
}

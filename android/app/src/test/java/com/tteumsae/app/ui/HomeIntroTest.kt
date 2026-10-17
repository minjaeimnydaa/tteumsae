package com.tteumsae.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIntroTest {
    @Test
    fun `오늘 숨김을 선택한 날짜에는 안내를 다시 보여주지 않는다`() {
        assertFalse(shouldShowHomeIntro("2026-08-04", "2026-08-04"))
        assertTrue(shouldShowHomeIntro("2026-08-03", "2026-08-04"))
        assertTrue(shouldShowHomeIntro(null, "2026-08-04"))
    }
}

package com.tteumsae.app.ui.route

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultViewModeTest {
    @Test fun `view switch names the destination not navigation`() {
        assertEquals("지도 보기", resultViewActionLabel(false))
        assertEquals("목록 보기", resultViewActionLabel(true))
    }
}

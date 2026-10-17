package com.tteumsae.app.ui.route

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultViewportPolicyTest {
    @Test fun `freshness never claims unknown or future calculation is current`() {
        assertEquals("계산 시각 확인 필요", resultFreshnessLabel(null, 600000L))
        assertEquals("계산 시각 확인 필요", resultFreshnessLabel(700000L, 600000L))
        assertEquals("계산 직후 출발 기준", resultFreshnessLabel(600000L, 600000L))
        assertEquals("계산 후 5분 지남 · 다시 확인 권장", resultFreshnessLabel(300000L, 600000L))
    }
}

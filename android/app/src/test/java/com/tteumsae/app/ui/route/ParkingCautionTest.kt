package com.tteumsae.app.ui.route

import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingCautionTest {
    @Test fun `resident label is a caution not permission to park`() {
        assertTrue(parkingAccessCaution("거주자우선 주차장").contains("거주자우선"))
        assertTrue(parkingAccessCaution("공영 주차장").contains("빈자리"))
    }
}

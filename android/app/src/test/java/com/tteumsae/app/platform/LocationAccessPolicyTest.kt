package com.tteumsae.app.platform

import com.tteumsae.app.BuildConfig
import com.tteumsae.app.location.LocationAccessPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationAccessPolicyTest {
    @Test
    fun `공통 정책은 빌드가 선택한 위치 기능과 일치한다`() {
        assertEquals(BuildConfig.AUTOMATIC_LOCATION_ENABLED, LocationAccessPolicy.automaticLocationEnabled)
    }

    @Test
    fun `수동과 자동 좌표 저장소는 서로 다른 버전으로 분리한다`() {
        val expected = if (BuildConfig.AUTOMATIC_LOCATION_ENABLED) "automatic_v1" else "manual_v1"
        assertEquals(expected, LocationAccessPolicy.persistenceMode)
    }
}

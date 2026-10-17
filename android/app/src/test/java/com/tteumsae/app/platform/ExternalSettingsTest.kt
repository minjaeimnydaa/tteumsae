package com.tteumsae.app.platform

import com.tteumsae.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalSettingsTest {
    @Test
    fun `문의 이메일은 운영 계정 주소를 사용한다`() {
        assertEquals("godburgundy@gmail.com", CONTACT_EMAIL)
    }

    @Test
    fun `개인정보처리방침은 운영 백엔드 공개 주소를 사용한다`() {
        assertEquals("${BuildConfig.API_BASE_URL}/privacy", PRIVACY_POLICY_URL)
    }
}

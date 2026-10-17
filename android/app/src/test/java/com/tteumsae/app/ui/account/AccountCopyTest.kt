package com.tteumsae.app.ui.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCopyTest {
    @Test
    fun `account copy only promises profile management and device local saves`() {
        val copy = listOf(
            GUEST_ACCOUNT_DESCRIPTION,
            LOGIN_SHEET_TITLE,
            LOGIN_SHEET_DESCRIPTION,
            ACCOUNT_DELETION_IMPACT,
        ).joinToString(" ")

        assertFalse(copy.contains("여러 기기"))
        assertFalse(copy.contains("동기화"))
        assertFalse(copy.contains("저장을 이어"))
        assertTrue(LOGIN_SHEET_DESCRIPTION.contains("이 기기"))
        assertTrue(ACCOUNT_DELETION_IMPACT.contains("기기에 저장된 장소는 유지"))
    }
}

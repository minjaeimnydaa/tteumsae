package com.tteumsae.app.ui.route

import com.tteumsae.app.reminder.ReminderDeliveryBlockReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPresentationTest {
    @Test
    fun `예약 선호가 켜져 있어도 시스템 차단이면 예약 문구를 약속하지 않는다`() {
        val presentation = reminderPresentation(
            checked = true,
            canEnable = true,
            blockedReason = ReminderDeliveryBlockReason.CHANNEL_DISABLED,
        )

        assertFalse(presentation.showScheduledTime)
        assertTrue(presentation.showSettingsAction)
        assertTrue(presentation.message.contains("출발 시간 알림"))
    }

    @Test
    fun `권한 거부는 스위치에서 권한 요청을 다시 할 수 있다`() {
        val presentation = reminderPresentation(
            checked = false,
            canEnable = true,
            blockedReason = ReminderDeliveryBlockReason.RUNTIME_PERMISSION,
        )

        assertFalse(presentation.showScheduledTime)
        assertTrue(presentation.showSettingsAction)
        assertTrue(presentation.message.contains("알림 권한"))
    }

    @Test
    fun `선호가 꺼져 있으면 알림 예정 시각을 표시하지 않는다`() {
        val presentation = reminderPresentation(false, true, null)
        assertFalse(presentation.showScheduledTime)
        assertFalse(presentation.showSettingsAction)
    }

    @Test
    fun `예약이 유효하고 시스템이 허용할 때만 예정 시각을 표시한다`() {
        val presentation = reminderPresentation(true, true, null)
        assertTrue(presentation.showScheduledTime)
        assertFalse(presentation.showSettingsAction)
    }

    @Test
    fun `추천이 오래돼 새 알림을 켤 수 없으면 교통 재확인을 안내한다`() {
        val presentation = reminderPresentation(false, false, null)
        assertFalse(presentation.showScheduledTime)
        assertTrue(presentation.message.contains("다시 확인"))
    }
}

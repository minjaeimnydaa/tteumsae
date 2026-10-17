package com.tteumsae.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderDeliveryPolicyTest {
    @Test
    fun `권한이 없으면 앱 차단과 구분해 권한 요청을 유지한다`() {
        assertEquals(
            ReminderDeliveryBlockReason.RUNTIME_PERMISSION,
            reminderDeliveryBlockReason(
                runtimePermissionGranted = false,
                appNotificationsEnabled = false,
                channelEnabled = true,
            ),
        )
    }

    @Test
    fun `권한이 있어도 앱 전체 알림이 꺼져 있으면 전달할 수 없다`() {
        assertEquals(
            ReminderDeliveryBlockReason.APP_NOTIFICATIONS_DISABLED,
            reminderDeliveryBlockReason(true, false, true),
        )
    }

    @Test
    fun `앱이 허용돼도 출발 알림 채널이 꺼져 있으면 전달할 수 없다`() {
        assertEquals(
            ReminderDeliveryBlockReason.CHANNEL_DISABLED,
            reminderDeliveryBlockReason(true, true, false),
        )
    }

    @Test
    fun `권한 앱 채널이 모두 허용되면 차단 이유가 없다`() {
        assertNull(reminderDeliveryBlockReason(true, true, true))
    }
}

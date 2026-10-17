package com.tteumsae.app.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureReminderPolicyTest {
    @Test
    fun `출발 권장시각 5분 전에 알림을 예약한다`() {
        assertEquals(
            DepartureReminderDecision.Schedule(triggerAtEpochMillis = 700_000),
            departureReminderDecision(
                latestDepartureEpochMillis = 1_000_000,
                nowEpochMillis = 100_000,
            ),
        )
    }

    @Test
    fun `알림 시각은 지났지만 출발 전이면 즉시 안내한다`() {
        assertEquals(
            DepartureReminderDecision.NotifyNow,
            departureReminderDecision(
                latestDepartureEpochMillis = 1_000_000,
                nowEpochMillis = 800_000,
            ),
        )
    }

    @Test
    fun `출발 권장시각이 지난 여행은 알리지 않는다`() {
        assertEquals(
            DepartureReminderDecision.Skip,
            departureReminderDecision(
                latestDepartureEpochMillis = 1_000_000,
                nowEpochMillis = 1_000_000,
            ),
        )
    }

    @Test
    fun `재부팅 뒤에도 유효한 여행만 다시 예약한다`() {
        assertTrue(shouldRescheduleTrip(latestDepartureEpochMillis = 1_000_000, expiresAtEpochMillis = 2_000_000, nowEpochMillis = 500_000))
        assertFalse(shouldRescheduleTrip(latestDepartureEpochMillis = 1_000_000, expiresAtEpochMillis = 2_000_000, nowEpochMillis = 1_000_000))
        assertFalse(shouldRescheduleTrip(latestDepartureEpochMillis = 3_000_000, expiresAtEpochMillis = 2_000_000, nowEpochMillis = 2_000_000))
    }
}

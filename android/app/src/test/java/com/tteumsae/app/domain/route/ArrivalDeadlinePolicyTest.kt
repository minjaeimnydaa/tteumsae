package com.tteumsae.app.domain.route

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalDeadlinePolicyTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `미래 시각은 같은 날 도착 마감으로 해석한다`() {
        val now = Instant.parse("2026-08-28T01:00:00Z").toEpochMilli()

        val deadline = resolveArrivalDeadline(
            selectedHour = 12,
            selectedMinute = 30,
            nowEpochMillis = now,
            zoneId = seoul,
        )

        assertEquals(Instant.parse("2026-08-28T03:30:00Z").toEpochMilli(), deadline)
    }

    @Test
    fun `이미 지난 시각은 다음 날 도착 마감으로 해석한다`() {
        val now = Instant.parse("2026-08-28T10:00:00Z").toEpochMilli()

        val deadline = resolveArrivalDeadline(
            selectedHour = 18,
            selectedMinute = 0,
            nowEpochMillis = now,
            zoneId = seoul,
        )

        assertEquals(Instant.parse("2026-08-29T09:00:00Z").toEpochMilli(), deadline)
    }

    @Test
    fun `정확히 15분부터 24시간까지만 유효하다`() {
        val now = 1_000_000L

        assertFalse(isValidArrivalDeadline(now + 15 * 60_000 - 1, now))
        assertTrue(isValidArrivalDeadline(now + 15 * 60_000, now))
        assertEquals(15, remainingWholeMinutes(now + 15 * 60_000, now))
        assertTrue(isValidArrivalDeadline(now + 24 * 60 * 60_000, now))
        assertFalse(isValidArrivalDeadline(now + 24 * 60 * 60_000 + 1, now))
    }

    @Test
    fun `새 경로 입력에는 도착 마감이 암묵적으로 선택되지 않는다`() {
        assertNull(RouteFlowInput().arrivalDeadlineEpochMillis)
    }

    @Test
    fun `최대 체류를 5분 단위로 내리고 최소 체류 미만은 선택할 수 없다`() {
        val now = 1_000_000L
        val deadline = now + 69 * 60_000

        assertEquals(35, floorToFiveMinutes(39))
        assertEquals(
            SelectedStopTiming(
                minimumStayMinutes = 15,
                maximumStayMinutes = 35,
                latestDepartureEpochMillis = deadline - 20 * 60_000,
            ),
            selectedStopTiming(
                nowEpochMillis = now,
                arrivalDeadlineEpochMillis = deadline,
                firstLegMinutes = 10,
                secondLegMinutes = 10,
            ),
        )
        assertNull(
            selectedStopTiming(
                nowEpochMillis = now,
                arrivalDeadlineEpochMillis = now + 44 * 60_000,
                firstLegMinutes = 10,
                secondLegMinutes = 10,
            ),
        )
    }
}

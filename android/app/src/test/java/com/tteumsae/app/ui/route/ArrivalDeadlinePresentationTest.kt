package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.route.isValidArrivalDeadline
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalDeadlinePresentationTest {
    private val now = Instant.parse("2026-10-13T14:45:30Z").toEpochMilli()

    @Test
    fun `빠른 시간 선택은 자정을 넘어도 정해진 분만큼 뒤를 선택한다`() {
        val deadline = quickArrivalDeadline(30, now)

        assertEquals(now + 30 * 60_000L, deadline)
        assertTrue(isValidArrivalDeadline(deadline, now))
        assertEquals("내일 00:15까지", formatArrivalDeadline(deadline, now))
    }

    @Test
    fun `같은 날의 도착 마감도 날짜를 함께 표시한다`() {
        val daytime = Instant.parse("2026-10-13T02:00:00Z").toEpochMilli()

        assertEquals("오늘 12:00까지", formatArrivalDeadline(quickArrivalDeadline(60, daytime), daytime))
    }

    @Test
    fun `만료된 날짜는 오늘이나 내일로 잘못 표시하지 않는다`() {
        val yesterday = now - 24 * 60 * 60_000L

        assertEquals("10월 12일 23:45까지", formatArrivalDeadline(yesterday, now))
    }
}

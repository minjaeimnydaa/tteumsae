package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.route.RouteFlowInput
import com.tteumsae.app.domain.route.RouteLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationContinuePolicyTest {
    private val now = 1_000_000L
    private val start = RouteLocation("강릉역", Coordinates(37.75, 128.87))
    private val destination = RouteLocation("경포대", Coordinates(37.80, 128.90))

    @Test
    fun `검색 결과는 열 건까지 노출한다`() {
        assertEquals(10, LOCATION_SEARCH_VISIBLE_LIMIT)
    }

    @Test
    fun `출발지 목적지 도착 마감이 모두 있어야 계속할 수 있다`() {
        assertFalse(canContinueRouteInput(RouteFlowInput(), now, isBusy = false))
        assertFalse(
            canContinueRouteInput(
                RouteFlowInput(start = start, destination = destination),
                now,
                isBusy = false,
            ),
        )
        assertTrue(
            canContinueRouteInput(
                RouteFlowInput(
                    start = start,
                    destination = destination,
                    arrivalDeadlineEpochMillis = now + 15 * 60_000,
                ),
                now,
                isBusy = false,
            ),
        )
    }

    @Test
    fun `도착 마감 경계와 진행 중 상태를 보수적으로 막는다`() {
        val input = RouteFlowInput(
            start = start,
            destination = destination,
            arrivalDeadlineEpochMillis = now + 15 * 60_000 - 1,
        )
        assertFalse(canContinueRouteInput(input, now, isBusy = false))
        assertTrue(
            canContinueRouteInput(
                input.copy(arrivalDeadlineEpochMillis = now + 24 * 60 * 60_000),
                now,
                isBusy = false,
            ),
        )
        assertFalse(
            canContinueRouteInput(
                input.copy(arrivalDeadlineEpochMillis = now + 24 * 60 * 60_000 + 1),
                now,
                isBusy = false,
            ),
        )
        assertFalse(
            canContinueRouteInput(
                input.copy(arrivalDeadlineEpochMillis = now + 60 * 60_000),
                now,
                isBusy = true,
            ),
        )
    }

    @Test
    fun `수동 검색으로 선택한 위치는 위치 권한 없이도 유효하다`() {
        val manuallySelected = RouteFlowInput(
            start = start,
            destination = destination,
            arrivalDeadlineEpochMillis = now + 60 * 60_000,
        )

        assertTrue(canContinueRouteInput(manuallySelected, now, isBusy = false))
    }

    @Test
    fun `비활성 버튼은 사용자가 다음에 할 일을 설명한다`() {
        assertEquals("출발지를 선택해 주세요", routeInputGuidance(RouteFlowInput(), now, false))
        assertEquals(
            "목적지를 선택해 주세요",
            routeInputGuidance(RouteFlowInput(start = start), now, false),
        )
        assertEquals(
            "도착 시각을 정해 주세요",
            routeInputGuidance(RouteFlowInput(start = start, destination = destination), now, false),
        )
        assertEquals(
            "도착 시각은 지금부터 15분 이후로 정해 주세요",
            routeInputGuidance(
                RouteFlowInput(
                    start = start,
                    destination = destination,
                    arrivalDeadlineEpochMillis = now + 14 * 60_000,
                ),
                now,
                false,
            ),
        )
        assertNull(
            routeInputGuidance(
                RouteFlowInput(
                    start = start,
                    destination = destination,
                    arrivalDeadlineEpochMillis = now + 60 * 60_000,
                ),
                now,
                false,
            ),
        )
    }
}

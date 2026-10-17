package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SafetyLevel
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.SearchMode
import com.tteumsae.app.domain.TransportMode
import com.tteumsae.app.domain.route.RouteNavigationAction
import com.tteumsae.app.domain.route.recommendationNeedsRecheck
import com.tteumsae.app.domain.route.routeNavigationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationFreshnessTest {
    private val calculated = 1_800_000_000_000L
    private val minute = 60_000L
    private fun stop(stay: Int = 35, latest: Long = calculated + 60 * minute) = SafeRecommendation(
        place = PlaceCandidate("one", "테스트", PlaceCategory.CAFE, 0, 10, 10, 2, reason = "", tags = emptyList()),
        totalMinutes = 20, marginMinutes = stay, safetyLevel = SafetyLevel.AVAILABLE,
        minimumStayMinutes = 15, maximumStayMinutes = stay, latestDepartureEpochMillis = latest,
    )

    @Test fun `신선한 결과는 경유 길 안내를 허용한다`() {
        assertFalse(recommendationNeedsRecheck(stop(), calculated, calculated + 10_000))
    }
    @Test fun `5분 경계부터 교통 재확인을 요구한다`() {
        assertFalse(recommendationNeedsRecheck(stop(), calculated, calculated + 5 * minute - 1))
        assertTrue(recommendationNeedsRecheck(stop(), calculated, calculated + 5 * minute))
    }
    @Test fun `경유지 출발시각 전이어도 첫 이동과 최소 체류가 안 맞으면 재확인한다`() {
        val stop = stop(latest = calculated + 26 * minute)
        assertFalse(recommendationNeedsRecheck(stop, calculated, calculated + minute))
        assertTrue(recommendationNeedsRecheck(stop, calculated, calculated + minute + 1))
    }
    @Test fun `영업 종료로 제한된 최대 체류도 경과시간을 반영해 재확인한다`() {
        assertFalse(recommendationNeedsRecheck(stop(stay = 15), calculated, calculated + 10_000))
        assertTrue(recommendationNeedsRecheck(stop(stay = 15), calculated, calculated + minute))
    }
    @Test fun `시각 누락이나 큰 시계 역행은 안전한 재확인으로 처리한다`() {
        assertTrue(recommendationNeedsRecheck(stop(), null, calculated))
        assertTrue(recommendationNeedsRecheck(stop(), calculated, calculated - 2 * minute))
        assertTrue(recommendationNeedsRecheck(stop().copy(latestDepartureEpochMillis = null), calculated, calculated))
    }

    @Test fun `서버와 기기의 작은 시계 차이는 허용하되 1분 초과 차이는 재확인한다`() {
        assertFalse(recommendationNeedsRecheck(stop(), calculated, calculated - minute))
        assertTrue(recommendationNeedsRecheck(stop(), calculated, calculated - minute - 1))
    }

    @Test fun `누락된 체류시간이나 음수 이동시간은 경유 안전성을 확정하지 않는다`() {
        assertTrue(recommendationNeedsRecheck(stop().copy(maximumStayMinutes = null), calculated, calculated))
        assertTrue(recommendationNeedsRecheck(stop().copy(place = stop().place.copy(firstLegMinutes = -1)), calculated, calculated))
        assertTrue(recommendationNeedsRecheck(stop(stay = 14), calculated, calculated))
    }

    @Test fun `최소 체류 기준은 15분보다 낮추지 않고 서버의 높은 기준은 보존한다`() {
        assertTrue(recommendationNeedsRecheck(stop(stay = 14).copy(minimumStayMinutes = 1), calculated, calculated))
        assertTrue(recommendationNeedsRecheck(stop(stay = 20).copy(minimumStayMinutes = 25), calculated, calculated))
    }

    private fun criteria(deadline: Long? = calculated + 120 * minute) = SearchCriteria(
        mode = SearchMode.ON_THE_WAY, startName = "출발", endName = "목적지",
        deadlineMinutesFromNow = 120, safetyBufferMinutes = 10, transportMode = TransportMode.CAR,
        arrivalDeadlineEpochMillis = deadline,
    )

    @Test fun `화면에서는 신선해도 실제 탭이 5분 경계를 넘으면 재확인한다`() {
        assertEquals(RouteNavigationAction.OPEN_ROUTE, routeNavigationAction(criteria(), listOf(stop()), calculated, calculated + 5 * minute - 1))
        assertEquals(RouteNavigationAction.RECHECK, routeNavigationAction(criteria(), listOf(stop()), calculated, calculated + 5 * minute))
    }

    @Test fun `재확인이 필요한데 도착 마감이 15분 미만이면 시간 재설정으로 안내한다`() {
        val tapTime = calculated + 5 * minute
        assertEquals(RouteNavigationAction.RECHECK, routeNavigationAction(criteria(tapTime + 15 * minute), listOf(stop()), calculated, tapTime))
        assertEquals(RouteNavigationAction.RESET_DEADLINE, routeNavigationAction(criteria(tapTime + 15 * minute - 1), listOf(stop()), calculated, tapTime))
        assertEquals(RouteNavigationAction.RESET_DEADLINE, routeNavigationAction(criteria(null), listOf(stop()), calculated, tapTime))
    }

    @Test fun `마감 후에도 경유하지 않는 목적지 직행은 막지 않는다`() {
        assertEquals(RouteNavigationAction.OPEN_ROUTE, routeNavigationAction(criteria(calculated), emptyList(), null, calculated + minute))
        assertEquals(RouteNavigationAction.RESET_DEADLINE, routeNavigationAction(criteria(calculated), listOf(stop()), calculated, calculated + minute))
    }
}

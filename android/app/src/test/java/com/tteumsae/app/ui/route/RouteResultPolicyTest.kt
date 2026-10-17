package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SafetyLevel
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.SearchMode
import com.tteumsae.app.domain.TransportMode
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResultPolicyTest {
    @Test
    fun `지도 핀은 추가 이동시간만 표시한다`() {
        assertEquals("+8분", detourPinLabel(recommendation("one", detour = 8)))
    }

    @Test
    fun `장소 선택은 항상 한 곳이며 같은 장소를 누르면 해제된다`() {
        assertEquals("one", nextSelectedPlaceId(null, "one"))
        assertEquals("two", nextSelectedPlaceId("one", "two"))
        assertNull(nextSelectedPlaceId("one", "one"))
    }

    @Test
    fun `선택 요약은 최대 체류와 출발 권장시각을 사용한다`() {
        val recommendation = recommendation("one", detour = 8)

        assertEquals(
            "여기서 최대 약 35분 머물 수 있어요",
            maximumStayLabel(recommendation),
        )
        assertEquals(
            "늦어도 오후 6시 20분 출발을 권장해요",
            latestDepartureLabel(
                recommendation,
                zoneId = ZoneId.of("Asia/Seoul"),
            ),
        )
    }

    @Test
    fun `제품 길 안내에는 선택한 경유지 한 곳만 전달한다`() {
        val recommendations = listOf(
            recommendation("one", 5),
            recommendation("two", 8),
        )

        assertEquals(
            listOf("two"),
            productNavigationRecommendations("two", recommendations).map { it.place.id },
        )
        assertEquals(emptyList<SafeRecommendation>(), productNavigationRecommendations(null, recommendations))
    }

    @Test
    fun `출발 권장 시각은 경계 시각부터 지난 것으로 본다`() {
        val recommendation = recommendation("one", detour = 5)

        assertFalse(recommendationDepartureHasPassed(recommendation, 1_787_908_799_999))
        assertTrue(recommendationDepartureHasPassed(recommendation, 1_787_908_800_000))
    }

    @Test
    fun `도착 마감은 경계 시각부터 지난 것으로 본다`() {
        val criteria = SearchCriteria(
            mode = SearchMode.ON_THE_WAY,
            startName = "출발지",
            endName = "목적지",
            deadlineMinutesFromNow = 60,
            safetyBufferMinutes = 10,
            transportMode = TransportMode.CAR,
            arrivalDeadlineEpochMillis = 2_000,
        )

        assertFalse(arrivalDeadlineHasPassed(criteria, 1_999))
        assertTrue(arrivalDeadlineHasPassed(criteria, 2_000))
    }

    @Test
    fun `도착 마감이 15분보다 적게 남으면 재검색할 수 없다`() {
        val criteria = SearchCriteria(
            mode = SearchMode.ON_THE_WAY,
            startName = "출발지",
            endName = "목적지",
            deadlineMinutesFromNow = 60,
            safetyBufferMinutes = 10,
            transportMode = TransportMode.CAR,
            arrivalDeadlineEpochMillis = 1_000_000,
        )

        assertFalse(arrivalDeadlineCannotBeRechecked(criteria, 100_000))
        assertTrue(arrivalDeadlineCannotBeRechecked(criteria, 100_001))
    }

    private fun recommendation(id: String, detour: Int) = SafeRecommendation(
        place = PlaceCandidate(
            id = id,
            name = id,
            category = PlaceCategory.CAFE,
            stayMinutes = 0,
            firstLegMinutes = 10,
            secondLegMinutes = 10,
            detourMinutes = detour,
            reason = "",
            tags = emptyList(),
        ),
        totalMinutes = 20,
        marginMinutes = 35,
        safetyLevel = SafetyLevel.AVAILABLE,
        minimumStayMinutes = 15,
        maximumStayMinutes = 35,
        latestDepartureEpochMillis = 1_787_908_800_000,
    )
}

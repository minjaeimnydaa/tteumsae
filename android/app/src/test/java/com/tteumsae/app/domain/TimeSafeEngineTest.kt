package com.tteumsae.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSafeEngineTest {
    private val place = PlaceCandidate(
        id = "test",
        name = "테스트 장소",
        category = PlaceCategory.CAFE,
        stayMinutes = 30,
        firstLegMinutes = 10,
        secondLegMinutes = 10,
        detourMinutes = 5,
        reason = "테스트",
        tags = emptyList(),
    )

    private fun criteria(
        deadline: Int = 90,
        buffer: Int = 15,
        categories: Set<PlaceCategory> = emptySet(),
    ) = SearchCriteria(
        mode = SearchMode.NEARBY,
        startName = "현재 위치",
        endName = "현재 위치",
        deadlineMinutesFromNow = deadline,
        safetyBufferMinutes = buffer,
        transportMode = TransportMode.WALK,
        categories = categories,
    )

    @Test
    fun `안전 여유시간 이상인 장소만 추천한다`() {
        assertEquals(1, TimeSafeEngine.recommend(criteria(), listOf(place)).size)
        assertTrue(
            TimeSafeEngine.recommend(
                criteria(deadline = 60, buffer = 15),
                listOf(place),
            ).isEmpty(),
        )
    }

    @Test
    fun `선택한 카테고리와 영업 상태를 적용한다`() {
        assertTrue(
            TimeSafeEngine.recommend(
                criteria(categories = setOf(PlaceCategory.CULTURE)),
                listOf(place),
            ).isEmpty(),
        )
        assertTrue(
            TimeSafeEngine.recommend(
                criteria(),
                listOf(place.copy(isOpen = false)),
            ).isEmpty(),
        )
    }

    @Test
    fun `여유시간에 맞는 안전도를 부여한다`() {
        assertEquals(SafetyLevel.COMFORTABLE, TimeSafeEngine.safetyLevel(20))
        assertEquals(SafetyLevel.AVAILABLE, TimeSafeEngine.safetyLevel(10))
        assertEquals(SafetyLevel.TIGHT, TimeSafeEngine.safetyLevel(9))
    }
}

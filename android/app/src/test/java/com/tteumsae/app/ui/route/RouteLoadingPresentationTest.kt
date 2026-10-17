package com.tteumsae.app.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RouteLoadingPresentationTest {
    @Test
    fun `추천이 없으면 완료 상태에서도 장소를 찾았다고 표시하지 않는다`() {
        val presentation = routeLoadingPresentation(
            completed = true,
            hasRecommendations = false,
            isTakingLonger = false,
        )

        assertEquals("경로를 확인했어요", presentation.title)
        assertFalse(presentation.message.contains("찾았"))
    }

    @Test
    fun `응답을 기다리는 동안 후보 여부에 관계없이 검색 중 상태를 유지한다`() {
        val waiting = routeLoadingPresentation(false, false, false)
        val waitingWithOldResults = routeLoadingPresentation(false, true, false)

        assertEquals(waiting, waitingWithOldResults)
        assertEquals("가는 길 주변을 찾고 있어요", waiting.title)
    }

    @Test
    fun `오래 걸려도 완료를 추측하지 않고 대기 안내만 바꾼다`() {
        val waiting = routeLoadingPresentation(false, false, false)
        val delayed = routeLoadingPresentation(false, false, true)

        assertEquals(waiting.title, delayed.title)
        assertEquals("응답이 조금 늦어지고 있어요. 잠시 기다리거나 검색을 취소할 수 있어요.", delayed.message)
    }
}

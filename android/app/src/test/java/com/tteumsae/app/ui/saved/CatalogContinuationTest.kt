package com.tteumsae.app.ui.saved

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogContinuationTest {
    @Test fun `continuation distinguishes loading failure and end`() {
        assertEquals("아래로 스크롤하면 더 많은 장소를 볼 수 있어요", catalogContinuationLabel(true, false, false))
        assertEquals("장소를 더 불러오고 있어요", catalogContinuationLabel(true, true, false))
        assertEquals("추가 장소를 불러오지 못했어요 · 다시 시도해 주세요", catalogContinuationLabel(true, false, true))
        assertEquals("마지막 장소까지 확인했어요", catalogContinuationLabel(false, false, false))
    }
}

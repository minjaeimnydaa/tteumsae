package com.tteumsae.app.ui.saved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPresentationPolicyTest {
    @Test fun `filtered empty page stays searching until remaining pages finish`() {
        assertEquals(CatalogUiState.SEARCHING, state(hasMore = true))
        assertEquals(CatalogUiState.SEARCHING, state(loadingMore = true, hasMore = true))
        assertEquals(CatalogUiState.EMPTY, state())
    }

    @Test fun `failed continuation has retry state even without visible matches`() {
        assertEquals(CatalogUiState.CONTINUATION_FAILURE, state(hasMore = true, loadMoreFailed = true))
        assertFalse(shouldLoad(hasMore = true, loadMoreFailed = true))
        assertTrue(shouldLoad(hasMore = true))
    }

    @Test fun `saved only uses local data during catalog loading and failure`() {
        assertEquals(CatalogUiState.EMPTY, state(savedOnly = true, initialLoading = true, hasMore = true))
        assertEquals(CatalogUiState.RESULTS, state(savedOnly = true, visibleCount = 2, initialFailed = true))
        assertFalse(shouldLoad(savedOnly = true, hasMore = true))
    }

    @Test fun `initial request and continuation have distinct recovery states`() {
        assertEquals(CatalogUiState.LOADING, state(initialLoading = true))
        assertEquals(CatalogUiState.INITIAL_FAILURE, state(initialFailed = true))
        assertEquals(CatalogUiState.RESULTS, state(visibleCount = 1, hasMore = true, loadMoreFailed = true))
        assertFalse(shouldLoad(hasMore = true, initialLoading = true))
        assertFalse(shouldLoad(hasMore = true, initialFailed = true))
        assertFalse(shouldLoad(hasMore = true, loadingMore = true))
    }

    @Test fun `catalog loads missing matches or near the end and stops at final page`() {
        assertTrue(shouldLoad(hasMore = true))
        assertFalse(shouldLoad(hasMore = true, visibleCount = 40))
        assertTrue(shouldLoad(hasMore = true, visibleCount = 40, nearListEnd = true))
        assertFalse(shouldLoad(nearListEnd = true))
    }

    @Test fun `normal 360dp cards fit two columns and large fonts get more width`() {
        val availableWidth = 360f - 24f * 2
        val gap = 12f
        val normalMinimum = catalogCardMinWidthDp(144f, 1f)
        val largeMinimum = catalogCardMinWidthDp(144f, 1.3f)
        assertTrue(normalMinimum * 2 + gap <= availableWidth)
        assertTrue(largeMinimum * 2 + gap > availableWidth)
        assertEquals(144f, catalogCardMinWidthDp(144f, 0.85f), 0.01f)
        assertEquals(288f, catalogCardMinWidthDp(144f, 2f), 0.01f)
    }

    private fun state(
        savedOnly: Boolean = false,
        visibleCount: Int = 0,
        initialLoading: Boolean = false,
        initialFailed: Boolean = false,
        hasMore: Boolean = false,
        loadingMore: Boolean = false,
        loadMoreFailed: Boolean = false,
    ) = catalogUiState(savedOnly, visibleCount, initialLoading, initialFailed, hasMore, loadingMore, loadMoreFailed)

    private fun shouldLoad(
        savedOnly: Boolean = false,
        visibleCount: Int = 0,
        initialLoading: Boolean = false,
        initialFailed: Boolean = false,
        hasMore: Boolean = false,
        loadingMore: Boolean = false,
        loadMoreFailed: Boolean = false,
        nearListEnd: Boolean = false,
    ) = shouldLoadCatalogMore(savedOnly, visibleCount, initialLoading, initialFailed, hasMore, loadingMore, loadMoreFailed, nearListEnd)
}

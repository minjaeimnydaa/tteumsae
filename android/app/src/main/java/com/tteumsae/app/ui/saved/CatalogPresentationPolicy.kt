package com.tteumsae.app.ui.saved

internal enum class CatalogUiState {
    LOADING,
    INITIAL_FAILURE,
    SEARCHING,
    CONTINUATION_FAILURE,
    EMPTY,
    RESULTS,
}

/** A partial catalog cannot confirm an empty search; saved places are already local. */
internal fun catalogUiState(
    savedOnly: Boolean,
    visibleCount: Int,
    initialLoading: Boolean,
    initialFailed: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMoreFailed: Boolean,
): CatalogUiState = when {
    savedOnly -> if (visibleCount == 0) CatalogUiState.EMPTY else CatalogUiState.RESULTS
    initialLoading -> CatalogUiState.LOADING
    initialFailed -> CatalogUiState.INITIAL_FAILURE
    visibleCount > 0 -> CatalogUiState.RESULTS
    loadMoreFailed -> CatalogUiState.CONTINUATION_FAILURE
    hasMore || loadingMore -> CatalogUiState.SEARCHING
    else -> CatalogUiState.EMPTY
}

internal fun shouldLoadCatalogMore(
    savedOnly: Boolean,
    visibleCount: Int,
    initialLoading: Boolean,
    initialFailed: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMoreFailed: Boolean,
    nearListEnd: Boolean,
): Boolean = !savedOnly &&
    !initialLoading &&
    !initialFailed &&
    !loadingMore &&
    !loadMoreFailed &&
    hasMore &&
    (visibleCount == 0 || nearListEnd)

/** Keep a card's text area growing with the user's font size, without shrinking normal cards. */
internal fun catalogCardMinWidthDp(baseCardMinWidthDp: Float, fontScale: Float): Float =
    baseCardMinWidthDp * fontScale.coerceAtLeast(1f)

package com.tteumsae.app.ui.saved

internal fun catalogContinuationLabel(hasMore: Boolean, loading: Boolean, failed: Boolean): String = when {
    failed -> "추가 장소를 불러오지 못했어요 · 다시 시도해 주세요"
    loading -> "장소를 더 불러오고 있어요"
    hasMore -> "아래로 스크롤하면 더 많은 장소를 볼 수 있어요"
    else -> "마지막 장소까지 확인했어요"
}

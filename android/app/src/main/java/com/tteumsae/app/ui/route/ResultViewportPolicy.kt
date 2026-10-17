package com.tteumsae.app.ui.route

internal fun resultViewActionLabel(showingMap: Boolean): String =
    if (showingMap) "목록 보기" else "지도 보기"

internal fun resultFreshnessLabel(calculatedAt: Long?, now: Long): String {
    if (calculatedAt == null || calculatedAt > now) return "계산 시각 확인 필요"
    val elapsed = (now - calculatedAt) / 60_000L
    return if (elapsed < 1) "계산 직후 출발 기준"
    else "계산 후 ${elapsed}분 지남 · 다시 확인 권장"
}

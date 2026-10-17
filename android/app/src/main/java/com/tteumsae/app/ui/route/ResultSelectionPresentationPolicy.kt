package com.tteumsae.app.ui.route

internal fun resultSelectionActionLabel(selected: Boolean): String =
    if (selected) "선택됨" else "선택"

internal fun resultSelectionStateDescription(selected: Boolean): String =
    if (selected) "선택됨" else "선택 안 됨"

/** Reserve the longer selected label even before selection, so neither action moves. */
internal fun resultCandidateActionsStack(availableWidthDp: Float, fontScale: Float): Boolean {
    val textScale = fontScale.coerceAtLeast(1f)
    // 14sp labels, 16dp side insets, plus the 20dp check and 8dp inline gap.
    val selectionWidth = maxOf(124f, 42f * textScale + 60f)
    val detailWidth = maxOf(120f, 64f * textScale + 32f)
    val equalWidthActions = 2f * maxOf(selectionWidth, detailWidth) + 12f
    return availableWidthDp < equalWidthActions
}

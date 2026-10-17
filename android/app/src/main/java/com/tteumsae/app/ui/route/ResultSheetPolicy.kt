package com.tteumsae.app.ui.route

internal enum class ResultSheetPosition { MAP, BALANCED, LIST }

internal fun resultSheetViewportPosition(
    requested: ResultSheetPosition,
    settled: ResultSheetPosition,
    gestureTarget: ResultSheetPosition,
): ResultSheetPosition = if (requested != settled) requested else gestureTarget

internal data class ResultSheetGeometry(
    val availableHeightDp: Float,
    val expandedOffsetDp: Float,
    val balancedOffsetDp: Float,
    val collapsedOffsetDp: Float,
)

internal fun resultSheetGeometry(availableHeightDp: Float, headerHeightDp: Float): ResultSheetGeometry {
    val height = availableHeightDp.takeIf { it.isFinite() && it > 0f } ?: 1f
    val header = headerHeightDp.coerceAtLeast(1f).coerceAtMost(height * 0.8f)
    val collapsed = height - header
    return ResultSheetGeometry(
        availableHeightDp = height,
        expandedOffsetDp = 0f,
        balancedOffsetDp = (height * 0.48f).coerceAtMost(collapsed * 0.8f),
        collapsedOffsetDp = collapsed,
    )
}

// A covered map retains a valid viewport. Sheet animations must not trigger camera fits.
internal fun resultMapObscuredHeightDp(
    geometry: ResultSheetGeometry,
    position: ResultSheetPosition,
): Float = geometry.availableHeightDp - when (position) {
    ResultSheetPosition.MAP -> geometry.collapsedOffsetDp
    ResultSheetPosition.BALANCED, ResultSheetPosition.LIST -> geometry.balancedOffsetDp
}

internal fun resultCandidateRevealIndex(ids: List<String>, selectedId: String, hasWarning: Boolean): Int? =
    ids.indexOf(selectedId).takeIf { it >= 0 }?.let { it + if (hasWarning) 1 else 0 }

internal data class ResultMapViewportInsets(val topDp: Float, val bottomDp: Float)

private const val OVERVIEW_BUTTON_TOP_DP = 12f
private const val OVERVIEW_BUTTON_HEIGHT_DP = 48f
private const val TALLEST_CANDIDATE_MARKER_DP = 56f
private const val MAP_CONTROL_MARKER_GAP_DP = 12f
private const val CLUSTER_CAMERA_FIT_PADDING_DP = 32f
// fitMapPoints positions the coordinate, while the bottom-anchored pin body extends above it.
private const val RESULT_MAP_TOP_CLEARANCE_DP = OVERVIEW_BUTTON_TOP_DP + OVERVIEW_BUTTON_HEIGHT_DP +
    TALLEST_CANDIDATE_MARKER_DP + MAP_CONTROL_MARKER_GAP_DP - CLUSTER_CAMERA_FIT_PADDING_DP

internal fun resultMapViewportInsets(availableHeightDp: Float, obscuredHeightDp: Float): ResultMapViewportInsets {
    val height = availableHeightDp.coerceAtLeast(1f)
    val top = minOf(RESULT_MAP_TOP_CLEARANCE_DP, height * 0.2f)
    val visibleMinimum = minOf(96f, height * 0.5f)
    return ResultMapViewportInsets(top, (obscuredHeightDp + 16f).coerceIn(0f, height - top - visibleMinimum))
}

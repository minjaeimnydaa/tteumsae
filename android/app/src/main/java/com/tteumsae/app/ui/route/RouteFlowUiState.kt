package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.RouteSummary
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.route.RouteFlowInput

enum class RouteStage {
    LOCATION,
    LOADING,
    RESULTS,
}

data class RouteFlowUiState(
    val input: RouteFlowInput = RouteFlowInput(),
    val stage: RouteStage = RouteStage.LOCATION,
    val recommendations: List<SafeRecommendation> = emptyList(),
    val baseRoute: RouteSummary? = null,
    val selectedPlaceId: String? = null,
    val calculatedAtEpochMillis: Long? = null,
    val corridorRadiusMeters: Int = 1_600,
    val warning: String = "",
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
    val isEditing: Boolean = false,
)

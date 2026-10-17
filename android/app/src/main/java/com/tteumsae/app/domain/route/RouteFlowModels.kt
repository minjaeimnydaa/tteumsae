package com.tteumsae.app.domain.route

import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.TransportMode

data class RouteLocation(
    val name: String,
    val coordinates: Coordinates,
)

data class RouteFlowInput(
    val start: RouteLocation? = null,
    val destination: RouteLocation? = null,
    val arrivalDeadlineEpochMillis: Long? = null,
    val transportMode: TransportMode = TransportMode.CAR,
    val categories: Set<PlaceCategory> = emptySet(),
)

data class SelectedStopTiming(
    val minimumStayMinutes: Int,
    val maximumStayMinutes: Int,
    val latestDepartureEpochMillis: Long,
)

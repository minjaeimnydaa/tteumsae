package com.tteumsae.app.data.route

import com.tteumsae.app.data.RecommendationResult
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.RouteSummary
import com.tteumsae.app.domain.SearchCriteria

data class RouteWaypoint(
    val id: String,
    val coordinates: Coordinates,
)

interface RouteGateway {
    suspend fun recommendations(criteria: SearchCriteria): RecommendationResult

    suspend fun calculateRoute(
        start: Coordinates,
        destination: Coordinates,
        waypoints: List<RouteWaypoint>,
    ): RouteSummary
}

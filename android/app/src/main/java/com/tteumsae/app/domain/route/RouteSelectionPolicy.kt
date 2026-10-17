package com.tteumsae.app.domain.route

import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.SafeRecommendation

internal fun orderWaypointIdsAlongRoute(
    start: Coordinates,
    destination: Coordinates,
    waypoints: List<Pair<String, Coordinates>>,
): List<String> {
    val latitudeDelta = destination.latitude - start.latitude
    val longitudeDelta = destination.longitude - start.longitude
    val denominator = latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta
    if (denominator == 0.0) return waypoints.map { it.first }
    return waypoints.sortedBy { (_, point) ->
        ((point.latitude - start.latitude) * latitudeDelta +
            (point.longitude - start.longitude) * longitudeDelta) / denominator
    }.map(Pair<String, Coordinates>::first)
}

internal fun selectedRouteEstimate(
    deadlineMinutes: Int,
    recommendations: List<SafeRecommendation>,
): Pair<Int, Int> {
    if (recommendations.isEmpty()) return 0 to deadlineMinutes
    val first = recommendations.first().place
    val directMinutes = (first.firstLegMinutes + first.secondLegMinutes - first.detourMinutes)
        .coerceAtLeast(0)
    val totalMinutes = directMinutes + recommendations.sumOf {
        it.place.detourMinutes + it.place.stayMinutes
    }
    return totalMinutes to (deadlineMinutes - totalMinutes)
}

internal fun isRouteWithinExtraTimeBudget(
    baseDrivingMinutes: Int,
    extraTimeMinutes: Int,
    selectedDrivingMinutes: Int,
    selectedStayMinutes: Int,
    safetyBufferMinutes: Int,
): Boolean = selectedDrivingMinutes + selectedStayMinutes + safetyBufferMinutes <=
    baseDrivingMinutes + extraTimeMinutes

internal fun additionalDetourDistanceMeters(
    place: PlaceCandidate,
    baseDistanceMeters: Int,
): Int? {
    if (baseDistanceMeters <= 0 || place.firstLegDistanceMeters <= 0 || place.secondLegDistanceMeters <= 0) {
        return null
    }
    return (place.firstLegDistanceMeters + place.secondLegDistanceMeters - baseDistanceMeters)
        .coerceAtLeast(0)
}

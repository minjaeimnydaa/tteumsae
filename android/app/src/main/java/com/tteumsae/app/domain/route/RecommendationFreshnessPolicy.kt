package com.tteumsae.app.domain.route

import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SearchCriteria

private const val MILLIS_PER_MINUTE = 60_000L
private const val MAXIMUM_RESULT_AGE_MILLIS = 5 * MILLIS_PER_MINUTE
private const val CLOCK_SKEW_TOLERANCE_MILLIS = MILLIS_PER_MINUTE

/** Checks whether a snapshot can still be used to start a detour, not to leave its waypoint. */
internal fun recommendationNeedsRecheck(
    recommendation: SafeRecommendation,
    calculatedAtEpochMillis: Long?,
    nowEpochMillis: Long,
): Boolean {
    val calculatedAt = calculatedAtEpochMillis ?: return true
    val latestWaypointDeparture = recommendation.latestDepartureEpochMillis ?: return true
    val maximumStay = recommendation.maximumStayMinutes ?: return true
    val minimumStay = (recommendation.minimumStayMinutes ?: MINIMUM_STAY_MINUTES)
        .coerceAtLeast(MINIMUM_STAY_MINUTES)
    val firstLeg = recommendation.place.firstLegMinutes
    if (calculatedAt <= 0L || nowEpochMillis <= 0L || latestWaypointDeparture <= 0L ||
        firstLeg < 0 || maximumStay < minimumStay
    ) return true

    val elapsedMillis = nowEpochMillis - calculatedAt
    if (elapsedMillis < -CLOCK_SKEW_TOLERANCE_MILLIS ||
        elapsedMillis >= MAXIMUM_RESULT_AGE_MILLIS
    ) return true

    // The server's deadline is when to LEAVE the waypoint. The user still needs
    // to drive there and spend at least the minimum stay before that deadline.
    val latestOriginDeparture = latestWaypointDeparture -
        (firstLeg.toLong() + minimumStay) * MILLIS_PER_MINUTE
    if (nowEpochMillis > latestOriginDeparture) return true

    // The maximum stay may have been capped by closing hours. Keep its original
    // value as a labelled snapshot; only use elapsed whole minutes as a guard.
    val elapsedWholeMinutes = elapsedMillis.coerceAtLeast(0L) / MILLIS_PER_MINUTE
    return maximumStay.toLong() - elapsedWholeMinutes < minimumStay
}

internal enum class RouteNavigationAction { OPEN_ROUTE, RECHECK, RESET_DEADLINE }

/** Re-evaluated both for button presentation and at the actual navigation tap. */
internal fun routeNavigationAction(
    criteria: SearchCriteria,
    recommendations: List<SafeRecommendation>,
    calculatedAtEpochMillis: Long?,
    nowEpochMillis: Long,
): RouteNavigationAction {
    // A direct route is always a useful escape, including after the deadline.
    if (recommendations.isEmpty()) return RouteNavigationAction.OPEN_ROUTE
    val deadline = criteria.arrivalDeadlineEpochMillis
        ?: return RouteNavigationAction.RESET_DEADLINE
    if (deadline <= nowEpochMillis) return RouteNavigationAction.RESET_DEADLINE
    if (recommendations.none {
            recommendationNeedsRecheck(it, calculatedAtEpochMillis, nowEpochMillis)
        }
    ) return RouteNavigationAction.OPEN_ROUTE
    return if (deadline - nowEpochMillis < MINIMUM_STAY_MINUTES * MILLIS_PER_MINUTE) {
        RouteNavigationAction.RESET_DEADLINE
    } else {
        RouteNavigationAction.RECHECK
    }
}

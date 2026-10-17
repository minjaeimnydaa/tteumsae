package com.tteumsae.app.ui.route

internal data class RouteDetailRequestKey(
    val placeId: String,
    val expectedArrivalEpochMillis: Long?,
)

internal fun routeDetailRequestKey(
    placeId: String,
    calculatedAtEpochMillis: Long?,
    firstLegMinutes: Int,
): RouteDetailRequestKey = RouteDetailRequestKey(
    placeId,
    calculatedAtEpochMillis?.plus(firstLegMinutes.toLong() * 60_000L),
)

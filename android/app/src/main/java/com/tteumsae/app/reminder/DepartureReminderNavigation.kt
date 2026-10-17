package com.tteumsae.app.reminder

import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.platform.buildKakaoMapMultiRouteUrl

/** The reminder fires after the stay: its route starts at the stop, not the trip's original start. */
internal fun buildDepartureReminderNavigationUrl(
    stopName: String,
    stop: Coordinates,
    destinationName: String,
    destination: Coordinates,
): String = buildKakaoMapMultiRouteUrl(
    startName = stopName,
    start = stop,
    waypoints = emptyList(),
    destinationName = destinationName,
    destination = destination,
)

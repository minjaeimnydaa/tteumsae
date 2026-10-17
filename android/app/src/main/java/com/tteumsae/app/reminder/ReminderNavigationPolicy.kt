package com.tteumsae.app.reminder

import com.tteumsae.app.location.LocationAccessPolicy

internal fun navigationUrlForReminderTap(
    trip: ActiveTrip?,
    requestedMode: String?,
    requestedToken: String?,
    nowEpochMillis: Long,
    currentMode: String = LocationAccessPolicy.persistenceMode,
): String? {
    if (trip == null || trip.locationMode != currentMode || requestedMode != currentMode) return null
    if (trip.navigationToken.isBlank() || requestedToken != trip.navigationToken) return null
    if (nowEpochMillis >= trip.latestDepartureEpochMillis || nowEpochMillis >= trip.expiresAtEpochMillis) return null
    return trip.navigationUrl
}

package com.tteumsae.app.domain.route

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

const val SAFETY_BUFFER_MINUTES = 10
const val MINIMUM_STAY_MINUTES = 15
const val MAXIMUM_DEADLINE_MINUTES = 24 * 60
private const val STAY_ROUNDING_MINUTES = 5
private const val MILLIS_PER_MINUTE = 60_000L

internal fun resolveArrivalDeadline(
    selectedHour: Int,
    selectedMinute: Int,
    nowEpochMillis: Long,
    zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
): Long {
    require(selectedHour in 0..23) { "selectedHour must be between 0 and 23" }
    require(selectedMinute in 0..59) { "selectedMinute must be between 0 and 59" }

    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
    var candidate = ZonedDateTime.of(
        now.toLocalDate(),
        java.time.LocalTime.of(selectedHour, selectedMinute),
        zoneId,
    )
    if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
    return candidate.toInstant().toEpochMilli()
}

internal fun remainingWholeMinutes(
    arrivalDeadlineEpochMillis: Long,
    nowEpochMillis: Long,
): Int = ((arrivalDeadlineEpochMillis - nowEpochMillis) / MILLIS_PER_MINUTE).toInt()

internal fun isValidArrivalDeadline(
    arrivalDeadlineEpochMillis: Long,
    nowEpochMillis: Long,
): Boolean {
    val remainingMillis = arrivalDeadlineEpochMillis - nowEpochMillis
    return remainingMillis in
        MINIMUM_STAY_MINUTES * MILLIS_PER_MINUTE..MAXIMUM_DEADLINE_MINUTES * MILLIS_PER_MINUTE
}

internal fun floorToFiveMinutes(minutes: Int): Int =
    Math.floorDiv(minutes, STAY_ROUNDING_MINUTES) * STAY_ROUNDING_MINUTES

internal fun selectedStopTiming(
    nowEpochMillis: Long,
    arrivalDeadlineEpochMillis: Long,
    firstLegMinutes: Int,
    secondLegMinutes: Int,
): SelectedStopTiming? {
    val maximumStayMinutes = floorToFiveMinutes(
        remainingWholeMinutes(arrivalDeadlineEpochMillis, nowEpochMillis) -
            firstLegMinutes -
            secondLegMinutes -
            SAFETY_BUFFER_MINUTES,
    )
    if (maximumStayMinutes < MINIMUM_STAY_MINUTES) return null

    return SelectedStopTiming(
        minimumStayMinutes = MINIMUM_STAY_MINUTES,
        maximumStayMinutes = maximumStayMinutes,
        latestDepartureEpochMillis = arrivalDeadlineEpochMillis -
            (secondLegMinutes + SAFETY_BUFFER_MINUTES) * MILLIS_PER_MINUTE,
    )
}

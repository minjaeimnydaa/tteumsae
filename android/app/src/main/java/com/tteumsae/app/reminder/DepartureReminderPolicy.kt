package com.tteumsae.app.reminder

const val DEPARTURE_REMINDER_LEAD_MINUTES = 5
private const val MILLIS_PER_MINUTE = 60_000L

sealed interface DepartureReminderDecision {
    data class Schedule(val triggerAtEpochMillis: Long) : DepartureReminderDecision
    data object NotifyNow : DepartureReminderDecision
    data object Skip : DepartureReminderDecision
}

internal fun departureReminderDecision(
    latestDepartureEpochMillis: Long,
    nowEpochMillis: Long,
): DepartureReminderDecision {
    if (nowEpochMillis >= latestDepartureEpochMillis) return DepartureReminderDecision.Skip
    val trigger = latestDepartureEpochMillis -
        DEPARTURE_REMINDER_LEAD_MINUTES * MILLIS_PER_MINUTE
    return if (nowEpochMillis < trigger) {
        DepartureReminderDecision.Schedule(trigger)
    } else {
        DepartureReminderDecision.NotifyNow
    }
}

internal fun shouldRescheduleTrip(
    latestDepartureEpochMillis: Long,
    expiresAtEpochMillis: Long,
    nowEpochMillis: Long,
): Boolean = nowEpochMillis < latestDepartureEpochMillis && nowEpochMillis < expiresAtEpochMillis

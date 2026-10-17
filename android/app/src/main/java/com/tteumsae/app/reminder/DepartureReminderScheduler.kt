package com.tteumsae.app.reminder

interface DepartureReminderScheduler {
    fun schedule(trip: ActiveTrip, nowEpochMillis: Long = System.currentTimeMillis())
    fun cancel()
}

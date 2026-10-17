package com.tteumsae.app.reminder

class DepartureReminderCoordinator(
    private val store: ActiveTripStore,
    private val scheduler: DepartureReminderScheduler,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    fun enable(trip: ActiveTrip): String? {
        val now = nowEpochMillis()
        if (!store.isCompatible(trip) || trip.latestDepartureEpochMillis <= now || trip.expiresAtEpochMillis <= now) {
            clear()
            return null
        }
        store.save(trip)
        scheduler.schedule(trip, now)
        return trip.stopId
    }

    fun clear() {
        scheduler.cancel()
        store.clear()
    }

    fun reconcile(updatedTrip: ActiveTrip?): String? {
        val currentStopId = currentEnabledStopId() ?: return null
        if (updatedTrip == null || updatedTrip.stopId != currentStopId) {
            clear()
            return null
        }
        return enable(updatedTrip)
    }

    fun currentEnabledStopId(): String? = currentValidTrip(nowEpochMillis())?.stopId

    // Only a notification that actually failed delivery may be restored. Keeping
    // that record in the store also covers a new process after settings changed.
    fun reschedulePendingDelivery(deliveryAvailable: Boolean): String? {
        val now = nowEpochMillis()
        currentValidTrip(now) ?: return null
        if (!deliveryAvailable) return null
        val trip = store.takePendingDelivery(now) ?: return null
        scheduler.schedule(trip, now)
        return trip.stopId
    }

    private fun currentValidTrip(now: Long): ActiveTrip? {
        val current = store.loadValid(now)
        if (current == null || current.latestDepartureEpochMillis <= now) {
            clear()
            return null
        }
        return current
    }
}

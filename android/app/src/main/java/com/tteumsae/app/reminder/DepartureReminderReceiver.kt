package com.tteumsae.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DepartureReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManagerDepartureReminderScheduler.ACTION_DEPARTURE_REMINDER) return
        val store = ActiveTripStore(SharedPreferencesActiveTripPreferences(context.applicationContext))
        val scheduler = AlarmManagerDepartureReminderScheduler(context.applicationContext)
        val coordinator = DepartureReminderCoordinator(store, scheduler)
        if (coordinator.currentEnabledStopId() == null) return
        val trip = store.loadValid()
        if (trip == null || System.currentTimeMillis() >= trip.latestDepartureEpochMillis) {
            coordinator.clear()
            return
        }
        if (
            navigationUrlForReminderTap(
                trip,
                intent.getStringExtra(ReminderNavigationActivity.EXTRA_LOCATION_MODE),
                intent.getStringExtra(ReminderNavigationActivity.EXTRA_NAVIGATION_TOKEN),
                System.currentTimeMillis(),
            ) == null
        ) return
        ReminderNotifications.show(context, trip)
    }
}

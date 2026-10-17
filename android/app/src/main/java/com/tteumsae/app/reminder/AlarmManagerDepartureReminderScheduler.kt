package com.tteumsae.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.tteumsae.app.location.LocationAccessPolicy

class AlarmManagerDepartureReminderScheduler(
    private val context: Context,
) : DepartureReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(trip: ActiveTrip, nowEpochMillis: Long) {
        if (
            trip.locationMode != LocationAccessPolicy.persistenceMode || trip.navigationToken.isBlank() ||
            trip.expiresAtEpochMillis <= nowEpochMillis
        ) {
            cancel()
            return
        }
        when (
            val decision = departureReminderDecision(
                latestDepartureEpochMillis = trip.latestDepartureEpochMillis,
                nowEpochMillis = nowEpochMillis,
            )
        ) {
            is DepartureReminderDecision.Schedule -> alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                decision.triggerAtEpochMillis,
                reminderPendingIntent(trip),
            )
            DepartureReminderDecision.NotifyNow -> ReminderNotifications.show(context, trip)
            DepartureReminderDecision.Skip -> cancel()
        }
    }

    override fun cancel() {
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            reminderIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pending ->
            alarmManager.cancel(pending)
            pending.cancel()
        }
        ReminderNotifications.cancel(context)
    }

    private fun reminderPendingIntent(trip: ActiveTrip): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        reminderIntent()
            .putExtra(ReminderNavigationActivity.EXTRA_LOCATION_MODE, trip.locationMode)
            .putExtra(ReminderNavigationActivity.EXTRA_NAVIGATION_TOKEN, trip.navigationToken),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun reminderIntent(): Intent =
        Intent(context, DepartureReminderReceiver::class.java).setAction(ACTION_DEPARTURE_REMINDER)

    companion object {
        const val ACTION_DEPARTURE_REMINDER = "com.tteumsae.app.action.DEPARTURE_REMINDER"
        private const val REQUEST_CODE = 4105
    }
}

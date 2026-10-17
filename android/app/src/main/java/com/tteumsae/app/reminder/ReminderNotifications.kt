package com.tteumsae.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.tteumsae.app.R

object ReminderNotifications {
    private const val CHANNEL_ID = "departure_reminders"
    private const val NOTIFICATION_ID = 4105

    fun deliveryBlockReason(context: Context): ReminderDeliveryBlockReason? {
        val manager = context.getSystemService(NotificationManager::class.java)
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val channelEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            manager.getNotificationChannel(CHANNEL_ID)?.importance?.let {
                it != NotificationManager.IMPORTANCE_NONE
            } == true
        return reminderDeliveryBlockReason(
            runtimePermissionGranted,
            manager.areNotificationsEnabled(),
            channelEnabled,
        )
    }

    fun canDeliver(context: Context): Boolean = deliveryBlockReason(context) == null

    fun openSettings(context: Context, reason: ReminderDeliveryBlockReason? = null): Boolean {
        val action = if (reason == ReminderDeliveryBlockReason.CHANNEL_DISABLED) {
            Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
        } else {
            Settings.ACTION_APP_NOTIFICATION_SETTINGS
        }
        val settingsIntent = Intent(action)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (reason == ReminderDeliveryBlockReason.CHANNEL_DISABLED) {
            settingsIntent.putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        }
        return try {
            context.startActivity(settingsIntent)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    fun createChannel(context: Context) {
        // Also runs after an APK update and before any Activity/Receiver restores an old trip.
        val applicationContext = context.applicationContext
        DepartureReminderCoordinator(
            ActiveTripStore(SharedPreferencesActiveTripPreferences(applicationContext)),
            AlarmManagerDepartureReminderScheduler(applicationContext),
        ).currentEnabledStopId()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "출발 시간 알림",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "선택한 경유지에서 출발해야 할 시각을 알려드려요."
            },
        )
    }

    fun show(context: Context, trip: ActiveTrip) {
        val now = System.currentTimeMillis()
        if (navigationUrlForReminderTap(trip, trip.locationMode, trip.navigationToken, now) == null) {
            cancel(context)
            return
        }
        val store = ActiveTripStore(SharedPreferencesActiveTripPreferences(context.applicationContext))
        if (!canDeliver(context)) {
            store.markDeliveryPending(trip, now)
            return
        }
        val navigationIntent = navigationIntent(context)
            .putExtra(ReminderNavigationActivity.EXTRA_LOCATION_MODE, trip.locationMode)
            .putExtra(ReminderNavigationActivity.EXTRA_NAVIGATION_TOKEN, trip.navigationToken)
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            navigationIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("이제 출발할 시간이에요")
            .setContentText("${trip.stopName}에서 출발해 목적지로 이동해 주세요.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
        store.clearDeliveryPending(trip)
    }

    internal fun cancel(context: Context, previousNavigationUrl: String? = null) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        PendingIntent.getActivity(
            context, NOTIFICATION_ID, navigationIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.cancel()
        previousNavigationUrl?.let { url ->
            PendingIntent.getActivity(
                context, NOTIFICATION_ID, Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.cancel()
        }
    }

    private fun navigationIntent(context: Context): Intent =
        Intent(context, ReminderNavigationActivity::class.java)
}

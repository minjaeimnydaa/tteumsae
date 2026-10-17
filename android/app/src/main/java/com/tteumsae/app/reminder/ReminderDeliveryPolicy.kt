package com.tteumsae.app.reminder

enum class ReminderDeliveryBlockReason {
    RUNTIME_PERMISSION,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
}

// Runtime denial must remain distinguishable from settings so the first toggle can
// still request POST_NOTIFICATIONS through the Activity permission launcher.
internal fun reminderDeliveryBlockReason(
    runtimePermissionGranted: Boolean,
    appNotificationsEnabled: Boolean,
    channelEnabled: Boolean,
): ReminderDeliveryBlockReason? = when {
    !runtimePermissionGranted -> ReminderDeliveryBlockReason.RUNTIME_PERMISSION
    !appNotificationsEnabled -> ReminderDeliveryBlockReason.APP_NOTIFICATIONS_DISABLED
    !channelEnabled -> ReminderDeliveryBlockReason.CHANNEL_DISABLED
    else -> null
}

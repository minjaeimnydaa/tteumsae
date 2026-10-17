package com.tteumsae.app.reminder

import android.annotation.SuppressLint
import android.content.Context
import com.tteumsae.app.location.LocationAccessPolicy
import org.json.JSONObject

interface ActiveTripPreferences {
    fun read(): String?
    fun write(value: String)
    fun readPendingDeliveryToken(): String?
    fun writePendingDeliveryToken(value: String?)
    fun clear()
}

class ActiveTripStore(
    private val preferences: ActiveTripPreferences,
    private val locationMode: String = LocationAccessPolicy.persistenceMode,
) {
    fun save(trip: ActiveTrip) {
        if (!isCompatible(trip)) {
            clear()
            return
        }
        preferences.writePendingDeliveryToken(null)
        preferences.write(ActiveTripCodec.encode(trip))
    }

    fun loadValid(nowEpochMillis: Long = System.currentTimeMillis()): ActiveTrip? {
        val trip = preferences.read()
            ?.let { runCatching { ActiveTripCodec.decode(it) }.getOrNull() }
        if (trip == null || !isCompatible(trip) || trip.expiresAtEpochMillis <= nowEpochMillis) {
            preferences.clear()
            return null
        }
        return trip
    }

    fun clear() = preferences.clear()

    internal fun markDeliveryPending(trip: ActiveTrip, nowEpochMillis: Long) {
        val current = loadValid(nowEpochMillis) ?: return
        if (current != trip || current.latestDepartureEpochMillis <= nowEpochMillis) return
        preferences.writePendingDeliveryToken(current.navigationToken)
    }

    internal fun clearDeliveryPending(trip: ActiveTrip) {
        if (preferences.readPendingDeliveryToken() == trip.navigationToken) {
            preferences.writePendingDeliveryToken(null)
        }
    }

    internal fun takePendingDelivery(nowEpochMillis: Long): ActiveTrip? {
        val current = loadValid(nowEpochMillis) ?: return null
        if (current.latestDepartureEpochMillis <= nowEpochMillis) {
            clear()
            return null
        }
        if (preferences.readPendingDeliveryToken() != current.navigationToken) return null
        preferences.writePendingDeliveryToken(null)
        return current
    }

    internal fun isCompatible(trip: ActiveTrip): Boolean =
        trip.locationMode == locationMode && trip.navigationToken.isNotBlank()
}

class SharedPreferencesActiveTripPreferences(context: Context) : ActiveTripPreferences {
    private val applicationContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(KEY_ACTIVE_TRIP, null)

    override fun write(value: String) {
        cancelPreviousNavigation()
        preferences.edit().putString(KEY_ACTIVE_TRIP, value).apply()
    }

    override fun readPendingDeliveryToken(): String? =
        preferences.getString(KEY_PENDING_DELIVERY_TOKEN, null)

    @SuppressLint("ApplySharedPref")
    override fun writePendingDeliveryToken(value: String?) {
        // A Receiver can be the process's final component. Persist its blocked
        // delivery before returning, without cancelling an existing notification.
        preferences.edit().putString(KEY_PENDING_DELIVERY_TOKEN, value).commit()
    }

    override fun clear() {
        cancelPreviousNavigation()
        preferences.edit()
            .remove(KEY_ACTIVE_TRIP)
            .remove(KEY_PENDING_DELIVERY_TOKEN)
            .apply()
    }

    private fun cancelPreviousNavigation() {
        // The old app put its coordinates directly in an external PendingIntent.
        // Revoke that exact token before removing the only copy of its URI.
        val previousUrl = read()?.let { value ->
            runCatching { JSONObject(value).optString("navigationUrl").takeIf(String::isNotBlank) }.getOrNull()
        }
        ReminderNotifications.cancel(applicationContext, previousUrl)
    }

    private companion object {
        const val PREFERENCES_NAME = "active_trip"
        const val KEY_ACTIVE_TRIP = "snapshot"
        const val KEY_PENDING_DELIVERY_TOKEN = "pending_delivery_token"
    }
}

package com.tteumsae.app.location

import com.tteumsae.app.BuildConfig

/**
 * Immutable APK capability. Switching modes requires rebuilding and releasing the app.
 * Source providers and manifest permissions are selected by the same Gradle property.
 */
object LocationAccessPolicy {
    val automaticLocationEnabled: Boolean
        get() = BuildConfig.AUTOMATIC_LOCATION_ENABLED

    // Coordinates restored from another mode (including old unversioned GPS snapshots)
    // must not enter the current mode's requests or notifications.
    val persistenceMode: String
        get() = if (automaticLocationEnabled) "automatic_v1" else "manual_v1"
}

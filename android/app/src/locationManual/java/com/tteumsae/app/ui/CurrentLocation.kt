package com.tteumsae.app.ui

import android.content.Context
import android.location.Location

/** Manual builds expose the same provider contract without touching device services. */
@Suppress("UNUSED_PARAMETER")
internal fun hasLocationPermission(context: Context): Boolean = false

/** Defense in depth if a caller is accidentally left enabled in the manual UI. */
@Suppress("UNUSED_PARAMETER")
internal fun requestCurrentLocation(
    context: Context,
    onSuccess: (Location) -> Unit,
    onLocationDisabled: () -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    onUnavailable()
    return {}
}

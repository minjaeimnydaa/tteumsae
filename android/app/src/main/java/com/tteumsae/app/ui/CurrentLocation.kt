package com.tteumsae.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

internal data class RequestedMapLocation(
    val latitude: Double,
    val longitude: Double,
    val requestId: Long,
)

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION")
internal fun requestCurrentLocation(
    context: Context,
    onSuccess: (Location) -> Unit,
    onLocationDisabled: () -> Unit,
    onUnavailable: () -> Unit,
) {
    if (!hasLocationPermission(context)) {
        onUnavailable()
        return
    }

    val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    if (!locationEnabled) {
        onLocationDisabled()
        return
    }

    val hasFinePermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val providers = buildList {
        if (hasFinePermission && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            add(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
    }
    if (providers.isEmpty()) {
        onLocationDisabled()
        return
    }

    val lastKnownLocation = providers
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
    if (
        lastKnownLocation != null &&
        System.currentTimeMillis() - lastKnownLocation.time <= 60_000L
    ) {
        onSuccess(lastKnownLocation)
        return
    }

    val completed = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    lateinit var timeout: Runnable
    lateinit var listener: LocationListener

    fun finish(location: Location?) {
        if (!completed.compareAndSet(false, true)) return
        handler.removeCallbacks(timeout)
        runCatching { locationManager.removeUpdates(listener) }
        if (location != null) onSuccess(location) else onUnavailable()
    }

    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = finish(location)

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) {
            if (providers.none(locationManager::isProviderEnabled)) {
                if (completed.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout)
                    runCatching { locationManager.removeUpdates(this) }
                    onLocationDisabled()
                }
            }
        }

        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    timeout = Runnable { finish(lastKnownLocation) }
    handler.postDelayed(timeout, 12_000L)

    var requested = false
    providers.forEach { provider ->
        if (runCatching {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }.isSuccess) {
            requested = true
        }
    }
    if (!requested) {
        finish(lastKnownLocation)
    }
}

internal fun createCurrentLocationMarkerBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt().coerceAtLeast(28)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val center = size / 2f

    canvas.drawCircle(
        center,
        center,
        size * 0.34f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(size * 0.08f, 0f, size * 0.04f, 0x55000000)
        },
    )
    canvas.drawCircle(
        center,
        center,
        size * 0.23f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(230, 15, 51)
            style = Paint.Style.FILL
        },
    )
    return bitmap
}

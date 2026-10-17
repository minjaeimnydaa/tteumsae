@file:JvmName("MapLocationPresentation")

package com.tteumsae.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint

private const val LOCATION_CLOCK_SKEW_TOLERANCE_MILLIS = 10_000L

internal data class RequestedMapLocation(
    val latitude: Double,
    val longitude: Double,
    val requestId: Long,
)

internal fun isLocationTimestampFresh(
    locationEpochMillis: Long,
    nowEpochMillis: Long,
    maxAgeMillis: Long,
): Boolean {
    if (locationEpochMillis <= 0L || maxAgeMillis < 0L) return false
    val ageMillis = nowEpochMillis - locationEpochMillis
    return ageMillis in -LOCATION_CLOCK_SKEW_TOLERANCE_MILLIS..maxAgeMillis
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

package com.tteumsae.app.ui.route

internal data class RouteMapCameraSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoomLevel: Int,
) {
    fun savedValues(): List<Double> = listOf(latitude, longitude, zoomLevel.toDouble())
}

internal fun restoredRouteMapCamera(values: List<Double>): RouteMapCameraSnapshot? {
    if (values.size != 3) return null
    val (latitude, longitude, zoom) = values
    if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
        !longitude.isFinite() || longitude !in -180.0..180.0 ||
        !zoom.isFinite() || zoom < 0 || zoom.toInt().toDouble() != zoom
    ) return null
    return RouteMapCameraSnapshot(latitude, longitude, zoom.toInt())
}

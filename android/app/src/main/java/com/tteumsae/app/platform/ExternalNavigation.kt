package com.tteumsae.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.TransportMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal const val MAX_KAKAO_WAYPOINTS = 5

internal fun isKakaoMapAvailable(context: Context): Boolean =
    context.packageManager.resolveActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://open")),
        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
    ) != null

internal fun openKakaoMapHome(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://open")))
    } catch (_: ActivityNotFoundException) {
        openKakaoMapInstallPage(context)
    }
}

internal fun openKakaoMapInstallPage(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://map.kakao.com/"))
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, "지도를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

internal fun openKakaoMap(
    context: Context,
    placeName: String,
    coordinates: Coordinates? = null,
) {
    val encoded = Uri.encode(placeName)
    val appUri = coordinates?.let {
        "kakaomap://look?p=${it.latitude},${it.longitude}"
    } ?: "kakaomap://search?q=$encoded"
    val webUri = coordinates?.let {
        "https://m.map.kakao.com/scheme/look?p=${it.latitude},${it.longitude}"
    } ?: "https://map.kakao.com/link/search/$encoded"
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(appUri))
    try {
        context.startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(webUri),
        )
        try {
            context.startActivity(webIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "지도를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun openKakaoMapRoute(
    context: Context,
    start: Coordinates?,
    startName: String?,
    waypoint: Coordinates?,
    destination: Coordinates?,
    destinationName: String?,
    transport: TransportMode,
) {
    if (start == null || destination == null) {
        Toast.makeText(
            context,
            "경로 좌표를 확인할 수 없어 카카오맵을 열지 못했어요.",
            Toast.LENGTH_SHORT,
        ).show()
        return
    }

    val routeQuery = buildKakaoMapRouteQuery(
        start = start,
        destination = destination,
        transport = transport,
        waypoint = waypoint,
        startName = startName,
        destinationName = destinationName,
    )
    val appIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("kakaomap://route?$routeQuery"),
    )

    try {
        context.startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://m.map.kakao.com/scheme/route?$routeQuery"),
        )
        try {
            context.startActivity(webIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "지도를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun openKakaoMapMultiRoute(
    context: Context,
    start: Coordinates?,
    startName: String,
    waypoints: List<Pair<String, Coordinates>>,
    destination: Coordinates?,
    destinationName: String,
) {
    if (start == null || destination == null) {
        Toast.makeText(context, "경로 좌표를 확인할 수 없어요.", Toast.LENGTH_SHORT).show()
        return
    }
    val url = buildKakaoMapMultiRouteUrl(
        startName,
        start,
        waypoints.take(MAX_KAKAO_WAYPOINTS),
        destinationName,
        destination,
    )
    if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure) {
        Toast.makeText(context, "카카오맵을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

internal fun buildKakaoMapMultiRouteUrl(
    startName: String,
    start: Coordinates,
    waypoints: List<Pair<String, Coordinates>>,
    destinationName: String,
    destination: Coordinates,
): String {
    fun stop(name: String, coordinates: Coordinates): String =
        "${URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("+", "%20")}," +
            "${coordinates.latitude},${coordinates.longitude}"
    return buildString {
        append("https://map.kakao.com/link/by/car/")
        append(stop(startName, start))
        waypoints.take(MAX_KAKAO_WAYPOINTS).forEach { (name, coordinates) ->
            append("/")
            append(stop(name, coordinates))
        }
        append("/")
        append(stop(destinationName, destination))
    }
}

internal fun buildKakaoMapRouteQuery(
    start: Coordinates,
    destination: Coordinates,
    transport: TransportMode,
    waypoint: Coordinates? = null,
    startName: String? = null,
    destinationName: String? = null,
): String =
    buildString {
        append("sp=${start.latitude},${start.longitude}")
        startName?.takeIf(String::isNotBlank)?.let {
            append("&sn=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}")
        }
        waypoint?.let { append("&vp=${it.latitude},${it.longitude}") }
        append("&ep=${destination.latitude},${destination.longitude}")
        destinationName?.takeIf(String::isNotBlank)?.let {
            append("&en=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}")
        }
        append("&by=${if (transport == TransportMode.CAR) "car" else "foot"}")
    }

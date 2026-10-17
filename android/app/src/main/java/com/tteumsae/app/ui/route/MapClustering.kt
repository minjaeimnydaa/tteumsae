package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.PlaceCategory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

private const val MAP_TILE_SIZE_PIXELS = 256.0
private const val DEFAULT_CLUSTER_DISTANCE_PIXELS = 72.0
private const val MAX_MERCATOR_LATITUDE = 85.05112878
internal const val CLUSTER_DISTANCE_ZOOMING_IN_DP = 48.0
internal const val CLUSTER_DISTANCE_NEUTRAL_DP = 56.0
internal const val CLUSTER_DISTANCE_ZOOMING_OUT_DP = 64.0

internal data class RouteMapCandidate(
    val id: String,
    val coordinates: Coordinates,
    val detourMinutes: Int,
    val selectedOrder: Int?,
    val isFocused: Boolean,
    val name: String = "",
    val category: PlaceCategory = PlaceCategory.ATTRACTION,
)

internal fun meaningfulClusterExpansionZoom(
    members: List<RouteMapCandidate>,
    currentZoomLevel: Int,
    clusterDistancePixels: Double,
    maximumZoomLevel: Int = 18,
): Int {
    if (members.size < 2) return currentZoomLevel
    if (currentZoomLevel >= maximumZoomLevel) return maximumZoomLevel
    val finalZoom = maximumZoomLevel
    for (zoom in (currentZoomLevel + 1)..finalZoom) {
        val childSizes = clusterRouteCandidates(
            candidates = members,
            zoomLevel = zoom,
            clusterDistancePixels = clusterDistancePixels,
        ).map { item ->
            when (item) {
                is RouteMapMarkerItem.Single -> 1
                is RouteMapMarkerItem.Cluster -> item.members.size
            }
        }
        val largestChild = childSizes.maxOrNull() ?: members.size
        val isOnlySinglePeel = members.size >= 4 &&
            childSizes.size == 2 &&
            childSizes.sorted() == listOf(1, members.size - 1)
        val meaningfullySplit = childSizes.size >= 3 ||
            largestChild <= (members.size * 0.67).toInt().coerceAtLeast(1) ||
            largestChild == 1
        if (!isOnlySinglePeel && meaningfullySplit) return zoom
    }
    return finalZoom
}

internal sealed interface RouteMapMarkerItem {
    data class Single(val candidate: RouteMapCandidate) : RouteMapMarkerItem

    data class Cluster(
        val id: String,
        val center: Coordinates,
        val members: List<RouteMapCandidate>,
    ) : RouteMapMarkerItem
}

internal fun clusterRouteCandidates(
    candidates: List<RouteMapCandidate>,
    zoomLevel: Int,
    clusterDistancePixels: Double = DEFAULT_CLUSTER_DISTANCE_PIXELS,
): List<RouteMapMarkerItem> {
    if (candidates.size < 2) {
        return candidates.map(RouteMapMarkerItem::Single)
    }

    val worldSize = MAP_TILE_SIZE_PIXELS * 2.0.pow(zoomLevel.coerceAtLeast(0))
    val groups = mutableListOf<MutableList<RouteMapCandidate>>()
    candidates.sortedBy(RouteMapCandidate::id).forEach { candidate ->
        val projectedCandidate = projectToWorldPixels(candidate.coordinates, worldSize)
        val matchingGroup = groups.firstOrNull { group ->
            group.all { member ->
                projectedDistance(
                    first = projectedCandidate,
                    second = projectToWorldPixels(member.coordinates, worldSize),
                    worldSize = worldSize,
                ) <= clusterDistancePixels
            }
        }
        if (matchingGroup == null) {
            groups += mutableListOf(candidate)
        } else {
            matchingGroup += candidate
        }
    }

    val stabilizedGroups = if (candidates.size >= 4 && groups.size == 2) {
        val singleton = groups.firstOrNull { it.size == 1 }
        val remainder = groups.firstOrNull { it.size == candidates.size - 1 }
        val singletonTouchesRemainder = singleton != null && remainder != null &&
            remainder.any { member ->
                projectedDistance(
                    first = projectToWorldPixels(singleton.single().coordinates, worldSize),
                    second = projectToWorldPixels(member.coordinates, worldSize),
                    worldSize = worldSize,
                ) <= clusterDistancePixels
            }
        if (singletonTouchesRemainder) {
            listOf((remainder.orEmpty() + singleton.orEmpty()).toMutableList())
        } else {
            groups
        }
    } else {
        groups
    }

    return stabilizedGroups.map { group ->
        if (group.size == 1) {
            RouteMapMarkerItem.Single(group.single())
        } else {
            val sorted = group.sortedBy(RouteMapCandidate::id)
            RouteMapMarkerItem.Cluster(
                id = sorted.joinToString(prefix = "cluster-", separator = "-") { it.id },
                center = groupCenter(sorted),
                members = sorted,
            )
        }
    }
}

internal fun clusterDistanceDpAfterZoom(
    previousZoomLevel: Int,
    newZoomLevel: Int,
    currentDistanceDp: Double,
): Double = when {
    newZoomLevel > previousZoomLevel -> CLUSTER_DISTANCE_ZOOMING_IN_DP
    newZoomLevel < previousZoomLevel -> CLUSTER_DISTANCE_ZOOMING_OUT_DP
    else -> currentDistanceDp
}

internal fun representativeClusterCategories(
    members: List<RouteMapCandidate>,
    limit: Int = 2,
): List<PlaceCategory> = members
    .groupingBy(RouteMapCandidate::category)
    .eachCount()
    .entries
    .sortedWith(
        compareByDescending<Map.Entry<PlaceCategory, Int>> { it.value }
            .thenBy { it.key.ordinal },
    )
    .take(limit.coerceAtLeast(0))
    .map(Map.Entry<PlaceCategory, Int>::key)

private fun groupCenter(group: List<RouteMapCandidate>): Coordinates = Coordinates(
    latitude = group.map { it.coordinates.latitude }.average(),
    longitude = group.map { it.coordinates.longitude }.average(),
)

private data class ProjectedPoint(val x: Double, val y: Double)

private fun projectToWorldPixels(coordinates: Coordinates, worldSize: Double): ProjectedPoint {
    val latitude = coordinates.latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
    val latitudeRadians = Math.toRadians(latitude)
    return ProjectedPoint(
        x = (coordinates.longitude + 180.0) / 360.0 * worldSize,
        y = (1.0 - ln(tan(latitudeRadians) + 1.0 / kotlin.math.cos(latitudeRadians)) / PI) /
            2.0 * worldSize,
    )
}

private fun projectedDistance(
    first: ProjectedPoint,
    second: ProjectedPoint,
    worldSize: Double,
): Double {
    val directX = abs(first.x - second.x)
    val wrappedX = min(directX, worldSize - directX)
    val y = first.y - second.y
    return kotlin.math.hypot(wrappedX, y)
}

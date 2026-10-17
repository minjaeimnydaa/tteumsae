package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapClusteringTest {
    @Test
    fun `zoomed out nearby candidates become one count cluster`() {
        val items = clusterRouteCandidates(
            candidates = listOf(
                candidate("a", 37.7519, 128.8761),
                candidate("b", 37.7550, 128.8790),
                candidate("c", 37.7600, 128.8840),
            ),
            zoomLevel = 10,
        )

        val cluster = items.single() as RouteMapMarkerItem.Cluster
        assertEquals(listOf("a", "b", "c"), cluster.members.map { it.id })
    }

    @Test
    fun `same candidates separate only when their marker spacing clears`() {
        val candidates = listOf(
            candidate("a", 37.7519, 128.8761),
            candidate("b", 37.7544, 128.8761),
        )

        val overviewItems = clusterRouteCandidates(
            candidates = candidates,
            zoomLevel = 13,
        )
        val detailedItems = clusterRouteCandidates(
            candidates = candidates,
            zoomLevel = 15,
        )

        assertTrue(overviewItems.single() is RouteMapMarkerItem.Cluster)
        assertEquals(2, detailedItems.size)
        assertTrue(detailedItems.all { it is RouteMapMarkerItem.Single })
    }

    @Test
    fun `very close candidates remain bundled even at detailed zoom`() {
        val items = clusterRouteCandidates(
            candidates = listOf(
                candidate("a", 37.7519, 128.8761),
                candidate("b", 37.7520, 128.8762),
            ),
            zoomLevel = 15,
        )

        assertTrue(items.single() is RouteMapMarkerItem.Cluster)
    }

    @Test
    fun `far candidates stay separate at overview zoom`() {
        val items = clusterRouteCandidates(
            candidates = listOf(
                candidate("gangneung", 37.7519, 128.8761),
                candidate("wonju", 37.3422, 127.9202),
            ),
            zoomLevel = 8,
        )

        assertEquals(2, items.size)
    }

    @Test
    fun `zoom direction leaves a hysteresis band between split and merge`() {
        assertEquals(
            CLUSTER_DISTANCE_ZOOMING_IN_DP,
            clusterDistanceDpAfterZoom(12, 13, CLUSTER_DISTANCE_NEUTRAL_DP),
            0.0,
        )
        assertEquals(
            CLUSTER_DISTANCE_ZOOMING_OUT_DP,
            clusterDistanceDpAfterZoom(13, 12, CLUSTER_DISTANCE_NEUTRAL_DP),
            0.0,
        )
        assertEquals(
            CLUSTER_DISTANCE_ZOOMING_IN_DP,
            clusterDistanceDpAfterZoom(13, 13, CLUSTER_DISTANCE_ZOOMING_IN_DP),
            0.0,
        )
    }

    @Test
    fun `a chain does not merge endpoints farther apart than the marker radius`() {
        val items = clusterRouteCandidates(
            candidates = listOf(
                candidate("a", 37.7519, 128.8700),
                candidate("b", 37.7519, 128.8725),
                candidate("c", 37.7519, 128.8750),
            ),
            zoomLevel = 15,
            clusterDistancePixels = 100.0,
        )

        assertEquals(2, items.size)
        assertEquals(listOf("a", "b"), (items.first() as RouteMapMarkerItem.Cluster).members.map { it.id })
        assertTrue(items.last() is RouteMapMarkerItem.Single)
    }

    @Test
    fun `cluster tap skips a cosmetic one plus remainder split`() {
        val base = 128.8700
        val members = listOf(
            candidate("a", 37.7519, base),
            candidate("b", 37.7519, base + 0.003),
            candidate("c", 37.7519, base + 0.006),
            candidate("d", 37.7519, base + 0.009),
            candidate("e", 37.7519, base + 0.018),
        )

        val expansionZoom = meaningfulClusterExpansionZoom(
            members = members,
            currentZoomLevel = 12,
            clusterDistancePixels = 72.0,
        )

        assertEquals(14, expansionZoom)
    }

    @Test
    fun `a nearby singleton is not peeled from a large cluster`() {
        val base = 128.8700
        val items = clusterRouteCandidates(
            candidates = listOf(
                candidate("a", 37.7519, base),
                candidate("b", 37.7519, base + 0.003),
                candidate("c", 37.7519, base + 0.006),
                candidate("d", 37.7519, base + 0.009),
                candidate("e", 37.7519, base + 0.018),
            ),
            zoomLevel = 13,
            clusterDistancePixels = 72.0,
        )

        assertEquals(1, items.size)
        assertEquals(5, (items.single() as RouteMapMarkerItem.Cluster).members.size)
    }

    @Test
    fun `overlapping cluster stops at the supported maximum zoom`() {
        val point = Coordinates(37.7519, 128.8700)
        val members = listOf("a", "b", "c").map { id ->
            candidate(id, point.latitude, point.longitude)
        }

        assertEquals(
            18,
            meaningfulClusterExpansionZoom(
                members = members,
                currentZoomLevel = 15,
                clusterDistancePixels = 72.0,
            ),
        )
    }

    @Test
    fun `cluster badge shows the most common categories first`() {
        val members = listOf(
            candidate("culture", 37.75, 128.87, PlaceCategory.CULTURE),
            candidate("food-a", 37.75, 128.87, PlaceCategory.RESTAURANT),
            candidate("food-b", 37.75, 128.87, PlaceCategory.RESTAURANT),
            candidate("tour-a", 37.75, 128.87, PlaceCategory.ATTRACTION),
            candidate("tour-b", 37.75, 128.87, PlaceCategory.ATTRACTION),
            candidate("tour-c", 37.75, 128.87, PlaceCategory.ATTRACTION),
        )

        assertEquals(
            listOf(PlaceCategory.ATTRACTION, PlaceCategory.RESTAURANT),
            representativeClusterCategories(members),
        )
    }

    private fun candidate(
        id: String,
        latitude: Double,
        longitude: Double,
        category: PlaceCategory = PlaceCategory.ATTRACTION,
    ) = RouteMapCandidate(
        id = id,
        coordinates = Coordinates(latitude, longitude),
        detourMinutes = 5,
        selectedOrder = null,
        isFocused = false,
        category = category,
    )
}

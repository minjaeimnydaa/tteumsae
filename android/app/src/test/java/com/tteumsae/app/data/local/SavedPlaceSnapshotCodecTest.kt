package com.tteumsae.app.data.local

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.PlaceDetailItem
import com.tteumsae.app.domain.PlaceImageAttribution
import com.tteumsae.app.domain.NearbyParkingLot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SavedPlaceSnapshotCodecTest {
    @Test
    fun durable_public_place_fields_round_trip() {
        val original = PlaceCandidate(
            id = "place-1",
            name = "경포해변",
            category = PlaceCategory.ATTRACTION,
            stayMinutes = 45,
            firstLegMinutes = 12,
            secondLegMinutes = 18,
            detourMinutes = 7,
            firstLegDistanceMeters = 4_000,
            secondLegDistanceMeters = 5_000,
            reason = "추천 시점의 이유",
            tags = listOf("바다", "산책"),
            address = "강원특별자치도 강릉시",
            imageUrl = "https://example.com/place.jpg",
            latitude = 37.8,
            longitude = 128.9,
            isOpen = false,
            openingHours = "09:00-18:00",
            closedDays = "매주 월요일",
            telephone = "033-123-4567",
            homepageUrl = "https://example.com",
            overview = "바다 옆 관광지",
            imageUrls = listOf("https://example.com/1.jpg", "https://example.com/2.jpg"),
            detailItems = listOf(PlaceDetailItem("이용 안내", "예약 불필요")),
            accessibilityItems = listOf(PlaceDetailItem("접근로", "턱 없는 접근로")),
            nearbyParkingLots = listOf(
                NearbyParkingLot(
                    id = "parking-1",
                    name = "경포 공영주차장",
                    parkingType = "노외",
                    address = "강원특별자치도 강릉시",
                    capacity = 120,
                    distanceMeters = 245,
                    distanceBasis = "STRAIGHT_LINE",
                    feeSummary = "무료",
                    operationSummary = "매일 24시간",
                    accessibleParking = true,
                    phone = "033-123-4567",
                    referenceDate = "2026-07-22",
                    source = "전국주차장정보표준데이터",
                ),
            ),
            imageAttributions = listOf(
                PlaceImageAttribution(
                    imageUrl = "https://example.com/place.jpg",
                    thumbnailUrl = "https://example.com/thumb.jpg",
                    name = "장소 전경",
                    copyrightType = "Type1",
                    copyrightLabel = "공공누리 제1유형",
                ),
            ),
            lastAdmission = "17:30",
            parkingInfo = "무료 주차",
            eventStartDate = "2026-09-05",
            eventEndDate = "2026-09-07",
            dataProvenance = "CURATION",
            operatingInfoStatus = "VERIFIED",
            reviewedAt = "2026-08-28T00:00:00Z",
        )

        val encoded = SavedPlaceSnapshotCodec.encode(original)
        val decoded = SavedPlaceSnapshotCodec.decode(encoded)!!

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.category, decoded.category)
        assertEquals(original.stayMinutes, decoded.stayMinutes)
        assertEquals(original.tags, decoded.tags)
        assertEquals(original.address, decoded.address)
        assertEquals(original.imageUrl, decoded.imageUrl)
        assertEquals(original.latitude, decoded.latitude)
        assertEquals(original.longitude, decoded.longitude)
        assertEquals(original.openingHours, decoded.openingHours)
        assertEquals(original.closedDays, decoded.closedDays)
        assertEquals(original.telephone, decoded.telephone)
        assertEquals(original.homepageUrl, decoded.homepageUrl)
        assertEquals(original.overview, decoded.overview)
        assertEquals(original.imageUrls, decoded.imageUrls)
        assertEquals(original.detailItems, decoded.detailItems)
        assertEquals(original.accessibilityItems, decoded.accessibilityItems)
        assertEquals(original.nearbyParkingLots, decoded.nearbyParkingLots)
        assertEquals(original.imageAttributions, decoded.imageAttributions)
        assertEquals(original.lastAdmission, decoded.lastAdmission)
        assertEquals(original.parkingInfo, decoded.parkingInfo)
        assertEquals(original.eventStartDate, decoded.eventStartDate)
        assertEquals(original.eventEndDate, decoded.eventEndDate)
        assertEquals(original.dataProvenance, decoded.dataProvenance)
        assertEquals(original.operatingInfoStatus, decoded.operatingInfoStatus)
        assertEquals(original.reviewedAt, decoded.reviewedAt)
    }

    @Test
    fun recommendation_route_metrics_are_not_persisted() {
        val encoded = SavedPlaceSnapshotCodec.encode(placeWithRouteMetrics())
        val json = JSONObject(encoded)
        val decoded = SavedPlaceSnapshotCodec.decode(encoded)!!

        assertFalse(json.has("firstLegMinutes"))
        assertFalse(json.has("secondLegMinutes"))
        assertFalse(json.has("detourMinutes"))
        assertFalse(json.has("firstLegDistanceMeters"))
        assertFalse(json.has("secondLegDistanceMeters"))
        assertFalse(json.has("reason"))
        assertEquals(0, decoded.firstLegMinutes)
        assertEquals(0, decoded.secondLegMinutes)
        assertEquals(0, decoded.detourMinutes)
        assertEquals(0, decoded.firstLegDistanceMeters)
        assertEquals(0, decoded.secondLegDistanceMeters)
        assertEquals("", decoded.reason)
    }

    @Test
    fun nullable_coordinates_and_malformed_snapshots_are_handled() {
        val withoutCoordinates = placeWithRouteMetrics().copy(latitude = null, longitude = null)

        val decoded = SavedPlaceSnapshotCodec.decode(
            SavedPlaceSnapshotCodec.encode(withoutCoordinates),
        )!!

        assertNull(decoded.latitude)
        assertNull(decoded.longitude)
        assertNull(SavedPlaceSnapshotCodec.decode("not-json"))
        assertNull(SavedPlaceSnapshotCodec.decode("""{"id":"missing-fields"}"""))
    }

    @Test
    fun `old snapshots remain readable with empty new detail fields`() {
        val decoded = SavedPlaceSnapshotCodec.decode(
            """{"id":"old","name":"예전 저장","category":"CAFE","stayMinutes":30,"tags":[]}""",
        )!!

        assertEquals("old", decoded.id)
        assertEquals("", decoded.overview)
        assertEquals(emptyList<String>(), decoded.imageUrls)
        assertEquals(emptyList<PlaceDetailItem>(), decoded.detailItems)
        assertEquals(emptyList<PlaceImageAttribution>(), decoded.imageAttributions)
        assertEquals("", decoded.parkingInfo)
    }

    private fun placeWithRouteMetrics() = PlaceCandidate(
        id = "place-1",
        name = "장소",
        category = PlaceCategory.CAFE,
        stayMinutes = 30,
        firstLegMinutes = 10,
        secondLegMinutes = 20,
        detourMinutes = 5,
        firstLegDistanceMeters = 1_000,
        secondLegDistanceMeters = 2_000,
        reason = "추천 이유",
        tags = listOf("커피"),
    )
}

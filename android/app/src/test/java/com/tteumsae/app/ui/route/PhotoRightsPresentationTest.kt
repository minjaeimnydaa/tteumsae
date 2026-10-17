package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.PlaceImageAttribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRightsPresentationTest {
    private val imageUrl = "https://example.com/original.jpg"

    @Test
    fun `only an exact image with an unambiguous Type1 permits crop`() {
        assertTrue(placePhotoMayCrop(place(rights("Type1")), imageUrl))
        assertFalse(placePhotoMayCrop(place(rights("Type3")), imageUrl))
        assertFalse(placePhotoMayCrop(place(), imageUrl))
        assertFalse(placePhotoMayCrop(place(rights("Unknown")), imageUrl))
    }

    @Test
    fun `thumbnail attribution is matched but unrelated image rights are not reused`() {
        val place = place(rights("Type1").copy(thumbnailUrl = "https://example.com/thumb.jpg"))
        assertTrue(placePhotoMayCrop(place, "https://example.com/thumb.jpg"))
        assertFalse(placePhotoMayCrop(place, "https://example.com/unrelated.jpg"))
        assertEquals(
            "사진 · 한국관광공사 TourAPI · 개별 이용조건 확인",
            placePhotoSourceCaption(place, "https://example.com/unrelated.jpg"),
        )
    }

    @Test
    fun `missing type or conflicting type and label fails closed`() {
        assertFalse(placePhotoMayCrop(place(rights("").copy(copyrightLabel = "공공누리 제1유형")), imageUrl))
        assertFalse(placePhotoMayCrop(place(rights("Type1").copy(copyrightLabel = "공공누리 제3유형")), imageUrl))
        val conflicting = place(rights("Type1"), rights("Type3"))
        assertFalse(placePhotoMayCrop(conflicting, imageUrl))
        assertEquals("사진 · 한국관광공사 TourAPI · 개별 이용조건 확인", placePhotoSourceCaption(conflicting, imageUrl))
    }

    @Test
    fun `valid type has a canonical caption even when label is missing`() {
        assertEquals(
            "사진 · 한국관광공사 TourAPI · 공공누리 제3유형",
            placePhotoSourceCaption(place(rights("Type3").copy(copyrightLabel = "")), imageUrl),
        )
    }

    @Test
    fun `missing metadata keeps source without asserting a license`() {
        assertEquals("사진 · 한국관광공사 TourAPI · 개별 이용조건 확인", placePhotoSourceCaption(place(), imageUrl))
        assertFalse(placePhotoMayCrop(place(rights("Type1")), "  "))
        assertNull(placePhotoSourceCaption(place(), null))
        assertNull(placePhotoSourceCaption(place(), "null"))
    }

    @Test
    fun `usage conditions link is the official TourAPI dataset page`() {
        assertEquals("https://www.data.go.kr/data/15101578/openapi.do", TOUR_PHOTO_USAGE_CONDITIONS_URL)
    }

    private fun rights(type: String) = PlaceImageAttribution(
        imageUrl = imageUrl,
        copyrightType = type,
        copyrightLabel = when (type) {
            "Type1" -> "공공누리 제1유형"
            "Type3" -> "공공누리 제3유형"
            else -> ""
        },
    )

    private fun place(vararg rights: PlaceImageAttribution) = PlaceCandidate(
        id = "tour:1", name = "장소", category = PlaceCategory.CULTURE,
        stayMinutes = 40, firstLegMinutes = 0, secondLegMinutes = 0,
        detourMinutes = 0, reason = "", tags = emptyList(), imageAttributions = rights.toList(),
    )
}

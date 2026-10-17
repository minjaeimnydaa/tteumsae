package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.PlaceDetailItem
import com.tteumsae.app.domain.PlaceImageAttribution
import com.tteumsae.app.domain.PlaceCongestionForecast
import com.tteumsae.app.domain.PlaceWeatherForecast
import com.tteumsae.app.domain.NearbyParkingLot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailPresentationTest {
    @Test
    fun `closed day rules are separated into scannable lines`() {
        assertEquals(
            "• 매주 월요일\n• 1월 1일\n• 설·추석 당일",
            structuredClosedDays("매주 월요일 / 1월 1일 / 설·추석 당일"),
        )
    }

    @Test
    fun `방문 정보의 공백과 null 문자열은 없는 값으로 정규화한다`() {
        assertNull(normalizedVisitInfo(null))
        assertNull(normalizedVisitInfo(""))
        assertNull(normalizedVisitInfo("   "))
        assertNull(normalizedVisitInfo("null"))
        assertNull(normalizedVisitInfo(" NULL "))
        assertEquals("매일 09:00~18:00", normalizedVisitInfo(" 매일 09:00~18:00 "))
    }

    @Test
    fun `분 단위 시간은 읽기 쉬운 시간과 분으로 표시한다`() {
        assertEquals("45분", readableDuration(45))
        assertEquals("1시간", readableDuration(60))
        assertEquals("2시간 5분", readableDuration(125))
    }

    @Test
    fun `활동 정보는 명시된 태그만 사용한다`() {
        assertNull(explicitActivityLabel(emptyList()))
        assertNull(explicitActivityLabel(listOf("관광지", "가족 여행")))
        assertEquals("야외 활동", explicitActivityLabel(listOf("가족 여행", "야외 활동")))
    }

    @Test
    fun `fresh detail replaces metadata without losing route metrics`() {
        val routePlace = place().copy(
            firstLegMinutes = 12,
            secondLegMinutes = 18,
            detourMinutes = 7,
            overview = "검색 당시 소개",
        )
        val fresh = place().copy(
            firstLegMinutes = 0,
            secondLegMinutes = 0,
            detourMinutes = 0,
            overview = "최신 소개",
            telephone = "033-123-4567",
            parkingInfo = "건물 뒤 주차장",
            detailItems = listOf(PlaceDetailItem("이용 안내", "예약 불필요")),
            accessibilityItems = listOf(PlaceDetailItem("휠체어", "대여 가능")),
            nearbyParkingLots = listOf(
                NearbyParkingLot(
                    id = "parking-1",
                    name = "경포 공영주차장",
                    distanceMeters = 245,
                ),
            ),
            imageAttributions = listOf(
                PlaceImageAttribution(
                    imageUrl = "https://example.com/hero.jpg",
                    copyrightLabel = "공공누리 제3유형",
                ),
            ),
            congestionForecast = PlaceCongestionForecast(
                forecastDate = "2026-09-08",
                concentrationRate = 72.35,
                level = "HIGH",
                label = "혼잡 예상",
                fetchedAt = "2026-09-07T00:00:00Z",
                source = "한국관광공사 관광지 집중률 예측",
                basis = "상대 예측값",
            ),
            weatherForecast = PlaceWeatherForecast(
                forecastAt = "2026-09-08T02:00:00Z",
                conditionLabel = "비 예상",
                temperatureC = 18.0,
                precipitationProbability = 70.0,
                windSpeedMps = 3.2,
                issuedAt = "2026-09-07T23:00:00Z",
                fetchedAt = "2026-09-07T23:20:00Z",
                source = "기상청 단기예보",
                basis = "5km 격자의 도착 무렵 예보",
            ),
        )

        val merged = mergeFreshPlaceDetails(routePlace, fresh)

        assertEquals(12, merged.firstLegMinutes)
        assertEquals(18, merged.secondLegMinutes)
        assertEquals(7, merged.detourMinutes)
        assertEquals("최신 소개", merged.overview)
        assertEquals("033-123-4567", merged.telephone)
        assertEquals("건물 뒤 주차장", merged.parkingInfo)
        assertEquals("이용 안내", merged.detailItems.single().title)
        assertEquals("휠체어", merged.accessibilityItems.single().title)
        assertEquals("경포 공영주차장", merged.nearbyParkingLots.single().name)
        assertEquals("공공누리 제3유형", merged.imageAttributions.single().copyrightLabel)
        assertEquals("혼잡 예상", merged.congestionForecast?.label)
        assertEquals("비 예상", merged.weatherForecast?.conditionLabel)
    }

    @Test
    fun `practical facts contain only supplied visit data`() {
        val facts = practicalVisitFacts(
            place().copy(
                openingHours = "09:00~18:00",
                closedDays = "null",
                parkingInfo = "무료 주차 20대",
                eventStartDate = "20260905",
                eventEndDate = "20260907",
            ),
        )

        assertEquals(listOf("운영시간", "행사 기간", "주차"), facts.map { it.label })
        assertEquals("2026.09.05 ~ 2026.09.07", facts[1].value)
    }

    @Test
    fun `TourAPI html is cleaned and homepage href is extracted`() {
        assertEquals(
            "첫 줄\n둘째 줄 & 안내",
            plainTourText("<p>첫 줄<br>둘째 줄 &amp; 안내</p>"),
        )
        assertEquals(
            "https://example.com/place",
            normalizedHomepageUrl("<a href='https://example.com/place'>홈페이지</a>"),
        )
        assertTrue(placeSourceCaption(place().copy(dataProvenance = "TOUR_API")).contains("TourAPI"))
    }

    @Test
    fun `혼잡 예측은 날짜와 상대값임을 함께 표시한다`() {
        val facts = practicalVisitFacts(
            place().copy(
                congestionForecast = PlaceCongestionForecast(
                    forecastDate = "2026-09-08",
                    concentrationRate = 72.35,
                    level = "HIGH",
                    label = "혼잡 예상",
                    fetchedAt = "2026-09-07T00:00:00Z",
                    source = "한국관광공사 관광지 집중률 예측",
                    basis = "해당 관광지의 과거 최고 혼잡 시기 대비 상대 예측값",
                ),
            ),
        )

        assertEquals("9월 8일 혼잡", facts.single().label)
        assertEquals("혼잡 예상 · 평소 최고치 대비 72%", facts.single().value)
    }

    @Test
    fun `도착 무렵 날씨는 예보시각과 핵심 수치를 함께 표시한다`() {
        val facts = practicalVisitFacts(
            place().copy(
                weatherForecast = PlaceWeatherForecast(
                    forecastAt = "2026-09-08T02:00:00Z",
                    conditionLabel = "비 예상",
                    temperatureC = 18.0,
                    precipitationProbability = 70.0,
                    windSpeedMps = 3.2,
                    issuedAt = "2026-09-07T23:00:00Z",
                    fetchedAt = "2026-09-07T23:20:00Z",
                    source = "기상청 단기예보",
                    basis = "5km 격자의 도착 무렵 예보",
                ),
            ),
        )

        assertEquals("9월 8일 11시 날씨", facts.single().label)
        assertEquals(
            "비 예상 · 18℃ · 강수확률 70% · 바람 3.2m/s · 기상청 단기예보",
            facts.single().value,
        )
    }

    @Test
    fun `대표 사진과 일치하는 권리 유형만 사진 출처 문구로 표시한다`() {
        val place = place().copy(
            imageAttributions = listOf(
                PlaceImageAttribution(
                    imageUrl = "https://example.com/hero.jpg",
                    name = "장소 전경",
                    copyrightType = "Type3",
                    copyrightLabel = "공공누리 제3유형",
                ),
            ),
        )

        assertEquals(
            "사진 · 한국관광공사 TourAPI · 공공누리 제3유형",
            placePhotoSourceCaption(place, "https://example.com/hero.jpg"),
        )
        assertEquals(
            "사진 · 한국관광공사 TourAPI · 개별 이용조건 확인",
            placePhotoSourceCaption(place, "https://example.com/other.jpg"),
        )
    }

    private fun place() = PlaceCandidate(
        id = "tour:1",
        name = "장소",
        category = PlaceCategory.CULTURE,
        stayMinutes = 40,
        firstLegMinutes = 0,
        secondLegMinutes = 0,
        detourMinutes = 0,
        reason = "",
        tags = emptyList(),
    )
}

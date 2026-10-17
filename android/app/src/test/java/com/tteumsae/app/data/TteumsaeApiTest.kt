package com.tteumsae.app.data

import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.SearchMode
import com.tteumsae.app.domain.TransportMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TteumsaeApiTest {
    @Test
    fun `검색어는 지원 지역과 무관하게 사용자가 입력한 그대로 전달한다`() {
        assertEquals("서울역", locationSearchQuery("서울역"))
        assertEquals("강릉역", locationSearchQuery("  강릉역  "))
        assertEquals("강원 원주시청", locationSearchQuery("강원 원주시청"))
    }

    @Test
    fun `V1 추천 요청은 절대 마감만 직렬화한다`() {
        val body = recommendationRequestBody(criteria(arrivalDeadlineEpochMillis = 1_787_907_600_000))

        assertEquals("ARRIVAL_DEADLINE_V1", body.getString("timeModel"))
        assertEquals(1_787_907_600_000, body.getLong("arrivalDeadlineEpochMillis"))
        assertFalse(body.has("deadlineMinutes"))
        assertFalse(body.has("extraTimeMinutes"))
        assertFalse(body.has("safetyBufferMinutes"))
    }

    @Test
    fun `legacy 추천 요청은 기존 상대 시간 계약을 유지한다`() {
        val body = recommendationRequestBody(criteria())

        assertEquals(45, body.getInt("extraTimeMinutes"))
        assertEquals(15, body.getInt("safetyBufferMinutes"))
        assertFalse(body.has("timeModel"))
        assertFalse(body.has("arrivalDeadlineEpochMillis"))
    }

    @Test
    fun `V1 응답의 체류 필드와 메타를 모두 파싱한다`() {
        val result = parseRecommendationResponse(JSONObject(V1_RESPONSE))
        val recommendation = result.recommendations.single()

        assertEquals(15, recommendation.minimumStayMinutes)
        assertEquals(35, recommendation.maximumStayMinutes)
        assertEquals(1_787_904_720_000, recommendation.latestDepartureEpochMillis)
        assertEquals(1_787_899_800_000, result.calculatedAtEpochMillis)
        assertEquals(1_787_907_600_000, result.arrivalDeadlineEpochMillis)
        assertEquals(15, result.minimumStayMinutes)
        assertEquals(0, recommendation.place.stayMinutes)
    }

    @Test
    fun `V1 응답 항목의 필수 시간 필드가 빠지면 거부한다`() {
        val malformed = JSONObject(V1_RESPONSE)
        malformed.getJSONArray("data").getJSONObject(0).remove("latestDepartureEpochMillis")

        assertThrows(ApiException::class.java) {
            parseRecommendationResponse(malformed)
        }
    }

    @Test
    fun `legacy 응답은 신규 시간 필드 없이 파싱된다`() {
        val result = parseRecommendationResponse(JSONObject(LEGACY_RESPONSE))
        val recommendation = result.recommendations.single()

        assertEquals(30, recommendation.place.stayMinutes)
        assertNull(recommendation.minimumStayMinutes)
        assertNull(recommendation.maximumStayMinutes)
        assertNull(recommendation.latestDepartureEpochMillis)
        assertNull(result.calculatedAtEpochMillis)
    }

    @Test
    fun `장소 상세의 실용 필드를 빠짐없이 파싱한다`() {
        val place = parsePlaceResponse(
            JSONObject(
                """
                {"data":{
                  "content_id":"tour:321",
                  "name":"바다 미술관",
                  "category":"CULTURE",
                  "default_stay_minutes":60,
                  "tel":"033-123-4567",
                  "homepage_url":"https://example.com",
                  "overview":"실제 장소 소개",
                  "image_url":"https://example.com/hero.jpg",
                  "image_urls":["https://example.com/one.jpg","https://example.com/two.jpg"],
                  "detail_items":[{"title":"이용 안내","description":"예약 없이 입장 가능"}],
                  "accessibility_items":[{"title":"휠체어","description":"대여 가능"}],
                  "nearby_parking_lots":[{
                    "parking_id":"4200000:P-1",
                    "name":"경포 공영주차장",
                    "parking_type":"노외",
                    "address":"강원특별자치도 강릉시 창해로 1",
                    "capacity":120,
                    "distance_meters":245,
                    "distance_basis":"STRAIGHT_LINE",
                    "fee_summary":"유료 · 기본 30분 1,000원",
                    "operation_summary":"평일 · 평일 09:00~18:00",
                    "accessible_parking":true,
                    "phone":"033-123-4567",
                    "reference_date":"2026-07-22",
                    "source":"전국주차장정보표준데이터"
                  }],
                  "image_attributions":[{
                    "image_url":"https://example.com/hero.jpg",
                    "thumbnail_url":"https://example.com/thumb.jpg",
                    "name":"미술관 전경",
                    "copyright_type":"Type3",
                    "copyright_label":"공공누리 제3유형"
                  }],
                  "opening_hours":"09:00~18:00",
                  "closed_days":"월요일",
                  "last_admission":"17:30",
                  "parking_info":"무료 주차",
                  "event_start_date":"2026-09-05",
                  "event_end_date":"2026-09-07",
                  "data_provenance":"CURATION",
                  "operating_info_status":"VERIFIED",
                  "reviewed_at":"2026-08-28T00:00:00Z"
                  ,"congestion_forecast":{
                    "forecast_date":"2026-09-08",
                    "concentration_rate":72.35,
                    "level":"HIGH",
                    "label":"혼잡 예상",
                    "fetched_at":"2026-09-07T00:00:00Z",
                    "source":"한국관광공사 관광지 집중률 예측",
                    "basis":"해당 관광지의 과거 최고 혼잡 시기 대비 상대 예측값"
                  },
                  "weather_forecast":{
                    "forecast_at":"2026-09-08T02:00:00Z",
                    "condition_label":"비 예상",
                    "temperature_c":18.0,
                    "precipitation_probability":70.0,
                    "wind_speed_mps":3.2,
                    "issued_at":"2026-09-07T23:00:00Z",
                    "fetched_at":"2026-09-07T23:20:00Z",
                    "source":"기상청 단기예보",
                    "basis":"5km 격자의 도착 무렵 예보"
                  }
                }}
                """.trimIndent(),
            ),
        )

        assertEquals("033-123-4567", place.telephone)
        assertEquals("https://example.com", place.homepageUrl)
        assertEquals("실제 장소 소개", place.overview)
        assertEquals(2, place.imageUrls.size)
        assertEquals("이용 안내", place.detailItems.single().title)
        assertEquals("예약 없이 입장 가능", place.detailItems.single().description)
        assertEquals("휠체어", place.accessibilityItems.single().title)
        assertEquals("대여 가능", place.accessibilityItems.single().description)
        assertEquals("경포 공영주차장", place.nearbyParkingLots.single().name)
        assertEquals(245, place.nearbyParkingLots.single().distanceMeters)
        assertEquals(true, place.nearbyParkingLots.single().accessibleParking)
        assertEquals("공공누리 제3유형", place.imageAttributions.single().copyrightLabel)
        assertEquals("17:30", place.lastAdmission)
        assertEquals("무료 주차", place.parkingInfo)
        assertEquals("2026-09-05", place.eventStartDate)
        assertEquals("CURATION", place.dataProvenance)
        assertEquals("VERIFIED", place.operatingInfoStatus)
        assertEquals("혼잡 예상", place.congestionForecast?.label)
        assertEquals(72.35, place.congestionForecast?.concentrationRate ?: 0.0, 0.001)
        assertEquals("2026-09-08", place.congestionForecast?.forecastDate)
        assertEquals("비 예상", place.weatherForecast?.conditionLabel)
        assertEquals(18.0, place.weatherForecast?.temperatureC ?: 0.0, 0.001)
        assertEquals(70.0, place.weatherForecast?.precipitationProbability ?: 0.0, 0.001)
        assertEquals("", place.reason)
    }

    @Test
    fun `구조화 오류는 상태 코드와 요청 식별자와 재시도 시간을 보존한다`() {
        val error = parseApiErrorResponse(
            status = 503,
            responseText = """
                {"error":{"code":"UPSTREAM_BUDGET_EXHAUSTED","message":"잠시 후 다시 시도해 주세요.","requestId":"req-123"}}
            """.trimIndent(),
            retryAfterHeader = "120",
            operation = "/api/recommendations",
        )

        assertEquals("잠시 후 다시 시도해 주세요.", error.message)
        assertEquals(503, error.status)
        assertEquals("UPSTREAM_BUDGET_EXHAUSTED", error.code)
        assertEquals("req-123", error.requestId)
        assertEquals(120L, error.retryAfterSeconds)
        assertEquals("/api/recommendations", error.operation)
    }

    @Test
    fun `잘못된 오류 본문도 HTTP 문맥을 잃지 않는다`() {
        val error = parseApiErrorResponse(
            status = 504,
            responseText = "not-json",
            retryAfterHeader = "invalid",
            operation = "/api/route",
        )

        assertEquals("서버 요청에 실패했습니다.", error.message)
        assertEquals(504, error.status)
        assertNull(error.code)
        assertNull(error.requestId)
        assertNull(error.retryAfterSeconds)
        assertEquals("/api/route", error.operation)
    }

    private fun criteria(arrivalDeadlineEpochMillis: Long? = null) = SearchCriteria(
        mode = SearchMode.ON_THE_WAY,
        startName = "강릉역",
        endName = "경포대",
        deadlineMinutesFromNow = 45,
        safetyBufferMinutes = 15,
        transportMode = TransportMode.CAR,
        categories = setOf(PlaceCategory.CAFE),
        startCoordinates = Coordinates(37.75, 128.87),
        endCoordinates = Coordinates(37.75, 128.90),
        arrivalDeadlineEpochMillis = arrivalDeadlineEpochMillis,
    )

    private companion object {
        val V1_RESPONSE = """
            {
              "data": [{
                "place": {
                  "content_id": "tour:123",
                  "name": "예시 카페",
                  "category": "CAFE",
                  "latitude": 37.75,
                  "longitude": 128.88
                },
                "minimumStayMinutes": 15,
                "maximumStayMinutes": 35,
                "latestDepartureEpochMillis": 1787904720000,
                "operationStatus": "UNKNOWN",
                "route": {
                  "provider": "KAKAO_MOBILITY",
                  "firstLegMinutes": 20,
                  "secondLegMinutes": 38,
                  "detourMinutes": 8
                }
              }],
              "meta": {
                "timeModel": "ARRIVAL_DEADLINE_V1",
                "calculatedAtEpochMillis": 1787899800000,
                "arrivalDeadlineEpochMillis": 1787907600000,
                "safetyBufferMinutes": 10,
                "minimumStayMinutes": 15
              }
            }
        """.trimIndent()

        val LEGACY_RESPONSE = """
            {
              "data": [{
                "place": {
                  "content_id": "tour:123",
                  "name": "예시 카페",
                  "category": "CAFE"
                },
                "stayMinutes": 30,
                "totalMinutes": 50,
                "marginMinutes": 20,
                "safetyLevel": "COMFORTABLE",
                "route": {
                  "firstLegMinutes": 10,
                  "secondLegMinutes": 10,
                  "detourMinutes": 5
                }
              }],
              "meta": {}
            }
        """.trimIndent()
    }
}

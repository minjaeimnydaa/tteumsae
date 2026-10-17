package com.tteumsae.app.ui

import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SafetyLevel
import com.tteumsae.app.domain.TransportMode
import com.tteumsae.app.domain.recommendation.RecommendationIntent
import com.tteumsae.app.domain.recommendation.matchesGangwonRegion
import com.tteumsae.app.domain.recommendation.recommendationCategories
import com.tteumsae.app.domain.recommendation.recommendationIntentFilters
import com.tteumsae.app.domain.recommendation.toggleRecommendationIntent
import com.tteumsae.app.domain.route.additionalDetourDistanceMeters
import com.tteumsae.app.domain.route.isRouteWithinExtraTimeBudget
import com.tteumsae.app.domain.route.orderWaypointIdsAlongRoute
import com.tteumsae.app.domain.route.selectedRouteEstimate
import com.tteumsae.app.platform.buildKakaoMapMultiRouteUrl
import com.tteumsae.app.platform.buildKakaoMapRouteQuery
import com.tteumsae.app.ui.common.compactTags
import com.tteumsae.app.ui.route.productNavigationRecommendations
import org.junit.Assert.assertEquals
import org.junit.Test

class KakaoMapRouteTest {
    @Test
    fun `다중 경유지를 출발지와 최종 목적지 사이에 포함한다`() {
        assertEquals(
            "https://map.kakao.com/link/by/car/%EC%84%9C%EC%9A%B8%EC%97%AD,37.1,127.1/%EC%B2%AB%20%EC%9E%A5%EC%86%8C,37.2,127.2/%EB%91%98%EC%A7%B8,37.3,127.3/%EA%B0%95%EB%A6%89%EC%97%AD,37.4,127.4",
            buildKakaoMapMultiRouteUrl(
                "서울역",
                Coordinates(37.1, 127.1),
                listOf(
                    "첫 장소" to Coordinates(37.2, 127.2),
                    "둘째" to Coordinates(37.3, 127.3),
                ),
                "강릉역",
                Coordinates(37.4, 127.4),
            ),
        )
    }

    @Test
    fun `경유지가 없어도 출발지에서 최종 목적지까지 안내한다`() {
        assertEquals(
            "https://map.kakao.com/link/by/car/%EC%84%9C%EC%9A%B8%EC%97%AD,37.1,127.1/%EA%B0%95%EB%A6%89%EC%97%AD,37.4,127.4",
            buildKakaoMapMultiRouteUrl(
                "서울역",
                Coordinates(37.1, 127.1),
                emptyList(),
                "강릉역",
                Coordinates(37.4, 127.4),
            ),
        )
    }

    @Test
    fun `제품 선택은 한 곳만 넘기고 저수준 링크는 여러 경유지를 계속 지원한다`() {
        fun recommendation(id: String) = SafeRecommendation(
            place = PlaceCandidate(
                id = id,
                name = id,
                category = PlaceCategory.CAFE,
                stayMinutes = 0,
                firstLegMinutes = 10,
                secondLegMinutes = 10,
                detourMinutes = 5,
                reason = "",
                tags = emptyList(),
            ),
            totalMinutes = 20,
            marginMinutes = 20,
            safetyLevel = SafetyLevel.AVAILABLE,
        )
        val recommendations = (1..5).map { recommendation("place-$it") }

        assertEquals(
            listOf("place-3"),
            productNavigationRecommendations("place-3", recommendations).map { it.place.id },
        )
        val lowLevelUrl = buildKakaoMapMultiRouteUrl(
            "출발",
            Coordinates(37.1, 127.1),
            (1..5).map { "경유-$it" to Coordinates(37.1 + it / 100.0, 127.1 + it / 100.0) },
            "도착",
            Coordinates(37.9, 127.9),
        )
        assertEquals(5, Regex("%EA%B2%BD%EC%9C%A0-").findAll(lowLevelUrl).count())
    }

    @Test
    fun `경유지는 출발지에서 목적지 방향으로 자동 정렬한다`() {
        assertEquals(
            listOf("near", "far"),
            orderWaypointIdsAlongRoute(
                Coordinates(0.0, 0.0),
                Coordinates(10.0, 10.0),
                listOf(
                    "far" to Coordinates(8.0, 8.0),
                    "near" to Coordinates(2.0, 2.0),
                ),
            ),
        )
    }

    @Test
    fun `선택 경유지의 머무름과 우회 시간을 합산한다`() {
        fun recommendation(id: String, stay: Int, detour: Int) = SafeRecommendation(
            place = PlaceCandidate(
                id = id,
                name = id,
                category = PlaceCategory.ATTRACTION,
                stayMinutes = stay,
                firstLegMinutes = 40,
                secondLegMinutes = 40,
                detourMinutes = detour,
                reason = "",
                tags = emptyList(),
            ),
            totalMinutes = 0,
            marginMinutes = 0,
            safetyLevel = SafetyLevel.AVAILABLE,
        )
        assertEquals(
            135 to 45,
            selectedRouteEstimate(
                180,
                listOf(recommendation("a", 20, 10), recommendation("b", 25, 10)),
            ),
        )
    }

    @Test
    fun `선택 경로는 기본 경로와 추가 여유시간 안에서만 허용한다`() {
        assertEquals(
            true,
            isRouteWithinExtraTimeBudget(
                baseDrivingMinutes = 120,
                extraTimeMinutes = 60,
                selectedDrivingMinutes = 135,
                selectedStayMinutes = 30,
                safetyBufferMinutes = 15,
            ),
        )
        assertEquals(
            false,
            isRouteWithinExtraTimeBudget(
                baseDrivingMinutes = 120,
                extraTimeMinutes = 60,
                selectedDrivingMinutes = 136,
                selectedStayMinutes = 30,
                safetyBufferMinutes = 15,
            ),
        )
    }

    @Test
    fun `후보 카드의 추가 거리는 기본 경로와 두 구간 거리의 차이로 계산한다`() {
        val place = PlaceCandidate(
            id = "place",
            name = "장소",
            category = PlaceCategory.ATTRACTION,
            stayMinutes = 40,
            firstLegMinutes = 50,
            secondLegMinutes = 64,
            detourMinutes = 14,
            firstLegDistanceMeters = 5_000,
            secondLegDistanceMeters = 6_000,
            reason = "",
            tags = emptyList(),
        )

        assertEquals(2_000, additionalDetourDistanceMeters(place, baseDistanceMeters = 9_000))
        assertEquals(null, additionalDetourDistanceMeters(place.copy(firstLegDistanceMeters = 0), 9_000))
    }

    @Test
    fun `자동차 경로에 출발지 경유지 도착지를 포함한다`() {
        assertEquals(
            "sp=37.1,128.1&sn=Start+Place&vp=37.2,128.2&ep=37.3,128.3&en=Final+Place&by=car",
            buildKakaoMapRouteQuery(
                start = Coordinates(37.1, 128.1),
                destination = Coordinates(37.3, 128.3),
                transport = TransportMode.CAR,
                waypoint = Coordinates(37.2, 128.2),
                startName = "Start Place",
                destinationName = "Final Place",
            ),
        )
    }

    @Test
    fun `근처 장소는 선택한 이동수단으로 바로 길 안내한다`() {
        assertEquals(
            "sp=37.1,128.1&ep=37.2,128.2&by=foot",
            buildKakaoMapRouteQuery(
                start = Coordinates(37.1, 128.1),
                destination = Coordinates(37.2, 128.2),
                transport = TransportMode.WALK,
            ),
        )
    }

    @Test
    fun `태그 폭 예산을 넘으면 남은 개수를 반환한다`() {
        assertEquals(
            listOf("주차 가능", "아이 동반", "점심 식사") to 1,
            compactTags(listOf("주차 가능", "아이 동반", "점심 식사", "반려동물 동반")),
        )
    }

    @Test
    fun `배가 부르면 음식점을 추천 대상에서 제외한다`() {
        assertEquals(
            PlaceCategory.entries.toSet() - PlaceCategory.RESTAURANT,
            recommendationCategories(emptySet(), excludeRestaurants = true),
        )
        assertEquals(
            setOf(PlaceCategory.CULTURE),
            recommendationCategories(setOf(PlaceCategory.CULTURE), excludeRestaurants = true),
        )
    }

    @Test
    fun `추천 의도를 기존 장소 조건으로 변환한다`() {
        assertEquals(
            setOf(PlaceCategory.RESTAURANT) to false,
            recommendationIntentFilters(RecommendationIntent.MEAL),
        )
        assertEquals(
            setOf(PlaceCategory.ATTRACTION, PlaceCategory.LEISURE) to false,
            recommendationIntentFilters(RecommendationIntent.WALK_TOUR),
        )
        assertEquals(
            emptySet<PlaceCategory>() to true,
            recommendationIntentFilters(RecommendationIntent.NO_FOOD),
        )
    }

    @Test
    fun `여러 관심사를 함께 고르고 충돌하는 선택은 정리한다`() {
        val selected = toggleRecommendationIntent(
            toggleRecommendationIntent(setOf(RecommendationIntent.ANY), RecommendationIntent.MEAL),
            RecommendationIntent.WALK_TOUR,
        )
        assertEquals(
            setOf(PlaceCategory.RESTAURANT, PlaceCategory.ATTRACTION, PlaceCategory.LEISURE) to false,
            recommendationIntentFilters(selected),
        )
        assertEquals(
            setOf(RecommendationIntent.NO_FOOD),
            toggleRecommendationIntent(setOf(RecommendationIntent.MEAL), RecommendationIntent.NO_FOOD),
        )
        assertEquals(
            setOf(RecommendationIntent.ANY),
            toggleRecommendationIntent(selected, RecommendationIntent.ANY),
        )
    }

    @Test
    fun `위치 권한을 다시 물을 수 없으면 앱 설정 안내가 필요하다`() {
        val denied = mapOf("fine" to false, "coarse" to false)

        assertEquals(true, deniedLocationPermissionNeedsSettings(denied) { false })
        assertEquals(false, deniedLocationPermissionNeedsSettings(denied) { true })
        assertEquals(
            false,
            deniedLocationPermissionNeedsSettings(mapOf("fine" to true)) { false },
        )
    }

    @Test
    fun `실패한 네트워크 기능 이름과 원인을 함께 보여준다`() {
        assertEquals(
            "장소 검색에 실패했어요. 인터넷 연결 없음",
            networkFailureMessage("장소 검색", "인터넷 연결 없음"),
        )
        assertEquals(
            "장소 추천에 실패했어요. 네트워크 연결을 확인해 주세요.",
            networkFailureMessage("장소 추천", null),
        )
    }

    @Test
    fun `선택한 강원도 지역과 주소가 일치하는 장소만 보여준다`() {
        assertEquals(true, matchesGangwonRegion("강원특별자치도 강릉시 용지로", "강릉"))
        assertEquals(false, matchesGangwonRegion("강원특별자치도 춘천시 중앙로", "강릉"))
        assertEquals(true, matchesGangwonRegion("강원특별자치도 춘천시 중앙로", "강원도 전체"))
    }
}

package com.tteumsae.app.data

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.SearchMode

object SamplePlaces {
    fun forMode(mode: SearchMode): List<PlaceCandidate> = when (mode) {
        SearchMode.ON_THE_WAY -> onTheWay
        SearchMode.NEARBY -> nearby
    }

    private val onTheWay = listOf(
        PlaceCandidate(
            id = "daegwallyeong-observatory",
            name = "대관령 전망대",
            category = PlaceCategory.ATTRACTION,
            stayMinutes = 30,
            firstLegMinutes = 18,
            secondLegMinutes = 22,
            detourMinutes = 8,
            reason = "경로에서 8분만 벗어나면 탁 트인 풍경을 볼 수 있어요.",
            tags = listOf("주차 가능", "사진 명소", "무료"),
        ),
        PlaceCandidate(
            id = "gangneung-coffee",
            name = "강릉 로컬 커피공방",
            category = PlaceCategory.CAFE,
            stayMinutes = 35,
            firstLegMinutes = 14,
            secondLegMinutes = 20,
            detourMinutes = 6,
            reason = "이동 경로와 가깝고 짧게 쉬어 가기 좋아요.",
            tags = listOf("실내 활동", "주차 가능"),
        ),
        PlaceCandidate(
            id = "wolhwa-street",
            name = "월화거리 산책길",
            category = PlaceCategory.ATTRACTION,
            stayMinutes = 25,
            firstLegMinutes = 12,
            secondLegMinutes = 19,
            detourMinutes = 4,
            reason = "도심 이동 중 가볍게 걷기 좋은 경유지예요.",
            tags = listOf("도보", "무료", "야외"),
        ),
        PlaceCandidate(
            id = "closed-museum",
            name = "강릉 작은 미술관",
            category = PlaceCategory.CULTURE,
            stayMinutes = 60,
            firstLegMinutes = 15,
            secondLegMinutes = 20,
            detourMinutes = 7,
            reason = "지역 작가의 작품을 만날 수 있어요.",
            tags = listOf("실내 활동"),
            isOpen = false,
        ),
    )

    private val nearby = listOf(
        PlaceCandidate(
            id = "gangneung-market",
            name = "강릉 중앙시장",
            category = PlaceCategory.SHOPPING,
            stayMinutes = 35,
            firstLegMinutes = 10,
            secondLegMinutes = 10,
            detourMinutes = 0,
            reason = "짧은 시간에도 강릉의 먹거리와 분위기를 즐길 수 있어요.",
            tags = listOf("먹거리", "실내·외", "화장실"),
        ),
        PlaceCandidate(
            id = "dan-o-museum",
            name = "강릉단오문화관",
            category = PlaceCategory.CULTURE,
            stayMinutes = 45,
            firstLegMinutes = 12,
            secondLegMinutes = 12,
            detourMinutes = 0,
            reason = "날씨와 관계없이 강릉 문화를 둘러볼 수 있어요.",
            tags = listOf("실내 활동", "아이 동반", "주차 가능"),
        ),
        PlaceCandidate(
            id = "namdaecheon",
            name = "남대천 산책로",
            category = PlaceCategory.ATTRACTION,
            stayMinutes = 30,
            firstLegMinutes = 8,
            secondLegMinutes = 8,
            detourMinutes = 0,
            reason = "가까운 거리에서 부담 없이 산책할 수 있어요.",
            tags = listOf("도보", "무료", "반려동물 동반"),
        ),
    )
}


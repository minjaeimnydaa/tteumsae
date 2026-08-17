package com.tteumsae.app.domain

enum class SearchMode {
    ON_THE_WAY,
    NEARBY,
}

enum class TransportMode {
    WALK,
    CAR,
}

enum class PlaceCategory(val label: String) {
    ATTRACTION("관광지"),
    RESTAURANT("음식점"),
    CAFE("카페"),
    CULTURE("문화시설"),
    FESTIVAL("축제·행사"),
    SHOPPING("쇼핑"),
    LEISURE("레포츠"),
}

enum class SafetyLevel(val label: String) {
    COMFORTABLE("시간 여유"),
    AVAILABLE("방문 가능 예상"),
    TIGHT("시간 빠듯"),
}

enum class OperationStatus {
    OPEN,
    UNKNOWN,
}

data class PlaceCandidate(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val stayMinutes: Int,
    val firstLegMinutes: Int,
    val secondLegMinutes: Int,
    val detourMinutes: Int,
    val reason: String,
    val tags: List<String>,
    val address: String = "",
    val imageUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isOpen: Boolean = true,
    val openingHours: String = "",
    val closedDays: String = "",
)

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

data class LocationSearchResult(
    val id: String,
    val name: String,
    val address: String,
    val coordinates: Coordinates,
)

data class SearchCriteria(
    val mode: SearchMode,
    val startName: String,
    val endName: String,
    val deadlineMinutesFromNow: Int,
    val safetyBufferMinutes: Int,
    val transportMode: TransportMode,
    val categories: Set<PlaceCategory> = emptySet(),
    val startCoordinates: Coordinates? = null,
    val endCoordinates: Coordinates? = null,
)

data class SafeRecommendation(
    val place: PlaceCandidate,
    val totalMinutes: Int,
    val marginMinutes: Int,
    val safetyLevel: SafetyLevel,
    val routePoints: List<Coordinates> = emptyList(),
    val operationStatus: OperationStatus = OperationStatus.UNKNOWN,
)

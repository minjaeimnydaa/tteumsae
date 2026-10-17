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

data class PlaceDetailItem(
    val title: String,
    val description: String,
)

data class PlaceImageAttribution(
    val imageUrl: String = "",
    val thumbnailUrl: String = "",
    val name: String = "",
    val copyrightType: String = "",
    val copyrightLabel: String = "",
)

data class PlaceCongestionForecast(
    val forecastDate: String,
    val concentrationRate: Double,
    val level: String,
    val label: String,
    val fetchedAt: String,
    val source: String,
    val basis: String,
)

data class PlaceWeatherForecast(
    val forecastAt: String,
    val conditionLabel: String,
    val temperatureC: Double,
    val precipitationProbability: Double?,
    val windSpeedMps: Double?,
    val issuedAt: String,
    val fetchedAt: String,
    val source: String,
    val basis: String,
)

data class NearbyParkingLot(
    val id: String,
    val name: String,
    val parkingType: String = "",
    val address: String = "",
    val capacity: Int? = null,
    val distanceMeters: Int = 0,
    val distanceBasis: String = "",
    val feeSummary: String = "",
    val operationSummary: String = "",
    val accessibleParking: Boolean? = null,
    val phone: String = "",
    val referenceDate: String = "",
    val source: String = "",
)

data class PlaceCandidate(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val stayMinutes: Int,
    val firstLegMinutes: Int,
    val secondLegMinutes: Int,
    val detourMinutes: Int,
    val firstLegDistanceMeters: Int = 0,
    val secondLegDistanceMeters: Int = 0,
    val reason: String,
    val tags: List<String>,
    val address: String = "",
    val imageUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isOpen: Boolean = true,
    val openingHours: String = "",
    val closedDays: String = "",
    val telephone: String = "",
    val homepageUrl: String = "",
    val overview: String = "",
    val imageUrls: List<String> = emptyList(),
    val lastAdmission: String = "",
    val parkingInfo: String = "",
    val eventStartDate: String = "",
    val eventEndDate: String = "",
    val dataProvenance: String = "",
    val operatingInfoStatus: String = "",
    val admissionInfoStatus: String = "",
    val parkingInfoStatus: String = "",
    val reviewedAt: String = "",
    val detailItems: List<PlaceDetailItem> = emptyList(),
    val accessibilityItems: List<PlaceDetailItem> = emptyList(),
    val imageAttributions: List<PlaceImageAttribution> = emptyList(),
    val congestionForecast: PlaceCongestionForecast? = null,
    val weatherForecast: PlaceWeatherForecast? = null,
    val nearbyParkingLots: List<NearbyParkingLot> = emptyList(),
)

data class RouteLeg(
    val drivingMinutes: Int,
    val distanceMeters: Int,
)

data class RouteSummary(
    val provider: String,
    val waypointCount: Int,
    val totalDrivingMinutes: Int,
    val totalDistanceMeters: Int,
    val tollFareWon: Int,
    val legs: List<RouteLeg> = emptyList(),
    val path: List<Coordinates> = emptyList(),
)

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

enum class LocationSearchResultType {
    PLACE,
    ADDRESS,
}

data class LocationSearchResult(
    val id: String,
    val name: String,
    val address: String,
    val coordinates: Coordinates,
    val type: LocationSearchResultType = LocationSearchResultType.PLACE,
    val secondaryAddress: String = "",
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
    val arrivalDeadlineEpochMillis: Long? = null,
)

data class SafeRecommendation(
    val place: PlaceCandidate,
    val totalMinutes: Int,
    val marginMinutes: Int,
    val safetyLevel: SafetyLevel,
    val routePoints: List<Coordinates> = emptyList(),
    val operationStatus: OperationStatus = OperationStatus.UNKNOWN,
    val minimumStayMinutes: Int? = null,
    val maximumStayMinutes: Int? = null,
    val latestDepartureEpochMillis: Long? = null,
)

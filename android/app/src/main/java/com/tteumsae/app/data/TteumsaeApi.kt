package com.tteumsae.app.data

import com.tteumsae.app.BuildConfig
import com.tteumsae.app.data.route.RouteGateway
import com.tteumsae.app.data.route.RouteWaypoint
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.LocationSearchResult
import com.tteumsae.app.domain.LocationSearchResultType
import com.tteumsae.app.domain.OperationStatus
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.RouteLeg
import com.tteumsae.app.domain.RouteSummary
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SafetyLevel
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.prioritizeGangwonDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TteumsaeApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) : RouteGateway {
    suspend fun searchPlaces(
        query: String,
        gangwonOnly: Boolean = false,
    ): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        val normalized = locationSearchQuery(query)
        val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())
        val response = request("GET", "/api/geocode?q=$encoded")
        val results = response.getJSONArray("data").mapObjects { it.toLocationSearchResult() }
        if (gangwonOnly) prioritizeGangwonDestinations(results) else results
    }

    suspend fun searchPlace(
        query: String,
        gangwonOnly: Boolean = false,
    ): LocationSearchResult =
        searchPlaces(query, gangwonOnly).firstOrNull()
            ?: throw ApiException("'$query'의 위치를 찾지 못했습니다.")

    suspend fun isGangwon(coordinates: Coordinates): Boolean = withContext(Dispatchers.IO) {
        val response = request(
            "GET",
            "/api/region?latitude=${coordinates.latitude}&longitude=${coordinates.longitude}",
        )
        response.getJSONObject("data").getBoolean("isGangwon")
    }

    suspend fun regionAddress(coordinates: Coordinates): String = withContext(Dispatchers.IO) {
        val response = request(
            "GET",
            "/api/region?latitude=${coordinates.latitude}&longitude=${coordinates.longitude}",
        )
        response.getJSONObject("data").getString("address")
    }

    override suspend fun recommendations(criteria: SearchCriteria): RecommendationResult =
        withContext(Dispatchers.IO) {
            parseRecommendationResponse(
                request("POST", "/api/recommendations", recommendationRequestBody(criteria)),
            )
        }

    suspend fun route(
        start: Coordinates,
        destination: Coordinates,
        waypoints: List<PlaceCandidate> = emptyList(),
    ): RouteSummary {
        if (waypoints.size > 5) throw ApiException("경유지는 최대 5곳까지 추가할 수 있어요.")
        return calculateRoute(
            start = start,
            destination = destination,
            waypoints = waypoints.map { place ->
                RouteWaypoint(
                    id = place.id,
                    coordinates = Coordinates(
                        latitude = place.latitude
                            ?: throw ApiException("경유지 좌표가 없습니다."),
                        longitude = place.longitude
                            ?: throw ApiException("경유지 좌표가 없습니다."),
                    ),
                )
            },
        )
    }

    override suspend fun calculateRoute(
        start: Coordinates,
        destination: Coordinates,
        waypoints: List<RouteWaypoint>,
    ): RouteSummary = withContext(Dispatchers.IO) {
        if (waypoints.size > 5) throw ApiException("경유지는 최대 5곳까지 추가할 수 있어요.")
        val body = JSONObject()
            .put("start", start.toJson())
            .put("destination", destination.toJson())
            .put(
                "waypoints",
                JSONArray().apply {
                    waypoints.forEach { waypoint ->
                        put(
                            JSONObject()
                                .put("contentId", waypoint.id)
                                .put("latitude", waypoint.coordinates.latitude)
                                .put("longitude", waypoint.coordinates.longitude),
                        )
                    }
                },
            )
        request("POST", "/api/route", body).getJSONObject("data").toRouteSummary()
    }

    suspend fun places(
        page: Int = 1,
        pageSize: Int = 100,
        sigunguCode: Int? = null,
    ): PlacePage =
        withContext(Dispatchers.IO) {
            val regionQuery = sigunguCode?.let { "&sigunguCode=$it" }.orEmpty()
            val response = request(
                "GET",
                "/api/places?page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 100)}$regionQuery",
            )
            PlacePage(
                places = response.getJSONArray("data")
                    .mapObjects { it.toPlaceCandidate() },
                hasMore = response.getJSONObject("pagination").getBoolean("hasMore"),
            )
        }

    suspend fun place(
        contentId: String,
        atEpochMillis: Long? = null,
    ): PlaceCandidate = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(contentId, StandardCharsets.UTF_8.name())
        val timeQuery = atEpochMillis?.takeIf { it > 0 }
            ?.let { "?atEpochMillis=$it" }
            .orEmpty()
        parsePlaceResponse(request("GET", "/api/places/$encoded$timeQuery"))
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
    ): JSONObject {
        val connection = URI.create("$baseUrl$path").toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(body.toString())
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw parseApiErrorResponse(
                    status = status,
                    responseText = text,
                    retryAfterHeader = connection.getHeaderField("Retry-After"),
                    operation = path,
                )
            }
            JSONObject(text)
        } catch (error: ApiException) {
            throw error
        } catch (error: IOException) {
            throw ApiException("네트워크 연결을 확인해주세요.", error)
        } finally {
            connection.disconnect()
        }
    }
}

data class RecommendationResult(
    val recommendations: List<SafeRecommendation>,
    val warning: String,
    val baseRoute: RouteSummary? = null,
    val corridorRadiusMeters: Int = 1_600,
    val calculatedAtEpochMillis: Long? = null,
    val arrivalDeadlineEpochMillis: Long? = null,
    val minimumStayMinutes: Int? = null,
)

internal fun recommendationRequestBody(criteria: SearchCriteria): JSONObject {
    val start = criteria.startCoordinates
        ?: throw ApiException("출발지 좌표가 없습니다.")
    val destination = criteria.endCoordinates
        ?: throw ApiException("목적지 좌표가 없습니다.")
    val body = JSONObject()
        .put("mode", criteria.mode.name)
        .put("start", start.toJson())
        .put("destination", destination.toJson())
        .put("transport", criteria.transportMode.name)
        .put(
            "categories",
            JSONArray().apply {
                criteria.categories.sortedBy { it.name }.forEach { put(it.name) }
            },
        )

    return criteria.arrivalDeadlineEpochMillis?.let { deadline ->
        body
            .put("arrivalDeadlineEpochMillis", deadline)
            .put("timeModel", "ARRIVAL_DEADLINE_V1")
    } ?: body
        .put("extraTimeMinutes", criteria.deadlineMinutesFromNow)
        .put("safetyBufferMinutes", criteria.safetyBufferMinutes)
}

internal fun parseRecommendationResponse(response: JSONObject): RecommendationResult {
    try {
        val meta = response.optJSONObject("meta") ?: JSONObject()
        val isArrivalDeadlineV1 = meta.optString("timeModel") == "ARRIVAL_DEADLINE_V1"
        val calculatedAtEpochMillis = if (isArrivalDeadlineV1) {
            meta.requiredPositiveLong("calculatedAtEpochMillis")
        } else {
            null
        }
        val arrivalDeadlineEpochMillis = if (isArrivalDeadlineV1) {
            meta.requiredPositiveLong("arrivalDeadlineEpochMillis")
        } else {
            null
        }
        val minimumStayMinutes = if (isArrivalDeadlineV1) {
            meta.requiredPositiveInt("minimumStayMinutes")
        } else {
            null
        }

        return RecommendationResult(
            recommendations = response.getJSONArray("data").mapObjects {
                it.toRecommendation(requireV1Fields = isArrivalDeadlineV1)
            },
            warning = meta.optString("warning"),
            baseRoute = (
                response.optJSONObject("baseRoute")
                    ?: meta.optJSONObject("baseRoute")
                )?.toRouteSummary(),
            corridorRadiusMeters = meta.optInt("corridorRadiusMeters", 1_600),
            calculatedAtEpochMillis = calculatedAtEpochMillis,
            arrivalDeadlineEpochMillis = arrivalDeadlineEpochMillis,
            minimumStayMinutes = minimumStayMinutes,
        )
    } catch (error: ApiException) {
        throw error
    } catch (error: Exception) {
        throw ApiException("추천 응답 형식이 올바르지 않습니다.", error)
    }
}

internal fun locationSearchQuery(query: String): String = query.trim()

data class PlacePage(
    val places: List<PlaceCandidate>,
    val hasMore: Boolean,
)

class ApiException(
    override val message: String,
    cause: Throwable? = null,
    val status: Int? = null,
    val code: String? = null,
    val requestId: String? = null,
    val retryAfterSeconds: Long? = null,
    val operation: String? = null,
) : Exception(message, cause)

internal fun parseApiErrorResponse(
    status: Int,
    responseText: String,
    retryAfterHeader: String?,
    operation: String,
): ApiException {
    val error = runCatching {
        JSONObject(responseText).optJSONObject("error")
    }.getOrNull()
    return ApiException(
        message = error?.optString("message")
            ?.takeIf(String::isNotBlank)
            ?: "서버 요청에 실패했습니다.",
        status = status,
        code = error?.optString("code")?.takeIf(String::isNotBlank),
        requestId = error?.optString("requestId")?.takeIf(String::isNotBlank),
        retryAfterSeconds = retryAfterHeader?.toLongOrNull()?.takeIf { it >= 0 },
        operation = operation,
    )
}

private fun Coordinates.toJson() = JSONObject()
    .put("latitude", latitude)
    .put("longitude", longitude)

internal fun JSONObject.toLocationSearchResult() = LocationSearchResult(
    id = getString("id"),
    name = getString("name"),
    address = optString("address"),
    coordinates = Coordinates(
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
    ),
    type = if (optString("type") == "ADDRESS") LocationSearchResultType.ADDRESS else LocationSearchResultType.PLACE,
    secondaryAddress = if (isNull("secondaryAddress")) "" else optString("secondaryAddress"),
)

private fun JSONObject.toRecommendation(requireV1Fields: Boolean): SafeRecommendation {
    val place = getJSONObject("place")
    val route = getJSONObject("route")
    val minimumStayMinutes = if (requireV1Fields) {
        requiredPositiveInt("minimumStayMinutes")
    } else {
        null
    }
    val maximumStayMinutes = if (requireV1Fields) {
        requiredPositiveInt("maximumStayMinutes")
    } else {
        null
    }
    val latestDepartureEpochMillis = if (requireV1Fields) {
        requiredPositiveLong("latestDepartureEpochMillis")
    } else {
        null
    }
    if (
        minimumStayMinutes != null &&
        maximumStayMinutes != null &&
        maximumStayMinutes < minimumStayMinutes
    ) {
        throw ApiException("추천 응답의 최대 체류시간이 최소 체류시간보다 짧습니다.")
    }
    return SafeRecommendation(
        place = place.toPlaceCandidate(
            stayMinutes = if (requireV1Fields) 0 else getInt("stayMinutes"),
            firstLegMinutes = route.getInt("firstLegMinutes"),
            secondLegMinutes = route.getInt("secondLegMinutes"),
            detourMinutes = route.getInt("detourMinutes"),
            firstLegDistanceMeters = route.optInt("firstLegDistanceMeters"),
            secondLegDistanceMeters = route.optInt("secondLegDistanceMeters"),
        ),
        totalMinutes = if (requireV1Fields) {
            route.getInt("firstLegMinutes") + route.getInt("secondLegMinutes")
        } else {
            getInt("totalMinutes")
        },
        marginMinutes = if (requireV1Fields) maximumStayMinutes ?: 0 else getInt("marginMinutes"),
        safetyLevel = runCatching {
            SafetyLevel.valueOf(getString("safetyLevel"))
        }.getOrDefault(SafetyLevel.TIGHT),
        routePoints = route.optJSONArray("path")?.mapObjects {
            Coordinates(
                latitude = it.getDouble("latitude"),
                longitude = it.getDouble("longitude"),
            )
        }.orEmpty(),
        operationStatus = runCatching {
            OperationStatus.valueOf(optString("operationStatus"))
        }.getOrDefault(OperationStatus.UNKNOWN),
        minimumStayMinutes = minimumStayMinutes,
        maximumStayMinutes = maximumStayMinutes,
        latestDepartureEpochMillis = latestDepartureEpochMillis,
    )
}

private fun JSONObject.requiredPositiveInt(name: String): Int {
    if (!has(name)) throw ApiException("추천 응답에 $name 값이 없습니다.")
    return getInt(name).takeIf { it > 0 }
        ?: throw ApiException("추천 응답의 $name 값이 올바르지 않습니다.")
}

private fun JSONObject.requiredPositiveLong(name: String): Long {
    if (!has(name)) throw ApiException("추천 응답에 $name 값이 없습니다.")
    return getLong(name).takeIf { it > 0 }
        ?: throw ApiException("추천 응답의 $name 값이 올바르지 않습니다.")
}

private fun JSONObject.toPlaceCandidate(
    stayMinutes: Int = optInt("default_stay_minutes", 40),
    firstLegMinutes: Int = 0,
    secondLegMinutes: Int = 0,
    detourMinutes: Int = 0,
    firstLegDistanceMeters: Int = 0,
    secondLegDistanceMeters: Int = 0,
) = PlaceCandidate(
    id = getString("content_id"),
    name = getString("name"),
    category = placeCategory(optString("category")),
    stayMinutes = stayMinutes,
    firstLegMinutes = firstLegMinutes,
    secondLegMinutes = secondLegMinutes,
    detourMinutes = detourMinutes,
    firstLegDistanceMeters = firstLegDistanceMeters,
    secondLegDistanceMeters = secondLegDistanceMeters,
    reason = "",
    tags = optJSONArray("tags")?.mapStrings().orEmpty(),
    address = optText("address"),
    imageUrl = optText("image_url"),
    latitude = optDouble("latitude").takeUnless { it.isNaN() },
    longitude = optDouble("longitude").takeUnless { it.isNaN() },
    openingHours = optText("opening_hours"),
    closedDays = optText("closed_days"),
    telephone = optText("tel"),
    homepageUrl = optText("homepage_url"),
    overview = optText("overview"),
    imageUrls = optJSONArray("image_urls")?.mapStrings().orEmpty(),
    lastAdmission = optText("last_admission"),
    parkingInfo = optText("parking_info"),
    eventStartDate = optText("event_start_date"),
    eventEndDate = optText("event_end_date"),
    dataProvenance = optText("data_provenance"),
    operatingInfoStatus = optText("operating_info_status"),
    admissionInfoStatus = optText("admission_info_status"),
    parkingInfoStatus = optText("parking_info_status"),
    reviewedAt = optText("reviewed_at"),
    detailItems = optJSONArray("detail_items")?.mapObjects {
        com.tteumsae.app.domain.PlaceDetailItem(
            title = it.optText("title"),
            description = it.optText("description"),
        )
    }?.filter { it.title.isNotBlank() && it.description.isNotBlank() }.orEmpty(),
    accessibilityItems = optJSONArray("accessibility_items")?.mapObjects {
        com.tteumsae.app.domain.PlaceDetailItem(
            title = it.optText("title"),
            description = it.optText("description"),
        )
    }?.filter { it.title.isNotBlank() && it.description.isNotBlank() }.orEmpty(),
    imageAttributions = optJSONArray("image_attributions")?.mapObjects {
        com.tteumsae.app.domain.PlaceImageAttribution(
            imageUrl = it.optText("image_url"),
            thumbnailUrl = it.optText("thumbnail_url"),
            name = it.optText("name"),
            copyrightType = it.optText("copyright_type"),
            copyrightLabel = it.optText("copyright_label"),
        )
    }?.filter { it.imageUrl.isNotBlank() || it.thumbnailUrl.isNotBlank() }.orEmpty(),
    congestionForecast = optJSONObject("congestion_forecast")?.let {
        com.tteumsae.app.domain.PlaceCongestionForecast(
            forecastDate = it.optText("forecast_date"),
            concentrationRate = it.optDouble("concentration_rate"),
            level = it.optText("level"),
            label = it.optText("label"),
            fetchedAt = it.optText("fetched_at"),
            source = it.optText("source"),
            basis = it.optText("basis"),
        )
    }?.takeIf {
        it.forecastDate.isNotBlank() &&
            it.label.isNotBlank() &&
            it.concentrationRate in 0.0..100.0
    },
    weatherForecast = optJSONObject("weather_forecast")?.let {
        com.tteumsae.app.domain.PlaceWeatherForecast(
            forecastAt = it.optText("forecast_at"),
            conditionLabel = it.optText("condition_label"),
            temperatureC = it.optDouble("temperature_c", Double.NaN),
            precipitationProbability = it.optNullableDouble("precipitation_probability"),
            windSpeedMps = it.optNullableDouble("wind_speed_mps"),
            issuedAt = it.optText("issued_at"),
            fetchedAt = it.optText("fetched_at"),
            source = it.optText("source"),
            basis = it.optText("basis"),
        )
    }?.takeIf {
        it.forecastAt.isNotBlank() &&
            it.conditionLabel.isNotBlank() &&
            it.temperatureC.isFinite()
    },
    nearbyParkingLots = optJSONArray("nearby_parking_lots")?.mapObjects {
        com.tteumsae.app.domain.NearbyParkingLot(
            id = it.optText("parking_id"),
            name = it.optText("name"),
            parkingType = it.optText("parking_type"),
            address = it.optText("address"),
            capacity = it.optNullableInt("capacity"),
            distanceMeters = it.optInt("distance_meters").coerceAtLeast(0),
            distanceBasis = it.optText("distance_basis"),
            feeSummary = it.optText("fee_summary"),
            operationSummary = it.optText("operation_summary"),
            accessibleParking = it.optNullableBoolean("accessible_parking"),
            phone = it.optText("phone"),
            referenceDate = it.optText("reference_date"),
            source = it.optText("source"),
        )
    }?.filter { it.id.isNotBlank() && it.name.isNotBlank() }.orEmpty(),
)

internal fun parsePlaceResponse(response: JSONObject): PlaceCandidate =
    response.getJSONObject("data").toPlaceCandidate()

private fun JSONObject.toRouteSummary() = RouteSummary(
    provider = optString("provider", "KAKAO_MOBILITY"),
    waypointCount = optInt("waypointCount"),
    totalDrivingMinutes = optInt("totalDrivingMinutes", optInt("durationMinutes")),
    totalDistanceMeters = optInt("totalDistanceMeters", optInt("distanceMeters")),
    tollFareWon = optInt("tollFareWon", optInt("tollFare")),
    legs = optJSONArray("legs")?.mapObjects {
        RouteLeg(
            drivingMinutes = it.optInt("drivingMinutes"),
            distanceMeters = it.optInt("distanceMeters"),
        )
    }.orEmpty(),
    path = optJSONArray("path")?.mapObjects {
        Coordinates(
            latitude = it.getDouble("latitude"),
            longitude = it.getDouble("longitude"),
        )
    }.orEmpty(),
)

private fun placeCategory(value: String): PlaceCategory = runCatching {
    PlaceCategory.valueOf(value)
}.getOrDefault(PlaceCategory.ATTRACTION)

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    buildList {
        for (index in 0 until length()) {
            add(transform(getJSONObject(index)))
        }
    }

private fun JSONArray.mapStrings(): List<String> =
    buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

private fun JSONObject.optText(name: String): String =
    if (!has(name) || isNull(name)) "" else optString(name).trim()

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name, Double.NaN).takeIf(Double::isFinite)

private fun JSONObject.optNullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name).takeIf { it >= 0 }

private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (!has(name) || isNull(name)) null else getBoolean(name)

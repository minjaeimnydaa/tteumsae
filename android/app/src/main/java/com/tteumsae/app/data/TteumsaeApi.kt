package com.tteumsae.app.data

import com.tteumsae.app.BuildConfig
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.LocationSearchResult
import com.tteumsae.app.domain.OperationStatus
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SafetyLevel
import com.tteumsae.app.domain.SearchCriteria
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
) {
    suspend fun searchPlaces(
        query: String,
        gangwonOnly: Boolean = false,
    ): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        val normalized = locationSearchQuery(query, gangwonOnly)
        val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())
        val response = request("GET", "/api/geocode?q=$encoded")
        response.getJSONArray("data").mapObjects { it.toLocationSearchResult() }
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

    suspend fun recommendations(criteria: SearchCriteria): RecommendationResult =
        withContext(Dispatchers.IO) {
            val start = criteria.startCoordinates
                ?: throw ApiException("출발지 좌표가 없습니다.")
            val destination = criteria.endCoordinates
                ?: throw ApiException("목적지 좌표가 없습니다.")
            val categories = JSONArray().apply {
                criteria.categories.forEach { put(it.name) }
            }
            val body = JSONObject()
                .put("mode", criteria.mode.name)
                .put("start", start.toJson())
                .put("destination", destination.toJson())
                .put("deadlineMinutes", criteria.deadlineMinutesFromNow)
                .put("safetyBufferMinutes", criteria.safetyBufferMinutes)
                .put("transport", criteria.transportMode.name)
                .put("categories", categories)

            val response = request("POST", "/api/recommendations", body)
            RecommendationResult(
                recommendations = response.getJSONArray("data").mapObjects { it.toRecommendation() },
                warning = response.optJSONObject("meta")?.optString("warning").orEmpty(),
            )
        }

    suspend fun places(page: Int = 1, pageSize: Int = 100): PlacePage =
        withContext(Dispatchers.IO) {
            val response = request(
                "GET",
                "/api/places?page=${page.coerceAtLeast(1)}&pageSize=${pageSize.coerceIn(1, 100)}",
            )
            PlacePage(
                places = response.getJSONArray("data")
                    .mapObjects { it.toPlaceCandidate() },
                hasMore = response.getJSONObject("pagination").getBoolean("hasMore"),
            )
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
                val message = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrDefault("서버 요청에 실패했습니다.")
                throw ApiException(message)
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
)

internal fun locationSearchQuery(query: String, gangwonOnly: Boolean): String =
    if (gangwonOnly && !query.contains("강원")) "강원 $query" else query

data class PlacePage(
    val places: List<PlaceCandidate>,
    val hasMore: Boolean,
)

class ApiException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

private fun Coordinates.toJson() = JSONObject()
    .put("latitude", latitude)
    .put("longitude", longitude)

private fun JSONObject.toLocationSearchResult() = LocationSearchResult(
    id = getString("id"),
    name = getString("name"),
    address = optString("address"),
    coordinates = Coordinates(
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
    ),
)

private fun JSONObject.toRecommendation(): SafeRecommendation {
    val place = getJSONObject("place")
    val route = getJSONObject("route")
    val address = place.optString("address")
    val routeProvider = route.optString("provider", "ESTIMATE")
    return SafeRecommendation(
        place = PlaceCandidate(
            id = place.getString("content_id"),
            name = place.getString("name"),
            category = placeCategory(place.optString("category")),
            stayMinutes = getInt("stayMinutes"),
            firstLegMinutes = route.getInt("firstLegMinutes"),
            secondLegMinutes = route.getInt("secondLegMinutes"),
            detourMinutes = route.getInt("detourMinutes"),
            reason = if (routeProvider == "KAKAO_MOBILITY") {
                "카카오 실시간 차량 이동시간을 반영했어요."
            } else {
                "도보 거리 추정 시간을 반영했어요."
            },
            tags = listOfNotNull(
                address.takeIf { it.isNotBlank() },
                routeProvider.takeIf { it == "KAKAO_MOBILITY" }?.let { "실시간 교통" },
            ),
            address = address,
            imageUrl = place.optString("image_url"),
            latitude = place.optDouble("latitude").takeUnless { it.isNaN() },
            longitude = place.optDouble("longitude").takeUnless { it.isNaN() },
            openingHours = place.optString("opening_hours"),
            closedDays = place.optString("closed_days"),
        ),
        totalMinutes = getInt("totalMinutes"),
        marginMinutes = getInt("marginMinutes"),
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
    )
}

private fun JSONObject.toPlaceCandidate() = PlaceCandidate(
    id = getString("content_id"),
    name = getString("name"),
    category = placeCategory(optString("category")),
    stayMinutes = optInt("default_stay_minutes", 40),
    firstLegMinutes = 0,
    secondLegMinutes = 0,
    detourMinutes = 0,
    reason = "한국관광공사 TourAPI에서 제공한 강원도 장소예요.",
    tags = optJSONArray("tags")?.mapStrings().orEmpty(),
    address = optString("address"),
    imageUrl = optString("image_url"),
    latitude = optDouble("latitude").takeUnless { it.isNaN() },
    longitude = optDouble("longitude").takeUnless { it.isNaN() },
    openingHours = optString("opening_hours"),
    closedDays = optString("closed_days"),
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

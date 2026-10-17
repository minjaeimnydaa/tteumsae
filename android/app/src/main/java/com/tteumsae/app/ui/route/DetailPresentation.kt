package com.tteumsae.app.ui.route

import com.tteumsae.app.domain.PlaceCandidate
import java.time.Instant
import java.time.ZoneId

internal fun normalizedVisitInfo(value: String?): String? = value
    ?.trim()
    ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }

internal fun readableDuration(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return buildString {
        if (hours > 0) append("${hours}시간")
        if (hours > 0 && remainingMinutes > 0) append(' ')
        if (remainingMinutes > 0 || hours == 0) append("${remainingMinutes}분")
    }
}

internal fun explicitActivityLabel(tags: List<String>): String? = tags.firstOrNull {
    it in setOf("야외 활동", "실내 활동", "실내·외 활동")
}

internal data class PlaceVisitFact(
    val label: String,
    val value: String,
)

internal fun mergeFreshPlaceDetails(
    routePlace: PlaceCandidate,
    freshPlace: PlaceCandidate,
): PlaceCandidate {
    fun preferFresh(fresh: String, original: String): String =
        normalizedVisitInfo(fresh) ?: original

    return routePlace.copy(
        name = preferFresh(freshPlace.name, routePlace.name),
        category = freshPlace.category,
        tags = freshPlace.tags.ifEmpty { routePlace.tags },
        address = preferFresh(freshPlace.address, routePlace.address),
        imageUrl = preferFresh(freshPlace.imageUrl, routePlace.imageUrl),
        latitude = freshPlace.latitude ?: routePlace.latitude,
        longitude = freshPlace.longitude ?: routePlace.longitude,
        openingHours = preferFresh(freshPlace.openingHours, routePlace.openingHours),
        closedDays = preferFresh(freshPlace.closedDays, routePlace.closedDays),
        telephone = preferFresh(freshPlace.telephone, routePlace.telephone),
        homepageUrl = preferFresh(freshPlace.homepageUrl, routePlace.homepageUrl),
        overview = preferFresh(freshPlace.overview, routePlace.overview),
        imageUrls = freshPlace.imageUrls.ifEmpty { routePlace.imageUrls },
        lastAdmission = preferFresh(freshPlace.lastAdmission, routePlace.lastAdmission),
        parkingInfo = preferFresh(freshPlace.parkingInfo, routePlace.parkingInfo),
        eventStartDate = preferFresh(freshPlace.eventStartDate, routePlace.eventStartDate),
        eventEndDate = preferFresh(freshPlace.eventEndDate, routePlace.eventEndDate),
        dataProvenance = preferFresh(freshPlace.dataProvenance, routePlace.dataProvenance),
        operatingInfoStatus = preferFresh(
            freshPlace.operatingInfoStatus,
            routePlace.operatingInfoStatus,
        ),
        admissionInfoStatus = preferFresh(
            freshPlace.admissionInfoStatus,
            routePlace.admissionInfoStatus,
        ),
        parkingInfoStatus = preferFresh(
            freshPlace.parkingInfoStatus,
            routePlace.parkingInfoStatus,
        ),
        reviewedAt = preferFresh(freshPlace.reviewedAt, routePlace.reviewedAt),
        detailItems = freshPlace.detailItems.ifEmpty { routePlace.detailItems },
        accessibilityItems = freshPlace.accessibilityItems.ifEmpty {
            routePlace.accessibilityItems
        },
        imageAttributions = freshPlace.imageAttributions.ifEmpty {
            routePlace.imageAttributions
        },
        congestionForecast = freshPlace.congestionForecast ?: routePlace.congestionForecast,
        weatherForecast = freshPlace.weatherForecast ?: routePlace.weatherForecast,
        nearbyParkingLots = freshPlace.nearbyParkingLots.ifEmpty {
            routePlace.nearbyParkingLots
        },
    )
}

internal fun nearbyParkingDescription(
    parking: com.tteumsae.app.domain.NearbyParkingLot,
): String = buildList {
    val headline = buildList {
        add("직선 약 ${parking.distanceMeters}m")
        normalizedVisitInfo(parking.parkingType)?.let(::add)
        parking.capacity?.let { add("${it}면") }
    }.joinToString(" · ")
    add(headline)
    normalizedVisitInfo(parking.feeSummary)?.let(::add)
    normalizedVisitInfo(parking.operationSummary)?.let(::add)
    if (parking.accessibleParking == true) add("장애인 전용 주차구역 있음")
    normalizedVisitInfo(parking.address)?.let(::add)
    val source = normalizedVisitInfo(parking.source)
    val referenceDate = normalizedVisitInfo(parking.referenceDate)?.replace('-', '.')
    listOfNotNull(source, referenceDate?.let { "$it 기준" })
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
        ?.let(::add)
}.joinToString("\n")

internal fun practicalVisitFacts(place: PlaceCandidate): List<PlaceVisitFact> = buildList {
    normalizedVisitInfo(place.openingHours)?.let { add(PlaceVisitFact("운영시간", it)) }
    normalizedVisitInfo(place.closedDays)?.let {
        add(PlaceVisitFact("휴무일", structuredClosedDays(it)))
    }
    normalizedVisitInfo(place.lastAdmission)?.let { add(PlaceVisitFact("입장 마감", it)) }
    eventPeriodLabel(place.eventStartDate, place.eventEndDate)?.let {
        add(PlaceVisitFact("행사 기간", it))
    }
    normalizedVisitInfo(place.parkingInfo)?.let { add(PlaceVisitFact("주차", it)) }
    place.congestionForecast?.let { forecast ->
        congestionForecastDateLabel(forecast.forecastDate)?.let { dateLabel ->
            add(
                PlaceVisitFact(
                    "$dateLabel 혼잡",
                    "${forecast.label} · 평소 최고치 대비 ${forecast.concentrationRate.toInt()}%",
                ),
            )
        }
    }
    place.weatherForecast?.let { forecast ->
        weatherForecastDateTimeLabel(forecast.forecastAt)?.let { dateTimeLabel ->
            val values = buildList {
                add(forecast.conditionLabel)
                add("${weatherNumber(forecast.temperatureC)}℃")
                forecast.precipitationProbability?.let {
                    add("강수확률 ${weatherNumber(it)}%")
                }
                forecast.windSpeedMps?.let { add("바람 ${weatherNumber(it)}m/s") }
                normalizedVisitInfo(forecast.source)?.let(::add)
            }
            add(PlaceVisitFact("$dateTimeLabel 날씨", values.joinToString(" · ")))
        }
    }
}

private fun congestionForecastDateLabel(value: String): String? {
    val parts = value.split('-')
    if (parts.size != 3) return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return "${month}월 ${day}일"
}

private fun weatherForecastDateTimeLabel(value: String): String? = runCatching {
    val time = Instant.parse(value).atZone(ZoneId.of("Asia/Seoul"))
    "${time.monthValue}월 ${time.dayOfMonth}일 ${time.hour}시"
}.getOrNull()

private fun weatherNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)

internal fun structuredClosedDays(value: String): String = value
    .split(Regex("\\s*/\\s*"))
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString(separator = "\n") { "• $it" }
    .ifBlank { value.trim() }

internal fun eventPeriodLabel(start: String?, end: String?): String? {
    val normalizedStart = normalizedDate(start)
    val normalizedEnd = normalizedDate(end)
    return when {
        normalizedStart == null && normalizedEnd == null -> null
        normalizedStart == normalizedEnd -> normalizedStart
        normalizedStart == null -> "~ $normalizedEnd"
        normalizedEnd == null -> "$normalizedStart ~"
        else -> "$normalizedStart ~ $normalizedEnd"
    }
}

private fun normalizedDate(value: String?): String? {
    val digits = normalizedVisitInfo(value)?.filter(Char::isDigit) ?: return null
    if (digits.length != 8) return normalizedVisitInfo(value)
    return "${digits.substring(0, 4)}.${digits.substring(4, 6)}.${digits.substring(6, 8)}"
}

internal fun plainTourText(value: String?): String? = normalizedVisitInfo(value)
    ?.replace(Regex("(?i)<br\\s*/?>"), "\n")
    ?.replace(Regex("<[^>]+>"), "")
    ?.replace("&nbsp;", " ")
    ?.replace("&amp;", "&")
    ?.replace("&lt;", "<")
    ?.replace("&gt;", ">")
    ?.replace("&quot;", "\"")
    ?.replace(Regex("[ \\t]+"), " ")
    ?.replace(Regex("\\n{3,}"), "\n\n")
    ?.trim()
    ?.takeIf(String::isNotBlank)

internal fun normalizedHomepageUrl(value: String?): String? {
    val raw = normalizedVisitInfo(value) ?: return null
    val href = Regex("(?i)href\\s*=\\s*[\"']([^\"']+)[\"']")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
    return (href ?: plainTourText(raw))
        ?.trim()
        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}

internal fun placeSourceCaption(place: PlaceCandidate): String {
    val source = if (place.dataProvenance.equals("CURATION", ignoreCase = true)) {
        "공식 자료 확인 · 한국관광공사 TourAPI"
    } else {
        "한국관광공사 TourAPI"
    }
    val reviewed = normalizedVisitInfo(place.reviewedAt)
        ?.take(10)
        ?.replace('-', '.')
    return if (reviewed == null) source else "$source · $reviewed 확인"
}

internal const val TOUR_PHOTO_USAGE_CONDITIONS_URL =
    "https://www.data.go.kr/data/15101578/openapi.do"

/** Only unambiguous rights for the actual displayed URL can permit cropping. */
internal fun placePhotoMayCrop(place: PlaceCandidate, imageUrl: String?): Boolean =
    matchingPhotoCopyrightType(place, imageUrl) == "Type1"

private fun matchingPhotoCopyrightType(place: PlaceCandidate, imageUrl: String?): String? {
    val normalizedUrl = normalizedVisitInfo(imageUrl) ?: return null
    val matches = place.imageAttributions.filter {
        normalizedVisitInfo(it.imageUrl) == normalizedUrl ||
            normalizedVisitInfo(it.thumbnailUrl) == normalizedUrl
    }
    if (matches.isEmpty()) return null
    val type = matches.map { normalizedVisitInfo(it.copyrightType) }.distinct().singleOrNull()
    val expectedLabel = when (type) {
        "Type1" -> "공공누리 제1유형"
        "Type3" -> "공공누리 제3유형"
        else -> return null
    }
    // Conflicting duplicate metadata or a contradictory label is not permission.
    if (matches.any { normalizedVisitInfo(it.copyrightLabel)?.let { label -> label != expectedLabel } == true }) {
        return null
    }
    return type
}

internal fun placePhotoSourceCaption(place: PlaceCandidate, imageUrl: String?): String? {
    if (normalizedVisitInfo(imageUrl) == null) return null
    val condition = when (matchingPhotoCopyrightType(place, imageUrl)) {
        "Type1" -> "공공누리 제1유형"
        "Type3" -> "공공누리 제3유형"
        else -> "개별 이용조건 확인"
    }
    return "사진 · 한국관광공사 TourAPI · $condition"
}

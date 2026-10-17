package com.tteumsae.app.data.local

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import org.json.JSONArray
import org.json.JSONObject

object SavedPlaceSnapshotCodec {
    fun encode(place: PlaceCandidate): String = JSONObject()
        .put("id", place.id)
        .put("name", place.name)
        .put("category", place.category.name)
        .put("stayMinutes", place.stayMinutes)
        .put("tags", JSONArray(place.tags))
        .put("address", place.address)
        .put("imageUrl", place.imageUrl)
        .put("latitude", place.latitude ?: JSONObject.NULL)
        .put("longitude", place.longitude ?: JSONObject.NULL)
        .put("openingHours", place.openingHours)
        .put("closedDays", place.closedDays)
        .put("telephone", place.telephone)
        .put("homepageUrl", place.homepageUrl)
        .put("overview", place.overview)
        .put("imageUrls", JSONArray(place.imageUrls))
        .put(
            "detailItems",
            JSONArray().apply {
                place.detailItems.forEach { item ->
                    put(
                        JSONObject()
                            .put("title", item.title)
                            .put("description", item.description),
                    )
                }
            },
        )
        .put(
            "accessibilityItems",
            JSONArray().apply {
                place.accessibilityItems.forEach { item ->
                    put(
                        JSONObject()
                            .put("title", item.title)
                            .put("description", item.description),
                    )
                }
            },
        )
        .put(
            "nearbyParkingLots",
            JSONArray().apply {
                place.nearbyParkingLots.forEach { parking ->
                    put(
                        JSONObject()
                            .put("id", parking.id)
                            .put("name", parking.name)
                            .put("parkingType", parking.parkingType)
                            .put("address", parking.address)
                            .put("capacity", parking.capacity ?: JSONObject.NULL)
                            .put("distanceMeters", parking.distanceMeters)
                            .put("distanceBasis", parking.distanceBasis)
                            .put("feeSummary", parking.feeSummary)
                            .put("operationSummary", parking.operationSummary)
                            .put("accessibleParking", parking.accessibleParking ?: JSONObject.NULL)
                            .put("phone", parking.phone)
                            .put("referenceDate", parking.referenceDate)
                            .put("source", parking.source),
                    )
                }
            },
        )
        .put(
            "imageAttributions",
            JSONArray().apply {
                place.imageAttributions.forEach { attribution ->
                    put(
                        JSONObject()
                            .put("imageUrl", attribution.imageUrl)
                            .put("thumbnailUrl", attribution.thumbnailUrl)
                            .put("name", attribution.name)
                            .put("copyrightType", attribution.copyrightType)
                            .put("copyrightLabel", attribution.copyrightLabel),
                    )
                }
            },
        )
        .put("lastAdmission", place.lastAdmission)
        .put("parkingInfo", place.parkingInfo)
        .put("eventStartDate", place.eventStartDate)
        .put("eventEndDate", place.eventEndDate)
        .put("dataProvenance", place.dataProvenance)
        .put("operatingInfoStatus", place.operatingInfoStatus)
        .put("admissionInfoStatus", place.admissionInfoStatus)
        .put("parkingInfoStatus", place.parkingInfoStatus)
        .put("reviewedAt", place.reviewedAt)
        .toString()

    fun decode(snapshotJson: String): PlaceCandidate? = runCatching {
        val json = JSONObject(snapshotJson)
        val tags = json.optJSONArray("tags")
        val imageUrls = json.optJSONArray("imageUrls")
        val detailItems = json.optJSONArray("detailItems")
        val accessibilityItems = json.optJSONArray("accessibilityItems")
        val nearbyParkingLots = json.optJSONArray("nearbyParkingLots")
        val imageAttributions = json.optJSONArray("imageAttributions")
        PlaceCandidate(
            id = json.getString("id"),
            name = json.getString("name"),
            category = PlaceCategory.valueOf(json.getString("category")),
            stayMinutes = json.getInt("stayMinutes"),
            firstLegMinutes = 0,
            secondLegMinutes = 0,
            detourMinutes = 0,
            firstLegDistanceMeters = 0,
            secondLegDistanceMeters = 0,
            reason = "",
            tags = buildList {
                if (tags != null) {
                    for (index in 0 until tags.length()) {
                        add(tags.getString(index))
                    }
                }
            },
            address = json.optString("address"),
            imageUrl = json.optString("imageUrl"),
            latitude = json.optNullableDouble("latitude"),
            longitude = json.optNullableDouble("longitude"),
            isOpen = true,
            openingHours = json.optString("openingHours"),
            closedDays = json.optString("closedDays"),
            telephone = json.optString("telephone"),
            homepageUrl = json.optString("homepageUrl"),
            overview = json.optString("overview"),
            imageUrls = buildList {
                if (imageUrls != null) {
                    for (index in 0 until imageUrls.length()) {
                        imageUrls.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            },
            lastAdmission = json.optString("lastAdmission"),
            parkingInfo = json.optString("parkingInfo"),
            eventStartDate = json.optString("eventStartDate"),
            eventEndDate = json.optString("eventEndDate"),
            dataProvenance = json.optString("dataProvenance"),
            operatingInfoStatus = json.optString("operatingInfoStatus"),
            admissionInfoStatus = json.optString("admissionInfoStatus"),
            parkingInfoStatus = json.optString("parkingInfoStatus"),
            reviewedAt = json.optString("reviewedAt"),
            detailItems = buildList {
                if (detailItems != null) {
                    for (index in 0 until detailItems.length()) {
                        val item = detailItems.optJSONObject(index) ?: continue
                        val title = item.optString("title").trim()
                        val description = item.optString("description").trim()
                        if (title.isNotBlank() && description.isNotBlank()) {
                            add(com.tteumsae.app.domain.PlaceDetailItem(title, description))
                        }
                    }
                }
            },
            accessibilityItems = buildList {
                if (accessibilityItems != null) {
                    for (index in 0 until accessibilityItems.length()) {
                        val item = accessibilityItems.optJSONObject(index) ?: continue
                        val title = item.optString("title").trim()
                        val description = item.optString("description").trim()
                        if (title.isNotBlank() && description.isNotBlank()) {
                            add(com.tteumsae.app.domain.PlaceDetailItem(title, description))
                        }
                    }
                }
            },
            nearbyParkingLots = buildList {
                if (nearbyParkingLots != null) {
                    for (index in 0 until nearbyParkingLots.length()) {
                        val item = nearbyParkingLots.optJSONObject(index) ?: continue
                        val id = item.optString("id").trim()
                        val name = item.optString("name").trim()
                        if (id.isBlank() || name.isBlank()) continue
                        add(
                            com.tteumsae.app.domain.NearbyParkingLot(
                                id = id,
                                name = name,
                                parkingType = item.optString("parkingType").trim(),
                                address = item.optString("address").trim(),
                                capacity = item.optNullableInt("capacity"),
                                distanceMeters = item.optInt("distanceMeters").coerceAtLeast(0),
                                distanceBasis = item.optString("distanceBasis").trim(),
                                feeSummary = item.optString("feeSummary").trim(),
                                operationSummary = item.optString("operationSummary").trim(),
                                accessibleParking = item.optNullableBoolean("accessibleParking"),
                                phone = item.optString("phone").trim(),
                                referenceDate = item.optString("referenceDate").trim(),
                                source = item.optString("source").trim(),
                            ),
                        )
                    }
                }
            },
            imageAttributions = buildList {
                if (imageAttributions != null) {
                    for (index in 0 until imageAttributions.length()) {
                        val item = imageAttributions.optJSONObject(index) ?: continue
                        val imageUrl = item.optString("imageUrl").trim()
                        val thumbnailUrl = item.optString("thumbnailUrl").trim()
                        if (imageUrl.isNotBlank() || thumbnailUrl.isNotBlank()) {
                            add(
                                com.tteumsae.app.domain.PlaceImageAttribution(
                                    imageUrl = imageUrl,
                                    thumbnailUrl = thumbnailUrl,
                                    name = item.optString("name").trim(),
                                    copyrightType = item.optString("copyrightType").trim(),
                                    copyrightLabel = item.optString("copyrightLabel").trim(),
                                ),
                            )
                        }
                    }
                }
            },
        )
    }.getOrNull()
}

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONObject.optNullableBoolean(key: String): Boolean? =
    if (!has(key) || isNull(key)) null else getBoolean(key)

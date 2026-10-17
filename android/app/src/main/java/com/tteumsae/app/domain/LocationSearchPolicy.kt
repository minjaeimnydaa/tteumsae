package com.tteumsae.app.domain

enum class DestinationSupport {
    SUPPORTED,
    UNKNOWN,
    UNSUPPORTED,
}

fun destinationSupport(address: String): DestinationSupport {
    val normalized = address.trim()
    if (normalized.isEmpty()) return DestinationSupport.UNKNOWN
    return if (normalized.startsWith("강원")) {
        DestinationSupport.SUPPORTED
    } else {
        DestinationSupport.UNSUPPORTED
    }
}

fun prioritizeGangwonDestinations(
    results: List<LocationSearchResult>,
): List<LocationSearchResult> = results.sortedBy { destinationSupport(it.address).ordinal }

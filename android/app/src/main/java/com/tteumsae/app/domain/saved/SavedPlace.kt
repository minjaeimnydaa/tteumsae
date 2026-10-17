package com.tteumsae.app.domain.saved

import com.tteumsae.app.domain.PlaceCandidate

data class SavedPlace(
    val place: PlaceCandidate,
    val savedAtMillis: Long,
)

fun List<SavedPlace>.latestFirst(): List<SavedPlace> = sortedByDescending { it.savedAtMillis }

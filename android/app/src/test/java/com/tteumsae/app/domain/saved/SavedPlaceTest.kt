package com.tteumsae.app.domain.saved

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPlaceTest {
    @Test
    fun saved_places_are_latest_first() {
        val old = SavedPlace(place("old"), 10)
        val recent = SavedPlace(place("recent"), 20)

        assertEquals(
            listOf("recent", "old"),
            listOf(old, recent).latestFirst().map { it.place.id },
        )
    }

    private fun place(id: String) = PlaceCandidate(
        id = id,
        name = id,
        category = PlaceCategory.ATTRACTION,
        stayMinutes = 15,
        firstLegMinutes = 0,
        secondLegMinutes = 0,
        detourMinutes = 0,
        reason = "",
        tags = emptyList(),
    )
}

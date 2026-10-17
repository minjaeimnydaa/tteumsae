package com.tteumsae.app.domain.recommendation

import com.tteumsae.app.domain.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationFiltersTest {
    @Test
    fun any_and_specific_intent_do_not_coexist() {
        assertEquals(
            setOf(RecommendationIntent.CAFE),
            toggleRecommendationIntent(
                setOf(RecommendationIntent.ANY),
                RecommendationIntent.CAFE,
            ),
        )
    }

    @Test
    fun no_food_excludes_restaurants() {
        val (categories, excludesRestaurants) = recommendationIntentFilters(
            setOf(RecommendationIntent.NO_FOOD),
        )

        assertTrue(excludesRestaurants)
        assertFalse(PlaceCategory.RESTAURANT in categories)
    }
}

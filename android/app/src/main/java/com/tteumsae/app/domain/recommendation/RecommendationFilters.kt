package com.tteumsae.app.domain.recommendation

import com.tteumsae.app.domain.PlaceCategory

internal enum class RecommendationIntent(val label: String) {
    ANY("아무거나"),
    MEAL("식사"),
    CAFE("카페"),
    WALK_TOUR("산책·관광"),
    INDOOR("실내 활동"),
    NO_FOOD("지금은 음식 제외"),
}

internal fun matchesGangwonRegion(address: String, region: String): Boolean =
    region == "강원도 전체" || address.contains(region)

internal fun recommendationCategories(
    selected: Set<PlaceCategory>,
    excludeRestaurants: Boolean,
): Set<PlaceCategory> {
    if (!excludeRestaurants) return selected
    return (selected.ifEmpty { PlaceCategory.entries.toSet() } - PlaceCategory.RESTAURANT)
        .ifEmpty { PlaceCategory.entries.toSet() - PlaceCategory.RESTAURANT }
}

internal fun recommendationIntentFilters(
    intent: RecommendationIntent,
): Pair<Set<PlaceCategory>, Boolean> = when (intent) {
    RecommendationIntent.ANY -> emptySet<PlaceCategory>() to false
    RecommendationIntent.MEAL -> setOf(PlaceCategory.RESTAURANT) to false
    RecommendationIntent.CAFE -> setOf(PlaceCategory.CAFE) to false
    RecommendationIntent.WALK_TOUR -> setOf(PlaceCategory.ATTRACTION, PlaceCategory.LEISURE) to false
    RecommendationIntent.INDOOR -> setOf(PlaceCategory.CULTURE, PlaceCategory.SHOPPING) to false
    RecommendationIntent.NO_FOOD -> emptySet<PlaceCategory>() to true
}

internal fun toggleRecommendationIntent(
    current: Set<RecommendationIntent>,
    intent: RecommendationIntent,
): Set<RecommendationIntent> {
    if (intent == RecommendationIntent.ANY) return setOf(RecommendationIntent.ANY)
    val updated = (current - RecommendationIntent.ANY).toMutableSet()
    if (!updated.add(intent)) updated.remove(intent)
    if (intent == RecommendationIntent.NO_FOOD) updated.remove(RecommendationIntent.MEAL)
    if (intent == RecommendationIntent.MEAL) updated.remove(RecommendationIntent.NO_FOOD)
    return updated.ifEmpty { setOf(RecommendationIntent.ANY) }
}

internal fun recommendationIntentFilters(
    intents: Set<RecommendationIntent>,
): Pair<Set<PlaceCategory>, Boolean> {
    if (RecommendationIntent.ANY in intents) return emptySet<PlaceCategory>() to false
    val categories = buildSet {
        intents.forEach { intent -> addAll(recommendationIntentFilters(intent).first) }
    }
    return categories to (RecommendationIntent.NO_FOOD in intents)
}

internal fun selectedRecommendationIntent(
    categories: Set<PlaceCategory>,
    excludeRestaurants: Boolean,
): RecommendationIntent = when {
    excludeRestaurants -> RecommendationIntent.NO_FOOD
    categories == setOf(PlaceCategory.RESTAURANT) -> RecommendationIntent.MEAL
    categories == setOf(PlaceCategory.CAFE) -> RecommendationIntent.CAFE
    categories == setOf(PlaceCategory.ATTRACTION, PlaceCategory.LEISURE) -> RecommendationIntent.WALK_TOUR
    categories == setOf(PlaceCategory.CULTURE, PlaceCategory.SHOPPING) -> RecommendationIntent.INDOOR
    else -> RecommendationIntent.ANY
}

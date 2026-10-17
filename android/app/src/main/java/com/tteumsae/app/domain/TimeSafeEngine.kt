package com.tteumsae.app.domain

object TimeSafeEngine {
    fun recommend(
        criteria: SearchCriteria,
        candidates: List<PlaceCandidate>,
    ): List<SafeRecommendation> {
        return candidates
            .asSequence()
            .filter { it.isOpen }
            .filter {
                criteria.categories.isEmpty() || it.category in criteria.categories
            }
            .map { place ->
                val total = place.firstLegMinutes +
                    place.stayMinutes +
                    place.secondLegMinutes
                val margin = criteria.deadlineMinutesFromNow - total
                SafeRecommendation(
                    place = place,
                    totalMinutes = total,
                    marginMinutes = margin,
                    safetyLevel = safetyLevel(margin),
                )
            }
            .filter { it.marginMinutes >= criteria.safetyBufferMinutes }
            .sortedWith(
                compareByDescending<SafeRecommendation> { it.marginMinutes }
                    .thenBy { it.place.detourMinutes },
            )
            .toList()
    }

    fun safetyLevel(marginMinutes: Int): SafetyLevel = when {
        marginMinutes >= 20 -> SafetyLevel.COMFORTABLE
        marginMinutes >= 10 -> SafetyLevel.AVAILABLE
        else -> SafetyLevel.TIGHT
    }
}


package com.tteumsae.app.ui.common

internal fun compactTags(
    tags: List<String>,
    characterBudget: Int = 18,
): Pair<List<String>, Int> {
    var used = 0
    val visible = tags.takeWhile { tag ->
        val fits = used + tag.length + 1 <= characterBudget
        if (fits) used += tag.length + 1
        fits
    }
    return visible to (tags.size - visible.size)
}

internal fun formatMinutes(minutes: Int): String =
    when {
        minutes < 60 -> "${minutes}분"
        minutes % 60 == 0 -> "${minutes / 60}시간"
        else -> "${minutes / 60}시간 ${minutes % 60}분"
    }

internal fun formatDistance(meters: Int): String = when {
    meters <= 0 -> "거리 계산 중"
    meters < 1_000 -> "${meters}m"
    else -> "${"%.1f".format(meters / 1_000.0)}km"
}

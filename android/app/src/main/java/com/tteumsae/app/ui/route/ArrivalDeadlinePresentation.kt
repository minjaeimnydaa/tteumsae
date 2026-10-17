package com.tteumsae.app.ui.route

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun quickArrivalDeadline(minutesFromNow: Int, nowEpochMillis: Long): Long {
    require(minutesFromNow in 15..1_440)
    return nowEpochMillis + minutesFromNow * 60_000L
}

internal fun formatArrivalDeadline(epochMillis: Long, nowEpochMillis: Long): String {
    val zoneId = ZoneId.of("Asia/Seoul")
    val selected = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
    val dayPrefix = when (selected.toLocalDate()) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> selected.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN))
    }
    return "$dayPrefix ${selected.format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN))}까지"
}

package com.tteumsae.app.reminder

import com.tteumsae.app.domain.Coordinates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderLocationModeTest {
    @Test
    fun `수동 빌드는 이전 GPS 또는 출처 없는 여행을 복원하지 않는다`() {
        val preferences = FakePreferences()
        val store = ActiveTripStore(preferences, locationMode = "manual_v1")
        preferences.value = ActiveTripCodec.encode(trip("automatic_v1"))

        assertNull(store.loadValid(0))
        assertNull(preferences.value)

        preferences.value = JSONObject(ActiveTripCodec.encode(trip("manual_v1")))
            .apply { remove("schemaVersion"); remove("locationMode"); remove("navigationToken") }
            .toString()
        assertNull(store.loadValid(0))
        assertNull(preferences.value)
    }

    @Test
    fun `새 수동 알림은 복원하고 자동 빌드로 바뀌면 다시 사용하지 않는다`() {
        val preferences = FakePreferences()
        val manualStore = ActiveTripStore(preferences, locationMode = "manual_v1")
        val manualTrip = trip("manual_v1")
        manualStore.save(manualTrip)

        assertEquals(manualTrip, manualStore.loadValid(0))
        assertNull(ActiveTripStore(preferences, locationMode = "automatic_v1").loadValid(0))
        assertNull(preferences.value)
    }

    @Test
    fun `새 자동 모드의 알림은 같은 자동 모드에서 유지된다`() {
        val store = ActiveTripStore(FakePreferences(), locationMode = "automatic_v1")
        val automaticTrip = trip("automatic_v1")
        store.save(automaticTrip)

        assertEquals(automaticTrip, store.loadValid(0))
    }

    @Test
    fun `위치 모드가 바뀌면 차단된 알림의 복구 기록도 지운다`() {
        val preferences = FakePreferences()
        val manualStore = ActiveTripStore(preferences, locationMode = "manual_v1")
        val manualTrip = trip("manual_v1")
        manualStore.save(manualTrip)
        manualStore.markDeliveryPending(manualTrip, 0)

        assertNull(ActiveTripStore(preferences, locationMode = "automatic_v1").takePendingDelivery(0))
        assertNull(preferences.pendingToken)
    }

    @Test
    fun `비어 있는 토큰은 알림 복구 기록을 만들지 않는다`() {
        val preferences = FakePreferences()
        val store = ActiveTripStore(preferences, locationMode = "manual_v1")
        val invalid = trip("manual_v1").copy(navigationToken = "")
        preferences.value = ActiveTripCodec.encode(invalid)

        store.markDeliveryPending(invalid, 0)

        assertNull(preferences.value)
        assertNull(preferences.pendingToken)
    }

    @Test
    fun `알림 탭은 현재 여행의 모드와 토큰이 일치할 때만 경로를 연다`() {
        val current = trip("manual_v1")
        assertEquals(current.navigationUrl, navigationUrlForReminderTap(current, "manual_v1", "current-token", 0, "manual_v1"))
        assertNull(navigationUrlForReminderTap(current, "automatic_v1", "current-token", 0, "manual_v1"))
        assertNull(navigationUrlForReminderTap(current, "manual_v1", "old-token", 0, "manual_v1"))
        assertNull(navigationUrlForReminderTap(trip("automatic_v1"), "automatic_v1", "current-token", 0, "manual_v1"))
        assertNull(navigationUrlForReminderTap(null, "manual_v1", "current-token", 0, "manual_v1"))
    }

    @Test
    fun `만료된 여행과 출발 마감이 지난 알림 탭은 예전 경로를 열지 않는다`() {
        val current = trip("manual_v1")
        assertNull(navigationUrlForReminderTap(current, "manual_v1", "current-token", current.latestDepartureEpochMillis, "manual_v1"))
        assertNull(navigationUrlForReminderTap(current.copy(expiresAtEpochMillis = 1), "manual_v1", "current-token", 1, "manual_v1"))
        assertNull(navigationUrlForReminderTap(current.copy(navigationToken = ""), "manual_v1", "", 0, "manual_v1"))
    }

    private fun trip(mode: String) = ActiveTrip(
        startName = "출발", start = Coordinates(37.1, 128.1),
        destinationName = "도착", destination = Coordinates(37.3, 128.3),
        stopId = "one", stopName = "경유", stop = Coordinates(37.2, 128.2),
        arrivalDeadlineEpochMillis = 1_000_000, latestDepartureEpochMillis = 700_000,
        navigationUrl = "https://map.kakao.com/one", expiresAtEpochMillis = 8_200_000,
        locationMode = mode, navigationToken = "current-token",
    )

    private class FakePreferences : ActiveTripPreferences {
        var value: String? = null
        var pendingToken: String? = null
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
        override fun readPendingDeliveryToken(): String? = pendingToken
        override fun writePendingDeliveryToken(value: String?) { pendingToken = value }
        override fun clear() { value = null; pendingToken = null }
    }
}

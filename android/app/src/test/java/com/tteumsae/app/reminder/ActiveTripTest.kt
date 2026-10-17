package com.tteumsae.app.reminder

import com.tteumsae.app.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveTripTest {
    @Test
    fun `활성 여행은 필요한 좌표와 마감만 JSON으로 왕복한다`() {
        val trip = trip("one")

        assertEquals(trip, ActiveTripCodec.decode(ActiveTripCodec.encode(trip)))
    }

    @Test
    fun `새 여행은 이전 여행을 교체하고 만료 여행은 제거한다`() {
        val preferences = FakePreferences()
        val store = ActiveTripStore(preferences)
        store.save(trip("one"))
        store.save(trip("two"))

        assertEquals("two", store.loadValid(nowEpochMillis = 5_000)?.stopName)
        assertNull(store.loadValid(nowEpochMillis = 8_201_000))
        assertNull(preferences.value)
    }

    @Test
    fun `여행 만료는 도착 마감 두 시간 뒤다`() {
        assertEquals(8_200_000, activeTripExpiryEpochMillis(1_000_000))
    }

    @Test
    fun `여행이 만료되면 차단된 전송 복구도 폐기한다`() {
        val preferences = FakePreferences()
        val store = ActiveTripStore(preferences)
        val current = trip("one")
        store.save(current)
        store.markDeliveryPending(current, 0)

        assertNull(ActiveTripStore(preferences).takePendingDelivery(8_200_000))
        assertNull(preferences.pendingToken)
    }

    private fun trip(name: String) = ActiveTrip(
        startName = "출발",
        start = Coordinates(37.1, 128.1),
        destinationName = "도착",
        destination = Coordinates(37.3, 128.3),
        stopId = name,
        stopName = name,
        stop = Coordinates(37.2, 128.2),
        arrivalDeadlineEpochMillis = 1_000_000,
        latestDepartureEpochMillis = 700_000,
        navigationUrl = "https://map.kakao.com/$name",
        expiresAtEpochMillis = activeTripExpiryEpochMillis(1_000_000),
    )

    private class FakePreferences : ActiveTripPreferences {
        var value: String? = null
        var pendingToken: String? = null

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }

        override fun readPendingDeliveryToken(): String? = pendingToken

        override fun writePendingDeliveryToken(value: String?) {
            pendingToken = value
        }

        override fun clear() {
            value = null
            pendingToken = null
        }
    }
}

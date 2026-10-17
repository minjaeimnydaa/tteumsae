package com.tteumsae.app.reminder

import com.tteumsae.app.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepartureReminderCoordinatorTest {
    @Test
    fun `새 검색은 저장된 여행과 예약을 함께 제거한다`() {
        val fixture = Fixture()
        fixture.coordinator.enable(trip("old", 700_000))

        fixture.coordinator.clear()

        assertNull(fixture.store.loadValid(nowEpochMillis = 0))
        assertEquals(1, fixture.scheduler.cancelCount)
    }

    @Test
    fun `재조회에서 선택 장소가 사라지면 이전 알림을 제거한다`() {
        val fixture = Fixture()
        fixture.coordinator.enable(trip("old", 700_000))

        assertNull(fixture.coordinator.reconcile(null))

        assertNull(fixture.store.loadValid(nowEpochMillis = 0))
        assertEquals(1, fixture.scheduler.cancelCount)
    }

    @Test
    fun `재조회에서 같은 장소가 남으면 새 출발 마감으로 교체한다`() {
        val fixture = Fixture()
        fixture.coordinator.enable(trip("same", 700_000))
        val updated = trip("same", 760_000)

        assertEquals("same", fixture.coordinator.reconcile(updated))

        assertEquals(updated, fixture.store.loadValid(nowEpochMillis = 0))
        assertEquals(updated, fixture.scheduler.scheduled.last())
    }

    @Test
    fun `권한 응답이 늦어 출발 마감이 지나면 저장하거나 예약하지 않는다`() {
        val fixture = Fixture()

        assertNull(fixture.coordinator.enable(trip("late", -1)))

        assertNull(fixture.store.loadValid(nowEpochMillis = 0))
        assertEquals(emptyList<ActiveTrip>(), fixture.scheduler.scheduled)
        assertEquals(1, fixture.scheduler.cancelCount)
    }

    @Test
    fun `재조회한 출발 마감이 이미 지났으면 기존 알림을 제거한다`() {
        val fixture = Fixture()
        fixture.coordinator.enable(trip("same", 700_000))

        assertNull(fixture.coordinator.reconcile(trip("same", -1)))

        assertNull(fixture.store.loadValid(nowEpochMillis = 0))
        assertEquals(1, fixture.scheduler.cancelCount)
    }

    @Test
    fun `앱 복원 시 출발 마감이 지난 여행을 활성 알림으로 표시하지 않는다`() {
        val fixture = Fixture()
        fixture.store.save(trip("late", -1))

        assertNull(fixture.coordinator.currentEnabledStopId())

        assertNull(fixture.store.loadValid(nowEpochMillis = 0))
        assertEquals(1, fixture.scheduler.cancelCount)
    }

    @Test
    fun `차단된 알림은 프로세스 재생성 후에도 원래 여행으로 한 번만 복구한다`() {
        val fixture = Fixture()
        val original = trip("same", 700_000)
        fixture.coordinator.enable(original)
        fixture.now = 500_000 // The original five-minute trigger has already passed.
        fixture.store.markDeliveryPending(original, fixture.now)
        val restoredCoordinator = fixture.recreateCoordinator()

        assertEquals("same", restoredCoordinator.reschedulePendingDelivery(deliveryAvailable = true))
        assertNull(restoredCoordinator.reschedulePendingDelivery(deliveryAvailable = true))

        assertEquals(original, fixture.store.loadValid(fixture.now))
        assertEquals(listOf(original, original), fixture.scheduler.scheduled)
        assertEquals(500_000L, fixture.scheduler.scheduledAt.last())
        assertEquals(DepartureReminderDecision.NotifyNow, fixture.scheduler.decisions.last())
    }

    @Test
    fun `차단 기록 없는 알림은 앱을 다시 열어도 중복 게시하지 않는다`() {
        val fixture = Fixture()
        fixture.coordinator.enable(trip("same", 700_000))
        fixture.now = 500_000

        assertNull(fixture.recreateCoordinator().reschedulePendingDelivery(deliveryAvailable = true))

        assertEquals(1, fixture.scheduler.scheduled.size)
    }

    @Test
    fun `차단이 계속되면 복구 대기를 소비하지 않고 허용 후 예약한다`() {
        val fixture = Fixture()
        val original = trip("same", 700_000)
        fixture.coordinator.enable(original)
        fixture.store.markDeliveryPending(original, fixture.now)

        assertNull(fixture.recreateCoordinator().reschedulePendingDelivery(deliveryAvailable = false))
        assertEquals(1, fixture.scheduler.scheduled.size)
        assertEquals("same", fixture.recreateCoordinator().reschedulePendingDelivery(deliveryAvailable = true))
        assertEquals(DepartureReminderDecision.Schedule(400_000), fixture.scheduler.decisions.last())
    }

    @Test
    fun `차단 해제 시 출발 마감이 지났으면 알림을 다시 게시하지 않는다`() {
        val fixture = Fixture()
        val original = trip("same", 700_000)
        fixture.coordinator.enable(original)
        fixture.store.markDeliveryPending(original, fixture.now)
        fixture.now = 700_000

        assertNull(fixture.recreateCoordinator().reschedulePendingDelivery(deliveryAvailable = true))

        assertNull(fixture.store.loadValid(fixture.now))
        assertEquals(1, fixture.scheduler.scheduled.size)
        assertEquals(1, fixture.scheduler.cancelCount)
    }

    @Test
    fun `차단 해제만으로 선택형 알림이 새로 켜지지 않는다`() {
        val fixture = Fixture()

        assertNull(fixture.coordinator.reschedulePendingDelivery(deliveryAvailable = true))

        assertEquals(emptyList<ActiveTrip>(), fixture.scheduler.scheduled)
    }

    @Test
    fun `새 여행은 이전 여행의 복구 대기를 이어받지 않는다`() {
        val fixture = Fixture()
        val old = trip("same", 700_000)
        fixture.coordinator.enable(old)
        fixture.store.markDeliveryPending(old, fixture.now)
        fixture.coordinator.enable(trip("same", 760_000))

        assertNull(fixture.recreateCoordinator().reschedulePendingDelivery(deliveryAvailable = true))
        assertEquals(2, fixture.scheduler.scheduled.size)
    }

    @Test
    fun `오래된 alarm 토큰은 현재 여행을 복구 대기로 바꾸지 않는다`() {
        val fixture = Fixture()
        val old = trip("same", 700_000)
        fixture.coordinator.enable(trip("same", 760_000))

        fixture.store.markDeliveryPending(old, fixture.now)

        assertNull(fixture.recreateCoordinator().reschedulePendingDelivery(deliveryAvailable = true))
        assertEquals(1, fixture.scheduler.scheduled.size)
    }

    @Test
    fun `이미 전송된 복구 대기는 앱 시작 때 다시 전송하지 않는다`() {
        val fixture = Fixture()
        val original = trip("same", 700_000)
        fixture.coordinator.enable(original)
        fixture.store.markDeliveryPending(original, fixture.now)
        fixture.store.clearDeliveryPending(original)
        fixture.now = 500_000

        assertNull(fixture.recreateCoordinator().reschedulePendingDelivery(deliveryAvailable = true))
        assertEquals(1, fixture.scheduler.scheduled.size)
    }

    private class Fixture {
        private val preferences = FakePreferences()
        val store = ActiveTripStore(preferences)
        val scheduler = FakeScheduler()
        var now = 0L
        val coordinator = recreateCoordinator()

        fun recreateCoordinator() = DepartureReminderCoordinator(
            store = ActiveTripStore(preferences),
            scheduler = scheduler,
            nowEpochMillis = { now },
        )
    }

    private class FakePreferences : ActiveTripPreferences {
        var value: String? = null
        var pendingToken: String? = null
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
        override fun readPendingDeliveryToken(): String? = pendingToken
        override fun writePendingDeliveryToken(value: String?) { pendingToken = value }
        override fun clear() { value = null; pendingToken = null }
    }

    private class FakeScheduler : DepartureReminderScheduler {
        val scheduled = mutableListOf<ActiveTrip>()
        val scheduledAt = mutableListOf<Long>()
        val decisions = mutableListOf<DepartureReminderDecision>()
        var cancelCount = 0
        override fun schedule(trip: ActiveTrip, nowEpochMillis: Long) {
            scheduled += trip
            scheduledAt += nowEpochMillis
            decisions += departureReminderDecision(trip.latestDepartureEpochMillis, nowEpochMillis)
        }
        override fun cancel() { cancelCount += 1 }
    }

    private fun trip(stopId: String, latestDeparture: Long) = ActiveTrip(
        startName = "출발",
        start = Coordinates(37.1, 128.1),
        destinationName = "도착",
        destination = Coordinates(37.3, 128.3),
        stopId = stopId,
        stopName = stopId,
        stop = Coordinates(37.2, 128.2),
        arrivalDeadlineEpochMillis = 1_000_000,
        latestDepartureEpochMillis = latestDeparture,
        navigationUrl = "https://map.kakao.com/$stopId",
        expiresAtEpochMillis = activeTripExpiryEpochMillis(1_000_000),
    )
}

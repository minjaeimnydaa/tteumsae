package com.tteumsae.app.reminder

import com.tteumsae.app.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureReminderNavigationTest {
    @Test
    fun `출발 알림은 경유지에서 최종 목적지로 가는 두 점 경로만 연다`() {
        assertEquals(
            "https://map.kakao.com/link/by/car/Stop,37.2,128.2/Destination,37.3,128.3",
            buildDepartureReminderNavigationUrl(
                stopName = "Stop",
                stop = Coordinates(37.2, 128.2),
                destinationName = "Destination",
                destination = Coordinates(37.3, 128.3),
            ),
        )
    }

    @Test
    fun `알림 경로의 장소명은 경로 구분자로 잘못 해석되지 않도록 인코딩한다`() {
        assertEquals(
            "https://map.kakao.com/link/by/car/Stop%20%2F%20Cafe,37.2,128.2/Final%20place,37.3,128.3",
            buildDepartureReminderNavigationUrl(
                stopName = "Stop / Cafe",
                stop = Coordinates(37.2, 128.2),
                destinationName = "Final place",
                destination = Coordinates(37.3, 128.3),
            ),
        )
    }
}

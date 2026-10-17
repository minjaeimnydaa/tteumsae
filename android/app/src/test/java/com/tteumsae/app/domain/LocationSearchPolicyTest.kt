package com.tteumsae.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationSearchPolicyTest {
    @Test
    fun `주소로 강원 목적지 지원 여부를 구분한다`() {
        assertEquals(
            DestinationSupport.SUPPORTED,
            destinationSupport("강원특별자치도 강릉시 용지로 176"),
        )
        assertEquals(
            DestinationSupport.UNSUPPORTED,
            destinationSupport("서울특별시 용산구 한강대로 405"),
        )
        assertEquals(DestinationSupport.UNKNOWN, destinationSupport(""))
    }

    @Test
    fun `목적지 결과는 강원 미확인 비지원 순서로 정렬한다`() {
        val results = listOf(
            result("서울역", "서울특별시 용산구"),
            result("주소 미확인", ""),
            result("강릉역", "강원특별자치도 강릉시"),
        )

        assertEquals(
            listOf("강릉역", "주소 미확인", "서울역"),
            prioritizeGangwonDestinations(results).map(LocationSearchResult::name),
        )
    }

    private fun result(name: String, address: String) = LocationSearchResult(
        id = name,
        name = name,
        address = address,
        coordinates = Coordinates(37.0, 127.0),
    )
}

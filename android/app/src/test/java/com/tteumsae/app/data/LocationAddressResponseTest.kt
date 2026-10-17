package com.tteumsae.app.data

import com.tteumsae.app.domain.DestinationSupport
import com.tteumsae.app.domain.LocationSearchResultType
import com.tteumsae.app.domain.destinationSupport
import com.tteumsae.app.domain.prioritizeGangwonDestinations
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationAddressResponseTest {
    @Test
    fun `주소 후보는 주소 종류와 지번 보조 주소를 읽는다`() {
        val result = response("강원특별자치도 강릉시 용지로 176")
            .put("type", "ADDRESS")
            .put("secondaryAddress", "강원특별자치도 강릉시 교동 118")
            .toLocationSearchResult()

        assertEquals(LocationSearchResultType.ADDRESS, result.type)
        assertEquals("강원특별자치도 강릉시 교동 118", result.secondaryAddress)
        assertEquals(37.7645, result.coordinates.latitude, 0.00001)
        assertEquals(DestinationSupport.SUPPORTED, destinationSupport(result.address))
    }

    @Test
    fun `기존 서버의 장소 응답에는 신규 필드가 없어도 된다`() {
        val result = response("서울특별시 용산구 한강대로 405").toLocationSearchResult()

        assertEquals(LocationSearchResultType.PLACE, result.type)
        assertEquals("", result.secondaryAddress)
        assertEquals(DestinationSupport.UNSUPPORTED, destinationSupport(result.address))
    }

    @Test
    fun `미래에 추가된 응답 종류도 기존 장소로 안전하게 표시한다`() {
        val result = response("강원특별자치도 강릉시 용지로 176")
            .put("type", "FUTURE_TYPE")
            .put("secondaryAddress", JSONObject.NULL)
            .toLocationSearchResult()

        assertEquals(LocationSearchResultType.PLACE, result.type)
        assertEquals("", result.secondaryAddress)
    }

    @Test
    fun `강원 주소 후보가 다른 지역 주소보다 먼저 오며 원문 검색을 유지한다`() {
        val outside = response("서울특별시 용산구 한강대로 405")
            .put("type", "ADDRESS").toLocationSearchResult()
        val gangwon = response("강원특별자치도 강릉시 용지로 176")
            .put("type", "ADDRESS").toLocationSearchResult()

        assertEquals(listOf(gangwon, outside), prioritizeGangwonDestinations(listOf(outside, gangwon)))
        assertEquals("강릉시 용지로 176", locationSearchQuery("  강릉시 용지로 176  "))
    }

    private fun response(address: String) = JSONObject()
        .put("id", "address:sample")
        .put("name", address)
        .put("address", address)
        .put("latitude", 37.7645)
        .put("longitude", 128.8996)
}

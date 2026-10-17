package com.tteumsae.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCopyTest {
    @Test
    fun zero_guest_places_are_described_as_device_local_storage() {
        assertEquals("이 기기에 0개 저장됨", guestSavedStorageDescription(0))
    }

    @Test
    fun positive_guest_place_count_is_included_in_device_local_storage_description() {
        assertEquals("이 기기에 3개 저장됨", guestSavedStorageDescription(3))
    }
}

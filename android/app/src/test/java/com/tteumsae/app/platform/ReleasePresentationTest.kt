package com.tteumsae.app.platform

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePresentationTest {
    @Test
    fun `지도 미설치 fallback은 다른 마켓으로 유도하지 않는다`() {
        val navigation = source("src/main/java/com/tteumsae/app/platform/ExternalNavigation.kt")
        assertFalse(navigation.contains("market://"))
        assertFalse(navigation.contains("play.google.com"))
        assertTrue(navigation.contains("https://map.kakao.com/"))
        assertFalse(source("src/main/java/com/tteumsae/app/ui/settings/SettingsScreen.kt").contains("설치 필요"))
    }

    @Test
    fun `공식 원본 로고를 안전 여백이 있는 adaptive 앱 아이콘에 연결한다`() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher\""))
        val adaptive = source("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue(adaptive.contains("<adaptive-icon"))
        val foreground = source("src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue(foreground.contains("@drawable/brand_logo"))
        assertTrue(foreground.contains("android:inset=\"12dp\""))
        assertTrue(file("src/main/res/drawable-nodpi/brand_logo.png").length() > 0)
    }

    private fun source(path: String): String = file(path).readText()
    private fun file(path: String): File = listOf(File(path), File("app/$path"))
        .firstOrNull(File::isFile) ?: error("소스 파일을 찾지 못했습니다: $path")
}

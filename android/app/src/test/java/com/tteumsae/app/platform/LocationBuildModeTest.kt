package com.tteumsae.app.platform

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocationBuildModeTest {
    @Test
    fun `위치 모드는 빌드 시에만 선택하고 기본값은 수동이다`() {
        val build = source("build.gradle.kts")
        assertTrue(build.contains("gradleProperty(\"locationMode\").orElse(\"manual\")"))
        assertTrue(build.contains("locationMode !in setOf(\"manual\", \"automatic\")"))
        assertTrue(build.contains("\"AUTOMATIC_LOCATION_ENABLED\""))
        assertTrue(build.contains("java.srcDir(\"src/\$locationSourceSet/java\")"))
        assertTrue(build.contains("manifest.srcFile(\"src/\$locationSourceSet/AndroidManifest.xml\")"))
    }

    @Test
    fun `수동 공급자는 기기 위치와 위치 권한에 접근하지 않는다`() {
        val manual = source("src/locationManual/java/com/tteumsae/app/ui/CurrentLocation.kt")
        assertTrue(manual.contains("hasLocationPermission"))
        assertTrue(manual.contains("requestCurrentLocation"))
        assertTrue(manual.contains("onUnavailable()"))
        listOf("LocationManager", "getSystemService", "checkSelfPermission", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION").forEach {
            assertFalse("수동 공급자에 기기 위치 접근이 포함되어 있습니다: $it", manual.contains(it))
        }
    }

    @Test
    fun `기존 GPS 공급자는 자동 전용 소스에 보존한다`() {
        val automatic = source("src/locationAutomatic/java/com/tteumsae/app/ui/CurrentLocation.kt")
        assertTrue(automatic.contains("LocationManager"))
        assertTrue(automatic.contains("getLastKnownLocation"))
        assertTrue(automatic.contains("requestSingleUpdate"))
        assertTrue(automatic.contains("removeUpdates"))
        val common = source("src/main/java/com/tteumsae/app/ui/CurrentLocation.kt")
        assertFalse(common.contains("LocationManager"))
        assertFalse(common.contains("fun requestCurrentLocation"))
    }

    @Test
    fun `수동 매니페스트는 의존성 위치 권한도 제거하고 자동에서만 요청한다`() {
        val main = source("src/main/AndroidManifest.xml")
        assertFalse(main.contains("ACCESS_FINE_LOCATION"))
        assertFalse(main.contains("ACCESS_COARSE_LOCATION"))
        val manual = manifestPermissions("src/locationManual/AndroidManifest.xml")
        listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION").forEach { permission ->
            assertTrue(manual.any {
                it.getAttributeNS(ANDROID_NAMESPACE, "name") == "android.permission.$permission" &&
                    it.getAttributeNS(TOOLS_NAMESPACE, "node") == "remove"
            })
        }
        val automatic = manifestPermissions("src/locationAutomatic/AndroidManifest.xml")
        listOf("ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION").forEach { permission ->
            assertTrue(automatic.any {
                it.getAttributeNS(ANDROID_NAMESPACE, "name") == "android.permission.$permission" &&
                    it.getAttributeNS(TOOLS_NAMESPACE, "node") != "remove"
            })
        }
        assertFalse(automatic.any {
            it.getAttributeNS(ANDROID_NAMESPACE, "name") == "android.permission.ACCESS_BACKGROUND_LOCATION" &&
                it.getAttributeNS(TOOLS_NAMESPACE, "node") != "remove"
        })
    }

    private fun manifestPermissions(relativePath: String): List<Element> {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(source(relativePath).byteInputStream())
        val elements = document.getElementsByTagName("uses-permission")
        return (0 until elements.length).map { elements.item(it) as Element }
    }

    private fun source(relativePath: String): String =
        listOf(File(relativePath), File("app/$relativePath"))
            .firstOrNull(File::isFile)?.readText() ?: error("소스 파일이 없습니다: $relativePath")

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val TOOLS_NAMESPACE = "http://schemas.android.com/tools"
    }
}

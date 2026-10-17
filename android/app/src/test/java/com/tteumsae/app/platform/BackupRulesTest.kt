package com.tteumsae.app.platform

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesTest {
    @Test
    fun `기기 로컬 저장 장소 DB는 구형 백업과 클라우드 및 기기 이전에서 제외한다`() {
        val factory = DocumentBuilderFactory.newInstance()
        val legacyRules = factory.newDocumentBuilder().parse(
            source("src/main/res/xml/backup_rules.xml").byteInputStream(),
        )
        val extractionRules = factory.newDocumentBuilder().parse(
            source("src/main/res/xml/data_extraction_rules.xml").byteInputStream(),
        )
        val scopes = listOf(
            legacyRules.documentElement,
            extractionRules.getElementsByTagName("cloud-backup").item(0) as Element,
            extractionRules.getElementsByTagName("device-transfer").item(0) as Element,
        )
        scopes.forEach { scope ->
            val exclusions = scope.getElementsByTagName("exclude")
            assertTrue("${scope.tagName}에서 Room DB 전체가 제외되어야 합니다", (0 until exclusions.length).any { index ->
                val element = exclusions.item(index) as Element
                element.getAttribute("domain") == "database" && element.getAttribute("path") == "."
            })
        }
    }

    @Test
    fun `활성 여행과 인증 세션이 포함된 shared preferences는 백업하지 않는다`() {
        val manifest = source("src/main/AndroidManifest.xml")
        val legacyRules = source("src/main/res/xml/backup_rules.xml")
        val extractionRules = source("src/main/res/xml/data_extraction_rules.xml")

        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(legacyRules.contains("<exclude domain=\"sharedpref\" path=\".\""))
        assertEquals(
            2,
            extractionRules.split("<exclude domain=\"sharedpref\" path=\".\"").size - 1,
        )
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("소스 파일을 찾지 못했습니다: $relativePath")
    }
}

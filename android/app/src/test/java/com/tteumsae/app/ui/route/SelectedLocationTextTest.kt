package com.tteumsae.app.ui.route

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural regression guards; actual wrapping at small width/large font requires device QA. */
class SelectedLocationTextTest {
    @Test
    fun `선택한 전체 주소는 큰 글자에서도 필요한 만큼 줄바꿈한다`() {
        val selectedText = selectedTextSource()
        assertTrue(selectedText.contains("maxLines = Int.MAX_VALUE"))
        assertTrue(selectedText.contains("softWrap = true"))
        assertFalse(selectedText.contains("TextOverflow.Ellipsis"))
    }

    @Test
    fun `선택한 주소의 건물번호나 장소명을 줄여서 전달하지 않는다`() {
        val selectedText = selectedTextSource()
        assertTrue(Regex("Text\\s*\\(\\s*value,").containsMatchIn(selectedText))
        assertFalse(selectedText.contains("value.take("))
        assertFalse(selectedText.contains("value.substring("))
        assertFalse(selectedText.contains("value.replace("))
    }

    private fun selectedTextSource(): String {
        val relative = "src/main/java/com/tteumsae/app/ui/route/LocationScreen.kt"
        val source = listOf(File(relative), File("app/$relative"))
            .firstOrNull(File::isFile)?.readText() ?: error("LocationScreen 소스가 없습니다")
        val field = source.substringAfter("private fun RouteLocationSearchField(")
        val selected = field.substringAfter("if (selected != null) {").substringBefore("} else {")
        assertTrue("선택된 위치 Text 블록을 찾을 수 있어야 합니다", selected.contains("Text("))
        return selected
    }
}

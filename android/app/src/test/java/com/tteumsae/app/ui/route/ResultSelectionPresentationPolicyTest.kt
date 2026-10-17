package com.tteumsae.app.ui.route

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultSelectionPresentationPolicyTest {
    @Test
    fun `selection keeps an explicit text label in both states`() {
        assertEquals("선택", resultSelectionActionLabel(false))
        assertEquals("선택됨", resultSelectionActionLabel(true))
        assertEquals("선택 안 됨", resultSelectionStateDescription(false))
        assertEquals("선택됨", resultSelectionStateDescription(true))
    }

    @Test
    fun `normal phone actions stay compact in one row`() {
        assertFalse(resultCandidateActionsStack(292f, 1f))
        assertFalse(resultCandidateActionsStack(356f, 1.3f))
    }

    @Test
    fun `two times text reserves space for the complete detail label`() {
        assertFalse(resultCandidateActionsStack(340f, 2f))
        assertTrue(resultCandidateActionsStack(324f, 2f))
        assertTrue(resultCandidateActionsStack(292f, 2f))
        assertTrue(resultCandidateActionsStack(260f, 2f))
    }

    @Test
    fun `very narrow width or larger text moves actions into full width rows`() {
        assertTrue(resultCandidateActionsStack(220f, 1f))
        assertTrue(resultCandidateActionsStack(292f, 2.5f))
    }

    @Test
    fun `selection indicator is decorative while card selection and details remain independent`() {
        val source = listOf(
            File("src/main/java/com/tteumsae/app/ui/route/RouteResultComponents.kt"),
            File("app/src/main/java/com/tteumsae/app/ui/route/RouteResultComponents.kt"),
        ).first(File::isFile).readText()
        val indicator = source.substringAfter("private fun ResultSelectionControl(")
            .substringBefore("\n@Composable")
        assertTrue(source.contains(".toggleable("))
        assertTrue(source.contains("onClick = onDetail"))
        assertTrue(source.contains("stateDescription = resultSelectionStateDescription(selected)"))
        assertTrue(indicator.contains(".heightIn(min = TteumSizing.touchTarget)"))
        assertTrue(indicator.contains(".clearAndSetSemantics { }"))
        assertTrue(indicator.contains("resultSelectionActionLabel(selected)"))
        assertFalse(indicator.contains("onClick"))
        assertFalse(indicator.contains(".toggleable("))
    }
}

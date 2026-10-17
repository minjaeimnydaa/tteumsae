package com.tteumsae.app.ui.route

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wiring guards, not a substitute for actual SDK/gesture tests. */
class ResultMapCompositionContractTest {
    @Test fun `map list switching does not conditionally dispose the map`() {
        val results = source("ui/route/ResultsScreen.kt")
        assertTrue(results.contains("ResultMapSheetLayout("))
        assertFalse(results.contains("showingMap"))
    }

    @Test fun `cluster browsing never clears a trip selection`() {
        val callback = source("ui/route/ResultsScreen.kt").substringAfter("onClusterClick = { memberIds ->").substringBefore("},")
        assertTrue(callback.contains("clusterScopeIds"))
        assertFalse(callback.contains("onClearSelection"))
    }

    @Test fun `only an explicit map reveal event scrolls the list once`() {
        val results = source("ui/route/ResultsScreen.kt")
        assertTrue(results.contains("LaunchedEffect(revealRequestId)"))
        assertEquals(1, Regex("animateScrollToItem").findAll(results).count())
        assertFalse(results.contains("delay(360)"))
    }

    @Test fun `result logo uses SDK safe area without a second bottom offset`() {
        assertFalse(source("ui/route/RouteMap.kt").contains("logoBottomMargin"))
    }

    @Test fun `fresh network search clears restored browsing state as well as normal new search`() {
        val app = source("ui/TteumsaeApp.kt")
        assertTrue(Regex("resultUiStateHolder.removeState").findAll(app).count() >= 2)
    }

    private fun source(relative: String): String = listOf(
        File("src/main/java/com/tteumsae/app/$relative"),
        File("app/src/main/java/com/tteumsae/app/$relative"),
    ).first(File::isFile).readText()
}

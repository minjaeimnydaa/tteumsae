package com.tteumsae.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMapAttributionTest {
    @Test
    fun homeRestoresItsNormalBottomMarginWithoutDoubleCountingNavigation() {
        assertEquals(12f, homeMapLogoBottomMarginDp(false, 24f), 0f)
    }

    @Test
    fun collapsedSearchClearsThePeekSheetAndGestureNavigationArea() {
        assertEquals(146f, homeMapLogoBottomMarginDp(true, 24f), 0f)
    }

    @Test
    fun collapsedSearchAlsoClearsThreeButtonNavigation() {
        assertEquals(170f, homeMapLogoBottomMarginDp(true, 48f), 0f)
    }

    @Test
    fun noNavigationInsetStillKeepsTheLogoAboveTheSheet() {
        assertEquals(122f, homeMapLogoBottomMarginDp(true, 0f), 0f)
        assertEquals(122f, homeMapLogoBottomMarginDp(true, -1f), 0f)
    }

    @Test
    fun attributionUsesTheSdkLogoWithoutChangingResultMapViewport() {
        val source = listOf(
            File("src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt"),
            File("app/src/main/java/com/tteumsae/app/ui/TteumsaeApp.kt"),
        ).first(File::isFile).readText()
        assertTrue(source.contains("logoBottomMargin: Dp? = null"))
        assertTrue(source.contains("logo?.setPosition("))
        assertTrue(source.contains("WindowInsets.navigationBars.getBottom(density)"))
        assertTrue(source.contains("logoBottomMargin = homeMapLogoBottomMarginDp("))
    }
}

package com.tteumsae.app.ui.route

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultSheetHeaderMeasurementTest {
    @Test
    fun `collapsed header measures its desired height before the sheet clips it`() {
        val source = listOf(
            File("src/main/java/com/tteumsae/app/ui/route/ResultMapSheetLayout.kt"),
            File("app/src/main/java/com/tteumsae/app/ui/route/ResultMapSheetLayout.kt"),
        ).first(File::isFile).readText()
        val headerModifier = source.substringAfter("header(").substringBefore("sheetContent()")
        val unconstrained = headerModifier.indexOf(".wrapContentHeight(Alignment.Top, unbounded = true)")
        val measured = headerModifier.indexOf(".onSizeChanged")
        val draggable = headerModifier.indexOf(".anchoredDraggable")
        assertTrue("The natural height must be unbounded before measurement", unconstrained >= 0)
        assertTrue("Measurement must see the unconstrained child height", measured > unconstrained)
        assertTrue("The full measured header remains the drag surface", draggable > measured)
    }

    @Test
    fun `larger restored header increases collapsed sheet beyond its initial estimate`() {
        val original = resultSheetGeometry(680f, 96f)
        for (desiredHeader in listOf(124f, 172f)) {
            val enlarged = resultSheetGeometry(680f, desiredHeader)
            assertEquals(desiredHeader, enlarged.availableHeightDp - enlarged.collapsedOffsetDp, 0.01f)
            assertTrue(enlarged.collapsedOffsetDp < original.collapsedOffsetDp)
        }
    }
}

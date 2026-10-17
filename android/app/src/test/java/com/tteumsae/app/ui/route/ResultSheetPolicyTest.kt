package com.tteumsae.app.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultSheetPolicyTest {
    @Test fun `cluster member body clears overview button including its top margin`() {
        val insets = resultMapViewportInsets(680f, 353.6f)
        val overviewTopAndHeight = 12f + 48f
        val tallestMarkerHeight = 56f
        val clusterFitPadding = 32f
        val visualGap = 12f

        assertEquals(96f, insets.topDp, 0.01f)
        assertTrue(
            insets.topDp + clusterFitPadding - tallestMarkerHeight >= overviewTopAndHeight + visualGap,
        )
    }

    @Test fun `larger top clearance still scales down on tiny measured viewports`() {
        for (height in listOf(1f, 80f, 200f, 260f)) {
            val insets = resultMapViewportInsets(height, height)
            assertTrue(insets.topDp <= height * 0.2f + 0.01f)
            assertTrue(insets.topDp >= 0f && insets.bottomDp >= 0f)
            assertTrue(height - insets.topDp - insets.bottomDp >= minOf(96f, height * 0.5f) - 0.01f)
        }
    }

    @Test fun `zero and temporary negative measurements have safe finite anchors`() {
        for (height in listOf(-1f, 0f, 1f)) {
            val stops = resultSheetGeometry(height, 120f)
            assertTrue(stops.balancedOffsetDp.isFinite())
            assertTrue(stops.collapsedOffsetDp.isFinite())
        }
    }

    @Test fun `top and bottom padding together retain a usable map viewport`() {
        for (height in listOf(1f, 200f, 260f, 680f)) {
            val insets = resultMapViewportInsets(height, height)
            assertTrue(insets.topDp + insets.bottomDp <= height - minOf(96f, height * 0.5f) + 0.01f)
        }
    }

    @Test fun `all three stops stay inside the scaffold content area`() {
        for (height in listOf(200f, 360f, 640f, 900f)) {
            for (header in listOf(88f, 128f, 176f)) {
                val stops = resultSheetGeometry(height, header)
                assertEquals(0f, stops.expandedOffsetDp, 0.01f)
                assertTrue(stops.balancedOffsetDp > 0f)
                assertTrue(stops.balancedOffsetDp < stops.collapsedOffsetDp)
                assertTrue(stops.collapsedOffsetDp < height)
                assertTrue(height - stops.collapsedOffsetDp >= header.coerceAtMost(height * 0.8f))
            }
        }
    }

    @Test fun `initial balanced stop leaves both map and useful list visible`() {
        val stops = resultSheetGeometry(680f, 96f)
        assertTrue(stops.balancedOffsetDp in 280f..360f)
        assertTrue(680f - stops.balancedOffsetDp >= 320f)
        assertEquals(584f, stops.collapsedOffsetDp, 0.01f)
    }

    @Test fun `full list never gives the underlying map an invalid full height padding`() {
        val stops = resultSheetGeometry(680f, 96f)
        val padding = resultMapObscuredHeightDp(stops, ResultSheetPosition.LIST)
        assertEquals(resultMapObscuredHeightDp(stops, ResultSheetPosition.BALANCED), padding, 0.01f)
        assertTrue(padding < 680f)
    }

    @Test fun `map stop accounts for the visible header not a fixed legacy sheet height`() {
        assertEquals(136f, resultMapObscuredHeightDp(resultSheetGeometry(680f, 136f), ResultSheetPosition.MAP), 0.01f)
    }

    @Test fun `list reveal index has only the actual optional warning before rows`() {
        assertEquals(0, resultCandidateRevealIndex(listOf("a", "b"), "a", false))
        assertEquals(2, resultCandidateRevealIndex(listOf("a", "b"), "b", true))
        assertEquals(null, resultCandidateRevealIndex(listOf("a", "b"), "missing", true))
    }
}

package com.nendo.argosy.ui.components.recap

import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapLayoutTest {

    private val shortEdge = ComponentDefaults.UnlockRecap.shortHeightDp.toFloat()
    private val tallEdge = ComponentDefaults.UnlockRecap.tallHeightDp.toFloat()

    @Test
    fun `heights below the short edge are short`() {
        assertEquals(RecapHeightClass.SHORT, RecapHeightClass.of(shortEdge - 1f))
        assertEquals(RecapHeightClass.SHORT, RecapHeightClass.of(360f))
    }

    @Test
    fun `the short edge itself is regular`() {
        assertEquals(RecapHeightClass.REGULAR, RecapHeightClass.of(shortEdge))
    }

    @Test
    fun `heights between the edges are regular`() {
        assertEquals(RecapHeightClass.REGULAR, RecapHeightClass.of(480f))
        assertEquals(RecapHeightClass.REGULAR, RecapHeightClass.of(tallEdge - 1f))
    }

    @Test
    fun `the tall edge and above are tall`() {
        assertEquals(RecapHeightClass.TALL, RecapHeightClass.of(tallEdge))
        assertEquals(RecapHeightClass.TALL, RecapHeightClass.of(1080f))
    }

    @Test
    fun `a row that fits exactly stays static`() {
        assertTrue(RecapLayout.unlockRowFits(count = 3, tileDp = 80f, gapDp = 16f, widthDp = 272f))
    }

    @Test
    fun `a row one dp too wide overflows`() {
        assertFalse(RecapLayout.unlockRowFits(count = 3, tileDp = 80f, gapDp = 16f, widthDp = 271f))
    }

    @Test
    fun `a single tile and an empty row always fit`() {
        assertTrue(RecapLayout.unlockRowFits(count = 1, tileDp = 80f, gapDp = 16f, widthDp = 80f))
        assertTrue(RecapLayout.unlockRowFits(count = 0, tileDp = 80f, gapDp = 16f, widthDp = 0f))
    }

    @Test
    fun `smaller tiles let more of a row fit`() {
        assertFalse(RecapLayout.unlockRowFits(count = 6, tileDp = 80f, gapDp = 16f, widthDp = 520f))
        assertTrue(RecapLayout.unlockRowFits(count = 6, tileDp = 64f, gapDp = 16f, widthDp = 520f))
    }

    @Test
    fun `a body scroll moves by whole steps in its direction`() {
        assertEquals(300, RecapLayout.bodyScrollTarget(from = 0, direction = 1, steps = 2, stepPx = 150f, maxValue = 1000))
        assertEquals(350, RecapLayout.bodyScrollTarget(from = 500, direction = -1, steps = 1, stepPx = 150f, maxValue = 1000))
    }

    @Test
    fun `a body scroll stops at either end`() {
        assertEquals(1000, RecapLayout.bodyScrollTarget(from = 950, direction = 1, steps = 1, stepPx = 150f, maxValue = 1000))
        assertEquals(0, RecapLayout.bodyScrollTarget(from = 50, direction = -1, steps = 1, stepPx = 150f, maxValue = 1000))
    }

    @Test
    fun `a body that cannot scroll stays at the top`() {
        assertEquals(0, RecapLayout.bodyScrollTarget(from = 0, direction = 1, steps = 3, stepPx = 150f, maxValue = 0))
    }
}

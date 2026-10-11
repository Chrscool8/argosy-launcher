package com.nendo.argosy.ui.screens.settings.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ReseatRowsTest {

    private val rows = listOf(
        SeatRow("a", "Pad A", 0),
        SeatRow("b", "Pad B", 1),
        SeatRow("c", "Pad C", null)
    )

    @Test
    fun `moving onto an occupied seat swaps the two pads`() {
        val result = reseatRows(rows, index = 1, port = 0)

        assertEquals(listOf(1, 0, null), result.map { it.port })
    }

    @Test
    fun `unseated pad moving onto an occupied seat unseats the holder`() {
        val result = reseatRows(rows, index = 2, port = 1)

        assertEquals(listOf(0, null, 1), result.map { it.port })
    }

    @Test
    fun `moving onto a free seat leaves the others`() {
        val result = reseatRows(rows, index = 0, port = 3)

        assertEquals(listOf(3, 1, null), result.map { it.port })
    }

    @Test
    fun `choosing none unseats only that pad`() {
        val result = reseatRows(rows, index = 0, port = null)

        assertEquals(listOf(null, 1, null), result.map { it.port })
    }

    @Test
    fun `choosing the current seat changes nothing`() {
        assertEquals(rows, reseatRows(rows, index = 1, port = 1))
    }
}

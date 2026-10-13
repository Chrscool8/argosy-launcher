package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockReorderSessionTest {
    @Test
    fun `visible targets skip missing pins without shifting crossed icons`() {
        val session = DockReorderSession.lift(1, true, listOf("a", "missing", "b", "c"),
            listOf("a", "b", "c"), "a")!!
        val moved = session.move(1).move(1)

        assertEquals(listOf("c", "missing", "b", "a"), moved.order)
        assertEquals(2, moved.targetIndex)
        assertEquals("a", moved.heldPackage)
        assertEquals(listOf("b", "missing", "a", "c"), moved.move(-1).order)
        assertEquals(session.order, moved.move(-1).move(-1).order)
        assertEquals(moved, moved.move(1))
    }

    @Test
    fun `cannot lift a control or fewer than two available pins`() {
        assertNull(DockReorderSession.lift(1, false, listOf("a", "missing"), listOf("a"), "a"))
        assertNull(DockReorderSession.lift(1, false, listOf("a", "b"), listOf("a", "b"), "tools"))
    }

    @Test
    fun `stationary centers latch and deliberate reversal selects the prior slot`() {
        val centers = listOf(0 to 24f, 1 to 80f, 2 to 136f, 3 to 192f)
        assertEquals(2, dockDragTarget(0, 24f, 160f, centers))
        assertEquals(2, dockDragTarget(2, 160f, 134f, centers))
        assertEquals(1, dockDragTarget(2, 134f, 70f, centers))
        assertEquals(0, dockDragTarget(1, 70f, 20f, centers))
        assertEquals(2, dockDragTarget(2, 136f, 136f, centers))
    }
}

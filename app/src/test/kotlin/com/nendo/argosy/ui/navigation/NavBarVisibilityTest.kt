package com.nendo.argosy.ui.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavBarVisibilityTest {

    private val pastTimeout = NavBarVisibility.AUTO_HIDE_MS + 1

    private fun TestScope.visibleAfterTimeout(quickNavigation: Boolean, autoHide: Boolean): Boolean {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = autoHide)
        runCurrent()
        advanceTimeBy(pastTimeout)
        runCurrent()
        return NavBarVisibility.isVisible(quickNavigation, autoHide, visibility.raised.value)
    }

    @Test
    fun `auto-hide on shows the bar until the timeout`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = true)
        runCurrent()
        assertTrue(NavBarVisibility.isVisible(quickNavigation = true, autoHide = true, raised = visibility.raised.value))

        advanceTimeBy(pastTimeout)
        runCurrent()
        assertFalse(NavBarVisibility.isVisible(quickNavigation = true, autoHide = true, raised = visibility.raised.value))
    }

    @Test
    fun `auto-hide off keeps the bar visible past the timeout`() = runTest {
        assertTrue(visibleAfterTimeout(quickNavigation = true, autoHide = false))
    }

    @Test
    fun `auto-hide off keeps the bar visible through a hide`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = false)
        visibility.hide()
        runCurrent()
        assertTrue(NavBarVisibility.isVisible(quickNavigation = true, autoHide = false, raised = visibility.raised.value))
    }

    @Test
    fun `auto-hide on lowers the bar at once on hide`() = runTest {
        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = true)
        visibility.hide()
        runCurrent()
        assertFalse(NavBarVisibility.isVisible(quickNavigation = true, autoHide = true, raised = visibility.raised.value))
    }

    @Test
    fun `quick navigation off hides the bar regardless of auto-hide`() = runTest {
        assertFalse(visibleAfterTimeout(quickNavigation = false, autoHide = false))

        val visibility = NavBarVisibility(backgroundScope)
        visibility.show(autoHide = true)
        runCurrent()
        assertFalse(NavBarVisibility.isVisible(quickNavigation = false, autoHide = true, raised = visibility.raised.value))
        assertFalse(NavBarVisibility.isAlwaysVisible(quickNavigation = false, autoHide = false))
    }

    @Test
    fun `only auto-hide off with quick navigation on is always visible`() {
        assertTrue(NavBarVisibility.isAlwaysVisible(quickNavigation = true, autoHide = false))
        assertFalse(NavBarVisibility.isAlwaysVisible(quickNavigation = true, autoHide = true))
    }
}

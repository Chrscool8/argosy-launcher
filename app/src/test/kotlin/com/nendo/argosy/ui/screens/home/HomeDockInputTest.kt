package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.TileEditMode
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDockInputTest {
    @Test
    fun `hydrating pins does not mistake the initial index for focused tools`() {
        val initial = HomeUiState().withDockApps(listOf("a", "b"))
        assertEquals(0, initial.appBarIndex)
        val tools = HomeUiState(homeApps = listOf("a", "b"), appBarFocused = true, appBarIndex = 2)
        assertEquals(3, tools.withDockApps(listOf("a", "b", "c")).appBarIndex)
    }
    private fun handler(state: HomeUiState): Pair<HomeInputHandler, HomeInputActions> {
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(state)
        every { actions.focusAppBar() } returns true
        return HomeInputHandler(actions, true, {}, {}, {}) to actions
    }

    @Test
    fun `down enters dock only after browse movement reaches the canvas boundary`() {
        val (handler, actions) = handler(HomeUiState(layoutKind = HomeLayoutKind.CUSTOM_GRID))
        every { actions.moveCustomGridFocus(GridDirection2D.DOWN) } returns true
        handler.onDown()
        verify(exactly = 0) { actions.focusAppBar() }
        every { actions.moveCustomGridFocus(GridDirection2D.DOWN) } returns false
        handler.onDown()
        verify(exactly = 1) { actions.focusAppBar() }
    }

    @Test
    fun `failed tile edit movement cannot enter dock`() {
        for (mode in listOf(TileEditMode.MOVE, TileEditMode.RESIZE)) {
            val (handler, actions) = handler(HomeUiState(layoutKind = HomeLayoutKind.CUSTOM_GRID,
                customGrid = CustomGridState(editMode = mode)))
            handler.onDown()
            verify(exactly = 0) { actions.focusAppBar() }
        }
    }

    @Test
    fun `hold confirm on focused dock starts reorder instead of opening menu`() {
        val (handler, actions) = handler(HomeUiState(appBarFocused = true, homeApps = listOf("a", "b")))
        every { actions.startAppBarReorder() } returns true

        assertTrue(handler.onLongConfirm().handled)
        verify(exactly = 1) { actions.startAppBarReorder() }
        verify(exactly = 0) { actions.openAppBarAppMenu() }
        verify(exactly = 0) { actions.activateAppBarSlot() }
    }
}

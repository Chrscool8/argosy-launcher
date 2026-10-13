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
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeDockInputTest {
    @Test
    fun `game actions belong to carousel and resume after leaving dock`() {
        val game = mockk<HomeGameUi>(relaxed = true)
        every { game.id } returns 42L
        val state = MutableStateFlow(HomeUiState(appBarFocused = true, recentGames = listOf(game)))
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns state
        every { actions.releaseAppBar() } answers { state.value = state.value.copy(appBarFocused = false) }
        every { actions.toggleGameMenu() } answers { state.value = state.value.copy(showGameMenu = true) }
        var detailsId: Long? = null
        var favoriteId: Long? = null
        every { actions.toggleFavorite(any()) } answers { favoriteId = firstArg() }
        val handler = HomeInputHandler(actions, true, { detailsId = it }, {}, {})

        assertTrue(handler.onContextMenu().handled)
        assertTrue(handler.onSecondaryAction().handled)
        assertTrue(handler.onSelect().handled)
        assertNull(detailsId)
        assertNull(favoriteId)
        assertFalse(state.value.showGameMenu)

        handler.onUp()
        handler.onContextMenu()
        handler.onSecondaryAction()
        handler.onSelect()
        assertEquals(42L, detailsId)
        assertEquals(42L, favoriteId)
        assertTrue(state.value.showGameMenu)
    }

    @Test
    fun `dock focus follows app identity through a preview and its cancellation`() {
        val original = HomeUiState(homeApps = listOf("a", "b", "c"), appBarFocused = true)
        val preview = original.withDockApps(listOf("c", "b", "a"))
        assertEquals(2, preview.appBarIndex)
        assertEquals(0, preview.withDockApps(original.homeApps).appBarIndex)
    }

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

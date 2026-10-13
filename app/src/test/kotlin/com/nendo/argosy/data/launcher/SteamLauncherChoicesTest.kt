package com.nendo.argosy.data.launcher

import com.nendo.argosy.domain.model.LaunchPromptOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLauncherChoicesTest {

    private val gameNative = GameNativeLauncher.packageName
    private val droidDeck = DroidDeckLauncher.packageName
    private val everythingInstalled: (String) -> Boolean = { true }

    @Test
    fun `both launchers reporting an unpinned game asks between them`() {
        val choices = SteamLaunchers.launchChoices(
            pinned = false,
            reporting = listOf(gameNative, droidDeck),
            isInstalled = everythingInstalled
        )

        assertEquals(listOf(gameNative, droidDeck), choices)
    }

    @Test
    fun `a pinned game never asks`() {
        val choices = SteamLaunchers.launchChoices(
            pinned = true,
            reporting = listOf(gameNative, droidDeck),
            isInstalled = everythingInstalled
        )

        assertTrue(choices.isEmpty())
    }

    @Test
    fun `one launcher reporting does not ask`() {
        listOf(gameNative, droidDeck).forEach { only ->
            val choices = SteamLaunchers.launchChoices(
                pinned = false,
                reporting = listOf(only),
                isInstalled = everythingInstalled
            )

            assertTrue(choices.isEmpty())
        }
    }

    @Test
    fun `a reporting launcher that is no longer installed is not offered`() {
        val choices = SteamLaunchers.launchChoices(
            pinned = false,
            reporting = listOf(gameNative, droidDeck),
            isInstalled = { it == droidDeck }
        )

        assertTrue(choices.isEmpty())
    }

    @Test
    fun `nothing reporting does not ask`() {
        val choices = SteamLaunchers.launchChoices(
            pinned = false,
            reporting = emptyList(),
            isInstalled = everythingInstalled
        )

        assertTrue(choices.isEmpty())
    }

    @Test
    fun `prompt options map back to the launcher they name`() {
        assertEquals(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE, SteamLaunchers.promptOptionFor(gameNative))
        assertEquals(LaunchPromptOption.LAUNCH_WITH_DROIDDECK, SteamLaunchers.promptOptionFor(droidDeck))
        assertEquals(GameNativeLauncher, SteamLaunchers.forPromptOption(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE))
        assertEquals(DroidDeckLauncher, SteamLaunchers.forPromptOption(LaunchPromptOption.LAUNCH_WITH_DROIDDECK))
    }
}

package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.os.Bundle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A game intent with the placement its display target resolved to.
 */
data class GameLaunchRequest(
    val intent: android.content.Intent,
    val options: Bundle? = null
)

@Singleton
class EmulatorLaunchTargetResolver @Inject constructor(
    @ApplicationContext context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val emulatorConfigRepository: EmulatorConfigRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    suspend fun launchOptionsFor(gameId: Long, overrideDisplayId: Int? = null): Bundle? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val target = placementTarget(
            stored = EmulatorDisplayTarget.fromString(
                emulatorConfigRepository.getEffectiveDisplayTarget(gameId)
            ),
            gamesDefault = preferencesRepository.userPreferences.first().gamesDisplayTarget
        )
        val displayId = overrideDisplayId
            ?: displayAffinityHelper.getDisplayTargetId(target, rolesSwapped)
        DualScreenManagerHolder.instance?.setEmulatorDisplay(
            displayId ?: displayAffinityHelper.getEmulatorDisplayId(rolesSwapped)
        )
        return displayAffinityHelper.getActivityOptions(
            forEmulator = true,
            rolesSwapped = rolesSwapped,
            overrideDisplayId = displayId
        )
    }

    companion object {
        fun placementTarget(
            stored: EmulatorDisplayTarget,
            gamesDefault: EmulatorDisplayTarget
        ): EmulatorDisplayTarget = when {
            stored != EmulatorDisplayTarget.DEFAULT -> stored
            gamesDefault == EmulatorDisplayTarget.PRESENTATION -> EmulatorDisplayTarget.DEFAULT
            else -> gamesDefault
        }

        fun resolvedTarget(
            stored: EmulatorDisplayTarget,
            gamesDefault: EmulatorDisplayTarget
        ): EmulatorDisplayTarget =
            if (stored != EmulatorDisplayTarget.DEFAULT) stored
            else EmulatorDisplayTarget.gamesDefaultFrom(gamesDefault.name)
    }
}

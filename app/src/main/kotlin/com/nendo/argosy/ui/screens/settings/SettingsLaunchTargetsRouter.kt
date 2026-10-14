package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LaunchTargetKind { GAMES, APPS }

internal fun routeRefreshLaunchTargets(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        val (screens, setCount) = withContext(Dispatchers.IO) {
            val helper = vm.displayAffinityHelper
            val rolesSwapped = SessionStateStore(vm.context).isRolesSwapped()
            val numbers = helper.screenNumbersByDisplayId()
            val roles = helper.getRoleDisplayIds(rolesSwapped)
            val screens = LaunchTargetScreens(
                primary = roles?.first?.let { numbers[it] },
                presentation = roles?.second?.let { numbers[it] },
                appScreen = helper.appScreenDisplayId(rolesSwapped)?.let { numbers[it] }
            )
            val platformsSet = vm.emulatorConfigRepo.getPlatformDisplayTargets()
                .count { EmulatorDisplayTarget.fromString(it) != EmulatorDisplayTarget.DEFAULT }
            val appsSet = vm.preferencesRepository.userPreferences.first().appDisplayTargets.size
            screens to platformsSet + appsSet
        }
        vm.displayDelegate.setLaunchTargetSummary(screens, setCount)
    }
}

internal fun routeCycleLaunchTarget(vm: SettingsViewModel, kind: LaunchTargetKind, direction: Int) {
    val display = vm._uiState.value.display
    val options = display.launchTargetScreens.options
    val current = options.indexOf(display.launchTargetScreens.shown(storedLaunchTarget(display, kind)))
    val next = if (current < 0) 0 else (current + direction).mod(options.size)
    routeSelectLaunchTarget(vm, kind, next)
}

internal fun routeSelectLaunchTarget(vm: SettingsViewModel, kind: LaunchTargetKind, index: Int) {
    val target = vm._uiState.value.display.launchTargetScreens.options.getOrNull(index) ?: return
    when (kind) {
        LaunchTargetKind.GAMES -> vm.displayDelegate.setGamesLaunchTarget(vm.viewModelScope, target)
        LaunchTargetKind.APPS -> vm.displayDelegate.setAppsLaunchTarget(vm.viewModelScope, target)
    }
}

private fun storedLaunchTarget(display: DisplayState, kind: LaunchTargetKind): EmulatorDisplayTarget =
    when (kind) {
        LaunchTargetKind.GAMES -> display.gamesLaunchTarget
        LaunchTargetKind.APPS -> display.appsLaunchTarget
    }

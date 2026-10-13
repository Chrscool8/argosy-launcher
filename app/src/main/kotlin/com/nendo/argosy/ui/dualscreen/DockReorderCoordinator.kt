package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.ui.components.DockReorderSession
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DockReorderCoordinator(
    private val scope: CoroutineScope,
    private val preferences: UserPreferencesRepository,
    private val packages: AppsRepository,
    private val cache: SessionStateStore,
    private val onAppsChanged: (List<String>) -> Unit,
    private val onSaveError: () -> Unit
) {
    private val pins = MutableStateFlow(cache.getHomeApps())
    private val installed = MutableStateFlow<Set<String>?>(null)
    private val editing = MutableStateFlow<DockReorderSession?>(null)
    val session: StateFlow<DockReorderSession?> = editing
    private val hydrated = MutableStateFlow(false)
    private var nextToken = 0L

    val apps: StateFlow<List<String>> = combine(pins, installed, editing, hydrated) { saved, available, edit, ready ->
        if (ready) (edit?.order ?: saved).filter { available?.contains(it) == true } else emptyList()
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            preferences.userPreferences.map { it.secondaryHomeApps }.distinctUntilChanged().collect { order ->
                if (order != pins.value) editing.value = null
                pins.value = order
                hydrated.value = true
                cache.setHomeApps(order)
                onAppsChanged(order)
            }
        }
        scope.launch {
            packages.packageChanges.collect {
                val available = try {
                    packages.getInstalledApps(includeSystemApps = true).map { it.packageName }.toSet()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Logger.error("DockReorder", "Package refresh failed", error)
                    return@collect
                }
                if (available != installed.value) editing.value = null
                installed.value = available
            }
        }
    }

    fun begin(packageName: String, controller: Boolean): Long? {
        if (!hydrated.value || installed.value == null || editing.value != null) return null
        val edit = DockReorderSession.lift(++nextToken, controller, pins.value, apps.value, packageName) ?: return null
        editing.value = edit
        return edit.token
    }

    fun target(token: Long, index: Int) {
        editing.update { edit -> if (edit?.token == token && !edit.saving) edit.target(index) else edit }
    }

    fun move(token: Long, delta: Int) {
        editing.value?.takeIf { it.token == token }?.let { target(token, it.targetIndex + delta) }
    }

    fun cancel(token: Long) {
        editing.update { if (it?.token == token && !it.saving) null else it }
    }

    fun save(token: Long) {
        val edit = editing.value?.takeIf { it.token == token && !it.saving } ?: return
        if (edit.order == edit.reorder.backup) {
            cancel(token)
            return
        }
        editing.value = edit.copy(saving = true)
        scope.launch {
            val saved = try {
                preferences.reorderDockPins(edit.reorder.backup, edit.order)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.error("DockReorder", "Persistence failed", error)
                false
            }
            if (!saved) {
                editing.update { if (it?.token == token) null else it }
                onSaveError()
            }
        }
    }
}

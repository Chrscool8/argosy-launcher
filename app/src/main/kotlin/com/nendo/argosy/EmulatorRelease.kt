package com.nendo.argosy

import com.nendo.argosy.data.emulator.ActiveSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

internal suspend fun releaseStoppedEmulator(
    emulatorPackage: String,
    endedSession: ActiveSession?,
    activeSession: StateFlow<ActiveSession?>,
    persistedSessionActive: () -> Boolean,
    stop: suspend (String) -> Unit,
    attempts: Int,
    intervalMs: Long
) {
    repeat(attempts) {
        if (newSessionStarted(endedSession, activeSession.value) || persistedSessionActive()) return
        stop(emulatorPackage)
        delay(intervalMs)
    }
}

private fun newSessionStarted(ended: ActiveSession?, live: ActiveSession?): Boolean =
    live != null && (ended == null || live.startTime != ended.startTime || live.gameId != ended.gameId)

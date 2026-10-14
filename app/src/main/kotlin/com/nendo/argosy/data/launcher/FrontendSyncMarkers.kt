package com.nendo.argosy.data.launcher

import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the marker files in one Frontend Sync folder. A marker holds a single positive decimal id
 * of at most 128 bytes; ids past the 32-bit range (DroidDeck's added-exe entries) and anything
 * else in the folder, such as DroidDeck's inventory json and images directory, are skipped.
 */
object FrontendSyncMarkers {
    private const val MAX_MARKER_BYTES = 128L
    private val MARKER_ID = Regex("[1-9][0-9]{0,19}")

    fun read(dir: File, folder: GameNativeSyncFolder): List<Pair<Int, String>>? =
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(folder.markerExtension, ignoreCase = true) }
            ?.mapNotNull { file ->
                val id = readId(file) ?: return@mapNotNull null
                id to file.name.dropLast(folder.markerExtension.length)
            }

    fun parseId(content: String): Int? =
        content.trim().takeIf { MARKER_ID.matches(it) }?.toIntOrNull()

    private fun readId(file: File): Int? {
        if (file.length() > MAX_MARKER_BYTES) return null
        val content = runCatching { file.readText() }.getOrNull() ?: return null
        return parseId(content)
    }
}

@Singleton
class SteamInstallReports @Inject constructor(
    private val storagePrefs: StoragePreferencesRepository
) {
    suspend fun launchersReporting(steamAppId: Long): List<String> = withContext(Dispatchers.IO) {
        val dirs = storagePrefs.preferences.first().gameNativeSyncDirs
        GameNativeSyncFolder.STEAM_INSTALL_MARKERS
            .filter { folder ->
                val dir = dirs[folder]?.let(::File) ?: return@filter false
                FrontendSyncMarkers.read(dir, folder)?.any { it.first.toLong() == steamAppId } == true
            }
            .map { it.launcherPackage }
            .distinct()
    }
}

package com.nendo.argosy.data.launcher

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.StoragePreferences
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SteamInstallMarkingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gameNative = GameNativeLauncher.packageName
    private val droidDeck = DroidDeckLauncher.packageName

    private val rows = linkedMapOf<Long, GameEntity>()

    private val gameDao = mockk<GameDao>(relaxed = true).also { dao ->
        coEvery { dao.getBySourcesForOwner(listOf(GameSource.STEAM), LocalPlatformIds.STEAM, any()) } answers {
            rows.values.toList()
        }
        coEvery { dao.setSteamLauncher(any(), any()) } answers {
            val id = firstArg<Long>()
            rows[id] = rows.getValue(id).copy(steamLauncher = secondArg())
        }
    }

    private fun steamRow(id: Long, appId: Long, launcher: String? = null, pinned: Boolean = false) {
        rows[id] = GameEntity(
            id = id,
            platformId = LocalPlatformIds.STEAM,
            platformSlug = "steam",
            title = "Game $appId",
            sortTitle = "game $appId",
            localPath = null,
            rommId = null,
            igdbId = null,
            steamAppId = appId,
            steamLauncher = launcher,
            launcherSetManually = pinned,
            source = GameSource.STEAM
        )
    }

    private fun markerFolder(vararg appIds: Long): String {
        val dir = tempFolder.newFolder()
        appIds.forEach { File(dir, "Game $it.steam").writeText(it.toString()) }
        return dir.absolutePath
    }

    private suspend fun scan(dirs: Map<GameNativeSyncFolder, String>): GameNativeStoreSync.ScanSummary {
        val storagePrefs = mockk<StoragePreferencesRepository>(relaxed = true)
        every { storagePrefs.preferences } returns flowOf(StoragePreferences(gameNativeSyncDirs = dirs))
        val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
        every { userPrefs.userPreferences } returns flowOf(UserPreferences())
        val syncPrefs = mockk<SyncPreferencesRepository>(relaxed = true)
        coEvery { syncPrefs.getRommUserId() } returns 1L
        return GameNativeStoreSync(
            gameDao = gameDao,
            platformDao = mockk(relaxed = true),
            imageCacheManager = mockk(relaxed = true),
            storagePrefs = storagePrefs,
            preferencesRepository = userPrefs,
            syncPreferencesRepository = syncPrefs,
            steamLibraryRepair = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            gameScreenshotDao = mockk(relaxed = true)
        ).scan()
    }

    @Test
    fun `a droiddeck marker marks the owned game installed for droiddeck`() = runTest {
        steamRow(id = 1, appId = 620)
        steamRow(id = 2, appId = 400, launcher = GameEntity.LAUNCHER_UNSPECIFIED)

        scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder(620, 400)))

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
        assertEquals(droidDeck, rows.getValue(2).steamLauncher)
    }

    @Test
    fun `a gamenative marker still marks gamenative`() = runTest {
        steamRow(id = 1, appId = 620)

        scan(mapOf(GameNativeSyncFolder.STEAM to markerFolder(620)))

        assertEquals(gameNative, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `a game both folders report is claimed by gamenative and stays put on rescans`() = runTest {
        steamRow(id = 1, appId = 620)
        val dirs = mapOf(
            GameNativeSyncFolder.STEAM to markerFolder(620),
            GameNativeSyncFolder.DROIDDECK to markerFolder(620)
        )

        scan(dirs)
        scan(dirs)

        assertEquals(gameNative, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `a game droiddeck already marked stays droiddeck when gamenative starts reporting it`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)

        scan(
            mapOf(
                GameNativeSyncFolder.STEAM to markerFolder(620),
                GameNativeSyncFolder.DROIDDECK to markerFolder(620)
            )
        )

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `when gamenative drops a game droiddeck still reports it moves to droiddeck in one scan`() = runTest {
        steamRow(id = 1, appId = 620, launcher = gameNative)

        scan(
            mapOf(
                GameNativeSyncFolder.STEAM to markerFolder(),
                GameNativeSyncFolder.DROIDDECK to markerFolder(620)
            )
        )

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `a droiddeck marker going away clears only droiddeck rows`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)
        steamRow(id = 2, appId = 400, launcher = gameNative)

        scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder()))

        assertNull(rows.getValue(1).steamLauncher)
        assertEquals(gameNative, rows.getValue(2).steamLauncher)
    }

    @Test
    fun `an unconfigured droiddeck folder leaves droiddeck rows alone`() = runTest {
        steamRow(id = 1, appId = 620, launcher = droidDeck)

        scan(mapOf(GameNativeSyncFolder.STEAM to markerFolder()))

        assertEquals(droidDeck, rows.getValue(1).steamLauncher)
    }

    @Test
    fun `a pinned row is never touched by either folder`() = runTest {
        steamRow(id = 1, appId = 620, launcher = null, pinned = true)
        steamRow(id = 2, appId = 400, launcher = droidDeck, pinned = true)

        scan(
            mapOf(
                GameNativeSyncFolder.STEAM to markerFolder(620),
                GameNativeSyncFolder.DROIDDECK to markerFolder(620)
            )
        )

        assertNull(rows.getValue(1).steamLauncher)
        assertEquals(droidDeck, rows.getValue(2).steamLauncher)
    }

    @Test
    fun `the droiddeck folder reports its own install counts`() = runTest {
        steamRow(id = 1, appId = 620)

        val summary = scan(mapOf(GameNativeSyncFolder.DROIDDECK to markerFolder(620, 999)))

        assertEquals(
            StoreScanResult.InstallState(markers = 2, matched = 1, notInLibrary = 1),
            summary.results[GameNativeSyncFolder.DROIDDECK]
        )
    }
}

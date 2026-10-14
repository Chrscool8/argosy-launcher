package com.nendo.argosy.ui.screens.library

import android.content.Intent
import com.nendo.argosy.data.preferences.UserPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class LibraryGameStartTest {

    private val dispatcher = StandardTestDispatcher()
    private val gameRepository = mockk<com.nendo.argosy.data.repository.GameRepository>(relaxed = true)
    private val mediaRepository = mockk<com.nendo.argosy.data.repository.MediaRepository>(relaxed = true)
    private val platformRepository = mockk<com.nendo.argosy.data.repository.PlatformRepository>(relaxed = true)
    private val preferences = mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository>(relaxed = true)
    private val gameLaunchDelegate =
        mockk<com.nendo.argosy.ui.screens.common.GameLaunchDelegate>(relaxed = true)
    private val collectionModalDelegate =
        mockk<com.nendo.argosy.ui.screens.common.CollectionModalDelegate>(relaxed = true)
    private val gradientExtractionDelegate =
        mockk<com.nendo.argosy.ui.screens.common.GradientExtractionDelegate>(relaxed = true)
    private val gameStartRouter =
        mockk<com.nendo.argosy.ui.screens.common.GameStartRouter>(relaxed = true)
    private val gameIntent = mockk<Intent>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { gameLaunchDelegate.syncOverlayState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.discPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.variantPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.memcardPickerState } returns MutableStateFlow(null)
        every {
            gameLaunchDelegate.launchGame(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } answers { arg<(Intent) -> Unit>(8).invoke(gameIntent) }
        every { collectionModalDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.common.CollectionModalDelegate.State()
        )
        every { gradientExtractionDelegate.gradients } returns MutableStateFlow(emptyMap())
        every { gradientExtractionDelegate.getGradient(any()) } returns null
        every { gameRepository.observeHiddenList() } returns flowOf(emptyList())
        every { gameRepository.observeAllList() } returns flowOf(emptyList())
        every { mediaRepository.isSignedIn } returns flowOf(false)
        every { mediaRepository.observeLibraries() } returns flowOf(emptyList())
        every { platformRepository.observeVisiblePlatforms() } returns MutableStateFlow(emptyList())
        every { preferences.userPreferences } returns flowOf(UserPreferences())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a launch reaches the game starter with nothing collecting the screen events`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.launchGame(5L)
            advanceUntilIdle()

            verify(exactly = 1) { gameStartRouter.start(5L, gameIntent, null, any()) }
        }

    private fun viewModel() = LibraryViewModel(
        context = mockk(relaxed = true),
        platformRepository = platformRepository,
        gameRepository = gameRepository,
        mediaRepository = mediaRepository,
        collectionRepository = mockk(relaxed = true),
        gameNavigationContext = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        preferencesRepository = preferences,
        homeTileRepository = mockk(relaxed = true),
        customGridShapeStore = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        gameActions = mockk(relaxed = true),
        gameLaunchDelegate = gameLaunchDelegate,
        collectionModalDelegate = collectionModalDelegate,
        romMRepository = mockk(relaxed = true),
        playStoreService = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        apkInstallManager = mockk(relaxed = true),
        platformSyncQueue = mockk(relaxed = true),
        repairImageCacheUseCase = mockk(relaxed = true),
        modalResetSignal = com.nendo.argosy.ui.ModalResetSignal(),
        gradientExtractionDelegate = gradientExtractionDelegate,
        downloadIndicatorSource = mockk(relaxed = true),
        emulatorDetector = mockk(relaxed = true),
        steamContentManager = mockk(relaxed = true),
        steamDownloadPromptController = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        gameStartRouter = gameStartRouter,
        siblingChoice = mockk(relaxed = true),
        socialRepository = mockk(relaxed = true),
        saveListStatusRepository = mockk(relaxed = true),
        libraryDefaultPlatformMigration = mockk(relaxed = true),
        showcaseSource = mockk(relaxed = true),
        reorderPlatforms = mockk(relaxed = true),
        quickNavigation = mockk { io.mockk.every { enabled } returns MutableStateFlow(true) }
    )
}

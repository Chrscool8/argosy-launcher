package com.nendo.argosy

import com.nendo.argosy.core.event.AchievementUpdateBus
import com.nendo.argosy.data.media.MediaPlaybackTracker
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.domain.model.PresentationStat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DualScreenManagerRoleSwapTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sessionStateStore: com.nendo.argosy.data.preferences.SessionStateStore
    private lateinit var preferencesRepository:
        com.nendo.argosy.data.preferences.UserPreferencesRepository
    private lateinit var manager: DualScreenManager
    private lateinit var appContext: android.content.Context
    private lateinit var hostContext: android.content.Context
    private var keyguardLocked = false

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionStateStore = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true) {
            every { userPreferences } returns kotlinx.coroutines.flow.emptyFlow()
        }
        every { sessionStateStore.hasActiveSession() } returns false
        appContext = mockk(relaxed = true) {
            every { getSystemService(android.app.KeyguardManager::class.java) } returns
                mockk {
                    every { isKeyguardLocked } answers {
                        this@DualScreenManagerRoleSwapTest.keyguardLocked
                    }
                }
            every { getSystemService(android.hardware.display.DisplayManager::class.java) } returns
                mockk(relaxed = true)
            every { getSystemService(android.os.PowerManager::class.java) } returns
                mockk(relaxed = true)
        }
        hostContext = mockk(relaxed = true) {
            every { applicationContext } returns appContext
        }
        manager = newManager(scope = testScope.backgroundScope)
    }

    @After
    fun tearDown() {
        testScope.backgroundScope.cancel()
        testScope.cancel()
        testScope.testScheduler.runCurrent()
        io.mockk.unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `rebinding after activity destruction resumes presentation preference updates`() {
        val preferences = MutableStateFlow(UserPreferences())
        every { preferencesRepository.userPreferences } returns preferences
        manager = newManager(scope = testScope)
        testScope.testScheduler.runCurrent()
        val initial = preferences.value
        assertEquals(initial.presentationStyle, manager.presentationStyle.value)
        assertEquals(initial.presentationBackgroundBlur, manager.presentationBackgroundBlur.value)

        testScope.cancel()
        val hidden = initial.copy(
            presentationStyle = initial.presentationStyle.copy(hiddenStats = PresentationStat.entries.toSet()),
            presentationBackgroundBlur = 80,
            backgroundBlur = 20
        )
        preferences.value = hidden
        testScope.testScheduler.runCurrent()
        assertEquals(initial.presentationStyle, manager.presentationStyle.value)
        assertEquals(initial.presentationBackgroundBlur, manager.presentationBackgroundBlur.value)

        val replacementScope = TestScope(testDispatcher)
        try {
            manager.rebind(mockk(relaxed = true), replacementScope)
            testScope.testScheduler.runCurrent()
            assertTrue(replacementScope.isActive)
            assertEquals(hidden.presentationStyle, manager.presentationStyle.value)
            assertEquals(80, manager.presentationBackgroundBlur.value)

            val shown = initial.copy(presentationBackgroundBlur = 0, backgroundBlur = 60)
            preferences.value = shown
            testScope.testScheduler.runCurrent()
            assertEquals(shown.presentationStyle, manager.presentationStyle.value)
            assertEquals(0, manager.presentationBackgroundBlur.value)
        } finally {
            replacementScope.cancel()
            testScope.testScheduler.runCurrent()
        }
    }

    @Test
    fun `a swap flips the live arrangement even when the stored override disagrees`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "SWAPPED"

        manager.swapRoles()

        assertTrue(
            "swapRoles must derive the next arrangement from the live value, not the override",
            manager.isRolesSwapped.value
        )
    }

    @Test
    fun `a swap records the override that matches the arrangement it produced`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.swapRoles()

        val stored = slot<String>()
        verify { sessionStateStore.setDisplayRoleOverride(capture(stored)) }
        assertEquals(DisplayRoleOverride.SWAPPED.name, stored.captured)
        assertTrue(manager.isRolesSwapped.value)
    }

    @Test
    fun `a swap is refused while a session is running`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.hasActiveSession() } returns true

        manager.swapRoles()

        assertEquals(false, manager.isRolesSwapped.value)
    }

    @Test
    fun `a layout giving the top screen primary swaps the roles under an auto override`() {
        thorWithLayout(primary = TOP_SCREEN, override = DisplayRoleOverride.AUTO)

        manager.rederiveRolesFromLayout()
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        verify { sessionStateStore.setRolesSwapped(true) }
        verify(exactly = 0) { sessionStateStore.setDisplayRoleOverride(any()) }
    }

    @Test
    fun `a layout giving the top screen primary swaps the roles under a standard override, which follows it`() {
        thorWithLayout(primary = TOP_SCREEN, override = DisplayRoleOverride.STANDARD)

        manager.rederiveRolesFromLayout()
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        verify { sessionStateStore.setRolesSwapped(true) }
        verify { sessionStateStore.setDisplayRoleOverride(DisplayRoleOverride.SWAPPED.name) }
    }

    @Test
    fun `a layout giving the lower screen primary unswaps the roles under a swapped override, which follows it`() {
        thorWithLayout(primary = LOWER_SCREEN, override = DisplayRoleOverride.SWAPPED)
        manager.setRolesSwapped(true)

        manager.rederiveRolesFromLayout()
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
        verify { sessionStateStore.setRolesSwapped(false) }
        verify { sessionStateStore.setDisplayRoleOverride(DisplayRoleOverride.STANDARD.name) }
    }

    @Test
    fun `a layout already matching the live arrangement commits nothing`() {
        thorWithLayout(primary = TOP_SCREEN, override = DisplayRoleOverride.AUTO)
        manager.setRolesSwapped(true)

        manager.rederiveRolesFromLayout()
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        verify(exactly = 0) { sessionStateStore.setRolesSwapped(any()) }
    }

    @Test
    fun `re-deriving waits while a session is active`() {
        thorWithLayout(primary = TOP_SCREEN, override = DisplayRoleOverride.AUTO)
        every { sessionStateStore.hasActiveSession() } returns true

        manager.rederiveRolesFromLayout()
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
    }

    @Test
    fun `a layout the running game could not follow applies when the session ends`() {
        thorWithLayout(primary = TOP_SCREEN, override = DisplayRoleOverride.AUTO)
        var sessionActive = true
        every { sessionStateStore.hasActiveSession() } answers { sessionActive }

        manager.setPrimaryDisplayId(TOP_SCREEN.displayId)
        testScope.testScheduler.advanceUntilIdle()
        assertEquals(false, manager.isRolesSwapped.value)

        sessionActive = false
        manager.onSessionChanged(-1L)
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        verify { sessionStateStore.setRolesSwapped(true) }
    }

    @Test
    fun `a virtual display coming and going during a session leaves roles, override and session alone`() {
        val (listener, tracker) = sessionOnLowerScreen(screens = { listOf(TOP_SCREEN, LOWER_SCREEN) })

        listener.onDisplayAdded(VIRTUAL_DISPLAY)
        listener.onDisplayChanged(VIRTUAL_DISPLAY)
        listener.onDisplayRemoved(VIRTUAL_DISPLAY)
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        assertEquals(LOWER_SCREEN.displayId, manager.emulatorDisplayId)
        verify(exactly = 0) { sessionStateStore.setRolesSwapped(any()) }
        verify(exactly = 0) { sessionStateStore.setDisplayRoleOverride(any()) }
        verify(exactly = 0) { sessionStateStore.clearSession() }
        verify(exactly = 0) { tracker.endSessionInBackground(any(), any()) }
    }

    @Test
    fun `a screen removed during a session still reaches the session teardown`() {
        var screens = listOf(TOP_SCREEN, LOWER_SCREEN)
        val (listener, tracker) = sessionOnLowerScreen(screens = { screens })

        screens = listOf(TOP_SCREEN)
        listener.onDisplayRemoved(LOWER_SCREEN.displayId)
        testScope.testScheduler.advanceUntilIdle()

        verify(exactly = 1) { tracker.endSessionInBackground(any(), any()) }
    }

    private fun sessionOnLowerScreen(
        screens: () -> List<com.nendo.argosy.util.AttachedScreen>
    ): Pair<android.hardware.display.DisplayManager.DisplayListener,
        com.nendo.argosy.data.emulator.PlaySessionTracker> {
        io.mockk.mockkObject(com.nendo.argosy.hardware.CompanionGuardService.Companion)
        every { com.nendo.argosy.hardware.CompanionGuardService.start(any()) } returns Unit
        every { com.nendo.argosy.hardware.CompanionGuardService.stop(any()) } returns Unit
        val listener = slot<android.hardware.display.DisplayManager.DisplayListener>()
        val helper = mockk<com.nendo.argosy.util.DisplayAffinityHelper>(relaxed = true) {
            every { getRoleDisplayIds(any()) } returns null
            every { isPhysicalDisplay(any()) } returns true
            every { isDockedDark } returns false
            every { isDesktopMode } returns false
            every { registerDisplayListener(capture(listener), any()) } returns Unit
        }
        val tracker = mockk<com.nendo.argosy.data.emulator.PlaySessionTracker>(relaxed = true)
        every { preferencesRepository.userPreferences } returns
            kotlinx.coroutines.flow.flowOf(UserPreferences())
        every { sessionStateStore.hasActiveSession() } returns true
        every { sessionStateStore.getDisplayRoleOverride() } returns DisplayRoleOverride.STANDARD.name
        manager = newManager(
            scope = testScope.backgroundScope,
            displayAffinityHelper = helper,
            attachedScreens = screens,
            playSessionTracker = tracker
        )
        manager.setRolesSwapped(true)
        manager.emulatorDisplayId = LOWER_SCREEN.displayId
        manager.registerReceivers()
        testScope.testScheduler.advanceUntilIdle()
        return listener.captured to tracker
    }

    @Test
    fun `a stored layout moving the primary role leaves the override at auto`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.setPrimaryDisplayId(android.view.Display.DEFAULT_DISPLAY)
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        verify(exactly = 0) { sessionStateStore.setDisplayRoleOverride(any()) }
        io.mockk.coVerify(exactly = 0) { preferencesRepository.setDisplayRoleOverride(any()) }
    }

    @Test
    fun `a stored layout returning the primary role to the second screen leaves the override at auto`() {
        manager.setRolesSwapped(true)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.setPrimaryDisplayId(2)
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
        verify(exactly = 0) { sessionStateStore.setDisplayRoleOverride(any()) }
        io.mockk.coVerify(exactly = 0) { preferencesRepository.setDisplayRoleOverride(any()) }
    }

    @Test
    fun `a live swap commits the roles once the game reaches the other display`() {
        val host = liveSwapReady(FakeGameWindowMover(arrives = true))

        manager.swapRoles()
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        assertEquals(PRESENTATION_DISPLAY, manager.emulatorDisplayId)
        verify(exactly = 0) { host.displayMoveAbandoned() }
    }

    @Test
    fun `a live swap the game never completes keeps the roles and the game display`() {
        val host = liveSwapReady(FakeGameWindowMover(arrives = false))

        manager.swapRoles()
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
        assertEquals(PRIMARY_DISPLAY, manager.emulatorDisplayId)
        verify(exactly = 1) { host.displayMoveAbandoned() }
    }

    @Test
    fun `a game arriving after the move was abandoned still commits the roles`() {
        liveSwapReady(FakeGameWindowMover(arrives = false))
        manager.swapRoles()
        testScope.testScheduler.advanceUntilIdle()

        manager.onGameMovedToDisplay(PRESENTATION_DISPLAY)

        assertTrue(manager.isRolesSwapped.value)
        assertEquals(PRESENTATION_DISPLAY, manager.emulatorDisplayId)
    }

    @Test
    fun `a late arrival after the session ended changes nothing`() {
        liveSwapReady(FakeGameWindowMover(arrives = false))
        manager.swapRoles()
        testScope.testScheduler.advanceUntilIdle()
        manager.emulatorDisplayId = null

        manager.onGameMovedToDisplay(PRESENTATION_DISPLAY)

        assertEquals(false, manager.isRolesSwapped.value)
        assertEquals(null, manager.emulatorDisplayId)
    }

    @Test
    fun `locking during delayed emulator recovery prevents focus until a new unlocked recovery`() = testScope.runTest {
        io.mockk.mockkObject(com.nendo.argosy.hardware.FocusDirectorActivity.Companion)
        every {
            com.nendo.argosy.hardware.FocusDirectorActivity.launchOnDisplay(any(), any())
        } returns Unit
        every { sessionStateStore.hasActiveSession() } returns true
        manager = newManager(scope = testScope.backgroundScope)
        manager.emulatorDisplayId = PRESENTATION_DISPLAY
        manager.restoreEmulatorFocus()
        testScope.testScheduler.runCurrent()
        testScope.testScheduler.advanceTimeBy(100)
        assertEquals(false, manager.isKeyguardShowing)

        keyguardLocked = true
        testScope.testScheduler.advanceTimeBy(200)
        testScope.testScheduler.runCurrent()

        verify(exactly = 0) {
            com.nendo.argosy.hardware.FocusDirectorActivity.launchOnDisplay(any(), any())
        }
        keyguardLocked = false
        manager.restoreEmulatorFocus()
        testScope.testScheduler.advanceTimeBy(200)
        testScope.testScheduler.runCurrent()

        verify(exactly = 1) {
            com.nendo.argosy.hardware.FocusDirectorActivity.launchOnDisplay(appContext, PRESENTATION_DISPLAY)
        }
    }

    @Test
    fun `locking during delayed companion recovery suppresses launch and unlock retries it`() = testScope.runTest {
        val receiver = companionRecoveryReceiver()
        keyguardLocked = false
        manager.ensureCompanionLaunched()
        testScope.testScheduler.runCurrent()
        testScope.testScheduler.advanceTimeBy(250)
        assertEquals(false, manager.isKeyguardShowing)
        keyguardLocked = true
        testScope.testScheduler.advanceTimeBy(250)
        testScope.testScheduler.runCurrent()

        verify(exactly = 0) { hostContext.startActivity(any(), any<android.os.Bundle>()) }

        keyguardLocked = false
        receiver.onReceive(appContext, userPresentIntent())
        testScope.testScheduler.runCurrent()
        testScope.testScheduler.advanceTimeBy(500)
        testScope.testScheduler.runCurrent()

        verify(exactly = 1) { hostContext.startActivity(any(), any<android.os.Bundle>()) }
        manager.unregisterReceivers()
    }

    @Test
    fun `unlock recovery does not displace a foreign app on the second screen`() = testScope.runTest {
        val receiver = companionRecoveryReceiver()
        every { sessionStateStore.isForeignAppOnSecondary() } returns true
        keyguardLocked = false

        receiver.onReceive(appContext, userPresentIntent())
        testScope.testScheduler.advanceTimeBy(500)
        testScope.testScheduler.runCurrent()

        verify(exactly = 0) { hostContext.startActivity(any(), any<android.os.Bundle>()) }
        manager.unregisterReceivers()
    }

    @Test
    fun `unlock recovery does not displace an active session outside the launcher`() = testScope.runTest {
        val receiver = companionRecoveryReceiver()
        every { sessionStateStore.hasActiveSession() } returns true
        every { sessionStateStore.isArgosyForeground() } returns false
        keyguardLocked = false

        receiver.onReceive(appContext, userPresentIntent())
        testScope.testScheduler.advanceTimeBy(500)
        testScope.testScheduler.runCurrent()

        verify(exactly = 0) { hostContext.startActivity(any(), any<android.os.Bundle>()) }
        manager.unregisterReceivers()
    }

    private fun companionRecoveryReceiver(): android.content.BroadcastReceiver {
        io.mockk.mockkStatic(androidx.core.content.ContextCompat::class)
        val receiver = slot<android.content.BroadcastReceiver>()
        every {
            androidx.core.content.ContextCompat.registerReceiver(
                appContext, capture(receiver), any(), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } returns null
        io.mockk.mockkObject(com.nendo.argosy.hardware.CompanionGuardService.Companion)
        every { com.nendo.argosy.hardware.CompanionGuardService.start(any()) } returns Unit
        manager = newManager(
            scope = testScope.backgroundScope,
            displayAffinityHelper = mockk(relaxed = true) {
                every { isDockedDark } returns false
                every { hasSecondaryDisplay } returns true
                every { getCompanionLaunchOptions() } returns mockk(relaxed = true)
            }
        )
        keyguardLocked = true
        manager.registerReceivers()
        testScope.testScheduler.runCurrent()
        return receiver.captured
    }

    private fun userPresentIntent(): android.content.Intent = mockk {
        every { action } returns android.content.Intent.ACTION_USER_PRESENT
    }

    private fun liveSwapReady(mover: FakeGameWindowMover): DualScreenManager.LiveMoveHost {
        io.mockk.mockkObject(com.nendo.argosy.hardware.FocusDirectorActivity.Companion)
        every {
            com.nendo.argosy.hardware.FocusDirectorActivity.launchOnDisplay(any(), any())
        } returns Unit
        every { sessionStateStore.hasActiveSession() } returns true
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"
        manager = newManager(
            displayAffinityHelper = mockk(relaxed = true) {
                every { isDockedDark } returns false
                every { getRoleDisplayIds(false) } returns (PRIMARY_DISPLAY to PRESENTATION_DISPLAY)
                every { getRoleDisplayIds(true) } returns (PRESENTATION_DISPLAY to PRIMARY_DISPLAY)
                every { appScreenDisplayId(any()) } returns null
            },
            gameWindowMover = mover
        )
        mover.onMoved = manager::onGameMovedToDisplay
        manager.setRolesSwapped(false)
        manager.emulatorDisplayId = PRIMARY_DISPLAY
        val host = mockk<DualScreenManager.LiveMoveHost>(relaxed = true)
        manager.registerLiveMoveHost(host)
        manager.registerReceivers()
        testScope.testScheduler.advanceUntilIdle()
        return host
    }

    private class FakeGameWindowMover(
        private val arrives: Boolean
    ) : com.nendo.argosy.hardware.GameWindowMover {
        var onMoved: (Int) -> Unit = {}

        override suspend fun isAvailable(): Boolean = true

        override suspend fun moveGame(displayId: Int): Boolean {
            if (arrives) onMoved(displayId)
            return true
        }
    }

    private fun thorWithLayout(
        primary: com.nendo.argosy.util.AttachedScreen,
        override: DisplayRoleOverride
    ) {
        val screens = listOf(TOP_SCREEN, LOWER_SCREEN)
        val layout = com.nendo.argosy.domain.model.ScreenLayout(
            screens.associate {
                it.key to if (it == primary) {
                    com.nendo.argosy.domain.model.ScreenRole.PRIMARY
                } else {
                    com.nendo.argosy.domain.model.ScreenRole.PRESENTATION
                }
            }
        )
        val layouts = com.nendo.argosy.domain.model.ScreenLayouts().with(
            com.nendo.argosy.domain.model.ScreenLayouts.setKeyOf(screens.map { it.key }),
            layout
        )
        every { preferencesRepository.userPreferences } returns
            kotlinx.coroutines.flow.flowOf(UserPreferences(screenLayouts = layouts))
        every { sessionStateStore.getDisplayRoleOverride() } returns override.name
        manager = newManager(attachedScreens = { screens })
        manager.setRolesSwapped(false)
    }

    private companion object {
        const val PRIMARY_DISPLAY = 0
        const val PRESENTATION_DISPLAY = 1
        const val VIRTUAL_DISPLAY = 9
        val TOP_SCREEN = com.nendo.argosy.util.AttachedScreen(
            key = "top", displayId = 0, number = 1, widthPx = 1920, heightPx = 1080, builtIn = true
        )
        val LOWER_SCREEN = com.nendo.argosy.util.AttachedScreen(
            key = "lower", displayId = 4, number = 2, widthPx = 1240, heightPx = 1080, builtIn = true
        )
    }

    private fun newManager(
        scope: kotlinx.coroutines.CoroutineScope = testScope,
        displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper =
            mockk(relaxed = true) { every { getRoleDisplayIds(any()) } returns null },
        gameWindowMover: com.nendo.argosy.hardware.GameWindowMover = FakeGameWindowMover(arrives = false),
        attachedScreens: () -> List<com.nendo.argosy.util.AttachedScreen> = { emptyList() },
        playSessionTracker: com.nendo.argosy.data.emulator.PlaySessionTracker = mockk(relaxed = true)
    ): DualScreenManager = DualScreenManager(
        context = hostContext,
        scope = scope,
        gameDao = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        activeSaveRepository = mockk(relaxed = true),
        prefetchGameSaveDataUseCase = mockk(relaxed = true),
        platformRepository = mockk(relaxed = true),
        collectionRepository = mockk(relaxed = true),
        socialRepository = mockk(relaxed = true),
        downloadQueueDao = mockk(relaxed = true),
        downloadQueueRepository = mockk(relaxed = true),
        gameFileDao = mockk(relaxed = true),
        downloadManager = mockk(relaxed = true),
        gameActionsDelegate = mockk(relaxed = true),
        platformSyncQueue = mockk(relaxed = true),
        sessionEndCoordinator = mockk(relaxed = true),
        saveCacheManager = mockk(relaxed = true),
        raRepository = mockk(relaxed = true),
        raTileContentRepository = mockk(relaxed = true),
        achievementUpdateBus = AchievementUpdateBus(),
        displayAffinityHelper = displayAffinityHelper,
        sessionStateStore = sessionStateStore,
        preferencesRepository = preferencesRepository,
        imageCacheManager = mockk(relaxed = true),
        romMRepository = mockk(relaxed = true),
        gameDocumentLoader = mockk(relaxed = true),
        documentHighlightStore = mockk(relaxed = true),
        resolveGameEmulatorContext = mockk(relaxed = true),
        hapticManager = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        homeTileRepository = mockk(relaxed = true),
        homeTilePromptQueue = mockk(relaxed = true),
        appsRepository = mockk(relaxed = true),
        appShortcutActions = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        titleIdDownloadObserver = mockk(relaxed = true),
        homeGridPageRepository = mockk(relaxed = true),
        pageChooserEntrySource = mockk(relaxed = true),
        ambientAudioManager = mockk(relaxed = true),
        emulatorConfigDao = mockk(relaxed = true),
        configureEmulatorUseCase = mockk(relaxed = true),
        builtinCoreResolver = mockk(relaxed = true),
        saveHandlerRegistry = mockk(relaxed = true),
        steamDownloadQueueDao = mockk(relaxed = true),
        steamRepository = mockk(relaxed = true),
        playSessionTracker = playSessionTracker,
        permissionHelper = mockk(relaxed = true),
        steamContentManager = mockk(relaxed = true),
        repairImageCacheUseCase = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        gradientExtractionDelegate = mockk(relaxed = true),
        filePickerFlow = mockk(relaxed = true),
        gameThemeAudioCoordinator = mockk(relaxed = true),
        getPinnedCollectionsUseCase = mockk(relaxed = true),
        getGamesForPinnedCollectionUseCase = mockk(relaxed = true),
        advanceCollectionFocusUseCase = mockk(relaxed = true),
        prepareCollectionQueueUseCase = mockk(relaxed = true),
        mediaRepository = mockk(relaxed = true),
        getRelatedMediaUseCase = mockk(relaxed = true),
        resolveMediaPlayTargetUseCase = mockk(relaxed = true),
        mediaPlaybackTracker = MediaPlaybackTracker(),
        mediaAvailabilityVerifier = mockk(relaxed = true),
        mediaDownloadDelegate = mockk(relaxed = true),
        mediaSeriesDelegate = mockk(relaxed = true),
        mediaSiblingsDelegate = mockk(relaxed = true),
        gameWindowMover = gameWindowMover,
        attachedScreens = attachedScreens
    )
}

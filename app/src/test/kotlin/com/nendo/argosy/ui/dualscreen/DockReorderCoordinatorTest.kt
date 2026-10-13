package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.InstalledApp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DockReorderCoordinatorTest {
    private class Fixture(scope: TestScope, initialPreferences: Flow<UserPreferences>? = null) {
        val original = listOf("a", "missing", "b", "c")
        val prefs = MutableStateFlow(UserPreferences(secondaryHomeApps = original))
        val packages = MutableStateFlow(0)
        var available = listOf("a", "b", "c", "d")
        val repository = mockk<UserPreferencesRepository>()
        val apps = mockk<AppsRepository>()
        val cache = mockk<SessionStateStore>(relaxed = true)
        var failures = 0
        val coordinator: DockReorderCoordinator

        init {
            every { repository.userPreferences } returns (initialPreferences ?: prefs)
            every { apps.packageChanges } returns packages.map { Unit }
            every { cache.getHomeApps() } returns original
            coEvery { apps.getInstalledApps(true) } coAnswers {
                available.map { InstalledApp(it, it, false, false) }
            }
            coEvery { repository.reorderDockPins(any(), any()) } coAnswers {
                prefs.value = prefs.value.copy(secondaryHomeApps = secondArg())
                true
            }
            coordinator = DockReorderCoordinator(scope.backgroundScope, repository, apps, cache, {}, { failures++ })
        }
    }

    @Test
    fun `cached pins stay hidden until authoritative preferences hydrate`() = runTest(UnconfinedTestDispatcher()) {
        val delayed = MutableSharedFlow<UserPreferences>()
        val fixture = Fixture(this, delayed)

        assertEquals(emptyList<String>(), fixture.coordinator.apps.value)
        assertNull(fixture.coordinator.begin("a", false))
        delayed.emit(UserPreferences(secondaryHomeApps = listOf("c", "b", "a")))
        assertEquals(listOf("c", "b", "a"), fixture.coordinator.apps.value)
    }

    @Test
    fun `preview cancel and successful commit keep cache authoritative`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = Fixture(this)
        val dock = fixture.coordinator
        val token = dock.begin("a", true)!!
        dock.target(token, 2)
        assertEquals(listOf("c", "b", "a"), dock.apps.value)
        verify(exactly = 0) { fixture.cache.setHomeApps(listOf("c", "missing", "b", "a")) }
        dock.cancel(token)
        assertEquals(listOf("a", "b", "c"), dock.apps.value)
        coVerify(exactly = 0) { fixture.repository.reorderDockPins(any(), any()) }
        val saved = dock.begin("a", false)!!
        dock.target(saved, 2)
        dock.save(saved)
        assertNull(dock.session.value)
        assertEquals(listOf("c", "missing", "b", "a"), fixture.prefs.value.secondaryHomeApps)
        verify(exactly = 1) { fixture.cache.setHomeApps(listOf("c", "missing", "b", "a")) }
    }

    @Test
    fun `failed write clears preview without touching saved pins and reports once`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = Fixture(this)
        coEvery { fixture.repository.reorderDockPins(any(), any()) } throws IOException("disk unavailable")
        val dock = fixture.coordinator
        val token = dock.begin("a", false)!!
        dock.target(token, 2)
        dock.save(token)

        assertNull(dock.session.value)
        assertEquals(fixture.original, fixture.prefs.value.secondaryHomeApps)
        assertEquals(listOf("a", "b", "c"), dock.apps.value)
        assertEquals(1, fixture.failures)
        verify(exactly = 1) { fixture.cache.setHomeApps(fixture.original) }
    }

    @Test
    fun `stale completion cannot cancel a newer session`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = Fixture(this)
        val result = CompletableDeferred<Boolean>()
        coEvery { fixture.repository.reorderDockPins(any(), any()) } coAnswers { result.await() }
        val dock = fixture.coordinator
        val old = dock.begin("a", true)!!
        dock.target(old, 2)
        dock.save(old)
        fixture.prefs.value = fixture.prefs.value.copy(secondaryHomeApps = fixture.original + "d")
        val newer = dock.begin("b", false)!!
        result.complete(false)

        assertEquals(newer, dock.session.value?.token)
        dock.cancel(old)
        assertEquals(newer, dock.session.value?.token)
    }

    @Test
    fun `inventory and pin changes discard preview without saving`() = runTest(UnconfinedTestDispatcher()) {
        val fixture = Fixture(this)
        val dock = fixture.coordinator
        val token = dock.begin("a", false)!!
        dock.target(token, 2)
        fixture.available = listOf("b", "c", "d")
        fixture.packages.value++

        assertNull(dock.session.value)
        assertEquals(listOf("b", "c"), dock.apps.value)
        assertEquals(fixture.original, fixture.prefs.value.secondaryHomeApps)
        val next = dock.begin("b", true)!!
        dock.target(next, 1)
        fixture.prefs.value = fixture.prefs.value.copy(secondaryHomeApps = fixture.original - "c")
        assertNull(dock.session.value)
        coVerify(exactly = 0) { fixture.repository.reorderDockPins(any(), any()) }
    }
}

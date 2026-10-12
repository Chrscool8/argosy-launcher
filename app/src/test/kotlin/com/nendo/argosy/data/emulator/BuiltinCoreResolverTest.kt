package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuiltinCoreResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val platformId = 14L
    private val emulatorConfigDao = mockk<EmulatorConfigDao>()
    private val preferences = mockk<UserPreferencesRepository>()

    private fun resolver(vararg biosFiles: String, gameCore: String? = null): BuiltinCoreResolver {
        val systemDir = tempFolder.newFolder()
        biosFiles.forEach { java.io.File(systemDir, it).writeBytes(byteArrayOf(0)) }
        coEvery { emulatorConfigDao.getByGameId(any()) } returns gameCore?.let {
            EmulatorConfigEntity(platformId = null, gameId = 1L, packageName = null, displayName = null, coreName = it)
        }
        coEvery { emulatorConfigDao.getDefaultForPlatform(platformId) } returns null
        every { preferences.getBuiltinCoreSelections() } returns flowOf(emptyMap())
        return BuiltinCoreResolver(emulatorConfigDao, preferences, systemDir)
    }

    @Test
    fun `the psx default is Beetle PSX HW when a PlayStation BIOS is present`() = runTest {
        assertEquals("mednafen_psx_hw", resolver("scph5501.bin").resolveCoreId(1L, platformId, "psx"))
        assertEquals("mednafen_psx_hw", resolver("scph5502.bin").resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `the psx default falls back to PCSX ReARMed when no PlayStation BIOS is present`() = runTest {
        assertEquals("pcsx_rearmed", resolver().resolveCoreId(1L, platformId, "psx"))
        assertEquals("pcsx_rearmed", resolver("unrelated.bin").resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `an explicit Beetle choice is kept even without a BIOS`() = runTest {
        assertEquals(
            "mednafen_psx_hw",
            resolver(gameCore = "mednafen_psx_hw").resolveCoreId(1L, platformId, "psx")
        )
    }

    @Test
    fun `platforms without a BIOS fallback keep their default`() = runTest {
        assertEquals("ppsspp", resolver().resolveCoreId(1L, platformId, "psp"))
    }
}

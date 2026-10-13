package com.nendo.argosy.data.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDockCacheTest {
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val context = mockk<Context>()

    private fun store(): SessionStateStore {
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        return SessionStateStore(context)
    }

    @Test
    fun `empty ordered cache takes precedence over legacy pins`() {
        every { prefs.contains("home_apps_order") } returns true
        every { prefs.getString("home_apps_order", null) } returns ""
        every { prefs.getStringSet("home_apps", any()) } returns setOf("a")

        assertEquals(emptyList<String>(), store().getHomeApps())
    }

    @Test
    fun `missing ordered cache reads legacy pins`() {
        every { prefs.contains("home_apps_order") } returns false
        every { prefs.getStringSet("home_apps", any()) } returns linkedSetOf("c", "a")

        assertEquals(listOf("c", "a"), store().getHomeApps())
    }

    @Test
    fun `ordered cache writes explicit empty string and retires legacy pins`() {
        store().setHomeApps(emptyList())

        verify { editor.putString("home_apps_order", "") }
        verify { editor.remove("home_apps") }
        verify { editor.apply() }
    }
}

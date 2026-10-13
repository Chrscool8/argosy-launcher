package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration212To213Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_212_213.migrate(db)
        return captured
    }

    @Test
    fun `adds the snapshot unlocks as a nullable column, so every recorded session keeps its row`() {
        assertEquals(
            listOf("ALTER TABLE `play_sessions` ADD COLUMN `raSnapshotUnlocks` TEXT"),
            statements()
        )
    }

    @Test
    fun `is the last registered migration and leaves no gap`() {
        assertEquals(Migration_212_213, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(213)
    }
}

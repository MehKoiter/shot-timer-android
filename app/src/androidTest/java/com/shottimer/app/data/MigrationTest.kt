package com.shottimer.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

/**
 * Guards the exact failure mode that crashed the app on real hardware earlier this project:
 * a migration that runs without error but leaves a schema that doesn't match what Room expects
 * from the current entities, or that silently drops existing rows. Every migration here is
 * checked against a database that already has real-shaped data in it, not an empty table.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ShotTimerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4_preservesExistingRowsAndAddsNullableRemoteId() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO runs " +
                    "(id, timestampEpochMillis, totalElapsedMillis, shotTimestampsMillis, parTimeSeconds, drillName, shooterName) " +
                    "VALUES (1, 1000, 5000, '100,200,300', NULL, 'Bill Drill', 'Mike')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        val cursor = migrated.query("SELECT * FROM runs WHERE id = 1")
        cursor.use {
            assertTrue("expected the pre-migration row to still be present", it.moveToFirst())
            assertEquals(5000L, it.getLong(it.getColumnIndexOrThrow("totalElapsedMillis")))
            assertEquals("Mike", it.getString(it.getColumnIndexOrThrow("shooterName")))
            assertTrue(
                "remoteId should be nullable and default to NULL for pre-existing rows",
                it.isNull(it.getColumnIndexOrThrow("remoteId"))
            )
        }
    }

    @Test
    fun migrate4To5_preservesRunsAndCreatesUsableCustomDrillsTable() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO runs " +
                    "(id, timestampEpochMillis, totalElapsedMillis, shotTimestampsMillis, parTimeSeconds, drillName, shooterName, remoteId) " +
                    "VALUES (1, 1000, 5000, '100,200,300', NULL, 'Bill Drill', 'Mike', 'abc123')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        migrated.query("SELECT * FROM runs WHERE id = 1").use {
            assertTrue("expected the pre-migration run to still be present", it.moveToFirst())
            assertEquals("abc123", it.getString(it.getColumnIndexOrThrow("remoteId")))
        }
        // The new table must actually accept inserts with the schema Room validated above.
        migrated.execSQL(
            "INSERT INTO custom_drills (name, instructions, roundCount) VALUES ('My Drill', 'Do it', 5)"
        )
        migrated.query("SELECT * FROM custom_drills").use {
            assertTrue("expected the inserted custom drill", it.moveToFirst())
            assertEquals("My Drill", it.getString(it.getColumnIndexOrThrow("name")))
            assertEquals(5, it.getInt(it.getColumnIndexOrThrow("roundCount")))
        }
    }
}

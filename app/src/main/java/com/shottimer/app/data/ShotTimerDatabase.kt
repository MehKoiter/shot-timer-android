package com.shottimer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the nullable piRunId column for runs synced from the Pi companion - existing rows get
 * NULL, which is exactly "not from the Pi", so no backfill is needed. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE runs ADD COLUMN piRunId INTEGER")
    }
}

/** Real devices that ran this app's history hit a crash MIGRATION_1_2 alone can't fix: the
 * never-merged `drills-section` branch was independently tested on the same hardware and also
 * bumped `runs` to version 2, but with a `drillName` column instead of `piRunId` - so Room saw
 * "already at version 2", skipped MIGRATION_1_2 entirely, and then failed identity validation
 * against a schema it never actually produced. Rather than trust ALTER TABLE against a version-2
 * table whose real shape depends on which branch last touched it, rebuild `runs` from scratch
 * with the exact current schema and copy over only the columns guaranteed to exist either way
 * (piRunId is populated NULL - no real device ever had genuine piRunId data at version 2, only
 * ever drillName, which is dropped since that feature isn't part of this schema). */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE runs_new (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "timestampEpochMillis INTEGER NOT NULL, " +
                "totalElapsedMillis INTEGER NOT NULL, " +
                "shotTimestampsMillis TEXT NOT NULL, " +
                "parTimeSeconds REAL, " +
                "piRunId INTEGER)"
        )
        db.execSQL(
            "INSERT INTO runs_new (id, timestampEpochMillis, totalElapsedMillis, shotTimestampsMillis, parTimeSeconds, piRunId) " +
                "SELECT id, timestampEpochMillis, totalElapsedMillis, shotTimestampsMillis, parTimeSeconds, NULL FROM runs"
        )
        db.execSQL("DROP TABLE runs")
        db.execSQL("ALTER TABLE runs_new RENAME TO runs")
    }
}

@Database(entities = [RunEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ShotTimerDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao

    companion object {
        @Volatile
        private var instance: ShotTimerDatabase? = null

        fun getInstance(context: Context): ShotTimerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShotTimerDatabase::class.java,
                    "shot_timer.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}

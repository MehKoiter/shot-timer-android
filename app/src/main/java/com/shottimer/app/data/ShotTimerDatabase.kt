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

@Database(entities = [RunEntity::class], version = 2, exportSchema = false)
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

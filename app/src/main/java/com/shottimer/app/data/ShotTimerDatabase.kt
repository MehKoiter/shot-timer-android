package com.shottimer.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE runs ADD COLUMN drillName TEXT")
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE runs ADD COLUMN shooterName TEXT")
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE runs ADD COLUMN remoteId TEXT")
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS custom_drills (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "instructions TEXT NOT NULL, " +
                "roundCount INTEGER NOT NULL)"
        )
    }
}

@Database(entities = [RunEntity::class, CustomDrillEntity::class], version = 5, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ShotTimerDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun customDrillDao(): CustomDrillDao

    companion object {
        @Volatile
        private var instance: ShotTimerDatabase? = null

        fun getInstance(context: Context): ShotTimerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShotTimerDatabase::class.java,
                    "shot_timer.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { instance = it }
            }

        /** Forces the next [getInstance] to build a fresh, empty database. Robolectric does not
         * reset app-defined companion-object singletons between test methods (only Android
         * framework statics), so without this every ViewModel test going through the real DAO
         * singleton would silently share rows with every other test in the same JVM - closing
         * the Room wrapper alone isn't enough either, since "shot_timer.db" is a named (not
         * in-memory) file that a fresh instance would just reopen with the old data still in it. */
        @VisibleForTesting
        fun resetForTesting(context: Context) {
            instance?.close()
            instance = null
            context.applicationContext.deleteDatabase("shot_timer.db")
        }
    }
}

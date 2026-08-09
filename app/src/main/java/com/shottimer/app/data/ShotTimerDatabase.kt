package com.shottimer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE runs ADD COLUMN drillName TEXT")
    }
}

@Database(entities = [RunEntity::class], version = 2, exportSchema = true)
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

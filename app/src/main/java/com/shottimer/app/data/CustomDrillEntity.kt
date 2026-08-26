package com.shottimer.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-defined drill. Kept separate from the static [com.shottimer.app.drills.DrillLibrary] -
 * builtins stay reference data in code, and runs keep tagging drills by plain name string
 * (RunEntity.drillName), so custom drills need no relationship to the runs table and deleting
 * one never touches recorded history.
 */
@Entity(tableName = "custom_drills")
data class CustomDrillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val instructions: String,
    val roundCount: Int
)

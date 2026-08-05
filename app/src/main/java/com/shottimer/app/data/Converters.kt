package com.shottimer.app.data

import androidx.room.TypeConverter

/** Room has no native list column type, so shot timestamps are stored as a comma-joined string. */
class Converters {
    @TypeConverter
    fun fromShotTimestamps(value: List<Long>): String = value.joinToString(",")

    @TypeConverter
    fun toShotTimestamps(value: String): List<Long> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.toLong() }
}

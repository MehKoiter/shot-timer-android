package com.shottimer.app.data

/** A shooter's "profile" is derived entirely from their tagged runs - there's no separate
 * profile table to keep in sync (rename, delete, etc.); this is just an aggregate over
 * [RunEntity] rows sharing the same [shooterName]. See [RunDao.observeShooterStats]. */
data class ShooterStats(
    val shooterName: String,
    val runCount: Int,
    val bestTimeMillis: Long,
    /** SQLite's AVG() always returns a real number, even over integer columns. */
    val avgTimeMillis: Double
)

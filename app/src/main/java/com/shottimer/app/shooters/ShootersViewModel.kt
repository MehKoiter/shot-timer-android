package com.shottimer.app.shooters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shottimer.app.data.RunRepository
import com.shottimer.app.data.ShooterStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many recent runs feed each shooter's trend sparkline. */
const val TREND_RUN_COUNT = 10

class ShootersViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RunRepository(application)

    val stats: StateFlow<List<ShooterStats>> = repository.observeShooterStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Per shooter: total times of their last [TREND_RUN_COUNT] runs in chronological order,
     * feeding the trend sparkline on each card. */
    val recentTimes: StateFlow<Map<String, List<Long>>> = repository.observeRuns()
        .map { runs ->
            runs.filter { it.shooterName != null }
                .groupBy { it.shooterName!! }
                .mapValues { (_, shooterRuns) ->
                    shooterRuns.sortedBy { it.timestampEpochMillis }
                        .takeLast(TREND_RUN_COUNT)
                        .map { it.totalElapsedMillis }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Renaming onto an existing shooter's name merges the two - that's the typo fix. */
    fun renameShooter(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        viewModelScope.launch { repository.renameShooter(oldName, trimmed) }
    }

    /** Removes the shooter by untagging their runs; the runs survive as unassigned practice. */
    fun removeShooter(name: String) {
        viewModelScope.launch { repository.untagShooter(name) }
    }
}

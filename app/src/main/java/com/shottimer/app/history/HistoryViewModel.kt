package com.shottimer.app.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shottimer.app.data.RunEntity
import com.shottimer.app.data.RunRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RunRepository(application)

    val runs: StateFlow<List<RunEntity>> = repository.observeRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteRun(run: RunEntity) {
        viewModelScope.launch { repository.deleteRun(run) }
    }

    /** Undo for [deleteRun]: re-inserting the same entity (original id, remoteId and all)
     * restores the row exactly, so cloud sync doesn't see it as a brand-new run. */
    fun restoreRun(run: RunEntity) {
        viewModelScope.launch { repository.saveRun(run) }
    }
}

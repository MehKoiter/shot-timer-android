package com.shottimer.app.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shottimer.app.data.RunEntity
import com.shottimer.app.data.RunRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RunRepository(application)

    val runs: StateFlow<List<RunEntity>> = repository.observeRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

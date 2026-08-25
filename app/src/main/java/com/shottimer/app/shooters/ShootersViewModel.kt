package com.shottimer.app.shooters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shottimer.app.data.RunRepository
import com.shottimer.app.data.ShooterStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ShootersViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RunRepository(application)

    val stats: StateFlow<List<ShooterStats>> = repository.observeShooterStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

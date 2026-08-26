package com.shottimer.app.drills

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shottimer.app.data.CustomDrillEntity
import com.shottimer.app.data.ShotTimerDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val MIN_DRILL_ROUNDS = 1
const val MAX_DRILL_ROUNDS = 100

class DrillsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ShotTimerDatabase.getInstance(application).customDrillDao()

    val customDrills: StateFlow<List<CustomDrillEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Blank names are rejected; rounds are clamped to a sane range. Duplicate names (including
     * against the builtin library) are allowed - runs tag drills by name, so two drills sharing
     * a name simply share a history bucket, same as shooters. */
    fun addDrill(name: String, roundCount: Int, instructions: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            dao.insert(
                CustomDrillEntity(
                    name = trimmedName,
                    instructions = instructions.trim(),
                    roundCount = roundCount.coerceIn(MIN_DRILL_ROUNDS, MAX_DRILL_ROUNDS)
                )
            )
        }
    }

    /** Deleting a custom drill never touches recorded runs - they keep their name tag. */
    fun deleteDrill(drill: CustomDrillEntity) {
        viewModelScope.launch { dao.delete(drill) }
    }
}

/** Bridge into the shared [Drill] shape the Timer flow already understands. */
fun CustomDrillEntity.toDrill(): Drill = Drill(
    id = "custom_$id",
    name = name,
    summary = "",
    instructions = instructions,
    roundCount = roundCount
)

package com.shottimer.app.sync

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shottimer.app.auth.AuthRepository
import com.shottimer.app.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [BackupScreen]. Exposes [AuthRepository]'s and [SyncRepository]'s state directly (both
 * are already `StateFlow`s on shared singletons - no need to re-wrap them), plus
 * [signInError], which is deliberately separate from [SyncStatus.lastError]: that one describes
 * a failed *sync* (uploading/pulling runs while already signed in), this one describes a failed
 * *sign-in attempt* (e.g. Play Services unavailable, user cancelled the picker) - conflating the
 * two would show a stale sync error on the signed-out screen or vice versa.
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository.getInstance(application)
    private val syncRepository = SyncRepository.getInstance(application)

    val authState: StateFlow<AuthState> = authRepository.authState
    val syncStatus: StateFlow<SyncStatus> = syncRepository.syncStatus

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    fun signIn(activityContext: Context) {
        _signInError.value = null
        viewModelScope.launch {
            authRepository.signIn(activityContext).onFailure { e ->
                _signInError.value = e.message ?: "Sign-in failed: ${e::class.simpleName}"
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepository.signOut(context)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncRepository.syncNow()
        }
    }
}

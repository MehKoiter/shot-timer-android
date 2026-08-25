package com.shottimer.app.sync

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.shottimer.app.auth.AuthRepository
import com.shottimer.app.auth.AuthState
import com.shottimer.app.data.RunRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val PREFS_NAME = "shot_timer_sync"
private const val KEY_LAST_SYNCED_AT = "last_synced_at_epoch_millis"
private const val NO_VALUE = -1L

/**
 * v1 sync status: [isSyncing] and [lastError] are transient/in-memory-only (they reset on
 * process death, which is fine - they only describe "right now"), while
 * [lastSyncedAtEpochMillis] is seeded from disk at construction so "last synced: ..." survives
 * an app restart.
 */
data class SyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncedAtEpochMillis: Long? = null,
    val lastError: String? = null
)

/**
 * Uploads local runs to Firestore and pulls down remote ones not yet present locally. Must be a
 * singleton (via [getInstance], matching [AuthRepository]'s pattern): [syncStatus] is shared
 * mutable state every observer (a future Backup screen, etc.) needs to see consistently.
 *
 * v1 scope is deliberately narrow - upload-once (a local run uploads and gets a `remoteId`,
 * never re-uploaded or edited in place) plus pull-on-sign-in (fetch remote runs not yet present
 * locally). No delete propagation, no conflict resolution: a run deleted locally may still exist
 * in the cloud and could reappear on a future pull.
 */
class SyncRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = AuthRepository.getInstance(appContext)
    private val runRepository = RunRepository(appContext)
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Guards syncNow() so a manual "Sync now" tap can't race the auto-sync-on-sign-in below.
    private val mutex = Mutex()

    // Lives for the singleton's lifetime (tied to applicationContext), same as how
    // AuthRepository's AuthStateListener never gets torn down - intentional for an app-lifetime
    // singleton, not a leak.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow(SyncStatus(lastSyncedAtEpochMillis = loadLastSyncedAt()))
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        // Auto-sync whenever sign-in state transitions into SignedIn (fresh sign-in, or a
        // cached session resuming on app start) - not on every AuthState emission, since a
        // token refresh while already signed in shouldn't kick off a redundant sync.
        scope.launch {
            authRepository.authState
                .filterIsInstance<AuthState.SignedIn>()
                .distinctUntilChanged()
                .collect { syncNow() }
        }
    }

    private fun loadLastSyncedAt(): Long? =
        prefs.getLong(KEY_LAST_SYNCED_AT, NO_VALUE).takeIf { it != NO_VALUE }

    /**
     * Uploads every unsynced local run, then pulls any remote run not yet present locally.
     * Returns [Result.failure] immediately, without touching [syncStatus], if nobody is signed
     * in. Never throws - any failure partway through is caught and reported as
     * [Result.failure], with [SyncStatus.lastError] set to a UI-safe message, so a caller (a
     * future ViewModel) can show it and let the user retry rather than crash.
     *
     * Uploads mark each run's `remoteId` immediately after that run's own successful upload
     * (not batched at the end), so a failure partway through a large batch doesn't lose track of
     * what already succeeded - a retry only re-uploads what's still unsynced.
     */
    suspend fun syncNow(): Result<Unit> = mutex.withLock {
        val signedIn = authRepository.authState.value as? AuthState.SignedIn
            ?: return@withLock Result.failure(IllegalStateException("Not signed in"))

        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, lastError = null)
        try {
            val runsRef = firestore.collection("users").document(signedIn.uid).collection("runs")

            for (run in runRepository.getUnsyncedRuns()) {
                val doc = runsRef.document()
                doc.set(run.toFirestoreMap()).await()
                runRepository.markSynced(run.id, doc.id)
            }

            val syncedRemoteIds = runRepository.getSyncedRemoteIds().toSet()
            val remoteDocs = runsRef.get().await()
            for (doc in remoteDocs.documents) {
                if (doc.id in syncedRemoteIds) continue
                val run = doc.data?.toRunEntity(doc.id) ?: continue
                runRepository.saveRun(run)
            }

            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNCED_AT, now).apply()
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                lastSyncedAtEpochMillis = now,
                lastError = null
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            // Never swallow coroutine cancellation - rethrow so structured concurrency still
            // works (e.g. the calling ViewModel scope being cleared mid-sync).
            throw e
        } catch (e: Exception) {
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                lastError = e.message ?: "Sync failed: ${e::class.simpleName}"
            )
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var instance: SyncRepository? = null

        fun getInstance(context: Context): SyncRepository =
            instance ?: synchronized(this) {
                instance ?: SyncRepository(context.applicationContext).also { instance = it }
            }
    }
}

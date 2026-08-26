package com.shottimer.app.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.R
import com.shottimer.app.auth.AuthState
import com.shottimer.app.ui.ScreenScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER)

/** Reached from Settings, not its own bottom-nav tab - mirrors MicTestScreen's push-pop pattern
 * (a local `showX` boolean in the caller), so [onBack] is required. */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val signInError by viewModel.signInError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ScreenScaffold(title = stringResource(R.string.backup_title), modifier = modifier, onBack = onBack) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when (val state = authState) {
            is AuthState.SignedOut -> SignedOutContent(
                signInError = signInError,
                onSignIn = { viewModel.signIn(context) }
            )
            is AuthState.SignedIn -> SignedInContent(
                state = state,
                syncStatus = syncStatus,
                onSyncNow = viewModel::syncNow,
                onSignOut = { viewModel.signOut(context) }
            )
        }
    }
    }
}

@Composable
private fun SignedOutContent(signInError: String?, onSignIn: () -> Unit) {
    Text(
        text = stringResource(R.string.backup_explainer),
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onSignIn) { Text(stringResource(R.string.signin_google)) }

    if (signInError != null) {
        Spacer(Modifier.height(16.dp))
        Text(text = signInError, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SignedInContent(
    state: AuthState.SignedIn,
    syncStatus: SyncStatus,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit
) {
    Text(
        text = stringResource(R.string.signed_in_as, state.email ?: state.displayName ?: state.uid),
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.last_synced,
            syncStatus.lastSyncedAtEpochMillis?.let { formatTimestamp(it) } ?: stringResource(R.string.never)
        ),
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSyncNow, enabled = !syncStatus.isSyncing) { Text(stringResource(R.string.sync_now)) }
        if (syncStatus.isSyncing) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }

    if (syncStatus.lastError != null) {
        Spacer(Modifier.height(16.dp))
        Text(text = syncStatus.lastError, color = MaterialTheme.colorScheme.error)
    }

    Spacer(Modifier.height(24.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.cloud_delete_note),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }

    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
}

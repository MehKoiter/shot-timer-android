package com.shottimer.app.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.R
import com.shottimer.app.permission.OpenAppSettingsButton
import com.shottimer.app.permission.rememberMicPermissionState
import com.shottimer.app.ui.ScreenScaffold

/** Reached from Settings, not its own bottom-nav tab - mirrors HistoryScreen's list/detail
 * push-pop pattern (a local `showX` boolean in the caller), so [onBack] is required. */
@Composable
fun MicTestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MicTestViewModel = viewModel()
) {
    val micPermission = rememberMicPermissionState(onGranted = viewModel::startListening)

    val level by viewModel.level.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.mic_test), modifier = modifier, onBack = onBack) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LevelMeter(
            level = level,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        when {
            !micPermission.isGranted && micPermission.isPermanentlyDenied -> OpenAppSettingsButton()
            !micPermission.isGranted -> {
                Text(text = stringResource(R.string.mic_permission_required))
                Spacer(Modifier.height(8.dp))
                Button(onClick = micPermission.request) { Text(stringResource(R.string.grant_mic)) }
            }
            isListening -> Button(onClick = viewModel::stopListening) { Text(stringResource(R.string.stop_listening)) }
            else -> Button(onClick = viewModel::startListening) { Text(stringResource(R.string.start_listening)) }
        }
    }
    }
}

@Composable
private fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = level.coerceIn(0.01f, 1f))
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

package com.shottimer.app.audio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MicTestScreen(
    modifier: Modifier = Modifier,
    viewModel: MicTestViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.startListening()
    }

    val level by viewModel.level.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Mic Test", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))

        LevelMeter(
            level = level,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        Spacer(Modifier.height(24.dp))

        when {
            !hasPermission -> {
                Text(text = "Microphone permission is required to test capture.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant microphone permission")
                }
            }
            isListening -> Button(onClick = viewModel::stopListening) { Text("Stop listening") }
            else -> Button(onClick = viewModel::startListening) { Text("Start listening") }
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

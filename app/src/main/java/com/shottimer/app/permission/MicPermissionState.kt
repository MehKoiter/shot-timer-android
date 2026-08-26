package com.shottimer.app.permission

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.shottimer.app.R

data class MicPermissionState(
    val isGranted: Boolean,
    /** True once the user has denied and Android will no longer show the system prompt - the only way forward is app settings. */
    val isPermanentlyDenied: Boolean,
    val request: () -> Unit
)

/**
 * Shared RECORD_AUDIO permission flow for the Timer and Mic Test screens: request it, and if the
 * user has permanently denied it (denied once already, so the system won't show the dialog again),
 * expose that so the caller can offer a way to app settings instead of a button that silently does
 * nothing.
 */
@Composable
fun rememberMicPermissionState(onGranted: () -> Unit = {}): MicPermissionState {
    val context = LocalContext.current
    val activity = context as? Activity

    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        if (granted) {
            isPermanentlyDenied = false
            onGranted()
        } else if (activity != null) {
            isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    return MicPermissionState(
        isGranted = isGranted,
        isPermanentlyDenied = isPermanentlyDenied,
        request = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    )
}

@Composable
fun OpenAppSettingsButton(label: String? = null) {
    val context = LocalContext.current
    val buttonLabel = label ?: stringResource(R.string.open_app_settings_mic)
    Button(onClick = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }) {
        Text(buttonLabel)
    }
}

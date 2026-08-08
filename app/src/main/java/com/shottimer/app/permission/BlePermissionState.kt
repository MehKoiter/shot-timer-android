package com.shottimer.app.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

data class BlePermissionState(
    val isGranted: Boolean,
    /** True once at least one required permission can no longer show the system prompt - the only way forward is app settings. */
    val isPermanentlyDenied: Boolean,
    val request: () -> Unit
)

/** Android 12+ uses BLUETOOTH_SCAN/BLUETOOTH_CONNECT; earlier versions need ACCESS_FINE_LOCATION
 * to scan for BLE devices at all (BLUETOOTH/BLUETOOTH_ADMIN are normal, install-time permissions
 * pre-12, not requestable at runtime). See AndroidManifest.xml for both permission sets. */
private fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/** Same shape and reasoning as [rememberMicPermissionState] in this package, generalized to a
 * set of permissions instead of one - see that function's doc for why the permanently-denied
 * detection works the way it does. */
@Composable
fun rememberBlePermissionState(onGranted: () -> Unit = {}): BlePermissionState {
    val context = LocalContext.current
    val activity = context as? Activity
    val permissions = remember { requiredBlePermissions() }

    fun allGranted(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var isGranted by remember { mutableStateOf(allGranted()) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        isGranted = granted
        if (granted) {
            isPermanentlyDenied = false
            onGranted()
        } else if (activity != null) {
            val stillDenied = permissions.filter { results[it] != true }
            isPermanentlyDenied = stillDenied.any {
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
        }
    }

    return BlePermissionState(
        isGranted = isGranted,
        isPermanentlyDenied = isPermanentlyDenied,
        request = { launcher.launch(permissions) }
    )
}

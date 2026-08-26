package com.shottimer.app.feedback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.shottimer.app.BuildConfig
import kotlin.math.sqrt

/**
 * Listens for a phone-shake gesture and launches the Firebase App Distribution feedback UI
 * when one is detected, so testers can report a bug the moment they hit it.
 *
 * Entirely a no-op outside debug builds - the full App Distribution SDK
 * ([FirebaseAppDistribution]'s implementation) is only linked into debug builds (see
 * app/build.gradle.kts), and Google warns that shipping it in a Play Store release can get the
 * app pulled from Play. Gating here too means this class never even registers a sensor listener
 * in a release build, rather than relying solely on the debug-only dependency to make it a no-op.
 */
class ShakeFeedbackDetector(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager? =
        if (BuildConfig.DEBUG) context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager else null
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastTriggerUptimeMs = 0L

    fun start() {
        if (!BuildConfig.DEBUG) return
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (!BuildConfig.DEBUG) return
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Net force after subtracting gravity - isolates the "jolt" of a deliberate shake from
        // the phone just sitting there (or being held steady) under normal gravity alone.
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        val netForce = magnitude - SensorManager.GRAVITY_EARTH

        if (netForce <= SHAKE_THRESHOLD_M_S2) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerUptimeMs < COOLDOWN_MS) return
        lastTriggerUptimeMs = now

        FirebaseAppDistribution.getInstance().startFeedback(FEEDBACK_PROMPT)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        // Net (gravity-subtracted) acceleration, in m/s^2, that counts as a shake. Comfortably
        // above the jostle of normal handling/walking but well below a deliberate shake.
        const val SHAKE_THRESHOLD_M_S2 = 13f

        // Minimum time between triggers so a single shake gesture (which fires many sensor
        // events) doesn't launch the feedback UI more than once.
        const val COOLDOWN_MS = 1_500L

        const val FEEDBACK_PROMPT = "Shake detected - what's going on?"
    }
}

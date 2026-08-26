package com.shottimer.app.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val viewModel = SettingsViewModel(application)

    @Test
    fun `setDefaultSensitivity clamps to 0 to 1`() {
        viewModel.setDefaultSensitivity(1.5f)
        assertEquals(1f, viewModel.settings.value.defaultSensitivity)

        viewModel.setDefaultSensitivity(-0.5f)
        assertEquals(0f, viewModel.settings.value.defaultSensitivity)
    }

    @Test
    fun `setEchoLockoutMs clamps to the documented ms range`() {
        viewModel.setEchoLockoutMs(1L)
        assertEquals(MIN_ECHO_LOCKOUT_MS, viewModel.settings.value.echoLockoutMs)

        viewModel.setEchoLockoutMs(10_000L)
        assertEquals(MAX_ECHO_LOCKOUT_MS, viewModel.settings.value.echoLockoutMs)
    }

    @Test
    fun `setBeepVolume clamps to 0 to 1`() {
        viewModel.setBeepVolume(2f)
        assertEquals(1f, viewModel.settings.value.beepVolume)
    }

    @Test
    fun `setDelayRange clamps each bound to the documented range`() {
        viewModel.setDelayRange(minSeconds = 0.01f, maxSeconds = 999f)

        assertEquals(MIN_RANDOM_DELAY_SECONDS, viewModel.settings.value.minDelaySeconds)
        assertEquals(MAX_RANDOM_DELAY_SECONDS, viewModel.settings.value.maxDelaySeconds)
    }

    @Test
    fun `setDelayRange never lets min end up above max`() {
        // Each bound is coerced using the OTHER (pre-clamp) value as its limit, so an inverted
        // call (min above max) comes out swapped rather than collapsed to one value - still a
        // valid, non-inverted range either way.
        viewModel.setDelayRange(minSeconds = 5f, maxSeconds = 2f)

        val settings = viewModel.settings.value
        assertEquals(2f, settings.minDelaySeconds)
        assertEquals(5f, settings.maxDelaySeconds)
        assertTrue(settings.minDelaySeconds <= settings.maxDelaySeconds)
    }
}

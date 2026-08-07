"""Recognizes the Pi's own start/par tone in a window of audio.

There is no Kotlin equivalent to port here - this problem doesn't exist on the Android side,
because the phone's beep comes out of the same AudioTrack pipeline the app itself owns, right
next to the phone's own mic, and the existing app has never needed to tell its own beep apart
from a shot. The Pi is different: the beep comes out of dedicated buzzer/speaker hardware
sitting physically close to the I2S mic, so run_controller.py needs a way to tell "that was
our own start tone" apart from "that was a shot" when both could otherwise cross the same
amplitude threshold in shot_detector.py.

Approach: a gunshot is a broadband transient - its energy is spread across many frequencies
at once. Our own beep is a single sustained sine tone at a known frequency (1800Hz for the
start beep, 1100Hz for the par beep - see beep.py). An FFT of a windowed chunk of samples
will show energy concentrated in the bin(s) nearest the beep's frequency for a beep, and
comparatively spread out for a shot (or for background noise). is_own_tone() checks what
fraction of a window's total spectral energy falls in a small band around the target
frequency, and calls it "the beep" if that fraction clears a threshold.

Pure numpy - no audio hardware dependency - and fully unit tested against synthetic fixtures
in tests/test_beep_detector.py.
"""

import numpy as np

# How much of a window's total FFT energy must sit in the band around target_freq_hz for the
# window to be classified as "the beep" rather than a broadband transient (a shot) or
# background noise. Picked empirically against the synthetic pure-tone vs. white-noise/
# impulse fixtures in tests/test_beep_detector.py: a pure sine tone concentrates the
# overwhelming majority (>90%) of its energy in one narrow band, a broadband impulse or noise
# puts well under 5% there, so there's a wide margin on either side of 0.5 - unlike the shot
# amplitude threshold, this isn't expected to need per-installation tuning.
DEFAULT_ENERGY_RATIO_THRESHOLD = 0.5

# Bins within this many Hz of the target frequency count as "at the target frequency" - keeps
# the check robust to target_freq_hz not landing exactly on an FFT bin center, and to the
# beep's own fade envelope (see beep.py) spreading its energy slightly.
DEFAULT_BAND_HALF_WIDTH_HZ = 100.0


def target_frequency_energy_ratio(
    samples,
    sample_rate_hz: int,
    target_freq_hz: float,
    band_half_width_hz: float = DEFAULT_BAND_HALF_WIDTH_HZ,
) -> float:
    """Returns the fraction (0.0-1.0) of a window's FFT energy that falls within
    target_freq_hz +/- band_half_width_hz. Silence or an empty window returns 0.0 rather than
    dividing by zero.
    """
    samples_arr = np.asarray(samples, dtype=np.float64)
    if samples_arr.size == 0:
        return 0.0

    # Hann window to reduce spectral leakage from the edges of an arbitrary-length chunk -
    # without it, a pure tone that doesn't complete a whole number of cycles within the
    # window smears energy across neighboring bins and can under-report its own ratio.
    windowed = samples_arr * np.hanning(samples_arr.size)

    spectrum = np.fft.rfft(windowed)
    power = np.abs(spectrum) ** 2
    freqs = np.fft.rfftfreq(samples_arr.size, d=1.0 / sample_rate_hz)

    total_energy = power.sum()
    if total_energy <= 0.0:
        return 0.0

    in_band = (freqs >= target_freq_hz - band_half_width_hz) & (freqs <= target_freq_hz + band_half_width_hz)
    band_energy = power[in_band].sum()

    return float(band_energy / total_energy)


def is_own_tone(
    samples,
    sample_rate_hz: int,
    target_freq_hz: float,
    energy_ratio_threshold: float = DEFAULT_ENERGY_RATIO_THRESHOLD,
    band_half_width_hz: float = DEFAULT_BAND_HALF_WIDTH_HZ,
) -> bool:
    """True if this window of samples looks like our own beep tone at target_freq_hz rather
    than a broadband transient (a shot) or background noise. See module docstring for the
    approach; see run_controller.py for how/when this gets called relative to
    shot_detector.ShotDetector.process().
    """
    ratio = target_frequency_energy_ratio(samples, sample_rate_hz, target_freq_hz, band_half_width_hz)
    return ratio >= energy_ratio_threshold

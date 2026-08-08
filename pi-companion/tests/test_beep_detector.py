"""Synthetic sine-wave fixtures at, near, and away from the beep frequency, plus broadband
noise/impulse fixtures standing in for a gunshot's transient character. No hardware involved
- pure numpy synthesis in, a bool/float out.

No Kotlin original to port (see beep_detector.py's module docstring for why this logic only
exists on the Pi side).
"""

import numpy as np

from shot_timer_pi.beep import (
    BEEP_SAMPLE_RATE_HZ,
    PAR_BEEP_FREQUENCY_HZ,
    START_BEEP_FREQUENCY_HZ,
    build_beep_samples,
)
from shot_timer_pi.beep_detector import is_own_tone, target_frequency_energy_ratio

SAMPLE_RATE_HZ = 44100
WINDOW_SIZE = 4096  # ~93ms at 44100Hz - long enough for a few Hz of frequency resolution.


def _pure_tone(frequency_hz: float, sample_rate_hz: int = SAMPLE_RATE_HZ, num_samples: int = WINDOW_SIZE, amplitude: float = 0.8):
    i = np.arange(num_samples)
    return amplitude * np.sin(2.0 * np.pi * i * frequency_hz / sample_rate_hz)


def _white_noise(num_samples: int = WINDOW_SIZE, amplitude: float = 0.8, seed: int = 0):
    rng = np.random.default_rng(seed)
    return rng.uniform(-amplitude, amplitude, size=num_samples)


def _impulse(num_samples: int = WINDOW_SIZE, amplitude: float = 1.0):
    """A single-sample spike surrounded by silence: the extreme case of a broadband
    transient, standing in for a gunshot's sharp onset (energy spread across effectively all
    frequencies at once, rather than concentrated at one)."""
    samples = np.zeros(num_samples)
    samples[num_samples // 2] = amplitude
    return samples


def test_pure_tone_at_target_frequency_is_recognized():
    tone = _pure_tone(START_BEEP_FREQUENCY_HZ)
    ratio = target_frequency_energy_ratio(tone, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)
    assert ratio > 0.9
    assert is_own_tone(tone, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_pure_tone_far_from_target_frequency_is_not_recognized():
    # 400Hz is nowhere near the 1800Hz start beep.
    tone = _pure_tone(400.0)
    ratio = target_frequency_energy_ratio(tone, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)
    assert ratio < 0.1
    assert not is_own_tone(tone, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_par_tone_is_not_recognized_as_the_start_tone():
    # A real tone our own hardware emits (see beep.py), just not the one being checked for -
    # confirms the check is frequency-specific, not "any pure tone whatsoever."
    tone = _pure_tone(PAR_BEEP_FREQUENCY_HZ)
    assert not is_own_tone(tone, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_frequency_just_inside_the_band_is_still_recognized():
    # Default band half-width is 100Hz; 50Hz off-target should still count as "the beep."
    tone = _pure_tone(START_BEEP_FREQUENCY_HZ + 50.0)
    assert is_own_tone(tone, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_frequency_well_outside_the_band_is_not_recognized():
    # 1800Hz + 500Hz is well past the default 100Hz band half-width.
    tone = _pure_tone(START_BEEP_FREQUENCY_HZ + 500.0)
    assert not is_own_tone(tone, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_white_noise_is_not_recognized():
    noise = _white_noise()
    ratio = target_frequency_energy_ratio(noise, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)
    assert ratio < 0.1
    assert not is_own_tone(noise, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_impulse_is_not_recognized():
    # A gunshot's defining acoustic feature (vs. our own tone) is a sharp broadband
    # transient, not sustained energy at one frequency - a single-sample spike is the extreme
    # version of that.
    impulse = _impulse()
    assert not is_own_tone(impulse, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_silence_has_zero_energy_ratio():
    silence = np.zeros(WINDOW_SIZE)
    assert target_frequency_energy_ratio(silence, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ) == 0.0
    assert not is_own_tone(silence, SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_empty_input_has_zero_energy_ratio():
    assert target_frequency_energy_ratio(np.array([]), SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ) == 0.0


def test_actual_start_beep_samples_are_recognized_as_the_start_tone():
    # Integration-style check tying beep.py's real synthesis to beep_detector.py's
    # recognition: the exact samples build_beep_samples() would hand to the speaker should be
    # recognized as "our own tone" when checked against the frequency it was built with. This
    # is also the only test coverage build_beep_samples() gets, since play_tone() (the only
    # other consumer) needs real audio hardware.
    samples = build_beep_samples(START_BEEP_FREQUENCY_HZ, sample_rate_hz=BEEP_SAMPLE_RATE_HZ)
    assert is_own_tone(samples, BEEP_SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)


def test_actual_par_beep_samples_are_not_recognized_as_the_start_tone():
    samples = build_beep_samples(PAR_BEEP_FREQUENCY_HZ, sample_rate_hz=BEEP_SAMPLE_RATE_HZ)
    assert not is_own_tone(samples, BEEP_SAMPLE_RATE_HZ, START_BEEP_FREQUENCY_HZ)

"""Start/par tone synthesis and playback.

Ported from buildBeepSamples()/playTone() in
app/src/main/java/com/shottimer/app/timer/ShotTimerViewModel.kt: synthesizes a faded sine
wave (not a raw square wave) so the tone doesn't click/pop at its start and end, using the
same symmetric linear fade-in/fade-out envelope as the Kotlin original - just built with
numpy instead of a ShortArray initializer lambda.

build_beep_samples() is pure numpy (no audio hardware touched) and is exercised by
tests/test_beep_detector.py, which feeds its output back through beep_detector.is_own_tone()
as a "does this synthesize the tone we say it does" sanity check. play_tone() opens a real
ALSA output stream via sounddevice and needs a real audio device - it is NOT exercised by
pytest (see README's test-coverage table). sounddevice is imported lazily inside play_tone()
rather than at module scope specifically so importing this module for build_beep_samples()
never requires sounddevice (or a working audio backend) to be installed.
"""

import numpy as np

BEEP_SAMPLE_RATE_HZ = 44100
BEEP_DURATION_MS = 150
START_BEEP_FREQUENCY_HZ = 1800.0
# Lower pitch than the start beep so a dry-fire drill can tell "go" and "par" apart by ear
# alone - matches PAR_BEEP_FREQUENCY_HZ in ShotTimerViewModel.kt.
PAR_BEEP_FREQUENCY_HZ = 1100.0
BEEP_FADE_MS = 5

# int16 PCM full-scale magnitude, mirrors Kotlin's Short.MAX_VALUE.
INT16_MAX = 32767


def build_beep_samples(
    frequency_hz: float,
    sample_rate_hz: int = BEEP_SAMPLE_RATE_HZ,
    duration_ms: int = BEEP_DURATION_MS,
    fade_ms: int = BEEP_FADE_MS,
) -> np.ndarray:
    """Returns int16 PCM samples for a fade-in/fade-out sine tone at frequency_hz.

    Same envelope shape as the Kotlin original: a linear ramp up over the first fade_samples
    samples, full volume through the middle, a linear ramp down over the last fade_samples -
    implemented as a sample-by-sample min() of the "ramping up" and "ramping down" lines,
    which is what makes it symmetric without needing a separate branch for each third of the
    tone.
    """
    num_samples = sample_rate_hz * duration_ms // 1000
    fade_samples = sample_rate_hz * fade_ms // 1000

    i = np.arange(num_samples)
    angle = 2.0 * np.pi * i * frequency_hz / sample_rate_hz

    fade_in = np.minimum(i, fade_samples) / fade_samples
    fade_out = np.minimum(num_samples - i, fade_samples) / fade_samples
    envelope = np.minimum(fade_in, fade_out)

    return (np.sin(angle) * envelope * INT16_MAX).astype(np.int16)


def play_tone(samples: np.ndarray, volume: float = 1.0, sample_rate_hz: int = BEEP_SAMPLE_RATE_HZ) -> None:
    """Plays samples through the Pi's default ALSA output device (buzzer amp / 3.5mm jack /
    USB audio - whatever `aplay -L`'s default resolves to; see README for wiring).

    Triggers playback and returns immediately (does not block until the tone finishes) -
    deliberately mirroring the Kotlin original's fire-and-forget AudioTrack.play(), since
    run_controller.py marks the run's start timestamp right after calling this, not after
    playback completes (see ShotTimerViewModel.start(): startMarkNanos is set immediately
    after playTone() is called, while the beep is still sounding).

    Requires a real audio output device - not exercised by pytest.
    """
    import sounddevice as sd  # lazy import - see module docstring.

    volume = min(max(volume, 0.0), 1.0)
    scaled = samples.astype(np.float32) * (volume / INT16_MAX)
    sd.play(scaled, samplerate=sample_rate_hz, blocking=False)

"""Pure sample-domain threshold shot detector.

Ported from app/src/main/java/com/shottimer/app/detection/ShotDetector.kt - same algorithm,
same shape of config (amplitude threshold, echo-lockout window), Python instead of Kotlin.
No hardware/audio-library dependency at all (not even numpy), specifically so this module -
and its tests in tests/test_shot_detector.py - never need real audio hardware to run.

Deliberately dumb, matching the Kotlin original: a single amplitude threshold plus an
echo-lockout window, matching how real shot timers describe their own detection (see
docs/DESIGN.md in the repo root). Sensitivity tuning happens by adjusting
threshold_amplitude, not by adding smarter signal processing.
"""

from dataclasses import dataclass
from typing import List, Sequence

# Matches AUDIO_SAMPLE_RATE_HZ in the Android app's AudioSource.kt. audio_source.py passes
# its own real capture rate explicitly at construction time - this is just the fallback
# default, the same role it plays on the Kotlin side.
DEFAULT_SAMPLE_RATE_HZ = 44100

DEFAULT_THRESHOLD_AMPLITUDE = 0.35
DEFAULT_ECHO_LOCKOUT_MS = 100

# int16 PCM full-scale magnitude, mirrors Kotlin's Short.MAX_VALUE.
INT16_MAX = 32767

# Kotlin's lastEventNanos starts at Long.MIN_VALUE / 2 - "so far in the past that nothing
# could ever fall inside the lockout window of it." Long.MIN_VALUE / 2 is exactly -(2**62)
# (Long.MIN_VALUE is -2**63 and divides evenly), reproduced here as the same literal value
# rather than just "a very negative number", even though Python ints don't need the headroom
# a 64-bit language does.
_NEVER_NS = -(2**62)


@dataclass(frozen=True)
class AudioChunk:
    """One buffer of PCM samples plus the timestamp of the moment capture of that buffer
    completed - mirrors AudioChunk in AudioSource.kt. capture_end_ns should be on the same
    monotonic clock the caller wants shot timestamps expressed on (audio_source.py uses
    time.monotonic_ns(), the Python analogue of Kotlin's SystemClock.elapsedRealtimeNanos()).

    Defined here rather than in audio_source.py - which is where its Kotlin counterpart
    AudioChunk actually lives, alongside the AudioRecord wrapper - specifically so this
    (pure-logic, hardware-free) module never has to import audio_source.py, which imports
    sounddevice at module scope and therefore requires real audio hardware to even import.
    audio_source.py imports AudioChunk from here instead, which is the reverse of the
    Kotlin package direction but avoids giving the pure detector an accidental hardware
    dependency.
    """

    samples: Sequence[int]
    capture_end_ns: int


@dataclass(frozen=True)
class ShotEvent:
    """A detected shot, timestamped on whatever monotonic clock the AudioChunks it was
    computed from used (see AudioChunk's docstring)."""

    timestamp_ns: int


class ShotDetector:
    def __init__(
        self,
        threshold_amplitude: float = DEFAULT_THRESHOLD_AMPLITUDE,
        lockout_ns: int = DEFAULT_ECHO_LOCKOUT_MS * 1_000_000,
        sample_rate_hz: int = DEFAULT_SAMPLE_RATE_HZ,
    ) -> None:
        self._threshold_amplitude = threshold_amplitude
        self._lockout_ns = lockout_ns
        self._sample_rate_hz = sample_rate_hz
        self._last_event_ns = _NEVER_NS

    def process(self, chunk: AudioChunk) -> List[ShotEvent]:
        """Feeds one chunk through the detector; returns any shots found in it (usually 0 or
        1)."""
        samples = chunk.samples
        if len(samples) == 0:
            return []

        nanos_per_sample = 1_000_000_000 // self._sample_rate_hz
        chunk_start_ns = chunk.capture_end_ns - len(samples) * nanos_per_sample

        events: List[ShotEvent] = []
        for i, sample in enumerate(samples):
            amplitude = abs(sample) / INT16_MAX
            if amplitude < self._threshold_amplitude:
                continue

            sample_time_ns = chunk_start_ns + i * nanos_per_sample
            if sample_time_ns - self._last_event_ns < self._lockout_ns:
                continue

            events.append(ShotEvent(sample_time_ns))
            self._last_event_ns = sample_time_ns

        return events

    def reset(self) -> None:
        """Call at the start of each run so a previous run's lockout state can't suppress
        shot #1."""
        self._last_event_ns = _NEVER_NS

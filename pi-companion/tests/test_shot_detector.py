"""Ported from app/src/test/java/com/shottimer/app/detection/ShotDetectorTest.kt - same test
cases, same structure, same comments explaining the timing math, translated to pytest.

sample_rate_hz is set to 1000 (1ms/sample) in these tests purely so expected timestamps are
easy to compute by hand - the real Pi config uses a much higher rate (see
shot_timer_pi.audio_source.SAMPLE_RATE_HZ), but the detector's math is sample-rate-agnostic.
"""

from typing import Iterable

from shot_timer_pi.shot_detector import AudioChunk, ShotDetector, ShotEvent

TEST_SAMPLE_RATE_HZ = 1000
TEST_LOCKOUT_MS = 10
TEST_THRESHOLD = 0.5

INT16_MAX = 32767


def silent_chunk(sample_count: int, capture_end_ns: int, spikes_at_indices: Iterable[int] = ()) -> AudioChunk:
    spikes = set(spikes_at_indices)
    samples = [INT16_MAX if i in spikes else 0 for i in range(sample_count)]
    return AudioChunk(samples, capture_end_ns)


def detector(lockout_ms: int = TEST_LOCKOUT_MS) -> ShotDetector:
    return ShotDetector(
        threshold_amplitude=TEST_THRESHOLD,
        lockout_ns=lockout_ms * 1_000_000,
        sample_rate_hz=TEST_SAMPLE_RATE_HZ,
    )


def test_silence_produces_no_events():
    chunk = silent_chunk(sample_count=10, capture_end_ns=10_000_000)
    assert detector().process(chunk) == []


def test_single_spike_is_timestamped_at_its_exact_sample_position():
    # 10 samples at 1ms each, chunk covers [0ms, 10ms), spike at index 5 -> 5ms.
    chunk = silent_chunk(sample_count=10, capture_end_ns=10_000_000, spikes_at_indices=[5])
    events = detector().process(chunk)
    assert events == [ShotEvent(5_000_000)]


def test_quiet_samples_below_threshold_are_ignored():
    samples = [int(INT16_MAX * 0.1)] * 10  # amplitude 0.1 < threshold 0.5
    chunk = AudioChunk(samples, capture_end_ns=10_000_000)
    assert detector().process(chunk) == []


def test_second_spike_within_the_lockout_window_is_suppressed():
    # Spikes 1ms apart; lockout is 10ms, so only the first should register.
    chunk = silent_chunk(sample_count=10, capture_end_ns=10_000_000, spikes_at_indices=[5, 6])
    events = detector().process(chunk)
    assert events == [ShotEvent(5_000_000)]


def test_second_spike_past_the_lockout_window_registers_as_a_new_shot():
    # Spikes 20ms apart with a 10ms lockout - both are real shots.
    chunk = silent_chunk(sample_count=30, capture_end_ns=30_000_000, spikes_at_indices=[5, 25])
    events = detector().process(chunk)
    assert events == [ShotEvent(5_000_000), ShotEvent(25_000_000)]


def test_lockout_persists_across_chunk_boundaries():
    d = detector()

    # Chunk 1 covers [0ms, 10ms); spike at index 9 -> 9ms.
    chunk1 = silent_chunk(sample_count=10, capture_end_ns=10_000_000, spikes_at_indices=[9])
    assert d.process(chunk1) == [ShotEvent(9_000_000)]

    # Chunk 2 covers [10ms, 20ms); spike at index 0 -> 10ms, only 1ms after the last event -
    # still inside the 10ms lockout, so it must be suppressed even though it's a new chunk.
    chunk2 = silent_chunk(sample_count=10, capture_end_ns=20_000_000, spikes_at_indices=[0])
    assert d.process(chunk2) == []

    # A later spike in the same chunk, well past the lockout from the chunk1 shot, registers.
    chunk2_with_late_spike = silent_chunk(sample_count=10, capture_end_ns=20_000_000, spikes_at_indices=[9])
    assert d.process(chunk2_with_late_spike) == [ShotEvent(19_000_000)]


def test_reset_clears_lockout_state_for_a_new_run():
    d = detector()
    chunk = silent_chunk(sample_count=10, capture_end_ns=10_000_000, spikes_at_indices=[5])

    assert d.process(chunk) == [ShotEvent(5_000_000)]
    # Without reset, replaying the exact same chunk would be suppressed (0ns since the last event).
    assert d.process(chunk) == []

    d.reset()
    assert d.process(chunk) == [ShotEvent(5_000_000)]

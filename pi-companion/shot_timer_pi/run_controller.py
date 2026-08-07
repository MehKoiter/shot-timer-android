"""Orchestrates one full run: random delay -> beep -> mark start -> detect shots (with a
short beep-recognition guard right after the beep) -> save to local storage - firing
callbacks along the way so ble_service.py can turn them into BLE notifications.

Mirrors the sequencing in ShotTimerViewModel.start()
(app/src/main/java/com/shottimer/app/timer/ShotTimerViewModel.kt): random pre-beep delay,
play the tone, mark the start instant immediately (not after the tone finishes playing), then
detect shots against that start mark. On Android, "mark the start instant" also kicks off a
second coroutine (runClock()) ticking a live UI clock every 10ms; the Pi has no screen to
tick a clock for, so that role collapses here to plain timestamp arithmetic instead of a
periodic background task - see _run()'s use of time.monotonic_ns() at each detected shot
rather than a ticking loop. What Android's start()/stop() pair does with two separate user
actions doesn't map directly either - see "Run-end behavior" below.

Both the physical Start button (button.py) and a BLE "ARM" command (ble_service.py) call the
exact same RunController.start() - one code path, two triggers, matching the "Offline runs"
decision in the project handoff doc (docs/PI_COMPANION.md). Neither trigger does anything
run-specific beyond calling start(); all of the actual sequencing lives here.

Run-end behavior (a necessary design decision the handoff doc left open, not a resolved
spec): the BLE protocol as specified only defines an "ARM" command, no "STOP", and the
button/ARM triggers are required to both funnel into the *same* start-a-run function - so
neither can double as an explicit stop signal without that meaning something different for
each trigger. Given that, a run has to end on its own. RunConfig.max_run_seconds is a hard
ceiling from the beep; RunConfig.silence_timeout_seconds ends it earlier once nothing's been
detected for that long since the beep or the most recent shot, whichever is later. Both are
untuned placeholders (like MIN_THRESHOLD_AMPLITUDE/MAX_THRESHOLD_AMPLITUDE's own
"placeholder... may need to shift" comment in ShotTimerViewModel.kt) - pick real values once
this has been run against an actual course of fire. A future version could add an explicit
BLE "STOP" command instead; deliberately not building that speculatively for v1.

NOT unit tested: this module directly drives beep.py's real playback and audio_source.py's
real mic capture, both of which need real hardware (see README's test-coverage table). It's
built entirely out of shot_detector.py/beep_detector.py/storage.py's tested, pure-logic APIs
plus straightforward sequencing on top, but the sequencing itself is only reviewed by
inspection - do not treat it as verified until it's actually run on a Pi.
"""

import logging
import random
import threading
import time
from dataclasses import dataclass
from typing import Callable, List, Optional

from . import audio_source, beep
from .beep_detector import is_own_tone
from .shot_detector import ShotDetector
from .storage import RunRecord, RunStorage

logger = logging.getLogger(__name__)


class RunState:
    """Deliberately plain string constants rather than an enum - the only consumer that
    cares about run state is this module and (read-only) ble_service.py, and there's no
    Android-style STOPPED state to represent here (see module docstring's "Run-end
    behavior"): a finished run goes straight back to IDLE once it's saved."""

    IDLE = "idle"
    ARMED_WAITING = "armed_waiting"
    RUNNING = "running"


@dataclass
class RunConfig:
    """Tunable knobs, defaulted to match TimerSettings.kt
    (app/src/main/java/com/shottimer/app/settings/TimerSettings.kt) so the Pi behaves the
    same way out of the box as the phone app does. max_run_seconds and
    silence_timeout_seconds have no Android equivalent - see module docstring."""

    threshold_amplitude: float = 0.35  # DEFAULT_THRESHOLD_AMPLITUDE in ShotDetector.kt
    echo_lockout_ms: int = 100  # DEFAULT_ECHO_LOCKOUT_MS in ShotDetector.kt
    min_delay_seconds: float = 1.0
    max_delay_seconds: float = 3.5
    beep_volume: float = 1.0

    max_run_seconds: float = 30.0
    silence_timeout_seconds: float = 5.0


@dataclass
class RunResult:
    total_ms: int
    shots_ms: List[int]


# How long after the beep starts playing to keep checking whether a threshold-crossing chunk
# is actually just the beep's own acoustic leakage into the mic (see beep_detector.py) rather
# than a shot. BEEP_DURATION_MS + BEEP_FADE_MS is the tone itself; the extra 100ms is slack
# for room echo/decay tail, not a measured value.
_BEEP_GUARD_SECONDS = (beep.BEEP_DURATION_MS + beep.BEEP_FADE_MS + 100) / 1000.0


class RunController:
    def __init__(self, storage: RunStorage, config: Optional[RunConfig] = None) -> None:
        self._storage = storage
        self._config = config or RunConfig()

        # Public on purpose - ble_service.py assigns these directly after constructing both
        # objects (there's an unavoidable two-way wiring need: RunController needs to call
        # into the BLE layer to notify, the BLE layer needs a RunController to call start()
        # on - see main.py for the construction order this implies). Any/all may be left
        # None, e.g. button.py-only usage with no BLE layer running.
        self.on_beep: Optional[Callable[[], None]] = None
        self.on_shot: Optional[Callable[[int], None]] = None
        self.on_run_complete: Optional[Callable[[RunResult, int], None]] = None

        self._state = RunState.IDLE
        self._state_lock = threading.Lock()
        self._run_thread: Optional[threading.Thread] = None

    @property
    def state(self) -> str:
        return self._state

    def start(self) -> bool:
        """Arms and runs a new course of fire on a background thread; returns immediately.
        Returns False (no-op) if a run is already armed or in progress - mirrors
        ShotTimerViewModel.start()'s own early-return guard. Both button.py's when_pressed
        callback and ble_service.py's Command write_callback call this directly; neither
        implements any run logic of its own (see module docstring).
        """
        with self._state_lock:
            if self._state != RunState.IDLE:
                logger.info("start() ignored - run already in progress (state=%s)", self._state)
                return False
            self._state = RunState.ARMED_WAITING

        self._run_thread = threading.Thread(target=self._run, daemon=True)
        self._run_thread.start()
        return True

    def _run(self) -> None:
        cfg = self._config
        try:
            delay_seconds = random.uniform(cfg.min_delay_seconds, cfg.max_delay_seconds)
            time.sleep(delay_seconds)

            start_beep_samples = beep.build_beep_samples(beep.START_BEEP_FREQUENCY_HZ)
            beep.play_tone(start_beep_samples, cfg.beep_volume)
            # Marked immediately after triggering playback, not after it finishes - see
            # beep.play_tone()'s docstring, mirroring ShotTimerViewModel.start() exactly.
            start_mark_ns = time.monotonic_ns()
            self._state = RunState.RUNNING
            if self.on_beep:
                self.on_beep()

            shots_ms = self._detect_shots(start_mark_ns)

            total_ms = (time.monotonic_ns() - start_mark_ns) // 1_000_000
            result = RunResult(total_ms=total_ms, shots_ms=shots_ms)

            row_id = self._storage.save_run(
                RunRecord(
                    timestamp_epoch_millis=int(time.time() * 1000),
                    total_elapsed_millis=result.total_ms,
                    shot_timestamps_millis=result.shots_ms,
                )
            )

            if self.on_run_complete:
                self.on_run_complete(result, row_id)
        except Exception:
            # Mirrors ShotTimerViewModel.detectShots()'s catch around audioSource.chunks()
            # (mic busy/unavailable) - surface it via logging instead of crashing whatever
            # thread called start(). Unlike the Kotlin version there's no UI to show a
            # micErrorMessage on; a future version could push an error Event over BLE instead.
            logger.exception("Run failed")
        finally:
            self._state = RunState.IDLE

    def _detect_shots(self, start_mark_ns: int) -> List[int]:
        cfg = self._config
        detector = ShotDetector(
            threshold_amplitude=cfg.threshold_amplitude,
            lockout_ns=cfg.echo_lockout_ms * 1_000_000,
        )

        shots_ms: List[int] = []
        last_event_monotonic = time.monotonic()
        run_deadline = last_event_monotonic + cfg.max_run_seconds

        chunk_stream = audio_source.chunks(sample_rate_hz=audio_source.SAMPLE_RATE_HZ)
        try:
            for chunk in chunk_stream:
                now = time.monotonic()
                if now >= run_deadline or now - last_event_monotonic >= cfg.silence_timeout_seconds:
                    break

                chunk_age_seconds = (chunk.capture_end_ns - start_mark_ns) / 1_000_000_000
                if chunk_age_seconds <= _BEEP_GUARD_SECONDS and is_own_tone(
                    chunk.samples, audio_source.SAMPLE_RATE_HZ, beep.START_BEEP_FREQUENCY_HZ
                ):
                    # Still within the window where the beep could physically be sounding,
                    # and this chunk's spectral content says it's the tone, not a shot - skip
                    # detection for it entirely (rather than detect-then-discard) so the
                    # detector's echo-lockout state is never touched by our own beep. See
                    # beep_detector.py and the module docstring's discussion of this guard.
                    continue

                for event in detector.process(chunk):
                    elapsed_ms = (event.timestamp_ns - start_mark_ns) // 1_000_000
                    shots_ms.append(elapsed_ms)
                    last_event_monotonic = now
                    if self.on_shot:
                        self.on_shot(elapsed_ms)
        finally:
            chunk_stream.close()

        return shots_ms

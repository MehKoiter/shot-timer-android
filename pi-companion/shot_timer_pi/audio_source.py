"""sounddevice-based microphone capture, yielding a stream of AudioChunks.

Mirrors the shape of AudioSource.kt's chunks() Flow: each chunk is a buffer of PCM samples
plus the timestamp of the moment capture of that buffer completed - see AudioChunk's
docstring in shot_detector.py (which is where AudioChunk actually lives, and why).

Requires a real ALSA capture device - the I2S mic wired up per README.md, with its overlay
enabled in /boot/firmware/config.txt (also see README.md for the SPH0645's known bit-
alignment quirk and the googlevoicehat-soundcard fallback). There is no software fallback,
and this module is NOT exercised by pytest (see README's test-coverage table) - sounddevice
needs a real PortAudio-visible input device to do anything, which doesn't exist in a
hardware-free build/test environment.

Written against sounddevice's documented blocking-read InputStream API
(https://python-sounddevice.readthedocs.io/): `InputStream(...).read(frames)` returns
`(data, overflowed)`, mirroring AudioRecord.read()'s blocking-call shape closely enough that
this module's control flow reads almost identically to AudioSource.kt's.
"""

import time
from typing import Iterator, Optional, Union

import sounddevice as sd

from .shot_detector import AudioChunk

SAMPLE_RATE_HZ = 44100
CHANNELS = 1

# ~46ms per chunk at 44100Hz - small enough for reasonably fine-grained shot timestamps (a
# whole chunk is too coarse a unit on its own, same reasoning as AudioChunk's docstring),
# large enough not to spend all its time in Python-level call/callback overhead on a Pi 3B.
# Untuned - a real starting point, not a measured-good value; revisit once this runs on
# actual hardware.
BLOCK_SIZE_FRAMES = 2048


def chunks(
    device: Optional[Union[int, str]] = None,
    sample_rate_hz: int = SAMPLE_RATE_HZ,
    block_size_frames: int = BLOCK_SIZE_FRAMES,
) -> Iterator[AudioChunk]:
    """Yields AudioChunks forever from the default (or given) input device.

    Blocks on each iteration until a full block is available, same as AudioRecord.read() in
    AudioSource.kt - so run this from a dedicated thread (run_controller.py does), not
    directly on a thread that also needs to stay responsive to something else (BLE writes,
    the button, etc).

    This is a generator, and the caller is responsible for calling .close() on it (not just
    letting a `break` out of a `for` loop end iteration) if it needs the underlying
    InputStream shut down deterministically rather than whenever the generator is next
    garbage collected - see run_controller.py's usage for the pattern.
    """
    with sd.InputStream(
        device=device,
        channels=CHANNELS,
        samplerate=sample_rate_hz,
        dtype="int16",
        blocksize=block_size_frames,
    ) as stream:
        while True:
            data, _overflowed = stream.read(block_size_frames)
            # Python analogue of SystemClock.elapsedRealtimeNanos() in AudioSource.kt: a
            # monotonic clock, immune to wall-clock adjustments, which is all shot_detector.py
            # and run_controller.py need (they only ever compare two of these to each other,
            # never treat one as an absolute wall-clock time).
            capture_end_ns = time.monotonic_ns()
            # data has shape (frames, channels); flatten mono to 1-D, mirroring AudioRecord's
            # flat ShortArray.
            samples = data[:, 0]
            yield AudioChunk(samples, capture_end_ns)

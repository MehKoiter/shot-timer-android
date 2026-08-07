# Shot Timer Pi Companion

A Raspberry Pi program that acts as a physically dedicated, better-controlled version of the
Android app's start-beep + shot-detection logic, talking to the phone over Bluetooth Low
Energy (BLE). The Pi owns the beep, the random delay, and shot detection entirely on its own
clock; the phone is just a receiver/display/logger. See
[`docs/PI_COMPANION.md`](../docs/PI_COMPANION.md) in the repo root for the full design
rationale (why this avoids needing clock sync between the two devices, the finalized
hardware list, and what's deliberately out of scope).

This is a separate Python project living alongside the Android app in the same repo - it
does not build with Gradle and has no dependency on the Android code, though several modules
are deliberate ports of specific Kotlin files (noted per-module below).

## What's tested and what isn't

**Read this before trusting anything below.** This was built in an environment with no
Raspberry Pi, no I2S microphone, no Bluetooth adapter, and no GPIO pins attached - so nothing
that touches real hardware could actually be run, only written carefully against each
library's documented API. Don't treat the hardware-facing modules as verified until they've
actually been run on a real Pi.

| Module | Status | Why |
|---|---|---|
| `shot_timer_pi/shot_detector.py` | **Unit tested** (`tests/test_shot_detector.py`) | Pure sample-domain logic, no I/O |
| `shot_timer_pi/beep_detector.py` | **Unit tested** (`tests/test_beep_detector.py`) | Pure numpy/FFT logic, no I/O |
| `shot_timer_pi/storage.py` | **Unit tested** (`tests/test_storage.py`) | Pure stdlib `sqlite3`, real temp-file DB round-trips |
| `shot_timer_pi/beep.py` | **Partially tested** | `build_beep_samples()` (synthesis) is pure and is exercised indirectly by `test_beep_detector.py`; `play_tone()` (playback) needs a real audio output device and is untested |
| `shot_timer_pi/audio_source.py` | **Untested** | Needs a real ALSA capture device (the I2S mic) |
| `shot_timer_pi/ble_service.py` | **Untested** | Needs a real BlueZ-managed Bluetooth adapter |
| `shot_timer_pi/button.py` | **Untested** | Needs real GPIO hardware |
| `shot_timer_pi/run_controller.py` | **Untested** | Orchestrates the above - calls real playback and real mic capture directly |
| `shot_timer_pi/main.py` | **Untested** | Wires everything together; is the real entry point |

`tests/` only ever imports the three pure modules - `shot_timer_pi/__init__.py` is
deliberately empty (no submodule imports) so that importing any one of them can never
accidentally drag in `sounddevice`, `bluezero`, or `gpiozero`.

None of the untested modules have mocked-hardware tests standing in for the real thing on
purpose - a test that fakes out `sounddevice`/`bluezero`/`gpiozero` and asserts against its
own fake would just be testing the fake, not proving anything about how this behaves on
actual hardware. They're written as carefully as the tested modules and reviewed against each
library's real documented API (bluezero's especially - see `ble_service.py`'s module
docstring), but "written correctly" and "verified" are different claims, and this README
isn't going to blur them.

**Next step for whoever has the hardware:** flash a Pi, wire it up per the tables below, run
through "Running it manually," and confirm a button press and a BLE "ARM" write both produce
a beep, detect a clap/shot, and show up correctly in local storage / over BLE. That's the
verification this project still needs.

## Hardware

See `docs/PI_COMPANION.md`'s hardware table for the full finalized list (Pi 3 Model B,
Adafruit I2S MEMS mic breakout, piezo buzzer + NPN transistor driver, tactile push button).
Summarized here with the parts most relevant to wiring:

- Raspberry Pi 3 Model B
- Adafruit I2S MEMS Microphone Breakout (SPH0645LM4H)
- Passive piezo buzzer module + NPN transistor (2N2222/2N3904) + ~1kΩ base resistor
- 6x6mm 4-pin tactile push button

## Setup

### 1. Flash the OS

Target OS is **Raspberry Pi OS Lite (64-bit)** - not Ubuntu, no desktop environment needed.

1. Install [Raspberry Pi Imager](https://www.raspberrypi.com/software/) on your computer.
2. Insert the microSD card.
3. In Imager: **Choose Device** -> Raspberry Pi 3. **Choose OS** -> Raspberry Pi OS (other) ->
   **Raspberry Pi OS Lite (64-bit)**. **Choose Storage** -> your microSD card.
4. Click the gear/settings icon (or press Ctrl+Shift+X) to open **advanced options** before
   writing:
   - Set a hostname (e.g. `shot-timer-pi.local`).
   - Enable SSH - either password auth or paste a public key.
   - Set a username/password (Raspberry Pi OS no longer ships a default `pi`/`raspberry`
     login - you choose the username here; if it isn't `pi`, remember to update
     `systemd/shot-timer-pi.service`'s `User=`/`Group=` and `WorkingDirectory=` later).
   - Configure Wi-Fi (SSID/password/country) if you're not wiring Ethernet.
   - Set locale/timezone/keyboard layout.
5. Write, then boot the Pi with the microSD card inserted. SSH in once it's up:
   `ssh <username>@<hostname>.local`.

### 2. Enable the I2S microphone overlay

I2S enablement on Raspberry Pi OS **Bookworm** goes in `/boot/firmware/config.txt` - not the
older `/boot/config.txt` path from previous OS releases.

**Try the standard I2S mic overlay first:**

```
dtparam=i2s=on
dtoverlay=googlevoicehat-soundcard
```

(You can also enable the `i2s` param via `sudo raspi-config` -> **Interface Options** -> I2S,
which edits the same file for you, then add the `dtoverlay` line manually.)

**Honest caveat, not a guarantee:** the SPH0645LM4H (the mic on Adafruit's breakout) has a
known hardware quirk documented in Adafruit's own guide for this part - its 18 significant
bits of output are left-justified in a 32-bit I2S frame, one bit off from what a strict I2S
receiver expects, which can produce silence or garbage with a naive/generic I2S input driver.
`googlevoicehat-soundcard` is the overlay the hobbyist community most often reports actually
working for this specific chip (it's also what Adafruit's own installer script defaults to
for this product) - it's listed above as the first thing to try, not as a separately-named
fallback, because in practice it's the option most likely to work. This has **not** been
verified against real hardware in this build - if it's silent, check `dmesg` for I2S/ALSA
errors and search current Adafruit/Raspberry Pi forum threads for this exact chip, since
overlay behavior does shift across Raspberry Pi OS releases.

**If you end up on `googlevoicehat-soundcard`, note its fixed sample rate:** it runs at a
fixed 48kHz with no software volume control. `shot_timer_pi/audio_source.py`'s
`SAMPLE_RATE_HZ` constant defaults to 44100 (matching the Android app's
`AUDIO_SAMPLE_RATE_HZ`) - if the overlay forces 48kHz, pass `sample_rate_hz=48000` through to
`audio_source.chunks()` (and `run_controller.py`'s call site) instead, or capture at 48kHz and
resample. `shot_detector.py`'s math is sample-rate-agnostic (see its own tests, which run at
yet another rate for readability), so this is a config change, not a logic change.

Reboot after editing config.txt: `sudo reboot`.

Verify the mic shows up: `arecord -l` should list a capture device. Test a raw capture:
`arecord -D plughw:<card>,0 -f S32_LE -r 48000 -c 2 -d 3 test.wav` (adjust `-r` per whichever
overlay/rate you ended up on), then check `test.wav` isn't silent.

### 3. Wiring reference

BCM GPIO numbering throughout (not physical pin number, except where noted). These are the
**standard I2S pins on the 40-pin header for BCM2835-family SoCs** (same across the Pi
family, including the 3B) - treat this as a starting reference and double-check against the
[SPH0645 breakout's own Adafruit guide](https://learn.adafruit.com/adafruit-i2s-mems-microphone-breakout)
before wiring, especially the breakout's `SEL` pin polarity, which this table does not claim
to have verified.

| Signal | BCM GPIO | Physical pin | Notes |
|---|---|---|---|
| I2S BCLK (bit clock) | GPIO18 | 12 | To mic breakout's `BCLK` |
| I2S LRCLK (word/frame select) | GPIO19 | 35 | To mic breakout's `LRCL` |
| I2S DIN (Pi data in) | GPIO20 | 38 | To mic breakout's `DOUT` (mic's output is the Pi's input) |
| I2S DOUT (Pi data out) | GPIO21 | 40 | Unused for a mic-only (input) setup - only needed if driving an I2S output device |
| 3.3V | - | 1 | To mic breakout's `3Vo` |
| GND | - | 6 (or any GND pin) | To mic breakout's `GND`, and common ground for the buzzer/transistor and button circuits below |
| Mic `SEL` (left/right channel select) | - | - | Tie to GND or 3.3V per the breakout's own guide - confirm polarity there, not assumed here |
| Buzzer driver control | GPIO17 | 11 | Through a ~1kΩ resistor to the NPN transistor's base - see circuit below |
| Button | GPIO27 | 13 | One leg here, diagonal leg to GND - internal pull-up in software, no external resistor (see `button.py`) |

**Buzzer + transistor circuit** (low-side NPN switch - the Pi's GPIO can't source enough
current to drive the buzzer directly):

```
GPIO17 --[~1kOhm resistor]--> Transistor Base
Transistor Emitter --> GND (shared with Pi GND)
Transistor Collector --> Buzzer negative lead
Buzzer positive lead --> 5V (physical pin 2 or 4)
```

A piezo element is capacitive, not inductive, so (unlike a motor/relay/solenoid) a flyback
diode isn't required here. The 1kΩ base resistor is a standard starting value for a
2N2222/2N3904 switching a small buzzer, not a value derived from the actual buzzer module's
datasheet - confirm current draw against the specific module once it's in hand, per
`docs/PI_COMPANION.md`'s own note that this circuit still needs finalizing against real
hardware.

### 4. Install Python dependencies

```bash
cd pi-companion
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

`bluezero` also needs BlueZ itself present (it ships with Raspberry Pi OS) and the Pi's user
in a position to talk to it over D-Bus - if you hit permission errors registering the GATT
application, the common fixes are running as root or adding the user to the `bluetooth`
group; consult bluezero's own troubleshooting docs, since this wasn't verified against a real
BlueZ stack here. GPIO access needs the user in the `gpio` group and audio access needs the
`audio` group - both are typically already set up for the user account created via Raspberry
Pi Imager, but if not: `sudo usermod -aG gpio,audio,bluetooth <username>` (log out/reboot for
group changes to take effect).

### 5. Running the tests

The pure-logic test suite (`shot_detector.py`, `beep_detector.py`, `beep.py`'s synthesis,
`storage.py`) doesn't need the Pi at all - it runs on any machine with Python 3 and two
packages:

```bash
pip install pytest numpy
pytest tests/
```

(From the repo root instead: `pytest pi-companion/tests/` - `pi-companion/pytest.ini` adds
`pi-companion/` to the import path either way, so both invocations work.)

All 28 tests should pass. This does **not** require `sounddevice`, `bluezero`, or `gpiozero`
to be installed - see "What's tested and what isn't" above for why that's a deliberate
property of the package layout, not an accident.

### 6. Running it manually

From the `pi-companion/` directory, with dependencies installed and hardware wired up:

```bash
python3 -m shot_timer_pi.main
```

This starts advertising over BLE as "Shot Timer Pi", arms the physical button, and logs to
stdout. Press the button, or connect from a BLE central (e.g. a phone with a generic BLE
scanner app like nRF Connect) and write the string `ARM` to the Command characteristic, to
trigger a run. Ctrl-C to stop.

### 7. Installing the systemd service

```bash
sudo cp systemd/shot-timer-pi.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now shot-timer-pi.service
```

Edit `systemd/shot-timer-pi.service`'s `User=`, `Group=`, and `WorkingDirectory=` first if
your username or repo location differs from the placeholders in that file (see comments
inside it). Check status/logs with `systemctl status shot-timer-pi` and
`journalctl -u shot-timer-pi -f`.

## BLE GATT protocol reference

One custom service (UUIDs are randomly generated for this project, not registered with the
Bluetooth SIG - see `ble_service.py`), three characteristics:

| Characteristic | Property | Payload |
|---|---|---|
| Command | write | ASCII string `"ARM"` - triggers a run. Anything else is logged and ignored. |
| Event | notify | JSON per run event: `{"type":"beep"}`, then `{"type":"shot","elapsed_ms":870}` per shot, then `{"type":"run_complete","total_ms":2580,"shots":[870,1200,2500]}` |
| Sync | notify | Same shape as `run_complete`, plus `"run_id"` (the local SQLite row id), replayed for every not-yet-synced run once a phone subscribes |

A run always saves to local SQLite first (`storage.py`), whether it was triggered by the
button or by BLE - see `run_controller.py`. A row is marked synced the instant its notify
call returns, with no ack/retry handshake (at-least-once delivery, not exactly-once - see
`storage.RunStorage.mark_synced`'s docstring). The phone-side `ShotEventSource`/BLE client
that would actually consume this is out of scope here - see `docs/PI_COMPANION.md`'s open
question #6.

## Design notes / where this fills in gaps the handoff doc left open

`docs/PI_COMPANION.md` resolved most of the open questions (OS, mic hardware, BLE library,
GATT shape, offline-run storage), but a few implementation details weren't specified and had
to be decided here to get something buildable. Flagging them explicitly rather than letting
them look like they were part of the original spec:

- **Run-end behavior.** The resolved BLE design only defines an "ARM" command - no "STOP" -
  and the button/ARM triggers are required to funnel into the exact same start-a-run
  function, so neither can double as a stop signal without diverging from that. A run
  therefore ends on its own: `RunConfig.max_run_seconds` (default 30s) is a hard ceiling from
  the beep, `RunConfig.silence_timeout_seconds` (default 5s) ends it earlier once nothing's
  been detected for that long since the beep or the most recent shot. Both are untuned
  placeholders - see `run_controller.py`'s module docstring for the full reasoning, and treat
  these the same way `ShotTimerViewModel.kt` treats its own
  `MIN_THRESHOLD_AMPLITUDE`/`MAX_THRESHOLD_AMPLITUDE`: a real starting point, not a measured
  one.
- **`AudioChunk` lives in `shot_detector.py`, not `audio_source.py`.** On the Kotlin side,
  `AudioChunk` is defined in `AudioSource.kt` and `ShotDetector.kt` imports it. Mirroring
  that exactly in Python would mean `shot_detector.py` importing from `audio_source.py` -
  which has a module-level `import sounddevice`, so it would make the pure detector's tests
  require sounddevice to be installed just to import the module. `AudioChunk` is defined in
  `shot_detector.py` instead (zero dependencies) and `audio_source.py` imports it from there
  - the reverse of the Kotlin direction, kept this way specifically so "pure logic, no
    hardware dependency" is actually true of the module, not just true in spirit.
- **Beep self-recognition window.** `beep_detector.is_own_tone()` is only consulted by
  `run_controller.py` for the brief window right after the beep starts (its duration + fade +
  a small margin), not for the whole run - see `run_controller.py`'s `_BEEP_GUARD_SECONDS`.
  Outside that window it's pure amplitude-threshold detection, identical to the Kotlin
  original, so there's no risk of a real shot late in a run ever being second-guessed by a
  frequency check.

## Porting map (Kotlin -> Python)

| Android (Kotlin) | Pi (Python) | Relationship |
|---|---|---|
| `detection/ShotDetector.kt` + its test | `shot_timer_pi/shot_detector.py` + `tests/test_shot_detector.py` | Faithful line-for-line port, same test cases |
| `timer/ShotTimerViewModel.kt`'s `buildBeepSamples()`/`playTone()` | `shot_timer_pi/beep.py` | Same fade envelope math, numpy instead of a `ShortArray` lambda |
| `timer/ShotTimerViewModel.kt`'s `start()` sequencing | `shot_timer_pi/run_controller.py` | Same delay -> beep -> mark-start -> detect shape, threads instead of coroutines |
| `data/RunEntity.kt` + `data/Converters.kt` | `shot_timer_pi/storage.py` | Same comma-joined-string encoding, sqlite3 instead of Room, plus a `synced` column |
| *(none - Pi-only problem)* | `shot_timer_pi/beep_detector.py` | New: FFT-bin energy check to tell the Pi's own beep apart from a shot - see its module docstring for why the phone never needed this |

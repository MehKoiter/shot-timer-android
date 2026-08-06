# Pi Companion: Better Acoustic Front-End

Status: **design/planning only, no code written yet**. This doc is the handoff for whoever
(agent or human) picks up implementation next. It captures the architecture and the reasoning
behind it, not just the conclusions, so decisions don't get re-litigated from scratch.

## Why this exists

The phone's mic-based detection (`ShotDetector.kt` + `AudioSource.kt`) works, but we hit real
limitations getting there: the phone mic's AGC had to be worked around (switched to
`MediaRecorder.AudioSource.VOICE_RECOGNITION`), and separating "loud transient" from "gunshot"
is inherently harder on hardware/software we don't control. A Raspberry Pi with a dedicated,
uncompressed mic input gives more control over the actual acoustic front-end.

This was explored as one of two Pi companion ideas (the other being an external scoreboard
display, already listed as a backlog item in [docs/DESIGN.md](DESIGN.md)). This doc covers only
the acoustic front-end direction, which is the one we designed out in detail.

## Core architecture: the Pi is the timer, the phone is the receiver

This is the load-bearing decision, and it's *not* what a naive "two devices, one microphone
each" design would do:

- **The Pi owns the beep, the random delay, and shot detection.** It plays the start tone
  itself, listens for shots itself, and keeps its own local clock for the whole run - the same
  responsibilities `ShotTimerViewModel.start()` has today, just running on the Pi instead of
  the phone.
- **The phone is a receiver/display/logger**, not a participant in timing. It sends an "arm"
  command to start a run (or the Pi's own physical button does, see below), then just receives
  results.

### Why this avoids clock synchronization entirely

An earlier version of this idea had *both* devices independently listening for the beep, each
timestamping relative to their own clock, specifically to avoid a phone-Pi clock-sync problem.
That's unnecessary complexity once the Pi owns the whole run: since the Pi both emits the beep
and detects the shots, every timestamp it produces is already relative to a single clock (its
own). It never needs to know what time it is on the phone - it just ships a list of "shot at
+X ms" values, which are correct by construction. Bluetooth latency only delays when the phone
*hears about* an event for live UI; it never corrupts the recorded timing, because the Pi did
the actual timing entirely on its own before anything went over the air.

The one cosmetic consequence: the phone's live-ticking MM:SS.CC display will start a beat late,
since it only begins once the "beep happened" BLE notification arrives. The *recorded* splits
saved to History are unaffected - they come from the Pi's own precise timestamps, not from the
phone's delayed view of them. This is normal behavior for Bluetooth-paired commercial timers
(e.g. AMG Commander + PractiScore) too.

## Roles in detail

**Pi:**
- Generates and plays the start beep (own speaker/buzzer hardware - it doesn't rely on the
  phone's `AudioTrack` beep at all in this mode).
- Runs the random pre-beep delay.
- Captures audio continuously via the I2S mic and runs shot detection - port of the
  threshold + echo-lockout logic in `ShotDetector.kt` (Kotlin) to Python. Same algorithm,
  same shape of config (amplitude threshold, lockout window), different language.
- Has its own physical Start button (GPIO), independent of the phone. Pressing it triggers the
  exact same local run logic as a BLE "arm" command would - both should funnel into one
  `start_run()` function on the Pi, not two parallel code paths.
- **Stores every run locally** (SQLite on the Pi's SD card) regardless of whether a phone is
  connected at the time. If the phone isn't connected when a button-triggered run happens, nothing
  is lost - the Pi syncs unsent runs to the phone the next time it connects over BLE. This was a
  deliberate choice over the simpler "no phone, no record" option, specifically so the physical
  button is actually useful as a standalone trigger, not just a phone-must-be-present convenience.

**Phone:**
- Sends the "arm" command (from the existing Start button, when in Pi mode) and otherwise just
  listens.
- Nearly all existing UI is reusable as-is: `RunSummaryView`, `HistoryScreen`, `DrillsScreen`
  all just consume elapsed-time/shot-timestamp data, and don't care whether it came from local
  detection or a Pi over BLE.
- Needs a new abstraction (not yet built) so `ShotTimerViewModel` can be fed either by the
  existing local `AudioSource` + `ShotDetector` pipeline, or by a BLE event stream from the Pi.
  A Settings toggle should pick which; if the Pi isn't connected, fall back to the phone's own
  mic so the app stays fully usable without the Pi. The Pi is a companion, not a hard dependency.

## Communication: BLE GATT

Bluetooth Low Energy, not WiFi/HTTP or Bluetooth Classic - lower power, standard Android pairing
UX, and this is small/infrequent data (a handful of events per run), not a throughput problem.

Not yet designed: exact service/characteristic UUIDs, the "arm" command payload, the shot-event
notification payload, and the sync protocol for runs the Pi recorded while disconnected (needs
some way to say "here's everything you haven't seen yet" - simplest version is probably just
"send all locally-stored runs with no `synced` flag set, phone acks by run ID").

## Hardware (finalized, ready to build against)

| Part | Notes |
|---|---|
| **Raspberry Pi 3 Model B v1.2** | Already owned - no purchase needed. Originally scoped around a Pi Zero 2 W, but Zero-line boards have chronic retail shortages; the 3B's quad-core 1.2GHz is more headroom than needed for this DSP workload anyway. Same 40-pin GPIO header, same I2S pins, same microSD slot - nothing else in this list changes because of the board swap. |
| Adafruit I2S MEMS Mic Breakout (SPH0645LM4H) | Digital mic, no AGC - the whole point. Wires to GPIO I2S pins (BCLK/LRCLK/DOUT/3.3V/GND). |
| Passive piezo buzzer module (2-pack) | The beep. **Known limitation**: tops out around 85dB, not the 100+dB of a real range-timer siren. Fine for bench testing; if it's too quiet for actual range use later, swap to a small amplified speaker off the 3.5mm jack - doesn't change the tone-generation software. |
| NPN transistor (2N2222/2N3904) + 1kΩ resistor | Pi's GPIO can't source enough current to drive the buzzer directly - needs a switching transistor. Exact driver circuit (base resistor value, buzzer voltage/current) still needs to be finalized against the actual buzzer module's datasheet once it's in hand. |
| Tactile push button (6x6mm, 4-pin, momentary) | Physical Start trigger. Wire one leg to a free GPIO pin, the diagonal leg to GND, use the Pi's internal pull-up in software - no external resistor needed. |
| Breadboard + jumper wires (M-F, M-M) | Prototyping before anything's permanent. |
| MicroSD card, 16GB Class 10 | OS storage - Raspberry Pi OS Lite (headless, no desktop). |
| Existing microUSB cable + 5V/2.5A power adapter | Bench power. Portability (battery pack, enclosure) explicitly deferred - see below. |

**I2S GPIO pins** on the standard 40-pin header (BCM2835/2837-family SoCs, same across the Pi
family including the 3B): GPIO18/19/20/21 for I2S clock/frame-sync/data. Treat this as a
starting reference, not verified against the specific breakout board's documentation yet -
confirm before wiring.

## Explicitly out of scope for the first build

- **Portability**: no battery pack, no enclosure. Bench prototype first, on wall power, while
  the detection logic and BLE protocol are actually being built and tuned. The Pi 3B draws
  more power than a Zero would have, which only matters once battery life is a goal - revisit
  the board choice then if needed.
- **True outdoor-range loudness**: the piezo buzzer's ~85dB is a known gap, not an oversight.
  Upgrade path exists (amplified speaker) but isn't needed until this leaves the bench.
- **Scoreboard/display integration**: a separate idea, not designed out here.

## Open questions for whoever implements this

1. Exact I2S pin wiring, verified against the SPH0645 breakout's own docs.
2. Buzzer driver circuit specifics (resistor value, transistor orientation) against the actual
   module's voltage/current rating.
3. BLE GATT service design - UUIDs, payload formats for "arm," shot events, and run sync.
4. Python audio stack choice (`sounddevice` vs `pyaudio` vs raw `alsaaudio`) and whether
   Raspberry Pi OS needs a specific I2S overlay enabled in `/boot/config.txt` for this specific
   breakout.
5. Beep recognition on the Pi side: needs to distinguish "that was our own start tone" from "that
   was a shot," most likely via an FFT-bin energy check tuned to the beep's known frequency
   (1800Hz, matching `START_BEEP_FREQUENCY_HZ` in `ShotTimerViewModel.kt`) rather than reusing
   the amplitude-only threshold logic that works for shots.
6. Android-side `ShotEventSource` abstraction and BLE client - not started.
7. Local SQLite schema on the Pi for offline-recorded runs, and the actual sync/dedup protocol
   when the phone reconnects.

## Reference: existing code this should mirror

- `app/src/main/java/com/shottimer/app/detection/ShotDetector.kt` - the threshold + echo-lockout
  algorithm to port to Python. Pure logic, no Android dependency, easy to read in isolation.
- `app/src/main/java/com/shottimer/app/timer/ShotTimerViewModel.kt` - the start/delay/beep/detect
  flow the Pi's own run loop should mirror (see `start()`).
- [docs/DESIGN.md](DESIGN.md) - original app spec; lists Bluetooth/external-hardware integration
  as backlog scope this doc is now the detailed follow-up to.

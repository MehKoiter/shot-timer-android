# Design Spec: Android Shot Timer

## Overview

A **professional shot timer** is an electronic timing device used in action shooting sports — such as USPSA, IDPA, IPSC, Steel Challenge, and 3-Gun — to measure a competitor's performance down to the hundredth of a second (0.01s). It initiates a course of fire with an audible start signal and automatically logs every shot fired until the stage is completed.

This document is the functional spec for an Android implementation.

## How shot timers work

```
[ RO Presses Start ] --> [ Buzzer Beeps (t = 0.00s) ] --> [ Shooter Fires ] --> [ Mic Detects Peak ] --> [ Log Centisecond ]
```

### 1. Start signal (T = 0.00s)

When the user presses start, the app triggers a high-precision timer and emits a high-decibel beep (~100+ dB target, 1.5–2.5 kHz tone, hardware permitting). Start can be **instant** or delayed by a **random interval (1.0–3.5s)** to prevent anticipation.

### 2. Acoustic muzzle blast detection

- Uses the device microphone tuned to pick up sharp, sudden sound pressure spikes from muzzle reports.
- Digital thresholding: when the incoming audio signal exceeds a configurable amplitude/decibel threshold, the app logs elapsed time relative to the start beep.

### 3. Echo & cross-fire filtering

- **Echo suppression**: after a shot is detected, a short digital lockout window (tunable, on the order of tens of milliseconds) ignores subsequent spikes so reflections/reverb off walls/berms aren't double-counted.
- **Sensitivity tuning**: adjustable threshold to dial down for loud/compensated guns or up for quiet calibers (e.g. .22 LR).

### 4. Alternative detection methods (future consideration)

- Accelerometer/recoil-based detection for wearable or gun-mounted use cases, to avoid false triggers from adjacent shooters. Not in scope for v1 (phone mic only).

## Primary metrics recorded

| Metric | What it measures | Significance |
|---|---|---|
| **First Shot (Draw Time)** | Time from start beep to first shot | Reaction time, draw speed, sight acquisition |
| **Split Time** | Time between consecutive shots | Recoil management, trigger control, sight tracking |
| **Transition Time** | Time between last shot on target A and first shot on target B | Target-to-target movement, gun driving |
| **Total Elapsed Time** | Start beep to final shot | Stage score input (with hits, for Hit Factor scoring) |
| **Par Time** | Secondary beep after a set duration | Fixed-time drills, dry fire training |

## Implementation status

v1 scope below has shipped and the app has grown beyond it: custom drills, per-shooter stats/trends/rename-merge, run share/CSV export, and optional Google/Firestore cloud backup are all implemented but weren't part of this original spec. One v1 item remains outstanding: **Transition Time** (listed in the metrics table above) is not implemented — only First Shot, Split, Total Elapsed, and Par Time are tracked.

## v1 scope

- Start/delay/beep
- Mic-based shot detection with adjustable sensitivity and echo lockout
- Live shot list with running clock, split times, total time
- Par time mode
- Local run history

## Explicitly out of scope for v1

- Bluetooth/PractiScore integration
- External LED scoreboard support
- Hardware recoil/accelerometer sensors
- Multi-timer sync across devices

## Open questions

- Target minimum Android API level / device mic hardware variance handling.
- Audio latency budget on typical Android hardware (buffer sizes, `AAudio` vs `AudioRecord`) and how it affects timestamp accuracy.
- UI/UX for match-day one-handed operation (RO holds phone, must be fast to arm/reset).

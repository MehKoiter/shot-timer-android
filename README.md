# Shot Timer for Android

An Android app that replicates a professional shot timer (à la USPSA/IDPA/IPSC/Steel Challenge/3-Gun) for use in dry fire practice, live fire training, and match administration — using the phone's microphone for acoustic shot detection instead of dedicated hardware.

## What it does

- **Start signal**: emits a high-decibel buzzer tone to start a course of fire, with a configurable random delay to prevent anticipation.
- **Acoustic shot detection**: uses the phone mic to detect muzzle-blast sound pressure spikes and timestamp each shot to the hundredth of a second. The mic is armed and recording throughout the random delay, before the beep, so a fast first shot is never missed.
- **Echo/cross-fire filtering**: digital lockout window after each detected shot to suppress echoes and reflections.
- **Sensitivity tuning**: adjustable detection threshold for different calibers/suppression levels (e.g. quiet .22 LR vs. compensated race guns).
- **Metrics**: first shot (draw) time, split times, total elapsed time, and par time. (Transition time, per [docs/DESIGN.md](docs/DESIGN.md), is not yet implemented.)
- **Drills**: four built-in classics (Bill Drill, El Presidente, Mozambique, Controlled Pairs) plus user-defined custom drills.
- **Run history**: per-run shot list with timestamps, filterable by drill/shooter, delete with undo, share a run as text, and export all runs as CSV.
- **Shooters**: per-shooter stats (runs, best, average) with a trend sparkline, tap-through to their filtered history, and rename/merge/remove.
- **Cloud backup**: optional Google sign-in backs up run history to Firestore; the app works fully offline if you never sign in.

See [docs/DESIGN.md](docs/DESIGN.md) for the original functional spec this project was built against (the "v1 scope" there has since shipped and been extended).

## Status

In active use by a small group of testers via Firebase App Distribution. Core timing/detection, drills, history, shooter management, and optional cloud backup are implemented; see [docs/DESIGN.md](docs/DESIGN.md)'s Open Questions for what's still unresolved.

## Device compatibility

Requires Android 8.0 (API 26) or newer — no other hardware requirements beyond a working microphone. Confirmed working on:

| Device | Android version | Notes |
|---|---|---|
| Samsung Galaxy S25 Ultra | Android 15 | First tester device; installed and used via Firebase App Distribution. |

This list only reflects devices a tester has actually confirmed - most Android 8.0+ phones should work fine even if not listed yet. Add a row here once a new device is confirmed.

## Intended use

This is a training/scoring tool for organized action-shooting sports and dry-fire practice on ranges where such use is permitted. It is a timing utility only — it does not control or interact with any firearm.

## License

MIT — see [LICENSE](LICENSE).

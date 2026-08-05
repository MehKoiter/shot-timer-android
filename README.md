# Shot Timer for Android

An Android app that replicates a professional shot timer (à la USPSA/IDPA/IPSC/Steel Challenge/3-Gun) for use in dry fire practice, live fire training, and match administration — using the phone's microphone for acoustic shot detection instead of dedicated hardware.

## What it does

- **Start signal**: emits a high-decibel buzzer tone to start a course of fire, with optional random delay (1.0–3.5s) to prevent anticipation.
- **Acoustic shot detection**: uses the phone mic to detect muzzle-blast sound pressure spikes and timestamp each shot to the hundredth of a second.
- **Echo/cross-fire filtering**: digital lockout window after each detected shot to suppress echoes and reflections.
- **Sensitivity tuning**: adjustable detection threshold for different calibers/suppression levels (e.g. quiet .22 LR vs. compensated race guns).
- **Metrics**: first shot (draw) time, split times, transition times, total elapsed time, and par time.
- **Review**: per-run shot list with timestamps, exportable/shareable results.

See [docs/DESIGN.md](docs/DESIGN.md) for the full functional spec this project is built against.

## Status

Early planning stage — no code yet. This repo currently holds the project spec and setup; implementation is tracked via GitHub Issues.

## Intended use

This is a training/scoring tool for organized action-shooting sports and dry-fire practice on ranges where such use is permitted. It is a timing utility only — it does not control or interact with any firearm.

## License

MIT — see [LICENSE](LICENSE).

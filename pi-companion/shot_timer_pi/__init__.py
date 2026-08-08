"""Raspberry Pi companion for the Android shot timer app - see pi-companion/README.md.

Deliberately empty beyond this docstring and __version__: it must not import any submodule
here, because `import shot_timer_pi.<anything>` runs this file first, and shot_detector.py /
beep_detector.py / storage.py are meant to be importable (and unit-testable) without pulling
in sounddevice / bluezero / gpiozero, none of which are installed or usable in a
hardware-free environment. Eagerly importing the hardware-dependent submodules here would
silently break that guarantee for every test in tests/.
"""

__version__ = "0.1.0"

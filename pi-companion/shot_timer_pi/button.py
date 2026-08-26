"""Physical Start button, wired to the exact same run-start entry point a BLE "ARM" command
uses - see run_controller.RunController.start() and its module docstring for why there is
deliberately only one code path behind both triggers.

Requires real GPIO hardware (a Raspberry Pi with the button wired per README.md's pinout
table); not exercised by pytest (see README's test-coverage table). Written against
gpiozero's documented Button API (verified against the gpiozero source's Button docstring:
pull_up=True is the default and enables the SoC's internal pull-up resistor in software, so
no external resistor is needed - matching the resolved hardware decision in
docs/PI_COMPANION.md).
"""

import logging

from gpiozero import Button

logger = logging.getLogger(__name__)

# BCM numbering. Matches README.md's wiring table - one leg of the button to this pin, the
# diagonal leg to GND.
DEFAULT_BUTTON_PIN = 27

# Simple debounce: ignores further edges for this long after a press. 100ms is a common
# starting point for tactile push buttons; it hasn't been tuned against the actual 6x6mm
# button in the hardware list, since that requires the physical part in hand.
DEFAULT_BOUNCE_TIME_SECONDS = 0.1


def create_button(on_press, pin: int = DEFAULT_BUTTON_PIN, bounce_time: float = DEFAULT_BOUNCE_TIME_SECONDS) -> Button:
    """Wires on_press (expected to be a RunController.start bound method - see main.py) to
    the physical button's falling edge (press-to-GND, via the internal pull-up).

    Returns the Button object; the caller must keep a reference to it alive for the lifetime
    of the process; gpiozero devices stop responding once they're garbage collected.
    """
    button = Button(pin, pull_up=True, bounce_time=bounce_time)
    button.when_pressed = on_press
    logger.info("Button armed on BCM GPIO%d (bounce_time=%.3fs)", pin, bounce_time)
    return button

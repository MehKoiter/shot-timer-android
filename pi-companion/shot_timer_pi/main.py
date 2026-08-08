"""Entry point: wires local storage, the run controller, the physical button, and the BLE
GATT server together, then blocks forever serving BLE requests. This is what
systemd/shot-timer-pi.service runs (`python3 -m shot_timer_pi.main`); it can also be run
directly for manual testing - see README.md.

Needs real hardware for everything it touches (mic, buzzer/speaker output, GPIO button,
Bluetooth adapter) - there is no software/simulation fallback, and this module is not
exercised by pytest (see README's test-coverage table).
"""

import logging

from .ble_service import ShotTimerBleService
from .button import create_button
from .run_controller import RunController
from .storage import RunStorage

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


def main() -> None:
    storage = RunStorage()
    run_controller = RunController(storage)

    # The physical button and the BLE Command characteristic's write_callback both end up
    # calling run_controller.start() directly - see run_controller.py's module docstring for
    # why that's one code path, not two. `button` must stay referenced for the lifetime of
    # the process (gpiozero devices stop responding once garbage collected), which this local
    # variable does simply by living in main()'s scope for as long as publish() blocks below.
    button = create_button(on_press=run_controller.start)

    # Wires run_controller's on_beep/on_shot/on_run_complete callbacks to BLE notifications -
    # see ble_service.py's constructor.
    ble_service = ShotTimerBleService(run_controller, storage)

    logger.info("Shot Timer Pi companion starting - advertising over BLE as 'Shot Timer Pi'")
    logger.info("Physical button armed; local runs stored at %s", storage.db_path)
    try:
        ble_service.publish()  # blocks forever; Ctrl-C raises KeyboardInterrupt, handled inside bluezero.
    finally:
        button.close()


if __name__ == "__main__":
    main()

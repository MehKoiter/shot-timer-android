"""bluezero GATT peripheral server: one custom service, three characteristics, per the
resolved BLE design in the project handoff doc (docs/PI_COMPANION.md):

- Command (write): phone writes the ASCII string "ARM" to trigger a run. Calls the exact
  same RunController.start() the physical button calls (see run_controller.py) - there is no
  separate "started via BLE" code path.
- Event (notify): pushed live as a run happens - {"type":"beep"}, then
  {"type":"shot","elapsed_ms":N} per shot, then
  {"type":"run_complete","total_ms":N,"shots":[...]} once the run ends.
- Sync (notify): replays any previously-unsynced runs (recorded while no phone was connected,
  see storage.py) using the same payload shape as the "run_complete" Event, plus the local
  SQLite row id (`run_id`) so the phone can tell runs apart / dedupe. Each row is marked
  synced=1 in local storage the instant its notify call returns - no ack/retry handshake for
  v1 (see storage.RunStorage.mark_synced's docstring), so delivery is at-least-once, not
  exactly-once.

Requires a real BlueZ-managed Bluetooth adapter (bluetoothd running, an adapter present); not
exercised by pytest (see README's test-coverage table). Written against bluezero's documented
Peripheral/add_service/add_characteristic/set_value API - verified against the actual
python-bluezero source (bluezero/peripheral.py, bluezero/localGATT.py) and its
heartrate_monitor_peripheral.py / ble_uart.py examples, not against a real BlueZ stack, which
doesn't exist in this build environment.
"""

import json
import logging
from typing import List, Optional

from bluezero import adapter, peripheral

from .run_controller import RunController, RunResult
from .storage import RunStorage

logger = logging.getLogger(__name__)

# Custom 128-bit UUIDs, randomly generated for this project (uuid4 - not a registered
# Bluetooth SIG UUID). Characteristic UUIDs increment the service UUID's last hex digit,
# following the same convention Nordic's well-known UART service uses (and which bluezero's
# own ble_uart.py example is built around): 6E400001/.../02/.../03.
SERVICE_UUID = "28559379-1dfa-497c-ab16-21999fe01d90"
COMMAND_CHAR_UUID = "28559379-1dfa-497c-ab16-21999fe01d91"
EVENT_CHAR_UUID = "28559379-1dfa-497c-ab16-21999fe01d92"
SYNC_CHAR_UUID = "28559379-1dfa-497c-ab16-21999fe01d93"

ARM_COMMAND = "ARM"

_SRV_ID = 1
_CHR_COMMAND = 1
_CHR_EVENT = 2
_CHR_SYNC = 3


class ShotTimerBleService:
    """Owns the bluezero Peripheral and bridges it to a RunController + RunStorage.

    Construction order matters (see main.py): build a RunStorage and a RunController first,
    then construct this with both - the constructor wires itself into
    run_controller.on_beep/on_shot/on_run_complete. publish() should be called last, since it
    blocks (bluezero's D-Bus/GLib event loop) for the life of the process.
    """

    def __init__(self, run_controller: RunController, storage: RunStorage, adapter_address: Optional[str] = None) -> None:
        self._run_controller = run_controller
        self._storage = storage

        # Set once a phone subscribes to each notify characteristic (see _on_event_notify /
        # _on_sync_notify below); None means "nobody's listening right now", in which case
        # the corresponding _notify_* call below is a silent no-op rather than an error - a
        # run happening with no phone connected is an expected, supported case (see storage.py
        # and the "Offline runs" decision in the project handoff doc).
        self._event_char = None
        self._sync_char = None

        address = adapter_address or _default_adapter_address()
        self._peripheral = peripheral.Peripheral(address, local_name="Shot Timer Pi")

        self._peripheral.add_service(srv_id=_SRV_ID, uuid=SERVICE_UUID, primary=True)

        self._peripheral.add_characteristic(
            srv_id=_SRV_ID, chr_id=_CHR_COMMAND, uuid=COMMAND_CHAR_UUID,
            value=[], notifying=False,
            flags=["write"],
            read_callback=None,
            write_callback=self._on_command_write,
            notify_callback=None,
        )
        self._peripheral.add_characteristic(
            srv_id=_SRV_ID, chr_id=_CHR_EVENT, uuid=EVENT_CHAR_UUID,
            value=[], notifying=False,
            flags=["notify"],
            read_callback=None,
            write_callback=None,
            notify_callback=self._on_event_notify,
        )
        self._peripheral.add_characteristic(
            srv_id=_SRV_ID, chr_id=_CHR_SYNC, uuid=SYNC_CHAR_UUID,
            value=[], notifying=False,
            flags=["notify"],
            read_callback=None,
            write_callback=None,
            notify_callback=self._on_sync_notify,
        )

        self._peripheral.on_connect = self._on_connect

        run_controller.on_beep = self._notify_beep
        run_controller.on_shot = self._notify_shot
        run_controller.on_run_complete = self._notify_run_complete

    def publish(self) -> None:
        """Advertises and starts bluezero's D-Bus event loop. Blocks until interrupted (see
        bluezero.peripheral.Peripheral.publish(), which catches KeyboardInterrupt itself) -
        call this last."""
        self._peripheral.publish()

    # ----- bluezero -> us -----

    def _on_command_write(self, value: List[int], _options) -> None:
        text = bytes(value).decode("utf-8", errors="replace").strip()
        if text == ARM_COMMAND:
            logger.info("Received ARM over BLE")
            self._run_controller.start()
        else:
            logger.warning("Ignoring unrecognized Command payload: %r", text)

    def _on_event_notify(self, notifying: bool, characteristic) -> None:
        self._event_char = characteristic if notifying else None

    def _on_sync_notify(self, notifying: bool, characteristic) -> None:
        self._sync_char = characteristic if notifying else None
        if notifying:
            # A phone subscribing to Sync is exactly the "here's everything you haven't seen
            # yet" moment described in the project handoff doc - push the backlog now.
            self._push_unsynced_runs()

    def _on_connect(self, _device) -> None:
        logger.info("Central connected")

    # ----- run_controller -> us -----

    def _notify_beep(self) -> None:
        self._notify_event({"type": "beep"})

    def _notify_shot(self, elapsed_ms: int) -> None:
        self._notify_event({"type": "shot", "elapsed_ms": elapsed_ms})

    def _notify_run_complete(self, result: RunResult, row_id: int) -> None:
        self._notify_event(
            {"type": "run_complete", "total_ms": result.total_ms, "shots": result.shots_ms}
        )
        # Fire-and-forget: mark synced as soon as the notify call has been made, regardless of
        # whether a phone was actually connected/subscribed to receive it - see
        # storage.RunStorage.mark_synced's docstring. If no phone was connected, this row
        # simply stays synced=0 and gets picked up by the Sync replay below on the next
        # connection.
        if self._event_char is not None:
            self._storage.mark_synced(row_id)

    # ----- low-level notify helpers -----

    def _push_unsynced_runs(self) -> None:
        for run in self._storage.unsynced_runs():
            payload = {
                "type": "run_complete",
                "total_ms": run.total_elapsed_millis,
                "shots": run.shot_timestamps_millis,
                "run_id": run.id,
            }
            if self._notify_sync(payload):
                self._storage.mark_synced(run.id)

    def _notify_event(self, payload: dict) -> bool:
        return _notify(self._event_char, payload)

    def _notify_sync(self, payload: dict) -> bool:
        return _notify(self._sync_char, payload)


def _notify(characteristic, payload: dict) -> bool:
    """Encodes payload as JSON/UTF-8 and pushes it via characteristic.set_value(), which
    bluezero turns into a GATT notification for any subscribed centrals (see localGATT.py:
    Characteristic.set_value() -> Characteristic.Set() -> a PropertiesChanged D-Bus signal,
    which BlueZ picks up and sends as an ATT notification because Notifying is True). Returns
    False without doing anything if nobody's subscribed yet (characteristic is None).
    """
    if characteristic is None:
        logger.debug("No subscriber for this characteristic yet - dropping %s", payload)
        return False
    encoded = json.dumps(payload).encode("utf-8")
    characteristic.set_value(list(encoded))
    return True


def _default_adapter_address() -> str:
    available = list(adapter.Adapter.available())
    if not available:
        raise RuntimeError("No Bluetooth adapter found - is BlueZ (bluetoothd) running?")
    return available[0].address

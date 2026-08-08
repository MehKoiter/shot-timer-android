"""Workaround for a confirmed BlueZ/kernel bug that breaks bluezero's (and any other D-Bus
LEAdvertisingManager1 client's) BLE advertising on this Pi - see README.md's "Known issues"
for the full investigation. Summary of what was actually proven, via `btmon` HCI/mgmt tracing
on the real hardware, not guessed:

- bluetoothd (BlueZ 5.82) registers a D-Bus advertisement by sending the kernel's newer
  *split* mgmt commands: "Add Extended Advertising Parameters" (succeeds) followed by "Add
  Extended Advertising Data" (fails with Status: Invalid Parameters (0x0d)) - and this failure
  happens synchronously inside the kernel's mgmt handler, before any HCI command is even sent
  to the controller. That rules out the BCM43430A1 chip/firmware as the cause, despite the
  0x0d status code superficially looking like an HCI-level controller rejection (that's just
  BlueZ echoing the mgmt-layer status through the same numeric error space).
- The older, single-shot "Add Advertising" mgmt command (opcode 0x003e - what `btmgmt add-adv`
  uses) reaches the controller fine: LE Set Advertising Parameters, LE Set Advertising Data, LE
  Set Scan Response Data, and LE Set Advertise Enable all come back Status: Success. So this is
  a BlueZ-5.82-vs-this-kernel (6.18.34+rpt-rpi-v8 as of 2026-08-07) bug in the split/"extended"
  advertising path specifically, not a hardware limitation.

Since bluezero (and BlueZ's D-Bus API in general) has no lever to force bluetoothd to use the
legacy single-shot mgmt command instead of the broken split one, this module bypasses
bluetoothd's advertising path entirely and drives the kernel's mgmt interface directly via the
`btmgmt` CLI. GATT is untouched - GattManager1 registration was never affected by this bug, so
bluezero still owns that half normally. A central that discovers this advertisement and
connects talks to the exact same BlueZ-hosted GATT server either way; advertising and GATT are
independent BlueZ D-Bus interfaces, so splitting how each is set up doesn't change what a
connected phone sees.

Requires `btmgmt` to be runnable without a password by whatever user runs this process - see
README.md's setup instructions for the one-time `setcap cap_net_admin+eip $(which btmgmt)` this
depends on (plain `sudo btmgmt ...` works too, but only interactively; a systemd service can't
type a sudo password, hence the capability grant instead of a sudoers rule).
"""

import logging
import shutil
import subprocess

logger = logging.getLogger(__name__)

_INSTANCE_ID = "1"
_BTMGMT_TIMEOUT_SECONDS = 10


class AdvertisingWorkaroundError(RuntimeError):
    """Raised when the btmgmt-based advertising workaround can't run at all (missing binary,
    missing permissions) - as opposed to an individual btmgmt call failing, which is logged
    but not fatal (see _run)."""


def _local_name_ad_hex(name: str) -> str:
    """Builds a "Complete Local Name" AD structure (BT Core Spec Supplement, Part A, 1.2) as a
    hex string suitable for btmgmt's -s/--scan-rsp option: one length byte (name bytes + 1 for
    the type byte), one type byte (0x09), then the name's raw UTF-8 bytes."""
    name_bytes = name.encode("utf-8")
    length = len(name_bytes) + 1
    if length > 255:
        raise ValueError(f"local_name too long for a single AD structure: {name!r}")
    return f"{length:02x}09{name_bytes.hex()}"


def _run(*args: str, warn_on_failure: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(
        ["btmgmt", *args],
        capture_output=True,
        text=True,
        timeout=_BTMGMT_TIMEOUT_SECONDS,
    )
    if result.returncode != 0 and warn_on_failure:
        logger.warning(
            "btmgmt %s exited %s: %s",
            " ".join(args), result.returncode, result.stderr.strip() or result.stdout.strip(),
        )
    return result


def start(local_name: str, service_uuid: str) -> None:
    """Starts legacy BLE advertising for the given local name + 128-bit service UUID via
    btmgmt, replacing whatever bluezero/bluetoothd's own (broken) advertisement registration
    would have done. Safe to call if an instance is already running (clears it first)."""
    if shutil.which("btmgmt") is None:
        raise AdvertisingWorkaroundError(
            "btmgmt not found on PATH - required by the BLE advertising workaround, "
            "see README.md's Known issues section"
        )

    # Defensive cleanup in case a previous instance 1 is still registered (e.g. this process
    # was killed rather than Ctrl-C'd last time, skipping stop()'s own clr-adv below). Failing
    # because there was nothing to clear is the expected common case, not a real problem - see
    # warn_on_failure=False.
    _run("clr-adv", warn_on_failure=False)
    result = _run(
        "add-adv", "-c", "-m",
        "-u", service_uuid,
        "-s", _local_name_ad_hex(local_name),
        _INSTANCE_ID,
    )
    if result.returncode != 0:
        raise AdvertisingWorkaroundError(
            "btmgmt add-adv failed - is cap_net_admin set on btmgmt? "
            "(see README.md's setup instructions). stderr: " + result.stderr.strip()
        )
    logger.info(
        "BLE advertising started via btmgmt workaround (local_name=%r, service_uuid=%s) - "
        "see README.md's Known issues for why this bypasses bluezero's own advertising call",
        local_name, service_uuid,
    )


def stop() -> None:
    """Stops advertising and removes the instance. Best-effort - logs but doesn't raise, since
    this is normally called during shutdown (see ble_service.py)."""
    _run("clr-adv")
    logger.info("BLE advertising stopped (btmgmt workaround)")


def patch_peripheral(peripheral_obj, local_name: str, service_uuid: str) -> None:
    """Monkeypatches a bluezero.peripheral.Peripheral instance's AdvertisingManager so that
    Peripheral.publish()'s own calls to register_advertisement()/unregister_advertisement()
    (which is exactly the D-Bus call sequence that triggers the bug described in this module's
    docstring) transparently use this module's working btmgmt-based path instead. GATT
    registration (the rest of publish()) is untouched and runs through bluezero normally.

    Must be called before peripheral_obj.publish().
    """

    def _register_advertisement(_advertisement, _options=None) -> None:
        start(local_name, service_uuid)

    def _unregister_advertisement(_advertisement) -> None:
        stop()

    peripheral_obj.ad_manager.register_advertisement = _register_advertisement
    peripheral_obj.ad_manager.unregister_advertisement = _unregister_advertisement

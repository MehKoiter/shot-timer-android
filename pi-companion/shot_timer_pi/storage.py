"""Local SQLite run storage for the Pi.

Mirrors the encoding in app/src/main/java/com/shottimer/app/data/RunEntity.kt and
Converters.kt exactly: shot timestamps are stored as a single comma-joined TEXT column, not a
separate table or a JSON column, so a run recorded on the Pi and a run recorded on the phone
are shaped the same on disk (modulo column naming - Room's camelCase vs. this module's
snake_case).

Adds one column RunEntity doesn't have: `synced`, tracking whether this row has been pushed
to the phone yet over the Sync BLE characteristic (see ble_service.py). Per the "Offline
runs" decision in the project handoff: a row is marked synced the instant its notify call
returns, with no ack/retry handshake - see mark_synced()'s docstring for why.

Pure stdlib (sqlite3) - no hardware dependency, fully unit tested (tests/test_storage.py).
"""

import sqlite3
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Union

DEFAULT_DB_PATH = Path.home() / ".local" / "share" / "shot-timer-pi" / "runs.db"

_SCHEMA = """
CREATE TABLE IF NOT EXISTS runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp_epoch_millis INTEGER NOT NULL,
    total_elapsed_millis INTEGER NOT NULL,
    shot_timestamps_millis TEXT NOT NULL,
    par_time_seconds REAL,
    synced INTEGER NOT NULL DEFAULT 0
);
"""


@dataclass
class RunRecord:
    """Mirrors RunEntity.kt's fields (timestamp_epoch_millis, total_elapsed_millis,
    shot_timestamps_millis, par_time_seconds), plus `synced` (see module docstring). id is
    None until the record has been inserted - SQLite assigns the autoincrement id on insert,
    the same as Room's `@PrimaryKey(autoGenerate = true)`.
    """

    timestamp_epoch_millis: int
    total_elapsed_millis: int
    shot_timestamps_millis: List[int]
    par_time_seconds: Optional[float] = None
    synced: bool = False
    id: Optional[int] = None


def _encode_shot_timestamps(values: List[int]) -> str:
    """Mirrors Converters.fromShotTimestamps: comma-joined, empty list -> empty string."""
    return ",".join(str(v) for v in values)


def _decode_shot_timestamps(value: str) -> List[int]:
    """Mirrors Converters.toShotTimestamps: blank string -> empty list."""
    if not value.strip():
        return []
    return [int(v) for v in value.split(",")]


class RunStorage:
    """One sqlite3 connection per instance, playing the same role the Room database does on
    the Android side.

    Real usage in this project (see main.py) has exactly one RunStorage instance shared
    between run_controller.py (which calls save_run() from the background thread each
    start()/_run() spins up) and ble_service.py (which calls mark_synced()/unsynced_runs()
    from bluezero's D-Bus/GLib callback thread) - genuinely concurrent, cross-thread access,
    not just "constructed on one thread, used on another." sqlite3 connections default to
    check_same_thread=True specifically to prevent that, so this passes
    check_same_thread=False and takes on serializing access itself via _lock instead, rather
    than relying on the specifics of how the underlying SQLite library was compiled.
    """

    def __init__(self, db_path: Union[str, Path] = DEFAULT_DB_PATH) -> None:
        self._db_path = Path(db_path)
        if str(self._db_path) != ":memory:":
            self._db_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(str(self._db_path), check_same_thread=False)
        with self._lock:
            self._conn.execute(_SCHEMA)
            self._conn.commit()

    @property
    def db_path(self) -> Path:
        return self._db_path

    def close(self) -> None:
        with self._lock:
            self._conn.close()

    def __enter__(self) -> "RunStorage":
        return self

    def __exit__(self, *_exc) -> None:
        self.close()

    def save_run(self, run: RunRecord) -> int:
        """Inserts a new run row (always synced=0 on insert - a fresh run hasn't been pushed
        to anyone yet regardless of what run.synced was set to) and returns its assigned id.
        """
        with self._lock:
            cursor = self._conn.execute(
                """
                INSERT INTO runs
                    (timestamp_epoch_millis, total_elapsed_millis, shot_timestamps_millis, par_time_seconds, synced)
                VALUES (?, ?, ?, ?, 0)
                """,
                (
                    run.timestamp_epoch_millis,
                    run.total_elapsed_millis,
                    _encode_shot_timestamps(run.shot_timestamps_millis),
                    run.par_time_seconds,
                ),
            )
            self._conn.commit()
            return cursor.lastrowid

    def mark_synced(self, run_id: int) -> None:
        """Called the instant a row has been successfully notified over the Sync (or Event -
        see ble_service.py) BLE characteristic. No ack/retry handshake for v1: delivery is
        at-least-once, not exactly-once, per the "Offline runs" decision in the project
        handoff - this is a fire-and-forget local update right after the notify call returns,
        not gated on the phone acknowledging anything.
        """
        with self._lock:
            self._conn.execute("UPDATE runs SET synced = 1 WHERE id = ?", (run_id,))
            self._conn.commit()

    def unsynced_runs(self) -> List[RunRecord]:
        """Every run not yet pushed to the phone, oldest first - what ble_service.py replays
        over the Sync characteristic when a phone connects."""
        with self._lock:
            rows = self._conn.execute(
                """
                SELECT id, timestamp_epoch_millis, total_elapsed_millis, shot_timestamps_millis, par_time_seconds, synced
                FROM runs
                WHERE synced = 0
                ORDER BY timestamp_epoch_millis ASC
                """
            ).fetchall()
        return [_row_to_record(row) for row in rows]

    def all_runs(self) -> List[RunRecord]:
        """All runs, most recent first - mirrors RunDao.observeAll()'s ordering."""
        with self._lock:
            rows = self._conn.execute(
                """
                SELECT id, timestamp_epoch_millis, total_elapsed_millis, shot_timestamps_millis, par_time_seconds, synced
                FROM runs
                ORDER BY timestamp_epoch_millis DESC
                """
            ).fetchall()
        return [_row_to_record(row) for row in rows]

    def get_run(self, run_id: int) -> Optional[RunRecord]:
        with self._lock:
            row = self._conn.execute(
                """
                SELECT id, timestamp_epoch_millis, total_elapsed_millis, shot_timestamps_millis, par_time_seconds, synced
                FROM runs
                WHERE id = ?
                """,
                (run_id,),
            ).fetchone()
        return _row_to_record(row) if row is not None else None


def _row_to_record(row) -> RunRecord:
    row_id, timestamp_epoch_millis, total_elapsed_millis, shot_timestamps_millis, par_time_seconds, synced = row
    return RunRecord(
        id=row_id,
        timestamp_epoch_millis=timestamp_epoch_millis,
        total_elapsed_millis=total_elapsed_millis,
        shot_timestamps_millis=_decode_shot_timestamps(shot_timestamps_millis),
        par_time_seconds=par_time_seconds,
        synced=bool(synced),
    )

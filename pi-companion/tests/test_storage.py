"""SQLite round-trip tests for storage.py. No hardware needed - just a real (temp-file or
in-memory) SQLite database, same as the production path minus the location.
"""

from shot_timer_pi.storage import RunRecord, RunStorage


def test_save_run_round_trips_all_fields(tmp_path):
    with RunStorage(tmp_path / "runs.db") as storage:
        row_id = storage.save_run(
            RunRecord(
                timestamp_epoch_millis=1_700_000_000_000,
                total_elapsed_millis=2580,
                shot_timestamps_millis=[870, 1200, 2500],
                par_time_seconds=None,
            )
        )

        run = storage.get_run(row_id)
        assert run is not None
        assert run.id == row_id
        assert run.timestamp_epoch_millis == 1_700_000_000_000
        assert run.total_elapsed_millis == 2580
        assert run.shot_timestamps_millis == [870, 1200, 2500]
        assert run.par_time_seconds is None
        assert run.synced is False


def test_save_run_round_trips_par_time_seconds(tmp_path):
    with RunStorage(tmp_path / "runs.db") as storage:
        row_id = storage.save_run(
            RunRecord(
                timestamp_epoch_millis=1_700_000_000_000,
                total_elapsed_millis=5000,
                shot_timestamps_millis=[],
                par_time_seconds=5.0,
            )
        )
        run = storage.get_run(row_id)
        assert run.par_time_seconds == 5.0


def test_empty_shot_list_round_trips_to_empty_list(tmp_path):
    # Mirrors Converters.toShotTimestamps's "blank string -> empty list" behavior - a run
    # with zero shots (e.g. the shooter never fired) must not round-trip to [0] or [""].
    with RunStorage(tmp_path / "runs.db") as storage:
        row_id = storage.save_run(
            RunRecord(
                timestamp_epoch_millis=1_700_000_000_000,
                total_elapsed_millis=3000,
                shot_timestamps_millis=[],
            )
        )
        run = storage.get_run(row_id)
        assert run.shot_timestamps_millis == []


def test_ids_assigned_in_increasing_order(tmp_path):
    with RunStorage(tmp_path / "runs.db") as storage:
        first_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=1, total_elapsed_millis=100, shot_timestamps_millis=[50])
        )
        second_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=2, total_elapsed_millis=200, shot_timestamps_millis=[150])
        )
        assert second_id > first_id


def test_get_run_returns_none_for_missing_id(tmp_path):
    with RunStorage(tmp_path / "runs.db") as storage:
        assert storage.get_run(999) is None


def test_new_runs_start_unsynced_and_appear_in_unsynced_runs(tmp_path):
    with RunStorage(tmp_path / "runs.db") as storage:
        row_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=1, total_elapsed_millis=100, shot_timestamps_millis=[50])
        )
        unsynced_ids = [run.id for run in storage.unsynced_runs()]
        assert unsynced_ids == [row_id]


def test_mark_synced_removes_a_run_from_unsynced_runs(tmp_path):
    with RunStorage(tmp_path / "runs.db") as storage:
        row_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=1, total_elapsed_millis=100, shot_timestamps_millis=[50])
        )
        storage.mark_synced(row_id)

        assert storage.unsynced_runs() == []
        run = storage.get_run(row_id)
        assert run.synced is True


def test_unsynced_runs_ordered_oldest_first(tmp_path):
    with RunStorage(tmp_path / "runs.db") as storage:
        newer_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=2_000, total_elapsed_millis=100, shot_timestamps_millis=[])
        )
        older_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=1_000, total_elapsed_millis=100, shot_timestamps_millis=[])
        )
        assert [run.id for run in storage.unsynced_runs()] == [older_id, newer_id]


def test_all_runs_ordered_most_recent_first(tmp_path):
    with RunStorage(tmp_path / "runs.db") as storage:
        older_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=1_000, total_elapsed_millis=100, shot_timestamps_millis=[])
        )
        newer_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=2_000, total_elapsed_millis=100, shot_timestamps_millis=[])
        )
        assert [run.id for run in storage.all_runs()] == [newer_id, older_id]


def test_data_persists_across_reconnecting_to_the_same_db_file(tmp_path):
    # Exercises real on-disk persistence (not :memory:) - the same property the Pi relies on
    # to survive a reboot with unsynced runs still queued up.
    db_path = tmp_path / "runs.db"

    with RunStorage(db_path) as storage:
        row_id = storage.save_run(
            RunRecord(timestamp_epoch_millis=42, total_elapsed_millis=1234, shot_timestamps_millis=[100, 200])
        )

    with RunStorage(db_path) as reopened:
        run = reopened.get_run(row_id)
        assert run is not None
        assert run.total_elapsed_millis == 1234
        assert run.shot_timestamps_millis == [100, 200]

"""Capture/compare emulator data around the upgrade without printing personal rows.

Run before installing: python verification/check-schedule-data.py before
Run after opening the upgraded app: python verification/check-schedule-data.py after
Snapshots are local-only, gitignored, and must not be committed.
"""
import argparse
import os
from pathlib import Path
import sqlite3
import subprocess

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument("stage", choices=["before", "after"])
parser.add_argument("--adb", default=str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"))
args = parser.parse_args()
folder = ROOT / ("schedule-" + args.stage)
if folder.exists() and any(folder.iterdir()):
    raise SystemExit("Snapshot already exists; refusing to overwrite it.")
folder.mkdir(exist_ok=True)
subprocess.run([args.adb, "shell", "am", "force-stop", "com.example.th9"], check=True)
for name in ["attendance.db", "attendance.db-wal", "attendance.db-shm"]:
    result = subprocess.run([args.adb, "exec-out", "run-as", "com.example.th9", "cat", "databases/" + name], capture_output=True)
    if result.returncode == 0:
        (folder / name).write_bytes(result.stdout)
    elif name == "attendance.db":
        raise SystemExit("Could not read installed database.")


def quote(name):
    return '"' + name.replace('"', '""') + '"'


with sqlite3.connect(folder / "attendance.db") as current:
    print("Schema:", current.execute("PRAGMA user_version").fetchone()[0])
    assert current.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    assert current.execute("PRAGMA foreign_key_check").fetchall() == []
    if args.stage == "after":
        assert current.execute("PRAGMA user_version").fetchone()[0] == 5
        with sqlite3.connect(ROOT / "schedule-before/attendance.db") as previous:
            tables = [x[0] for x in previous.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name != 'room_master_table'")]
            for table in tables:
                columns = [x[1] for x in previous.execute("PRAGMA table_info(" + quote(table) + ")")]
                query = "SELECT " + ",".join(map(quote, columns)) + " FROM " + quote(table)
                old = previous.execute(query).fetchall()
                new = current.execute(query).fetchall()
                # Preserve duplicate rows as well as all values; repr handles nullable mixed types.
                assert sorted(map(repr, old)) == sorted(map(repr, new)), table + " changed"
                print(table + ": " + str(len(old)) + " rows, all original columns unchanged")
        assert current.execute("SELECT COUNT(*) FROM ClassSchedule").fetchone()[0] == 0
        assert current.execute("SELECT COUNT(*) FROM AttendanceSession WHERE scheduleId IS NOT NULL").fetchone()[0] == 0
        print("PASS: migration preserves installed data, sequences, assignments, credentials and login.")

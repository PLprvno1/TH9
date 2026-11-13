"""Compare old rows/columns against the pre-upgrade local snapshot. Stops app for a consistent copy.
Run only after instrumentation has completed. Never modifies emulator database contents.
"""
import io,json,os,pathlib,sqlite3,subprocess,tarfile
root=pathlib.Path(__file__).resolve().parent
adb=pathlib.Path(os.environ.get("LOCALAPPDATA",str(pathlib.Path.home()/"AppData/Local")))/"Android/Sdk/platform-tools/adb.exe"
subprocess.run([str(adb),"shell","am","force-stop","com.example.th9"],check=True,capture_output=True)
result=subprocess.run([str(adb),"exec-out","run-as","com.example.th9","tar","-cf","-","databases","shared_prefs","files"],check=True,capture_output=True)
folder=root/"student-card-after";folder.mkdir(exist_ok=True)
with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
    for name in ["attendance.db","attendance.db-wal","attendance.db-shm"]:
        try:(folder/name).write_bytes(archive.extractfile("databases/"+name).read())
        except KeyError:pass
baseline=json.loads((root/"student-card-before/baseline.json").read_text(encoding="utf-8"))
con=sqlite3.connect(folder/"attendance.db");con.row_factory=sqlite3.Row
lines=[]
for table,rows in baseline.items():
    current=[dict(row) for row in con.execute("SELECT * FROM "+table)]
    for previous in rows:
        assert any(all(row.get(k)==value for k,value in previous.items()) for row in current),"Old row changed in "+table
    lines.append(f"PASS {table}: {len(rows)} original rows preserved; current rows {len(current)}")
assert con.execute("PRAGMA user_version").fetchone()[0]==2
assert not con.execute("PRAGMA foreign_key_check").fetchall()
active=con.execute("SELECT COUNT(*) FROM AttendanceSession WHERE status=?",("OPEN",)).fetchone()[0]
lines.extend(["PASS schema version 2; foreign_key_check empty",f"OPEN sessions: {active}"])
con.close()
with tarfile.open(root/"pre-student-card-data.tar") as before,tarfile.open(fileobj=io.BytesIO(result.stdout)) as after:
    for member in before.getmembers():
        if member.isfile() and member.name.startswith("shared_prefs/"):
            # Settings are not touched by the new module. Camera permission bookkeeping may differ.
            original=before.extractfile(member.name).read();updated=after.extractfile(member.name).read()
            if b'name="sound"' in original or b'name="vibrate"' in original or b'name="theme"' in original:
                import xml.etree.ElementTree as ET
                old={n.attrib["name"]:ET.tostring(n) for n in ET.fromstring(original)}
                new={n.attrib["name"]:ET.tostring(n) for n in ET.fromstring(updated)}
                for key in ("sound","vibrate","theme"):
                    if key in old:assert old[key]==new.get(key),"Settings changed: "+key
                lines.append("PASS original sound/vibration/theme settings preserved")
(root/"student-card-data-preservation.log").write_text("\n".join(lines)+"\n",encoding="utf-8")
print("\n".join(lines))

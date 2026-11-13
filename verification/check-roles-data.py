"""Read-only verification after instrumentation; stops app to copy consistent private data."""
import io,json,os,pathlib,sqlite3,subprocess,tarfile
root=pathlib.Path(__file__).resolve().parent
adb=pathlib.Path(os.environ["LOCALAPPDATA"])/"Android/Sdk/platform-tools/adb.exe"
subprocess.run([str(adb),"shell","am","force-stop","com.example.th9"],check=True,capture_output=True)
result=subprocess.run([str(adb),"exec-out","run-as","com.example.th9","tar","-cf","-","databases","shared_prefs","files"],check=True,capture_output=True)
folder=root/"roles-after";folder.mkdir(exist_ok=True)
(folder/"app-data.tar").write_bytes(result.stdout)
with tarfile.open(fileobj=io.BytesIO(result.stdout)) as archive:
    for name in ["attendance.db","attendance.db-wal","attendance.db-shm"]:
        (folder/name).unlink(missing_ok=True)
        try:(folder/name).write_bytes(archive.extractfile("databases/"+name).read())
        except KeyError:pass
baseline=json.loads((root/"roles-before/baseline.json").read_text(encoding="utf-8"))
con=sqlite3.connect(folder/"attendance.db");con.row_factory=sqlite3.Row
lines=[]
for table,rows in baseline.items():
    current=[dict(row) for row in con.execute('SELECT * FROM "'+table+'"')]
    assert len(current)==len(rows),"Row count changed in "+table
    for previous in rows:
        assert any(all(row.get(k)==value for k,value in previous.items()) for row in current),"Old row changed in "+table
    lines.append(f"PASS {table}: all {len(rows)} original rows and original columns unchanged")
assert con.execute("PRAGMA user_version").fetchone()[0]==4
assert not con.execute("PRAGMA foreign_key_check").fetchall()
assert con.execute("PRAGMA integrity_check").fetchone()[0]=="ok"
for table in ["UserAccount","TeacherClass","LocalLogin"]:
    assert con.execute('SELECT COUNT(*) FROM '+table).fetchone()[0]==0,"Tests polluted production "+table
lines.append("PASS schema version 4; foreign_key_check empty; integrity_check ok")
lines.append("PASS production accounts, assignments and login empty: first Admin setup remains available")
con.close()
with tarfile.open(root/"roles-before/app-data.tar") as before,tarfile.open(fileobj=io.BytesIO(result.stdout)) as after:
    checked=0
    for member in before.getmembers():
        # AndroidX ProfileInstaller updates this APK installation marker on app update.
        if member.name=="files/profileInstalled":continue
        if member.isfile() and (member.name.startswith("shared_prefs/") or member.name.startswith("files/")):
            assert before.extractfile(member.name).read()==after.extractfile(member.name).read(),"Original file changed: "+member.name
            checked+=1
    lines.append(f"PASS all {checked} original settings and student photo files byte-for-byte unchanged")
(root/"roles-data-preservation.log").write_text("\n".join(lines)+"\n",encoding="utf-8")
print("\n".join(lines))

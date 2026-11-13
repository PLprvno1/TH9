package com.example.th9;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.data.*;
import com.example.th9.model.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
/** Isolated databases only. Never deletes or resets the installed attendance database. */
@RunWith(AndroidJUnit4.class)
public class RolesInstrumentedTest {
    Context context; AttendanceDatabase db; AttendanceRepository r;
    static final String PASS="TestPass123!";
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();db=Room.inMemoryDatabaseBuilder(context,AttendanceDatabase.class).build();r=new AttendanceRepository(db);r.initialize();r.setupAdmin("admin","Admin",PASS);}
    @After public void close(){db.close();}
    interface Operation {void run() throws Exception;}
    void rejected(Operation action){try{action.run();fail("Operation must be denied");}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}catch(Exception failure){throw new AssertionError(failure);}}
    ClassRoom classroom(String name){ClassRoom c=new ClassRoom(null,name,name,"");r.addClass(c);return c;}
    Student student(String course,ClassRoom c){Student s=new Student(null,"","Sinh viên",null,"","",0);s.setBirthDate("01/01/2005");s.setGender("Nữ");s.setCourse(course);return r.registerStudent(s,c==null?null:c.getId());}
    AttendanceSession session(ClassRoom c){AttendanceSession s=new AttendanceSession(null,c.getId(),"Buổi thử","10/11/2026","07:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0);r.addSession(s);return s;}
    void login(String username){r.logout();r.login(username,PASS);}
    @Test public void generatedNumbersImmutableAndMembershipAtomic(){
        ClassRoom c=classroom("A");Student a=student("2020–2026",c),b=student("2020–2026",null),other=student("2021–2027",null);
        assertEquals("202020260001",a.getMssv());assertEquals(a.getMssv(),a.getDigitalCardCode());assertEquals("202020260002",b.getMssv());assertEquals("202120270001",other.getMssv());assertEquals(1,r.getStudentsForClass(c.getId()).size());
        a.setCourse("2022–2028");a.setMssv("hacked");rejected(()->r.updateStudent(a));a.setMssv("202020260001");r.updateStudent(a);assertEquals("202020260001",r.getStudentById(a.getId()).getMssv());
        r.deleteStudent(b.getId());assertEquals("202020260003",student("2020–2026",null).getMssv());
        Student invalid=new Student(null,"","Invalid",null,"","",0);invalid.setCourse("2020–2026");invalid.setBirthDate("01/01/2005");invalid.setGender("Nam");rejected(()->r.registerStudent(invalid,"missing-class"));assertEquals("202020260004",student("2020–2026",null).getMssv());
    }
    @Test public void teacherScopeAndBusinessPermissions()throws Exception{
        ClassRoom a=classroom("A"),b=classroom("B");Student sa=student("2020–2026",a),sb=student("2020–2026",b);String t=r.createTeacher("teacher","Teacher",PASS);r.setAssignment(t,a.getId(),true);AttendanceSession hidden=session(b);
        login("teacher");assertFalse(r.isAdmin());assertEquals(1,r.getAllClasses().size());assertEquals(1,r.getAllStudents().size());assertNull(r.getClassById(b.getId()));assertNull(r.getStudentById(sb.getId()));assertNull(r.getSessionById(hidden.getId()));
        rejected(()->r.getRecordsForSession(hidden.getId()));rejected(()->r.verifyCard(sb.getMssv()));rejected(()->CsvTransfer.csv(r,hidden.getId()));
        rejected(()->r.addClass(new ClassRoom(null,"X","", "")));rejected(()->r.updateClass(a));rejected(()->r.deleteClass(a.getId()));rejected(()->r.updateStudent(sa));rejected(()->r.deleteStudent(sa.getId()));rejected(()->student("2020–2026",a));rejected(()->r.addExistingToClass(sb.getId(),a.getId()));rejected(()->r.removeFromClass(sa.getId(),a.getId()));rejected(()->r.linkCardBarcode(sa.getId(),"new"));rejected(()->r.issueDigitalCard(sa.getId()));rejected(()->r.createTeacher("bad","Bad",PASS));rejected(()->r.setAssignment(t,b.getId(),true));rejected(()->r.importCsv(a.getId(),Collections.emptyList(),false));rejected(()->session(b));
        assertEquals(sa.getId(),r.verifyCard(sa.getMssv()).getId());AttendanceSession own=session(a);r.openSession(own.getId());r.scan(own.getId(),sa.getMssv(),0);r.finishSession(own.getId());assertEquals(1,r.getCheckedInTotal(own.getId()));assertTrue(CsvTransfer.csv(r,own.getId()).contains("Teacher"));
        r.logout();assertNull(r.currentAccount());rejected(()->r.getAllClasses());rejected(()->r.manual(own.getId(),sa.getId(),0));
    }
    @Test public void multipleTeachersOpenLimitsSnapshotsAndRevocation()throws Exception{
        ClassRoom a=classroom("A"),b=classroom("B"),c=classroom("C");Student sa=student("2020–2026",a),sb=student("2020–2026",b);student("2020–2026",c);Student absent=student("2020–2026",a);
        String t1=r.createTeacher("teacher1","Teacher One",PASS),t2=r.createTeacher("teacher2","Teacher Two",PASS);
        r.replaceAssignments(t1,Arrays.asList(a.getId(),c.getId()));rejected(()->r.replaceAssignments(t2,Arrays.asList(a.getId(),b.getId())));r.replaceAssignments(t2,Collections.singletonList(b.getId()));
        login("teacher1");AttendanceSession one=session(a),sameTeacher=session(c);r.openSession(one.getId());rejected(()->r.openSession(sameTeacher.getId()));
        login("teacher2");AttendanceSession two=session(b);rejected(()->session(a));r.openSession(two.getId());rejected(()->r.openSession(one.getId()));rejected(()->r.finishSession(one.getId()));rejected(()->r.manual(one.getId(),sa.getId(),0));assertFalse(r.canControlSession(one.getId()));r.manual(two.getId(),sb.getId(),0);r.finishSession(two.getId());
        login("admin");assertEquals(2,r.getRecordsForSession(one.getId()).size());rejected(()->r.setAssignment(t1,a.getId(),false));rejected(()->r.updateTeacher(t1,"Renamed",false,null));r.removeFromClass(absent.getId(),a.getId());sa.setFullName("Changed");r.updateStudent(sa);
        login("teacher1");assertEquals(2,r.getSessionById(one.getId()).getExpectedCount());assertEquals("Sinh viên",r.getRecordForStudent(one.getId(),sa.getId()).getNameSnapshot());r.scan(one.getId(),sa.getMssv(),0);r.finishSession(one.getId());assertEquals(1,r.getAbsentCount(one.getId()));
        login("admin");r.updateTeacher(t1,"Renamed",true,null);r.setAssignment(t1,a.getId(),false);assertEquals("Teacher One",r.getSessionById(one.getId()).getTeacherNameSnapshot());assertEquals(t1,r.getSessionById(one.getId()).getTeacherId());assertTrue(CsvTransfer.csv(r,one.getId()).contains("Teacher One"));
        login("teacher1");assertNull(r.getSessionById(one.getId()));rejected(()->r.getRecordsForSession(one.getId()));assertEquals(1,r.getAllClasses().size());
    }
    @Test public void passwordsLockDisableResetAndPersistentLogin(){
        String t=r.createTeacher("teacher","Teacher",PASS);AttendanceDatabase.UserAccount account=db.store().user(t);assertNotEquals(PASS,account.passwordHash);assertFalse(account.salt.isEmpty());assertTrue(account.iterations>=600000);
        r.logout();for(int i=0;i<5;i++)rejected(()->r.login("teacher","wrong"));assertEquals(0,db.store().user(t).failedAttempts);assertTrue(db.store().user(t).lockedUntil>System.currentTimeMillis());rejected(()->r.login("teacher",PASS));
        r.login("admin",PASS);r.updateTeacher(t,"Teacher",true,"ResetPass123!");r.logout();rejected(()->r.login("teacher",PASS));r.login("teacher","ResetPass123!");assertEquals(t,r.currentAccount().id);
        r.logout();r.login("admin",PASS);r.updateTeacher(t,"Teacher",false,null);r.logout();rejected(()->r.login("teacher","ResetPass123!"));
        String name="roles-persist-"+UUID.randomUUID()+".db",prefsName="roles-"+UUID.randomUUID();SharedPreferences prefs=context.getSharedPreferences(prefsName,Context.MODE_PRIVATE);AttendanceDatabase disk=Room.databaseBuilder(context,AttendanceDatabase.class,name).build();
        try{AttendanceRepository x=new AttendanceRepository(disk,prefs);x.initialize();x.setupAdmin("persist","Persist",PASS);String id=x.currentAccount().id;ClassRoom c=new ClassRoom(null,"Persist","","");x.addClass(c);Student s=new Student(null,"","Saved",null,"","",0);s.setBirthDate("01/01/2005");s.setGender("Nam");s.setCourse("2020–2026");x.registerStudent(s,c.getId());assertNotEquals(prefs.getString("session_token",""),disk.store().login().tokenHash);disk.close();
            disk=Room.databaseBuilder(context,AttendanceDatabase.class,name).build();x=new AttendanceRepository(disk,prefs);x.initialize();assertEquals(id,x.currentAccount().id);assertEquals("202020260001",x.getAllStudents().get(0).getMssv());assertEquals(1,disk.store().numberSequence("20202026").lastNumber);x.logout();AttendanceRepository y=new AttendanceRepository(disk,prefs);y.initialize();assertNull(y.currentAccount());
        }finally{disk.close();context.deleteDatabase(name);prefs.edit().clear().commit();}
    }
    @Test public void migrationV3ToV4PreservesLegacyAndSequence()throws Exception{migration(3);}
    @Test public void fullMigrationV1ToV4PreservesLegacy()throws Exception{migration(1);}
    void migration(int version)throws Exception{
        String name="roles-migrate-"+UUID.randomUUID()+".db";File file=context.getDatabasePath(name);file.getParentFile().mkdirs();String json;
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("com.example.th9.data.AttendanceDatabase/"+version+".json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);json=out.toString("UTF-8");}
        SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(file,null);JSONArray tables=new JSONObject(json).getJSONObject("database").getJSONArray("entities");for(int i=0;i<tables.length();i++){JSONObject e=tables.getJSONObject(i);String table=e.getString("tableName");old.execSQL(e.getString("createSql").replace("${TABLE_NAME}",table));JSONArray indices=e.optJSONArray("indices");if(indices!=null)for(int j=0;j<indices.length();j++)old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}",table));}
        old.execSQL("INSERT INTO Classroom VALUES ('c','Old class','OLD','')");old.execSQL("INSERT INTO Student (id,mssv,fullName,cardCode,email,phone,avatarColor) VALUES ('s','0001','Old name','LEGACY:0001','','',0)");old.execSQL("INSERT INTO ClassMembership VALUES ('c','s')");old.execSQL("INSERT INTO AttendanceSession (id,classId,title,scheduledAt,status,classNameSnapshot,classCodeSnapshot) VALUES ('open','c','Old session',1,'OPEN','Old class','OLD')");old.execSQL("INSERT INTO AttendanceRecord (sessionId,studentId,mssvSnapshot,nameSnapshot,status) VALUES ('open','s','0001','Old name','PENDING')");if(version==3){old.execSQL("INSERT INTO StudentNumberSequence VALUES ('20202026',42)");old.execSQL("UPDATE Student SET gender='Nam',course='2020–2026',photoPath='/private/old.jpg',digitalCardCode='OLD-DIGITAL'");}old.setVersion(version);old.close();
        AttendanceDatabase migrated=Room.databaseBuilder(context,AttendanceDatabase.class,name).addMigrations(AttendanceDatabase.MIGRATION_1_2,AttendanceDatabase.MIGRATION_2_3,AttendanceDatabase.MIGRATION_3_4,AttendanceDatabase.MIGRATION_4_5).build();
        try{AttendanceRepository x=new AttendanceRepository(migrated);x.initialize();assertTrue(x.needsAdminSetup());assertNull(migrated.store().session("open").teacherId);assertEquals("Old name",migrated.store().records().get(0).nameSnapshot);if(version==3){assertEquals(42,migrated.store().numberSequence("20202026").lastNumber);assertEquals("/private/old.jpg",migrated.store().student("s").photoPath);}x.setupAdmin("admin","Admin",PASS);assertEquals("Dữ liệu trước phân quyền",x.getSessionById("open").getTeacherNameSnapshot());assertEquals("s",x.verifyCard("LEGACY:0001").getId());x.scan("open","LEGACY:0001",0);x.finishSession("open");assertEquals(1,x.getCheckedInTotal("open"));if(version==3){Student s=new Student(null,"","New",null,"","",0);s.setBirthDate("01/01/2005");s.setGender("Nam");s.setCourse("2020–2026");assertEquals("202020260043",x.registerStudent(s,"c").getMssv());}}
        finally{migrated.close();context.deleteDatabase(name);}
    }
}

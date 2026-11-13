package com.example.th9;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.data.*;
import com.example.th9.model.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Each test owns an isolated Room database; installed application data is never reset. */
@RunWith(AndroidJUnit4.class)
public class ScheduleInstrumentedTest {
    private Context context;private AttendanceDatabase db;private AttendanceRepository r;
    private static final String PASS="TestPass123!";
    private ClassRoom a,b;private String t,u;private Student student;
    @Before public void setup(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();db=Room.inMemoryDatabaseBuilder(context,AttendanceDatabase.class).build();
        r=new AttendanceRepository(db);r.initialize();r.setupAdmin("admin","Admin",PASS);
        a=new ClassRoom(null,"A","A","");b=new ClassRoom(null,"B","B","");r.addClass(a);r.addClass(b);
        t=r.createTeacher("teacher","Teacher",PASS);u=r.createTeacher("other","Other",PASS);r.setAssignment(t,a.getId(),true);r.setAssignment(u,b.getId(),true);
        Student s=new Student(null,"","Student",null,"","",0);s.setBirthDate("01/01/2005");s.setGender("Nam");s.setCourse("2020–2026");student=r.registerStudent(s,a.getId());r.addExistingToClass(student.getId(),b.getId());
    }
    @After public void close(){db.close();}
    private void login(String name){r.logout();r.login(name,PASS);}
    private void rejected(Runnable action){try{action.run();fail("Expected rejection");}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}}
    private ClassSchedule schedule(ClassRoom c){ClassSchedule s=new ClassSchedule();s.classId=c.getId();s.startDate="2026-11-16";s.endDate="2027-01-15";s.weekdays=1|4|16;return s;}
    private ClassSchedule save(ClassSchedule s){return r.getSchedule(r.saveSchedule(s));}
    @Test public void oneTeacherPerClassIsEnforcedAcrossAllAssignmentMethods(){
        rejected(()->r.replaceClassTeachers(a.getId(),Arrays.asList(t,u)));
        rejected(()->r.setAssignment(u,a.getId(),true));
        rejected(()->r.replaceAssignments(u,Arrays.asList(a.getId(),b.getId())));
        assertEquals(Collections.singletonList(t),r.assignedTeacherIds(a.getId()));
        assertEquals(Collections.singletonList(b.getId()),r.assignedClassIds(u));
        r.replaceClassTeachers(a.getId(),Collections.singletonList(u));
        assertEquals(Collections.singletonList(u),r.assignedTeacherIds(a.getId()));
        assertTrue(r.assignedClassIds(t).isEmpty());
        assertEquals(2,r.assignedClassIds(u).size());
        r.setAssignment(u,a.getId(),true);
        assertEquals(1,r.assignedTeacherIds(a.getId()).size());
    }
    @Test public void classTeacherSelectionIsAtomicScopedAndProtectsOpenSessions(){
        save(schedule(a));ClassSchedule sb=save(schedule(b));rejected(()->r.replaceClassTeachers(a.getId(),Arrays.asList(t,u)));assertEquals(Collections.singletonList(t),r.assignedTeacherIds(a.getId()));
        rejected(()->r.replaceClassTeachers(a.getId(),Collections.singletonList(u)));assertEquals(Collections.singletonList(t),r.assignedTeacherIds(a.getId()));
        r.archiveSchedule(sb.id);r.replaceClassTeachers(a.getId(),Collections.singletonList(u));assertEquals(Collections.singletonList(u),r.assignedTeacherIds(a.getId()));assertTrue(r.assignedClassIds(u).contains(b.getId()));assertFalse(r.assignedClassIds(t).contains(a.getId()));
        login("other");String session=r.startScheduledSession(r.getScheduleForClass(a.getId()).id,"2026-11-16");rejected(()->r.replaceClassTeachers(a.getId(),Collections.emptyList()));rejected(()->r.assignedTeacherIds(a.getId()));
        login("admin");rejected(()->r.replaceClassTeachers(a.getId(),Collections.emptyList()));assertEquals(Collections.singletonList(u),r.assignedTeacherIds(a.getId()));
        login("other");r.finishSession(session);login("admin");r.updateTeacher(t,"Teacher",false,null);rejected(()->r.replaceClassTeachers(a.getId(),Collections.singletonList(t)));assertEquals(Collections.singletonList(u),r.assignedTeacherIds(a.getId()));
        r.replaceClassTeachers(a.getId(),Collections.emptyList());assertTrue(r.assignedTeacherIds(a.getId()).isEmpty());assertEquals(Collections.singletonList(b.getId()),r.assignedClassIds(u));
    }
    @Test public void homeSuggestionsAreScopedReadOnlyTimedAndExcludeClosedSessions(){
        ClassSchedule own=save(schedule(a));save(schedule(b));long start=AttendanceRepository.parseDateTime("16/11/2026","07:00");
        assertTrue(r.getCurrentScheduleSuggestions(start).isEmpty());login("teacher");
        assertTrue(r.getCurrentScheduleSuggestions(start-1).isEmpty());assertEquals(1,r.getCurrentScheduleSuggestions(start).size());assertEquals(own.id,r.getCurrentScheduleSuggestions(start).get(0).id);
        assertTrue(r.getCurrentScheduleSuggestions(AttendanceRepository.parseDateTime("16/11/2026","10:00")).isEmpty());
        assertTrue(r.getCurrentScheduleSuggestions(AttendanceRepository.parseDateTime("17/11/2026","08:00")).isEmpty());assertTrue(db.store().sessions().isEmpty());assertTrue(db.store().records().isEmpty());
        String id=r.startScheduledSession(own.id,"2026-11-16");assertEquals(1,r.getCurrentScheduleSuggestions(start).size());r.finishSession(id);assertTrue(r.getCurrentScheduleSuggestions(start).isEmpty());
        login("admin");r.setAssignment(t,a.getId(),false);login("teacher");assertTrue(r.getCurrentScheduleSuggestions(AttendanceRepository.parseDateTime("18/11/2026","08:00")).isEmpty());
    }
    @Test public void createEditArchiveAndRejectNoOccurrence(){
        ClassSchedule s=save(schedule(a));assertEquals(1,r.getSchedules().size());s.endMinute=660;r.saveSchedule(s);assertEquals(660,r.getSchedule(s.id).endMinute);
        ClassSchedule bad=schedule(b);bad.startDate="2026-11-17";bad.endDate=bad.startDate;bad.weekdays=1;rejected(()->r.saveSchedule(bad));
        r.archiveSchedule(s.id);assertNull(r.getSchedule(s.id));assertTrue(db.store().schedule(s.id).archived);assertTrue(r.getAllSessions().isEmpty());
    }
    @Test public void sameClassAndTeacherConflictsAndTouchingTimes(){
        save(schedule(a));rejected(()->r.saveSchedule(schedule(a)));
        ClassSchedule adjacent=schedule(a);adjacent.startMinute=600;adjacent.endMinute=660;rejected(()->r.saveSchedule(adjacent));
        r.setAssignment(u,b.getId(),false);r.setAssignment(t,b.getId(),true);rejected(()->r.saveSchedule(schedule(b)));
        adjacent.classId=b.getId();save(adjacent);
    }
    @Test public void confirmedOverwriteReusesIdAndPreservesAttendanceHistory(){
        ClassSchedule original=save(schedule(a));login("teacher");String sessionId=r.startScheduledSession(original.id,"2026-11-16");r.finishSession(sessionId);login("admin");
        ClassSchedule replacement=schedule(a);replacement.startMinute=480;replacement.endMinute=660;
        rejected(()->r.saveSchedule(replacement));assertEquals(420,r.getSchedule(original.id).startMinute);
        assertEquals(original.id,r.saveSchedule(replacement,true));assertEquals(1,r.getSchedules().size());assertEquals(480,r.getSchedule(original.id).startMinute);
        AttendanceSession history=r.getSessionById(sessionId);assertEquals(original.id,history.getScheduleId());assertEquals(Integer.valueOf(420),history.getPlannedStartMinute());assertEquals(AttendanceSession.SessionStatus.CLOSED,history.getStatus());assertEquals(1,r.getRecordsForSession(sessionId).size());
    }
    @Test public void overwriteConsolidatesLegacyDuplicatesAndConflictRollsBack(){
        ClassSchedule original=save(schedule(a));ClassSchedule duplicate=schedule(a);duplicate.id=AttendanceRepository.id();duplicate.shift="AFTERNOON";duplicate.startMinute=780;duplicate.endMinute=960;
        db.store().insertSchedule(duplicate);r.reload();assertEquals(2,r.getSchedules().size());
        rejected(()->r.saveSchedule(original));r.setAssignment(u,b.getId(),false);r.setAssignment(t,b.getId(),true);
        ClassSchedule other=schedule(b);other.startMinute=600;other.endMinute=660;save(other);
        ClassSchedule replacement=schedule(a);replacement.startMinute=600;replacement.endMinute=660;rejected(()->r.saveSchedule(replacement,true));
        assertFalse(db.store().schedule(duplicate.id).archived);assertEquals(420,r.getSchedule(original.id).startMinute);
        assertEquals(original.id,r.saveSchedule(original,true));assertTrue(db.store().schedule(duplicate.id).archived);assertNotNull(db.store().schedule(duplicate.id));assertEquals(2,r.getSchedules().size());
    }
    @Test public void assignmentConflictRollsBackBothApis(){
        save(schedule(a));save(schedule(b));r.setAssignment(u,b.getId(),false);rejected(()->r.setAssignment(t,b.getId(),true));assertFalse(r.assignedClassIds(t).contains(b.getId()));
        rejected(()->r.replaceAssignments(t,Arrays.asList(a.getId(),b.getId())));assertEquals(Collections.singletonList(a.getId()),r.assignedClassIds(t));
        ClassSchedule changed=r.getSchedules().get(1);if(!changed.classId.equals(b.getId()))changed=r.getSchedules().get(0);
        r.setAssignment(u,b.getId(),true);changed.startMinute=600;changed.endMinute=660;r.saveSchedule(changed);r.setAssignment(u,b.getId(),false);r.replaceAssignments(t,Arrays.asList(a.getId(),b.getId()));assertEquals(2,r.assignedClassIds(t).size());
    }
    @Test public void scopeIsEnforcedAtReadAndWrite(){
        ClassSchedule own=save(schedule(a)),hidden=save(schedule(b));login("teacher");assertEquals(1,r.getSchedules().size());assertNull(r.getSchedule(hidden.id));
        rejected(()->r.startScheduledSession(hidden.id,"2026-11-16"));rejected(()->r.saveSchedule(own));rejected(()->r.archiveSchedule(own.id));
        rejected(()->r.addExistingStudentsToClass(a.getId(),Collections.singletonList(student.getId())));rejected(()->r.importStudentsCsv(Collections.emptyList(),true));
        rejected(()->r.startScheduledSession(own.id,"2026-11-17"));assertTrue(db.store().sessions().isEmpty());
    }
    @Test public void opensContinuesClosesAndPreservesSnapshotsAfterEditingAndArchiving(){
        ClassSchedule s=save(schedule(a));login("teacher");String id=r.startScheduledSession(s.id,"2026-11-16");
        assertEquals(id,r.startScheduledSession(s.id,"2026-11-16"));assertEquals(1,db.store().sessions().size());assertEquals(1,r.getRecordsForSession(id).size());
        AttendanceSession session=r.getSessionById(id);assertEquals(t,session.getTeacherId());assertEquals(Integer.valueOf(420),session.getPlannedStartMinute());
        r.scan(id,student.getMssv(),"CODE_128",session.getScheduledAt());assertEquals(1,r.getPresentCount(id));rejected(()->r.manual(id,student.getId(),session.getScheduledAt()));
        login("admin");rejected(()->r.setAssignment(t,a.getId(),false));s.startMinute=480;s.endMinute=660;r.saveSchedule(s);assertEquals(Integer.valueOf(420),r.getSessionById(id).getPlannedStartMinute());
        student.setFullName("Renamed");r.updateStudent(student);r.archiveSchedule(s.id);assertEquals("Student",r.getRecordsForSession(id).get(0).getNameSnapshot());
        login("teacher");assertEquals(id,r.startScheduledSession(s.id,"2026-11-16"));r.finishSession(id);assertEquals(id,r.startScheduledSession(s.id,"2026-11-16"));assertEquals(AttendanceSession.SessionStatus.CLOSED,r.getSessionById(id).getStatus());
        rejected(()->r.startScheduledSession(s.id,"2026-11-18"));assertEquals(1,db.store().sessions().size());assertNotNull(db.store().schedule(s.id));
    }
    @Test public void oneOpenPerTeacherAndClassAndAtomicFailure(){
        ClassSchedule sa=save(schedule(a));ClassSchedule later=schedule(b);later.startMinute=600;later.endMinute=660;ClassSchedule sb=save(later);
        login("admin");rejected(()->r.setAssignment(u,a.getId(),true));login("teacher");String one=r.startScheduledSession(sa.id,"2026-11-16");
        login("admin");rejected(()->r.replaceClassTeachers(a.getId(),Collections.singletonList(u)));login("teacher");
        rejected(()->r.startScheduledSession(sa.id,"2026-11-18"));assertEquals(1,db.store().sessions().size());
        login("other");rejected(()->r.startScheduledSession(sa.id,"2026-11-16"));rejected(()->r.startScheduledSession(sa.id,"2026-11-18"));
        String two=r.startScheduledSession(sb.id,"2026-11-16");assertEquals(2,db.store().sessions().size());rejected(()->r.finishSession(one));r.finishSession(two);
    }
    @Test public void teacherCannotOpenSecondAssignedClassAndUnscheduledRemainsSupported(){
        ClassSchedule sa=save(schedule(a));ClassSchedule rule=schedule(b);rule.startMinute=600;rule.endMinute=660;ClassSchedule sb=save(rule);r.replaceClassTeachers(b.getId(),Collections.singletonList(t));
        login("teacher");String id=r.startScheduledSession(sa.id,"2026-11-16");rejected(()->r.startScheduledSession(sb.id,"2026-11-16"));r.finishSession(id);
        AttendanceSession extra=new AttendanceSession(null,a.getId(),"Makeup","16/11/2026","13:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0);
        r.addSession(extra);r.openSession(extra.getId());assertNull(r.getSessionById(extra.getId()).getScheduleId());r.finishSession(extra.getId());assertEquals(1,r.getAbsentCount(extra.getId()));
    }
    @Test public void concurrentStartsAreIdempotent()throws Exception{
        ClassSchedule s=save(schedule(a));login("teacher");ExecutorService pool=Executors.newFixedThreadPool(2);
        try{Future<String> x=pool.submit(()->r.startScheduledSession(s.id,"2026-11-16")),y=pool.submit(()->r.startScheduledSession(s.id,"2026-11-16"));assertEquals(x.get(),y.get());assertEquals(1,db.store().sessions().size());assertEquals(1,db.store().records().size());}finally{pool.shutdownNow();}
    }
    @Test public void migrationV4PreservesAllTablesAndNullableLegacyLink()throws Exception{migration(4);}
    @Test public void fullMigrationV1PreservesHistory()throws Exception{migration(1);}
    private void migration(int version)throws Exception{
        String name="schedule-migration-"+UUID.randomUUID()+".db";File file=context.getDatabasePath(name);file.getParentFile().mkdirs();
        String json;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("com.example.th9.data.AttendanceDatabase/"+version+".json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);json=out.toString("UTF-8");}
        SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(file,null);JSONArray entities=new JSONObject(json).getJSONObject("database").getJSONArray("entities");
        for(int i=0;i<entities.length();i++){JSONObject e=entities.getJSONObject(i);String table=e.getString("tableName");old.execSQL(e.getString("createSql").replace("${TABLE_NAME}",table));JSONArray indices=e.optJSONArray("indices");if(indices!=null)for(int j=0;j<indices.length();j++)old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}",table));}
        old.execSQL("INSERT INTO Classroom VALUES ('c','Old class','OLD','')");old.execSQL("INSERT INTO Student (id,mssv,fullName,cardCode,email,phone,avatarColor) VALUES ('s','0001','Old name','CARD','','',0)");old.execSQL("INSERT INTO ClassMembership VALUES ('c','s')");
        old.execSQL("INSERT INTO AttendanceSession (id,classId,title,scheduledAt,status,classNameSnapshot,classCodeSnapshot) VALUES ('closed','c','History',1,'CLOSED','Original class','OLD')");
        old.execSQL("INSERT INTO AttendanceRecord (sessionId,studentId,mssvSnapshot,nameSnapshot,status) VALUES ('closed','s','0001','Snapshot','ABSENT')");
        if(version==4){
            old.execSQL("INSERT INTO StudentNumberSequence VALUES ('20202026',42)");old.execSQL("UPDATE Student SET digitalCardCode='0001',gender='Nam',course='2020–2026',photoPath='/private/photo.jpg'");
            old.execSQL("INSERT INTO UserAccount VALUES ('t','teacher','Teacher','TEACHER','hash','salt','algorithm',600000,1,0,0)");old.execSQL("INSERT INTO TeacherClass VALUES ('t','c')");old.execSQL("INSERT INTO LocalLogin VALUES (1,'t','tokenhash',123)");
            old.execSQL("UPDATE AttendanceSession SET teacherId='t',teacherNameSnapshot='Original teacher',teacherUsernameSnapshot='teacher'");
        }
        old.setVersion(version);old.close();
        AttendanceDatabase upgraded=Room.databaseBuilder(context,AttendanceDatabase.class,name).addMigrations(AttendanceDatabase.MIGRATION_1_2,AttendanceDatabase.MIGRATION_2_3,AttendanceDatabase.MIGRATION_3_4,AttendanceDatabase.MIGRATION_4_5).build();
        try{
            assertEquals(1,upgraded.store().sessions().size());assertEquals("Snapshot",upgraded.store().records().get(0).nameSnapshot);assertEquals("Original class",upgraded.store().session("closed").classNameSnapshot);assertNull(upgraded.store().session("closed").scheduleId);assertEquals(1,upgraded.store().memberships().size());assertTrue(upgraded.store().schedules().isEmpty());
            if(version==4){assertEquals(42,upgraded.store().numberSequence("20202026").lastNumber);assertEquals("/private/photo.jpg",upgraded.store().student("s").photoPath);assertEquals("Original teacher",upgraded.store().session("closed").teacherNameSnapshot);assertEquals(1,upgraded.store().assignments().size());assertEquals("tokenhash",upgraded.store().login().tokenHash);assertEquals("hash",upgraded.store().user("t").passwordHash);}
            assertEquals(0,upgraded.getOpenHelper().getReadableDatabase().query("PRAGMA foreign_key_check").getCount());
        }finally{upgraded.close();context.deleteDatabase(name);}
    }
}

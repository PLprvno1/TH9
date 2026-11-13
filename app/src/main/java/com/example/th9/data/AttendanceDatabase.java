package com.example.th9.data;

import androidx.annotation.NonNull;
import androidx.room.*;
import android.database.Cursor;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import com.example.th9.model.ClassSchedule;

/** Version 1 is the first persistent schema. Exported schemas must be kept for future migrations.
 * No destructive fallback: an unsupported upgrade must never erase attendance history. */
@Database(entities = {AttendanceDatabase.Classroom.class, AttendanceDatabase.StudentRow.class,
        AttendanceDatabase.ClassMembership.class, AttendanceDatabase.SessionRow.class,
        AttendanceDatabase.RecordRow.class, AttendanceDatabase.StudentNumberSequence.class, AttendanceDatabase.UserAccount.class,
        AttendanceDatabase.TeacherClass.class, AttendanceDatabase.LocalLogin.class, ClassSchedule.class}, version = 6, exportSchema = true)
public abstract class AttendanceDatabase extends RoomDatabase {
    public abstract Store store();
    public static final androidx.room.migration.Migration MIGRATION_4_5 = new androidx.room.migration.Migration(4,5) {
        @Override public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS ClassSchedule (id TEXT NOT NULL, classId TEXT NOT NULL, shift TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT NOT NULL, weekdays INTEGER NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, archived INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(classId) REFERENCES Classroom(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX index_ClassSchedule_classId ON ClassSchedule(classId)");
            db.execSQL("ALTER TABLE AttendanceSession ADD COLUMN scheduleId TEXT REFERENCES ClassSchedule(id) ON UPDATE NO ACTION ON DELETE RESTRICT");
            for(String field:new String[]{"plannedDate","plannedShift"})db.execSQL("ALTER TABLE AttendanceSession ADD COLUMN "+field+" TEXT");
            for(String field:new String[]{"plannedStartMinute","plannedEndMinute"})db.execSQL("ALTER TABLE AttendanceSession ADD COLUMN "+field+" INTEGER");
            db.execSQL("CREATE UNIQUE INDEX index_AttendanceSession_scheduleId_plannedDate ON AttendanceSession(scheduleId,plannedDate)");
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_5_6 = new androidx.room.migration.Migration(5,6) {
        @Override public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
            Map<String,String> phonesByStudent=new LinkedHashMap<>();
            Map<String,Integer> phoneCounts=new HashMap<>();
            try(Cursor cursor=db.query("SELECT id, phone FROM Student ORDER BY id")) {
                while(cursor.moveToNext()) {
                    String id=cursor.getString(0),phone=cursor.isNull(1)?"":cursor.getString(1).trim();
                    phonesByStudent.put(id,phone);
                    if(phone.matches("[0-9]{10}"))phoneCounts.put(phone,phoneCounts.getOrDefault(phone,0)+1);
                }
            }
            Set<String> reserved=new HashSet<>();
            for(String phone:phonesByStudent.values())if(phone.matches("[0-9]{10}")&&phoneCounts.get(phone)==1)reserved.add(phone);
            Set<String> used=new HashSet<>(reserved);
            SecureRandom random=new SecureRandom();
            for(Map.Entry<String,String> entry:phonesByStudent.entrySet()) {
                String phone=entry.getValue();
                if(!phone.matches("[0-9]{10}")||phoneCounts.getOrDefault(phone,0)>1) {
                    String generated;
                    do {
                        StringBuilder digits=new StringBuilder(10);
                        for(int i=0;i<10;i++)digits.append(random.nextInt(10));
                        generated=digits.toString();
                    } while(!used.add(generated));
                    db.execSQL("UPDATE Student SET phone=? WHERE id=?",new Object[]{generated,entry.getKey()});
                }
            }
            db.execSQL("CREATE UNIQUE INDEX index_Student_phone ON Student(phone)");
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1,2) {
        @Override public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
            for(String field:new String[]{"digitalCardCode","birthDate","course","primaryClassId","photoPath"})
                db.execSQL("ALTER TABLE Student ADD COLUMN "+field+" TEXT");
            db.execSQL("ALTER TABLE Student ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE UNIQUE INDEX index_Student_digitalCardCode ON Student(digitalCardCode)");
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_2_3 = new androidx.room.migration.Migration(2,3) {
        @Override public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE Student ADD COLUMN gender TEXT");
            db.execSQL("CREATE TABLE IF NOT EXISTS StudentNumberSequence (prefix TEXT NOT NULL, lastNumber INTEGER NOT NULL, PRIMARY KEY(prefix))");
        }
    };

    public static final androidx.room.migration.Migration MIGRATION_3_4 = new androidx.room.migration.Migration(3,4) {
        @Override public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS UserAccount (id TEXT NOT NULL, username TEXT NOT NULL, fullName TEXT NOT NULL, role TEXT NOT NULL, passwordHash TEXT NOT NULL, salt TEXT NOT NULL, algorithm TEXT NOT NULL, iterations INTEGER NOT NULL, enabled INTEGER NOT NULL, failedAttempts INTEGER NOT NULL, lockedUntil INTEGER NOT NULL, PRIMARY KEY(id))");
            db.execSQL("CREATE UNIQUE INDEX index_UserAccount_username ON UserAccount(username)");
            db.execSQL("CREATE TABLE IF NOT EXISTS TeacherClass (teacherId TEXT NOT NULL, classId TEXT NOT NULL, PRIMARY KEY(teacherId,classId), FOREIGN KEY(teacherId) REFERENCES UserAccount(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(classId) REFERENCES Classroom(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX index_TeacherClass_classId ON TeacherClass(classId)");
            db.execSQL("CREATE TABLE IF NOT EXISTS LocalLogin (id INTEGER NOT NULL, userId TEXT NOT NULL, tokenHash TEXT NOT NULL, expiresAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(userId) REFERENCES UserAccount(id) ON UPDATE NO ACTION ON DELETE RESTRICT)");
            db.execSQL("CREATE INDEX index_LocalLogin_userId ON LocalLogin(userId)");
            db.execSQL("ALTER TABLE AttendanceSession ADD COLUMN teacherId TEXT REFERENCES UserAccount(id) ON UPDATE NO ACTION ON DELETE RESTRICT");
            db.execSQL("ALTER TABLE AttendanceSession ADD COLUMN teacherNameSnapshot TEXT");
            db.execSQL("ALTER TABLE AttendanceSession ADD COLUMN teacherUsernameSnapshot TEXT");
            db.execSQL("CREATE INDEX index_AttendanceSession_teacherId ON AttendanceSession(teacherId)");
        }
    };
    @Entity(tableName="UserAccount",indices=@Index(value="username",unique=true))
    public static class UserAccount {
        @PrimaryKey @NonNull public String id="";
        @NonNull public String username="",fullName="",role="TEACHER",passwordHash="",salt="",algorithm="";
        public int iterations,failedAttempts;
        public boolean enabled=true;
        public long lockedUntil;
    }
    @Entity(tableName="TeacherClass",primaryKeys={"teacherId","classId"},
        foreignKeys={@ForeignKey(entity=UserAccount.class,parentColumns="id",childColumns="teacherId",onDelete=ForeignKey.RESTRICT),
            @ForeignKey(entity=Classroom.class,parentColumns="id",childColumns="classId",onDelete=ForeignKey.CASCADE)},indices=@Index("classId"))
    public static class TeacherClass {
        @NonNull public String teacherId="",classId="";
    }
    @Entity(tableName="LocalLogin",foreignKeys=@ForeignKey(entity=UserAccount.class,parentColumns="id",childColumns="userId",onDelete=ForeignKey.RESTRICT),indices=@Index("userId"))
    public static class LocalLogin {
        @PrimaryKey public int id=1;
        @NonNull public String userId="",tokenHash="";
        public long expiresAt;
    }

    @Entity(tableName="StudentNumberSequence")
    public static class StudentNumberSequence {
        @PrimaryKey @NonNull public String prefix="";
        public int lastNumber;
    }

    @Entity(tableName = "Classroom")
    public static class Classroom {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String name = "";
        @NonNull public String code = "";
        @NonNull public String note = "";
    }

    @Entity(tableName = "Student", indices = {@Index(value="mssv", unique=true), @Index(value="cardCode", unique=true), @Index(value="digitalCardCode", unique=true), @Index(value="phone", unique=true)})
    public static class StudentRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String mssv = "";
        @NonNull public String fullName = "";
        public String cardCode;
        public String digitalCardCode;
        public String birthDate, course, primaryClassId, photoPath, gender;
        @ColumnInfo(defaultValue="0") public boolean archived;
        @NonNull public String email = "";
        @NonNull public String phone = "";
        public int avatarColor;
    }

    @Entity(tableName="ClassMembership", primaryKeys={"classId","studentId"},
        foreignKeys={@ForeignKey(entity=Classroom.class,parentColumns="id",childColumns="classId",onDelete=ForeignKey.CASCADE),
            @ForeignKey(entity=StudentRow.class,parentColumns="id",childColumns="studentId",onDelete=ForeignKey.RESTRICT)},
        indices={@Index("studentId")})
    public static class ClassMembership {
        @NonNull public String classId = "";
        @NonNull public String studentId = "";
    }

    @Entity(tableName="AttendanceSession", foreignKeys={@ForeignKey(entity=Classroom.class,
        parentColumns="id", childColumns="classId", onDelete=ForeignKey.RESTRICT),
        @ForeignKey(entity=UserAccount.class,parentColumns="id",childColumns="teacherId",onDelete=ForeignKey.RESTRICT),
        @ForeignKey(entity=ClassSchedule.class,parentColumns="id",childColumns="scheduleId",onDelete=ForeignKey.RESTRICT)}, indices={@Index("classId"),@Index("status"),@Index("teacherId"),@Index(value={"scheduleId","plannedDate"},unique=true)})
    public static class SessionRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String classId = "";
        @NonNull public String title = "";
        public long scheduledAt;
        public Long lateAt, openedAt, closedAt;
        @NonNull public String status = "DRAFT";
        public String classNameSnapshot, classCodeSnapshot;
        public String teacherId,teacherNameSnapshot,teacherUsernameSnapshot;
        public String scheduleId,plannedDate,plannedShift;
        public Integer plannedStartMinute,plannedEndMinute;
    }

    @Entity(tableName="AttendanceRecord", primaryKeys={"sessionId","studentId"},
        foreignKeys={@ForeignKey(entity=SessionRow.class,parentColumns="id",childColumns="sessionId",onDelete=ForeignKey.RESTRICT),
            @ForeignKey(entity=StudentRow.class,parentColumns="id",childColumns="studentId",onDelete=ForeignKey.RESTRICT)},
        indices={@Index("studentId"),@Index(value={"sessionId","status"})})
    public static class RecordRow {
        @NonNull public String sessionId = "";
        @NonNull public String studentId = "";
        @NonNull public String mssvSnapshot = "";
        @NonNull public String nameSnapshot = "";
        @NonNull public String status = "PENDING";
        public Long checkedAt, editedAt;
        public String source, editReason;
    }

    @Dao public interface Store {
        @Query("SELECT * FROM ClassSchedule ORDER BY startDate,startMinute,id") List<ClassSchedule> schedules();
        @Query("SELECT * FROM ClassSchedule WHERE id=:id") ClassSchedule schedule(String id);
        @Insert void insertSchedule(ClassSchedule row);
        @Update void updateSchedule(ClassSchedule row);
        @Query("SELECT * FROM AttendanceSession WHERE scheduleId=:scheduleId AND plannedDate=:date LIMIT 1") SessionRow occurrence(String scheduleId,String date);
        @Query("SELECT * FROM UserAccount ORDER BY username") List<UserAccount> users();
        @Query("SELECT * FROM UserAccount WHERE id=:id") UserAccount user(String id);
        @Query("SELECT * FROM UserAccount WHERE username=:name") UserAccount username(String name);
        @Insert void insertUser(UserAccount row);
        @Update void updateUser(UserAccount row);
        @Query("SELECT * FROM TeacherClass") List<TeacherClass> assignments();
        @Query("SELECT COUNT(*) FROM TeacherClass WHERE teacherId=:teacherId AND classId=:classId") int assigned(String teacherId,String classId);
        @Insert(onConflict=OnConflictStrategy.IGNORE) void assign(TeacherClass row);
        @Query("DELETE FROM TeacherClass WHERE teacherId=:teacherId AND classId=:classId") void unassign(String teacherId,String classId);
        @Query("SELECT * FROM LocalLogin WHERE id=1") LocalLogin login();
        @Insert(onConflict=OnConflictStrategy.REPLACE) void saveLogin(LocalLogin row);
        @Query("DELETE FROM LocalLogin") void clearLogin();
        @Query("SELECT * FROM StudentNumberSequence WHERE prefix=:prefix") StudentNumberSequence numberSequence(String prefix);
        @Insert(onConflict=OnConflictStrategy.REPLACE) void saveNumberSequence(StudentNumberSequence row);
        @Query("SELECT * FROM Classroom ORDER BY name COLLATE NOCASE, id") List<Classroom> classes();
        @Query("SELECT * FROM Student ORDER BY mssv") List<StudentRow> students();
        @Query("SELECT * FROM ClassMembership") List<ClassMembership> memberships();
        @Query("SELECT * FROM AttendanceSession ORDER BY scheduledAt DESC, id") List<SessionRow> sessions();
        @Query("SELECT * FROM AttendanceRecord ORDER BY mssvSnapshot") List<RecordRow> records();
        @Query("SELECT * FROM Classroom WHERE id=:id") Classroom classroom(String id);
        @Query("SELECT * FROM Student WHERE id=:id") StudentRow student(String id);
        @Query("SELECT * FROM Student WHERE archived=0 AND (mssv=:code OR cardCode=:code)") List<StudentRow> identify(String code);
        @Query("SELECT * FROM Student WHERE digitalCardCode=:code AND archived=0") List<StudentRow> digital(String code);
        @Query("SELECT * FROM Student WHERE mssv=:mssv") StudentRow byMssv(String mssv);
        @Query("SELECT s.* FROM Student s INNER JOIN ClassMembership m ON s.id=m.studentId WHERE m.classId=:classId AND s.archived=0 ORDER BY s.mssv") List<StudentRow> roster(String classId);
        @Query("SELECT COUNT(*) FROM ClassMembership WHERE classId=:classId AND studentId=:studentId") int membership(String classId,String studentId);
        @Query("SELECT * FROM AttendanceSession WHERE id=:id") SessionRow session(String id);
        @Query("SELECT * FROM AttendanceSession WHERE status='OPEN' LIMIT 1") SessionRow active();
        @Query("SELECT * FROM AttendanceRecord WHERE sessionId=:sessionId AND studentId=:studentId") RecordRow record(String sessionId,String studentId);
        @Insert void insertClass(Classroom row);
        @Update void updateClass(Classroom row);
        @Insert void insertStudent(StudentRow row);
        @Update void updateStudent(StudentRow row);
        @Insert(onConflict=OnConflictStrategy.IGNORE) long addMember(ClassMembership row);
        @Query("DELETE FROM ClassMembership WHERE classId=:classId AND studentId=:studentId") void removeMember(String classId,String studentId);
        @Insert void insertSession(SessionRow row);
        @Update void updateSession(SessionRow row);
        @Insert void insertRecords(List<RecordRow> rows);
        @Update void updateRecord(RecordRow row);
        @Query("UPDATE AttendanceRecord SET status=:status, checkedAt=:time, source=:source WHERE sessionId=:sessionId AND studentId=:studentId AND status='PENDING'")
        int checkIn(String sessionId,String studentId,String status,long time,String source);
        @Query("UPDATE AttendanceRecord SET status='ABSENT', source='AUTO_FINISH' WHERE sessionId=:sessionId AND status='PENDING'") void markAbsent(String sessionId);
        @Query("DELETE FROM Classroom WHERE id=:id") void deleteClass(String id);
    }
}

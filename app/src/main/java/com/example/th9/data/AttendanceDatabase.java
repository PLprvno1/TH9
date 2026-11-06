package com.example.th9.data;

import androidx.annotation.NonNull;
import androidx.room.*;
import java.util.List;

/** Version 1 is the first persistent schema. Exported schemas must be kept for future migrations.
 * No destructive fallback: an unsupported upgrade must never erase attendance history. */
@Database(entities = {AttendanceDatabase.Classroom.class, AttendanceDatabase.StudentRow.class,
        AttendanceDatabase.ClassMembership.class, AttendanceDatabase.SessionRow.class,
        AttendanceDatabase.RecordRow.class}, version = 1, exportSchema = true)
public abstract class AttendanceDatabase extends RoomDatabase {
    public abstract Store store();

    @Entity(tableName = "Classroom")
    public static class Classroom {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String name = "";
        @NonNull public String code = "";
        @NonNull public String note = "";
    }

    @Entity(tableName = "Student", indices = {@Index(value="mssv", unique=true), @Index(value="cardCode", unique=true)})
    public static class StudentRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String mssv = "";
        @NonNull public String fullName = "";
        public String cardCode;
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

    @Entity(tableName="AttendanceSession", foreignKeys=@ForeignKey(entity=Classroom.class,
        parentColumns="id", childColumns="classId", onDelete=ForeignKey.RESTRICT), indices={@Index("classId"),@Index("status")})
    public static class SessionRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String classId = "";
        @NonNull public String title = "";
        public long scheduledAt;
        public Long lateAt, openedAt, closedAt;
        @NonNull public String status = "DRAFT";
        public String classNameSnapshot, classCodeSnapshot;
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
        @Query("SELECT * FROM Classroom ORDER BY name COLLATE NOCASE, id") List<Classroom> classes();
        @Query("SELECT * FROM Student ORDER BY mssv") List<StudentRow> students();
        @Query("SELECT * FROM ClassMembership") List<ClassMembership> memberships();
        @Query("SELECT * FROM AttendanceSession ORDER BY scheduledAt DESC, id") List<SessionRow> sessions();
        @Query("SELECT * FROM AttendanceRecord ORDER BY mssvSnapshot") List<RecordRow> records();
        @Query("SELECT * FROM Classroom WHERE id=:id") Classroom classroom(String id);
        @Query("SELECT * FROM Student WHERE id=:id") StudentRow student(String id);
        @Query("SELECT * FROM Student WHERE mssv=:code OR cardCode=:code") List<StudentRow> identify(String code);
        @Query("SELECT * FROM Student WHERE mssv=:mssv") StudentRow byMssv(String mssv);
        @Query("SELECT s.* FROM Student s INNER JOIN ClassMembership m ON s.id=m.studentId WHERE m.classId=:classId ORDER BY s.mssv") List<StudentRow> roster(String classId);
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

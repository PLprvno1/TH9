package com.example.th9;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.data.*;
import com.example.th9.model.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RepositoryInstrumentedTest {
    private AttendanceDatabase db;
    private AttendanceRepository repo;
    private ClassRoom a,b;
    private Student s1,s2,s3,other;
    private AttendanceSession session;
    @Before public void setup(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        db=Room.inMemoryDatabaseBuilder(context,AttendanceDatabase.class).build();repo=new AttendanceRepository(db);repo.initialize();
        assertTrue(repo.getAllClasses().isEmpty());assertTrue(repo.getAllSessions().isEmpty());
        a=new ClassRoom(null,"Lớp A","A","");b=new ClassRoom(null,"Lớp B","B","");repo.addClass(a);repo.addClass(b);
        s1=student("0001","Nguyễn, \"An\"","SV:00010001",a);
        s2=student("0002","Bình",null,a);s3=student("0003","Chi",null,a);other=student("0004","Dũng","OTHER",b);
        session=draft(a);repo.addSession(session);
    }
    @After public void cleanup(){db.close();}
    private Student student(String mssv,String name,String card,ClassRoom classroom){
        Student s=new Student(null,mssv,name,card,"","",0);repo.addStudentToClass(s,classroom.getId());return s;
    }
    private AttendanceSession draft(ClassRoom classroom){return new AttendanceSession(null,classroom.getId(),"Buổi thử","06/11/2026","07:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0);}
    private void fails(String message,Runnable operation){try{operation.run();fail("Phải từ chối: "+message);}catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().contains(message));}}
    @Test public void acceptanceTwoOfThreeAndCsv() throws Exception {
        repo.openSession(session.getId());
        AttendanceRecord first=repo.scan(session.getId(),"SV:00010001",System.currentTimeMillis());
        fails("Đã điểm danh",()->repo.scan(session.getId(),"SV:00010001",System.currentTimeMillis()));
        assertEquals(first.getCheckedAt(),repo.getRecordForStudent(session.getId(),s1.getId()).getCheckedAt());
        fails("không có trong buổi",()->repo.scan(session.getId(),"OTHER",System.currentTimeMillis()));
        fails("chưa đăng ký",()->repo.scan(session.getId(),"unknown",System.currentTimeMillis()));
        repo.manual(session.getId(),s2.getId(),System.currentTimeMillis());repo.finishSession(session.getId());
        assertEquals(2,repo.getCheckedInTotal(session.getId()));assertEquals(1,repo.getAbsentCount(session.getId()));
        assertEquals(0,repo.getUncheckedCount(session.getId()));
        assertEquals(AttendanceRecord.CheckInSource.MANUAL,repo.getRecordForStudent(session.getId(),s2.getId()).getSource());
        fails("đã đóng",()->repo.manual(session.getId(),s3.getId(),System.currentTimeMillis()));
        String csv=CsvTransfer.csv(repo,session.getId());assertTrue(csv.contains("0001"));assertTrue(csv.contains("\"Nguyễn, \"\"An\"\"\""));
        assertEquals(3,org.apache.commons.csv.CSVParser.parse(csv,org.apache.commons.csv.CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).build()).getRecords().size());
    }
    @Test public void openTwiceAndGlobalOpenGuard(){
        repo.openSession(session.getId());fails("Chỉ mở",()->repo.openSession(session.getId()));
        AttendanceSession next=draft(b);repo.addSession(next);fails("Đang mở",()->repo.openSession(next.getId()));
        assertTrue(repo.getRecordsForSession(next.getId()).isEmpty());assertEquals(3,repo.getRecordsForSession(session.getId()).size());
    }
    @Test public void emptyClassCannotOpen(){
        ClassRoom empty=new ClassRoom(null,"Trống","","");repo.addClass(empty);AttendanceSession s=draft(empty);repo.addSession(s);
        fails("chưa có sinh viên",()->repo.openSession(s.getId()));assertTrue(repo.getRecordsForSession(s.getId()).isEmpty());
    }
    @Test public void rosterNamesAndMembershipAreSnapshots(){
        repo.openSession(session.getId());student("0005","Mới",null,a);repo.removeFromClass(s1.getId(),a.getId());
        a.setName("Tên mới");repo.updateClass(a);s1.setFullName("Đã đổi tên");s1.setMssv("0099");repo.updateStudent(s1);
        assertEquals(3,repo.getSessionById(session.getId()).getExpectedCount());
        assertEquals("Nguyễn, \"An\"",repo.getRecordForStudent(session.getId(),s1.getId()).getNameSnapshot());
        assertEquals("0001",repo.getRecordForStudent(session.getId(),s1.getId()).getMssvSnapshot());
        assertEquals("Lớp A",repo.getSessionById(session.getId()).getClassNameSnapshot());
        repo.manual(session.getId(),s1.getId(),System.currentTimeMillis());repo.finishSession(session.getId());
        fails("bảo toàn lịch sử",()->repo.deleteClass(a.getId()));
    }
    @Test public void multiClassMembershipAndCrossFieldCodeConflicts(){
        repo.addExistingToClass(s1.getId(),b.getId());fails("đã thuộc",()->repo.addExistingToClass(s1.getId(),b.getId()));
        assertEquals(2,repo.getClassesForStudent(s1.getId()).size());
        fails("xung đột",()->repo.linkCardBarcode(s2.getId(),"0001"));
        Student copy=repo.getStudentById(s2.getId());copy.setMssv("SV:00010001");fails("xung đột",()->repo.updateStudent(copy));
        assertNull(repo.getStudentById(s2.getId()).getCardBarcode());
        repo.linkCardBarcode(s1.getId(),"0001");repo.openSession(session.getId());repo.scan(session.getId(),"0001",System.currentTimeMillis());
        assertEquals(1,repo.getCheckedInTotal(session.getId()));
    }
    @Test public void ambiguousLegacyCodeIsRejected(){
        AttendanceDatabase.StudentRow row=db.store().student(s2.getId());row.cardCode="0001";db.store().updateStudent(row);repo.reload();
        repo.openSession(session.getId());fails("Mã bị xung đột",()->repo.scan(session.getId(),"0001",System.currentTimeMillis()));
        assertEquals(0,repo.getCheckedInTotal(session.getId()));
    }
    @Test public void lateRulesUseMinuteAndValidateSchedule(){
        session.setEnableLateThreshold(true);session.setLateThresholdTime("07:40");repo.updateSession(session);repo.openSession(session.getId());
        long threshold=AttendanceRepository.parseDateTime("06/11/2026","07:40");
        assertEquals(AttendanceRecord.AttendanceStatus.PRESENT,repo.manual(session.getId(),s1.getId(),threshold-1).getStatus());
        assertEquals(AttendanceRecord.AttendanceStatus.PRESENT,repo.manual(session.getId(),s2.getId(),threshold+59999).getStatus());
        assertEquals(AttendanceRecord.AttendanceStatus.LATE,repo.manual(session.getId(),s3.getId(),threshold+60000).getStatus());
        AttendanceSession invalid=draft(b);invalid.setEnableLateThreshold(true);invalid.setLateThresholdTime("06:59");
        fails("Mốc đi muộn",()->repo.addSession(invalid));
    }
    @Test public void scanAndManualRaceOnlyOneWrite() throws Exception {
        repo.openSession(session.getId());ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> scan=()->{start.await();try{repo.scan(session.getId(),"0001",123456000);return true;}catch(IllegalArgumentException e){return false;}};
        Callable<Boolean> manual=()->{start.await();try{repo.manual(session.getId(),s1.getId(),123456001);return true;}catch(IllegalArgumentException e){return false;}};
        Future<Boolean> f1=pool.submit(scan),f2=pool.submit(manual);start.countDown();
        assertNotEquals(f1.get(),f2.get());assertEquals(1,repo.getCheckedInTotal(session.getId()));pool.shutdown();
    }
    @Test public void closedCorrectionRequiresReasonAndDoesNotInventScanTime(){
        repo.openSession(session.getId());repo.manual(session.getId(),s1.getId(),123456000);repo.finishSession(session.getId());
        AttendanceRecord p=repo.getRecordForStudent(session.getId(),s1.getId());
        fails("lý do",()->repo.updateRecordStatus(p.getId(),AttendanceRecord.AttendanceStatus.LATE," "));
        repo.updateRecordStatus(p.getId(),AttendanceRecord.AttendanceStatus.LATE,"Điều chỉnh");
        assertEquals(p.getCheckedAt(),repo.getRecordForStudent(session.getId(),s1.getId()).getCheckedAt());
        AttendanceRecord absent=repo.getRecordForStudent(session.getId(),s2.getId());
        repo.updateRecordStatus(absent.getId(),AttendanceRecord.AttendanceStatus.PRESENT,"Có giấy xác nhận");
        AttendanceRecord corrected=repo.getRecordForStudent(session.getId(),s2.getId());assertNull(corrected.getCheckedAt());
        assertEquals("Chỉnh thủ công",corrected.getSourceDisplayName());assertNotNull(corrected.getEditedAt());
        repo.updateRecordStatus(p.getId(),AttendanceRecord.AttendanceStatus.ABSENT,"Vắng");assertNull(repo.getRecordForStudent(session.getId(),s1.getId()).getCheckedAt());
    }
    @Test public void csvPreviewDoesNotWriteAndImportIsAtomic() throws Exception {
        List<CsvTransfer.Row> valid=CsvTransfer.parse(new StringReader("\uFEFFmssv,ho_ten,ma_the\r\n0010,\"Tên, có dấu\",RAW:0010\r\n"));
        assertEquals(0,repo.importCsv(a.getId(),valid,false).errors);assertNull(repo.findByMssv("0010"));
        repo.importCsv(a.getId(),valid,true);assertEquals("Tên, có dấu",repo.findByMssv("0010").getFullName());
        assertTrue(repo.importCsv(a.getId(),valid,false).lines.get(0).contains("Bỏ qua"));
        List<CsvTransfer.Row> duplicate=CsvTransfer.parse(new StringReader("mssv,ho_ten\n0011,Một\n0011,Hai\n"));
        fails("dòng lỗi",()->repo.importCsv(a.getId(),duplicate,true));assertNull(repo.findByMssv("0011"));
        List<CsvTransfer.Row> conflict=CsvTransfer.parse(new StringReader("mssv,ho_ten,ma_the\n0012,Mới,0001\n"));
        fails("dòng lỗi",()->repo.importCsv(a.getId(),conflict,true));assertNull(repo.findByMssv("0012"));
        List<CsvTransfer.Row> stale=CsvTransfer.parse(new StringReader("mssv,ho_ten\n0013,Mới\n"));
        assertEquals(0,repo.importCsv(a.getId(),stale,false).errors);student("0013","Tên khác",null,b);
        fails("dòng lỗi",()->repo.importCsv(a.getId(),stale,true));
        assertEquals(0,db.store().membership(a.getId(),repo.findByMssv("0013").getId()));
    }
    @Test public void diskDatabaseSurvivesReopen(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="test-"+UUID.randomUUID()+".db";
        AttendanceDatabase disk=Room.databaseBuilder(context,AttendanceDatabase.class,name).build();
        try{
            AttendanceRepository r=new AttendanceRepository(disk);r.initialize();ClassRoom c=new ClassRoom(null,"Bền vững","","");r.addClass(c);
            Student student=new Student(null,"0000","An",null,"","",0);r.addStudentToClass(student,c.getId());
            AttendanceSession s=draft(c);r.addSession(s);r.openSession(s.getId());disk.close();
            disk=Room.databaseBuilder(context,AttendanceDatabase.class,name).build();AttendanceRepository reopened=new AttendanceRepository(disk);reopened.initialize();
            assertEquals(s.getId(),reopened.getActiveSession().getId());assertEquals("0000",reopened.getAllStudents().get(0).getMssv());
            reopened.manual(s.getId(),student.getId(),System.currentTimeMillis());reopened.finishSession(s.getId());
            assertEquals(1,reopened.getCheckedInTotal(s.getId()));
        }finally{disk.close();context.deleteDatabase(name);}
    }
}

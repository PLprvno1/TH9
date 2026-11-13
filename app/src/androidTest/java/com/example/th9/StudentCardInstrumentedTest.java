package com.example.th9;
import android.content.*;
import android.graphics.*;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.view.StudentCardView;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class StudentCardInstrumentedTest {
    Context context;AttendanceDatabase db;AttendanceRepository repo;
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();db=Room.inMemoryDatabaseBuilder(context,AttendanceDatabase.class).build();repo=new AttendanceRepository(db);repo.initialize();repo.setupAdmin("admin","Admin","TestPass123!");}
    @After public void close(){db.close();}
    private Student student(String mssv){Student s=new Student(null,mssv,"Nguyễn Thị Ánh", "OLD:"+mssv,"","",0);s.setBirthDate("29/02/2004");s.setCourse("2022–2027");repo.addStudent(s);return repo.getStudentById(s.getId());}
    private ClassRoom classroom(String name){ClassRoom c=new ClassRoom(null,name,"","");repo.addClass(c);return c;}
    private AttendanceSession session(ClassRoom c){AttendanceSession s=new AttendanceSession(null,c.getId(),"Buổi thử","10/11/2026","07:00",true,"07:15",AttendanceSession.SessionStatus.DRAFT,null,0);repo.addSession(s);return s;}
    private void rejected(Runnable action){try{action.run();fail("Expected rejection");}catch(IllegalArgumentException expected){}}
    @Test public void digitalAndLegacyScanShareSnapshotAndDuplicateGuard(){
        ClassRoom a=classroom("A"),b=classroom("B");Student s=student("00001"),other=student("00002");repo.addExistingToClass(s.getId(),a.getId());repo.addExistingToClass(other.getId(),b.getId());AttendanceSession open=session(a);repo.openSession(open.getId());
        assertEquals(s.getId(),repo.verifyCard(s.getDigitalCardCode()).getId());assertEquals(0,repo.getCheckedInTotal(open.getId()));
        rejected(()->repo.scan(open.getId(),other.getDigitalCardCode(),0));rejected(()->repo.verifyCard(CardCodes.create()));rejected(()->repo.verifyCard(s.getDigitalCardCode()+" "));
        rejected(()->repo.scan(open.getId(),s.getDigitalCardCode(),"CODE_39",0));assertEquals(0,repo.getCheckedInTotal(open.getId()));
        long time=AttendanceRepository.parseDateTime("10/11/2026","07:16");assertEquals(AttendanceRecord.AttendanceStatus.LATE,repo.scan(open.getId(),s.getDigitalCardCode(),time).getStatus());
        rejected(()->repo.scan(open.getId(),s.getCardBarcode(),time));rejected(()->repo.manual(open.getId(),s.getId(),time));assertEquals(1,repo.getCheckedInTotal(open.getId()));
        repo.finishSession(open.getId());rejected(()->repo.scan(open.getId(),s.getDigitalCardCode(),time));assertEquals(s.getId(),repo.verifyCard(s.getCardBarcode()).getId());
    }
    @Test public void stableCodeProfileMembershipAndArchiveHistory(){
        Student s=student("00001");String code=s.getDigitalCardCode();ClassRoom a=classroom("A"),b=classroom("B");repo.addExistingToClass(s.getId(),a.getId());repo.addExistingToClass(s.getId(),b.getId());AttendanceSession open=session(a);repo.openSession(open.getId());
        s.setFullName("Tên đã đổi");s.setPrimaryClassId(b.getId());s.setDigitalCardCode("malicious-change");repo.updateStudent(s);
        assertEquals(code,repo.issueDigitalCard(s.getId()));assertEquals(2,repo.getClassesForStudent(s.getId()).size());assertEquals("Nguyễn Thị Ánh",repo.getRecordForStudent(open.getId(),s.getId()).getNameSnapshot());
        repo.deleteStudent(s.getId());assertTrue(repo.getAllStudents().isEmpty());assertEquals(1,repo.getSessionById(open.getId()).getExpectedCount());rejected(()->repo.verifyCard(code));
        repo.manual(open.getId(),s.getId(),0);repo.finishSession(open.getId());assertEquals(1,repo.getCheckedInTotal(open.getId()));assertTrue(CsvTransfer.summary(repo,open.getId()).contains("1/1"));
    }
    @Test public void preexistingPrefixedPhysicalCardKeepsItsExactIdentity(){
        Student s=student("0001");AttendanceDatabase.StudentRow row=db.store().student(s.getId());row.cardCode="TDMU1-LEGACY";db.store().updateStudent(row);repo.reload();
        assertEquals(s.getId(),repo.verifyCard("TDMU1-LEGACY","CODE_39").getId());Student edited=repo.getStudentById(s.getId());edited.setFullName("Đổi tên");repo.updateStudent(edited);
        assertEquals("TDMU1-LEGACY",repo.getStudentById(s.getId()).getCardBarcode());rejected(()->repo.linkCardBarcode(s.getId(),"TDMU1-NEW"));
    }
    @Test public void uniqueIndexAndOneTimeIssueForOldStudent(){
        Student first=student("0001"),second=student("0002");AttendanceDatabase.StudentRow row=db.store().student(second.getId());row.digitalCardCode=first.getDigitalCardCode();
        try{db.store().updateStudent(row);fail("Unique index required");}catch(android.database.sqlite.SQLiteConstraintException expected){}
        row.digitalCardCode=null;db.store().updateStudent(row);repo.reload();String code=repo.issueDigitalCard(second.getId());assertTrue(CardCodes.valid(code));assertEquals(code,repo.issueDigitalCard(second.getId()));assertEquals("OLD:0002",repo.getStudentById(second.getId()).getCardBarcode());
    }
    @Test public void exportedFullCardPngDecodedByBundledMlKit()throws Exception{
        Student s=student("00001");s.setFullName("Nguyễn Thị Ánh với tên rất dài để kiểm tra bố cục thẻ sinh viên");
        AtomicReference<Bitmap> image=new AtomicReference<>();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{StudentCardView card=new StudentCardView(context,null);card.bind(s,"Lớp Công nghệ thông tin D22CNTT01");image.set(card.export());});
        File file=new File(context.getExternalFilesDir("verification"),"student-card.png");file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){assertTrue(image.get().compress(Bitmap.CompressFormat.PNG,100,out));}image.get().recycle();
        Bitmap png=BitmapFactory.decodeFile(file.getAbsolutePath());assertEquals(1600,png.getWidth());assertEquals(1050,png.getHeight());BarcodeScanner scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128).build());
        try{java.util.List<Barcode> codes=com.google.android.gms.tasks.Tasks.await(scanner.process(InputImage.fromBitmap(png,0)),30,TimeUnit.SECONDS);assertEquals(1,codes.size());assertEquals(s.getMssv(),codes.get(0).getRawValue());}finally{scanner.close();png.recycle();}
    }
    @Test public void photoIsCopiedBoundedAndIndependentOfSource()throws Exception{
        File source=new File(context.getCacheDir(),"photo-test.jpg");Bitmap bitmap=Bitmap.createBitmap(2400,3200,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);try(OutputStream out=new FileOutputStream(source)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}bitmap.recycle();
        android.media.ExifInterface exif=new android.media.ExifInterface(source.getAbsolutePath());exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();
        String path=StudentPhotos.importPhoto(context,Uri.fromFile(source));assertTrue(source.delete());Bitmap saved=StudentPhotos.read(path);assertNotNull(saved);assertTrue(saved.getWidth()>saved.getHeight());assertTrue(saved.getWidth()<=1200&&saved.getHeight()<=1200);saved.recycle();
        String name="card-test-"+UUID.randomUUID()+".db";AttendanceDatabase disk=Room.databaseBuilder(context,AttendanceDatabase.class,name).build();
        try{AttendanceRepository r=new AttendanceRepository(disk);r.initialize();r.setupAdmin("admin","Admin","TestPass123!");Student s=new Student(null,"0000","Ảnh bền vững",null,"","",0);s.setPhotoPath(path);r.addStudent(s);String code=r.getStudentById(s.getId()).getDigitalCardCode();disk.close();disk=Room.databaseBuilder(context,AttendanceDatabase.class,name).build();r=new AttendanceRepository(disk);r.initialize();r.login("admin","TestPass123!");assertEquals(code,r.getStudentById(s.getId()).getDigitalCardCode());assertNotNull(StudentPhotos.read(r.getStudentById(s.getId()).getPhotoPath()));}
        finally{disk.close();context.deleteDatabase(name);new File(path).delete();}assertNull(StudentPhotos.read(path));
    }
    @Test public void migrationFromExportedV1PreservesAllTablesAndLegacyScan()throws Exception{
        String name="migration-card-"+UUID.randomUUID()+".db";File dest=context.getDatabasePath(name);dest.getParentFile().mkdirs();
        String schema;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("com.example.th9.data.AttendanceDatabase/1.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);schema=out.toString("UTF-8");}
        SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(dest,null);JSONArray entities=new JSONObject(schema).getJSONObject("database").getJSONArray("entities");
        for(int i=0;i<entities.length();i++){JSONObject e=entities.getJSONObject(i);String table=e.getString("tableName");old.execSQL(e.getString("createSql").replace("${TABLE_NAME}",table));JSONArray indices=e.optJSONArray("indices");if(indices!=null)for(int j=0;j<indices.length();j++)old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}",table));}
        old.execSQL("INSERT INTO Classroom VALUES ('c','Lớp cũ','OLD','')");old.execSQL("INSERT INTO Student VALUES ('s','0001','Tên cũ','SV:0001','','',0)");old.execSQL("INSERT INTO ClassMembership VALUES ('c','s')");
        old.execSQL("INSERT INTO AttendanceSession (id,classId,title,scheduledAt,status,classNameSnapshot,classCodeSnapshot) VALUES ('open','c','Buổi cũ',1,'OPEN','Lớp cũ','OLD')");
        old.execSQL("INSERT INTO AttendanceRecord (sessionId,studentId,mssvSnapshot,nameSnapshot,status) VALUES ('open','s','0001','Tên cũ','PENDING')");old.setVersion(1);old.close();
        AttendanceDatabase migrated=Room.databaseBuilder(context,AttendanceDatabase.class,name).addMigrations(AttendanceDatabase.MIGRATION_1_2,AttendanceDatabase.MIGRATION_2_3,AttendanceDatabase.MIGRATION_3_4,AttendanceDatabase.MIGRATION_4_5).build();
        try{AttendanceRepository r=new AttendanceRepository(migrated);r.initialize();r.setupAdmin("admin","Admin","TestPass123!");assertEquals(1,r.getAllClasses().size());assertEquals(1,r.getAllStudents().size());assertEquals(1,r.getClassesForStudent("s").size());assertEquals("open",r.getActiveSession().getId());assertEquals("Tên cũ",r.getRecordForStudent("open","s").getNameSnapshot());assertNull(r.getStudentById("s").getDigitalCardCode());String code=r.issueDigitalCard("s");r.scan("open",code,0);rejected(()->r.scan("open","SV:0001",0));assertEquals("SV:0001",r.getStudentById("s").getCardBarcode());}
        finally{migrated.close();context.deleteDatabase(name);}
    }
}

package com.example.th9.data;

import android.content.Context;
import android.os.Looper;
import androidx.room.Room;
import com.example.th9.model.*;
import com.example.th9.data.AttendanceDatabase.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.Callable;
import static com.example.th9.data.AttendanceRules.require;

/** Room is authoritative. UI reads detached display models from an atomically published snapshot.
 * All disk access and mutations run on Tasks.IO, including startup and process restoration. */
public final class AttendanceRepository {
    private static AttendanceRepository instance;
    private final AttendanceDatabase db;
    private final Store dao;
    private volatile Snapshot snapshot = new Snapshot();
    private boolean initialized;
    private static class Snapshot {
        List<Classroom> classes = new ArrayList<>();
        List<StudentRow> students = new ArrayList<>();
        List<ClassMembership> members = new ArrayList<>();
        List<SessionRow> sessions = new ArrayList<>();
        List<RecordRow> records = new ArrayList<>();
    }
    public static synchronized void configure(Context context) {
        if (instance == null) instance = new AttendanceRepository(Room.databaseBuilder(context,
                AttendanceDatabase.class,"attendance.db").build());
    }
    public static AttendanceRepository getInstance() {
        if (instance == null) throw new IllegalStateException("Chưa khởi tạo dữ liệu");
        return instance;
    }
    public AttendanceRepository(AttendanceDatabase database) { db=database; dao=db.store(); }
    private void worker() { require(Looper.myLooper()!=Looper.getMainLooper(), "Phải truy cập Room ngoài main thread"); }
    public synchronized void initialize() { worker(); if (!initialized) { reload(); initialized=true; } }
    public synchronized boolean isInitialized(){return initialized;}
    public synchronized void reload() {
        worker();
        snapshot=db.runInTransaction(() -> {
            Snapshot s=new Snapshot(); s.classes=dao.classes(); s.students=dao.students();
            s.members=dao.memberships(); s.sessions=dao.sessions(); s.records=dao.records(); return s;
        });
    }
    private synchronized <T> T write(Callable<T> action) {
        worker();
        T result=db.runInTransaction(action);
        reload();
        return result;
    }
    public static String id() { return UUID.randomUUID().toString(); }
    public static String format(Long time,String pattern) {
        return time==null ? "" : new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(time));
    }
    public static long parseDateTime(String date,String time) {
        try {
            SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()); f.setLenient(false);
            String text=date+" "+time;
            ParsePosition position=new ParsePosition(0); Date parsed=f.parse(text,position);
            require(parsed!=null && position.getIndex()==text.length(),"Ngày giờ không hợp lệ");
            return parsed.getTime();
        } catch (RuntimeException e) { throw new IllegalArgumentException("Ngày giờ không hợp lệ"); }
    }
    private ClassRoom classModel(Classroom row, Snapshot s) {
        List<String> ids=new ArrayList<>();
        for(ClassMembership m:s.members) if(m.classId.equals(row.id)) ids.add(m.studentId);
        return new ClassRoom(row.id,row.name,row.code,row.note,ids);
    }
    private Student studentModel(StudentRow s) {
        return new Student(s.id,s.mssv,s.fullName,s.cardCode,s.email,s.phone,s.avatarColor);
    }
    private AttendanceSession sessionModel(SessionRow r,Snapshot s) {
        int expected=0;
        if(r.status.equals("DRAFT")) { for(ClassMembership m:s.members) if(m.classId.equals(r.classId)) expected++; }
        else for(RecordRow rec:s.records) if(rec.sessionId.equals(r.id)) expected++;
        AttendanceSession model=new AttendanceSession(r.id,r.classId,r.title,format(r.scheduledAt,"dd/MM/yyyy"),
                format(r.scheduledAt,"HH:mm"),r.lateAt!=null,format(r.lateAt,"HH:mm"),
                AttendanceSession.SessionStatus.valueOf(r.status),r.closedAt==null?null:format(r.closedAt,"HH:mm, dd/MM/yyyy"),expected);
        model.setSnapshot(r.classNameSnapshot,r.classCodeSnapshot,r.scheduledAt,r.openedAt,r.closedAt);
        return model;
    }
    private AttendanceRecord recordModel(RecordRow r) {
        AttendanceRecord result=new AttendanceRecord(r.sessionId+"/"+r.studentId,r.sessionId,r.studentId,
                AttendanceRecord.AttendanceStatus.valueOf(r.status),r.checkedAt==null?null:format(r.checkedAt,"HH:mm:ss"),
                r.source==null?null:AttendanceRecord.CheckInSource.valueOf(r.source),r.editReason);
        result.setSnapshot(r.mssvSnapshot,r.nameSnapshot,r.checkedAt,r.editedAt);
        return result;
    }
    public List<ClassRoom> getAllClasses() {
        Snapshot s=snapshot; List<ClassRoom> result=new ArrayList<>();
        for(Classroom r:s.classes) result.add(classModel(r,s)); return result;
    }
    public ClassRoom getClassById(String id) { for(ClassRoom c:getAllClasses()) if(c.getId().equals(id)) return c; return null; }
    public List<Student> getAllStudents() { List<Student> out=new ArrayList<>(); for(StudentRow s:snapshot.students)out.add(studentModel(s)); return out; }
    public Student getStudentById(String id) { for(StudentRow s:snapshot.students)if(s.id.equals(id))return studentModel(s); return null; }
    public Student findByMssv(String code) { for(StudentRow s:snapshot.students)if(s.mssv.equals(code))return studentModel(s); return null; }
    public List<Student> getStudentsForClass(String classId) {
        List<Student> out=new ArrayList<>(); Snapshot s=snapshot;
        for(ClassMembership m:s.members) if(m.classId.equals(classId))
            for(StudentRow student:s.students) if(student.id.equals(m.studentId)) out.add(studentModel(student));
        out.sort(Comparator.comparing(Student::getMssv)); return out;
    }
    public List<ClassRoom> getClassesForStudent(String studentId) {
        List<ClassRoom> out=new ArrayList<>(); for(ClassRoom c:getAllClasses())if(c.getStudentIds().contains(studentId))out.add(c);return out;
    }
    public List<AttendanceSession> getAllSessions() { Snapshot s=snapshot; List<AttendanceSession> out=new ArrayList<>();for(SessionRow r:s.sessions)out.add(sessionModel(r,s));return out; }
    public AttendanceSession getSessionById(String id) { for(AttendanceSession s:getAllSessions())if(s.getId().equals(id))return s;return null; }
    public AttendanceSession getActiveSession() { for(AttendanceSession s:getAllSessions())if(s.getStatus()==AttendanceSession.SessionStatus.OPEN)return s;return null; }
    public List<AttendanceSession> getSessionsForClass(String classId) {
        List<AttendanceSession> out=new ArrayList<>(); for(AttendanceSession s:getAllSessions())if(s.getClassId().equals(classId))out.add(s);return out;
    }
    public List<AttendanceRecord> getRecordsForSession(String sessionId) {
        List<AttendanceRecord> out=new ArrayList<>(); for(RecordRow r:snapshot.records)if(r.sessionId.equals(sessionId))out.add(recordModel(r));return out;
    }
    public AttendanceRecord getRecordForStudent(String sessionId,String studentId) {
        for(RecordRow r:snapshot.records)if(r.sessionId.equals(sessionId)&&r.studentId.equals(studentId))return recordModel(r);return null;
    }
    public List<Student> getSessionStudents(String sessionId) {
        List<Student> out=new ArrayList<>();
        for(AttendanceRecord r:getRecordsForSession(sessionId))out.add(snapshotStudent(r));return out;
    }
    public Student snapshotStudent(AttendanceRecord r) {
        return new Student(r.getStudentId(),r.getMssvSnapshot(),r.getNameSnapshot(),null,"","",0);
    }
    public String sessionClassLabel(AttendanceSession s) {
        if(s.getClassNameSnapshot()!=null)return s.getClassNameSnapshot()+" • "+s.getClassCodeSnapshot();
        ClassRoom c=getClassById(s.getClassId());return c==null?"":c.getName()+" • "+c.getCode();
    }
    private int count(String sessionId,AttendanceRecord.AttendanceStatus status) {
        int n=0;for(AttendanceRecord r:getRecordsForSession(sessionId))if(r.getStatus()==status)n++;return n;
    }
    public int getPresentCount(String id) {return count(id,AttendanceRecord.AttendanceStatus.PRESENT);}
    public int getLateCount(String id) {return count(id,AttendanceRecord.AttendanceStatus.LATE);}
    public int getAbsentCount(String id) {return count(id,AttendanceRecord.AttendanceStatus.ABSENT);}
    public int getUncheckedCount(String id) {return count(id,AttendanceRecord.AttendanceStatus.PENDING);}
    public int getCheckedInTotal(String id) {return getPresentCount(id)+getLateCount(id);}

    public void addClass(ClassRoom model) { saveClass(model,false); }
    public void updateClass(ClassRoom model) { saveClass(model,true); }
    private void saveClass(ClassRoom model,boolean edit) {
        write(() -> {require(model.getName()!=null&&!model.getName().trim().isEmpty(),"Tên lớp là bắt buộc");
            Classroom row=new Classroom();row.id=model.getId()==null?id():model.getId();row.name=model.getName().trim();
            row.code=model.getCode()==null?"":model.getCode().trim();row.note=model.getNote()==null?"":model.getNote().trim();
            if(edit){require(dao.classroom(row.id)!=null,"Lớp không còn tồn tại");dao.updateClass(row);}else dao.insertClass(row);
            model.setId(row.id);return null;});
    }
    public void deleteClass(String id) {
        write(() -> {for(SessionRow s:dao.sessions())require(!s.classId.equals(id),"Lớp đã có buổi điểm danh. Giữ lớp để bảo toàn lịch sử.");dao.deleteClass(id);return null;});
    }
    private StudentRow studentRow(Student model) {
        StudentRow row=new StudentRow();row.id=model.getId()==null?id():model.getId();row.mssv=model.getMssv().trim();
        row.fullName=model.getFullName().trim();row.cardCode=model.getCardBarcode();
        // A scan is an opaque, exact string. Only an absent card is represented as SQL NULL.
        if(row.cardCode!=null&&row.cardCode.isEmpty())row.cardCode=null;
        row.email=model.getEmail()==null?"":model.getEmail();row.phone=model.getPhone()==null?"":model.getPhone();row.avatarColor=model.getAvatarBgColor();
        return row;
    }
    private void validateStudent(StudentRow row) {
        require(!row.mssv.isEmpty()&&!row.fullName.isEmpty(),"Nhập họ tên và MSSV");
        for(StudentRow other:dao.students()) if(!other.id.equals(row.id)) {
            require(!other.mssv.equals(row.mssv),"MSSV đã tồn tại: "+other.fullName);
            require(!Objects.equals(other.cardCode,row.mssv),"Mã bị xung đột với thẻ của "+other.fullName);
            if(row.cardCode!=null) require(!row.cardCode.equals(other.mssv)&&!row.cardCode.equals(other.cardCode),"Mã bị xung đột với "+other.fullName);
        }
    }
    public void addStudentToClass(Student model,String classId) {
        write(() -> {require(dao.classroom(classId)!=null,"Lớp không tồn tại");StudentRow row=studentRow(model);
            require(dao.byMssv(row.mssv)==null,"MSSV đã có. Chọn Thêm vào lớp này trên hồ sơ tìm được.");
            validateStudent(row);dao.insertStudent(row);member(classId,row.id);model.setId(row.id);return null;});
    }
    private void member(String classId,String studentId) {
        ClassMembership m=new ClassMembership();m.classId=classId;m.studentId=studentId;dao.addMember(m);
    }
    public void addExistingToClass(String studentId,String classId) {
        write(() -> {require(dao.student(studentId)!=null&&dao.classroom(classId)!=null,"Không tìm thấy sinh viên hoặc lớp");
            require(dao.membership(classId,studentId)==0,"Sinh viên đã thuộc lớp này");member(classId,studentId);return null;});
    }
    public void removeFromClass(String studentId,String classId) {write(()->{dao.removeMember(classId,studentId);return null;});}
    public void updateStudent(Student model) {
        write(() -> {require(dao.student(model.getId())!=null,"Sinh viên không tồn tại");StudentRow row=studentRow(model);validateStudent(row);dao.updateStudent(row);return null;});
    }
    public void linkCardBarcode(String studentId,String code) {
        write(() -> {StudentRow row=dao.student(studentId);require(row!=null,"Sinh viên không tồn tại");
            row.cardCode=code;validateStudent(row);dao.updateStudent(row);return null;});
    }
    public void addSession(AttendanceSession model) { saveSession(model,false); }
    public void updateSession(AttendanceSession model) { saveSession(model,true); }
    private void saveSession(AttendanceSession model,boolean edit) {
        write(() -> {require(dao.classroom(model.getClassId())!=null,"Chọn lớp học");require(!model.getTitle().trim().isEmpty(),"Nhập tên buổi");
            SessionRow r=new SessionRow();r.id=model.getId()==null?id():model.getId();r.classId=model.getClassId();r.title=model.getTitle().trim();
            r.scheduledAt=parseDateTime(model.getDate(),model.getStartTime());
            r.lateAt=model.isEnableLateThreshold()?parseDateTime(model.getDate(),model.getLateThresholdTime()):null;
            require(r.lateAt==null||r.lateAt>=r.scheduledAt,"Mốc đi muộn phải từ giờ dự kiến trở đi");
            if(edit) {SessionRow old=dao.session(r.id);require(old!=null&&old.status.equals("DRAFT"),"Chỉ sửa được buổi chưa mở");dao.updateSession(r);}
            else dao.insertSession(r);
            model.setId(r.id);return null;});
    }
    public void openSession(String sessionId) {
        write(() -> {SessionRow s=dao.session(sessionId);require(s!=null,"Không tìm thấy buổi");
            require(s.status.equals("DRAFT"),"Chỉ mở buổi chưa mở");
            SessionRow active=dao.active();require(active==null,"Đang mở: "+(active==null?"":active.title)+". Tiếp tục buổi đang mở.");
            List<StudentRow> students=dao.roster(s.classId);require(!students.isEmpty(),"Lớp chưa có sinh viên. Hãy thêm sinh viên trước.");
            List<RecordRow> records=new ArrayList<>();for(StudentRow st:students) {RecordRow r=new RecordRow();r.sessionId=s.id;r.studentId=st.id;r.mssvSnapshot=st.mssv;r.nameSnapshot=st.fullName;records.add(r);}
            dao.insertRecords(records);Classroom c=dao.classroom(s.classId);s.classNameSnapshot=c.name;s.classCodeSnapshot=c.code;
            s.status="OPEN";s.openedAt=System.currentTimeMillis();dao.updateSession(s);return null;});
    }
    public void finishSession(String sessionId) {
        write(() -> {SessionRow s=dao.session(sessionId);require(s!=null&&s.status.equals("OPEN"),"Buổi đã đóng hoặc chưa mở");
            dao.markAbsent(sessionId);s.status="CLOSED";s.closedAt=System.currentTimeMillis();dao.updateSession(s);return null;});
    }
    public AttendanceRecord scan(String sessionId,String raw,long now) {
        return write(() -> {requireOpen(sessionId);List<StudentRow> matches=dao.identify(raw);
            require(!matches.isEmpty(),"Mã chưa đăng ký");require(matches.size()==1,"Mã bị xung đột. Hãy sửa dữ liệu.");
            return checkIn(sessionId,matches.get(0).id,"BARCODE",now);});
    }
    public AttendanceRecord manual(String sessionId,String studentId,long now) {return write(()->checkIn(sessionId,studentId,"MANUAL",now));}
    private SessionRow requireOpen(String id) {SessionRow s=dao.session(id);require(s!=null&&s.status.equals("OPEN"),"Buổi đã đóng hoặc chưa mở");return s;}
    private AttendanceRecord checkIn(String sessionId,String studentId,String source,long now) {
        SessionRow s=requireOpen(sessionId);RecordRow r=dao.record(sessionId,studentId);
        require(r!=null,"Sinh viên không có trong buổi");
        require(r.status.equals("PENDING"),"Đã điểm danh"+(r.checkedAt==null?"":" lúc "+format(r.checkedAt,"HH:mm:ss")));
        String status=AttendanceRules.statusAt(now,s.lateAt);
        int changed=dao.checkIn(sessionId,studentId,status,now,source);
        require(changed==1,"Đã điểm danh");return recordModel(dao.record(sessionId,studentId));
    }
    public void updateRecordStatus(String recordId,AttendanceRecord.AttendanceStatus newStatus,String reason) {
        write(() -> {require(reason!=null&&!reason.trim().isEmpty(),"Nhập lý do chỉnh kết quả");
            require(newStatus!=AttendanceRecord.AttendanceStatus.PENDING,"Chọn Có mặt, Đi muộn hoặc Vắng");
            RecordRow target=null;for(RecordRow r:dao.records())if((r.sessionId+"/"+r.studentId).equals(recordId))target=r;
            require(target!=null,"Không tìm thấy kết quả");SessionRow s=dao.session(target.sessionId);
            require(s.status.equals("CLOSED"),"Chỉ chỉnh kết quả khi buổi đã đóng");
            if(newStatus==AttendanceRecord.AttendanceStatus.ABSENT)target.checkedAt=null;
            if(target.status.equals("ABSENT")||newStatus==AttendanceRecord.AttendanceStatus.ABSENT)target.source="CORRECTION";
            target.status=newStatus.name();target.editedAt=System.currentTimeMillis();target.editReason=reason.trim();dao.updateRecord(target);return null;});
    }

    /** Validates and optionally commits CSV inside the same database transaction. */
    public CsvTransfer.Preview importCsv(String classId,List<CsvTransfer.Row> rows,boolean commit) {
        return write(() -> {
            require(dao.classroom(classId)!=null,"Lớp đích không tồn tại");
            CsvTransfer.Preview preview=new CsvTransfer.Preview();Set<String> seen=new HashSet<>();
            List<StudentRow> existing=dao.students();List<StudentRow> proposed=new ArrayList<>(existing);
            for(CsvTransfer.Row row:rows) {
                String action="";String error=row.error;StudentRow found=null;
                if(error==null && (!seen.add(row.mssv)))error="Trùng MSSV trong tệp";
                if(error==null && (row.mssv.isEmpty()||row.name.isEmpty()))error="Thiếu MSSV hoặc họ tên";
                for(StudentRow s:existing)if(s.mssv.equals(row.mssv))found=s;
                if(error==null && found!=null) {
                    if(!found.fullName.equals(row.name)||(row.hasCardColumn&&!Objects.equals(found.cardCode,row.card)))error="Xung đột với hồ sơ hiện tại";
                    else action=dao.membership(classId,found.id)>0?"Bỏ qua":"Thêm vào lớp";
                }
                StudentRow newRow=null;
                if(error==null && found==null) {
                    newRow=new StudentRow();newRow.id=id();newRow.mssv=row.mssv;newRow.fullName=row.name;newRow.cardCode=row.card;
                    for(StudentRow other:proposed) {
                        if(Objects.equals(other.cardCode,newRow.mssv)||(newRow.cardCode!=null&&(newRow.cardCode.equals(other.mssv)||newRow.cardCode.equals(other.cardCode)))) {error="Mã bị xung đột với "+other.mssv;break;}
                    }
                    if(error==null){proposed.add(newRow);action="Tạo hồ sơ và thêm vào lớp";}
                }
                preview.lines.add("Dòng "+row.line+" · "+row.mssv+" · "+row.name+"\n"+(error==null?action:"Lỗi: "+error));
                if(error!=null)preview.errors++;
                preview.students.add(found==null?newRow:found);preview.actions.add(action);
            }
            if(commit) {
                require(preview.errors==0,"Tệp còn "+preview.errors+" dòng lỗi. Hãy sửa tệp và chọn lại.");
                for(int i=0;i<rows.size();i++) {
                    StudentRow s=preview.students.get(i);String action=preview.actions.get(i);
                    if(action.startsWith("Tạo")){validateStudent(s);dao.insertStudent(s);}
                    if(!action.equals("Bỏ qua"))member(classId,s.id);
                }
            }
            return preview;
        });
    }
}

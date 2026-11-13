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
    private final AuthStore auth;
    private volatile Snapshot snapshot = new Snapshot();
    private boolean initialized;
    private static class Snapshot {
        List<ClassSchedule> schedules=new ArrayList<>();
        List<UserAccount> users=new ArrayList<>();
        List<TeacherClass> assignments=new ArrayList<>();
        List<Classroom> classes = new ArrayList<>();
        List<StudentRow> students = new ArrayList<>();
        List<ClassMembership> members = new ArrayList<>();
        List<SessionRow> sessions = new ArrayList<>();
        List<RecordRow> records = new ArrayList<>();
    }
    public static synchronized void configure(Context context) {
        if (instance == null) instance = new AttendanceRepository(Room.databaseBuilder(context,
                AttendanceDatabase.class,"attendance.db").addMigrations(AttendanceDatabase.MIGRATION_1_2,AttendanceDatabase.MIGRATION_2_3,AttendanceDatabase.MIGRATION_3_4,AttendanceDatabase.MIGRATION_4_5,AttendanceDatabase.MIGRATION_5_6).build(),context.getSharedPreferences("auth",Context.MODE_PRIVATE));
    }
    public static AttendanceRepository getInstance() {
        if (instance == null) throw new IllegalStateException("Chưa khởi tạo dữ liệu");
        return instance;
    }
    public AttendanceRepository(AttendanceDatabase database){this(database,null);}
    public AttendanceRepository(AttendanceDatabase database,android.content.SharedPreferences preferences){db=database;dao=db.store();auth=new AuthStore(dao,preferences);}
    private void worker() { require(Looper.myLooper()!=Looper.getMainLooper(), "Phải truy cập Room ngoài main thread"); }
    public synchronized void initialize() { worker(); if (!initialized) { reload(); initialized=true; } }
    public synchronized boolean isInitialized(){return initialized;}
    public synchronized void reload() {
        worker();
        snapshot=db.runInTransaction(() -> {
            Snapshot s=new Snapshot(); s.schedules=dao.schedules(); s.classes=dao.classes(); s.students=dao.students();
            s.members=dao.memberships(); s.sessions=dao.sessions(); s.records=dao.records();s.users=dao.users();s.assignments=dao.assignments();auth.refresh(); return s;
        });
    }
    private synchronized <T> T write(Callable<T> action) {
        worker();
        T result=db.runInTransaction(action);
        reload();
        return result;
    }
    private <T> T writeAdmin(Callable<T> action){return write(()->{auth.admin();return action.call();});}
    public Account currentAccount(){return auth.current();}
    public boolean isAdmin(){Account user=currentAccount();return user!=null&&user.isAdmin();}
    public boolean needsAdminSetup(){return snapshot.users.isEmpty();}
    public void setupAdmin(String username,String name,String password){write(()->{auth.setup(username,name,password);return null;});}
    public void login(String username,String password){String error=write(()->auth.login(username,password));require(error==null,error);}
    public void logout(){write(()->{auth.logout();return null;});}
    public List<Account> getTeachers(){require(isAdmin(),"Chỉ quản trị viên được xem tài khoản");List<Account> out=new ArrayList<>();for(UserAccount user:snapshot.users)if("TEACHER".equals(user.role))out.add(AuthStore.account(user));return out;}
    public String createTeacher(String username,String name,String password){return writeAdmin(()->{
        UserAccount user=new UserAccount();user.id=id();user.username=AuthStore.username(username);user.fullName=AuthStore.fullName(name);require(dao.username(user.username)==null,"Tên đăng nhập đã tồn tại");PasswordHasher.set(user,password);dao.insertUser(user);return user.id;
    });}
    public void updateTeacher(String userId,String name,boolean enabled,String newPassword){writeAdmin(()->{
        UserAccount user=dao.user(userId);require(user!=null&&"TEACHER".equals(user.role),"Không tìm thấy giảng viên");
        if(!enabled)for(SessionRow session:dao.sessions())require(!"OPEN".equals(session.status)||!userId.equals(session.teacherId),"Hãy kết thúc buổi đang mở của giảng viên trước khi khóa tài khoản");
        user.fullName=AuthStore.fullName(name);user.enabled=enabled;if(newPassword!=null&&!newPassword.isEmpty()){PasswordHasher.set(user,newPassword);user.failedAttempts=0;user.lockedUntil=0;}dao.updateUser(user);return null;
    });}
    public List<String> assignedClassIds(String teacherId){require(isAdmin(),"Chỉ quản trị viên được xem phân công");List<String> out=new ArrayList<>();for(TeacherClass a:snapshot.assignments)if(a.teacherId.equals(teacherId))out.add(a.classId);return out;}
    public List<String> assignedTeacherIds(String classId){require(isAdmin(),"Chỉ quản trị viên được xem phân công");List<String> out=new ArrayList<>();for(TeacherClass a:snapshot.assignments)if(a.classId.equals(classId))out.add(a.teacherId);return out;}
    /** Update one class atomically; preserve every other class assignment. */
    public void replaceClassTeachers(String classId,List<String> teacherIds){
        Set<String> desired=new HashSet<>(teacherIds);
        writeAdmin(()->{
            require(dao.classroom(classId)!=null,"Lớp không tồn tại");
            require(desired.size()<=1,"Mỗi lớp chỉ được phân công một giảng viên");
            List<TeacherClass> all=dao.assignments();
            for(String teacherId:desired){
                UserAccount teacher=dao.user(teacherId);require(teacher!=null&&"TEACHER".equals(teacher.role),"Không tìm thấy giảng viên");
                if(dao.assigned(teacherId,classId)==0)require(teacher.enabled,"Tài khoản giảng viên đang bị khóa");
                Set<String> classes=new HashSet<>();for(TeacherClass a:all)if(a.teacherId.equals(teacherId))classes.add(a.classId);
                classes.add(classId);validateAssignmentSchedules(classes);
            }
            for(SessionRow session:dao.sessions())if(classId.equals(session.classId)&&"OPEN".equals(session.status)&&session.teacherId!=null&&dao.assigned(session.teacherId,classId)>0)
                require(desired.contains(session.teacherId),"Hãy kết thúc buổi đang mở trước khi hủy phân công");
            for(TeacherClass a:all)if(a.classId.equals(classId)&&!desired.contains(a.teacherId))dao.unassign(a.teacherId,classId);
            for(String teacherId:desired){TeacherClass a=new TeacherClass();a.teacherId=teacherId;a.classId=classId;dao.assign(a);}
            return null;
        });
    }
    public void setAssignment(String teacherId,String classId,boolean assigned){writeAdmin(()->{
        UserAccount teacher=dao.user(teacherId);require(teacher!=null&&"TEACHER".equals(teacher.role),"Không tìm thấy giảng viên");require(dao.classroom(classId)!=null,"Lớp không tồn tại");
        if(assigned)requireClassTeacherAvailable(classId,teacherId);
        if(assigned){require(teacher.enabled,"Tài khoản giảng viên đang bị khóa");Set<String> desired=new HashSet<>();for(TeacherClass old:dao.assignments())if(old.teacherId.equals(teacherId))desired.add(old.classId);desired.add(classId);validateAssignmentSchedules(desired);TeacherClass a=new TeacherClass();a.teacherId=teacherId;a.classId=classId;dao.assign(a);}
        else{for(SessionRow session:dao.sessions())require(!"OPEN".equals(session.status)||!teacherId.equals(session.teacherId)||!classId.equals(session.classId),"Hãy kết thúc buổi đang mở trước khi hủy phân công");dao.unassign(teacherId,classId);}return null;
    });}
    public void replaceAssignments(String teacherId,List<String> classIds){writeAdmin(()->{
        UserAccount teacher=dao.user(teacherId);require(teacher!=null&&"TEACHER".equals(teacher.role),"Không tìm thấy giảng viên");Set<String> desired=new HashSet<>(classIds);
        for(String classId:desired){require(dao.classroom(classId)!=null,"Lớp không tồn tại");requireClassTeacherAvailable(classId,teacherId);if(dao.assigned(teacherId,classId)==0)require(teacher.enabled,"Tài khoản giảng viên đang bị khóa");}
        for(SessionRow session:dao.sessions())if("OPEN".equals(session.status)&&teacherId.equals(session.teacherId))require(desired.contains(session.classId),"Hãy kết thúc buổi đang mở trước khi hủy phân công");
        validateAssignmentSchedules(desired);
        for(TeacherClass a:dao.assignments())if(a.teacherId.equals(teacherId)&&!desired.contains(a.classId))dao.unassign(teacherId,a.classId);
        for(String classId:desired){TeacherClass a=new TeacherClass();a.teacherId=teacherId;a.classId=classId;dao.assign(a);}return null;
    });}
    private void requireClassTeacherAvailable(String classId,String teacherId){
        for(TeacherClass assignment:dao.assignments())if(classId.equals(assignment.classId))
            require(teacherId.equals(assignment.teacherId),"Lớp đã có giảng viên. Vào Chi tiết lớp → Phân công giảng viên để thay đổi");
    }
    private boolean classVisible(String classId){Account user=auth.requireCurrent();if(user.isAdmin())return true;for(TeacherClass a:snapshot.assignments)if(a.teacherId.equals(user.id)&&a.classId.equals(classId))return true;return false;}
    private boolean studentVisible(String studentId){if(isAdmin())return true;auth.requireCurrent();for(ClassMembership m:snapshot.members)if(m.studentId.equals(studentId)&&classVisible(m.classId))return true;return false;}
    private void requireClassOnDisk(String classId){UserAccount user=auth.actor();require("ADMIN".equals(user.role)||dao.assigned(user.id,classId)>0,"Bạn không được phân công lớp này");}
    private void requireStudentOnDisk(String studentId){UserAccount user=auth.actor();if("ADMIN".equals(user.role))return;for(ClassMembership m:dao.memberships())if(m.studentId.equals(studentId)&&dao.assigned(user.id,m.classId)>0)return;throw new IllegalArgumentException("Sinh viên không thuộc lớp được phân công");}
    private SessionRow controlledSession(String id){auth.actor();SessionRow session=dao.session(id);require(session!=null,"Không tìm thấy buổi");requireClassOnDisk(session.classId);UserAccount user=auth.actor();require("ADMIN".equals(user.role)||user.id.equals(session.teacherId),"Chỉ giảng viên phụ trách buổi này được thao tác");return session;}
    public boolean canControlSession(String sessionId){AttendanceSession session=getSessionById(sessionId);Account user=currentAccount();return session!=null&&user!=null&&(user.isAdmin()||user.id.equals(session.getTeacherId()));}
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
        Student model=new Student(s.id,s.mssv,s.fullName,s.cardCode,s.email,s.phone,s.avatarColor);
        model.setDigitalCardCode(s.digitalCardCode);model.setBirthDate(s.birthDate);model.setCourse(s.course);
        model.setPrimaryClassId(s.primaryClassId);model.setPhotoPath(s.photoPath);model.setGender(s.gender);return model;
    }
    private AttendanceSession sessionModel(SessionRow r,Snapshot s) {
        int expected=0;
        if(r.status.equals("DRAFT")) { for(ClassMembership m:s.members) if(m.classId.equals(r.classId)) expected++; }
        else for(RecordRow rec:s.records) if(rec.sessionId.equals(r.id)) expected++;
        AttendanceSession model=new AttendanceSession(r.id,r.classId,r.title,format(r.scheduledAt,"dd/MM/yyyy"),
                format(r.scheduledAt,"HH:mm"),r.lateAt!=null,format(r.lateAt,"HH:mm"),
                AttendanceSession.SessionStatus.valueOf(r.status),r.closedAt==null?null:format(r.closedAt,"HH:mm, dd/MM/yyyy"),expected);
        model.setSnapshot(r.classNameSnapshot,r.classCodeSnapshot,r.scheduledAt,r.openedAt,r.closedAt);
        model.setTeacher(r.teacherId,r.teacherNameSnapshot,r.teacherUsernameSnapshot);
        model.setSchedule(r.scheduleId,r.plannedDate,r.plannedShift,r.plannedStartMinute,r.plannedEndMinute);
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
        auth.requireCurrent();
        Snapshot s=snapshot; List<ClassRoom> result=new ArrayList<>();
        for(Classroom r:s.classes)if(classVisible(r.id)) result.add(classModel(r,s)); return result;
    }
    public ClassRoom getClassById(String id) { for(ClassRoom c:getAllClasses()) if(c.getId().equals(id)) return c; return null; }
    public List<Student> getAllStudents() {auth.requireCurrent(); List<Student> out=new ArrayList<>(); for(StudentRow s:snapshot.students)if(!s.archived&&studentVisible(s.id))out.add(studentModel(s)); return out; }
    public Student getStudentById(String id) {auth.requireCurrent(); for(StudentRow s:snapshot.students)if(s.id.equals(id)&&!s.archived&&studentVisible(s.id))return studentModel(s); return null; }
    public Student findByMssv(String code) {auth.requireCurrent(); for(StudentRow s:snapshot.students)if(s.mssv.equals(code)&&!s.archived&&studentVisible(s.id))return studentModel(s); return null; }
    public List<Student> getStudentsForClass(String classId) {
        List<Student> out=new ArrayList<>(); Snapshot s=snapshot;
        require(classVisible(classId),"Bạn không được phân công lớp này");
        for(ClassMembership m:s.members) if(m.classId.equals(classId))
            for(StudentRow student:s.students) if(student.id.equals(m.studentId)&&!student.archived) out.add(studentModel(student));
        out.sort(Comparator.comparing(Student::getMssv)); return out;
    }
    public List<ClassRoom> getClassesForStudent(String studentId) {
        List<ClassRoom> out=new ArrayList<>(); for(ClassRoom c:getAllClasses())if(c.getStudentIds().contains(studentId))out.add(c);return out;
    }
    public List<AttendanceSession> getAllSessions() {auth.requireCurrent(); Snapshot s=snapshot; List<AttendanceSession> out=new ArrayList<>();for(SessionRow r:s.sessions)if(classVisible(r.classId))out.add(sessionModel(r,s));return out; }
    public AttendanceSession getSessionById(String id) { for(AttendanceSession s:getAllSessions())if(s.getId().equals(id))return s;return null; }
    public AttendanceSession getActiveSession() { for(AttendanceSession s:getAllSessions())if(s.getStatus()==AttendanceSession.SessionStatus.OPEN&&(isAdmin()||auth.requireCurrent().id.equals(s.getTeacherId())))return s;return null; }
    public List<AttendanceSession> getSessionsForClass(String classId) {
        List<AttendanceSession> out=new ArrayList<>(); for(AttendanceSession s:getAllSessions())if(s.getClassId().equals(classId))out.add(s);return out;
    }
    public List<AttendanceRecord> getRecordsForSession(String sessionId) {
        require(getSessionById(sessionId)!=null,"Không có quyền xem buổi này");
        List<AttendanceRecord> out=new ArrayList<>(); for(RecordRow r:snapshot.records)if(r.sessionId.equals(sessionId))out.add(recordModel(r));return out;
    }
    public AttendanceRecord getRecordForStudent(String sessionId,String studentId) {
        require(getSessionById(sessionId)!=null,"Không có quyền xem buổi này");
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
        writeAdmin(() -> {require(model.getName()!=null&&!model.getName().trim().isEmpty(),"Tên lớp là bắt buộc");
            Classroom row=new Classroom();row.id=model.getId()==null?id():model.getId();row.name=model.getName().trim();
            row.code=model.getCode()==null?"":model.getCode().trim();row.note=model.getNote()==null?"":model.getNote().trim();
            if(edit){require(dao.classroom(row.id)!=null,"Lớp không còn tồn tại");dao.updateClass(row);}else dao.insertClass(row);
            model.setId(row.id);return null;});
    }
    public void deleteClass(String id) {
        writeAdmin(() -> {for(SessionRow s:dao.sessions())require(!s.classId.equals(id),"Lớp đã có buổi điểm danh. Giữ lớp để bảo toàn lịch sử.");dao.deleteClass(id);return null;});
    }
    private StudentRow studentRow(Student model) {
        StudentRow row=new StudentRow();row.id=model.getId()==null?id():model.getId();row.mssv=model.getMssv().trim();
        row.fullName=model.getFullName().trim();row.cardCode=model.getCardBarcode();
        // A scan is an opaque, exact string. Only an absent card is represented as SQL NULL.
        if(row.cardCode!=null&&row.cardCode.isEmpty())row.cardCode=null;
        row.email=model.getEmail()==null?"":model.getEmail();row.phone=model.getPhone()==null?"":model.getPhone().trim();row.avatarColor=model.getAvatarBgColor();
        row.birthDate=model.getBirthDate();row.course=model.getCourse();row.primaryClassId=model.getPrimaryClassId();row.photoPath=model.getPhotoPath();row.gender=model.getGender();
        return row;
    }
    private void validateStudent(StudentRow row) {
        ProfileRules.validate(row.birthDate,row.course);
        StudentRow original=dao.student(row.id);
        require((!row.mssv.startsWith("TDMU1-")||(original!=null&&row.mssv.equals(original.mssv))) &&
            (row.cardCode==null||!row.cardCode.startsWith("TDMU1-")||(original!=null&&row.cardCode.equals(original.cardCode))),"Tiền tố TDMU1- dành cho thẻ điện tử");
        if(row.primaryClassId!=null)require(dao.classroom(row.primaryClassId)!=null,"Lớp chính không tồn tại");
        require(!row.mssv.isEmpty()&&!row.fullName.isEmpty(),"Nhập họ tên và MSSV");
        require(row.phone.matches("[0-9]{10}"),"Số điện thoại phải gồm đúng 10 chữ số");
        for(StudentRow other:dao.students()) if(!other.id.equals(row.id)) {
            require(!other.mssv.equals(row.mssv),"MSSV đã tồn tại: "+other.fullName);
            require(!other.phone.equals(row.phone),"Số điện thoại đã được dùng cho sinh viên "+other.fullName);
            require(!Objects.equals(other.cardCode,row.mssv),"Mã thẻ đã được dùng cho sinh viên thẻ của "+other.fullName);
            if(row.cardCode!=null) require(!row.cardCode.equals(other.mssv)&&!row.cardCode.equals(other.cardCode),"Mã thẻ đã được dùng cho sinh viên "+other.fullName);
        }
    }
    public void addStudentToClass(Student model,String classId) {
        writeAdmin(() -> {require(dao.classroom(classId)!=null,"Lớp không tồn tại");StudentRow row=studentRow(model);
            require(dao.byMssv(row.mssv)==null,"MSSV đã có. Chọn Thêm vào lớp này trên hồ sơ tìm được.");
            validateStudent(row);row.digitalCardCode=newDigitalCode();if(row.primaryClassId==null)row.primaryClassId=classId;dao.insertStudent(row);member(classId,row.id);model.setId(row.id);return null;});
    }
    private void member(String classId,String studentId) {
        ClassMembership m=new ClassMembership();m.classId=classId;m.studentId=studentId;dao.addMember(m);
    }
    /** Allocates a cohort number and creates the profile/card in the same transaction.
     * A stable creation ID makes a retry after Activity recreation return the saved profile. */
    public Student registerStudent(Student model,String classId) {
        return writeAdmin(()->{
            StudentRow existing=model.getId()==null?null:dao.student(model.getId());
            if(existing!=null){require(!existing.archived,"Hồ sơ đã được lưu trữ");return studentModel(existing);}
            ProfileRules.validateRegistration(model.getFullName(),model.getBirthDate(),model.getCourse(),model.getGender());
            String course=ProfileRules.normalizeCourse(model.getCourse());
            StudentRow row=studentRow(model);row.course=course;
            row.mssv=allocateStudentNumber(course);row.digitalCardCode=row.mssv;row.cardCode=null;
            if(classId!=null){require(dao.classroom(classId)!=null,"Lớp không tồn tại");row.primaryClassId=classId;}
            validateStudent(row);dao.insertStudent(row);
            if(classId!=null)member(classId,row.id);
            model.setId(row.id);model.setMssv(row.mssv);return studentModel(row);
        });
    }
    private String allocateStudentNumber(String course){
        String prefix=course.substring(0,4)+course.substring(5,9);
        StudentNumberSequence sequence=dao.numberSequence(prefix);
        if(sequence==null){sequence=new StudentNumberSequence();sequence.prefix=prefix;}
        // Include archived identities and legacy card codes so an existing code is never reused.
        for(StudentRow other:dao.students())for(String code:new String[]{other.mssv,other.cardCode,other.digitalCardCode}){
            if(code!=null&&code.length()==12&&code.startsWith(prefix)&&code.substring(8).matches("[0-9]{4}"))
                sequence.lastNumber=Math.max(sequence.lastNumber,Integer.parseInt(code.substring(8)));
        }
        require(sequence.lastNumber<9999,"Niên khóa này đã cấp đủ 9.999 MSSV");
        sequence.lastNumber++;dao.saveNumberSequence(sequence);
        return prefix+String.format(Locale.ROOT,"%04d",sequence.lastNumber);
    }
    public void addExistingToClass(String studentId,String classId) {
        writeAdmin(() -> {StudentRow student=dao.student(studentId);require(student!=null&&!student.archived&&dao.classroom(classId)!=null,"Không tìm thấy sinh viên hoặc lớp");
            require(dao.membership(classId,studentId)==0,"Sinh viên đã thuộc lớp này");member(classId,studentId);
            if(student.primaryClassId==null){student.primaryClassId=classId;dao.updateStudent(student);}return null;});
    }
    /** Add identities already in the system, atomically and idempotently. Never changes OPEN snapshots. */
    public int addExistingStudentsToClass(String classId,List<String> studentIds){
        Set<String> desired=new LinkedHashSet<>(studentIds);
        return writeAdmin(()->{
            require(dao.classroom(classId)!=null,"Lớp không còn tồn tại");
            List<StudentRow> students=new ArrayList<>();
            for(String studentId:desired){StudentRow student=dao.student(studentId);require(student!=null&&!student.archived,"Sinh viên không còn trong hệ thống. Hãy tải lại danh sách.");students.add(student);}
            int added=0;
            for(StudentRow student:students){
                ClassMembership membership=new ClassMembership();membership.classId=classId;membership.studentId=student.id;
                if(dao.addMember(membership)!=-1)added++;
                if(student.primaryClassId==null){student.primaryClassId=classId;dao.updateStudent(student);}
            }
            return added;
        });
    }
    public void removeFromClass(String studentId,String classId) {writeAdmin(()->{
        dao.removeMember(classId,studentId);StudentRow student=dao.student(studentId);
        if(student!=null&&classId.equals(student.primaryClassId)){
            student.primaryClassId=null;
            for(ClassMembership m:dao.memberships())if(m.studentId.equals(studentId)){student.primaryClassId=m.classId;break;}
            dao.updateStudent(student);
        }
        return null;
    });}
    public void updateStudent(Student model) {
        writeAdmin(() -> {StudentRow original=dao.student(model.getId());require(original!=null&&!original.archived,"Sinh viên không tồn tại");
            StudentRow row=studentRow(model);
            if(original.mssv.equals(original.digitalCardCode))require(row.mssv.equals(original.mssv),"MSSV đã cấp không thể thay đổi");
            row.digitalCardCode=original.digitalCardCode;validateStudent(row);dao.updateStudent(row);return null;});
    }
    public void linkCardBarcode(String studentId,String code) {
        writeAdmin(() -> {StudentRow row=dao.student(studentId);require(row!=null&&!row.archived,"Sinh viên không tồn tại");
            row.cardCode=code;validateStudent(row);dao.updateStudent(row);return null;});
    }
    public List<ClassSchedule> getSchedules() {
        auth.requireCurrent();List<ClassSchedule> out=new ArrayList<>();
        for(ClassSchedule row:snapshot.schedules)if(!row.archived&&classVisible(row.classId))out.add(row.copy());return out;
    }
    public ClassSchedule getSchedule(String id){for(ClassSchedule row:getSchedules())if(row.id.equals(id))return row;return null;}
    public static final String SCHEDULE_EXISTS="Lớp đã có lịch học. Xác nhận ghi đè để cập nhật lịch hiện có.";
    public ClassSchedule getScheduleForClass(String classId){for(ClassSchedule row:getSchedules())if(row.classId.equals(classId))return row;return null;}
    /** Read-only suggestions: never creates sessions or attendance records. */
    public List<ClassSchedule> getCurrentScheduleSuggestions(long now){
        Account actor=auth.requireCurrent();List<ClassSchedule> out=new ArrayList<>();if(actor.isAdmin())return out;
        String date=format(now,"yyyy-MM-dd");Calendar clock=Calendar.getInstance();clock.setTimeInMillis(now);
        int minute=clock.get(Calendar.HOUR_OF_DAY)*60+clock.get(Calendar.MINUTE);
        for(ClassSchedule rule:getSchedules())if(ScheduleRules.inProgress(rule,date,minute)){
            AttendanceSession session=getOccurrenceSession(rule.id,date);
            if(session==null||session.getStatus()!=AttendanceSession.SessionStatus.CLOSED)out.add(rule);
        }
        out.sort(Comparator.comparingInt((ClassSchedule s)->s.startMinute).thenComparing(s->s.classId));return out;
    }
    private void validateAssignmentSchedules(Set<String> desired){
        List<ClassSchedule> schedules=dao.schedules();
        for(int i=0;i<schedules.size();i++)for(int j=i+1;j<schedules.size();j++){
            ClassSchedule a=schedules.get(i),b=schedules.get(j);
            if(!a.classId.equals(b.classId)&&desired.contains(a.classId)&&desired.contains(b.classId))
                require(!ScheduleRules.overlaps(a,b),"Phân công gây trùng lịch giữa "+dao.classroom(a.classId).name+" và "+dao.classroom(b.classId).name);
        }
    }
    public String saveSchedule(ClassSchedule proposed) {
        return saveSchedule(proposed,false);
    }
    /** Confirmation is required to replace a rule discovered by a stale creation form.
     * Reuse its ID, and archive any older duplicate rules without changing attendance history. */
    public String saveSchedule(ClassSchedule proposed,boolean overwriteExisting) {
        ClassSchedule row=proposed.copy();
        return writeAdmin(()->{
            require(dao.classroom(row.classId)!=null,"Chọn lớp học");
            ClassSchedule old=row.id.isEmpty()?null:dao.schedule(row.id);
            require(row.id.isEmpty()||old!=null,"Lịch không còn tồn tại");
            require(old==null||(!old.archived&&old.classId.equals(row.classId)),"Không đổi lớp hoặc sửa lịch đã lưu trữ");
            List<ClassSchedule> all=dao.schedules();
            List<ClassSchedule> others=new ArrayList<>();
            for(ClassSchedule other:all)if(!other.archived&&other.classId.equals(row.classId)&&!other.id.equals(row.id))others.add(other);
            require(others.isEmpty()||overwriteExisting,SCHEDULE_EXISTS);
            if(old==null&&!others.isEmpty()){
                old=others.remove(0);row.id=old.id;
            }
            row.archived=false;ScheduleRules.validate(row);
            Set<String> teachers=new HashSet<>();for(TeacherClass a:dao.assignments())if(a.classId.equals(row.classId))teachers.add(a.teacherId);
            require(!teachers.isEmpty(),"Hãy phân công giảng viên cho lớp trước khi xếp lịch");
            for(ClassSchedule other:all)if(!other.classId.equals(row.classId)&&ScheduleRules.overlaps(row,other)){
                for(TeacherClass a:dao.assignments())if(a.classId.equals(other.classId))
                    require(!teachers.contains(a.teacherId),"Giảng viên trùng lịch với lớp "+dao.classroom(other.classId).name);
            }
            for(ClassSchedule duplicate:others){duplicate.archived=true;dao.updateSchedule(duplicate);}
            if(old==null){row.id=id();dao.insertSchedule(row);}else dao.updateSchedule(row);
            return row.id;
        });
    }
    /** Archive even unused rules, so references and audit history always remain valid. */
    public void archiveSchedule(String scheduleId){writeAdmin(()->{
        ClassSchedule row=dao.schedule(scheduleId);require(row!=null,"Không tìm thấy lịch");row.archived=true;dao.updateSchedule(row);return null;
    });}
    public AttendanceSession getOccurrenceSession(String scheduleId,String date){
        for(AttendanceSession s:getAllSessions())if(scheduleId.equals(s.getScheduleId())&&date.equals(s.getPlannedDate()))return s;return null;
    }
    /** One transaction includes authorization, deduplication, OPEN limits and roster snapshot. */
    public String startScheduledSession(String scheduleId,String date){return write(()->{
        UserAccount actor=auth.actor();ClassSchedule rule=dao.schedule(scheduleId);
        require(rule!=null,"Không tìm thấy lịch");requireClassOnDisk(rule.classId);
        require("TEACHER".equals(actor.role),"Chức năng điểm danh dành cho giảng viên");
        long day=ScheduleRules.day(date);
        SessionRow existing=dao.occurrence(scheduleId,date);
        if(existing!=null){
            if(!"CLOSED".equals(existing.status))controlledSession(existing.id);
            if("DRAFT".equals(existing.status))openOnDisk(existing);
            return existing.id;
        }
        require(ScheduleRules.occurs(rule,day),"Ngày đã chọn không còn thuộc lịch học đang hoạt động");
        SessionRow row=new SessionRow();row.id=id();row.classId=rule.classId;
        row.title="Theo lịch • "+ScheduleRules.shift(rule)+" • "+ScheduleRules.display(date);
        row.teacherId=actor.id;row.teacherNameSnapshot=actor.fullName;row.teacherUsernameSnapshot=actor.username;
        row.scheduleId=rule.id;row.plannedDate=date;row.plannedShift=rule.shift;
        row.plannedStartMinute=rule.startMinute;row.plannedEndMinute=rule.endMinute;
        row.scheduledAt=parseDateTime(ScheduleRules.display(date),ScheduleRules.time(rule.startMinute));
        row.lateAt=null;
        dao.insertSession(row);openOnDisk(row);return row.id;
    });}

    /** Saves a teacher-adjusted scheduled session and opens it without applying an implicit late rule. */
    public String startScheduledSession(String scheduleId,String date,AttendanceSession details){return write(()->{
        UserAccount actor=auth.actor();ClassSchedule rule=dao.schedule(scheduleId);
        require(rule!=null,"Không tìm thấy lịch");requireClassOnDisk(rule.classId);
        require("TEACHER".equals(actor.role),"Chức năng điểm danh dành cho giảng viên");
        require(ScheduleRules.occurs(rule,ScheduleRules.day(date)),"Ngày đã chọn không còn thuộc lịch học đang hoạt động");
        require(rule.classId.equals(details.getClassId()),"Lớp không khớp với lịch học");
        require(ScheduleRules.display(date).equals(details.getDate()),"Ngày điểm danh không khớp với lịch học");
        require(details.getTitle()!=null&&!details.getTitle().trim().isEmpty(),"Nhập tên buổi");
        long scheduledAt=parseDateTime(details.getDate(),details.getStartTime());
        Long lateAt=details.isEnableLateThreshold()?parseDateTime(details.getDate(),details.getLateThresholdTime()):null;
        require(lateAt==null||lateAt>=scheduledAt,"Mốc đi muộn phải từ giờ dự kiến trở đi");
        SessionRow row=dao.occurrence(scheduleId,date);
        if(row!=null){
            if(!"DRAFT".equals(row.status))return row.id;
            controlledSession(row.id);require(row.classId.equals(rule.classId),"Lớp của buổi không khớp với lịch học");
            row.title=details.getTitle().trim();row.scheduledAt=scheduledAt;row.lateAt=lateAt;dao.updateSession(row);
        }else{
            row=new SessionRow();row.id=details.getId()==null?id():details.getId();row.classId=rule.classId;row.title=details.getTitle().trim();
            row.teacherId=actor.id;row.teacherNameSnapshot=actor.fullName;row.teacherUsernameSnapshot=actor.username;
            row.scheduleId=rule.id;row.plannedDate=date;row.plannedShift=rule.shift;
            row.plannedStartMinute=rule.startMinute;row.plannedEndMinute=rule.endMinute;row.scheduledAt=scheduledAt;row.lateAt=lateAt;
            dao.insertSession(row);
        }
        openOnDisk(row);details.setId(row.id);return row.id;
    });}

    public void addSession(AttendanceSession model) { saveSession(model,false); }
    public void updateSession(AttendanceSession model) { saveSession(model,true); }
    private void saveSession(AttendanceSession model,boolean edit) {
        write(() -> {requireClassOnDisk(model.getClassId());UserAccount actor=auth.actor();require(dao.classroom(model.getClassId())!=null,"Chọn lớp học");require(!model.getTitle().trim().isEmpty(),"Nhập tên buổi");
            SessionRow r=new SessionRow();r.id=model.getId()==null?id():model.getId();r.classId=model.getClassId();r.title=model.getTitle().trim();
            r.teacherId=actor.id;r.teacherNameSnapshot=actor.fullName;r.teacherUsernameSnapshot=actor.username;
            r.scheduledAt=parseDateTime(model.getDate(),model.getStartTime());
            r.lateAt=model.isEnableLateThreshold()?parseDateTime(model.getDate(),model.getLateThresholdTime()):null;
            require(r.lateAt==null||r.lateAt>=r.scheduledAt,"Mốc đi muộn phải từ giờ dự kiến trở đi");
            if(edit) {SessionRow old=controlledSession(r.id);require(old!=null&&old.status.equals("DRAFT"),"Chỉ sửa được buổi chưa mở");require(old.scheduleId==null,"Không sửa thông tin buổi theo lịch");require(old.classId.equals(r.classId),"Không đổi lớp của buổi đã tạo");r.teacherId=old.teacherId;r.teacherNameSnapshot=old.teacherNameSnapshot;r.teacherUsernameSnapshot=old.teacherUsernameSnapshot;dao.updateSession(r);}
            else dao.insertSession(r);
            model.setId(r.id);return null;});
    }
    public void openSession(String sessionId) {
        write(() -> {openOnDisk(controlledSession(sessionId));return null;});
    }
    private void openOnDisk(SessionRow s) {
            require(s.status.equals("DRAFT"),"Chỉ mở buổi chưa mở");
            UserAccount owner=s.teacherId==null?auth.actor():dao.user(s.teacherId);
            require(owner!=null,"Không tìm thấy tài khoản phụ trách buổi");
            s.teacherId=owner.id;s.teacherNameSnapshot=owner.fullName;s.teacherUsernameSnapshot=owner.username;
            for(SessionRow active:dao.sessions())if("OPEN".equals(active.status)){
                require(!active.classId.equals(s.classId),"Lớp này đang có buổi điểm danh chưa kết thúc: "+active.title);
                require(s.teacherId==null||!s.teacherId.equals(active.teacherId),"Giảng viên này đang có buổi điểm danh chưa kết thúc: "+active.title);
            }
            List<StudentRow> students=dao.roster(s.classId);require(!students.isEmpty(),"Lớp chưa có sinh viên. Hãy thêm sinh viên trước.");
            List<RecordRow> records=new ArrayList<>();for(StudentRow st:students) {RecordRow r=new RecordRow();r.sessionId=s.id;r.studentId=st.id;r.mssvSnapshot=st.mssv;r.nameSnapshot=st.fullName;records.add(r);}
            dao.insertRecords(records);Classroom c=dao.classroom(s.classId);s.classNameSnapshot=c.name;s.classCodeSnapshot=c.code;
            s.status="OPEN";s.openedAt=System.currentTimeMillis();dao.updateSession(s);
    }
    public void finishSession(String sessionId) {
        write(() -> {SessionRow s=controlledSession(sessionId);require(s!=null&&s.status.equals("OPEN"),"Buổi đã đóng hoặc chưa mở");
            dao.markAbsent(sessionId);s.status="CLOSED";s.closedAt=System.currentTimeMillis();dao.updateSession(s);return null;});
    }
    public AttendanceRecord scan(String sessionId,String raw,long now) {return scan(sessionId,raw,null,now);}
    public AttendanceRecord scan(String sessionId,String raw,String format,long now) {
        return write(() -> {requireOpen(sessionId);List<StudentRow> matches=identify(raw);
            require(!matches.isEmpty(),"Thẻ này chưa được liên kết với sinh viên nào");require(matches.size()==1,"Mã thẻ bị trùng. Vui lòng kiểm tra lại thông tin thẻ.");
            validateFormat(matches.get(0),raw,format);return checkIn(sessionId,matches.get(0).id,"BARCODE",now);});
    }
    private List<StudentRow> identify(String raw){
        require(raw!=null&&!raw.isEmpty(),"Thẻ này chưa được liên kết với sinh viên nào");
        if(raw.startsWith("TDMU1-")) {
            List<StudentRow> digital=dao.digital(raw);if(!digital.isEmpty())return digital;
            List<StudentRow> legacy=dao.identify(raw);if(!legacy.isEmpty())return legacy;
            require(CardCodes.valid(raw),"Định dạng mã thẻ điện tử không hợp lệ");return digital;
        }
        return dao.identify(raw);
    }
    /** Read-only verification, never creates or updates attendance. */
    private void validateFormat(StudentRow row,String raw,String format){
        if(raw.equals(row.digitalCardCode)&&format!=null)require("CODE_128".equals(format),"Mã vạch trên thẻ này không hợp lệ");
    }
    public Student verifyCard(String raw){return verifyCard(raw,null);}
    public synchronized Student verifyCard(String raw,String format){
        worker();return db.runInTransaction(()->{auth.actor();List<StudentRow> matches=identify(raw);
            require(!matches.isEmpty(),"Thẻ này chưa được liên kết với sinh viên nào");require(matches.size()==1,"Mã thẻ bị trùng. Vui lòng kiểm tra lại thông tin thẻ.");requireStudentOnDisk(matches.get(0).id);validateFormat(matches.get(0),raw,format);return studentModel(matches.get(0));});
    }
    private String newDigitalCode(){
        for(int attempt=0;attempt<32;attempt++){
            String code=CardCodes.create();boolean exists=false;
            for(StudentRow st:dao.students())if(code.equals(st.digitalCardCode)||code.equals(st.cardCode)||code.equals(st.mssv)){exists=true;break;}
            if(!exists)return code;
        }
        throw new IllegalArgumentException("Không thể cấp mã duy nhất. Hãy thử lại.");
    }
    public String issueDigitalCard(String studentId){
        return writeAdmin(()->{StudentRow row=dao.student(studentId);require(row!=null&&!row.archived,"Sinh viên không tồn tại");
            if(row.digitalCardCode==null){row.digitalCardCode=newDigitalCode();dao.updateStudent(row);}return row.digitalCardCode;});
    }
    public void addStudent(Student model){
        writeAdmin(()->{StudentRow row=studentRow(model);validateStudent(row);row.digitalCardCode=newDigitalCode();dao.insertStudent(row);model.setId(row.id);return null;});
    }
    /** Archive identity because records reference it; remove only current memberships. */
    public void deleteStudent(String studentId){
        writeAdmin(()->{StudentRow row=dao.student(studentId);require(row!=null,"Sinh viên không tồn tại");row.archived=true;dao.updateStudent(row);
            for(ClassMembership m:dao.memberships())if(m.studentId.equals(studentId))dao.removeMember(m.classId,studentId);return null;});
    }
    public AttendanceRecord manual(String sessionId,String studentId,long now) {return write(()->checkIn(sessionId,studentId,"MANUAL",now));}
    private SessionRow requireOpen(String id) {SessionRow s=controlledSession(id);require(s!=null&&s.status.equals("OPEN"),"Buổi đã đóng hoặc chưa mở");return s;}
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
            require(target!=null,"Không tìm thấy kết quả");SessionRow s=controlledSession(target.sessionId);
            require(s.status.equals("CLOSED"),"Chỉ chỉnh kết quả khi buổi đã đóng");
            if(newStatus==AttendanceRecord.AttendanceStatus.ABSENT)target.checkedAt=null;
            if(target.status.equals("ABSENT")||newStatus==AttendanceRecord.AttendanceStatus.ABSENT)target.source="CORRECTION";
            target.status=newStatus.name();target.editedAt=System.currentTimeMillis();target.editReason=reason.trim();dao.updateRecord(target);return null;});
    }

    /** Validates and optionally commits CSV inside the same database transaction. */
    public CsvTransfer.Preview importStudentsCsv(List<CsvTransfer.Row> rows,boolean commit){return importCsv(null,rows,commit);}
    public CsvTransfer.Preview importCsv(String classId,List<CsvTransfer.Row> rows,boolean commit) {
        return writeAdmin(() -> {
            require(classId==null||dao.classroom(classId)!=null,"Lớp đích không tồn tại");
            CsvTransfer.Preview preview=new CsvTransfer.Preview();
            Set<String> usedPhones=new HashSet<>();
            for(StudentRow student:dao.students())if(student.phone!=null&&!student.phone.isEmpty())usedPhones.add(student.phone);
            for(CsvTransfer.Row row:rows) {
                String error=row.error;
                if(error==null)try{
                    ProfileRules.validateRegistration(row.name,row.birthDate,row.course,row.gender);
                    String phone=row.phone==null?"":row.phone.trim();
                    require(phone.matches("[0-9]{10}"),"Số điện thoại phải gồm đúng 10 chữ số");
                    require(usedPhones.add(phone),"Số điện thoại bị trùng với sinh viên đã có hoặc trong tệp");
                } catch(IllegalArgumentException e){error=e.getMessage();}
                preview.lines.add("Dòng "+row.line+" · "+row.name+"\n"+(error==null?"Hợp lệ · MSSV sẽ tự cấp":"Lỗi: "+error));
                if(error!=null)preview.errors++;
            }
            if(commit) {
                require(preview.errors==0,"Tệp còn "+preview.errors+" dòng cần sửa. Vui lòng sửa rồi thử lại.");
                for(CsvTransfer.Row csvRow:rows) {
                    ProfileRules.validateRegistration(csvRow.name,csvRow.birthDate,csvRow.course,csvRow.gender);
                    StudentRow student=new StudentRow();student.id=id();student.fullName=csvRow.name.trim();
                    student.birthDate=csvRow.birthDate;student.course=ProfileRules.normalizeCourse(csvRow.course);student.gender=csvRow.gender;student.phone=csvRow.phone.trim();
                    student.mssv=allocateStudentNumber(student.course);student.digitalCardCode=student.mssv;student.primaryClassId=classId;
                    validateStudent(student);dao.insertStudent(student);
                    if(classId!=null)member(classId,student.id);
                }
            }
            return preview;
        });
    }
}

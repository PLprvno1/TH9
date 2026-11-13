package com.example.th9.data;

import com.example.th9.model.*;
import org.apache.commons.csv.*;
import java.io.*;
import java.util.*;

/** RFC 4180 parser/printer: quoted commas, escaped quotes, multiline values and UTF-8 BOM. */
public final class CsvTransfer {
    public static class Row {
        public int line;
        public String name,birthDate,course,gender,phone,error;
    }
    public static class Preview {
        public int errors;
        public final List<String> lines=new ArrayList<>();
    }
    public static List<Row> parse(Reader source) throws IOException {
        PushbackReader input=new PushbackReader(source,1);
        int first=input.read();if(first!=-1&&first!=0xFEFF)input.unread(first);
        CSVFormat format=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build();
        try(CSVParser parser=new CSVParser(input,format)) {
            List<String> headers=parser.getHeaderNames();
            AttendanceRules.require(headers.contains("ho_ten")&&headers.contains("ngay_sinh")&&headers.contains("nien_khoa")&&headers.contains("gioi_tinh")&&headers.contains("so_dien_thoai"),
                "Tệp cần có các cột ho_ten, ngay_sinh, nien_khoa, gioi_tinh và so_dien_thoai");
            AttendanceRules.require(new HashSet<>(headers).size()==headers.size(),"Tên cột bị lặp");
            List<Row> out=new ArrayList<>();
            for(CSVRecord record:parser) {
                Row row=new Row();row.line=(int)record.getRecordNumber()+1;
                row.name=record.isSet("ho_ten")?record.get("ho_ten").trim():"";
                row.birthDate=record.isSet("ngay_sinh")?record.get("ngay_sinh").trim():"";
                row.course=ProfileRules.normalizeCourse(record.isSet("nien_khoa")?record.get("nien_khoa"):"");
                row.gender=normalizeGender(record.isSet("gioi_tinh")?record.get("gioi_tinh"):"");
                row.phone=record.isSet("so_dien_thoai")?record.get("so_dien_thoai").trim():"";
                if(!record.isConsistent())row.error="Số cột trong dòng không đúng";
                out.add(row);
                AttendanceRules.require(out.size()<=20000,"Tệp không được có quá 20.000 dòng");
            }
            AttendanceRules.require(!out.isEmpty(),"Tệp chưa có sinh viên nào");return out;
        } catch(UncheckedIOException e) {throw e.getCause();}
    }
    private static String normalizeGender(String value){
        String gender=value==null?"":value.trim();
        if("nam".equalsIgnoreCase(gender))return "Nam";
        if("nữ".equalsIgnoreCase(gender))return "Nữ";
        if("khác".equalsIgnoreCase(gender))return "Khác";
        return gender;
    }
    public static String csv(AttendanceRepository repo,String sessionId) throws IOException {
        AttendanceSession s=repo.getSessionById(sessionId);
        AttendanceRules.require(s!=null,"Buổi không tồn tại");
        StringWriter text=new StringWriter();
        try(CSVPrinter printer=new CSVPrinter(text,CSVFormat.RFC4180)) {
            printer.printRecord("lop","ma_nhom","buoi","mssv","ho_ten","trang_thai","gio_ghi_nhan","nguon","gio_chinh","ly_do_chinh","giang_vien","tai_khoan_giang_vien");
            ClassRoom c=repo.getClassById(s.getClassId());
            for(AttendanceRecord r:repo.getRecordsForSession(sessionId))printer.printRecord(
                s.getClassNameSnapshot()!=null?s.getClassNameSnapshot():c.getName(),
                s.getClassCodeSnapshot()!=null?s.getClassCodeSnapshot():c.getCode(),s.getTitle(),r.getMssvSnapshot(),r.getNameSnapshot(),
                r.getStatusDisplayName(),AttendanceRepository.format(r.getCheckedAt(),"dd/MM/yyyy HH:mm:ss"),r.getSourceDisplayName(),
                AttendanceRepository.format(r.getEditedAt(),"dd/MM/yyyy HH:mm:ss"),r.getNote(),s.getTeacherNameSnapshot(),s.getTeacherUsernameSnapshot());
        }
        return text.toString();
    }
    public static String summary(AttendanceRepository repo,String sessionId) {
        AttendanceSession s=repo.getSessionById(sessionId);if(s==null)return "";
        return "ĐIỂM DANH SINH VIÊN\n"+repo.sessionClassLabel(s)+"\n"+s.getTitle()+"\nGiảng viên: "+s.getTeacherNameSnapshot()+"\n"+s.getDate()+" "+s.getStartTime()
            +"\n"+repo.getCheckedInTotal(sessionId)+"/"+s.getExpectedCount()+" đã điểm danh"
            +"\nCó mặt: "+repo.getPresentCount(sessionId)+" · Đi muộn: "+repo.getLateCount(sessionId)+" · Vắng: "+repo.getAbsentCount(sessionId);
    }
}

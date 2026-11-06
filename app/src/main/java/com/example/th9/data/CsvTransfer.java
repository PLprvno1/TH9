package com.example.th9.data;

import com.example.th9.model.*;
import org.apache.commons.csv.*;
import java.io.*;
import java.util.*;

/** RFC 4180 parser/printer: quoted commas, escaped quotes, multiline values and UTF-8 BOM. */
public final class CsvTransfer {
    public static class Row {
        public int line;
        public String mssv,name,card,error;
        public boolean hasCardColumn;
    }
    public static class Preview {
        public int errors;
        public final List<String> lines=new ArrayList<>();
        final List<AttendanceDatabase.StudentRow> students=new ArrayList<>();
        final List<String> actions=new ArrayList<>();
    }
    public static List<Row> parse(Reader source) throws IOException {
        PushbackReader input=new PushbackReader(source,1);
        int first=input.read();if(first!=-1&&first!=0xFEFF)input.unread(first);
        CSVFormat format=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build();
        try(CSVParser parser=new CSVParser(input,format)) {
            List<String> headers=parser.getHeaderNames();
            AttendanceRules.require(headers.contains("mssv")&&headers.contains("ho_ten"),"Header bắt buộc: mssv,ho_ten; tùy chọn: ma_the");
            AttendanceRules.require(new HashSet<>(headers).size()==headers.size(),"Header bị trùng");
            List<Row> out=new ArrayList<>();
            for(CSVRecord record:parser) {
                Row row=new Row();row.line=(int)record.getRecordNumber()+1;
                row.mssv=record.isSet("mssv")?record.get("mssv").trim():"";
                row.name=record.isSet("ho_ten")?record.get("ho_ten").trim():"";
                row.hasCardColumn=headers.contains("ma_the");
                row.card=row.hasCardColumn&&record.isSet("ma_the")?record.get("ma_the"):null;
                if("".equals(row.card))row.card=null;
                if(!record.isConsistent())row.error="Số trường không khớp header";
                out.add(row);
                AttendanceRules.require(out.size()<=20000,"Tệp vượt quá 20.000 dòng");
            }
            AttendanceRules.require(!out.isEmpty(),"Tệp chưa có sinh viên");return out;
        } catch(UncheckedIOException e) {throw e.getCause();}
    }
    public static String csv(AttendanceRepository repo,String sessionId) throws IOException {
        AttendanceSession s=repo.getSessionById(sessionId);
        AttendanceRules.require(s!=null,"Buổi không tồn tại");
        StringWriter text=new StringWriter();
        try(CSVPrinter printer=new CSVPrinter(text,CSVFormat.RFC4180)) {
            printer.printRecord("lop","ma_nhom","buoi","mssv","ho_ten","trang_thai","gio_ghi_nhan","nguon","gio_chinh","ly_do_chinh");
            ClassRoom c=repo.getClassById(s.getClassId());
            for(AttendanceRecord r:repo.getRecordsForSession(sessionId))printer.printRecord(
                s.getClassNameSnapshot()!=null?s.getClassNameSnapshot():c.getName(),
                s.getClassCodeSnapshot()!=null?s.getClassCodeSnapshot():c.getCode(),s.getTitle(),r.getMssvSnapshot(),r.getNameSnapshot(),
                r.getStatusDisplayName(),AttendanceRepository.format(r.getCheckedAt(),"dd/MM/yyyy HH:mm:ss"),r.getSourceDisplayName(),
                AttendanceRepository.format(r.getEditedAt(),"dd/MM/yyyy HH:mm:ss"),r.getNote());
        }
        return text.toString();
    }
    public static String summary(AttendanceRepository repo,String sessionId) {
        AttendanceSession s=repo.getSessionById(sessionId);if(s==null)return "";
        return "ĐIỂM DANH SINH VIÊN\n"+repo.sessionClassLabel(s)+"\n"+s.getTitle()+"\n"+s.getDate()+" "+s.getStartTime()
            +"\n"+repo.getCheckedInTotal(sessionId)+"/"+s.getExpectedCount()+" đã điểm danh"
            +"\nCó mặt: "+repo.getPresentCount(sessionId)+" · Đi muộn: "+repo.getLateCount(sessionId)+" · Vắng: "+repo.getAbsentCount(sessionId);
    }
}

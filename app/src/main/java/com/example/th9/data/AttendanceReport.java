package com.example.th9.data;

import com.example.th9.model.AttendanceRecord;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.ClassRoom;
import com.example.th9.model.Student;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One immutable snapshot shared by the on-screen tables and the Excel export. */
public final class AttendanceReport {
    public final String period, legend;
    public final List<ClassTable> tables;

    private AttendanceReport(String period, String legend, List<ClassTable> tables) {
        this.period = period;
        this.legend = legend;
        this.tables = Collections.unmodifiableList(new ArrayList<>(tables));
    }

    public static final class ClassTable {
        public final String title;
        public final List<String> headers;
        public final List<List<String>> rows;
        public final int sessionCount;

        private ClassTable(String title, List<String> headers, List<List<String>> rows, int sessionCount) {
            this.title = title;
            this.headers = Collections.unmodifiableList(new ArrayList<>(headers));
            List<List<String>> copy = new ArrayList<>();
            for (List<String> row : rows) copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
            this.rows = Collections.unmodifiableList(copy);
            this.sessionCount = sessionCount;
        }
    }

    private static final class StudentLine {
        final String number, name;
        final List<String> marks;

        StudentLine(String number, String name, int sessions) {
            this.number = number;
            this.name = name;
            marks = new ArrayList<>(Collections.nCopies(sessions, ""));
        }
    }

    public static AttendanceReport capture(AttendanceRepository repo, List<ClassRoom> classes,
                                           List<AttendanceSession> sessions, String period, String legend) {
        List<ClassTable> tables = new ArrayList<>();
        for (ClassRoom classroom : classes) {
            List<AttendanceSession> classSessions = new ArrayList<>();
            for (AttendanceSession session : sessions) {
                if (classroom.getId().equals(session.getClassId())
                        && session.getStatus() != AttendanceSession.SessionStatus.DRAFT) classSessions.add(session);
            }
            classSessions.sort(Comparator.comparingLong(AttendanceSession::getScheduledAt)
                    .thenComparing(AttendanceSession::getId));
            List<String> headers = new ArrayList<>(Arrays.asList("STT", "MSSV", "Họ và tên"));
            for (AttendanceSession session : classSessions) {
                headers.add(session.getDate() + "\n" + session.getStartTime()
                        + (session.getStatus() == AttendanceSession.SessionStatus.OPEN ? "\nĐang mở" : ""));
            }
            Map<String, StudentLine> students = new LinkedHashMap<>();
            for (Student student : repo.getStudentsForClass(classroom.getId())) {
                students.put(student.getId(), new StudentLine(student.getMssv(), student.getFullName(), classSessions.size()));
            }
            for (int column = 0; column < classSessions.size(); column++) {
                for (AttendanceRecord record : repo.getRecordsForSession(classSessions.get(column).getId())) {
                    StudentLine line = students.get(record.getStudentId());
                    if (line == null) {
                        // Keep historical attendance when a student has since left the roster.
                        line = new StudentLine(record.getMssvSnapshot(), record.getNameSnapshot() + " (đã rời lớp)", classSessions.size());
                        students.put(record.getStudentId(), line);
                    }
                    if (record.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT) line.marks.set(column, "x");
                    else if (record.getStatus() == AttendanceRecord.AttendanceStatus.LATE) line.marks.set(column, "t");
                }
            }
            List<StudentLine> sorted = new ArrayList<>(students.values());
            sorted.sort(Comparator.comparing((StudentLine line) -> line.number).thenComparing(line -> line.name));
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i++) {
                StudentLine line = sorted.get(i);
                List<String> cells = new ArrayList<>(Arrays.asList(String.valueOf(i + 1), line.number, line.name));
                cells.addAll(line.marks);
                rows.add(cells);
            }
            String title = classroom.getName() + (classroom.getCode().isEmpty() ? "" : " · " + classroom.getCode());
            tables.add(new ClassTable(title, headers, rows, classSessions.size()));
        }
        return new AttendanceReport(period, legend, tables);
    }
}

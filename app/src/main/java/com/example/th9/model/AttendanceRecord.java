package com.example.th9.model;

import java.io.Serializable;

public class AttendanceRecord implements Serializable {
    public enum AttendanceStatus {
        PRESENT,   // Có mặt
        LATE,      // Đi muộn
        ABSENT,    // Vắng
        PENDING  // Chưa điểm danh
    }

    public enum CheckInSource {
        BARCODE,     // Quét mã vạch thẻ
        MANUAL,      // Điểm danh thủ công
        CORRECTION,
        AUTO_FINISH  // Tự động khi đóng buổi
    }

    private String id;
    private String sessionId;
    private String studentId;
    private AttendanceStatus status;
    private String checkInTime;
    private CheckInSource source;
    private String note;
    private String mssvSnapshot, nameSnapshot;
    private Long checkedAt, editedAt;
    public void setSnapshot(String mssv,String name,Long checked,Long edited) {
        mssvSnapshot=mssv;nameSnapshot=name;checkedAt=checked;editedAt=edited;
    }
    public String getMssvSnapshot(){return mssvSnapshot;}
    public String getNameSnapshot(){return nameSnapshot;}
    public Long getCheckedAt(){return checkedAt;}
    public Long getEditedAt(){return editedAt;}

    public AttendanceRecord(String id, String sessionId, String studentId, AttendanceStatus status,
                            String checkInTime, CheckInSource source, String note) {
        this.id = id;
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.status = status;
        this.checkInTime = checkInTime;
        this.source = source;
        this.note = note;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public String getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(String checkInTime) {
        this.checkInTime = checkInTime;
    }

    public CheckInSource getSource() {
        return source;
    }

    public void setSource(CheckInSource source) {
        this.source = source;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatusDisplayName() {
        switch (status) {
            case PRESENT:
                return "Có mặt";
            case LATE:
                return "Đi muộn";
            case ABSENT:
                return "Vắng";
            case PENDING:
                return "Chưa điểm danh";
            default:
                return "";
        }
    }

    public String getSourceDisplayName() {
        if (source == null) return "";
        switch (source) {
            case CORRECTION:
                return "Chỉnh thủ công";
            case BARCODE:
                return "Quét thẻ";
            case MANUAL:
                return "Thủ công";
            case AUTO_FINISH:
                return "Tự động đóng";
            default:
                return "";
        }
    }
}


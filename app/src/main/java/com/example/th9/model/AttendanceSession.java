package com.example.th9.model;

import java.io.Serializable;

public class AttendanceSession implements Serializable {
    public enum SessionStatus {
        DRAFT, // Chưa mở
        OPEN,   // Đang mở
        CLOSED  // Đã kết thúc
    }

    private String id;
    private String classId;
    private String title;
    private String date;
    private String startTime;
    private boolean enableLateThreshold;
    private String lateThresholdTime;
    private SessionStatus status;
    private String finishedTime;
    private int expectedCount;
    private String classNameSnapshot, classCodeSnapshot;
    private long scheduledAt;
    private Long openedAt, closedAt;

    public void setSnapshot(String name,String code,long scheduled,Long opened,Long closed) {
        classNameSnapshot=name;classCodeSnapshot=code;scheduledAt=scheduled;openedAt=opened;closedAt=closed;
    }
    public String getClassNameSnapshot(){return classNameSnapshot;}
    public String getClassCodeSnapshot(){return classCodeSnapshot;}
    public long getScheduledAt(){return scheduledAt;}
    public Long getOpenedAt(){return openedAt;}
    public Long getClosedAt(){return closedAt;}

    public AttendanceSession(String id, String classId, String title, String date, String startTime,
                             boolean enableLateThreshold, String lateThresholdTime,
                             SessionStatus status, String finishedTime, int expectedCount) {
        this.id = id;
        this.classId = classId;
        this.title = title;
        this.date = date;
        this.startTime = startTime;
        this.enableLateThreshold = enableLateThreshold;
        this.lateThresholdTime = lateThresholdTime;
        this.status = status;
        this.finishedTime = finishedTime;
        this.expectedCount = expectedCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClassId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public boolean isEnableLateThreshold() {
        return enableLateThreshold;
    }

    public void setEnableLateThreshold(boolean enableLateThreshold) {
        this.enableLateThreshold = enableLateThreshold;
    }

    public String getLateThresholdTime() {
        return lateThresholdTime;
    }

    public void setLateThresholdTime(String lateThresholdTime) {
        this.lateThresholdTime = lateThresholdTime;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getFinishedTime() {
        return finishedTime;
    }

    public void setFinishedTime(String finishedTime) {
        this.finishedTime = finishedTime;
    }

    public int getExpectedCount() {
        return expectedCount;
    }

    public void setExpectedCount(int expectedCount) {
        this.expectedCount = expectedCount;
    }

    public String getStatusDisplayName() {
        switch (status) {
            case DRAFT:
                return "Chưa mở";
            case OPEN:
                return "Đang mở";
            case CLOSED:
                return "Đã kết thúc";
            default:
                return "";
        }
    }
}


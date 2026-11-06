package com.example.th9.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ClassRoom implements Serializable {
    private String id;
    private String name;
    private String code;
    private String note;
    private List<String> studentIds;

    public ClassRoom(String id, String name, String code, String note) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.note = note;
        this.studentIds = new ArrayList<>();
    }

    public ClassRoom(String id, String name, String code, String note, List<String> studentIds) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.note = note;
        this.studentIds = studentIds != null ? new ArrayList<>(studentIds) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<String> getStudentIds() {
        return studentIds;
    }

    public void setStudentIds(List<String> studentIds) {
        this.studentIds = studentIds;
    }

    public int getStudentCount() {
        return studentIds != null ? studentIds.size() : 0;
    }
}


package com.example.th9.model;

import java.io.Serializable;

public class Student implements Serializable {
    private String id;
    private String mssv;
    private String fullName;
    private String cardBarcode; // e.g. "SV20210001", or null/empty
    private String email;
    private String phone;
    private int avatarBgColor;

    public Student(String id, String mssv, String fullName, String cardBarcode, String email, String phone, int avatarBgColor) {
        this.id = id;
        this.mssv = mssv;
        this.fullName = fullName;
        this.cardBarcode = cardBarcode;
        this.email = email;
        this.phone = phone;
        this.avatarBgColor = avatarBgColor;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMssv() {
        return mssv;
    }

    public void setMssv(String mssv) {
        this.mssv = mssv;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCardBarcode() {
        return cardBarcode;
    }

    public void setCardBarcode(String cardBarcode) {
        this.cardBarcode = cardBarcode;
    }

    public boolean hasCardBarcode() {
        return cardBarcode != null && !cardBarcode.trim().isEmpty();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getAvatarBgColor() {
        return avatarBgColor;
    }

    public void setAvatarBgColor(int avatarBgColor) {
        this.avatarBgColor = avatarBgColor;
    }

    public String getInitial() {
        if (fullName == null || fullName.trim().isEmpty()) return "S";
        String[] parts = fullName.trim().split("\\s+");
        String lastPart = parts[parts.length - 1];
        return lastPart.substring(0, 1).toUpperCase();
    }
}


package com.example.th9.data;
import java.text.*;
import java.util.*;
public final class ProfileRules {
    public static String normalizeCourse(String course){
        return course==null?"":course.trim().replace(" ","").replace('—','–').replace('-','–');
    }
    public static void validateRegistration(String name,String birth,String course,String gender){
        AttendanceRules.require(name!=null&&!name.trim().isEmpty(),"Nhập họ và tên sinh viên");
        AttendanceRules.require(birth!=null&&!birth.isEmpty(),"Chọn ngày sinh");
        AttendanceRules.require(course!=null&&!course.trim().isEmpty(),"Nhập niên khóa");
        AttendanceRules.require(Arrays.asList("Nam","Nữ","Khác").contains(gender),"Chọn giới tính");
        validate(birth,normalizeCourse(course));
    }
    public static void validate(String birth,String course){
        if(birth!=null&&!birth.isEmpty()){
            AttendanceRules.require(birth.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}"),"Ngày sinh phải có dạng dd/MM/yyyy");
            SimpleDateFormat f=new SimpleDateFormat("dd/MM/yyyy",Locale.ROOT);f.setLenient(false);
            ParsePosition p=new ParsePosition(0);Date d=f.parse(birth,p);
            AttendanceRules.require(d!=null&&p.getIndex()==birth.length()&&d.getTime()<=System.currentTimeMillis(),"Ngày sinh không hợp lệ hoặc nằm trong tương lai");
        }
        if(course!=null&&!course.isEmpty()){
            AttendanceRules.require(course.matches("[12][0-9]{3}[-–][12][0-9]{3}"),"Khóa học có dạng 2022–2026");
            int start=Integer.parseInt(course.substring(0,4)),end=Integer.parseInt(course.substring(5));
            AttendanceRules.require(end>start,"Năm kết thúc phải sau năm bắt đầu");
        }
    }
}

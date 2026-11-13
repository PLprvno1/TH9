package com.example.th9.data;

import com.example.th9.model.ClassSchedule;
import java.text.*;
import java.util.*;
import static com.example.th9.data.AttendanceRules.require;

/** Civil dates use UTC solely for arithmetic; attendance timestamps use the device's local zone. */
public final class ScheduleRules {
    private ScheduleRules() {}
    private static SimpleDateFormat formatter(String pattern) {
        SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));f.setLenient(false);return f;
    }
    public static long day(String iso) {
        require(iso!=null&&iso.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),"Ngày không hợp lệ");
        ParsePosition p=new ParsePosition(0);Date d=formatter("yyyy-MM-dd").parse(iso,p);
        require(d!=null&&p.getIndex()==iso.length()&&iso.compareTo("1900-01-01")>=0,"Ngày không hợp lệ (từ năm 1900)");
        return Math.floorDiv(d.getTime(),86400000L);
    }
    public static String iso(long day){return formatter("yyyy-MM-dd").format(new Date(day*86400000L));}
    public static String display(String iso){return formatter("dd/MM/yyyy").format(new Date(day(iso)*86400000L));}
    public static String today(){return AttendanceRepository.format(System.currentTimeMillis(),"yyyy-MM-dd");}
    public static int weekday(long day){return (int)Math.floorMod(day+3,7);}
    public static String daysForDate(String iso){int d=weekday(day(iso));return d==6?"Chủ nhật":"Thứ "+(d+2);}
    public static String time(int minutes){return String.format(Locale.ROOT,"%02d:%02d",minutes/60,minutes%60);}
    public static String shift(ClassSchedule s){return "MORNING".equals(s.shift)?"Sáng":"Chiều";}
    public static String days(ClassSchedule s){StringJoiner j=new StringJoiner(", ");for(int i=0;i<7;i++)if((s.weekdays&(1<<i))!=0)j.add(i==6?"CN":"T"+(i+2));return j.toString();}
    public static boolean occurs(ClassSchedule s,long day){return !s.archived&&day>=day(s.startDate)&&day<=day(s.endDate)&&(s.weekdays&(1<<weekday(day)))!=0;}
    public static boolean inProgress(ClassSchedule s,String date,int minute){return occurs(s,day(date))&&minute>=s.startMinute&&minute<s.endMinute;}
    public static void validate(ClassSchedule s){
        long from=day(s.startDate),until=day(s.endDate);
        require(until>=from,"Ngày kết thúc không được trước ngày bắt đầu");
        require(s.weekdays>0&&(s.weekdays&~127)==0,"Chọn ít nhất một thứ trong tuần");
        require(s.startMinute>=0&&s.startMinute<s.endMinute&&s.endMinute<1440,"Giờ trong khoảng 00:00–23:59; giờ bắt đầu phải nhỏ hơn giờ kết thúc");
        require(("MORNING".equals(s.shift)&&s.endMinute<=720)||("AFTERNOON".equals(s.shift)&&s.startMinute>=720),"Ca Sáng: trước 12:00; ca Chiều: từ 12:00");
        boolean found=false;for(long d=from;d<=Math.min(until,from+6);d++)if(occurs(s,d))found=true;
        require(found,"Không có ngày học thực tế trong khoảng và các thứ đã chọn");
    }
    public static boolean overlaps(ClassSchedule a,ClassSchedule b){
        if(a.archived||b.archived||a.startMinute>=b.endMinute||b.startMinute>=a.endMinute)return false;
        long from=Math.max(day(a.startDate),day(b.startDate)),until=Math.min(day(a.endDate),day(b.endDate));
        int mask=a.weekdays&b.weekdays;
        for(long d=from;d<=Math.min(until,from+6);d++)if((mask&(1<<weekday(d)))!=0)return true;
        return false;
    }
}

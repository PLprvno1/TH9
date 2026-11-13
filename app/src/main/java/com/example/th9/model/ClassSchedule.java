package com.example.th9.model;

import androidx.annotation.NonNull;
import androidx.room.*;
import com.example.th9.data.AttendanceDatabase.Classroom;
import java.io.Serializable;

/** Recurring rule only; occurrences are computed, never pre-created. ISO dates, Monday bit 0. */
@Entity(tableName="ClassSchedule", foreignKeys=@ForeignKey(entity=Classroom.class,
        parentColumns="id",childColumns="classId",onDelete=ForeignKey.CASCADE),indices=@Index("classId"))
public class ClassSchedule implements Serializable {
    @PrimaryKey @NonNull public String id="";
    @NonNull public String classId="", shift="MORNING", startDate="", endDate="";
    public int weekdays, startMinute=420, endMinute=600;
    public boolean archived;

    public ClassSchedule copy() {
        ClassSchedule s=new ClassSchedule();s.id=id;s.classId=classId;s.shift=shift;
        s.startDate=startDate;s.endDate=endDate;s.weekdays=weekdays;
        s.startMinute=startMinute;s.endMinute=endMinute;s.archived=archived;return s;
    }
}

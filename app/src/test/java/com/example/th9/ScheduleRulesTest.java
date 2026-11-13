package com.example.th9;

import com.example.th9.data.ScheduleRules;
import com.example.th9.model.ClassSchedule;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScheduleRulesTest {
    @Test public void suggestionsIncludeStartButExcludeEndAndOtherDates(){
        ClassSchedule s=rule("2026-11-16","2026-11-30",1);s.startMinute=420;s.endMinute=600;
        assertFalse(ScheduleRules.inProgress(s,"2026-11-16",419));assertTrue(ScheduleRules.inProgress(s,"2026-11-16",420));assertTrue(ScheduleRules.inProgress(s,"2026-11-16",599));assertFalse(ScheduleRules.inProgress(s,"2026-11-16",600));
        assertFalse(ScheduleRules.inProgress(s,"2026-11-17",480));assertFalse(ScheduleRules.inProgress(s,"2026-12-07",480));s.archived=true;assertFalse(ScheduleRules.inProgress(s,"2026-11-16",480));
    }
    private ClassSchedule rule(String from,String until,int days){ClassSchedule s=new ClassSchedule();s.startDate=from;s.endDate=until;s.weekdays=days;return s;}
    private void invalid(ClassSchedule s){try{ScheduleRules.validate(s);fail("Must reject invalid schedule");}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}}
    @Test public void repeatsOnMondayWednesdayFridayAcrossYear(){
        ClassSchedule s=rule("2026-11-16","2027-01-15",1|4|16);ScheduleRules.validate(s);
        int count=0;for(long d=ScheduleRules.day(s.startDate);d<=ScheduleRules.day(s.endDate);d++)if(ScheduleRules.occurs(s,d))count++;
        assertEquals(27,count);assertTrue(ScheduleRules.occurs(s,ScheduleRules.day("2027-01-15")));assertFalse(ScheduleRules.occurs(s,ScheduleRules.day("2026-11-17")));
    }
    @Test public void rejectsNoOccurrenceAndInvalidRanges(){
        invalid(rule("2026-11-17","2026-11-17",1));invalid(rule("2026-11-18","2026-11-16",127));invalid(rule("2026-11-16","2026-11-18",0));
        invalid(rule("2026-02-30","2026-03-01",127));invalid(rule("2026-11-16","2026-11-18",128));
    }
    @Test public void validatesShiftAndStrictTimeOrdering(){
        ClassSchedule s=rule("2026-11-16","2026-11-30",127);s.endMinute=s.startMinute;invalid(s);
        s.endMinute=800;invalid(s);s.shift="AFTERNOON";invalid(s);s.startMinute=720;ScheduleRules.validate(s);
        s.endMinute=1440;invalid(s);s.endMinute=800;s.shift="OTHER";invalid(s);
    }
    @Test public void overlapNeedsActualSharedDateAndStrictTimeIntersection(){
        ClassSchedule a=rule("2026-11-16","2026-11-30",1),b=rule("2026-11-17","2026-11-22",1);
        assertFalse(ScheduleRules.overlaps(a,b));b.endDate="2026-11-23";assertTrue(ScheduleRules.overlaps(a,b));
        b.startMinute=a.endMinute;assertFalse(ScheduleRules.overlaps(a,b));b.startMinute=a.endMinute-1;assertTrue(ScheduleRules.overlaps(a,b));
        b.weekdays=2;assertFalse(ScheduleRules.overlaps(a,b));b.weekdays=1;b.archived=true;assertFalse(ScheduleRules.overlaps(a,b));
    }
    @Test public void sundayLeapDayAndCivilDateRoundtrip(){
        assertEquals(6,ScheduleRules.weekday(ScheduleRules.day("2026-11-22")));assertEquals("2028-02-29",ScheduleRules.iso(ScheduleRules.day("2028-02-29")));
        assertEquals("01/01/2027",ScheduleRules.display("2027-01-01"));
    }
}

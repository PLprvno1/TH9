package com.example.th9;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.ui.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class ScheduleUiTest {
    private RoleUiTest f;private AttendanceRepository r;private ClassRoom c;private Student student;
    @Before public void setup()throws Exception{
        f=new RoleUiTest();f.setup();r=f.repo;r.setupAdmin("admin","Admin","TestPass123!");c=new ClassRoom(null,"CNTT01","CNTT01","");r.addClass(c);
        String teacher=r.createTeacher("teacher","Giảng viên demo","TestPass123!");r.setAssignment(teacher,c.getId(),true);
        Student s=new Student(null,"","Sinh viên demo",null,"","",0);s.setBirthDate("01/01/2005");s.setGender("Nam");s.setCourse("2020–2026");student=r.registerStudent(s,c.getId());
    }
    @After public void close()throws Exception{f.close();}
    private void screenshot(String name)throws Exception{
        android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();java.io.File folder=f.context.getExternalFilesDir("verification");folder.mkdirs();
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    @Test public void addOnlyListsUnscheduledClassesAndStaleFormRequiresOverwriteConfirmation()throws Exception{
        ClassSchedule existing=new ClassSchedule();existing.classId=c.getId();existing.startDate=ScheduleRules.today();existing.endDate=ScheduleRules.iso(ScheduleRules.day(existing.startDate)+30);existing.weekdays=127;
        String originalId=r.saveSchedule(existing);ClassRoom b=new ClassRoom(null,"Lớp chưa có lịch","B","");r.addClass(b);r.setAssignment(r.getTeachers().get(0).id,b.getId(),true);
        try(ActivityScenario<ScheduleFormActivity> screen=ActivityScenario.launch(ScheduleFormActivity.class)){
            screen.onActivity(a->{android.widget.Spinner picker=a.findViewById(R.id.schedule_form_class);assertEquals(1,picker.getCount());assertTrue(picker.getSelectedItem().toString().contains("B"));});
            screen.recreate();f.idle();
            ClassSchedule concurrent=existing.copy();concurrent.classId=b.getId();concurrent.startMinute=600;concurrent.endMinute=660;String targetId=r.saveSchedule(concurrent);
            f.tap(R.id.shift_afternoon);onView(withId(R.id.schedule_form_save)).perform(scrollTo(),click());f.idle();
            onView(withText("Lớp đã có lịch học")).check(matches(isDisplayed()));onView(withText("Hủy")).perform(click());assertEquals(2,r.getSchedules().size());assertEquals(600,r.getSchedule(targetId).startMinute);
            onView(withId(R.id.schedule_form_save)).perform(scrollTo(),click());f.idle();onView(withText("Ghi đè / cập nhật")).perform(click());f.idle();assertEquals(2,r.getSchedules().size());assertEquals("AFTERNOON",r.getSchedule(targetId).shift);assertEquals(originalId,r.getScheduleForClass(c.getId()).id);
        }
        try(ActivityScenario<ScheduleFormActivity> screen=ActivityScenario.launch(ScheduleFormActivity.class)){
            screen.onActivity(a->{assertEquals(0,((android.widget.Spinner)a.findViewById(R.id.schedule_form_class)).getCount());assertFalse(a.findViewById(R.id.schedule_form_save).isEnabled());assertTrue(((TextView)a.findViewById(R.id.schedule_form_error)).getText().toString().contains("Tất cả lớp đã có lịch"));});
        }
        r.archiveSchedule(originalId);
        try(ActivityScenario<ScheduleFormActivity> screen=ActivityScenario.launch(ScheduleFormActivity.class)){
            screen.onActivity(a->{android.widget.Spinner picker=a.findViewById(R.id.schedule_form_class);assertEquals(1,picker.getCount());assertTrue(picker.getSelectedItem().toString().contains("CNTT01"));});
        }
    }
    @Test public void adminCreatesEditsArchivesWithPickersAndNavigation()throws Exception{
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){
            screen.onActivity(a->((BottomNavigationView)a.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_timetable));f.activity(TimetableActivity.class);
            f.tap(R.id.schedule_add);f.activity(ScheduleFormActivity.class);f.tap(R.id.schedule_form_from);onView(withId(android.R.id.button1)).perform(click());
            f.tap(R.id.schedule_form_start);onView(withId(android.R.id.button1)).perform(click());
            ScheduleFormActivity form=f.activity(ScheduleFormActivity.class);InstrumentationRegistry.getInstrumentation().runOnMainSync(form::recreate);f.idle();
            f.tap(R.id.schedule_form_save);f.idle();assertEquals(1,r.getSchedules().size());f.activity(TimetableActivity.class);f.tap(R.id.mode_rules);
            TimetableActivity timetable=f.activity(TimetableActivity.class);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                RecyclerView list=timetable.findViewById(R.id.schedule_list);assertEquals(1,list.getAdapter().getItemCount());list.findViewHolderForAdapterPosition(0).itemView.performClick();
            });f.activity(ScheduleDetailActivity.class);f.tap(R.id.schedule_edit);f.activity(ScheduleFormActivity.class);f.tap(R.id.shift_afternoon);f.tap(R.id.schedule_form_save);f.idle();
            assertEquals("AFTERNOON",r.getSchedules().get(0).shift);f.activity(ScheduleDetailActivity.class);screenshot("schedule-admin-detail.png");
            f.tap(R.id.schedule_archive);onView(withText("Lưu trữ")).perform(click());f.idle();assertTrue(r.getSchedules().isEmpty());f.activity(TimetableActivity.class);
            onView(withId(R.id.schedule_empty)).check(matches(isDisplayed()));
        }
    }
    @Test public void teacherWeeklyScheduleToAttendanceAndClosedResult()throws Exception{
        ClassSchedule rule=new ClassSchedule();rule.classId=c.getId();rule.startDate=ScheduleRules.today();rule.endDate=ScheduleRules.iso(ScheduleRules.day(rule.startDate)+14);rule.weekdays=127;
        String scheduleId=r.saveSchedule(rule);r.logout();r.login("teacher","TestPass123!");
        Intent detail=new Intent(f.context,ScheduleDetailActivity.class).putExtra(ScheduleDetailActivity.EXTRA_ID,scheduleId).putExtra(ScheduleDetailActivity.EXTRA_DATE,rule.startDate);
        try(ActivityScenario<TimetableActivity> screen=ActivityScenario.launch(TimetableActivity.class)){
            f.idle();screen.onActivity(a->{assertEquals(View.GONE,a.findViewById(R.id.schedule_add).getVisibility());assertTrue(((RecyclerView)a.findViewById(R.id.schedule_list)).getAdapter().getItemCount()>0);});
            f.tap(R.id.week_next);screen.recreate();f.idle();f.tap(R.id.week_previous);screenshot("schedule-week.png");
        }
        try(ActivityScenario<ScheduleDetailActivity> screen=ActivityScenario.launch(detail)){
            f.tap(R.id.schedule_attend);f.activity(SessionFormActivity.class);
            onView(withId(R.id.switch_session_late)).check(matches(isNotChecked()));
            f.tap(R.id.btn_session_submit);f.idle();f.activity(SessionDetailActivity.class);
            AttendanceSession session=r.getActiveSession();assertNotNull(session);assertEquals(scheduleId,session.getScheduleId());
            assertFalse(session.isEnableLateThreshold());
            r.scan(session.getId(),student.getMssv(),"CODE_128",session.getScheduledAt());
            f.tap(R.id.btn_action_finish_session);onView(withText("Kết thúc ngay")).perform(click());f.idle();f.activity(SessionResultActivity.class);assertEquals(1,r.getPresentCount(session.getId()));
        }
        r.logout();r.login("admin","TestPass123!");ClassSchedule edited=r.getSchedule(scheduleId);edited.startMinute=480;edited.endMinute=660;r.saveSchedule(edited);r.logout();r.login("teacher","TestPass123!");
        try(ActivityScenario<ScheduleDetailActivity> screen=ActivityScenario.launch(detail)){
            screen.onActivity(a->{String info=((TextView)a.findViewById(R.id.schedule_detail_info)).getText().toString();assertFalse(info.contains("Mốc đi muộn mặc định"));assertTrue(info.contains("07:00–10:00"));});
            onView(withId(R.id.schedule_attend)).check(matches(withText("Xem kết quả")));f.tap(R.id.schedule_attend);f.activity(SessionResultActivity.class);assertEquals(1,r.getAllSessions().size());
        }
        try(ActivityScenario<ScheduleFormActivity> denied=ActivityScenario.launch(ScheduleFormActivity.class)){
            long until=System.currentTimeMillis()+3000;while(denied.getState()!=Lifecycle.State.DESTROYED&&System.currentTimeMillis()<until)Thread.sleep(100);assertEquals(Lifecycle.State.DESTROYED,denied.getState());
        }
    }
}

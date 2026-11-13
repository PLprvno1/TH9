package com.example.th9;

import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.ui.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.Calendar;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

/** Uses the existing isolated in-memory UI fixture, never changes device time or user data. */
@RunWith(AndroidJUnit4.class)
public class HomeSuggestionsUiTest {
    private RoleUiTest f;private AttendanceRepository r;private String scheduleId;
    @Before public void setup()throws Exception{
        f=new RoleUiTest();f.setup();r=f.repo;r.setupAdmin("admin","Admin","TestPass123!");
        ClassRoom c=new ClassRoom(null,"Lớp đến giờ học","TODAY","");r.addClass(c);String t=r.createTeacher("teacher","Giảng viên demo","TestPass123!");r.setAssignment(t,c.getId(),true);
        Student s=new Student(null,"","Sinh viên demo",null,"","",0);s.setBirthDate("01/01/2005");s.setCourse("2020–2026");s.setGender("Nam");r.registerStudent(s,c.getId());
        Calendar now=Calendar.getInstance();int minute=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);Assume.assumeTrue(minute<1439);
        ClassSchedule rule=new ClassSchedule();rule.classId=c.getId();rule.startDate=ScheduleRules.today();rule.endDate=rule.startDate;rule.weekdays=127;
        rule.shift=minute<720?"MORNING":"AFTERNOON";rule.startMinute=minute<720?0:720;rule.endMinute=minute<720?720:1439;scheduleId=r.saveSchedule(rule);
    }
    @After public void close()throws Exception{f.close();}
    @Test public void teacherStartsResumesAndStopsSuggestingClosedLesson()throws Exception{
        r.logout();r.login("teacher","TestPass123!");
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){
            onView(withId(R.id.home_suggestions_section)).check(matches(isDisplayed()));onView(withId(R.id.home_suggestion_class)).check(matches(withText("Lớp đến giờ học • TODAY")));
            screen.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.card_no_active_session).getVisibility()));assertTrue(r.getAllSessions().isEmpty());
            android.graphics.Bitmap shot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();java.io.File folder=f.context.getExternalFilesDir("verification");folder.mkdirs();
            try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"home-suggestions.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            onView(withId(R.id.home_suggestion_start)).perform(scrollTo(),click());f.activity(SessionFormActivity.class);
            onView(withId(R.id.switch_session_late)).check(matches(isNotChecked()));
            onView(withId(R.id.et_session_title)).perform(replaceText("Buổi điểm danh đã chỉnh"),closeSoftKeyboard());
            f.tap(R.id.btn_session_submit);f.idle();f.activity(SessionDetailActivity.class);
            AttendanceSession session=r.getActiveSession();assertNotNull(session);assertEquals(scheduleId,session.getScheduleId());assertEquals(ScheduleRules.today(),session.getPlannedDate());
            assertEquals("Buổi điểm danh đã chỉnh",session.getTitle());assertFalse(session.isEnableLateThreshold());
            androidx.test.espresso.Espresso.pressBack();f.activity(MainActivity.class);onView(withId(R.id.home_suggestion_start)).check(matches(withText("Tiếp tục điểm danh")));
            onView(withId(R.id.home_suggestion_start)).perform(scrollTo(),click());f.activity(SessionDetailActivity.class);assertEquals(1,r.getAllSessions().size());
            r.finishSession(session.getId());androidx.test.espresso.Espresso.pressBack();f.activity(MainActivity.class);
            screen.onActivity(a->{assertEquals(View.GONE,a.findViewById(R.id.home_suggestions_section).getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.card_no_active_session).getVisibility());});assertEquals(1,r.getAllSessions().size());
        }
    }
    @Test public void adminHomeNeverSuggestsAttendance(){
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){
            screen.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.home_suggestions_section).getVisibility()));assertTrue(r.getCurrentScheduleSuggestions(System.currentTimeMillis()).isEmpty());assertTrue(r.getAllSessions().isEmpty());
        }
    }
}

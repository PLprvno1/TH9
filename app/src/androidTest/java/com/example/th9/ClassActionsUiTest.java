package com.example.th9;

import android.content.Intent;
import android.view.View;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.ui.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.anything;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class ClassActionsUiTest {
    private RoleUiTest f;private AttendanceRepository r;private ClassRoom target;private String an,binh;
    @Before public void setup()throws Exception{
        f=new RoleUiTest();f.setup();r=f.repo;r.setupAdmin("admin","Admin","TestPass123!");
        r.addClass(new ClassRoom(null,"AAA lớp khác","OTHER",""));target=new ClassRoom(null,"ZZZ lớp đang xem","TARGET","");r.addClass(target);
        an=r.createTeacher("teacher.an","Nguyễn Ánh","TestPass123!");binh=r.createTeacher("teacher.binh","Bình","TestPass123!");r.setAssignment(an,target.getId(),true);
    }
    @After public void close()throws Exception{f.close();}
    private Intent detail(){return new Intent(f.context,ClassDetailActivity.class).putExtra(ClassDetailActivity.EXTRA_CLASS_ID,target.getId());}
    @Test public void classScheduleOpensFormDirectlyWithFixedClassForCreateAndEdit()throws Exception{
        try(ActivityScenario<ClassFormActivity> create=ActivityScenario.launch(ClassFormActivity.class)){
            f.edit(R.id.et_class_name,"Lớp vừa tạo");f.edit(R.id.et_class_code,"NEW");
            onView(withId(R.id.btn_class_submit)).perform(scrollTo(),click());f.idle();
            ClassDetailActivity created=f.activity(ClassDetailActivity.class);
            onView(withId(R.id.tv_detail_class_name)).check(matches(withText("Lớp vừa tạo")));
            assertTrue(r.getSchedules().isEmpty());
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,create.getState());
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(created::finish);
            f.idle();
        }
        try(ActivityScenario<ClassDetailActivity> screen=ActivityScenario.launch(detail())){
            screen.onActivity(a->{assertEquals(View.GONE,a.findViewById(R.id.btn_detail_add_student).getVisibility());assertTrue(a.findViewById(R.id.btn_detail_assign_teachers).getTop()>=a.findViewById(R.id.btn_detail_create_session).getBottom());});
            f.tap(R.id.btn_detail_create_session);ScheduleFormActivity form=f.activity(ScheduleFormActivity.class);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                Spinner picker=form.findViewById(R.id.schedule_form_class);assertEquals(1,picker.getCount());assertFalse(picker.isEnabled());assertTrue(picker.getSelectedItem().toString().contains("TARGET"));
            });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(form::recreate);f.idle();onView(withId(R.id.schedule_form_save)).perform(scrollTo(),click());f.idle();f.activity(ClassDetailActivity.class);
            ClassSchedule schedule=r.getScheduleForClass(target.getId());assertNotNull(schedule);String id=schedule.id;
            f.tap(R.id.btn_detail_create_session);ScheduleFormActivity edit=f.activity(ScheduleFormActivity.class);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{Spinner picker=edit.findViewById(R.id.schedule_form_class);assertFalse(picker.isEnabled());assertTrue(picker.getSelectedItem().toString().contains("TARGET"));});
            f.tap(R.id.shift_afternoon);onView(withId(R.id.schedule_form_save)).perform(scrollTo(),click());f.idle();f.activity(ClassDetailActivity.class);
            assertEquals(id,r.getScheduleForClass(target.getId()).id);assertEquals("AFTERNOON",r.getSchedule(id).shift);assertEquals(1,r.getSchedules().size());
        }
    }
    @Test public void teacherPopupSearchPreservesSelectionRotationAndCancel()throws Exception{
        try(ActivityScenario<ClassDetailActivity> screen=ActivityScenario.launch(detail())){
            onView(withId(R.id.tv_detail_assigned_teachers)).check(matches(withText(org.hamcrest.Matchers.containsString("Nguyễn Ánh"))));
            f.tap(R.id.btn_detail_assign_teachers);f.edit(R.id.class_teachers_search,"binh");onData(anything()).inAdapterView(withId(R.id.class_teachers_list)).atPosition(0).perform(click());
            f.edit(R.id.class_teachers_search,"nguyen");onView(withId(R.id.class_teachers_count)).check(matches(withText(f.context.getString(R.string.class_teachers_count,1,1))));
            screen.recreate();f.idle();onView(withId(R.id.class_teachers_search)).check(matches(withText("nguyen")));onView(withId(R.id.class_teachers_count)).check(matches(withText(f.context.getString(R.string.class_teachers_count,1,1))));
            f.edit(R.id.class_teachers_search,"nobody");onView(withId(R.id.class_teachers_empty)).check(matches(isDisplayed()));f.edit(R.id.class_teachers_search,"");
            android.graphics.Bitmap shot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();java.io.File folder=f.context.getExternalFilesDir("verification");folder.mkdirs();
            try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"class-teachers-popup.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            onView(withText(R.string.class_teachers_save)).perform(click());f.idle();assertEquals(Collections.singletonList(binh),r.assignedTeacherIds(target.getId()));
            onView(withId(R.id.tv_detail_assigned_teachers)).check(matches(withText(org.hamcrest.Matchers.containsString("Bình"))));
            onView(withId(R.id.tv_detail_assigned_teachers)).check(matches(withText(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nguyễn Ánh")))));
            screen.recreate();f.idle();onView(withId(R.id.tv_detail_assigned_teachers)).check(matches(withText(org.hamcrest.Matchers.containsString("Bình"))));
            f.tap(R.id.btn_detail_assign_teachers);f.edit(R.id.class_teachers_search,"nguyen");onData(anything()).inAdapterView(withId(R.id.class_teachers_list)).atPosition(0).perform(click());onView(withText("Hủy")).perform(click());assertEquals(Collections.singletonList(binh),r.assignedTeacherIds(target.getId()));
            onView(withId(R.id.tv_detail_assigned_teachers)).check(matches(withText(org.hamcrest.Matchers.containsString("Bình"))));
            f.tap(R.id.btn_detail_assign_teachers);f.tap(R.id.class_teachers_clear);onView(withText(R.string.class_teachers_save)).perform(click());f.idle();
            onView(withId(R.id.tv_detail_assigned_teachers)).check(matches(withText(R.string.class_teachers_unassigned)));
        }
    }
}

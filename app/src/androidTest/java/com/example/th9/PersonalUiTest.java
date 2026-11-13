package com.example.th9;

import android.content.Intent;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.ui.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

/** Personal navigation and logout use isolated data; installed login and attendance are untouched. */
@RunWith(AndroidJUnit4.class)
public class PersonalUiTest {
    private RoleUiTest f;private AttendanceRepository r;private ClassRoom a;
    private static final String PASS="TestPass123!";
    @Before public void setup()throws Exception{
        f=new RoleUiTest();f.setup();r=f.repo;r.setupAdmin("admin","Quản trị demo",PASS);a=new ClassRoom(null,"Lớp được giao","A","");ClassRoom b=new ClassRoom(null,"Lớp riêng","B","");r.addClass(a);r.addClass(b);
        String teacher=r.createTeacher("teacher","Giảng viên demo",PASS);r.setAssignment(teacher,a.getId(),true);
        Student s=new Student(null,"","Sinh viên demo",null,"","",0);s.setBirthDate("01/01/2005");s.setGender("Nam");s.setCourse("2020–2026");r.registerStudent(s,a.getId());
        for(ClassRoom c:new ClassRoom[]{a,b})r.addSession(new AttendanceSession(null,c.getId(),"Buổi của "+c.getCode(),"12/11/2026","07:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0));
    }
    @After public void close()throws Exception{f.close();}
    private void capture()throws Exception{
        android.graphics.Bitmap shot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();java.io.File folder=f.context.getExternalFilesDir("verification");folder.mkdirs();
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"personal-page.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
    }
    @Test public void bothRolesSeeAccountHistorySettingsAndRestoredNavigation()throws Exception{
        verifyPersonal(true);r.logout();r.login("teacher",PASS);verifyPersonal(false);
    }
    private void verifyPersonal(boolean admin)throws Exception{
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){
            screen.onActivity(activity->{BottomNavigationView nav=activity.findViewById(R.id.bottom_navigation);assertNull(nav.getMenu().findItem(R.id.nav_history));assertNotNull(nav.getMenu().findItem(R.id.nav_personal));nav.setSelectedItemId(R.id.nav_personal);});
            onView(withId(R.id.personal_name)).check(matches(withText(admin?"Quản trị demo":"Giảng viên demo")));onView(withId(R.id.personal_username)).check(matches(withText(admin?"admin":"teacher")));onView(withId(R.id.personal_role)).check(matches(withText(admin?"Quản trị viên":"Giảng viên")));
            screen.recreate();f.idle();screen.onActivity(activity->assertEquals(View.VISIBLE,activity.findViewById(R.id.view_personal).getVisibility()));
            onView(withId(R.id.personal_history)).perform(scrollTo(),click());
            screen.onActivity(activity->{assertEquals(View.VISIBLE,activity.findViewById(R.id.view_history).getVisibility());assertEquals(admin?2:1,((RecyclerView)activity.findViewById(R.id.rv_history_list)).getAdapter().getItemCount());assertEquals(R.id.nav_personal,((BottomNavigationView)activity.findViewById(R.id.bottom_navigation)).getSelectedItemId());});
            screen.recreate();f.idle();screen.onActivity(activity->assertEquals(View.VISIBLE,activity.findViewById(R.id.view_history).getVisibility()));
            openActionBarOverflowOrOptionsMenu(f.context);onView(withText("Lịch sử: Lọc trạng thái")).perform(click());onView(withText("Đã kết thúc")).inRoot(isDialog()).perform(click());
            screen.recreate();f.idle();onView(withId(R.id.ll_history_empty_filter)).check(matches(isDisplayed()));f.tap(R.id.btn_history_reset_filter);
            androidx.test.espresso.Espresso.pressBack();screen.onActivity(activity->assertEquals(View.VISIBLE,activity.findViewById(R.id.view_personal).getVisibility()));
            onView(withId(R.id.personal_settings)).perform(scrollTo(),click());f.activity(SettingsActivity.class);androidx.test.espresso.Espresso.pressBack();f.activity(MainActivity.class);
            onView(withId(R.id.personal_logout)).perform(scrollTo(),click());onView(withText("Hủy")).inRoot(isDialog()).perform(click());assertNotNull(r.currentAccount());if(!admin)capture();
        }
        Intent legacy=new Intent(f.context,MainActivity.class).putExtra(MainActivity.EXTRA_INITIAL_TAB,R.id.nav_history);
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(legacy)){
            screen.onActivity(activity->{assertEquals(View.VISIBLE,activity.findViewById(R.id.view_history).getVisibility());assertEquals(R.id.nav_personal,((BottomNavigationView)activity.findViewById(R.id.bottom_navigation)).getSelectedItemId());});
            androidx.test.espresso.Espresso.pressBack();screen.onActivity(activity->assertEquals(View.VISIBLE,activity.findViewById(R.id.view_personal).getVisibility()));
        }
    }
    @Test public void logoutClearsLoginNavigationButPreservesOpenAttendance()throws Exception{
        r.logout();r.login("teacher",PASS);AttendanceSession session=new AttendanceSession(null,a.getId(),"Buổi đang mở","12/11/2026","07:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0);r.addSession(session);r.openSession(session.getId());
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){
            screen.onActivity(activity->((BottomNavigationView)activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_personal));
            onView(withId(R.id.personal_logout)).perform(scrollTo(),click());onView(withText("Đăng xuất")).inRoot(isDialog()).perform(click());f.idle();f.activity(LoginActivity.class);
            assertNull(r.currentAccount());assertEquals(Lifecycle.State.DESTROYED,screen.getState());assertEquals("OPEN",f.db.store().session(session.getId()).status);assertEquals(1,f.db.store().records().size());
        }
        r.login("teacher",PASS);assertEquals(session.getId(),r.getActiveSession().getId());
    }
}

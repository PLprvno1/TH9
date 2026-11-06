package com.example.th9;

import android.content.Intent;
import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.function.BooleanSupplier;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Real Activity/XML navigation. Creates clearly named verification data, never clears user data. */
@RunWith(AndroidJUnit4.class)
public class UiFlowTest {
    private AttendanceRepository repo;
    private String tag;
    private void waitFor(BooleanSupplier condition) throws Exception {
        long deadline=System.currentTimeMillis()+12000;
        while(!condition.getAsBoolean()&&System.currentTimeMillis()<deadline)Thread.sleep(50);
        assertTrue("Timed out waiting for persisted operation",condition.getAsBoolean());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void addStudent(String mssv,String name) throws Exception {
        onView(withId(R.id.btn_detail_add_student)).perform(click());
        onView(withId(R.id.et_student_name)).perform(replaceText(name));
        onView(withId(R.id.et_student_mssv)).perform(replaceText(mssv),closeSoftKeyboard());
        onView(withId(R.id.btn_student_submit)).perform(scrollTo(),click());
        waitFor(()->repo.findByMssv(mssv)!=null);
    }
    @Test public void createClassStudentsSessionManualCloseResultsHistoryStatsSettings() throws Exception {
        tag=Long.toString(System.currentTimeMillis());
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(ActivityScenario<MainActivity> main=ActivityScenario.launch(MainActivity.class)){
            repo=AttendanceRepository.getInstance();waitFor(()->repo.isInitialized());
            onView(withId(R.id.nav_classes)).perform(click());onView(withId(R.id.btn_classes_add)).perform(click());
            onView(withId(R.id.et_class_name)).perform(replaceText("Kiem thu UI "+tag));
            onView(withId(R.id.et_class_code)).perform(replaceText("UI"),closeSoftKeyboard());
            onView(withId(R.id.btn_class_submit)).perform(scrollTo(),click());
            waitFor(()->repo.getAllClasses().stream().anyMatch(c->c.getName().equals("Kiem thu UI "+tag)));
            addStudent("0"+tag+"1","An UI");addStudent("0"+tag+"2","Binh UI");addStudent("0"+tag+"3","Chi UI");
            onView(withId(R.id.tv_detail_student_count)).check(matches(withText(containsString("3"))));
            onView(withId(R.id.btn_detail_create_session)).perform(click());
            onView(withId(R.id.et_session_title)).perform(replaceText("Buoi UI "+tag),closeSoftKeyboard());
            onView(withId(R.id.switch_session_late)).perform(scrollTo(),click());
            onView(withId(R.id.btn_session_submit)).perform(scrollTo(),click());
            waitFor(()->repo.getAllSessions().stream().anyMatch(s->s.getTitle().equals("Buoi UI "+tag)));
            AttendanceSession session=repo.getAllSessions().stream().filter(s->s.getTitle().equals("Buoi UI "+tag)).findFirst().get();
            onView(withId(R.id.btn_action_open_session)).perform(click());
            onView(withText("Mở buổi")).inRoot(isDialog()).perform(click());waitFor(()->repo.getSessionById(session.getId()).getStatus()==AttendanceSession.SessionStatus.OPEN);
            onView(withId(R.id.btn_action_manual)).perform(click());
            onView(allOf(withText("An UI"),isDescendantOfA(withId(R.id.rv_manual_candidates)))).perform(click());
            onView(withId(R.id.btn_manual_confirm)).perform(click());waitFor(()->repo.getCheckedInTotal(session.getId())==1);
            // Decoded-value path; physical camera is checked separately.
            repo.scan(session.getId(),"0"+tag+"2",System.currentTimeMillis());
            onView(withId(R.id.btn_action_finish_session)).perform(click());
            onView(withText("Kết thúc ngay")).inRoot(isDialog()).perform(click());waitFor(()->repo.getSessionById(session.getId()).getStatus()==AttendanceSession.SessionStatus.CLOSED);
            onView(withId(R.id.tv_result_finish_info)).check(matches(withText(containsString("3 SV"))));
            assertEquals(2,repo.getCheckedInTotal(session.getId()));assertEquals(1,repo.getAbsentCount(session.getId()));
            onView(withId(R.id.et_result_search)).perform(replaceText("Chi"),closeSoftKeyboard());
            onView(withId(R.id.chip_filter_absent)).perform(scrollTo(),click());onView(withText("Chi UI")).check(matches(isDisplayed()));
            onView(withId(R.id.action_export)).perform(click());onView(withId(R.id.tv_export_preview_content)).check(matches(withText(containsString("Chi UI"))));pressBack();
            Thread.sleep(700); // Wait for Android's predictive-back dialog dismissal animation.
            // Context menu is exercised on a closed result, with a required reason.
            onView(withText("Chi UI")).perform(longClick());
            onView(withText(R.string.context_change_status)).perform(click());
            onView(withText("Đi muộn")).inRoot(isDialog()).perform(click());
            onView(withHint("Lý do chỉnh kết quả (bắt buộc)")).perform(replaceText("Kiem thu ly do"),closeSoftKeyboard());
            onView(withText("Lưu trạng thái")).inRoot(isDialog()).perform(click());
            waitFor(()->repo.getLateCount(session.getId())==1);
            assertNull(repo.getRecordForStudent(session.getId(),repo.findByMssv("0"+tag+"3").getId()).getCheckedAt());
            // Return to the acceptance result after testing corrections.
            AttendanceRecord corrected=repo.getRecordForStudent(session.getId(),repo.findByMssv("0"+tag+"3").getId());
            repo.updateRecordStatus(corrected.getId(),AttendanceRecord.AttendanceStatus.ABSENT,"Khoi phuc ket qua kiem thu");
        }
        android.os.Bundle filters=new android.os.Bundle();
        ClassRoom classroom=repo.getAllClasses().stream().filter(c->c.getName().equals("Kiem thu UI "+tag)).findFirst().get();
        filters.putString("filter_class",classroom.getId());
        try(ActivityScenario<com.example.th9.ui.AttendanceStatsActivity> stats=ActivityScenario.launch(new Intent(context,com.example.th9.ui.AttendanceStatsActivity.class).putExtra("filters",filters))){
            onView(withId(R.id.tv_stats_avg_rate)).check(matches(withText(containsString("66"))));
            onView(withId(R.id.tv_stats_total_sessions)).check(matches(withText("1 buổi")));stats.recreate();
            onView(withId(R.id.tv_stats_total_sessions)).check(matches(withText("1 buổi")));
        }
        try(ActivityScenario<MainActivity> history=ActivityScenario.launch(new Intent(context,MainActivity.class).putExtra(MainActivity.EXTRA_INITIAL_TAB,R.id.nav_history))){
            onView(withText("Buoi UI "+tag)).check(matches(isDisplayed()));
        }
        android.content.SharedPreferences prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE);
        boolean previousSound=prefs.getBoolean("sound",true);
        int previousTheme=prefs.getInt("theme",androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        try(ActivityScenario<com.example.th9.ui.SettingsActivity> settings=ActivityScenario.launch(com.example.th9.ui.SettingsActivity.class)){
            onView(withId(R.id.switch_sound)).perform(click());
            assertEquals(!previousSound,prefs.getBoolean("sound",true));settings.recreate();
            onView(withId(R.id.switch_sound)).check(matches(previousSound?isNotChecked():isChecked()));
            onView(withId(R.id.rb_theme_light)).perform(click());
            assertEquals(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO,prefs.getInt("theme",0));
        }finally{
            prefs.edit().putBoolean("sound",previousSound).putInt("theme",previousTheme).commit();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(previousTheme));
        }
        // Explicit opt-in for the host-side force-stop/relaunch verification; normal suite closes all sessions.
        if("true".equals(InstrumentationRegistry.getArguments().getString("leaveOpen"))){
            AttendanceSession recovery=new AttendanceSession(null,classroom.getId(),"Phuc hoi sau khoi dong lai","06/11/2026","07:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0);
            repo.addSession(recovery);repo.openSession(recovery.getId());
            repo.manual(recovery.getId(),repo.findByMssv("0"+tag+"1").getId(),System.currentTimeMillis());
        }
    }
}

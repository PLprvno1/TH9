package com.example.th9;
import android.app.Activity;
import android.content.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.ui.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
/** UI runs against an injected in-memory repository; production DB and login remain untouched. */
@RunWith(AndroidJUnit4.class)
public class RoleUiTest {
    Context context;AttendanceDatabase db;AttendanceRepository repo,original;Field singleton;
    @Before public void setup()throws Exception{context=InstrumentationRegistry.getInstrumentation().getTargetContext();db=Room.inMemoryDatabaseBuilder(context,AttendanceDatabase.class).build();repo=new AttendanceRepository(db);repo.initialize();singleton=AttendanceRepository.class.getDeclaredField("instance");singleton.setAccessible(true);original=(AttendanceRepository)singleton.get(null);singleton.set(null,repo);}
    @After public void close()throws Exception{Tasks.IO.submit(()->{}).get(45,TimeUnit.SECONDS);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Stage stage:new Stage[]{Stage.RESUMED,Stage.STARTED,Stage.CREATED,Stage.PAUSED,Stage.STOPPED})for(Activity a:new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)))a.finish();});InstrumentationRegistry.getInstrumentation().waitForIdleSync();singleton.set(null,original);db.close();}
    void idle()throws Exception{Tasks.IO.submit(()->{}).get(45,TimeUnit.SECONDS);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    <T extends Activity>T activity(Class<T> type)throws Exception{AtomicReference<T> found=new AtomicReference<>();long until=System.currentTimeMillis()+15000;do{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(type.isInstance(a))found.set(type.cast(a));});if(found.get()!=null){Thread.sleep(450);return found.get();}Thread.sleep(100);}while(System.currentTimeMillis()<until);throw new AssertionError("No resumed "+type.getSimpleName());}
    void visible(int id){try{onView(withId(id)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));}catch(AssertionError e){onView(withId(id)).perform(scrollTo());}}
    void edit(int id,String text){visible(id);onView(withId(id)).perform(replaceText(text),closeSoftKeyboard());}
    void tap(int id){visible(id);onView(withId(id)).perform(click());}
    void launch(Class<? extends Activity> type){context.startActivity(new Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @Test public void setupAccountsAssignmentTeacherLoginAndReadOnlyProfile()throws Exception{
        try(ActivityScenario<LoginActivity> initial=ActivityScenario.launch(LoginActivity.class)){
            edit(R.id.login_name,"Demo Admin");edit(R.id.login_username,"admin");edit(R.id.login_password,"TestPass123!");edit(R.id.login_confirm,"TestPass123!");tap(R.id.login_submit);initial.onActivity(Activity::recreate);idle();MainActivity main=activity(MainActivity.class);assertTrue(repo.isAdmin());
            ClassRoom a=new ClassRoom(null,"Assigned class","A", ""),b=new ClassRoom(null,"Private class","B", "");repo.addClass(a);repo.addClass(b);Student s=new Student(null,"","Sinh viên demo",null,"","",0);s.setBirthDate("01/01/2005");s.setCourse("2020–2026");s.setGender("Nữ");Student saved=repo.registerStudent(s,a.getId());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->((BottomNavigationView)main.findViewById(R.id.bottom_navigation)).setSelectedItemId(R.id.nav_classes));
            tap(R.id.card_manage_teachers);activity(TeacherAccountsActivity.class);tap(R.id.accounts_add);edit(R.id.account_username,"teacher");edit(R.id.account_name,"Demo Teacher");edit(R.id.account_password,"TestPass123!");onView(withText("Lưu")).perform(click());idle();assertEquals(1,repo.getTeachers().size());
            onView(withText("Phân công lớp")).perform(click());onView(withText("Assigned class • A")).perform(click());onView(withText("Lưu phân công")).perform(click());idle();assertEquals(Collections.singletonList(a.getId()),repo.assignedClassIds(repo.getTeachers().get(0).id));
            repo.logout();launch(LoginActivity.class);activity(LoginActivity.class);edit(R.id.login_username,"teacher");edit(R.id.login_password,"incorrect");tap(R.id.login_submit);idle();assertNull(repo.currentAccount());edit(R.id.login_password,"TestPass123!");tap(R.id.login_submit);idle();MainActivity teacher=activity(MainActivity.class);assertFalse(repo.isAdmin());assertEquals(1,repo.getAllClasses().size());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{BottomNavigationView nav=teacher.findViewById(R.id.bottom_navigation);assertEquals("Lớp được giao",nav.getMenu().findItem(R.id.nav_classes).getTitle());nav.setSelectedItemId(R.id.nav_classes);assertEquals(View.GONE,teacher.findViewById(R.id.btn_classes_add).getVisibility());});
            Intent profile=new Intent(context,StudentProfileActivity.class).putExtra(StudentProfileActivity.EXTRA_STUDENT_ID,saved.getId()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(profile);StudentProfileActivity p=activity(StudentProfileActivity.class);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertEquals(View.GONE,p.findViewById(R.id.btn_profile_edit).getVisibility());assertEquals(View.GONE,p.findViewById(R.id.btn_profile_add_class).getVisibility());assertEquals(View.VISIBLE,p.findViewById(R.id.btn_profile_classes).getVisibility());assertNotNull(p.findViewById(R.id.profile_student_card));});
            try(ActivityScenario<StudentFormActivity> denied=ActivityScenario.launch(StudentFormActivity.class)){long until=System.currentTimeMillis()+3000;while(denied.getState()!=androidx.lifecycle.Lifecycle.State.DESTROYED&&System.currentTimeMillis()<until)Thread.sleep(100);assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,denied.getState());}
        }
    }
    @Test public void teacherSessionOpenScanCallbackManualFinishAndCamera()throws Exception{
        repo.setupAdmin("admin","Admin","TestPass123!");ClassRoom c=new ClassRoom(null,"A","A","");repo.addClass(c);Student first=new Student(null,"","One",null,"","",0);first.setBirthDate("01/01/2005");first.setCourse("2020–2026");first.setGender("Nam");first=repo.registerStudent(first,c.getId());Student second=new Student(null,"","Two",null,"","",0);second.setBirthDate("01/01/2005");second.setCourse("2020–2026");second.setGender("Nữ");second=repo.registerStudent(second,c.getId());String t=repo.createTeacher("teacher","Teacher","TestPass123!");repo.setAssignment(t,c.getId(),true);repo.logout();repo.login("teacher","TestPass123!");
        Intent form=new Intent(context,SessionFormActivity.class).putExtra(SessionFormActivity.EXTRA_PRESELECTED_CLASS_ID,c.getId());try(ActivityScenario<SessionFormActivity> scenario=ActivityScenario.launch(form)){tap(R.id.btn_session_submit);onView(withText("Tạo ngoài lịch")).perform(click());idle();activity(SessionDetailActivity.class);tap(R.id.btn_action_open_session);onView(withText("Mở buổi")).perform(click());idle();}
        AttendanceSession session=repo.getActiveSession();assertNotNull(session);assertEquals(t,session.getTeacherId());
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.CAMERA);
        Intent scan=new Intent(context,ScanActivity.class).putExtra(ScanActivity.EXTRA_MODE,ScanActivity.MODE_ATTENDANCE).putExtra(ScanActivity.EXTRA_SESSION_ID,session.getId());String code=first.getMssv();
        try(ActivityScenario<ScanActivity> scenario=ActivityScenario.launch(scan)){cameraStreams(scenario);scenario.recreate();cameraStreams(scenario);Method handle=ScanActivity.class.getDeclaredMethod("handleCode",String.class,String.class);handle.setAccessible(true);scenario.onActivity(a->{try{handle.invoke(a,code,"CODE_128");}catch(Exception e){throw new AssertionError(e);}});idle();assertEquals(1,repo.getCheckedInTotal(session.getId()));}
        repo.manual(session.getId(),second.getId(),System.currentTimeMillis());Intent detail=new Intent(context,SessionDetailActivity.class).putExtra(SessionDetailActivity.EXTRA_SESSION_ID,session.getId());try(ActivityScenario<SessionDetailActivity> scenario=ActivityScenario.launch(detail)){tap(R.id.btn_action_finish_session);onView(withText("Kết thúc ngay")).perform(click());idle();assertEquals(AttendanceSession.SessionStatus.CLOSED,repo.getSessionById(session.getId()).getStatus());assertEquals(2,repo.getCheckedInTotal(session.getId()));assertTrue(CsvTransfer.csv(repo,session.getId()).contains("Teacher"));}
    }
    @Test public void adminRegistersFromClassReceiptSurvivesRotation()throws Exception{
        repo.setupAdmin("admin","Admin","TestPass123!");ClassRoom c=new ClassRoom(null,"A","A","");repo.addClass(c);
        Intent form=new Intent(context,StudentFormActivity.class).putExtra(StudentFormActivity.EXTRA_CLASS_ID,c.getId());
        try(ActivityScenario<StudentFormActivity> scenario=ActivityScenario.launch(form)){
            edit(R.id.et_student_name,"New student");tap(R.id.et_student_birth);onView(withId(android.R.id.button1)).perform(click());edit(R.id.et_student_course,"2020–2026");tap(R.id.sp_student_gender);androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.is("Nam")).perform(click());tap(R.id.btn_student_submit);idle();
            onView(withId(R.id.created_student_number)).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("202020260001")));onView(withId(R.id.created_student_card)).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));assertEquals(1,repo.getStudentsForClass(c.getId()).size());scenario.recreate();
            onView(withId(R.id.created_student_number)).check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("202020260001")));assertEquals(1,repo.getAllStudents().size());onView(withText("Xem hồ sơ")).perform(click());StudentProfileActivity profile=activity(StudentProfileActivity.class);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertEquals(View.VISIBLE,profile.findViewById(R.id.btn_profile_classes).getVisibility());assertEquals(View.VISIBLE,profile.findViewById(R.id.btn_profile_add_class).getVisibility());assertNotNull(profile.findViewById(R.id.profile_student_card));});
        }
    }
    void cameraStreams(ActivityScenario<ScanActivity> scenario)throws Exception{java.util.concurrent.atomic.AtomicBoolean ok=new java.util.concurrent.atomic.AtomicBoolean();long until=System.currentTimeMillis()+20000;do{scenario.onActivity(a->{androidx.camera.view.PreviewView p=a.findViewById(R.id.camera_preview);ok.set(p!=null&&p.getPreviewStreamState().getValue()==androidx.camera.view.PreviewView.StreamState.STREAMING);});if(!ok.get())Thread.sleep(100);}while(!ok.get()&&System.currentTimeMillis()<until);assertTrue("CameraX preview streams",ok.get());}
}

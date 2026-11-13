package com.example.th9;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.ui.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.function.Predicate;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.hamcrest.Matchers.*;
/** Additive UI verification. Never closes sessions, clears storage or removes the installed app. */
@RunWith(AndroidJUnit4.class)
public class StudentCardUiTest {
    private final UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,Predicate<AccessibilityNodeInfo> match){if(node==null)return null;if(match.test(node))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo result=find(node.getChild(i),match);if(result!=null)return result;}return null;}
    private AccessibilityNodeInfo waitNode(Predicate<AccessibilityNodeInfo> match)throws Exception{long until=System.currentTimeMillis()+12000;do{AccessibilityNodeInfo result=find(automation.getRootInActiveWindow(),match);if(result!=null)return result;Thread.sleep(100);}while(System.currentTimeMillis()<until);throw new AssertionError("System UI control not found: "+describe(automation.getRootInActiveWindow()));}
    private String describe(AccessibilityNodeInfo n){if(n==null)return "";String s="["+n.getViewIdResourceName()+" | "+n.getText()+" | "+n.getContentDescription()+"]";for(int i=0;i<n.getChildCount();i++)s+=describe(n.getChild(i));return s;}
    private boolean text(AccessibilityNodeInfo n,String s){return n.getText()!=null&&s.contentEquals(n.getText());}
    private void clickNode(AccessibilityNodeInfo node){AccessibilityNodeInfo original=node;while(node!=null&&!node.isClickable())node=node.getParent();
        if(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;
        android.graphics.Rect bounds=new android.graphics.Rect();original.getBoundsInScreen(bounds);long time=SystemClock.uptimeMillis();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=android.view.MotionEvent.obtain(time,time+60,android.view.MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
        assertTrue(automation.injectInputEvent(down,true));assertTrue(automation.injectInputEvent(up,true));down.recycle();up.recycle();}
    private void downloads()throws Exception{
        if(find(automation.getRootInActiveWindow(),n->text(n,"Downloads")||text(n,"Tải xuống")||text(n,"Tệp đã tải xuống"))==null){
            clickNode(waitNode(n->{String d=String.valueOf(n.getContentDescription());return d.equals("Show roots")||d.equals("Hiển thị gốc")||d.equals("Mở ngăn điều hướng");}));
            clickNode(waitNode(n->text(n,"Downloads")||text(n,"Tải xuống")||text(n,"Tệp đã tải xuống")));Thread.sleep(300);
        }
        AccessibilityNodeInfo list=find(automation.getRootInActiveWindow(),n->String.valueOf(n.getViewIdResourceName()).endsWith(":id/sub_menu_list"));if(list!=null){clickNode(list);Thread.sleep(300);}
    }
    private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=automation.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private void rotateCurrent(int requested,int expected)throws Exception{
        java.util.concurrent.atomic.AtomicReference<android.app.Activity> previous=new java.util.concurrent.atomic.AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(android.app.Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)){previous.set(a);a.setRequestedOrientation(requested);break;}});
        java.util.concurrent.atomic.AtomicBoolean rotated=new java.util.concurrent.atomic.AtomicBoolean();long until=System.currentTimeMillis()+12000;
        do{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(android.app.Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))if(a!=previous.get()&&a.getResources().getConfiguration().orientation==expected)rotated.set(true);});if(!rotated.get())Thread.sleep(100);}while(!rotated.get()&&System.currentTimeMillis()<until);assertTrue("Actual orientation change and recreation required",rotated.get());Thread.sleep(600);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private final java.util.Map<String,String> animations=new java.util.LinkedHashMap<>();
    @org.junit.Before public void disableSystemAnimations()throws Exception{
        for(String key:new String[]{"window_animation_scale","transition_animation_scale","animator_duration_scale"}){animations.put(key,shell("settings get global "+key).trim());shell("settings put global "+key+" 0");}
    }
    @org.junit.After public void restoreSystemAnimations()throws Exception{
        for(java.util.Map.Entry<String,String> entry:animations.entrySet())if("null".equals(entry.getValue()))shell("settings delete global "+entry.getKey());else shell("settings put global "+entry.getKey()+" "+entry.getValue());
    }
    @Test public void createPhotoProfileSaveShareRotateAndVerifyWithoutAttendance()throws Exception{
        AccessibilityServiceInfo service=automation.getServiceInfo();service.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.setServiceInfo(service);
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();AttendanceRepository repo=AttendanceRepository.getInstance();repo.initialize();
        String tag=Long.toString(System.currentTimeMillis());int recordsBefore=0;for(AttendanceSession s:repo.getAllSessions())recordsBefore+=repo.getRecordsForSession(s.getId()).size();
        File source=new File(context.getExternalFilesDir(null),"photo-"+tag+".png");Bitmap fixture=Bitmap.createBitmap(450,600,Bitmap.Config.ARGB_8888);fixture.eraseColor(Color.rgb(60,130,180));try(OutputStream out=new FileOutputStream(source)){fixture.compress(Bitmap.CompressFormat.PNG,100,out);}fixture.recycle();
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29);
        ContentValues values=new ContentValues();values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,source.getName());values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/");
        android.net.Uri fixtureUri=context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);assertNotNull(fixtureUri);
        try(InputStream in=new FileInputStream(source);OutputStream out=context.getContentResolver().openOutputStream(fixtureUri)){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
        try(ActivityScenario<StudentsActivity> list=ActivityScenario.launch(StudentsActivity.class)){
            onView(withId(R.id.students_add)).perform(click());
            onView(withId(R.id.et_student_name)).perform(replaceText("Nguyễn Ánh UI "+tag),closeSoftKeyboard());
            onView(withId(R.id.btn_student_photo)).perform(scrollTo(),click());waitNode(n->String.valueOf(n.getPackageName()).endsWith(".documentsui"));downloads();clickNode(waitNode(n->text(n,source.getName())||String.valueOf(n.getContentDescription()).startsWith(source.getName()+",")));Thread.sleep(400);
            onView(withId(R.id.et_student_birth)).perform(scrollTo(),click());onView(withId(android.R.id.button1)).perform(click());
            onView(withId(R.id.et_student_course)).perform(scrollTo(),replaceText("2022–2028"),closeSoftKeyboard());rotateCurrent(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,android.content.res.Configuration.ORIENTATION_LANDSCAPE);
            onView(withId(R.id.et_student_name)).check(matches(withText("Nguyễn Ánh UI "+tag)));onView(withId(R.id.et_student_course)).check(matches(withText("2022–2028")));
            rotateCurrent(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,android.content.res.Configuration.ORIENTATION_PORTRAIT);onView(withId(R.id.et_student_course)).check(matches(withText("2022–2028")));onView(withId(R.id.sp_student_gender)).perform(scrollTo(),click());onData(is("Nữ")).perform(click());onView(withId(R.id.btn_student_submit)).perform(scrollTo(),click());
            long until=System.currentTimeMillis()+12000;Student[] found=new Student[1];
            while(found[0]==null&&System.currentTimeMillis()<until){for(Student candidate:repo.getAllStudents())if(candidate.getFullName().equals("Nguyễn Ánh UI "+tag))found[0]=candidate;if(found[0]==null)Thread.sleep(100);}
            Student student=found[0];String mssv=student==null?"":student.getMssv();assertNotNull("Student must be persisted after rotated form submission",student);assertNotNull("Photo must survive both form rotations",student.getPhotoPath());assertTrue(new File(student.getPhotoPath()).isFile());assertNotNull("Birth date must survive both form rotations",student.getBirthDate());assertEquals("2022–2028",student.getCourse());assertEquals(mssv,student.getDigitalCardCode());assertTrue(mssv.matches("20222028[0-9]{4}"));assertEquals("Nữ",student.getGender());
            onView(withId(R.id.created_student_number)).check(matches(withText(mssv)));onView(withText("Đóng")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            onView(withId(R.id.students_search)).perform(replaceText(mssv),closeSoftKeyboard());onView(withText("Nguyễn Ánh UI "+tag)).perform(click());onView(withId(R.id.btn_profile_card_export)).perform(scrollTo(),click());
            onView(withId(R.id.student_card)).check(matches(isDisplayed()));
            onView(withId(R.id.card_save)).perform(scrollTo(),click());waitNode(n->String.valueOf(n.getPackageName()).endsWith(".documentsui"));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(android.app.Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.STOPPED))if(a instanceof StudentCardActivity){a.recreate();break;}});
            downloads();clickNode(waitNode(n->String.valueOf(n.getViewIdResourceName()).endsWith(":id/button1")||text(n,"Save")||text(n,"Lưu")));
            String saved="/sdcard/Download/the-sinh-vien-"+student.getId()+".png";until=System.currentTimeMillis()+10000;while(!shell("stat -c %s "+saved).trim().matches("[1-9][0-9]*")&&System.currentTimeMillis()<until)Thread.sleep(100);
            assertTrue(shell("stat -c %s "+saved).trim().matches("[1-9][0-9]*"));waitNode(n->String.valueOf(n.getViewIdResourceName()).endsWith(":id/card_share"));
            onView(withId(R.id.card_share)).perform(scrollTo(),click());waitNode(n->{String p=String.valueOf(n.getPackageName());return p.endsWith(".intentresolver")||p.equals("android");});automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);Thread.sleep(900);InstrumentationRegistry.getInstrumentation().waitForIdleSync();waitNode(n->String.valueOf(n.getViewIdResourceName()).endsWith(":id/card_verify"));
            onView(withId(R.id.card_verify)).perform(scrollTo(),click());waitNode(n->String.valueOf(n.getViewIdResourceName()).endsWith(":id/tv_scan_header_title"));onView(withId(R.id.tv_scan_header_title)).check(matches(withText("Quét kiểm tra thẻ")));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                for(android.app.Activity activity:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))if(activity instanceof ScanActivity){
                    try{java.lang.reflect.Method callback=ScanActivity.class.getDeclaredMethod("handleCode",String.class,String.class);callback.setAccessible(true);callback.invoke(activity,student.getDigitalCardCode(),"CODE_128");}catch(Exception e){throw new AssertionError(e);}
                }
            });waitNode(n->text(n,"Thẻ trùng khớp"));onView(withId(R.id.tv_result_student_info)).check(matches(withText(containsString(mssv))));pressBack();
            onView(withId(R.id.card_edit)).perform(scrollTo(),click());onView(withId(R.id.et_student_name)).perform(replaceText("Tên UI đã sửa"),closeSoftKeyboard());onView(withId(R.id.btn_student_submit)).perform(scrollTo(),click());Thread.sleep(400);assertEquals(student.getDigitalCardCode(),repo.getStudentById(student.getId()).getDigitalCardCode());
            File directory=context.getExternalFilesDir("verification");directory.mkdirs();Bitmap screenshot=automation.takeScreenshot();try(OutputStream out=new FileOutputStream(new File(directory,"student-card-ui.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
            shell("rm -f "+saved);
            Intent cardIntent=new Intent(context,StudentCardActivity.class).putExtra(StudentProfileActivity.EXTRA_STUDENT_ID,student.getId());
            try(ActivityScenario<StudentCardActivity> card=ActivityScenario.launch(cardIntent)){card.recreate();onView(withId(R.id.student_card)).check(matches(isDisplayed()));
                rotateCurrent(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,android.content.res.Configuration.ORIENTATION_LANDSCAPE);onView(withId(R.id.student_card)).perform(scrollTo()).check(matches(isDisplayed()));
                rotateCurrent(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,android.content.res.Configuration.ORIENTATION_PORTRAIT);onView(withId(R.id.student_card)).check(matches(isDisplayed()));}
            assertEquals("Tên UI đã sửa",repo.getStudentById(student.getId()).getFullName());int recordsAfter=0;for(AttendanceSession s:repo.getAllSessions())recordsAfter+=repo.getRecordsForSession(s.getId()).size();assertEquals(recordsBefore,recordsAfter);
        }finally{source.delete();context.getContentResolver().delete(fixtureUri,null,null);}
    }
}

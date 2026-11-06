package com.example.th9;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.*;
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
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class DocumentsUiTest {
    private final UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,Predicate<AccessibilityNodeInfo> match){
        if(node==null)return null;if(match.test(node))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=find(node.getChild(i),match);if(found!=null)return found;}return null;
    }
    private AccessibilityNodeInfo waitNode(Predicate<AccessibilityNodeInfo> predicate) throws Exception {
        long until=System.currentTimeMillis()+12000;
        do{AccessibilityNodeInfo found=find(automation.getRootInActiveWindow(),predicate);if(found!=null)return found;Thread.sleep(100);}while(System.currentTimeMillis()<until);
        logTree(automation.getRootInActiveWindow());
        throw new AssertionError("System document control not found");
    }
    private void logTree(AccessibilityNodeInfo node){if(node==null)return;android.util.Log.i("TH9Documents",String.valueOf(node.getPackageName())+" | "+node.getViewIdResourceName()+" | "+node.getText()+" | "+node.getContentDescription());for(int i=0;i<node.getChildCount();i++)logTree(node.getChild(i));}
    private boolean text(AccessibilityNodeInfo n,String value){return n.getText()!=null&&value.contentEquals(n.getText());}
    private void clickSystem(AccessibilityNodeInfo node){
        while(node!=null&&!node.isClickable())node=node.getParent();
        assertNotNull("Clickable system control",node);assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }
    private void downloads() throws Exception {
        AccessibilityNodeInfo root=automation.getRootInActiveWindow();
        if(find(root,n->text(n,"Tải xuống")||text(n,"Tệp đã tải xuống")||text(n,"Downloads"))!=null)return;
        clickSystem(waitNode(n->{String d=String.valueOf(n.getContentDescription());return d.equals("Show roots")||d.equals("Hiển thị gốc")||d.equals("Mở ngăn điều hướng");}));
        clickSystem(waitNode(n->text(n,"Tải xuống")||text(n,"Tệp đã tải xuống")||text(n,"Downloads")));
        Thread.sleep(300);
    }
    private String shell(String command) throws Exception {
        try(ParcelFileDescriptor descriptor=automation.executeShellCommand(command);
            InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
            ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int read;
            while((read=input.read(buffer))!=-1)output.write(buffer,0,read);return output.toString("UTF-8");
        }
    }
    @Test public void saveShareAndImportUsingAndroidDocuments() throws Exception {
        AccessibilityServiceInfo service=automation.getServiceInfo();service.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.setServiceInfo(service);
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();AttendanceRepository repo=AttendanceRepository.getInstance();repo.initialize();
        String tag=Long.toString(System.currentTimeMillis());
        ClassRoom classroom=new ClassRoom(null,"Kiem thu CSV "+tag,"CSV","");repo.addClass(classroom);
        Student student=new Student(null,"0"+tag,"Nguyễn, \"An\"",null,"","",0);repo.addStudentToClass(student,classroom.getId());
        AttendanceSession session=new AttendanceSession(null,classroom.getId(),"Xuat CSV","06/11/2026","07:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0);
        repo.addSession(session);repo.openSession(session.getId());repo.manual(session.getId(),student.getId(),System.currentTimeMillis());repo.finishSession(session.getId());
        String path="/sdcard/Download/diem_danh_"+session.getId()+".csv";
        try(ActivityScenario<SessionResultActivity> screen=ActivityScenario.launch(new Intent(context,SessionResultActivity.class).putExtra(SessionResultActivity.EXTRA_SESSION_ID,session.getId()))){
            onView(withId(R.id.action_export)).perform(click());onView(withId(R.id.btn_export_copy)).perform(click());
            waitNode(n->String.valueOf(n.getPackageName()).endsWith(".documentsui"));downloads();
            waitNode(n->String.valueOf(n.getViewIdResourceName()).endsWith(":id/button1")||text(n,"Lưu")||text(n,"Save"))
                .performAction(AccessibilityNodeInfo.ACTION_CLICK);
            String csv="";long deadline=System.currentTimeMillis()+10000;
            while(!csv.contains(student.getMssv())&&System.currentTimeMillis()<deadline){Thread.sleep(150);csv=shell("cat "+path);}
            assertTrue(csv,csv.contains(student.getMssv()));assertTrue(csv,csv.contains("\"Nguyễn, \"\"An\"\"\""));
            waitNode(n->String.valueOf(n.getViewIdResourceName()).endsWith(":id/btn_export_share"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withId(R.id.btn_export_share)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            waitNode(n->{String pkg=String.valueOf(n.getPackageName());return pkg.endsWith(".intentresolver")||pkg.equals("android");});
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            Thread.sleep(500);
        }
        String fixture="th9-import-"+tag+".csv";File source=new File(context.getExternalFilesDir(null),fixture);
        try(OutputStream out=new FileOutputStream(source)){out.write(("\uFEFFmssv,ho_ten,ma_the\r\n00"+tag+",\"Lê, Bình\",RAW:"+tag+"\r\n").getBytes(StandardCharsets.UTF_8));}
        shell("cp "+source.getAbsolutePath()+" /sdcard/Download/"+fixture);
        try(ActivityScenario<CsvImportPreviewActivity> screen=ActivityScenario.launch(new Intent(context,CsvImportPreviewActivity.class).putExtra(CsvImportPreviewActivity.EXTRA_CLASS_ID,classroom.getId()))){
            onView(withId(R.id.btn_csv_pick_file)).perform(click());
            waitNode(n->String.valueOf(n.getPackageName()).endsWith(".documentsui"));downloads();
            clickSystem(waitNode(n->text(n,fixture)));
            long deadline=System.currentTimeMillis()+10000;java.util.concurrent.atomic.AtomicBoolean enabled=new java.util.concurrent.atomic.AtomicBoolean();
            while(!enabled.get()&&System.currentTimeMillis()<deadline){screen.onActivity(a->enabled.set(a.findViewById(R.id.btn_csv_confirm_import).isEnabled()));Thread.sleep(100);}
            if(!enabled.get())logTree(automation.getRootInActiveWindow());
            assertTrue("Valid CSV preview must enable import",enabled.get());onView(withId(R.id.btn_csv_confirm_import)).perform(click());
            while(repo.findByMssv("00"+tag)==null&&System.currentTimeMillis()<deadline)Thread.sleep(100);
            assertNotNull(repo.findByMssv("00"+tag));assertEquals("Lê, Bình",repo.findByMssv("00"+tag).getFullName());
        }finally{
            shell("rm -f "+path+" /sdcard/Download/"+fixture);source.delete();
        }
    }
}

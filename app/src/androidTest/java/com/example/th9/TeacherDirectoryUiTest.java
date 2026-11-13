package com.example.th9;
import android.widget.*;
import android.view.View;
import android.graphics.Bitmap;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.model.*;
import com.example.th9.ui.TeacherAccountsActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
@RunWith(AndroidJUnit4.class)
public class TeacherDirectoryUiTest extends RoleUiTest {
    void first(ActivityScenario<TeacherAccountsActivity> screen,String username,int count){
        onView(withId(R.id.accounts_list)).check(matches(isDisplayed()));
        screen.onActivity(a->{RecyclerView list=a.findViewById(R.id.accounts_list);assertEquals(count,list.getAdapter().getItemCount());RecyclerView.ViewHolder row=list.findViewHolderForAdapterPosition(0);assertNotNull(row);assertEquals(username,((TextView)row.itemView.findViewById(R.id.teacher_username)).getText().toString());});
    }
    @Test public void searchSortRestoreAndAccountActions()throws Exception{
        repo.setupAdmin("admin","Admin","TestPass123!");ClassRoom a=new ClassRoom(null,"Class A","A",""),b=new ClassRoom(null,"Class B","B",""),c=new ClassRoom(null,"Class C","C","");repo.addClass(a);repo.addClass(b);repo.addClass(c);
        String an=repo.createTeacher("gv.an","Nguyễn Ánh","TestPass123!"),binh=repo.createTeacher("gv.binh","Đỗ Bình","TestPass123!"),zed=repo.createTeacher("gv.zed","Zed","TestPass123!");repo.setAssignment(an,a.getId(),true);repo.setAssignment(an,b.getId(),true);
        try(ActivityScenario<TeacherAccountsActivity> screen=ActivityScenario.launch(TeacherAccountsActivity.class)){
            activity(TeacherAccountsActivity.class);
            edit(R.id.accounts_search,"do binh");first(screen,"gv.binh",1);onView(withId(R.id.accounts_results)).check(matches(withText(context.getString(R.string.teachers_results,1,3))));
            edit(R.id.accounts_search,"GV.");tap(R.id.accounts_sort);onView(withText("Số lớp giảm dần")).inRoot(isDialog()).perform(click());first(screen,"gv.an",3);
            screen.recreate();activity(TeacherAccountsActivity.class);onView(withId(R.id.accounts_search)).check(matches(withText("GV.")));onView(withId(R.id.accounts_sort)).check(matches(withText("Số lớp giảm dần")));first(screen,"gv.an",3);
            Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();File folder=context.getExternalFilesDir("verification");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,"teacher-directory.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            edit(R.id.accounts_search,"nobody");onView(withId(R.id.accounts_empty)).check(matches(isDisplayed()));tap(R.id.accounts_clear_search);first(screen,"gv.an",3);
            tap(R.id.accounts_sort);onView(withText("Tài khoản Z–A")).inRoot(isDialog()).perform(click());first(screen,"gv.zed",3);
            edit(R.id.accounts_search,"gv.zed");tap(R.id.teacher_more);onView(withText(R.string.teachers_edit)).perform(click());edit(R.id.account_name,"Zed Mới");onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());idle();assertEquals("Zed Mới",repo.getTeachers().stream().filter(x->x.id.equals(zed)).findFirst().get().fullName);onView(withId(R.id.accounts_search)).check(matches(withText("gv.zed")));
            tap(R.id.teacher_more);onView(withText(R.string.teachers_lock)).perform(click());onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());idle();assertFalse(repo.getTeachers().stream().filter(x->x.id.equals(zed)).findFirst().get().enabled);onView(withId(R.id.teacher_status)).check(matches(withText(R.string.teachers_locked)));
            edit(R.id.accounts_search,"gv.an");tap(R.id.teacher_assign);onView(withText("Class C • C")).inRoot(isDialog()).perform(click());onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());idle();assertEquals(3,repo.assignedClassIds(an).size());onView(withId(R.id.teacher_classes)).check(matches(withText(context.getString(R.string.teachers_classes,3))));onView(withId(R.id.accounts_search)).check(matches(withText("gv.an")));
        }
    }
}

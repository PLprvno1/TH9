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
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class ClassStudentSelectionUiTest {
    private RoleUiTest f;private AttendanceRepository r;private ClassRoom a,b;private Student binh;
    @Before public void setup()throws Exception{
        f=new RoleUiTest();f.setup();r=f.repo;r.setupAdmin("admin","Admin","TestPass123!");a=new ClassRoom(null,"Lớp đích","TARGET","");b=new ClassRoom(null,"Lớp khác","OTHER","");r.addClass(a);r.addClass(b);
        student("Nguyễn Ánh",null);binh=student("Bình",b.getId());student("Chi",null);
    }
    private Student student(String name,String classId){Student s=new Student(null,"",name,null,"","",0);s.setBirthDate("01/01/2005");s.setGender("Nam");s.setCourse("2020–2026");return r.registerStudent(s,classId);}
    @After public void close()throws Exception{f.close();}
    private void pickFirst(){onData(anything()).inAdapterView(withId(R.id.class_student_selection_list)).atPosition(0).perform(click());}
    @Test public void classAddsExistingStudentsWithSearchRotationCancelAndDuplicateProtection()throws Exception{
        try(ActivityScenario<ClassDetailActivity> screen=ActivityScenario.launch(new Intent(f.context,ClassDetailActivity.class).putExtra(ClassDetailActivity.EXTRA_CLASS_ID,a.getId()))){
            screen.onActivity(activity->assertEquals(View.GONE,activity.findViewById(R.id.btn_empty_import_csv).getVisibility()));
            f.tap(R.id.btn_empty_add_student);ClassStudentSelectionActivity selector=f.activity(ClassStudentSelectionActivity.class);assertEquals(3,r.getAllStudents().size());
            f.edit(R.id.class_student_selection_search,"anh");pickFirst();f.edit(R.id.class_student_selection_search,binh.getMssv());pickFirst();
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(selector::recreate);f.idle();
            onView(withId(R.id.class_student_selection_count)).check(matches(withText(f.context.getString(R.string.class_select_students_count,2))));
            f.edit(R.id.class_student_selection_search,"");
            android.graphics.Bitmap shot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();java.io.File folder=f.context.getExternalFilesDir("verification");folder.mkdirs();
            try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"class-student-selection.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            androidx.test.espresso.Espresso.pressBack();f.activity(ClassDetailActivity.class);assertTrue(r.getStudentsForClass(a.getId()).isEmpty());
            f.tap(R.id.btn_empty_add_student);f.activity(ClassStudentSelectionActivity.class);f.edit(R.id.class_student_selection_search,"binh");pickFirst();f.tap(R.id.class_student_selection_save);f.idle();f.activity(ClassDetailActivity.class);
            assertEquals(1,r.getStudentsForClass(a.getId()).size());assertEquals(3,r.getAllStudents().size());assertEquals(b.getId(),r.getStudentById(binh.getId()).getPrimaryClassId());
            openActionBarOverflowOrOptionsMenu(f.context);onView(withText("Thêm sinh viên")).perform(click());ClassStudentSelectionActivity again=f.activity(ClassStudentSelectionActivity.class);f.edit(R.id.class_student_selection_search,"binh");
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{ListView list=again.findViewById(R.id.class_student_selection_list);assertFalse(list.getAdapter().isEnabled(0));assertTrue(list.isItemChecked(0));assertFalse(again.findViewById(R.id.class_student_selection_save).isEnabled());});
        }
    }
    @Test public void csvEntryIsInStudentManagementAndTargetsSystemDirectory()throws Exception{
        try(ActivityScenario<StudentsActivity> screen=ActivityScenario.launch(StudentsActivity.class)){
            f.tap(R.id.students_import_csv);f.activity(CsvImportPreviewActivity.class);
            onView(withId(R.id.tv_csv_summary)).check(matches(withText(containsString("Chưa gán vào lớp"))));assertEquals(3,r.getAllStudents().size());assertEquals(1,r.getStudentsForClass(b.getId()).size());
        }
    }
}

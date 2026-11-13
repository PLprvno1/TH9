package com.example.th9;
import android.content.Intent;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.th9.model.*;
import com.example.th9.ui.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.action.ViewActions.click;
/** Uses an isolated in-memory repository inherited from RoleUiTest. */
@RunWith(AndroidJUnit4.class)
public class StudentDeletionUiTest extends RoleUiTest {
    Student student(String name,ClassRoom c){Student s=new Student(null,"",name,null,"","",0);s.setBirthDate("01/01/2005");s.setCourse("2020–2026");s.setGender("Nam");return repo.registerStudent(s,c.getId());}
    @Test public void deleteFromListAndProfilePreservesHistory()throws Exception{
        repo.setupAdmin("admin","Admin","TestPass123!");ClassRoom c=new ClassRoom(null,"A","A","");repo.addClass(c);
        Student first=student("Delete One",c),second=student("Delete Two",c);
        AttendanceSession session=new AttendanceSession(null,c.getId(),"History","11/11/2026","07:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0);repo.addSession(session);repo.openSession(session.getId());repo.manual(session.getId(),first.getId(),0);repo.finishSession(session.getId());
        String teacher=repo.createTeacher("teacher","Teacher","TestPass123!");repo.setAssignment(teacher,c.getId(),true);repo.logout();repo.login("teacher","TestPass123!");
        Intent profile=new Intent(context,StudentProfileActivity.class).putExtra(StudentProfileActivity.EXTRA_STUDENT_ID,first.getId());
        try(ActivityScenario<StudentProfileActivity> screen=ActivityScenario.launch(profile)){screen.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.btn_profile_delete).getVisibility()));}
        try{repo.deleteStudent(first.getId());fail("Teacher must not delete");}catch(IllegalArgumentException expected){}
        repo.logout();repo.login("admin","TestPass123!");
        try(ActivityScenario<StudentsActivity> screen=ActivityScenario.launch(StudentsActivity.class)){
            activity(StudentsActivity.class);
            screen.onActivity(a->{RecyclerView list=a.findViewById(R.id.students_list);assertEquals(2,list.getAdapter().getItemCount());View button=list.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.btn_student_delete);assertEquals(View.VISIBLE,button.getVisibility());button.performClick();});
            screen.recreate();activity(StudentsActivity.class);assertNotNull(repo.getStudentById(first.getId()));
            screen.onActivity(a->{RecyclerView list=a.findViewById(R.id.students_list);list.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.btn_student_delete).performClick();});
            onView(withId(android.R.id.button2)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());assertNotNull(repo.getStudentById(first.getId()));
            screen.onActivity(a->{RecyclerView list=a.findViewById(R.id.students_list);list.findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.btn_student_delete).performClick();});
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());idle();assertNull(repo.getStudentById(first.getId()));assertEquals(1,repo.getStudentsForClass(c.getId()).size());assertTrue(db.store().student(first.getId()).archived);
            screen.onActivity(a->assertEquals(1,((RecyclerView)a.findViewById(R.id.students_list)).getAdapter().getItemCount()));
        }
        profile.putExtra(StudentProfileActivity.EXTRA_STUDENT_ID,second.getId());
        try(ActivityScenario<StudentProfileActivity> screen=ActivityScenario.launch(profile)){
            activity(StudentProfileActivity.class);
            onView(withId(R.id.btn_profile_delete)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
            onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());idle();
        }
        assertTrue(repo.getAllStudents().isEmpty());assertTrue(repo.getStudentsForClass(c.getId()).isEmpty());assertEquals(2,repo.getRecordsForSession(session.getId()).size());assertEquals(1,repo.getCheckedInTotal(session.getId()));assertEquals(1,repo.getAbsentCount(session.getId()));assertEquals("Delete One",repo.getRecordForStudent(session.getId(),first.getId()).getNameSnapshot());
        try{repo.verifyCard(first.getMssv());fail("Deleted card must not scan");}catch(IllegalArgumentException expected){}
        assertEquals("202020260003",student("Next",c).getMssv());
    }
}

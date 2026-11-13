package com.example.th9;
import android.content.Intent;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.th9.model.*;
import com.example.th9.ui.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/** Exercises role-specific navigation against the inherited isolated repository. */
@RunWith(AndroidJUnit4.class)
public class AdminLandingUiTest extends RoleUiTest {
    @Test public void adminStartsInManagementAndTeacherKeepsAttendance()throws Exception{
        repo.setupAdmin("admin","Admin","TestPass123!");ClassRoom c=new ClassRoom(null,"A","A","");repo.addClass(c);
        String teacher=repo.createTeacher("teacher","Teacher","TestPass123!");repo.setAssignment(teacher,c.getId(),true);
        AttendanceSession legacy=new AttendanceSession(null,c.getId(),"Existing session","11/11/2026","07:00",false,"",AttendanceSession.SessionStatus.DRAFT,null,0);repo.addSession(legacy);
        Intent oldHome=new Intent(context,MainActivity.class).putExtra(MainActivity.EXTRA_INITIAL_TAB,R.id.nav_home);
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(oldHome)){
            screen.onActivity(a->{BottomNavigationView nav=a.findViewById(R.id.bottom_navigation);assertNull(nav.getMenu().findItem(R.id.nav_home));assertEquals(3,nav.getMenu().size());assertEquals(R.id.nav_classes,nav.getSelectedItemId());assertEquals(View.VISIBLE,a.findViewById(R.id.view_management).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.view_home).getVisibility());a.findViewById(R.id.card_manage_classes).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.view_classes).getVisibility());a.getOnBackPressedDispatcher().onBackPressed();assertEquals(View.VISIBLE,a.findViewById(R.id.view_management).getVisibility());nav.setSelectedItemId(R.id.nav_personal);a.findViewById(R.id.personal_history).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.view_history).getVisibility());nav.setSelectedItemId(R.id.nav_classes);});
            screen.recreate();screen.onActivity(a->{assertEquals(View.VISIBLE,a.findViewById(R.id.view_management).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.view_home).getVisibility());});
        }
        Intent classroom=new Intent(context,ClassDetailActivity.class).putExtra(ClassDetailActivity.EXTRA_CLASS_ID,c.getId());
        try(ActivityScenario<ClassDetailActivity> screen=ActivityScenario.launch(classroom)){screen.onActivity(a->{assertEquals(View.VISIBLE,a.findViewById(R.id.btn_detail_create_session).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.btn_detail_add_student).getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.btn_detail_assign_teachers).getVisibility());});}
        try(ActivityScenario<SessionDetailActivity> screen=ActivityScenario.launch(new Intent(context,SessionDetailActivity.class).putExtra(SessionDetailActivity.EXTRA_SESSION_ID,legacy.getId()))){screen.onActivity(a->{for(int id:new int[]{R.id.btn_action_open_session,R.id.btn_action_scan,R.id.btn_action_manual,R.id.btn_action_finish_session})assertEquals(View.GONE,a.findViewById(id).getVisibility());});}
        for(Intent blocked:new Intent[]{new Intent(context,SessionFormActivity.class),new Intent(context,ScanActivity.class).putExtra(ScanActivity.EXTRA_MODE,ScanActivity.MODE_ATTENDANCE)}){
            try(ActivityScenario<?> screen=ActivityScenario.launch(blocked)){long until=System.currentTimeMillis()+3000;while(screen.getState()!=Lifecycle.State.DESTROYED&&System.currentTimeMillis()<until)Thread.sleep(100);assertEquals(Lifecycle.State.DESTROYED,screen.getState());}
        }
        repo.logout();repo.login("teacher","TestPass123!");
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){screen.onActivity(a->{BottomNavigationView nav=a.findViewById(R.id.bottom_navigation);assertNotNull(nav.getMenu().findItem(R.id.nav_home));assertEquals(4,nav.getMenu().size());assertEquals(R.id.nav_home,nav.getSelectedItemId());assertEquals(View.VISIBLE,a.findViewById(R.id.view_home).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.view_management).getVisibility());});}
        try(ActivityScenario<ClassDetailActivity> screen=ActivityScenario.launch(classroom)){screen.onActivity(a->{assertEquals(View.VISIBLE,a.findViewById(R.id.btn_detail_create_session).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.btn_detail_add_student).getVisibility());});}
    }
}

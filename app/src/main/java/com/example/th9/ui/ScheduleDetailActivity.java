package com.example.th9.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ScheduleDetailActivity extends DataActivity {
    public static final String EXTRA_ID="schedule_id",EXTRA_DATE="schedule_date";
    private AttendanceRepository repo;
    private ClassSchedule schedule;
    private String id,date;
    private MaterialButton attend;
    @Override protected void onReady(Bundle state){
        repo=AttendanceRepository.getInstance();id=getIntent().getStringExtra(EXTRA_ID);date=getIntent().getStringExtra(EXTRA_DATE);
        setContentView(R.layout.activity_schedule_detail);
        MaterialToolbar toolbar=findViewById(R.id.toolbar_schedule_detail);toolbar.setNavigationOnClickListener(v->finish());
        attend=findViewById(R.id.schedule_attend);attend.setOnClickListener(v->startAttendance());
        findViewById(R.id.schedule_roster).setOnClickListener(v->{if(schedule!=null)startActivity(new Intent(this,ClassDetailActivity.class).putExtra(ClassDetailActivity.EXTRA_CLASS_ID,schedule.classId));});
        findViewById(R.id.schedule_edit).setOnClickListener(v->startActivity(new Intent(this,ScheduleFormActivity.class).putExtra(EXTRA_ID,id)));
        findViewById(R.id.schedule_archive).setOnClickListener(v->new MaterialAlertDialogBuilder(this).setTitle("Lưu trữ lịch học?")
                .setMessage("Lịch sẽ ngừng xuất hiện trong thời khóa biểu. Buổi điểm danh và kết quả đã có vẫn được giữ nguyên trong Lịch sử.")
                .setPositiveButton("Lưu trữ",(d,w)->Tasks.run(this,null,()->{repo.archiveSchedule(id);return true;},ok->finish())).setNegativeButton("Hủy",null).show());
        for(int view:new int[]{R.id.schedule_edit,R.id.schedule_archive})findViewById(view).setVisibility(repo.isAdmin()?View.VISIBLE:View.GONE);
    }
    @Override protected void onDataResume(){
        schedule=repo.getSchedule(id);if(schedule==null){finish();return;}
        ClassRoom c=repo.getClassById(schedule.classId);if(c==null){finish();return;}
        ((TextView)findViewById(R.id.schedule_detail_title)).setText(c.getName()+" • "+c.getCode());
        String info=ScheduleRules.shift(schedule)+" • "+ScheduleRules.time(schedule.startMinute)+"–"+ScheduleRules.time(schedule.endMinute)
                +"\n"+ScheduleRules.days(schedule)+"\n"+ScheduleRules.display(schedule.startDate)+" – "+ScheduleRules.display(schedule.endDate)
                +"\nSĩ số hiện tại: "+c.getStudentCount()+" sinh viên";
        AttendanceSession existing=date==null?null:repo.getOccurrenceSession(id,date);
        if(date!=null)info+="\nNgày học đã chọn: "+ScheduleRules.display(date);
        if(existing!=null)info+="\n"+existing.getScheduleLabel()+"\nTrạng thái: "+existing.getStatusDisplayName()+"\nGiảng viên: "+existing.getTeacherNameSnapshot();
        else info+="\nChưa mở điểm danh. Chưa có dữ liệu có mặt/vắng.";
        if(existing!=null&&existing.isEnableLateThreshold())info+="\nMốc đi muộn do giảng viên chọn: sau "+existing.getLateThresholdTime();
        ((TextView)findViewById(R.id.schedule_detail_info)).setText(info);
        attend.setVisibility(date!=null&&(!repo.isAdmin()||existing!=null)?View.VISIBLE:View.GONE);
        attend.setText(existing==null?"Tạo buổi điểm danh":existing.getStatus()==AttendanceSession.SessionStatus.CLOSED?"Xem kết quả":
                existing.getStatus()==AttendanceSession.SessionStatus.DRAFT?(!repo.isAdmin()&&repo.canControlSession(existing.getId())?"Hoàn tất thiết lập":"Xem buổi chưa mở"):"Tiếp tục điểm danh");
        if(existing!=null&&existing.getStatus()!=AttendanceSession.SessionStatus.CLOSED&&!repo.canControlSession(existing.getId()))attend.setText("Xem buổi của "+existing.getTeacherNameSnapshot());
    }
    private void startAttendance(){
        AttendanceSession existing=repo.getOccurrenceSession(id,date);
        if(existing!=null){
            if(existing.getStatus()==AttendanceSession.SessionStatus.DRAFT&&!repo.isAdmin()&&repo.canControlSession(existing.getId()))openForm(existing.getId());
            else open(existing.getId());
            return;
        }
        openForm(null);
    }
    private void openForm(String sessionId){
        Intent intent=new Intent(this,SessionFormActivity.class).putExtra(SessionFormActivity.EXTRA_SCHEDULE_ID,id)
                .putExtra(SessionFormActivity.EXTRA_SCHEDULE_DATE,date);
        if(sessionId!=null)intent.putExtra(SessionFormActivity.EXTRA_SESSION_ID,sessionId);
        startActivity(intent);
    }
    private void open(String sessionId){
        AttendanceSession session=repo.getSessionById(sessionId);if(session==null){onDataResume();return;}
        boolean closed=session.getStatus()==AttendanceSession.SessionStatus.CLOSED;
        startActivity(new Intent(this,closed?SessionResultActivity.class:SessionDetailActivity.class).putExtra(SessionDetailActivity.EXTRA_SESSION_ID,sessionId));
    }
}

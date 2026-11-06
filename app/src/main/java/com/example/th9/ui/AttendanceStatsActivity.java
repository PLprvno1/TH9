package com.example.th9.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import com.example.th9.R;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.*;
import com.example.th9.view.SimpleBarChartView;
import com.google.android.material.chip.ChipGroup;
import java.util.*;

public class AttendanceStatsActivity extends DataActivity {
    private SessionFilters filters;
    private TextView rate,total,students;
    private SimpleBarChartView chart;
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_attendance_stats);
        Toolbar toolbar=findViewById(R.id.toolbar_stats);setSupportActionBar(toolbar);
        if(getSupportActionBar()!=null)getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v->finish());
        rate=findViewById(R.id.tv_stats_avg_rate);total=findViewById(R.id.tv_stats_total_sessions);chart=findViewById(R.id.chart_view);
        students=new TextView(this);students.setTextSize(15);students.setPadding(20,20,20,30);
        ((android.widget.LinearLayout)chart.getParent()).addView(students);
        filters=new SessionFilters(this,this::render);filters.restore(state);
        if(state==null&&getIntent().getBundleExtra("filters")!=null)filters.restore(getIntent().getBundleExtra("filters"));
        filters.classes(findViewById(R.id.chip_group_stats_class));render();
    }
    @Override public boolean onCreateOptionsMenu(Menu menu){menu.add(0,7201,0,"Lọc thời gian");return true;}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==7201){filters.dateDialog();return true;}return super.onOptionsItemSelected(item);}
    private void render(){
        AttendanceRepository repo=AttendanceRepository.getInstance();List<SimpleBarChartView.BarData> bars=new ArrayList<>();
        Map<String,int[]> counts=new LinkedHashMap<>();Map<String,String> names=new LinkedHashMap<>();
        int present=0,late=0,absent=0,sessions=0;
        // Statistics always use CLOSED sessions, independent of a history status selection.
        AttendanceSession.SessionStatus previous=filters.status;filters.status=null;
        for(AttendanceSession s:repo.getAllSessions())if(s.getStatus()==AttendanceSession.SessionStatus.CLOSED&&filters.matches(s)){
            sessions++;int p=repo.getPresentCount(s.getId()),l=repo.getLateCount(s.getId()),a=repo.getAbsentCount(s.getId());
            present+=p;late+=l;absent+=a;bars.add(new SimpleBarChartView.BarData(s.getDate(),p,l,a));
            for(AttendanceRecord r:repo.getRecordsForSession(s.getId())){
                int[] c=counts.computeIfAbsent(r.getStudentId(),id->new int[3]);
                if(r.getStatus()==AttendanceRecord.AttendanceStatus.PRESENT)c[0]++;else if(r.getStatus()==AttendanceRecord.AttendanceStatus.LATE)c[1]++;else if(r.getStatus()==AttendanceRecord.AttendanceStatus.ABSENT)c[2]++;
                names.putIfAbsent(r.getStudentId(),r.getMssvSnapshot()+" · "+r.getNameSnapshot());
            }
        }
        filters.status=previous;int denominator=present+late+absent;
        rate.setText(denominator==0?"Chưa có dữ liệu":String.format(Locale.getDefault(),"%.1f%%",100.0*(present+late)/denominator));
        total.setText(sessions+" buổi");chart.setData(bars);
        StringBuilder text=new StringBuilder("Có mặt: "+present+" · Đi muộn: "+late+" · Vắng: "+absent+"\n\nTheo sinh viên\n");
        if(denominator==0)text.append("Chưa có dữ liệu");
        for(String id:counts.keySet()){
            int[] c=counts.get(id);int all=c[0]+c[1]+c[2];
            text.append(names.get(id)).append("\nCó mặt ").append(c[0]).append(" · Muộn ").append(c[1]).append(" · Vắng ").append(c[2])
                .append(" · ").append(all==0?"Chưa có dữ liệu":String.format(Locale.getDefault(),"%.1f%%",100.0*(c[0]+c[1])/all)).append("\n\n");
        }
        students.setText(text);
    }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state){if(filters!=null)filters.save(state);super.onSaveInstanceState(state);}
}

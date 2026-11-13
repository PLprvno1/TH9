package com.example.th9.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import androidx.core.content.ContextCompat;
import java.util.*;

public class TimetableActivity extends DataActivity {
    public static final String EXTRA_CLASS_ID="schedule_class_id";
    private AttendanceRepository repo;
    private long week;
    private boolean manage;
    private String classId,teacherId;
    private Spinner classes,teachers;
    private List<ClassRoom> classOptions;
    private List<Account> teacherOptions;
    private Rows adapter;
    private boolean binding;
    @Override protected void onReady(Bundle state){
        repo=AttendanceRepository.getInstance();setContentView(R.layout.activity_timetable);
        long today=ScheduleRules.day(ScheduleRules.today());week=today-ScheduleRules.weekday(today);
        classId=getIntent().getStringExtra(EXTRA_CLASS_ID);
        if(state!=null){week=state.getLong("week",week);manage=state.getBoolean("manage");classId=state.getString("classId");teacherId=state.getString("teacherId");}
        MaterialToolbar toolbar=findViewById(R.id.toolbar_timetable);setSupportActionBar(toolbar);toolbar.setNavigationOnClickListener(v->finish());
        RecyclerView list=findViewById(R.id.schedule_list);list.setLayoutManager(new LinearLayoutManager(this));adapter=new Rows();list.setAdapter(adapter);
        MaterialButtonToggleGroup modes=findViewById(R.id.schedule_modes);
        modes.setVisibility(repo.isAdmin()?View.VISIBLE:View.GONE);modes.check(manage?R.id.mode_rules:R.id.mode_week);
        modes.addOnButtonCheckedListener((group,id,checked)->{if(checked){manage=id==R.id.mode_rules;render();}});
        findViewById(R.id.week_previous).setOnClickListener(v->{week-=7;render();});
        findViewById(R.id.week_next).setOnClickListener(v->{week+=7;render();});
        findViewById(R.id.schedule_add).setVisibility(repo.isAdmin()?View.VISIBLE:View.GONE);
        findViewById(R.id.schedule_add).setOnClickListener(v->startActivity(new Intent(this,ScheduleFormActivity.class).putExtra(EXTRA_CLASS_ID,classId)));
        classes=findViewById(R.id.schedule_class_filter);teachers=findViewById(R.id.schedule_teacher_filter);
        teachers.setVisibility(repo.isAdmin()?View.VISIBLE:View.GONE);
        classes.setOnItemSelectedListener(new Selection(()->{int p=classes.getSelectedItemPosition();classId=p>0&&p<=classOptions.size()?classOptions.get(p-1).getId():null;render();}));
        teachers.setOnItemSelectedListener(new Selection(()->{int p=teachers.getSelectedItemPosition();teacherId=p>0&&p<=teacherOptions.size()?teacherOptions.get(p-1).id:null;render();}));
    }
    private class Selection implements AdapterView.OnItemSelectedListener {
        final Runnable changed;Selection(Runnable changed){this.changed=changed;}
        public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!binding)changed.run();}
        public void onNothingSelected(AdapterView<?> p){}
    }
    @Override protected void onDataResume(){
        binding=true;classOptions=repo.getAllClasses();List<String> labels=new ArrayList<>();labels.add("Tất cả lớp");int selected=0;
        for(ClassRoom c:classOptions){labels.add(c.getName()+" • "+c.getCode());if(c.getId().equals(classId))selected=labels.size()-1;}
        classes.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));classes.setSelection(selected);
        if(selected==0)classId=null;
        teacherOptions=repo.isAdmin()?repo.getTeachers():Collections.emptyList();labels=new ArrayList<>();labels.add("Tất cả giảng viên");selected=0;
        for(Account t:teacherOptions){labels.add(t.fullName+" ("+t.username+")");if(t.id.equals(teacherId))selected=labels.size()-1;}
        teachers.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));teachers.setSelection(selected);
        if(selected==0)teacherId=null;binding=false;render();
    }
    private void render(){
        if(adapter==null)return;
        findViewById(R.id.week_navigation).setVisibility(manage?View.GONE:View.VISIBLE);
        ((TextView)findViewById(R.id.week_label)).setText(ScheduleRules.display(ScheduleRules.iso(week))+" – "+ScheduleRules.display(ScheduleRules.iso(week+6)));
        List<Entry> entries=new ArrayList<>();List<String> allowed=teacherId==null?null:repo.assignedClassIds(teacherId);
        for(ClassSchedule rule:repo.getSchedules()){
            if(classId!=null&&!classId.equals(rule.classId)||allowed!=null&&!allowed.contains(rule.classId))continue;
            if(manage)entries.add(new Entry(rule,null));
            else for(long d=week;d<week+7;d++)if(ScheduleRules.occurs(rule,d))entries.add(new Entry(rule,ScheduleRules.iso(d)));
        }
        entries.sort(Comparator.comparing((Entry e)->e.date==null?e.rule.startDate:e.date).thenComparingInt(e->e.rule.startMinute).thenComparing(e->e.rule.classId));
        adapter.entries=entries;adapter.notifyDataSetChanged();findViewById(R.id.schedule_empty).setVisibility(entries.isEmpty()?View.VISIBLE:View.GONE);
        RecyclerView list=findViewById(R.id.schedule_list);list.setAlpha(0.4f);list.animate().alpha(1f).setDuration(160).start();
    }
    @Override public boolean onCreateOptionsMenu(Menu menu){menu.add(0,1,0,R.string.schedule_this_week);return true;}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==1){long d=ScheduleRules.day(ScheduleRules.today());week=d-ScheduleRules.weekday(d);render();return true;}return super.onOptionsItemSelected(item);}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putLong("week",week);out.putBoolean("manage",manage);out.putString("classId",classId);out.putString("teacherId",teacherId);}
    static class Entry {final ClassSchedule rule;final String date;Entry(ClassSchedule s,String d){rule=s;date=d;}}
    class Holder extends RecyclerView.ViewHolder {TextView title,info,status;Holder(View v){super(v);title=v.findViewById(R.id.schedule_row_title);info=v.findViewById(R.id.schedule_row_info);status=v.findViewById(R.id.schedule_row_status);}}
    class Rows extends RecyclerView.Adapter<Holder> {
        List<Entry> entries=new ArrayList<>();
        public Holder onCreateViewHolder(ViewGroup p,int type){return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_schedule,p,false));}
        public int getItemCount(){return entries.size();}
        public void onBindViewHolder(Holder h,int position){
            Entry e=entries.get(position);ClassSchedule s=e.rule;ClassRoom c=repo.getClassById(s.classId);
            h.title.setText(c==null?"Lớp học":c.getName()+" • "+c.getCode());
            String range=e.date==null?ScheduleRules.display(s.startDate)+" – "+ScheduleRules.display(s.endDate)+"\n"+ScheduleRules.days(s):ScheduleRules.daysForDate(e.date)+" • "+ScheduleRules.display(e.date);
            h.info.setText(range+"\n"+ScheduleRules.shift(s)+" • "+ScheduleRules.time(s.startMinute)+"–"+ScheduleRules.time(s.endMinute));
            String status="Lịch lặp";int bg=R.color.secondary_container,fg=R.color.text_secondary;
            if(e.date!=null){int cmp=e.date.compareTo(ScheduleRules.today());status=cmp==0?"Hôm nay":cmp<0?"Đã qua":"Sắp tới";
                if(cmp==0){bg=R.color.primary_container;fg=R.color.on_primary_container;}
                AttendanceSession session=repo.getOccurrenceSession(s.id,e.date);if(session!=null)status+=" • "+session.getStatusDisplayName();}
            h.status.setText(status);h.status.setBackgroundColor(ContextCompat.getColor(TimetableActivity.this,bg));h.status.setTextColor(ContextCompat.getColor(TimetableActivity.this,fg));
            h.itemView.setOnClickListener(v->startActivity(new Intent(TimetableActivity.this,ScheduleDetailActivity.class).putExtra(ScheduleDetailActivity.EXTRA_ID,s.id).putExtra(ScheduleDetailActivity.EXTRA_DATE,e.date)));
        }
    }
}

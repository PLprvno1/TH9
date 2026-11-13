package com.example.th9.ui;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

public class ScheduleFormActivity extends DataActivity {
    public static final String EXTRA_FIXED_CLASS_ID="schedule_fixed_class_id";
    private ClassSchedule draft;
    private List<ClassRoom> classes;
    private Spinner classPicker;
    private TextView from,until,start,end,error;
    private final int[] dayIds={R.id.schedule_day_0,R.id.schedule_day_1,R.id.schedule_day_2,R.id.schedule_day_3,R.id.schedule_day_4,R.id.schedule_day_5,R.id.schedule_day_6};
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_schedule_form);
        AttendanceRepository repo=AttendanceRepository.getInstance();String id=getIntent().getStringExtra(ScheduleDetailActivity.EXTRA_ID);
        String fixedClass=getIntent().getStringExtra(EXTRA_FIXED_CLASS_ID);
        if(id==null&&fixedClass!=null){ClassSchedule existing=repo.getScheduleForClass(fixedClass);if(existing!=null)id=existing.id;}
        draft=id==null?new ClassSchedule():repo.getSchedule(id);
        if(draft==null){finish();return;}
        if(id==null){draft.startDate=ScheduleRules.today();draft.endDate=ScheduleRules.iso(ScheduleRules.day(draft.startDate)+90);draft.weekdays=1<<ScheduleRules.weekday(ScheduleRules.day(draft.startDate));}
        if(state!=null){ClassSchedule restored=(ClassSchedule)state.getSerializable("draft");if(restored!=null)draft=restored;}
        if(fixedClass!=null){
            if(repo.getClassById(fixedClass)==null||(!draft.classId.isEmpty()&&!fixedClass.equals(draft.classId))){finish();return;}
            draft.classId=fixedClass;
        }
        MaterialToolbar toolbar=findViewById(R.id.toolbar_schedule_form);toolbar.setTitle(id==null?R.string.schedule_add:R.string.schedule_edit);toolbar.setNavigationOnClickListener(v->finish());
        classes=new ArrayList<>();
        for(ClassRoom c:repo.getAllClasses())if(fixedClass!=null?c.getId().equals(fixedClass):!draft.id.isEmpty()?c.getId().equals(draft.classId):repo.getScheduleForClass(c.getId())==null)classes.add(c);
        classPicker=findViewById(R.id.schedule_form_class);List<String> labels=new ArrayList<>();int selected=0;
        String selectedClass=draft.classId.isEmpty()?getIntent().getStringExtra(TimetableActivity.EXTRA_CLASS_ID):draft.classId;
        for(ClassRoom c:classes){labels.add(c.getName()+" • "+c.getCode());if(c.getId().equals(selectedClass))selected=labels.size()-1;}
        classPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));classPicker.setSelection(selected);classPicker.setEnabled(id==null&&fixedClass==null);
        from=findViewById(R.id.schedule_form_from);until=findViewById(R.id.schedule_form_until);start=findViewById(R.id.schedule_form_start);end=findViewById(R.id.schedule_form_end);error=findViewById(R.id.schedule_form_error);
        ((RadioGroup)findViewById(R.id.schedule_form_shift)).check("MORNING".equals(draft.shift)?R.id.shift_morning:R.id.shift_afternoon);
        ((RadioGroup)findViewById(R.id.schedule_form_shift)).setOnCheckedChangeListener((g,checked)->{
            draft.shift=checked==R.id.shift_morning?"MORNING":"AFTERNOON";
            draft.startMinute=checked==R.id.shift_morning?420:780;draft.endMinute=checked==R.id.shift_morning?600:960;showValues();
        });
        for(int i=0;i<7;i++)((Chip)findViewById(dayIds[i])).setChecked((draft.weekdays&(1<<i))!=0);
        from.setOnClickListener(v->pickDate(true));until.setOnClickListener(v->pickDate(false));
        start.setOnClickListener(v->pickTime(true));end.setOnClickListener(v->pickTime(false));
        findViewById(R.id.schedule_form_save).setOnClickListener(v->{
            capture();error.setText("");save(false);
        });
        if(classes.isEmpty()){error.setText(repo.getAllClasses().isEmpty()?"Hãy tạo lớp và phân công giảng viên trước khi xếp lịch.":"Tất cả lớp đã có lịch học. Vào Quản lý lịch để sửa lịch hiện có.");findViewById(R.id.schedule_form_save).setEnabled(false);}
        showValues();
    }
    private void save(boolean overwrite){
        ClassSchedule proposed=draft.copy();
        Tasks.run(this,findViewById(R.id.schedule_form_save),()->AttendanceRepository.getInstance().saveSchedule(proposed,overwrite),saved->finish(),message->{
            error.setText(message);
            if(AttendanceRepository.SCHEDULE_EXISTS.equals(message))new MaterialAlertDialogBuilder(this)
                .setTitle("Lớp đã có lịch học")
                .setMessage("Lớp này đã có lịch. Ghi đè sẽ cập nhật lịch hiện có theo thông tin đang nhập, không tạo lịch thứ hai. Các lịch trùng cũ sẽ được lưu trữ; lịch sử điểm danh được giữ nguyên.")
                .setPositiveButton("Ghi đè / cập nhật",(dialog,which)->save(true)).setNegativeButton("Hủy",null).show();
        });
    }
    private void capture(){int p=classPicker.getSelectedItemPosition();if(p>=0&&p<classes.size())draft.classId=classes.get(p).getId();draft.weekdays=0;for(int i=0;i<7;i++)if(((Chip)findViewById(dayIds[i])).isChecked())draft.weekdays|=1<<i;}
    private void showValues(){from.setText(ScheduleRules.display(draft.startDate));until.setText(ScheduleRules.display(draft.endDate));start.setText(ScheduleRules.time(draft.startMinute));end.setText(ScheduleRules.time(draft.endMinute));}
    private void pickDate(boolean first){
        String[] parts=(first?draft.startDate:draft.endDate).split("-");
        new DatePickerDialog(this,(view,y,m,d)->{
            String date=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);if(first)draft.startDate=date;else draft.endDate=date;showValues();
        },Integer.parseInt(parts[0]),Integer.parseInt(parts[1])-1,Integer.parseInt(parts[2])).show();
    }
    private void pickTime(boolean first){int minutes=first?draft.startMinute:draft.endMinute;
        new TimePickerDialog(this,(view,h,m)->{if(first)draft.startMinute=h*60+m;else draft.endMinute=h*60+m;showValues();},minutes/60,minutes%60,true).show();
    }
    @Override protected void onSaveInstanceState(Bundle out){if(draft!=null&&classPicker!=null){capture();out.putSerializable("draft",draft.copy());}super.onSaveInstanceState(out);}
}

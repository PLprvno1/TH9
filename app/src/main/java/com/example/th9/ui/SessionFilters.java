package com.example.th9.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.View;
import android.widget.*;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.*;
import com.google.android.material.chip.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

/** Shared class/date selection for history and statistics, evaluated against timestamps. */
public final class SessionFilters {
    public String classId;
    public Long from,until;
    public AttendanceSession.SessionStatus status;
    private final Context context;
    private final Runnable changed;
    public SessionFilters(Context context,Runnable changed){this.context=context;this.changed=changed;}
    public boolean matches(AttendanceSession session){
        long time=session.getScheduledAt();
        return (classId==null||classId.equals(session.getClassId()))&&(from==null||time>=from)&&(until==null||time<until)
            &&(status==null||status==session.getStatus());
    }
    public void classes(ChipGroup group){
        String selected=classId;group.setOnCheckedStateChangeListener(null);group.removeAllViews();
        addClass(group,"Tất cả lớp",null,selected==null);
        for(ClassRoom c:AttendanceRepository.getInstance().getAllClasses())addClass(group,c.getName(),c.getId(),c.getId().equals(selected));
        group.setSingleSelection(true);group.setSelectionRequired(true);
        group.setOnCheckedStateChangeListener((g,ids)->{View v=g.findViewById(g.getCheckedChipId());classId=v==null?null:(String)v.getTag();changed.run();});
    }
    private void addClass(ChipGroup group,String label,String id,boolean checked){
        Chip chip=new Chip(context);chip.setId(View.generateViewId());chip.setText(label);chip.setTag(id);chip.setCheckable(true);group.addView(chip);chip.setChecked(checked);
    }
    public void dateDialog(){
        LinearLayout panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(24,12,24,12);
        Button start=new Button(context),end=new Button(context);panel.addView(start);panel.addView(end);
        Long[] selection={from,until};
        Runnable labels=()->{start.setText(selection[0]==null?"Từ ngày: Tất cả":"Từ ngày: "+AttendanceRepository.format(selection[0],"dd/MM/yyyy"));
            end.setText(selection[1]==null?"Đến ngày: Tất cả":"Đến ngày: "+AttendanceRepository.format(selection[1]-1,"dd/MM/yyyy"));};labels.run();
        start.setOnClickListener(v->pick(selection[0],value->{selection[0]=value;labels.run();}));
        end.setOnClickListener(v->pick(selection[1]==null?null:selection[1]-1,value->{Calendar c=Calendar.getInstance();c.setTimeInMillis(value);c.add(Calendar.DAY_OF_MONTH,1);selection[1]=c.getTimeInMillis();labels.run();}));
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(context).setTitle("Lọc thời gian dự kiến")
            .setView(panel).setPositiveButton("Áp dụng",null).setNeutralButton("Tất cả",(d,w)->{from=null;until=null;changed.run();}).setNegativeButton("Hủy",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            if(selection[0]!=null&&selection[1]!=null&&selection[0]>=selection[1]){Toast.makeText(context,"Ngày kết thúc phải từ ngày bắt đầu trở đi",Toast.LENGTH_LONG).show();return;}
            from=selection[0];until=selection[1];changed.run();dialog.dismiss();
        }));dialog.show();
    }
    private void pick(Long initial,java.util.function.Consumer<Long> result){
        Calendar c=Calendar.getInstance();if(initial!=null)c.setTimeInMillis(initial);
        new DatePickerDialog(context,(v,y,m,d)->{Calendar day=Calendar.getInstance();day.clear();day.set(y,m,d);result.accept(day.getTimeInMillis());},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    public void statusDialog(){
        String[] labels={"Tất cả trạng thái","Chưa mở","Đang mở","Đã kết thúc"};
        new MaterialAlertDialogBuilder(context).setTitle("Trạng thái buổi").setItems(labels,(d,w)->{status=w==0?null:w==1?AttendanceSession.SessionStatus.DRAFT:w==2?AttendanceSession.SessionStatus.OPEN:AttendanceSession.SessionStatus.CLOSED;changed.run();}).show();
    }
    public void save(android.os.Bundle state){state.putString("filter_class",classId);if(from!=null)state.putLong("filter_from",from);if(until!=null)state.putLong("filter_until",until);if(status!=null)state.putString("filter_status",status.name());}
    public void restore(android.os.Bundle state){if(state==null)return;classId=state.getString("filter_class");from=state.containsKey("filter_from")?state.getLong("filter_from"):null;until=state.containsKey("filter_until")?state.getLong("filter_until"):null;String s=state.getString("filter_status");status=s==null?null:AttendanceSession.SessionStatus.valueOf(s);}
}

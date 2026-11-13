package com.example.th9.ui;

import android.content.Intent;
import android.view.*;
import android.widget.*;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.button.MaterialButton;
import java.util.List;

/** Shows today's classes and sends the teacher to session setup before attendance starts. */
public final class HomeScheduleSuggestions {
    private final DataActivity owner;
    private final View section;
    private final LinearLayout list;
    public HomeScheduleSuggestions(DataActivity owner){
        this.owner=owner;section=owner.findViewById(R.id.home_suggestions_section);list=owner.findViewById(R.id.home_suggestions_list);
        owner.findViewById(R.id.home_suggestions_extra).setOnClickListener(v->owner.startActivity(new Intent(owner,SessionFormActivity.class)));
    }
    public int render(long now){
        AttendanceRepository repo=AttendanceRepository.getInstance();List<ClassSchedule> suggestions=repo.getCurrentScheduleSuggestions(now);
        list.removeAllViews();section.setVisibility(suggestions.isEmpty()?View.GONE:View.VISIBLE);
        String date=AttendanceRepository.format(now,"yyyy-MM-dd");AttendanceSession active=repo.getActiveSession();
        for(ClassSchedule rule:suggestions){
            ClassRoom classroom=repo.getClassById(rule.classId);if(classroom==null)continue;
            View card=LayoutInflater.from(owner).inflate(R.layout.item_home_schedule_suggestion,list,false);
            ((TextView)card.findViewById(R.id.home_suggestion_class)).setText(classroom.getName()+" • "+classroom.getCode());
            ((TextView)card.findViewById(R.id.home_suggestion_time)).setText(ScheduleRules.display(date)+" • "+ScheduleRules.shift(rule)+" • "+ScheduleRules.time(rule.startMinute)+"–"+ScheduleRules.time(rule.endMinute));
            AttendanceSession existing=repo.getOccurrenceSession(rule.id,date);
            TextView status=card.findViewById(R.id.home_suggestion_status);MaterialButton button=card.findViewById(R.id.home_suggestion_start);
            if(existing!=null){
                boolean own=repo.canControlSession(existing.getId());
                if(existing.getStatus()==AttendanceSession.SessionStatus.DRAFT&&own){
                    button.setText("Hoàn tất thiết lập");status.setText("Buổi chưa mở • "+existing.getTeacherNameSnapshot());
                    button.setOnClickListener(v->openForm(rule.id,date,existing.getId()));
                }else{
                    boolean draft=existing.getStatus()==AttendanceSession.SessionStatus.DRAFT;
                    button.setText(draft?"Xem buổi chưa mở":own?"Tiếp tục điểm danh":"Xem buổi đang mở");
                    status.setText((draft?"Buổi chưa mở • ":"Buổi đang mở • ")+existing.getTeacherNameSnapshot());
                    button.setOnClickListener(v->open(existing.getId()));
                }
            }else{
                boolean blocked=active!=null;button.setEnabled(!blocked);
                status.setText(blocked?"Hãy kết thúc buổi đang mở trước khi bắt đầu lớp này.":"Chưa mở điểm danh • "+classroom.getStudentCount()+" sinh viên");
                button.setOnClickListener(v->prepare(rule,date));
            }
            list.addView(card);
        }
        return suggestions.size();
    }
    private void prepare(ClassSchedule rule,String date){
        AttendanceRepository repo=AttendanceRepository.getInstance();
        boolean current=false;for(ClassSchedule s:repo.getCurrentScheduleSuggestions(System.currentTimeMillis()))if(s.id.equals(rule.id)&&date.equals(ScheduleRules.today()))current=true;
        if(!current){Toast.makeText(owner,"Lịch này không còn trong khung giờ đề xuất. Hãy xem Thời khóa biểu.",Toast.LENGTH_LONG).show();render(System.currentTimeMillis());return;}
        if(repo.getActiveSession()!=null){Toast.makeText(owner,"Hãy kết thúc buổi đang mở trước khi bắt đầu lớp này.",Toast.LENGTH_LONG).show();render(System.currentTimeMillis());return;}
        openForm(rule.id,date,null);
    }
    private void openForm(String scheduleId,String date,String sessionId){
        Intent intent=new Intent(owner,SessionFormActivity.class).putExtra(SessionFormActivity.EXTRA_SCHEDULE_ID,scheduleId)
                .putExtra(SessionFormActivity.EXTRA_SCHEDULE_DATE,date);
        if(sessionId!=null)intent.putExtra(SessionFormActivity.EXTRA_SESSION_ID,sessionId);
        owner.startActivity(intent);
    }
    private void open(String id){owner.startActivity(new Intent(owner,SessionDetailActivity.class).putExtra(SessionDetailActivity.EXTRA_SESSION_ID,id));}
}

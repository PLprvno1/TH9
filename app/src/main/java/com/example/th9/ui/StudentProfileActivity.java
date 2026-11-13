package com.example.th9.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.view.StudentCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

public class StudentProfileActivity extends DataActivity {
    public static final String EXTRA_STUDENT_ID="extra_student_id",EXTRA_CLASS_ID="extra_class_id";
    private String studentId;
    private TextView tvName,tvMssv,tvClass,tvBirth,tvCourse,tvGender,tvPhone,cardError;
    private ImageView avatar;
    private StudentCardView card;
    private LinearLayout llHistoryContainer;
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_student_profile);studentId=getIntent().getStringExtra(EXTRA_STUDENT_ID);
        Toolbar toolbar=findViewById(R.id.toolbar_student_profile);setSupportActionBar(toolbar);getSupportActionBar().setTitle("Hồ sơ sinh viên");getSupportActionBar().setDisplayHomeAsUpEnabled(true);toolbar.setNavigationOnClickListener(v->finish());
        tvName=findViewById(R.id.tv_profile_name);tvMssv=findViewById(R.id.tv_profile_mssv);tvClass=findViewById(R.id.tv_profile_class);tvBirth=findViewById(R.id.tv_profile_birth);tvCourse=findViewById(R.id.tv_profile_course);tvGender=findViewById(R.id.tv_profile_gender);tvPhone=findViewById(R.id.tv_profile_phone);avatar=findViewById(R.id.img_profile_photo);card=findViewById(R.id.profile_student_card);cardError=findViewById(R.id.profile_card_error);llHistoryContainer=findViewById(R.id.ll_profile_history_container);
        findViewById(R.id.btn_profile_classes).setOnClickListener(v->showClasses(false));findViewById(R.id.btn_profile_add_class).setOnClickListener(v->showClasses(true));
        findViewById(R.id.btn_profile_edit).setOnClickListener(v->startActivity(new Intent(this,StudentFormActivity.class).putExtra(StudentFormActivity.EXTRA_STUDENT_ID,studentId)));
        findViewById(R.id.btn_profile_delete).setOnClickListener(v->StudentDeletion.confirm(this,AttendanceRepository.getInstance().getStudentById(studentId),this::finish));
        if(!AttendanceRepository.getInstance().isAdmin()){findViewById(R.id.btn_profile_delete).setVisibility(View.GONE);findViewById(R.id.btn_profile_edit).setVisibility(View.GONE);findViewById(R.id.btn_profile_add_class).setVisibility(View.GONE);}
        View.OnClickListener openCard=v->startActivity(new Intent(this,StudentCardActivity.class).putExtra(EXTRA_STUDENT_ID,studentId));card.setOnClickListener(openCard);findViewById(R.id.btn_profile_card_export).setOnClickListener(openCard);
    }
    @Override protected void onDataResume(){loadStudentData();}
    private void showClasses(boolean add){
        AttendanceRepository repo=AttendanceRepository.getInstance();List<ClassRoom> enrolled=repo.getClassesForStudent(studentId),choices=new ArrayList<>();
        if(add){for(ClassRoom c:repo.getAllClasses()){boolean present=false;for(ClassRoom e:enrolled)if(e.getId().equals(c.getId()))present=true;if(!present)choices.add(c);}}else choices.addAll(enrolled);
        if(choices.isEmpty()){new MaterialAlertDialogBuilder(this).setTitle(add?"Thêm vào lớp":"Lớp đang học").setMessage(add?"Chưa có lớp khác để thêm. Hãy tạo lớp trong Quản lý lớp học.":"Sinh viên chưa thuộc lớp học nào.").setPositiveButton("Đóng",null).show();return;}
        String[] labels=new String[choices.size()];for(int i=0;i<labels.length;i++)labels[i]=choices.get(i).getName()+" • "+choices.get(i).getCode();
        new MaterialAlertDialogBuilder(this).setTitle(add?"Thêm vào lớp đang quản lý":"Các lớp đang học").setItems(labels,(d,n)->{
            ClassRoom selected=choices.get(n);
            if(add)Tasks.run(this,null,()->{repo.addExistingToClass(studentId,selected.getId());return true;},ok->{loadStudentData();Toast.makeText(this,"Đã thêm vào lớp "+selected.getName(),Toast.LENGTH_SHORT).show();});
            else startActivity(new Intent(this,ClassDetailActivity.class).putExtra(ClassDetailActivity.EXTRA_CLASS_ID,selected.getId()));
        }).show();
    }
    private String value(String s){return s==null||s.isEmpty()?"Chưa cập nhật":s;}
    private void loadStudentData(){
        AttendanceRepository repo=AttendanceRepository.getInstance();Student student=repo.getStudentById(studentId);if(student==null){finish();return;}
        tvName.setText(student.getFullName());tvMssv.setText("MSSV: "+student.getMssv());tvBirth.setText("Ngày sinh: "+value(student.getBirthDate()));tvCourse.setText("Niên khóa: "+value(student.getCourse()));tvGender.setText("Giới tính: "+value(student.getGender()));tvPhone.setText("Số điện thoại: "+value(student.getPhone()));StudentPhotos.show(avatar,student.getPhotoPath());
        List<ClassRoom> enrolled=repo.getClassesForStudent(studentId);StringBuilder label=new StringBuilder();for(ClassRoom c:enrolled){if(label.length()>0)label.append("\n");label.append(c.getName());}tvClass.setText(label.length()==0?"Chưa thuộc lớp nào":"Lớp đang học: "+label);
        ClassRoom main=repo.getClassById(student.getPrimaryClassId());
        try{card.bind(student,main==null?null:main.getName());card.setVisibility(View.VISIBLE);cardError.setVisibility(View.GONE);}catch(IllegalArgumentException e){card.setVisibility(View.GONE);cardError.setVisibility(View.VISIBLE);cardError.setText("Không thể hiển thị thẻ. Vui lòng thử lại.");}
        llHistoryContainer.removeAllViews();
        List<AttendanceSession> sessions = AttendanceRepository.getInstance().getAllSessions();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (AttendanceSession session : sessions) {
            AttendanceRecord record = AttendanceRepository.getInstance().getRecordForStudent(session.getId(), studentId);
            if(record==null)continue;
            View row = inflater.inflate(R.layout.item_attendance_record, llHistoryContainer, false);

            TextView rName = row.findViewById(R.id.tv_record_name);
            TextView rMssv = row.findViewById(R.id.tv_record_mssv);
            TextView rSource = row.findViewById(R.id.tv_record_source);
            TextView rStatus = row.findViewById(R.id.tv_record_status_badge);
            TextView rTime = row.findViewById(R.id.tv_record_time);
            View rAvatar = row.findViewById(R.id.fl_record_avatar);
            rAvatar.setVisibility(View.GONE);

            rName.setText(session.getTitle());
            rMssv.setText(AttendanceRepository.getInstance().sessionClassLabel(session)+"\n"+session.getDate() + " • " + session.getStartTime());

            if (record != null) {
                switch (record.getStatus()) {
                    case PRESENT:
                        rStatus.setText(R.string.result_filter_present);
                        rStatus.setBackgroundResource(R.drawable.bg_badge_present);
                        rStatus.setTextColor(ContextCompat.getColor(this, R.color.on_status_present_container));
                        rTime.setText(record.getCheckInTime() != null ? record.getCheckInTime() : "");
                        rSource.setText("• " + record.getSourceDisplayName());
                        break;
                    case LATE:
                        rStatus.setText(R.string.result_filter_late);
                        rStatus.setBackgroundResource(R.drawable.bg_badge_late);
                        rStatus.setTextColor(ContextCompat.getColor(this, R.color.on_status_late_container));
                        rTime.setText(record.getCheckInTime() != null ? record.getCheckInTime() : "");
                        rSource.setText("• " + record.getSourceDisplayName());
                        break;
                    case ABSENT:
                        rStatus.setText(R.string.result_filter_absent);
                        rStatus.setBackgroundResource(R.drawable.bg_badge_absent);
                        rStatus.setTextColor(ContextCompat.getColor(this, R.color.on_status_absent_container));
                        rTime.setText("—");
                        rSource.setText("• Vắng mặt");
                        break;
                    case PENDING:
                        rStatus.setText(R.string.result_filter_unchecked);
                        rStatus.setBackgroundResource(R.drawable.bg_badge_unchecked);
                        rStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                        rTime.setText("Chưa điểm danh");
                        rSource.setText("");
                        break;
                }
            } else {
                rStatus.setText("Chưa có");
                rStatus.setBackgroundResource(R.drawable.bg_badge_unchecked);
                rTime.setText("");
                rSource.setText("");
            }

            llHistoryContainer.addView(row);
        }
    }
}


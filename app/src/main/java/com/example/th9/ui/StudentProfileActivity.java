package com.example.th9.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.th9.R;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceRecord;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.ClassRoom;
import com.example.th9.model.Student;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class StudentProfileActivity extends com.example.th9.ui.DataActivity {

    public static final String EXTRA_STUDENT_ID = "extra_student_id";
    public static final String EXTRA_CLASS_ID = "extra_class_id";

    private String studentId;
    private String classId;

    private Toolbar toolbar;
    private FrameLayout flAvatar;
    private TextView tvInitial, tvName, tvMssv, tvClass, tvBarcodeCode, tvBarcodeBadge;
    private MaterialButton btnEdit, btnLinkCard;
    private LinearLayout llHistoryContainer;

    @Override
    protected void onReady(Bundle savedInstanceState) {

        setContentView(R.layout.activity_student_profile);

        studentId = getIntent().getStringExtra(EXTRA_STUDENT_ID);
        classId = getIntent().getStringExtra(EXTRA_CLASS_ID);



        initViews();
        setupToolbar();
        setupListeners();
    }

    @Override
    protected void onDataResume() {

        loadStudentData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_student_profile);
        flAvatar = findViewById(R.id.fl_profile_avatar);
        tvInitial = findViewById(R.id.tv_profile_initial);
        tvName = findViewById(R.id.tv_profile_name);
        tvMssv = findViewById(R.id.tv_profile_mssv);
        tvClass = findViewById(R.id.tv_profile_class);
        tvBarcodeCode = findViewById(R.id.tv_profile_barcode_code);
        tvBarcodeBadge = findViewById(R.id.tv_profile_barcode_status_badge);
        btnEdit = findViewById(R.id.btn_profile_edit);
        btnLinkCard = findViewById(R.id.btn_profile_link_card);
        llHistoryContainer = findViewById(R.id.ll_profile_history_container);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.student_profile_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, StudentFormActivity.class);
            intent.putExtra(StudentFormActivity.EXTRA_STUDENT_ID, studentId);
            intent.putExtra(StudentFormActivity.EXTRA_CLASS_ID, classId);
            startActivity(intent);
        });

        btnLinkCard.setOnClickListener(v -> {
            Intent intent = new Intent(this, StudentFormActivity.class);
            intent.putExtra(StudentFormActivity.EXTRA_CLASS_ID, classId);
            intent.putExtra(StudentFormActivity.EXTRA_STUDENT_ID, studentId);
            startActivity(intent);
        });
    }

    private void loadStudentData() {
        Student s = AttendanceRepository.getInstance().getStudentById(studentId);
        if (s == null) return;

        tvName.setText(s.getFullName());
        tvMssv.setText("MSSV: " + s.getMssv());
        tvInitial.setText(s.getInitial());

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(s.getAvatarBgColor() != 0 ? s.getAvatarBgColor() : ContextCompat.getColor(this, R.color.primary));
        flAvatar.setBackground(bg);

        StringBuilder classes=new StringBuilder();
        for(ClassRoom cr:AttendanceRepository.getInstance().getClassesForStudent(studentId)) {
            if(classes.length()>0)classes.append("\n");classes.append(cr.getName()).append(" • ").append(cr.getCode());
        }
        tvClass.setText(classes.length()==0?"Chưa thuộc lớp nào":classes.toString());

        if (s.hasCardBarcode()) {
            tvBarcodeCode.setText("* " + s.getCardBarcode() + " *");
            tvBarcodeBadge.setText("Thẻ hợp lệ • Sẵn sàng quét");
            tvBarcodeBadge.setBackgroundResource(R.drawable.bg_badge_present);
            tvBarcodeBadge.setTextColor(ContextCompat.getColor(this, R.color.on_status_present_container));
            btnLinkCard.setText("Đổi mã thẻ");
        } else {
            tvBarcodeCode.setText("[ Chưa gắn mã vạch ]");
            tvBarcodeBadge.setText("Chưa liên kết thẻ sinh viên");
            tvBarcodeBadge.setBackgroundResource(R.drawable.bg_badge_unchecked);
            tvBarcodeBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            btnLinkCard.setText(R.string.context_link_card);
        }

        // Load sample attendance history
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


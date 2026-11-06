package com.example.th9.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.th9.R;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.ClassRoom;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SessionFormActivity extends com.example.th9.ui.DataActivity {

    public static final String EXTRA_PRESELECTED_CLASS_ID = "extra_preselected_class_id";
    public static final String EXTRA_SESSION_ID = "edit_session_id";
    private String editSessionId;
    private String creationId;

    private String preselectedClassId;
    private String selectedClassId;

    private Toolbar toolbar;
    private TextInputLayout tilClass, tilTitle, tilDate, tilTime, tilLateTime;
    private AutoCompleteTextView actvClass;
    private TextInputEditText etTitle, etDate, etTime, etLateTime;
    private MaterialSwitch switchLate;
    private LinearLayout llLateContainer;
    private TextView tvExpectedCount;
    private MaterialButton btnSubmit, btnPreview, btnCancel;

    private final Calendar calendar = Calendar.getInstance();
    private List<ClassRoom> classesList = new ArrayList<>();

    @Override
    protected void onReady(Bundle savedInstanceState) {

        setContentView(R.layout.activity_session_form);

        preselectedClassId = getIntent().getStringExtra(EXTRA_PRESELECTED_CLASS_ID);
        creationId=savedInstanceState==null?AttendanceRepository.id():savedInstanceState.getString("creation_id",AttendanceRepository.id());

        initViews();
        setupToolbar();
        setupClassesDropdown();
        setupDateTimePickers();
        setupLateToggle();
        setupListeners();
        editSessionId=getIntent().getStringExtra(EXTRA_SESSION_ID);
        if(editSessionId!=null){
            AttendanceSession s=AttendanceRepository.getInstance().getSessionById(editSessionId);
            if(s==null||s.getStatus()!=AttendanceSession.SessionStatus.DRAFT){finish();return;}
            selectedClassId=s.getClassId();
            calendar.setTimeInMillis(s.getScheduledAt());
            updateExpectedCount(AttendanceRepository.getInstance().getClassById(selectedClassId));
            for(int i=0;i<classesList.size();i++)if(classesList.get(i).getId().equals(selectedClassId))
                actvClass.setText(classesList.get(i).getName()+" ("+classesList.get(i).getCode()+")",false);
            etTitle.setText(s.getTitle());etDate.setText(s.getDate());etTime.setText(s.getStartTime());
            switchLate.setChecked(s.isEnableLateThreshold());etLateTime.setText(s.getLateThresholdTime());btnSubmit.setText("Lưu thay đổi");
        }
        if(savedInstanceState!=null){selectedClassId=savedInstanceState.getString("selectedClassId",selectedClassId);updateExpectedCount(AttendanceRepository.getInstance().getClassById(selectedClassId));}
        if(classesList.isEmpty()){
            btnSubmit.setEnabled(false);tvExpectedCount.setText("Chưa có lớp. Hãy tạo lớp trước.");
            btnPreview.setVisibility(View.VISIBLE);btnPreview.setText("Tạo lớp");
            btnPreview.setOnClickListener(v->{startActivity(new Intent(this,ClassFormActivity.class));finish();});
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_session_form);
        tilClass = findViewById(R.id.til_session_class);
        tilTitle = findViewById(R.id.til_session_title);
        tilDate = findViewById(R.id.til_session_date);
        tilTime = findViewById(R.id.til_session_time);
        tilLateTime = findViewById(R.id.til_session_late_time);

        actvClass = findViewById(R.id.actv_session_class);
        etTitle = findViewById(R.id.et_session_title);
        etDate = findViewById(R.id.et_session_date);
        etTime = findViewById(R.id.et_session_time);
        etLateTime = findViewById(R.id.et_session_late_time);

        switchLate = findViewById(R.id.switch_session_late);
        llLateContainer = findViewById(R.id.ll_late_threshold_container);
        tvExpectedCount = findViewById(R.id.tv_session_expected_count);

        btnSubmit = findViewById(R.id.btn_session_submit);
        btnPreview = findViewById(R.id.btn_session_preview);
        btnCancel = findViewById(R.id.btn_session_cancel);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.session_form_create_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClassesDropdown() {
        classesList = AttendanceRepository.getInstance().getAllClasses();
        List<String> displayNames = new ArrayList<>();
        int selectedIndex = 0;

        for (int i = 0; i < classesList.size(); i++) {
            ClassRoom c = classesList.get(i);
            displayNames.add(c.getName() + " (" + c.getCode() + ")");
            if (preselectedClassId != null && preselectedClassId.equals(c.getId())) {
                selectedIndex = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, displayNames);
        actvClass.setAdapter(adapter);

        if (!classesList.isEmpty()) {
            actvClass.setText(displayNames.get(selectedIndex), false);
            selectedClassId = classesList.get(selectedIndex).getId();
            updateExpectedCount(classesList.get(selectedIndex));
        }

        actvClass.setOnItemClickListener((parent, view, position, id) -> {
            ClassRoom selected = classesList.get(position);
            selectedClassId = selected.getId();
            updateExpectedCount(selected);
            tilClass.setError(null);
        });
    }

    private void updateExpectedCount(ClassRoom c) {
        if (c != null) {
            tvExpectedCount.setText("Sĩ số dự kiến: " + c.getStudentCount() + " sinh viên");
            if (etTitle.getText() == null || etTitle.getText().toString().isEmpty()) {
                etTitle.setText("Điểm danh thường kỳ");
            }
        }
    }

    private void setupDateTimePickers() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        etDate.setText(dateFormat.format(calendar.getTime()));
        etTime.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(calendar.getTime()));
        Calendar lateDefault=(Calendar)calendar.clone();lateDefault.add(Calendar.MINUTE,15);
        if(lateDefault.get(Calendar.DAY_OF_YEAR)!=calendar.get(Calendar.DAY_OF_YEAR)){
            lateDefault=(Calendar)calendar.clone();lateDefault.set(Calendar.HOUR_OF_DAY,23);lateDefault.set(Calendar.MINUTE,59);
        }
        etLateTime.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(lateDefault.getTime()));

        etDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                etDate.setText(dateFormat.format(calendar.getTime()));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        etTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                String timeStr = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                etTime.setText(timeStr);
            }, pickerPart(etTime,0), pickerPart(etTime,1), true).show();
        });

        etLateTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                String timeStr = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                etLateTime.setText(timeStr);
            }, pickerPart(etLateTime,0), pickerPart(etLateTime,1), true).show();
        });
    }
    private int pickerPart(TextInputEditText field,int part){
        try{return Integer.parseInt(field.getText().toString().split(":")[part]);}
        catch(RuntimeException e){return part==0?calendar.get(Calendar.HOUR_OF_DAY):calendar.get(Calendar.MINUTE);}
    }

    private void setupLateToggle() {
        switchLate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            llLateContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
    }

    private void setupListeners() {
        btnSubmit.setOnClickListener(v -> validateAndCreateSession());
        btnPreview.setVisibility(View.GONE);

        btnCancel.setOnClickListener(v -> finish());
    }

    private void validateAndCreateSession() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String date = etDate.getText() != null ? etDate.getText().toString().trim() : "";
        String time = etTime.getText() != null ? etTime.getText().toString().trim() : "";
        boolean enableLate = switchLate.isChecked();
        String lateTime = etLateTime.getText() != null ? etLateTime.getText().toString().trim() : "";

        boolean hasError = false;
        if (selectedClassId == null) {
            tilClass.setError("Vui lòng chọn lớp học");
            hasError = true;
        } else {
            tilClass.setError(null);
        }

        if (title.isEmpty()) {
            tilTitle.setError("Vui lòng nhập tên buổi");
            hasError = true;
        } else {
            tilTitle.setError(null);
        }

        if (hasError) return;

        ClassRoom cr = AttendanceRepository.getInstance().getClassById(selectedClassId);
        int expected = cr != null ? cr.getStudentCount() : 0;

        createAndOpen(title,date,time,enableLate,lateTime,expected,AttendanceSession.SessionStatus.DRAFT);
    }

    private void createAndOpen(String title, String date, String time, boolean enableLate, String lateTime, int expected, AttendanceSession.SessionStatus status) {
        AttendanceSession session = new AttendanceSession(
                null, selectedClassId, title, date, time, enableLate, lateTime,
                status, null, expected
        );
        session.setId(editSessionId==null?creationId:editSessionId);
        com.example.th9.data.Tasks.run(this,btnSubmit,()->{
            if(editSessionId==null)AttendanceRepository.getInstance().addSession(session);
            else AttendanceRepository.getInstance().updateSession(session);
            return session;
        },saved->{
            Toast.makeText(this,"Đã lưu buổi chưa mở",Toast.LENGTH_SHORT).show();
            Intent intent=new Intent(this,SessionDetailActivity.class);
            intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID,saved.getId());startActivity(intent);finish();
        });
    }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state){
        state.putString("selectedClassId",selectedClassId);super.onSaveInstanceState(state);
        state.putString("creation_id",creationId);
    }
}


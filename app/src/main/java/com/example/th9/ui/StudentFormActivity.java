package com.example.th9.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.th9.R;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.ClassRoom;
import com.example.th9.model.Student;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class StudentFormActivity extends com.example.th9.ui.DataActivity {

    public static final String EXTRA_STUDENT_ID = "extra_student_id";
    public static final String EXTRA_CLASS_ID = "extra_class_id";
    private static final int REQUEST_CODE_LINK_CARD = 101;

    private String studentId;
    private String creationId;
    private String classId;
    private boolean isEditMode = false;
    private boolean isFormDirty = false;

    private Toolbar toolbar;
    private TextInputLayout tilName, tilMssv, tilClass;
    private TextInputEditText etName, etMssv, etClass;
    private TextView tvBarcodeStatus;
    private MaterialButton btnScanBarcode, btnSubmit, btnCancel;

    private String currentBarcode = null;
    private String originalName = "";
    private String originalMssv = "";
    private String originalBarcode = "";

    @Override
    protected void onReady(Bundle savedInstanceState) {

        setContentView(R.layout.activity_student_form);

        studentId = getIntent().getStringExtra(EXTRA_STUDENT_ID);
        creationId=savedInstanceState==null?AttendanceRepository.id():savedInstanceState.getString("creation_id",AttendanceRepository.id());
        classId = getIntent().getStringExtra(EXTRA_CLASS_ID);
        isEditMode = (studentId != null);

        initViews();
        setupToolbar();
        setupData();
        setupListeners();
        if(savedInstanceState!=null){currentBarcode=savedInstanceState.getString("barcode");updateBarcodeStatusUi();}

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                checkDirtyAndExit();
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_student_form);
        tilName = findViewById(R.id.til_student_name);
        tilMssv = findViewById(R.id.til_student_mssv);
        tilClass = findViewById(R.id.til_student_class);
        etName = findViewById(R.id.et_student_name);
        etMssv = findViewById(R.id.et_student_mssv);
        etClass = findViewById(R.id.et_student_class);
        tvBarcodeStatus = findViewById(R.id.tv_student_form_barcode_status);
        btnScanBarcode = findViewById(R.id.btn_student_scan_barcode);
        btnSubmit = findViewById(R.id.btn_student_submit);
        btnCancel = findViewById(R.id.btn_student_cancel);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? R.string.student_form_edit_title : R.string.student_form_add_title);
        }
        toolbar.setNavigationOnClickListener(v -> checkDirtyAndExit());
    }

    private void setupData() {
        ClassRoom c = AttendanceRepository.getInstance().getClassById(classId);
        if (c != null) {
            etClass.setText(c.getName() + " • " + c.getCode());
        }

        if (isEditMode) {
            Student s = AttendanceRepository.getInstance().getStudentById(studentId);
            if (s != null) {
                originalName = s.getFullName();
                originalMssv = s.getMssv();
                currentBarcode = s.getCardBarcode();
                originalBarcode = currentBarcode != null ? currentBarcode : "";

                etName.setText(originalName);
                etMssv.setText(originalMssv);
            }
            btnSubmit.setText("Lưu thay đổi");
        } else {
            btnSubmit.setText("Thêm sinh viên");
        }

        updateBarcodeStatusUi();
    }

    private void updateBarcodeStatusUi() {
        if (currentBarcode != null && !currentBarcode.trim().isEmpty()) {
            tvBarcodeStatus.setText("Đã gắn mã thẻ: " + currentBarcode);
            tvBarcodeStatus.setBackgroundResource(R.drawable.bg_badge_present);
            tvBarcodeStatus.setTextColor(ContextCompat.getColor(this, R.color.on_status_present_container));
            tvBarcodeStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_barcode, 0, 0, 0);
            btnScanBarcode.setText(R.string.student_btn_change_barcode);
        } else {
            tvBarcodeStatus.setText(R.string.student_card_status_unlinked);
            tvBarcodeStatus.setBackgroundResource(R.drawable.bg_badge_unchecked);
            tvBarcodeStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvBarcodeStatus.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            btnScanBarcode.setText(R.string.student_btn_scan_barcode);
        }
    }

    private void setupListeners() {
        TextWatcher dirtyWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkIfDirty();
                tilName.setError(null);
                tilMssv.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        etName.addTextChangedListener(dirtyWatcher);
        etMssv.addTextChangedListener(dirtyWatcher);

        btnScanBarcode.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_LINK_CARD);
            intent.putExtra(ScanActivity.EXTRA_STUDENT_ID, studentId);
            startActivityForResult(intent, REQUEST_CODE_LINK_CARD);
        });

        btnSubmit.setOnClickListener(v -> validateAndSubmit());
        btnCancel.setOnClickListener(v -> checkDirtyAndExit());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_LINK_CARD && resultCode == RESULT_OK && data != null) {
            String scannedCode = data.getStringExtra(ScanActivity.EXTRA_SCANNED_BARCODE);
            if (scannedCode != null) {
                new MaterialAlertDialogBuilder(this).setTitle("Xác nhận mã thẻ")
                    .setMessage(scannedCode+"\nĐịnh dạng: "+data.getStringExtra("barcode_format")+"\nMã sẽ được ghi khi bấm Lưu.")
                    .setPositiveButton("Dùng mã này",(d,w)->{currentBarcode=scannedCode;updateBarcodeStatusUi();checkIfDirty();})
                    .setNegativeButton("Hủy",null).show();
            }
        }
    }

    private void checkIfDirty() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String mssv = etMssv.getText() != null ? etMssv.getText().toString().trim() : "";
        String barcode = currentBarcode != null ? currentBarcode : "";

        if (isEditMode) {
            isFormDirty = !name.equals(originalName) || !mssv.equals(originalMssv) || !barcode.equals(originalBarcode);
        } else {
            isFormDirty = !name.isEmpty() || !mssv.isEmpty() || !barcode.isEmpty();
        }
    }

    private void validateAndSubmit() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String mssv = etMssv.getText() != null ? etMssv.getText().toString().trim() : "";

        boolean hasError = false;
        if (name.isEmpty()) {
            tilName.setError(getString(R.string.student_form_name_hint));
            hasError = true;
        } else {
            tilName.setError(null);
        }

        if (mssv.isEmpty()) {
            tilMssv.setError(getString(R.string.student_form_mssv_hint));
            hasError = true;
        } else {
            tilMssv.setError(null);
        }

        if (hasError) return;

        if(!isEditMode){
            Student found=AttendanceRepository.getInstance().findByMssv(mssv);
            if(found!=null){
                new MaterialAlertDialogBuilder(this).setTitle("MSSV đã có")
                    .setMessage(found.getFullName()+"\nMSSV: "+found.getMssv()+"\nMã thẻ: "+(found.getCardBarcode()==null?"Chưa gắn":found.getCardBarcode()))
                    .setPositiveButton("Thêm vào lớp này",(d,w)->com.example.th9.data.Tasks.run(this,btnSubmit,()->{
                        AttendanceRepository.getInstance().addExistingToClass(found.getId(),classId);return true;
                    },ok->{Toast.makeText(this,"Đã thêm vào lớp",Toast.LENGTH_SHORT).show();finish();}))
                    .setNegativeButton("Hủy",null).show();return;
            }
        }
        Student student=isEditMode?AttendanceRepository.getInstance().getStudentById(studentId):new Student(creationId,mssv,name,currentBarcode,"","",0);
        if(student==null){Toast.makeText(this,"Không tìm thấy sinh viên",Toast.LENGTH_LONG).show();return;}
        student.setMssv(mssv);student.setFullName(name);student.setCardBarcode(currentBarcode);
        com.example.th9.data.Tasks.run(this,btnSubmit,()->{
            if(isEditMode)AttendanceRepository.getInstance().updateStudent(student);
            else AttendanceRepository.getInstance().addStudentToClass(student,classId);
            return true;
        },ok->{Toast.makeText(this,"Đã lưu sinh viên",Toast.LENGTH_SHORT).show();finish();});
    }

    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state){
        state.putString("barcode",currentBarcode);super.onSaveInstanceState(state);
        state.putString("creation_id",creationId);
    }

    private void checkDirtyAndExit() {
        checkIfDirty();
        if (isFormDirty) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Rời form có thay đổi?")
                    .setMessage("Bạn có các thông tin chưa được lưu. Bạn có chắc chắn muốn thoát?")
                    .setPositiveButton("Rời khỏi", (dialog, which) -> finish())
                    .setNegativeButton("Ở lại", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            finish();
        }
    }
}


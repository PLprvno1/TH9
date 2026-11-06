package com.example.th9.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.th9.R;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.ClassRoom;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ClassFormActivity extends com.example.th9.ui.DataActivity {

    public static final String EXTRA_CLASS_ID = "extra_class_id";

    private String classId;
    private String creationId;
    private boolean isEditMode = false;
    private boolean isFormDirty = false;

    private Toolbar toolbar;
    private TextInputLayout tilName, tilCode, tilNote;
    private TextInputEditText etName, etCode, etNote;
    private MaterialButton btnSubmit, btnCancel;

    private String originalName = "";
    private String originalCode = "";
    private String originalNote = "";

    @Override
    protected void onReady(Bundle savedInstanceState) {

        setContentView(R.layout.activity_class_form);

        classId = getIntent().getStringExtra(EXTRA_CLASS_ID);
        creationId=savedInstanceState==null?AttendanceRepository.id():savedInstanceState.getString("creation_id",AttendanceRepository.id());
        isEditMode = (classId != null);

        initViews();
        setupToolbar();
        setupData();
        setupListeners();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                checkDirtyAndExit();
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_class_form);
        tilName = findViewById(R.id.til_class_name);
        tilCode = findViewById(R.id.til_class_code);
        tilNote = findViewById(R.id.til_class_note);
        etName = findViewById(R.id.et_class_name);
        etCode = findViewById(R.id.et_class_code);
        etNote = findViewById(R.id.et_class_note);
        btnSubmit = findViewById(R.id.btn_class_submit);
        btnCancel = findViewById(R.id.btn_class_cancel);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? R.string.class_form_edit_title : R.string.class_form_create_title);
        }
        toolbar.setNavigationOnClickListener(v -> checkDirtyAndExit());
    }

    private void setupData() {
        if (isEditMode) {
            ClassRoom c = AttendanceRepository.getInstance().getClassById(classId);
            if (c != null) {
                originalName = c.getName();
                originalCode = c.getCode();
                originalNote = c.getNote() != null ? c.getNote() : "";

                etName.setText(originalName);
                etCode.setText(originalCode);
                etNote.setText(originalNote);
            }
            btnSubmit.setText("Lưu thay đổi");
        } else {
            btnSubmit.setText("Tạo lớp học");
        }
    }

    private void setupListeners() {
        TextWatcher dirtyWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkIfDirty();
                tilName.setError(null);
                tilCode.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        etName.addTextChangedListener(dirtyWatcher);
        etCode.addTextChangedListener(dirtyWatcher);
        etNote.addTextChangedListener(dirtyWatcher);

        btnSubmit.setOnClickListener(v -> validateAndSubmit());
        btnCancel.setOnClickListener(v -> checkDirtyAndExit());
    }

    private void checkIfDirty() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String code = etCode.getText() != null ? etCode.getText().toString().trim() : "";
        String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

        if (isEditMode) {
            isFormDirty = !name.equals(originalName) || !code.equals(originalCode) || !note.equals(originalNote);
        } else {
            isFormDirty = !name.isEmpty() || !code.isEmpty() || !note.isEmpty();
        }
    }

    private void validateAndSubmit() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String code = etCode.getText() != null ? etCode.getText().toString().trim() : "";
        String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

        boolean hasError = false;
        if (name.isEmpty()) {
            tilName.setError(getString(R.string.class_form_error_name_required));
            hasError = true;
        } else {
            tilName.setError(null);
        }

        if (hasError) return;
        com.example.th9.data.Tasks.run(this,btnSubmit,()->{
            ClassRoom c=new ClassRoom(isEditMode?classId:creationId,name,code,note);
            if(isEditMode)AttendanceRepository.getInstance().updateClass(c);
            else AttendanceRepository.getInstance().addClass(c);
            return c;
        },c->{
            Toast.makeText(this,"Đã lưu lớp học",Toast.LENGTH_SHORT).show();
            if(!isEditMode){
                android.content.Intent intent=new android.content.Intent(this,ClassDetailActivity.class);
                intent.putExtra(ClassDetailActivity.EXTRA_CLASS_ID,c.getId());startActivity(intent);
            }
            finish();
        });
    }

    private void checkDirtyAndExit() {
        checkIfDirty();
        if (isFormDirty) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Rời form có thay đổi?")
                    .setMessage("Bạn có các thay đổi chưa được lưu. Bạn có chắc chắn muốn thoát?")
                    .setPositiveButton("Rời khỏi", (dialog, which) -> finish())
                    .setNegativeButton("Ở lại", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            finish();
        }
    }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state){state.putString("creation_id",creationId);super.onSaveInstanceState(state);}
}


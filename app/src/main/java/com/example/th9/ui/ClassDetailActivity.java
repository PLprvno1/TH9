package com.example.th9.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th9.R;
import com.example.th9.adapter.StudentAdapter;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.ClassRoom;
import com.example.th9.model.Student;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ClassDetailActivity extends com.example.th9.ui.DataActivity {

    public static final String EXTRA_CLASS_ID = "extra_class_id";

    private String classId;
    private ClassRoom currentClass;

    private Toolbar toolbar;
    private TextView tvName, tvCode, tvNote, tvStudentCount;
    private MaterialButton btnCreateSession, btnAddStudent, btnEmptyAdd, btnEmptyImport;
    private EditText etSearch;
    private LinearLayout llEmpty;
    private RecyclerView rvStudents;
    private StudentAdapter adapter;

    private boolean sortByName = true;

    @Override
    protected void onReady(Bundle savedInstanceState) {

        setContentView(R.layout.activity_class_detail);

        classId = getIntent().getStringExtra(EXTRA_CLASS_ID);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();
    }

    @Override
    protected void onDataResume() {

        loadClassData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_class_detail);
        tvName = findViewById(R.id.tv_detail_class_name);
        tvCode = findViewById(R.id.tv_detail_class_code);
        tvNote = findViewById(R.id.tv_detail_class_note);
        tvStudentCount = findViewById(R.id.tv_detail_student_count);
        btnCreateSession = findViewById(R.id.btn_detail_create_session);
        btnAddStudent = findViewById(R.id.btn_detail_add_student);
        btnEmptyAdd = findViewById(R.id.btn_empty_add_student);
        btnEmptyImport = findViewById(R.id.btn_empty_import_csv);
        etSearch = findViewById(R.id.et_student_search);
        llEmpty = findViewById(R.id.ll_students_empty);
        rvStudents = findViewById(R.id.rv_class_students);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chi tiết lớp học");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new StudentAdapter(this, (student, position) -> {
            // Tap -> Open Student Profile
            Intent intent = new Intent(ClassDetailActivity.this, StudentProfileActivity.class);
            intent.putExtra(StudentProfileActivity.EXTRA_STUDENT_ID, student.getId());
            intent.putExtra(StudentProfileActivity.EXTRA_CLASS_ID, classId);
            startActivity(intent);
        }, (student, position, anchorView) -> {
            // Long click triggers context menu automatically
        });

        rvStudents.setLayoutManager(new LinearLayoutManager(this));
        rvStudents.setAdapter(adapter);
        registerForContextMenu(rvStudents);
    }

    private void setupListeners() {
        btnCreateSession.setOnClickListener(v -> {
            Intent intent = new Intent(ClassDetailActivity.this, SessionFormActivity.class);
            intent.putExtra(SessionFormActivity.EXTRA_PRESELECTED_CLASS_ID, classId);
            startActivity(intent);
        });

        View.OnClickListener addStudentListener = v -> {
            Intent intent = new Intent(ClassDetailActivity.this, StudentFormActivity.class);
            intent.putExtra(StudentFormActivity.EXTRA_CLASS_ID, classId);
            startActivity(intent);
        };
        btnAddStudent.setOnClickListener(addStudentListener);
        btnEmptyAdd.setOnClickListener(addStudentListener);

        View.OnClickListener importCsvListener = v -> {
            Intent intent = new Intent(ClassDetailActivity.this, CsvImportPreviewActivity.class);
            intent.putExtra(CsvImportPreviewActivity.EXTRA_CLASS_ID, classId);
            startActivity(intent);
        };
        btnEmptyImport.setOnClickListener(importCsvListener);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndSortStudents(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadClassData() {
        currentClass = AttendanceRepository.getInstance().getClassById(classId);
        if(currentClass==null){finish();return;}
        if (currentClass != null) {
            tvName.setText(currentClass.getName());
            tvCode.setText(currentClass.getCode());
            tvNote.setText(currentClass.getNote());
            tvStudentCount.setText(currentClass.getStudentCount() + " " + getString(R.string.class_detail_students_count));
        }

        filterAndSortStudents(etSearch != null ? etSearch.getText().toString() : "");
    }

    private void filterAndSortStudents(String query) {
        List<Student> list = AttendanceRepository.getInstance().getStudentsForClass(classId);
        if (list == null || list.isEmpty()) {
            findViewById(R.id.tv_student_search_empty).setVisibility(View.GONE);
            llEmpty.setVisibility(View.VISIBLE);
            rvStudents.setVisibility(View.GONE);
            return;
        }

        llEmpty.setVisibility(View.GONE);
        rvStudents.setVisibility(View.VISIBLE);

        List<Student> filtered = new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase() : "";

        for (Student s : list) {
            if (q.isEmpty()
                    || s.getFullName().toLowerCase().contains(q)
                    || s.getMssv().toLowerCase().contains(q)
                    || (s.getCardBarcode() != null && s.getCardBarcode().toLowerCase().contains(q))) {
                filtered.add(s);
            }
        }

        // Apply Sorting
        if (sortByName) {
            Collections.sort(filtered, (a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()));
        } else {
            Collections.sort(filtered, (a, b) -> a.getMssv().compareToIgnoreCase(b.getMssv()));
        }

        adapter.setStudents(filtered);
        findViewById(R.id.tv_student_search_empty).setVisibility(filtered.isEmpty()?View.VISIBLE:View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu){
        getMenuInflater().inflate(R.menu.menu_class_detail,menu);
        menu.add(0,7001,10,"Sửa lớp");menu.add(0,7002,11,"Xóa lớp");return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if(id==7001){Intent i=new Intent(this,ClassFormActivity.class);i.putExtra(ClassFormActivity.EXTRA_CLASS_ID,classId);startActivity(i);return true;}
        if(id==7002){new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Xóa lớp?")
            .setMessage("Chỉ xóa được lớp chưa có buổi điểm danh.").setPositiveButton("Xóa",(d,w)->
                com.example.th9.data.Tasks.run(this,null,()->{AttendanceRepository.getInstance().deleteClass(classId);return true;},ok->finish()))
            .setNegativeButton("Hủy",null).show();return true;}
        if (id == R.id.action_sort_name) {
            sortByName = true;
            Toast.makeText(this, "Đã sắp xếp danh sách theo họ tên (A - Z)", Toast.LENGTH_SHORT).show();
            filterAndSortStudents(etSearch.getText().toString());
            return true;
        } else if (id == R.id.action_sort_mssv) {
            sortByName = false;
            Toast.makeText(this, "Đã sắp xếp danh sách theo MSSV", Toast.LENGTH_SHORT).show();
            filterAndSortStudents(etSearch.getText().toString());
            return true;
        } else if (id == R.id.action_import_csv) {
            Intent intent = new Intent(this, CsvImportPreviewActivity.class);
            intent.putExtra(CsvImportPreviewActivity.EXTRA_CLASS_ID, classId);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        int position = adapter.getSelectedPosition();
        Student student = adapter.getStudentAt(position);
        if (student == null) {
            return super.onContextItemSelected(item);
        }

        int itemId = item.getItemId();
        if (itemId == R.id.context_view_profile) {
            Intent intent = new Intent(this, StudentProfileActivity.class);
            intent.putExtra(StudentProfileActivity.EXTRA_STUDENT_ID, student.getId());
            intent.putExtra(StudentProfileActivity.EXTRA_CLASS_ID, classId);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.context_edit_student) {
            Intent intent = new Intent(this, StudentFormActivity.class);
            intent.putExtra(StudentFormActivity.EXTRA_STUDENT_ID, student.getId());
            intent.putExtra(StudentFormActivity.EXTRA_CLASS_ID, classId);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.context_link_card) {
            Intent intent = new Intent(this, StudentFormActivity.class);
            intent.putExtra(StudentFormActivity.EXTRA_STUDENT_ID, student.getId());
            intent.putExtra(StudentFormActivity.EXTRA_CLASS_ID,classId);
            startActivity(intent);
            return true;
        } else if(itemId==7003){
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Gỡ khỏi lớp?")
                .setMessage(student.getFullName()+"\nLịch sử điểm danh vẫn được giữ.")
                .setPositiveButton("Gỡ",(d,w)->com.example.th9.data.Tasks.run(this,null,()->{
                    AttendanceRepository.getInstance().removeFromClass(student.getId(),classId);return true;
                },ok->loadClassData())).setNegativeButton("Hủy",null).show();return true;
        }

        return super.onContextItemSelected(item);
    }
}


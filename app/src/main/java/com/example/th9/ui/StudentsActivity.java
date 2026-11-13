package com.example.th9.ui;
import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.example.th9.R;
import com.example.th9.adapter.StudentAdapter;
import com.example.th9.data.*;
import com.example.th9.model.Student;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
public class StudentsActivity extends DataActivity {
    private StudentAdapter adapter;private Student selected;private EditText search;
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_students);androidx.appcompat.widget.Toolbar bar=findViewById(R.id.toolbar_students);setSupportActionBar(bar);getSupportActionBar().setTitle("Quản lý sinh viên");getSupportActionBar().setDisplayHomeAsUpEnabled(true);bar.setNavigationOnClickListener(v->finish());
        search=findViewById(R.id.students_search);RecyclerView list=findViewById(R.id.students_list);list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new StudentAdapter(this,(s,p)->profile(s),(s,p,v)->selected=s,s->StudentDeletion.confirm(this,s,this::refresh));list.setAdapter(adapter);
        findViewById(R.id.students_add).setOnClickListener(v->startActivity(new Intent(this,StudentFormActivity.class)));
        findViewById(R.id.students_import_csv).setOnClickListener(v->startActivity(new Intent(this,CsvImportPreviewActivity.class)));
        findViewById(R.id.students_verify).setOnClickListener(v->startActivity(new Intent(this,ScanActivity.class).putExtra(ScanActivity.EXTRA_MODE,ScanActivity.MODE_VERIFY_CARD)));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refresh();}public void afterTextChanged(Editable e){}});
    }
    @Override protected void onDataResume(){refresh();}
    private void refresh(){String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);List<Student> rows=new ArrayList<>();for(Student s:AttendanceRepository.getInstance().getAllStudents())if(s.getFullName().toLowerCase(Locale.ROOT).contains(q)||s.getMssv().toLowerCase(Locale.ROOT).contains(q))rows.add(s);adapter.setStudents(rows);
        TextView empty=findViewById(R.id.students_empty);empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);empty.setText(q.isEmpty()?"Chưa có sinh viên. Nhấn Thêm sinh viên để tạo hồ sơ.":"Không tìm thấy sinh viên phù hợp.");}
    private void profile(Student s){startActivity(new Intent(this,StudentProfileActivity.class).putExtra(StudentProfileActivity.EXTRA_STUDENT_ID,s.getId()));}
    @Override public boolean onContextItemSelected(@NonNull MenuItem item){
        if(selected==null)return super.onContextItemSelected(item);int id=item.getItemId();
        if(id==R.id.context_view_profile){profile(selected);return true;}
        if(id==R.id.context_edit_student){startActivity(new Intent(this,StudentFormActivity.class).putExtra(StudentFormActivity.EXTRA_STUDENT_ID,selected.getId()));return true;}
        if(id==7401){startActivity(new Intent(this,StudentCardActivity.class).putExtra(StudentProfileActivity.EXTRA_STUDENT_ID,selected.getId()));return true;}
        if(id==7402){StudentDeletion.confirm(this,selected,this::refresh);return true;}
        return super.onContextItemSelected(item);
    }
}

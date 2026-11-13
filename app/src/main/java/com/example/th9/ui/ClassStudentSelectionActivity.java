package com.example.th9.ui;

import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.appbar.MaterialToolbar;
import java.text.Normalizer;
import java.util.*;

public class ClassStudentSelectionActivity extends DataActivity {
    public static final String EXTRA_CLASS_ID="select_students_class_id";
    private String classId;
    private final Set<String> selected=new LinkedHashSet<>(),members=new HashSet<>();
    private List<Student> students=new ArrayList<>(),visible=new ArrayList<>();
    private EditText search;private ListView list;private TextView error;
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_class_student_selection);classId=getIntent().getStringExtra(EXTRA_CLASS_ID);
        MaterialToolbar toolbar=findViewById(R.id.toolbar_class_student_selection);toolbar.setNavigationOnClickListener(v->finish());
        search=findViewById(R.id.class_student_selection_search);list=findViewById(R.id.class_student_selection_list);error=findViewById(R.id.class_student_selection_error);
        if(state!=null){ArrayList<String> ids=state.getStringArrayList("selected");if(ids!=null)selected.addAll(ids);search.setText(state.getString("query",""));}
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}});
        list.setOnItemClickListener((p,v,position,id)->{String studentId=visible.get(position).getId();if(list.isItemChecked(position))selected.add(studentId);else selected.remove(studentId);updateCount();});
        findViewById(R.id.class_student_selection_save).setOnClickListener(v->{
            List<String> ids=new ArrayList<>(selected);error.setText("");
            Tasks.run(this,v,()->AttendanceRepository.getInstance().addExistingStudentsToClass(classId,ids),added->{Toast.makeText(this,"Đã thêm "+added+" sinh viên vào lớp",Toast.LENGTH_SHORT).show();finish();},message->{error.setText(message);reload();});
        });
    }
    @Override protected void onDataResume(){reload();}
    private void reload(){
        AttendanceRepository repo=AttendanceRepository.getInstance();ClassRoom classroom=repo.getClassById(classId);if(classroom==null){finish();return;}
        ((TextView)findViewById(R.id.class_student_selection_class)).setText(classroom.getName()+" • "+classroom.getCode());
        students=repo.getAllStudents();Set<String> active=new HashSet<>();for(Student student:students)active.add(student.getId());selected.retainAll(active);
        members.clear();for(Student student:repo.getStudentsForClass(classId))members.add(student.getId());selected.removeAll(members);render();
    }
    private static String key(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).trim();}
    private void render(){
        String query=key(search.getText().toString());visible=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(Student student:students)if(key(student.getFullName()+" "+student.getMssv()).contains(query)){
            visible.add(student);labels.add(student.getFullName()+"\n"+student.getMssv()+(members.contains(student.getId())?" • Đã có trong lớp":""));
        }
        list.clearChoices();list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_multiple_choice,labels){
            @Override public boolean areAllItemsEnabled(){return false;}
            @Override public boolean isEnabled(int position){return !members.contains(visible.get(position).getId());}
        });
        for(int i=0;i<visible.size();i++){String id=visible.get(i).getId();list.setItemChecked(i,members.contains(id)||selected.contains(id));}
        TextView empty=findViewById(R.id.class_student_selection_empty);empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);empty.setText(students.isEmpty()?R.string.class_select_students_empty:R.string.class_select_students_no_results);updateCount();
    }
    private void updateCount(){
        ((TextView)findViewById(R.id.class_student_selection_count)).setText(getString(R.string.class_select_students_count,selected.size()));
        ((TextView)findViewById(R.id.class_student_selection_save)).setText(getString(R.string.class_select_students_save,selected.size()));findViewById(R.id.class_student_selection_save).setEnabled(!selected.isEmpty());
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle state){super.onSaveInstanceState(state);state.putStringArrayList("selected",new ArrayList<>(selected));if(search!=null)state.putString("query",search.getText().toString());}
}

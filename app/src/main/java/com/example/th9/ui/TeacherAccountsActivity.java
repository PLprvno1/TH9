package com.example.th9.ui;
import android.os.Bundle;
import android.view.View;
import android.text.*;
import androidx.recyclerview.widget.*;
import com.example.th9.adapter.TeacherAdapter;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
public class TeacherAccountsActivity extends DataActivity {
    private RecyclerView list;
    private TeacherAdapter adapter;
    private EditText search;
    private TextView results,summary;
    private TeacherDirectory.Sort sort=TeacherDirectory.Sort.NAME_ASC;
    private String[] sortLabels;
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_teacher_accounts);
        Toolbar bar=findViewById(R.id.toolbar_accounts);setSupportActionBar(bar);
        getSupportActionBar().setTitle(R.string.teachers_title);getSupportActionBar().setDisplayHomeAsUpEnabled(true);bar.setNavigationOnClickListener(v->finish());
        search=findViewById(R.id.accounts_search);results=findViewById(R.id.accounts_results);summary=findViewById(R.id.accounts_summary);
        sortLabels=getResources().getStringArray(R.array.teachers_sort_labels);
        if(state!=null){try{sort=TeacherDirectory.Sort.valueOf(state.getString("teacher_sort","NAME_ASC"));}catch(IllegalArgumentException ignored){}search.setText(state.getString("teacher_query",""));}
        list=findViewById(R.id.accounts_list);list.setLayoutManager(new LinearLayoutManager(this));list.setHasFixedSize(true);
        adapter=new TeacherAdapter(new TeacherAdapter.Actions(){
            @Override public void assign(Account user){TeacherAccountsActivity.this.assign(user);}
            @Override public void edit(Account user){editor(user);}
            @Override public void toggle(Account user){toggleAccount(user);}
        });list.setAdapter(adapter);
        findViewById(R.id.accounts_add).setOnClickListener(v->editor(null));
        findViewById(R.id.accounts_sort).setOnClickListener(v->sortDialog());
        findViewById(R.id.accounts_clear_search).setOnClickListener(v->search.setText(""));
        search.setOnEditorActionListener((v,action,event)->{
            if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)return false;
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();return true;
        });
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){render();list.scrollToPosition(0);}
            public void afterTextChanged(Editable value){}
        });
    }
    @Override protected void onDataResume(){render();}
    @Override protected void onSaveInstanceState(android.os.Bundle state){
        if(search!=null){state.putString("teacher_query",search.getText().toString());state.putString("teacher_sort",sort.name());}
        super.onSaveInstanceState(state);
    }
    private void render(){
        AttendanceRepository repo=AttendanceRepository.getInstance();List<Account> teachers=repo.getTeachers();Map<String,Integer> counts=new HashMap<>();int active=0;
        for(Account user:teachers){counts.put(user.id,repo.assignedClassIds(user.id).size());if(user.enabled)active++;}
        List<TeacherDirectory.Entry> rows=TeacherDirectory.select(teachers,counts,search.getText().toString(),sort);adapter.setRows(rows);
        summary.setText(getString(R.string.teachers_summary,active,teachers.size()-active));
        results.setText(getString(R.string.teachers_results,rows.size(),teachers.size()));
        ((TextView)findViewById(R.id.accounts_sort)).setText(sortLabels[sort.ordinal()]);
        boolean empty=rows.isEmpty(),noAccounts=teachers.isEmpty();
        list.setVisibility(empty?View.GONE:View.VISIBLE);findViewById(R.id.accounts_empty).setVisibility(empty?View.VISIBLE:View.GONE);
        ((TextView)findViewById(R.id.accounts_empty_title)).setText(noAccounts?R.string.teachers_empty:R.string.teachers_no_results);
        ((TextView)findViewById(R.id.accounts_empty_description)).setText(noAccounts?R.string.teachers_empty_description:R.string.teachers_no_results_description);
        findViewById(R.id.accounts_clear_search).setVisibility(search.getText().toString().trim().isEmpty()?View.GONE:View.VISIBLE);
    }
    private void sortDialog(){
        new MaterialAlertDialogBuilder(this).setTitle(R.string.teachers_sort)
            .setSingleChoiceItems(sortLabels,sort.ordinal(),(dialog,index)->{sort=TeacherDirectory.Sort.values()[index];render();list.scrollToPosition(0);dialog.dismiss();})
            .setNegativeButton("Đóng",null).show();
    }
    private void toggleAccount(Account user){
        new MaterialAlertDialogBuilder(this).setTitle(user.enabled?"Khóa tài khoản?":"Mở khóa tài khoản?")
            .setMessage(user.fullName+"\n"+user.username+"\n\nGiữ nguyên phân công và lịch sử điểm danh.")
            .setPositiveButton("Xác nhận",(d,w)->Tasks.run(this,null,()->{AttendanceRepository.getInstance().updateTeacher(user.id,user.fullName,!user.enabled,null);return true;},ok->render()))
            .setNegativeButton("Hủy",null).show();
    }
    private void editor(Account user){
        View form=getLayoutInflater().inflate(R.layout.dialog_account,null);EditText username=form.findViewById(R.id.account_username),name=form.findViewById(R.id.account_name),password=form.findViewById(R.id.account_password);TextView error=form.findViewById(R.id.account_error);
        if(user!=null){username.setText(user.username);username.setEnabled(false);name.setText(user.fullName);((com.google.android.material.textfield.TextInputLayout)form.findViewById(R.id.account_password_layout)).setHint("Mật khẩu mới (để trống nếu giữ nguyên)");}
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(user==null?"Thêm giảng viên":"Cập nhật giảng viên").setView(form).setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String login=username.getText().toString(),full=name.getText().toString(),secret=password.getText().toString();Tasks.run(this,v,()->{if(user==null)AttendanceRepository.getInstance().createTeacher(login,full,secret);else AttendanceRepository.getInstance().updateTeacher(user.id,full,user.enabled,secret);return true;},ok->{password.setText("");dialog.dismiss();if(user==null)search.setText("");render();Toast.makeText(this,user==null?"Đã thêm giảng viên":"Đã cập nhật tài khoản",Toast.LENGTH_SHORT).show();},error::setText);}));dialog.show();
    }
    private void assign(Account user){
        AttendanceRepository repo=AttendanceRepository.getInstance();List<ClassRoom> classes=new ArrayList<>();
        for(ClassRoom classroom:repo.getAllClasses()){
            List<String> assigned=repo.assignedTeacherIds(classroom.getId());
            if(assigned.isEmpty()||(assigned.size()==1&&assigned.contains(user.id)))classes.add(classroom);
        }
        if(classes.isEmpty()){new MaterialAlertDialogBuilder(this).setMessage("Không có lớp chưa phân công. Để đổi giảng viên, vào Chi tiết lớp → Phân công giảng viên.").setPositiveButton("Đóng",null).show();return;}
        List<String> current=repo.assignedClassIds(user.id);boolean[] selected=new boolean[classes.size()];String[] labels=new String[classes.size()];for(int i=0;i<classes.size();i++){selected[i]=current.contains(classes.get(i).getId());labels[i]=classes.get(i).getName()+" • "+classes.get(i).getCode();}
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("Phân công: "+user.fullName).setMultiChoiceItems(labels,selected,(d,n,checked)->selected[n]=checked).setPositiveButton("Lưu phân công",null).setNegativeButton("Hủy",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{List<String> ids=new ArrayList<>();for(int i=0;i<selected.length;i++)if(selected[i])ids.add(classes.get(i).getId());Tasks.run(this,v,()->{repo.replaceAssignments(user.id,ids);return true;},ok->{dialog.dismiss();render();});}));dialog.show();
    }
}

package com.example.th9.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.*;

public class StudentFormActivity extends DataActivity {
    public static final String EXTRA_STUDENT_ID="extra_student_id",EXTRA_CLASS_ID="extra_class_id";
    private String studentId,creationId,classId,photoPath,originalSignature="";
    private TextInputEditText name,phone,birth,course,mssv;
    private Spinner gender;
    private MaterialButton submit;
    private boolean editing,saving,photoLoading,receiptShown;
    private StudentSaveViewModel saveModel;
    private android.net.Uri pendingPhoto;
    private final List<String> genders=Arrays.asList("Chọn giới tính","Nam","Nữ","Khác");
    private final ActivityResultLauncher<String[]> photoPicker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
        if(uri!=null){pendingPhoto=uri;importPendingPhoto();}
    });
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_student_form);
        studentId=getIntent().getStringExtra(EXTRA_STUDENT_ID);classId=getIntent().getStringExtra(EXTRA_CLASS_ID);editing=studentId!=null;
        creationId=state==null?AttendanceRepository.id():state.getString("creation_id",AttendanceRepository.id());
        name=findViewById(R.id.et_student_name);phone=findViewById(R.id.et_student_phone);birth=findViewById(R.id.et_student_birth);course=findViewById(R.id.et_student_course);mssv=findViewById(R.id.et_student_mssv);gender=findViewById(R.id.sp_student_gender);submit=findViewById(R.id.btn_student_submit);
        Toolbar bar=findViewById(R.id.toolbar_student_form);setSupportActionBar(bar);getSupportActionBar().setTitle(editing?"Chỉnh sửa hồ sơ":"Thêm sinh viên");getSupportActionBar().setDisplayHomeAsUpEnabled(true);bar.setNavigationOnClickListener(v->leave());
        gender.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,genders));
        if(editing){Student s=AttendanceRepository.getInstance().getStudentById(studentId);if(s==null){finish();return;}
            name.setText(s.getFullName());phone.setText(s.getPhone());birth.setText(s.getBirthDate());course.setText(s.getCourse());photoPath=s.getPhotoPath();gender.setSelection(Math.max(0,genders.indexOf(s.getGender())));mssv.setText(s.getMssv());
            findViewById(R.id.til_student_mssv).setVisibility(View.VISIBLE);submit.setText("Lưu thay đổi");((TextView)findViewById(R.id.student_number_hint)).setText("MSSV hiện tại sẽ được giữ nguyên khi bạn cập nhật hồ sơ.");
        }
        originalSignature=signature();
        if(state!=null){name.setText(state.getString("name",name.getText().toString()));phone.setText(state.getString("phone",phone.getText().toString()));birth.setText(state.getString("birth",birth.getText().toString()));course.setText(state.getString("course",course.getText().toString()));photoPath=state.getString("photo",photoPath);gender.setSelection(state.getInt("gender",gender.getSelectedItemPosition()));}
        StudentPhotos.show(findViewById(R.id.img_student_photo),photoPath);
        findViewById(R.id.btn_student_photo).setOnClickListener(v->photoPicker.launch(new String[]{"image/*"}));
        birth.setOnClickListener(v->pickBirth());submit.setOnClickListener(v->save());findViewById(R.id.btn_student_cancel).setOnClickListener(v->leave());
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){leave();}});
        saveModel=new androidx.lifecycle.ViewModelProvider(this).get(StudentSaveViewModel.class);
        saveModel.state().observe(this,result->{
            saving=result.saving;updateButtons();
            if(result.student!=null){if(editing){Toast.makeText(this,"Đã lưu hồ sơ",Toast.LENGTH_SHORT).show();finish();}else created(result.student);}
            else if(result.error!=null)Toast.makeText(this,result.error,Toast.LENGTH_LONG).show();
        });
        saveModel.photo().observe(this,result->{
            photoLoading=result.loading;
            if(result.path!=null){photoPath=result.path;StudentPhotos.show(findViewById(R.id.img_student_photo),photoPath);}
            if(result.error!=null)Toast.makeText(this,result.error,Toast.LENGTH_LONG).show();
            updateButtons();
        });
        if(pendingPhoto==null&&state!=null&&state.getString("pending_photo")!=null)pendingPhoto=android.net.Uri.parse(state.getString("pending_photo"));
        importPendingPhoto();
    }
    private void importPendingPhoto(){if(saveModel!=null&&pendingPhoto!=null){android.net.Uri uri=pendingPhoto;pendingPhoto=null;saveModel.importPhoto(getApplicationContext(),uri);}}
    private void updateButtons(){submit.setEnabled(!saving&&!photoLoading&&!receiptShown);findViewById(R.id.btn_student_photo).setEnabled(!saving&&!photoLoading&&!receiptShown);}
    @Override protected void onDataResume(){
        if(!editing&&creationId!=null){Student saved=AttendanceRepository.getInstance().getStudentById(creationId);if(saved!=null)created(saved);}
    }
    private void pickBirth(){
        Calendar selected=Calendar.getInstance();selected.add(Calendar.YEAR,-18);
        try{String[] parts=birth.getText().toString().split("/");selected.set(Integer.parseInt(parts[2]),Integer.parseInt(parts[1])-1,Integer.parseInt(parts[0]));}catch(Exception ignored){}
        DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->birth.setText(String.format(Locale.ROOT,"%02d/%02d/%04d",d,m+1,y)),selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH));picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();
    }
    private String signature(){return Arrays.asList(name.getText().toString(),phone.getText().toString(),birth.getText().toString(),course.getText().toString(),String.valueOf(gender.getSelectedItemPosition()),photoPath).toString();}
    private void save(){
        if(saving||photoLoading)return;
        String fullName=name.getText().toString().trim(),phoneNumber=phone.getText().toString().trim(),birthday=birth.getText().toString(),cohort=ProfileRules.normalizeCourse(course.getText().toString()),sex=gender.getSelectedItemPosition()>0?genders.get(gender.getSelectedItemPosition()):null;
        if(!phoneNumber.matches("[0-9]{10}")){phone.setError("Số điện thoại phải gồm đúng 10 chữ số");phone.requestFocus();return;}phone.setError(null);
        try{ProfileRules.validateRegistration(fullName,birthday,cohort,sex);}catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        Student s=editing?AttendanceRepository.getInstance().getStudentById(studentId):new Student(creationId,"",fullName,null,"","",0);
        if(s==null){Toast.makeText(this,"Không tìm thấy sinh viên",Toast.LENGTH_LONG).show();return;}
        s.setFullName(fullName);s.setPhone(phoneNumber);s.setBirthDate(birthday);s.setCourse(cohort);s.setGender(sex);s.setPhotoPath(photoPath);
        saveModel.save(s,classId,editing);
    }
    private void created(Student saved){
        submit.setEnabled(false);findViewById(R.id.btn_student_photo).setEnabled(false);
        if(receiptShown)return;
        if(getSupportFragmentManager().findFragmentByTag("student_created")!=null){receiptShown=true;return;}
        if(getSupportFragmentManager().findFragmentByTag("student_created")==null&&!getSupportFragmentManager().isStateSaved()){
            receiptShown=true;
            ClassRoom c=AttendanceRepository.getInstance().getClassById(saved.getPrimaryClassId());StudentCreatedDialog.newInstance(saved,c==null?null:c.getName()).show(getSupportFragmentManager(),"student_created");
        }
    }
    private void leave(){
        if(saving||photoLoading){Toast.makeText(this,"Đang lưu dữ liệu, vui lòng chờ",Toast.LENGTH_SHORT).show();return;}
        if(!editing&&AttendanceRepository.getInstance().getStudentById(creationId)!=null){finish();return;}
        if(!signature().equals(originalSignature))new MaterialAlertDialogBuilder(this).setTitle("Rời form?").setMessage("Các thay đổi chưa lưu sẽ bị bỏ.").setPositiveButton("Rời khỏi",(d,w)->finish()).setNegativeButton("Ở lại",null).show();else finish();
    }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state){
        state.putString("creation_id",creationId);state.putString("photo",photoPath);
        if(pendingPhoto!=null)state.putString("pending_photo",pendingPhoto.toString());
        if(name!=null){state.putString("name",name.getText().toString());state.putString("phone",phone.getText().toString());state.putString("birth",birth.getText().toString());state.putString("course",course.getText().toString());state.putInt("gender",gender.getSelectedItemPosition());}
        super.onSaveInstanceState(state);
    }
}

package com.example.th9.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.*;
import java.util.*;

public class ClassTeachersDialog extends DialogFragment {
    public static final String RESULT_UPDATED="class_teachers_updated";
    private final Set<String> selected=new HashSet<>();
    private final Set<String> originallyAssigned=new HashSet<>();
    private List<Account> teachers;
    private List<TeacherDirectory.Entry> visible=new ArrayList<>();
    private EditText search;private ListView list;private TextView count,empty,error;
    private String classId;
    public static ClassTeachersDialog create(String classId){ClassTeachersDialog dialog=new ClassTeachersDialog();Bundle args=new Bundle();args.putString("classId",classId);dialog.setArguments(args);return dialog;}
    @NonNull @Override public Dialog onCreateDialog(Bundle state){
        AttendanceRepository repo=AttendanceRepository.getInstance();classId=requireArguments().getString("classId");
        if(!repo.isAdmin())return new MaterialAlertDialogBuilder(requireContext()).setMessage("Chỉ quản trị viên mới có thể phân công giảng viên.").setPositiveButton("Đóng",null).create();
        teachers=repo.getTeachers();selected.addAll(repo.assignedTeacherIds(classId));
        originallyAssigned.addAll(selected);
        boolean legacyMultiple=selected.size()>1;
        if(legacyMultiple)selected.clear();
        if(state!=null){selected.clear();selected.addAll(state.getStringArrayList("selected")!=null?state.getStringArrayList("selected"):Collections.emptyList());}
        View form=getLayoutInflater().inflate(R.layout.dialog_class_teachers,null);
        ClassRoom classroom=repo.getClassById(classId);((TextView)form.findViewById(R.id.class_teachers_class)).setText(classroom==null?"Lớp không còn tồn tại":classroom.getName()+" • "+classroom.getCode());
        search=form.findViewById(R.id.class_teachers_search);list=form.findViewById(R.id.class_teachers_list);count=form.findViewById(R.id.class_teachers_count);empty=form.findViewById(R.id.class_teachers_empty);error=form.findViewById(R.id.class_teachers_error);
        if(legacyMultiple)error.setText("Lớp này đang có nhiều giảng viên phụ trách. Chọn một giảng viên để tiếp tục.");
        form.findViewById(R.id.class_teachers_clear).setOnClickListener(v->{selected.clear();render();});
        if(state!=null)search.setText(state.getString("query",""));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int before,int c){render();}});
        list.setOnItemClickListener((parent,view,position,id)->{selected.clear();selected.add(visible.get(position).account.id);updateCount();});
        render();
        MaterialShapeDrawable background=new MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(24*getResources().getDisplayMetrics().density).build());
        background.setFillColor(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(),R.color.surface)));
        return new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.class_assign_teachers).setBackground(background).setView(form)
            .setPositiveButton(R.string.class_teachers_save,null).setNegativeButton("Hủy",null).create();
    }
    private void updateCount(){count.setText(getString(R.string.class_teachers_count,selected.size(),visible.size()));}
    private void render(){
        visible=TeacherDirectory.select(teachers,Collections.emptyMap(),search.getText().toString(),TeacherDirectory.Sort.NAME_ASC);
        List<String> labels=new ArrayList<>();for(TeacherDirectory.Entry entry:visible){Account t=entry.account;labels.add(t.fullName+"\n"+t.username+(t.enabled?"":" • Đã khóa"));}
        list.clearChoices();list.setAdapter(new ArrayAdapter<String>(requireContext(),android.R.layout.simple_list_item_single_choice,labels){
            @Override public boolean areAllItemsEnabled(){return false;}
            @Override public boolean isEnabled(int position){Account t=visible.get(position).account;return t.enabled||originallyAssigned.contains(t.id);}
        });
        for(int i=0;i<visible.size();i++)list.setItemChecked(i,selected.contains(visible.get(i).account.id));
        empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);list.setVisibility(visible.isEmpty()?View.GONE:View.VISIBLE);updateCount();
    }
    @Override public void onStart(){super.onStart();if(search==null)return;
        AlertDialog dialog=(AlertDialog)requireDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            List<String> desired=new ArrayList<>(selected);error.setText("");
            Tasks.run(requireActivity(),v,()->{AttendanceRepository.getInstance().replaceClassTeachers(classId,desired);return true;},ok->{
                if(isAdded()){Bundle result=new Bundle();result.putString("classId",classId);getParentFragmentManager().setFragmentResult(RESULT_UPDATED,result);dismiss();}
            },message->{if(isAdded())error.setText(message);});
        });
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out){super.onSaveInstanceState(out);out.putStringArrayList("selected",new ArrayList<>(selected));if(search!=null)out.putString("query",search.getText().toString());}
}

package com.example.th9.ui;

import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.example.th9.R;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.data.Tasks;
import com.example.th9.model.Student;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Shared confirmation for removing a profile while preserving attendance snapshots. */
final class StudentDeletion {
    private StudentDeletion(){}
    static void confirm(DataActivity owner,Student student,Runnable deleted){
        if(!AttendanceRepository.getInstance().isAdmin()){
            Toast.makeText(owner,"Chỉ quản trị viên được xóa sinh viên",Toast.LENGTH_LONG).show();return;
        }
        if(student==null)return;
        String studentId=student.getId();
        AlertDialog dialog=new MaterialAlertDialogBuilder(owner).setTitle(R.string.student_delete_title)
            .setMessage(owner.getString(R.string.student_delete_message,student.getFullName(),student.getMssv()))
            .setNegativeButton("Hủy",null).setPositiveButton(R.string.student_delete,null).create();
        androidx.lifecycle.DefaultLifecycleObserver observer=new androidx.lifecycle.DefaultLifecycleObserver(){
            @Override public void onDestroy(androidx.lifecycle.LifecycleOwner source){dialog.dismiss();source.getLifecycle().removeObserver(this);}
        };
        owner.getLifecycle().addObserver(observer);
        dialog.setOnDismissListener(d->owner.getLifecycle().removeObserver(observer));
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            dialog.setCancelable(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            Tasks.run(owner,v,()->{AttendanceRepository.getInstance().deleteStudent(studentId);return true;},ok->{
                dialog.dismiss();Toast.makeText(owner,R.string.student_deleted,Toast.LENGTH_SHORT).show();deleted.run();
            },error->{dialog.setCancelable(true);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);Toast.makeText(owner,error,Toast.LENGTH_LONG).show();});
        }));
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(androidx.core.content.ContextCompat.getColor(owner,R.color.status_absent));
    }
}

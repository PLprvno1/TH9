package com.example.th9.ui;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.example.th9.R;
import com.example.th9.model.Student;
import com.example.th9.view.StudentCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
/** Saved arguments let the creation receipt survive rotation/process recreation. */
public class StudentCreatedDialog extends DialogFragment {
    public static StudentCreatedDialog newInstance(Student student,String className){
        StudentCreatedDialog dialog=new StudentCreatedDialog();Bundle args=new Bundle();args.putSerializable("student",student);args.putString("class_name",className);dialog.setArguments(args);return dialog;
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle state){
        setCancelable(false);Student student=(Student)requireArguments().getSerializable("student");
        View content=getLayoutInflater().inflate(R.layout.dialog_student_created,null);((TextView)content.findViewById(R.id.created_student_number)).setText(student.getMssv());
        ((StudentCardView)content.findViewById(R.id.created_student_card)).bind(student,requireArguments().getString("class_name"));
        return new MaterialAlertDialogBuilder(requireContext()).setTitle("Đã thêm sinh viên").setView(content)
            .setPositiveButton("Xem hồ sơ",(d,w)->{startActivity(new Intent(requireContext(),StudentProfileActivity.class).putExtra(StudentProfileActivity.EXTRA_STUDENT_ID,student.getId()));requireActivity().finish();})
            .setNegativeButton("Đóng",(d,w)->requireActivity().finish()).create();
    }
}

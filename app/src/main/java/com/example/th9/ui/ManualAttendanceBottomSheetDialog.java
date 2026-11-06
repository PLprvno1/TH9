package com.example.th9.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th9.R;
import com.example.th9.adapter.StudentAdapter;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceRecord;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.Student;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class ManualAttendanceBottomSheetDialog extends BottomSheetDialogFragment {

    public interface OnAttendanceChangedListener {
        void onAttendanceChanged();
    }

    private static final String ARG_SESSION_ID = "arg_session_id";

    private String sessionId;
    private OnAttendanceChangedListener callback;

    private EditText etSearch;
    private MaterialCardView cardSelectedStudent;
    private TextView tvSelectedName, tvSelectedMssv;
    private RecyclerView rvCandidates;
    private StudentAdapter candidateAdapter;
    private RadioGroup rgStatus;
    private MaterialButton btnConfirm;

    private Student selectedStudent = null;

    public static ManualAttendanceBottomSheetDialog newInstance(String sessionId, OnAttendanceChangedListener callback) {
        ManualAttendanceBottomSheetDialog dialog = new ManualAttendanceBottomSheetDialog();
        dialog.callback = callback;
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_manual_attendance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            sessionId = getArguments().getString(ARG_SESSION_ID);
        }

        etSearch = view.findViewById(R.id.et_manual_search);
        cardSelectedStudent = view.findViewById(R.id.card_selected_student);
        tvSelectedName = view.findViewById(R.id.tv_manual_student_name);
        tvSelectedMssv = view.findViewById(R.id.tv_manual_student_mssv);
        rvCandidates = view.findViewById(R.id.rv_manual_candidates);
        rgStatus = view.findViewById(R.id.rg_manual_status);
        rgStatus.setVisibility(View.GONE);
        btnConfirm = view.findViewById(R.id.btn_manual_confirm);

        setupCandidatesList();
        setupSearchAndConfirm();
        if(!AttendanceRepository.getInstance().isInitialized()){
            com.example.th9.data.Tasks.run(requireActivity(),null,()->{AttendanceRepository.getInstance().initialize();return true;},ok->{if(isAdded())loadCandidates("");});
        }
    }

    private void setupCandidatesList() {
        candidateAdapter = new StudentAdapter(requireContext(), (student, position) -> {
            selectedStudent = student;
            tvSelectedName.setText(student.getFullName());
            tvSelectedMssv.setText("MSSV: " + student.getMssv());
            cardSelectedStudent.setVisibility(View.VISIBLE);
            btnConfirm.setEnabled(true);
            btnConfirm.setText("Xác nhận: " + student.getFullName());
        }, null);

        rvCandidates.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCandidates.setAdapter(candidateAdapter);

        loadCandidates("");
    }

    private void loadCandidates(String query) {
        AttendanceSession session = AttendanceRepository.getInstance().getSessionById(sessionId);
        if (session == null) return;

        List<Student> classStudents = AttendanceRepository.getInstance().getSessionStudents(sessionId);
        List<Student> filtered = new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase() : "";

        for (Student s : classStudents) {
            if (q.isEmpty() || s.getFullName().toLowerCase().contains(q) || s.getMssv().toLowerCase().contains(q)) {
                filtered.add(s);
            }
        }

        candidateAdapter.setStudents(filtered);
    }

    private void setupSearchAndConfirm() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadCandidates(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedStudent == null) return;

            com.example.th9.data.Tasks.run(requireActivity(),btnConfirm,()->
                AttendanceRepository.getInstance().manual(sessionId,selectedStudent.getId(),System.currentTimeMillis()),record->{
                if(!isAdded())return;
                Toast.makeText(requireContext(),"Ghi nhận thành công: "+record.getNameSnapshot()+" · "+record.getStatusDisplayName(),Toast.LENGTH_SHORT).show();
                if(callback!=null)callback.onAttendanceChanged();
                getParentFragmentManager().setFragmentResult("attendance_changed",new Bundle());dismiss();
            });
        });
    }
}


package com.example.th9.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th9.R;
import com.example.th9.adapter.AttendanceRecordAdapter;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceRecord;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.ClassRoom;
import com.example.th9.model.Student;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SessionResultActivity extends com.example.th9.ui.DataActivity {

    public static final String EXTRA_SESSION_ID = "extra_session_id";

    private String sessionId;
    private AttendanceSession session;

    private Toolbar toolbar;
    private TextView tvTitle, tvClass, tvFinishInfo;
    private LinearLayout llSummaryCardsContainer;
    private ChipGroup chipGroupFilter;
    private Chip chipAll, chipPresent, chipLate, chipAbsent;
    private EditText etSearch;
    private RecyclerView rvRecords;
    private AttendanceRecordAdapter adapter;

    @Override
    protected void onReady(Bundle savedInstanceState) {

        setContentView(R.layout.activity_session_result);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();
    }

    @Override
    protected void onDataResume() {

        loadSessionData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_result);
        tvTitle = findViewById(R.id.tv_result_session_title);
        tvClass = findViewById(R.id.tv_result_session_class);
        tvFinishInfo = findViewById(R.id.tv_result_finish_info);
        llSummaryCardsContainer = findViewById(R.id.ll_summary_cards_container);
        chipGroupFilter = findViewById(R.id.chip_group_result_filter);
        chipAll = findViewById(R.id.chip_filter_all);
        chipPresent = findViewById(R.id.chip_filter_present);
        chipLate = findViewById(R.id.chip_filter_late);
        chipAbsent = findViewById(R.id.chip_filter_absent);
        etSearch = findViewById(R.id.et_result_search);
        rvRecords = findViewById(R.id.rv_result_records);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.result_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_session_result, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_export) {
            ExportPreviewBottomSheetDialog dialog = ExportPreviewBottomSheetDialog.newInstance(sessionId);
            dialog.show(getSupportFragmentManager(), "export_preview");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        adapter = new AttendanceRecordAdapter(this, (record, student) -> {
            if (student != null) {
                Intent intent = new Intent(this, StudentProfileActivity.class);
                intent.putExtra(StudentProfileActivity.EXTRA_STUDENT_ID, student.getId());
                intent.putExtra(StudentProfileActivity.EXTRA_CLASS_ID, session != null ? session.getClassId() : null);
                startActivity(intent);
            }
        }, (record, student, anchorView) -> {
            // Handled via context menu
        });

        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(adapter);
        registerForContextMenu(rvRecords);
    }

    private void setupListeners() {
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            filterRecords(etSearch.getText() != null ? etSearch.getText().toString() : "");
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRecords(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadSessionData() {
        session = AttendanceRepository.getInstance().getSessionById(sessionId);
        if (session == null) return;

        tvTitle.setText(session.getTitle());
        ClassRoom cr = AttendanceRepository.getInstance().getClassById(session.getClassId());
        if (cr != null) {
            tvClass.setText(AttendanceRepository.getInstance().sessionClassLabel(session));
        }

        String finishText = session.getFinishedTime() != null ? session.getFinishedTime() : "Đang mở";
        tvFinishInfo.setText("Sĩ số: " + session.getExpectedCount() + " SV • " + finishText);

        buildDynamicSummaryCardsInJava();
        updateFilterChipLabels();
        filterRecords(etSearch.getText() != null ? etSearch.getText().toString() : "");
    }

    /**
     * YÊU CẦU ĐẶC THÙ: Dựng một cụm giao diện thực sự bằng Java.
     * Tạo MaterialCardView, LinearLayout, TextView, ImageView thuần túy trong mã nguồn Java,
     * áp dụng style, màu sắc, khoảng cách và gắn vào llSummaryCardsContainer theo dữ liệu Room.
     */
    private void buildDynamicSummaryCardsInJava() {
        llSummaryCardsContainer.removeAllViews();

        int presentCount = AttendanceRepository.getInstance().getPresentCount(sessionId);
        int lateCount = AttendanceRepository.getInstance().getLateCount(sessionId);
        int absentCount = AttendanceRepository.getInstance().getAbsentCount(sessionId);
        int total = session.getExpectedCount();

        int presentPct = total > 0 ? (presentCount * 100 / total) : 0;
        int latePct = total > 0 ? (lateCount * 100 / total) : 0;
        int absentPct = total > 0 ? (absentCount * 100 / total) : 0;
        int overallPct = total > 0 ? ((presentCount + lateCount) * 100 / total) : 0;

        // Card 1: Có mặt (Xanh lá)
        MaterialCardView cardPresent = createSummaryCard(
                "Có mặt",
                String.valueOf(presentCount),
                presentPct + "% sĩ số",
                R.drawable.ic_check_circle,
                Color.parseColor("#16A34A"),
                Color.parseColor("#DCFCE7"),
                Color.parseColor("#14532D")
        );
        llSummaryCardsContainer.addView(cardPresent);

        // Card 2: Đi muộn (Hổ phách)
        MaterialCardView cardLate = createSummaryCard(
                "Đi muộn",
                String.valueOf(lateCount),
                latePct + "% sĩ số",
                R.drawable.ic_schedule,
                Color.parseColor("#D97706"),
                Color.parseColor("#FEF3C7"),
                Color.parseColor("#78350F")
        );
        llSummaryCardsContainer.addView(cardLate);

        // Card 3: Vắng mặt (Đỏ)
        MaterialCardView cardAbsent = createSummaryCard(
                "Vắng",
                String.valueOf(absentCount),
                absentPct + "% sĩ số",
                R.drawable.ic_cancel,
                Color.parseColor("#DC2626"),
                Color.parseColor("#FEE2E2"),
                Color.parseColor("#7F1D1D")
        );
        llSummaryCardsContainer.addView(cardAbsent);

        // Card 4: Tỷ lệ chuyên cần (Xanh Indigo / Brand)
        MaterialCardView cardRate = createSummaryCard(
                "Chuyên cần",
                overallPct + "%",
                (presentCount + lateCount) + "/" + total + " SV",
                R.drawable.ic_stats,
                Color.parseColor("#1E3A8A"),
                Color.parseColor("#DBEAFE"),
                Color.parseColor("#1E3A8A")
        );
        llSummaryCardsContainer.addView(cardRate);
    }

    /**
     * Trợ hàm Java tạo MaterialCardView tổng kết động hoàn toàn bằng code.
     */
    private MaterialCardView createSummaryCard(String label, String value, String subtext,
                                              int iconRes, int iconColor, int bgColor, int textColor) {
        float density = getResources().getDisplayMetrics().density;

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                (int) (130 * density),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins((int) (4 * density), (int) (2 * density), (int) (6 * density), (int) (4 * density));
        card.setLayoutParams(cardParams);
        card.setCardElevation(1 * density);
        card.setRadius(12 * density);
        card.setStrokeColor(Color.parseColor("#E2E8F0"));
        card.setStrokeWidth((int) (1 * density));
        card.setCardBackgroundColor(ContextCompat.getColor(this,R.color.surface_card));

        LinearLayout innerLayout = new LinearLayout(this);
        innerLayout.setOrientation(LinearLayout.VERTICAL);
        innerLayout.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));

        // Top Row: Label and Icon
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(labelParams);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(Color.parseColor("#64748B"));
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        topRow.addView(tvLabel);

        ImageView ivIcon = new ImageView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams((int) (18 * density), (int) (18 * density));
        ivIcon.setLayoutParams(iconParams);
        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(iconColor);
        topRow.addView(ivIcon);

        innerLayout.addView(topRow);

        // Value TextView
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(22);
        tvValue.setTextColor(ContextCompat.getColor(this,R.color.text_primary));
        tvValue.setTypeface(null, android.graphics.Typeface.BOLD);
        tvValue.setPadding(0, (int) (4 * density), 0, 0);
        innerLayout.addView(tvValue);

        // Subtext Badge
        TextView tvSub = new TextView(this);
        tvSub.setText(subtext);
        tvSub.setTextSize(11);
        tvSub.setTextColor(textColor);
        tvSub.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSub.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));

        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setCornerRadius(4 * density);
        badgeBg.setColor(bgColor);
        tvSub.setBackground(badgeBg);

        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = (int) (4 * density);
        tvSub.setLayoutParams(subParams);
        innerLayout.addView(tvSub);

        card.addView(innerLayout);
        return card;
    }

    private void updateFilterChipLabels() {
        int presentCount = AttendanceRepository.getInstance().getPresentCount(sessionId);
        int lateCount = AttendanceRepository.getInstance().getLateCount(sessionId);
        int absentCount = AttendanceRepository.getInstance().getAbsentCount(sessionId);
        int total = session != null ? session.getExpectedCount() : 0;

        chipAll.setText(getString(R.string.result_filter_all) + " (" + total + ")");
        chipPresent.setText(getString(R.string.result_filter_present) + " (" + presentCount + ")");
        chipLate.setText(getString(R.string.result_filter_late) + " (" + lateCount + ")");
        chipAbsent.setText(getString(R.string.result_filter_absent) + " (" + absentCount + ")");
    }

    private void filterRecords(String query) {
        List<AttendanceRecord> all = AttendanceRepository.getInstance().getRecordsForSession(sessionId);
        int checkedChipId = chipGroupFilter.getCheckedChipId();

        List<AttendanceRecord> filtered = new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase() : "";

        for (AttendanceRecord r : all) {
            Student s = AttendanceRepository.getInstance().snapshotStudent(r);
            boolean matchesSearch = q.isEmpty()
                    || (s != null && s.getFullName().toLowerCase().contains(q))
                    || (s != null && s.getMssv().toLowerCase().contains(q));

            if (!matchesSearch) continue;

            if (checkedChipId == R.id.chip_filter_present && r.getStatus() != AttendanceRecord.AttendanceStatus.PRESENT) {
                continue;
            } else if (checkedChipId == R.id.chip_filter_late && r.getStatus() != AttendanceRecord.AttendanceStatus.LATE) {
                continue;
            } else if (checkedChipId == R.id.chip_filter_absent && r.getStatus() != AttendanceRecord.AttendanceStatus.ABSENT) {
                continue;
            }

            filtered.add(r);
        }

        adapter.setRecords(filtered);
        findViewById(R.id.tv_result_empty).setVisibility(filtered.isEmpty()?View.VISIBLE:View.GONE);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        int position = adapter.getSelectedPosition();
        AttendanceRecord record = adapter.getRecordAt(position);
        if (record == null) return super.onContextItemSelected(item);

        Student s = AttendanceRepository.getInstance().snapshotStudent(record);

        if (item.getItemId() == R.id.context_view_profile) {
            if (s != null) {
                Intent intent = new Intent(this, StudentProfileActivity.class);
                intent.putExtra(StudentProfileActivity.EXTRA_STUDENT_ID, s.getId());
                intent.putExtra(StudentProfileActivity.EXTRA_CLASS_ID, session != null ? session.getClassId() : null);
                startActivity(intent);
            }
            return true;
        } else if (item.getItemId() == R.id.context_change_status) {
            showChangeStatusDialog(record, s);
            return true;
        }

        return super.onContextItemSelected(item);
    }

    private void showChangeStatusDialog(AttendanceRecord record, Student student) {
        if(session==null||session.getStatus()!=AttendanceSession.SessionStatus.CLOSED){
            Toast.makeText(this,"Chỉ chỉnh kết quả khi buổi đã đóng",Toast.LENGTH_LONG).show();return;
        }
        String[] options={"Có mặt","Đi muộn","Vắng"};
        int checked=record.getStatus()==AttendanceRecord.AttendanceStatus.LATE?1:record.getStatus()==AttendanceRecord.AttendanceStatus.ABSENT?2:0;
        int[] selected={checked};
        EditText reason=new EditText(this);reason.setHint("Lý do chỉnh kết quả (bắt buộc)");reason.setMinLines(2);
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this)
            .setTitle("Chỉnh: "+record.getNameSnapshot()).setSingleChoiceItems(options,checked,(d,w)->selected[0]=w)
            .setView(reason).setPositiveButton("Lưu trạng thái",null).setNegativeButton("Hủy",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String text=reason.getText().toString().trim();if(text.isEmpty()){reason.setError("Nhập lý do");return;}
            AttendanceRecord.AttendanceStatus status=selected[0]==0?AttendanceRecord.AttendanceStatus.PRESENT:selected[0]==1?AttendanceRecord.AttendanceStatus.LATE:AttendanceRecord.AttendanceStatus.ABSENT;
            com.example.th9.data.Tasks.run(this,v,()->{AttendanceRepository.getInstance().updateRecordStatus(record.getId(),status,text);return true;},ok->{dialog.dismiss();loadSessionData();});
        }));dialog.show();
    }
}

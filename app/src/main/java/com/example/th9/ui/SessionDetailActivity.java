package com.example.th9.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.th9.R;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.ClassRoom;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class SessionDetailActivity extends com.example.th9.ui.DataActivity {

    public static final String EXTRA_SESSION_ID = "extra_session_id";

    private String sessionId;
    private AttendanceSession session;

    private Toolbar toolbar;
    private TextView tvTitle, tvBadge, tvClass, tvSchedule, tvExpected, tvLateRule, tvFinishedTime;

    // States layout containers
    private LinearLayout layoutUpcoming, layoutActive, layoutFinished;

    // Active state views
    private TextView tvActiveRatio, tvActivePresent, tvActiveLate, tvActiveUnchecked;
    private LinearProgressIndicator progressActive;
    private MaterialButton btnScan, btnManual, btnFinish;

    // Upcoming state view
    private MaterialButton btnOpen;

    // Finished state views
    private TextView tvFinishedSummary;
    private MaterialButton btnViewResult;

    @Override
    protected void onReady(Bundle savedInstanceState) {

        setContentView(R.layout.activity_session_detail);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);

        initViews();
        setupToolbar();
        setupListeners();
        getSupportFragmentManager().setFragmentResultListener("attendance_changed",this,(key,result)->loadSessionData());
    }

    @Override
    protected void onDataResume() {

        loadSessionData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_session_detail);
        tvTitle = findViewById(R.id.tv_detail_session_title);
        tvBadge = findViewById(R.id.tv_detail_session_badge);
        tvClass = findViewById(R.id.tv_detail_session_class);
        tvSchedule = findViewById(R.id.tv_detail_session_schedule);
        tvExpected = findViewById(R.id.tv_detail_session_total_expected);
        tvLateRule = findViewById(R.id.tv_detail_late_rule);
        tvFinishedTime = findViewById(R.id.tv_detail_finished_time);

        layoutUpcoming = findViewById(R.id.layout_state_upcoming);
        layoutActive = findViewById(R.id.layout_state_active);
        layoutFinished = findViewById(R.id.layout_state_finished);

        // Upcoming
        btnOpen = findViewById(R.id.btn_action_open_session);

        // Active
        tvActiveRatio = findViewById(R.id.tv_active_ratio);
        tvActivePresent = findViewById(R.id.tv_active_present);
        tvActiveLate = findViewById(R.id.tv_active_late);
        tvActiveUnchecked = findViewById(R.id.tv_active_unchecked);
        progressActive = findViewById(R.id.progress_active_session);
        btnScan = findViewById(R.id.btn_action_scan);
        btnManual = findViewById(R.id.btn_action_manual);
        btnFinish = findViewById(R.id.btn_action_finish_session);

        // Finished
        tvFinishedSummary = findViewById(R.id.tv_finished_summary_text);
        btnViewResult = findViewById(R.id.btn_action_view_result);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.session_detail_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        // Mở buổi
        btnOpen.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Mở buổi điểm danh?")
                    .setMessage("Bạn có muốn kích hoạt buổi điểm danh này để bắt đầu quét thẻ sinh viên?")
                    .setPositiveButton("Mở buổi", (dialog, which) -> {
                        AttendanceSession active=AttendanceRepository.getInstance().getActiveSession();
                        if(active!=null){showActive(active);return;}
                        com.example.th9.data.Tasks.run(this,btnOpen,()->{
                            AttendanceRepository.getInstance().openSession(sessionId);return true;
                        },ok->loadSessionData(),error->{
                            AttendanceSession opened=AttendanceRepository.getInstance().getActiveSession();
                            if(opened!=null)showActive(opened);else Toast.makeText(this,error,Toast.LENGTH_LONG).show();
                        });
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Quét thẻ
        btnScan.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(ScanActivity.EXTRA_SESSION_ID, sessionId);
            intent.putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_ATTENDANCE);
            startActivity(intent);
        });

        // Điểm danh thủ công
        btnManual.setOnClickListener(v -> {
            ManualAttendanceBottomSheetDialog dialog = ManualAttendanceBottomSheetDialog.newInstance(sessionId, () -> {
                loadSessionData();
            });
            dialog.show(getSupportFragmentManager(), "manual_attendance");
        });

        // Kết thúc buổi
        btnFinish.setOnClickListener(v -> {
            int unchecked = AttendanceRepository.getInstance().getUncheckedCount(sessionId);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Kết thúc buổi điểm danh?")
                    .setMessage("Hiện còn " + unchecked + " sinh viên CHƯA ĐIỂM DANH.\n\n"
                            + "Khi kết thúc, tất cả " + unchecked + " sinh viên này sẽ được tự động ghi nhận trạng thái là VẮNG MẶT.\n\n"
                            + "Bạn có chắc chắn muốn kết thúc buổi điểm danh không?")
                    .setPositiveButton("Kết thúc ngay", (dialog, which) -> {
                        com.example.th9.data.Tasks.run(this,btnFinish,()->{
                            AttendanceRepository.getInstance().finishSession(sessionId);return true;
                        },ok->{loadSessionData();Intent i=new Intent(this,SessionResultActivity.class);
                            i.putExtra(SessionResultActivity.EXTRA_SESSION_ID,sessionId);startActivity(i);});
                    })
                    .setNegativeButton("Tiếp tục điểm danh", null)
                    .show();
        });

        // Xem kết quả
        btnViewResult.setOnClickListener(v -> {
            Intent intent = new Intent(this, SessionResultActivity.class);
            intent.putExtra(SessionResultActivity.EXTRA_SESSION_ID, sessionId);
            startActivity(intent);
        });
    }

    private void loadSessionData() {
        session = AttendanceRepository.getInstance().getSessionById(sessionId);
        if (session == null) {finish();return;}

        tvTitle.setText(session.getTitle());
        ClassRoom cr = AttendanceRepository.getInstance().getClassById(session.getClassId());
        if (cr != null) {
            tvClass.setText(AttendanceRepository.getInstance().sessionClassLabel(session));
        }
        tvSchedule.setText(session.getStartTime() + " • " + session.getDate());
        tvExpected.setText("Sĩ số: " + session.getExpectedCount() + " SV");

        if (session.isEnableLateThreshold() && session.getLateThresholdTime() != null) {
            tvLateRule.setText("Quy tắc: Sau " + session.getLateThresholdTime() + " tính đi muộn");
            tvLateRule.setVisibility(View.VISIBLE);
        } else {
            tvLateRule.setVisibility(View.GONE);
        }

        if (session.getFinishedTime() != null) {
            tvFinishedTime.setText("Kết thúc lúc: " + session.getFinishedTime());
            tvFinishedTime.setVisibility(View.VISIBLE);
        } else {
            tvFinishedTime.setVisibility(View.GONE);
        }

        // Toggle state containers
        switch (session.getStatus()) {
            case DRAFT:
                layoutUpcoming.setVisibility(View.VISIBLE);
                layoutActive.setVisibility(View.GONE);
                layoutFinished.setVisibility(View.GONE);

                tvBadge.setText(R.string.session_status_upcoming);
                tvBadge.setBackgroundResource(R.drawable.bg_badge_unchecked);
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                break;

            case OPEN:
                layoutUpcoming.setVisibility(View.GONE);
                layoutActive.setVisibility(View.VISIBLE);
                layoutFinished.setVisibility(View.GONE);

                tvBadge.setText(R.string.session_status_active);
                tvBadge.setBackgroundResource(R.drawable.bg_badge_present);
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.on_status_present_container));

                int present = AttendanceRepository.getInstance().getPresentCount(sessionId);
                int late = AttendanceRepository.getInstance().getLateCount(sessionId);
                int unchecked = AttendanceRepository.getInstance().getUncheckedCount(sessionId);
                int checkedIn = present + late;
                int total = session.getExpectedCount();
                int pct = total > 0 ? (checkedIn * 100 / total) : 0;

                tvActiveRatio.setText(checkedIn + "/" + total + " đã điểm danh (" + pct + "%)");
                progressActive.setProgress(pct);

                tvActivePresent.setText(present + " Có mặt");
                tvActiveLate.setText(late + " Đi muộn");
                tvActiveUnchecked.setText(unchecked + " Chưa điểm");
                break;

            case CLOSED:
                layoutUpcoming.setVisibility(View.GONE);
                layoutActive.setVisibility(View.GONE);
                layoutFinished.setVisibility(View.VISIBLE);

                tvBadge.setText(R.string.session_status_finished);
                tvBadge.setBackgroundResource(R.drawable.bg_badge_late);
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.on_status_late_container));

                int fPresent = AttendanceRepository.getInstance().getPresentCount(sessionId);
                int fLate = AttendanceRepository.getInstance().getLateCount(sessionId);
                int fAbsent = AttendanceRepository.getInstance().getAbsentCount(sessionId);
                int fTotal = session.getExpectedCount();
                int fPct = fTotal > 0 ? ((fPresent + fLate) * 100 / fTotal) : 0;

                tvFinishedSummary.setText("• Có mặt: " + fPresent + "  • Đi muộn: " + fLate + "  • Vắng mặt: " + fAbsent + "\n• Tỷ lệ tham gia đạt: " + fPct + "%");
                break;
        }
    }
    private void showActive(AttendanceSession active){
        new MaterialAlertDialogBuilder(this).setTitle("Đã có buổi đang mở")
            .setMessage(active.getTitle()+"\n"+AttendanceRepository.getInstance().sessionClassLabel(active))
            .setPositiveButton("Tiếp tục buổi đang mở",(d,w)->{
                Intent i=new Intent(this,SessionDetailActivity.class);i.putExtra(EXTRA_SESSION_ID,active.getId());startActivity(i);
            }).setNegativeButton("Đóng",null).show();
    }
    @Override public boolean onCreateOptionsMenu(android.view.Menu menu){menu.add(0,7101,0,"Sửa buổi chưa mở");return true;}
    @Override public boolean onOptionsItemSelected(android.view.MenuItem item){
        if(item.getItemId()==7101){
            if(session!=null&&session.getStatus()==AttendanceSession.SessionStatus.DRAFT){Intent i=new Intent(this,SessionFormActivity.class);
                i.putExtra(SessionFormActivity.EXTRA_SESSION_ID,sessionId);startActivity(i);}
            else Toast.makeText(this,"Chỉ sửa được buổi chưa mở",Toast.LENGTH_SHORT).show();return true;
        }return super.onOptionsItemSelected(item);
    }
}


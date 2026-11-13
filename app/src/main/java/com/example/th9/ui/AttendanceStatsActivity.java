package com.example.th9.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.th9.R;
import com.example.th9.data.AttendanceExcel;
import com.example.th9.data.AttendanceReport;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.ClassRoom;
import com.example.th9.view.AttendanceReportTableView;
import com.example.th9.view.SimpleBarChartView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AttendanceStatsActivity extends DataActivity {
    private SessionFilters filters;
    private TextView rate, total, chartSummary, reportPeriod;
    private LinearLayout tables;
    private MaterialButton export;
    private SimpleBarChartView chart;
    private AttendanceReport displayedReport;
    private AttendanceReportExportModel exportModel;
    private final ActivityResultLauncher<String> createExcel = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(AttendanceExcel.MIME), uri -> exportModel().saveTo(uri));

    @Override protected void onReady(Bundle state) {
        setContentView(R.layout.activity_attendance_stats);
        Toolbar toolbar = findViewById(R.id.toolbar_stats);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
        rate = findViewById(R.id.tv_stats_avg_rate);
        total = findViewById(R.id.tv_stats_total_sessions);
        chart = findViewById(R.id.chart_view);
        chartSummary = findViewById(R.id.tv_stats_chart_summary);
        reportPeriod = findViewById(R.id.tv_stats_report_period);
        tables = findViewById(R.id.ll_stats_report_tables);
        export = findViewById(R.id.btn_stats_export_excel);

        filters = new SessionFilters(this, this::render);
        filters.restore(state);
        if (state == null && getIntent().getBundleExtra("filters") != null)
            filters.restore(getIntent().getBundleExtra("filters"));
        filters.classes(findViewById(R.id.chip_group_stats_class));
        render();
        export.setOnClickListener(v -> exportModel().prepare(displayedReport));
        exportModel().state().observe(this, status -> {
            updateExportButton();
            String message = exportModel().takeMessage();
            if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            String name = exportModel().takePickerName();
            if (name != null) {
                try { createExcel.launch(name); }
                catch (android.content.ActivityNotFoundException e) {
                    exportModel().cancel();
                    Toast.makeText(this, R.string.stats_report_export_failed, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private AttendanceReportExportModel exportModel() {
        if (exportModel == null) exportModel = new ViewModelProvider(this).get(AttendanceReportExportModel.class);
        return exportModel;
    }

    @Override protected void onDataResume() {
        if (filters == null) return;
        if (filters.classId != null && AttendanceRepository.getInstance().getClassById(filters.classId) == null)
            filters.classId = null;
        filters.classes(findViewById(R.id.chip_group_stats_class));
        render();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 7201, 0, "Lọc thời gian");
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 7201) { filters.dateDialog(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void render() {
        AttendanceRepository repo = AttendanceRepository.getInstance();
        List<ClassRoom> classes = new ArrayList<>();
        for (ClassRoom classroom : repo.getAllClasses()) {
            if (filters.classId == null || filters.classId.equals(classroom.getId())) classes.add(classroom);
        }
        List<AttendanceSession> sessions = new ArrayList<>();
        for (AttendanceSession session : repo.getAllSessions()) {
            long time = session.getScheduledAt();
            if (session.getStatus() != AttendanceSession.SessionStatus.DRAFT
                    && (filters.classId == null || filters.classId.equals(session.getClassId()))
                    && (filters.from == null || time >= filters.from)
                    && (filters.until == null || time < filters.until)) sessions.add(session);
        }
        sessions.sort(Comparator.comparingLong(AttendanceSession::getScheduledAt).thenComparing(AttendanceSession::getId));
        List<SimpleBarChartView.BarData> bars = new ArrayList<>();
        int present = 0, late = 0, absent = 0;
        for (AttendanceSession session : sessions) {
            if (session.getStatus() != AttendanceSession.SessionStatus.CLOSED) continue;
            int p = repo.getPresentCount(session.getId()), l = repo.getLateCount(session.getId()), a = repo.getAbsentCount(session.getId());
            present += p; late += l; absent += a;
            bars.add(new SimpleBarChartView.BarData(session.getDate(), p, l, a));
        }
        int denominator = present + late + absent;
        rate.setText(denominator == 0 ? "Chưa có dữ liệu" : String.format(Locale.getDefault(), "%.1f%%", 100.0 * (present + late) / denominator));
        total.setText(bars.size() + " buổi");
        chart.setData(bars);
        chartSummary.setText(bars.isEmpty() ? "Chưa có buổi đã kết thúc trong khoảng thời gian này."
                : "Có mặt: " + present + " · Đi trễ: " + late + " · Vắng: " + absent);

        displayedReport = AttendanceReport.capture(repo, classes, sessions, periodLabel(), getString(R.string.stats_report_legend));
        reportPeriod.setText(displayedReport.period);
        renderTables(displayedReport);
        updateExportButton();
    }

    private String periodLabel() {
        if (filters.from == null && filters.until == null) return "Tất cả thời gian";
        if (filters.from == null) return "Đến ngày " + AttendanceRepository.format(filters.until - 1, "dd/MM/yyyy");
        if (filters.until == null) return "Từ ngày " + AttendanceRepository.format(filters.from, "dd/MM/yyyy");
        return "Từ " + AttendanceRepository.format(filters.from, "dd/MM/yyyy") + " đến "
                + AttendanceRepository.format(filters.until - 1, "dd/MM/yyyy");
    }

    private void renderTables(AttendanceReport report) {
        tables.removeAllViews();
        if (report.tables.isEmpty()) tables.addView(label(getString(R.string.stats_report_empty), 14, false));
        for (AttendanceReport.ClassTable table : report.tables) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(16));
            card.setCardElevation(0);
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface));
            card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
            card.setStrokeWidth(dp(1));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = dp(12);
            card.setLayoutParams(cardParams);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(12), dp(12), dp(12), dp(12));
            content.addView(label(table.title, 16, true));
            content.addView(label(getString(R.string.stats_report_count, table.rows.size(), table.sessionCount), 12, false));
            if (table.sessionCount == 0) content.addView(label(getString(R.string.stats_report_no_sessions), 12, false));
            AttendanceReportTableView grid = new AttendanceReportTableView(this, table);
            LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gridParams.topMargin = dp(12);
            content.addView(grid, gridParams);
            if (table.rows.isEmpty()) content.addView(label(getString(R.string.stats_report_no_students), 14, false));
            card.addView(content);
            tables.addView(card);
        }
    }

    private TextView label(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(ContextCompat.getColor(this, bold ? R.color.text_primary : R.color.text_secondary));
        text.setPadding(0, 0, 0, dp(6));
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private void updateExportButton() {
        if (export == null) return;
        AttendanceReportExportModel.State state = exportModel().state().getValue();
        export.setEnabled(displayedReport != null && !displayedReport.tables.isEmpty() && (state == null || !state.busy));
        export.setText(state == null ? R.string.stats_report_export : state.buttonLabel);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state) {
        if (filters != null) filters.save(state);
        super.onSaveInstanceState(state);
    }
}

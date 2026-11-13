package com.example.th9.ui;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.example.th9.R;
import com.example.th9.data.AttendanceExcel;
import com.example.th9.data.AttendanceReport;
import com.example.th9.data.Tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Prepare a snapshot before opening the file picker; never requery filters while saving it. */
public final class AttendanceReportExportModel extends AndroidViewModel {
    public static final class State {
        public final boolean busy;
        public final int buttonLabel;
        State(boolean busy, int label) { this.busy = busy; this.buttonLabel = label; }
    }

    private final SavedStateHandle saved;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean preparing, saving, cleared;
    private String message;

    public AttendanceReportExportModel(@NonNull Application app, SavedStateHandle saved) {
        super(app);
        this.saved = saved;
        String path = saved.get("excel_path");
        if (path != null && (!Boolean.TRUE.equals(saved.get("excel_ready")) || !new File(path).isFile())) {
            new File(path).delete();
            clearPending();
            message = app.getString(R.string.stats_report_snapshot_missing);
        }
        publish();
        String destination = saved.get("excel_destination");
        if (destination != null) main.post(() -> { if (!cleared) saveTo(Uri.parse(destination)); });
    }

    public LiveData<State> state() { return state; }
    public String takeMessage() { String value = message; message = null; return value; }

    public void prepare(AttendanceReport report) {
        if (report == null || report.tables.isEmpty() || preparing || saving || saved.get("excel_path") != null) return;
        File directory = new File(getApplication().getCacheDir(), "attendance_exports");
        File file = new File(directory, UUID.randomUUID() + ".xlsx");
        String name = "diem_danh_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".xlsx";
        saved.set("excel_path", file.getAbsolutePath());
        saved.set("excel_name", name);
        saved.set("excel_ready", false);
        saved.set("excel_picker", false);
        preparing = true;
        publish();
        Tasks.IO.execute(() -> {
            boolean success = false;
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create export directory");
                try (OutputStream output = new FileOutputStream(file)) { AttendanceExcel.write(report, output); }
                success = true;
            } catch (Exception ignored) { file.delete(); }
            final boolean ready = success;
            main.post(() -> {
                preparing = false;
                if (cleared) { file.delete(); return; }
                if (ready) saved.set("excel_ready", true);
                else { clearPending(); message = getApplication().getString(R.string.stats_report_export_failed); }
                publish();
            });
        });
    }

    public String takePickerName() {
        if (preparing || saving || !Boolean.TRUE.equals(saved.get("excel_ready")) || Boolean.TRUE.equals(saved.get("excel_picker"))) return null;
        saved.set("excel_picker", true);
        return saved.get("excel_name");
    }

    public void saveTo(Uri uri) {
        if (uri == null) { cancel(); return; }
        String path = saved.get("excel_path");
        if (saving) return;
        if (path == null || !Boolean.TRUE.equals(saved.get("excel_ready"))) {
            clearPending();
            message = getApplication().getString(R.string.stats_report_snapshot_missing);
            publish();
            return;
        }
        File file = new File(path);
        try {
            getApplication().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) { /* Some document providers only grant temporary access. */ }
        saved.set("excel_destination", uri.toString());
        saving = true;
        publish();
        Tasks.IO.execute(() -> {
            boolean success = false;
            try (FileInputStream input = new FileInputStream(file);
                 OutputStream output = getApplication().getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("Cannot open destination");
                byte[] buffer = new byte[16384]; int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
                output.flush();
                success = true;
            } catch (Exception ignored) {
                success = false;
                // Keep the UI message readable; no internal paths or provider errors.
            } finally {
                file.delete();
                try {
                    getApplication().getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (SecurityException ignored) { /* No persisted grant from this provider. */ }
            }
            final boolean exported = success;
            main.post(() -> {
                saving = false;
                if (cleared) return;
                clearPending();
                message = getApplication().getString(exported ? R.string.stats_report_saved : R.string.stats_report_export_failed);
                publish();
            });
        });
    }

    public void cancel() {
        if (preparing || saving) return;
        String path = saved.get("excel_path");
        if (path != null) Tasks.IO.execute(() -> new File(path).delete());
        clearPending();
        publish();
    }

    private void clearPending() {
        saved.remove("excel_path"); saved.remove("excel_name"); saved.remove("excel_ready"); saved.remove("excel_picker");
        saved.remove("excel_destination");
    }

    private void publish() {
        state.setValue(new State(preparing || saving || saved.get("excel_path") != null,
                preparing ? R.string.stats_report_preparing : saving ? R.string.stats_report_saving : R.string.stats_report_export));
    }

    @Override protected void onCleared() {
        cleared = true;
        String path = saved.get("excel_path");
        if (path != null && !preparing && !saving) Tasks.IO.execute(() -> new File(path).delete());
    }
}

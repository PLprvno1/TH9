package com.example.th9.data;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Serializes app writes; Room transactions remain the authority for all state transitions. */
public final class Tasks {
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static <T> void run(Activity owner, View button, Callable<T> task, Consumer<T> success) {
        run(owner, button, task, success, error -> Toast.makeText(owner, error, Toast.LENGTH_LONG).show());
    }
    public static <T> void run(Activity owner, View button, Callable<T> task, Consumer<T> success, Consumer<String> failure) {
        if (button != null) {
            if (!button.isEnabled()) return;
            button.setEnabled(false);
        }
        IO.execute(() -> {
            T result = null;
            String problem = null;
            try { result = task.call(); }
            catch (Exception e) { problem = e.getMessage() == null ? "Không thể lưu dữ liệu" : e.getMessage(); }
            final T value = result;
            final String error = problem;
            MAIN.post(() -> {
                if (owner.isFinishing() || owner.isDestroyed()) return;
                if (button != null) button.setEnabled(true);
                if (error == null) success.accept(value); else failure.accept(error);
            });
        });
    }
}

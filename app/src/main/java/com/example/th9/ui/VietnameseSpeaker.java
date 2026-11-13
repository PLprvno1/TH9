package com.example.th9.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.example.th9.R;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/** Keeps speech requests until initialization completes and stops reading when the screen closes. */
public final class VietnameseSpeaker implements DefaultLifecycleObserver {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Deque<String> pending = new ArrayDeque<>();
    private TextToSpeech engine;
    private boolean active, ready, failed, closed, errorShown;
    private int errorMessage = R.string.speech_unavailable;
    private long utteranceId;

    public VietnameseSpeaker(AppCompatActivity owner) {
        context = owner.getApplicationContext();
        owner.getLifecycle().addObserver(this);
        try {
            engine = new TextToSpeech(context, status -> main.post(() -> initialize(status)));
        } catch (RuntimeException e) {
            failed = true;
        }
    }

    private void initialize(int status) {
        if (closed || engine == null) return;
        if (status != TextToSpeech.SUCCESS) {
            unavailable(R.string.speech_unavailable);
            return;
        }
        int language = engine.setLanguage(Locale.forLanguageTag("vi-VN"));
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            unavailable(R.string.speech_vietnamese_unavailable);
            return;
        }
        engine.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        ready = true;
        if (active) {
            while (!pending.isEmpty()) play(pending.removeFirst(), TextToSpeech.QUEUE_ADD);
        }
    }

    /** Queue scan announcements; interrupt an earlier report when the read button is tapped again. */
    public void speak(String text, boolean interrupt) {
        if (closed || !active || text == null || text.trim().isEmpty()) return;
        if (failed) {
            showError();
            return;
        }
        if (interrupt) pending.clear();
        if (!ready) {
            pending.addLast(text);
            return;
        }
        play(text, interrupt ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD);
    }

    private void play(String text, int queueMode) {
        if (engine.speak(text, queueMode, null, "attendance-" + (++utteranceId)) == TextToSpeech.ERROR) {
            errorMessage = R.string.speech_unavailable;
            showError();
        }
    }

    private void unavailable(int message) {
        failed = true;
        errorMessage = message;
        boolean requested = !pending.isEmpty();
        pending.clear();
        if (requested && active) showError();
    }

    private void showError() {
        if (!errorShown) {
            errorShown = true;
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onResume(@NonNull LifecycleOwner owner) {
        active = true;
    }

    @Override public void onPause(@NonNull LifecycleOwner owner) {
        active = false;
        pending.clear();
        if (engine != null) engine.stop();
    }

    @Override public void onDestroy(@NonNull LifecycleOwner owner) {
        closed = true;
        active = false;
        pending.clear();
        if (engine != null) {
            engine.stop();
            engine.shutdown();
            engine = null;
        }
        owner.getLifecycle().removeObserver(this);
    }
}

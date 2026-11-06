package com.example.th9;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.th9.data.AttendanceRepository;

public class AttendanceApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(getSharedPreferences("settings", MODE_PRIVATE)
                .getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        AttendanceRepository.configure(this);
    }
}

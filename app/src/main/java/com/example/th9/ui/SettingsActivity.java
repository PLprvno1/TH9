package com.example.th9.ui;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import com.example.th9.R;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends DataActivity {
    @Override protected void onReady(Bundle state) {
        setContentView(R.layout.activity_settings);
        Toolbar toolbar=findViewById(R.id.toolbar_settings);setSupportActionBar(toolbar);
        if(getSupportActionBar()!=null)getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v->finish());
        SharedPreferences p=getSharedPreferences("settings",MODE_PRIVATE);
        RadioGroup theme=findViewById(R.id.rg_settings_theme);
        int mode=p.getInt("theme",AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        theme.check(mode==AppCompatDelegate.MODE_NIGHT_NO?R.id.rb_theme_light:mode==AppCompatDelegate.MODE_NIGHT_YES?R.id.rb_theme_dark:R.id.rb_theme_system);
        theme.setOnCheckedChangeListener((g,id)->{
            int selected=id==R.id.rb_theme_light?AppCompatDelegate.MODE_NIGHT_NO:id==R.id.rb_theme_dark?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            p.edit().putInt("theme",selected).apply();AppCompatDelegate.setDefaultNightMode(selected);
        });
        MaterialSwitch sound=findViewById(R.id.switch_sound),vibration=findViewById(R.id.switch_vibrate);
        sound.setChecked(p.getBoolean("sound",true));vibration.setChecked(p.getBoolean("vibrate",true));
        sound.setOnCheckedChangeListener((v,checked)->p.edit().putBoolean("sound",checked).apply());
        vibration.setOnCheckedChangeListener((v,checked)->p.edit().putBoolean("vibrate",checked).apply());
    }
}

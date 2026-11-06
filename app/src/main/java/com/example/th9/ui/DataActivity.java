package com.example.th9.ui;

import android.os.Bundle;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.data.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Restored activities also wait for the Room snapshot after process recreation. */
public abstract class DataActivity extends AppCompatActivity {
    private boolean ready, resumed;
    private Bundle pendingRestore;
    @Override protected final void onCreate(Bundle state) {
        super.onCreate(state);
        if(AttendanceRepository.getInstance().isInitialized()){
            finishInitialization(state);return;
        }
        setContentView(new ProgressBar(this));
        initialize(state);
    }
    private void initialize(Bundle state) {
        Tasks.run(this, null, () -> { AttendanceRepository.getInstance().initialize(); return true; }, value -> {
            finishInitialization(state);
        }, error -> new MaterialAlertDialogBuilder(this).setTitle("Không thể mở dữ liệu")
                .setMessage(error).setPositiveButton("Thử lại", (d,w) -> initialize(state)).setNegativeButton("Đóng",(d,w)->finish()).show());
    }
    private void finishInitialization(Bundle state){
        onReady(state);ready=true;
        boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat controller=new androidx.core.view.WindowInsetsControllerCompat(getWindow(),getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!dark && !(this instanceof ScanActivity));
        controller.setAppearanceLightNavigationBars(!dark && !(this instanceof ScanActivity));
        android.view.View root=((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        if(root!=null){
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
                androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout());
                androidx.core.graphics.Insets ime=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
                v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return insets;
            });androidx.core.view.ViewCompat.requestApplyInsets(root);
        }
        if(pendingRestore!=null){super.onRestoreInstanceState(pendingRestore);pendingRestore=null;}
        if(resumed)onDataResume();
    }
    @Override protected void onRestoreInstanceState(Bundle state){if(ready)super.onRestoreInstanceState(state);else pendingRestore=state;}
    protected abstract void onReady(Bundle state);
    @Override protected final void onResume() { super.onResume(); resumed=true; if (ready) onDataResume(); }
    @Override protected void onPause() { resumed=false; super.onPause(); }
    protected void onDataResume() {}
}

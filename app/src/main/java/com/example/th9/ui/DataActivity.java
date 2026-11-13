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
    private String screenUserId;
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
        }, error -> new MaterialAlertDialogBuilder(this).setTitle("Không thể tải thông tin")
                .setMessage("Không thể tải thông tin lúc này. Vui lòng thử lại.").setPositiveButton("Thử lại", (d,w) -> initialize(state)).setNegativeButton("Đóng",(d,w)->finish()).show());
    }
    private void finishInitialization(Bundle state){
        if(!authorizeScreen())return;
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
    private boolean authorizeScreen(){
        if(this instanceof LoginActivity)return true;
        com.example.th9.model.Account account=AttendanceRepository.getInstance().currentAccount();
        if(account==null){startActivity(new android.content.Intent(this,LoginActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();return false;}
        if(screenUserId!=null&&!screenUserId.equals(account.id)){startActivity(new android.content.Intent(this,com.example.th9.MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();return false;}
        boolean adminOnly=this instanceof ClassStudentSelectionActivity||this instanceof ScheduleFormActivity||this instanceof StudentFormActivity||this instanceof StudentsActivity||this instanceof ClassFormActivity||this instanceof CsvImportPreviewActivity||this instanceof TeacherAccountsActivity;
        if(adminOnly&&!account.isAdmin()){android.widget.Toast.makeText(this,"Bạn cần đăng nhập bằng tài khoản quản trị để tiếp tục.",android.widget.Toast.LENGTH_LONG).show();finish();return false;}
        boolean attendanceScreen=this instanceof SessionFormActivity||(this instanceof ScanActivity&&getIntent().getIntExtra(ScanActivity.EXTRA_MODE,ScanActivity.MODE_ATTENDANCE)==ScanActivity.MODE_ATTENDANCE);
        if(account.isAdmin()&&attendanceScreen){android.widget.Toast.makeText(this,"Vui lòng đăng nhập bằng tài khoản giảng viên để điểm danh.",android.widget.Toast.LENGTH_SHORT).show();finish();return false;}
        screenUserId=account.id;return true;
    }
    protected abstract void onReady(Bundle state);
    @Override protected final void onResume() { super.onResume(); resumed=true; if (ready&&authorizeScreen()) onDataResume(); }
    @Override protected void onPause() { resumed=false; super.onPause(); }
    protected void onDataResume() {}
}

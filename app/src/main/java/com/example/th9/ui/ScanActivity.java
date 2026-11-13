package com.example.th9.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.Camera;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.example.th9.R;
import com.example.th9.camera.CameraScanner;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ScanActivity extends DataActivity {
    public static final String EXTRA_MODE="extra_mode",EXTRA_SESSION_ID="extra_session_id";
    public static final int MODE_ATTENDANCE=1,MODE_VERIFY_CARD=3;
    private int mode;
    private String sessionId;
    private CameraScanner scanner;
    private Camera camera;
    private VietnameseSpeaker speaker;
    private boolean modal,busy,torch,uiReady,activityResumed;
    private String lastCode;
    private long lastCodeAt;
    private TextView title,subtitle,status,info,time,error;
    private View errorOverlay,reticle;
    private ImageButton flash;
    private final ActivityResultLauncher<String> permission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{
        if(granted)startCamera();else cameraError("Bạn chưa cấp quyền camera");
    });
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_scan);
        speaker=new VietnameseSpeaker(this);
        mode=getIntent().getIntExtra(EXTRA_MODE,MODE_ATTENDANCE)==MODE_ATTENDANCE?MODE_ATTENDANCE:MODE_VERIFY_CARD;sessionId=getIntent().getStringExtra(EXTRA_SESSION_ID);
        title=findViewById(R.id.tv_scan_header_title);subtitle=findViewById(R.id.tv_scan_header_subtitle);
        status=findViewById(R.id.tv_result_status_title);info=findViewById(R.id.tv_result_student_info);time=findViewById(R.id.tv_result_time_badge);
        error=findViewById(R.id.tv_camera_error);errorOverlay=findViewById(R.id.ll_camera_error_overlay);reticle=findViewById(R.id.fl_reticle_frame);
        flash=findViewById(R.id.btn_scan_flash);flash.setEnabled(false);
        findViewById(R.id.btn_scan_back).setOnClickListener(v->finish());
        findViewById(R.id.ll_scan_actions_attendance).setVisibility(mode==MODE_ATTENDANCE?View.VISIBLE:View.GONE);
        findViewById(R.id.btn_fallback_manual).setVisibility(mode==MODE_ATTENDANCE?View.VISIBLE:View.GONE);
        findViewById(R.id.btn_scan_manual_input).setOnClickListener(v->manual());
        findViewById(R.id.btn_fallback_manual).setOnClickListener(v->manual());
        findViewById(R.id.btn_finish_scan).setOnClickListener(v->finishSession());
        findViewById(R.id.btn_camera_retry).setOnClickListener(v->{
            boolean asked=getSharedPreferences("settings",MODE_PRIVATE).getBoolean("camera_asked",false);
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED && asked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
            else requestCamera();
        });
        flash.setOnClickListener(v->{if(camera!=null&&camera.getCameraInfo().hasFlashUnit()){
            torch=!torch;camera.getCameraControl().enableTorch(torch);
            flash.setImageResource(torch?R.drawable.ic_flash_on:R.drawable.ic_flash_off);
        }});
        getSupportFragmentManager().setFragmentResultListener("attendance_changed",this,(key,result)->refresh());
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks(){
            @Override public void onFragmentDetached(androidx.fragment.app.FragmentManager manager,androidx.fragment.app.Fragment fragment){
                if(fragment instanceof ManualAttendanceBottomSheetDialog){modal=false;pauseScanner();refresh();}
            }
        },false);
        modal=getSupportFragmentManager().findFragmentByTag("manual_attendance")!=null;
        uiReady=true;refresh();requestCamera();
    }
    private void requestCamera(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
        else {getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("camera_asked",true).apply();permission.launch(Manifest.permission.CAMERA);}
    }
    private void startCamera(){
        if(!uiReady||isFinishing())return;
        if(scanner!=null)scanner.close();
        camera=null;torch=false;flash.setImageResource(R.drawable.ic_flash_off);
        errorOverlay.setVisibility(View.GONE);reticle.setVisibility(View.VISIBLE);flash.setEnabled(false);
        PreviewView preview=findViewById(R.id.camera_preview);
        scanner=new CameraScanner(this,this,preview,new CameraScanner.Listener(){
            @Override public void onCode(String raw,String format){handleCode(raw,format);}
            @Override public void onMessage(String message){ /* Keep decoder guidance out of the scan interface. */ }
            @Override public void onCamera(Camera value){camera=value;flash.setEnabled(value.getCameraInfo().hasFlashUnit());}
            @Override public void onError(String message){cameraError(message);}
        });
        pauseScanner();scanner.start();
    }
    private void cameraError(String message){
        if(!uiReady)return;error.setText(message);errorOverlay.setVisibility(View.VISIBLE);reticle.setVisibility(View.GONE);flash.setEnabled(false);
        ((TextView)findViewById(R.id.btn_camera_retry)).setText(message.startsWith("Bạn chưa cấp quyền")&&!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)?"Mở cài đặt ứng dụng":"Thử lại");
    }
    private void handleCode(String raw,String format){
        if(modal||busy)return;
        long elapsed=SystemClock.elapsedRealtime();if(raw.equals(lastCode)&&elapsed-lastCodeAt<2500)return;
        lastCode=raw;lastCodeAt=elapsed;
        if(mode==MODE_VERIFY_CARD){
            busy=true;pauseScanner();
            Tasks.run(this,null,()->AttendanceRepository.getInstance().verifyCard(raw,format),student->{
                busy=false;pauseScanner();show("Thẻ trùng khớp",student.getFullName()+" · MSSV: "+student.getMssv()+"\nNgày sinh: "+(student.getBirthDate()==null?"—":student.getBirthDate()),"Chỉ tra cứu thông tin thẻ");
                speaker.speak(getString(R.string.speech_scan_success,student.getFullName()),false);
            },message->{busy=false;pauseScanner();show(message,raw,"");});return;
        }
        busy=true;pauseScanner();
        Tasks.run(this,null,()->AttendanceRepository.getInstance().scan(sessionId,raw,format,System.currentTimeMillis()),record->{
            busy=false;pauseScanner();show("Ghi nhận thành công",record.getNameSnapshot()+" · "+record.getStatusDisplayName(),record.getCheckInTime());feedback();refresh();
            speaker.speak(getString(R.string.speech_scan_success,record.getNameSnapshot()),false);
        },message->{busy=false;pauseScanner();show(message,raw,"");refresh();});
    }
    private void show(String result,String details,String recorded){
        status.setText(result);info.setText(details);info.setVisibility(View.VISIBLE);time.setText(recorded);
        ((ImageView)findViewById(R.id.iv_result_icon)).setImageResource((result.startsWith("Ghi nhận thành công")||result.equals("Thẻ trùng khớp"))?R.drawable.ic_check_circle:result.startsWith("Đã đọc")?R.drawable.ic_barcode:R.drawable.ic_cancel);
        View card=findViewById(R.id.card_scan_result);card.animate().cancel();card.setAlpha(.4f);card.animate().alpha(1f).setDuration(220).start();
    }
    private void refresh(){
        if(mode==MODE_VERIFY_CARD){title.setText("Quét kiểm tra thẻ");subtitle.setText("Tra cứu hồ sơ · Không ghi điểm danh");return;}
        AttendanceSession s=AttendanceRepository.getInstance().getSessionById(sessionId);
        if(s==null||!AttendanceRepository.getInstance().canControlSession(sessionId)){finish();return;}
        if(s.getStatus()==AttendanceSession.SessionStatus.CLOSED){resultScreen();return;}
        if(s.getStatus()!=AttendanceSession.SessionStatus.OPEN){finish();return;}
        title.setText(s.getTitle());int checked=AttendanceRepository.getInstance().getCheckedInTotal(sessionId);
        subtitle.setText(AttendanceRepository.getInstance().sessionClassLabel(s)+" · "+checked+"/"+s.getExpectedCount());
        subtitle.animate().cancel();subtitle.setAlpha(.5f);subtitle.animate().alpha(1).setDuration(250).start();
    }
    private void manual(){
        if(modal||busy||mode!=MODE_ATTENDANCE)return;modal=true;pauseScanner();
        ManualAttendanceBottomSheetDialog.newInstance(sessionId,this::refresh).show(getSupportFragmentManager(),"manual_attendance");
    }
    private void finishSession(){
        if(modal||busy)return;modal=true;pauseScanner();int pending=AttendanceRepository.getInstance().getUncheckedCount(sessionId);
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle("Kết thúc buổi?")
            .setMessage("Còn "+pending+" sinh viên chưa điểm danh. Những sinh viên này sẽ được ghi Vắng.")
            .setPositiveButton("Kết thúc",(d,w)->{busy=true;Tasks.run(this,findViewById(R.id.btn_finish_scan),()->{
                AttendanceRepository.getInstance().finishSession(sessionId);return true;
            },ok->resultScreen(),message->{busy=false;pauseScanner();show(message,"","");});})
            .setNegativeButton("Tiếp tục",null).create();
        dialog.setOnDismissListener(d->{modal=false;pauseScanner();});dialog.show();
    }
    private void resultScreen(){Intent i=new Intent(this,SessionResultActivity.class);i.putExtra(SessionResultActivity.EXTRA_SESSION_ID,sessionId);startActivity(i);finish();}
    // DataActivity calls onDataResume from inside onResume, before AndroidX necessarily
    // dispatches ON_RESUME. Using the registry there can leave analysis paused forever
    // even though CameraX later starts its preview. Resume explicitly in onPostResume.
    private void pauseScanner(){if(scanner!=null)scanner.setPaused(modal||busy||!activityResumed);}
    private void feedback(){
        android.content.SharedPreferences p=getSharedPreferences("settings",MODE_PRIVATE);
        if(p.getBoolean("sound",true)){
            android.media.ToneGenerator tone=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,70);
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,120);new Handler(Looper.getMainLooper()).postDelayed(tone::release,200);
        }
        if(p.getBoolean("vibrate",true)){
            Vibrator vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(vibrator!=null&&vibrator.hasVibrator()){if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(80,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(80);}
        }
    }
    @Override protected void onDataResume(){if(uiReady){refresh();pauseScanner();if(scanner==null&&ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();}}
    @Override protected void onPostResume(){super.onPostResume();activityResumed=true;pauseScanner();}
    @Override protected void onPause(){activityResumed=false;pauseScanner();super.onPause();}
    @Override protected void onDestroy(){if(scanner!=null)scanner.close();super.onDestroy();}
}

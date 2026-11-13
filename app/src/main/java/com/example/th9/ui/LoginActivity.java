package com.example.th9.ui;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import com.example.th9.R;
import com.example.th9.MainActivity;
import com.example.th9.data.*;
public class LoginActivity extends DataActivity {
    private boolean setup;
    private boolean leaving;
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_login);AttendanceRepository repo=AttendanceRepository.getInstance();setup=repo.needsAdminSetup();
        ((TextView)findViewById(R.id.login_title)).setText(setup?"Thiết lập quản trị viên":"Chào mừng trở lại");
        ((TextView)findViewById(R.id.login_description)).setText(setup?"Tạo tài khoản quản trị đầu tiên để quản lý sinh viên, lớp và giảng viên. Mật khẩu cần có ít nhất 8 ký tự.":"Đăng nhập bằng tài khoản quản trị hoặc giảng viên đã được tạo trên thiết bị này.");
        findViewById(R.id.login_name_layout).setVisibility(setup?View.VISIBLE:View.GONE);findViewById(R.id.login_confirm_layout).setVisibility(setup?View.VISIBLE:View.GONE);
        ((TextView)findViewById(R.id.login_submit)).setText(setup?"Tạo tài khoản quản trị":"Đăng nhập");
        LoginViewModel model=new androidx.lifecycle.ViewModelProvider(this).get(LoginViewModel.class);
        model.state.observe(this,result->{findViewById(R.id.login_submit).setEnabled(!result.busy);((TextView)findViewById(R.id.login_error)).setText(result.error);if(result.success){((EditText)findViewById(R.id.login_password)).setText("");((EditText)findViewById(R.id.login_confirm)).setText("");openMain();}});
        findViewById(R.id.login_submit).setOnClickListener(v->{
            String username=((EditText)findViewById(R.id.login_username)).getText().toString(),password=((EditText)findViewById(R.id.login_password)).getText().toString();
            String name=((EditText)findViewById(R.id.login_name)).getText().toString(),confirm=((EditText)findViewById(R.id.login_confirm)).getText().toString();
            TextView error=findViewById(R.id.login_error);error.setText("");
            if(setup&&!password.equals(confirm)){error.setText("Mật khẩu nhập lại không khớp");return;}
            model.submit(setup,username,name,password);
        });
        findViewById(R.id.login_title).setAlpha(0);findViewById(R.id.login_title).animate().alpha(1).setDuration(220).start();
    }
    @Override protected void onDataResume(){if(AttendanceRepository.getInstance().currentAccount()!=null)openMain();}
    private void openMain(){if(leaving)return;leaving=true;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
}

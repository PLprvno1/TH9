package com.example.th9.ui;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import com.example.th9.R;
import com.example.th9.data.*;
import com.example.th9.model.*;
import com.example.th9.view.StudentCardView;
import java.io.*;
public class StudentCardActivity extends DataActivity {
    private String studentId;private StudentCardView card;private boolean ready;
    private android.net.Uri pendingSave;
    private final ActivityResultLauncher<String> save=registerForActivityResult(new ActivityResultContracts.CreateDocument("image/png"),uri->{
        if(uri!=null){pendingSave=uri;writePendingSave();}
    });
    private void writePendingSave(){
        if(!ready||pendingSave==null)return;android.net.Uri uri=pendingSave;pendingSave=null;Bitmap image=card.export();
        Tasks.run(this,null,()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Không thể ghi ảnh");}finally{image.recycle();}return true;
        },ok->Toast.makeText(this,"Đã lưu ảnh thẻ",Toast.LENGTH_LONG).show());
    }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state){if(pendingSave!=null)state.putString("pending_save",pendingSave.toString());super.onSaveInstanceState(state);}
    @Override protected void onReady(Bundle state){
        if(state!=null&&state.getString("pending_save")!=null)pendingSave=android.net.Uri.parse(state.getString("pending_save"));
        setContentView(R.layout.activity_student_card);studentId=getIntent().getStringExtra(StudentProfileActivity.EXTRA_STUDENT_ID);card=findViewById(R.id.student_card);
        if(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)card.post(()->{
            android.view.View viewport=(android.view.View)card.getParent().getParent();int spacing=Math.round(32*getResources().getDisplayMetrics().density);
            int width=Math.min(viewport.getWidth()-spacing,Math.round(Math.max(1,viewport.getHeight()-spacing)*1600f/1050));
            if(width>0){android.widget.LinearLayout.LayoutParams params=(android.widget.LinearLayout.LayoutParams)card.getLayoutParams();params.width=width;params.gravity=android.view.Gravity.CENTER_HORIZONTAL;card.setLayoutParams(params);}
        });
        androidx.appcompat.widget.Toolbar bar=findViewById(R.id.toolbar_card);setSupportActionBar(bar);getSupportActionBar().setTitle("Thẻ sinh viên điện tử");getSupportActionBar().setDisplayHomeAsUpEnabled(true);bar.setNavigationOnClickListener(v->finish());
        findViewById(R.id.card_save).setOnClickListener(v->{if(ready)save.launch("the-sinh-vien-"+studentId+".png");});
        findViewById(R.id.card_share).setOnClickListener(v->share());
        findViewById(R.id.card_verify).setOnClickListener(v->startActivity(new Intent(this,ScanActivity.class).putExtra(ScanActivity.EXTRA_MODE,ScanActivity.MODE_VERIFY_CARD)));
        if(!AttendanceRepository.getInstance().isAdmin())findViewById(R.id.card_edit).setVisibility(android.view.View.GONE);
        findViewById(R.id.card_edit).setOnClickListener(v->startActivity(new Intent(this,StudentFormActivity.class).putExtra(StudentFormActivity.EXTRA_STUDENT_ID,studentId)));
    }
    @Override protected void onDataResume(){
        Student s=AttendanceRepository.getInstance().getStudentById(studentId);ready=false;findViewById(R.id.student_card).setVisibility(android.view.View.GONE);
        if(s==null){ready=false;finish();((TextView)findViewById(R.id.card_state)).setText("Không tìm thấy hồ sơ sinh viên");return;}
        try{ClassRoom c=AttendanceRepository.getInstance().getClassById(s.getPrimaryClassId());card.bind(s,c==null?null:c.getName());ready=true;
            findViewById(R.id.card_state).setOnClickListener(null);((TextView)findViewById(R.id.card_state)).setText("Thẻ đã sẵn sàng · Mã vạch dùng để điểm danh");card.setVisibility(android.view.View.VISIBLE);card.setAlpha(0);card.animate().alpha(1).setDuration(220).start();writePendingSave();
        }catch(Exception e){((TextView)findViewById(R.id.card_state)).setText("Không thể tạo thẻ. Vui lòng thử lại.");}
    }
    private void share(){if(!ready)return;Bitmap image=card.export();Tasks.run(this,findViewById(R.id.card_share),()->{
        File folder=new File(getCacheDir(),"exports");if(!folder.exists()&&!folder.mkdirs())throw new IOException("Không thể tạo thư mục chia sẻ");File file=new File(folder,"card-"+java.util.UUID.randomUUID()+".png");
        try(OutputStream out=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Không thể tạo PNG");}finally{image.recycle();}
        return FileProvider.getUriForFile(this,getPackageName()+".files",file);
    },uri->{Intent intent=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("Thẻ sinh viên",uri));startActivity(Intent.createChooser(intent,"Chia sẻ thẻ sinh viên"));});}
}

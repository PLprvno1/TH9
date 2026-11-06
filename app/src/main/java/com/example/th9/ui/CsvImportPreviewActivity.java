package com.example.th9.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import com.example.th9.R;
import com.example.th9.adapter.CsvPreviewAdapter;
import com.example.th9.data.*;
import com.example.th9.model.ClassRoom;
import com.google.android.material.button.MaterialButton;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvImportPreviewActivity extends DataActivity {
    public static final String EXTRA_CLASS_ID="extra_class_id";
    private String classId;
    private Uri selectedUri;
    private List<CsvTransfer.Row> rows;
    private MaterialButton pick,confirm;
    private TextView summary;
    private CsvPreviewAdapter adapter;
    private final ActivityResultLauncher<String[]> picker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
        if(uri==null){Toast.makeText(this,"Đã hủy chọn tệp",Toast.LENGTH_SHORT).show();return;}
        try{getContentResolver().takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        selectedUri=uri;loadFile();
    });
    @Override protected void onReady(Bundle state){
        setContentView(R.layout.activity_csv_import_preview);classId=getIntent().getStringExtra(EXTRA_CLASS_ID);
        ClassRoom c=AttendanceRepository.getInstance().getClassById(classId);if(c==null){finish();return;}
        Toolbar toolbar=findViewById(R.id.toolbar_csv);setSupportActionBar(toolbar);
        if(getSupportActionBar()!=null){getSupportActionBar().setDisplayHomeAsUpEnabled(true);getSupportActionBar().setTitle("Nhập CSV · "+c.getName());}
        toolbar.setNavigationOnClickListener(v->finish());
        pick=findViewById(R.id.btn_csv_pick_file);confirm=findViewById(R.id.btn_csv_confirm_import);summary=findViewById(R.id.tv_csv_summary);
        findViewById(R.id.btn_csv_sample_file).setVisibility(View.GONE);
        adapter=new CsvPreviewAdapter(this);RecyclerView list=findViewById(R.id.rv_csv_rows);
        list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);
        summary.setText("Lớp đích: "+c.getName()+"\nChọn tệp UTF-8 có header mssv,ho_ten,ma_the (ma_the tùy chọn).");
        confirm.setEnabled(false);confirm.setText("Xác nhận nhập");
        pick.setOnClickListener(v->picker.launch(new String[]{"text/*","application/csv","application/vnd.ms-excel","application/octet-stream"}));
        confirm.setOnClickListener(v->{if(rows==null)return;pick.setEnabled(false);
            Tasks.run(this,confirm,()->AttendanceRepository.getInstance().importCsv(classId,rows,true),preview->{
                Toast.makeText(this,"Đã nhập danh sách vào lớp",Toast.LENGTH_SHORT).show();finish();
            },error->{pick.setEnabled(true);Toast.makeText(this,error,Toast.LENGTH_LONG).show();loadFile();});
        });
        if(state!=null&&state.getString("uri")!=null){selectedUri=Uri.parse(state.getString("uri"));loadFile();}
    }
    private void loadFile(){
        confirm.setEnabled(false);rows=null;adapter.setRows(Collections.emptyList());
        summary.setText("Đang đọc và kiểm tra tệp…");
        Tasks.run(this,pick,()->{
            try(InputStream input=getContentResolver().openInputStream(selectedUri)){
                if(input==null)throw new IOException("Không mở được tệp");
                return CsvTransfer.parse(new InputStreamReader(input,StandardCharsets.UTF_8));
            }
        },parsed->{rows=parsed;preview();},error->{summary.setText("Lỗi đọc CSV: "+error+"\nHãy sửa tệp và chọn lại.");confirm.setEnabled(false);});
    }
    private void preview(){
        Tasks.run(this,pick,()->AttendanceRepository.getInstance().importCsv(classId,rows,false),preview->{
            List<CsvPreviewAdapter.CsvRowItem> items=new ArrayList<>();
            for(int i=0;i<rows.size();i++){
                CsvTransfer.Row row=rows.get(i);String detail=preview.lines.get(i).split("\n",2)[1];
                items.add(new CsvPreviewAdapter.CsvRowItem(row.line,row.mssv,row.name,!detail.startsWith("Lỗi:"),detail));
            }
            adapter.setRows(items);summary.setText("Lớp đích: "+AttendanceRepository.getInstance().getClassById(classId).getName()+"\n"+rows.size()+" dòng · "+preview.errors+" dòng lỗi"+(preview.errors>0?"\nHãy sửa tệp và chọn lại.":"\nChỉ ghi khi bạn xác nhận."));
            confirm.setEnabled(preview.errors==0);confirm.setText("Xác nhận nhập "+rows.size()+" dòng");
        },error->{summary.setText(error);confirm.setEnabled(false);});
    }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state){if(selectedUri!=null)state.putString("uri",selectedUri.toString());super.onSaveInstanceState(state);}
}

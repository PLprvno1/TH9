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
        setContentView(R.layout.activity_csv_import_preview);
        Toolbar toolbar=findViewById(R.id.toolbar_csv);setSupportActionBar(toolbar);
        if(getSupportActionBar()!=null){getSupportActionBar().setDisplayHomeAsUpEnabled(true);getSupportActionBar().setTitle("Nhập danh sách sinh viên");}
        toolbar.setNavigationOnClickListener(v->finish());
        pick=findViewById(R.id.btn_csv_pick_file);confirm=findViewById(R.id.btn_csv_confirm_import);summary=findViewById(R.id.tv_csv_summary);
        findViewById(R.id.btn_csv_sample_file).setVisibility(View.GONE);
        adapter=new CsvPreviewAdapter(this);RecyclerView list=findViewById(R.id.rv_csv_rows);
        list.setLayoutManager(new LinearLayoutManager(this));list.setAdapter(adapter);
        summary.setText("Danh sách sẽ được thêm vào Quản lý sinh viên và chưa thuộc lớp nào. MSSV được tự cấp theo niên khóa. Tệp cần có các cột ho_ten, ngay_sinh, nien_khoa, gioi_tinh, so_dien_thoai (10 chữ số).");
        confirm.setEnabled(false);confirm.setText("Xác nhận nhập");
        pick.setOnClickListener(v->picker.launch(new String[]{"text/*","application/csv","application/vnd.ms-excel","application/octet-stream"}));
        confirm.setOnClickListener(v->{if(rows==null)return;pick.setEnabled(false);
            Tasks.run(this,confirm,()->AttendanceRepository.getInstance().importStudentsCsv(rows,true),preview->{
                Toast.makeText(this,"Đã thêm sinh viên vào danh sách",Toast.LENGTH_SHORT).show();finish();
            },error->{pick.setEnabled(true);Toast.makeText(this,error,Toast.LENGTH_LONG).show();loadFile();});
        });
        if(state!=null&&state.getString("uri")!=null){selectedUri=Uri.parse(state.getString("uri"));loadFile();}
    }
    private void loadFile(){
        confirm.setEnabled(false);rows=null;adapter.setRows(Collections.emptyList());
        summary.setText("Đang đọc tệp…");
        Tasks.run(this,pick,()->{
            try(InputStream input=getContentResolver().openInputStream(selectedUri)){
                if(input==null)throw new IOException("Không mở được tệp");
                return CsvTransfer.parse(new InputStreamReader(input,StandardCharsets.UTF_8));
            }
        },parsed->{rows=parsed;preview();},error->{summary.setText("Không đọc được tệp CSV. Hãy kiểm tra định dạng rồi thử lại.");confirm.setEnabled(false);});
    }
    private void preview(){
        Tasks.run(this,pick,()->AttendanceRepository.getInstance().importStudentsCsv(rows,false),preview->{
            List<CsvPreviewAdapter.CsvRowItem> items=new ArrayList<>();
            for(int i=0;i<rows.size();i++){
                CsvTransfer.Row row=rows.get(i);String detail=preview.lines.get(i).split("\n",2)[1];
                String profileDetails="Ngày sinh: "+row.birthDate+" · Niên khóa: "+row.course+" · "+row.gender+" · SĐT: "+row.phone;
                items.add(new CsvPreviewAdapter.CsvRowItem(row.line,"Tự cấp",row.name,profileDetails,!detail.startsWith("Lỗi:"),detail));
            }
            adapter.setRows(items);
            String summaryText="Danh sách sinh viên · Chưa thêm vào lớp\n"+rows.size()+" dòng trong tệp";
            summaryText+=preview.errors>0?" · "+preview.errors+" dòng cần sửa\nHãy sửa tệp rồi chọn lại.":"\nKiểm tra danh sách trước khi nhập.";
            summary.setText(summaryText);
            confirm.setEnabled(preview.errors==0);confirm.setText("Thêm "+rows.size()+" sinh viên");
        },error->{summary.setText(error);confirm.setEnabled(false);});
    }
    @Override protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle state){if(selectedUri!=null)state.putString("uri",selectedUri.toString());super.onSaveInstanceState(state);}
}

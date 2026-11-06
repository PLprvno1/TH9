package com.example.th9.ui;

import android.content.*;
import android.os.Bundle;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import com.example.th9.R;
import com.example.th9.data.*;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ExportPreviewBottomSheetDialog extends BottomSheetDialogFragment {
    private String sessionId;
    private TextView preview;
    private RadioGroup format;
    private MaterialButton save,share;
    private final ActivityResultLauncher<String> create=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),uri->{
        if(uri==null){if(isAdded())Toast.makeText(requireContext(),"Đã hủy xuất tệp",Toast.LENGTH_SHORT).show();return;}
        if(!isAdded())return;
        Context context=requireContext().getApplicationContext();
        Tasks.run(requireActivity(),save,()->{
            String csv=CsvTransfer.csv(AttendanceRepository.getInstance(),sessionId);
            try(OutputStream output=context.getContentResolver().openOutputStream(uri,"wt")){
                if(output==null)throw new IOException("Không mở được tệp đích");
                output.write(csv.getBytes(StandardCharsets.UTF_8));output.flush();
            }return true;
        },ok->{if(isAdded())Toast.makeText(requireContext(),"Đã xuất CSV",Toast.LENGTH_LONG).show();});
    });
    public static ExportPreviewBottomSheetDialog newInstance(String sessionId){
        ExportPreviewBottomSheetDialog d=new ExportPreviewBottomSheetDialog();Bundle args=new Bundle();args.putString("session",sessionId);d.setArguments(args);return d;
    }
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state){return inflater.inflate(R.layout.dialog_export_preview,parent,false);}
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
        sessionId=requireArguments().getString("session");
        preview=view.findViewById(R.id.tv_export_preview_content);format=view.findViewById(R.id.rg_export_format);
        save=view.findViewById(R.id.btn_export_copy);share=view.findViewById(R.id.btn_export_share);
        save.setText("Lưu CSV vào tệp");
        share.setText("Chia sẻ");
        if(!AttendanceRepository.getInstance().isInitialized()){
            save.setEnabled(false);share.setEnabled(false);
            Tasks.run(requireActivity(),null,()->{AttendanceRepository.getInstance().initialize();return true;},ok->{
                if(isAdded()){save.setEnabled(true);share.setEnabled(true);update();}
            });
        }
        ((TextView)view.findViewById(R.id.tv_export_session_info)).setText(CsvTransfer.summary(AttendanceRepository.getInstance(),sessionId));
        update();format.setOnCheckedChangeListener((g,id)->update());
        save.setOnClickListener(v->create.launch("diem_danh_"+sessionId+".csv"));
        share.setOnClickListener(v->{
            if(format.getCheckedRadioButtonId()==R.id.rb_export_csv){
                Context context=requireContext().getApplicationContext();
                Tasks.run(requireActivity(),share,()->{
                    File directory=new File(context.getCacheDir(),"exports");
                    if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Không tạo được tệp chia sẻ");
                    File file=new File(directory,"diem_danh_"+sessionId+".csv");
                    try(OutputStream out=new FileOutputStream(file)){out.write(CsvTransfer.csv(AttendanceRepository.getInstance(),sessionId).getBytes(StandardCharsets.UTF_8));}
                    return FileProvider.getUriForFile(context,context.getPackageName()+".files",file);
                },uri->{if(isAdded()){
                    Intent send=new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM,uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("CSV",uri));
                    startActivity(Intent.createChooser(send,"Chia sẻ CSV"));
                }});
            }else startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT,CsvTransfer.summary(AttendanceRepository.getInstance(),sessionId)),"Chia sẻ tổng kết"));
        });
    }
    private void update(){
        if(!isAdded())return;
        if(AttendanceRepository.getInstance().getSessionById(sessionId)==null){preview.setText("Đang tải dữ liệu buổi…");return;}
        ((TextView)requireView().findViewById(R.id.tv_export_session_info)).setText(CsvTransfer.summary(AttendanceRepository.getInstance(),sessionId));
        try{preview.setText(format.getCheckedRadioButtonId()==R.id.rb_export_csv?CsvTransfer.csv(AttendanceRepository.getInstance(),sessionId):CsvTransfer.summary(AttendanceRepository.getInstance(),sessionId));}
        catch(IOException e){preview.setText("Không tạo được nội dung: "+e.getMessage());}
    }
}

package com.example.th9.camera;

import android.content.Context;
import androidx.annotation.OptIn;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one back-camera pipeline. Every frame is closed, including empty and failed frames. */
public final class CameraScanner implements AutoCloseable {
    public interface Listener {
        void onCode(String raw,String format);
        void onMessage(String message);
        void onCamera(Camera camera);
        void onError(String message);
    }
    private final Context context;
    private final LifecycleOwner owner;
    private final PreviewView view;
    private final Listener listener;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean analyzing=new AtomicBoolean();
    private final BarcodeScanner scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_CODE_128,Barcode.FORMAT_CODE_39,Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODABAR,Barcode.FORMAT_EAN_13,Barcode.FORMAT_EAN_8,Barcode.FORMAT_ITF,
            Barcode.FORMAT_UPC_A,Barcode.FORMAT_UPC_E).build());
    private ProcessCameraProvider provider;
    private Preview preview;
    private ImageAnalysis analysis;
    private volatile boolean closed,paused;
    private volatile long epoch;
    private long lastMessage;
    public CameraScanner(Context context,LifecycleOwner owner,PreviewView view,Listener listener) {
        this.context=context;this.owner=owner;this.view=view;this.listener=listener;
    }
    public void setPaused(boolean value){paused=value;epoch++;}
    public void start() {
        listener.onMessage("Đang mở camera");
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(context);
        future.addListener(()->{
            if(closed)return;
            try {
                provider=future.get();
                if(!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))throw new IllegalStateException("Không tìm thấy camera sau");
                preview=new Preview.Builder().build();preview.setSurfaceProvider(view.getSurfaceProvider());
                analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(new android.util.Size(1280,720)).build();
                analysis.setAnalyzer(executor,this::analyze);
                Camera camera=provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis);
                listener.onCamera(camera);listener.onMessage("Chưa đọc được mã · Đưa một thẻ vào khung");
                camera.getCameraInfo().getCameraState().observe(owner,state->{
                    if(!closed&&state.getError()!=null)listener.onError("Camera không khả dụng. Hãy thử lại.");
                });
            }catch(Exception e){listener.onError("Camera không khả dụng: "+e.getMessage());}
        },ContextCompat.getMainExecutor(context));
    }
    @OptIn(markerClass=ExperimentalGetImage.class)
    private void analyze(ImageProxy frame) {
        if(closed||paused||frame.getImage()==null||!analyzing.compareAndSet(false,true)){frame.close();return;}
        long token=epoch;
        try {
            InputImage image=InputImage.fromMediaImage(frame.getImage(),frame.getImageInfo().getRotationDegrees());
            scanner.process(image).addOnSuccessListener(ContextCompat.getMainExecutor(context),codes->{
                if(closed||paused||token!=epoch)return;
                Map<String,Integer> values=new LinkedHashMap<>();
                for(Barcode code:codes)if(code.getRawValue()!=null&&!code.getRawValue().isEmpty())values.put(code.getRawValue(),code.getFormat());
                if(values.size()>1){message("Có nhiều mã · Hãy đưa một thẻ vào khung");return;}
                if(values.isEmpty()){message("Chưa đọc được mã · Đưa một thẻ vào khung");return;}
                Map.Entry<String,Integer> entry=values.entrySet().iterator().next();listener.onCode(entry.getKey(),format(entry.getValue()));
            }).addOnFailureListener(ContextCompat.getMainExecutor(context),e->{
                if(!closed&&!paused&&token==epoch)message("Không đọc được mã: "+e.getMessage());
            }).addOnCompleteListener(ContextCompat.getMainExecutor(context),task->{
                frame.close();analyzing.set(false);if(closed)scanner.close();
            });
        }catch(Exception e){frame.close();analyzing.set(false);if(closed)scanner.close();}
    }
    private void message(String text){long now=android.os.SystemClock.elapsedRealtime();if(now-lastMessage>4000){lastMessage=now;listener.onMessage(text);}}
    public static String format(int format) {
        switch(format){
            case Barcode.FORMAT_CODE_128:return "CODE_128";case Barcode.FORMAT_CODE_39:return "CODE_39";
            case Barcode.FORMAT_CODE_93:return "CODE_93";case Barcode.FORMAT_CODABAR:return "CODABAR";
            case Barcode.FORMAT_EAN_13:return "EAN_13";case Barcode.FORMAT_EAN_8:return "EAN_8";
            case Barcode.FORMAT_ITF:return "ITF";case Barcode.FORMAT_UPC_A:return "UPC_A";case Barcode.FORMAT_UPC_E:return "UPC_E";
            default:return String.valueOf(format);
        }
    }
    @Override public void close(){
        closed=true;epoch++;
        if(analysis!=null)analysis.clearAnalyzer();
        if(provider!=null&&preview!=null&&analysis!=null)provider.unbind(preview,analysis);
        executor.shutdown();if(!analyzing.get())scanner.close();
    }
}

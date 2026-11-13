package com.example.th9.camera;

import android.content.Context;
import android.util.Log;
import android.view.Surface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.camera.core.resolutionselector.*;
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
    private static final String TAG="TH9CameraScanner";
    public interface Listener {
        void onCode(String raw,String format);
        void onMessage(String message);
        void onCamera(Camera camera);
        void onError(String message);
        default void onZoom(float ratio) {}
    }
    private final Context context;
    private final LifecycleOwner owner;
    private final PreviewView view;
    private final Listener listener;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean analyzing=new AtomicBoolean();
    private final AtomicBoolean decoderReleased=new AtomicBoolean();
    private volatile android.graphics.RectF zoomBounds;
    private volatile long zoomBoundsAt;
    private BarcodeScanner scanner;
    private Camera camera;
    private final Handler main=new Handler(Looper.getMainLooper());
    private float maxZoom=1f;
    private volatile int missedFrames;
    private long lastFallback,lastFocus;
    private final Runnable refocus=new Runnable(){@Override public void run(){
        if(closed)return;
        if(!paused&&missedFrames>=6&&zoomBounds==null)focus(view.getWidth()/2f,view.getHeight()/2f);
        main.postDelayed(this,3500);
    }};
    private ProcessCameraProvider provider;
    private Preview preview;
    private ImageAnalysis analysis;
    private volatile boolean closed,paused=true;
    private volatile long epoch;
    private long lastMessage;
    private boolean loggedFrame;
    public CameraScanner(Context context,LifecycleOwner owner,PreviewView view,Listener listener) {
        this.context=context;this.owner=owner;this.view=view;this.listener=listener;
    }
    public void setPaused(boolean value){
        // Repeating the same state must not invalidate a frame already being decoded.
        if(paused==value)return;
        paused=value;epoch++;
        Log.d(TAG,value?"Barcode analysis paused":"Barcode analysis resumed");
    }
    public void start() {
        listener.onMessage("Đang mở camera");
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(context);
        future.addListener(()->{
            if(closed)return;
            try {
                provider=future.get();
                if(!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))throw new IllegalStateException("Không tìm thấy camera sau");
                int rotation=view.getDisplay()==null?Surface.ROTATION_0:view.getDisplay().getRotation();
                try{bind(rotation,new android.util.Size(1920,1080));}
                catch(IllegalArgumentException unsupported){
                    Log.w(TAG,"1080p camera combination unavailable; retrying at 720p",unsupported);
                    if(preview!=null&&analysis!=null)provider.unbind(preview,analysis);
                    bind(rotation,new android.util.Size(1280,720));
                }
                ZoomState zoom=camera.getCameraInfo().getZoomState().getValue();
                maxZoom=zoom==null?1f:Math.min(3f,zoom.getMaxZoomRatio());
                scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_CODE_128,Barcode.FORMAT_CODE_39,Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_CODABAR,Barcode.FORMAT_EAN_13,Barcode.FORMAT_EAN_8,Barcode.FORMAT_ITF,
                        Barcode.FORMAT_UPC_A,Barcode.FORMAT_UPC_E)
                    .enableAllPotentialBarcodes()
                    .setZoomSuggestionOptions(new ZoomSuggestionOptions.Builder(this::suggestZoom)
                        .setMaxSupportedZoomRatio(maxZoom).build()).build());
                analysis.setAnalyzer(executor,this::analyze);
                android.view.ScaleGestureDetector pinch=new android.view.ScaleGestureDetector(context,
                    new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
                        @Override public boolean onScale(android.view.ScaleGestureDetector detector){
                            ZoomState state=camera.getCameraInfo().getZoomState().getValue();
                            if(state!=null)setZoom(state.getZoomRatio()*detector.getScaleFactor());return true;
                        }
                    });
                android.view.GestureDetector tap=new android.view.GestureDetector(context,
                    new android.view.GestureDetector.SimpleOnGestureListener(){
                        @Override public boolean onDown(android.view.MotionEvent event){return true;}
                        @Override public boolean onSingleTapUp(android.view.MotionEvent event){
                            view.performClick();focus(event.getX(),event.getY());return true;
                        }
                    });
                view.setOnTouchListener((target,event)->{pinch.onTouchEvent(event);if(!pinch.isInProgress())tap.onTouchEvent(event);return true;});
                camera.getCameraInfo().getZoomState().observe(owner,state->{if(!closed)listener.onZoom(state.getZoomRatio());});
                view.getPreviewStreamState().observe(owner,state->{
                    if(!closed&&state==PreviewView.StreamState.STREAMING)view.post(()->focus(view.getWidth()/2f,view.getHeight()/2f));
                });
                main.postDelayed(refocus,3500);
                listener.onCamera(camera);listener.onMessage("Quét toàn màn hình · Mã không cần nằm trong khung hướng dẫn");
                camera.getCameraInfo().getCameraState().observe(owner,state->{
                    if(!closed&&state.getError()!=null)listener.onError("Không thể mở camera. Hãy thử lại.");
                });
            }catch(Exception e){Log.e(TAG,"Cannot bind barcode camera",e);listener.onError("Không thể mở camera. Hãy thử lại.");}
        },ContextCompat.getMainExecutor(context));
    }
    private void bind(int rotation,android.util.Size resolution){
        ResolutionSelector selector=new ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(new ResolutionStrategy(resolution,ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build();
        preview=new Preview.Builder().setTargetRotation(rotation).setResolutionSelector(selector).build();
        preview.setSurfaceProvider(view.getSurfaceProvider());
        analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetRotation(rotation).setResolutionSelector(selector).build();
        camera=provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis);
    }
    private void focus(float x,float y){
        if(closed||paused||camera==null||view.getWidth()==0||SystemClock.elapsedRealtime()-lastFocus<1800)return;
        lastFocus=SystemClock.elapsedRealtime();MeteringPoint point=view.getMeteringPointFactory().createPoint(x,y,0.22f);
        FocusMeteringAction action=new FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AF|FocusMeteringAction.FLAG_AE|FocusMeteringAction.FLAG_AWB)
            .setAutoCancelDuration(2,TimeUnit.SECONDS).build();
        if(camera.getCameraInfo().isFocusMeteringSupported(action)){
            com.google.common.util.concurrent.ListenableFuture<FocusMeteringResult> future=camera.getCameraControl().startFocusAndMetering(action);
            future.addListener(()->{try{future.get();}catch(Exception ignored){/* A newer focus or lifecycle stop can cancel this request. */}},ContextCompat.getMainExecutor(context));
        }
    }
    private boolean suggestZoom(float ratio){
        if(closed||paused||camera==null||!Float.isFinite(ratio))return false;
        ZoomState state=camera.getCameraInfo().getZoomState().getValue();if(state==null)return false;
        float requested=Math.max(state.getMinZoomRatio(),Math.min(maxZoom,ratio));
        if(requested>state.getZoomRatio()){
            android.graphics.RectF bounds=zoomBounds;
            if(bounds==null||SystemClock.elapsedRealtime()-zoomBoundsAt>750)return false;
            // Digital zoom crops symmetrically. Never crop a candidate near an image edge.
            if(!ZoomFraming.keepsVisible(state.getZoomRatio(),requested,bounds.left,bounds.top,bounds.right,bounds.bottom))return false;
        }
        setZoom(requested);return true;
    }
    private void setZoom(float ratio){
        if(closed||camera==null)return;
        ZoomState state=camera.getCameraInfo().getZoomState().getValue();if(state==null)return;
        float clamped=Math.max(state.getMinZoomRatio(),Math.min(maxZoom,ratio));
        camera.getCameraControl().setZoomRatio(clamped);
    }
    public void cycleZoom(){
        if(closed||camera==null)return;ZoomState state=camera.getCameraInfo().getZoomState().getValue();if(state==null)return;
        float current=state.getZoomRatio(),next=current<1.4f?1.5f:current<1.9f?2f:current<maxZoom-0.1f?maxZoom:1f;
        setZoom(next);
    }
    private com.google.zxing.Result fallback(ImageProxy frame){
        long now=SystemClock.elapsedRealtime();if(now-lastFallback<250)return null;
        lastFallback=now;
        try{ImageProxy.PlaneProxy y=frame.getPlanes()[0];return BarcodeFallback.decode(y.getBuffer(),frame.getWidth(),frame.getHeight(),y.getRowStride(),y.getPixelStride());}
        catch(RuntimeException e){Log.w(TAG,"Cannot read luminance for secondary decoder",e);return null;}
    }
    @OptIn(markerClass=ExperimentalGetImage.class)
    private void analyze(ImageProxy frame) {
        if(closed||paused||frame.getImage()==null||!analyzing.compareAndSet(false,true)){frame.close();return;}
        long token=epoch;
        try {
            if(!loggedFrame){
                loggedFrame=true;
                Log.i(TAG,"Analyzing camera frames "+frame.getWidth()+"x"+frame.getHeight()
                    +", rotation="+frame.getImageInfo().getRotationDegrees());
            }
            InputImage image=InputImage.fromMediaImage(frame.getImage(),frame.getImageInfo().getRotationDegrees());
            scanner.process(image).addOnCompleteListener(executor,task->{
                try {
                    if(closed||paused||token!=epoch)return;
                    if(!task.isSuccessful()){
                        Log.w(TAG,"ML Kit barcode decoding failed",task.getException());
                        missedFrames++;zoomBounds=null;
                        com.google.zxing.Result secondary=fallback(frame);
                        if(secondary!=null){missedFrames=0;deliver(token,secondary.getText(),secondary.getBarcodeFormat().name());}
                        else message("Đang dùng bộ đọc dự phòng · Giữ trọn mã trong ảnh camera");return;
                    }
                    Map<String,Integer> values=new LinkedHashMap<>();
                    for(Barcode code:task.getResult())if(code.getRawValue()!=null&&!code.getRawValue().isEmpty())values.put(code.getRawValue(),code.getFormat());
                    updateZoomBounds(values.isEmpty()?task.getResult():Collections.emptyList(),frame);
                    if(values.size()>1){message("Có nhiều mã · Chỉ giữ một thẻ trong ảnh camera");return;}
                    if(values.isEmpty()){
                        missedFrames++;
                        com.google.zxing.Result secondary=fallback(frame);
                        if(secondary!=null){missedFrames=0;deliver(token,secondary.getText(),secondary.getBarcodeFormat().name());}
                        else message("Quét toàn ảnh camera · Giữ mã rõ nét, đủ sáng và tránh lóa");return;
                    }
                    missedFrames=0;
                    Map.Entry<String,Integer> entry=values.entrySet().iterator().next();
                    Log.d(TAG,"Decoded barcode format="+format(entry.getValue()));
                    deliver(token,entry.getKey(),format(entry.getValue()));
                } finally {
                    frame.close();analyzing.set(false);if(closed)releaseDecoder();
                }
            });
        }catch(Exception e){
            Log.e(TAG,"Cannot submit camera frame to ML Kit",e);
            frame.close();analyzing.set(false);if(closed)releaseDecoder();
            ContextCompat.getMainExecutor(context).execute(()->{
                if(!closed&&!paused&&token==epoch)message("Không thể phân tích ảnh camera · Hãy đóng màn quét rồi mở lại");
            });
        }
    }
    private void updateZoomBounds(List<Barcode> candidates,ImageProxy frame){
        zoomBounds=null;
        if(candidates.size()!=1)return;
        android.graphics.Rect rect=candidates.get(0).getBoundingBox();if(rect==null||rect.isEmpty())return;
        int rotation=frame.getImageInfo().getRotationDegrees();
        float width=rotation%180==0?frame.getWidth():frame.getHeight();
        float height=rotation%180==0?frame.getHeight():frame.getWidth();
        zoomBoundsAt=SystemClock.elapsedRealtime();
        zoomBounds=new android.graphics.RectF(rect.left/width,rect.top/height,rect.right/width,rect.bottom/height);
        main.post(()->{
            if(closed||paused)return;
            float scale=Math.min(view.getWidth()/width,view.getHeight()/height);
            focus((view.getWidth()-width*scale)/2f+rect.exactCenterX()*scale,
                (view.getHeight()-height*scale)/2f+rect.exactCenterY()*scale);
        });
    }
    private void deliver(long token,String raw,String format){
        main.post(()->{if(!closed&&!paused&&token==epoch)listener.onCode(raw,format);});
    }
    private void message(String text){main.post(()->{
        if(closed||paused)return;
        long now=SystemClock.elapsedRealtime();if(now-lastMessage>4000){lastMessage=now;listener.onMessage(text);}
    });}
    public static String format(int format) {
        switch(format){
            case Barcode.FORMAT_CODE_128:return "CODE_128";case Barcode.FORMAT_CODE_39:return "CODE_39";
            case Barcode.FORMAT_CODE_93:return "CODE_93";case Barcode.FORMAT_CODABAR:return "CODABAR";
            case Barcode.FORMAT_EAN_13:return "EAN_13";case Barcode.FORMAT_EAN_8:return "EAN_8";
            case Barcode.FORMAT_ITF:return "ITF";case Barcode.FORMAT_UPC_A:return "UPC_A";case Barcode.FORMAT_UPC_E:return "UPC_E";
            default:return String.valueOf(format);
        }
    }
    private void releaseDecoder(){if(decoderReleased.compareAndSet(false,true)){if(scanner!=null)scanner.close();executor.shutdown();}}
    @Override public void close(){
        closed=true;epoch++;main.removeCallbacks(refocus);view.setOnTouchListener(null);
        if(analysis!=null)analysis.clearAnalyzer();
        if(provider!=null&&preview!=null&&analysis!=null)provider.unbind(preview,analysis);
        if(!analyzing.get())releaseDecoder();
    }
}

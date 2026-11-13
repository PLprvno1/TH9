package com.example.th9;
import android.content.Intent;
import androidx.camera.core.Camera;
import androidx.camera.core.ZoomState;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.th9.camera.CameraScanner;
import com.example.th9.ui.ScanActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
/** Reuses the isolated UI fixture; no account is created in the real database. */
@RunWith(AndroidJUnit4.class)
public class CameraSensitivityTest extends RoleUiTest {
    @Test public void zoomAndResume()throws Exception{
        repo.setupAdmin("admin","Admin","TestPass123!");
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.CAMERA);
        Intent intent=new Intent(context,ScanActivity.class).putExtra(ScanActivity.EXTRA_MODE,ScanActivity.MODE_VERIFY_CARD);
        Field cameraField=ScanActivity.class.getDeclaredField("camera");cameraField.setAccessible(true);
        Field scannerField=ScanActivity.class.getDeclaredField("scanner");scannerField.setAccessible(true);
        try(ActivityScenario<ScanActivity> scenario=ActivityScenario.launch(intent)){
            cameraStreams(scenario);
            scenario.onActivity(a->{try{((CameraScanner)scannerField.get(a)).cycleZoom();}catch(Exception e){throw new AssertionError(e);}});
            AtomicBoolean zoomed=new AtomicBoolean();long deadline=System.currentTimeMillis()+5000;
            do{scenario.onActivity(a->{try{Camera c=(Camera)cameraField.get(a);ZoomState z=c.getCameraInfo().getZoomState().getValue();zoomed.set(z!=null&&z.getZoomRatio()>=Math.min(1.5f,z.getMaxZoomRatio())-0.05f);}catch(Exception e){throw new AssertionError(e);}});if(!zoomed.get())Thread.sleep(100);}while(!zoomed.get()&&System.currentTimeMillis()<deadline);assertTrue("Camera zoom uses a supported ratio",zoomed.get());
            scenario.onActivity(a->{try{CameraScanner scanner=(CameraScanner)scannerField.get(a);Method suggestion=CameraScanner.class.getDeclaredMethod("suggestZoom",float.class);suggestion.setAccessible(true);scanner.setPaused(true);assertEquals(false,suggestion.invoke(scanner,2f));scanner.setPaused(false);
                Field bounds=CameraScanner.class.getDeclaredField("zoomBounds"),at=CameraScanner.class.getDeclaredField("zoomBoundsAt");bounds.setAccessible(true);at.setAccessible(true);
                bounds.set(scanner,new android.graphics.RectF(.40f,.40f,.60f,.60f));at.setLong(scanner,android.os.SystemClock.elapsedRealtime());assertEquals(true,suggestion.invoke(scanner,2f));
                bounds.set(scanner,new android.graphics.RectF(.01f,.40f,.12f,.60f));at.setLong(scanner,android.os.SystemClock.elapsedRealtime());Camera c=(Camera)cameraField.get(a);ZoomState z=c.getCameraInfo().getZoomState().getValue();
                Field limit=CameraScanner.class.getDeclaredField("maxZoom");limit.setAccessible(true);
                boolean increasing=Math.min(limit.getFloat(scanner),3f)>z.getZoomRatio();
                assertEquals("Edge code must block zoom that would narrow the field of view",!increasing,suggestion.invoke(scanner,3f));}catch(Exception e){throw new AssertionError(e);}});
            scenario.recreate();cameraStreams(scenario);
        }
        try(ActivityScenario<ScanActivity> reopened=ActivityScenario.launch(intent)){cameraStreams(reopened);}
    }
}

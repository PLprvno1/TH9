package com.example.th9;

import android.Manifest;
import android.content.*;
import android.graphics.Bitmap;
import androidx.camera.view.PreviewView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.th9.ui.ScanActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CameraLifecycleTest {
    private void streaming(ActivityScenario<ScanActivity> scenario) throws Exception {
        AtomicBoolean value=new AtomicBoolean();long deadline=System.currentTimeMillis()+15000;
        do{
            scenario.onActivity(a->{PreviewView view=a.findViewById(R.id.camera_preview);value.set(view!=null&&view.getPreviewStreamState().getValue()==PreviewView.StreamState.STREAMING);});
            if(!value.get())Thread.sleep(100);
        }while(!value.get()&&System.currentTimeMillis()<deadline);
        assertTrue("Back camera preview must stream on this AVD",value.get());
    }
    private void rotate(ActivityScenario<ScanActivity> scenario,int requested,int expected) throws Exception {
        java.util.concurrent.atomic.AtomicReference<ScanActivity> previous=new java.util.concurrent.atomic.AtomicReference<>();
        scenario.onActivity(a->{previous.set(a);a.setRequestedOrientation(requested);});
        AtomicBoolean rotated=new AtomicBoolean();long deadline=System.currentTimeMillis()+15000;
        do{scenario.onActivity(a->rotated.set(a!=previous.get()&&a.getResources().getConfiguration().orientation==expected));
            if(!rotated.get())Thread.sleep(100);
        }while(!rotated.get()&&System.currentTimeMillis()<deadline);
        assertTrue("Activity must actually rotate and recreate",rotated.get());streaming(scenario);
    }
    @Test public void backCameraStreamsAfterRecreationAndReopen() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.CAMERA);
        Intent intent=new Intent(context,ScanActivity.class).putExtra(ScanActivity.EXTRA_MODE,ScanActivity.MODE_LINK_CARD);
        try(ActivityScenario<ScanActivity> scenario=ActivityScenario.launch(intent)){
            streaming(scenario);scenario.recreate();streaming(scenario);
            rotate(scenario,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,android.content.res.Configuration.ORIENTATION_LANDSCAPE);
            rotate(scenario,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,android.content.res.Configuration.ORIENTATION_PORTRAIT);
            Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            File directory=context.getExternalFilesDir("verification");if(directory!=null){directory.mkdirs();try(OutputStream out=new FileOutputStream(new File(directory,"camera-preview.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}}
        }
        try(ActivityScenario<ScanActivity> reopened=ActivityScenario.launch(intent)){streaming(reopened);}
    }
}

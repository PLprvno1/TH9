package com.example.th9;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BundledBarcodeTest {
    /** Exercises the bundled native decoder on a bitmap, without claiming physical camera coverage. */
    @Test public void bundledDecoderPreservesRawCode128() throws Exception {
        String value="SV:00010001";
        BitMatrix matrix=new Code128Writer().encode(value,BarcodeFormat.CODE_128,1000,300);
        Bitmap bitmap=Bitmap.createBitmap(1200,600,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xffffffff);
        for(int y=0;y<300;y++)for(int x=0;x<1000;x++)if(matrix.get(x,y))bitmap.setPixel(x+100,y+150,0xff000000);
        BarcodeScanner scanner=BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128).build());
        try{
            List<Barcode> results=Tasks.await(scanner.process(InputImage.fromBitmap(bitmap,0)),30,TimeUnit.SECONDS);
            assertEquals(1,results.size());assertEquals(value,results.get(0).getRawValue());assertEquals(Barcode.FORMAT_CODE_128,results.get(0).getFormat());
        }finally{scanner.close();bitmap.recycle();}
    }
}

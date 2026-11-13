package com.example.th9;
import com.example.th9.camera.BarcodeFallback;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
public class BarcodeFallbackTest {
    static final String NUMBER="202020260001";
    static final int W=1200,H=600;
    byte[] image(int dark,int light){byte[] y=new byte[W*H];Arrays.fill(y,(byte)light);BitMatrix bars=new Code128Writer().encode(NUMBER,BarcodeFormat.CODE_128,900,150);for(int row=0;row<150;row++)for(int col=0;col<900;col++)if(bars.get(col,row))y[(row+225)*W+col+150]=(byte)dark;return y;}
    void expected(ByteBuffer y,int w,int h,int rowStride,int pixelStride){Result r=BarcodeFallback.decode(y,w,h,rowStride,pixelStride);assertNotNull(r);assertEquals(NUMBER,r.getText());assertEquals(BarcodeFormat.CODE_128,r.getBarcodeFormat());}
    @Test public void readsLowContrastBarcode(){expected(ByteBuffer.wrap(image(100,135)),W,H,W,1);}
    @Test public void readsWhiteBarsOnDarkBackground(){expected(ByteBuffer.wrap(image(240,20)),W,H,W,1);}
    @Test public void readsVerticalBarcode(){byte[] y=image(0,255),vertical=new byte[y.length];for(int row=0;row<H;row++)for(int col=0;col<W;col++)vertical[col*H+H-1-row]=y[row*W+col];expected(ByteBuffer.wrap(vertical),H,W,H,1);}
    @Test public void respectsRowPixelStrideOffsetAndLastRowLimit(){byte[] y=image(0,255);int stride=W*2+17;ByteBuffer plane=ByteBuffer.allocate(7+(H-1)*stride+W*2-1);for(int row=0;row<H;row++)for(int col=0;col<W;col++)plane.put(7+row*stride+col*2,y[row*W+col]);plane.position(7);expected(plane,W,H,stride,2);assertEquals(7,plane.position());}
    @Test public void readsCodesAtAllFourCornersOutsideReticle(){
        for(int left:new int[]{8,W-408})for(int top:new int[]{8,H-58}){
            byte[] y=new byte[W*H];Arrays.fill(y,(byte)255);
            BitMatrix bars=new Code128Writer().encode(NUMBER,BarcodeFormat.CODE_128,400,50);
            for(int row=0;row<50;row++)for(int col=0;col<400;col++)if(bars.get(col,row))y[(row+top)*W+col+left]=0;
            expected(ByteBuffer.wrap(y),W,H,W,1);
            byte[] rotated=new byte[y.length];for(int row=0;row<H;row++)for(int col=0;col<W;col++)rotated[col*H+H-1-row]=y[row*W+col];
            expected(ByteBuffer.wrap(rotated),H,W,H,1);
        }
    }
    @Test public void readsInvertedCodeAtUpperEdge(){
        byte[] y=new byte[W*H];Arrays.fill(y,(byte)20);BitMatrix bars=new Code128Writer().encode(NUMBER,BarcodeFormat.CODE_128,400,50);
        for(int row=0;row<50;row++)for(int col=0;col<400;col++)if(bars.get(col,row))y[(row+5)*W+col+5]=(byte)240;
        expected(ByteBuffer.wrap(y),W,H,W,1);
    }
    @Test public void blankAndTruncatedPlanesAreRejected(){byte[] blank=new byte[W*H];Arrays.fill(blank,(byte)255);assertNull(BarcodeFallback.decode(ByteBuffer.wrap(blank),W,H,W,1));assertNull(BarcodeFallback.decode(ByteBuffer.allocate(20),W,H,W,1));}
}

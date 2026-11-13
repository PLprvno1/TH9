package com.example.th9.camera;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.nio.ByteBuffer;
import java.util.*;

/** Secondary offline 1D decoder. Reads the Y plane directly, including camera row padding. */
public final class BarcodeFallback {
    private static final List<BarcodeFormat> FORMATS=Arrays.asList(BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,BarcodeFormat.CODE_93,BarcodeFormat.CODABAR,BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,BarcodeFormat.ITF,BarcodeFormat.UPC_A,BarcodeFormat.UPC_E);
    private BarcodeFallback() {}
    public static Result decode(ByteBuffer plane,int width,int height,int rowStride,int pixelStride){
        if(width<=0||height<=0||rowStride<width||pixelStride<=0)return null;
        ByteBuffer input=plane.duplicate();int base=input.position();
        if((long)base+(long)(height-1)*rowStride+(long)(width-1)*pixelStride>=input.limit())return null;
        byte[] y=new byte[Math.multiplyExact(width,height)];
        for(int row=0;row<height;row++){
            int offset=base+row*rowStride;
            if(pixelStride==1){input.position(offset);input.get(y,row*width,width);}
            else for(int col=0;col<width;col++)y[row*width+col]=input.get(offset+col*pixelStride);
        }
        MultiFormatReader reader=new MultiFormatReader();Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,FORMATS);hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);reader.setHints(hints);
        // Search every row across the full image, including codes outside the visual reticle.
        Result result=decodeImage(reader,y,width,height);if(result!=null)return result;
        byte[] rotated=new byte[y.length];
        for(int row=0;row<height;row++)for(int col=0;col<width;col++)rotated[(width-1-col)*height+row]=y[row*width+col];
        return decodeImage(reader,rotated,height,width);
    }
    private static Result decodeImage(MultiFormatReader reader,byte[] y,int width,int height){
        LuminanceSource source=new PlanarYUVLuminanceSource(y,width,height,0,0,width,height,false);
        for(int polarity=0;polarity<2;polarity++){
            try{return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(polarity==0?source:source.invert())));}
            catch(NotFoundException ignored){}finally{reader.reset();}
        }
        return null;
    }
}

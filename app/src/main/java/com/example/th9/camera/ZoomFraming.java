package com.example.th9.camera;
/** Checks whether a symmetric digital zoom keeps the complete candidate inside the image. */
public final class ZoomFraming {
    private ZoomFraming(){}
    public static boolean keepsVisible(float current,float requested,float left,float top,float right,float bottom){
        if(requested<=current)return true;
        float margin=(1f-current/requested)/2f+0.02f;
        return left>=margin&&top>=margin&&right<=1f-margin&&bottom<=1f-margin;
    }
}

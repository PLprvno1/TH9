package com.example.th9;
import com.example.th9.camera.ZoomFraming;
import org.junit.Test;
import static org.junit.Assert.*;
public class ZoomFramingTest {
    @Test public void centeredCodeSurvivesTwoTimesZoom(){assertTrue(ZoomFraming.keepsVisible(1,2,.35f,.35f,.65f,.65f));}
    @Test public void leftAndRightCodesMustNotBeCropped(){assertFalse(ZoomFraming.keepsVisible(1,2,.02f,.4f,.20f,.6f));assertFalse(ZoomFraming.keepsVisible(1,2,.8f,.4f,.98f,.6f));}
    @Test public void topAndBottomCodesMustNotBeCropped(){assertFalse(ZoomFraming.keepsVisible(1,2,.4f,.02f,.6f,.20f));assertFalse(ZoomFraming.keepsVisible(1,2,.4f,.8f,.6f,.98f));}
    @Test public void entireCodeMustFitNotJustItsCenter(){assertFalse(ZoomFraming.keepsVisible(1,2,.20f,.35f,.8f,.65f));}
    @Test public void ZoomOutOrUnchangedRatioKeepsFieldOfView(){assertTrue(ZoomFraming.keepsVisible(2,1,.01f,.01f,.99f,.99f));assertTrue(ZoomFraming.keepsVisible(1,1,.01f,.01f,.99f,.99f));}
}

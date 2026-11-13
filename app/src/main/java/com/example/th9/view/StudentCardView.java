package com.example.th9.view;
import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.util.AttributeSet;
import android.view.View;
import com.example.th9.data.*;
import com.example.th9.model.Student;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import java.util.Collections;
/** Same drawing path for responsive preview and full-resolution PNG. */
public final class StudentCardView extends View {
    private Student student;private String className;private Bitmap barcode,photo;
    public StudentCardView(Context c,AttributeSet a){super(c,a);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void bind(Student s,String label){student=s;className=label;photo=StudentPhotos.read(s.getPhotoPath());
        if(s.getMssv()==null||!s.getMssv().matches("[ -~]{1,80}"))throw new IllegalArgumentException("MSSV không thể biểu diễn bằng CODE_128");
        BitMatrix matrix=new Code128Writer().encode(s.getMssv(),BarcodeFormat.CODE_128,1440,220,Collections.singletonMap(EncodeHintType.MARGIN,40));
        int[] pixels=new int[matrix.getWidth()*matrix.getHeight()];for(int y=0;y<matrix.getHeight();y++)for(int x=0;x<matrix.getWidth();x++)pixels[y*matrix.getWidth()+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
        barcode=Bitmap.createBitmap(pixels,matrix.getWidth(),matrix.getHeight(),Bitmap.Config.ARGB_8888);invalidate();
        setContentDescription("Thẻ sinh viên của "+s.getFullName()+", MSSV "+s.getMssv());
    }
    @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);setMeasuredDimension(width,Math.round(width*1050f/1600));}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(student==null)return;canvas.save();canvas.scale(getWidth()/1600f,getHeight()/1050f);drawCard(canvas);canvas.restore();}
    public Bitmap export(){if(student==null||barcode==null)throw new IllegalStateException("Thẻ chưa sẵn sàng");Bitmap result=Bitmap.createBitmap(1600,1050,Bitmap.Config.ARGB_8888);drawCard(new Canvas(result));return result;}
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));c.drawText(value==null||value.isEmpty()?"—":value,x,y,p);}
    private void block(Canvas c,String value,int x,int y,int width,int maxHeight,int size,int color){
        TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));StaticLayout layout;
        do{p.setTextSize(size--);layout=StaticLayout.Builder.obtain(value==null||value.isEmpty()?"—":value,0,value==null||value.isEmpty()?1:value.length(),p,width).setIncludePad(false).build();}while(layout.getHeight()>maxHeight&&size>10);
        c.save();c.translate(x,y);layout.draw(c);c.restore();
    }
    private void drawCard(Canvas c){
        int blue=Color.rgb(17,77,143),ink=Color.rgb(23,45,72);c.save();Path outline=new Path();outline.addRoundRect(new RectF(0,0,1600,1050),40,40,Path.Direction.CW);c.clipPath(outline);c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(blue);c.drawRect(0,0,1600,180,p);
        block(c,"TRƯỜNG ĐẠI HỌC THỦ DẦU MỘT",70,40,1460,65,54,Color.WHITE);
        text(c,"THẺ SINH VIÊN",70,155,48,Color.WHITE,true);
        RectF portrait=new RectF(80,230,390,620);p.setColor(Color.rgb(229,238,248));c.drawRoundRect(portrait,16,16,p);
        if(photo!=null){float scale=Math.max(310f/photo.getWidth(),390f/photo.getHeight());float w=photo.getWidth()*scale,h=photo.getHeight()*scale;c.save();c.clipRect(portrait);c.drawBitmap(photo,null,new RectF(235-w/2,425-h/2,235+w/2,425+h/2),p);c.restore();}
        else {text(c,student.getInitial(),190,465,120,blue,true);}
        block(c,student.getFullName(),450,225,1070,125,58,ink);
        block(c,"MSSV: "+student.getMssv(),450,365,1070,65,42,ink);
        text(c,"Ngày sinh: "+safe(student.getBirthDate()),450,460,38,ink,false);
        text(c,"Niên khóa: "+safe(student.getCourse()),450,520,38,ink,false);
        text(c,"Giới tính: "+safe(student.getGender()),450,580,38,ink,false);
        block(c,"Lớp: "+safe(className),450,610,1070,60,34,ink);
        p.setFilterBitmap(false);c.drawBitmap(barcode,null,new Rect(80,690,1520,910),p);
        text(c,student.getMssv(),80,965,42,Color.BLACK,true);
        text(c,"Mã vạch dùng để điểm danh",80,1020,30,Color.DKGRAY,false);c.restore();
    }
    private String safe(String s){return s==null||s.isEmpty()?"—":s;}
}

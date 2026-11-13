package com.example.th9.data;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.widget.ImageView;
import java.io.*;
/** Copies a bounded, decoded photo to private storage; never persists a picker URI. */
public final class StudentPhotos {
    public static String importPhoto(Context context,Uri uri)throws IOException{
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}
        if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("Không đọc được ảnh");
        opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>1200)opts.inSampleSize*=2;
        opts.inJustDecodeBounds=false;Bitmap image;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){image=BitmapFactory.decodeStream(in,null,opts);}
        if(image==null)throw new IOException("Ảnh không hợp lệ");
        int orientation=1;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            orientation=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
        }catch(IOException ignored){}
        Matrix transform=new Matrix();
        switch(orientation){
            case 2:transform.setScale(-1,1);break;
            case 3:transform.setRotate(180);break;
            case 4:transform.setScale(1,-1);break;
            case 5:transform.setRotate(90);transform.postScale(-1,1);break;
            case 6:transform.setRotate(90);break;
            case 7:transform.setRotate(270);transform.postScale(-1,1);break;
            case 8:transform.setRotate(270);break;
        }
        if(!transform.isIdentity()){Bitmap upright=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),transform,true);if(upright!=image)image.recycle();image=upright;}
        File folder=new File(context.getFilesDir(),"student_photos");if(!folder.exists()&&!folder.mkdirs())throw new IOException("Không thể lưu ảnh");
        File dest=new File(folder,java.util.UUID.randomUUID()+".jpg");
        try(OutputStream out=new FileOutputStream(dest)){if(!image.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Không thể ghi ảnh");}
        finally{image.recycle();}return dest.getAbsolutePath();
    }
    public static Bitmap read(String path){
        if(path==null)return null;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);
        if(o.outWidth<=0||o.outHeight<=0)return null;o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>800)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;return BitmapFactory.decodeFile(path,o);
    }
    public static void show(ImageView view,String path){Bitmap image=read(path);if(image==null)view.setImageResource(com.example.th9.R.drawable.ic_person);else view.setImageBitmap(image);}
}

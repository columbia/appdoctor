package com.andchecker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.Log;

public class BBImage {
	ByteBuffer image;
	int width;
	int height;
	static int count = 0;
	
	BBImage(Bitmap bitmap) {
		width = bitmap.getWidth();
		height = bitmap.getHeight();
		image = ByteBufferHelper.createNativeFromBitmap(bitmap);
		count++;
		Log.d("BBImage", "current instance count: " + count);
	}
	
	void free() {
		NativeHelper.freeNativeByteBuffer(image);
		count--;
		Log.d("BBImage", "current instance count: " + count);
	}
	
	private Bitmap toBitmap() {
		int[] colors = new int[width*height];
		((IntBuffer)image.asIntBuffer().rewind()).get(colors);
		Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		b.setPixels(colors, 0, width, 0, 0, width, height);
		return b;
	}
	
    public File save(String filename) throws IOException {
    	File f = new File(ACInstrumentation.getSelf().getInstrumentDataDir().getAbsolutePath() + "/" + filename);
    	FileOutputStream out = new FileOutputStream(f);
    	save(out);
    	out.flush();
    	out.close();
    	return f;
    }
    
    void save(OutputStream stream) {
    	Bitmap b = toBitmap();
    	b.compress(CompressFormat.PNG, 90, stream);    	
    	b.recycle();
    	b = null;
    	Runtime.getRuntime().gc();
    }
    
    int getWidth() { return width; }
    int getHeight() { return height; }
    void getPixels(int[] data) {
    	((IntBuffer)image.asIntBuffer().rewind()).get(data);
    }
    
    void drawRects(Rect[] rects) {
    	Bitmap b = toBitmap();
    	Canvas c = new Canvas(b);
    	Paint paint = new Paint();
    	paint.setColor(Color.RED);
    	paint.setStrokeWidth(5);
    	paint.setStyle(Style.STROKE);
    	for(Rect r : rects) {
    		c.drawRect(r, paint);
    	}
    	NativeHelper.freeNativeByteBuffer(image);
		image = ByteBufferHelper.createNativeFromBitmap(b);
		b.recycle();
		b = null;
		Runtime.getRuntime().gc();
    }
    
    ByteBuffer getBuffer() { return image; }
}

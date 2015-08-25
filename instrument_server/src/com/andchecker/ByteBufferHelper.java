package com.andchecker;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import android.graphics.Bitmap;
import android.util.Log;

class ByteBufferHelper
{
    static ByteBuffer createNativeFromBitmap(Bitmap bitmap) {
        ByteBuffer bytebuf = null;
        
        try
        {
            int height = bitmap.getHeight();
            int width  = bitmap.getWidth();
            int arr[]  = new int[height * width];
            bitmap.getPixels(arr, 0, width, 0, 0, width, height);
            bytebuf = NativeHelper.allocateNativeByteBuffer(height * width * 4);
            Log.i("ACInstrumentation", "Create native bitmap buffer with size " + (height * width * 4));
            ((IntBuffer)bytebuf.asIntBuffer().rewind()).put(arr);
        }
        catch (Exception x)
        {
            if (bytebuf != null)
            {
                NativeHelper.freeNativeByteBuffer(bytebuf);
                bytebuf = null;
            }
        }

        return bytebuf;
    }
}

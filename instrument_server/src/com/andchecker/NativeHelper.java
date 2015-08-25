package com.andchecker;

import android.content.Context;
import java.nio.ByteBuffer;

class NativeHelper
{
    public static void initNativeLibrary(Context ctx) {
        String base = ctx.getApplicationInfo().dataDir;
        System.load(base + "/lib/libaci_native_util.so");
    }

    public static native ByteBuffer allocateNativeByteBuffer(int size);
    public static native void       freeNativeByteBuffer(ByteBuffer bytebuf);
    public static native boolean    equalNativeByteBuffer(ByteBuffer a, ByteBuffer b);
    public static native boolean    diffNativeByteBuffers0(ByteBuffer a, ByteBuffer b, int width, int height, int threshold);
    public static native boolean    fork();
}

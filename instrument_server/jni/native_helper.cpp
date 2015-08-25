#include "com_andchecker_NativeHelper.h"
#include <android/log.h>
#include <unistd.h>
#include <cstdlib>
#include <cstring>
#include <queue>

using namespace std;

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jobject JNICALL Java_com_andchecker_NativeHelper_allocateNativeByteBuffer
(JNIEnv *env, jclass self, jint size)
{
    void *buf = malloc(size);
    if (buf == NULL)
        return NULL;
    
    jobject result = env->NewDirectByteBuffer(buf, size);
    if (result == NULL)
        free(buf);
    
    __android_log_print(ANDROID_LOG_INFO, "ACI_NATIVE_UTIL", "Allocate native byte buffer addr = %p", buf);

    return result;
}

JNIEXPORT void JNICALL Java_com_andchecker_NativeHelper_freeNativeByteBuffer
(JNIEnv *env, jclass self, jobject bytebuf)
{
    if (bytebuf == NULL) return;
    
    void *buf = env->GetDirectBufferAddress(bytebuf);
    free(buf);

    __android_log_print(ANDROID_LOG_INFO, "ACI_NATIVE_UTIL", "Free native byte buffer addr = %p", buf);
}

JNIEXPORT jboolean JNICALL Java_com_andchecker_NativeHelper_equalNativeByteBuffer
(JNIEnv *env, jclass self, jobject bufa, jobject bufb)
{
    if (bufa == bufb) return JNI_TRUE;
    if (bufa == NULL || bufb == NULL) return JNI_FALSE;
    
    jlong size = env->GetDirectBufferCapacity(bufa);
    if (env->GetDirectBufferCapacity(bufb) != size) return JNI_FALSE;
    void *a = env->GetDirectBufferAddress(bufa);
    void *b = env->GetDirectBufferAddress(bufb);
    if (memcmp(a, b, size) == 0)
        return JNI_TRUE;
    else return JNI_FALSE;
}

static bool
comparePixelBuffer(int *pb_a, int *pb_b, int width, int height, int threshold, int rect[4])
{
    char *diff = (char *)malloc(width * height);
    if (diff == NULL) return false;
    
    queue<int> qx;
    queue<int> qy;
    int x, y;
    int x_min, x_max, y_min, y_max;

    for (int i = 0; i < height * width; ++ i)
        diff[i] = pb_a[i] != pb_b[i];

    for (y = 0; y < height; ++ y)
        for (x = 0; x < width; ++ x)
        {
            if (diff[y * width + x] == 0)
                continue;

            if (threshold == 0) goto failed;

            qy.push(y);
            qx.push(x);

            x_min = x_max = x;
            y_min = y_max = y;

            while (!qx.empty())
            {
                y = qy.front(); qy.pop();
                x = qx.front(); qx.pop();
                for (int dy = -1; dy <= 1; ++ dy)
                    for (int dx = -1; dx <= 1; ++ dx)
                    {
                        int ty = y + dy;
                        int tx = x + dx;

                        if (ty < 0 || ty >= height) continue;
                        if (tx < 0 || tx >= width) continue;
                        if (diff[ty * width + tx] == 0) continue;
                        diff[ty * width + tx] = 0;

                        if (tx < x_min) x_min = tx;
                        if (tx > x_max) x_max = tx;
                        if (ty < y_min) y_min = ty;
                        if (ty > y_max) y_max = ty;

                        qy.push(ty);
                        qx.push(tx);
                    }

                if (x_max - x_min >= threshold &&
                    y_max - y_min >= threshold)
                    goto failed;
            }
        }

    free(diff);
    return true;

  failed:
    if (rect != NULL)
    {
        rect[0] = x_min;
        rect[1] = y_min;
        rect[2] = x_max;
        rect[3] = y_max;
    }
    free(diff);
    return false;
}

JNIEXPORT jboolean JNICALL Java_com_andchecker_NativeHelper_diffNativeByteBuffers0
(JNIEnv *env, jclass self, jobject bufa, jobject bufb, jint width, jint height, jint threshold)
{
    int size = width * height * 4;
    if (env->GetDirectBufferCapacity(bufa) != size) return JNI_FALSE;
    if (env->GetDirectBufferCapacity(bufb) != size) return JNI_FALSE;

    int *pb_a = (int *)env->GetDirectBufferAddress(bufa);
    int *pb_b = (int *)env->GetDirectBufferAddress(bufb);

    if (pb_a == NULL || pb_b == NULL) return JNI_FALSE;
    if (comparePixelBuffer(pb_a, pb_b, width, height, threshold, NULL))
        return JNI_TRUE;
    else return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_andchecker_NativeHelper_fork
(JNIEnv *, jclass) {
    return fork() >= 0;
}

#ifdef __cplusplus
}
#endif

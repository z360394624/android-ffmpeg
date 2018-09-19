//
// Created by Lucius Zhang on 2018/9/13.
//

#ifndef ANDROIDFFMPEG_FFMPEG_NATIVE_H
#define ANDROIDFFMPEG_FFMPEG_NATIVE_H

#include <stdio.h>
#include <time.h>


#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswscale/swscale.h"
#include "libavutil/log.h"


#include <jni.h>
#include <android/log.h>
#define LOGE(format, ...)  __android_log_print(ANDROID_LOG_ERROR, "NativeE", format, ##__VA_ARGS__)
#define LOGI(format, ...)  __android_log_print(ANDROID_LOG_INFO,  "NativeI", format, ##__VA_ARGS__)
#define LOGW(format, ...)  __android_log_print(ANDROID_LOG_WARN,  "NativeW", format, ##__VA_ARGS__)
#else
#define LOGE(format, ...)  printf("FFMPEG err  " format "\n", ##__VA_ARGS__)
#define LOGI(format, ...)  printf("FFMPEG info " format "\n", ##__VA_ARGS__)


#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_integer_ffmpeg_nativeintf_FFmpegNative
 * Method:    initial
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_initial
        (JNIEnv *, jobject, jint, jint, jstring);

/*
 * Class:     com_integer_ffmpeg_nativeintf_FFmpegNativeJ
 * Method:    encode
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_encode
        (JNIEnv *, jobject, jbyteArray);

/*
 * Class:     com_integer_ffmpeg_nativeintf_FFmpegNativeJ
 * Method:    flush
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_flush
        (JNIEnv *, jobject);

/*
 * Class:     com_integer_ffmpeg_nativeintf_FFmpegNativeJ
 * Method:    close
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_close
        (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif

#endif //ANDROIDFFMPEG_FFMPEG_NATIVE_H





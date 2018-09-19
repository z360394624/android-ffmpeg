#include <jni.h>
#include <string>

#include "ffmpeg-native.h"



AVFormatContext *ofmt_ctx;
AVStream* video_st;
AVCodecContext* pCodecCtx;
AVCodec* pCodec;
AVPacket enc_pkt;
AVFrame *pFrameYUV;


extern "C"
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_initial
        (JNIEnv *pEnv, jobject, jint width, jint height, jstring cache) {

    const char* _cache = NULL;

    if (cache) {
        _cache = pEnv -> GetStringUTFChars(cache, JNI_FALSE);
    } else {
        return -1;
    }

    LOGI("input: %d : %d, dir = %s", width, height, _cache);

    if (_cache) {
        pEnv -> ReleaseStringUTFChars(cache, _cache);
    } else {
        LOGW("cache release failed");
    }

    return 1;
}

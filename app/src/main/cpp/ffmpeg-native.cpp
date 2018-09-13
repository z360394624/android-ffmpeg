#include <jni.h>
#include <string>

#include "ffmpeg-native.h"



AVFormatContext *ofmt_ctx;
AVStream* video_st;
AVCodecContext* pCodecCtx;
AVCodec* pCodec;
AVPacket enc_pkt;
AVFrame *pFrameYUV;


extern "C" JNIEXPORT jstring

JNICALL
Java_com_example_luciuszhang_androidffmpeg_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

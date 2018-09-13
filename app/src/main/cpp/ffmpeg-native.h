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
#define LOGE(format, ...)  __android_log_print(ANDROID_LOG_ERROR, "android err", format, ##__VA_ARGS__)
#define LOGI(format, ...)  __android_log_print(ANDROID_LOG_INFO,  "android inf", format, ##__VA_ARGS__)
#else
#define LOGE(format, ...)  printf("FFMPEG err  " format "\n", ##__VA_ARGS__)
#define LOGI(format, ...)  printf("FFMPEG info " format "\n", ##__VA_ARGS__)

#endif //ANDROIDFFMPEG_FFMPEG_NATIVE_H

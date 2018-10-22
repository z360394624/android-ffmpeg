
#include <jni.h>
#include <string>

#include "ffmpeg-native.h"



AVFormatContext *ofmt_ctx;
AVOutputFormat *fmt;
AVStream* video_st;
AVCodecContext* pCodecCtx;
AVCodec* pCodec;
AVPacket enc_pkt;
AVFrame *pFrameYUV;


int framecnt = 0;
int y_length;
int uv_length;
int64_t start_time;
int pts = 0;


void a_log(void *ptr, int level, const char* fmt, va_list v1) {
    char *log = (char*)malloc(strlen(fmt));
    memcpy(log, fmt, strlen(fmt));
    vsprintf(log, fmt, v1);
    LOGE("LOG >>>>>>>>>>>>>>> level: %d fmt: %s\n", level, log);
    free(log);
}


extern "C"
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_initial
        (JNIEnv *pEnv, jobject, jint width, jint height, jstring cache) {

    const char* _cache = NULL;

    if (cache) {
        _cache = pEnv -> GetStringUTFChars(cache, JNI_FALSE);
    } else {
        return -1;
    }

    y_length = width * height;
    uv_length = width * height / 4;

    av_log_set_callback(a_log);

    /** 注册所有复用，解复用器，注册协议 */
    av_register_all();

    /** init output file type */
    avformat_alloc_output_context2(&ofmt_ctx, fmt, "mp4", _cache);

    /** fine & init encoder */
    pCodec = avcodec_find_encoder(AV_CODEC_ID_H264);

    if (!pCodec) {
        LOGE("Can not find encoder!\n");
        return -1;
    }

    /** 创建AVCodecContext结构 */
    pCodecCtx = avcodec_alloc_context3(pCodec);
    /** 设置数据源格式 YUV420P */
    pCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P;
    pCodecCtx->width = width;
    pCodecCtx->height = height;

    /** AVRational是一个分数结构，num分子，den分母，这里按照帧率设定 */
    pCodecCtx->time_base.num = 1;
    pCodecCtx->time_base.den = 30;
    /** 设置码率，单位kbps (kb/s) */
    pCodecCtx->bit_rate = 800000;
    /** 关键帧周期，一个帧组的最大帧数，一个关键帧I，多个预测帧P、B。过大影响seek，且影响编码效率； 过小影响带宽，增加负载，但是提升质量 */
    pCodecCtx->gop_size = 300;

    if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER) {
        pCodecCtx->flags = pCodecCtx->flags | AV_CODEC_FLAG_GLOBAL_HEADER;
    }

    /** 量化值设置 */
    pCodecCtx->qmin = 10;
    pCodecCtx->qmax = 51;

    /** I帧和P帧之间能插入的B帧的最大数量，1～16，越大编码越慢 */
    pCodecCtx->max_b_frames = 3;

    AVDictionary *param = 0;
    av_dict_set(&param, "preset", "slow", 0);
    av_dict_set(&param, "tune", "zerolatency", 0);

    /** open encoder */
    if (avcodec_open2(pCodecCtx, pCodec, &param) < 0) {
        LOGE("Failed to open encoder!\n");
        return -1;
    }

    /** create output stream */
    video_st = avformat_new_stream(ofmt_ctx, pCodec);

    if (video_st == NULL) {
        return -1;
    }

    video_st->time_base.num = 1;
    video_st->time_base.den = 30;
    video_st->codec = pCodecCtx;

    /** open output file */
    if (avio_open(&ofmt_ctx->pb, _cache, AVIO_FLAG_READ_WRITE) < 0) {
        LOGE("Failed to open output file!\n");
        return -1;
    }

    /** set output file header */
    avformat_write_header(ofmt_ctx, NULL);

    start_time = av_gettime();

    LOGI("input: %d : %d, dir = %s", width, height, _cache);

    if (_cache) {
        pEnv -> ReleaseStringUTFChars(cache, _cache);
    } else {
        LOGW("cache release failed");
    }

    return 1;
}

//extern "C"
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_encode
        (JNIEnv *env, jobject, jbyteArray yuv) {

    LOGE("start encode\n");
    int ret;
    int enc_got_frame = 0;

    /** allow mem */
    pFrameYUV = av_frame_alloc();
    /** YUV数据格式 */
    int y_size = pCodecCtx->width * pCodecCtx->height;
    int size = avpicture_get_size(AV_PIX_FMT_YUV420P, pCodecCtx->width, pCodecCtx->height);
    uint8_t *out_buffer = (uint8_t *)av_malloc(size);
    avpicture_fill((AVPicture*)pFrameYUV, out_buffer, AV_PIX_FMT_YUV420P, pCodecCtx->width, pCodecCtx->height);
    LOGE("avpicture_fill : width: %d, height: %d \n", pCodecCtx->width, pCodecCtx->height);
    jbyte* in= (*env).GetByteArrayElements(yuv, JNI_FALSE);


    memcpy(pFrameYUV->data[0], in, y_length);

    for (int i = 0; i < uv_length; i ++) {
        *(pFrameYUV->data[2] + i) = *(in + y_length + i * 2);
        *(pFrameYUV->data[1] + i) = *(in + y_length + i*2 + 1);
    }


//    pFrameYUV->pts = pts;
//    pts ++;


    pFrameYUV->format = AV_PIX_FMT_YUV420P;
    pFrameYUV->width = pCodecCtx->width;
    pFrameYUV->height = pCodecCtx->height;

    enc_pkt.data = NULL;
    enc_pkt.size = 0;

    /** init AVPacket */
    av_init_packet(&enc_pkt);

    /** encode AVFrame(YUV) data to AVPacket(mkv) */
    ret = avcodec_encode_video2(pCodecCtx, &enc_pkt, pFrameYUV, &enc_got_frame);

    /** release  AVFrame*/
    av_frame_free(&pFrameYUV);

    if (enc_got_frame == 1) {
        LOGI("Succeed to encode frame: %5d\tsize:%5d\n", framecnt, enc_pkt.size);
        framecnt ++;
        enc_pkt.stream_index = video_st->index;

        AVRational time_base = ofmt_ctx->streams[0]->time_base;
        AVRational r_framerate1 = {60, 2};
        AVRational time_base_q = {1, AV_TIME_BASE};

        int64_t calc_duration = (double)(AV_TIME_BASE)*(1 / av_q2d(r_framerate1));

        enc_pkt.pts = av_rescale_q(framecnt*calc_duration, time_base_q, time_base);
        enc_pkt.dts = enc_pkt.pts;
        enc_pkt.duration = av_rescale_q(calc_duration, time_base_q, time_base);
        enc_pkt.pos = -1;

        int64_t pts_time = av_rescale_q(enc_pkt.dts, time_base, time_base_q);
        int64_t now_time = av_gettime() - start_time;
        if (pts_time > now_time) {
            av_usleep(pts_time - now_time);
        }

        ret = av_write_frame(ofmt_ctx, &enc_pkt);

        av_free_packet(&enc_pkt);
    }

    return 0;


}


//extern "C"
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_flush
        (JNIEnv *env, jobject obj) {
    int ret;
    int got_frame;

    AVPacket enc_pkt;
    const AVCodec *tmp = ofmt_ctx->streams[0]->codec->codec;

    if (!(tmp->capabilities & AV_CODEC_CAP_DELAY)) {
        LOGI("AV_CODEC_CAP_DELAY\n");
        return 0;
    }
    int count = 0;
    while (1) {
        enc_pkt.data = NULL;
        enc_pkt.size = 0;
        av_init_packet(&enc_pkt);
        ret = avcodec_encode_video2(ofmt_ctx->streams[0]->codec, &enc_pkt, NULL, &got_frame);
        av_frame_free(NULL);
        if (ret < 0) {
            break;
        }
        if (!got_frame) {
            ret = 0;
            break;
        }

        count ++;
        LOGI("FLUSH Encoder: Success to encode 1 frame!\t size: %5d\n", enc_pkt.size);


        AVRational time_base = ofmt_ctx->streams[0]->time_base;
        AVRational r_framerate1 = {60, 2};
        AVRational time_base_q = {1, AV_TIME_BASE};

        int64_t calc_duration = (double)(AV_TIME_BASE)*(1 / av_q2d(r_framerate1));

        enc_pkt.pts = av_rescale_q(framecnt*calc_duration, time_base_q, time_base);
        enc_pkt.dts = enc_pkt.pts;
        enc_pkt.duration = av_rescale_q(calc_duration, time_base_q, time_base);
        enc_pkt.pos = -1;

        framecnt++;
        ofmt_ctx->duration = enc_pkt.duration * framecnt;

        ret = av_write_frame(ofmt_ctx, &enc_pkt);
        if (ret < 0) {
            break;
        }
    }
    LOGI("count ===== %d\n", count);
    av_write_trailer(ofmt_ctx);
    return ret;
}


//extern "C"
JNIEXPORT jint JNICALL Java_com_integer_ffmpeg_nativeintf_FFmpegNative_close
        (JNIEnv *env, jobject obj) {

    if (video_st) {
        avcodec_close(video_st->codec);
    }
    if (ofmt_ctx) {
        avio_close(ofmt_ctx->pb);
//        avformat_free_context(ofmt_ctx);
        for (int i=0; i<ofmt_ctx->nb_streams; i++)
        {
            av_freep(&ofmt_ctx->streams[i]->codec);
            av_freep(&ofmt_ctx->streams[i]);
        }
        av_free(ofmt_ctx);
        avformat_free_context(ofmt_ctx);
    }

    return 0;
}





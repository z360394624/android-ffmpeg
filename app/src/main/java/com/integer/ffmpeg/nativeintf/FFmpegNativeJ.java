package com.integer.ffmpeg.nativeintf;

public class FFmpegNativeJ {


    //JNI
    //初始化，读取待编码数据的宽和高
    public native int initial(int width,int height);
    //读取yuv数据进行编码
    public native int encode(byte[] yuvimage);
    //清空缓存的帧
    public native int flush();
    //清理
    public native int close();


}

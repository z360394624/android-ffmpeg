package com.integer.ffmpeg.nativeintf

/**
 * @detail
 *
 * @author luciuszhang
 * @date 2018/9/14 11:57
 */

class FFmpegNative {

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    //JNI
    //初始化，读取待编码数据的宽和高
    external fun initial(width: Int, height: Int, cache: String): Int

    //读取yuv数据进行编码
    external fun encode(yuvimage: ByteArray): Int

    //清空缓存的帧
    external fun flush(): Int

    //清理
    external fun close(): Int


    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("ffmpeg-native")
        }
    }


}
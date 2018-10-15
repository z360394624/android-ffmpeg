package com.integer.ffmpeg

import android.os.Environment
import android.util.Log
import com.integer.ffmpeg.nativeintf.FFmpegNative

/**
 * @detail
 *
 * @author luciuszhang
 * @date 2018/10/15 16:06
 */
class FFmpeg {

    companion object {
        private const val TAG = "FFmpeg"
    }

    private val ffmpegNative: FFmpegNative = FFmpegNative()

    fun init(width: Int, height: Int) {
        Log.d(TAG, "initFFmpeg")
        val localStorge = Environment.getExternalStorageDirectory().absolutePath.plus("/").plus("${System.currentTimeMillis()}.mp4")
        Log.d(TAG, "localStorge = $localStorge")
        ffmpegNative.initial(width, height, localStorge)
    }

    fun encode(data: ByteArray) {

        ffmpegNative.encode(data)
    }


    fun flush() {
        ffmpegNative.flush()
    }

    fun close() {
        ffmpegNative.close()
    }
}
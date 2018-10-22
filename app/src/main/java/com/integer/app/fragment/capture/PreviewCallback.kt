package com.integer.app.fragment.capture

import android.hardware.Camera
import android.util.Log

/**
 * @detail
 *
 * @author luciuszhang
 * @date 2018/10/22 18:30
 */
class PreviewCallback : Camera.PreviewCallback {

    companion object {
        private const val TAG = "PreviewCallback"
    }

    var recording: Boolean = false

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (recording) {
            Log.d(TAG, "data size: ${data?.size ?: 0}")
        }
    }
}
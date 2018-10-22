package com.integer.app.fragment.capture

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.util.Log

/**
 * @detail
 *
 * @author luciuszhang
 * @date 2018/10/22 17:32
 */
class CameraManager(context: Context) {

    companion object {
        private const val TAG = "CameraManager"
        val CAMERA_BACK = 0
        val CAMERA_FRONT = 1
    }



    private var context: Context = context

    private var previewCallback = PreviewCallback()


    private var currentCameraIndex = 0



    @Synchronized fun openDriver(texture: SurfaceTexture): Camera? {
        val numCameras = Camera.getNumberOfCameras()
        if (numCameras == 0) {
            Log.w(TAG, "No cameras!")
            return null
        }
        val camera = Camera.open(currentCameraIndex)
        CameraConfigManager.initFromCameraParameters(camera, context)
        CameraConfigManager.setDesiredCameraParameters(camera)
        camera?.setPreviewTexture(texture)
        return camera
    }

    fun startPreview(camera: Camera?) {
        camera?.setOneShotPreviewCallback(previewCallback)
        camera?.startPreview()
    }


    fun stopPreview(camera: Camera?) {
        camera?.setOneShotPreviewCallback(null)
        camera?.stopPreview()
    }


    fun startRecording() {
        previewCallback.recording = true
    }

    fun stopRecording() {
        previewCallback.recording = false
    }

    fun closeCamera(camera: Camera?) {
        camera?.release()
    }




}
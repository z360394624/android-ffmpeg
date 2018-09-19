package com.integer.ffmpeg.app.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.*
import com.example.android.camera2basic.CompareSizesByArea
import com.example.android.camera2basic.ConfirationDialog
import com.example.android.camera2basic.ErrorDialog
import com.integer.ffmpeg.R
import com.integer.ffmpeg.app.REQUEST_CAMERA_PERMISSION
import com.integer.ffmpeg.app.view.AutoFitTextureView
import kotlinx.android.synthetic.main.fragment_capture.*
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * @detail
 *
 * @author luciuszhang
 * @date 2018/9/18 14:56
 */
class CaptureFragmentV2: Fragment(), ActivityCompat.OnRequestPermissionsResultCallback {


    companion object {
        private const val TAG = "CaptureFragmentV2"


        @JvmStatic fun newInstance(): CaptureFragmentV2 = CaptureFragmentV2()
    }

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /** 0为前置摄像头， 1为后置摄像头 */
    private var cameraIds: Array<String?> =  arrayOfNulls(2)
    private var currentCameraId: String? = ""

    /** A [Handler] for running tasks in the background. */
    private var backgroundHandler: Handler? = null

    /** An additional thread for running tasks that shouldn't block the UI. */
    private var backgroundThread: HandlerThread? = null

    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var previewRequest: CaptureRequest
    private lateinit var previewTextureView: AutoFitTextureView


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?  =
            inflater.inflate(R.layout.fragment_capture, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewTextureView = main_texture_view
    }

    override fun onResume() {
        super.onResume()
        if (!requestPermission()) return
        prepareThread()
        detectionCamera()
        calculateCameraParameters()
        if (previewTextureView.isAvailable) {
            openCamera(previewTextureView.width, previewTextureView.height)
        } else {
            previewTextureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(childFragmentManager, "request permission camera")
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /** 准备工作线程 */
    private fun prepareThread() {
        backgroundThread = HandlerThread("CaptureV2").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /** 请求权限 */
    private fun requestPermission(): Boolean {
        val context = activity
        return if (context == null) {
            false
        } else {
            val permission = ContextCompat.checkSelfPermission(context.applicationContext, Manifest.permission.CAMERA)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    ConfirationDialog().show(childFragmentManager, "request camera")
                } else {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
                }
                false
            } else {
                true
            }
        }
    }

    /** 检测摄像头 */
    private fun detectionCamera() {
        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (cameraId in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                cameraIds[0] = cameraId
            } else if (cameraDirection == CameraCharacteristics.LENS_FACING_BACK) {
                cameraIds[1] = cameraId
            }
        }
        currentCameraId = cameraIds[0]
    }

    /** 根据当前摄像头计算所需参数 */
    private fun calculateCameraParameters() {
        Log.d(TAG, "calculateCameraParameters===============")
        val context = activity
        if (context != null) {
            Log.d(TAG, "setOnImageAvailableListener")
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics: CameraCharacteristics = manager.getCameraCharacteristics(currentCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val largest = Collections.max(Arrays.asList(*map.getOutputSizes(ImageFormat.YUV_420_888)), CompareSizesByArea())
            imageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.YUV_420_888, /*maxImages*/ 1).apply {
                setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
            }
        } else {
            Log.d(TAG, "context is null")
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture?, width: Int, height: Int) {
//            configureTransform(width, height)
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture?) = Unit

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture?): Boolean = true

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture?, width: Int, height: Int) {
            openCamera(width, height)
        }

    }

    /** YUV_420_888数据接收器 */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        // TODO handle YUV_420_888
        backgroundHandler?.post {
            val threadName = Thread.currentThread().name
            val image = reader?.acquireNextImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                Log.d(TAG, "data size = " + data.size + "; $threadName")
                image.close()
            } else {
                Log.d(TAG, "image is null")
            }
        }

    }


    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        Log.d(TAG, "open camera ===========================")
        val manager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(currentCameraId, deviceStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            Log.d(TAG, "deviceStateCallback onOpened ===========================cameraDevice = $cameraDevice")
            this@CaptureFragmentV2.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }
        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CaptureFragmentV2.cameraDevice = null
        }
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@CaptureFragmentV2.activity?.finish()
        }
    }

    /**
     * 创建camera会话
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = previewTextureView.surfaceTexture
            val surface = Surface(texture)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)
            previewRequestBuilder.addTarget(imageReader?.surface)
            /** camera数据发送到两个surface上 */
            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader?.surface), captureStateCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private val captureStateCallback = object : CameraCaptureSession.StateCallback() {

        /** camera设置完成，会话创建成功，在此处开始请求 */
        override fun onConfigured(session: CameraCaptureSession?) {
            Log.d(TAG, "onConfigured =========================== $cameraDevice")
            if (cameraDevice == null) {
                return
            } else {
                Log.d(TAG, "cameraDevice not empty")
            }
            captureSession = session
            try {
                /** 自动对焦模式是continuous */
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                /** 闪光灯自动模式 */
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                /** 创建请求 */
                previewRequest = previewRequestBuilder.build()
                /** 开始请求 */
                captureSession?.setRepeatingRequest(previewRequest, null, backgroundHandler)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession?) {
            Log.d(TAG, "onConfigureFailed ===========================")
        }
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun stopThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }

    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        private fun process(result: CaptureResult) {}
    }



}
package com.integer.app.fragment

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
import android.widget.ImageView
import android.widget.TextView
import com.example.android.camera2basic.CompareSizesByArea
import com.example.android.camera2basic.ConfirationDialog
import com.example.android.camera2basic.ErrorDialog
import com.integer.ffmpeg.R
import com.integer.app.REQUEST_CAMERA_PERMISSION
import com.integer.app.view.AutoFitTextureView
import com.integer.ffmpeg.FFmpeg
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
class CaptureFragmentV2: Fragment(), ActivityCompat.OnRequestPermissionsResultCallback, View.OnClickListener {


    companion object {
        private const val TAG = "CaptureFragmentV2"
        @JvmStatic fun newInstance(): CaptureFragmentV2 = CaptureFragmentV2()
    }

    /** A [Semaphore] to prevent the app from exiting before closing the camera. */
    private val cameraOpenCloseLock = Semaphore(1)

    /** 0为前置摄像头， 1为后置摄像头 */
    private var cameraIds: Array<String?> =  arrayOfNulls(2)
    private var currentCameraId: String? = ""

    /** A [Handler] for running tasks in the background. */
    private var backgroundHandler: Handler? = null

    /** An additional thread for running tasks that shouldn't block the UI. */
    private var backgroundThread: HandlerThread? = null

    private var recording: Boolean = false

    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var previewCaptureSession: CameraCaptureSession? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var previewRequest: CaptureRequest

    private var ffmpeg: FFmpeg = FFmpeg()

    private var recordCaptureSession: CameraCaptureSession? = null
    private lateinit var recordRequestBuilder: CaptureRequest.Builder
    private lateinit var recordRequest: CaptureRequest

    private lateinit var previewTextureView: AutoFitTextureView
    private lateinit var mainStartRecordView: ImageView
    private lateinit var mainSwitchCameraTextView: TextView


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?  =
            inflater.inflate(R.layout.fragment_capture, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewTextureView = main_texture_view
        mainStartRecordView = iv_main_start_record
        mainSwitchCameraTextView = tv_main_switch_camera

        mainSwitchCameraTextView.setOnClickListener(this)
        mainStartRecordView.setOnClickListener(this)
        mainStartRecordView.isSelected = recording
    }

    override fun onResume() {
        super.onResume()
        if (!requestPermission()) return
        prepareThread()
        detectionCamera()
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

    override fun onClick(v: View?) {
       if (v == mainStartRecordView) {
           switchRecord()
       } else if (v == mainSwitchCameraTextView) {
           switchCamera()
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
        val context = activity
        if (context != null) {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics: CameraCharacteristics = manager.getCameraCharacteristics(currentCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val largest = Collections.max(Arrays.asList(*map.getOutputSizes(ImageFormat.YUV_420_888)), CompareSizesByArea())
            imageReader = ImageReader.newInstance(largest.width, largest.height, ImageFormat.YUV_420_888, /*maxImages*/ 1).apply {
                setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
            }
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



    /** create camera preview session */
    private fun createCameraPreviewSession() {
        try {
            val texture = previewTextureView.surfaceTexture
            val surface = Surface(texture)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)
            /** send camera data to preview surface */
            cameraDevice?.createCaptureSession(Arrays.asList(surface), previewCaptureStateCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** create camera data handle session */
    private fun createCameraRecordSession() {
        try {
            closePreviewSession()
            calculateCameraParameters()
            val texture = previewTextureView.surfaceTexture
            val surface = Surface(texture)
            recordRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            recordRequestBuilder.addTarget(surface)
            recordRequestBuilder.addTarget(imageReader?.surface)
            /** send camera data to preview surface */
            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader?.surface), recordCaptureStateCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 初始化编码器 */
    private fun initFFmpeg() {
        Log.d(TAG, "initFFmpeg")
        ffmpeg.init(previewTextureView.width, previewTextureView.height)
    }

    /** 切换摄像头 */
    private fun switchCamera() {

        closeCamera()
        currentCameraId = if (cameraIds.indexOf(currentCameraId) == 0) {
            cameraIds[1]
        } else {
            cameraIds[0]
        }
        if (previewTextureView.isAvailable) {
            openCamera(previewTextureView.width, previewTextureView.height)
        } else {
            previewTextureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    /** turn on/off recording */
    private fun switchRecord() {
        if (recording) {
            Log.d(TAG, "stop recording")
            recording = false
            closeRecordSession()
            createCameraPreviewSession()
        } else {
            Log.d(TAG, "start recording")
            recording = true
            createCameraRecordSession()
        }
        mainStartRecordView.isSelected = recording
    }

    /** close preview seesion */
    private fun closePreviewSession() {
        previewCaptureSession?.close()
        previewCaptureSession = null
    }


    /** close recording session */
    private fun closeRecordSession() {
        recordCaptureSession?.close()
        recordCaptureSession = null
        imageReader?.close()
        imageReader = null

        ffmpeg.close()
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            closePreviewSession()
            closeRecordSession()
            cameraDevice?.close()
            cameraDevice = null
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

    /** CameraDevice state listener */
    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            Log.d(TAG, "deviceStateCallback onOpened ===========================cameraDevice = $cameraDevice")
            this@CaptureFragmentV2.cameraDevice = cameraDevice
            /** create preview session */
            createCameraPreviewSession()
            /** init FFmpeg */
            initFFmpeg()
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

    /** camera preview session state listener  */
    private val previewCaptureStateCallback = object : CameraCaptureSession.StateCallback() {

        /** camera session configured, create preview request */
        override fun onConfigured(session: CameraCaptureSession?) {
            Log.d(TAG, "onConfigured =========================== $cameraDevice")
            if (cameraDevice == null) {
                return
            } else {
                Log.d(TAG, "cameraDevice not empty")
            }
            previewCaptureSession = session
            try {
                /** 自动对焦模式是continuous */
                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                /** 闪光灯自动模式 */
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                /** 创建请求 */
                previewRequest = previewRequestBuilder.build()
                /** start preview */
                previewCaptureSession?.setRepeatingRequest(previewRequest, null, backgroundHandler)

            } catch (e: Exception) {
                e?.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession?) {
        }
    }


    /** camera data handle session state listener  */
    private val recordCaptureStateCallback = object : CameraCaptureSession.StateCallback() {

        /** camera session configured, create preview request */
        override fun onConfigured(session: CameraCaptureSession?) {
            Log.d(TAG, "onConfigured =========================== $cameraDevice")
            if (cameraDevice == null) {
                return
            } else {
                Log.d(TAG, "cameraDevice not empty")
            }
            recordCaptureSession = session
            try {
                /** 自动对焦模式是continuous */
                recordRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                /** 闪光灯自动模式 */
                recordRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                /** 创建请求 */
                recordRequest = recordRequestBuilder.build()
                /** start preview */
                recordCaptureSession?.setRepeatingRequest(recordRequest, null, backgroundHandler)

            } catch (e: Exception) {
                e?.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession?) {
        }
    }


    /** YUV_420_888 data handler */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        // TODO handle YUV_420_888
        backgroundHandler?.post {
            val threadName = Thread.currentThread().name
            val image = reader?.acquireNextImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                /** ffmpeg encode */
                ffmpeg.encode(data)
                ffmpeg.flush()
                image.close()
            } else {
                Log.d(TAG, "image is null")
            }
        }

    }


}
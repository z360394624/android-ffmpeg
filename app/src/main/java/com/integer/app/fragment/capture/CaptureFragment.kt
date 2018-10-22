package com.integer.app.fragment.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.example.android.camera2basic.ConfirationDialog
import com.example.android.camera2basic.ErrorDialog
import com.integer.app.REQUEST_CAMERA_PERMISSION
import com.integer.app.view.AutoFitTextureView
import com.integer.ffmpeg.R
import kotlinx.android.synthetic.main.fragment_capture.*

/**
 * @detail
 *
 * @author luciuszhang
 * @date 2018/10/22 15:50
 */
class CaptureFragment : Fragment(), View.OnClickListener {


    companion object {
        private const val TAG = "CaptureFragment"
        @JvmStatic fun newInstance(): CaptureFragment = CaptureFragment()
    }

    private lateinit var previewTextureView: AutoFitTextureView
    private lateinit var mainStartRecordView: ImageView
    private lateinit var mainSwitchCameraTextView: TextView

    private var cameraManager: CameraManager? = null
    /** 是否在录制过程中 */
    private var recording: Boolean = false

    /** 是否是关闭页面 */
    private var closing: Boolean = false


    private var camera: Camera? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_capture, container, false)


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (cameraManager == null && activity != null) {
            cameraManager = CameraManager(activity!!.applicationContext)
        }
    }


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
        if (previewTextureView.isAvailable) {
            openCamera(previewTextureView.width, previewTextureView.height)
        } else {
            previewTextureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closing = true
        cameraManager?.stopPreview(camera)
        super.onPause()

    }

    override fun onDestroyView() {
        cameraManager?.closeCamera(camera)
        super.onDestroyView()
    }

    override fun onClick(v: View?) {

        when (v) {
            mainSwitchCameraTextView -> {

            }

            mainStartRecordView -> {

                if (recording) {
                    recording = false
                    cameraManager?.stopRecording()
                } else {
                    recording = true
                    cameraManager?.startRecording()
                }
            }
        }
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


    private fun openCamera(width: Int, height: Int) {
        try {
            camera = cameraManager?.openDriver(previewTextureView.surfaceTexture)
            cameraManager?.startPreview(camera)
        } catch (e: Exception) {
            Log.d(TAG, "exception: $e")
        }
    }


    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture?, width: Int, height: Int) {}

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture?) = Unit

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture?): Boolean = true

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture?, width: Int, height: Int) {
            openCamera(width, height)
        }

    }

}
package com.integer.app.fragment.capture

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import java.util.*

/**
 * @detail
 *
 * @author luciuszhang
 * @date 2018/10/22 17:47
 */
object CameraConfigManager {

    private const val TAG = "CameraConfigManager"

    const val CAMERA_BACK = 0
    const val CAMERA_FRONT = 1
    /**
     * This is bigger than the size of a small screen, which is still supported.
     * The routine below will still select the default (presumably 320x240) size for these.
     * This prevents accidental selection of very low resolution on some devices.
     */
    private const val MIN_PREVIEW_PIXELS = 480 * 320
    private const val MAX_ASPECT_DISTORTION = 0.15
    private const val AREA_PER_1000 = 400
    private const val MIN_FPS = 10
    private const val MAX_FPS = 20

    private var cwNeededRotation: Int = 0
    private var cwRotationFromDisplayToCamera: Int = 0
    private var screenResolution: Point? = null
    private var cameraResolution: Point? = null
    private var bestPreviewSize: Point? = null
    private var previewSizeOnScreen: Point? = null

    private val cameraInfo = Camera.CameraInfo()

    fun initFromCameraParameters(camera: Camera?, context: Context) {

        val parameters = camera?.parameters
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = manager.defaultDisplay
        val displayRotation = display.rotation
        Log.d(TAG, "displayRotation = $displayRotation")
        val cwRotationFromNaturalToDisplay = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else ->
                // Have seen this return incorrect values like -90
                if (displayRotation % 90 == 0) {
                    (360 + displayRotation) % 360
                } else {
                    throw IllegalArgumentException("Bad rotation: $displayRotation")
                }
        }

        Log.i(TAG, "Display at: $cwRotationFromNaturalToDisplay")
        Camera.getCameraInfo(0, cameraInfo)
        var cwRotationFromNaturalToCamera = cameraInfo.orientation
        Log.i(TAG, "Camera at: $cwRotationFromNaturalToCamera")

        // Still not 100% sure about this. But acts like we need to flip this:
        if (cameraInfo.facing == CAMERA_FRONT) {
            cwRotationFromNaturalToCamera = (360 - cwRotationFromNaturalToCamera) % 360
            Log.i(TAG, "Front camera overriden to: $cwRotationFromNaturalToCamera")
        }

        cwRotationFromDisplayToCamera = (360 + cwRotationFromNaturalToCamera - cwRotationFromNaturalToDisplay) % 360
        Log.i(TAG, "Final display orientation: $cwRotationFromDisplayToCamera")
        cwNeededRotation = if (cameraInfo.facing == CAMERA_FRONT) {
            Log.i(TAG, "Compensating rotation for front camera")
            (360 - cwRotationFromDisplayToCamera) % 360
        } else {
            cwRotationFromDisplayToCamera
        }
        Log.i(TAG, "Clockwise rotation from display to camera: $cwNeededRotation")


        val theScreenResolution = Point()
        display.getSize(theScreenResolution)
        screenResolution = theScreenResolution
        Log.i(TAG, "Screen resolution in current orientation: $screenResolution")
        cameraResolution = findBestPreviewSizeValue(parameters!!, screenResolution!!)
        Log.i(TAG, "Camera resolution: $cameraResolution")
        bestPreviewSize = findBestPreviewSizeValue(parameters, screenResolution!!)
        Log.i(TAG, "Best available preview size: $bestPreviewSize")

        val isScreenPortrait = screenResolution!!.x < screenResolution!!.y
        val isPreviewSizePortrait = bestPreviewSize!!.x < bestPreviewSize!!.y

        if (isScreenPortrait == isPreviewSizePortrait) {
            previewSizeOnScreen = bestPreviewSize
        } else {
            previewSizeOnScreen = Point(bestPreviewSize!!.y, bestPreviewSize!!.x)
        }
        Log.i(TAG, "Preview size on screen: $previewSizeOnScreen")
    }


    internal fun setDesiredCameraParameters(camera: Camera?) {

        val parameters = camera?.parameters
        if (parameters == null) {
            Log.w(TAG, "Device error: no camera parameters are available. Proceeding without configuration.")
            return
        }
        Log.i(TAG, "Initial camera parameters: " + parameters!!.flatten())


        initializeTorch(parameters)

        setFocus(
                parameters,
                true,
                true)

        setVideoStabilization(parameters)
        setFocusArea(parameters)
        setMetering(parameters)
        parameters!!.setRecordingHint(true)

        parameters!!.setPreviewSize(bestPreviewSize!!.x, bestPreviewSize!!.y)

        camera?.parameters = parameters

        camera?.setDisplayOrientation(cwRotationFromDisplayToCamera)

        val afterParameters = camera?.parameters
        val afterSize = afterParameters.previewSize
        if (afterSize != null && (bestPreviewSize!!.x != afterSize!!.width || bestPreviewSize!!.y != afterSize!!.height)) {
            Log.w(TAG, "Camera said it supported preview size " + bestPreviewSize!!.x + 'x'.toString() + bestPreviewSize!!.y +
                    ", but after setting it, preview size is " + afterSize!!.width + 'x'.toString() + afterSize!!.height)
            bestPreviewSize!!.x = afterSize!!.width
            bestPreviewSize!!.y = afterSize!!.height
        }
    }


    private fun setFocus(parameters: Camera.Parameters,
                         autoFocus: Boolean,
                         disableContinuous: Boolean) {
        val supportedFocusModes = parameters.getSupportedFocusModes()
        var focusMode: String? = null
        if (autoFocus) {
            if (disableContinuous) {
                focusMode = findSettableValue("focus mode",
                        supportedFocusModes,
                        Camera.Parameters.FOCUS_MODE_AUTO)
            } else {
                focusMode = findSettableValue("focus mode",
                        supportedFocusModes,
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
                        Camera.Parameters.FOCUS_MODE_AUTO)
            }
        }
        // Maybe selected auto-focus but not available, so fall through here:
        if (focusMode == null) {
            focusMode = findSettableValue("focus mode",
                    supportedFocusModes,
                    Camera.Parameters.FOCUS_MODE_MACRO,
                    Camera.Parameters.FOCUS_MODE_EDOF)
        }
        if (focusMode != null) {
            if (focusMode == parameters.getFocusMode()) {
                Log.i(TAG, "Focus mode already set to $focusMode")
            } else {
                parameters.focusMode = focusMode
            }
        }
    }


    private fun setVideoStabilization(parameters: Camera.Parameters) {
        if (parameters.isVideoStabilizationSupported) {
            if (parameters.videoStabilization) {
                Log.i(TAG, "Video stabilization already enabled")
            } else {
                Log.i(TAG, "Enabling video stabilization...")
                parameters.videoStabilization = true
            }
        } else {
            Log.i(TAG, "This device does not support video stabilization")
        }
    }


    private fun findSettableValue(name: String,
                                  supportedValues: Collection<String>?,
                                  vararg desiredValues: String): String? {
        Log.i(TAG, "Requesting " + name + " value from among: " + Arrays.toString(desiredValues))
        Log.i(TAG, "Supported $name values: $supportedValues")
        if (supportedValues != null) {
            for (desiredValue in desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    Log.i(TAG, "Can set $name to: $desiredValue")
                    return desiredValue
                }
            }
        }
        Log.i(TAG, "No supported values match")
        return null
    }

    private fun setFocusArea(parameters: Camera.Parameters) {
        if (parameters.maxNumFocusAreas > 0) {
            Log.i(TAG, "Old focus areas: ${parameters.focusAreas}")
            val middleArea = buildMiddleArea(AREA_PER_1000)
            Log.i(TAG, "Setting focus area to : $middleArea")
            parameters.focusAreas = middleArea
        } else {
            Log.i(TAG, "Device does not support focus areas")
        }
    }


    private fun buildMiddleArea(areaPer1000: Int): List<Camera.Area> {
        return listOf(Camera.Area(Rect(-areaPer1000, -areaPer1000, areaPer1000, areaPer1000), 1))
    }

    private fun setMetering(parameters: Camera.Parameters) {
        if (parameters.maxNumMeteringAreas > 0) {
            Log.i(TAG, "Old metering areas: ${parameters.meteringAreas}")
            val middleArea = buildMiddleArea(AREA_PER_1000)
            Log.i(TAG, "Setting metering area to : $middleArea")
            parameters.meteringAreas = middleArea
        } else {
            Log.i(TAG, "Device does not support metering areas")
        }
    }

    /**
     * 从相机支持的分辨率中计算出最适合的预览界面尺寸
     *
     * @param parameters
     * @param screenResolution
     * @return
     */
    private fun findBestPreviewSizeValue(parameters: Camera.Parameters,
                                         screenResolution: Point): Point {
        val rawSupportedSizes = parameters
                .supportedPreviewSizes
        if (rawSupportedSizes == null) {
            Log.w(TAG,
                    "Device returned no supported preview sizes; using default")
            val defaultSize = parameters.previewSize
            return Point(defaultSize.width, defaultSize.height)
        }

        // Sort by size, descending
        val supportedPreviewSizes = ArrayList(
                rawSupportedSizes)
        Collections.sort<Camera.Size>(supportedPreviewSizes, Comparator<Camera.Size> { a, b ->
            val aPixels = a.height * a.width
            val bPixels = b.height * b.width
            if (bPixels < aPixels) {
                return@Comparator -1
            }
            if (bPixels > aPixels) {
                1
            } else 0
        })

        if (Log.isLoggable(TAG, Log.INFO)) {
            val previewSizesString = StringBuilder()
            for (supportedPreviewSize in supportedPreviewSizes) {
                previewSizesString.append(supportedPreviewSize.width)
                        .append('x').append(supportedPreviewSize.height)
                        .append(' ')
            }
            Log.i(TAG, "Supported preview sizes: $previewSizesString")
        }

        val screenAspectRatio = screenResolution.x.toDouble() / screenResolution.y.toDouble()

        // Remove sizes that are unsuitable
        val it = supportedPreviewSizes.iterator()
        while (it.hasNext()) {
            val supportedPreviewSize = it.next()
            val realWidth = supportedPreviewSize.width
            val realHeight = supportedPreviewSize.height
            if (realWidth * realHeight < MIN_PREVIEW_PIXELS) {
                it.remove()
                continue
            }

            val isCandidatePortrait = realWidth > realHeight
            val maybeFlippedWidth = if (isCandidatePortrait)
                realHeight
            else
                realWidth
            val maybeFlippedHeight = if (isCandidatePortrait)
                realWidth
            else
                realHeight

            val aspectRatio = maybeFlippedWidth.toDouble() / maybeFlippedHeight.toDouble()
            val distortion = Math.abs(aspectRatio - screenAspectRatio)
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove()
                continue
            }

            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                val exactPoint = Point(realWidth, realHeight)
                Log.i(TAG, "Found preview size exactly matching screen size: $exactPoint")
                return exactPoint
            }
        }

        // If no exact match, use largest preview size. This was not a great
        // idea on older devices because
        // of the additional computation needed. We're likely to get here on
        // newer Android 4+ devices, where
        // the CPU is much more powerful.
        if (!supportedPreviewSizes.isEmpty()) {
            val largestPreview = supportedPreviewSizes[0]
            val largestSize = Point(largestPreview.width,
                    largestPreview.height)
            Log.i(TAG, "Using largest suitable preview size: $largestSize")
            return largestSize
        }

        // If there is nothing at all suitable, return current preview size
        val defaultPreview = parameters.previewSize
        val defaultSize = Point(defaultPreview.width,
                defaultPreview.height)
        Log.i(TAG, "No suitable preview sizes, using default: $defaultSize")

        return defaultSize
    }

    private fun initializeTorch(parameters: Camera.Parameters) {
        val supportedFlashModes = parameters.supportedFlashModes
        val flashMode: String?
        flashMode = findSettableValue("flash mode",
                supportedFlashModes,
                Camera.Parameters.FLASH_MODE_OFF)
        if (flashMode != null) {
            if (flashMode == parameters.flashMode) {
                Log.i(TAG, "Flash mode already set to $flashMode")
            } else {
                Log.i(TAG, "Setting flash mode to $flashMode")
                parameters.flashMode = flashMode
            }
        }
    }

    fun setBestPreviewFPS(parameters: Camera.Parameters) {
        setBestPreviewFPS(parameters, MIN_FPS, MAX_FPS)
    }

    fun setBestPreviewFPS(parameters: Camera.Parameters, minFPS: Int, maxFPS: Int) {
        val supportedPreviewFpsRanges = parameters.supportedPreviewFpsRange
        Log.i(TAG, "Supported FPS ranges: $supportedPreviewFpsRanges")
        if (supportedPreviewFpsRanges != null && !supportedPreviewFpsRanges.isEmpty()) {
            var suitableFPSRange: IntArray? = null
            for (fpsRange in supportedPreviewFpsRanges) {
                val thisMin = fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                val thisMax = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
                if (thisMin >= minFPS * 1000 && thisMax <= maxFPS * 1000) {
                    suitableFPSRange = fpsRange
                    break
                }
            }
            if (suitableFPSRange == null) {
                Log.i(TAG, "No suitable FPS range?")
            } else {
                val currentFpsRange = IntArray(2)
                parameters.getPreviewFpsRange(currentFpsRange)
                if (Arrays.equals(currentFpsRange, suitableFPSRange)) {
                    Log.i(TAG, "FPS range already set to " + Arrays.toString(suitableFPSRange))
                } else {
                    Log.i(TAG, "Setting FPS range to " + Arrays.toString(suitableFPSRange))
                    parameters.setPreviewFpsRange(suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                            suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX])
                }
            }
        }
    }

}
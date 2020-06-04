package com.github.dzhey.videochatsample.capture

import android.content.Context
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class CameraDeviceCapture(context: Context, val lifecycleOwner: LifecycleOwner, val cameraId: String) {
    private var status: Status = Status.STOPPED
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var handlerThread: HandlerThread? = null
    private var mediaRecorder: MediaRecorder? = null
    private var previewSession: CameraCaptureSession? = null
    private lateinit var config: CaptureConfig

    private val lifecycleObserver = object : LifecycleObserver {

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        fun handleAutoRelease() {
            release()

            if (status == Status.STARTED) {
                status = Status.PAUSED
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun handleAutoStart() {
            if (status == Status.PAUSED) {
                startCapture(config)
            }
        }
    }

    fun startCapture(config: CaptureConfig) {
        if (status == Status.STARTED) {
            return
        }

        this.config = config

        lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            try {
                doStartCapture()
            } catch (e: Exception) {
                release()
                Timber.w(e, "failed to start capture")
            }
        }
    }

    fun stopCapture() {
        release()
    }

    private suspend fun doStartCapture() {
        if (status == Status.STARTED) {
            return
        }

        status = Status.STARTED

        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        handlerThread = HandlerThread("CameraThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        cameraDevice = cameraManager.openCamera(cameraId, handler)
        val previewRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(config.surface)
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        }.build()

        if (status != Status.STARTED) {
            return
        }

        previewSession = cameraDevice!!.createCaptureSession(listOf(config.surface), handler).apply {
            if (status == Status.STARTED) {
                setRepeatingRequest(previewRequest, null, handler)
            }
        }
    }

    private fun release() {
        status = Status.STOPPED
        previewSession?.close()
        previewSession = null
        cameraDevice?.close()
        cameraDevice = null
        mediaRecorder?.release()
        mediaRecorder = null
        handlerThread?.quitSafely()
        handlerThread = null
    }

    private enum class Status {
        STARTED, STOPPED, PAUSED
    }

    data class CaptureConfig(val surface: Surface)
}
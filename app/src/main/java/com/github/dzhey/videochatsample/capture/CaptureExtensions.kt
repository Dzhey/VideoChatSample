package com.github.dzhey.videochatsample.capture

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.suspendCoroutine

suspend fun CameraManager.openCamera(
    cameraId: String, handler: Handler? = null
): CameraDevice? = suspendCancellableCoroutine {

    var isResumed = false

    val callback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (isResumed) {
                return
            }
            isResumed = true
            it.resume(camera) {
                Timber.d("camera opened, but open request cancelled")
                camera.close()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            if (isResumed) {
                return
            }
            isResumed = true
            it.resume(null) {
                Timber.d("camera disconnected")
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            if (isResumed) {
                return
            }
            isResumed = true
            val text = "camera error: $error"
            Timber.d(text)
            it.resumeWith(Result.failure(Throwable(text)))
        }
    }

    try {
        openCamera(cameraId, callback, handler)
    } catch (e: SecurityException) {
        Timber.w(e, "unable to open camera")
        it.resumeWith(Result.failure(e))
    }
}

suspend fun CameraDevice.createCaptureSession(
    targets: List<Surface>,
    handler: Handler? = null
): CameraCaptureSession = suspendCancellableCoroutine {
    createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            val exception = RuntimeException("camera session configuration failed")
            Timber.w(exception)
            it.resumeWith(Result.failure(exception))
        }

        override fun onConfigured(session: CameraCaptureSession) {
            Timber.d("camera session configuration succeed")
            it.resume(session) {
                Timber.w(it, "camera session configuration cancelled")
            }
        }
    }, handler)
}

suspend fun TextureView.requireSurfaceTexture(): SurfaceTexture = suspendCoroutine {
    val surface = surfaceTexture
    if (surface != null) {
        it.resumeWith(Result.success(surface))
        return@suspendCoroutine
    }

    val initialListener = surfaceTextureListener
    var isResumed = false

    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
            initialListener?.onSurfaceTextureSizeChanged(surface, width, height)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            initialListener?.onSurfaceTextureUpdated(surface)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            try {
                return initialListener?.onSurfaceTextureDestroyed(surface) ?: true
            } finally {
                if (!isResumed) {
                    it.resumeWith(Result.failure(RuntimeException("texture destroyed")))
                    isResumed = true
                }
            }
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            initialListener?.onSurfaceTextureAvailable(surface, width, height)

            if (surfaceTextureListener === this) {
                surfaceTextureListener = initialListener
            }

            if (isResumed) {
                return
            }

            it.resumeWith(Result.success(surface!!))
            isResumed = true
        }
    }
}
package com.github.dzhey.videochatsample.capture

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size

class CameraInfo(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun getFrontCameraId(): String {
        return cameraManager.cameraIdList.first {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    fun getPreviewSizes(cameraId: String): Array<Size> {
        val camera = cameraManager.getCameraCharacteristics(cameraId)
        val map = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        return map.getOutputSizes(SurfaceTexture::class.java)
    }
}
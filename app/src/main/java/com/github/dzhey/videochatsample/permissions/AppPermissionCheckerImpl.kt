package com.github.dzhey.videochatsample.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class AppPermissionCheckerImpl(context: Context) : AppPermissionChecker {

    private val context = context.applicationContext

    override fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
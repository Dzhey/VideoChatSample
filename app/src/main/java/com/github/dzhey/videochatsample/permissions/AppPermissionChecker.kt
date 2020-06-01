package com.github.dzhey.videochatsample.permissions

interface AppPermissionChecker {
    fun isCameraPermissionGranted(): Boolean
}
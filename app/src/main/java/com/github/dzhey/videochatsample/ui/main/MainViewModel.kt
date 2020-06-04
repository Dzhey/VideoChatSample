package com.github.dzhey.videochatsample.ui.main

import android.graphics.Point
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.github.dzhey.videochatsample.permissions.AppPermissionChecker

class MainViewModel(private val permissionChecker: AppPermissionChecker) : ViewModel() {

    val state: LiveData<MainViewContract.State>
        get() {
            if (!::_state.isInitialized) {
                initState()
            }
            return _state
        }

    private lateinit var _state: MutableLiveData<MainViewContract.State>

    fun onUpdatePermission() {
        if (!permissionChecker.isCameraPermissionGranted()) {
            _state.postValue(MainViewContract.State.CameraPermissionDeclined)
            return
        }

        _state.postValue(MainViewContract.State.Content())
    }

    fun onLocationSelected(position: Point) {
        val currentState = _state.value
        if (currentState is MainViewContract.State.Content) {
            _state.postValue(currentState.copy(avatarPosition = position))
        }
    }

    fun onCaptureStarted() {
        val currentState = _state.value
        if (currentState is MainViewContract.State.Content) {
            _state.postValue(currentState.copy(isShowingVideos = true))
        }
    }

    private fun initState() {
        if (permissionChecker.isCameraPermissionGranted()) {
            _state = MutableLiveData(MainViewContract.State.Content())
            return
        }

        _state = MutableLiveData(MainViewContract.State.CameraPermissionRequired)
    }
}

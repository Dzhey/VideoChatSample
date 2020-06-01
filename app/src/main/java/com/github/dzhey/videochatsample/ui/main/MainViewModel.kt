package com.github.dzhey.videochatsample.ui.main

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

        _state.postValue(MainViewContract.State.Content)
    }

    private fun initState() {
        if (permissionChecker.isCameraPermissionGranted()) {
            _state = MutableLiveData(MainViewContract.State.Content)
            return
        }

        _state = MutableLiveData(MainViewContract.State.CameraPermissionRequired)
    }
}

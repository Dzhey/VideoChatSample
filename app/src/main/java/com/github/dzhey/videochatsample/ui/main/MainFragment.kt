package com.github.dzhey.videochatsample.ui.main

import android.Manifest
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.github.dzhey.videochatsample.R
import com.github.dzhey.videochatsample.capture.CameraDeviceCapture
import com.github.dzhey.videochatsample.capture.CameraInfo
import com.github.dzhey.videochatsample.capture.PreviewSize
import com.github.dzhey.videochatsample.capture.requireSurfaceTexture
import com.github.dzhey.videochatsample.decoder.VideoPlayer
import com.github.dzhey.videochatsample.ui.App
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    private lateinit var component: MainViewContract.Component

    private val viewModel: MainViewModel by viewModels(
        factoryProducer = { component.viewModelFactory() })
    private var capture: CameraDeviceCapture? = null
    private var captureJob: Job? = null
    private var surface: Surface? = null
    private lateinit var cameraInfo: CameraInfo

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        component = DaggerMainViewContract_Component.builder()
            .applicationComponent(App.component)
            .build()

        cameraInfo = CameraInfo(requireContext())

        viewModel.state.observe(viewLifecycleOwner, Observer { render(it) })
    }

    private fun render(state: MainViewContract.State) {
        if (state is MainViewContract.State.CameraPermissionRequired) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST)
            permissionNotice.visibility = View.VISIBLE
            previewView.visibility = View.GONE
            return
        }

        if (state is MainViewContract.State.CameraPermissionDeclined) {
            permissionNotice.visibility = View.VISIBLE
            previewView.visibility = View.GONE
            return
        }

        if (state is MainViewContract.State.Content) {
            permissionNotice.visibility = View.GONE
            previewView.visibility = View.VISIBLE
            capturePreview()
            return
        }
    }

    private fun capturePreview() {
        if (captureJob?.isActive == true) {
            return
        }

        captureJob = lifecycleScope.launch(Dispatchers.Main) {
            val surfaceTexture = previewView.requireSurfaceTexture()
            if (surface == null) {
                surface = Surface(surfaceTexture)
            }

            val targetCameraId = cameraInfo.getFrontCameraId()
            if (capture == null) {
                capture = CameraDeviceCapture(requireContext(), this@MainFragment, targetCameraId)
            }

            val supportedSizes = cameraInfo.getPreviewSizes(targetCameraId)
            val surfaceTextureSize = previewView.surfaceTextureSize
            val chosenSize = PreviewSize.getOptimalSize(supportedSizes,
                surfaceTextureSize.width,
                surfaceTextureSize.height,
                surfaceTextureSize)!!
            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                previewView.setAspectRatio(chosenSize.width, chosenSize.height)
            } else {
                previewView.setAspectRatio(chosenSize.height, chosenSize.width)
            }

            previewView.applyTransform(previewView.surfaceTextureSize.width, previewView.surfaceTextureSize.height)
            surfaceTexture.setDefaultBufferSize(chosenSize.width, chosenSize.height)

            capture!!.startCapture(CameraDeviceCapture.CaptureConfig(surface!!))
        }

        val videos = listOf("video1.mp4", "video2.mp4")
        lifecycleScope.launch(Dispatchers.IO) {
            val player = VideoPlayer(requireContext(), lifecycle)
            VideoAvatarProducer(requireContext())
                .createViews(avatarContainer as ViewGroup,
                    VIDEOS_NUM,
                    avatarContainer.children.toList(),
                    PRODUCER_THROTTLE_MS)
                .collectIndexed { index, value ->
                    val video = videos[index % videos.size]
                    player.loopVideo(video, value.surfaceTexture)
                }
        }
    }

    override fun onStart() {
        super.onStart()

        viewModel.state.value?.let { render(it) }
    }

    override fun onStop() {
        super.onStop()

        captureJob?.cancel()
        capture?.stopCapture()
        surface?.release()
        surface = null
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST) {
            return
        }

        viewModel.onUpdatePermission()
    }

    companion object {
        private const val PERMISSION_REQUEST = 123
        private const val VIDEOS_NUM = 9
        private const val PRODUCER_THROTTLE_MS = 50L
    }
}

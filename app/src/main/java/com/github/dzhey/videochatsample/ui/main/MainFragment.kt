package com.github.dzhey.videochatsample.ui.main

import android.Manifest
import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import android.view.*
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.github.dzhey.videochatsample.R
import com.github.dzhey.videochatsample.capture.CameraDeviceCapture
import com.github.dzhey.videochatsample.capture.CameraInfo
import com.github.dzhey.videochatsample.capture.PreviewSize
import com.github.dzhey.videochatsample.capture.requireSurfaceTexture
import com.github.dzhey.videochatsample.decoder.VideoPlayer
import com.github.dzhey.videochatsample.ui.App
import com.github.dzhey.videochatsample.ui.views.getRelativePosition
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.android.synthetic.main.main_fragment.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    private lateinit var component: MainViewContract.Component

    private val viewModel: MainViewModel by viewModels(
        factoryProducer = { component.viewModelFactory() })
    private val viewState = ViewState()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.main_fragment, container, false).apply {
            VideoAvatarProducer(requireContext()).getRandomViewPosition(userAvatar, userAvatarContainer) {
                userAvatar.translationX = it.x.toFloat()
                userAvatar.translationY = it.y.toFloat()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        component = DaggerMainViewContract_Component.builder()
            .applicationComponent(App.component)
            .build()

        viewState.cameraInfo = CameraInfo(requireContext())

        viewModel.state.observe(viewLifecycleOwner, Observer { render(it) })
        permissionNotice.setOnClickListener { viewModel.onGrantPermissionRequested() }

        setupAvatarMotion()
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
            showVideos(state)
            handleAvatarPositionSelection(state)
            return
        }
    }

    private fun handleAvatarPositionSelection(state: MainViewContract.State.Content) {
        state.avatarPosition?.let { point ->
            requireView().userAvatar.doOnLayout {
                val targetX = maxOf(0, point.x - it.width / 2)
                val targetY = maxOf(0, point.y - it.height / 2)
                animateToPoint(Point(targetX, targetY))
            }
        }
    }

    private fun animateToPoint(point: Point) {
        SpringAnimation(requireView().userAvatar, DynamicAnimation.TRANSLATION_X, point.x.toFloat()).apply {
            spring.stiffness = SpringForce.STIFFNESS_VERY_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }

        SpringAnimation(requireView().userAvatar, DynamicAnimation.TRANSLATION_Y, point.y.toFloat()).apply {
            spring.stiffness = SpringForce.STIFFNESS_VERY_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
    }

    private fun showVideos(state: MainViewContract.State.Content) {
        if (viewState.isShowingVideos == state.isShowingVideos) {
            return
        }

        if (!state.isShowingVideos) {
            avatarContainer.removeAllViews()
            return
        }

        viewState.isShowingVideos = true

        val videos = listOf("video1.mp4", "video2.mp4")
        val player = VideoPlayer(requireContext(), lifecycle)

        lifecycleScope.launch(Dispatchers.IO) {
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

    private fun setupAvatarMotion() {
        var touchLocation: Point? = null
        requireView().setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                touchLocation = requireView().userAvatarContainer.getRelativePosition(event.rawX, event.rawY)
                return@setOnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_UP) {
                touchLocation?.let { viewModel.onLocationSelected(it) }
                touchLocation = null
                return@setOnTouchListener true
            }

            false
        }
    }

    private fun capturePreview() {
        if (viewState.captureJob?.isActive == true || viewState.isCaptureStarted) {
            return
        }

        viewState.isCaptureStarted = true

        viewState.captureJob = lifecycleScope.launch(Dispatchers.Main) {
            val surfaceTexture = previewView.requireSurfaceTexture()
            if (viewState.surface == null) {
                viewState.surface = Surface(surfaceTexture)
            }

            val targetCameraId = viewState.cameraInfo!!.getFrontCameraId()
            if (viewState.capture == null) {
                viewState.capture = CameraDeviceCapture(requireContext(), this@MainFragment, targetCameraId)
            }

            val supportedSizes = viewState.cameraInfo!!.getPreviewSizes(targetCameraId)
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

            previewView.applyTransform(previewView.width, previewView.height)
            surfaceTexture.setDefaultBufferSize(chosenSize.width, chosenSize.height)

            viewState.capture!!.startCapture(CameraDeviceCapture.CaptureConfig(viewState.surface!!))

            lifecycle.whenStarted {
                viewModel.onCaptureStarted()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        viewModel.state.value?.let { render(it) }
    }

    override fun onStop() {
        super.onStop()

        viewState.release()
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

    private class ViewState(
        var capture: CameraDeviceCapture? = null,
        var captureJob: Job? = null,
        var isCaptureStarted: Boolean = false,
        var surface: Surface? = null,
        var cameraInfo: CameraInfo? = null,
        var isShowingVideos: Boolean = false
    ) {
        fun release() {
            captureJob?.cancel()
            capture?.stopCapture()
            surface?.release()
            surface = null
            isCaptureStarted = false
            isShowingVideos = false
        }
    }
}

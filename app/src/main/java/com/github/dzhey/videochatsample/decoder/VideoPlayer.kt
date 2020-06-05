package com.github.dzhey.videochatsample.decoder

import android.graphics.SurfaceTexture
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

interface VideoPlayer {

    val statusChannel: ReceiveChannel<Status>

    val currentStatus: Status

    /**
     * Stream video to given texture. Loops video until activity/fragment is stopped.
     */
    fun loopVideo(videoName: String, surfaceTexture: SurfaceTexture)

    fun bindLifecycle(lifecycle: Lifecycle)

    enum class Status {
        STARTED, STOPPED
    }
}

suspend fun VideoPlayer.doWhenStopped(
    coroutineScope: CoroutineScope, block: () -> Unit
) = suspendCancellableCoroutine<VideoPlayer.Status> { cont ->
    coroutineScope.launch(Dispatchers.Main.immediate) {
        if (currentStatus == VideoPlayer.Status.STOPPED) {
            block()
            cont.resume(currentStatus) { e ->
                Timber.w(e, "cancelled")
            }
            return@launch
        }

        statusChannel.receiveAsFlow().collect {
            if (it == VideoPlayer.Status.STOPPED) {
                block()
                cont.resume(it) { e ->
                    Timber.w(e, "cancelled")
                }
            }
        }
    }
}
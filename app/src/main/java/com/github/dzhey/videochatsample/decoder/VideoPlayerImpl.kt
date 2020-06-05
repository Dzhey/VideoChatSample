package com.github.dzhey.videochatsample.decoder

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Stream specified video to given [SurfaceTexture].
 * Player automatically release resources on activity/fragment stop which is determined from lifecycle.
 */
class VideoPlayerImpl(context: Context) : VideoPlayer {

    override val statusChannel: ReceiveChannel<VideoPlayer.Status>
        get() = _statusChannel

    override val currentStatus: VideoPlayer.Status
        get() = _status

    private val context = context.applicationContext
    private val decodeSessions = hashMapOf<String, DecodeSession>()
    private val surfaces = hashMapOf<String, Surface>()
    private var _statusChannel = Channel<VideoPlayer.Status>(Channel.CONFLATED)
    private var isFinishing: Boolean = false
    private var isObserverRegistered = false
    private var _lifecycle: Lifecycle? = null
    private var _status: VideoPlayer.Status = VideoPlayer.Status.STOPPED

    private val lifecycleObserver = object : LifecycleObserver {

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        fun doOnStart() {
            Timber.d("doOnStart")
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun doOnStop() {
            val observer = this
            GlobalScope.launch(Dispatchers.Main.immediate) {
                _lifecycle!!.removeObserver(observer)
                isObserverRegistered = false

                if (isFinishing) {
                    return@launch
                }

                isFinishing = true

                decodeSessions.forEach {
                    it.value.stopSafely()
                    surfaces[it.key]!!.release()
                }
                decodeSessions.clear()
                surfaces.clear()
                isFinishing = false
                setPlayerStatus(VideoPlayer.Status.STOPPED)
            }
        }
    }

    override fun bindLifecycle(lifecycle: Lifecycle) {
        require(_lifecycle == null || lifecycle === _lifecycle) {
            "already bound different lifecycle"
        }

        _lifecycle = lifecycle
    }

    override fun loopVideo(videoName: String, surfaceTexture: SurfaceTexture) {
        requireNotNull(_lifecycle) { "should bind lifecycle first" }

        if (isFinishing) {
            Timber.d("won't start because already finishing")
            return
        }

        if (!isObserverRegistered) {
            _lifecycle!!.addObserver(lifecycleObserver)
            isObserverRegistered = true
        }

        val sessionId = createSessionId(videoName)
        val surface = Surface(surfaceTexture)

        setPlayerStatus(VideoPlayer.Status.STARTED)

        DecodeSession(context, _lifecycle!!, videoName, surface).apply {
            decodeSessions[sessionId] = this
            surfaces[sessionId] = surface
            Timber.d("added session playback session '%s'", sessionId)
            observeStatus(this, sessionId)
            start()
        }
    }

    private fun observeStatus(session: DecodeSession, sessionId: String) = _lifecycle!!.coroutineScope.launch {
        for (status in session.statusChannel) {
            when (status) {
                SessionStatus.STARTED -> {
                    Timber.d("playback '%s' started", sessionId)
                }
                SessionStatus.FINISHED -> {
                    Timber.d("play video '%s' after finish", sessionId)
                    if (_lifecycle!!.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        session.restart()
                    }
                }
                SessionStatus.STOPPED -> {
                    Timber.d("playback '%s' stopped", sessionId)
                }
                SessionStatus.ERROR -> {
                    Timber.w("playback '%s' error", sessionId)
                }
                else -> {
                    Timber.d("session status changed to %s", status)
                }
            }
        }
    }

    private fun setPlayerStatus(status: VideoPlayer.Status) = _lifecycle!!.coroutineScope.launch {
        _status = status
        _statusChannel.send(status)
    }

    private fun createSessionId(videoName: String) = "$videoName-${decodeSessions.size}"
}
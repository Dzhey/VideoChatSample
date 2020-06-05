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
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Stream specified video to given [SurfaceTexture].
 * Player automatically release resources on activity/fragment stop which is determined from lifecycle.
 */
class VideoPlayer(context: Context, private val lifecycle: Lifecycle) {

    private val context = context.applicationContext
    private val decodeSessions = hashMapOf<String, DecodeSession>()
    private val surfaces = hashMapOf<String, Surface>()
    private var isFinishing: Boolean = false

    private val lifecycleObserver = object : LifecycleObserver {

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun doOnStop() = GlobalScope.launch(Dispatchers.Main) {
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
        }
    }

    init {
        lifecycle.addObserver(lifecycleObserver)
    }

    /**
     * Stream video to given texture. Loops video until activity/fragment is stopped.
     * Video won't resume automatically after stop.
     */
    fun loopVideo(videoName: String, surfaceTexture: SurfaceTexture) {
        if (isFinishing) {
            return
        }

        val sessionId = createSessionId(videoName)
        val surface = Surface(surfaceTexture)

        DecodeSession(context, lifecycle, videoName, surface).apply {
            decodeSessions[sessionId] = this
            surfaces[sessionId] = surface
            Timber.d("added session playback session '%s'", sessionId)
            observeStatus(this, sessionId)
            start()
        }
    }

    private fun observeStatus(session: DecodeSession, sessionId: String) = lifecycle.coroutineScope.launch {
        for (status in session.statusChannel) {
            when (status) {
                SessionStatus.STARTED -> {
                    Timber.d("playback '%s' started", sessionId)
                }
                SessionStatus.FINISHED -> {
                    Timber.d("play video '%s' after finish", sessionId)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
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

    private fun createSessionId(videoName: String) = "$videoName-${decodeSessions.size}"
}
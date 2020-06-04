package com.github.dzhey.videochatsample.decoder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Decode specified video file from android assets and render to [Surface] with [start()][start].
 * Stop and release all resources with [stop()][stop].
 * It's session holder responsibility to control surface lifecycle and release it
 * when needed as well as session object.
 * Initial status of decode session is [SessionStatus.STOPPED].
 * @param context android context
 * @param lifecycle component lifecycle to stick to
 * @param videoName video name to play from assets
 * @param surface render surface
 * @see [SessionStatus]
 * @see start
 * @see restart
 * @see stop
 */
class DecodeSession constructor(
    context: Context,
    private val lifecycle: Lifecycle,
    private val videoName: String,
    private val surface: Surface
) : SessionStatusSupplier {

    /**
     * Receive status updates from channel
     * @see [SessionStatus]
     */
    val statusChannel: ReceiveChannel<SessionStatus>
        get() = _statusChannel

    override val currentStatus: SessionStatus
        get() = status

    private val _statusChannel = Channel<SessionStatus>(Channel.CONFLATED)

    private val context = context.applicationContext

    @Volatile
    private var status = SessionStatus.STOPPED
    private lateinit var decoder: MediaCodec
    private lateinit var extractor: MediaExtractor
    private lateinit var decodeThread: HandlerThread
    private lateinit var decoderInfo: SessionDecoderInfo

    private val statusObserver = object : SessionStatusObserver {
        override fun onNext(status: SessionStatus) {
            setStatus(status)
        }
    }

    private val decoderCallback = MediaCodecCallback(
        statusObserver = statusObserver, statusSupplier = this)

    /**
     * Start decode session. DecodeSession [status] must be [SessionStatus.STOPPED].
     * Puts current status to [SessionStatus.STARTED] upon successful start or [SessionStatus.ERROR].
     */
    fun start() = lifecycle.coroutineScope.launch(Dispatchers.Main) {
        require(videoName.isNotEmpty())

        checkStatusOneOf(SessionStatus.STOPPED)

        try {
            prepareAndStart()
        } catch (e: Exception) {
            Timber.w(e, "unable to start decoder session")
            setStatus(SessionStatus.ERROR)
        }
    }

    /**
     * Restart decode session from beginning. DecodeSession status must
     * be [SessionStatus.FINISHED] or [SessionStatus.RESTARTING].
     * Puts current status to [SessionStatus.RESTARTING] immediately. Once operation finished, status become
     * either [SessionStatus.STARTED] upon successful restart or [SessionStatus.ERROR].
     * @see currentStatus
     */
    fun restart() = lifecycle.coroutineScope.launch(Dispatchers.Main) {
        if (currentStatus == SessionStatus.RESTARTING) {
            return@launch
        }

        checkStatusOneOf(SessionStatus.FINISHED)
        setStatus(SessionStatus.RESTARTING)

        try {
            prepareAndRestart()
        } catch (e: Exception) {
            Timber.w(e, "unable to start decoder session")
            setStatus(SessionStatus.ERROR)
        }
    }

    /**
     * Stop decode, releasing any held resources.
     * Puts current status to [SessionStatus.STOPPING] immediately.
     * Idempotent and safe to call with any current status except [SessionStatus.RESTARTING].
     * Since method is async user must wait until status changes to [SessionStatus.STOPPED]
     * prior to any other commands.
     * Does nothing if session is already stopped.
     */
    fun stop() = GlobalScope.launch(Dispatchers.Main) {
        stopImpl()
    }

    private suspend fun stopImpl(): Boolean = suspendCoroutine { cont ->
        GlobalScope.launch(Dispatchers.Main) {
            Timber.d("stop decode session")

            if (currentStatus.isStoppingOrStopped) {
                cont.resume(true)
                return@launch
            }

            checkStatusOneOf(SessionStatus.STARTED, SessionStatus.FINISHED, SessionStatus.ERROR)

            setStatus(SessionStatus.STOPPING)

            release()
            cont.resume(true)
        }
    }

    /**
     * Suspending stop allowing to perform [stop()][stop] from any state,
     * waiting if necessary until stop may be performed.
     * @see stop
     */
    @FlowPreview
    suspend fun stopSafely(): SessionStatus = suspendCoroutine { cont ->
        GlobalScope.launch(Dispatchers.Main) {
            val current = status

            if (current.isStoppingOrStopped) {
                cont.resume(current)
                return@launch
            }

            if (!current.isTransitiveState) {
                release()
                cont.resume(current)
                return@launch
            }

            _statusChannel.receiveAsFlow()
                .filter { !it.isTransitiveState }
                .take(1)
                .onEach { stopImpl() }
                .collect {
                    cont.resume(it)
                }
        }
    }

    private suspend fun release(): Boolean = suspendCoroutine {
        Timber.d("release decode session requested")

        check(::decodeThread.isInitialized)

        val releaseRunnable = Runnable {
            releaseBlocking()
            it.resume(true)
        }

        // Use the same thread as media decoder's callbacks to ensure
        // we prevent any race conditions with buffers and rendering
        try {
            Handler(decodeThread.looper).post(releaseRunnable)
        } catch (e: Exception) {
            Timber.e(e, "is Alive: %s", decodeThread.isAlive)
            throw e
        }
    }

    private fun releaseBlocking() {
        Timber.d("performing decode session release..")

        decoderCallback.release()

        if (::decoder.isInitialized) {
            if (status == SessionStatus.STARTED) {
                try {
                    decoder.stop()
                } catch (e: IllegalStateException) {
                    Timber.w(e, "status: %s", status)
                    throw e
                }
            }
            decoder.release()
        }
        if (::extractor.isInitialized) {
            extractor.release()
        }

        decodeThread.quitSafely()
        Timber.d("decode session released")
    }

    private fun prepareAndStart() {
        Timber.d("prepare and start requested")

        extractor = MediaExtractorFactory.createPreparedMediaExtractorOrThrow(
            context.assets.openFd(videoName))

        val trackId = extractor.getVideoTrackId()
        Timber.d("prepare session for track id: %d", trackId)
        val trackFormat = extractor.getTrackFormat(trackId)

        decoderInfo = SessionDecoderInfo.createSessionDecoderInfo(trackFormat)
        requireCodecAvailable(decoderInfo)

        decodeThread = HandlerThread("DecodeSessionThread").apply { start() }

        decoderCallback.reset(extractor)

        decoder = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder.setCallback(decoderCallback, Handler(decodeThread.looper))
        decoder.configure(trackFormat, surface, null, 0)
        decoder.start()

        Timber.d("started decoder")

        setStatus(SessionStatus.STARTED)
    }

    private fun prepareAndRestart() {
        Timber.d("prepare and restart requested")

        check(::decodeThread.isInitialized)

        val restartRunnable = Runnable {
            checkStatusOneOf(SessionStatus.RESTARTING)
            decoderCallback.release()

            var isSeekApplied = false
            if (decoderInfo.isAdaptivePlaybackSupported) {
                try {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    isSeekApplied = true
                } catch (e: Exception) {
                    Timber.w(e, "seek failed")
                }
            }

            if (!isSeekApplied) {
                extractor.release()
                extractor = MediaExtractorFactory.createPreparedMediaExtractorOrThrow(
                    context.assets.openFd(videoName))
            }

            decoder.flush()
            Timber.d("flushed decoder")

            decoder.start()

            decoderCallback.reset(extractor)
            setStatus(SessionStatus.STARTED)
            Timber.d("re-started decoder")
        }

        Handler(decodeThread.looper).post(restartRunnable)
    }

    private fun requireCodecAvailable(decoderInfo: SessionDecoderInfo) {
        if (decoderInfo.decoderName.isNotEmpty()) {
            return
        }

        Timber.w("codec is not available for format %s", decoderInfo.format.getString(MediaFormat.KEY_MIME))
        releaseBlocking()
        setStatus(SessionStatus.ERROR)
        return
    }

    private fun checkStatusOneOf(vararg status: SessionStatus) {
        check(status.any { it == this.status}) {
            "status must be one of: [${status.joinToString(", ")}]; current is $currentStatus"
        }
    }

    private fun setStatus(status: SessionStatus) {
        this.status = status

        lifecycle.coroutineScope.launch(Dispatchers.Main) {
            _statusChannel.send(status)
        }
    }
}
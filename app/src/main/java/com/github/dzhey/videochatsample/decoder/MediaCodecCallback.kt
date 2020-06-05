package com.github.dzhey.videochatsample.decoder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import timber.log.Timber

internal class MediaCodecCallback(
    private val statusObserver: SessionStatusObserver,
    private val statusSupplier: SessionStatusSupplier
) : MediaCodec.Callback() {

    private val currentStatus: SessionStatus
        get() = statusSupplier.currentStatus

    @Volatile
    private var bufferRender: BufferRender = BufferRender()
    @Volatile
    lateinit var extractor: MediaExtractor
    @Volatile
    private var isReleased = false
    @Volatile
    private var isEndOfStream = false
    @Volatile
    private var isInputBufferAvailable = false

    fun release() {
        isReleased = true
    }

    fun reset(extractor: MediaExtractor) {
        isReleased = false
        isEndOfStream = false
        isInputBufferAvailable = false
        bufferRender.release()
        bufferRender = BufferRender()
        this.extractor = extractor
    }

    override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
        if (isEndOfStream || isReleased || !isInputBufferAvailable || currentStatus != SessionStatus.STARTED) {
            return
        }

        bufferRender.renderOutput(codec, index, info.presentationTimeUs)
    }

    override fun onInputBufferAvailable(decoder: MediaCodec, index: Int) {
        if (isEndOfStream || isReleased || !ALLOWED_RENDER_STATUSES.contains(currentStatus)) {
            return
        }

        isInputBufferAvailable = true
        val buffer = decoder.getInputBuffer(index)!!
        val sampleSize = extractor.readSampleData(buffer, 0)

        if (sampleSize < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            isEndOfStream = true
            statusObserver.onNext(SessionStatus.FINISHED)
        } else {
            decoder.queueInputBuffer(index, 0, sampleSize, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = Unit

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        if (isReleased || currentStatus.isStoppingOrStopped) {
            return
        }
        Timber.w("decoding error; code: %s, recoverable: %s, transient: %s",
            e.errorCode, e.isRecoverable, e.isTransient)
        statusObserver.onNext(SessionStatus.ERROR)
    }

    companion object {
        private val ALLOWED_RENDER_STATUSES = listOf(
            SessionStatus.STARTED,
            SessionStatus.FINISHED)
    }
}
package com.github.dzhey.videochatsample.decoder

import android.media.MediaCodec
import android.os.SystemClock
import timber.log.Timber

/**
 * [MediaCodec]'s callback delegate dedicated to render specified buffer at particular correct time
 */
class BufferRender {

    @Volatile
    private var isReleased = false
    private val waitObj = Object()
    private var startTimeMillis = 0L

    fun release() {
        isReleased = true
    }

    fun renderOutput(codec: MediaCodec, bufferIndex: Int, presentationTimeUs: Long) {
        if (isReleased) {
            return
        }

        if (startTimeMillis == 0L) {
            startTimeMillis = SystemClock.elapsedRealtime()
        }

        waitForRender(presentationTimeUs / 1000)

        if (isReleased) {
            Timber.v("won't render, buffer released")
            return
        }

        try {
            codec.releaseOutputBuffer(bufferIndex, true)
        } catch (e: Exception) {
            Timber.w(e, "buffer render error; buffer released")
            throw e
        }
    }

    private fun waitForRender(presentationTimeMillis: Long) {
        while (true) {
            val targetMillis = startTimeMillis + presentationTimeMillis
            val sleepMs = targetMillis - SystemClock.elapsedRealtime()

            if (sleepMs > 0) {
                synchronized(waitObj) {
                    waitObj.wait(sleepMs)
                }
            }

            // prevent from spurious wake up
            if (SystemClock.elapsedRealtime() < targetMillis) {
                continue
            }
            break
        }
    }
}
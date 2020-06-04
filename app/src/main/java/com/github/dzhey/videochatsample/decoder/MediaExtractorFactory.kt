package com.github.dzhey.videochatsample.decoder

import android.content.res.AssetFileDescriptor
import android.media.MediaExtractor

object MediaExtractorFactory {

    fun createPreparedMediaExtractorOrThrow(fd: AssetFileDescriptor): MediaExtractor {
        val extractor = MediaExtractor()

        fd.use {
            extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
        }

        val videoTrackId = extractor.findVideoTrackId()
        if (videoTrackId < 0) {
            extractor.release()
            throw DecoderException("unable to find suitable track in media")
        }

        return extractor.apply { selectTrack(videoTrackId) }
    }
}
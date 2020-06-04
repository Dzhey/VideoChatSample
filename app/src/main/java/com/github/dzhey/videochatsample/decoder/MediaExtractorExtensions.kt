package com.github.dzhey.videochatsample.decoder

import android.media.MediaExtractor
import android.media.MediaFormat

/**
 * @return found video track or -1
 */
fun MediaExtractor.findVideoTrackId(): Int {
    return (0..trackCount).indexOfFirst {
        getTrackFormat(it).getString(MediaFormat.KEY_MIME)
            .orEmpty()
            .startsWith("video/")
    }
}

/**
 * @return found video track id
 * @throws [IllegalArgumentException] if track could not be found
 */
fun MediaExtractor.getVideoTrackId(): Int {
    return findVideoTrackId().also {
        require(it > -1)
    }
}
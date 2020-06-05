package com.github.dzhey.videochatsample.decoder

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat

internal data class SessionDecoderInfo constructor(
    val format: MediaFormat,
    val decoderName: String,
    val isAdaptivePlaybackSupported: Boolean) {

    companion object {
        fun createSessionDecoderInfo(format: MediaFormat): SessionDecoderInfo {
            val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val decoderName = mediaCodecList.findDecoderForFormat(format)

            if (decoderName.isEmpty()) {
                return SessionDecoderInfo(format, "", false)
            }

            val mime = format.getString(MediaFormat.KEY_MIME)
            val decoderInfo = mediaCodecList.codecInfos.first { it.name == decoderName }
            val isAdaptivePlaybackSupported = decoderInfo.getCapabilitiesForType(mime).isFeatureSupported(
                MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback)

            return SessionDecoderInfo(format, decoderName, isAdaptivePlaybackSupported)
        }
    }
}
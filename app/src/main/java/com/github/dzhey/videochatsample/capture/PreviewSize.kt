package com.github.dzhey.videochatsample.capture

import android.util.Size
import java.util.*

object PreviewSize {
    fun getOptimalSize(
        variants: Array<Size>,
        width: Int,
        height: Int,
        aspectRatio: Size
    ): Size? {
        if (variants.isEmpty()) {
            return null
        }

        val w = aspectRatio.width
        val h = aspectRatio.height
        val accepted = variants.filter {
            it.height == it.width * h / w && it.width >= width && it.height >= height
        }

        // Pick the smallest of those, assuming we found any
        return if (accepted.isNotEmpty()) {
             Collections.min(accepted, CompareSizesByArea())
        } else {
            variants.first()
        }
    }

    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height -
                        rhs.width.toLong() * rhs.height
            )
        }
    }
}
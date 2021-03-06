package com.github.dzhey.videochatsample.ui.views

import android.graphics.Point
import android.view.View
import androidx.core.view.doOnLayout
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


suspend fun View.getSize(): Point = suspendCoroutine { cont ->
    if (width > 0 || height > 0) {
        cont.resume(Point(width, height))
        return@suspendCoroutine
    }

    doOnLayout {
        cont.resume(Point(width, height))
    }
}

fun View.getRelativePosition(x: Float, y: Float): Point {
    val location = IntArray(2)
    getLocationOnScreen(location)
    val viewX = x - location[0]
    val viewY = y - location[1]
    return Point(viewX.toInt(), viewY.toInt())
}
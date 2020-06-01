package com.github.dzhey.videochatsample.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.widget.FrameLayout

class CircleFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val centerX = w / 2f
        val centerY = h / 2f
        path.reset()
        path.addCircle(centerX, centerY, minOf(centerX, centerY), Path.Direction.CW)
        path.close()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(path)

        super.dispatchDraw(canvas)

        canvas.restoreToCount(save)
    }
}

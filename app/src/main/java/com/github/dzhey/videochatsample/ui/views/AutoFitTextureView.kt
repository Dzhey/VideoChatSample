package com.github.dzhey.videochatsample.ui.views

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager

class AutoFitTextureView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextureView(context, attrs, defStyle) {

    val surfaceTextureSize: Size
        get() = Size(surfaceTextureWidth, surfaceTextureHeight)

    private var ratioWidth = 0
    private var ratioHeight = 0
    private var surfaceTextureWidth = 0
    private var surfaceTextureHeight = 0

    init {
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
                surfaceTextureWidth = width
                surfaceTextureHeight = height
                applyTransform(width, height)
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = true

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
                surfaceTextureWidth = width
                surfaceTextureHeight = height
            }
        }
    }

    fun setAspectRatio(width: Int, height: Int) {
        require(width >= 0 && height >= 0)
        ratioWidth = width
        ratioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        setMeasuredDimension(width, height)
        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height)
        } else {
            if (width < height * ratioWidth / ratioHeight) {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth)
            } else {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height)
            }
        }
    }

    fun applyTransform(viewWidth: Int, viewHeight: Int) {
        if (surfaceTextureWidth == 0 || surfaceTextureHeight == 0) {
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, surfaceTextureHeight.toFloat(), surfaceTextureWidth.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale: Float = Math.max(
                viewHeight.toFloat() / surfaceTextureHeight,
                viewWidth.toFloat() / surfaceTextureWidth
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        }
        setTransform(matrix)
    }
}
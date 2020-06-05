package com.github.dzhey.videochatsample.ui.main

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import com.github.dzhey.videochatsample.R
import com.github.dzhey.videochatsample.capture.requireSurfaceTexture
import com.github.dzhey.videochatsample.ui.views.getSize
import kotlinx.android.synthetic.main.avatar_view.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

class VideoAvatarProducer(context: Context) {

    private val inflater = LayoutInflater.from(context)
    private val viewSize = context.resources.getDimensionPixelSize(R.dimen.avatar_size)

    @FlowPreview
    fun createViews(parent: ViewGroup,
                    reuseViews: List<View> = listOf(),
                    numOfViews: Int = MAX_VIEWS): Flow<AvatarData> {
        require(numOfViews > 0)

        val viewCache = reuseViews.toMutableList()

        return flow { emit(parent.getSize()) }
            .flatMapConcat { size ->
                flow {
                    size to (0 until numOfViews).forEach { emit(size) }
                }.map { it to createOrReuseView(parent, viewCache) }
            }
            .onEach {
                val (size, view) = it
                setViewPosition(view, selectViewPosition(size))
            }
            .map { it.second }
            .flowOn(Dispatchers.Main)
            .map { AvatarData(it, it.textureView.requireSurfaceTexture()) }
            .flowOn(Dispatchers.IO)
    }

    fun getRandomViewPosition(viewParent: ViewGroup, onPositionReady: (Point) -> Unit) {
        viewParent.doOnLayout {
            onPositionReady(selectViewPosition(Point(it.width, it.height)))
        }
    }

    private fun setViewPosition(view: View, position: Point) {
        view.layoutParams.apply {
            this as ViewGroup.MarginLayoutParams
            leftMargin = position.x
            topMargin = position.y
            view.layoutParams = this
        }
    }

    private fun selectViewPosition(parentSize: Point): Point {
        val x = Math.random() * (parentSize.x - viewSize)
        val y = Math.random() * (parentSize.y - viewSize)

        return Point(x.toInt(), y.toInt())
    }

    private fun createOrReuseView(parent: ViewGroup, viewCache: MutableList<View>): View {
        if (viewCache.isNotEmpty()) {
            return viewCache.removeAt(0).also {
                it.textureView.isAutoReleaseEnabled = false
            }
        }

        return inflater.inflate(R.layout.avatar_view, parent, false).also {
            it.textureView.isAutoReleaseEnabled = false
            parent.addView(it)
        }
    }

    companion object {
        private const val MAX_VIEWS = 9
    }

    data class AvatarData(
        val view: View,
        val surfaceTexture: SurfaceTexture
    )
}
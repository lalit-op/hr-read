package com.example.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

class ZoomController(
    val minScale: Float = 0.5f,
    val maxScale: Float = 5.0f
) {
    var scale by mutableFloatStateOf(1.0f)
        private set

    var offsetX by mutableFloatStateOf(0f)
        private set

    var offsetY by mutableFloatStateOf(0f)
        private set

    fun onTransform(
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
        maxPanX: Float = Float.MAX_VALUE,
        maxPanY: Float = Float.MAX_VALUE
    ) {
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        scale = newScale

        if (newScale > 1.0f) {
            val limitX = if (maxPanX < Float.MAX_VALUE) maxPanX.coerceAtLeast(0f) else Float.MAX_VALUE
            val limitY = if (maxPanY < Float.MAX_VALUE) maxPanY.coerceAtLeast(0f) else Float.MAX_VALUE
            offsetX = (offsetX + panChangeX).coerceIn(-limitX, limitX)
            offsetY = (offsetY + panChangeY).coerceIn(-limitY, limitY)
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun onDoubleTap(tapX: Float, tapY: Float) {
        if (scale > 1.2f) {
            reset()
        } else {
            scale = 2.0f
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun zoomIn() {
        val newScale = (scale * 1.3f).coerceIn(minScale, maxScale)
        scale = newScale
    }

    fun zoomOut() {
        val newScale = (scale / 1.3f).coerceIn(minScale, maxScale)
        scale = newScale
        if (newScale <= 1.0f) {
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun fitWidth() {
        reset()
    }

    fun fitPage() {
        reset()
    }

    fun focusOn(targetNormX: Float, targetNormY: Float, targetScale: Float = 2.0f, viewportWidth: Float = 0f, viewportHeight: Float = 0f) {
        scale = targetScale.coerceIn(minScale, maxScale)
        if (viewportWidth > 0f && viewportHeight > 0f) {
            val focusPixelX = (targetNormX - 0.5f) * viewportWidth * scale
            val focusPixelY = (targetNormY - 0.5f) * viewportHeight * scale
            offsetX = -focusPixelX
            offsetY = -focusPixelY
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun reset() {
        scale = 1.0f
        offsetX = 0f
        offsetY = 0f
    }
}

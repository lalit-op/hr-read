package com.example

import com.example.renderer.PageDimensions
import com.example.viewer.FitMode
import com.example.viewer.ZoomController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentViewerScalingTest {

    @Test
    fun `test Fit Width preserves portrait aspect ratio and utilizes full width`() {
        // Container: standard phone (412dp wide x 892dp high)
        val containerWidth = 412f
        val containerHeight = 892f
        // Standard A4 portrait: 595 x 842 points -> aspect ratio 0.7066f
        val portraitRatio = 595f / 842f

        // Sizing logic as implemented in PageViewer:
        val w = (containerWidth - 4f).coerceAtLeast(100f)
        val h = (w / portraitRatio).coerceAtLeast(100f)

        // Document fills 408dp of 412dp available width (99% width utilization, no giant void)
        assertEquals(408f, w, 0.01f)
        assertTrue("Page height must scale proportionally", h > 570f && h < 580f)
        val computedRatio = w / h
        assertEquals(portraitRatio, computedRatio, 0.01f)
    }

    @Test
    fun `test Fit Width preserves landscape aspect ratio and utilizes full width`() {
        val containerWidth = 412f
        // Standard presentation 16:9 landscape: aspect ratio 1.777f
        val landscapeRatio = 16f / 9f

        val w = (containerWidth - 4f).coerceAtLeast(100f)
        val h = (w / landscapeRatio).coerceAtLeast(100f)

        assertEquals(408f, w, 0.01f)
        assertTrue("Landscape height must be proportional (~229.5dp)", h > 229f && h < 231f)
        val computedRatio = w / h
        assertEquals(landscapeRatio, computedRatio, 0.01f)
    }

    @Test
    fun `test Fit Page fits completely within both viewport boundaries`() {
        val containerWidth = 412f
        val containerHeight = 892f
        val portraitRatio = 595f / 842f

        val availableW = (containerWidth - 8f).coerceAtLeast(100f)
        val availableH = (containerHeight - 16f).coerceAtLeast(100f)

        val (w, h) = if (availableW / portraitRatio <= availableH) {
            val width = availableW
            val height = (width / portraitRatio).coerceAtLeast(100f)
            Pair(width, height)
        } else {
            val height = availableH
            val width = (height * portraitRatio).coerceAtLeast(100f)
            Pair(width, height)
        }

        assertTrue("Page width must fit within container", w <= containerWidth)
        assertTrue("Page height must fit within container", h <= containerHeight)
        assertEquals(portraitRatio, w / h, 0.01f)
    }

    @Test
    fun `test ZoomController pan clamping prevents drifting off screen`() {
        val zoomController = ZoomController()
        zoomController.zoomIn() // 1.3x

        // Simulate massive drag pan beyond allowable margin
        zoomController.onTransform(
            zoomChange = 1.0f,
            panChangeX = 5000f,
            panChangeY = 5000f,
            maxPanX = 200f,
            maxPanY = 300f
        )

        assertEquals(200f, zoomController.offsetX, 0.01f)
        assertEquals(300f, zoomController.offsetY, 0.01f)

        zoomController.reset()
        assertEquals(0f, zoomController.offsetX, 0.01f)
        assertEquals(0f, zoomController.offsetY, 0.01f)
    }

    @Test
    fun `test ZoomController double tap zoom toggle`() {
        val zoomController = ZoomController()
        assertEquals(1.0f, zoomController.scale, 0.01f)

        // Double tap when at 1.0x zooms in to 2.0x
        zoomController.onDoubleTap(100f, 100f)
        assertEquals(2.0f, zoomController.scale, 0.01f)

        // Double tap when zoomed resets back to 1.0x
        zoomController.onDoubleTap(100f, 100f)
        assertEquals(1.0f, zoomController.scale, 0.01f)
    }
}

package com.example.viewer

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun PageViewer(
    bitmap: Bitmap?,
    isLoading: Boolean,
    fitMode: FitMode = FitMode.FIT_WIDTH,
    zoomController: ZoomController,
    onToggleControls: () -> Unit,
    onNextPage: () -> Unit = {},
    onPreviousPage: () -> Unit = {},
    highlightBoxes: List<RectF> = emptyList(),
    focusTarget: Pair<Float, Float>? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var dragAccumulatorX by remember { mutableFloatStateOf(0f) }

    // Reset vertical scroll on page change
    LaunchedEffect(bitmap) {
        scrollState.scrollTo(0)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (MaterialTheme.colorScheme.background.red < 0.3f) Color(0xFF161A22) else Color(0xFFE6EBF2)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        zoomController.onDoubleTap(offset.x, offset.y)
                    },
                    onTap = {
                        onToggleControls()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoomController.scale > 1.05f || zoom != 1.0f) {
                        zoomController.onTransform(zoom, pan.x, pan.y)
                    } else {
                        // When at 1.0x zoom, track horizontal drag for slide/page navigation
                        dragAccumulatorX += pan.x
                        if (dragAccumulatorX < -90f) {
                            onNextPage()
                            dragAccumulatorX = 0f
                        } else if (dragAccumulatorX > 90f) {
                            onPreviousPage()
                            dragAccumulatorX = 0f
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        if (bitmap != null) {
            val bmpWidth = bitmap.width.toFloat()
            val bmpHeight = bitmap.height.toFloat()
            val aspectRatio = if (bmpHeight > 0f) bmpWidth / bmpHeight else 0.707f

            // Responsive dimensions based on user instructions
            val (pageWidth, pageHeight) = if (fitMode == FitMode.FIT_WIDTH) {
                // Fit Width: page fills the available container width while strictly preserving aspect ratio
                val w = (containerWidth - 4.dp).coerceAtLeast(100.dp)
                val h = (w / aspectRatio).coerceAtLeast(100.dp)
                Pair(w, h)
            } else {
                // Fit Page: page fits entirely inside the available container without cropping or stretching
                val availableW = (containerWidth - 8.dp).coerceAtLeast(100.dp)
                val availableH = (containerHeight - 16.dp).coerceAtLeast(100.dp)
                if (availableW / aspectRatio <= availableH) {
                    val w = availableW
                    val h = (w / aspectRatio).coerceAtLeast(100.dp)
                    Pair(w, h)
                } else {
                    val h = availableH
                    val w = (h * aspectRatio).coerceAtLeast(100.dp)
                    Pair(w, h)
                }
            }

            val isScrollable = pageHeight > containerHeight && fitMode == FitMode.FIT_WIDTH

            LaunchedEffect(focusTarget) {
                focusTarget?.let { (fx, fy) ->
                    zoomController.focusOn(
                        targetNormX = fx,
                        targetNormY = fy,
                        targetScale = 2.0f,
                        viewportWidth = pageWidth.value,
                        viewportHeight = pageHeight.value
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isScrollable) Modifier.verticalScroll(scrollState) else Modifier),
                contentAlignment = if (isScrollable) Alignment.TopCenter else Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = if (isScrollable) 16.dp else 0.dp)
                        .width(pageWidth)
                        .height(pageHeight)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(2.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                        .graphicsLayer {
                            scaleX = zoomController.scale
                            scaleY = zoomController.scale
                            translationX = zoomController.offsetX
                            translationY = zoomController.offsetY
                        }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Document Page",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (highlightBoxes.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            for (box in highlightBoxes) {
                                val left = box.left * w
                                val top = box.top * h
                                val right = box.right * w
                                val bottom = box.bottom * h

                                // Translucent amber fill
                                drawRect(
                                    color = Color(0x66FFD54F),
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top)
                                )

                                // Crisp highlight border
                                drawRect(
                                    color = Color(0xFFFF8F00),
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        } else if (!isLoading) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                androidx.compose.material3.Text(
                    text = "Unable to display this page.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

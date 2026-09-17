package com.example.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.renderer.DocumentRenderer
import com.example.renderer.PageDimensions
import com.example.renderer.PageRenderResult
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ContinuousReader(
    totalPages: Int,
    currentPage: Int,
    renderer: DocumentRenderer?,
    fitMode: FitMode,
    zoomController: ZoomController,
    onPageVisible: (Int) -> Unit,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0)))

    // Sync current page on scroll
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index in 0 until totalPages) {
                    onPageVisible(index)
                }
            }
    }

    // Scroll to page when external selection occurs
    LaunchedEffect(currentPage) {
        if (currentPage in 0 until totalPages && !listState.isScrollInProgress) {
            if (listState.firstVisibleItemIndex != currentPage) {
                listState.animateScrollToItem(currentPage)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (MaterialTheme.colorScheme.background.red < 0.3f) Color(0xFF161A22) else Color(0xFFE6EBF2)
            )
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("continuous_reader_list"),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(
                count = totalPages,
                key = { it }
            ) { pageIndex ->
                ContinuousPageItem(
                    pageIndex = pageIndex,
                    renderer = renderer,
                    fitMode = fitMode,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight,
                    zoomController = zoomController,
                    onToggleControls = onToggleControls
                )
            }
        }
    }
}

@Composable
private fun ContinuousPageItem(
    pageIndex: Int,
    renderer: DocumentRenderer?,
    fitMode: FitMode,
    containerWidth: Dp,
    containerHeight: Dp,
    zoomController: ZoomController,
    onToggleControls: () -> Unit
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pageIndex) { mutableStateOf(true) }

    val dims = remember(pageIndex, renderer) {
        renderer?.getPageDimensions(pageIndex) ?: PageDimensions(595f, 842f)
    }
    val aspectRatio = dims.aspectRatio

    // Calculate dimensions maintaining exact aspect ratio
    val (pageWidth, pageHeight) = remember(fitMode, containerWidth, containerHeight, dims) {
        if (fitMode == FitMode.FIT_WIDTH) {
            val availableW = (containerWidth - 4.dp).coerceAtLeast(100.dp)
            val w = availableW
            val h = (w / aspectRatio).coerceAtLeast(100.dp)
            Pair(w, h)
        } else {
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
    }

    LaunchedEffect(pageIndex, renderer, fitMode, pageWidth) {
        if (renderer == null) return@LaunchedEffect
        isLoading = true
        // High-DPI target resolution: render at 2.8x point density so text is crystal sharp
        val targetW = (pageWidth.value * 2.8f).toInt().coerceIn(1200, 2400)
        val targetH = (targetW / aspectRatio).toInt().coerceIn(1600, 3200)
        when (val res = renderer.renderPage(pageIndex, targetW, targetH)) {
            is PageRenderResult.Success -> {
                bitmap = res.bitmap
                isLoading = false
            }
            is PageRenderResult.Error -> {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("continuous_page_item_$pageIndex"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(pageWidth)
                .height(pageHeight)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(2.dp))
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
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
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomController.scale
                            scaleY = zoomController.scale
                            translationX = zoomController.offsetX
                            translationY = zoomController.offsetY
                        }
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

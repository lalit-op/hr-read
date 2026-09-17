package com.example.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.renderer.DocumentRenderer
import com.example.renderer.PageRenderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-fidelity slide thumbnail strip.
 * Lazily renders actual slide bitmaps and displays them in an interactive carousel
 * allowing instant seeking across all presentation slides.
 */
@Composable
fun SlideThumbnailStrip(
    totalPages: Int,
    currentPage: Int,
    renderer: DocumentRenderer?,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalPages <= 0) return

    val listState = rememberLazyListState()

    // Automatically keep active slide visible
    LaunchedEffect(currentPage) {
        if (currentPage in 0 until totalPages) {
            listState.animateScrollToItem((currentPage - 1).coerceAtLeast(0))
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("slide_thumbnail_strip"),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // Section Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "SLIDES (${currentPage + 1} / $totalPages)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    count = totalPages,
                    key = { it }
                ) { index ->
                    SlideThumbnailItem(
                        slideIndex = index,
                        isSelected = index == currentPage,
                        renderer = renderer,
                        onClick = { onPageSelected(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideThumbnailItem(
    slideIndex: Int,
    isSelected: Boolean,
    renderer: DocumentRenderer?,
    onClick: () -> Unit
) {
    var bitmap by remember(slideIndex) { mutableStateOf<Bitmap?>(null) }

    // Lazily render thumbnail when item comes into composition
    LaunchedEffect(slideIndex, renderer) {
        if (renderer != null) {
            withContext(Dispatchers.IO) {
                try {
                    // 16:9 thumbnail dimensions: 240x135 px
                    val result = renderer.renderPage(slideIndex, 240, 135)
                    if (result is PageRenderResult.Success) {
                        bitmap = result.bitmap
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.5.dp else 1.dp

    Box(
        modifier = Modifier
            .width(132.dp)
            .aspectRatio(16f / 9f)
            .shadow(if (isSelected) 4.dp else 1.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
            .testTag("thumbnail_slide_${slideIndex + 1}")
    ) {
        val currentBmp = bitmap
        if (currentBmp != null && !currentBmp.isRecycled) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = "Slide ${slideIndex + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Subtle placeholder while lazily loading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }

        // Slide number pill badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${slideIndex + 1}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Active indicator mark
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

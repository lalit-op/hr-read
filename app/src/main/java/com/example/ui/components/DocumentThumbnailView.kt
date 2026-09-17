package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.Document
import com.example.domain.model.DocumentType
import com.example.thumbnail.DocumentThumbnailManager

/**
 * High-performance Document Thumbnail UI component.
 *
 * Lazily renders actual document content thumbnails generated in the background:
 * - Shows the actual document page/slide/sheet/image preview when available
 * - Falls back cleanly to the official file-type icon if generation fails or while loading
 * - Preserves memory and cancels background rendering if the item scrolls offscreen
 */
@Composable
fun DocumentThumbnailView(
    document: Document,
    thumbnailManager: DocumentThumbnailManager,
    modifier: Modifier = Modifier,
    width: Dp = 44.dp,
    height: Dp = 52.dp
) {
    var thumbnailBitmap by remember(document.uri, document.modifiedDate) {
        mutableStateOf(thumbnailManager.getCachedMemoryThumbnail(document))
    }

    LaunchedEffect(document.uri, document.modifiedDate) {
        if (thumbnailBitmap == null) {
            val bitmap = thumbnailManager.loadThumbnail(document)
            if (bitmap != null) {
                thumbnailBitmap = bitmap
            }
        }
    }

    val shape = RoundedCornerShape(8.dp)
    val bitmap = thumbnailBitmap

    if (bitmap != null && !bitmap.isRecycled) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = modifier
                .size(width = width, height = height)
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = shape
                )
                .clip(shape)
                .testTag("doc_thumbnail_${document.id}")
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${document.displayName} thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // Simple file-type icon fallback
        Surface(
            color = document.type.accentColor.copy(alpha = 0.15f),
            shape = shape,
            modifier = modifier
                .size(width = width, height = height)
                .testTag("doc_icon_${document.id}")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = getThumbnailIconForType(document.type),
                    contentDescription = document.type.displayName,
                    tint = document.type.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

fun getThumbnailIconForType(type: DocumentType): ImageVector = when (type) {
    DocumentType.PDF -> Icons.Filled.PictureAsPdf
    DocumentType.WORD -> Icons.Filled.Description
    DocumentType.EXCEL -> Icons.Filled.TableChart
    DocumentType.POWERPOINT -> Icons.Filled.Slideshow
    DocumentType.TEXT -> Icons.Filled.TextFields
    DocumentType.IMAGE -> Icons.Filled.Image
    DocumentType.UNSUPPORTED -> Icons.Filled.Description
}

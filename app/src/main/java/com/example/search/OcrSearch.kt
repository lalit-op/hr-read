package com.example.search

import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.flow.Flow

data class OcrBlock(
    val text: String,
    val confidence: Float,
    val pageIndex: Int,
    val boundingBox: RectF? = null
)

/**
 * OCR extraction interface for scanned PDFs and image-based documents.
 * Feeds into the decoupled search index and never replaces visual rendering.
 */
interface OcrSearch {
    val isOcrAvailable: Boolean

    suspend fun processPage(pageIndex: Int, pageBitmap: Bitmap): List<OcrBlock>

    fun searchOcrIndex(query: String): Flow<List<SearchMatch>>

    suspend fun search(query: String): List<SearchMatch> = emptyList()

    fun clear()
}

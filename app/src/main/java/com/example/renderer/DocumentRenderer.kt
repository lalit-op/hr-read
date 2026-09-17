package com.example.renderer

import android.graphics.Bitmap
import com.example.domain.model.DocumentType
import com.example.domain.model.ResolvedDocumentSource
import com.example.search.SearchMatch

/**
 * Dimensions of an individual rendered page, slide, or sheet.
 */
data class PageDimensions(
    val width: Float,
    val height: Float
) {
    val aspectRatio: Float
        get() = if (height > 0f) width / height else 1f
}

/**
 * Outcome of opening a document with a format-specific renderer.
 */
sealed class DocumentOpenResult {
    data class Success(
        val pageCount: Int,
        val title: String,
        val isSeekable: Boolean = true
    ) : DocumentOpenResult()

    data class PasswordRequired(
        val documentTitle: String
    ) : DocumentOpenResult()

    data class EnginePending(
        val documentType: DocumentType,
        val reason: String
    ) : DocumentOpenResult()

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : DocumentOpenResult()
}

/**
 * Outcome of rendering a specific page into a high-fidelity visual bitmap.
 */
sealed class PageRenderResult {
    data class Success(
        val bitmap: Bitmap,
        val pageIndex: Int
    ) : PageRenderResult()

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : PageRenderResult()
}

/**
 * Core interface for format-specific visual document renderers in HR Read.
 *
 * Adheres strictly to the Visual Rendering Pipeline:
 * Original document -> Format-specific rendering engine -> Actual document page/slide/sheet
 */
interface DocumentRenderer {
    val documentType: DocumentType
    val isInitialized: Boolean

    /**
     * Initializes the document engine with the resolved source.
     */
    suspend fun open(source: ResolvedDocumentSource): DocumentOpenResult

    /**
     * Returns total number of pages, slides, or sheets in the document.
     */
    fun getPageCount(): Int

    /**
     * Gets native dimensions for a given page index.
     */
    fun getPageDimensions(pageIndex: Int): PageDimensions?

    /**
     * Gets native width in points for a given page index.
     */
    fun getPageWidth(pageIndex: Int): Float? = getPageDimensions(pageIndex)?.width

    /**
     * Gets native height in points for a given page index.
     */
    fun getPageHeight(pageIndex: Int): Float? = getPageDimensions(pageIndex)?.height

    /**
     * Renders a specific page at the target pixel dimensions into a high-fidelity Bitmap.
     */
    suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): PageRenderResult

    /**
     * Extracts plain text for a specific page index where supported by the engine.
     */
    suspend fun extractText(pageIndex: Int): String? = null

    /**
     * Searches for text occurrences across document pages where supported.
     */
    suspend fun searchInDocument(query: String): List<SearchMatch> = emptyList()

    /**
     * Checks if the document has searchable content (native text or auxiliary OCR).
     * If false, search affordances will be hidden.
     */
    suspend fun isSearchAvailable(): Boolean = false

    /**
     * Releases native handles, file descriptors, and temporary memory buffers.
     */
    fun close()
}

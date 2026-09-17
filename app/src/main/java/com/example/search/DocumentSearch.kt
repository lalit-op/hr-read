package com.example.search

import android.graphics.RectF
import com.example.domain.model.ResolvedDocumentSource
import kotlinx.coroutines.flow.Flow

data class SearchMatch(
    val pageIndex: Int,
    val lineText: String,
    val snippet: String,
    val boundingBoxes: List<RectF> = emptyList(),
    val contextInfo: String? = null,
    val cellReference: String? = null,
    val isOcrMatch: Boolean = false
) {
    val hasCoordinates: Boolean get() = boundingBoxes.isNotEmpty()
}

/**
 * Text extraction and search engine interface.
 *
 * Runs completely decoupled from the visual document rendering pipeline.
 */
interface DocumentSearch {
    /**
     * Searches for occurrences of [query] within the document text index.
     */
    fun search(source: ResolvedDocumentSource, query: String): Flow<List<SearchMatch>>

    /**
     * Clears internal search index.
     */
    fun clearIndex()
}

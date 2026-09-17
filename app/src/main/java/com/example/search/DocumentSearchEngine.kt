package com.example.search

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.domain.model.ResolvedDocumentSource
import com.example.search.ocr.AndroidOcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Unified document search engine conforming to the SEARCH PIPELINE architecture:
 *
 * For searchable documents:
 * Document -> Native extraction -> Search index -> Search results
 *
 * For scanned documents:
 * Document -> OCR -> Search index -> Search results
 *
 * Visual rendering is 100% decoupled and never replaced by search/OCR text.
 */
class DocumentSearchEngine(
    private val ocrEngine: AndroidOcrEngine = AndroidOcrEngine()
) : DocumentSearch {

    val searchIndex = SearchIndex()

    val isOcrAvailable: Boolean
        get() = ocrEngine.isOcrAvailable

    fun indexNativeEntries(entries: List<IndexedEntry>) {
        searchIndex.addEntries(entries)
    }

    suspend fun indexOcrPage(pageIndex: Int, pageBitmap: Bitmap): List<OcrBlock> {
        val blocks = ocrEngine.processPage(pageIndex, pageBitmap)
        for (block in blocks) {
            searchIndex.addEntry(
                IndexedEntry(
                    pageIndex = block.pageIndex,
                    text = block.text,
                    boundingBoxes = block.boundingBox?.let { listOf(it) } ?: emptyList(),
                    contextInfo = "Scanned Page ${block.pageIndex + 1} (OCR)",
                    isOcr = true
                )
            )
        }
        return blocks
    }

    suspend fun executeSearch(query: String): List<SearchMatch> = withContext(Dispatchers.Default) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // Search primary index (both native and OCR indexed entries)
        val matches = searchIndex.search(trimmed)
        matches
    }

    override fun search(source: ResolvedDocumentSource, query: String): Flow<List<SearchMatch>> = flow {
        emit(executeSearch(query))
    }.flowOn(Dispatchers.Default)

    override fun clearIndex() {
        searchIndex.clear()
        ocrEngine.clear()
    }
}

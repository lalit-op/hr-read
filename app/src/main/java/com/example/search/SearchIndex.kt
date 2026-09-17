package com.example.search

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Entry stored in the in-memory decoupled document search index.
 */
data class IndexedEntry(
    val pageIndex: Int,
    val text: String,
    val boundingBoxes: List<RectF> = emptyList(),
    val contextInfo: String? = null,
    val cellReference: String? = null,
    val isOcr: Boolean = false
)

/**
 * Decoupled search index that houses extracted text and OCR blocks without affecting visual rendering.
 *
 * Architecture:
 * Document -> Text Extraction / OCR -> SearchIndex -> Search Results
 */
class SearchIndex {

    private val entries = mutableListOf<IndexedEntry>()

    @Synchronized
    fun addEntry(entry: IndexedEntry) {
        if (entry.text.isNotBlank()) {
            entries.add(entry)
        }
    }

    @Synchronized
    fun addEntries(newEntries: List<IndexedEntry>) {
        entries.addAll(newEntries.filter { it.text.isNotBlank() })
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun isEmpty(): Boolean = entries.isEmpty()

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun getEntries(): List<IndexedEntry> = entries.toList()

    /**
     * Executes case-insensitive query searching against the index.
     * Returns matching snippets with page/slide/sheet context and bounding box coordinates where present.
     */
    @Synchronized
    fun search(query: String, maxResults: Int = 100): List<SearchMatch> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || entries.isEmpty()) return emptyList()

        val results = mutableListOf<SearchMatch>()

        for (entry in entries) {
            val text = entry.text
            var startIndex = 0
            while (startIndex < text.length) {
                val foundIdx = text.indexOf(trimmed, startIndex, ignoreCase = true)
                if (foundIdx == -1) break

                val snippet = createSnippet(text, foundIdx, trimmed.length)

                // Calculate bounding box for the specific matched substring if line bounding box is present
                val matchBoundingBoxes = calculateMatchBoundingBoxes(entry, foundIdx, trimmed.length)

                results.add(
                    SearchMatch(
                        pageIndex = entry.pageIndex,
                        lineText = text.trim(),
                        snippet = snippet,
                        boundingBoxes = matchBoundingBoxes,
                        contextInfo = entry.contextInfo,
                        cellReference = entry.cellReference,
                        isOcrMatch = entry.isOcr
                    )
                )

                if (results.size >= maxResults) return results
                startIndex = foundIdx + max(1, trimmed.length)
            }
        }

        return results
    }

    private fun createSnippet(fullText: String, matchStart: Int, matchLength: Int): String {
        val prefixContext = 35
        val suffixContext = 40

        val start = max(0, matchStart - prefixContext)
        val end = min(fullText.length, matchStart + matchLength + suffixContext)

        val prefix = if (start > 0) "... " else ""
        val suffix = if (end < fullText.length) " ..." else ""

        val rawSnippet = fullText.substring(start, end).replace("\n", " ").trim()
        return "$prefix$rawSnippet$suffix"
    }

    private fun calculateMatchBoundingBoxes(
        entry: IndexedEntry,
        matchStart: Int,
        matchLength: Int
    ): List<RectF> {
        if (entry.boundingBoxes.isEmpty()) return emptyList()

        val lineBox = entry.boundingBoxes.first()
        val totalChars = max(1, entry.text.length)

        // Interpolate horizontal position within the line bounding box
        val charWidth = (lineBox.right - lineBox.left) / totalChars
        val matchLeft = (lineBox.left + matchStart * charWidth).coerceIn(0f, 1f)
        val matchRight = (matchLeft + matchLength * charWidth).coerceIn(matchLeft, 1f)

        return listOf(
            RectF(
                matchLeft,
                lineBox.top,
                matchRight,
                lineBox.bottom
            )
        )
    }
}

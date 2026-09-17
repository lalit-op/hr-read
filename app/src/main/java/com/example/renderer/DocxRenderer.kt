package com.example.renderer

import android.graphics.Bitmap
import android.util.LruCache
import com.example.domain.model.DocumentType
import com.example.domain.model.ResolvedDocumentSource
import com.example.renderer.docx.DocxDocument
import com.example.renderer.docx.DocxPage
import com.example.renderer.docx.DocxPageLayouter
import com.example.renderer.docx.DocxPageRenderer
import com.example.renderer.docx.DocxParser
import com.example.renderer.docx.DocxRenderElement
import com.example.search.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dedicated contract for Microsoft Word (.doc, .docx) visual rendering engine.
 *
 * Designed to separate visual page rendering from text search.
 */
interface DocxRendererContract : DocumentRenderer {
    fun getDocumentSections(): List<String> = emptyList()
}

/**
 * High-Fidelity OpenXML (DOCX) Visual Page Renderer for HR Read.
 *
 * Evaluates document structure, typography, page sizing, margins,
 * headers, footers, page numbering, tables, cell borders, shading,
 * embedded logos, images, signatures, and page breaks.
 *
 * Produces crisp, vector-accurate page layouts delivered directly to
 * the document viewer surface, supporting fit width, fit page, and smooth pan/zoom.
 */
class DocxRenderer : DocxRendererContract {

    override val documentType: DocumentType = DocumentType.WORD
    override var isInitialized: Boolean = false
        private set

    private var parsedDocument: DocxDocument? = null
    private var pages: List<DocxPage> = emptyList()
    private var seekableFile: File? = null
    private var documentTitle: String = ""

    private val renderLock = Mutex()

    // 32MB LRU memory cache for rendered page bitmaps
    private val pageBitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }

    override suspend fun open(source: ResolvedDocumentSource): DocumentOpenResult = withContext(Dispatchers.IO) {
        close()
        try {
            val file = source.provideSeekableFile()
            seekableFile = file
            documentTitle = source.displayName

            val doc = DocxParser.parse(file, source.displayName)
            parsedDocument = doc
            val laidOutPages = DocxPageLayouter.layout(doc)
            pages = laidOutPages
            isInitialized = true

            DocumentOpenResult.Success(
                pageCount = pages.size,
                title = source.displayName,
                isSeekable = true
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            close()
            android.util.Log.e("DocxRenderer", "Failed opening Word document", t)
            DocumentOpenResult.Error("Failed to render Word document: ${t.localizedMessage ?: t.javaClass.simpleName}", t)
        }
    }

    override fun getPageCount(): Int = if (isInitialized) pages.size else 0

    override fun getPageDimensions(pageIndex: Int): PageDimensions? {
        val page = pages.getOrNull(pageIndex) ?: return null
        return PageDimensions(page.width, page.height)
    }

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): PageRenderResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext PageRenderResult.Error("DocxRenderer is not initialized")
        }
        val page = pages.getOrNull(pageIndex)
            ?: return@withContext PageRenderResult.Error("Page index $pageIndex is out of range (0..${pages.size - 1})")

        val effectiveWidth = targetWidth.coerceAtLeast(100)
        val effectiveHeight = targetHeight.coerceAtLeast(100)
        val cacheKey = "p_${pageIndex}_${effectiveWidth}x${effectiveHeight}"

        // Check LRU cache first
        pageBitmapCache.get(cacheKey)?.let { cachedBitmap ->
            if (!cachedBitmap.isRecycled) {
                return@withContext PageRenderResult.Success(cachedBitmap, pageIndex)
            }
        }

        renderLock.withLock {
            // Re-check after lock
            pageBitmapCache.get(cacheKey)?.let { cachedBitmap ->
                if (!cachedBitmap.isRecycled) {
                    return@withContext PageRenderResult.Success(cachedBitmap, pageIndex)
                }
            }

            try {
                currentCoroutineContext().ensureActive()
                val bitmap = DocxPageRenderer.renderPageToBitmap(page, effectiveWidth, effectiveHeight)
                pageBitmapCache.put(cacheKey, bitmap)
                PageRenderResult.Success(bitmap, pageIndex)
            } catch (cancelEx: kotlinx.coroutines.CancellationException) {
                throw cancelEx
            } catch (t: Throwable) {
                android.util.Log.e("DocxRenderer", "Failed rendering Word page $pageIndex", t)
                PageRenderResult.Error("Failed rendering Word page $pageIndex: ${t.localizedMessage}", t)
            }
        }
    }

    override suspend fun extractText(pageIndex: Int): String? = withContext(Dispatchers.Default) {
        try {
            val page = pages.getOrNull(pageIndex) ?: return@withContext null
            val sb = StringBuilder()
            for (el in page.elements) {
                when (el) {
                    is DocxRenderElement.ParagraphElement -> sb.appendLine(el.paragraph.fullText)
                    is DocxRenderElement.TableElement -> {
                        for (row in el.table.rows) {
                            val rowText = row.cells.joinToString(" | ") { cell ->
                                cell.paragraphs.joinToString(" ") { it.fullText }
                            }
                            sb.appendLine(rowText)
                        }
                    }
                    else -> {}
                }
            }
            sb.toString().trim()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun searchInDocument(query: String): List<SearchMatch> = withContext(Dispatchers.Default) {
        if (query.isBlank() || !isInitialized) return@withContext emptyList()
        try {
            val results = mutableListOf<SearchMatch>()
            for ((idx, page) in pages.withIndex()) {
                currentCoroutineContext().ensureActive()
                val pageText = extractText(idx) ?: continue
                var searchIndex = 0
                var matchCountOnPage = 0
                while (searchIndex < pageText.length) {
                    val found = pageText.indexOf(query, searchIndex, ignoreCase = true)
                    if (found == -1) break
                    matchCountOnPage++
                    val snippetStart = (found - 25).coerceAtLeast(0)
                    val snippetEnd = (found + query.length + 25).coerceAtMost(pageText.length)
                    val snippet = "..." + pageText.substring(snippetStart, snippetEnd).replace("\n", " ") + "..."
                    val lineStart = pageText.lastIndexOf('\n', found).let { if (it == -1) 0 else it + 1 }
                    val lineEnd = pageText.indexOf('\n', found).let { if (it == -1) pageText.length else it }
                    val line = pageText.substring(lineStart, lineEnd).trim()
                    results.add(
                        SearchMatch(
                            pageIndex = idx,
                            lineText = line,
                            snippet = snippet,
                            contextInfo = "Page ${idx + 1}"
                        )
                    )
                    searchIndex = found + query.length.coerceAtLeast(1)
                }
            }
            results
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            android.util.Log.w("DocxRenderer", "Word search error", t)
            emptyList()
        }
    }

    override suspend fun isSearchAvailable(): Boolean {
        if (!isInitialized) return false
        return pages.indices.any { idx ->
            extractText(idx)?.isNotBlank() == true
        }
    }

    override fun close() {
        try {
            pageBitmapCache.evictAll()
        } catch (_: Exception) {}

        parsedDocument = null
        pages = emptyList()
        seekableFile = null
        isInitialized = false
    }
}

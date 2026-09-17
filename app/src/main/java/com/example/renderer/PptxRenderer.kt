package com.example.renderer

import android.graphics.Bitmap
import android.util.LruCache
import com.example.domain.model.DocumentType
import com.example.domain.model.ResolvedDocumentSource
import com.example.renderer.pptx.PptxParser
import com.example.renderer.pptx.PptxPresentation
import com.example.renderer.pptx.PptxSlideRenderer
import com.example.search.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Dedicated contract for Microsoft PowerPoint (.ppt, .pptx) slide visual rendering engine.
 */
interface PptxRendererContract : DocumentRenderer {
    fun getSlideTitles(): List<String> = emptyList()
}

/**
 * Production-ready, native visual PowerPoint (.pptx) rendering engine.
 * Directly renders presentations onto native Android Canvas surfaces with:
 * - Accurate slide dimensions & landscape aspect ratios (16:9 widescreen, 4:3 standard)
 * - Custom backgrounds (solid fills, gradients, images)
 * - Layered shape geometries (rectangles, rounded cards, ellipses, banners, callouts)
 * - Typography styling (fonts, bold, italic, sizing, font colors, bullet lists)
 * - Tables with headers and styled zebra rows
 * - Embedded diagrams and media pictures
 * - Lazy bitmap caching for smooth navigation
 * - In-slide text extraction and search
 */
class PptxRenderer : PptxRendererContract {

    override val documentType: DocumentType = DocumentType.POWERPOINT
    override var isInitialized: Boolean = false
        private set

    private var presentation: PptxPresentation? = null
    private var seekableFile: File? = null

    // Cache recently rendered high-DPI slide bitmaps
    private val slideCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override suspend fun open(source: ResolvedDocumentSource): DocumentOpenResult = withContext(Dispatchers.IO) {
        close()
        try {
            val file = source.provideSeekableFile()
            seekableFile = file

            // Check if file is legacy binary .ppt (CFBF magic bytes D0 CF 11 E0)
            if (isLegacyPpt(file)) {
                return@withContext DocumentOpenResult.EnginePending(
                    documentType = DocumentType.POWERPOINT,
                    reason = "Legacy binary .ppt presentation detected. Convert to modern OpenXML .pptx for full high-fidelity rendering, or open with external viewer."
                )
            }

            val parsed = PptxParser.parse(file, source.displayName)
            presentation = parsed
            isInitialized = true

            val count = parsed.slides.size
            if (count == 0) {
                DocumentOpenResult.Error("Presentation contains no readable slides")
            } else {
                DocumentOpenResult.Success(
                    pageCount = count,
                    title = parsed.title,
                    isSeekable = true
                )
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            android.util.Log.e("PptxRenderer", "Failed opening PowerPoint presentation", t)
            DocumentOpenResult.Error(
                message = "Failed rendering PowerPoint presentation: ${t.localizedMessage ?: t.javaClass.simpleName}",
                throwable = t
            )
        }
    }

    private fun isLegacyPpt(file: File): Boolean {
        if (file.extension.equals("ppt", ignoreCase = true)) {
            try {
                FileInputStream(file).use { fis ->
                    val header = ByteArray(8)
                    val read = fis.read(header)
                    if (read >= 4) {
                        // OLE CFBF magic: 0xD0 0xCF 0x11 0xE0
                        if (header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
                            header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()
                        ) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    override fun getPageCount(): Int = presentation?.slides?.size ?: 0

    override fun getPageDimensions(pageIndex: Int): PageDimensions? {
        val pres = presentation ?: return null
        return PageDimensions(pres.slideWidthPt, pres.slideHeightPt)
    }

    override fun getSlideTitles(): List<String> {
        return presentation?.slides?.map { it.title } ?: emptyList()
    }

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): PageRenderResult =
        withContext(Dispatchers.Default) {
            val pres = presentation
                ?: return@withContext PageRenderResult.Error("PowerPoint renderer is not initialized")

            val slides = pres.slides
            if (pageIndex !in slides.indices) {
                return@withContext PageRenderResult.Error("Slide index $pageIndex is out of range [0, ${slides.size})")
            }

            val cacheKey = "${pageIndex}_${targetWidth}x${targetHeight}"
            val cachedBitmap = slideCache.get(cacheKey)
            if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                return@withContext PageRenderResult.Success(cachedBitmap, pageIndex)
            }

            try {
                currentCoroutineContext().ensureActive()
                val slide = slides[pageIndex]
                val rendered = PptxSlideRenderer.render(
                    slide = slide,
                    presentationWidthPt = pres.slideWidthPt,
                    presentationHeightPt = pres.slideHeightPt,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
                slideCache.put(cacheKey, rendered)
                PageRenderResult.Success(rendered, pageIndex)
            } catch (cancelEx: kotlinx.coroutines.CancellationException) {
                throw cancelEx
            } catch (t: Throwable) {
                android.util.Log.e("PptxRenderer", "Failed rendering slide ${pageIndex + 1}", t)
                PageRenderResult.Error("Failed rendering slide ${pageIndex + 1}: ${t.localizedMessage}", t)
            }
        }

    override suspend fun extractText(pageIndex: Int): String? = withContext(Dispatchers.Default) {
        val pres = presentation ?: return@withContext null
        pres.slides.getOrNull(pageIndex)?.fullText
    }

    override suspend fun searchInDocument(query: String): List<SearchMatch> = withContext(Dispatchers.Default) {
        val pres = presentation ?: return@withContext emptyList()
        val results = mutableListOf<SearchMatch>()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        try {
            pres.slides.forEachIndexed { slideIndex, slide ->
                currentCoroutineContext().ensureActive()
                val text = slide.fullText
                var matchIndex = text.indexOf(trimmed, ignoreCase = true)
                var count = 0
                while (matchIndex >= 0) {
                    val start = (matchIndex - 30).coerceAtLeast(0)
                    val end = (matchIndex + trimmed.length + 30).coerceAtMost(text.length)
                    val snippet = text.substring(start, end).replace("\n", " ").trim()

                    results.add(
                        SearchMatch(
                            pageIndex = slideIndex,
                            lineText = snippet,
                            snippet = "...$snippet...",
                            boundingBoxes = emptyList(),
                            contextInfo = "Slide ${slideIndex + 1}"
                        )
                    )
                    count++
                    matchIndex = text.indexOf(trimmed, matchIndex + trimmed.length, ignoreCase = true)
                }
            }
            results
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            android.util.Log.w("PptxRenderer", "PowerPoint search error", t)
            emptyList()
        }
    }

    override suspend fun isSearchAvailable(): Boolean {
        if (!isInitialized) return false
        return presentation?.slides?.any { it.fullText.isNotBlank() } == true
    }

    override fun close() {
        slideCache.evictAll()
        presentation = null
        seekableFile = null
        isInitialized = false
    }
}

package com.example.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.domain.model.DocumentType
import com.example.domain.model.ResolvedDocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Visual renderer for plaintext files (.txt, .log, .md).
 *
 * Renders pages visually onto Bitmaps rather than raw UI text dumping.
 */
class TextRenderer : DocumentRenderer {

    override val documentType: DocumentType = DocumentType.TEXT
    override var isInitialized: Boolean = false
        private set

    private val pagesOfLines = mutableListOf<List<String>>()
    private val linesPerPage = 45

    override suspend fun open(source: ResolvedDocumentSource): DocumentOpenResult = withContext(Dispatchers.IO) {
        close()
        try {
            val allLines = mutableListOf<String>()
            source.openInputStream().use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        allLines.add(line)
                    }
                }
            }

            if (allLines.isEmpty()) {
                allLines.add("")
            }

            pagesOfLines.clear()
            allLines.chunked(linesPerPage).forEach { chunk ->
                pagesOfLines.add(chunk)
            }

            isInitialized = true
            DocumentOpenResult.Success(
                pageCount = pagesOfLines.size,
                title = source.displayName
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            close()
            android.util.Log.e("TextRenderer", "Failed reading text document", t)
            DocumentOpenResult.Error("Failed reading text document: ${t.localizedMessage}", t)
        }
    }

    override fun getPageCount(): Int = pagesOfLines.size

    override fun getPageDimensions(pageIndex: Int): PageDimensions {
        return PageDimensions(1200f, 1600f) // Standard A4-ratio page canvas
    }

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): PageRenderResult = withContext(Dispatchers.Default) {
        if (pageIndex < 0 || pageIndex >= pagesOfLines.size) {
            return@withContext PageRenderResult.Error("Page index $pageIndex out of range (total: ${pagesOfLines.size})")
        }

        try {
            val w = if (targetWidth > 0) targetWidth else 1200
            val h = if (targetHeight > 0) targetHeight else 1600

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 28f
                typeface = Typeface.MONOSPACE
            }

            val leftMargin = 60f
            var yPos = 80f
            val lineHeight = 34f

            val lines = pagesOfLines[pageIndex]
            for (line in lines) {
                val safeLine = if (line.length > 75) line.substring(0, 75) + "..." else line
                canvas.drawText(safeLine, leftMargin, yPos, paint)
                yPos += lineHeight
            }

            // Page number footer
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                textSize = 22f
                typeface = Typeface.SANS_SERIF
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${pageIndex + 1} / ${pagesOfLines.size}", w / 2f, h - 40f, footerPaint)

            PageRenderResult.Success(bitmap, pageIndex)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            android.util.Log.e("TextRenderer", "Failed rendering text page $pageIndex", t)
            PageRenderResult.Error("Failed rendering text page: ${t.localizedMessage}", t)
        }
    }

    override fun close() {
        pagesOfLines.clear()
        isInitialized = false
    }
}

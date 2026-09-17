package com.example.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.data.documents.DocumentSourceResolver
import com.example.domain.model.Document
import com.example.domain.model.DocumentType
import com.example.renderer.docx.DocxPageLayouter
import com.example.renderer.docx.DocxPageRenderer
import com.example.renderer.docx.DocxParser
import com.example.renderer.pptx.PptxParser
import com.example.renderer.pptx.PptxSlideRenderer
import com.example.renderer.xlsx.XlsxBitmapRenderer
import com.example.renderer.xlsx.XlsxParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Production-grade, memory-efficient Document Thumbnail Manager for HR Read.
 *
 * Renders authentic low-resolution previews directly from actual document content:
 * - PDF: First page rendered via Android OS native [AndroidPdfRenderer]
 * - PPT/PPTX: Actual first slide rendered via native canvas slide pipeline
 * - DOC/DOCX: Actual first page rendered via native OpenXML visual layout pipeline
 * - XLS/XLSX: Real spreadsheet grid snapshot of the first active worksheet
 * - Images: Memory-efficient downsampled decode with inSampleSize
 * - Plaintext: First lines rendered on a document paper canvas
 *
 * Performance Characteristics:
 * - Lazy & background-only processing with limited concurrency (max 2 parallel tasks)
 * - In-memory LRU Bitmap cache (16MB)
 * - Atomic persistent disk cache in cacheDir/document_thumbnails
 * - Deduplication of concurrent in-flight requests
 * - Safe fallback to null (allowing UI to display clean file-type icons)
 */
class DocumentThumbnailManager(
    private val context: Context,
    private val sourceResolver: DocumentSourceResolver
) {

    // Concurrency limiter to protect CPU and memory
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val scope = CoroutineScope(thumbnailDispatcher)

    // In-memory LRU bitmap cache
    private val memoryCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }

    // Disk cache directory
    private val thumbnailDir: File by lazy {
        File(context.cacheDir, "document_thumbnails").apply { mkdirs() }
    }

    // Deduplicate in-flight requests for the same document
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    /**
     * Lazily loads or generates a low-resolution thumbnail bitmap for [document].
     * Returns null if generation is not possible (e.g. legacy binary OLE or revoked access).
     */
    suspend fun loadThumbnail(document: Document): Bitmap? {
        val cacheKey = getCacheKey(document)

        // 1. Fast path: Memory cache hit
        memoryCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) {
                return cached
            }
        }

        // 2. Disk cache hit
        val diskBitmap = withContext(Dispatchers.IO) {
            readFromDiskCache(cacheKey)
        }
        if (diskBitmap != null) {
            memoryCache.put(cacheKey, diskBitmap)
            return diskBitmap
        }

        // 3. Deduplicate in-flight work for this key
        val deferred = inFlightRequests.computeIfAbsent(cacheKey) {
            scope.async {
                try {
                    val generated = generateThumbnail(document)
                    if (generated != null) {
                        saveToDiskCache(cacheKey, generated)
                        memoryCache.put(cacheKey, generated)
                    }
                    generated
                } finally {
                    inFlightRequests.remove(cacheKey)
                }
            }
        }

        return try {
            deferred.await()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if a thumbnail is already available in memory without I/O.
     */
    fun getCachedMemoryThumbnail(document: Document): Bitmap? {
        val cacheKey = getCacheKey(document)
        val cached = memoryCache.get(cacheKey)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    /**
     * Clears all memory and disk cached thumbnails.
     */
    fun clearCache() {
        memoryCache.evictAll()
        try {
            thumbnailDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun getCacheKey(document: Document): String {
        val raw = "${document.uri}_${document.fileSize}_${document.modifiedDate}"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            raw.hashCode().toString()
        }
    }

    private fun readFromDiskCache(cacheKey: String): Bitmap? {
        val file = File(thumbnailDir, "$cacheKey.webp")
        if (!file.exists() || file.length() <= 0) return null
        return try {
            file.setLastModified(System.currentTimeMillis())
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun saveToDiskCache(cacheKey: String, bitmap: Bitmap) {
        try {
            trimDiskCacheIfNeeded()
            val file = File(thumbnailDir, "$cacheKey.webp")
            val tmp = File(thumbnailDir, "$cacheKey.tmp")
            FileOutputStream(tmp).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                }
            }
            tmp.renameTo(file)
            file.setLastModified(System.currentTimeMillis())
        } catch (_: Exception) {}
    }

    private fun trimDiskCacheIfNeeded() {
        try {
            if (!thumbnailDir.exists()) return
            val files = thumbnailDir.listFiles()?.filter { it.isFile && it.name.endsWith(".webp") } ?: return
            var currentBytes = files.sumOf { it.length() }
            val maxBytes = 24 * 1024 * 1024L // 24 MB
            val targetBytes = 16 * 1024 * 1024L // Evict down to 16 MB
            if (currentBytes > maxBytes || files.size > 250) {
                val sorted = files.sortedBy { it.lastModified() }
                for (f in sorted) {
                    if (currentBytes <= targetBytes && files.size <= 180) break
                    val len = f.length()
                    if (f.delete()) {
                        currentBytes -= len
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun generateThumbnail(document: Document): Bitmap? = withContext(thumbnailDispatcher) {
        try {
            when (document.type) {
                DocumentType.PDF -> generatePdfThumbnail(document)
                DocumentType.POWERPOINT -> generatePptxThumbnail(document)
                DocumentType.WORD -> generateDocxThumbnail(document)
                DocumentType.EXCEL -> generateXlsxThumbnail(document)
                DocumentType.IMAGE -> generateImageThumbnail(document)
                DocumentType.TEXT -> generateTextThumbnail(document)
                DocumentType.UNSUPPORTED -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ==========================================
    // PDF: First Page at Low Resolution
    // ==========================================
    private suspend fun generatePdfThumbnail(document: Document): Bitmap? {
        val uri = Uri.parse(document.uri)
        var pfd: ParcelFileDescriptor? = null
        var renderer: AndroidPdfRenderer? = null
        var page: AndroidPdfRenderer.Page? = null
        return try {
            val seekable = sourceResolver.resolve(uri).provideSeekableFile()
            pfd = ParcelFileDescriptor.open(seekable, ParcelFileDescriptor.MODE_READ_ONLY)
            if (pfd == null) return null

            renderer = AndroidPdfRenderer(pfd)
            if (renderer.pageCount <= 0) return null

            page = renderer.openPage(0)
            val srcW = page.width.coerceAtLeast(1)
            val srcH = page.height.coerceAtLeast(1)

            val targetWidth = 180
            val targetHeight = ((targetWidth.toFloat() / srcW) * srcH).roundToInt().coerceIn(120, 260)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            android.util.Log.w("ThumbnailManager", "Failed generating PDF thumbnail for ${document.displayName}", e)
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    // ==========================================
    // PPT / PPTX: First Slide
    // ==========================================
    private suspend fun generatePptxThumbnail(document: Document): Bitmap? {
        val uri = Uri.parse(document.uri)
        return try {
            val file = sourceResolver.resolve(uri).provideSeekableFile()
            if (isLegacyOleDocument(file, "ppt")) return null

            val presentation = PptxParser.parse(file, displayName = document.displayName, firstSlideOnly = true)
            val slide = presentation.slides.firstOrNull() ?: return null

            val targetWidth = 240
            val targetHeight = ((targetWidth.toFloat() / presentation.slideWidthPt.coerceAtLeast(10f)) * presentation.slideHeightPt)
                .roundToInt().coerceIn(120, 200)

            PptxSlideRenderer.render(
                slide = slide,
                presentationWidthPt = presentation.slideWidthPt,
                presentationHeightPt = presentation.slideHeightPt,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // DOC / DOCX: First Page via Document Layouter
    // ==========================================
    private suspend fun generateDocxThumbnail(document: Document): Bitmap? {
        val uri = Uri.parse(document.uri)
        return try {
            val file = sourceResolver.resolve(uri).provideSeekableFile()
            if (isLegacyOleDocument(file, "doc")) return null

            val doc = DocxParser.parse(file, documentTitle = document.displayName)
            val pages = DocxPageLayouter.layout(doc, firstPageOnly = true)
            val firstPage = pages.firstOrNull() ?: return null

            val targetWidth = 180
            val targetHeight = ((targetWidth.toFloat() / firstPage.width.coerceAtLeast(10f)) * firstPage.height)
                .roundToInt().coerceIn(140, 260)

            DocxPageRenderer.renderPageToBitmap(firstPage, targetWidth, targetHeight)
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // XLS / XLSX: First Sheet Grid Preview
    // ==========================================
    private suspend fun generateXlsxThumbnail(document: Document): Bitmap? {
        val uri = Uri.parse(document.uri)
        return try {
            val file = sourceResolver.resolve(uri).provideSeekableFile()
            if (isLegacyOleDocument(file, "xls")) return null

            val workbook = XlsxParser.parse(file, displayName = document.displayName)
            if (workbook.sheetCount <= 0) return null
            val sheet = workbook.getWorksheet(0)

            XlsxBitmapRenderer.renderThumbnail(
                worksheet = sheet,
                targetWidth = 240,
                targetHeight = 180
            )
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // Image: Downsampled Decode
    // ==========================================
    private suspend fun generateImageThumbnail(document: Document): Bitmap? {
        val uri = Uri.parse(document.uri)
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW <= 0 || origH <= 0) return null

            val sampleSize = calculateInSampleSize(origW, origH, 200, 200)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // Plaintext: First Lines Canvas Preview
    // ==========================================
    private suspend fun generateTextThumbnail(document: Document): Bitmap? {
        val uri = Uri.parse(document.uri)
        return try {
            val lines = mutableListOf<String>()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { seq ->
                    for (line in seq) {
                        lines.add(line)
                        if (lines.size >= 14) break
                    }
                }
            }
            if (lines.isEmpty() || lines.all { it.isBlank() }) return null

            val w = 180
            val h = 240
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 10f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }

            val linePaint = Paint().apply {
                color = Color.parseColor("#F1F5F9")
                strokeWidth = 1f
            }

            // Draw ruled lines
            var y = 20f
            while (y < h - 10f) {
                canvas.drawLine(10f, y + 4f, w - 10f, y + 4f, linePaint)
                y += 16f
            }

            // Draw actual text lines
            var textY = 20f
            for (line in lines) {
                val truncated = if (line.length > 24) line.take(23) + "…" else line
                canvas.drawText(truncated, 12f, textY, textPaint)
                textY += 16f
                if (textY >= h - 12f) break
            }

            // Outer border
            val borderPaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            canvas.drawRect(0f, 0f, w.toFloat() - 1f, h.toFloat() - 1f, borderPaint)

            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / inSampleSize >= reqHeight && halfW / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun isLegacyOleDocument(file: File, extension: String): Boolean {
        if (!file.extension.equals(extension, ignoreCase = true)) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                read >= 4 &&
                    header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
                    header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }
}

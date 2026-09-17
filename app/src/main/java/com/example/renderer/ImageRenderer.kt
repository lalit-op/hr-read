package com.example.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.domain.model.DocumentType
import com.example.domain.model.ResolvedDocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Visual renderer for common image documents (PNG, JPEG, WebP).
 */
class ImageRenderer : DocumentRenderer {

    override val documentType: DocumentType = DocumentType.IMAGE
    override var isInitialized: Boolean = false
        private set

    private var loadedBitmap: Bitmap? = null
    private var imageTitle: String = ""

    override suspend fun open(source: ResolvedDocumentSource): DocumentOpenResult = withContext(Dispatchers.IO) {
        close()
        try {
            source.openInputStream().use { stream ->
                loadedBitmap = BitmapFactory.decodeStream(stream)
            }

            val bmp = loadedBitmap
            if (bmp != null) {
                imageTitle = source.displayName
                isInitialized = true
                DocumentOpenResult.Success(pageCount = 1, title = imageTitle)
            } else {
                DocumentOpenResult.Error("Failed to decode image data")
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            close()
            android.util.Log.e("ImageRenderer", "Failed opening image", t)
            DocumentOpenResult.Error("Failed opening image: ${t.localizedMessage}", t)
        }
    }

    override fun getPageCount(): Int = if (isInitialized) 1 else 0

    override fun getPageDimensions(pageIndex: Int): PageDimensions? {
        val bmp = loadedBitmap ?: return null
        return PageDimensions(bmp.width.toFloat(), bmp.height.toFloat())
    }

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): PageRenderResult = withContext(Dispatchers.Default) {
        val bmp = loadedBitmap ?: return@withContext PageRenderResult.Error("Image not loaded")
        PageRenderResult.Success(bmp, 0)
    }

    override fun close() {
        loadedBitmap = null
        isInitialized = false
    }
}
